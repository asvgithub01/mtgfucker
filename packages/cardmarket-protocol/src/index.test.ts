import { describe, expect, it } from "vitest";
import {
  buildTransferBatches,
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

  it("splits 150 rows from one expansion into 100 and 50", () => {
    const candidates: CardmarketBatchCandidate[] = Array.from({ length: 150 }, (_, index) => ({
      ...sample.items[0],
      mcmId: String(20_000 + index),
      name: `Card ${index + 1}`,
      setCode: "LRW",
      setName: "Lorwyn",
      mcmSetIds: [84]
    }));
    const batches = buildTransferBatches(candidates, sample.createdAt);
    expect(batches.map(batch => batch.items.length)).toEqual([100, 50]);
  });

  it("keeps mixed expansions on their exact Cardmarket pages", () => {
    const candidates: CardmarketBatchCandidate[] = [84, 15].flatMap((setId, setIndex) =>
      Array.from({ length: 75 }, (_, index) => ({
        ...sample.items[0],
        mcmId: String(30_000 + setIndex * 100 + index),
        name: `Card ${setIndex}-${index}`,
        setCode: setId === 84 ? "LRW" : "ALL",
        setName: setId === 84 ? "Lorwyn" : "Alliances",
        mcmSetIds: [setId]
      }))
    );
    const batches = buildTransferBatches(candidates, sample.createdAt);
    expect(batches.map(batch => [batch.mcmSetIds[0], batch.items.length])).toEqual([[84, 75], [15, 75]]);
  });

  it("puts repeated products into separate submissions", () => {
    const candidate: CardmarketBatchCandidate = {
      ...sample.items[0], setCode: "LRW", setName: "Lorwyn", mcmSetIds: [84]
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
