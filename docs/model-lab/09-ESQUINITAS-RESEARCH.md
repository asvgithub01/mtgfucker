# Esquinitas: revisión de Cornelius/FastWeb — 29-10-2026

Investigación, NO nueva arquitectura implementada ni entrenamiento lanzado.

## Evidencia comprobada
- [Cornelius](https://huggingface.co/HanClinto/cornelius): MobileViT-XXS + global-token SimCC,
  entrada384²; fichaactual usa argmax y sharpness, presence2.x constante. Declara mejora
  bajo oclusión/rotación. Son resultados del autor, no nuestro benchmark.
- [FastWeb](https://huggingface.co/HanClinto/ccgdetector-fastweb-single): EfficientViT-B0,
  también global-token SimCC,384². Es una alternativa ligera, no regresión directa.
- [Blog20](https://github.com/HanClinto/SimpleGitBlog/issues/20): explicación histórica;
  describe expectativa de distribución y gate0.08–0.10. No copiarlo como receta actual:
  fichaCornelius posterior muestra argmax/gateejemplo0.02; calibrar por modelo/escenario.
- [Issue24 y comentarios](https://github.com/HanClinto/CollectorVision/issues/24):
  distribuciones multimodales provocaban puntos entre dos esquinas al promediar picos.
  Corrigieron decodificación; refinamiento local parabólico es experimento distinto.
  El ONNX publicado no expone logits: no activar ese cambio desde cliente por suposición.
- [Código inference](https://github.com/HanClinto/CollectorVision/blob/main/collector_vision/detectors/neural.py)
  usa sharpness para gating cuando existe. No es una receta de entrenamiento.
- [SimCC original](https://arxiv.org/abs/2107.03332): clasificación separada por eje;
  evidencia en pose humana, no garantía automática de resultados con cartas.

## Límites de lo publicado
Revisados árbolmain de CollectorVision, issues/blog y archivos HF. No localizado el
trainer completo Cornelius/FastWeb, dataset anotado, receta íntegra de aumentaciones,
sigma/loss/optimizer/LR/batch de la corrida original. Los comentarios mencionan código
ccg_card_id, pero no se ha encontrado entre repos públicos inspeccionados.
No afirmar que sabemos reproducir su entrenamiento exacto. Tampoco está demostrado
que entrenaran con Unity. Material público útil ≠ pipeline completo disponible.

Snapshot CollectorVision:8b295ab80a997766afc00c640a616c1f2716df21.
HFcornelius:42e9b8dca7ecb09edd13039332ef82620973d238.
HFfastweb:2ef37ff78ee80ad4c3558e4a15341e447ff8a1be.
Las fichas actuales declaran AGPL-3.0; conservar procedencia/versiones y no asumir
permisos para copiar código/pesos. El plan es implementación propia desde conceptos.

## Diagnóstico de nuestro piloto (hipótesis, no causalidad demostrada)
CornerNetdesde cero, entrada160×224, cuatro convolucionesstride2 y pooling4×4,
regresiónsigmoid8coords+SmoothL1. Solo300pasos/80texturas. No era equivalente a
Cornelius/FastWeb: el experimento validó plumbing, no agotó su capacidad.
El poolingreduce información espacial; su efecto debe probarse. Ni el minuto de
entrenamiento ni nuestra métrica97.5%presencia justifican prometer esquinas correctas.
No comparar errornormalizado0.135 con IoU del autor: son métricas distintas.

## Experimentos propuestos para el siguiente chat
1. Prueba de sobreajuste8–16escenas conocidas: comprobar loss/orden/escala/gradientes.
   Mantener testreservado intacto. Si no ajusta esos ejemplos, depurar antes de ampliar.
2. Comparación controlada cabeza regresión vs SimCC con mismo backbone y presupuesto;
   mantener features espaciales y representación global compartida entre esquinas.
   Documentar como diseño nuestro, no réplica exacta de su cabeza sin trainerpublicado.
3. Ablación resolución256/384 y, separadamente, backbone ligero preentrenado con
   procedencia/licencia adecuada. No cambiar todos los factores y atribuir éxito auno.
4. Coordenadas semánticas GT y ordenconvexo consistentes; no colapsar esquinasni
   promediar dos hipótesis. Medir intercambios/duplicados bajo rotación. Si se añade
   pérdida de permutación, preservar orientación necesaria para Odin upright.
5. Sharpness/entropía o confianza geométrica como señales por calibrar, no probabilidades
   automáticas. Medir cobertura vs error; permitir esquinaoculta plausible sin rechazar
   toda oclusión. Separar ausencia real, oclusión y fuera de encuadre.
6. Unity: aumentar fondos reales/autorizados, texturas de mesa/álbum, manos menos
   esquemáticas, reflexiones/fundas, iluminación y blur. Datasetrealrevisado para cerrar
   brecha sintético-real. Mantener GTUnity aun cuando teacher falle.
7. Comparar teachers mismos renders/fotos, sin usar test para pseudoetiquetartrain.
   Pseudoetiquetas reales requieren revisión; acuerdo de dos modelos no garantiza GT.
8. Evaluación: IoU cuadrilátero medio/p10, errores píxel/normalizadospor esquina visible
   y oculta, colapsos, falsospositivos/negativos, tasa identificación Odin y p50/p95Sony.
   Reportar por carta/escena; no inflar evidencia contando framescorrelacionados.

No modificar Odin ni umbrales actuales. Futuro port a rama de refactor; no mergeciego.
