"""Read-only replay of a diagnostic frame using the lab's independent PC recipe."""
from pathlib import Path
import sys,json,hashlib
sys.path.insert(0,'C:/Users/nubor/.codex/worktrees/dinov2-benchmark/mtgfucker/build/dhvazquez-python')
import numpy as np, cv2, onnxruntime as ort
from PIL import Image
sys.stdout.reconfigure(encoding='utf-8')
root=Path(__file__).resolve().parents[2]
lab=Path('C:/Users/nubor/.codex/worktrees/dinov2-benchmark/mtgfucker')
assets=lab/'build/diego-android-assets-v2'
sample=Path(sys.argv[1]);output=sample/'comparison';output.mkdir(exist_ok=True)
opt=ort.SessionOptions();opt.intra_op_num_threads=4;opt.inter_op_num_threads=1
encoder=ort.InferenceSession(str(assets/'diego.onnx'),opt,providers=['CPUExecutionProvider'])
search=ort.InferenceSession(str(assets/'diego-search.onnx'),opt,providers=['CPUExecutionProvider'])
ids=json.loads((assets/'full-ids.json').read_text());labels=json.loads((assets/'scan-labels.json').read_text(encoding='utf8'))
mean=np.float32([.485,.456,.406]);std=np.float32([.229,.224,.225])
def tensor(rgb):return ((rgb.astype(np.float32)-mean*255)/(std*255)).transpose(2,0,1)[None].copy()
def identify(x):
 z=encoder.run(None,{encoder.get_inputs()[0].name:x})[0].ravel();z/=np.linalg.norm(z)
 r=search.run(None,{'query':np.stack([z,z])});scores=np.asarray(r[0]).ravel();ix=np.asarray(r[1]).ravel()
 return [dict(id=ids[k],label=labels[k],score=float(s)) for k,s in zip(ix[:5],scores[:5])]
a=np.array(Image.open(sample/'upright.png').convert('RGB'));h,w=a.shape[:2]
result=json.loads((sample/'result.json').read_text());q=np.float32(result['quad'])*np.float32([w,h])
transform=cv2.getPerspectiveTransform(q,np.float32([[0,0],[639,0],[639,895],[0,895]]))
crop=cv2.warpPerspective(a,transform,(640,896));Image.fromarray(crop).save(output/'lab-crop.png')
actual=np.array(Image.open(sample/'diego-crop.png').convert('RGB'))
rgb=cv2.resize(crop,(320,448),interpolation=cv2.INTER_LINEAR_EXACT);Image.fromarray(rgb).save(output/'lab-input.png')
actual_rgb=np.fromfile(sample/'input.rgb',np.uint8).reshape(448,320,3);Image.fromarray(actual_rgb).save(output/'triple-input.png')
actual_x=np.fromfile(sample/'input.f32',dtype='<f4').reshape(1,3,448,320)
report=dict(frame_size=[w,h],rotation=result['rotation'],android_hits=result['hits'],
 crop_max_error=int(np.abs(actual.astype(int)-crop.astype(int)).max()),
 crop_mean_error=float(np.abs(actual.astype(int)-crop.astype(int)).mean()),
 rgb_max_error=int(np.abs(actual_rgb.astype(int)-rgb.astype(int)).max()),
 tensor_max_error=float(np.abs(actual_x-tensor(rgb)).max()),
 triple_tensor_pc=identify(actual_x),lab_recipe_pc=identify(tensor(rgb)),
 input_sha256=hashlib.sha256((sample/'input.f32').read_bytes()).hexdigest())
(output/'result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8');print(json.dumps(report,ensure_ascii=False,indent=2))
