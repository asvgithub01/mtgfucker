"""Export frozen Esquinitas spatial01 with exact fixed adaptive pooling, no training."""
import sys,torch,pathlib,json,hashlib,onnxruntime as ort,numpy as np,math
root=pathlib.Path(__file__).resolve().parents[2];sys.path.insert(0,str(root/'research/card-corners'))
from train_heads import HeadNet,decode
class FixedPool(torch.nn.Module):
 def forward(self,x):
  return torch.cat([torch.cat([x[:,:,math.floor(i*14/8):math.ceil((i+1)*14/8),math.floor(j*10/8):math.ceil((j+1)*10/8)].mean((2,3),keepdim=True) for j in range(8)],3) for i in range(8)],2)
class Export(torch.nn.Module):
 def __init__(self):
  super().__init__();self.net=HeadNet('simcc',8)
 def forward(self,x):
  p,q=self.net(x);return decode(q),p.sigmoid()
m=Export().eval();ck=pathlib.Path('C:/asv/proyectos/2026/mtgfucker/mtgfucker/trainning models/unity/runs/spatial-01/generalization-simcc/final.pt');m.net.load_state_dict(torch.load(ck,weights_only=True,map_location='cpu'))
out=root/'build/odin-lab-assets-v1/esquinitas.onnx';x=torch.rand(1,3,224,160);ref=m(x);m.net.features[8]=FixedPool();now=m(x);assert torch.equal(ref[0],now[0]) and torch.allclose(ref[1],now[1],atol=1e-6)
torch.onnx.export(m,(x,),str(out),input_names=['image'],output_names=['corners','presence'],opset_version=17,dynamo=False)
s=ort.InferenceSession(str(out),providers=['CPUExecutionProvider']);y=s.run(None,{'image':x.numpy()});assert np.max(np.abs(y[0]-ref[0].detach().numpy()))<1e-6
(out.parent/'esquinitas-model.json').write_text(json.dumps({'esquinitas.onnx':hashlib.sha256(out.read_bytes()).hexdigest(),'checkpoint':hashlib.sha256(ck.read_bytes()).hexdigest(),'experimental':True,'input':'RGB Pillow bilinear160x224 /255, no ImageNet','presenceThreshold':.5},indent=2));print(out)
