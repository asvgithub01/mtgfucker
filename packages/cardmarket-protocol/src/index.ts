export const TRANSFER_PREFIX = "MTGF1.";
export const MAX_BATCH_ITEMS = 100;

export const CARDMARKET_LANGUAGES = {
  en: 1,
  fr: 2,
  de: 3,
  es: 4,
  it: 5,
  zhs: 6,
  ja: 7,
  pt: 8,
  ru: 9,
  ko: 10,
  zht: 11,
  nl: 12,
  pl: 13,
  cs: 14,
  hu: 15,
  id: 16,
  th: 17
} as const;

export const CARDMARKET_CONDITIONS = {
  mint: 1,
  near_mint: 2,
  excellent: 3,
  good: 4,
  light_played: 5,
  played: 6,
  poor: 7
} as const;

export type CardmarketLanguage = keyof typeof CARDMARKET_LANGUAGES;
export type CardmarketCondition = keyof typeof CARDMARKET_CONDITIONS;
export type CardFinish = "nonfoil" | "foil" | "etched";

export interface CardmarketTransferItem {
  collectionItemId?: string;
  printingUuid?: string;
  mcmId: string;
  mcmMetaId?: string;
  name: string;
  collectorNumber?: string;
  quantity: number;
  priceCents: number;
  language: CardmarketLanguage;
  condition: CardmarketCondition;
  finish: CardFinish;
  comment?: string;
}

export interface CardmarketTransfer {
  version: 1;
  batchId: string;
  createdAt: string;
  setCode: string;
  setName: string;
  mcmSetIds: number[];
  items: CardmarketTransferItem[];
}

export interface CardmarketBatchCandidate extends CardmarketTransferItem {
  setCode: string;
  setName: string;
  mcmSetIds: number[];
}

function assertText(value: unknown, field: string): asserts value is string {
  if (typeof value !== "string" || value.trim() === "") {
    throw new Error(`Falta ${field}.`);
  }
}

export function validateTransfer(value: unknown): CardmarketTransfer {
  if (!value || typeof value !== "object") throw new Error("El lote no es válido.");
  const transfer = value as Partial<CardmarketTransfer>;
  if (transfer.version !== 1) throw new Error("Versión de lote no compatible.");
  assertText(transfer.batchId, "el identificador del lote");
  assertText(transfer.createdAt, "la fecha del lote");
  assertText(transfer.setCode, "el código de edición");
  assertText(transfer.setName, "el nombre de edición");
  if (!Array.isArray(transfer.mcmSetIds) ||
      transfer.mcmSetIds.some(id => !Number.isInteger(id) || id <= 0)) {
    throw new Error("Los identificadores de edición de Cardmarket no son válidos.");
  }
  if (!Array.isArray(transfer.items) || transfer.items.length === 0 ||
      transfer.items.length > MAX_BATCH_ITEMS) {
    throw new Error(`El lote debe contener entre 1 y ${MAX_BATCH_ITEMS} filas.`);
  }

  const seenProducts = new Set<string>();
  for (const item of transfer.items) {
    assertText(item.mcmId, "el identificador de producto de Cardmarket");
    if (!/^\d+$/.test(item.mcmId)) throw new Error(`mcmId no válido: ${item.mcmId}`);
    if (seenProducts.has(item.mcmId)) {
      throw new Error(`La prueba todavía no admite dos variantes del producto ${item.mcmId}.`);
    }
    seenProducts.add(item.mcmId);
    assertText(item.name, "el nombre de la carta");
    if (!Number.isInteger(item.quantity) || item.quantity < 1 || item.quantity > 99) {
      throw new Error(`Cantidad no válida para ${item.name}.`);
    }
    if (!Number.isInteger(item.priceCents) || item.priceCents < 1) {
      throw new Error(`Precio no válido para ${item.name}.`);
    }
    if (!(item.language in CARDMARKET_LANGUAGES)) {
      throw new Error(`Idioma no compatible para ${item.name}.`);
    }
    if (!(item.condition in CARDMARKET_CONDITIONS)) {
      throw new Error(`Estado no compatible para ${item.name}.`);
    }
    if (!(["nonfoil", "foil", "etched"] as string[]).includes(item.finish)) {
      throw new Error(`Acabado no compatible para ${item.name}.`);
    }
  }
  return transfer as CardmarketTransfer;
}

function bytesToBase64Url(bytes: Uint8Array): string {
  let binary = "";
  bytes.forEach(byte => { binary += String.fromCharCode(byte); });
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function base64UrlToBytes(value: string): Uint8Array {
  const base64 = value.replace(/-/g, "+").replace(/_/g, "/")
    .padEnd(Math.ceil(value.length / 4) * 4, "=");
  const binary = atob(base64);
  return Uint8Array.from(binary, char => char.charCodeAt(0));
}

export function encodeTransfer(value: CardmarketTransfer): string {
  const transfer = validateTransfer(value);
  return TRANSFER_PREFIX + bytesToBase64Url(new TextEncoder().encode(JSON.stringify(transfer)));
}

export function decodeTransfer(code: string): CardmarketTransfer {
  const normalized = code.trim();
  if (!normalized.startsWith(TRANSFER_PREFIX)) throw new Error("El código no empieza por MTGF1.");
  try {
    const json = new TextDecoder().decode(base64UrlToBytes(normalized.slice(TRANSFER_PREFIX.length)));
    return validateTransfer(JSON.parse(json));
  } catch (error) {
    if (error instanceof Error && !error.message.toLowerCase().includes("json")) throw error;
    throw new Error("El código del lote está dañado o incompleto.");
  }
}

export function newBatchId(): string {
  return crypto.randomUUID();
}

export function priceWithMultiplier(basePriceEur: number, multiplier: number): number {
  if (!Number.isFinite(basePriceEur) || basePriceEur <= 0) throw new Error("El precio base no es válido.");
  if (!Number.isFinite(multiplier) || multiplier <= 0) throw new Error("El multiplicador no es válido.");
  return Math.max(1, Math.round(basePriceEur * multiplier * 100));
}

/**
 * Turns a mixed selection into the exact pages Cardmarket can accept.
 * BulkListing exposes one expansion at a time, so every transfer contains
 * at most 100 distinct product rows from the same expansion.
 */
export function buildTransferBatches(
  candidates: CardmarketBatchCandidate[],
  createdAt = new Date().toISOString()
): CardmarketTransfer[] {
  const groups = new Map<number, CardmarketBatchCandidate[]>();
  for (const candidate of candidates) {
    assertText(candidate.setCode, "el código de edición");
    assertText(candidate.setName, "el nombre de edición");
    const setIds = [...new Set(candidate.mcmSetIds.filter(id => Number.isInteger(id) && id > 0))];
    if (setIds.length === 0) throw new Error(`Falta el ID de edición de Cardmarket para ${candidate.name}.`);
    const normalized = { ...candidate, mcmSetIds: setIds };
    const group = groups.get(setIds[0]) || [];
    group.push(normalized);
    groups.set(setIds[0], group);
  }

  const transfers: CardmarketTransfer[] = [];
  for (const group of groups.values()) {
    const batches: CardmarketBatchCandidate[][] = [];
    for (const candidate of group) {
      let batch = batches.find(current =>
        current.length < MAX_BATCH_ITEMS &&
        current.every(existing => existing.mcmId !== candidate.mcmId)
      );
      if (!batch) {
        batch = [];
        batches.push(batch);
      }
      batch.push(candidate);
    }

    for (const batch of batches) {
      const first = batch[0];
      const transfer: CardmarketTransfer = {
        version: 1,
        batchId: newBatchId(),
        createdAt,
        setCode: first.setCode.trim().toUpperCase(),
        setName: first.setName.trim(),
        mcmSetIds: first.mcmSetIds,
        items: batch.map(({ setCode: _setCode, setName: _setName, mcmSetIds: _mcmSetIds, ...item }) => item)
      };
      transfers.push(validateTransfer(transfer));
    }
  }
  return transfers;
}
