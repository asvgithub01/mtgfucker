"""Validate render geometry/hashes/split; make overlays for human QA before training."""
import argparse, hashlib, json, math
from pathlib import Path
from PIL import Image, ImageDraw

def validate(root, overlays=None):
    root=Path(root); rows=[]; seen={}
    count=int((root/'COMPLETE').read_text())
    for index in range(count):
        r=json.loads((root/f'{index:06}.json').read_text())
        assert r['schema'] in (1,2) and r['coordinates']=='normalized-top-left'
        assert r['cornerOrder']=='texture-TL,TR,BR,BL'
        assert r['image']==f'{index:06}.png'
        image=root/r['image']; assert hashlib.sha256(image.read_bytes()).hexdigest()==r['imageSha256']
        assert r['split'] in ('train','validation')
        for key in ('group:'+r['group'],'sha:'+r['textureSha256']):
            assert key not in seen or seen[key]==r['split'], 'Split leakage'
            seen[key]=r['split']
        pts=[(p['x'],p['y']) for p in r['corners']]
        assert len(pts)==(4 if r['present'] else 0)
        visible=r.get('cornerVisible',[True]*len(pts))
        if r['schema']==2:
            assert 'cornerVisible' in r and len(visible)==len(pts)
            assert all(type(v) is bool for v in visible)
            assert type(r['thumbOccluder']) is bool
            assert r['thumbOccluder'] or all(visible)
        assert all(math.isfinite(v) and 0<=v<=1 for p in pts for v in p)
        if pts:
            cross=[]
            for i in range(4):
                a,b,c=pts[i],pts[(i+1)%4],pts[(i+2)%4]
                cross.append((b[0]-a[0])*(c[1]-b[1])-(b[1]-a[1])*(c[0]-b[0]))
            assert all(x>1e-6 for x in cross), 'Non-convex or wrong winding'
        with Image.open(image) as im:
            assert im.size==(r['width'],r['height'])
            if overlays and index<24:
                vis=im.convert('RGB');d=ImageDraw.Draw(vis);xy=[(x*im.width,y*im.height) for x,y in pts]
                if xy:
                    d.line(xy+[xy[0]],fill='red',width=3)
                    for k,p in enumerate(xy):d.text(p,str(k)+(' visible' if visible[k] else ' HIDDEN'),fill='yellow' if visible[k] else 'cyan')
                Path(overlays).mkdir(parents=True,exist_ok=True);vis.save(Path(overlays)/r['image'])
        rows.append(r)
    assert {r['split'] for r in rows}=={'train','validation'}
    assert all(any(r['present'] and r['split']==s for r in rows) for s in ('train','validation'))
    return rows

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('dataset');p.add_argument('--overlays');a=p.parse_args()
    print(json.dumps({'validated':len(validate(a.dataset,a.overlays))}))
