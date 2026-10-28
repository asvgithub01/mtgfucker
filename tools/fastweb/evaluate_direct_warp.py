"""Paired frozen-frame comparison; no detector/threshold changes or writes to inputs."""
from pathlib import Path
import sys,json,time,hashlib
sys.path.insert(0,'C:/Users/nubor/.codex/worktrees/dinov2-benchmark/mtgfucker/build/dhvazquez-python')
import cv2,numpy as np,onnxruntime as ort
from PIL import Image
sys.stdout.reconfigure(encoding='utf8')
root=Path(__file__).resolve().parents[2];lab=Path('C:/Users/nubor/.codex/worktrees/dinov2-benchmark/mtgfucker')
out=root/'build/direct-warp-01';out.mkdir(exist_ok=True)
a=lab/'build/diego-android-assets-v2';opt=ort.SessionOptions();opt.intra_op_num_threads=4;opt.inter_op_num_threads=1
encoder=ort.InferenceSession(str(a/'diego.onnx'),opt);search=ort.InferenceSession(str(a/'diego-search.onnx'),opt)
ids=json.loads((a/'full-ids.json').read_text());labels=json.loads((a/'scan-labels.json').read_text(encoding='utf8'))
mean=np.float32([.485,.456,.406]);std=np.float32([.229,.224,.225]);cv2.setNumThreads(1)
cases=[]
for i,r in enumerate(json.loads((root/'build/fastweb-test-assets/expected.json').read_text())):
 cases.append(dict(name=f'frozen-{i}',path=root/'build/fastweb-test-assets'/r['file'],q=r['quads'][0],sha=r['sha256']))
for p in sorted((root/'build/triple-diego-input-01/evidence/cache/fastweb-triple-probe').glob('sample-*')):
 r=json.loads((p/'result.json').read_text());cases.append(dict(name=p.name,path=p/'upright.png',q=r['quad']))
pc=lab/'build/fastweb-pc-01/evaluation/results.json'
if pc.exists():
 for i,r in enumerate(json.loads(pc.read_text())['rows']):
  m=r['modes']['fastweb'][-1]
  if m['valid']:
   p=Path(r['path']);p=p if p.is_absolute() else lab/p
   if p.exists():cases.append(dict(name=f'archive-{i}',path=p,q=m['quad'],sha=r['sha256']))
seen=set();rows=[]
for c in cases:
 sha=hashlib.sha256(c['path'].read_bytes()).hexdigest();assert sha==c.get('sha',sha)
 if sha in seen:continue
 seen.add(sha);im=np.array(Image.open(c['path']).convert('RGB'));h,w=im.shape[:2];q=np.float32(c['q'])*np.float32([w,h]);modes={}
 for mode in ['old','direct']:
  target=np.float32([[0,0],[639,0],[639,895],[0,895]]) if mode=='old' else np.float32([[0,0],[320,0],[320,448],[0,448]])
  # Same geometric ordering as author; avoid changing detector output between arms.
  src=q
  if mode=='direct':
   s=q.sum(1);d=np.diff(q,axis=1).ravel();src=np.float32([q[s.argmin()],q[d.argmin()],q[s.argmax()],q[d.argmax()]])
  samples=[]
  for rep in range(4):
   t=time.perf_counter();M=cv2.getPerspectiveTransform(src,target);crop=cv2.warpPerspective(im,M,(640,896) if mode=='old' else (320,448),flags=cv2.INTER_LINEAR)
   rgb=cv2.resize(crop,(320,448),interpolation=cv2.INTER_LINEAR_EXACT) if mode=='old' else crop
   x=((rgb.astype(np.float32)-mean*255)/(std*255)).transpose(2,0,1)[None].copy();prep=(time.perf_counter()-t)*1000
   v=encoder.run(None,{encoder.get_inputs()[0].name:x})[0].ravel();v/=np.linalg.norm(v);result=search.run(None,{'query':np.stack([v,v])});ix=np.asarray(result[1]).ravel();scores=np.asarray(result[0]).ravel()
   if rep>0:samples.append(dict(preprocess_ms=prep,total_ms=(time.perf_counter()-t)*1000))
  Image.fromarray(rgb).save(out/f'{c["name"]}-{mode}.png')
  modes[mode]=dict(top=[dict(id=ids[k],label=labels[k],score=float(v))for k,v in zip(ix[:5],scores[:5])],samples=samples)
 rows.append(dict(name=c['name'],path=str(c['path']),sha256=sha,modes=modes));print(c['name'],round(modes['old']['top'][0]['score'],4),round(modes['direct']['top'][0]['score'],4),modes['old']['top'][0]['id']==modes['direct']['top'][0]['id'],flush=True)
 assert hashlib.sha256(c['path'].read_bytes()).hexdigest()==sha
 (out/'results.json').write_text(json.dumps(dict(scope='PC CPU4, same images/quads/models/gallery, 1warmup+3 samples; rank retention is not accuracy',rows=rows),ensure_ascii=False,indent=2),encoding='utf8')
