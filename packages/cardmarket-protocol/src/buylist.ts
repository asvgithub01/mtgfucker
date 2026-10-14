export interface BuyEntry {
  id: string; uuid: string; name: string; set: string; number: string; finish: "nonfoil" | "foil" | "etched";
  image: string; mcmId: string; quantity: number; language: string; condition: "NM"; setName?: string;
}
export interface BuyQuote { entryId: string; amount: number | null; date: string }
export interface BuySnapshot { at: number; composition: string; quotes: BuyQuote[] }
export interface BuyList { format: "mtgfucker-buylist"; version: 1; id: string; name: string; entries: BuyEntry[]; history: BuySnapshot[] }
function check(ok: unknown): asserts ok { if (!ok) throw new Error("Buylist inválida o incompatible. No se ha importado."); }
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export function parseBuylist(text: string): BuyList {
  check(text.length <= 8 * 1024 * 1024);
  const l = JSON.parse(text) as BuyList;
  check(l && l.format === "mtgfucker-buylist" && l.version === 1 && typeof l.id === "string");
  check(typeof l.name === "string" && l.name.trim() && l.name.length <= 120);
  check(Array.isArray(l.entries) && l.entries.length <= 10000 && Array.isArray(l.history) && l.history.length <= 10000);
  const ids = new Set<string>(), keys = new Set<string>();
  for (const e of l.entries) {
    check(e && typeof e.id === "string" && e.id && !ids.has(e.id)); ids.add(e.id);
    check(typeof e.uuid === "string" && uuid.test(e.uuid));
    check(typeof e.name === "string" && e.name.trim() && e.name.length <= 500 && !/[\r\n]/.test(e.name));
    check(typeof e.set === "string" && typeof e.number === "string" && typeof e.language === "string");
    check(["nonfoil", "foil", "etched"].includes(e.finish) && e.condition === "NM");
    check(Number.isInteger(e.quantity) && e.quantity > 0 && e.quantity <= 999);
    check(typeof e.mcmId === "string" && /^\d*$/.test(e.mcmId));
    check(typeof e.image === "string" && (!e.image || e.image.startsWith("https://")));
    check(e.setName === undefined || typeof e.setName === "string" && e.setName.length <= 300 && !/[\r\n]/.test(e.setName));
    const key = `${e.uuid}|${e.finish}|${e.language}|${e.condition}`; check(!keys.has(key)); keys.add(key);
  }
  for (const s of l.history) {
    check(Number.isFinite(s.at) && s.at >= 0 && typeof s.composition === "string" && Array.isArray(s.quotes) && s.quotes.length <= 10000);
    for (const q of s.quotes) check(typeof q.entryId === "string" && typeof q.date === "string" && (q.amount === null || Number.isFinite(q.amount) && q.amount >= 0));
  }
  return l;
}
export function buyComposition(entries: BuyEntry[]): string {
  return [...entries].sort((a,b) => a.id < b.id ? -1 : a.id > b.id ? 1 : 0).map(e => `${e.id}:${e.uuid}|${e.finish}|${e.language}|${e.condition}:${e.quantity}`).join(";");
}
/** Amounts are line totals in EUR, always MKM retail references, never seller offers. */
export function buySnapshot(list: BuyList, data: Record<string, unknown>, at: number): BuySnapshot {
  return { at, composition: buyComposition(list.entries), quotes: list.entries.map(e => {
    const record = data[e.uuid] as { paper?: { cardmarket?: { currency?: string; retail?: Record<string, Record<string, number>> } } } | undefined;
    const mkm = record?.paper?.cardmarket;
    const values = mkm?.currency === "EUR" ? mkm.retail?.[e.finish === "nonfoil" ? "normal" : e.finish] : undefined;
    const date = Object.keys(values ?? {}).filter(d => /^\d{4}-\d{2}-\d{2}$/.test(d) && typeof values![d] === "number" && Number.isFinite(values![d]) && values![d] >= 0).sort().at(-1);
    return { entryId: e.id, amount: date ? values![date] * e.quantity : null, date: date ?? "" };
  }) };
}
/** Official decklist text can specify expansion, but cannot encode NM/language/finish/collector number. */
export function wantsBatches(list: BuyList): string[] {
  const result: string[] = [];
  for (let i = 0; i < list.entries.length; i += 150) result.push(list.entries.slice(i,i+150).map(e =>
    `${e.quantity} ${e.name}${e.setName ? ` (${e.setName})` : ""}`).join("\n"));
  return result;
}
