import { parseBuylist, buyComposition, buySnapshot, wantsBatches, type BuyList } from "@mtgfucker/cardmarket-protocol";

export function mountShopping(parent: HTMLElement): void {
  const section = document.createElement("section"); section.className = "panel shopping";
  section.innerHTML = `<h2>Compras · buylists NM</h2>
    <p>Importa el JSON exportado desde Compras en Android. Se guarda solo en este navegador; exporta el JSON para devolverlo a la app. No modifica tu inventario.</p>
    <p>Los importes son referencias MKM en EUR: no ofertas NM garantizadas ni gastos de envío.</p>
    <label>Importar buylist <input type="file" accept=".json,application/json" data-import></label>
    <select aria-label="Listas de compra" data-lists></select>
    <button data-export>Exportar JSON para Android</button><button data-refresh>Actualizar precios MKM</button>
    <button data-delete>Eliminar seleccionadas</button><button data-remove>Eliminar lista</button>
    <p role="status" data-status></p><h3 data-total></h3><div data-cards class="shopping-cards"></div>
    <details><summary>Histórico de la lista</summary><div data-history></div></details>
    <details><summary>Crear esta buylist también en Cardmarket</summary>
      <p>En Cardmarket crea una Wants List vacía. Cada bloque tiene como máximo 150 entradas. Pégalo en «Añadir lista de cartas» y revisa los resultados antes de continuar.</p>
      <p><strong>El texto no conserva NM, idioma, foil/etched ni número de coleccionista.</strong> Después de importar fija condición mínima NM y comprueba edición, versión, acabado e idioma de cada carta. No compramos ni enviamos formularios automáticamente.</p>
      <a target="_blank" rel="noopener" href="https://help.cardmarket.com/en/how-to-add-a-mtg-decklist-to-wants">Instrucciones oficiales de Cardmarket</a>
      <div data-wants></div>
    </details>`;
  parent.prepend(section);
  const get = <T extends HTMLElement>(name:string) => section.querySelector<T>(`[data-${name}]`)!;
  const status = get("status"), picker = get<HTMLSelectElement>("lists");
  const key = "mtgfucker.shopping.v1"; let lists: BuyList[] = [], id = "", busy = false;
  const selected = new Set<string>();
  try { const raw = JSON.parse(localStorage.getItem(key) ?? "[]"); if(!Array.isArray(raw))throw Error();lists = raw.map(x => parseBuylist(JSON.stringify(x))); }
  catch { status.textContent = "No se pudieron leer las listas guardadas. Exporta una copia antes de reemplazar el almacenamiento."; get<HTMLInputElement>("import").disabled=true; return; }
  const current = () => lists.find(l => l.id === id);
  function save(next: BuyList[]): void { localStorage.setItem(key, JSON.stringify(next)); lists = next; }
  function update(next:BuyList):void { save(lists.map(l => l.id===next.id ? next:l)); }
  function safe(action:()=>void):void { try { action(); } catch(e) { status.textContent=String(e); } }
  const euro = (n:number) => new Intl.NumberFormat("es",{style:"currency",currency:"EUR"}).format(n);
  function render():void {
    picker.replaceChildren();for(const l of lists)picker.add(new Option(l.name,l.id));
    if(!lists.some(l=>l.id===id))id=lists[0]?.id ?? ""; picker.value=id;
    const l=current();get("cards").replaceChildren();get("history").replaceChildren();get("wants").replaceChildren();get("total").textContent="";
    for(const name of ["export","refresh","delete","remove"])get<HTMLButtonElement>(name).disabled=!l || busy;
    if(!l)return;
    const snap=l.history.at(-1), valid=snap?.composition===buyComposition(l.entries) ? snap:undefined;
    const missing=valid?.quotes.filter(q=>q.amount===null).length ?? l.entries.length;
    get("total").textContent=`${l.name} · ${valid ? euro(valid.quotes.reduce((sum,q)=>sum+(q.amount??0),0)):"—"}${missing ? ` + ? (${missing} sin precio)`:""}`;
    for(const e of l.entries) {
      const card=document.createElement("article"),check=document.createElement("input"); check.type="checkbox";check.checked=selected.has(e.id);check.ariaLabel=`Seleccionar ${e.name}`;
      check.onchange=()=>{if(check.checked)selected.add(e.id);else selected.delete(e.id);};card.append(check);
      if(e.image) { const img=document.createElement("img");img.src=e.image;img.alt=e.name;img.loading="lazy";card.append(img); }
      const label=document.createElement("p"),q=valid?.quotes.find(q=>q.entryId===e.id);
      label.textContent=`${e.quantity} × ${e.name} · ${e.set} #${e.number} · ${e.finish} · NM · ${q?.amount!=null ? euro(q.amount):"—"}`;card.append(label);
      const details=document.createElement("details"),summary=document.createElement("summary");summary.textContent="Histórico MKM";details.append(summary);
      for(let i=l.history.length-1;i>=0;i--) {
        const s=l.history[i],value=s.quotes.find(q=>q.entryId===e.id),prev=l.history[i-1];
        const before=prev?.composition===s.composition ? prev.quotes.find(q=>q.entryId===e.id)?.amount:undefined;
        const line=document.createElement("p");line.textContent=`${new Date(s.at).toLocaleString()} · ${value?.amount!=null?euro(value.amount):"—"} · ${value?.date??""}`;
        if(value?.amount!=null && before!=null && before>0) { const d=(value.amount-before)/before*100;line.textContent+=` · ${d>0?"↑":d<0?"↓":"→"} ${d.toFixed(2)}%`;line.style.color=d>0?"#55d58a":d<0?"#ff7777":"inherit"; }details.append(line);
      }
      card.append(details);get("cards").append(card);
    }
    for(let i=l.history.length-1;i>=0;i--) {
      const s=l.history[i],line=document.createElement("p"),missing=s.quotes.some(q=>q.amount===null),total=s.quotes.reduce((n,q)=>n+(q.amount??0),0),prev=l.history[i-1];
      line.textContent=`${new Date(s.at).toLocaleString()} · ${euro(total)}${missing?" + ?":""}`;
      if(prev && prev.composition===s.composition && !missing && prev.quotes.every(q=>q.amount!==null)) {
        const before=prev.quotes.reduce((n,q)=>n+q.amount!,0);if(before>0) { const d=(total-before)/before*100;line.textContent+=` · ${d>0?"↑":d<0?"↓":"→"} ${d.toFixed(2)}%`;line.style.color=d>0?"#55d58a":d<0?"#ff7777":"inherit"; }
      }get("history").append(line);
    }
    wantsBatches(l).forEach((text,i)=> {const title=document.createElement("p");title.textContent=`Bloque ${i+1} · revisar NM / edición / acabado`;const area=document.createElement("textarea");area.readOnly=true;area.value=text;area.rows=5;get("wants").append(title,area);});
  }
  picker.onchange=()=>{id=picker.value;selected.clear();render();};
  get<HTMLInputElement>("import").onchange=async event=> {
    const file=(event.target as HTMLInputElement).files?.[0];if(!file)return;
    try { if(file.size>8*1024*1024)throw Error("Archivo demasiado grande");const l=parseBuylist(await file.text());l.id=crypto.randomUUID();save([...lists,l]);id=l.id;selected.clear();render();status.textContent="Lista importada como copia independiente."; }catch(e){status.textContent=String(e);}
  };
  get("export").onclick=()=>safe(()=> {const l=current();if(!l)return;const url=URL.createObjectURL(new Blob([JSON.stringify(l,null,2)],{type:"application/json"}));const a=document.createElement("a");a.href=url;a.download=`buylist-${l.id}.json`;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);});
  get("delete").onclick=()=>safe(()=>{const l=current();if(l && selected.size && confirm("¿Eliminar las cartas seleccionadas de Compras?")) {const entries=l.entries.filter(e=>!selected.has(e.id)),previous=l.history.at(-1);
      const snapshot={at:Date.now(),composition:buyComposition(entries),quotes:entries.map(e=>({entryId:e.id,amount:previous?.composition===buyComposition(l.entries)?previous.quotes.find(q=>q.entryId===e.id)?.amount??null:null,date:previous?.quotes.find(q=>q.entryId===e.id)?.date??""}))};
      update({...l,entries,history:[...l.history,snapshot]});selected.clear();render();}});
  get("remove").onclick=()=>safe(()=>{if(current() && confirm("¿Eliminar esta lista local? La colección no cambia.")){save(lists.filter(l=>l.id!==id));selected.clear();render();}});
  get("refresh").onclick=async()=> {
    if(busy || !lists.length)return;busy=true;render();status.textContent="Descargando referencias MKM de MTGJSON. Puede tardar y consumir datos…";
    try {
      const response=await fetch("https://mtgjson.com/api/v5/AllPricesToday.json.gz",{cache:"no-store"});if(!response.ok || !response.body)throw Error("No se pudieron descargar los precios");
      const bytes=new Uint8Array(await response.arrayBuffer()),blob=new Blob([bytes]);
      // Some hosts apply Content-Encoding and fetch already decompresses the .gz file.
      const decoded=bytes[0]===0x1f && bytes[1]===0x8b ? blob.stream().pipeThrough(new DecompressionStream("gzip")) : blob.stream();
      const snapshot=await new Response(decoded).json() as {data?:Record<string,unknown>};
      if(!snapshot.data || !Object.keys(snapshot.data).length)throw Error("Snapshot vacío o incompatible");
      const at=Date.now();save(lists.map(l=>({...l,history:[...l.history,buySnapshot(l,snapshot.data!,at)]})));
      status.textContent="Precios actualizados e histórico guardado. Exporta JSON para llevar los cambios a Android.";
    } catch(e) {status.textContent=`No se ha añadido un refresco al histórico: ${String(e)}`;}finally{busy=false;render();}
  };
  render();
}
