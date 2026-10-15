import { parseCloudBuylists } from "./shoppingCloud";
import type { BuyList } from "@mtgfucker/cardmarket-protocol";
import { initializeApp, type FirebaseApp } from "firebase/app";
import { GoogleAuthProvider, getAuth, onAuthStateChanged, signInWithPopup, signOut, type User } from "firebase/auth";
import { collection, doc, getDoc, getDocs, getFirestore, serverTimestamp, setDoc, onSnapshot, runTransaction } from "firebase/firestore";
import type { CardmarketTransfer } from "@mtgfucker/cardmarket-protocol";

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID
};

export const firebaseConfigured = Object.values(firebaseConfig)
  .every(value => typeof value === "string" && value.length > 0 && !value.startsWith("pega_"));

let app: FirebaseApp | undefined;
if (firebaseConfigured) app = initializeApp(firebaseConfig);

export interface CloudCard {
  collectionItemId: string;
  name: string;
  quantity: number;
  printingUuid: string;
  mcmId: string;
  mcmMetaId: string;
  setCode: string;
  setName: string;
  mcmSetId: number | null;
  mcmSetIdExtras: number | null;
  mcmSetName: string;
  collectorNumber: string;
  finish: string;
  language: string;
  condition: string;
  price: string;
}

export interface CloudLibrary {
  id: string;
  name: string;
  cards: CloudCard[];
}

export function observeUser(listener: (user: User | null) => void): () => void {
  if (!app) {
    listener(null);
    return () => undefined;
  }
  return onAuthStateChanged(getAuth(app), listener);
}

export async function loginWithGoogle(): Promise<User> {
  if (!app) throw new Error("Primero configura la aplicación Web en Firebase.");
  const result = await signInWithPopup(getAuth(app), new GoogleAuthProvider());
  return result.user;
}

export async function logout(): Promise<void> {
  if (app) await signOut(getAuth(app));
}

export async function saveDraft(user: User, transfer: CardmarketTransfer): Promise<void> {
  if (!app) throw new Error("Firebase no está configurado.");
  const database = getFirestore(app);
  await setDoc(doc(database, "users", user.uid, "cardmarketDrafts", transfer.batchId), {
    ...transfer,
    ownerUid: user.uid,
    createdAtServer: serverTimestamp(),
    status: "draft"
  });
}

function decodeBase64(value: string): Uint8Array {
  const binary = atob(value);
  return Uint8Array.from(binary, character => character.charCodeAt(0));
}

async function gunzip(bytes: Uint8Array): Promise<string> {
  const stream = new Blob([bytes as BlobPart]).stream().pipeThrough(new DecompressionStream("gzip"));
  return new Response(stream).text();
}

async function sha256(bytes: Uint8Array): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", bytes as BufferSource);
  return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, "0")).join("");
}

export async function loadCloudLibraries(user: User): Promise<CloudLibrary[]> {
  if (!app) throw new Error("Firebase no está configurado.");
  const database = getFirestore(app);
  const heads = await getDocs(collection(database, "users", user.uid, "libraries"));
  const libraries: CloudLibrary[] = [];
  for (const head of heads.docs) {
    const activeSnapshotId = String(head.get("activeSnapshotId") || "");
    if (!activeSnapshotId) continue;
    const snapshotRef = doc(head.ref, "snapshots", activeSnapshotId);
    const metadata = await getDoc(snapshotRef);
    const count = Number(metadata.get("webChunkCount") || 0);
    if (count < 1) continue;
    const chunkDocs = await getDocs(collection(snapshotRef, "webChunks"));
    const encoded = new Map(chunkDocs.docs.map(chunk => [chunk.id, String(chunk.get("data") || "")]));
    const chunks = Array.from({ length: count }, (_, index) => {
      const value = encoded.get(String(index).padStart(5, "0"));
      if (!value) throw new Error(`Falta una parte web de la Biblio ${head.id}.`);
      return decodeBase64(value);
    });
    const total = chunks.reduce((sum, chunk) => sum + chunk.length, 0);
    const bytes = new Uint8Array(total);
    let offset = 0;
    chunks.forEach(chunk => { bytes.set(chunk, offset); offset += chunk.length; });
    const expectedHash = String(metadata.get("webSha256") || "");
    if (expectedHash && await sha256(bytes) !== expectedHash) {
      throw new Error(`La copia web de ${head.id} no superó la verificación de integridad.`);
    }
    const decoded = JSON.parse(await gunzip(bytes)) as { name?: string; cards?: CloudCard[] };
    libraries.push({
      id: head.id,
      name: String(head.get("name") || decoded.name || head.id),
      cards: Array.isArray(decoded.cards) ? decoded.cards : []
    });
  }
  return libraries;
}

export interface CloudShoppingState { enabled: boolean; uid: string | null; lists: BuyList[]; message: string; snapshotId?: string; hash?: string }
/** Never cache another account's lists or allow a late response after logout to repaint them. */
export function observeCloudShopping(listener: (state: CloudShoppingState) => void): () => void {
  let epoch = 0;
  let stopHead: (() => void) | undefined;
  const stopUser = observeUser(user => {
    const account = ++epoch; stopHead?.(); stopHead = undefined;
    listener({enabled: false, uid: user?.uid ?? null, lists: [], message: user ? "Comprobando Compras Premium…" : "Inicia sesión con tu cuenta Premium para ver Compras."});
    if (!user || !app) return;
    const database = getFirestore(app);
    const ref = doc(database, "users", user.uid, "shopping", "state");
    let revision = 0;
    stopHead = onSnapshot(ref, async head => {
      if (account !== epoch) return;
      const request = ++revision;
      const current = () => account === epoch && request === revision;
      if (head.get("premiumEnabled") !== true) {
        listener({enabled:false, uid:user.uid, lists:[], message:"Compras requiere Premium. Actívalo y sincroniza desde Android; las listas guardadas se conservan."}); return;
      }
      try {
        const id = String(head.get("activeSnapshotId") || "");
        let lists: BuyList[] = [];
        if (id) {
          const snapshot = doc(ref, "snapshots", id);
          const metadata = await getDoc(snapshot);
          const count = Number(metadata.get("chunkCount"));
          if (!Number.isInteger(count) || count < 1 || count > 160 || metadata.get("schemaVersion") !== 1) throw Error("Copia de Compras incompleta");
          const parts = await getDocs(collection(snapshot, "chunks"));
          const indexed = new Map(parts.docs.map(part => [part.id, String(part.get("data") || "")]));
          const chunks = Array.from({length:count}, (_, i) => {
            const value = indexed.get(String(i).padStart(5,"0"));
            if (!value) throw Error("Falta una parte del histórico de Compras");
            return decodeBase64(value);
          });
          const size = chunks.reduce((n,b) => n+b.length,0);
          if (size > 64*1024*1024) throw Error("Copia de Compras demasiado grande");
          const bytes = new Uint8Array(size); let offset=0;
          for (const chunk of chunks) { bytes.set(chunk,offset);offset+=chunk.length; }
          const reader = new Blob([bytes]).stream().pipeThrough(new DecompressionStream("gzip")).getReader();
          const decoded: Uint8Array[] = []; let length=0;
          try { while (true) { const {done,value}=await reader.read(); if(done)break;length+=value.length;if(length>64*1024*1024)throw Error("Histórico demasiado grande");decoded.push(value); } }
          finally { await reader.cancel(); }
          const plain = new Uint8Array(length); offset=0;
          for (const chunk of decoded) { plain.set(chunk,offset);offset+=chunk.length; }
          if (await sha256(plain)!==String(head.get("sha256") || "") || metadata.get("sha256")!==head.get("sha256")) throw Error("La copia de Compras no supera la verificación de integridad");
          lists=parseCloudBuylists(new TextDecoder().decode(plain));
        }
        if(current())listener({enabled:true,uid:user.uid,lists,snapshotId:id,hash:String(head.get("sha256") || ""),message:"Las mismas listas e históricos en Android y web. Los cambios se guardan en tu cuenta, sin crear copias."});
      } catch(error) {
        if(current())listener({enabled:false,uid:user.uid,lists:[],message:`No se pudo cargar Compras: ${String(error)}`});
      }
    }, error => {
      ++revision;
      if(account===epoch)listener({enabled:false,uid:user.uid,lists:[],message:`No se pudo comprobar Premium: ${String(error)}`});
    });
  });
  return () => { ++epoch;stopHead?.();stopUser(); };
}


/** Publish a complete snapshot only if the revision edited is still current. Never last-writer-wins. */
export async function saveCloudShopping(base: CloudShoppingState, next: BuyList[]): Promise<CloudShoppingState> {
  if (!app || !base.enabled || !base.uid || getAuth(app).currentUser?.uid !== base.uid) throw Error("Inicia sesión con tu cuenta Premium.");
  const database=getFirestore(app), uid=base.uid;
  const allowed=()=>{if(getAuth(app!).currentUser?.uid!==uid)throw Error("La cuenta ha cambiado. No se guardaron los cambios.");};
  const ref=doc(database,"users",uid,"shopping","state");
  const validateHead=(head: {get(field:string):unknown})=>{
    allowed();
    if(head.get("premiumEnabled")!==true)throw Error("Compras requiere Premium.");
    if(String(head.get("activeSnapshotId") || "")!==(base.snapshotId ?? "") || String(head.get("sha256") || "")!==(base.hash ?? ""))
      throw Error("La lista cambió en otro dispositivo. No se sobrescribió nada. Revisa la versión actual y vuelve a aplicar tu cambio.");
  };
  validateHead(await getDoc(ref));
  const text=JSON.stringify({version:1,lists:[...next].sort((a,b)=>a.id.localeCompare(b.id))});
  const validated=parseCloudBuylists(text), plain=new TextEncoder().encode(text), hash=await sha256(plain);
  const compressed=new Uint8Array(await new Response(new Blob([plain]).stream().pipeThrough(new CompressionStream("gzip"))).arrayBuffer());
  const snapshotId=crypto.randomUUID(), snapshot=doc(ref,"snapshots",snapshotId), chunkSize=450000;
  const count=Math.ceil(compressed.length/chunkSize);
  for(let i=0;i<count;i++) {
    allowed();const bytes=compressed.subarray(i*chunkSize,(i+1)*chunkSize);
    let binary="";for(let offset=0;offset<bytes.length;offset+=8192)binary+=String.fromCharCode(...bytes.subarray(offset,offset+8192));
    await setDoc(doc(snapshot,"chunks",String(i).padStart(5,"0")),{data:btoa(binary)});
  }
  await setDoc(snapshot,{schemaVersion:1,chunkCount:count,sha256:hash,byteCount:plain.length});
  await runTransaction(database,async transaction=>{
    const current=await transaction.get(ref);validateHead(current);
    transaction.set(ref,{activeSnapshotId:snapshotId,sha256:hash,premiumEnabled:true,updatedAt:serverTimestamp()});
  });
  allowed();
  return {...base,lists:validated,snapshotId,hash};
}
