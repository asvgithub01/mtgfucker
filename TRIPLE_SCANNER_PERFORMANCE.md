# Triple: análisis de rendimiento tras la corrección de seguridad (15/10/2026)

## Alcance y conclusión

**No se han aplicado optimizaciones ni rebajado umbrales.** Primero se corrigieron
las altas AR no corroboradas en modo combinado y la sustitución de idiomas físicos.
No se ha instalado esta versión en el Sony ni alterado su colección. Las pruebas
instrumentadas se ejecutan en el emulador, en modo diagnóstico sin inventario.

La evidencia apunta a dos costes importantes: **OCR multialfabeto secuencial** y
**extracción visual en dos orientaciones**. El catálogo/precio no fue el cuello
principal de las consultas visuales medidas. También hay trabajo perdido porque
algunos nombres llegan después del límite de frescura; acelerar inferencia sin
resolver esos vencimientos no garantiza mejorar el reconocimiento útil.

## Evidencia y límites

- Sony XQ-DQ54, sesión del 14/10/2026, ventana 19:52:59–20:28:00, Europe/Madrid.
- Logs privados `triple-trace/previous.log` y `current.log`, conservados fuera del
  repositorio en `C:/Users/nubor/.codex/backups/mtgfucker/sony-scan-audit-20261015/`.
  No se incorporan imágenes, bibliotecas ni textos OCR reales al repositorio.
- Hubo siete configuraciones de motores durante la ventana, además del cambio a
  idioma fijo EN a las 20:23:17. **No es un benchmark A/B controlado.**
- La versión actualmente instalada fue actualizada al día siguiente. Las trazas
  describen la ejecución nocturna, no una medición de esta corrección.
- La cola antigua de trazas podía descartar mensajes sin contabilizarlos. Los
  recuentos siguientes son muestras disponibles, no un censo garantizado.
- `ocrComplete` antiguo solo se registra en determinadas rutas válidas; excluye
  fallos/descartes y parte del OCR con idioma fijo. No compararlo directamente con
  el nuevo `ocrLane`, que incluye liberaciones caducadas/fallidas.
- Un intervalo entre cartas incluye manipulación/espera humana. Ni ese intervalo,
  ni `candidateShown ageMs`, prueban el tiempo físico hasta reconocer una carta.
- Los percentiles de distintas etapas NO se suman: poblaciones y solapamientos
  difieren. Ninguna cifra de rechazo equivale a porcentaje de error de identidad.

## Medidas observadas

Milisegundos, salvo memoria. Percentil por muestra, sin ponderar por carta.

| Etapa | n | p50 | p95 | máximo |
| --- | ---: | ---: | ---: | ---: |
| Frame visual informado | 755 | 858 | 1467 | 1788 |
| Detección (incluye intentos sin carta) | 785 | 169 | 296 | 697 |
| Extracción visual/feature | 755 | 507 | 887 | 1891 |
| Búsqueda visual | 755 | 72 | 124 | 205 |
| Preprocesado + cola visual | 755 | 109 | 168 | 356 |
| Cola + decodificación, todas las lanes | 1901 | 98 | 137 | 285 |
| OCR completo, rutas registradas antiguas | 474 | 969 | 2480 | 5653 |
| Resolución local del título, cola incluida | 371 | 32 | 1049 | 1499 |
| Edad de captura al resolver título | 371 | 1441 | 3329 | 7514 |
| Lookup visual de metadata | 49 | 59 | 86 | 91 |
| Impresión rápida para OCR | 39 | 85 | 181 | 307 |
| Guardado de fila | 180 | 92 | 355 | 1145 |
| Incorporación de referencia a AR | 89 | 683 | 1449 | 3104 |
| Heap Java, MiB | 416 | 224 | 312 | 346 |

Un `featureMs` máximo mayor que `frameMs` máximo no es contradictorio: la ruta de
revalidación conserva costes de inferencia anterior pero informa un frame fresco.
No tratar esas muestras como un desglose aditivo de una única captura.

OCR por modelo:

| Modelo | n | p50 | p95 | máximo |
| --- | ---: | ---: | ---: | ---: |
| Latino | 601 | 206 | 823 | 3314 |
| Japonés | 495 | 235 | 569 | 2006 |
| Coreano | 495 | 246 | 540 | 1313 |
| Chino | 495 | 261 | 581 | 1930 |

Otros indicadores:

- **46/371 (12,4 %) resoluciones de título** llegan con captura >2500 ms. Esa
  evidencia ya no puede votar con seguridad. No alargar el TTL para disimularlo.
- Confirmación visual: 436 muestras rechazadas por score, 171 por margen,
  87 esperando lecturas, 61 confirmadas, 30 sin detección y 20 esperando scope.
  Las 61 incluyen lecturas repetidas de la misma carta.
- Las 49 consultas visuales de metadata medidas fueron **locales**. Esto no
  descarta red para imágenes, idioma por impresión, otras consultas o arranques.
- 620 lanes OCR, 827 EdScan y 454 IDLE. La elección se hace después de decodificar
  en la implementación actual: revisar coste de IDLE, sin suprimir la observación
  de retirada/apariencia que protege las copias.
- Las 416 muestras térmicas informan estado 0; no prueban ausencia de contención
  CPU/GPU, frecuencia variable, GC ni presión nativa. Heap no equivale a RSS total.
- De 17 revalidaciones lentas, 7 fueron aceptadas y 10 descartadas.

## Por qué cuesta: recorrido del código

1. `TripleScanActivity.frame`: cola, conversión YUV/RGB y bitmap antes de decidir
   lane. Una sola tarea visual; la política añade descanso de 150–650 ms según el
   trabajo. Ese descanso es deliberado para carga sostenida, no tiempo perdido
   que deba eliminarse sin medir temperatura y fluidez.
2. `TripleFrameScheduler`: alterna OCR/EdScan cuando ambos son elegibles. Dos
   confirmaciones fuertes requieren dos capturas distintas, no dos motores sobre
   una imagen vieja. La espera de la otra lane influye en el tiempo de convergencia.
3. `NativeCollectorVisionEngine`: detecta a 384; rectifica y ejecuta embeddings a
   448 tanto normal como girado 180°, con búsqueda para ambas orientaciones.
   El modo cámara configura 2 hilos intra-op como máximo y 1 inter-op.
4. `TripleSnapshotOcr`: sin idioma fijado ejecuta cuatro modelos secuenciales
   sobre la misma imagen. La selección por script se hace **después** de ejecutarlos.
   No hay selección anticipada de alfabeto en esta ruta de snapshot.
5. `matchLocalOcrText`: ejecutor de nombres separado; alias exacto → índice fuzzy
   → posible lectura de `AtomicCards.json.gz` si no hay coincidencia. No hace red,
   pero la traza exterior mezcla cola, búsqueda y entrega al hilo principal. Los
   datos actuales no permiten atribuir el p95 a una subetapa concreta.
6. Idioma/reglas y lookup de título comparten recursos y callbacks. El timeout no
   puede liberar la lane mientras un modelo nativo sigue trabajando: hacerlo
   permitiría solapamiento y resultados cruzados.
7. Entrenar una referencia AR y reconfigurar su DB puede tardar, pero el tiempo
   `sessionAppend` no significa que todo ese tiempo bloquee cámara. Separar carga,
   entrenamiento, serialización y aplicación GL antes de optimizarlo.
8. Legacy/Room conservan un ejecutor de escritura serial. No moverlos a cámara ni
   quitar comprobaciones de versión para ahorrar los ~92 ms medianos observados.

## Prioridades propuestas (aún NO implementadas)

### P1 — Reducir trabajo OCR sin perder idiomas

Evaluar selección de alfabeto por **captura actual**, con fallback acotado y
obligatorio si la evidencia es insuficiente. Nunca fijar el idioma de la carta
siguiente al de la anterior ni aceptar un nombre latino espurio sobre JA/CH/KO.
La ruta rápida de título latino de `guidedOcr` ya existe; el snapshot completo no
la usa. El beneficio potencial es evitar modelos innecesarios, no una promesa de
ahorro igual a la suma de medianas. Corpus de aceptación: ES/PT con alias común,
EN/IT consecutivos, JA/CH/KO, texto borroso, título sin lectura y ruso no soportado.

### P1 — Resolver títulos antes de que caduquen

Medir cola del ejecutor, exacto/fuzzy/fallback gzip y entrega UI por captura.
Evaluar caché acotada por texto normalizado + scope + revisión del índice (incluido
resultado negativo de vida corta), evitando lecturas completas repetidas del gzip.
No sustituir un fallo por la última carta, no ignorar sets ni devolver un resultado
viejo como fresco. El objetivo es bajar el porcentaje >2500 ms manteniendo TTL.

### P2 — Orientación visual y conversiones

Evaluar geometría canónica que permita omitir el segundo embedding **solo con
orientación inequívoca**, conservando fallback 180°. La mitad del trabajo visual
de embeddings no equivale a la mitad del tiempo completo. Validar cartas giradas,
recorte sesgado, marcos antiguos y carta siguiente en orientación diferente.
Separar asimismo cola/YUV/bitmap/warp/JPEG: reutilizar buffers o elegir lane antes
de convertir puede ayudar; no reutilizar fotografías ni firmas de otra captura.

### P2 — Frecuencia de auditoría después de confirmar

Medir cuánto OCR/EdScan sigue ejecutándose sobre una carta inmóvil. Evaluar una
cadencia reducida solo después de identidad corroborada; debe reactivarse al
cambio físico, pérdida de tracking o contradicción. **No suprimir observación de
idioma**: EN→IT con igual arte fue precisamente un fallo real.
Conservar equidad entre lanes y evitar que AR equivocado deje sin CPU a OCR.

### P3 — Entrenamiento AR, memoria y guardado

Medir tiempos internos y colas de AR antes de decidir agrupar referencias o limitar
entrenamientos redundantes. No eliminar referencias históricas a ciegas ni usar
una referencia provisional como evidencia independiente.
Perfilar GC/RSS y costes JPEG antes de reducir resolución/calidad. La corrección
de idiomas necesita una foto fresca para la copia nueva; ahora la captura OCR
con idioma automático transporta ese JPEG y mide `triple.languagePhoto`.
El agrupado de guardados, si fuese necesario, debe mantener revisión por evento,
fotos inmutables, Room+legacy y durabilidad al pausar.

### No hacer todavía

No bajar score/margen, ampliar TTL, aceptar primera impresión por rapidez, lanzar
los cuatro OCR en paralelo con embeddings, aumentar hilos indiscriminadamente,
activar la política adaptativa en sombra ni introducir GPU/NNAPI sin benchmark
compatible. No atribuir la mejora a una consulta de red no medida.

## Instrumentación añadida (debug, sin modificar decisiones)

- `triple.session v=14`: distingue esta revisión de los logs viejos.
- `triple.ocrLane`: al liberar ownership, captura/generación/evento inicial,
  total, edad, modelos, lookup de nombre, idioma y vencimiento. `-1` indica etapa
  ausente/no completada al liberar, **no duración cero**. Los spans pueden solaparse.
- `triple.languageCopy`: acción KEEP/WAIT/ASSIGN/CONFLICT/SPLIT, origen y edad.
- `triple.health`: añade `ocrBusy` y contador acumulado `traceDropped`; la cola
  sigue acotada, no bloquea cámara para garantizar escritura de diagnósticos.
- `EdScanPerf triple.languagePhoto`: coste de la foto necesaria para separación.
  Es agregado de logcat, no del fichero persistente.
- No añade nombres de cartas, texto OCR, imágenes ni subida de telemetría.

Analizador reproducible (Python estándar):

```powershell
python scripts/analyze_triple_pipeline.py RUTA/previous.log RUTA/current.log --from-ms 1792000379000 --to-ms 1792002480000
python -m unittest discover -s scripts -p 'test_analyze_triple*.py'
```

## Validación antes de cualquier optimización

1. Primero APK de seguridad sobre corpus reproducible, con inventario aislado.
2. Comparar una sola variable A/B en mismo dispositivo/condiciones, alternando
   orden para no confundir calentamiento/caché con mejora; separar arranque frío
   y caliente. No ejecutar pruebas que escriban sobre la colección del usuario.
3. Registrar entrada física/salida (ground truth externo), identidad/impresión,
   copia/idioma correctos, falsos positivos, duplicados, descartes y p50/p95 desde
   captura hasta aceptación. Incluir 7 combinaciones, ausencia/reintroducción,
   cambios rápidos de idioma, ediciones antiguas, arte compartido y fuera de scope.
4. Guardar etapas, expiraciones, FPS/tiempos UI, cola de persistencia, memoria
   nativa/Java y térmica en prueba sostenida. No exigir una mejora porcentual
   inventada antes de tener el baseline físico posterior a la corrección.
5. Rechazar una optimización si mejora latencia a costa de cualquiera de las
   regresiones de seguridad, aunque pase un benchmark de cartas fáciles.
