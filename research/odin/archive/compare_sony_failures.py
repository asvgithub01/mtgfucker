import os
os.environ['OPENBLAS_NUM_THREADS']='4'
from pathlib import Path
import json,time,hashlib
import numpy as np,cv2,onnxruntime as ort
from PIL import Image
R=Path(__file__).resolve().parent;O=R/'sony-failures-01';L=Path('C:/Users/nubor/.codex/worktrees/dinov2-benchmark/mtgfucker');cv2.setNumThreads(1)
a=json.loads((O/'samples.json').read_text());cat=json.loads((O/'catalog-candidates.json').read_text());base=json.loads((R/'expanded-01/manifest.json').read_text())['refs'];full=json.loads((R/'pilot-02/catalog-manifest.json').read_text())['refs'];androidids=json.loads((R/'pilot-02/android-bundle/ids.json').read_text());idset=set(androidids)
ranges=[(0,7,'Requisition Raid','soa','10'),(8,25,'Deathlace','4ed','131'),(27,29,'Disintegrate','4ed','185'),(31,32,'Opal Guardian','tsp','30'),(35,40,'Charging Monstrosaur','xln','138'),(42,48,'Frozen Shade','4ed','138'),(49,53,'Northern Paladin','4ed','37'),(55,58,'Throne of the Grim Captain','lci','313'),(59,62,'Caparocti Sunborn','lci','302'),(63,68,'Captain Storm, Cosmium Raider','lci','303'),(70,72,'Uchbenbak, the Great Mistake','lci','310'),(73,79,'Bloodletter of Aclazotz','lci','336'),(81,94,'Twisted Abomination','tsb','50')]
neg={26,30,33,34,38,41,54,69,80};truth={};coverage=[];extra=[];known={r['illustration_id'] for r in base}
def uid(r):return r.get('scryfall_id',r['id'].split('_f')[0])
for lo,hi,name,code,num in ranges:
 ref=next(x for x in cat[name] if x['set']==code and x['collector_number']==num);art=ref['illustration_id'];truth[name]=ref
 for i in range(lo,hi+1):
  if i not in neg:a[i]['truth']=name
 existing=[x for x in full if uid(x) in idset]
 coverage.append({'name':name,'art':art,'shared_gallery_art':art in known,'sony_gallery_name':any(x['name']==name for x in existing),'sony_gallery_art':any(x['illustration_id']==art for x in existing),'scryfall_reference':ref})
 if art not in known:extra.append(ref);known.add(art)
(O/'labels-and-coverage.json').write_text(json.dumps({'provenance':'Manual visual title/art/footer review against downloaded Scryfall references BEFORE model inference. Language/physical printing not inferred from English gallery. Negative/transitions excluded from card accuracy; occluded cards retained. No training use.','coverage':coverage,'negative_indices':sorted(neg),'labels':{str(x['index']):x.get('truth') for x in a}},indent=2))
aug=base+extra;outputs={};opt=ort.SessionOptions();opt.intra_op_num_threads=4;opt.inter_op_num_threads=1
specs={'own02':(R/'pilot-02/own-fp32.onnx',R/'pilot-02/gallery.npy'),'milo':(L/'build/diego-android-assets-v2/milo.onnx',R/'expanded-01/milo-gallery.npy'),'diego':(L/'build/diego-android-assets-v2/diego.onnx',R/'expanded-01/diego-gallery.npy')}
for name,(mp,gp) in specs.items():
 s=ort.InferenceSession(str(mp),opt,providers=['CPUExecutionProvider']);g=np.load(gp,mmap_mode='r');gbase=g[:len(base)]
 def encode(im):
  if name=='diego':
   ar=cv2.resize(np.asarray(im),(320,448),interpolation=cv2.INTER_LINEAR_EXACT).astype(np.float32);ar=(ar-np.float32([.485,.456,.406])*255)/(np.float32([.229,.224,.225])*255)
  else:
   ar=np.asarray(im.resize((448,448) if name=='milo' else (320,448),Image.Resampling.BILINEAR),dtype=np.float32)/255;ar=(ar-np.float32([.485,.456,.406]))/np.float32([.229,.224,.225])
  z=s.run(None,{s.get_inputs()[0].name:np.ascontiguousarray(ar.transpose(2,0,1)[None])})[0].ravel();return z/np.linalg.norm(z)
 ge=np.stack([encode(Image.open(R/x['path']).convert('RGB')) for x in extra]);ga=np.concatenate([gbase,ge]);rows=[]
 if name=='own02':
  chosen=[i for i,r in enumerate(full) if uid(r) in idset and r.get('face',0)==0];gf=g[chosen];mf=[full[i] for i in chosen]
 for x in a:
  crop=Path(x['path'])/'crop.png';row={'index':x['index'],'truth':x.get('truth'),'has_crop':crop.exists()}
  if crop.exists():
   im=Image.open(crop).convert('RGB');encode(im);times=[]
   for _ in range(3):
    t=time.perf_counter();z=encode(im);times.append((time.perf_counter()-t)*1000)
   row['encode_ms']=float(np.median(times))
   for mode,mat,meta in [('shared',gbase,base),('added_references_diagnostic',ga,aug)]+([('sony_gallery',gf,mf)] if name=='own02' else []):
    t=time.perf_counter();v=mat@z;order=np.argsort(-v);k=int(order[0]);row[mode]={'predicted':meta[k]['name'],'art':meta[k]['illustration_id'],'score':float(v[k]),'correct_art':bool(x.get('truth') and meta[k]['illustration_id']==truth[x['truth']]['illustration_id']),'search_ms':(time.perf_counter()-t)*1000,'top3':[{'name':meta[int(i)]['name'],'score':float(v[i])} for i in order[:3]]}
  rows.append(row)
 outputs[name]=rows;print(name,'done',flush=True)
 result={'scope':'Same saved crops, upright only. Baseline same17702 refs. Diagnostic adds ONLY known missing target arts: not unbiased accuracy, not production update. No thresholds changed. Encoder CPU4 includes preprocessing but not file read; not Sony latency.','shared_rows':len(base),'diagnostic_added_refs':len(extra),'models':outputs}
 (O/'results.json').write_text(json.dumps(result,indent=2),encoding='utf8')
print('complete',len(a),'samples',len(extra),'added refs',flush=True)
