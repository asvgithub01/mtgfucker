import { describe, it, expect } from "vitest";
import { parseCloudBuylists, editBuylistEntries, importSharedBuylist } from "../src/shoppingCloud";
const list={format:"mtgfucker-buylist",version:1,id:"list-1",name:"Test",entries:[],history:[{at:1,composition:"",quotes:[]},{at:2,composition:"",quotes:[]}]};
describe("cloud shopping envelope",()=>{
 it("preserves IDs and unchanged historical snapshots",()=>expect(parseCloudBuylists(JSON.stringify({version:1,lists:[list]}))).toEqual([list]));
 it("keeps an empty snapshot as an intentional deletion",()=>expect(parseCloudBuylists('{"version":1,"lists":[]}')).toEqual([]));
 it("rejects unsupported or missing envelope",()=>{expect(()=>parseCloudBuylists('{"version":2,"lists":[]}')).toThrow();expect(()=>parseCloudBuylists('{}')).toThrow();});
 it("rejects duplicate identities",()=>expect(()=>parseCloudBuylists(JSON.stringify({version:1,lists:[list,list]}))).toThrow());
 it("rejects malformed history rather than dropping it",()=>expect(()=>parseCloudBuylists(JSON.stringify({version:1,lists:[{...list,history:[{at:-1,quotes:[],composition:""}]}]}))).toThrow());
});

const entry={id:"entry-1",uuid:"12345678-1234-1234-1234-123456789abc",name:"Card",set:"FRA",number:"1",finish:"nonfoil" as const,image:"",mcmId:"",quantity:2,language:"en",condition:"NM" as const};
import {buyComposition,type BuyList} from "@mtgfucker/cardmarket-protocol";
const original:BuyList={...list,format:"mtgfucker-buylist",version:1,entries:[entry],history:[{at:1,composition:buyComposition([entry]),quotes:[{entryId:entry.id,amount:10,date:"2026-10-15"}]}]};
describe("shared edits",()=>{
 it("keeps list/entry IDs and every historical observation when quantity changes",()=>{
  const edited=editBuylistEntries(original,[{...entry,quantity:3}],2);
  expect(edited.id).toBe(original.id);expect(edited.entries[0].id).toBe(entry.id);
  expect(edited.history[0]).toEqual(original.history[0]);expect(edited.history[1].quotes[0].amount).toBe(15);
  expect(original.entries[0].quantity).toBe(2);
 });
 it("does not reuse prices from another finish",()=>expect(editBuylistEntries(original,[{...entry,finish:"foil"}],2).history[1].quotes[0].amount).toBeNull());
 it("retains removed-card history and records empty composition",()=>{
  const edited=editBuylistEntries(original,[],2);expect(edited.history[0]).toEqual(original.history[0]);expect(edited.history[1].quotes).toEqual([]);
 });
 it("no-op edits do not create artificial observations",()=>expect(editBuylistEntries(original,[entry]).history).toEqual(original.history));
 it("reimport never duplicates an ID or identical copied list",()=>{
  expect(importSharedBuylist([original],original)).toHaveLength(1);
  expect(importSharedBuylist([original],{...original,id:"old-local-copy"})).toHaveLength(1);
 });
 it("rejects diverged imports instead of losing history or duplicating",()=>{
  expect(()=>importSharedBuylist([original],{...original,history:[]})).toThrow();
  expect(()=>importSharedBuylist([original],{...original,id:"old-local-copy",history:[]})).toThrow();
 });
});
