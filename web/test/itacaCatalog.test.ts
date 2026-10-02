import { describe, expect, it } from "vitest";
import { valueLibrariesWithItaca, type ItacaCatalog } from "../src/itacaCatalog";
import type { CloudCard } from "../src/firebase";

const card = (mcmId: string, quantity = 1) => ({ mcmId, quantity, name: "Carta", collectionItemId: mcmId } as CloudCard);
const catalog = {
  expansions: [{ cards: [
    { cardmarketId: "123", defaultOffer: { cashEur: 1.23, itacoins: 1.48 } },
    { cardmarketId: "456", defaultOffer: { cashEur: null, itacoins: null } },
    { cardmarketId: "789", defaultOffer: { cashEur: 0, itacoins: 0 } }
  ] }]
} as ItacaCatalog;

describe("valoración Itaca", () => {
  it("cruza por ID exacto y multiplica céntimos por copias", () => {
    const result = valueLibrariesWithItaca([{ id: "a", name: "Biblio", cards: [card("123", 3), card("999", 2)] }], catalog);
    expect(result).toMatchObject({ cashTotalCents: 369, itacoinTotalHundredths: 444, matchedRows: 1, unmatchedRows: 1, matchedCopies: 3, totalCopies: 5 });
  });
  it("respeta la Biblio seleccionada", () => {
    const libraries = ["a", "b"].map(id => ({ id, name: id, cards: [card("123")] }));
    expect(valueLibrariesWithItaca(libraries, catalog, "b").rows.map(row => row.libraryName)).toEqual(["b"]);
  });
  it("no empareja por nombre ni acepta IDs inválidos u ofertas nulas", () => {
    const result = valueLibrariesWithItaca([{ id: "a", name: "A", cards: [card(""), card("123x"), card("456")] }], catalog);
    expect(result.matchedRows).toBe(0);
    expect(result.cashTotalCents).toBe(0);
  });
  it("conserva ofertas cero e ignora cantidades no positivas o no finitas", () => {
    const result = valueLibrariesWithItaca([{ id: "a", name: "A", cards: [card("789"), card("123", 0), card("123", -1), card("123", NaN)] }], catalog);
    expect(result).toMatchObject({ matchedRows: 1, totalCopies: 1, cashTotalCents: 0 });
  });
});
