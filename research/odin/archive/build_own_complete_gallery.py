import os
os.environ['OPENBLAS_NUM_THREADS']='4'
from pathlib import Path
import json,sqlite3,hashlib,time
from concurrent.futures import ThreadPoolExecutor
import numpy as np,torch,onnxruntime as ort
from PIL import Image
from pilot import Encoder,tensor
R=Path(__file__).resolve().parent;O=R/'own02-complete-gallery-01';O.mkdir(exist_ok=False)
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
c=sqlite3.connect('file:'+str(R/'manifest.sqlite')+'?mode=ro',uri=True);refs=[];backs=[]
for key,raw,h,w,hh in c.execute('select id,metadata,sha256,width,height from images order by id'):
 x=json.loads(raw);x.update(sha256=h,width=w,height=hh)
 if x.get('face',0)==0:refs.append(x)
 else:backs.append(x)
assert len(set(x['scryfall_id'] for x in refs))==len(refs)
(O/'refs.json').write_text(json.dumps(refs,ensure_ascii=False),encoding='utf8');(O/'back-faces-not-indexed.json').write_text(json.dumps(backs),encoding='utf8')
modelpath=R/'pilot-02/own-fp32.onnx';expected='d7dc898744ce9ce530e9b6344e043dd9315b426b513bb6f5e66f6367f6ca9fd6';assert sha(modelpath)==expected
config={'encoder_sha256':expected,'weights_sha256':sha(R/'pilot-02/trained.pt'),'refs_sha256':sha(O/'refs.json'),'rows':len(refs),'downloaded_faces':len(refs)+len(backs),'excluded_backs':len(backs),'policy':'Every downloaded front face, including landscape/split cards. Back faces preserved in separate manifest, not indexed in upright-front scanner. FrozenOwn02, no training.'};(O/'config.json').write_text(json.dumps(config,indent=2))
opts=ort.SessionOptions();opts.intra_op_num_threads=4;opts.inter_op_num_threads=1;s=ort.InferenceSession(str(modelpath),opts)
torch.set_num_threads(4);torch.backends.cudnn.allow_tf32=False;torch.backends.cuda.matmul.allow_tf32=False;torch.set_float32_matmul_precision('highest');model=Encoder(False).eval();model.load_state_dict(torch.load(R/'pilot-02/trained.pt',weights_only=True,map_location='cpu'));model.cuda()
def read(row):
 p=R/row['path'];assert sha(p)==row['sha256']
 with Image.open(p) as im:return tensor(im.convert('RGB'))
g=np.lib.format.open_memmap(O/'gallery.npy',mode='w+',dtype=np.float32,shape=(len(refs),256));start=time.monotonic();maxerr=0.
with ThreadPoolExecutor(max_workers=4) as pool,torch.inference_mode():
 for i in range(0,len(refs),32):
  x=torch.stack(list(pool.map(read,refs[i:i+32])));z=model(x.cuda()).cpu().numpy();z/=np.linalg.norm(z,axis=1,keepdims=True);g[i:i+len(z)]=z
  if i%4096==0:
   cpu=s.run(None,{'image':x[:1].numpy()})[0];err=float(abs(cpu[0]-z[0]).max());maxerr=max(maxerr,err);assert err<1e-4
   g.flush();status={'phase':'encoding','rows':min(i+len(z),len(refs)),'total':len(refs),'elapsed_seconds':time.monotonic()-start,'max_gpu_onnx_error':maxerr};(O/'status.json').write_text(json.dumps(status));print(status,flush=True)
g.flush();(O/'status.json').write_text(json.dumps({'phase':'complete_pending_replay','rows':len(refs),'elapsed_seconds':time.monotonic()-start,'max_gpu_onnx_error':maxerr}));print('COMPLETE',len(refs),flush=True)
