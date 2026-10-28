"""Versioned second experiment; never reads heldout photographs. No Android writes."""
import json,random,time,hashlib,shutil,sqlite3
from pathlib import Path
import numpy as np
import torch
from torch.nn import functional as F
from torchvision import transforms as T
from PIL import Image,ImageDraw,ImageFilter
from pilot import Encoder,tensor
R=Path(__file__).resolve().parent;O=R/'pilot-02'
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def save(name,data):
    p=O/name;t=p.with_suffix('.tmp');t.write_text(json.dumps(data,indent=2),encoding='utf8');t.replace(p)
def sample_batch(train,neighbors,batch=32):
    chosen=[];groups=set()
    for anchor in random.sample(range(len(train)),len(train)):
        for i in [anchor]+random.sample(neighbors[anchor],len(neighbors[anchor]))[:1]:
            if train[i]['group'] in groups:continue
            groups.add(train[i]['group']);chosen.append(i)
            if len(chosen)==batch:return chosen
    raise ValueError('Insufficient distinct training groups')
def main():
    O.mkdir(exist_ok=True)
    if (O/'config.json').exists():raise RuntimeError('Existing run: do not overwrite; choose another version')
    random.seed(20261026);np.random.seed(20261026);torch.manual_seed(20261026);torch.set_num_threads(4)
    assert torch.cuda.is_available();torch.cuda.reset_peak_memory_stats()
    split=json.loads((R/'pilot-01/split.json').read_text(encoding='utf8'));train=split['train']
    assert not ({x['group'] for x in train}&{x['group'] for x in split['validation']})
    # Explicitly exclude known new-photo card names too; no reading heldout images.
    reserved={'archmage emeritus','pick your poison','sleight of hand','giant growth','bartolomé del presidio','deduce'}
    assert not ({r['name'].casefold() for r in train}&reserved)
    pairs=json.loads((R/'expanded-01/hard-negatives-train-only.json').read_text())['pairs'];index={r['id']:i for i,r in enumerate(train)}
    neighbors={index[p['id']]:[index[n['id']] for n in p['negatives']] for p in pairs}
    for _ in range(100):
        ix=sample_batch(train,neighbors);assert len(ix)==32 and len({train[i]['group'] for i in ix})==32
    config={'steps':1000,'batch':32,'views':2,'seed':20261026,'lr':.0001,'source_weights_sha256':sha(R/'pilot-01/trained.pt'),'split_sha256':sha(R/'pilot-01/split.json'),'hard_pairs_sha256':sha(R/'expanded-01/hard-negatives-train-only.json'),'scope':'Same 2048 training arts, warm start pilot01; hard-negative batches plus stronger synthetic corruption. No heldout photos or tuning. Final checkpoint predetermined, not selected on heldout. Not a controlled single-factor ablation.'}
    save('config.json',config);shutil.copy2(R/'pilot-01/split.json',O/'split.json')
    # Freeze available gallery now, independent of downloads continuing in background.
    old=json.loads((R/'expanded-01/manifest.json').read_text(encoding='utf8'))
    refs=list(old['refs']);seen={x['key'] for x in refs}
    db=sqlite3.connect(R/'manifest.sqlite')
    for raw,h in db.execute('select metadata,sha256 from images where sha256 is not null order by id').fetchall():
        row=json.loads(raw)
        if row['id'] not in seen:
            row['key']=row['id'];row['sha256']=h;refs.append(row);seen.add(row['id'])
    db.close();save('catalog-manifest.json',{'refs':refs,'paired_gallery_prefix':len(old['refs']),'scope':'Frozen available English catalogue, not all Scryfall. First rows exactly match expanded01 for paired comparison.'})
    images=[]
    for row in train:
        p=R/row['path'];assert sha(p)==row['sha256']
        with Image.open(p) as im:images.append(im.convert('RGB').resize((320,448)))
    model=Encoder(False).cuda();model.load_state_dict(torch.load(R/'pilot-01/trained.pt',map_location='cuda',weights_only=True));model.train()
    base=T.Compose([T.RandomPerspective(.13,p=.6,fill=127),T.RandomAffine(4,translate=(.03,.03),scale=(.92,1.04),fill=127),T.ColorJitter(.30,.30,.16,.015),T.RandomApply([T.GaussianBlur(7,(.1,2.0))],p=.4)])
    def aug(im):
        im=base(im)
        if random.random()<.3:
            w=random.randint(120,250);im=im.resize((w,round(w*1.4)),Image.Resampling.BILINEAR).resize((320,448),Image.Resampling.BILINEAR)
        if random.random()<.25:
            mask=Image.new('L',im.size);d=ImageDraw.Draw(mask);x=random.randint(-80,280);y=random.randint(-80,400)
            d.ellipse((x,y,x+random.randint(70,200),y+random.randint(70,240)),fill=random.randint(25,95))
            mask=mask.filter(ImageFilter.GaussianBlur(24));im=Image.composite(Image.new('RGB',im.size,'white'),im,mask)
        return tensor(im)
    opt=torch.optim.AdamW(model.parameters(),lr=config['lr'],weight_decay=1e-4);scaler=torch.amp.GradScaler('cuda')
    scheduler=torch.optim.lr_scheduler.CosineAnnealingLR(opt,config['steps'],eta_min=1e-5)
    records=[];start=time.perf_counter()
    for step in range(config['steps']):
        if (R/'STOP_TRAIN').exists():
            torch.save(model.state_dict(),O/'stopped.pt');save('status.json',{'phase':'stopped','step':step});return
        tick=time.perf_counter();ix=sample_batch(train,neighbors)
        x=torch.stack([aug(images[i]) for i in ix]+[aug(images[i]) for i in ix]).cuda()
        opt.zero_grad(set_to_none=True)
        with torch.autocast('cuda',dtype=torch.float16):z=model(x)
        logits=z[:32].float()@z[32:].float().T/.1;targets=torch.arange(32,device='cuda')
        loss=(F.cross_entropy(logits,targets)+F.cross_entropy(logits.T,targets))/2;assert torch.isfinite(loss)
        scaler.scale(loss).backward();scaler.unscale_(opt);torch.nn.utils.clip_grad_norm_(model.parameters(),1);scaler.step(opt);scaler.update();scheduler.step();torch.cuda.synchronize()
        records.append({'step':step+1,'loss':float(loss.detach()),'seconds':time.perf_counter()-tick})
        if (step+1)%20==0:
            state={'phase':'training','step':step+1,'total':config['steps'],'last':records[-1],'elapsed_seconds':time.perf_counter()-start};save('status.json',state);print(json.dumps(state),flush=True)
        if (step+1)%200==0:torch.save(model.state_dict(),O/f'step-{step+1:04}.pt')
    torch.save(model.state_dict(),O/'trained.pt');save('training.json',{'device':torch.cuda.get_device_name(),'wall_seconds':time.perf_counter()-start,'peak_allocated_mib':torch.cuda.max_memory_allocated()/1024**2,'steps':len(records),'records':records})
    save('status.json',{'phase':'training_complete','step':len(records)})
    from prepare_second import prepare
    prepare()
if __name__=='__main__':main()
