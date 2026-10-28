"""Prepare hard negatives from training references only, never real queries."""
import json
from pathlib import Path
import numpy as np
R=Path(__file__).resolve().parent;O=R/'expanded-01'
s=json.loads((R/'pilot-01/split.json').read_text(encoding='utf8'))
m=json.loads((O/'manifest.json').read_text(encoding='utf8'))
index={r['key']:i for i,r in enumerate(m['refs'])}
train=[r for r in s['train'] if r['id'] in index]
assert len(train)==len(s['train'])
assert not ({r['group'] for r in train}&{r['group'] for r in s['validation']})
g=np.load(O/'own-gallery.npy',mmap_mode='r');x=np.stack([g[index[r['id']]] for r in train]);sims=x@x.T
pairs=[]
for i,r in enumerate(train):
    selected=[]
    for j in np.argsort(-sims[i]):
        other=train[int(j)]
        if r['group']==other['group']:continue
        selected.append({'id':other['id'],'name':other['name'],'cosine':float(sims[i,j])})
        if len(selected)==3:break
    pairs.append({'id':r['id'],'name':r['name'],'full_art':r['full_art'],'negatives':selected})
(O/'hard-negatives-train-only.json').write_text(json.dumps({'scope':'Candidate batch mining for next experiment only. Training split references only; no real queries/validation images used; no new training started. Cosine does not imply labeling error.','anchors':len(pairs),'pairs':pairs},indent=2),encoding='utf8')
print('Prepared',len(pairs),'anchors and',sum(len(x['negatives']) for x in pairs),'training-only pairs')
