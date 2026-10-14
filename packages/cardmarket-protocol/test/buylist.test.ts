import { describe, it, expect } from "vitest";
import { parseBuylist, buySnapshot, wantsBatches, buyComposition, type BuyList, type BuyEntry } from "../src/buylist";
const entry:BuyEntry={id:"a",uuid:"00000000-0000-0000-0000-000000000001",name:"Card",set:"FRA",setName:"Final Fantasy",number:"1",finish:"nonfoil",quantity:2,image:"",mcmId:"123",language:"",condition:"NM"};
const list:BuyList={format:"mtgfucker-buylist",version:1,id:"list",name:"Missing",entries:[entry],history:[]};
describe("buylist interchange and prices",()=>{
 it("round trips without changing printing or finish",()=>expect(parseBuylist(JSON.stringify(list))).toEqual(list));
 it("rejects future versions, wrong condition and duplicate identities",()=>{
  for(const bad of [{...list,version:2},{...list,entries:[{...entry,condition:"EX"}]},{...list,entries:[entry,{...entry,id:"b"}]}])expect(()=>parseBuylist(JSON.stringify(bad))).toThrow();
 });
 it("rejects missing UUID, negative quantity and unsafe image",()=>{
  for(const e of [{...entry,uuid:""},{...entry,quantity:-1},{...entry,image:"javascript:alert(1)"}])expect(()=>parseBuylist(JSON.stringify({...list,entries:[e]}))).toThrow();
 });
 it("uses only MKM EUR exact finish and newest date",()=>{
  const data={[entry.uuid]:{paper:{cardmarket:{currency:"EUR",retail:{normal:{"2026-10-13":5,"2026-10-14":4},foil:{"2026-10-14":99}}}}}};
  expect(buySnapshot(list,data,1).quotes).toEqual([{entryId:"a",amount:8,date:"2026-10-14"}]);
 });
 it("does not substitute foil, USD, another provider or stale former values",()=>{
  const data={[entry.uuid]:{paper:{cardmarket:{currency:"USD",retail:{normal:{"2026-10-14":4}}},tcgplayer:{currency:"EUR",retail:{normal:{"2026-10-14":1}}}}}};
  expect(buySnapshot(list,data,1).quotes[0].amount).toBeNull();expect(buySnapshot(list,{},2).quotes[0].amount).toBeNull();
 });
 it("missing values never become free cards",()=>expect(buySnapshot(list,{},1).quotes[0]).toEqual({entryId:"a",amount:null,date:""}));
 it("splits into max 150 entries and preserves expansion",()=>{const many={...list,entries:Array.from({length:301},(_,i)=>({...entry,id:String(i)}))};const batches=wantsBatches(many);expect(batches.map(b=>b.split("\n").length)).toEqual([150,150,1]);expect(batches[0].split("\n")[0]).toBe("2 Card (Final Fantasy)");});
 it("same composition as Android, reordered entries invariant",()=>{expect(buyComposition([entry])).toBe(`a:${entry.uuid}|nonfoil||NM:2`);expect(buyComposition([entry,{...entry,id:"b"}])).toBe(buyComposition([{...entry,id:"b"},entry]));});
});
