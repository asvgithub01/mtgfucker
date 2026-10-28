import json,hashlib
from pathlib import Path
import numpy as np,onnxruntime as ort
from PIL import Image,ImageOps
R=Path(r'C:/asv/datasets/mtg-identity-v1');W=Path(r'C:/Users/nubor/.codex/worktrees/edscan-fastweb-diego/mtgfucker');O=W/'build/fastweb-test-assets/own';O.mkdir(parents=True,exist_ok=True)
opts=ort.SessionOptions();opts.intra_op_num_threads=4;opts.inter_op_num_threads=1
B=R/'pilot-02/android-bundle';enc=ort.InferenceSession(str(B/'encoder.onnx'),opts);search=ort.InferenceSession(str(B/'search.onnx'),opts);ids=json.loads((B/'ids.json').read_text());det=json.loads((R/'heldout-eval-01/detection.json').read_text())['rows'];rows=[]
for i in [0,1,3,4,5,6]:
 row=det[i];src=R/'heldout-20261026/originals'/row['file'];assert hashlib.sha256(src.read_bytes()).hexdigest()==row['sha256']
 with Image.open(src) as im:ImageOps.exif_transpose(im).convert('RGB').save(O/f'{i:02}.png')
 with Image.open(R/'heldout-eval-01'/f'{i:02}-crop.png') as im:a=np.asarray(im.convert('RGB').resize((320,448),Image.Resampling.BILINEAR),np.float32)/255
 a=(a-np.float32([.485,.456,.406]))/np.float32([.229,.224,.225]);z=enc.run(None,{enc.get_inputs()[0].name:np.ascontiguousarray(a.transpose(2,0,1)[None])})[0].ravel();z/=np.linalg.norm(z)
 scores,indices=search.run(None,{'query':np.stack([z,z]),'scope_bias':np.zeros(len(ids),np.float32)})
 rows.append({'file':f'own/{i:02}.png','quad':row['quad'],'top':[{'id':ids[int(ix)],'score':float(sc)} for ix,sc in zip(indices.ravel(),scores.ravel())],'frame_sha256':hashlib.sha256((O/f'{i:02}.png').read_bytes()).hexdigest()})
(O/'expected.json').write_text(json.dumps({'model_manifest':json.loads((B/'manifest.json').read_text()),'rows':rows},indent=2));print('6 upright PNG fixtures with own02 +41001 search expectations; no model tuning')
