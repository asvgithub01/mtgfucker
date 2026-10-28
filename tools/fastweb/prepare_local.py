from pathlib import Path
import shutil,json,hashlib
import onnx
from onnx import helper,numpy_helper,TensorProto
import numpy as np
root=Path.cwd(); lab=Path('C:/Users/nubor/.codex/worktrees/dinov2-benchmark/mtgfucker')
out=root/'build/fastweb-e2e-assets/fastweb';out.mkdir(parents=True,exist_ok=True)
for n,src in [('detector.onnx',lab/'build/fastweb-assets-v26/fastweb-single-1.39.onnx'),('diego.onnx',lab/'build/diego-android-assets-v2/diego.onnx'),('ids.json',lab/'build/diego-android-assets-v2/full-ids.json')]:shutil.copy2(src,out/n)
m=onnx.load(str(lab/'build/diego-android-assets-v2/diego-search.onnx'))
n=len(json.loads((out/'ids.json').read_text()))
base=list(m.graph.node)[:2]
del m.graph.node[:];m.graph.node.extend(base+[helper.make_node('Add',['scores','scope_bias'],['scoped']),helper.make_node('TopK',['scoped','k'],['values','indices'])])
gallery=next(x for x in m.graph.initializer if x.name=='gallery')
del m.graph.initializer[:];m.graph.initializer.extend([gallery,numpy_helper.from_array(np.array([8],dtype=np.int64),'k')])
m.graph.input.extend([helper.make_tensor_value_info('scope_bias',TensorProto.FLOAT,[n])])
del m.graph.output[:];m.graph.output.extend([helper.make_tensor_value_info('values',TensorProto.FLOAT,[8]),helper.make_tensor_value_info('indices',TensorProto.INT64,[8])]);onnx.checker.check_model(m);onnx.save(m,str(out/'search.onnx'))
manifest={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in out.iterdir() if p.suffix in ['.onnx','.json'] and p.name!='manifest.json'}
expected=json.loads((root/'tools/fastweb/manifest.json').read_text())
assert manifest==expected, 'Pinned asset checksum changed; do not silently update models/catalog'
(out/'manifest.json').write_text(json.dumps(manifest,indent=2))

print("Prepared",n,"catalog rows",manifest)

# Local test-only fixtures, SHA checked against the existing Sony replay. Never packaged in app.
d=json.loads((lab/'build/fastweb-sony-v27/results.json').read_text())
fixtures=root/'build/fastweb-test-assets';fixtures.mkdir(exist_ok=True);rows=[]
for i in range(7):
 r=d['rows'][i]
 paths=[lab/'build/capture-review-10'/r['input'],lab/'build/catalog-refresh-01/sony-before'/r['input'],lab/'build/jackttv-pose-01/sony-before'/r['input']]
 src=next(p for p in paths if p.exists())
 assert hashlib.sha256(src.read_bytes()).hexdigest()==r['sha256']
 shutil.copy2(src,fixtures/f'{i}.png');sample=r['modes']['fastweb-1.39']['samples'][0]
 rows.append({'file':f'{i}.png','quads':sample['quads'],'top':sample['diego']['top'],'sha256':r['sha256']})
(fixtures/'expected.json').write_text(json.dumps(rows))
