"""Render synthetic validation predictions. Green=GT, red=prediction. No real photos."""
import argparse,html,json
from pathlib import Path
import numpy as np
import torch
from PIL import Image,ImageDraw
from train_pilot import CornerNet
from validate import validate

def main():
    p=argparse.ArgumentParser();p.add_argument('--dataset',required=True);p.add_argument('--run',required=True);p.add_argument('--out',required=True);a=p.parse_args()
    root=Path(a.dataset);run=Path(a.run);out=Path(a.out);rows=validate(root)
    frozen=json.loads((run/'dataset-hashes.json').read_text());assert frozen=={r['image']:r['imageSha256'] for r in rows}
    out.mkdir(parents=True,exist_ok=False);model=CornerNet();model.load_state_dict(torch.load(run/'final.pt',map_location='cpu',weights_only=True));model.eval()
    chosen=[];seen=set()
    for r in rows:
        if r['split']=='validation' and r['present'] and r['group'] not in seen:
            chosen.append(r);seen.add(r['group'])
            if len(chosen)==12:break
    cards=[]
    for r in chosen:
        with Image.open(root/r['image']) as im:
            im=im.convert('RGB');x=torch.from_numpy(np.asarray(im.resize((160,224),Image.Resampling.BILINEAR),dtype=np.float32).transpose(2,0,1).copy()/255)[None]
            with torch.no_grad():presence,pred=model(x)
            d=ImageDraw.Draw(im);gt=[(c['x']*im.width,c['y']*im.height) for c in r['corners']];pr=[(float(x)*im.width,float(y)*im.height) for x,y in pred.reshape(4,2)]
            d.line(gt+[gt[0]],fill='lime',width=4);d.line(pr+[pr[0]],fill='red',width=4)
            name=r['image'];im.save(out/name);cards.append(f'<figure><img width="260" src="{name}"><figcaption>{name}</figcaption></figure>')
    result=json.loads((run/'result.json').read_text())
    body='<h1>Esquinitas — piloto sintético 01</h1><p>NO apto para integrar: error de esquinas elevado. No evaluado con fotos reales. Teachers no utilizados.</p><p>80 texturas (64 entrenamiento,16 validación),1600 renders,mitad full art. 300 pasos. Verde: geometría real; rojo: predicción.</p><pre>'+html.escape(json.dumps(result,indent=2))+'</pre><section>'+''.join(cards)+'</section>'
    (out/'report.html').write_text('<!doctype html><meta charset="utf-8"><title>Esquinitas piloto01</title><style>body{font:16px system-ui;margin:30px;background:#eee}section{display:flex;flex-wrap:wrap}figure{margin:8px}pre{background:white;padding:16px}</style>'+body,encoding='utf8')
    print(out/'report.html')

if __name__=='__main__':main()
