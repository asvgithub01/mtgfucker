"""Prepare isolated own-model assets for later Triple lab integration; no APK writes."""
from pathlib import Path
import json,hashlib,shutil
import numpy as np,onnx,onnxruntime as ort
from onnx import helper as h,numpy_helper as nh,TensorProto as T
R=Path(__file__).resolve().parent;P=R/'pilot-02';O=P/'android-bundle';O.mkdir(exist_ok=True)
refs=json.loads((P/'catalog-manifest.json').read_text(encoding='utf8'))['refs'];g=np.load(P/'gallery.npy',mmap_mode='r')
# Initial scanner integration uses front faces only and one row per printing UUID.
chosen=[];seen=set()
for i,r in enumerate(refs):
    uid=r.get('scryfall_id',r['id'].split('_f')[0])
    if r.get('face',0)!=0 or uid in seen:continue
    seen.add(uid);chosen.append(i)
ids=[refs[i].get('scryfall_id',refs[i]['id'].split('_f')[0]) for i in chosen];n=len(ids);matrix=np.asarray(g[chosen].T,dtype=np.float32)
nodes=[h.make_node('MatMul',['query','gallery'],['two_scores']),h.make_node('ReduceMax',['two_scores'],['scores'],axes=[0],keepdims=0),h.make_node('Add',['scores','scope_bias'],['scoped']),h.make_node('TopK',['scoped','k'],['values','indices'])]
graph=h.make_graph(nodes,'own02_scoped_search',[h.make_tensor_value_info('query',T.FLOAT,[2,256]),h.make_tensor_value_info('scope_bias',T.FLOAT,[n])],[h.make_tensor_value_info('values',T.FLOAT,[8]),h.make_tensor_value_info('indices',T.INT64,[8])],[nh.from_array(matrix,'gallery'),nh.from_array(np.array([8],dtype=np.int64),'k')])
model=h.make_model(graph,opset_imports=[h.make_opsetid('',17)]);model.ir_version=9;onnx.checker.check_model(model);onnx.save(model,str(O/'search.onnx'))
shutil.copy2(P/'own-fp32.onnx',O/'encoder.onnx')
shutil.copy2('C:/Users/nubor/.codex/worktrees/dinov2-benchmark/mtgfucker/build/fastweb-assets-v26/fastweb-single-1.39.onnx',O/'detector.onnx')
(O/'ids.json').write_text(json.dumps(ids))
opt=ort.SessionOptions();opt.intra_op_num_threads=4;s=ort.InferenceSession(str(O/'search.onnx'),opt)
rng=np.random.default_rng(42);checks=[]
for subset in (False,True):
    for row in (0,n//2,n-1):
        q=matrix[:,row];bias=np.zeros(n,dtype=np.float32)
        if subset:
            bias.fill(-1e6);bias[rng.choice(n,200,replace=False)]=0;bias[row]=0
        values,indices=s.run(None,{'query':np.stack([q,q]),'scope_bias':bias})
        expected=q@matrix+bias
        assert np.allclose(values,expected[indices],atol=1e-5)
        assert values[-1]>=np.partition(expected,-8)[-8]-1e-5
        checks.append({'subset':subset,'row':row,'top_id':ids[int(indices[0])]})
manifest={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in O.iterdir() if p.name in ('search.onnx','encoder.onnx','detector.onnx','ids.json')}
(O/'manifest.json').write_text(json.dumps(manifest,indent=2))
(O/'contract.json').write_text(json.dumps({'rows':n,'source_rows':len(refs),'front_faces_only':True,'encoder':'RGB upright Pillow BILINEAR rectangular320x448, ImageNet float32 /255 then subtractmean/divstd, L2','warning':'NOT Diego preprocessing (OpenCV INTER_LINEAR_EXACT). Android implementation and device parity required. DO NOT swap into original or Diego asset folder. Acceptance thresholds not calibrated.','query':'duplicate same upright256D vector into2x256, no extra orientations','checks':checks,'state':'assets_prepared_not_integrated_not_installed'},indent=2))
print('Prepared unique front printing rows:',n,'source:',len(refs),'search tests:',len(checks))
