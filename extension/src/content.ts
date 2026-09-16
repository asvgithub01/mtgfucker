import {
  CARDMARKET_CONDITIONS,
  CARDMARKET_LANGUAGES,
  decodeTransfer,
  type CardmarketTransfer,
  type CardmarketTransferItem
} from "@mtgfucker/cardmarket-protocol";

const ROOT_ID = "mtgfucker-cardmarket-companion";

interface MatchedRow {
  item: CardmarketTransferItem;
  row: HTMLTableRowElement;
}

function findField<T extends HTMLInputElement | HTMLSelectElement>(
  row: HTMLTableRowElement,
  selector: string,
  label: string
): T {
  const field = row.querySelector<T>(selector);
  if (!field) throw new Error(`Cardmarket ha cambiado: no encuentro ${label}. No se ha rellenado nada.`);
  return field;
}

function setValue(field: HTMLInputElement | HTMLSelectElement, value: string): void {
  field.value = value;
  field.dispatchEvent(new Event("input", { bubbles: true }));
  field.dispatchEvent(new Event("change", { bubbles: true }));
}

function setChecked(field: HTMLInputElement, checked: boolean): void {
  field.checked = checked;
  field.dispatchEvent(new Event("input", { bubbles: true }));
  field.dispatchEvent(new Event("change", { bubbles: true }));
}

function matchTransfer(transfer: CardmarketTransfer): MatchedRow[] {
  const pageUrl = new URL(location.href);
  const pageExpansion = Number(pageUrl.searchParams.get("idExpansion"));
  if (transfer.mcmSetIds.length > 0 && !transfer.mcmSetIds.includes(pageExpansion)) {
    throw new Error(`Abre en Cardmarket la edición ${transfer.setName} (ID ${transfer.mcmSetIds.join(" o ")}).`);
  }
  if (transfer.catalogPage) {
    const currentPage = Math.max(1, Number(pageUrl.searchParams.get("site")) || 1);
    if (currentPage !== transfer.catalogPage) {
      throw new Error(`Este lote corresponde a la página ${transfer.catalogPage} ordenada por collector number; ahora estás en la ${currentPage}. Ábrela desde el botón de la web.`);
    }
    const currentSort = pageUrl.searchParams.get("sortBy");
    if (currentSort && currentSort !== "collectorsnumber_asc") {
      throw new Error("Este lote necesita la ordenación por collector number. Ábrelo desde el botón de la web.");
    }
  }

  const rowsByProduct = new Map<string, HTMLTableRowElement>();
  document.querySelectorAll<HTMLInputElement>('input[name^="idProduct["]').forEach(input => {
    const row = input.closest("tr");
    if (row) rowsByProduct.set(input.value, row);
  });
  if (rowsByProduct.size === 0) {
    throw new Error("No hay cartas cargadas. Elige la edición y pulsa Filtro antes de importar.");
  }

  return transfer.items.map(item => {
    const row = rowsByProduct.get(item.mcmId);
    if (!row) {
      const number = item.collectorNumber ? ` · nº ${item.collectorNumber}` : "";
      throw new Error(`${item.name} (Product ID ${item.mcmId}${number}) no aparece en esta página del catálogo. No se ha rellenado nada.`);
    }
    // Validate the complete adapter before writing any field, so failures remain all-or-nothing.
    findField(row, 'select[name^="idLanguage["]', "el idioma");
    findField(row, 'select[name^="idCondition["]', "el estado");
    findField(row, 'input[name^="amount["]', "la cantidad");
    findField(row, 'input[name^="price["]', "el precio");
    findField(row, 'input[name^="isFoil["]', "el acabado foil");
    findField(row, 'input[name^="isSigned["]', "la marca de firma");
    findField(row, 'input[name^="comments["]', "el comentario");
    return { item, row };
  });
}

function fillRows(matches: MatchedRow[]): void {
  for (const { item, row } of matches) {
    setValue(findField(row, 'select[name^="idLanguage["]', "el idioma"),
      String(CARDMARKET_LANGUAGES[item.language]));
    setValue(findField(row, 'select[name^="idCondition["]', "el estado"),
      String(CARDMARKET_CONDITIONS[item.condition]));
    setValue(findField(row, 'input[name^="amount["]', "la cantidad"), String(item.quantity));
    setValue(findField(row, 'input[name^="price["]', "el precio"), (item.priceCents / 100).toFixed(2));
    setValue(findField(row, 'input[name^="comments["]', "el comentario"), item.comment || "");
    setChecked(findField(row, 'input[name^="isFoil["]', "el acabado foil"), item.finish !== "nonfoil");
    setChecked(findField(row, 'input[name^="isSigned["]', "la marca de firma"), false);
    row.scrollIntoView({ behavior: "smooth", block: "center" });
    row.style.outline = "3px solid #c9ef46";
    row.style.outlineOffset = "-3px";
  }
}

function escapeHtml(value: string): string {
  return value.replace(/[&<>'"]/g, character => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;"
  })[character] || character);
}

function mount(): void {
  if (document.getElementById(ROOT_ID)) return;
  const host = document.createElement("div");
  host.id = ROOT_ID;
  document.body.append(host);
  const shadow = host.attachShadow({ mode: "open" });
  shadow.innerHTML = `
    <style>
      :host{all:initial;font-family:Inter,system-ui,sans-serif;color:#f6f3e9}
      button,textarea{font:inherit}.launcher{position:fixed;right:22px;bottom:22px;z-index:2147483646;border:0;border-radius:999px;padding:13px 18px;background:#c9ef46;color:#111318;font-weight:850;box-shadow:0 12px 35px #0008;cursor:pointer}
      .backdrop{position:fixed;inset:0;z-index:2147483647;background:#09090dbb;display:grid;place-items:center;padding:20px}.modal{width:min(680px,100%);max-height:90vh;overflow:auto;background:#17171d;border:1px solid #45434d;border-radius:18px;box-shadow:0 25px 90px #000b;padding:25px}.top{display:flex;justify-content:space-between;gap:20px}.eyebrow{margin:0 0 7px;color:#c9ef46;text-transform:uppercase;letter-spacing:.13em;font-size:11px;font-weight:800}h2{font-size:25px;margin:0 0 8px}.muted{color:#aaa7b1;font-size:13px;line-height:1.45}.close{border:0;background:transparent;color:#aaa7b1;font-size:26px;cursor:pointer}.code{width:100%;min-height:130px;box-sizing:border-box;background:#0d0d12;color:#f6f3e9;border:1px solid #45434d;border-radius:10px;padding:12px;font:11px ui-monospace,monospace;resize:vertical}.actions{display:flex;gap:10px;margin-top:14px}.primary,.secondary{border-radius:10px;padding:12px 15px;font-weight:800;cursor:pointer}.primary{border:0;background:#c9ef46;color:#111318}.secondary{border:1px solid #56535e;background:transparent;color:#f6f3e9}.primary:disabled{opacity:.45;cursor:not-allowed}.status{min-height:21px;margin:13px 0 0;color:#ef9f9f;font-size:13px}.preview{margin-top:17px;border-top:1px solid #34323a;padding-top:15px}.card{display:grid;grid-template-columns:1fr auto;gap:6px 16px;background:#0f0f14;border-left:3px solid #c9ef46;padding:13px;margin-bottom:9px}.card span{color:#aaa7b1;font-size:12px}.safe{color:#c9ef46!important}.hidden{display:none!important}
    </style>
    <button class="launcher" type="button">Importar lote MTGFucker</button>
    <div class="backdrop hidden" role="dialog" aria-modal="true">
      <section class="modal">
        <div class="top"><div><p class="eyebrow">Cardmarket Companion</p><h2>Revisión antes de rellenar</h2><p class="muted">Pega el código creado en la web. Se exige el Product ID exacto y nunca se pulsa «Poner en venta».</p></div><button class="close" aria-label="Cerrar">×</button></div>
        <textarea class="code" placeholder="MTGF1.…" aria-label="Código del lote"></textarea>
        <div class="actions"><button class="secondary inspect" type="button">Comprobar lote</button><button class="primary fill" type="button" disabled>Rellenar formulario</button></div>
        <p class="status"></p><div class="preview"></div>
      </section>
    </div>`;

  const launcher = shadow.querySelector<HTMLButtonElement>(".launcher")!;
  const backdrop = shadow.querySelector<HTMLElement>(".backdrop")!;
  const close = shadow.querySelector<HTMLButtonElement>(".close")!;
  const inspect = shadow.querySelector<HTMLButtonElement>(".inspect")!;
  const fill = shadow.querySelector<HTMLButtonElement>(".fill")!;
  const code = shadow.querySelector<HTMLTextAreaElement>(".code")!;
  const status = shadow.querySelector<HTMLElement>(".status")!;
  const preview = shadow.querySelector<HTMLElement>(".preview")!;
  let matches: MatchedRow[] = [];

  const hide = () => backdrop.classList.add("hidden");
  launcher.addEventListener("click", () => backdrop.classList.remove("hidden"));
  close.addEventListener("click", hide);
  backdrop.addEventListener("click", event => { if (event.target === backdrop) hide(); });

  inspect.addEventListener("click", () => {
    try {
      const transfer = decodeTransfer(code.value);
      matches = matchTransfer(transfer);
      preview.innerHTML = matches.map(({ item }) => `<div class="card"><strong>${escapeHtml(item.name)}</strong><span class="safe">Coincidencia exacta</span><span>${escapeHtml(transfer.setCode)} · Product ID ${escapeHtml(item.mcmId)}</span><span>${item.quantity} × ${(item.priceCents / 100).toFixed(2)} €</span></div>`).join("");
      status.textContent = `${matches.length} fila${matches.length === 1 ? "" : "s"} lista${matches.length === 1 ? "" : "s"}. Revisa y confirma.`;
      status.style.color = "#c9ef46";
      fill.disabled = false;
    } catch (error) {
      matches = [];
      preview.innerHTML = "";
      fill.disabled = true;
      status.style.color = "#ef9f9f";
      status.textContent = error instanceof Error ? error.message : "No se pudo comprobar el lote.";
    }
  });

  fill.addEventListener("click", () => {
    try {
      if (matches.length === 0) throw new Error("Primero comprueba el lote.");
      fillRows(matches);
      status.style.color = "#c9ef46";
      status.textContent = "Formulario rellenado. Ciérralo, revisa cada dato y publica manualmente si todo es correcto.";
      fill.disabled = true;
      fill.textContent = "Rellenado";
    } catch (error) {
      status.style.color = "#ef9f9f";
      status.textContent = error instanceof Error ? error.message : "No se pudo rellenar el formulario.";
    }
  });
}

mount();
