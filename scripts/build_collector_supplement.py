"""Build FRC + Reality Fracture SPG supplemental Milo vectors from cached Scryfall metadata.
Inputs: build/collector-coverage/{frc,spg}.json (API queries set:frc and set:spg date:2026-10-02).
Run using build/docaligner-venv/Scripts/python.exe. Existing published FRA assets remain unchanged.
"""
import json,hashlib,time,urllib.request
from pathlib import Path
import numpy as np
from PIL import Image
import onnxruntime as ort
root=Path(__file__).resolve().parents[1];work=root/'build/collector-coverage'
model=root/'build/collectorvision-native-assets/milo.onnx'
assert hashlib.sha256(model.read_bytes()).hexdigest()=='bd13d8d60383c69da04dce261f32e93fdaeaa8fd618fbc991e7385f71b3d45df'
base=set(json.loads((root/'build/collectorvision-native-assets/scryfall-mtg-card-ids.json').read_text()))
assets=root/'cameraVision/src/main/assets/edscan-fra'
base.update(json.loads((assets/'fra-20260928-card-ids.json').read_text()))
opts=ort.SessionOptions();opts.intra_op_num_threads=4
session=ort.InferenceSession(str(model),sess_options=opts,providers=['CPUExecutionProvider'])
mean=np.array([.485,.456,.406],np.float32);std=np.array([.229,.224,.225],np.float32)
ids=[];vectors=[];source=[]
for code in ['frc','spg']:
 for c in json.loads((work/(code+'.json')).read_text(encoding='utf-8')):
  assert 'paper' in c['games']
  if c['id'] in base:continue
  face=c if c.get('image_uris') else c['card_faces'][0]
  url=face['image_uris']['normal'];photo=work/(c['id']+'.jpg')
  if not photo.exists():
   req=urllib.request.Request(url,headers={'User-Agent':'MtgOcr/1.0'})
   photo.write_bytes(urllib.request.urlopen(req,timeout=30).read());time.sleep(.12)
  x=np.asarray(Image.open(photo).convert('RGB').resize((448,448),Image.Resampling.BILINEAR),dtype=np.float32)/255
  x=((x-mean)/std).transpose(2,0,1)[None].copy()
  v=session.run(['embedding'],{session.get_inputs()[0].name:x})[0].reshape(-1);v=v/np.linalg.norm(v)
  assert v.shape==(128,) and np.isfinite(v).all()
  ids.append(c['id']);vectors.append(v);source.append({'id':c['id'],'set':c['set'],'number':c['collector_number'],'imageSha256':hashlib.sha256(photo.read_bytes()).hexdigest()})
  print(len(ids),c['name'],flush=True)
assert len(ids)==113 and len(set(ids))==113
prefix='collector-20260928'
emb=assets/(prefix+'-embeddings.f16.bin');ident=assets/(prefix+'-card-ids.json')
emb.write_bytes(np.stack(vectors).astype('<f2').tobytes());ident.write_text(json.dumps(ids,separators=(',',':')),encoding='utf-8')
meta={'rows':len(ids),'modelSha256':hashlib.sha256(model.read_bytes()).hexdigest(),'files':{p.name:{'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in [emb,ident]},'sources':source}
(assets/(prefix+'-provenance.json')).write_text(json.dumps(meta,indent=2),encoding='utf-8');print(json.dumps(meta['files']),flush=True)
