import { describe, expect, it } from "vitest";
import {
  buildTransferBatches,
  buildCardmarketCatalogIndex,
  decodeTransfer,
  encodeTransfer,
  priceWithMultiplier,
  validateTransfer,
  type CardmarketBatchCandidate,
  type CardmarketTransfer
} from "./index";

const sample: CardmarketTransfer = {
  version: 1,
  batchId: "batch-1",
  createdAt: "2026-09-16T10:00:00.000Z",
  setCode: "LRW",
  setName: "Lorwyn",
  mcmSetIds: [84],
  items: [{
    mcmId: "17812",
    name: "Jace Beleren",
    quantity: 1,
    priceCents: 250,
    language: "es",
    condition: "near_mint",
    finish: "nonfoil"
  }]
};

describe("Cardmarket transfer protocol", () => {
  it("round-trips unicode card data", () => {
    const code = encodeTransfer({...sample, items: [{...sample.items[0], name: "Ángel japonés"}]});
    expect(decodeTransfer(code).items[0].name).toBe("Ángel japonés");
  });

  it("rejects duplicate product variants until row duplication is supported", () => {
    expect(() => validateTransfer({...sample, items: [sample.items[0], sample.items[0]]}))
      .toThrow(/dos variantes/);
  });

  it("rejects zero prices", () => {
    expect(() => validateTransfer({...sample, items: [{...sample.items[0], priceCents: 0}]}))
      .toThrow(/Precio/);
  });

  it("matches the English-name page boundary observed in Revised BulkListing", () => {
    const beforeFork = Array.from({ length: 99 }, (_, index) => ({
      mcmId: String(10_000 + index), name: `A ${String(index).padStart(3, "0")}`,
      collectorNumber: String(index + 1)
    }));
    const afterGreenWard = Array.from({ length: 204 }, (_, index) => ({
      mcmId: String(20_000 + index), name: `Z ${String(index).padStart(3, "0")}`,
      collectorNumber: String(index + 103)
    }));
    const catalog = buildCardmarketCatalogIndex([
      ...afterGreenWard,
      { mcmId: "5732", name: "Green Ward", collectorNumber: "20" },
      { mcmId: "5548", name: "Frozen Shade", collectorNumber: "112" },
      { mcmId: "5680", name: "Fork", collectorNumber: "153" },
      ...beforeFork
    ]);
    expect(catalog.get("5680")).toMatchObject({
      page: 1, position: 100, size: 306, pageLastName: "Fork"
    });
    expect(catalog.get("5548")).toMatchObject({ page: 2, position: 101, pageFirstName: "Frozen Shade" });
    expect(catalog.get("5732")).toMatchObject({ page: 2, position: 102 });
  });

  it("splits 150 rows using the 100-product windows of the full catalog", () => {
    const candidates: CardmarketBatchCandidate[] = Array.from({ length: 150 }, (_, index) => ({
      ...sample.items[0],
      mcmId: String(20_000 + index),
      name: `Card ${index + 1}`,
      setCode: "LRW",
      setName: "Lorwyn",
      mcmSetIds: [84],
      catalogPage: Math.floor(index / 100) + 1,
      catalogPosition: index + 1,
      catalogSize: 150,
      catalogFirstName: index < 100 ? "Card 1" : "Card 101",
      catalogLastName: index < 100 ? "Card 100" : "Card 150"
    }));
    const batches = buildTransferBatches(candidates, sample.createdAt);
    expect(batches.map(batch => batch.items.length)).toEqual([100, 50]);
    expect(batches.map(batch => batch.catalogPage)).toEqual([1, 2]);
  });

  it("does not mix sparse owned cards from different full-catalog pages", () => {
    const positions = [2, 80, 101, 199, 201, 306];
    const candidates: CardmarketBatchCandidate[] = positions.map(position => ({
      ...sample.items[0],
      mcmId: String(20_000 + position),
      name: `Card ${position}`,
      collectorNumber: String(position),
      setCode: "3ED",
      setName: "Revised Edition",
      mcmSetIds: [6],
      catalogPage: Math.floor((position - 1) / 100) + 1,
      catalogPosition: position,
      catalogSize: 306,
      catalogFirstName: `First page ${Math.floor((position - 1) / 100) + 1}`,
      catalogLastName: `Last page ${Math.floor((position - 1) / 100) + 1}`
    }));
    const batches = buildTransferBatches(candidates, sample.createdAt);
    expect(batches.map(batch => [batch.catalogPage, batch.items.map(item => item.collectorNumber)]))
      .toEqual([[1, ["2", "80"]], [2, ["101", "199"]], [3, ["201"]], [4, ["306"]]]);
  });

  it("keeps mixed expansions on their exact Cardmarket pages", () => {
    const candidates: CardmarketBatchCandidate[] = [84, 15].flatMap((setId, setIndex) =>
      Array.from({ length: 75 }, (_, index) => ({
        ...sample.items[0],
        mcmId: String(30_000 + setIndex * 100 + index),
        name: `Card ${setIndex}-${index}`,
        setCode: setId === 84 ? "LRW" : "ALL",
        setName: setId === 84 ? "Lorwyn" : "Alliances",
        mcmSetIds: [setId],
        catalogPage: 1,
        catalogPosition: index + 1,
        catalogSize: 75
      }))
    );
    const batches = buildTransferBatches(candidates, sample.createdAt);
    expect(batches.map(batch => [batch.mcmSetIds[0], batch.items.length])).toEqual([[84, 75], [15, 75]]);
  });

  it("puts repeated products into separate submissions", () => {
    const candidate: CardmarketBatchCandidate = {
      ...sample.items[0], setCode: "LRW", setName: "Lorwyn", mcmSetIds: [84],
      catalogPage: 1, catalogPosition: 1, catalogSize: 1
    };
    const batches = buildTransferBatches([
      candidate,
      { ...candidate, language: "en", condition: "light_played" }
    ], sample.createdAt);
    expect(batches).toHaveLength(2);
  });

  it("applies x10 and x100 before rounding to Cardmarket cents", () => {
    expect(priceWithMultiplier(3.345, 10)).toBe(3345);
    expect(priceWithMultiplier(3.345, 100)).toBe(33450);
  });
});
