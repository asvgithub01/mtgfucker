import test from 'node:test';import assert from 'node:assert/strict';
import {validateSources,selectCards,validateReferences,sha256} from '../src/policy.mjs';
test('SPG must be explicitly scoped',()=>{assert.throws(()=>validateSources([{set:'SPG'}]));validateSources([{set:'FRA'},{set:'SPG',numbers:['159']}]);});
test('missing annex fails rather than publishing partial',()=>assert.throws(()=>selectCards([],{set:'SPG',numbers:['159']})));
test('no silent truncation or duplicate references',()=>{assert.throws(()=>validateReferences([]));assert.throws(()=>validateReferences(Array.from({length:1001},(_,i)=>({printing:String(i)}))));assert.throws(()=>validateReferences([{printing:'a'},{printing:'a'}]));});
test('stable content checksum',()=>assert.equal(sha256('a'),sha256(Buffer.from('a'))));
import {readFile} from 'node:fs/promises';
const root=new URL('../../../web/public/ar/',import.meta.url);
test('published catalog checksums and reference identities match',async()=>{
  const catalog=JSON.parse(await readFile(new URL('catalog.json',root),'utf8'));
  assert.equal(catalog.schemaVersion,1);
  for(const p of catalog.packages){
    for(const kind of ['database','metadata']){
      const a=p[kind],bytes=await readFile(new URL(a.file,root));assert.equal(bytes.length,a.bytes);assert.equal(sha256(bytes),a.sha256);
    }
    const metadata=JSON.parse(await readFile(new URL(p.metadata.file,root),'utf8'));
    assert.equal(metadata.version,p.version);assert.equal(metadata.id,p.id);assert.equal(metadata.references.length,p.referenceCount);validateReferences(metadata.references);
    assert.equal(metadata.widthMeters,0.063);
  }
});

// The SPG set itself is not an automatically guessed annex.
test("standalone SPG is allowed but unscoped annex is rejected",()=>{
  validateSources([{set:"SPG",standalone:true}]);
  assert.throws(()=>validateSources([{set:"SPG"}]));
});
