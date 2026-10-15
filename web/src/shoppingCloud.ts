import { parseBuylist, buyComposition, type BuyList, type BuyEntry } from "@mtgfucker/cardmarket-protocol";

export function parseCloudBuylists(text: string): BuyList[] {
  if (new TextEncoder().encode(text).length > 64 * 1024 * 1024) throw Error("Copia de Compras demasiado grande");
  const root = JSON.parse(text) as {version?: number; lists?: unknown[]};
  if (root.version !== 1 || !Array.isArray(root.lists) || root.lists.length > 10000) throw Error("Copia de Compras incompatible");
  const lists = root.lists.map(l => parseBuylist(JSON.stringify(l)));
  if (new Set(lists.map(l => l.id)).size !== lists.length) throw Error("Identificadores de buylist duplicados");
  return lists;
}


/** Keep old observations intact; add one composition snapshot for the edited entries. */
export function editBuylistEntries(list: BuyList, entries: BuyEntry[], at=Date.now()): BuyList {
  if(buyComposition(list.entries)===buyComposition(entries))return {...list,entries};
  const previous=list.history.at(-1);
  const valid=previous?.composition===buyComposition(list.entries);
  const snapshot={at,composition:buyComposition(entries),quotes:entries.map(entry=>{
    const old=list.entries.find(e=>e.id===entry.id && e.uuid===entry.uuid && e.finish===entry.finish && e.language===entry.language && e.condition===entry.condition);
    const quote=valid && old ? previous?.quotes.find(q=>q.entryId===old.id):undefined;
    return {entryId:entry.id,amount:quote?.amount!=null && old ? quote.amount/old.quantity*entry.quantity:null,date:quote?.date ?? ""};
  })};
  return parseBuylist(JSON.stringify({...list,entries,history:[...list.history,snapshot]}));
}

/** Import is idempotent. Same ID never becomes a second list or silently replaces another edit. */
export function importSharedBuylist(lists: BuyList[], incoming: BuyList): BuyList[] {
  const stable=(value:unknown):unknown=>Array.isArray(value)?value.map(stable):value && typeof value==="object"?Object.fromEntries(Object.entries(value).sort(([a],[b])=>a.localeCompare(b)).map(([key,v])=>[key,stable(v)])):value;
  const key=(l:BuyList)=>JSON.stringify(stable({name:l.name,entries:l.entries,history:l.history}));
  const same=lists.find(l=>l.id===incoming.id);
  if(same) {
    if(key(same)!==key(incoming))throw Error("Ya existe esta lista con cambios distintos. No se ha sobrescrito ni duplicado.");
    return lists;
  }
  if(lists.some(l=>key(l)===key(incoming)))return lists;
  if(lists.some(l=>l.name===incoming.name && l.entries.some(e=>incoming.entries.some(other=>other.id===e.id))))
    throw Error("Esta importación parece una copia editada de una lista existente. No se ha duplicado; revisa la lista sincronizada.");
  return [...lists,incoming];
}
