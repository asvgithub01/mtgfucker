#!/usr/bin/env python3
"""Offline smoke test of FRA embeddings under brightness/JPEG perturbation, not camera accuracy."""
import io,json,hashlib
from pathlib import Path
import numpy as np
from PIL import Image,ImageEnhance
import onnxruntime as ort
ROOT=Path(__file__).resolve().parents[1];WORK=ROOT/'build/reality-fracture-indices';BASE=ROOT/'build/collectorvision-native-assets';EXTRA=ROOT/'collectorvision-native/src/main/assets/edscan-fra'
base_ids=json.loads((BASE/'scryfall-mtg-card-ids.json').read_text());extra_ids=json.loads((EXTRA/'fra-20260928-card-ids.json').read_text());ids=base_ids+extra_ids
vectors=np.concatenate([np.fromfile(BASE/'scryfall-mtg-embeddings.f16.bin',dtype='<f2').reshape(-1,128),np.fromfile(EXTRA/'fra-20260928-embeddings.f16.bin',dtype='<f2').reshape(-1,128)]).astype(np.float32)
assert len(ids)==len(vectors) and len(ids)==len(set(ids)) and np.isfinite(vectors).all()
opts=ort.SessionOptions();opts.intra_op_num_threads=4
s=ort.InferenceSession(str(BASE/'milo.onnx'),sess_options=opts,providers=['CPUExecutionProvider'])
mean=np.array([.485,.456,.406],dtype=np.float32);std=np.array([.229,.224,.225],dtype=np.float32)
results=[]
for id in extra_ids[::max(1,len(extra_ids)//16)][:16]:
    img=ImageEnhance.Brightness(Image.open(WORK/(id+'.jpg')).convert('RGB')).enhance(.8)
    stream=io.BytesIO();img.save(stream,format='JPEG',quality=70);stream.seek(0)
    x=np.asarray(Image.open(stream).resize((448,448),Image.Resampling.BILINEAR),dtype=np.float32)/255
    x=((x-mean)/std).transpose(2,0,1)[None].copy()
    v=s.run(['embedding'],{s.get_inputs()[0].name:x})[0].reshape(-1);v/=np.linalg.norm(v)
    score=vectors@v;top=np.argsort(score)[-3:][::-1]
    results.append(dict(expected=id,top=[dict(id=ids[i],score=float(score[i])) for i in top],exactTop1=ids[top[0]]==id))
report=dict(test='Brightness 0.8 + JPEG quality70 on reference images (not camera)',rows=len(ids),samples=len(results),exactTop1=sum(r['exactTop1'] for r in results),results=results)
(WORK/'embedding-validation.json').write_text(json.dumps(report,indent=2));print(json.dumps({k:v for k,v in report.items() if k!='results'}))
