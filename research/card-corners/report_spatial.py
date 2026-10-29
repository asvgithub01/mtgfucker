"""Summarize frozen spatial experiments, preserving their original run directories."""
import argparse,html,json
from pathlib import Path

def main():
    p=argparse.ArgumentParser();p.add_argument('--runs',required=True);p.add_argument('--out',required=True);a=p.parse_args()
    root=Path(a.runs);out=Path(a.out);out.mkdir(parents=True,exist_ok=False)
    entries=[];table=[]
    for name,pool,seed in [('heads-02',4,20261029),('spatial-01',8,20261029),('spatial-seed30-pool4',4,20261030),('spatial-seed30-pool8',8,20261030)]:
        run=root/name;results=json.loads((run/'result.json').read_text());sub=json.loads((run/'subgroups.json').read_text())
        cfg=json.loads((run/'config.json').read_text());assert cfg['seed']==seed and cfg.get('pool',4)==pool
        for head in ('regression','simcc'):
            r=results['generalization-'+head]
            entry={'run':name,'pool':pool,'seed':seed,'head':head,'metrics':r,'subgroups':sub[head]};entries.append(entry)
            table.append(f'<tr><td>{seed}</td><td>{pool}x{pool}</td><td>{head}</td><td>{r["meanIoU"]:.4f}</td><td>{r["p10IoU"]:.4f}</td><td>{sub[head]["fullArt"]["meanIoU"]:.4f}</td><td>{r["hiddenError"]:.4f}</td><td>{r["badIoUBelow05"]}</td></tr>')
    summary={'experiments':entries,'decoder':json.loads((root/'spatial-01/decoder-probe.json').read_text()),'promoted':False}
    (out/'summary.json').write_text(json.dumps(summary,indent=2))
    links=''.join(f'<li><a href="{(root/n/"report.html").as_uri()}">{n}</a></li>' for n in ('heads-02','spatial-01','spatial-seed30-pool4','spatial-seed30-pool8'))
    body='<h1>Esquinitas: conservación espacial</h1><p>Dos semillas,3000pasos,mismos1600renders y16artes de validación (8fullart). No test físico ni resultado de Sony. Pool8 aumenta parámetros, no solo precisión espacial; no atribuir causalidad exclusiva al pooling.</p><table><tr><th>Semilla</th><th>Malla</th><th>Cabeza</th><th>IoU media↑</th><th>p10↑</th><th>Fullart↑</th><th>Error ocultas↓</th><th>IoU&lt;0.5↓</th></tr>'+''.join(table)+'</table><p>SimCC: mejora consistente en las dos semillas, pero los contornos siguen desviándose. No promocionado. Refinamiento parabólico cambia IoU0.796106 a0.796129: efecto despreciable frente al problema actual.</p><h2>Overlays y resultados completos</h2><ul>'+links+'</ul><h2>Siguiente experimento</h2><p>Backbone ligero preentrenado, comparado con la misma arquitectura desde cero; después más variedad de escenas y evaluación física anotada independiente. No entrenar con fotos reservadas ni adoptar umbrales de otros modelos.</p>'
    (out/'report.html').write_text('<meta charset="utf-8"><title>Esquinitas espacial</title><style>body{font:16px system-ui;margin:32px;max-width:1100px}td,th{padding:9px;border-bottom:1px solid #ddd}table{border-collapse:collapse}</style>'+body,encoding='utf8')
    print(out/'report.html')
if __name__=='__main__':main()
