"""Frozen expanded reference gallery, CPU4 ONNX for all models. Development only."""
import os
os.environ['OPENBLAS_NUM_THREADS']='4'
import hashlib,json,sqlite3,time
from pathlib import Path
import cv2,numpy as np,onnxruntime as ort
from PIL import Image,ImageOps
R=Path(__file__).resolve().parent;O=R/'expanded-01';O.mkdir(exist_ok=True)
L=Path('C:/Users/nubor/.codex/worktrees/dinov2-benchmark/mtgfucker')
C=Path('C:/Users/nubor/.codex/worktrees/edscan-fastweb-diego/mtgfucker/build/direct-warp-01')
cv2.setNumThreads(1)
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def load(r):
    p=Path(r['path']);p=p if p.is_absolute() else R/p
    assert sha(p)==r['sha256'],str(p)
    with Image.open(p) as im:return ImageOps.exif_transpose(im).convert('RGB')
def save(p,data):
    tmp=p.with_suffix('.tmp');tmp.write_text(json.dumps(data,indent=2),encoding='utf8');tmp.replace(p)
def snapshot():
    path=O/'manifest.json'
    if path.exists():return json.loads(path.read_text(encoding='utf8'))
    old=json.loads((L/'build/dinov2-expanded/corpus/manifest.json').read_text(encoding='utf8'))
    refs=[r for r in old['references'] if r['lang']=='en'];queries=old['queries']
    for r in refs:r['key']=r['id']+'_f0'
    extra=json.loads((R/'pilot-01/supplemental.json').read_text(encoding='utf8'))
    for r in extra:
        r['key']=r['id'];refs.append(r)
        names=[r['source_crop']] if r['set']!='spg' else ['frozen-0-old.png','frozen-4-old.png','frozen-6-old.png']
        for name in names:
            p=C/name
            queries.append({'id':name,'path':str(p),'sha256':sha(p),'name':r['name'],'truth':{'illustration_id':r['illustration_id']},'full_art':r['set']=='spg','label_provenance':'Visual review of title/footer/illustration; SPG150 three frames of same card, not independent classes.'})
    seen={r['key'] for r in refs}
    db=sqlite3.connect(R/'manifest.sqlite');rows=db.execute('select metadata,sha256 from images where sha256 is not null order by id').fetchall()
    allmeta=[json.loads(raw) for raw, in db.execute('select metadata from images')]
    full={f"{r['set']}:{r['collector_number']}":r['full_art'] for r in allmeta}
    for raw,h in rows:
        r=json.loads(raw)
        if r['id'] not in seen:
            r['key']=r['id'];r['sha256']=h;refs.append(r);seen.add(r['id'])
    arts={r.get('illustration_id') for r in refs}
    queries=[q for q in queries if q['truth'].get('illustration_id') in arts]
    for q in queries:q['full_art']=q.get('full_art',full.get(q['truth'].get('printing'),False))
    train=json.loads((R/'pilot-01/split.json').read_text(encoding='utf8'))['train']
    assert not ({r['illustration_id'] for r in train}&{q['truth']['illustration_id'] for q in queries})
    m={'refs':refs,'queries':queries,'downloaded_snapshot':len(rows),'created':time.strftime('%Y-%m-%d %H:%M:%S'),'scope':'All downloaded faces plus previous English benchmark refs. Not complete Scryfall catalogue. Frozen, no extra training.'}
    save(path,m);return m
def main():
    m=snapshot();refs=m['refs'];queries=m['queries'];outputs={}
    for name in ['milo','diego','own']:
        path=O/'own-fp32.onnx' if name=='own' else L/f'build/diego-android-assets-v2/{name}.onnx'
        opt=ort.SessionOptions();opt.intra_op_num_threads=4;opt.inter_op_num_threads=1
        sess=ort.InferenceSession(str(path),opt,providers=['CPUExecutionProvider'])
        def encode(im):
            if name=='diego':
                a=cv2.resize(np.asarray(im),(320,448),interpolation=cv2.INTER_LINEAR_EXACT).astype(np.float32)
                x=((a-np.float32([.485,.456,.406])*255)/(np.float32([.229,.224,.225])*255)).transpose(2,0,1)[None]
            else:
                size=(448,448) if name=='milo' else (320,448)
                a=np.asarray(im.resize(size,Image.Resampling.BILINEAR),np.float32)/255
                x=((a-np.float32([.485,.456,.406]))/np.float32([.229,.224,.225])).transpose(2,0,1)[None]
            z=sess.run(None,{sess.get_inputs()[0].name:np.ascontiguousarray(x)})[0].ravel()
            assert np.isfinite(z).all() and np.linalg.norm(z)>0
            return z/np.linalg.norm(z)
        dim=len(encode(load(refs[0])));gpath=O/f'{name}-gallery.npy';progress=O/f'{name}-progress.json'
        fingerprint=sha(O/'manifest.json')+sha(path)
        if progress.exists():
            state=json.loads(progress.read_text());assert state['fingerprint']==fingerprint
            start=state['done'];gallery=np.lib.format.open_memmap(gpath,mode='r+')
        else:
            start=0;gallery=np.lib.format.open_memmap(gpath,mode='w+',dtype=np.float32,shape=(len(refs),dim))
        for i in range(start,len(refs)):
            if (R/'STOP_EVAL').exists():return
            gallery[i]=encode(load(refs[i]))
            if (i+1)%250==0 or i+1==len(refs):
                gallery.flush();save(progress,{'fingerprint':fingerprint,'done':i+1,'total':len(refs)})
                print(name,'gallery',i+1,'/',len(refs),flush=True)
        rows=[];timings=[]
        for q in queries:
            im=load(q);encode(im)
            for repeat in range(3):
                t=time.perf_counter();z=encode(im);enc=(time.perf_counter()-t)*1000
                t=time.perf_counter();scores=gallery@z;search=(time.perf_counter()-t)*1000
                timings.append([enc,search])
            order=np.argsort(-scores);top=int(order[0]);art=refs[top].get('illustration_id')
            different=next(int(i) for i in order if refs[i].get('illustration_id')!=art)
            # Proxy of a missing identity, not a substitute for background/other-TCG negatives.
            allowed=[int(i) for i in order if refs[i]['name'].casefold()!=q['name'].casefold() and refs[i].get('illustration_id')!=q['truth']['illustration_id']]
            absent=allowed[0]
            rows.append({'query':q['id'],'truth_name':q['name'],'full_art':q['full_art'],'predicted':refs[top]['name'],'art_correct':art==q['truth']['illustration_id'],'score':float(scores[top]),'margin_other_art':float(scores[top]-scores[different]),'absent_identity_proxy_score':float(scores[absent]),'top5':[{'name':refs[int(i)]['name'],'art':refs[int(i)].get('illustration_id'),'score':float(scores[int(i)])} for i in order[:5]]})
        ts=np.array(timings);outputs[name]={'correct':sum(r['art_correct'] for r in rows),'n':len(rows),'full_art_correct':sum(r['art_correct'] for r in rows if r['full_art']),'full_art_n':sum(r['full_art'] for r in rows),'encoder_p50_ms':float(np.median(ts[:,0])),'encoder_p95_ms':float(np.percentile(ts[:,0],95)),'search_p50_ms':float(np.median(ts[:,1])),'model_sha256':sha(path),'rows':rows}
        save(O/f'{name}-results.json',outputs[name]);print(name,'RESULT',outputs[name]['correct'],'/',len(rows),flush=True)
    save(O/'results.json',{'gallery':len(refs),'downloaded_snapshot':m['downloaded_snapshot'],'scope':'CPU4 ORT all models; same frozen references and crops; no detector. Development not blind. Fullart4frames2identities. No accepted-scan/false-positive claim; proxy only.', 'models':outputs})
if __name__=='__main__':main()
