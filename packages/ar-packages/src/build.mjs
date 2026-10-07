#!/usr/bin/env node
// No cloud side effects: outputs a versioned static catalog; publish via existing Hosting deployment.
import {readFile,writeFile,mkdir,rename,stat} from 'node:fs/promises';
import {resolve,join} from 'node:path';
import {spawnSync} from 'node:child_process';
import {sha256,validateSources,selectCards,validateReferences} from './policy.mjs';
const args=process.argv.slice(2), option=(k,d)=>args.includes(k)?args[args.indexOf(k)+1]:d;
if(args.includes('--help')) { console.log('node build.mjs --sets FRA,FRC | --all [--arcoreimg PATH] [--output web/public/ar] [--force]\n--all only builds explicitly reviewed editions.json coverage. Add new coverage before publishing.'); process.exit(0); }
if(args.includes('--discover')) throw Error('Discovery moved to npm run discover:ar');
const config=JSON.parse(await readFile(new URL('../assets/ar-editions.json',import.meta.url),'utf8'));
const sets=args.includes('--all')?Object.keys(config):option('--sets','FRA').toUpperCase().split(',');
const output=resolve(option('--output','web/public/ar')), cache=resolve('build/ar-source-cache');
const tool=resolve(option('--arcoreimg',process.env.ARCOREIMG || 'build/ar-tools/arcoreimg.exe'));
await stat(tool); await mkdir(output,{recursive:true}); await mkdir(cache,{recursive:true});
const headers={'User-Agent':'MtgOcr-ArPackages/1.0','Accept':'application/json'};
async function fetchBytes(url) {
  let last;
  for(let attempt=0;attempt<3;attempt++) {
    await new Promise(r=>setTimeout(r,150*(attempt+1)));
    try { const r=await fetch(url,{headers,signal:AbortSignal.timeout(45000)}); if(!r.ok) throw Error(`HTTP ${r.status}: ${url}`); return Buffer.from(await r.arrayBuffer()); } catch(e){last=e;}
  } throw last;
}
async function json(url){return JSON.parse((await fetchBytes(url)).toString());}
async function setCards(code) {
  let url=`https://api.scryfall.com/cards/search?q=${encodeURIComponent('set:'+code+' game:paper')}&unique=prints&order=set`,all=[];
  while(url){const p=await json(url);all.push(...p.data);url=p.has_more?p.next_page:null;}
  return all;
}
async function atomic(path,bytes){const tmp=path+'.tmp';await writeFile(tmp,bytes);await rename(tmp,path);}
let catalog;try{catalog=JSON.parse(await readFile(join(output,'catalog.json'),'utf8'));}catch{catalog={schemaVersion:1,packages:[]};}
for(const code of sets){
  if(!/^[A-Z0-9]{2,8}$/.test(code)) throw Error('Invalid set code');
  const info=config[code] ? null : await json(`https://api.scryfall.com/sets/${code.toLowerCase()}`);
  if(info?.digital) throw Error('Digital-only set');
  const spec=config[code] || {name:info.name,sources:[{set:code,standalone:true}],coverage:`${code} only; EN images; annexes not reviewed`};
  validateSources(spec.sources);
  const refs=[];
  for(const source of spec.sources){
    const cards=selectCards(await setCards(source.set),source);
    for(const c of cards){
      const faces=c.image_uris?[{name:c.name,image_uris:c.image_uris}]:(c.card_faces||[]).filter(f=>f.image_uris);
      if(!faces.length) throw Error('Missing image '+c.id);
      faces.forEach((face,i)=>refs.push({printing:c.id+(i?'_back':''),scryfallId:c.id,rarity:c.rarity||'',setCode:c.set.toUpperCase(),collectorNumber:c.collector_number,label:`${face.name} · ${c.set.toUpperCase()} #${c.collector_number}`,image:face.image_uris.normal,finish:c.finishes.includes('nonfoil')?'nonfoil':c.finishes[0]}));
    }
  }
  refs.sort((a,b)=>a.printing.localeCompare(b.printing)); validateReferences(refs);
  const fingerprint=sha256(JSON.stringify({refs,width:0.063,compiler:sha256(await readFile(tool)),coverage:spec.coverage}));
  const version=fingerprint.slice(0,20), base=`${code.toLowerCase()}-${version}`;
  const existing=catalog.packages.find(p=>p.id===code && p.version===version);
  if(existing && !args.includes('--force')){
    try { const db=await readFile(join(output,existing.database.file)); const metadata=await readFile(join(output,existing.metadata.file));
    if(sha256(db)===existing.database.sha256 && sha256(metadata)===existing.metadata.sha256){console.log(`${code}: unchanged, skipped`);continue;}
    } catch(error) { if(error.code!=='ENOENT') throw error; }
  }
  let list='';let n=0;
  for(const ref of refs){
    const file=join(cache,sha256(ref.image)+'.jpg');
    try{await stat(file);}catch{await atomic(file,await fetchBytes(ref.image));}
    list+=`${ref.printing}|${file.replaceAll('\\','/')}|0.063\n`;
    if(++n%25===0) console.log(`${code}: images ${n}/${refs.length}`);
  }
  const listFile=join(cache,base+'.txt'),dbFile=join(output,base+'.imgdb');await writeFile(listFile,list);
  const compiled=join(cache,base+"-staging.imgdb");
  const run=spawnSync(tool,['build-db',`--input_image_list_path=${listFile}`,`--output_db_path=${compiled}`],{encoding:'utf8',timeout:1200000,maxBuffer:8*1024*1024});
  if(run.status!==0) throw Error(run.error?.message || run.stderr || run.stdout);
  await atomic(dbFile,await readFile(compiled));
  const metadata=Buffer.from(JSON.stringify({schemaVersion:1,id:code,version,widthMeters:0.063,references:refs}));
  await atomic(join(output,base+'.json'),metadata);
  const db=await readFile(dbFile); if(db.length>16*1024*1024) throw Error('Database too large');
  const entry={id:code,name:spec.name,version,coverage:spec.coverage,setCodes:spec.sources.map(s=>s.set),referenceCount:refs.length,database:{file:base+'.imgdb',bytes:db.length,sha256:sha256(db)},metadata:{file:base+'.json',bytes:metadata.length,sha256:sha256(metadata)}};
  catalog.packages=catalog.packages.filter(p=>p.id!==code).concat(entry).sort((a,b)=>a.name.localeCompare(b.name));
  catalog.generatedAt=new Date().toISOString();await atomic(join(output,'catalog.json'),JSON.stringify(catalog,null,2)+'\n');
  console.log(`${code}: ${refs.length} references, ${db.length} bytes, ${version}`);
}
