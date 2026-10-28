"""Automatic export, parity, catalog regeneration and OLD development replay only."""
import json,time,hashlib,html
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import numpy as np,torch,onnx,onnxruntime as ort
from PIL import Image,ImageOps
from pilot import Encoder,tensor
R=Path(__file__).resolve().parent;O=R/'pilot-02'
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def save(name,data):
    p=O/name;t=p.with_suffix('.tmp');t.write_text(json.dumps(data,indent=2),encoding='utf8');t.replace(p)
def read(row):
    p=Path(row['path']);p=p if p.is_absolute() else R/p
    assert sha(p)==row['sha256'],str(p)
    with Image.open(p) as im:return tensor(ImageOps.exif_transpose(im).convert('RGB'))
def prepare():
    # Strict FP32 gallery must agree with CPU ONNX, not TF32 convolution approximations.
    torch.backends.cudnn.allow_tf32=False
    torch.backends.cuda.matmul.allow_tf32=False
    torch.set_float32_matmul_precision('highest')
    torch.set_num_threads(4);save('status.json',{'phase':'exporting'})
    model=Encoder(False).eval();model.load_state_dict(torch.load(O/'trained.pt',map_location='cpu',weights_only=True))
    path=O/'own-fp32.onnx'
    torch.onnx.export(model,torch.zeros(1,3,448,320),str(path),input_names=['image'],output_names=['embedding'],opset_version=17,dynamo=False)
    onnx.checker.check_model(onnx.load(path))
    opts=ort.SessionOptions();opts.intra_op_num_threads=4;opts.inter_op_num_threads=1
    sess=ort.InferenceSession(str(path),opts,providers=['CPUExecutionProvider'])
    split=json.loads((O/'split.json').read_text(encoding='utf8'));errors=[]
    for r in split['validation'][:32]:
        x=read(r)[None]
        with torch.inference_mode():y=model(x).numpy()
        z=sess.run(None,{'image':x.numpy()})[0];errors.append(float(abs(y-z).max()))
        assert np.allclose(np.linalg.norm(z,axis=1),1,atol=1e-5)
    assert max(errors)<1e-4
    save('export-parity.json',{'max_abs_error':max(errors),'n':32,'model_sha256':sha(path),'bytes':path.stat().st_size})
    m=json.loads((O/'catalog-manifest.json').read_text(encoding='utf8'));refs=m['refs'];model.cuda()
    gallery=np.lib.format.open_memmap(O/'gallery.npy',mode='w+',dtype=np.float32,shape=(len(refs),256));start=time.perf_counter()
    with ThreadPoolExecutor(max_workers=4) as pool,torch.inference_mode():
        for i in range(0,len(refs),16):
            if (R/'STOP_TRAIN').exists():save('status.json',{'phase':'catalog_stopped','done':i});return
            x=torch.stack(list(pool.map(read,refs[i:i+16]))).cuda();z=model(x).cpu().numpy()
            assert np.isfinite(z).all();z/=np.linalg.norm(z,axis=1,keepdims=True);gallery[i:i+len(z)]=z
            if i%512==0:
                gallery.flush();state={'phase':'catalog','done':i+len(z),'total':len(refs),'seconds':time.perf_counter()-start};save('status.json',state);print(json.dumps(state),flush=True)
    gallery.flush();gpu_errors=[]
    for i,r in enumerate(refs[:32]):
        x=read(r)[None].numpy();v=sess.run(None,{'image':x})[0].ravel();v/=np.linalg.norm(v)
        gpu_errors.append(float(abs(v-gallery[i]).max()))
    assert max(gpu_errors)<1e-4,'GPU gallery parity failed'
    save('catalog-parity.json',{'max_abs_error':max(gpu_errors),'n':32,'rows':len(refs),'dimensions':256,'scope':'FP32 GPU generated gallery vs CPU ONNX, same image preparation'})
    gallery.astype('<f4').tofile(O/'gallery.f32')
    save('ids.json',[r.get('scryfall_id',r['id'].split('_f')[0]) for r in refs])
    save('labels.json',[f"{r['name']} | {r['set']} | {r.get('collector_number',r.get('printing',''))}" for r in refs])
    # Evaluate only the already-used development set; keep 23 new photographs untouched.
    old=json.loads((R/'expanded-01/manifest.json').read_text(encoding='utf8'));n=len(old['refs'])
    assert [x['key'] for x in refs[:n]]==[x['key'] for x in old['refs']]
    rows=[]
    for q in old['queries']:
        x=read(q)[None].numpy();v=sess.run(None,{'image':x})[0].ravel();v/=np.linalg.norm(v)
        scores=gallery[:n]@v;k=int(scores.argmax())
        rows.append({'query':q['id'],'truth':q['name'],'predicted':refs[k]['name'],'art_correct':refs[k].get('illustration_id')==q['truth']['illustration_id'],'full_art':q['full_art'],'score':float(scores[k])})
    result={'correct':sum(x['art_correct'] for x in rows),'n':len(rows),'full_art_correct':sum(x['art_correct'] for x in rows if x['full_art']),'full_art_n':sum(x['full_art'] for x in rows),'gallery':n,'rows':rows,'scope':'OLD development set only, same expanded01 gallery. Heldout23photos not read; no threshold calibration or Android parity.'}
    save('development-results.json',result)
    save('package.json',{'model':'own-fp32.onnx','model_sha256':sha(path),'gallery':'gallery.f32','gallery_sha256':sha(O/'gallery.f32'),'rows':len(refs),'dimensions':256,'dtype':'little-endian float32','input':'RGB upright, Pillow bilinear resize320x448, ImageNet normalization; L2 embedding, cosine dotproduct','ids':'ids.json','labels':'labels.json','limitations':'English reference catalogue snapshot, not complete Scryfall. No calibrated acceptance thresholds. Lab integration and Android pixel/vector parity pending. Do NOT reuse Diego embeddings or search index.','status':'candidate_for_review_not_installed'})
    train=json.loads((O/'training.json').read_text());fails=''.join(f"<li>{html.escape(x['truth'])} → {html.escape(x['predicted'])}</li>" for x in rows if not x['art_correct'])
    report=f'''<!doctype html><meta charset="utf-8"><title>Piloto 02 — candidato</title><style>body{{font:17px system-ui;max-width:900px;margin:40px auto;padding:20px}}aside{{background:#fff0cc;padding:20px}}</style><h1>Piloto 02: entrenamiento y paquete experimental</h1><aside>No instalado. App y colección intactas. Las 23 fotos nuevas no se han utilizado para entrenar ni seleccionar este checkpoint. Validación nueva, calibración e integración Android pendientes.</aside><p>1000 pasos adicionales con negativos difíciles y degradación sintética; {train['wall_seconds']/60:.1f} minutos. Catálogo regenerado: {len(refs)} referencias.</p><p>Desarrollo anterior, misma galería {n}: propio02 {result['correct']}/{result['n']}; propio01 48/53, Milo50/53, Diego37/53. Full art {result['full_art_correct']}/{result['full_art_n']} (solo2identidades).</p><p>ONNX FP32: {path.stat().st_size/1e6:.2f}MB; error máximo export {max(errors):.2g}, galería GPU/CPU {max(gpu_errors):.2g}.</p><h2>Fallos de desarrollo</h2><ul>{fails}</ul><p>No promover por esta prueba reducida. El paquete requiere su propio índice; no usar el de Diego.</p>'''
    (O/'report.html').write_text(report,encoding='utf8');save('status.json',{'phase':'candidate_ready_for_review','development_correct':result['correct'],'development_n':len(rows),'catalog_rows':len(refs),'heldout_evaluation':'pending','android_installation':'not done'});print('PIPELINE COMPLETE',flush=True)
if __name__=='__main__':
    try:prepare()
    except Exception as exc:
        save('status.json',{'phase':'failed','error':str(exc)})
        raise
