"""Generate review proposals, never automatic training labels."""
import sys, json, hashlib, time, argparse
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'build/odin-parity-pillow123'))
import PIL
from PIL import Image, ImageDraw
import numpy as np
import onnxruntime as ort
import cv2

def order(q):
    if not np.isfinite(q).all(): return None
    c=q.mean(0)
    q=q[np.argsort(np.arctan2(q[:,1]-c[1],q[:,0]-c[0]))]
    return np.roll(q,-int(np.argmin(q.sum(1))),axis=0)

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--manifest',type=Path,required=True)
    ap.add_argument('--out',type=Path,required=True)
    args=ap.parse_args()
    if args.out.exists(): raise RuntimeError('Output already exists')
    assert PIL.__version__=='12.3.0'
    args.out.mkdir(parents=True)
    root=Path('C:/Users/nubor/.codex/worktrees/dinov2-benchmark/mtgfucker/build')
    paths={'cornelius':root/'diego-android-assets-v2/cornelius.onnx','fastweb':root/'fastweb-assets-v26/fastweb-single-1.39.onnx'}
    opts=ort.SessionOptions();opts.intra_op_num_threads=4;opts.inter_op_num_threads=1
    cv2.setNumThreads(1)
    sessions={k:ort.InferenceSession(str(p),opts,providers=['CPUExecutionProvider']) for k,p in paths.items()}
    cfg={'models':{k:{'path':str(p),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for k,p in paths.items()},'pillow':PIL.__version__,'ort':ort.__version__,'input':'RGB384 bilinear ImageNet normalization; raw outputs, no acceptance gate','source_sha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),'manifest_sha256':hashlib.sha256(args.manifest.read_bytes()).hexdigest()}
    (args.out/'config.json').write_text(json.dumps(cfg,indent=2),encoding='utf-8')
    rows=[]
    for item in json.loads(args.manifest.read_text(encoding='utf-8')):
        if item['session']!='1793469343529': continue
        p=Path(item['path']);assert hashlib.sha256(p.read_bytes()).hexdigest()==item['sha256']
        im=Image.open(p).convert('RGB')
        a=np.asarray(im.resize((384,384),Image.Resampling.BILINEAR),np.float32)/255
        x=np.ascontiguousarray(((a-np.float32([.485,.456,.406]))/np.float32([.229,.224,.225])).transpose(2,0,1)[None])
        row={**item,'proposals':{},'training_eligible':False,'review_status':'teacher-proposals-unreviewed'}
        overlay=im.copy();d=ImageDraw.Draw(overlay)
        for name,ss in sessions.items():
            t=time.perf_counter()
            out=dict(zip([v.name for v in ss.get_outputs()],ss.run(None,{ss.get_inputs()[0].name:x})))
            ms=(time.perf_counter()-t)*1000
            raw=out['corners'].reshape(4,2).astype(float);q=order(raw)
            row['proposals'][name]={'raw_corners':raw.tolist(),'ordered_corners':None if q is None else q.tolist(),'sharpness':float(out['sharpness'].ravel()[0]),'inference_ms_pc_cpu4':ms}
            if q is not None:
                pts=[tuple(v) for v in q*[im.width,im.height]]
                d.line(pts+[pts[0]],fill='cyan' if name=='cornelius' else 'orange',width=7)
        q1=order(np.array(row['proposals']['cornelius']['raw_corners']));q2=order(np.array(row['proposals']['fastweb']['raw_corners']))
        if q1 is not None and q2 is not None:
            a1=cv2.convexHull(q1.astype('float32'));a2=cv2.convexHull(q2.astype('float32'))
            inter,_=cv2.intersectConvexConvex(a1,a2);union=cv2.contourArea(a1)+cv2.contourArea(a2)-inter
            row['teacher_agreement_iou']=float(inter/union) if union>0 else 0.
        overlay.thumbnail((900,900));overlay.save(args.out/f'{item["index"]:03d}-overlay.jpg',quality=94)
        rows.append(row)
        (args.out/'proposals.json').write_text(json.dumps(rows,indent=2),encoding='utf-8')
        if len(rows)%20==0:print(len(rows),flush=True)
    for start in range(0,len(rows),20):
        sheet=Image.new('RGB',(1200,1040),'white');d=ImageDraw.Draw(sheet)
        for k,r in enumerate(rows[start:start+20]):
            im=Image.open(args.out/f'{r["index"]:03d}-overlay.jpg');im.thumbnail((235,235));xx=k%5*240;yy=k//5*260
            sheet.paste(im,(xx,yy+24));d.text((xx+3,yy+4),f'{r["index"]} IoU {r.get("teacher_agreement_iou",0):.2f}',fill='black')
        sheet.save(args.out/f'contact-{start:03d}.jpg')
    status={'status':'proposals_complete_not_labels','count':len(rows),'training_started':False,'next':'Independent corner review, visibility/uncertainty, group split audit. Agreement is not correctness.'}
    (args.out/'status.json').write_text(json.dumps(status,indent=2),encoding='utf-8')
    print(json.dumps(status),flush=True)

if __name__=='__main__': main()
