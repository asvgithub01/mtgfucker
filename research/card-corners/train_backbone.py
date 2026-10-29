"""Bounded synthetic MobileNetV3 SimCC initialization ablation; no real training."""
import argparse, hashlib, json, random, sys, time
from pathlib import Path

# Match the lab's explicitly pinned resize contract, without mutating its venv.
PIN = Path(__file__).resolve().parents[2] / 'build/odin-parity-pillow123'
sys.path.insert(0, str(PIN))
import PIL
from PIL import Image
import numpy as np
import torch
from torch import nn
import torchvision
from torchvision.models import mobilenet_v3_small, MobileNet_V3_Small_Weights
from train_heads import coordinate_loss, evaluate
from validate import validate


class BackboneNet(nn.Module):
    def __init__(self, pretrained=False):
        super().__init__()
        self.features = mobilenet_v3_small(weights=MobileNet_V3_Small_Weights.DEFAULT if pretrained else None).features
        self.shared = nn.Sequential(nn.Flatten(), nn.Linear(576 * 7 * 5, 128), nn.ReLU())
        self.presence = nn.Linear(128, 1)
        self.coordinates = nn.Linear(128, 8 * 256)

    def forward(self, x):
        z = self.shared(self.features(x))
        return self.presence(z).flatten(), self.coordinates(z).reshape(-1, 8, 256)


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--dataset', required=True); p.add_argument('--out', required=True)
    p.add_argument('--steps', type=int, default=2000)
    a = p.parse_args()
    assert PIL.__version__ == '12.3.0', PIL.__version__
    assert torch.cuda.is_available() and 1 <= a.steps <= 3000
    torch.set_num_threads(4)
    root, out = Path(a.dataset), Path(a.out)
    out.mkdir(parents=True, exist_ok=False)
    rows = validate(root)
    train = [i for i,r in enumerate(rows) if r['split']=='train']
    val = [i for i,r in enumerate(rows) if r['split']=='validation']
    sha = lambda f: hashlib.sha256(Path(f).read_bytes()).hexdigest()
    config = dict(seed=20261029, steps=a.steps, batch=16, lr=3e-4, maxSecondsPerArm=900,
        input=[3,224,160], normalization='ImageNet', resize='Pillow12.3 bilinear',
        torch=torch.__version__, torchvision=torchvision.__version__, pillow=PIL.__version__,
        device=torch.cuda.get_device_name(), weightsUrl=MobileNet_V3_Small_Weights.DEFAULT.url,
        weightsSha=sha(Path(torch.hub.get_dir())/'checkpoints/mobilenet_v3_small-047dcff4.pth'),
        sourceHashes={n:sha(Path(__file__).with_name(n)) for n in ['train_backbone.py','train_heads.py','validate.py','train_pilot.py']},
        realPhotosUsed=False, teachersUsed=False, promoted=False,
        note='Development synthetic validation, previously reused; not independent physical test')
    (out/'config.json').write_text(json.dumps(config,indent=2))
    (out/'split.json').write_text(json.dumps(dict(train=train,validation=val,rows=rows)))
    arrays=[]
    for r in rows:
        with Image.open(root/r['image']) as im:
            arrays.append(np.asarray(im.convert('RGB').resize((160,224),Image.Resampling.BILINEAR)).transpose(2,0,1).copy())
    xs=torch.tensor(np.stack(arrays),device='cuda').float()/255
    xs=(xs-torch.tensor([.485,.456,.406],device='cuda')[None,:,None,None])/torch.tensor([.229,.224,.225],device='cuda')[None,:,None,None]
    ps=torch.tensor([float(r['present']) for r in rows],device='cuda')
    cs=torch.tensor([[v for pt in r['corners'] for v in (pt['x'],pt['y'])] if r['present'] else [0.]*8 for r in rows],device='cuda')
    results={}
    for pretrained in (False,True):
        name='pretrained' if pretrained else 'random'; folder=out/name; folder.mkdir()
        random.seed(config['seed']); torch.manual_seed(config['seed']); torch.cuda.manual_seed_all(config['seed'])
        model=BackboneNet(pretrained).cuda()
        opt=torch.optim.AdamW(model.parameters(),lr=config['lr'])
        model.train(); torch.cuda.synchronize(); start=time.perf_counter(); completed=0
        with (folder/'metrics.jsonl').open('x') as log:
            for step in range(a.steps):
                if time.perf_counter()-start>config['maxSecondsPerArm']: break
                ix=random.choices(train,k=16); score,q=model(xs[ix]); mask=ps[ix]>0
                loss=nn.functional.binary_cross_entropy_with_logits(score,ps[ix])
                if mask.any(): loss=loss+coordinate_loss(q[mask],cs[ix][mask])
                assert torch.isfinite(loss)
                opt.zero_grad(); loss.backward(); opt.step(); completed=step+1
                log.write(json.dumps(dict(step=completed,loss=loss.item()))+'\n'); log.flush()
                if completed%500==0: torch.save(model.state_dict(),folder/f'step-{completed}.pt')
        torch.cuda.synchronize(); seconds=time.perf_counter()-start
        torch.save(model.state_dict(),folder/'final.pt')
        metrics, records=evaluate(model,val,xs,ps,cs,rows)
        metrics.update(steps=completed,trainingSeconds=seconds,parameters=sum(p.numel() for p in model.parameters()))
        (folder/'predictions.json').write_text(json.dumps(records))
        (folder/'result.json').write_text(json.dumps(metrics,indent=2)); results[name]=metrics
        print(name,json.dumps(metrics),flush=True)
        del model,opt; torch.cuda.empty_cache()
    (out/'result.json').write_text(json.dumps(results,indent=2))


if __name__=='__main__': main()
