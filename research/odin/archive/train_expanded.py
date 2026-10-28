"""Bounded expanded contrastive run. Requires completed download, final split and verified audit."""
import argparse,collections,hashlib,json,random,shutil,time
from pathlib import Path
import numpy as np
import torch
from torch.nn import functional as F
from torchvision import transforms as T
from PIL import Image,ImageDraw,ImageFilter
from pilot import Encoder,tensor
R=Path(__file__).resolve().parent

def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def write(out,name,value):
 p=out/name;t=p.with_suffix('.tmp');t.write_text(json.dumps(value,indent=2),encoding='utf8');t.replace(p)

class Batches(torch.utils.data.Dataset):
 def __init__(self,rows,steps,seed):
  self.rows=rows;self.steps=steps;self.seed=seed;d=collections.defaultdict(list)
  for i,r in enumerate(rows):d[r['group']].append(i)
  self.groups=list(d.values());assert len(self.groups)>=32
 def __len__(self):return self.steps
 def __getitem__(self,step):
  seed=self.seed+step;random.seed(seed);np.random.seed(seed);torch.manual_seed(seed)
  indices=[random.choice(g) for g in random.sample(self.groups,32)]
  base=T.Compose([T.RandomPerspective(.13,p=.6,fill=127),T.RandomAffine(4,translate=(.03,.03),scale=(.92,1.04),fill=127),T.ColorJitter(.30,.30,.16,.015),T.RandomApply([T.GaussianBlur(7,(.1,2.0))],p=.4)])
  def aug(im):
   im=base(im)
   if random.random()<.3:
    w=random.randint(120,250);im=im.resize((w,round(w*1.4)),Image.Resampling.BILINEAR).resize((320,448),Image.Resampling.BILINEAR)
   if random.random()<.25:
    mask=Image.new('L',im.size);d=ImageDraw.Draw(mask);x=random.randint(-80,280);y=random.randint(-80,400)
    d.ellipse((x,y,x+random.randint(70,200),y+random.randint(70,240)),fill=random.randint(25,95));mask=mask.filter(ImageFilter.GaussianBlur(24));im=Image.composite(Image.new('RGB',im.size,'white'),im,mask)
   return tensor(im)
  first=[];second=[]
  for i in indices:
   row=self.rows[i];p=(R/row['path']).resolve();assert p.is_relative_to((R/'images').resolve());assert sha(p)==row['sha256']
   with Image.open(p) as source:im=source.convert('RGB').resize((320,448),Image.Resampling.BILINEAR)
   first.append(aug(im));second.append(aug(im))
  return torch.stack(first+second)

def validate(splitdir,auditdir):
 status=json.loads((splitdir/'status.json').read_text());audit=json.loads((auditdir/'status.json').read_text())
 assert status['complete_download'] and status['mode']=='final_pending_integrity_audit','Final complete split required'
 assert audit['eligible_for_training'] and audit['phase']=='complete' and not audit['errors'],'Full successful audit required'
 assert audit['audited']==status['total']==status['downloaded'],'Audit completeness mismatch'
 verified={r['id']:r['sha256'] for line in (auditdir/'verified.jsonl').read_text().splitlines() if (r:=json.loads(line))['ok']}
 s=json.loads((splitdir/'split.json').read_text(encoding='utf8'))
 for f in ('group','oracle_id','illustration_id','sha256'):
  assert not {r[f] for r in s['train'] if r[f]}&{r[f] for r in s['validation'] if r[f]},f
 for row in s['train']+s['validation']:assert verified.get(row['id'])==row['sha256'],'Unaudited row'
 assert len(verified)==audit['audited'],'Duplicate or incomplete audit records'
 old=json.loads((R/'pilot-01/split.json').read_text(encoding='utf8'))
 for f in ('oracle_id','illustration_id','sha256'):
  assert not {r[f] for r in old['validation'] if r.get(f)}&{r[f] for r in s['train'] if r.get(f)},'Prior validation leaked'
  assert not {r[f] for r in old['train'] if r.get(f)}&{r[f] for r in s['validation'] if r.get(f)},'Prior train leaked to validation'
 dev=json.loads((R/'expanded-01/manifest.json').read_text(encoding='utf8'))
 labels=json.loads((R/'heldout-eval-01/labels-frozen.json').read_text(encoding='utf8'))
 reserved_arts={q['truth']['illustration_id'] for q in dev['queries']}|{v['art'] for v in labels.values()}
 assert not reserved_arts&{r['illustration_id'] for r in s['train']},'Real evaluation art leaked'

 excluded=set(json.loads((R/'pilot-01/audit.json').read_text())['excluded_real_query_names'])|{'archmage emeritus','pick your poison','sleight of hand','giant growth','bartolomÃ© del presidio','deduce'}
 assert not excluded&{r['name'].casefold() for r in s['train']}
 return s

def main():
 p=argparse.ArgumentParser();p.add_argument('--audit',required=True);p.add_argument('--output',default='expanded-run-01');p.add_argument('--steps',type=int,default=4000);a=p.parse_args();assert 1<=a.steps<=4000
 s=validate(R/'expanded-training-final',R/a.audit);out=R/a.output
 assert out.parent==R and not out.exists(),'Use a new run directory'
 assert not (R/'STOP_TRAIN').exists(),'STOP_TRAIN present'
 assert torch.cuda.is_available();torch.set_num_threads(4);torch.manual_seed(20261026);torch.cuda.reset_peak_memory_stats()
 config={'runner_sha256':sha(Path(__file__)), 'encoder_source_sha256':sha(R/'pilot.py'), 'steps':a.steps,'batch_distinct_groups':32,'views':2,'seed':20261026,'lr':.0001,'max_seconds':7200,'source_weights_sha256':sha(R/'pilot-02/trained.pt'),'split_sha256':sha(R/'expanded-training-final/split.json'),'audit_status_sha256':sha(R/a.audit/'status.json'),'scope':'Expanded highres Scryfall only; warmstart own02; uniform group sampler, no real photographs, predetermined final checkpoint. No claim of full epoch coverage.'}
 out.mkdir();write(out,'config.json',config);shutil.copy2(R/'expanded-training-final/split.json',out/'split.json')
 model=Encoder(False).cuda();model.load_state_dict(torch.load(R/'pilot-02/trained.pt',map_location='cuda',weights_only=True));model.train()
 opt=torch.optim.AdamW(model.parameters(),lr=config['lr'],weight_decay=1e-4);scaler=torch.amp.GradScaler('cuda');sched=torch.optim.lr_scheduler.CosineAnnealingLR(opt,a.steps,eta_min=1e-5)
 loader=torch.utils.data.DataLoader(Batches(s['train'],a.steps,config['seed']),batch_size=None,num_workers=2,prefetch_factor=2,pin_memory=True)
 start=time.perf_counter();records=[]
 for step,x in enumerate(loader,1):
  if (R/'STOP_TRAIN').exists() or time.perf_counter()-start>config['max_seconds']:
   torch.save(model.state_dict(),out/'stopped.pt');write(out,'status.json',{'phase':'stopped','completed_steps':len(records)});return
  tick=time.perf_counter();x=x.cuda(non_blocking=True);opt.zero_grad(set_to_none=True)
  with torch.autocast('cuda',dtype=torch.float16):z=model(x)
  logits=z[:32].float()@z[32:].float().T/.1;target=torch.arange(32,device='cuda');loss=(F.cross_entropy(logits,target)+F.cross_entropy(logits.T,target))/2;assert torch.isfinite(loss)
  scaler.scale(loss).backward();scaler.unscale_(opt);torch.nn.utils.clip_grad_norm_(model.parameters(),1);scaler.step(opt);scaler.update();sched.step();torch.cuda.synchronize()
  records.append({'step':step,'loss':float(loss.detach()),'compute_seconds':time.perf_counter()-tick})
  if step%20==0:
   state={'phase':'training','step':step,'total':a.steps,'elapsed_seconds':time.perf_counter()-start,'last':records[-1]};write(out,'status.json',state);print(json.dumps(state),flush=True)
  if step%500==0:torch.save(model.state_dict(),out/f'step-{step:04}.pt')
 torch.save(model.state_dict(),out/'trained.pt');write(out,'training.json',{'records':records,'wall_seconds':time.perf_counter()-start,'device':torch.cuda.get_device_name(),'peak_allocated_mib':torch.cuda.max_memory_allocated()/1024**2});write(out,'status.json',{'phase':'training_complete_pending_evaluation','step':len(records)})
if __name__=='__main__':main()
