"""Summarize completed ablations without selecting a model from two real examples."""
import argparse, html, json
from pathlib import Path

def main():
    p=argparse.ArgumentParser();p.add_argument('--unity',required=True);p.add_argument('--out',required=True);a=p.parse_args()
    root=Path(a.unity);out=Path(a.out);out.mkdir(parents=True,exist_ok=False)
    rows=[]
    for run in sorted((root/'runs').glob('backbone-*')):
        if not (run/'result.json').exists():continue
        result=json.loads((run/'result.json').read_text())
        report=root/'reports'/(run.name.replace('-01','-real-01'))/'result.json'
        real=json.loads(report.read_text())['records'] if report.exists() else []
        for arm,m in result.items():
            rows.append(dict(run=run.name,arm=arm,steps=m['steps'],syntheticFrames=m['frames'],
                syntheticIoU=m['meanIoU'],syntheticP10=m['p10IoU'],syntheticFPR=m['falsePositiveRateAt05'],
                syntheticInvalid=m['invalidQuads'],hiddenError=m['hiddenError'],
                realIoUs={r['frame']:r['models'][arm]['iou'] for r in real},promoted=False))
    result=dict(note='Different training/validation corpora across runs: raw synthetic means are NOT a shared leaderboard. Compare cross-corpus reports. Physical labels approximate, two quads/two identities repeatedly consulted development-only. No physical general accuracy or occluded-corner benchmark. Odin/apps unchanged.',rows=rows)
    (out/'results.json').write_text(json.dumps(result,indent=2))
    body=''.join('<tr>'+''.join('<td>'+html.escape(str(r[k]))+'</td>' for k in ('run','arm','syntheticIoU','syntheticP10','syntheticFPR','realIoUs'))+'</tr>' for r in rows)
    (out/'report.html').write_text('<meta charset="utf-8"><style>body{font:16px system-ui;margin:30px}td,th{padding:8px;border:1px solid #bbb}table{border-collapse:collapse}</style><h1>Esquinitas: ablaciones de la tarde</h1><p>'+html.escape(result['note'])+'</p><p>Ninguna promoción automática. Mejorar identificación no certifica las esquinas.</p><table><tr><th>Experimento</th><th>Inicialización</th><th>IoU sintético</th><th>p10</th><th>FPR sintético</th><th>IoU físico exploratorio</th></tr>'+body+'</table>',encoding='utf8')

if __name__=='__main__':main()
