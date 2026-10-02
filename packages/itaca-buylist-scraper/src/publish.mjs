#!/usr/bin/env node

import { gzip } from "node:zlib";
import { promisify } from "node:util";
import { mkdir, readFile, rename, rm, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import process from "node:process";

const gzipAsync = promisify(gzip);
const root = process.env.INIT_CWD || process.cwd();
const source = resolve(root, process.argv[2] || "build/itaca-buylist-full.json");
const destination = resolve(root, process.argv[3] || "web/public/data/itaca-buylist.json.gz");

const raw = await readFile(source);
const catalog = JSON.parse(raw.toString("utf8"));
if (catalog.schemaVersion !== 1 || catalog.provider !== "itaca.gg") {
  throw new Error("El catálogo no tiene un formato compatible.");
}
if (!catalog.complete || catalog.failedExpansions?.length) {
  throw new Error("El catálogo de Itaca está incompleto y no se publicará en la web.");
}

const compressed = await gzipAsync(raw, { level: 9 });
const temporary = `${destination}.tmp`;
await mkdir(dirname(destination), { recursive: true });
await writeFile(temporary, compressed);
await rm(destination, { force: true });
await rename(temporary, destination);
process.stdout.write(`Catálogo web publicado: ${destination} (${compressed.length} bytes)\n`);
