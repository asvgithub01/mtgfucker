"""Build a Unity config. Input is a reviewed, group-split texture manifest, not photos."""
import argparse, hashlib, json
from pathlib import Path

def prepare(rows, output, count=120):
    seen = {}; textures=[]
    for row in rows:
        path=Path(row['path']).resolve(strict=True)
        group=row['group']; split=row['split']
        if not group or split not in ('train','validation'): raise ValueError('Explicit group and split required')
        sha=hashlib.sha256(path.read_bytes()).hexdigest()
        for key in ('group:'+group,'sha:'+sha):
            if key in seen and seen[key]!=split: raise ValueError('Train/validation leakage: '+key)
            seen[key]=split
        textures.append(dict(path=str(path),sha256=sha,group=group,split=split))
    if {t['split'] for t in textures}!={'train','validation'}: raise ValueError('Both splits required')
    if count < len(textures) or count>10000: raise ValueError('Count must cover textures and be <=10000')
    return dict(seed=20261028,count=count,width=640,height=896,output=str(Path(output).resolve()),textures=textures)

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--textures',required=True);p.add_argument('--config',required=True);p.add_argument('--output',required=True);p.add_argument('--count',type=int,default=120);a=p.parse_args()
    cfg=prepare(json.loads(Path(a.textures).read_text()),a.output,a.count)
    with Path(a.config).open('x',encoding='utf8') as f: json.dump(cfg,f,indent=2)
