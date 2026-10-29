"""Post-hoc synthetic subgroup metrics; no fitting or threshold selection."""
import argparse, json
from pathlib import Path
import numpy as np


def main():
    p=argparse.ArgumentParser(); p.add_argument('--run',required=True); p.add_argument('--dataset',required=True); p.add_argument('--provenance',required=True); a=p.parse_args()
    root=Path(a.dataset); run=Path(a.run)
    provenance=json.loads(Path(a.provenance).read_text())
    group_art={r['group']: r['full_art'] for r in provenance['rows']}
    results={}
    for head in ('regression','simcc'):
        rows=json.loads((run/f'generalization-{head}'/'predictions.json').read_text())
        for r in rows:
            meta=json.loads((root/Path(r['image']).with_suffix('.json')).read_text())
            r.update(group=meta['group'],fullArt=group_art[meta['group']])
        results[head]={}
        for label, subset in [('all',rows),('fullArt',[r for r in rows if r['fullArt']]),('notFullArt',[r for r in rows if not r['fullArt']])]:
            pos=[r for r in subset if r['present']]
            groups=sorted({r['group'] for r in pos})
            group_means=[float(np.mean([r['iou'] for r in pos if r['group']==g])) for g in groups]
            results[head][label]={'frames':len(subset),'positiveFrames':len(pos),'uniqueArtGroups':len(groups),
                'meanIoU':float(np.mean([r['iou'] for r in pos])), 'macroArtIoU':float(np.mean(group_means)),
                'meanCornerError':float(np.mean([np.mean(r['error']) for r in pos]))}
    dest=run/'subgroups.json'
    with dest.open('x') as f: json.dump(results,f,indent=2)
    print(json.dumps(results,indent=2))

if __name__=='__main__': main()
