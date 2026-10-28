import json,hashlib,time
from pathlib import Path
import numpy as np,cv2,onnxruntime as ort
from PIL import Image,ImageOps,ImageDraw
R=Path(__file__).resolve().parent;H=R/'heldout-20261026';O=R/'heldout-eval-01';O.mkdir(exist_ok=True)
path=Path('C:/Users/nubor/.codex/worktrees/dinov2-benchmark/mtgfucker/build/fastweb-assets-v26/fastweb-single-1.39.onnx')
opts=ort.SessionOptions();opts.intra_op_num_threads=4;opts.inter_op_num_threads=1
s=ort.InferenceSession(str(path),opts,providers=['CPUExecutionProvider']);cv2.setNumThreads(1)
rows=[]
for i,row in enumerate(json.loads((H/'manifest.json').read_text())['images']):
    p=H/'originals'/row['file'];assert hashlib.sha256(p.read_bytes()).hexdigest()==row['sha256']
    with Image.open(p) as im:im=ImageOps.exif_transpose(im).convert('RGB')
    a=np.array(im);h,w=a.shape[:2];t=time.perf_counter()
    b=np.asarray(im.resize((384,384),Image.Resampling.BILINEAR),np.float32)/255
    x=np.ascontiguousarray(((b-np.float32([.485,.456,.406]))/np.float32([.229,.224,.225])).transpose(2,0,1)[None])
    out=dict(zip([o.name for o in s.get_outputs()],s.run(None,{s.get_inputs()[0].name:x})))
    q=out['corners'].reshape(4,2).astype(float);sharp=float(out['sharpness'].ravel()[0]);ms=(time.perf_counter()-t)*1000
    valid=np.isfinite(q).all() and np.all(q>=0) and np.all(q<=1) and np.isfinite(sharp) and sharp>=.02
    if valid:
        c=q.mean(0);q=q[np.argsort(np.arctan2((q[:,1]-c[1])*h,(q[:,0]-c[0])*w))];q=np.roll(q,-np.argmin(q[:,0]*w+q[:,1]*h),axis=0)
        u=np.roll(q,-1,0)-q;v=np.roll(q,-2,0)-np.roll(q,-1,0);ds=np.linalg.norm(u*[w,h],axis=1)
        valid=bool(np.all(u[:,0]*v[:,1]-u[:,1]*v[:,0]>0) and cv2.contourArea(q.astype('float32'))>=.002 and ds[1]+ds[3]>ds[0]+ds[2])
    if valid:
        mat=cv2.getPerspectiveTransform(np.float32(q*[w,h]),np.float32([[0,0],[639,0],[639,895],[0,895]]));crop=cv2.warpPerspective(a,mat,(640,896));Image.fromarray(crop).save(O/f'{i:02}-crop.png')
    draw=ImageDraw.Draw(im)
    if np.isfinite(q).all():
        points=[tuple(p) for p in q*[w,h]];draw.line(points+[points[0]],fill='lime' if valid else 'red',width=max(4,w//200))
    im.thumbnail((600,800));im.save(O/f'{i:02}-overlay.jpg')
    rows.append({'index':i,'file':row['file'],'sha256':row['sha256'],'valid':bool(valid),'quad':q.tolist(),'sharpness':sharp,'detect_ms':ms})
(O/'detection.json').write_text(json.dumps({'detector_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'policy':'Frozen FastWeb1.39 whole frame RGB384, sharpness>=.02, same previous geometry checks. No threshold tuning. Identification not yet run.','rows':rows},indent=2))
for start in range(0,len(rows),8):
    sheet=Image.new('RGB',(1280,940),'#ddd');d=ImageDraw.Draw(sheet)
    for j,r in enumerate(rows[start:start+8]):
        x=j%4*320;y=j//4*470;d.text((x+5,y+5),str(r['index'])+' '+r['file'],fill='black')
        p=O/f"{r['index']:02}-crop.png" if r['valid'] else O/f"{r['index']:02}-overlay.jpg"
        with Image.open(p) as im:im.thumbnail((310,440));sheet.paste(im,(x,y+25))
    sheet.save(O/f'crops-{start//8}.jpg')
print('detections',sum(r['valid'] for r in rows),'/',len(rows))
