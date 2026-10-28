"""Paired development benchmark. Identical queries/gallery, model-specific input sizes.
Not Android latency, full-catalog accuracy, or a held-out final test.
"""
import hashlib, json, time
from pathlib import Path
import cv2, numpy as np, onnxruntime as ort
from PIL import Image, ImageEnhance, ImageFilter, ImageOps
import torch
from pilot import Encoder, tensor
ROOT=Path(__file__).resolve().parent; OUT=ROOT/'pilot-01'
LAB=Path('C:/Users/nubor/.codex/worktrees/dinov2-benchmark/mtgfucker')
torch.set_num_threads(4); cv2.setNumThreads(1)

def load(row):
    p=Path(row['path']); p=p if p.is_absolute() else ROOT/p
    assert hashlib.sha256(p.read_bytes()).hexdigest()==row['sha256'],str(p)
    with Image.open(p) as im:return ImageOps.exif_transpose(im).convert('RGB')

def main():
    split=json.loads((OUT/'split.json').read_text(encoding='utf8')); val=split['validation']
    refs=[load(r) for r in val]; queries=[]; truth=[]; full=[]
    for i,im in enumerate(refs):
        for mode in range(2):
            # Fixed transformations different from the training stochastic recipe.
            q=im.resize((128,179) if mode==0 else (180,252),Image.Resampling.BILINEAR).resize((320,448),Image.Resampling.BILINEAR)
            q=q.filter(ImageFilter.GaussianBlur(1.2 if mode==0 else 2.2))
            q=ImageEnhance.Contrast(q).enhance(.72 if mode==0 else 1.2)
            q=ImageEnhance.Brightness(q).enhance(.8 if mode==0 else 1.15)
            queries.append(q); truth.append(i); full.append(val[i]['full_art'])
    manifest=json.loads((LAB/'build/dinov2-expanded/corpus/manifest.json').read_text(encoding='utf8'))
    # Reference-only English gallery; no language classification or multilingual advantage.
    realrefs=[r for r in manifest['references'] if r['lang']=='en']
    arts={r['illustration_id'] for r in realrefs}
    realq=[r for r in manifest['queries'] if r.get('truth',{}).get('illustration_id') in arts]
    supplemental=json.loads((OUT/'supplemental.json').read_text(encoding='utf8'))
    croproot=Path('C:/Users/nubor/.codex/worktrees/edscan-fastweb-diego/mtgfucker/build/direct-warp-01')
    for r in supplemental:
        realrefs.append(r)
        p=croproot/r['source_crop']
        realq.append({'id':r['source_crop'],'name':r['name'],'path':str(p),'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'truth':{'illustration_id':r['illustration_id'],'printing':f"{r['set']}:{r['collector_number']}"},'manual_full_art':r['set']=='spg','label_provenance':'Visual reading of title, footer and illustration on frozen crop; not inferred from model top1.'})
    import sqlite3
    rows=sqlite3.connect(ROOT/'manifest.sqlite').execute('select metadata from images').fetchall()
    byprinting={}
    for raw, in rows:
        r=json.loads(raw); byprinting[f"{r['set']}:{r['collector_number']}"]=r['full_art']
    realfull=[q.get('manual_full_art',byprinting.get(q['truth'].get('printing'),False)) for q in realq]
    realimages=[load(r) for r in realq]; realgallery=[load(r) for r in realrefs]
    assert not ({r['illustration_id'] for r in split['train']} & {q['truth']['illustration_id'] for q in realq}), 'Real query artwork leaked into training'
    opts=ort.SessionOptions();opts.intra_op_num_threads=4;opts.inter_op_num_threads=1
    results={}
    for name in ['milo','diego','initial','trained']:
        if name in ('milo','diego'):
            modelpath=LAB/f'build/diego-android-assets-v2/{name}.onnx'
            model=ort.InferenceSession(str(modelpath),opts,providers=['CPUExecutionProvider'])
        else:
            modelpath=OUT/f'{name}.pt'; model=Encoder(False).eval()
            model.load_state_dict(torch.load(modelpath,map_location='cpu',weights_only=True))
        def encode(im):
            if name=='milo':
                a=np.asarray(im.resize((448,448),Image.Resampling.BILINEAR),np.float32)/255
                x=np.ascontiguousarray(((a-np.float32([.485,.456,.406]))/np.float32([.229,.224,.225])).transpose(2,0,1)[None])
            elif name=='diego':
                a=cv2.resize(np.asarray(im),(320,448),interpolation=cv2.INTER_LINEAR_EXACT).astype(np.float32)
                x=np.ascontiguousarray(((a-np.float32([.485,.456,.406])*255)/(np.float32([.229,.224,.225])*255)).transpose(2,0,1)[None])
            else:
                with torch.inference_mode(): z=model(tensor(im)[None]).numpy().ravel()
                return z/np.linalg.norm(z)
            z=model.run(None,{model.get_inputs()[0].name:x})[0].ravel()
            return z/np.linalg.norm(z)
        encode(refs[0]); encode(refs[0]); times=[]
        g=np.stack([encode(im) for im in refs]); rg=np.stack([encode(im) for im in realgallery])
        correct=[]; realcorrect=[]; predictions=[]
        for i,im in enumerate(queries):
            t=time.perf_counter(); z=encode(im); times.append((time.perf_counter()-t)*1000)
            pred=int(np.argmax(g@z)); correct.append(pred==truth[i])
        for i,im in enumerate(realimages):
            z=encode(im); sims=rg@z; pred=int(np.argmax(sims)); ok=realrefs[pred]['illustration_id']==realq[i]['truth']['illustration_id']
            realcorrect.append(ok); predictions.append({'query':realq[i]['id'],'name':realq[i]['name'],'predicted':realrefs[pred]['name'],'art_correct':ok,'score':float(sims[pred]),'full_art_flag':realfull[i]})
        def metric(c,mask):
            subset=[x for x,m in zip(c,mask) if m];return {'correct':sum(subset),'n':len(subset),'recall1':sum(subset)/len(subset) if subset else None}
        results[name]={'weights_sha256':hashlib.sha256(modelpath.read_bytes()).hexdigest(),'synthetic':metric(correct,[True]*len(correct)),'synthetic_full_art':metric(correct,full),'real':metric(realcorrect,[True]*len(realcorrect)),'real_full_art':metric(realcorrect,realfull),'encode_cpu4_p50_ms':float(np.median(times)),'encode_cpu4_p95_ms':float(np.percentile(times,95)),'real_predictions':predictions}
        print(name,json.dumps({k:v for k,v in results[name].items() if k!='real_predictions'}),flush=True)
        (OUT/'evaluation.json').write_text(json.dumps({'scope':'Development only. Synthetic 256-art gallery, real English-reference restricted gallery; no confidence rejection. CPU4 preprocess+encoder, excludes search/IO/init; PyTorch own versus ORT baselines not deployment parity. Not Sony. Full-art flags incomplete; SPG150 explicitly visually annotated.', 'synthetic_gallery':len(refs),'real_gallery':len(realrefs),'real_queries':len(realq),'excluded_real_queries_no_english_art':len(manifest['queries'])+len(supplemental)-len(realq),'results':results},indent=2),encoding='utf8')

if __name__=='__main__':main()
