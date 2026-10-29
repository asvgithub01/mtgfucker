"""Cross-corpus development evaluation; verifies split consistency across renders."""
import argparse, json
from pathlib import Path
import train_backbone as b
from train_heads import HeadNet, evaluate
from validate import validate
from PIL import Image
import numpy as np
import torch

def main():
    p=argparse.ArgumentParser(); p.add_argument('--unity',required=True); p.add_argument('--out',required=True)
    p.add_argument('--candidate-run', help='Evaluate only this new pretrained run; keep earlier comparisons untouched')
    p.add_argument('--corpora', nargs='+', default=['pilot-01','square-diverse-01'])
    a=p.parse_args(); root=Path(a.unity); out=Path(a.out); out.mkdir(parents=True,exist_ok=False)
    torch.set_num_threads(4); seen={}; corpora={}
    for name in a.corpora:
        path=root/'data/mtg-pilot-01/renders'/name; rows=validate(path)
        for r in rows:
            for k in ('group','textureSha256'):
                key=(k,r[k]); assert key not in seen or seen[key]==r['split'], 'Cross-corpus split leakage'
                seen[key]=r['split']
        corpora[name]=(path,[r for r in rows if r['split']=='validation'])
    results={}
    for corpus,(path,rows) in corpora.items():
        images=[]
        for r in rows:
            with Image.open(path/r['image']) as im:
                images.append(np.asarray(im.convert('RGB').resize((160,224),Image.Resampling.BILINEAR)).transpose(2,0,1).copy())
        xs=torch.from_numpy(np.stack(images)).cuda().float()/255
        ps=torch.tensor([float(r['present']) for r in rows],device='cuda')
        cs=torch.tensor([[v for pt in r['corners'] for v in (pt['x'],pt['y'])] if r['present'] else [0.]*8 for r in rows],device='cuda')
        results[corpus]={}
        candidates=[(a.candidate_run,a.candidate_run+'/pretrained/final.pt')] if a.candidate_run else [('spatial01','spatial-01/generalization-simcc/final.pt'),('pretrained-original','backbone-01/pretrained/final.pt'),('pretrained-square','backbone-square-01/pretrained/final.pt')]
        for name,checkpoint in candidates:
            model=HeadNet('simcc',8) if name=='spatial01' else b.BackboneNet()
            model.load_state_dict(torch.load(root/'runs'/checkpoint,map_location='cpu',weights_only=True)); model.cuda()
            x=xs if name=='spatial01' else (xs-torch.tensor([.485,.456,.406],device='cuda')[None,:,None,None])/torch.tensor([.229,.224,.225],device='cuda')[None,:,None,None]
            metrics,records=evaluate(model,list(range(len(rows))),x,ps,cs,rows)
            results[corpus][name]=metrics
            (out/f'{corpus}-{name}.json').write_text(json.dumps(records))
            del model
        del xs
    (out/'result.json').write_text(json.dumps(results,indent=2)); print(json.dumps(results,indent=2))

if __name__=='__main__':main()
