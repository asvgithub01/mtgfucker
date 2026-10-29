"""Post-hoc peak refinement on identical frozen weights; original implementation."""
import argparse, hashlib, json
from pathlib import Path
import numpy as np
from PIL import Image
import torch
from torch import nn
from train_heads import HeadNet, BINS, evaluate
from validate import validate


def parabolic(q):
    peak=q.argmax(-1)
    center=q.gather(-1,peak[...,None]).squeeze(-1)
    left=q.gather(-1,(peak-1).clamp(0,BINS-1)[...,None]).squeeze(-1)
    right=q.gather(-1,(peak+1).clamp(0,BINS-1)[...,None]).squeeze(-1)
    denom=left-2*center+right
    valid=(peak>0)&(peak<BINS-1)&(denom.abs()>1e-8)
    delta=torch.where(valid, .5*(left-right)/torch.where(valid,denom,torch.ones_like(denom)),torch.zeros_like(center)).clamp(-.5,.5)
    return (peak.float()+delta)/(BINS-1)


class Refined(nn.Module):
    def __init__(self,model):
        super().__init__(); self.model=model
    def forward(self,x):
        p,q=self.model(x); return p,parabolic(q)


def main():
    p=argparse.ArgumentParser();p.add_argument('--dataset');p.add_argument('--run');p.add_argument('--selftest',action='store_true');a=p.parse_args()
    q=-((torch.arange(BINS).float()-30.25)**2)[None,None,:].repeat(1,8,1)
    assert torch.allclose(parabolic(q)*255,torch.full((1,8),30.25),atol=1e-5)
    q=torch.zeros(1,8,BINS);assert torch.equal(parabolic(q),torch.zeros(1,8))
    q[...,0]=10;assert torch.equal(parabolic(q),torch.zeros(1,8))
    q=torch.zeros(1,8,BINS);q[...,30]=10;q[...,220]=9
    assert torch.allclose(parabolic(q)*255,torch.full((1,8),30.))
    if a.selftest: print('Parabolic tests PASS');return
    run=Path(a.run);root=Path(a.dataset);rows=validate(root)
    assert json.loads((run/'dataset-hashes.json').read_text())=={r['image']:r['imageSha256'] for r in rows}
    rows=[r for r in rows if r['split']=='validation'];cfg=json.loads((run/'config.json').read_text())
    torch.set_num_threads(4);assert torch.cuda.is_available()
    xs=[]
    for r in rows:
        with Image.open(root/r['image']) as im:xs.append(np.asarray(im.convert('RGB').resize((160,224),Image.Resampling.BILINEAR)).transpose(2,0,1).copy())
    xs=torch.from_numpy(np.stack(xs)).cuda().float()/255
    ps=torch.tensor([float(r['present']) for r in rows],device='cuda')
    cs=torch.tensor([[v for pt in r['corners'] for v in (pt['x'],pt['y'])] if r['present'] else [0.]*8 for r in rows],device='cuda')
    model=HeadNet('simcc',pool=cfg.get('pool',4)).cuda()
    checkpoint=run/'generalization-simcc/final.pt'
    model.load_state_dict(torch.load(checkpoint,weights_only=True,map_location='cuda'))
    results={'checkpointSha256':hashlib.sha256(checkpoint.read_bytes()).hexdigest(),'sourceSha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),'promoted':False}
    for name,m in [('argmax',model),('parabolic',Refined(model))]:
        metrics,_=evaluate(m,list(range(len(rows))),xs,ps,cs,rows);results[name]=metrics
    with (run/'decoder-probe.json').open('x') as f:json.dump(results,f,indent=2)
    print(json.dumps(results,indent=2))

if __name__=='__main__':main()
