import json, hashlib
from pathlib import Path
import torch, numpy as np, onnx, onnxruntime as ort
from PIL import Image
from pilot import Encoder,tensor
R=Path(__file__).resolve().parent; O=R/'expanded-01';O.mkdir(exist_ok=True)
torch.set_num_threads(4)
m=Encoder(False).eval();m.load_state_dict(torch.load(R/'pilot-01/trained.pt',map_location='cpu',weights_only=True))
p=O/'own-fp32.onnx'
torch.onnx.export(m,torch.zeros(1,3,448,320),str(p),input_names=['image'],output_names=['embedding'],opset_version=17,dynamo=False)
onnx.checker.check_model(onnx.load(p))
opt=ort.SessionOptions();opt.intra_op_num_threads=4
s=ort.InferenceSession(str(p),opt,providers=['CPUExecutionProvider'])
rows=json.loads((R/'pilot-01/split.json').read_text())['validation'][:32];errors=[];a=[];b=[]
for row in rows:
    with Image.open(R/row['path']) as im:x=tensor(im.convert('RGB'))[None]
    with torch.inference_mode():y=m(x).numpy()
    z=s.run(None,{'image':x.numpy()})[0];errors.append(float(np.max(abs(y-z))));a.append(y.ravel());b.append(z.ravel())
a=np.stack(a);b=np.stack(b)
assert max(errors)<1e-4
assert np.allclose(np.linalg.norm(b,axis=1),1,atol=1e-5)
assert np.array_equal((a@a.T).argmax(1),(b@b.T).argmax(1))
result={'max_abs_error':max(errors),'samples':len(rows),'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'scope':'FP32 same tensors;32 heldout reference images; self-retrieval ranks agree. Android parity pending.'}
(O/'export-parity.json').write_text(json.dumps(result,indent=2));print(result)
