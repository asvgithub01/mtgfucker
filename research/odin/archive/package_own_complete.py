from pathlib import Path
import json,hashlib,shutil
import numpy as np,onnx,onnxruntime as ort
from onnx import helper as h,numpy_helper as nh,TensorProto as T
R=Path(__file__).resolve().parent;G=R/'own02-complete-gallery-01';res=json.loads((G/'replay/results.json').read_text(encoding='utf8'))
assert res['sony_failures']['correct']>46 and res['reserved']['correct']>=23
O=G/'android-bundle';O.mkdir(exist_ok=False);refs=json.loads((G/'refs.json').read_text(encoding='utf8'));n=len(refs);mat=np.load(G/'gallery.npy',mmap_mode='r');matrix=np.asarray(mat.T,dtype=np.float32);ids=[r['scryfall_id'] for r in refs];assert len(set(ids))==n
nodes=[h.make_node('MatMul',['query','gallery'],['two_scores']),h.make_node('ReduceMax',['two_scores'],['scores'],axes=[0],keepdims=0),h.make_node('Add',['scores','scope_bias'],['scoped']),h.make_node('TopK',['scoped','k'],['values','indices'])]
graph=h.make_graph(nodes,'own02_complete_scoped_search',[h.make_tensor_value_info('query',T.FLOAT,[2,256]),h.make_tensor_value_info('scope_bias',T.FLOAT,[n])],[h.make_tensor_value_info('values',T.FLOAT,[8]),h.make_tensor_value_info('indices',T.INT64,[8])],[nh.from_array(matrix,'gallery'),nh.from_array(np.array([8],dtype=np.int64),'k')]);model=h.make_model(graph,opset_imports=[h.make_opsetid('',17)]);model.ir_version=9;onnx.checker.check_model(model);onnx.save(model,str(O/'search.onnx'))
for name in ['encoder.onnx','detector.onnx']:shutil.copy2(R/'pilot-02/android-bundle'/name,O/name)
(O/'ids.json').write_text(json.dumps(ids));(O/'identities.json').write_text(json.dumps({r['scryfall_id']:['oracle:'+r['oracle_id'] if r.get('oracle_id') else 'printing:'+r['scryfall_id'],r['set'],r.get('released_at','')] for r in refs}))
manifest={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in O.iterdir()};(O/'manifest.json').write_text(json.dumps(manifest,indent=2));assert manifest['encoder.onnx']=='d7dc898744ce9ce530e9b6344e043dd9315b426b513bb6f5e66f6367f6ca9fd6'
opt=ort.SessionOptions();opt.intra_op_num_threads=4;opt.inter_op_num_threads=1;s=ort.InferenceSession(str(O/'search.onnx'),opt)
for i in [0,n//2,n-1]:
 z=np.asarray(mat[i]);v,ix=s.run(None,{'query':np.stack([z,z]),'scope_bias':np.zeros(n,np.float32)});raw=mat@z;assert np.allclose(v,raw[ix],atol=1e-5)
print('bundle validated',n)
