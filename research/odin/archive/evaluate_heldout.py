import os
os.environ['OPENBLAS_NUM_THREADS']='4'
import json,hashlib,time,html
from pathlib import Path
import numpy as np,cv2,onnxruntime as ort
from PIL import Image
R=Path(__file__).resolve().parent;H=R/'heldout-20261026';O=R/'heldout-eval-01'
L=Path('C:/Users/nubor/.codex/worktrees/dinov2-benchmark/mtgfucker')
cv2.setNumThreads(1)
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def main():
    if (O/'results.json').exists():raise RuntimeError('Frozen first evaluation already exists; do not overwrite')
    gallery_meta=json.loads((R/'expanded-01/manifest.json').read_text(encoding='utf8'));refs=gallery_meta['refs'];n=len(refs)
    groups=json.loads((H/'visual-groups.json').read_text(encoding='utf8'))['rows'];groups={r['file']:r['visual_group'] for r in groups}
    # Readable collector footers and visual artwork checked BEFORE any recognition run.
    mapping={'archmage':('spg','150'),'poison':('soa','55'),'sleight':('soa','22'),'growth':('soa','52'),'japanese':('lci','301'),'deduce':('soa','16')}
    truth={}
    for key,(code,num) in mapping.items():
        matches=[r for r in refs if r['set']==code and str(r.get('collector_number',r.get('printing','').split(':')[-1]))==num]
        assert matches and len({r['illustration_id'] for r in matches})==1
        truth[key]={'name':matches[0]['name'],'set':code,'number':num,'art':matches[0]['illustration_id'],'provenance':'Visual title/footer/art review of detector crops before inference; Japanese user identifies Bartolome, footer LCI JP0301 and illustration matches catalog.'}
    split=json.loads((R/'pilot-02/split.json').read_text(encoding='utf8'))
    assert not ({r['illustration_id'] for r in split['train']}&{v['art'] for v in truth.values()})
    (O/'labels-frozen.json').write_text(json.dumps(truth,indent=2,ensure_ascii=False),encoding='utf8')
    detections=json.loads((O/'detection.json').read_text())['rows'];outputs={}
    specs={'milo':(L/'build/diego-android-assets-v2/milo.onnx',R/'expanded-01/milo-gallery.npy'),'diego':(L/'build/diego-android-assets-v2/diego.onnx',R/'expanded-01/diego-gallery.npy'),'own01':(R/'expanded-01/own-fp32.onnx',R/'expanded-01/own-gallery.npy'),'own02':(R/'pilot-02/own-fp32.onnx',R/'pilot-02/gallery.npy')}
    ownmeta=json.loads((R/'pilot-02/catalog-manifest.json').read_text(encoding='utf8'))['refs'];assert [r['key'] for r in ownmeta[:n]]==[r['key'] for r in refs]
    for name,(mp,gp) in specs.items():
        opt=ort.SessionOptions();opt.intra_op_num_threads=4;opt.inter_op_num_threads=1;s=ort.InferenceSession(str(mp),opt,providers=['CPUExecutionProvider']);g=np.load(gp,mmap_mode='r')[:n];rows=[]
        def encode(im):
            if name=='diego':
                a=cv2.resize(np.array(im),(320,448),interpolation=cv2.INTER_LINEAR_EXACT).astype(np.float32)
                a=(a-np.float32([.485,.456,.406])*255)/(np.float32([.229,.224,.225])*255)
            else:
                a=np.array(im.resize((448,448) if name=='milo' else (320,448),Image.Resampling.BILINEAR),np.float32)/255
                a=(a-np.float32([.485,.456,.406]))/np.float32([.229,.224,.225])
            z=s.run(None,{s.get_inputs()[0].name:np.ascontiguousarray(a.transpose(2,0,1)[None])})[0].ravel();return z/np.linalg.norm(z)
        for d in detections:
            expected=truth[groups[d['file']]];row={'index':d['index'],'file':d['file'],'group':groups[d['file']],'truth':expected['name'],'detected':d['valid'],'art_correct':False}
            if d['valid']:
                p=O/f"{d['index']:02}-crop.png"
                with Image.open(p) as im:im=im.convert('RGB')
                encode(im);times=[]
                for _ in range(3):
                    t=time.perf_counter();z=encode(im);enc=(time.perf_counter()-t)*1000;t=time.perf_counter();scores=g@z;search=(time.perf_counter()-t)*1000;times.append([enc,search])
                order=np.argsort(-scores);k=int(order[0]);topart=refs[k].get('illustration_id');second=next(int(j) for j in order if refs[j].get('illustration_id')!=topart)
                row.update(art_correct=topart==expected['art'],predicted=refs[k]['name'],predicted_art=topart,score=float(scores[k]),margin_other_art=float(scores[k]-scores[second]),encode_ms=float(np.median(np.array(times)[:,0])),search_ms=float(np.median(np.array(times)[:,1])),crop_sha256=sha(p))
            rows.append(row)
        outputs[name]={'correct':sum(r['art_correct'] for r in rows),'n':len(rows),'encode_median_ms':float(np.median([r['encode_ms'] for r in rows if r['detected']])),'groups':{key:{'correct':sum(r['art_correct'] for r in rows if r['group']==key),'n':sum(r['group']==key for r in rows)} for key in truth},'weights_sha256':sha(mp),'rows':rows}
        print(name,outputs[name]['correct'],'/',len(rows),flush=True)
    result={'gallery':n,'scope':'First evaluation on 23 new photos, 6 physical identities; frozen 200/1200-step models and frozen FastWeb1.39 corners. Shared English gallery17702. Exact-art top1, no acceptance thresholds. CPU4ORT times not Sony. Not independent classes; some identities seen in prior development, none of these photos or target artwork in own training. Further tuning using these outcomes would turn this set into development.','models':outputs}
    (O/'results.json').write_text(json.dumps(result,indent=2,ensure_ascii=False),encoding='utf8')
    table=''.join(f"<tr><th>{name}</th><td>{v['correct']}/{v['n']}</td><td>{v['encode_median_ms']:.2f} ms</td></tr>" for name,v in outputs.items())
    bygroup=''.join('<tr><th>'+html.escape(truth[key]['name'])+'</th>'+''.join(f"<td>{v['groups'][key]['correct']}/{v['groups'][key]['n']}</td>" for v in outputs.values())+'</tr>' for key in truth)
    details=''
    for d in detections:
        i=d['index'];details+=f'<section><h3>{i}: {html.escape(d["file"])}</h3><img width="220" src="{i:02}-crop.png"><img width="220" src="{i:02}-overlay.jpg"><ul>'
        for name,v in outputs.items():
            x=v['rows'][i];details+=f"<li>{name}: {'OK' if x['art_correct'] else 'ERROR'} — {html.escape(x.get('predicted','Sin detección'))}, coseno {x.get('score',0):.3f}</li>"
        details+='</ul></section>'
    page=f'''<!doctype html><meta charset="utf-8"><title>Fotos nuevas: identificación</title><style>body{{font:16px system-ui;max-width:1100px;margin:40px auto;padding:20px;background:#f4f6fa}}table{{border-collapse:collapse;background:white}}td,th{{padding:12px;border:1px solid #ccd}}aside{{background:#fff0cc;padding:20px}}section{{display:inline-block;vertical-align:top;width:48%;margin:1%}}img{{max-width:46%}}</style><h1>23 fotos nuevas: primera comparación</h1><aside>Sin entrenar con estas fotos ni ajustar umbrales. 23 capturas de 6 cartas, no 23 identidades independientes. Detección FastWeb válida en 23/23; recortes revisados visualmente, no medidos mediante esquinas manuales. Algunas fotos tienen reflejos fuertes o desenfoque.</aside><p>Misma galería de {n} referencias inglesas y mismos recortes para los cuatro modelos. Propio01:200pasos; propio02:1200pasos acumulados. Acierto de ilustración top1, NO escaneo aceptado/impresión/idioma. Tiempos preparación+encoder CPU4ONNX; no cámara/Sony.</p><table><tr><th>Modelo</th><th>Aciertos</th><th>Mediana</th></tr>{table}</table><h2>Por identidad</h2><table><tr><th>Carta</th><th>Milo</th><th>Diego</th><th>Propio01</th><th>Propio02</th></tr>{bygroup}</table><p>Falta catálogo completo, negativos reales para calibrar rechazo y validación Android. No promover solo por esta muestra. Estos resultados no deben usarse para entrenar sobre las fotografías.</p><h2>Evidencias</h2>{details}'''
    (O/'report.html').write_text(page,encoding='utf8')
    assert all(sha(Path(x['source']))==x['sha256'] for x in json.loads((H/'manifest.json').read_text())['images'])
if __name__=='__main__':main()
