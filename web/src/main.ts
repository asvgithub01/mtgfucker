import "./styles.css";
import {
  CARDMARKET_CONDITIONS,
  CARDMARKET_LANGUAGES,
  encodeTransfer,
  newBatchId,
  type CardFinish,
  type CardmarketCondition,
  type CardmarketLanguage,
  type CardmarketTransfer
} from "@mtgfucker/cardmarket-protocol";
import {
  firebaseConfigured,
  loadCloudLibraries,
  loginWithGoogle,
  logout,
  observeUser,
  saveDraft,
  type CloudCard
} from "./firebase";
import type { User } from "firebase/auth";

const app = document.querySelector<HTMLDivElement>("#app");
if (!app) throw new Error("No se pudo iniciar la web.");

app.innerHTML = `
  <header class="hero">
    <div class="brand"><span class="brand-mark">M</span><span>MTGFucker</span></div>
    <div class="hero-copy">
      <p class="eyebrow">Cardmarket Companion</p>
      <h1>De tu colección a Cardmarket,<br><em>sin publicar a ciegas.</em></h1>
      <p>Prepara una carta, crea un código y deja que la extensión rellene el formulario exacto.</p>
    </div>
    <div class="steps"><span class="active">01 · Preparar</span><span>02 · Revisar</span><span>03 · Publicar tú</span></div>
  </header>
  <main>
    <section class="account panel">
      <div>
        <p class="eyebrow">Cuenta Premium</p>
        <h2 id="accountTitle">Modo de prueba local</h2>
        <p id="accountText">Puedes probar una carta sin Firebase. El login enlazará después esta web con la misma cuenta de Android.</p>
      </div>
      <button id="authButton" class="secondary">Entrar con Google</button>
    </section>

    <section class="workspace">
      <form id="listingForm" class="panel listing-form">
        <div class="section-heading">
          <div><p class="eyebrow">Prueba controlada</p><h2>Una carta</h2></div>
          <span class="safety">Nunca publicamos</span>
        </div>
        <div id="cloudPicker" class="cloud-picker" hidden>
          <div><p class="eyebrow">Desde Android</p><strong>Elige una carta sincronizada</strong></div>
          <select id="cloudCard" aria-label="Carta de la Biblio"><option value="">Cargando Biblio…</option></select>
          <p id="cloudStatus" class="status"></p>
        </div>
        <div class="grid two">
          <label>Nombre de la carta<input name="name" required placeholder="Jace Beleren"></label>
          <label>Cardmarket Product ID<input name="mcmId" required inputmode="numeric" pattern="[0-9]+" placeholder="17812"></label>
          <label>Código de edición<input name="setCode" required placeholder="LRW" maxlength="12"></label>
          <label>Nombre de edición<input name="setName" required placeholder="Lorwyn"></label>
          <label>IDs de edición Cardmarket<input name="mcmSetIds" inputmode="numeric" pattern="[0-9, ]*" placeholder="84"></label>
          <label>Número de coleccionista<input name="collectorNumber" placeholder="71"></label>
          <label>Precio en EUR<input name="price" required type="number" min="0.01" step="0.01" value="1.00"></label>
          <label>Cantidad<input name="quantity" type="number" min="1" max="1" value="1" readonly></label>
          <label>Idioma<select name="language">${Object.keys(CARDMARKET_LANGUAGES).map(code => `<option value="${code}" ${code === "es" ? "selected" : ""}>${code.toUpperCase()}</option>`).join("")}</select></label>
          <label>Estado<select name="condition">${Object.keys(CARDMARKET_CONDITIONS).map(value => `<option value="${value}" ${value === "near_mint" ? "selected" : ""}>${value.replaceAll("_", " ")}</option>`).join("")}</select></label>
          <label>Acabado<select name="finish"><option value="nonfoil">Normal</option><option value="foil">Foil</option><option value="etched">Etched</option></select></label>
          <label>Comentario opcional<input name="comment" maxlength="250" placeholder=""></label>
        </div>
        <p class="hint">El Product ID debe ser el <strong>mcmId exacto</strong> guardado por la app. La extensión no buscará por parecido.</p>
        <button class="primary" type="submit">Crear código para la extensión</button>
      </form>

      <aside class="panel output">
        <p class="eyebrow">Paso siguiente</p>
        <h2>Código del lote</h2>
        <div id="emptyOutput" class="empty-state"><span>100</span><p>Máximo de filas por lote.<br>Esta prueba generará solo una.</p></div>
        <div id="readyOutput" class="ready-output" hidden>
          <div id="summary" class="summary"></div>
          <textarea id="transferCode" readonly aria-label="Código del lote"></textarea>
          <button id="copyButton" class="primary" type="button">Copiar código</button>
          <p id="saveStatus" class="status"></p>
        </div>
      </aside>
    </section>
  </main>
`;

const authButton = document.querySelector<HTMLButtonElement>("#authButton")!;
const accountTitle = document.querySelector<HTMLElement>("#accountTitle")!;
const accountText = document.querySelector<HTMLElement>("#accountText")!;
const form = document.querySelector<HTMLFormElement>("#listingForm")!;
const emptyOutput = document.querySelector<HTMLElement>("#emptyOutput")!;
const readyOutput = document.querySelector<HTMLElement>("#readyOutput")!;
const codeOutput = document.querySelector<HTMLTextAreaElement>("#transferCode")!;
const summary = document.querySelector<HTMLElement>("#summary")!;
const copyButton = document.querySelector<HTMLButtonElement>("#copyButton")!;
const saveStatus = document.querySelector<HTMLElement>("#saveStatus")!;
const cloudPicker = document.querySelector<HTMLElement>("#cloudPicker")!;
const cloudCardSelect = document.querySelector<HTMLSelectElement>("#cloudCard")!;
const cloudStatus = document.querySelector<HTMLElement>("#cloudStatus")!;

let currentUser: User | null = null;
let currentTransfer: CardmarketTransfer | null = null;
let cloudCards: CloudCard[] = [];

function formField(name: string): HTMLInputElement | HTMLSelectElement {
  const field = form.elements.namedItem(name);
  if (!(field instanceof HTMLInputElement) && !(field instanceof HTMLSelectElement)) {
    throw new Error(`Falta el campo ${name}.`);
  }
  return field;
}

function useCloudCard(card: CloudCard): void {
  formField("name").value = card.name;
  formField("mcmId").value = card.mcmId;
  formField("setCode").value = card.setCode;
  formField("setName").value = card.mcmSetName || card.setName;
  formField("mcmSetIds").value = [card.mcmSetId, card.mcmSetIdExtras]
    .filter((id): id is number => typeof id === "number" && id > 0).join(",");
  formField("collectorNumber").value = card.collectorNumber;
  if (card.language in CARDMARKET_LANGUAGES) formField("language").value = card.language;
  if (card.condition in CARDMARKET_CONDITIONS) formField("condition").value = card.condition;
  if (["nonfoil", "foil", "etched"].includes(card.finish)) formField("finish").value = card.finish;
  const eur = card.price.match(/([0-9]+(?:[.,][0-9]+)?)\s*EUR/i);
  if (eur) formField("price").value = eur[1].replace(",", ".");
  cloudStatus.textContent = `${card.name} cargada. Revisa especialmente precio, idioma y estado.`;
}

async function refreshCloudCards(user: User): Promise<void> {
  cloudPicker.hidden = false;
  cloudStatus.textContent = "Leyendo la copia privada de Android…";
  const libraries = await loadCloudLibraries(user);
  cloudCards = libraries.flatMap(library => library.cards).filter(card => /^\d+$/.test(card.mcmId));
  cloudCardSelect.replaceChildren(
    new Option("Selecciona una carta…", ""),
    ...cloudCards.map((card, index) => new Option(
      `${card.name} · ${card.setCode} #${card.collectorNumber} · ${card.quantity} copias`,
      String(index)
    ))
  );
  cloudStatus.textContent = cloudCards.length > 0
    ? `${cloudCards.length} filas con Product ID exacto disponibles.`
    : "La copia actual no contiene IDs de Cardmarket. Sincroniza de nuevo desde la app actualizada.";
}

observeUser(user => {
  currentUser = user;
  if (user) {
    accountTitle.textContent = user.displayName || user.email || "Cuenta enlazada";
    accountText.textContent = "Es la misma identidad de Firebase que usa Android. El lote se guardará también en tu espacio privado.";
    authButton.textContent = "Cerrar sesión";
    refreshCloudCards(user).catch(error => {
      cloudPicker.hidden = false;
      cloudStatus.textContent = error instanceof Error ? error.message : "No se pudo leer la Biblio.";
    });
  } else {
    cloudPicker.hidden = true;
    cloudCards = [];
    accountTitle.textContent = firebaseConfigured ? "Conecta tu cuenta" : "Modo de prueba local";
    accountText.textContent = firebaseConfigured
      ? "Usa la misma cuenta de Google que en Android para ver y guardar tus borradores."
      : "Puedes probar una carta sin Firebase. Falta registrar la aplicación Web para activar el login.";
    authButton.textContent = "Entrar con Google";
  }
});

cloudCardSelect.addEventListener("change", () => {
  const index = Number(cloudCardSelect.value);
  if (Number.isInteger(index) && cloudCards[index]) useCloudCard(cloudCards[index]);
});

authButton.addEventListener("click", async () => {
  try {
    authButton.disabled = true;
    if (currentUser) await logout(); else await loginWithGoogle();
  } catch (error) {
    accountText.textContent = error instanceof Error ? error.message : "No se pudo iniciar sesión.";
  } finally {
    authButton.disabled = false;
  }
});

form.addEventListener("submit", async event => {
  event.preventDefault();
  const data = new FormData(form);
  const priceCents = Math.round(Number(data.get("price")) * 100);
  const mcmSetIds = String(data.get("mcmSetIds"))
    .split(/[ ,]+/).map(Number).filter(id => Number.isInteger(id) && id > 0);
  currentTransfer = {
    version: 1,
    batchId: newBatchId(),
    createdAt: new Date().toISOString(),
    setCode: String(data.get("setCode")).trim().toUpperCase(),
    setName: String(data.get("setName")).trim(),
    mcmSetIds: [...new Set(mcmSetIds)],
    items: [{
      mcmId: String(data.get("mcmId")).trim(),
      name: String(data.get("name")).trim(),
      collectorNumber: String(data.get("collectorNumber")).trim(),
      quantity: 1,
      priceCents,
      language: String(data.get("language")) as CardmarketLanguage,
      condition: String(data.get("condition")) as CardmarketCondition,
      finish: String(data.get("finish")) as CardFinish,
      comment: String(data.get("comment")).trim()
    }]
  };

  try {
    codeOutput.value = encodeTransfer(currentTransfer);
    summary.innerHTML = `<strong>${currentTransfer.items[0].name}</strong><span>${currentTransfer.setCode} · #${currentTransfer.items[0].collectorNumber || "—"}</span><span>1 copia · ${(priceCents / 100).toFixed(2)} €</span>`;
    emptyOutput.hidden = true;
    readyOutput.hidden = false;
    saveStatus.textContent = currentUser ? "Guardando borrador privado…" : "Código local: todavía no se ha subido a Firebase.";
    if (currentUser) {
      await saveDraft(currentUser, currentTransfer);
      saveStatus.textContent = "Borrador guardado en tu cuenta.";
    }
  } catch (error) {
    saveStatus.textContent = error instanceof Error ? error.message : "No se pudo crear el lote.";
    readyOutput.hidden = false;
  }
});

copyButton.addEventListener("click", async () => {
  await navigator.clipboard.writeText(codeOutput.value);
  copyButton.textContent = "Código copiado";
  window.setTimeout(() => { copyButton.textContent = "Copiar código"; }, 1600);
});
