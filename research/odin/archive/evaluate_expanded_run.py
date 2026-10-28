"""Evaluate frozen expanded checkpoint; same 17702 reference gallery as Milo/Diego baselines."""
import os
os.environ['OPENBLAS_NUM_THREADS']='4'
import argparse,hashlib,json,time
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import numpy as np,torch,onnx,onnxruntime as ort
from PIL import Image,ImageOps
from pilot import Encoder,tensor
R=Path(__file__).resolve().parent

def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def read(row):
 p=Path(row['path']);p=p if p.is_absolute() else R/p
 assert sha(p)==row['sha256']
 with Image.open(p) as im:return tensor(ImageOps.exif_transpose(im).convert('RGB'))

def main():
 ap=argparse.ArgumentParser();ap.add_argument('--run',default='expanded-run-01');a=ap.parse_args();run=R/a.run
 assert run.parent==R
 status=json.loads((run/'status.json').read_text());assert status['phase']=='training_complete_pending_evaluation'
 trainconfig=json.loads((run/'config.json').read_text());assert status['step']==trainconfig['steps']
 O=run/'evaluation-01';O.mkdir(exist_ok=False)
 m=json.loads((R/'expanded-01/manifest.json').read_text(encoding='utf8'));refs=m['refs'];queries=m['queries']
 split=json.loads((run/'split.json').read_text(encoding='utf8'));assert not {r['illustration_id'] for r in split['train']}&{r['truth']['illustration_id'] for r in queries}
 torch.set_num_threads(4);torch.backends.cudnn.allow_tf32=False;torch.backends.cuda.matmul.allow_tf32=False;torch.set_float32_matmul_precision('highest')
 model=Encoder(False).eval();model.load_state_dict(torch.load(run/'trained.pt',map_location='cpu',weights_only=True))
 path=O/'encoder.onnx';torch.onnx.export(model,torch.zeros(1,3,448,320),str(path),input_names=['image'],output_names=['embedding'],opset_version=17,dynamo=False);onnx.checker.check_model(onnx.load(path))
 opts=ort.SessionOptions();opts.intra_op_num_threads=4;opts.inter_op_num_threads=1;s=ort.InferenceSession(str(path),opts,providers=['CPUExecutionProvider']);errors=[]
 for r in split['validation'][:32]:
  x=read(r)[None]
  with torch.inference_mode():v=model(x).numpy()
  z=s.run(None,{'image':x.numpy()})[0];errors.append(float(abs(v-z).max()))
 assert max(errors)<1e-4
 model.cuda();gallery=np.lib.format.open_memmap(O/'gallery.npy',mode='w+',dtype=np.float32,shape=(len(refs),256))
 with ThreadPoolExecutor(max_workers=4) as pool,torch.inference_mode():
  for i in range(0,len(refs),16):
   if (R/'STOP_TRAIN').exists():raise RuntimeError('Stopped by STOP_TRAIN')
   z=model(torch.stack(list(pool.map(read,refs[i:i+16]))).cuda()).cpu().numpy();z/=np.linalg.norm(z,axis=1,keepdims=True);gallery[i:i+len(z)]=z
   if i%1024==0:gallery.flush();print('gallery',i,len(refs),flush=True)
 gallery.flush();gpuerrors=[]
 def encode(row):
  x=read(row)[None].numpy();z=s.run(None,{'image':x})[0].ravel();return z/np.linalg.norm(z)
 for i,r in enumerate(refs[:32]):gpuerrors.append(float(abs(encode(r)-gallery[i]).max()))
 assert max(gpuerrors)<1e-4
 labels=json.loads((R/'heldout-eval-01/labels-frozen.json').read_text(encoding='utf8'))
 oldheld=json.loads((R/'heldout-eval-01/results.json').read_text(encoding='utf8'))
 h=[]
 for r in oldheld['models']['own02']['rows']:
  p=R/'heldout-eval-01'/f"{r['index']:02}-crop.png";h.append({'id':r['index'],'path':str(p),'sha256':sha(p),'truth':{'illustration_id':labels[r['group']]['art']},'group':r['group']})
 assert not {r['illustration_id'] for r in split['train']}&{r['truth']['illustration_id'] for r in h}
 results={}
 for label,items in [('development',queries),('reserved_photos',h)]:
  out=[]
  for row in items:
   encode(row);samples=[]
   for _ in range(3):
    t=time.perf_counter();z=encode(row);mid=time.perf_counter();scores=gallery@z;idx=int(scores.argmax());end=time.perf_counter();samples.append([(mid-t)*1000,(end-mid)*1000])
   out.append({'id':row['id'],'predicted':refs[idx]['name'],'correct_art':refs[idx]['illustration_id']==row['truth']['illustration_id'],'score':float(scores[idx]),'encode_with_file_io_ms':float(np.median(samples,axis=0)[0]),'search_ms':float(np.median(samples,axis=0)[1])})
  results[label]={'correct':sum(x['correct_art'] for x in out),'n':len(out),'rows':out}
 result={'scope':'Frozen17702gallery, own new checkpoint. Baseline counts from existing identical gallery; no direct timing comparison: current encode includes file IO. Photos23/sixidentities, no thresholds calibrated or promotion. Full catalog evaluation and Android remain pending.','gallery_rows':len(refs),'model_sha256':sha(path),'weights_sha256':sha(run/'trained.pt'),'manifest_sha256':sha(R/'expanded-01/manifest.json'),'cpu_export_max_abs':max(errors),'gpu_gallery_max_abs':max(gpuerrors),'results':results,'reserved_baselines':{k:{'correct':v['correct'],'n':v['n']} for k,v in oldheld['models'].items()}}
 (O/'results.json').write_text(json.dumps(result,indent=2,ensure_ascii=False),encoding='utf8');print({k:{'correct':v['correct'],'n':v['n']} for k,v in results.items()})
if __name__=='__main__':main()
