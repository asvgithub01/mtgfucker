"""Read-only Sony parity collection; never accesses original app or gallery data."""
from pathlib import Path
import subprocess,tarfile,json,io,datetime
import numpy as np
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/"build/odin-parity-pillow123"))
from PIL import Image, __version__ as pillow_version
assert pillow_version == "12.3.0"
import onnxruntime as ort
root=Path(__file__).resolve().parents[2];adb=Path.home()/'AppData/Local/Android/Sdk/platform-tools/adb.exe';prefix=[str(adb),'-s','QV770HG2JD']
listing=subprocess.check_output(prefix+['shell','run-as','io.asv.odinlab','ls','files'],text=True).split()
run=sorted(x for x in listing if x.startswith('odin-parity-'))[-1]
out=root/'build'/('odin-lab-parity-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'));out.mkdir()
blob=subprocess.check_output(prefix+['exec-out','run-as','io.asv.odinlab','tar','-cf','-',f'files/{run}'])
(out/'evidence.tar').write_bytes(blob)
with tarfile.open(fileobj=io.BytesIO(blob)) as t:t.extractall(out,filter='data')
d=out/'files'/run;im=Image.open(d/'input.png').convert('RGB');assets=root/'build/odin-lab-assets-v1'
x=np.asarray(im.resize((320,448),Image.Resampling.BILINEAR),np.float32)/255;x=((x-np.array([.485,.456,.406],np.float32))/np.array([.229,.224,.225],np.float32)).transpose(2,0,1)[None]
s=ort.InferenceSession(str(assets/'ownidentity/encoder.onnx'),providers=['CPUExecutionProvider']);z=s.run(None,{s.get_inputs()[0].name:x})[0].reshape(-1);z=z/np.linalg.norm(z)
s=ort.InferenceSession(str(assets/'ownidentity/search.onnx'),providers=['CPUExecutionProvider']);ids=json.loads((assets/'ownidentity/ids.json').read_text());scores,indices=s.run(None,{'query':np.stack([z,z]),'scope_bias':np.zeros(len(ids),np.float32)})[:2]
r=json.loads((d/'result.json').read_text());device=np.fromfile(d/'tensor.bin',np.float32);dz=np.fromfile(d/'embedding.bin',np.float32)
report={'odinTensorMaxDiff':float(np.max(abs(device-x.ravel()))),'odinEmbeddingMaxDiff':float(np.max(abs(dz-z))),'odinTopMatch':[v['id'] for v in r['top']]==[ids[int(i)] for i in indices.ravel()],'odinScoreMaxDiff':float(np.max(abs(scores.ravel()-np.array([v['cosine'] for v in r['top']]))))}
e=np.asarray(im.resize((160,224),Image.Resampling.BILINEAR),np.float32).transpose(2,0,1)[None]/255
s=ort.InferenceSession(str(assets/'esquinitas.onnx'),providers=['CPUExecutionProvider']);q,p=s.run(None,{'image':e});de=json.loads((d/'esquinitas.json').read_text())
report.update(esquinitasTensorMaxDiff=float(np.max(abs(e.ravel()-np.fromfile(d/'esquinitas-tensor.bin',np.float32)))),esquinitasCornersMaxDiff=float(np.max(abs(q.ravel()-de['corners']))),esquinitasPresenceDiff=abs(float(p.ravel()[0])-de['presence']))
report['passed']=report['odinTensorMaxDiff']<1e-6 and report['odinEmbeddingMaxDiff']<1e-4 and report['odinTopMatch'] and report['odinScoreMaxDiff']<1e-4 and report['esquinitasTensorMaxDiff']<1e-6 and report['esquinitasCornersMaxDiff']<1e-6 and report['esquinitasPresenceDiff']<1e-4
(out/'result.json').write_text(json.dumps(report,indent=2));print(out);print(json.dumps(report,indent=2));assert report['passed']
