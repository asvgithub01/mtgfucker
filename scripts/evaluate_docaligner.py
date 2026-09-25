"""Offline Docsaid DocAligner heatmap adapter vs saved Android quads (NOT PC OpenCV port).
Upstream reference: DocsaidLab/DocAligner heatmap_reg/infer.py commit
3275b0f07f8e99d8c01cb0774dea2549be1416b6 (Apache-2.0).
Uses original upstream ONNX weights; reproduces resize/threshold/Otsu/largest-contour
centroid postprocessing with OpenCV instead of Capybara. Photos never leave disk.
CPU times are PC only, exclude decoding/session init. Baseline is stored live Android
quad, including temporal consensus. Fixed-session samples are labelled, not automatic.
"""
import argparse, hashlib, json, time, platform
from pathlib import Path
import cv2
import numpy as np
import onnxruntime as ort

def predict(session, image):
    h,w=image.shape[:2]
    tensor=np.transpose(cv2.resize(image,(256,256),interpolation=cv2.INTER_LINEAR),(2,0,1))[None].astype(np.float32)/255.
    outputs={v.name:x for v,x in zip(session.get_outputs(),session.run(None,{'img':tensor}))}
    if 'points' in outputs:
        if float(outputs['has_obj'].reshape(-1)[0]) <= .5: return []
        return outputs['points'].reshape(4,2).astype(float).tolist()
    maps=outputs['heatmap'][0]
    points=[]
    for heat in maps:
        heat=cv2.resize(heat,(w,h),interpolation=cv2.INTER_LINEAR)
        heat[heat<.3]=0
        mask=cv2.threshold((heat*255).astype(np.uint8),0,255,cv2.THRESH_BINARY|cv2.THRESH_OTSU)[1]
        contours,_=cv2.findContours(mask,cv2.RETR_EXTERNAL,cv2.CHAIN_APPROX_SIMPLE)
        contours=[c for c in contours if len(c)>1 and cv2.contourArea(c)>0]
        if not contours: return []
        area=max(cv2.contourArea(c) for c in contours)
        winners=[c for c in contours if cv2.contourArea(c)==area]
        if len(winners)!=1: return []
        m=cv2.moments(winners[0]);points.append([m['m10']/(m['m00']+1e-5)/w,m['m01']/(m['m00']+1e-5)/h])
    return points

def run(source,models,output):
    output.mkdir(parents=True,exist_ok=True)
    sessions={}; provenance={}
    for path in models:
        options=ort.SessionOptions();options.intra_op_num_threads=1;options.inter_op_num_threads=1
        start=time.perf_counter();sessions[path.stem]=ort.InferenceSession(str(path),sess_options=options,providers=['CPUExecutionProvider'])
        provenance[path.stem]={'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'bytes':path.stat().st_size,'load_ms':(time.perf_counter()-start)*1000}
    rows=[];thumbs=[]
    for n,f in enumerate(sorted(source.glob('*-original.jpg'))):
        stem=f.name.removesuffix('-original.jpg');meta=json.loads((source/(stem+'.json')).read_text(encoding='utf-8'))
        image=cv2.imread(str(f));h,w=image.shape[:2]
        baseline=[[float(v) for v in p.split(',')] for p in meta['boundaryQuad'].split(';')]
        row={'id':stem,'baseline_mode':meta['boundaryMode'],'baseline_quad':baseline,'models':{}}
        for name,session in sessions.items():
            if n==0:
                for _ in range(3): predict(session,image)
            start=time.perf_counter();points=predict(session,image);elapsed=(time.perf_counter()-start)*1000
            row['models'][name]={'quad':points,'ms':elapsed}
        rows.append(row)
        thumb=cv2.resize(image,(250,540));cv2.rectangle(thumb,(0,0),(250,43),(0,0,0),-1)
        cv2.putText(thumb,f'{n:02d} {stem[:13]}',(4,15),cv2.FONT_HERSHEY_SIMPLEX,.40,(255,255,255),1)
        cv2.putText(thumb,'AUTO' if 'AUTO' in row['baseline_mode'] else 'FIXED',(4,34),cv2.FONT_HERSHEY_SIMPLEX,.45,(255,255,255),1)
        all_quads=[(baseline,(0,0,255))]+[(row['models'][name]['quad'],color) for name,color in zip(sessions,[(0,255,0),(255,255,0),(255,0,255),(0,255,255)])]
        for points,color in all_quads:
            if len(points)==4:cv2.polylines(thumb,[np.round(np.array(points)*[250,540]).astype(np.int32)],True,color,2)
        thumbs.append(thumb)
    for start in range(0,len(thumbs),10):
        group=thumbs[start:start+10];group += [np.zeros((540,250,3),np.uint8)]*(10-len(group))
        cv2.imwrite(str(output/f'sheet-{start//10}.jpg'),np.vstack([np.hstack(group[:5]),np.hstack(group[5:])]))
    result={'runtime':ort.__version__,'platform':platform.platform(),'threads':1,'models':provenance,'rows':rows}
    (output/'results.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    for name in sessions:
        ts=[r['models'][name]['ms'] for r in rows]
        print(name,'photos',len(rows),'p50',np.median(ts),'p95',np.percentile(ts,95),'fourCorners',sum(len(r['models'][name]['quad'])==4 for r in rows))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--source',type=Path,required=True);p.add_argument('--models',type=Path,nargs='+',required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();run(a.source,a.models,a.output)
