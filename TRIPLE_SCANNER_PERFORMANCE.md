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


## Auditoría Sony del 15 de octubre de 2026 y primer modo rápido

Evidencia privada: `~/.codex/backups/mtgfucker/sony-scan-audit-20261016/`.
TAR 527.161.344 bytes; 139 archivos (68 fotos, sus metadatos, trazas y preferencias)
contrastados SHA256 con el Sony. No se ha instalado esta nueva rama ni modificado
el inventario del teléfono durante la auditoría. Horas Europe/Madrid (UTC+2).

### Dos sesiones distintas

- Triple v14, 20:04:29–20:26:43: 80 encuentros, 50 iniciados por OCR y 30 por EdScan;
  ninguno por AR. Las 32 transiciones de rechazo `ar_unverified` no equivalen a
  32 cartas físicas erróneas. Hay 68 pares foto/metadatos; no usar 80 como corpus
  completo de imágenes ni como número de cartas únicas.
- EdScan independiente, alrededor de 23:35–23:40: preferencias fast_score=.50,
  fast_margin=.20, fast_two_readings=false, safety_filters=false, save_photos=false.
  Estos ajustes NO afectan los umbrales fijos del Triple. .20 no es .02: exige
  veinte puntos de separación frente a dos, por lo que endurece la aceptación.

### Casos comprobados y límites

- 23:38:26–23:38:31: ocho lecturas presentes con top1 Cyclopean Mummy en LEG/4ED/REN,
  score .456–.514, casi empatadas; seis no superan ni .50. En la última, .514 frente
  a .512. Usuario confirma que era Cyclopean Mummy JP. Detector sí funciona;
  no había guardado pendiente ni marca sameSavedCard. No basta bajar el score:
  faltaba separación entre candidatos y muchas lecturas seguían por debajo.
- 23:40:07–23:40:08: ocho `present=false`, sin embeddings. Puede ser carta fuera
  de cuadro, contorno inválido o detección fallida; NO prueba un bloqueo ni permite
  nombrar la segunda carta que el usuario recuerda china/coreana.
- 20:12:25–20:13:19: diez rechazos por margen con Cuombajj Witches CHR como top1
  entre .879 y .959. Fotos confirman Cuombajj. La identidad puede ser correcta y
  aun así no separar impresiones. OCR abre evento35; después EdScan evento39.
- 20:19:04–20:20:35: 32 lecturas; 28 top1 Repentant Blacksmith CHR/RIN. Predomina
  score bajo y empate entre impresiones; tras seleccionar CHR se reconoce a20:21:02.
  Foto de ese evento confirma Blacksmith, pero no prueba que todos los frames del
  intervalo fueran la misma carta.
- 20:10:27–20:11:28: repetidos candidatos Frozen Shade y otras identidades con
  score bajo; se termina reconociendo Will-o-the-Wisp. Sin foto de cada rechazo
  no atribuir todos los candidatos a la carta guardada después.
- Will-o-the-Wisp IT (evento28) y EN (29) tienen filas y fotos distintas: el split
  de idioma ya funciona en este caso. ZHS→ZHT eventos31/32 es sospechoso: raw vacío,
  alias zhs inicial y alias zht repetido; fotos casi idénticas. Confirmar con usuario
  si hubo dos ejemplares. No reparar la colección ni afirmar ausencia de errores.
- Hay un hueco de traza de17,484s a20:22:15–20:22:32, seguido de recarga CHR.
  v14 no registra pause/resume; no permite distinguir salida de pantalla de un
  bloqueo real. La nueva rama registra ambos estados. Heartbeat máximo22,008s.
- El standalone sobrescribe su archivo `.frames.json` con las últimas8 muestras.
  No conserva toda la sesión fallida; fotos estaban desactivadas. Los hashCode de
  Triple son reversibles solo contrastándolos con el catálogo y posibles colisiones;
  no son nombres ni prueba de la carta fotografiada.

### Mediciones de Triple (no latencia total por carta)

| Etapa | n | p50 ms | p95 ms |
|---|---:|---:|---:|
| Frame visual |438|855|1068|
| Detector |546|173|263|
| Embeddings y preparación (sin búsqueda) |438|519|622|
| Búsqueda catálogo (dos orientaciones) |438|70|71|
| Cola y preproceso visual |438|88|156|
| Lane OCR completa |445|1129|1804|
| Modelos OCR |445|834|1273|
| Resolver nombre OCR |339|405|1062|
| Clasificación de idioma |287|6|124|
| Metadata impresión |37|57|87|
| Guardado |128|342|512|

Todas las37 consultas de metadata registradas fueron locales. Ocho de445 OCR
caducaron; traceDropped=0, térmico=0 en264 muestras. Rechazos:299score,54margin,
108no_detection;47esperando lecturas y38confirmaciones (son frames, no cartas).
No sumar percentiles de etapas solapadas. Preparar cuatro alfabetos y resolver
nombres pesa mucho más que la clasificación final de idioma. No se observa red
como cuello en esas37consultas; esto no caracteriza todas las rutas de la app.

### Funcionamiento técnico actual (verificado contra código)

1. Triple obtiene YUV de ARCore y convierte a RGB; un worker y un OCR en vuelo.
   Su scheduler alterna OCR y EdScan; OCR ocupa su lane hasta liberar callbacks.
2. `NativeCollectorVisionEngine`: ONNX Runtime CPU, 2hilos intraop/1inter en
   prepareForCamera, sin spinning; el EdScan standalone usa4intra como máximo.
3. Cornelius detector: tensor RGB NCHW384×384 normalizado ImageNet; devuelve
   cuatro esquinas y sharpness. Requiere geometría usable y sharpness>=.02.
4. Perspectiva a448×448. Milo genera vector128 para imagen normal y girada180°;
   se normalizan ambos. No es OCR ni un hash del arte: ve la carta rectificada
   completa. Título extranjero, recorte, reflejos y marco pueden alterar similitud.
5. Catálogo110.278 filas:109.711base +454FRA +113suplemento. Embeddingsfloat16;
   producto escalar contra cada fila y top8 por orientación, fusión por UUID y top8
   final. Índice global estático, no búsqueda web ni catálogo actualizado en vivo.
6. Triple exige score>=.70, top1-top2>=.08 y dos frames individualmente fuertes.
   El runner es otra impresión, no necesariamente otra identidad. Se añaden gates
   de frescura2500ms, cambio de escena, scope y arbitraje. Hay descansos150–650ms.
7. Metadata local/caché/red resuelve UUID/nombre; foto y persistencia van aparte.
   Idioma físico requiere evidencia OCR/manual: similitud visual no lo confirma.
   Tampoco demuestra inequívocamente edición cuando existen reimpresiones iguales.

### Modo rápido inicial implementado (rama codex/edscan-fast-mode)

Botón en Triple abre `EdScanFastActivity`; volver conserva el modo completo.
Cámara CameraX latest-only, misma resolución solicitada1280×960 y motor2hilos,
catálogo global sin heredar filtros, ambos embeddings y umbrales estrictos intactos.
Sin AR, OCR, entrenamiento de referencias ni resolución de idioma. Descanso150ms.
Esto cambia captura y scheduling; no atribuir todo incremento al modelo ni prometer
un tiempo Sony antes de medir. El engine de Triple puede seguir cargado en memoria
mientras su Activity está detenida; no está inferiendo, pero comparar memoria con cuidado.

Por petición explícita del usuario, añade nuevas filas en grupo `EdScan Fast ...`
mediante `TripleCollectionSession`: cantidad1, ediciónprovisional=true, idioma vacío,
acabado del checkfoil, foto rectificada del frame aceptado y enlace de revisión.
No modifica filas antiguas. Confirmación por identidad canónica y captura fresca;
misma identidad sostenida no crea copias. Retirada>=1500ms/3muestras permite otra.
Sin OCR no diferencia dos idiomas del mismo arte sin retirada. Metadata asíncrona
no bloquea inferencia; guardado usa el writer existente, máximo8pendientes/fallos.
Error permite retry del mismo evento, nunca nueva copia automática.

Logs `edfast.frame`: UUIDs/score top8, reason, decode/detect/feature/search, frameMs,
candidateSpanMs (desde primer top1, NO desde entrada física), timestamp del sensor,
temperatura; `edfast.accept` separa metadata de inferencia. Captura manual de fallo:
30fotos privadas rotativas en files/edfast-diagnostics, sin alta por ese botón.
Sin envío remoto de fotos. Preferencias de otros scanners intactas.

### Próximos experimentos, por orden

1. Sony A/B mismo lote/luz/batería: completo vsrápido, orden alternado y calentamiento.
   Medir entrada visible→resultado, captura→aceptación y aceptación→guardado, p50/p95,
   aciertos/rechazos/falsasaltas y temperatura; no solo tiempo de un frame.
2. Corpus JP/CH/KO incluyendo Mummy con captura manual: comprobar esquinas, nitidez,
   reflejos, orientación y cobertura del índice antes de tocar mínimos.
3. Separar score/margen de IDENTIDAD frente a margen de IMPRESIÓN usando mapeo local
   completo UUID→identidad. Agrupar solo nombres/oracle exactos; tailtop8 separado,
   sin datos faltantes ni exclusiones ocultas. No aceptar el primer alias de red.
4. Perfilar y reducir tensor/conversiones; probar búsqueda nativa/SIMD después. Buscar
   solo cuesta70ms aquí, frente a519ms de embeddings: no es primera prioridad ANN.
5. Medir orientación única con fallback seguro y detección de inversión de carta;
   no quitar el segundo embedding sin corpus180° y validación de falsospositivos.
6. Cuantización/NNAPI/GPU solo experimental tras comparar scores contra baseline:
   cambiar modelo exige revalidar embeddings/catálogo, no solo bajarlatencia.

No se rebajaron umbrales ni se cambiaron modelos para aparentar una mejora.
