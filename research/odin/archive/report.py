import html,json
from pathlib import Path
R=Path(__file__).resolve().parent; O=R/'pilot-01'
a=json.loads((O/'audit.json').read_text());t=json.loads((O/'training.json').read_text());e=json.loads((O/'evaluation.json').read_text())
def metric(m):return f"{m['correct']}/{m['n']} ({100*m['recall1']:.1f}%)" if m['n'] else 'Sin muestras'
rows=''
for name,v in e['results'].items():
    rows+=f"<tr><th>{name}</th><td>{metric(v['synthetic'])}</td><td>{metric(v['synthetic_full_art'])}</td><td>{metric(v['real'])}</td><td>{metric(v['real_full_art'])}</td><td>{v['encode_cpu4_p50_ms']:.2f} / {v['encode_cpu4_p95_ms']:.2f}</td></tr>"
text=f'''<!doctype html><meta charset="utf-8"><title>Piloto propio 01 — identificación MTG</title>
<style>body{{font:16px system-ui;max-width:1150px;margin:40px auto;padding:20px;background:#f4f6fa;color:#182335}}table{{border-collapse:collapse;width:100%;background:white}}td,th{{padding:10px;border:1px solid #ccd5e2;text-align:left}}aside{{background:#fff0cc;padding:20px}}code{{overflow-wrap:anywhere}}h2{{margin-top:32px}}</style>
<h1>Piloto propio 01: datos, entrenamiento y comparación</h1>
<aside><b>Prueba de ingeniería, no modelo listo para sustituir Milo.</b> No cambios en app, escáner, colección ni fotografías. Evaluación de desarrollo con galerías reducidas; NO precisión en catálogo completo, NO tiempos Sony, NO test final ciego.</aside>
<h2>Datos</h2><p>{a['audited']} imágenes comprobadas mediante SHA256, decodificación y dimensiones; {len(a['bad'])} errores. {a['lowres']} lowres excluidas del entrenamiento; {a['duplicate_exact_files']} duplicados exactos detectados.</p>
<p>Entrenamiento: {a['train']} ilustraciones, {a['train_full_art']} con full_art=true. Validación: {a['validation']}, {a['validation_full_art']} con full_art=true. Split agrupado por oracle/ilustración/hash; sin solapamiento, identidades de capturas reales excluidas.</p>
<h2>Entrenamiento medido</h2><p>MobileNetV3 Small preentrenado en ImageNet, cabeza nueva 256D L2; contraste entre dos vistas, AdamW, AMP. No se copian pesos Milo ni Diego. Piloto {t['steps']} pasos, 32 ilustraciones ×2 vistas/paso; no equivalentes a épocas completas deterministas.</p>
<p>GPU: {html.escape(t['device'])}; {t['wall_seconds']:.1f} s de bucle de entrenamiento, P50 {t['step_p50_seconds']:.3f} s/paso, P95 {t['step_p95_seconds']:.3f}; memoria máxima asignada PyTorch {t['peak_allocated_mib']:.0f} MiB. Incluye augmentaciones CPU y sincronización; excluye instalación, precarga y descarga de pesos.</p>
<h2>Comparación emparejada</h2><table><tr><th>Modelo</th><th>Sintético</th><th>Sintético full art</th><th>Capturas reales: arte</th><th>Reales full art*</th><th>CPU4 ms P50/P95</th></tr>{rows}</table>
<p>Galería sintética: {e['synthetic_gallery']} ilustraciones no entrenadas. Misma degradación fija para todos. Galería real: {e['real_gallery']} referencias inglesas; {e['real_queries']} consultas, {e['excluded_real_queries_no_english_art']} descartadas por no disponer del arte en inglés. Top1 sin umbral/rechazo: no representa tasa de escaneo aceptado.</p>
<p>* Etiqueta Scryfall full_art incompleta: Archmage Emeritus SPG150 consta false pese al tratamiento visual borderless. Caso anotado visualmente. Una muestra pequeña no representa el rendimiento global full art.</p>
<p>initial = backbone ImageNet + proyección aleatoria antes del entrenamiento. trained = después de 200 pasos. Milo/Diego ejecutan ONNX Runtime CPU4; propio usa PyTorch CPU4. Tiempos incluyen preparación+encoder y excluyen búsqueda, cámara, disco e inicialización. No comparar estos tiempos como si fueran mediciones Android equivalentes.</p>
<h2>Qué falta</h2><ul><li>Completar y balancear ediciones; más casos de reflejos/movimiento y full art reales.</li><li>Revisar fallos individuales, entrenamiento más largo y evaluación sobre catálogo completo.</li><li>Negativos/desconocidas, calibración de umbral/margen, tasa de falso positivo y candidato estable.</li><li>Exportación ONNX y paridad, CPU y Sony con el mismo runtime antes de decidir sustitución.</li><li>Test real nuevo bloqueado y separado de todas las capturas ya inspeccionadas.</li></ul>
<p>Detalle reproducible: audit.json, split.json, training.json, evaluation.json, initial.pt, trained.pt. Descargas en ../status.json y ../download-02.log.</p>'''
(O/'report.html').write_text(text,encoding='utf8')
