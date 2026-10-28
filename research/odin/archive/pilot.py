"""Small contrastive engineering pilot; not a production training run."""
import argparse, hashlib, json, random, time
from pathlib import Path
import numpy as np
import torch
from torch import nn
from torch.nn import functional as F
from torchvision import models, transforms as T
from PIL import Image
ROOT=Path(__file__).resolve().parent; OUT=ROOT/'pilot-01'

class Encoder(nn.Module):
    def __init__(self,pretrained=True):
        super().__init__()
        self.backbone=models.mobilenet_v3_small(weights=models.MobileNet_V3_Small_Weights.DEFAULT if pretrained else None).features
        self.pool=nn.AdaptiveAvgPool2d(1)
        self.project=nn.Linear(576,256)
    def forward(self,x):
        return F.normalize(self.project(self.pool(self.backbone(x)).flatten(1)),dim=1)

def tensor(im):
    a=np.array(im.resize((320,448),Image.Resampling.BILINEAR),dtype=np.float32)/255
    return torch.from_numpy(((a-np.float32([.485,.456,.406]))/np.float32([.229,.224,.225])).transpose(2,0,1).copy())

def main():
    parser=argparse.ArgumentParser(); parser.add_argument('--steps',type=int,default=200); args=parser.parse_args()
    if (OUT/'trained.pt').exists():
        raise SystemExit('Completed checkpoint exists; use a new run directory, do not overwrite.')
    random.seed(20261026); np.random.seed(20261026); torch.manual_seed(20261026); torch.set_num_threads(4)
    assert torch.cuda.is_available(),'CUDA required; do not silently benchmark CPU'
    torch.cuda.set_device(0)
    print(torch.cuda.get_device_name(),torch.__version__,torch.cuda.get_device_capability(),flush=True)
    split=json.loads((OUT/'split.json').read_text()); train=split['train']; val=split['validation']
    assert len(train)>=64 and len(val)>=64
    model=Encoder().cuda(); torch.save(model.state_dict(),OUT/'initial.pt')
    images=[]
    for r in train:
        p=ROOT/r['path']; assert hashlib.sha256(p.read_bytes()).hexdigest()==r['sha256']
        with Image.open(p) as im: images.append(im.convert('RGB').resize((320,448)))
    augment=T.Compose([T.RandomPerspective(.10,p=.6,fill=127),T.RandomAffine(4,translate=(.025,.025),scale=(.93,1.03),fill=127),T.ColorJitter(.3,.3,.15,.015),T.RandomApply([T.GaussianBlur(5,(.1,2.))],p=.5)])
    opt=torch.optim.AdamW(model.parameters(),lr=2e-4,weight_decay=1e-4)
    scaler=torch.amp.GradScaler('cuda'); timings=[]; records=[]; batch=32
    model.train(); start=time.perf_counter()
    for step in range(args.steps):
        if (ROOT/'STOP_TRAIN').exists(): break
        tick=time.perf_counter(); ix=random.sample(range(len(images)),batch)
        x=torch.stack([tensor(augment(images[i])) for i in ix]+[tensor(augment(images[i])) for i in ix]).cuda()
        opt.zero_grad(set_to_none=True)
        with torch.autocast('cuda',dtype=torch.float16):
            z=model(x)
        # Cross-view retrieval; distinct illustration classes in each batch.
        logits=z[:batch].float() @ z[batch:].float().T / .1
        target=torch.arange(batch,device='cuda')
        loss=(F.cross_entropy(logits,target)+F.cross_entropy(logits.T,target))/2
        assert torch.isfinite(loss), 'Non-finite loss'
        scaler.scale(loss).backward(); scaler.unscale_(opt); torch.nn.utils.clip_grad_norm_(model.parameters(),1)
        scaler.step(opt); scaler.update(); torch.cuda.synchronize()
        elapsed=time.perf_counter()-tick
        if step>=10: timings.append(elapsed)
        records.append({'step':step+1,'loss':float(loss.detach()),'seconds':elapsed})
        if (step+1)%20==0: print(records[-1],flush=True)
    torch.save(model.state_dict(),OUT/'trained.pt')
    result={'device':torch.cuda.get_device_name(),'torch':torch.__version__,'cuda':torch.version.cuda,'steps':len(records),'batch_distinct_arts':batch,'views_per_step':2*batch,'wall_seconds':time.perf_counter()-start,'step_p50_seconds':float(np.median(timings)),'step_p95_seconds':float(np.percentile(timings,95)),'peak_allocated_mib':torch.cuda.max_memory_allocated()/1024**2,'records':records,'scope':'Training wall includes CPU augmentations and GPU sync; first10steps excluded from percentiles. Engineering pilot, not full training.'}
    (OUT/'training.json').write_text(json.dumps(result,indent=2)); print(json.dumps({k:v for k,v in result.items() if k!='records'}),flush=True)

if __name__=='__main__':main()
