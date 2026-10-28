import json,html,hashlib
from pathlib import Path
R=Path(__file__).resolve().parent;O=R/'expanded-01'
r=json.loads((O/'results.json').read_text());p=json.loads((O/'export-parity.json').read_text());rows='';details=''
for name,v in r['models'].items():
    rows+=f"<tr><th>{name}</th><td>{v['correct']}/{v['n']}</td><td>{v['full_art_correct']}/{v['full_art_n']}</td><td>{v['encoder_p50_ms']:.2f}/{v['encoder_p95_ms']:.2f}</td><td>{v['search_p50_ms']:.2f}</td></tr>"
    failures=[x for x in v['rows'] if not x['art_correct']]
    details+=f'<h3>{name}</h3><ul>'+''.join(f"<li>{html.escape(x['truth_name'])} → {html.escape(x['predicted'])}; coseno {x['score']:.3f}, margen entre artes {x['margin_other_art']:.3f}</li>" for x in failures)+'</ul>'
    positive=[x['score'] for x in v['rows'] if x['art_correct']]
    proxy=[x['absent_identity_proxy_score'] for x in v['rows']]
    details+=f'<p>Scores de aciertos: {min(positive):.3f}–{max(positive):.3f}; score de candidato erróneo al retirar identidad: {min(proxy):.3f}–{max(proxy):.3f}. Esto NO calibra falsos positivos reales ni determina un umbral.</p>'
s=f'''<!doctype html><meta charset="utf-8"><title>Comparación ampliada 01</title><style>body{{font:16px system-ui;max-width:1100px;margin:40px auto;padding:20px;background:#f4f6fa}}table{{border-collapse:collapse;background:white;width:100%}}td,th{{padding:12px;border:1px solid #ccd}}aside{{padding:20px;background:#fff0cc}}</style>
<h1>Identificación: galería ampliada, mismo runtime ONNX</h1>
<aside>Desarrollo, no test final ciego. No se ha instalado nada ni cambiado la app, la colección o las fotos. El modelo propio sigue siendo el mismo piloto de 200 pasos: no se ha ajustado a esta prueba.</aside>
<p>Galería congelada de <b>{r['gallery']:,} referencias</b>, incluidas las {r['downloaded_snapshot']:,} imágenes descargadas en el momento del snapshot y referencias inglesas anteriores. No es todavía el catálogo completo. Mismas imágenes para los tres modelos.</p>
<table><tr><th>Modelo</th><th>Arte correcto</th><th>Full art</th><th>Preparación+encoder P50/P95 ms</th><th>Búsqueda P50 ms</th></tr>{rows}</table>
<p>53 capturas de desarrollo. Full art: solo 4 fotogramas de 2 identidades (Emrakul y Archmage Emeritus); los tres Archmage son la misma carta. No son cuatro pruebas independientes. Acierto por ilustración, no impresión ni aceptación de escaneo.</p>
<p>Todos con ONNX Runtime CPU, 4 hilos, una vista vertical. Tres repeticiones por consulta, calentamiento excluido. Tiempos PC, NO Sony. Se excluyen carga de archivos, detector, inicialización y construcción de galería.</p>
<h2>Exportación del modelo propio</h2><p>FP32 {p['bytes']/1e6:.2f} MB; diferencia máxima PyTorch→ONNX {p['max_abs_error']:.2g} en {p['samples']} tensores. Android y cuantización pendientes.</p>
<h2>Errores y riesgo de aceptación</h2>{details}
<h2>Siguiente paso</h2><p>Completar catálogo, adquirir full art nuevas de más identidades para evaluación bloqueada; añadir fondos, reversos y cartas fuera del catálogo como negativos reales. No bajar umbrales ni promover este piloto por porcentajes de una muestra reducida.</p>
<p>Datos reproducibles: manifest.json, results.json, export-parity.json, modelos de galería .npy y logs. Piloto anterior conservado sin cambios.</p>'''
(O/'report.html').write_text(s,encoding='utf8')
files=['expanded.py','report_expanded.py','export_pilot.py','expanded-01/manifest.json','expanded-01/results.json','expanded-01/own-fp32.onnx']
(O/'provenance.json').write_text(json.dumps({x:hashlib.sha256((R/x).read_bytes()).hexdigest() for x in files},indent=2))
print(json.dumps({k:{x:v[x] for x in ['correct','n','full_art_correct','full_art_n','encoder_p50_ms','encoder_p95_ms','search_p50_ms']} for k,v in r['models'].items()},indent=2))
