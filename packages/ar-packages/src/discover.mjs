#!/usr/bin/env node
import {readFile,writeFile,mkdir,rename} from 'node:fs/promises';
import {resolve,join} from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {sha256} from './policy.mjs';
const args=process.argv.slice(2),option=(key,fallback)=>args.includes(key)?args[args.indexOf(key)+1]:fallback;
if(args.includes('--help')) {
  console.log('discover:ar [--output web/public/ar] [--generate-missing] [--arcoreimg PATH]\nLists current paper sets and verifies published artifacts. Generation is opt-in; annexes require editions.json coverage.');
} else {
  const output=resolve(option('--output','web/public/ar'));
  await mkdir(output,{recursive:true});
  const response=await fetch('https://api.scryfall.com/sets',{headers:{'User-Agent':'MtgOcr-ArPackages/1.0','Accept':'application/json'},signal:AbortSignal.timeout(45000)});
  if(!response.ok) throw Error(`Set discovery HTTP ${response.status}`);
  const body=await response.json();if(!Array.isArray(body.data)) throw Error('Invalid set response');
  let catalog={packages:[]};try {catalog=JSON.parse(await readFile(join(output,'catalog.json'),'utf8'));} catch(e) {if(e.code!=='ENOENT') throw e;}
  const editions=[];
  for(const set of body.data.filter(s=>!s.digital && s.released_at<=new Date().toISOString().slice(0,10) && !['token','memorabilia','minigame'].includes(s.set_type))) {
    const id=set.code.toUpperCase();if(!/^[A-Z0-9]{2,8}$/.test(id)) continue;
    const entry=catalog.packages.find(p=>p.id===id);
    let ready=false;
    if(entry) ready=await Promise.all([entry.database,entry.metadata].map(async artifact=> {
      if(!/^[a-z0-9-]+\.(imgdb|json)$/.test(artifact.file)) return false;
      try {const data=await readFile(join(output,artifact.file));return data.length===artifact.bytes && sha256(data)===artifact.sha256;} catch{return false;}
    })).then(results=>results.every(Boolean));
    editions.push({id,name:set.name,releasedAt:set.released_at,referenceEstimate:set.card_count,status:ready?'ready':'missing'});
  }
  const file=join(output,'editions.json');
  await writeFile(file+'.tmp',JSON.stringify({schemaVersion:1,generatedAt:new Date().toISOString(),editions},null,2)+'\n');await rename(file+'.tmp',file);
  const missing=editions.filter(e=>e.status==='missing');console.log(`${editions.length} editions; ${missing.length} missing or damaged DBs. ${file}`);
  if(args.includes('--generate-missing')) {
    const failures=[];
    const startedAt=new Date().toISOString();let completed=0;
    const progressFile=join(output,'generation-status.json');
    async function report(current,finished=false) {
      await writeFile(progressFile+'.tmp',JSON.stringify({startedAt,updatedAt:new Date().toISOString(),total:missing.length,completed,current,failures,finished},null,2)+'\n');
      await rename(progressFile+'.tmp',progressFile);
    }
    for(const edition of missing) {
      await report(edition.id);
      console.log(`[${completed+1}/${missing.length}] ${edition.id} ${edition.name}`);
      const extra=args.includes('--arcoreimg')?['--arcoreimg',option('--arcoreimg')]:[];
      const result=spawnSync(process.execPath,[fileURLToPath(new URL('./build.mjs',import.meta.url)),'--sets',edition.id,'--output',output,...extra],{stdio:'inherit'});
      if(result.status!==0) failures.push(edition.id); else edition.status='ready';
      completed++;await report(null);
    }
    await writeFile(file+'.tmp',JSON.stringify({schemaVersion:1,generatedAt:new Date().toISOString(),editions},null,2)+'\n');await rename(file+'.tmp',file);
    await report(null,true);
    console.log(`Failed (not silently truncated): ${failures.join(', ') || 'none'}`);
    if(failures.length) process.exitCode=1;
  }
}
