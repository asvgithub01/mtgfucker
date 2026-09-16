import { initializeApp, type FirebaseApp } from "firebase/app";
import { GoogleAuthProvider, getAuth, onAuthStateChanged, signInWithPopup, signOut, type User } from "firebase/auth";
import { collection, doc, getDoc, getDocs, getFirestore, serverTimestamp, setDoc } from "firebase/firestore";
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
