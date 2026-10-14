# Laboratorio Triple AR

Entrada independiente `Triple AR · Opciones`. No modifica el escáner AR anterior ni
sus preferencias. **Esta fase reconoce y muestra candidatos: no añade cartas a la colección.**
La tira horizontal muestra hasta20 resultados de esta pantalla, no copias guardadas.

## Flujo actual

- Cámara única ARCore. Con una edición y paquete Premium instalado: carga `.imgdb`,
  muestra precio local sin esperar OCR/guardado y rectifica la carta del frame actual para OCR.
- Sin coincidencia AR: gracia450ms, luego EdScan global. Frames CPU limitados a250ms,
  una tarea en vuelo. Candidata provisional con score≥.85 en una lectura, o ≥.70 en dos.
  La confirmación visual conserva margen≥.08 y dos lecturas; no confirma idioma físico.
  El idioma desconocido no bloquea mostrar carta/precio. No guarda aún para postprocesado.
- Con AR activo, auditoría global cada1.5s: una coincidencia visual fuerte diferente puede
  desplazar el objetivo anterior; un resultado ambiguo no desplaza una coincidencia AR.
- Datos de candidata: Room → caché JSON → una carta Scryfall (timeout2s), una consulta
  en vuelo; nunca sincronizar toda la edición para mostrar una carta. Fallos son reintentables.
- Con o sin paquete, las referencias se acumulan en una DB de sesión (total≤1000,
  contando el paquete). Cola de un entrenamiento; clona la última DB serializada y añade
  solo la imagen nueva. Conserva anteriores y deduplica; al llenarse avisa sin borrar. Añadir la referencia puede tardar por imagen/entrenamiento, pero
  no bloquea el resultado textual. El AR anterior queda intacto.
- Sin edición: EdScan global desde el principio; al resolver UUID exacta prepara una
  referencia dinámica AR (si está activado). Imágenes no cacheadas pueden añadir latencia.
- OCR sobre carta rectificada: título y reglas por separado; idioma observado con
  confianza≥.80 o desconocido. OCR Latino en esta fase; otros alfabetos no se prometen.
  Una coincidencia de arte no confirma idioma ni distingue todas las reimpresiones.
- Alias de nombre se consultan aparte, máximo una consulta; resultado tardío>2s descartado.
  El diccionario no bloquea cámara ni detección de idioma. No se decide edición por título solo.
- EdScan fuera de los sets permitidos o nombre OCR contradictorio: aviso de conflicto.
  El selector admite varios códigos: se usa respaldo visual/OCR sin elegir una DB arbitraria.
- Si falta el paquete, se ofrece el selector de descargas y se mantiene EdScan/OCR.
  La activación del paquete seleccionado debe verificarse físicamente con varias cartas.

## Efectos

Opciones → «Confeti 3D experimental»:96 partículas GPU, una explosión por cambio de
objetivo, solo cuando la pose tiene FULL_TRACKING. Se puede combinar con la opción
existente de apertura3D. Precio dibujado en el overlay de la carta, no texto3D volumétrico.
No consume GIF ni ejecuta simulación CPU por partícula. Efectos apagados por defecto.

## Medición

Build debug activa `EdScanPerf`; no registra fotos ni texto OCR. Etapas:
`triple.workerQueue`, `triple.decode`, `triple.edscan`, `triple.ocr`,
`triple.titleMatch`, `triple.language`, `triple.localPrices`, `triple.lookup`,
`triple.confettiDraw`. Las etapas AR existentes miden referencia, configuración y GL.
`triple.health` cada5s incluye heap, ahorro energético, estado térmico y contadores.
`triple.ocrComplete` permite comparar lectura frente a espera del diccionario.

```powershell
adb -s QV770HG2JD logcat -v threadtime -s EdScanPerf:D EdScanFrame:I AndroidRuntime:E
```

Comparar mismo teléfono/batería/iluminación: AR anterior con FRA, Triple con FRA,
Triple sin edición; después repetir con confeti/apertura. Medir primera lectura y
lecturas calientes por separado. No atribuir a los efectos una demora de catálogo/red.

## Pruebas

- `TripleScanPolicyTest`: arbitraje, límite temporal, score/margen, idioma incierto,
  acuerdo de títulos.
- `TripleScanSmokeTest`: cámaraAR + inferencia real, pausa/reanudación, OCR de fixture
  SolRing FRC en inglés, referencia dinámica, shader GL sin errores, FRA574+respaldo,
  colección y preferencias del scanner anterior sin cambios.
- Assets solo del runner en `build/ar-test-assets/triple-card.json` (objeto con
  `references:[ref]`) y `triple-card.jpg`: extraídos del paqueteFRC, referencia SolRing.
  No empaquetar corpus de pruebas en la APK principal.
- Prueba física pendiente: seguimiento y rectificación sobre cartas móviles, conflictos
  de idioma/edición, latencia sostenida y experiencia del confeti en el Sony.

No hay guardado automático, resolución automática de conflictos, ni garantía de
identificar todas las ediciones por OCR. Promos históricas pueden tener imagen en otro
idioma aunque el nombre canónico del catálogo sea inglés (PAPC es un caso real).

## Traza persistente de pruebas

En debug `files/triple-trace/current.log` y `previous.log` guardan observaciones acotadas
(~2MiB total; cola64, escritura en worker). Incluyen score/margen, candidata provisional,
consulta local/disco/red, idioma confirmado, tiempos OCR/AR y estado térmico. Sin fotos,
nombres ni texto OCR. Los contadores de etapa detallados siguen en logcat.

```powershell
adb -s QV770HG2JD shell run-as io.asv.mtgocr.ocrreader cat files/triple-trace/current.log
```

Probar cartas difíciles primero sin set y después con Ixalan seleccionado, alternando
cartas de Ixalan y de otras ediciones. No confundir una candidata rápida con impresión
confirmada; comprobar el aviso provisional y el idioma `?` cuando falta evidencia.

## Ayuda OCR de nombre

Guía cian activada por defecto (se puede ocultar en Opciones). Comparte geometría
`OcrTitleRegion.forFrame` con OCRscanner. ARCore convierte sus esquinas VIEW a
IMAGE_PIXELS en el mismo frame: respeta recorte de preview y rotación. Se lee el nombre
sin exigir el contorno de carta cuando no hay AR ni candidata visual confirmada.
Una lectura por segundo como máximo; comparte el único OCR en vuelo. El nombre local
reconocido aparece temporalmente debajo de la guía; no inventa impresión, idioma ni precio.
EdScan y AR siguen trabajando. No es aún guardado para postprocesado.

Trazas persistentes: `triple.guideStart`, `triple.guideOcr` (MLKit y número de líneas),
`triple.guideLookup` (diccionario, coincidencia y resultado vigente), `guideLookupDeferred`.
No se registra el nombre leído. Si se necesita analizar una carta concreta, probarla aislada
y anotar la hora aproximada: los logs no permiten identificarla por su nombre.

## Borde por rareza

Triple usa verde para común, plata para infrecuente, oro para rara, naranja para mítica,
violeta para especial/bonus y gris neutro si falta información. Canvas y borde energético
comparten color; el comparador AR conserva su comportamiento. Rareza de impresión exacta,
no del precio. Paquetes nuevos incluyen rareza; los antiguos usan catálogo local por bloques
y consulta exacta asíncrona si falta. No es necesario regenerar las bases de imágenes.

## OCR → candidato → AR sin esperar arte

Tras matching local de nombre, `TripleOcrPrintingResolver` verifica todas las impresiones
con búsqueda exacta Scryfall `unique=prints`, incluyendo extras y papel. Caché completa
por nombre durante un día; una consulta en vuelo y timeout3s. Una lista parcial (`has_more`)
o varias ediciones NO permiten asumir edición única. Room local no prueba exhaustividad.
Con una sola edición muestra candidata OCR y la añade a la galería y DB AR, sin esperar
EdScan. Si hay variantes sigue marcada provisional; idioma físico desconocido no se inventa.
Soporta imagen de la cara nombrada en cartas de doble cara. No escribe en la colección.
La consulta inicial requiere red si no existe caché completa; error no bloquea cámara.

DB de sesión conserva referencias al pausar/reanudar. Al cambiar paquete reconstruye la
unión del nuevo paquete y las cartas aprendidas en la sesión, hasta el límite; no persiste
esa DB temporal tras cerrar Activity. Continúa mostrándose un overlay principal, no20.
Logs: `triple.ocrPrinting`, `triple.ocrPromoted`, `triple.sessionAppend`, `sessionDbFull`.

Borde energético: núcleo blanco/neón teñido por rareza, halo y niebla procedural animada
(ruido de tres octavas GPU, sin GIF). Activarlo en Opciones si estaba desactivado. Probar
latencia/temperatura con efecto encendido y apagado; shader conserva medición AR.energyBorder.

### Visibilidad del neón y la niebla

Núcleo ensanchado (~3x), halo más ancho y niebla principalmente exterior hasta0.29 del
ancho/alto de referencia; el centro queda libre. Primera pasada de humo con alpha normal
para contrastar incluso sobre fondos claros; segunda pasada neón aditivo. Mantiene dos
pasadas y ruido procedural, usando highp cuando está disponible. Smoke instrumentado
comprueba píxeles coloreados fuera del borde sobre fondo blanco y centro sin cubrir.

## Flags multicarta e imagen proyectada

En Opciones, ambos desactivados por defecto y con preferencias independientes:
- «Seguir varias cartas en AR»: dibuja contorno/neón y precio para hasta20 referencias
  distintas con FULL_TRACKING simultáneo. No conserva posiciones obsoletas al perderlas.
  OCR, apertura y confeti siguen sobre el objetivo principal; no multiplica inferencias.
- «Mostrar imagen sobre la carta»: bitmap proyectado con las cuatro esquinas de la pose
  AR, también para las demás cartas si multicarta está activo. No es un rectángulo fijo.
  Carga asíncrona, máximo20 bitmaps384×536 y2 workers; no bloquea cámara.
  Con idioma observado busca imagen del set+número exactos. Sin imagen localizada utiliza
  la referencia, etiquetada como tal; no inventa el idioma físico. Para reversos DFC mantiene
  referencia correcta porque el proveedor localizado devuelve solo la primera cara.
  Al cambiar idioma/URL se retira el arte anterior, con placeholder durante la carga.

El núcleo neón pasa de.017 a.012 (~30% menos); niebla sin cambios. Multicarta aumenta
coste gráfico: medir AR.drawWork/triple.multiBorder y triple.arImage. Dos copias físicas
idénticas no equivalen a dos referencias distintas y no se garantiza su separación.
Pruebas: política1/20, dos imágenes sobre quads independientes por píxeles, pérdida de
tracking, carga asíncrona/error sin reutilizar arte y flags. Falta prueba física multicarta.

### Idioma y datos visibles
- AR y galería muestran bandera + código del último idioma confirmado por OCR; sin evidencia, `?`, nunca EN por defecto. Independiente del flag de imagen AR.
- Galería horizontal: edición/número y precio, actualizados cuando llegan datos asíncronos. Máximo 20 entradas por identidad Scryfall, sin duplicar filas al actualizar.

### OCR multiescritura sobre snapshot real
`TripleSnapshotOcr` ejecuta latino, japonés, coreano y chino secuencialmente sobre el mismo bitmap rectificado de cámara; un snapshot en vuelo. No lee el overlay ni guarda fotos permanentemente. Mantiene la imagen hasta completar todos los modelos y registra `triple.snapshot.<modelo>` en EdScanPerf. La selección requiere al menos cuatro caracteres nativos y un 25 % de letras de esa escritura; los empates latinos conservan la lectura latina.
Idioma: dos observaciones compatibles por identidad; reglas cortas distintivas compartidas con OCR scanner e índice de títulos exactos. Kana/Hangul abundantes son evidencia de JA/KO; Han solo no prueba JA ni distingue ZHS/ZHT. Lecturas inciertas conservan la confirmación; el cambio requiere evidencia repetida. Ausencia sostenida/pausa reinicia evidencia, sin borrar la última observación histórica de la galería. No separa ejemplares idénticos sin interrupción visible. Original y postprocesado conservan política anterior.
Ruso pendiente: las dependencias ML Kit Android usadas no ofrecen OCR cirílico; requiere otro motor, no basta activar el clasificador de idioma. Medir latencia adicional de cuatro modelos en Sony; cámara/arte no esperan al idioma.

### Estabilidad de idioma y convergencia con OCR scanner
- El detector estadístico es compartido (`CardTextLanguageDetector`). Triple recupera `ScanLanguagePolicy.shortRulesLanguage` para verbos distintivos ES/PT/EN y el índice exacto `TitleLanguageIndex` ya usado en análisis de reglas. No usa un alias arbitrario como prueba de idioma: títulos ES/PT compartidos conservan ambos candidatos.
- Dos observaciones compatibles confirman; una lectura vacía no borra ni rompe el progreso. Cambiar un idioma confirmado exige tres observaciones alternativas compatibles. Una lectura que respalda el idioma actual cancela la alternativa. Conflictos título/reglas se muestran como conflicto y no autorizan corrección automática.
- Pérdidas de contorno menores de 3 s no reinician evidencia. Una ausencia sostenida, pausa o cambio de identidad sí; la galería conserva la última observación histórica hasta una nueva entrada de esa identidad. No distingue dos copias de la misma impresión presentes a la vez: pendiente `scanEventId` al añadir colección.
- Logs guardan origen de evidencia, candidatos de idioma, confianza, cantidad de letras y resultado; no texto OCR ni fotos. Captura de logs anterior: `build/triple-language-before.log` (PT/JA confirmados seguidos de lecturas vacías que antes eliminaban el idioma).

### Evaluación para integrar postprocesado (propuesta, todavía NO implementada)
El laboratorio sigue sin añadir inventario ni crear trabajos de postprocesado. Hoy recicla el bitmap tras OCR; texto/títulos solo viven durante callbacks, y scores/márgenes visuales están principalmente en logs, no en un expediente durable.

| Evidencia | Disponible en Triple | Uso futuro y límites |
|---|---|---|
| Frame real y recorte rectificado | RAM, antes del overlay | Guardar la mejor captura limpia y geometría/rotación/calidad; permitir repetir OCR sin cámara. Nunca guardar como evidencia la imagen inglesa de referencia. |
| Título impreso y canónico | OCR + diccionario; índice exacto de idiomas | Conservar ambos, coincidencias y todos los idiomas compatibles; no reemplazar título localizado por inglés. |
| Reglas, motor OCR, confianza y consenso | Lectura actual; logs parciales | Conservar lecturas útiles y contradicciones por frame, no solo el idioma final; evitar volver a analizar una evidencia ya fiable. |
| Candidato AR | UUID/Scryfall ID, set/número, procedencia y referencia de DB | Propuesta rápida de impresión, NO demostración de idioma, foil ni de exclusividad del arte entre reimpresiones. Versionar paquete/referencia. |
| Candidatos EdScan | Ranking/score/margen durante scan | Conservar shortlist, umbral y si fue provisional; contraste independiente con OCR/AR. |
| Restricción de edición y conflicto | Selector y candidato | Registrar como preferencia del usuario, no como prueba visual. |
| Precio/imagen del catálogo | Caché/lookup | Solo presentación; no evidencia para resolver idioma o impresión. |

Al implementar guardado: expediente versionado por `scanEventId` (ejemplar/evento), enlazado a `collectionItemId`, biblioteca y `photoKey`, con escritura atómica fuera de cámara. Reutilizar almacenamiento/cola `EdScanJobs` y contratos de `ScanReviewStore`, no inventar una tercera colección. El patrón de `OcrFramePhoto` sirve de referencia pero su entrada es NV21; Triple necesita adaptar su bitmap real. Respetar preferencia de fotos, acotar retención y no hacer escrituras por frame.

Automatización propuesta: confirmar cada campo por separado (identidad, impresión, idioma, acabado); reutilizar evidencia coincidente de varias fuentes; reanalizar solo campos pendientes y solo con mejor imagen. Título compartido, idioma contrario a catálogo, arte reutilizado o señales contradictorias => revisión, no elegir EN/primera edición por defecto. Confirmación humana tiene prioridad. Aplicar únicamente si `collectionItemId`, UUID/acabado y versión del elemento no han cambiado; nunca alterar cantidades, condición ni foil por el resultado AR.

### Comparación de motores y copias en galería
Checks siempre visibles AR/Arte/OCR, persistidos en `triple_scanner`. AR desactiva reconocimiento de imágenes/DB y efectos, NO la cámara ARCore ni su seguimiento de entorno (no es una comparación CameraX vs ARCore). Arte desactiva detección/inferencia EdScan; OCR desactiva los cuatro modelos. Cambios invalidan resultados pendientes. Solo AR reconoce directamente desde tracking, sin necesitar decodificación YUV/OCR/arte. Solo OCR usa la guía del título: ediciones ambiguas pueden quedar sin resolver. Sin ninguno queda preview, sin análisis de cartas.

Galería por evento de lectura, no por Scryfall ID: actualizaciones de una misma lectura permanecen en su fila; otra identidad o retirada observada durante >=1 s y reentrada crea otra copia, con idioma propio. No se crea una copia por frame. Mantener siempre una carta idéntica visible/intercambiarla sin hueco detectable no permite distinguir físicamente copias. Histórico acotado20; aún no guarda inventario. Precios se pueden refrescar para todas las copias, idioma únicamente para el evento vigente; callbacks OCR anteriores invalidados al iniciar evento.

### Medición antes de estos cambios (Sony, últimos escaneos del usuario)
Recogida ANTES de instalar/pruebas: `build/triple-latest-analysis/{current,previous,logcat,last-session}.log`. Última sesión real delimitada por `triple.session v=3`: 1791290063581–1791290581291 (~8 min 38 s), separada de sesiones instrumentadas anteriores.

| Trabajo observado | Muestras | Mediana ms | p95 ms | Máximo ms |
|---|---:|---:|---:|---:|
| OCR carta + clasificación/índice | 77 | 1702 | 5819 | 11526 |
| OCR guía título | 179 | 986 | 1895 | 7309 |
| Inferencia arte, muestras con carta | 177 | 638 | 1791 | 3795 |
| Detección contorno, muestras con/sin carta | 398 | 212.5 | 518 | 1741 |
| Lookup metadata | 19 | 44 | 262 | 262 |
| Añadir referencia a DB dinámica | 11 | 849 | 1379 | 1379 |

Guía: solo4/179lecturas con texto. Salud104muestras: thermal0 y powerSavefalse. No prueba ausencia total de calentamiento, pero no hay limitación térmica declarada. `candidateShown`:25EdScan,1OCR; NO significa cero deteccionesAR porque esa métrica anterior no contaba todas las rutas. Arte muestreado/rate-limited: no distribución completa por frame. `ageMs` de escena mezclaba varias cartas y NO sirve como tiempo hasta reconocer cada copia. Entrenar referenciaAR no es identificarla. No existe medición histórica fiable de latencia físicaAR en estos logs.

Conclusión: OCRcuatromodelos es trabajo importante, lookupnoeselcuellodebotella. No atribuir causalmente ralentización cruzada sin comparar mismas cartas/misma luz con combinaciones de flags. Mantener algoritmos iguales durante esa comparación. Nuevos logs persistentes `triple.methods`, `triple.methodSample`(arte detect/infer y OCRporcada modelo), `triple.arTracking` (desdehabilitado, incluye tiempo sin carta), `triple.encounter` por copia. ARCore sessionUpdate enlogcat mide trabajo de cámara/AR, no latencia física de reconocimiento. No presentar estos tiempos como mediciones equivalentes.

### OCR rápido: identidad e idioma antes de edición
El FAB `fabOcrMlKit` (derecha de la fila inferior) usa `OcrCaptureActivity` y su ruta `matchLocalOcrText` → `quickScanCard` con fallback de nombre; no confundirlo con `RapidEditionScanActivity`. Triple reutiliza esas APIs y el tono `STREAM_NOTIFICATION/TONE_PROP_BEEP` de95ms, una vez por evento aceptado (AR, EdScan u OCR), no por actualización de datos. El sonido respeta volumen/silencio del dispositivo.

La guía intenta primero el modelo latino y diccionario local; solo sin coincidencia prueba multiescritura. Una coincidencia crea inmediatamente una fila de nombre/edición pendiente, sin HTTP obligatorio. `quickScanCard` propone impresión local en segundo plano (si hay bloqueo de edición y falta caché puede consultar catálogo, pero no bloquea mostrar la identidad). La impresión es siempre provisional para OCR rápido; precio/arte de referencia no prueban que sea la edición física. Sin opción local se conserva la fila y el resolver anterior de edición única solo enriquece cuando puede hacerlo. `printing` vacío significa identidad sin edición, no UUID real.

`TitleLanguageIndex.lookup` usa líneas leídas + identidad canónica: un único idioma exacto confirma inmediatamente, sin exigir OCR de reglas ni segunda lectura. Ejemplo del asset: `Madeixas da Medusa` → `Coils of the Medusa` → `pt`. ES/PT compartido no se resuelve arbitrariamente; conflicto con evidencia previa se señala. La actualización de edición cambia la clave de identidad del evento sin crear otra copia ni pitar otra vez, conserva idioma y descarta respuestas de eventos anteriores. Un callback de impresión provisional OCR no puede sustituir una impresión que AR/arte ya resolvió en ese evento.

Logs originales recogidos antes de cambios: `build/triple-ocr-comparison/`. Sesiones de usuario1791295164847 y1791295674875: soloOCR190lecturasguía,mediana416ms,max1125ms;92matchesválidos,4matchesdescartados,11sinmatch;solo2eventosOCR. Consultasrepetidas`uniqueSet=false` explican bloqueo aunque título ya estaba reconocido. EnAR+OCR conFRA7eventosAR. Esto demuestra un bloqueo de política de edición, no una lentitud equivalente del OCR de caracteres. Nuevoslogs`triple.ocrIdentityReady`, `triple.ocrQuick`, `triple.titleLanguage` y `triple.feedback` separan etapas/feedback. No se guardan cartas en inventario todavía.

## Idioma único de impresión visual (2026-10-06)

Excepción a la regla previa de no inferir idioma: AR/EdScan no provisional, con impresión,
edición y número concretos. Consulta independiente de Scryfall con idiomas incluidos,
sin filtrar por disponibilidad de imágenes; solo acepta respuesta completa (sin paginación pendiente).
Caché de sesión y disco 24 h; ejecución dedicada, timeout 3 s, sin bloquear galería/cámara.
OCR provisional nunca activa esta inferencia. Multilingüe, catálogo incompleto o error: no asignar.
Título ambiguo sigue requiriendo evidencia; no se cambia su consenso.
OCR contrario marca conflicto conservador y no sobrescribe el idioma anterior; se registra
`printingLanguageApplied` y tiempo/fuente en `printingLanguage`. Callbacks protegidos por
generación, encuentro e impresión. No cambia colección ni integra todavía el expediente
con postprocesado: allí deberá cuestionarse la edición ante OCR físico contrario.

## Menú y colección (2026-10-07; sustituye el contrato anterior de laboratorio sin guardado)

- `TripleCollectionSession`: una fila Legacy por encuentro, sin merge, grupo Triple por sesión; UUID exacta resuelta por repositorio y selección Room verificada. No inventar UUID para identidad OCR sin impresión: queda pendiente. Cola separada y protección de versiones/edición/idioma/biblioteca; errores visibles con reintento.
- Menú sobre preview, anclado al contorno si AR tiene pose: foil siempre, idioma si falta evidencia o es manual. Galería abre popup con idioma editable, foil, cantidad 1–99 y papelera confirmada. La carta grande ocupa el lado izquierdo, a todo el alto del popup y con proporción 63:88; `TripleGalleryPopupLayout` reserva su ancho antes que los controles, que pueden desplazarse verticalmente en pantallas pequeñas. Selección manual de idioma (incluido desconocido) no se reemplaza por callbacks OCR. Precios por identidad+acabado, sin heredar el importe anterior.
- Foto JPEG inmutable de la captura aceptada, sin efectos AR, enlazada en `files/edscan` con fuente OCR para reconocimiento OCR/provisional, EdScan para visual fiable. En guía de nombre se extrapola el encuadre completo; si cae fuera del frame no se inventa foto. AR solo también solicita CPU frame hasta guardar su foto. Guardado/identificación no esperan postprocesado. Sus trabajos pesados ceden mientras Triple está activo.
- Triple no aplica CLAHE: CPU ARCore hasta1280×960 según configuraciones disponibles, rectificación630×880 y guía630×182. No se ha aumentado resolución en esta tarea. El original OCR usa1600×1280 con mejora activada,1280×1024 sin ella. Triple ahora prepara el predictor con indicador no bloqueante y logs, a diferencia de la barrera visual del OCR original.
- Precios: el paquete antes consultaba solo Room; ahora también lee el snapshot local completo del proveedor, como `loadCollectorVisionPrinting`. Persistencia reutiliza esta ruta; nunca precio por nombre ni acabado distinto.
- Validación: `TripleCollectionSessionTest` usa archivos/preferencias aislados y limpia sus selecciones Room. No ejecutar los antiguos smoke tests con supuesto sin-escrituras sobre biblioteca real hasta migrarlos.

## Correcciones de encuentros, idioma y edición (2026-10-07)

- Una identidad canónica dentro del mismo encuentro conserva fila/copia, foto, idioma y cantidad al alternar OCR/EdScan/AR o variar impresión. No se añade cooldown al primer reconocimiento. Ausencia observada >=1 s permite nueva copia; cambiar a otra identidad sigue iniciando otro encuentro. Copias idénticas sustituidas sin ausencia visible siguen requiriendo control de cantidad.
- No sustituir impresión fiable por candidata provisional ni una edición manual por resultados tardíos. Selector compartido desde overlay/galería; UUID exacta y acabado seleccionados se pasan a la persistencia. Revisión de callbacks por fila; no requieren que siga siendo la carta actual.
- Si la consulta OCR restringida al set falla, búsqueda global con impresión provisional y aviso de fuera de selección. Set elegido visible permanentemente. Si tampoco hay metadatos globales: fila explícitamente pendiente, selector y reintento, no falso guardado.
- `LocalCardNameMatch.displayName/language` se contrasta con TLV1: alias único PT como Madeixas da Medusa confirma idioma aunque el texto bruto tenga una errata. Ambigüedades ES/PT no se resuelven tomando el primer alias. Procedencia title/rules/printing/manual persistida; PT observado puede sustituir EN inferido y cuestionar edición. Consenso ligado al encuentro, no UUID de impresión.
- Preparación de nombres comparte predictor/autocompletado/matching del OCR original; logs reales 11143 ms primer arranque y192 ms siguiente. No es entrenamiento del OCR: es catálogo de alias y búsqueda aproximada. Triple deja continuar reconocimiento visual durante la preparación.
- CLAHE sigue desactivado en Triple; preferir futura prueba A/B de ROI del título o fallback, sin añadir una pasada a cada frame ni cambiar fotos originales/arte. Coste real en Sony aún sin medir.


## Idioma explícito del lote (2026-10-07)
- Al confirmar otro conjunto de ediciones se ofrece idioma o Todos los idiomas. Cabecera siempre visible y pulsable: edición + bandera/código, o Todos los idiomas.
- Catálogo MTGJSON SetList.languages, caché atómica local de 24h y fallback al último catálogo sin red. Consulta asíncrona independiente de cámara. Para varios sets se ofrece la unión; dato ausente no se interpreta como EN-only. Sin catálogo: mensaje, reintento y Todos los idiomas.
- Preferencia triple_scanner scope_language ligada a language_scope_sets; editar/cambiar sets elimina el idioma antiguo. Cancelar la elección nueva conserva Todos. No modifica filas anteriores ni crea copias al cambiar ajustes; se aplica al siguiente encuentro nuevo.
- Origen scope, protegido como selección manual, guardado en colección/revisión y visible en AR/galería. Por carta puede sobreescribirse en su menú. Se omite inferencia de idioma por impresión y OCR de idioma; El título se sigue comprobando aunque AR/ART identifique la carta (v7); solo se omite clasificar idioma. Identidad OCR sigue funcionando con solo el modelo del alfabeto fijado (Latin/JP/KO/ZH); alfabetos sin modelo específico mantienen fallback, no se promete soporte OCR nativo ruso.
- Logs triple.languageScope, triple.setLanguages, triple.ocrSkipped y triple.languageSkipped para medir el ahorro.
- **Pendiente, NO activar CLAHE**: prueba A/B sobre ROI del título o reintento, medir coste y precisión en Sony antes de habilitarlo. Mantener resolución actual.
- **Pendiente tras pruebas reales**: valorar promoción a develop como escáner principal y refinar UI/UX con edición/idioma claramente visibles. No merge automático ni cambio del escáner predeterminado en esta entrega.


## Retoques de controles antes del rediseño
- Selector explícito de idioma junto al campo de edición dentro de Opciones; cabecera no interactiva, solo refleja símbolo de set, siglas en negrita e idioma.
- Retirado el comentario del laboratorio. Mensajes de espera «Enfoca una carta» y «AR + EdScan + OCR preparados» no ocupan espacio; errores, cargas y resultados se conservan.
- Preparación del índice: contenedor destacado con texto en negrita y barra indeterminada; desaparece al completar el callback.
- Rediseño completo aplazado hasta integración en develop; estos cambios no realizan el merge.


## Precio exacto ausente de la copia local
- `CardRepository.loadScannerPrice` consulta el snapshot/Room y, si falta el precio exacto de ese acabado, Scryfall en ejecutor independiente (timeout8s); almacena caché para próximas lecturas. No descarga el índice global ni busca otra edición.
- `TripleScanActivity.requestMissingPrice` valida ID/impresión/acabado al volver; persiste el precio y refresca AR y galería. Una respuesta local vacía no borra un precio ya obtenido. Log `triple.priceFallback`.
- Regresión Sony `TriplePriceFallbackTest`: Stingerquill Charm FRA434 tenía catálogo pero no precio local; FRA150 sí tenía0.37EUR en snapshot27sep. No reutilizar150 para434. Prueba live sin mutar inventario, solo caché de precios.


## Arbitraje y medición v7 (2026-10-08)

- Los motores proponen; solo `offer`/`TripleRecognitionArbiter` autoriza persistir. Preview AR inmediato, identidad AR/OCR estable600ms; EdScan exige dos frames **cada uno** con score>=.70 y margen>=.08. No sumar votos por frecuencia AR.
- v9: `prepareForCamera` limita ONNX a dos hilos y desactiva espera activa solo en Triple. OCR y arte no se ejecutan simultáneamente; se deja una pausa de 150 ms tras el trabajo. La primera lectura visual fuerte prioriza la segunda; OCR de una identidad estable se audita cada 3 s.
- Un análisis visual >1800 ms no se acepta como fresco: se conserva un único resultado sin bitmap y se compara su huella con el recorte del siguiente fotograma, en las mismas coordenadas. Deben coincidir escena, generación, scope y apariencia (distancia <=.08, trabajo <=15 s). Si cambió la carta, se descarta y reinicia la confirmación. Solo tras esta comprobación se excluye el tiempo de inferencia del límite de inactividad de las dos lecturas. El árbitro mantiene su caducidad de 2500 ms.
- `TriplePersistencePolicy` normaliza AR/EdScan fuertes al mismo origen persistido: alternar motores sin cambiar el estado real no reescribe la colección. AR tampoco degrada una impresión ya confirmada por EdScan.
- `TripleSessionTotals` mantiene importes y cantidades por encuentro fuera del límite de 20 miniaturas; convierte con `PriceCurrency` y señala copias sin precio. El resumen está a la derecha del acceso a idioma, encima de la galería.
- Restauración explícita mediante `triple_resume_group` + `triple_resume_library`: carga únicamente las filas de esa agrupación, conserva IDs/cantidades/ediciones/metadatos y continúa escribiendo en el mismo grupo. No reescanea ni añade copias al restaurar. La preferencia interna `triple_resume_once` permite preparar una única reanudación tras una actualización autorizada.
- EdScan fuerte corrige AR dentro del mismo evento (misma fila/cantidad/foto), sin nueva copia. Otro EdScan contradictorio requiere OCR repetido coincidente para corregir; si no, queda provisional para revisión. AR no revoca EdScan. La preferencia temporal también se respeta en el callback AR directo.
- Nuevo evento solo tras ausencia sostenida3muestras/1500ms o cambio estable de apariencia rectificada400ms, con mínimo600ms entre altas. La huella compara arte normalizado de un solo productor por modo, no reconoce cartas. La confianza de identificación no filtra la huella física: una carta con score bajo también puede abrir escena tras un cambio estable. Los recortes planos/sin contraste no autorizan cambio. Perspectiva de la misma identidad no añade copia; dos idénticas sin retirada deben contarse explícitamente. Estos umbrales físicos necesitan prueba de cámara con manos/reflejos/foil.
- Las propuestas llevan tiempo de captura; no reutilizar callbacks anteriores a la escena o de más de2500ms. Pausa/cambio de opciones limpia propuestas, no duplica la fila actual. OCR conserva título con idioma fijo y evita reclasificar idioma manual.
- EdScan llama al motor con IDs Scryfall exactos de `loadVisualSetIds` y conserva lista estable para reutilizar el índice. `null`=global sin edición; vacío=cargando/error, nunca fallback global. Se admiten varios códigos. FRA y FRC son sets distintos en este filtro: seleccionar ambos para incluir ambos; los anexos existentes de STX/SOS siguen la política compartida. OCR mantiene su fallback fuera de set marcado como conflicto.
- Mientras falta la segunda lectura fuerte se acelera la auditoría EdScan, sin esperar el periodo normal1500ms. No se han rebajado .70/.08/2. Constantes centralizadas en `TripleScanPolicy`.
- `triple.confirmation`: score,runner,margin,strongReadings,confirmMs,frameMs,preprocessAndQueueMs,detectMs,inferMs,featureMs,searchMs,searchedRows,scopeSize y reason (`score`, `margin`, `readings`, `no_runner`, `no_candidate`, `no_detection`, `scope_pending`, `confirmed`). `featureMs` incluye rectificación y modelo; no es solo ONNX. `confirmMs` parte del primer candidato de la secuencia, no de la entrada física. Complementar con `triple.arbitration`, `triple.scene`, `triple.acceptInterval` y `triple.health` (térmico/ahorro).
- Trazas debug privadas conservan hora de emisión, no de escritura, con rotación8MiB actual+anterior. No contienen nombres ni fotos. Analizador local:

```sh
python scripts/analyze_triple_confirmation.py build/triple-box-audit/previous.log build/triple-box-audit/current.log --slow-ms 1000
python scripts/test_analyze_triple_confirmation.py
```

- `TripleRecognitionPerformanceTest` compara catálogo global/seleccionado y scope vacío sin cámara ni escritura de inventario. Primer replay Sony: fixture Sol Ring FRC21,110278→103referencias, dos lecturas457→300ms; todos los top1 esperados. Matchingglobal27–94ms frente<1ms acotado. No es benchmark de FRA ni garantía de latencia live; no incluye cadencia cámara/AR/OCR. Salida privada `build/triple-box-audit/recognition-benchmark.log`.

- Repetición final tras suite de integración: dos lecturas global803ms/acotado658ms, todos los top1 correctos; matchingglobal30–101ms yacotado<1ms. El coste restante de detección/extracción varió: no atribuir sin medición a temperatura/carga ni prometer300ms constantes. Suite final603tests app+50native,3Python y3instrumentalesSony correctos; versión instalada install-r, sin borrar datos. Prueba física con cambios rápidos/reflejos/foil pendiente.

- v10: varias ediciones pueden reutilizar un único paquete AR instalado si sus metadatos cubren todas (FRA contiene FRA+FRC+SPG). La DB compilada conserva sus entradas y la recepción de identidades se filtra a los sets elegidos; no se mezclan binarios. Sin cobertura completa se conserva fallback. Pausa adaptativa entre análisis: mitad del trabajo, mínimo150/máximo650ms; umbrales .70/.08/2 sin cambios. `TripleCameraLoadTest` acepta duración30–180s y validación optativa de cobertura FRA+FRC, sin persistir inventario.

- v11: `TripleArConsensus` permite una lectura visual >=.60/margen.08 sólo con AR continuo600ms de UUID idéntico y reciente (tracking<=350ms, captura<=2500ms); se vuelve a comprobar al aplicar. El consenso abre transición de identidad sin esperar dos análisis completos adicionales; el mismo nombre/otra impresión no crea copia. Sin consenso siguen .70/.08/2. Identidad ya verificada+AR se audita cada5s; idioma fijo evita OCR repetido de esa impresión. Un cambio AR descarta resultados del target previo antes de OCR y evita el descanso de ese frame obsoleto. Pérdida AR breve respeta450ms desde último callback real, no sólo último frame analizado. No se amplía TTL global.
- Las notificaciones AR directas son la única fuente de votos AR: no repetir desde el frame decodificado un timestamp anterior. `stale`/`out_of_order` no rearman auditoría. Deduplicar entrenamiento frente al paquete por UUID Scryfall, no por clave printing (puede ser distinta entre AR y catálogo).


## Auditoría integral y revisión v12 (9 octubre 2026)

Esta sección sustituye las reglas antiguas que exigían corroboración de OCR o cambio de huella para abandonar la identidad anterior. No es otra bajada de umbrales.

### Cambios acumulados y destino

| Revisión | Cambio | Resultado de la auditoría |
| --- | --- | --- |
| v7 | Antirráfagas, dos EdScan .70/.08, prioridad EdScan, índice filtrado y trazas | Mantener frescura, intervalo de copias, filtros y diagnóstico; sustituir la autoridad histórica que bloqueaba indefinidamente cartas nuevas. |
| v8 | Huella física independiente del score para cambiar de escena | Mantener como indicio de presencia/ausencia; no exigirla para aceptar una identidad nueva ya fiable. Puede no separar dos cartas o reaccionar a perspectiva. |
| v9 | Dos hilos ONNX sin spin, liberación temprana del frame, modelos serializados, revalidación de capturas lentas, deduplicación de guardados | Mantener. La frescura de una captura y el plazo para acumular evidencia son conceptos distintos. |
| v9 | Total de sesión y restauración por IDs | Mantener; comprobar SHA de todas las bibliotecas en pruebas de cámara/replay. |
| v10 | Paquete AR que cubre varios sets y descanso adaptativo | Mantener carga completa del binario; filtrar identidades siempre, también al elegir sólo FRA. Nunca ampliar el filtro por todo lo incluido en el paquete. |
| v11 | AR estable + EdScan .60/.08 exacto, auditoría espaciada, descarte de frames obsoletos, eliminación de callbacks AR duplicados y entrenamiento repetido | Mantener como corroboración de impresión y control de carga, pero no como requisito para reconocer el nombre. Una diferencia de UUID puede ser una variante de la misma carta. |
| Galería/postprocesado | Imagen grande del popup, filtro de agrupación, total y protección de filas por ID | No son la causa de estos bloqueos; conservar. |

### Fallos demostrados por las trazas Sony

- **Loyal Tutor**: AR indicaba FRA #424 (`f2d44935-2473-464f-b402-ea45b0e80c99`), EdScan FRA #14 (`490dae91-94ce-42a9-a11f-6c5e77c4e486`). La comparación exacta de impresión fallaba aunque coincidiera el nombre. La autoridad de Fateshaper anterior seguía rechazando AR. Al retirar/reponer se creó finalmente otra escena. No es prueba de que desactivar OCR sea la solución.
- **Idioma**: durante el fallo consta `languageSkipped reason=fixedScope language=en`. El idioma fijo sí se respetaba en esa ruta; no atribuirle el rechazo sin evidencia.
- **OCR**: resultados separados aproximadamente 4,3 s reiniciaban el contador que sólo admitía huecos de 2,5 s. Se confundía caducidad de una lectura con continuidad del candidato.
- **Danitha, Sword of Hope**: EdScan emitió repetidamente resultados confirmados (por ejemplo .806 y margen .312), rechazados por `identity_conflict` porque seguía mandando Predictive Preparations. Bajar .70 no arregla un rechazo posterior al reconocimiento.
- **AR solo**: una identidad diferente se aceptaba con `newCopy=false`; después aparecía `physical_confirmation` para la misma identidad sin ningún motor habilitado capaz de liberarla. La primera parte también podía reutilizar la fila previa. No reconstruir inventario automáticamente a partir de estas trazas: falta ground truth de las cartas físicas.
- **Filtros**: AR ampliaba `allowedSets` al paquete entero con un único set; OCR podía recurrir automáticamente al catálogo global. Ambas rutas contradicen un bloqueo explícito de edición.

### Contrato v12

1. Identidad canónica, impresión, idioma y copia física son decisiones separadas. Cambiar de impresión del mismo nombre no añade una copia.
2. Otra identidad fiable crea otra fila; una autoridad histórica no puede bloquearla. EdScan fuerte ya confirmado no requiere OCR adicional. AR estable no necesita conocer idioma por OCR.
3. Sólo una contradicción visual fresca capturada durante el candidato puede bloquearlo; OCR no veta una identificación visual fiable. Corregir una hipótesis AR sobre la misma captura exige que la muestra EdScan sea anterior a su aceptación, no una captura posterior de otra carta.
4. Conservar mínimo 600 ms entre nuevas copias, rechazo de muestras repetidas/caducadas, y rearme de idénticas sólo por ausencia sostenida. Ruido de apariencia no deja AR solo en espera perpetua.
5. Al desactivar un motor, borrar sus votos conservando la identidad ya añadida para no duplicarla. No conservar un veto de un motor apagado.
6. OCR puede acumular lecturas separadas hasta 6 s; cada captura individual sigue limitada a 2,5 s. No se acepta una captura antigua para alcanzar el consenso.
7. Todos los candidatos con edición conocida pasan por el mismo filtro de sets. El OCR no sale al catálogo global si se ha elegido edición. Un nombre aún sin impresión queda pendiente/revisable, no recibe otra edición a escondidas.
8. El idioma fijo se copia como elección manual a las filas de los tres motores. Un idioma desconocido del reconocedor no lo reemplaza.
9. Aceptar AR también termina la confirmación urgente: no seguir ejecutando EdScan continuamente porque discrepa la variante de una carta ya añadida.

### Validación sin operador

- `TripleLivenessRegressionTest`: patrones Loyal Tutor/Danitha, AR solo, motores apagados, OCR espaciado, filtros; siete combinaciones no vacías de motores × 500 cambios alternos sin perder filas; ráfaga 49 lecturas en 500 ms y variantes del mismo nombre sin copias nuevas.
- `TripleArbitrationIntegrationTest`: secuencias inyectadas en la Activity real del Sony, aceptación y número de filas, idioma EN fijo, filtros comunes, ausencia de bucle de auditoría; modo diagnóstico sin persistencia y SHA de bibliotecas antes/después.
- Mantener `TripleCollectionSessionTest` para persistencia aislada y restauración exacta de la sesión del usuario.
- Estas pruebas comprueban coordinación y progreso, **no** miden exactitud de los modelos ni latencia física sobre 500 cartas. No presentar el número de tests verdes como garantía de reconocimiento. La compactación del chat no cambia la APK; el problema demostrado estaba en reglas contradictorias del código y en pruebas que daban esas reglas por correctas.

### Resultado instalado v12

- Sony XQ-DQ54 `QV770HG2JD`: APK actualizada mediante `install -r`, sin borrar datos. 689 pruebas JVM, cero fallos; integración de colección y arbitraje en dispositivo: `OK (2 tests)`, 13,874 s.
- Restauración exacta comprobada: 26 filas del grupo `Triple 2026-10-09 15:20:37`, total verificado y reanudación preparada para próxima apertura. Biblioteca completa: 42 filas. Los 14 archivos de bibliotecas/catálogos y backups comprobados conservan SHA-256 idéntico al backup previo.
- OCR sin impresión compatible queda pendiente y no se persiste con una edición ajena. El test comprueba después la elección manual asíncrona y el guardado con idioma PT conservado.
- Evidencias locales: `build/triple-v12-audit/`; backup externo: `C:/Users/nubor/.codex/backups/mtgfucker/triple-v12-20261009-204222/`. No se ha medido una nueva sesión física: usuario ausente.


## Coordinación con captura fresca y evaluación adaptativa (v13 local, 9 octubre 2026)

**Estado: compilación local, NO instalada ni validada físicamente.** Los inventarios
26/42 de la sección v12 son históricos: el usuario ha seguido añadiendo cartas.
No detener la app ni instalar sin acuerdo nuevo. No desinstalar ni borrar datos.

### Contraste reproducido

- En `build/triple-v12-audit/live-lyra.log`, capturas 2284286658 y 2284291171,
  edades OCR 2229/2031 ms: ambas eran frescas. `startScene` por apariencia entre
  ellas borraba el primer voto. La prueba reproduce esos timestamps y el cambio
  de huella, no solo dos llamadas aisladas al árbitro.
- Hortimancer: .724315/.467151, luego .5480534 y .52722865. OCR consumió1169 ms,
  pero llegó con edad2893 ms porque EdScan ya había consumido tiempo de esa foto.
  La lectura antigua continúa siendo rechazada; no se rejuvenece su timestamp.
- `NativeCollectorVisionEngine` compara embeddings por UUID Scryfall, conserva8
  resultados por orientación y mezcla ambas. Top2 puede ser otra impresión del
  mismo nombre. FRA tenía461 filas de462 IDs solicitados. Un margen grande en un
  índice filtrado no demuestra que la carta física pertenezca a ese conjunto.

### Cambio de coordinación activo en esta compilación

- `TripleFrameScheduler`: un trabajo pesado por captura, sin cola de bitmaps.
  Cuando ambos están pendientes, alterna OCR/EdScan; una confirmación EdScan
  urgente no puede cancelar indefinidamente OCR. AR sigue por callbacks propios.
- OCR recibe una captura nueva y, sin quad AR, usa solo detección para rectificar:
  **no** embeddings ni búsqueda antes del OCR. `TripleOcrGeometry` toma la arista
  corta superior para cartas derechas (el detector puede elegir la inferior).
  Mantener pruebas físicas de giro, perspectiva y cartas boca abajo pendientes.
- OCR consulta su propio intervalo1200 ms. Los3000 ms de auditoría solo se usan
  cuando su último nombre coincide con el target AR ya aceptado; no por la mera
  existencia de una escena anterior. No espera al índice de nombres en el worker:
  durante preparación se omite esa lane y AR/EdScan continúan.
- `TripleOcrStageGate` conserva ownership hasta modelos y nombre completos. Una
  resolución de nombre caducada puede liberar la lane, pero nunca mientras sigue
  ejecutándose un modelo; callbacks tardíos no liberan una lane posterior.
- Apariencia conserva candidatos independientes por motor. Mantiene `sceneAt`
  como barrera contra callbacks capturados antes del cambio. Ausencia sostenida,
  cambios de configuración y pausa limpian evidencia; no duplican el último ID.
- Cada resultado sigue caducando a2500 ms. Continuidad de EdScan pasa de2500 a6000
  ms (igual OCR), para no perder una muestra por ceder CPU al OCR. EdScan activo
  sigue exigiendo dos lecturas individualmente .70/.08; un timestamp repetido o
  fuera de orden no cuenta ni rebobina la secuencia. Revalidación lenta se conserva.
- Se conservan fotos/revisión en AR solo, límites de copia, guardado por ID, idioma
  manual, multiedición, scope estricto, totales y galería. Sin nuevos textos UI.

### Política adaptativa explícita: solo sombra, no autoañadido

`TripleAdaptiveVisualPolicy` evalúa la **unión** de estas condiciones suficientes,
no intervalos excluyentes de score. Así aumentar score, margen o evidencia válida
no empeora la decisión:

| Score mínimo | Margen mínimo | Capturas distintas necesarias |
| --- | --- | --- |
| .55 | .30 | 5 |
| .60 | .20 | 3 |
| .70 | .08 | 2 |
| .85 | .25 | 1 |

Todos son **experimentales**, no probabilidades ni valores calibrados. Los
umbrales históricos .70/.08 tampoco tienen una tasa de acierto acreditada.
Ventana12s, hueco máximo6s entre muestras cualificadas y separación mínima600ms
para las condiciones de varias capturas. Una lectura débil es neutral, no añade
votos; otra identidad reinicia. No se reutilizan muestras repetidas ni no finitas.

La secuencia Lyra alcanza un candidato hipotético en la tercera muestra.
Hortimancer no: una .724 y dos inferiores a .55 no justifican una alta automática.
La política se ejecuta en sombra con `comparisonComplete=false` y
`authorized=false`; nunca llega a `offer`. El margen entre impresiones se conserva
como cota conservadora de separación entre nombres, sin eliminar variantes para
inventar un rival distante. No se sustituye runner ausente por cero.

**Antes de activar**: mapear resultados a identidad canónica con cobertura
comprobada; contrastar competidores fuera del scope sin autorizar edición ajena;
separar candidato de nombre de impresión resuelta y medir positivos/negativos.
Un índice global disponible tampoco prueba cobertura de todo MTG. Mientras no se
cumplan esas condiciones, las lecturas moderadas no autorizan altas por EdScan;
OCR/AR pueden confirmar independientemente. La corroboración no es obligatoria.

### Medición y validación

- `triple.lane`: captura elegida y cola+decodificación; `EdScanPerf triple.decode`
  y `triple.workerQueue` separan esas etapas. `triple.ocrGeometry`: detección sin
  inferencia. `triple.ocrStart`: edad al iniciar OCR. `triple.methodSample`: coste
  OCR por modelo. `triple.titleResolved`: resolución y edad total de captura.
- EdScan mantiene detect/infer/feature/search y revalidación. Arbitraje y alta UI
  tienen mediciones `triple.arbitration`/`triple.acceptUi` en EdScanPerf. Persistencia
  mantiene su diagnóstico independiente. `triple.feedback` añade captura→petición
  de pitido; esto **no** mide el instante acústico ni entrada física de la carta.
- `triple.adaptiveShadow`: hipótesis, contadores, cobertura de scope y razón de
  bloqueo. No confundir `scopeCovered` con cobertura global o precisión.
- JVM: `TripleFreshFrameRegressionTest`, `TripleAdaptiveVisualPolicyTest`, suite
  anterior de siete modos/variantes/ráfagas/reanudación y demás app/native.
  706 pruebas JVM (656 app+50 native),0 fallos;3 pruebas Python correctas.
- APK e instrumental compilados con Java17. No se han ejecutado nuevas pruebas
  instrumentadas porque interrumpirían la sesión actual. No se ha medido una
  mejora física, carga térmica, FPS ni porcentaje de aciertos de esta revisión.
- Pendiente tras acordar parada: backup consistente de sesión ACTUAL (Legacy,
  preferencias, Room+WAL/SHM, fotos), hashes y recuentos actuales; `install -r`,
  verificación de restauración sin altas, integraciones aisladas y prueba física
  equivalente de los7modos. Registrar ground truth de nombre/impresión/copia,
  falsos positivos/negativos, duplicados y p50/p95 por etapa; incluir fuera-de-set,
  variantes, foil/reflejos, alternancia, retirada/reintroducción y giros.
- Evidencias locales: `build/triple-v13-audit/`, incluida copia de los archivos
  afectados antes del cambio para separar modificaciones previas sin commit.
