import { parseBuylist, wantsBatches } from "@mtgfucker/cardmarket-protocol";

// Separate from BulkListing: no guessed private API or writes to Cardmarket forms.
const box=document.createElement("aside");box.id="mtgfucker-wants-assistant";
Object.assign(box.style,{position:"fixed",right:"12px",bottom:"12px",width:"360px",maxWidth:"90vw",maxHeight:"70vh",overflow:"auto",zIndex:"2147483646",background:"#20242b",color:"white",padding:"16px",border:"2px solid #c4aa61",borderRadius:"12px"});
box.innerHTML=`<button data-close style="float:right">Cerrar</button><h3>MTGFucker · Compras NM</h3>
<p>Importa el JSON de la app o web. Crea una Wants List vacía en Cardmarket y pega cada bloque en su importador de listas.</p>
<p><strong>Revisa NM, edición, número, idioma y foil/etched.</strong> El texto no puede fijar todos esos filtros. No enviamos formularios ni compramos.</p>
<input data-file type="file" accept=".json,application/json"><p role="status" data-status></p><div data-result></div>`;
document.body.append(box);
box.querySelector<HTMLButtonElement>("[data-close]")!.onclick=()=>box.remove();
box.querySelector<HTMLInputElement>("[data-file]")!.onchange=async event=>{
 const file=(event.target as HTMLInputElement).files?.[0];if(!file)return;
 const status=box.querySelector<HTMLElement>("[data-status]")!,result=box.querySelector<HTMLElement>("[data-result]")!;result.replaceChildren();
 try {
  if(file.size>8*1024*1024)throw Error("Archivo demasiado grande");const list=parseBuylist(await file.text());
  status.textContent=`${list.name}: ${list.entries.length} entradas. Bloques de hasta 150; usa una lista vacía para cada bloque.`;
  wantsBatches(list).forEach((text,i)=>{
   const label=document.createElement("p");label.textContent=`Bloque ${i+1}`;
   const area=document.createElement("textarea");area.value=text;area.readOnly=true;area.rows=5;area.style.width="100%";
   const copy=document.createElement("button");copy.textContent="Copiar bloque";copy.onclick=async()=>{
    try { await navigator.clipboard.writeText(text);status.textContent="Copiado. Pega en Cardmarket y revisa todos los filtros antes de confirmar."; }
    catch { area.select();status.textContent="Selecciona y copia el texto manualmente."; }
   };result.append(label,area,copy);
  });
 }catch(e){status.textContent=String(e);}
};
