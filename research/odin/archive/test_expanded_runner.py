import json,hashlib
from pathlib import Path
import torch
from train_expanded import Batches,validate
R=Path(__file__).resolve().parent
if __name__=='__main__':
 torch.set_num_threads(2)
 split=json.loads((R/'expanded-training-preview/split.json').read_text(encoding='utf8'));ds=Batches(split['train'],2,20261026)
 x=ds[0];y=ds[0];assert x.shape==(64,3,448,320) and torch.isfinite(x).all() and torch.equal(x,y)
 for b in torch.utils.data.DataLoader(ds,batch_size=None,num_workers=2):assert b.shape==x.shape and torch.isfinite(b).all()
 try:validate(R/'expanded-training-preview',R/'download-audit-01')
 except AssertionError as e:assert 'Final complete split' in str(e)
 else:raise AssertionError('Preview incorrectly accepted')
 out={'deterministic_batch':True,'shape':list(x.shape),'two_worker_windows_loader':True,'preview_rejected':True,'source_sha256':hashlib.sha256((R/'train_expanded.py').read_bytes()).hexdigest(),'note':'Data-loader smoke only: no optimization or model changes'}
 (R/'expanded-training-preview/runner-tests.json').write_text(json.dumps(out,indent=2));print(json.dumps(out))
