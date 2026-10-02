#!/usr/bin/env node

import { mkdir, readFile, rename, rm, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import process from "node:process";
import {
  extractOwnedSetCodes,
  parseBuylistPage,
  parseExpansionDirectory
} from "./parser.mjs";

const BASE_URL = "https://itaca.gg";
const DEFAULT_OUTPUT = "build/itaca-buylist.json";
const DEFAULT_DELAY_MS = 5_000;
const DEFAULT_MAX_PAGES = 100;

function userPath(path) {
  return resolve(process.env.INIT_CWD || process.cwd(), path);
}

function usage() {
  return `Actualiza un catálogo local de la BuyList de Itaca.

Uso:
  node src/cli.mjs --set-codes STX,SOS
  node src/cli.mjs --collection-json ruta/a/biblioteca.json
  node src/cli.mjs --all --output build/itaca-buylist-full.json

Opciones:
  --all                     Descarga todas las expansiones de la BuyList.
  --set-codes COD1,COD2     Códigos de las expansiones que posees.
  --collection-json RUTA    JSON con tarjetas que contengan el campo setCode.
  --set-file RUTA           JSON con códigos o fichero con un código por línea.
  --output RUTA             Destino (por defecto: ${DEFAULT_OUTPUT}).
  --resume                  Continúa desde un fichero parcial existente.
  --delay-ms N              Pausa entre peticiones (por defecto: ${DEFAULT_DELAY_MS}).
  --max-pages N             Límite de seguridad por expansión (por defecto: ${DEFAULT_MAX_PAGES}).
  --dry-run                 Resuelve las expansiones sin descargar sus cartas.
  --help                    Muestra esta ayuda.
`;
}

function parseArgs(argv) {
  const options = {
    codes: [],
    all: false,
    collectionJson: "",
    setFile: "",
    output: DEFAULT_OUTPUT,
    delayMs: DEFAULT_DELAY_MS,
    maxPages: DEFAULT_MAX_PAGES,
    dryRun: false,
    resume: false,
    help: false
  };
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    const value = () => {
      const next = argv[index + 1];
      if (!next || next.startsWith("--")) throw new Error(`Falta el valor de ${argument}`);
      index += 1;
      return next;
    };
    if (argument === "--all") options.all = true;
    else if (argument === "--set-codes") options.codes.push(...value().split(","));
    else if (argument === "--collection-json") options.collectionJson = value();
    else if (argument === "--set-file") options.setFile = value();
    else if (argument === "--output") options.output = value();
    else if (argument === "--delay-ms") options.delayMs = Number.parseInt(value(), 10);
    else if (argument === "--max-pages") options.maxPages = Number.parseInt(value(), 10);
    else if (argument === "--dry-run") options.dryRun = true;
    else if (argument === "--resume") options.resume = true;
    else if (argument === "--help" || argument === "-h") options.help = true;
    else throw new Error(`Opción desconocida: ${argument}`);
  }
  if (!Number.isInteger(options.delayMs) || options.delayMs < 1_000) {
    throw new Error("--delay-ms debe ser al menos 1000 para no sobrecargar Itaca.");
  }
  if (!Number.isInteger(options.maxPages) || options.maxPages < 1) {
    throw new Error("--max-pages debe ser un entero positivo.");
  }
  return options;
}

function normalizeCodes(values) {
  return [...new Set(values.map(value => String(value).trim().toUpperCase()).filter(Boolean))].sort();
}

async function codesFromFile(path) {
  const source = await readFile(userPath(path), "utf8");
  try {
    const parsed = JSON.parse(source);
    if (Array.isArray(parsed) && parsed.every(value => typeof value === "string")) return parsed;
    return extractOwnedSetCodes(parsed);
  } catch {
    return source.split(/[,;\s]+/);
  }
}

const sleep = milliseconds => new Promise(resolvePromise => setTimeout(resolvePromise, milliseconds));

async function fetchText(url, attempts = 3) {
  let lastError;
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      const response = await fetch(url, {
        headers: {
          Accept: "text/html,application/xhtml+xml",
          "User-Agent": "MTGFucker personal BuyList updater/0.1"
        },
        signal: AbortSignal.timeout(45_000)
      });
      if (response.ok) return response.text();
      if (response.status !== 429 && response.status < 500) {
        throw new Error(`Itaca devolvió HTTP ${response.status} para ${url}`);
      }
      lastError = new Error(`Itaca devolvió HTTP ${response.status} para ${url}`);
    } catch (error) {
      lastError = error;
    }
    if (attempt < attempts) await sleep(attempt * 5_000);
  }
  throw lastError;
}

async function scrapeExpansion(expansion, options, waitBeforeRequest) {
  const cards = new Map();
  let offset = 0;
  let page = 0;
  let hasAnotherPage = true;
  while (page < options.maxPages) {
    await waitBeforeRequest();
    const url = new URL("/buylist/expansion", BASE_URL);
    url.searchParams.set("expansionName", expansion.name);
    url.searchParams.set("expansionAbbreviation", expansion.code);
    url.searchParams.set("max", "20");
    if (offset > 0) url.searchParams.set("offset", String(offset));
    const html = await fetchText(url);
    const parsed = parseBuylistPage(html, {
      baseUrl: BASE_URL,
      expansionCode: expansion.code,
      expansionName: expansion.name,
      currentOffset: offset
    });
    parsed.cards.forEach(card => cards.set(card.productId, card));
    page += 1;
    process.stdout.write(`  ${expansion.code}: página ${page}, ${cards.size} cartas\n`);
    if (parsed.nextOffset === null || parsed.nextOffset <= offset) {
      hasAnotherPage = false;
      break;
    }
    offset = parsed.nextOffset;
  }
  if (hasAnotherPage && page >= options.maxPages) {
    throw new Error(`${expansion.code} alcanzó el límite de ${options.maxPages} páginas.`);
  }
  return {
    code: expansion.code,
    name: expansion.name,
    sourceUrl: `${BASE_URL}/buylist/expansion?expansionName=${encodeURIComponent(expansion.name)}&expansionAbbreviation=${encodeURIComponent(expansion.code)}`,
    cardCount: cards.size,
    cards: [...cards.values()]
  };
}

async function writeJsonSafely(path, value) {
  const absolute = userPath(path);
  const temporary = `${absolute}.tmp`;
  await mkdir(dirname(absolute), { recursive: true });
  await writeFile(temporary, `${JSON.stringify(value, null, 2)}\n`, "utf8");
  await rm(absolute, { force: true });
  await rename(temporary, absolute);
  return absolute;
}

async function readPreviousResult(path) {
  try {
    const parsed = JSON.parse(await readFile(userPath(path), "utf8"));
    return parsed?.schemaVersion === 1 && parsed?.provider === "itaca.gg" ? parsed : null;
  } catch {
    return null;
  }
}

function makeResult({ startedAt, setCodes, missing, selected, completed, failures, complete }) {
  const expansions = selected.map(expansion => completed.get(expansion.code)).filter(Boolean);
  return {
    schemaVersion: 1,
    provider: "itaca.gg",
    startedAt,
    updatedAt: new Date().toISOString(),
    complete,
    requestedSetCodes: setCodes,
    missingSetCodes: missing,
    directoryExpansionCount: selected.length,
    expansionCount: expansions.length,
    cardCount: expansions.reduce((total, expansion) => total + expansion.cardCount, 0),
    failedExpansions: failures,
    expansions
  };
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    process.stdout.write(usage());
    return;
  }

  const requested = [...options.codes];
  if (options.collectionJson) requested.push(...await codesFromFile(options.collectionJson));
  if (options.setFile) requested.push(...await codesFromFile(options.setFile));
  const setCodes = normalizeCodes(requested);
  if (!options.all && !setCodes.length) throw new Error(`Indica --all, --set-codes, --collection-json o --set-file.\n\n${usage()}`);
  if (options.all && setCodes.length) throw new Error("No combines --all con una selección de expansiones.");

  let lastRequestAt = 0;
  const waitBeforeRequest = async () => {
    const pending = options.delayMs - (Date.now() - lastRequestAt);
    if (pending > 0) await sleep(pending);
    lastRequestAt = Date.now();
  };

  process.stdout.write(`Consultando el índice de expansiones de Itaca…\n`);
  await waitBeforeRequest();
  const directory = parseExpansionDirectory(await fetchText(`${BASE_URL}/buylist/expansions`));
  const byCode = new Map(directory.map(expansion => [expansion.code, expansion]));
  const found = options.all ? directory : setCodes.map(code => byCode.get(code)).filter(Boolean);
  const selectedCodes = options.all ? found.map(expansion => expansion.code) : setCodes;
  const missing = options.all ? [] : setCodes.filter(code => !byCode.has(code));

  if (options.all) process.stdout.write(`Encontradas ${found.length} expansiones en la BuyList.\n`);
  else if (found.length) process.stdout.write(`Encontradas: ${found.map(item => `${item.code} (${item.name})`).join(", ")}\n`);
  if (missing.length) process.stderr.write(`Sin BuyList coincidente: ${missing.join(", ")}\n`);
  if (!found.length) throw new Error("Itaca no tiene ninguna de las expansiones solicitadas.");
  if (options.dryRun) return;

  const startedAt = new Date().toISOString();
  const completed = new Map();
  if (options.resume) {
    const previous = await readPreviousResult(options.output);
    // A completed snapshot is stale input for a new refresh, not a checkpoint.
    const resumable = previous?.complete ? [] : previous?.expansions;
    resumable?.forEach(expansion => {
      if (byCode.has(expansion.code) && Array.isArray(expansion.cards)) completed.set(expansion.code, expansion);
    });
    if (completed.size) process.stdout.write(`Reanudando con ${completed.size} expansiones ya guardadas.\n`);
  }
  const failures = [];
  let processedSinceCheckpoint = 0;
  for (const expansion of found) {
    if (completed.has(expansion.code)) continue;
    process.stdout.write(`Descargando ${expansion.code} · ${expansion.name}\n`);
    try {
      completed.set(expansion.code, await scrapeExpansion(expansion, options, waitBeforeRequest));
      processedSinceCheckpoint += 1;
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      failures.push({ code: expansion.code, name: expansion.name, error: message });
      process.stderr.write(`  Falló ${expansion.code}: ${message}\n`);
      processedSinceCheckpoint = 10;
    }
    if (processedSinceCheckpoint >= 10) {
      await writeJsonSafely(options.output, makeResult({
        startedAt, setCodes: selectedCodes, missing, selected: found, completed, failures, complete: false
      }));
      process.stdout.write(`  Progreso guardado: ${completed.size}/${found.length} expansiones.\n`);
      processedSinceCheckpoint = 0;
    }
  }

  const result = makeResult({
    startedAt,
    setCodes: selectedCodes,
    missing,
    selected: found,
    completed,
    failures,
    complete: failures.length === 0 && completed.size === found.length
  });
  const output = await writeJsonSafely(options.output, result);
  process.stdout.write(`Guardadas ${result.cardCount} cartas de ${result.expansionCount} expansiones en ${output}\n`);
  if (failures.length) throw new Error(`No se pudieron actualizar ${failures.length} expansiones; puedes reintentar con --resume.`);
}

main().catch(error => {
  process.stderr.write(`Error: ${error instanceof Error ? error.message : String(error)}\n`);
  process.exitCode = 1;
});
