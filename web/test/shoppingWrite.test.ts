import {beforeEach,describe,it,expect,vi} from "vitest";
const db=vi.hoisted(()=>({uid:"u1",head:{activeSnapshotId:"old",sha256:"oldhash",premiumEnabled:true} as Record<string,unknown>,docs:new Map<string,unknown>(),race:false,fail:false,switchAccount:false}));
vi.mock("firebase/app",()=>({initializeApp:()=>({})}));
vi.mock("firebase/auth",()=>({getAuth:()=>({currentUser:{uid:db.uid}}),GoogleAuthProvider:class {},onAuthStateChanged:vi.fn(),signInWithPopup:vi.fn(),signOut:vi.fn()}));
vi.mock("firebase/firestore",()=>({
 getFirestore:()=>({}),doc:(...parts:unknown[])=>({path:parts.map(p=>typeof p==="string"?p:(p as {path?:string}).path ?? "").filter(Boolean).join("/")}),
 collection:vi.fn(),getDocs:vi.fn(),onSnapshot:vi.fn(),serverTimestamp:()=>"server-time",
 getDoc:async()=>({get:(key:string)=>db.head[key]}),
 setDoc:async(ref:{path:string},value:unknown)=>{if(db.fail)throw Error("network");db.docs.set(ref.path,value);if(db.switchAccount)db.uid="other";},
 runTransaction:async(_:unknown,action:(tx:unknown)=>Promise<void>)=>{
  if(db.race)db.head={...db.head,activeSnapshotId:"newer"};
  await action({get:async()=>({get:(key:string)=>db.head[key]}),set:(_:unknown,value:Record<string,unknown>)=>{db.head=value;}});
 }
}));
for(const field of ["API_KEY","AUTH_DOMAIN","PROJECT_ID","STORAGE_BUCKET","MESSAGING_SENDER_ID","APP_ID"])vi.stubEnv("VITE_FIREBASE_"+field,"test");
const {saveCloudShopping}=await import("../src/firebase");
const list={format:"mtgfucker-buylist" as const,version:1 as const,id:"same-id",name:"Edited",entries:[],history:[{at:1,composition:"",quotes:[]},{at:2,composition:"",quotes:[]}]};
const base={enabled:true,uid:"u1",lists:[list],message:"",snapshotId:"old",hash:"oldhash"};
beforeEach(()=>{db.uid="u1";db.head={activeSnapshotId:"old",sha256:"oldhash",premiumEnabled:true};db.docs.clear();db.race=false;db.fail=false;db.switchAccount=false;});
describe("shared cloud writer",()=>{
 it("publishes complete gzip snapshot with same list IDs and all history",async()=>{
  const saved=await saveCloudShopping(base,[list]);expect(saved.lists).toEqual([list]);expect(saved.snapshotId).toBe(db.head.activeSnapshotId);
  const chunk=[...db.docs.entries()].find(([path])=>path.endsWith("/chunks/00000"))![1] as {data:string};
  const bytes=Uint8Array.from(atob(chunk.data),c=>c.charCodeAt(0));
  const text=await new Response(new Blob([bytes]).stream().pipeThrough(new DecompressionStream("gzip"))).text();
  expect(JSON.parse(text).lists).toEqual([list]);expect(db.head.sha256).toHaveLength(64);
 });
 it("rejects stale edits before uploading anything",async()=>{db.head.activeSnapshotId="other";await expect(saveCloudShopping(base,[list])).rejects.toThrow("otro dispositivo");expect(db.docs.size).toBe(0);});
 it("rejects a competing commit without replacing its head",async()=>{db.race=true;await expect(saveCloudShopping(base,[list])).rejects.toThrow("otro dispositivo");expect(db.head.activeSnapshotId).toBe("newer");});
 it("does not publish partial data after network failure",async()=>{db.fail=true;await expect(saveCloudShopping(base,[list])).rejects.toThrow();expect(db.head.activeSnapshotId).toBe("old");});
 it("does not publish after account switch",async()=>{db.switchAccount=true;await expect(saveCloudShopping(base,[list])).rejects.toThrow();expect(db.head.activeSnapshotId).toBe("old");});
 it("rejects non-premium and duplicate IDs",async()=>{db.head.premiumEnabled=false;await expect(saveCloudShopping(base,[list])).rejects.toThrow("Premium");db.head.premiumEnabled=true;await expect(saveCloudShopping(base,[list,list])).rejects.toThrow("duplicados");});
 it("synchronizes an intentional empty list set",async()=>{expect((await saveCloudShopping(base,[])).lists).toEqual([]);});
});
