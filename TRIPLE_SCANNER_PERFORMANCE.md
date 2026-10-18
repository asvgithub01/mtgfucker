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


## Fast v2 — identidad y experimento de regiones (16 octubre 2026)

Cambios exclusivos de EdScanFastActivity: EdScanFastIdentity agrupa por nombre canónico
exacto (el esquema local no expone oracleId). Conserva .70/.08 y dos lecturas; otra
impresión de la misma identidad no reinicia la racha. Score de grupo = máximo, nunca suma.
Desconocidos compiten; ausencia de rival en top8 no se interpreta como margen infinito.
Metadata local se consulta en lote y se cachea (512); misses se reintentan cada2frames.
El lookup visual asíncrono puede calentar su caché de disco, sin bloquear cámara con red.
El callback de aceptación vuelve a contrastar nombre, generación, captura y frescura.

Referencia provisional: entre impresiones observadas de la identidad ganadora dentro
 de .08 del máximo, ordenar no-PLST, fecha más reciente, precio del acabado más barato
 convertido a moneda común, ID. Sin precio va al final dentro de la misma fecha, no es
 gratis. Esta preferencia nunca sustituye a la identidad ganadora ni modifica su score.
No se resuelve idioma físico ni se cambia una fila preexistente.

Resolución: sustituida petición orientativa1280x960 por ResolutionSelector compartido
entre Preview/Analysis, ratio4:3 y filtro lado mayor<=1280; si no existe, menor tamaño
admitido. Dimensión efectiva en edfast.frame. Emulador entrega480x640; no afirmar aún
que el Sony negocie1280x960. Solo el Sony puede validar rendimiento y negociación reales.

Validación: replay real ONNX de las5fotos privadas a2160 y1280, sobre catálogo global:
Brontodon confirma .708/.704 y referenciaXLN; vacía no detecta; FrozenShade, Mummy y
HeartKiran siguen sin confirmar por score. Los scores difieren ligeramente del frame
original por JPEG/reprocesamiento. Repetir una foto prueba la política, no estabilidad
física de dos capturas nuevas. Regresión de Cuombajj usa los scores registrados.

Experimento separado `scripts/evaluate_edfast_regions.py`: corpus44referencias y4fotos,
MiloCPU fijo448. Cada perfil regenera embeddings tanto de referencia como de consulta.

| Foto | Carta completa | Superior58% | Ilustración (.08,.15,.92,.55) |
|---|---|---|---|
| Frozen Shade KO | .544 | .775 | .819 |
| Cyclopean Mummy KO | Clue .455 | Mummy .717 | Mummy .904 |
| Heart of Kiran | .644 | .787 | .765 |
| Belligerent Brontodon | .702 | .818 | .813 |

Resultados exploratorios, NO precisión global:44referencias no equivalen a110278.
Mantener separado de autoalta hasta construir/verificar índice global ROI y evaluar
negativos, idiomas, marcos antiguos, full-art, split/adventure y cartas boca abajo.
La parte superior aún contiene título/idioma; ilustración elimina más texto. Misma
entrada448 significa igual coste teórico del modelo: el recorte por sí solo no acelera
ONNX. Medidas de escritorio no extrapolables al Sony. No cambiado motor compartido,
modelos, índice de producción ni umbrales globales. Fotos/resultados privados fuera Git.


## Auditoría física Fast v2 — Sony 16 octubre, 12:50–13:04

Extracción solo lectura;843frames,61altas/61guardados,56identidades frente a35altas/
32identidades del primer rápido. Revisadas61fotosaceptadas y18diagnósticosnuevos;
158archivosfoto/metadatos coincidenSHA256 conSony. No es benchmarkcontrolado ni
porcentaje de acierto: no hay groundtruth completo de cada intento físico.

Todos los frames son720x960(vertical), no1280x960 ni2160cuadrado. Medianas/p95 ms:
visual789/839,decode27/31,detect188/222,feature503/520,search71/72,
identity37/51. Visual+identity por muestra827/878 (NO suma depercentiles), frente
avisual897/973v1;cadencia999/1063 vs1069/1146. Sinerrors/stale/thermal;gapmáximo1544ms.
Accept→save430/510ms,max714. Racha de identidad de los aceptados1857/9685ms,max16950:
no mide entrada física; ahora incluye éxitos difíciles antes nunca aceptados.

Cuombajj ya confirma (8framesconfirmed,dosaltas con fotoscorrectas);tambiénAbuJa'far,
HellkiteIgniter yUnyaroGriffin. LordofthePit no confirma:16no_runner,top8mismacarta.
Es un límite del topK truncado: ampliar búsqueda hasta un rival distinto; NO inventar
rival con score0 ni bajar margen.20no_candidate por identidad top no disponible,
4margin y392score. Metadatacache aún consume37msmediana; revisar misses recurrentes.

Brontodon no confirma ahora:74top1,71score y3readings aislados,max.70396. Su nueva
foto contiene la carta a~284x421px frente a~942x1361previos. Cambiaron resolución y
encuadre/luz, no atribuir caída exclusivamente a uno. FaerieMiscreant foto.4806 y
HeartKiran.6033 tampoco. Mummy top1en25frames,max.52788;fotosKO ychino tradicional
(獨眼木乃伊), no fotoJP verificada. No confundir fallosprovisionales de candidato con
falsasaltas. No nuevasasociaciones falsas evidentes en fotosdeWitchHunter/Paladin/Ornithopter.

### Piloto híbrido offline, no activo en Android

`evaluate_edfast_regions.py --hybrid --index-dir ...` prepara arte normal y full-card
para full_art=true. Split/transform/adventure/art-series/showcase/borderless ymetadata
incompleta conservan full hasta validar una ROI específica. Evalúa ambas consultas en
0/180 grados porque no conoce diseño antes de identificar. No usa groundtruth para
elegir carril;no compara scores crudos entre carriles. Hipótesis fuertes contradictorias
se marcanconflict;ninguna salida autoriza altas. Misma red448 ydobleconsulta pueden
costar más,no prometeraceleración.

Corpus110referencias(100art/10full),22fotosdiagnósticas(4previas+18nuevas),61fotosaceptadas,
una foto realSwampJPfull-art anterior y uncontrolsyntheticfullart separado. No se ha
generado índiceglobal110278 ni añadido flagAndroid. Datos eíndices fueraGit;binariosf16
conSHA256/modelSHA/provenance. Comando preparado admite manifiesto mayor,pero requiere
cobertura/descarga/validación completas antes de uso real. TestsPython5 cubrenROI,
enrutadofallback,marginporidentidad yconflictosentreperfiles.


Resultados piloto híbrido (110referencias, no catálogo global):en22diagnósticos reales,
full logra17top1correctos/3hipótesisfuertes;upper22/22fuertes;art22top1/21fuertes;
híbrido20hipótesisfuertescorrectas y2débiles. Las dos débiles sonHeartKiranactual
(art.684) yBartoloméshowcase(full.569). No falsas hipótesisfuertes en este corpus,
pero competidores limitados hacen optimista el margen.61altasreales:híbrido58fuertes
correctas/3débiles. SwampJPfullartreal conservafull yacierta.856. Controlautoreferencia
fullart separado, no sumarlo a precisión. MummyKOart.908/.912/.910 yCH.884/.879.
El piloto no habilita guardado ni modifica umbrales o APK instalados. Falta generar
índiceglobal y comprobar negativos/diseños/latenciamóvil antes de activar flag.


## Fast v3: rival global e índice completo de regiones (16-10-2026)

- `IdentitySearch` recorre las 110278 filas por identidad: ocho impresiones repetidas
  ya no ocultan al rival real. Solo `EdScanFastActivity` activa esta ruta; los motores
  de los otros escáneres mantienen su comportamiento. Umbrales .70/.08 y dos lecturas.
- Selector experimental FULL (por defecto en cada apertura), UPPER (0,0,1,.58,
  título/coste/ilustración sin reglas, como la imagen del usuario), ART (.08,.15,.92,.55).
  Las 34796 referencias full-art/especiales/desconocidas conservan carta completa;
  75482 referencias normales tienen ambos recortes reconstruidos simétricamente.
- Se empaquetan ambos índices float16 completos (28231168 bytes cada uno), metadata,
  SHA/modelo/ROI/versión. La alineación es base + FRA + collector; comprobar solo el
  conjunto de IDs no basta. El primer test UI detectó y permitió corregir ese orden.
- El carril full y el recortado compiten internamente por identidad; no comparar sus
  scores crudos entre sí. Si ambos son fuertes y discrepan, no se confirma. Cambiar
  de modo invalida callbacks antiguos y reinicia estabilidad, sin rearmar la copia física.
- `edfast.frame` registra perfil, score/rival y tiempo por carril, resolución, feature,
  búsqueda y metadatos. Cuatro inferencias con recorte frente a dos con FULL: la
  mejora buscada es necesitar menos intentos, no prometer menos coste por frame.

### Replay offline global, no aceptación en vivo

84 fotos reales (22 diagnósticos +61 fotos de altas revisadas +1 full-art japonesa).
Se excluye el control sintético. Cada foto se enfrenta a las 110278 referencias.
Una hipótesis fuerte cumple .70/.08; esto no simula dos capturas temporales distintas.

| Comparación | Fuertes correctas /84 | Débiles /84 | Fuertes incorrectas |
|---|---:|---:|---:|
| FULL anterior top8 | 56 | 28 | 0 |
| FULL con rival global | 61 | 23 | 0 |
| UPPER + full-art completas | 81 | 3 | 0 |
| ART + full-art completas | 79 | 5 | 0 |

- Sobre los 22 diagnósticos: 2, 3, 21 y20 fuertes correctas respectivamente.
- Mummy coreana: FULL .528 (ganaba Urborg Skeleton), UPPER .869, ART .908.
  Mummy china tradicional: .474 / .849 / .884. No etiquetar esas fotos como japonesas.
- Lord of the Pit: FULL .764 y margen global .275; antes faltaba rival entre top8.
- UPPER sigue débil en Bartolomé showcase, Charging Monstrosaur (.699, no se redondea
  para aceptar) y Eidolon of Blossoms. ART además falla el umbral/margen en una foto
  de Heart of Kiran y otra de Burning Sun's Avatar. No bajar los mínimos para esos casos.
- Swamp full-art japonesa mantiene evidencia completa y fuerte. En replay Android,
  la foto ya rectificada necesita un borde neutro añadido para probar Cornelius;
  documentar esa variante como aumentada, no como captura original de cámara.

Son casos seleccionados de estrés, útiles para elegir UPPER como primera prueba,
no una estimación de precisión para todas las cartas ni de tiempos del Sony.
Resultados privados: build/edfast-global/global-results.json. No se ha instalado
esta versión ni se ha escrito ningún dato de colección en el Sony en esta tarea.


Validación final: 730 tests JVM app +57 nativos, 5 Python y5 instrumentados en
emulator-5554 pasan. Replay Android: 5 fotos ×3 perfiles, incluyendo ambas momias,
Lord of the Pit, Brontodon y Swamp full-art con borde aumentado; Swamp conserva
exactamente .79490286 y el mismo rival en FULL/UPPER/ART. También se verifican
cambios del selector con cámara activa, persistencia provisional, idiomas y seguridad AR.
La ruta anterior mantiene su replay de cinco fotos ×dos resoluciones. Captura UI revisada.
APK final SHA256: 05f92702d0d94579d293b0b69fd382095e489ffd291ebed5bc71ab326eac76ff.
Tiempos reales y resolución de esta versión en Sony pendientes de la prueba física.


## Fast v4 automático, FULL primero (16-10-2026)

- AUTO es la opción inicial. Tras dos lecturas débiles consecutivas, o cuatro lecturas
  válidas sin confirmar, pasa de FULL a UPPER. Una primera lectura fuerte permite una
  segunda antes del rescate. Stale/out_of_order no cuentan. Ausencia y lifecycle vuelven
  a FULL; en la misma exposición UPPER se mantiene para evitar oscilaciones.
- Confianza se reinicia al cambiar de carril; .70/.08/dos lecturas permanecen intactos.
  AUTO hace dos inferencias por frame, no los cuatro de los manuales híbridos. El índice
  UPPER se precarga fuera de la cámara y se conserva en memoria al usar FULL. Los
  callbacks de modos manuales anteriores siguen protegidos por generación.
- Índice completo conserva 110278 filas. Única ampliación de crop: Bartolomé showcase
  LCI301 f82bb486-7009-4b53-973f-477fc13ad7e7. `extend_edfast_showcase_index.py` verifica
  SHA del base/modelo y conserva las otras 110277 referencias. FullRows ahora34795.
- Foto nueva JP Bartolomé: UPPER .800669, margen .196696 contra catálogo completo;
  foto anterior .734518, margen .173645. Las cuatro fotos diagnósticas v3 pasan con
  UPPER; 84 fotos reales anteriores dan82 fuertes correctas UPPER,0 incorrectas (antes81).
  Esto mide hipótesis offline, no tiempos físicos ni precisión global en cartas nuevas.
- Ranking Fast conserva resultados/empates, pero mantiene solo los ocho máximos por
  inserción en vez de ordenar decenas de miles de identidades. Test aleatorio con
  empates contrasta20catálogos de2000filas contra el ordenamiento completo.
- Pruebas:734JVM app +58nativos;5Python;6Android incluyendo18replays manuales,
 10legacy ysecuenciasAUTO de6fotos (MummyKO/CH,Bart,Bronto,LordPit,fullartSwamp con
  borde neutro añadido). Confirmación fácil en FULL,FULL; rescate en FULL,FULL,UPPER,UPPER.
  Protecciones de colección, idiomas yAR mantienen sus pruebas. Medición Sony pendiente.
- APK validada:84e5dfbed3bbd7051be95bfecd4f1ad015c6a83f0cd009a2d6758805b06f115b.


## Fast v5 — automático reversible (2026-10-16)

Corrección de la prueba v4: si el detector no observa un hueco entre cartas, AUTO
podía permanecer en UPPER y dejar fuera las referencias full-art/diseños especiales.
Ahora recorre FULL → ART → UPPER → FULL tras dos fallos consecutivos o cuatro
intentos válidos sin confirmar en cada perfil. Confirmar conserva perfil y reinicia
contadores. Ausencia/lifecycle reinician FULL; stale/out_of_order no suman votos.
Cada transición borra la confirmación visual, sin alterar 0.70/0.08 ni dos lecturas.
No se rearma el guardado por cambiar de perfil: otra copia idéntica requiere retirada.

Solo AUTO ejecuta un carril por frame (dos orientaciones). Modos manuales híbridos,
OCR/AR y modo lento conservan sus rutas. ART/UPPER se precargan antes de cámara y
se conservan en caché (~56 MB combinados), invalidada al cambiar índice y al cerrar.
Persistencia/colección sin modificaciones. Traza de sesión v=5 y regiones efectivas.

Validación: 737 JVM app +58 nativo, 5 Python y 7 Android, sin fallos ni skips.
La prueba real secuencial conserva scheduler y confianza entre fotos, sin simular
no_detection: Mummy FULL,FULL,ART,ART; Mountain ART,ART,UPPER,UPPER,FULL,FULL;
Charging Monstrosaur FULL,FULL,ART,ART,UPPER,UPPER. Los seis fixtures anteriores
(incluido Bartolomé JP) y regresiones de persistencia/otros scanners pasan.
Se corrigió un error inicial de ruta del fixture Mummy (apuntaba a Brontodon), no
un umbral de reconocimiento. Las fotos privadas siguen solo en cache del emulador.

Evidencias locales ignoradas: build/edfast-auto/build-v5-final.log,
emulator-v5-verified.log, cycle-results-v5.json y screen-v5.png.
APK SHA256: `5a30669edac77a40a85671c3982834e48ead5620007f4df699e31afa856c265c`.
Pendiente medir tiempos y cobertura en la nueva repetición física Sony; el replay
no equivale a latencia desde que entra una carta en cámara.


## Fast v6 + Triple — híbrido permanente (2026-10-16)

Tras auditoría v5 (36 altas/fotos correctas; 21 especiales SOA solo en referencia FULL),
se elimina el ciclo que dejaba de consultar FULL. Fast AUTO y el carril EdScan del Triple
usan ART+FULL; tras dos fallos/cuatro intentos sin confirmar alternan a UPPER+FULL.
Los perfiles nunca comparten votos. FULL continúa en todas las lecturas; los conflictos
entre identidades fuertes de ambos carriles producen hits vacíos. No se reducen umbrales.
Un FULL débil con margen >=.08 se prefiere como candidato de pantalla si el recorte
también es débil; esto no autoriza alta ni convierte un score <.70 en confirmación.

Triple opta explícitamente por el índice completo y precarga ambas matrices fuera de
cámara. Conserva scopes por sets, margen entre UUID/impresiones (no margen solo por nombre),
arbitraje OCR/AR, idiomas, frescura/revalidación de frames lentos y persistencia existentes.
Por tanto, identidad de arte ambigua NO certifica edición: OCR/AR siguen participando.
Fast continúa separado como respaldo con modos manuales y persistencia provisional.
Trazas nuevas triple.hybrid / triple.hybridSwitch permiten auditar perfiles y conflictos.

Pruebas: 737 JVM app +59 JVM nativo,5Python. Replay híbrido de seis fotos (incluido
Bartolomé JP) y secuencia Mummy→Mountain→Monstrosaur conservan FULL y rescatan UPPER.
TripleHybridIntegrationTest prepara la Activity real con guardado diagnóstico deshabilitado,
y usa su motor con fotos privadas, comprueba ambos carriles y que scopes restringidos/vacíos
no filtren candidatos globales. El emulador no tiene servicio ARCore: no se ha validado
la cámara Triple en vivo; requiere repetición física Sony. El intento inicial de exigir
frames de cámara falló por esa carencia, no se oculta como una prueba de cámara superada.
Evidencias: build/edfast-auto/build-v6.log y emulator-v6-final.log (8/8 Android).
APK: b49a9fe7a14c0e01b2350e0a49c23e3b56dd3c47fb0a337efcb0c3e3412ec669.

## Triple v7 — identidad independiente de edición (2026-10-16)

La integración v6 anterior compartía el índice, pero no la confirmación de Fast.
Sony v6 (sesiones 21:34 y 21:37, solo arte, sin sets) confirma el bloqueo:
Manamorphose score .9201401 vs otra impresión .906795, aunque el rival de distinta
identidad era .7021289. La siguiente lectura .90047437/.8741614 tenía rival .6610686.
Triple rechazaba ambas por margen y alternaba recortes, sin poder confirmar la carta.
Las tres últimas fotos guardadas corresponden visualmente a Vault Skirge.

TripleIdentityConfirmation reutiliza EdScanFastIdentity: dos lecturas independientes
con score >=.70 y margen >=.08 frente a OTRA identidad. Un segundo tracker exige dos
votos FULL estrictos por UUID para certificar impresión. Los recortes confirman identidad
pero dejan edición pendiente. No se deduce idioma de esa impresión provisional.
La evidencia viaja explícita por captura a offerVisual; no queda pegada a Target.
Se mantienen arbitraje OCR/AR independiente, filtros, frescura y persistencia existentes.
ScanResult.comparisonRegion identifica FULL/ART/UPPER; triple.identity y triple.confirmation
registran margen de identidad y margen de impresión por separado (sesión v15).

Validación: 745 app +59 native =804 JVM,5Python,10Android. Incluye regresión con
los dos scores reales de Manamorphose, momia, cambios de UUID, conflictos, respuestas
obsoletas, AR no verificado, filtros y persistencia provisional sin alterar biblioteca.
Emulador sin ARCore: replay y Activity real, no cámara Triple en vivo.
No se modifica aún la cadencia de Triple ni su revalidación de frames lentos.
Manamorphose bloqueada usaba ~1.6 s/frame y ~2.3 s entre capturas; la corrección elimina
el bloqueo de identidad, no promete una nueva latencia antes de repetir físicamente.
APK v7: 67d58b59a9df8fbdb8e759a17352fbf00c36bbad6106b70a04cc5418bcf10e09.
Evidencias locales: build/edfast-auto/build-v7-final.log, emulator-v7-final.log,
test-v7-sony-regression.log; backup externo sony-triple-v6-audit-20261016-215132.

## Triple v8 — prioridad de confirmación y OCR de idioma por foto (2026-10-17)

La auditoría v7 sitúa la espera antes de confirmar: soloArte mediana 3,934 s desde primera
captura de la racha hasta pitido, postconfirmación 143,5 ms (18 altas enlazadas). Tres motores
5,561 s y144,5 ms (solo4altas visuales). Fast3,223 s hasta confirmación (34altas, excluye lookup).
Muestras no idénticas ni tiempo desde presentación física. Cadencia mismo candidato2230ms
soloArte y3696ms tres motores; OCR intercalado impide completar el par rápidamente.

V8 reserva un único turno de Arte tras el primer voto fuerte, reduce su descanso a150ms
(sin cambiar el descanso normal), mantiene perfil entre esos votos y devuelve luego OCR.
No se modifican umbrales .70/.08, dos votos, revalidación ni corroboración independiente AR.

Con OCR activo, las nuevas filas visuales sin idioma ejecutan OCR en segundo plano sobre
su foto, con modelos propios y cola serial máxima20, fuera de las puertas de cámara/OCRvivo.
Se muestra globo+spinner; al terminar bandera o interrogación, sin inventar idioma.
Título debe coincidir con identidad y decisión idioma no contradictoria; aliasES/PT ambiguo
sin reglas queda desconocido. Nunca vuelve a añadir una carta, cambiar identidad, ni actualizar
otra fila/copia o elección manual. No se usa idioma de impresión mientras el OCR físico está
habilitado. La cola pertenece a Activity; cerrar descarta pendientes, no es un nuevo WorkManager.
El OCR independiente de identidad continúa funcionando; no se confunde con enriquecimiento.

Pendiente medir CPU/temperatura con OCR de foto en paralelo y tiempos físicos Sony. El cambio
no promete latencias concretas ni arregla el fallo separado de guardado de ALCI ni la discrepancia
foto/identidad en solo AR descubiertos en auditoría. No se modifica colección histórica.


## Auditoría v20 y cambios v21 (18 octubre 2026)

- Evidencias privadas: `sony-triple-v20-audit-20261018-145849` fuera del repositorio,
  54 fotos y dos sesiones; no se ha reescrito la colección para realizar la auditoría.
- Solo Arte v20: 75 frames identificados, mediana 1550 ms; 25 pitidos,
  mediana 1714 ms desde captura del último frame. v8: 1538/1701 ms respectivamente.
  Estas cifras NO son el tiempo desde que el usuario presenta la carta ni un A/B controlado:
  v20 tiene activado singleReading y diferentes cartas/fases. Coste por frame similar.
- Contorno OFF: solo 4 frames, sin reconocimiento aceptado. No hay muestra suficiente
  para atribuir diferencias al overlay. ON reutiliza las esquinas de la inferencia existente.
- Cinco rearmes explícitos en v20; las demás identidades se aceptan sin tap.
  La regresión automatizada A→B→A / A→A mantiene copias separadas por ID.
- Paladín del Norte (fotos 24 y 27): 30A era una preferencia provisional, no edición
  confirmada (printingConfirmed=false, región upper). v21 relega 30A/CED/CEI/SUM/LEA/LEB
  ante ediciones normales dentro del mismo empate visual; PLST sigue último. Luego fecha
  descendente y precio conocido como desempate. No altera score, margen ni impresión confirmada.
- Primeras 30 fotos: idioma EN procedía del scope seleccionado, no de resolución OCR.
  v21 no sobrescribe una selección manual. En automático elimina el veto de título ausente;
  conserva contradicciones y umbrales estadísticos originales.
- CardLanguageResolver es la decisión común del Triple; el lector conserva los cuatro
  resultados de OCR y procesa texto por bloques, no solo y=510..800. Excluye el pie legal
  para no confundir su inglés con el idioma de la carta. Evidencia sidecar disponible en
  el panel Premium por copia. No relee la cámara ni cambia el planificador.
- Ruso sigue sin modelo OCR dedicado; no inventar ZHS/ZHT solo por hallar ideogramas.
  Los replays no son pruebas de velocidad del móvil.


Validación v21: 829 tests JVM (770 app + 59 native), 13 tests instrumentados en
emulador, sin fallos. Corpus privado de 24 fotos: 8 idiomas resueltos por la ruta
anterior frente a 22 correctos con el nuevo resolver. Se mantiene desconocido el
ruso y la foto china borrosa (49); la foto china legible (51) resuelve ZHT mediante
frases chinas y caracteres de variante corroborados, sin kana/hangul ni mezcla de
variantes. No generalizar esta muestra a una tasa universal de precisión.
Replay de las dos fotos reales del Paladín con borde neutro para detección:
30A → 4ED en ambas, conservando scores 0.84246/0.87488 y runners 0.61634/0.61602.
La APK validada es `build/edfast-auto/app-debug-v21-validated.apk`, SHA256
`9d87f739d1a20bbf8f827bdf8e6b4f7318f78074fd9722297a692d933cdaac4e`.
