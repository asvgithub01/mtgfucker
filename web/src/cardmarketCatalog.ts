import {
  buildCardmarketCatalogIndex,
  type CardmarketCatalogPlacement
} from "@mtgfucker/cardmarket-protocol";

export type { CardmarketCatalogPlacement } from "@mtgfucker/cardmarket-protocol";

interface MtgJsonCard {
  name?: string;
  number?: string;
  availability?: string[];
  identifiers?: { mcmId?: string };
}

interface MtgJsonSetResponse {
  data?: { cards?: MtgJsonCard[] };
}

const catalogCache = new Map<string, Promise<Map<string, CardmarketCatalogPlacement>>>();

function buildCatalogIndex(cards: MtgJsonCard[]): Map<string, CardmarketCatalogPlacement> {
  return buildCardmarketCatalogIndex(cards
    .filter(card => card.identifiers?.mcmId && (!card.availability || card.availability.includes("paper")))
    .map(card => ({
      mcmId: card.identifiers!.mcmId!,
      name: card.name || "",
      collectorNumber: card.number || ""
    })));
}

export function loadCardmarketCatalog(
  setCode: string
): Promise<Map<string, CardmarketCatalogPlacement>> {
  const normalizedCode = setCode.trim().toUpperCase();
  let request = catalogCache.get(normalizedCode);
  if (!request) {
    request = fetch(`https://mtgjson.com/api/v5/${encodeURIComponent(normalizedCode)}.json`)
      .then(response => {
        if (!response.ok) throw new Error(`MTGJSON devolvió HTTP ${response.status} para ${normalizedCode}.`);
        return response.json() as Promise<MtgJsonSetResponse>;
      })
      .then(payload => {
        const index = buildCatalogIndex(payload.data?.cards || []);
        if (index.size === 0) throw new Error(`El catálogo ${normalizedCode} no contiene productos de Cardmarket.`);
        return index;
      })
      .catch(error => {
        catalogCache.delete(normalizedCode);
        throw error;
      });
    catalogCache.set(normalizedCode, request);
  }
  return request;
}
