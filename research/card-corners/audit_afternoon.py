"""Audit completed run artifacts read-only; not a model quality approval."""
import argparse, hashlib, json, math
from pathlib import Path

def main():
    p=argparse.ArgumentParser();p.add_argument('--unity',required=True);p.add_argument('--out',required=True);a=p.parse_args()
    root=Path(a.unity).resolve(); destination=Path(a.out); assert not destination.exists()
    audited=[]; image_cache={}
    for run in sorted((root/'runs').glob('backbone-*')):
        if not (run/'result.json').exists():continue
        cfg=json.loads((run/'config.json').read_text()); split=json.loads((run/'split.json').read_text())
        result=json.loads((run/'result.json').read_text()); rows=split['rows']; groups={}; hashes={}
        assert set(split['train']).isdisjoint(split['validation'])
        assert sorted(split['train']+split['validation'])==list(range(len(rows)))
        for i,r in enumerate(rows):
            expected='train' if i in set(split['train']) else 'validation';assert expected==r['split']
            for mapping,key in [(groups,r['group']),(hashes,r['textureSha256'])]:
                assert key not in mapping or mapping[key]==expected;mapping[key]=expected
            image=Path(r['image'])
            if not image.is_absolute():
                # Initial single-corpus configs predate the explicit datasets field.
                corpus='square-diverse-01' if run.name=='backbone-square-01' else 'pilot-01'
                image=root/'data/mtg-pilot-01/renders'/corpus/image
            image=image.resolve();assert image.is_relative_to(root/'data/mtg-pilot-01/renders')
            if str(image) not in image_cache:image_cache[str(image)]=hashlib.sha256(image.read_bytes()).hexdigest()
            assert image_cache[str(image)]==r['imageSha256']
        arms={}
        for name,metrics in result.items():
            folder=run/name;logs=[json.loads(s) for s in (folder/'metrics.jsonl').read_text().splitlines()]
            assert len(logs)==metrics['steps']==cfg['steps']
            assert [r['step'] for r in logs]==list(range(1,len(logs)+1))
            assert all(math.isfinite(r['loss']) for r in logs)
            for step in range(500,len(logs)+1,500):assert (folder/f'step-{step}.pt').is_file()
            arms[name]=dict(steps=len(logs),checkpointSha256=hashlib.sha256((folder/'final.pt').read_bytes()).hexdigest())
        audited.append(dict(run=run.name,arms=arms,train=len(split['train']),validation=len(split['validation']),
            pillow=cfg['pillow'],sourceHashes=cfg['sourceHashes'],realTraining=False))
    destination.write_text(json.dumps(dict(status='PASS',runs=audited,uniqueRenderFilesVerified=len(image_cache),
        limitations=['Only saved synthetic run integrity/splits audited; NOT real-world quality approval.',
        'Real annotation queue remains locked; reserved identity overlap audit pending.',
        'Checkpoints contain model state only, not optimizer/RNG state; not exact resumable training.']),indent=2))
    print(json.dumps(dict(status='PASS',runs=len(audited),uniqueImages=len(image_cache))))

if __name__=='__main__':main()
