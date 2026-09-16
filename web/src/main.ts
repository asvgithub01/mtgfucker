import "./styles.css";
import {
  CARDMARKET_CONDITIONS,
  CARDMARKET_LANGUAGES,
  buildTransferBatches,
  decodeTransfer,
  encodeTransfer,
  priceWithMultiplier,
  type CardFinish,
  type CardmarketBatchCandidate,
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
  type CloudCard,
  type CloudLibrary
} from "./firebase";
import type { User } from "firebase/auth";
import { loadCardmarketCatalog, type CardmarketCatalogPlacement } from "./cardmarketCatalog";

const PLAN_STORAGE_KEY = "mtgfucker.cardmarket.sale-plan.v3";
const LEGACY_PLAN_STORAGE_KEYS = [
  "mtgfucker.cardmarket.sale-plan.v1",
  "mtgfucker.cardmarket.sale-plan.v2"
];
const TEST_SELECTION_SIZE = 150;

interface CloudSaleCard extends CloudCard {
  key: string;
  libraryId: string;
  libraryName: string;
  basePriceAmount: number;
  basePriceCents: number;
  mcmSetIds: number[];
}

interface StoredPlan {
  multiplier: number;
  currentIndex: number;
  codes: string[];
}

const app = document.querySelector<HTMLDivElement>("#app");
if (!app) throw new Error("No se pudo iniciar la web.");

app.innerHTML = `
  <header class="hero compact-hero">
    <div class="brand"><span class="brand-mark">M</span><span>MTGFucker</span></div>
    <div class="hero-copy">
      <p class="eyebrow">Cardmarket Companion</p>
      <h1>Importación mixta,<br><em>por lotes seguros.</em></h1>
      <p>Selecciona cartas de cualquier Biblio. La web las ordena por edición y prepara formularios de hasta 100 filas.</p>
    </div>
    <div class="steps"><span class="active">01 · Seleccionar</span><span>02 · Importar lotes</span><span>03 · Publicar tú</span></div>
  </header>
  <main>
    <section class="account panel">
      <div>
        <p class="eyebrow">Cuenta Premium</p>
        <h2 id="accountTitle">Conecta tu cuenta</h2>
        <p id="accountText">Usa la misma cuenta de Google que en Android para cargar tus Biblios.</p>
      </div>
      <button id="authButton" class="secondary">Entrar con Google</button>
    </section>

    <section id="bulkWorkspace" class="bulk-workspace" hidden>
      <section class="panel bulk-panel">
        <div class="section-heading">
          <div><p class="eyebrow">Importador mixto</p><h2>Elige las cartas</h2></div>
          <span class="safety">Nunca publicamos</span>
        </div>

        <div class="bulk-controls">
          <label>Biblio
            <select id="libraryFilter"><option value="">Todas las Biblios</option></select>
          </label>
          <label>Buscar
            <input id="searchFilter" type="search" placeholder="Nombre, edición o código">
          </label>
          <label>Multiplicador de precio
            <input id="priceMultiplier" type="number" min="0.01" max="1000" step="0.01" value="10">
          </label>
          <label>Comentario para todas
            <input id="bulkComment" maxlength="250" placeholder="Mejor dejarlo vacío salvo que sea necesario">
          </label>
        </div>

        <div class="multiplier-presets" aria-label="Multiplicadores rápidos">
          <span>Precio de la app ×</span>
          <button type="button" data-multiplier="1">1</button>
          <button type="button" data-multiplier="10" class="active">10</button>
          <button type="button" data-multiplier="100">100</button>
        </div>
        <p class="warning">Un precio ×10 o ×100 reduce el riesgo de compra, pero la oferta seguirá siendo pública si pulsas «Poner a la venta».</p>

        <div class="selection-bar">
          <div>
            <strong id="selectionSummary">0 seleccionadas</strong>
            <span id="cloudStatus">Leyendo la copia privada de Android…</span>
          </div>
          <div class="selection-actions">
            <button id="selectTest" class="secondary" type="button">Seleccionar 150 de prueba</button>
            <button id="selectAll" class="secondary" type="button">Seleccionar visibles</button>
            <button id="clearSelection" class="secondary" type="button">Limpiar</button>
          </div>
        </div>

        <div class="card-list-wrap">
          <table class="card-list">
            <thead><tr><th></th><th>Carta</th><th>Edición</th><th>Copias</th><th>Precio app</th><th>Precio modificado</th></tr></thead>
            <tbody id="cardRows"></tbody>
          </table>
          <p id="emptyCards" class="empty-copy">No hay cartas que coincidan con el filtro.</p>
        </div>

        <button id="createPlan" class="primary" type="button" disabled>Preparar lotes seleccionados</button>
      </section>

      <aside class="panel output batch-output">
        <p class="eyebrow">Cola de Cardmarket</p>
        <h2 id="batchHeading">Todavía sin lotes</h2>
        <div id="emptyOutput" class="empty-state"><span>100</span><p>Máximo por formulario.<br>Puedes seleccionar más y se dividirán solos.</p></div>
        <div id="readyOutput" class="ready-output" hidden>
          <div id="summary" class="summary"></div>
          <div class="batch-nav">
            <button id="previousBatch" class="secondary" type="button">← Anterior</button>
            <span id="batchPosition"></span>
            <button id="nextBatch" class="secondary" type="button">Siguiente →</button>
          </div>
          <textarea id="transferCode" readonly aria-label="Código del lote"></textarea>
          <button id="copyButton" class="primary" type="button">Copiar código</button>
          <a id="openCardmarket" class="primary link-button" target="_blank" rel="noopener">Abrir esta edición en Cardmarket</a>
          <p id="saveStatus" class="status"></p>
        </div>
      </aside>
    </section>
  </main>
`;

const authButton = document.querySelector<HTMLButtonElement>("#authButton")!;
const accountTitle = document.querySelector<HTMLElement>("#accountTitle")!;
const accountText = document.querySelector<HTMLElement>("#accountText")!;
const bulkWorkspace = document.querySelector<HTMLElement>("#bulkWorkspace")!;
const libraryFilter = document.querySelector<HTMLSelectElement>("#libraryFilter")!;
const searchFilter = document.querySelector<HTMLInputElement>("#searchFilter")!;
const priceMultiplier = document.querySelector<HTMLInputElement>("#priceMultiplier")!;
const bulkComment = document.querySelector<HTMLInputElement>("#bulkComment")!;
const cloudStatus = document.querySelector<HTMLElement>("#cloudStatus")!;
const selectionSummary = document.querySelector<HTMLElement>("#selectionSummary")!;
const selectTest = document.querySelector<HTMLButtonElement>("#selectTest")!;
const selectAll = document.querySelector<HTMLButtonElement>("#selectAll")!;
const clearSelection = document.querySelector<HTMLButtonElement>("#clearSelection")!;
const createPlan = document.querySelector<HTMLButtonElement>("#createPlan")!;
const cardRows = document.querySelector<HTMLTableSectionElement>("#cardRows")!;
const emptyCards = document.querySelector<HTMLElement>("#emptyCards")!;
const emptyOutput = document.querySelector<HTMLElement>("#emptyOutput")!;
const readyOutput = document.querySelector<HTMLElement>("#readyOutput")!;
const batchHeading = document.querySelector<HTMLElement>("#batchHeading")!;
const batchPosition = document.querySelector<HTMLElement>("#batchPosition")!;
const codeOutput = document.querySelector<HTMLTextAreaElement>("#transferCode")!;
const summary = document.querySelector<HTMLElement>("#summary")!;
const copyButton = document.querySelector<HTMLButtonElement>("#copyButton")!;
const openCardmarket = document.querySelector<HTMLAnchorElement>("#openCardmarket")!;
const previousBatch = document.querySelector<HTMLButtonElement>("#previousBatch")!;
const nextBatch = document.querySelector<HTMLButtonElement>("#nextBatch")!;
const saveStatus = document.querySelector<HTMLElement>("#saveStatus")!;

let currentUser: User | null = null;
let libraries: CloudLibrary[] = [];
let saleCards: CloudSaleCard[] = [];
let visibleCards: CloudSaleCard[] = [];
let selectedKeys = new Set<string>();
let transfers: CardmarketTransfer[] = [];
let currentBatchIndex = 0;
let currentPlanMultiplier = 1;
const savedDrafts = new Set<string>();

function escapeHtml(value: string): string {
  return value.replace(/[&<>'"]/g, character => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;"
  })[character] || character);
}

function parsePriceAmount(raw: string): number | null {
  let value = raw.trim().replace(/\u00a0/g, "").replace(/\s/g, "").replace(/[^0-9,.-]/g, "");
  if (!value || value === "-" || value === "." || value === ",") return null;
  const comma = value.lastIndexOf(",");
  const dot = value.lastIndexOf(".");
  if (comma >= 0 && dot >= 0 && comma > dot) value = value.replace(/\./g, "").replace(",", ".");
  else if (comma >= 0 && dot >= 0) value = value.replace(/,/g, "");
  else if (comma >= 0) value = value.replace(",", ".");
  const amount = Number(value);
  return Number.isFinite(amount) && amount > 0 ? amount : null;
}

function formatPrice(cents: number): string {
  return `${(cents / 100).toFixed(2)} €`;
}

function multiplier(): number {
  const value = Number(priceMultiplier.value);
  return Number.isFinite(value) && value > 0 ? value : 1;
}

function adjustedPrice(card: CloudSaleCard): number {
  return priceWithMultiplier(card.basePriceAmount, multiplier());
}

function normalizeCloudCards(sourceLibraries: CloudLibrary[]): { cards: CloudSaleCard[]; rejected: number } {
  const cards: CloudSaleCard[] = [];
  let rejected = 0;
  sourceLibraries.forEach(library => library.cards.forEach((card, index) => {
    const basePriceAmount = parsePriceAmount(card.price);
    const basePriceCents = basePriceAmount === null ? null : Math.max(1, Math.round(basePriceAmount * 100));
    const mcmSetIds = [...new Set([card.mcmSetId, card.mcmSetIdExtras]
      .filter((id): id is number => typeof id === "number" && Number.isInteger(id) && id > 0))];
    const compatible = /^\d+$/.test(card.mcmId) && basePriceCents !== null && mcmSetIds.length > 0 &&
      card.language in CARDMARKET_LANGUAGES && card.condition in CARDMARKET_CONDITIONS &&
      ["nonfoil", "foil", "etched"].includes(card.finish) && card.quantity > 0;
    if (!compatible || basePriceAmount === null || basePriceCents === null) {
      rejected += 1;
      return;
    }
    cards.push({
      ...card,
      key: `${library.id}:${card.collectionItemId || card.printingUuid}:${index}`,
      libraryId: library.id,
      libraryName: library.name,
      basePriceAmount,
      basePriceCents,
      mcmSetIds
    });
  }));
  return { cards, rejected };
}

function filteredCards(): CloudSaleCard[] {
  const libraryId = libraryFilter.value;
  const search = searchFilter.value.trim().toLocaleLowerCase();
  return saleCards.filter(card =>
    (!libraryId || card.libraryId === libraryId) &&
    (!search || `${card.name} ${card.setName} ${card.setCode} ${card.collectorNumber}`.toLocaleLowerCase().includes(search))
  );
}

function updateMultiplierButtons(): void {
  document.querySelectorAll<HTMLButtonElement>("[data-multiplier]").forEach(button => {
    button.classList.toggle("active", Number(button.dataset.multiplier) === multiplier());
  });
}

function updateSelectionSummary(): void {
  const selected = saleCards.filter(card => selectedKeys.has(card.key));
  const copies = selected.reduce((total, card) => total + card.quantity, 0);
  const base = selected.reduce((total, card) => total + card.basePriceCents * card.quantity, 0);
  const adjusted = selected.reduce((total, card) => total + adjustedPrice(card) * card.quantity, 0);
  selectionSummary.textContent = selected.length === 0
    ? "0 seleccionadas"
    : `${selected.length} filas · ${copies} copias · ${formatPrice(base)} → ${formatPrice(adjusted)}`;
  createPlan.disabled = selected.length === 0;
}

function renderCards(): void {
  visibleCards = filteredCards();
  cardRows.innerHTML = visibleCards.map(card => `
    <tr>
      <td><input class="card-check" type="checkbox" data-card-key="${escapeHtml(card.key)}" ${selectedKeys.has(card.key) ? "checked" : ""}></td>
      <td><strong>${escapeHtml(card.name)}</strong><small>${escapeHtml(card.libraryName)} · #${escapeHtml(card.collectorNumber || "—")}</small></td>
      <td>${escapeHtml(card.setCode)}<small>${escapeHtml(card.setName)}</small></td>
      <td>${card.quantity}</td>
      <td>${formatPrice(card.basePriceCents)}</td>
      <td class="adjusted-price">${formatPrice(adjustedPrice(card))}</td>
    </tr>`).join("");
  emptyCards.hidden = visibleCards.length > 0;
  updateSelectionSummary();
}

function renderLibraries(): void {
  libraryFilter.replaceChildren(
    new Option("Todas las Biblios", ""),
    ...libraries.map(library => new Option(`${library.name} (${library.cards.length})`, library.id))
  );
}

function cardToCandidates(card: CloudSaleCard): CardmarketBatchCandidate[] {
  const candidates: CardmarketBatchCandidate[] = [];
  let remaining = card.quantity;
  while (remaining > 0) {
    const quantity = Math.min(remaining, 99);
    candidates.push({
      collectionItemId: card.collectionItemId,
      printingUuid: card.printingUuid,
      mcmId: card.mcmId,
      mcmMetaId: card.mcmMetaId,
      name: card.name,
      collectorNumber: card.collectorNumber,
      quantity,
      priceCents: adjustedPrice(card),
      language: card.language as CardmarketLanguage,
      condition: card.condition as CardmarketCondition,
      finish: card.finish as CardFinish,
      comment: bulkComment.value.trim(),
      setCode: card.setCode,
      setName: card.mcmSetName || card.setName,
      mcmSetIds: card.mcmSetIds,
      catalogPage: 0,
      catalogPosition: 0,
      catalogSize: 0
    });
    remaining -= quantity;
  }
  return candidates;
}

async function placeCandidatesInCatalog(
  candidates: CardmarketBatchCandidate[]
): Promise<CardmarketBatchCandidate[]> {
  const catalogs = new Map<string, Map<string, CardmarketCatalogPlacement>>();
  const setCodes = [...new Set(candidates.map(candidate => candidate.setCode.trim().toUpperCase()))];
  await Promise.all(setCodes.map(async setCode => {
    catalogs.set(setCode, await loadCardmarketCatalog(setCode));
  }));

  return candidates.map(candidate => {
    const setCode = candidate.setCode.trim().toUpperCase();
    const placement = catalogs.get(setCode)?.get(candidate.mcmId);
    if (!placement) {
      throw new Error(`${candidate.name} (Product ID ${candidate.mcmId}) no aparece en el catálogo completo ${setCode}.`);
    }
    return {
      ...candidate,
      catalogPage: placement.page,
      catalogPosition: placement.position,
      catalogSize: placement.size,
      catalogFirstName: placement.pageFirstName,
      catalogLastName: placement.pageLastName
    };
  });
}

function persistPlan(): void {
  if (transfers.length === 0) {
    localStorage.removeItem(PLAN_STORAGE_KEY);
    return;
  }
  const stored: StoredPlan = {
    multiplier: currentPlanMultiplier,
    currentIndex: currentBatchIndex,
    codes: transfers.map(encodeTransfer)
  };
  localStorage.setItem(PLAN_STORAGE_KEY, JSON.stringify(stored));
}

function restorePlan(): void {
  try {
    const raw = localStorage.getItem(PLAN_STORAGE_KEY);
    if (!raw) return;
    const stored = JSON.parse(raw) as StoredPlan;
    const restored = stored.codes.map(decodeTransfer);
    if (restored.length === 0) return;
    transfers = restored;
    currentBatchIndex = Math.min(Math.max(0, stored.currentIndex || 0), transfers.length - 1);
    currentPlanMultiplier = stored.multiplier || 1;
    priceMultiplier.value = String(currentPlanMultiplier);
    renderBatch();
  } catch {
    localStorage.removeItem(PLAN_STORAGE_KEY);
  }
}

async function saveCurrentDraft(transfer: CardmarketTransfer): Promise<void> {
  if (!currentUser || savedDrafts.has(transfer.batchId)) return;
  saveStatus.textContent = "Guardando este lote en tu cuenta…";
  try {
    await saveDraft(currentUser, transfer);
    savedDrafts.add(transfer.batchId);
    if (transfers[currentBatchIndex]?.batchId === transfer.batchId) saveStatus.textContent = "Lote guardado en tu cuenta.";
  } catch (error) {
    if (transfers[currentBatchIndex]?.batchId === transfer.batchId) {
      saveStatus.textContent = error instanceof Error ? error.message : "No se pudo guardar el lote.";
    }
  }
}

function renderBatch(): void {
  const transfer = transfers[currentBatchIndex];
  if (!transfer) return;
  const copies = transfer.items.reduce((total, item) => total + item.quantity, 0);
  batchHeading.textContent = `${transfer.setName} · ${transfer.setCode}`;
  batchPosition.textContent = `Lote ${currentBatchIndex + 1} de ${transfers.length}`;
  summary.innerHTML = `
    <strong>${transfer.items.length} filas · ${copies} copias</strong>
    <span>Edición ${escapeHtml(transfer.setName)} · ID ${transfer.mcmSetIds.join(" / ")}</span>
    ${transfer.catalogPage ? `<span>Página ${transfer.catalogPage} del catálogo · ${escapeHtml(transfer.catalogFirstName || "?")} – ${escapeHtml(transfer.catalogLastName || "?")} · orden alfabético inglés</span>` : ""}
    <span>Precios calculados con ×${currentPlanMultiplier.toLocaleString("es-ES")}</span>`;
  codeOutput.value = encodeTransfer(transfer);
  const cardmarketUrl = new URL("https://www.cardmarket.com/en/Magic/Stock/ListingMethods/BulkListing");
  cardmarketUrl.searchParams.set("idExpansion", String(transfer.mcmSetIds[0]));
  cardmarketUrl.searchParams.set("sortBy", "name_asc");
  cardmarketUrl.searchParams.set("site", String(transfer.catalogPage || 1));
  openCardmarket.href = cardmarketUrl.toString();
  previousBatch.disabled = currentBatchIndex === 0;
  nextBatch.disabled = currentBatchIndex === transfers.length - 1;
  emptyOutput.hidden = true;
  readyOutput.hidden = false;
  copyButton.textContent = "Copiar código";
  saveStatus.textContent = currentUser ? "" : "Plan guardado en este navegador.";
  persistPlan();
  void saveCurrentDraft(transfer);
}

async function refreshCloudCards(user: User): Promise<void> {
  bulkWorkspace.hidden = false;
  cloudStatus.textContent = "Leyendo la copia privada de Android…";
  libraries = await loadCloudLibraries(user);
  const normalized = normalizeCloudCards(libraries);
  saleCards = normalized.cards;
  selectedKeys.clear();
  renderLibraries();
  renderCards();
  cloudStatus.textContent = `${saleCards.length} filas listas para vender` +
    (normalized.rejected > 0 ? ` · ${normalized.rejected} omitidas por faltar precio o IDs exactos.` : ".");
}

observeUser(user => {
  currentUser = user;
  if (user) {
    accountTitle.textContent = user.displayName || user.email || "Cuenta enlazada";
    accountText.textContent = "Misma cuenta que Android. Ya puedes preparar una selección mixta de tus Biblios.";
    authButton.textContent = "Cerrar sesión";
    refreshCloudCards(user).catch(error => {
      bulkWorkspace.hidden = false;
      cloudStatus.textContent = error instanceof Error ? error.message : "No se pudo leer la Biblio.";
    });
  } else {
    bulkWorkspace.hidden = true;
    libraries = [];
    saleCards = [];
    selectedKeys.clear();
    accountTitle.textContent = firebaseConfigured ? "Conecta tu cuenta" : "Firebase pendiente";
    accountText.textContent = firebaseConfigured
      ? "Usa la misma cuenta de Google que en Android para cargar tus Biblios."
      : "Falta configurar la aplicación Web de Firebase.";
    authButton.textContent = "Entrar con Google";
  }
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

libraryFilter.addEventListener("change", renderCards);
searchFilter.addEventListener("input", renderCards);
priceMultiplier.addEventListener("input", () => {
  updateMultiplierButtons();
  renderCards();
});

document.querySelectorAll<HTMLButtonElement>("[data-multiplier]").forEach(button => {
  button.addEventListener("click", () => {
    priceMultiplier.value = button.dataset.multiplier || "1";
    updateMultiplierButtons();
    renderCards();
  });
});

cardRows.addEventListener("change", event => {
  const checkbox = (event.target as Element).closest<HTMLInputElement>(".card-check");
  if (!checkbox) return;
  if (checkbox.checked) selectedKeys.add(checkbox.dataset.cardKey || "");
  else selectedKeys.delete(checkbox.dataset.cardKey || "");
  updateSelectionSummary();
});

selectTest.addEventListener("click", () => {
  selectedKeys.clear();
  visibleCards.slice(0, TEST_SELECTION_SIZE).forEach(card => selectedKeys.add(card.key));
  renderCards();
  cardRows.scrollIntoView({ behavior: "smooth", block: "start" });
});

selectAll.addEventListener("click", () => {
  visibleCards.forEach(card => selectedKeys.add(card.key));
  renderCards();
});

clearSelection.addEventListener("click", () => {
  selectedKeys.clear();
  renderCards();
});

createPlan.addEventListener("click", async () => {
  try {
    createPlan.disabled = true;
    createPlan.textContent = "Calculando páginas exactas…";
    const selected = saleCards.filter(card => selectedKeys.has(card.key));
    if (selected.length === 0) throw new Error("Selecciona al menos una carta.");
    const candidates = await placeCandidatesInCatalog(selected.flatMap(cardToCandidates));
    transfers = buildTransferBatches(candidates);
    currentBatchIndex = 0;
    currentPlanMultiplier = multiplier();
    savedDrafts.clear();
    renderBatch();
    readyOutput.scrollIntoView({ behavior: "smooth", block: "start" });
  } catch (error) {
    saveStatus.textContent = error instanceof Error ? error.message : "No se pudo crear el plan.";
    readyOutput.hidden = false;
  } finally {
    createPlan.textContent = "Preparar lotes seleccionados";
    updateSelectionSummary();
  }
});

previousBatch.addEventListener("click", () => {
  if (currentBatchIndex > 0) {
    currentBatchIndex -= 1;
    renderBatch();
  }
});

nextBatch.addEventListener("click", () => {
  if (currentBatchIndex < transfers.length - 1) {
    currentBatchIndex += 1;
    renderBatch();
  }
});

copyButton.addEventListener("click", async () => {
  await navigator.clipboard.writeText(codeOutput.value);
  copyButton.textContent = "Código copiado";
  window.setTimeout(() => { copyButton.textContent = "Copiar código"; }, 1600);
});

LEGACY_PLAN_STORAGE_KEYS.forEach(key => localStorage.removeItem(key));
restorePlan();
