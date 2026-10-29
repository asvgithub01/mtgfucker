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

## Ablación ejecutada 2026-10-29: cabezas, sin promoción
Aplicada implementación propia SimCC (256 bins/eje, sigma2, argmax) frente a
regresión directa. NO réplica del global-token head de Han: se mantiene CNN del
piloto, representación espacial4x4 y vector compartido128. No código/pesos teachers.
Entrada160x224, batch16, AdamW3e-4, semilla20261029; misma secuencia de ejemplos
por cabeza. Cambian cabeza y pérdida, y parámetros:276873 vs540033. No atribuir
la diferencia exclusivamente a argmax ni comparar estos tiempos con el Sony.

Primero sobreajuste16escenas train/600pasos: SimCC IoU0.973–0.985,
regresión0.945–0.951. Comprueba que aprende etiquetas; NO generalización.
CUDA no configurado bitexact; repeticiones con misma semilla varían ligeramente.
Después1200 y3000pasos, resultados preservados por separado:

| Pasos | Regresión IoU medio | SimCC IoU medio |
|---|---:|---:|
|1200|0.7143|0.6906|
|3000|0.7487|0.7577|

Validación320renders,282con carta, solo16artes distintos (8fullart).
A3000pasos: p10IoU0.6374 vs0.6703; fullartIoU0.7290 vs0.7516;
macro por arte0.7492 vs0.7581. SimCC errornormalizado0.07410,
visible0.07371/oculta0.07786;2/282IoU<0.5,0quadsno-convexos,
0/38falsospositivos a0.5. Ese umbral es del experimento sintético, no de la app.
Inspección overlay000064 aún muestra bordes desviados pese a mejora.

Conclusión: indicio favorable pequeño tras3000pasos, especialmentefullart;
insuficiente para producción y sin evidencia estadística multisemilla.
No instalar, no tocar Odin ni umbrales. No equivale a calidad de Cornelius/FastWeb.
Siguiente: aumentar conservación de información espacial / backbone ligero
preentrenado como ablación separada y mejorar dominioUnity; mantener un conjunto
nuevo final sin ajustar a él. Los16artes actuales ya son validación de desarrollo,
no test final independiente. Fotos reales reservadas siguen sin entrenar.

Código: research/card-corners/train_heads.py y summarize_heads.py.
Artefactos locales en trainning models/unity/runs/heads-01 y heads-02:
config, hashesdataset, split-indices, checkpoints, métricas, predicciones,
subgroups.json(heads-02) y report.html con overlays. Pilotoanterior intacto.

## Continuación espacial y segunda revisión de fuentes (2026-10-29)
Revisados búsqueda web, índice de issues CollectorVision/SimpleGitBlog, comentarios
completos de issue24 y README histórico c2d9c4d. No aparece receta completa adicional.
El README histórico está parcialmente malformado (salida ImageMagick), no sirve de
trainer. No confundir sus condiciones/arquitectura con los artefactos actuales.
Fuentes útiles verificadas:
- https://huggingface.co/HanClinto/cornelius : continuación con LR bajo y selección
  por equilibrio normal/rotación/oclusión. No publica valor de LR ni receta completa.
- https://github.com/HanClinto/CollectorVision/issues/24#issuecomment-4966032262 :
  refinamiento parabólico post-hoc da ganancias pequeñas. Menciona conjuntos
  val_packopening/val_listos/test_listos/test_clint; no son un dataset descargado aquí.
- https://huggingface.co/HanClinto/ccgdetector-fastweb-single : corroboración contrato,
  arquitectura y límites de métricas. No equivalente a nuestros datos sintéticos.

Experimento propio: conservar malla8x8 en vez4x4 antes del vector128, sin cambiar
convoluciones,entrada,datos,loss,LR o3000pasos. SimCC crece540033->1129857params;
no aisla capacidad del efecto espacial. Dos semillas, validación de desarrollo:

| Semilla | Pool4 IoU | Pool8 IoU | Pool4 fullart | Pool8 fullart |
|---|---:|---:|---:|---:|
|20261029|0.75768|0.79611|0.75161|0.78758|
|20261030|0.76232|0.79907|0.75457|0.79928|

Pool8: p10IoU0.70744/0.70366, errorocultas0.06743/0.06220,
0casosIoU<0.5/282 en ambas semillas,0/38FP a0.5. No cambiar umbral de app.
La ganancia se repite, pero16artes (8fullart) son evidencia limitada, no testfinal.
Overlay000064 aún muestra desviaciones. No hay tiemposSony ni comparación teachers.
Refinamiento parabólico mismo checkpointspatial01:0.796106->0.796129IoU,
erroroculto empeora ligeramente. No merece priorizarlo ante errores geométricos grandes.
Pruebas nuevas: pico cuadrático conocido,bimodal,plano,extremo; formaspool4/8.
Corregido conteo real de pasos si timeout y añadidos hashes de imports al config.

Artefactos conservados en unity/runs/spatial-01,spatial-seed30-pool4 y
spatial-seed30-pool8. Resumen reproducible: report_spatial.py, salida local
unity/reports/spatial-01/report.html. Pesos,config,split,hashes ylogs en cada run.
No promoción. Siguiente: comparar backbone preentrenado contra el mismo desde cero,
antes de aumentar escenas/realismo; reservar evaluación física nueva e independiente.
