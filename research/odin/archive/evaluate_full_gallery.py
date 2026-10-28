import os
os.environ['OPENBLAS_NUM_THREADS']='4'
import json,time
from pathlib import Path
import numpy as np,onnxruntime as ort
from PIL import Image
R=Path(r'C:/asv/datasets/mtg-identity-v1');O=R/'full-gallery-eval-01';O.mkdir(exist_ok=True)
assert not (O/'results.json').exists()
meta=json.loads((R/'pilot-02/catalog-manifest.json').read_text(encoding='utf8'))['refs'];g=np.load(R/'pilot-02/gallery.npy',mmap_mode='r');assert len(meta)==len(g)
previous=json.loads((R/'heldout-eval-01/results.json').read_text(encoding='utf8'))['models']['own02']['rows'];truth=json.loads((R/'heldout-eval-01/labels-frozen.json').read_text(encoding='utf8'))
opt=ort.SessionOptions();opt.intra_op_num_threads=4;opt.inter_op_num_threads=1;s=ort.InferenceSession(str(R/'pilot-02/own-fp32.onnx'),opt,providers=['CPUExecutionProvider']);rows=[]
for r in previous:
 p=R/'heldout-eval-01'/f"{r['index']:02}-crop.png"
 im=Image.open(p).convert('RGB');a=np.array(im.resize((320,448),Image.Resampling.BILINEAR),np.float32)/255;a=(a-np.float32([.485,.456,.406]))/np.float32([.229,.224,.225]);a=np.ascontiguousarray(a.transpose(2,0,1)[None]);z=s.run(None,{s.get_inputs()[0].name:a})[0].ravel();z/=np.linalg.norm(z)
 t=time.perf_counter();scores=g@z;ids=np.argsort(scores)[-8:][::-1];ms=(time.perf_counter()-t)*1000;best=meta[int(ids[0])]
 rows.append({'index':r['index'],'group':r['group'],'predicted':best['name'],'art_correct':best['illustration_id']==truth[r['group']]['art'],'score':float(scores[ids[0]]),'search_ms':ms,'top8':[{'name':meta[int(i)]['name'],'art':meta[int(i)]['illustration_id'],'score':float(scores[i])} for i in ids]})
result={'model':'own02 frozen','gallery_faces':len(meta),'correct':sum(x['art_correct'] for x in rows),'n':len(rows),'scope':'Same23photos/sixidentities, full frozen41347face gallery. No retraining/threshold tuning; not Android timing or acceptance rate. Not a matched-gallery comparison with Milo/Diego.','rows':rows};(O/'results.json').write_text(json.dumps(result,indent=2),encoding='utf8');print({k:v for k,v in result.items() if k!='rows'})
