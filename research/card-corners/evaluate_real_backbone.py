"""Small reviewed development probe, never a training set or benchmark claim."""
import argparse, hashlib, json, time
from pathlib import Path
import train_backbone as backbone  # pins Pillow before other imports
from train_heads import HeadNet, decode, quad_iou, geometry
from PIL import Image, ImageDraw
import numpy as np
import torch


def main():
    p=argparse.ArgumentParser(); p.add_argument('--manifest',required=True)
    p.add_argument('--unity',required=True); p.add_argument('--out',required=True)
    p.add_argument('--backbone-run', default='backbone-01')
    a=p.parse_args(); out=Path(a.out); out.mkdir(parents=True,exist_ok=False)
    manifest=json.loads(Path(a.manifest).read_text(encoding='utf-8-sig'))
    (out/'review-manifest.json').write_text(json.dumps(manifest,indent=2))
    torch.set_num_threads(4); root=Path(a.unity)
    models={}
    for name,path in [('spatial01','spatial-01/generalization-simcc/final.pt'),
                      ('random',a.backbone_run+'/random/final.pt'),
                      ('pretrained',a.backbone_run+'/pretrained/final.pt')]:
        model=HeadNet('simcc',8) if name=='spatial01' else backbone.BackboneNet()
        file=root/'runs'/path; model.load_state_dict(torch.load(file,map_location='cpu',weights_only=True))
        models[name]=(model.eval(),hashlib.sha256(file.read_bytes()).hexdigest())
    records=[]; figures=[]
    with torch.inference_mode():
        for index,r in enumerate(manifest['frames']):
            im=Image.open(r['path']).convert('RGB'); canvas=im.copy(); draw=ImageDraw.Draw(canvas)
            raw=torch.from_numpy(np.asarray(im.resize((160,224),Image.Resampling.BILINEAR)).transpose(2,0,1).copy()).float()[None]/255
            gt=r.get('corners'); row=dict(frame=r['id'],imageSha=hashlib.sha256(Path(r['path']).read_bytes()).hexdigest(),models={})
            for (name,(model,sha)),color in zip(models.items(),['red','orange','cyan']):
                x=raw if name=='spatial01' else (raw-torch.tensor([.485,.456,.406])[None,:,None,None])/torch.tensor([.229,.224,.225])[None,:,None,None]
                for _ in range(10): model(x)
                times=[]
                for _ in range(50):
                    t=time.perf_counter(); score,q=model(x); times.append((time.perf_counter()-t)*1000)
                corners=decode(q)[0].numpy().reshape(4,2)
                row['models'][name]=dict(sha256=sha,presence=score.sigmoid().item(),corners=corners.tolist(),
                    invalid=not geometry(corners),iou=quad_iou(corners,gt) if gt else None,
                    cpuMedianMs=float(np.median(times)),cpuP95Ms=float(np.percentile(times,95)))
                pts=[(float(x)*im.width,float(y)*im.height) for x,y in corners]
                draw.line(pts+[pts[0]],fill=color,width=6)
            if gt:
                pts=[(x*im.width,y*im.height) for x,y in gt]; draw.line(pts+[pts[0]],fill='lime',width=5)
            canvas.thumbnail((800,800)); canvas.save(out/f'{index}.png'); records.append(row)
            figures.append(f'<h2>{r["id"]}</h2><img src="{index}.png">')
    result=dict(note='Two identities, three development frames. Approximate manual visible-card labels, NOT teacher labels. No FPR/hidden-corner accuracy measurable here. CPU PyTorch timings exclude resize and are NOT Sony latency.',records=records)
    (out/'result.json').write_text(json.dumps(result,indent=2))
    (out/'report.html').write_text('<meta charset="utf-8"><h1>Esquinitas: prueba física exploratoria</h1><p>Verde: anotación manual aproximada. Rojo: spatial01. Naranja: aleatorio. Cian: preentrenado. Tres fotos, dos cartas; no estimación de precisión general. Primera foto sin GT completo.</p>'+''.join(figures),encoding='utf8')
    print(json.dumps(result,indent=2))


if __name__=='__main__': main()
