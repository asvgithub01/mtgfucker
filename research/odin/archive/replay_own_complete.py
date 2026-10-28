import os
os.environ['OPENBLAS_NUM_THREADS']='4'
from pathlib import Path
import json,time
import numpy as np,onnxruntime as ort
from PIL import Image
R=Path(__file__).resolve().parent;G=R/'own02-complete-gallery-01';status=json.loads((G/'status.json').read_text(encoding='utf8'));assert status['phase']=='complete_pending_replay'
O=G/'replay';O.mkdir(exist_ok=False);refs=json.loads((G/'refs.json').read_text(encoding='utf8'));mat=np.load(G/'gallery.npy',mmap_mode='r');opt=ort.SessionOptions();opt.intra_op_num_threads=4;opt.inter_op_num_threads=1;s=ort.InferenceSession(str(R/'pilot-02/own-fp32.onnx'),opt)
def encode(im):
 a=np.asarray(im.resize((320,448),Image.Resampling.BILINEAR),np.float32)/255;a=(a-np.float32([.485,.456,.406]))/np.float32([.229,.224,.225]);z=s.run(None,{'image':np.ascontiguousarray(a.transpose(2,0,1)[None])})[0].ravel();return z/np.linalg.norm(z)
def run(items):
 out=[]
 for x in items:
  p=Path(x['path']);row={k:v for k,v in x.items() if k!='path'}
  if p.exists():
   im=Image.open(p).convert('RGB');z=encode(im);t=time.perf_counter();scores=mat@z;order=np.argsort(-scores);k=int(order[0]);art=refs[k]['illustration_id'];other=next(int(i) for i in order if refs[int(i)]['oracle_id']!=refs[k]['oracle_id'])
   row.update(predicted=refs[k]['name'],art=art,correct_art=bool(x.get('truth_art') and art==x['truth_art']),score=float(scores[k]),identity_margin=float(scores[k]-scores[other]),search_with_sort_ms=(time.perf_counter()-t)*1000,top8=[{'id':refs[int(i)]['scryfall_id'],'name':refs[int(i)]['name'],'score':float(scores[i])} for i in order[:8]])
  else:row['correct_art']=False
  out.append(row)
 return {'n':sum(x.get('truth_art') is not None for x in items),'correct':sum(x['correct_art'] for x in out),'rows':out}
ss=json.loads((R/'sony-failures-01/samples.json').read_text(encoding='utf8'));lab=json.loads((R/'sony-failures-01/labels-and-coverage.json').read_text(encoding='utf8'));arts={x['name']:x['art'] for x in lab['coverage']};items=[{'id':x['index'],'path':str(Path(x['path'])/'crop.png'),'truth':lab['labels'][str(x['index'])],'truth_art':arts.get(lab['labels'][str(x['index'])])} for x in ss];res={'sony_failures':run(items)};print('sony',res['sony_failures']['correct'],res['sony_failures']['n'],flush=True)
old=json.loads((R/'heldout-eval-01/results.json').read_text(encoding='utf8'))['models']['own02']['rows'];labels=json.loads((R/'heldout-eval-01/labels-frozen.json').read_text(encoding='utf8'));items=[{'id':x['index'],'path':str(R/'heldout-eval-01'/f"{x['index']:02}-crop.png"),'truth':x['truth'],'truth_art':labels[x['group']]['art']} for x in old];res['reserved']=run(items);print('reserved',res['reserved']['correct'],res['reserved']['n'],flush=True)
qs=json.loads((R/'expanded-01/manifest.json').read_text(encoding='utf8'))['queries'];res['development']=run([{'id':x['id'],'path':str(Path(x['path']) if Path(x['path']).is_absolute() else R/x['path']),'truth_art':x['truth']['illustration_id']} for x in qs]);print('development',res['development']['correct'],res['development']['n'],flush=True)
res['scope']='FrozenOwn02, complete100626frontgallery, no threshold tuning/training. Top1 art is not acceptance. Sony failures are development, reserved23 only6identities. No threshold-based false-positive conclusion from few negatives.'
(O/'results.json').write_text(json.dumps(res,indent=2),encoding='utf8')
