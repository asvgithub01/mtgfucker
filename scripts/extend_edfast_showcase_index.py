"""Add a bounded, evaluated showcase ROI to a verified complete index (no user photos).

The override is printing-specific, not a general relaxation for every unusual layout.
All existing vectors/row order are preserved. Model and complete base hashes are checked.
"""
import argparse
import hashlib
import json
from pathlib import Path
import numpy as np
import onnxruntime as ort
from PIL import Image
from evaluate_edfast_regions import MODEL_SHA, REGIONS, region

BARTOLOME_SHOWCASE = 'f82bb486-7009-4b53-973f-477fc13ad7e7'


def extend(base, output, model, reference):
    sha = lambda data: hashlib.sha256(data).hexdigest()
    assert sha(model.read_bytes()) == MODEL_SHA
    manifest = json.loads((base/'manifest.json').read_text(encoding='utf8'))
    assert manifest['complete'] and manifest['modelSha256'] == MODEL_SHA
    assert manifest['regions'] == {k:list(v) for k,v in REGIONS.items()}
    for name,spec in manifest['files'].items():
        data=(base/name).read_bytes()
        assert len(data)==spec['bytes'] and sha(data)==spec['sha256']
    rows=json.loads((base/'rows.json').read_text(encoding='utf8'))
    assert len(rows)==110278
    index=next(i for i,r in enumerate(rows) if r[0]==BARTOLOME_SHOWCASE)
    assert rows[index][4] is True
    rows[index][4]=False
    options=ort.SessionOptions();options.intra_op_num_threads=2;options.inter_op_num_threads=1
    session=ort.InferenceSession(str(model),sess_options=options,providers=['CPUExecutionProvider'])
    mean=np.array([.485,.456,.406],np.float32);std=np.array([.229,.224,.225],np.float32)
    image=Image.open(reference).convert('RGB')
    output.mkdir(parents=True,exist_ok=True)
    for profile in ('upper','art'):
        pixels=np.asarray(region(image,profile).resize((448,448),Image.Resampling.BILINEAR),np.float32)/255
        tensor=((pixels-mean)/std).transpose(2,0,1)[None].copy()
        vector=session.run(['embedding'],{'image':tensor})[0].reshape(-1)
        vector=vector/np.linalg.norm(vector)
        assert vector.shape==(128,) and np.isfinite(vector).all()
        data=bytearray((base/(profile+'.f16.bin')).read_bytes())
        data[index*256:(index+1)*256]=vector.astype('<f2').tobytes()
        (output/(profile+'.f16.bin')).write_bytes(data)
    (output/'rows.json').write_text(json.dumps(rows,ensure_ascii=False,separators=(',',':')),encoding='utf8')
    manifest['fullRows']-=1
    manifest['baseManifestSha256']=sha((base/'manifest.json').read_bytes())
    manifest['cropOverrides']={BARTOLOME_SHOWCASE:{'referenceSha256':sha(reference.read_bytes()),'reason':'LCI 301 showcase with normal text-box geometry'}}
    for name in manifest['files']:
        data=(output/name).read_bytes();manifest['files'][name]={'bytes':len(data),'sha256':sha(data)}
    (output/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf8')
    print(json.dumps(manifest,indent=2))


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    for name in ('base','output','model','reference'):p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();extend(a.base,a.output,a.model,a.reference)
