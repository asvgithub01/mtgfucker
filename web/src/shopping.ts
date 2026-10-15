import { observeCloudShopping, saveCloudShopping, type CloudShoppingState } from "./firebase";
import { editBuylistEntries, importSharedBuylist } from "./shoppingCloud";
import { parseBuylist, buyComposition, buySnapshot, wantsBatches, type BuyList } from "@mtgfucker/cardmarket-protocol";

export function mountShopping(parent: HTMLElement): void {
  const section=document.createElement("section");section.className="panel shopping";
  section.innerHTML=`<h2>Compras · Premium</h2><p data-access role="status"></p><div data-content hidden>
    <p>Edita las mismas listas que en Android. Se guardan en tu cuenta con todos sus históricos, sin copias locales independientes.</p>
    <p>Los importes son referencias MKM en EUR: no ofertas NM garantizadas ni gastos de envío.</p>
    <div class="shopping-toolbar"><button data-new>Nueva lista</button><button data-rename>Cambiar nombre</button>
    <label>Importar JSON <input type="file" accept=".json,application/json" data-import></label>
    <button data-legacy>Recuperar listas anteriores del navegador</button></div>
    <select aria-label="Listas de compra" data-lists></select>
    <div class="shopping-toolbar"><button data-export>Descargar copia de seguridad JSON</button><button data-refresh>Actualizar precios MKM</button>
    <label><input type="checkbox" data-all> Seleccionar todas</label><button data-delete>Eliminar seleccionadas</button><button data-remove>Eliminar lista</button></div>
    <p role="status" data-status></p><h3 data-total></h3><div data-cards class="shopping-cards"></div>
    <details><summary>Histórico de la lista</summary><div data-history></div></details>
    <details><summary>Crear esta buylist también en Cardmarket</summary>
      <p>En Cardmarket crea una Wants List vacía. Cada bloque tiene como máximo 150 entradas. Pégalo en «Añadir lista de cartas» y revisa los resultados.</p>
      <p><strong>El texto no conserva NM, idioma, foil/etched ni número de coleccionista.</strong> Comprueba estos datos después de importar. No compramos ni enviamos formularios automáticamente.</p>
      <div data-wants></div>
    </details></div>`;
  parent.prepend(section);
  const get=<T extends HTMLElement>(name:string)=>section.querySelector<T>(`[data-${name}]`)!;
  const status=get("status"),picker=get<HTMLSelectElement>("lists");
  let state:CloudShoppingState={enabled:false,uid:null,lists:[],message:""}, pending:CloudShoppingState|undefined;
  let id="",busy=false,epoch=0;
  const selected=new Set<string>();
  const current=()=>state.lists.find(l=>l.id===id);
  const euro=(n:number)=>new Intl.NumberFormat("es",{style:"currency",currency:"EUR"}).format(n);
  function syncSelection():void {
    const entries=current()?.entries ?? [];
    const all=get<HTMLInputElement>("all");all.disabled=busy || !entries.length;
    all.checked=!!entries.length && entries.every(e=>selected.has(e.id));
    all.indeterminate=selected.size>0 && !all.checked;
    get<HTMLButtonElement>("delete").disabled=busy || !selected.size;
  }
  async function mutate(change:(rows:BuyList[])=>BuyList[]|Promise<BuyList[]>):Promise<void> {
    if(busy || !state.enabled || !state.uid)return;
    const base=state,request=epoch;busy=true;pending=undefined;render();status.textContent="Guardando cambios en tu cuenta…";
    try {
      const next=await change(base.lists);
      if(request!==epoch)return;
      const saved=await saveCloudShopping(base,next);
      if(request!==epoch)return;
      state=saved;status.textContent="Guardado. Android recibirá los cambios al sincronizar; el histórico se conserva.";
    } catch(error) { if(request===epoch)status.textContent=String(error); }
    finally {
      if(request===epoch) {
        busy=false;
        const latest=pending as CloudShoppingState|undefined;
        if(latest && latest.snapshotId!==base.snapshotId)state=latest;
        pending=undefined;render();
      }
    }
  }
  function render():void {
    get("content").hidden=!state.enabled;
    picker.replaceChildren();for(const list of state.lists)picker.add(new Option(list.name,list.id));
    if(!current())id=state.lists[0]?.id ?? "";picker.value=id;picker.disabled=busy;
    const l=current();selected.forEach(key=>{if(!l?.entries.some(e=>e.id===key))selected.delete(key);});
    for(const name of ["export","refresh","remove","rename"])get<HTMLButtonElement>(name).disabled=!state.enabled || !l || busy;
    for(const name of ["new","legacy","import"])get<HTMLInputElement>(name).disabled=!state.enabled || busy;
    syncSelection();get("cards").replaceChildren();get("history").replaceChildren();get("wants").replaceChildren();get("total").textContent="";
    if(!l)return;
    const snap=l.history.at(-1), valid=snap?.composition===buyComposition(l.entries) ? snap:undefined;
    const missing=valid?.quotes.filter(q=>q.amount===null).length ?? l.entries.length;
    get("total").textContent=`${l.name} · ${valid ? euro(valid.quotes.reduce((sum,q)=>sum+(q.amount??0),0)):"—"}${missing ? ` + ? (${missing} sin precio)`:""}`;
    for(const e of l.entries) {
      const card=document.createElement("article"),check=document.createElement("input"); check.type="checkbox";check.disabled=busy;check.checked=selected.has(e.id);check.ariaLabel=`Seleccionar ${e.name}`;
      check.onchange=()=>{if(check.checked)selected.add(e.id);else selected.delete(e.id);syncSelection();};card.append(check);
      if(e.image) { const img=document.createElement("img");img.src=e.image;img.alt=e.name;img.loading="lazy";card.append(img); }
      const label=document.createElement("p"),q=valid?.quotes.find(q=>q.entryId===e.id);
      label.textContent=`${e.quantity} × ${e.name} · ${e.set} #${e.number} · ${e.finish} · NM · ${q?.amount!=null ? euro(q.amount):"—"}`;card.append(label);
      const editor=document.createElement("div");editor.className="shopping-editor";
      const quantity=document.createElement("input");quantity.type="number";quantity.min="1";quantity.max="999";quantity.value=String(e.quantity);quantity.ariaLabel=`Cantidad de ${e.name}`;
      const language=document.createElement("input");language.value=e.language;language.placeholder="Idioma (es, en…)";language.ariaLabel=`Idioma de ${e.name}`;
      const finish=document.createElement("select");finish.ariaLabel=`Acabado de ${e.name}`;
      for(const value of ["nonfoil","foil","etched"])finish.add(new Option(value,value));finish.value=e.finish;
      const apply=document.createElement("button");apply.textContent="Guardar cambios";
      for(const control of [quantity,language,finish,apply])control.disabled=busy;
      apply.onclick=()=>{
        const count=Number(quantity.value),lang=language.value.trim().toLowerCase(),foil=finish.value as typeof e.finish;
        if(!Number.isInteger(count) || count<1 || count>999){status.textContent="La cantidad debe estar entre 1 y 999.";return;}
        void mutate(rows=>rows.map(row=>row.id===l.id ? editBuylistEntries(row,row.entries.map(entry=>entry.id===e.id ? {...entry,quantity:count,language:lang,finish:foil}:entry)):row));
      };
      editor.append(quantity,language,finish,apply);card.append(editor);
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
  get<HTMLInputElement>("all").onchange=event=>{
    selected.clear();if((event.target as HTMLInputElement).checked)current()?.entries.forEach(e=>selected.add(e.id));render();
  };
  get("new").onclick=()=>{
    if(busy || !state.enabled)return;const name=prompt("Nombre de la nueva lista")?.trim();if(!name)return;
    const list:BuyList={format:"mtgfucker-buylist",version:1,id:crypto.randomUUID(),name,entries:[],history:[]};
    void mutate(rows=>[...rows,parseBuylist(JSON.stringify(list))]).then(()=>{if(state.lists.some(l=>l.id===list.id)){id=list.id;render();}});
  };
  get("rename").onclick=()=>{
    const l=current();if(!l || busy)return;const name=prompt("Nombre de la lista",l.name)?.trim();if(!name || name===l.name)return;
    void mutate(rows=>rows.map(row=>row.id===l.id?{...row,name}:row));
  };
  get<HTMLInputElement>("import").onchange=event=>{
    const file=(event.target as HTMLInputElement).files?.[0];if(!file)return;
    void mutate(async rows=>{if(file.size>8*1024*1024)throw Error("Archivo demasiado grande");return importSharedBuylist(rows,parseBuylist(await file.text()));});
    (event.target as HTMLInputElement).value="";
  };
  get("legacy").onclick=()=>{
    if(!state.uid || !confirm("¿Recuperar en esta cuenta las listas anteriores? Los originales se conservan y no se duplican IDs existentes."))return;
    const uid=state.uid;
    void mutate(rows=>{
      let next=rows;
      for(const key of ["mtgfucker.shopping.v1",`mtgfucker.shopping.v1:${uid}`]) {
        const raw=JSON.parse(localStorage.getItem(key) ?? "[]");if(!Array.isArray(raw))throw Error("Listas anteriores incompatibles");
        for(const value of raw)next=importSharedBuylist(next,parseBuylist(JSON.stringify(value)));
      }
      return next;
    });
  };
  get("export").onclick=()=>{
    const l=current();if(!l || !state.enabled)return;
    const url=URL.createObjectURL(new Blob([JSON.stringify(l,null,2)],{type:"application/json"}));
    const a=document.createElement("a");a.href=url;a.download=`buylist-${l.id}.json`;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
  };
  get("delete").onclick=()=>{
    const l=current(),ids=new Set(selected);if(!l || !ids.size || !confirm("¿Eliminar las cartas seleccionadas de esta lista en Android y web? El histórico se conserva."))return;
    void mutate(rows=>rows.map(row=>row.id===l.id?editBuylistEntries(row,row.entries.filter(e=>!ids.has(e.id))):row));
  };
  get("remove").onclick=()=>{
    const l=current();if(!l || !confirm("¿Eliminar esta lista en Android y web? Descarga antes su copia de seguridad si quieres conservarla."))return;
    void mutate(rows=>rows.filter(row=>row.id!==l.id));
  };
  get("refresh").onclick=()=>void mutate(async rows=>{
    const response=await fetch("https://mtgjson.com/api/v5/AllPricesToday.json.gz",{cache:"no-store"});if(!response.ok)throw Error("No se pudieron descargar los precios");
    const bytes=new Uint8Array(await response.arrayBuffer()),blob=new Blob([bytes]);
    const decoded=bytes[0]===0x1f && bytes[1]===0x8b?blob.stream().pipeThrough(new DecompressionStream("gzip")):blob.stream();
    const snapshot=await new Response(decoded).json() as {data?:Record<string,unknown>};
    if(!snapshot.data || !Object.keys(snapshot.data).length)throw Error("Snapshot vacío o incompatible");
    const at=Date.now();return rows.map(l=>({...l,history:[...l.history,buySnapshot(l,snapshot.data!,at)]}));
  });
  observeCloudShopping(next=>{
    get("access").textContent=next.message;
    if(state.uid!==next.uid || !next.enabled) {
      epoch++;busy=false;pending=undefined;selected.clear();id="";state=next;render();return;
    }
    if(busy){pending=next;return;}
    state=next;render();
  });
}
