import { describe, expect, it } from "vitest";
import { decodeTransfer, encodeTransfer, validateTransfer, type CardmarketTransfer } from "./index";

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
});
