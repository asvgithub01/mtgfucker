"""Bounded detector smoke experiment, NOT a validated mobile model. No Odin imports."""
import argparse, hashlib, json, random, time
from pathlib import Path
import numpy as np
from PIL import Image
import torch
from torch import nn
from validate import validate

class CornerNet(nn.Module):
    def __init__(self):
        super().__init__()
        layers=[];c=3
        for out in (16,32,64,96):
            layers += [nn.Conv2d(c,out,3,2,1),nn.ReLU()];c=out
        self.net=nn.Sequential(*layers,nn.AdaptiveAvgPool2d((4,4)),nn.Flatten(),nn.Linear(96*16,128),nn.ReLU(),nn.Linear(128,9))
    def forward(self,x):
        y=self.net(x);return y[:,0],y[:,1:].sigmoid()

def main():
    p=argparse.ArgumentParser();p.add_argument('--dataset',required=True);p.add_argument('--out',required=True);p.add_argument('--steps',type=int,default=300);p.add_argument('--max-seconds',type=int,default=600);a=p.parse_args()
    assert 1<=a.steps<=3000 and 1<=a.max_seconds<=1800
    assert torch.cuda.is_available(), 'CUDA required; do not silently benchmark CPU'
    root=Path(a.dataset);rows=validate(root);out=Path(a.out);out.mkdir(parents=True,exist_ok=False)
    cfg=vars(a)|{'modelName':'Esquinitas','experimentVersion':'pilot-v1','teachersUsed':False,'seed':20261028,'torch':torch.__version__,'device':torch.cuda.get_device_name(),'input':[3,224,160],'runnerSha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),'purpose':'synthetic engineering pilot, no promotion'}
    (out/'config.json').write_text(json.dumps(cfg,indent=2))
    (out/'dataset-hashes.json').write_text(json.dumps({r['image']:r['imageSha256'] for r in rows},indent=2))
    random.seed(cfg['seed']);torch.manual_seed(cfg['seed']);torch.cuda.manual_seed_all(cfg['seed'])
    def batch(batchrows):
        xs=[];ys=[];cs=[]
        for r in batchrows:
            with Image.open(root/r['image']) as im:xs.append(torch.from_numpy(np.asarray(im.convert('RGB').resize((160,224),Image.Resampling.BILINEAR),dtype=np.float32).transpose(2,0,1).copy()/255))
            ys.append(float(r['present']));cs.append([v for pt in r['corners'] for v in (pt['x'],pt['y'])] if r['present'] else [0.]*8)
        return torch.stack(xs).cuda(),torch.tensor(ys).cuda(),torch.tensor(cs).cuda()
    train=[r for r in rows if r['split']=='train'];val=[r for r in rows if r['split']=='validation']
    model=CornerNet().cuda();opt=torch.optim.AdamW(model.parameters(),lr=3e-4);start=time.monotonic()
    completed=0
    with (out/'metrics.jsonl').open('x') as log:
        for step in range(a.steps):
            if time.monotonic()-start>a.max_seconds:break
            x,present,corners=batch(random.choices(train,k=16));score,quad=model(x)
            mask=present>0;loss=nn.functional.binary_cross_entropy_with_logits(score,present)
            if mask.any():loss=loss+5*nn.functional.smooth_l1_loss(quad[mask],corners[mask])
            assert torch.isfinite(loss);opt.zero_grad();loss.backward();opt.step()
            completed=step+1
            log.write(json.dumps({'step':step+1,'loss':loss.item(),'elapsed':time.monotonic()-start})+'\n');log.flush()
            if (step+1)%100==0:torch.save(model.state_dict(),out/f'step-{step+1:04}.pt')
    torch.save(model.state_dict(),out/'final.pt');model.eval();errors=[];presence=[];visible_errors=[];hidden_errors=[];positive=[];negative=[]
    with torch.no_grad():
        for i in range(0,len(val),16):
            chunk=val[i:i+16];x,p,c=batch(chunk);s,q=model(x);pred=s.sigmoid()>.5;presence.extend((pred==p.bool()).cpu().tolist())
            for j,row in enumerate(chunk):
                (positive if row['present'] else negative).append(bool(pred[j].item()))
                if row['present']:
                    e=(q[j].reshape(4,2)-c[j].reshape(4,2)).norm(dim=1).cpu().tolist();errors.append(float(np.mean(e)))
                    for v,err in zip(row.get('cornerVisible',[True]*4),e):(visible_errors if v else hidden_errors).append(err)
    mean=lambda x:float(np.mean(x)) if x else None
    (out/'result.json').write_text(json.dumps({'steps':completed,'seconds':time.monotonic()-start,'validationFrames':len(val),'meanNormalizedCornerError':mean(errors),'visibleCornerError':mean(visible_errors),'hiddenCornerError':mean(hidden_errors),'hiddenCornerCount':len(hidden_errors),'presenceAccuracy':mean(presence),'presenceRecall':mean(positive),'falsePositiveRate':mean(negative),'realEvaluation':'NOT RUN','promoted':False},indent=2))

if __name__=='__main__':main()
