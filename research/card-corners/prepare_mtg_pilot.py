"""Copy a tiny, grouped Scryfall subset from an existing frozen split. No photos."""
import argparse,hashlib,json,shutil
from pathlib import Path
from prepare import prepare

def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def main():
    p=argparse.ArgumentParser();p.add_argument('--identity-root',required=True);p.add_argument('--out',required=True);a=p.parse_args()
    root=Path(a.identity_root).resolve();out=Path(a.out).resolve()
    split_path=root/'expanded-training-final/split.json'
    split=json.loads(split_path.read_text(encoding='utf8'));selected=[]
    for dest,n in [('train',64),('validation',16)]:
        used=set()
        for full in (True,False):
            pool=sorted((r for r in split[dest] if bool(r.get('full_art'))==full),key=lambda r:hashlib.sha256(('esquinitas-v1:'+r['id']).encode()).hexdigest())
            chosen=[]
            for row in pool:
                if row['group'] in used:continue
                used.add(row['group']);chosen.append(dict(row,split=dest))
                if len(chosen)==n//2:break
            assert len(chosen)==n//2;selected+=chosen
    for field in ('group','oracle_id','illustration_id','sha256'):
        train={r[field] for r in selected if r['split']=='train' and r.get(field)}
        val={r[field] for r in selected if r['split']=='validation' and r.get(field)}
        assert not train&val,field
    for row in selected:
        src=(root/row['path']).resolve();assert src.is_relative_to(root/'images')
        assert sha(src)==row['sha256']
    out.mkdir(parents=True,exist_ok=False);(out/'sources').mkdir()
    textures=[]
    for row in selected:
        dst=out/'sources'/(row['id']+'.jpg');shutil.copy2(root/row['path'],dst)
        textures.append(dict(path=str(dst),group=row['group'],split=row['split']))
    (out/'textures.json').write_text(json.dumps(textures,indent=2))
    (out/'provenance.json').write_text(json.dumps(dict(parentSplit=str(split_path),parentSplitSha256=sha(split_path),selection='64train16validation, half full-art each, unique parent groups; no real photos',rows=selected),indent=2))
    cfg=prepare(textures,out/'renders'/'pilot-01',1600)
    (out/'config.json').write_text(json.dumps(cfg,indent=2));print(out/'config.json')

if __name__=='__main__':main()
