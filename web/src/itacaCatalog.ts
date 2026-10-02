import type { CloudCard, CloudLibrary } from "./firebase";

export interface ItacaCatalogCard {
  productId: string;
  cardmarketId: string | null;
  name: string;
  localizedName: string | null;
  expansionCode: string;
  expansionName: string;
  defaultSelection: {
    language: string | null;
    condition: string | null;
    finish: string | null;
  };
  defaultOffer: {
    cashEur: number | null;
    itacoins: number | null;
  };
}

interface ItacaCatalogExpansion {
  code: string;
  name: string;
  cards: ItacaCatalogCard[];
}

export interface ItacaCatalog {
  schemaVersion: number;
  provider: string;
  updatedAt: string;
  complete: boolean;
  directoryExpansionCount: number;
  expansionCount: number;
  cardCount: number;
  failedExpansions: Array<{ code: string; name: string; error: string }>;
  expansions: ItacaCatalogExpansion[];
}

export interface ItacaValuationRow {
  key: string;
  libraryName: string;
  card: CloudCard;
  quote: ItacaCatalogCard | null;
  cashUnitCents: number;
  itacoinUnitHundredths: number;
  cashTotalCents: number;
  itacoinTotalHundredths: number;
}

export interface ItacaValuation {
  rows: ItacaValuationRow[];
  matchedRows: number;
  unmatchedRows: number;
  matchedCopies: number;
  totalCopies: number;
  cashTotalCents: number;
  itacoinTotalHundredths: number;
}

async function decodeGzip(response: Response): Promise<string> {
  // Vite and most static hosts advertise .gz files with Content-Encoding: gzip.
  // In that case fetch() has already decompressed the response body for us.
  if (response.headers.get("content-encoding")?.toLowerCase().includes("gzip")) {
    return response.text();
  }
  if (!("DecompressionStream" in window)) {
    throw new Error("Este navegador no permite abrir el catálogo comprimido de Itaca.");
  }
  const decompressed = response.body?.pipeThrough(new DecompressionStream("gzip"));
  if (!decompressed) throw new Error("Itaca devolvió un catálogo vacío.");
  return new Response(decompressed).text();
}

function validateCatalog(value: unknown): ItacaCatalog {
  const catalog = value as Partial<ItacaCatalog>;
  if (catalog.schemaVersion !== 1 || catalog.provider !== "itaca.gg" || !Array.isArray(catalog.expansions)) {
    throw new Error("El fichero de Itaca no tiene un formato compatible.");
  }
  if ((catalog.failedExpansions?.length || 0) > 0 || (!catalog.complete && !import.meta.env.DEV)) {
    throw new Error("El catálogo de Itaca todavía se está generando. Inténtalo cuando termine la actualización.");
  }
  return catalog as ItacaCatalog;
}

export async function loadItacaCatalog(): Promise<ItacaCatalog> {
  const compressedUrl = `${import.meta.env.BASE_URL}data/itaca-buylist.json.gz`;
  const compressed = await fetch(compressedUrl, { cache: "no-cache" });
  if (compressed.ok) return validateCatalog(JSON.parse(await decodeGzip(compressed)));

  const jsonUrl = `${import.meta.env.BASE_URL}data/itaca-buylist.json`;
  const plain = await fetch(jsonUrl, { cache: "no-cache" });
  if (!plain.ok) throw new Error("Todavía no se ha publicado el catálogo completo de Itaca.");
  return validateCatalog(await plain.json());
}

function availableQuote(card: ItacaCatalogCard): boolean {
  return Number.isFinite(card.defaultOffer.cashEur) && Number.isFinite(card.defaultOffer.itacoins)
    && (card.defaultOffer.cashEur || 0) >= 0 && (card.defaultOffer.itacoins || 0) >= 0;
}

export function valueLibrariesWithItaca(
  libraries: CloudLibrary[],
  catalog: ItacaCatalog,
  libraryId = ""
): ItacaValuation {
  const byCardmarketId = new Map<string, ItacaCatalogCard>();
  catalog.expansions.forEach(expansion => expansion.cards.forEach(card => {
    if (card.cardmarketId && availableQuote(card) && !byCardmarketId.has(card.cardmarketId)) {
      byCardmarketId.set(card.cardmarketId, card);
    }
  }));

  const rows: ItacaValuationRow[] = [];
  libraries.filter(library => !libraryId || library.id === libraryId).forEach(library => {
    library.cards.forEach((card, index) => {
      if (!Number.isFinite(card.quantity) || card.quantity <= 0) return;
      const quote = /^\d+$/.test(card.mcmId) ? byCardmarketId.get(card.mcmId) || null : null;
      const cashUnitCents = quote ? Math.round((quote.defaultOffer.cashEur || 0) * 100) : 0;
      const itacoinUnitHundredths = quote ? Math.round((quote.defaultOffer.itacoins || 0) * 100) : 0;
      rows.push({
        key: `${library.id}:${card.collectionItemId || card.printingUuid}:${index}`,
        libraryName: library.name,
        card,
        quote,
        cashUnitCents,
        itacoinUnitHundredths,
        cashTotalCents: cashUnitCents * card.quantity,
        itacoinTotalHundredths: itacoinUnitHundredths * card.quantity
      });
    });
  });

  return {
    rows: rows.sort((left, right) => {
      if (Boolean(left.quote) !== Boolean(right.quote)) return left.quote ? -1 : 1;
      return right.cashTotalCents - left.cashTotalCents || left.card.name.localeCompare(right.card.name);
    }),
    matchedRows: rows.filter(row => row.quote).length,
    unmatchedRows: rows.filter(row => !row.quote).length,
    matchedCopies: rows.filter(row => row.quote).reduce((total, row) => total + row.card.quantity, 0),
    totalCopies: rows.reduce((total, row) => total + row.card.quantity, 0),
    cashTotalCents: rows.reduce((total, row) => total + row.cashTotalCents, 0),
    itacoinTotalHundredths: rows.reduce((total, row) => total + row.itacoinTotalHundredths, 0)
  };
}
