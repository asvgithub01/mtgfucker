# EdScan: trabajo persistente y fotos

Checkpoint anterior: `codex/edscan-checkpoint`, commit `51d82d4` (origin).
Evolución: `codex/edscan-durable-jobs`.

## Qué funciona
- Host se prepara/inicia automáticamente. Botón preparar solo aparece si falla como reintento.
- Preview encima de miniatura/precio. Nombre EdScan, sin introducción técnica.
- Un pitido al aceptar; precios no bloquean ni aceptación ni guardado.
- Flag fotos apagado por defecto. Una foto por aceptación, independiente de mostrar miniatura.
- Foto privada rectificada 744×1040 JPEG95, UUID exacta, acabado, fecha y enlace a library/collectionItemId/printingUuid tras guardado correcto.
- `files/edscan/`: `.jpg`, `.json` (entrada), `.link.json` (asociación), `.ocr.json` (resultado).
- WorkManager + CoroutineWorker: precio UUID único, red requerida, reintento exponencial y caché24h. OCR sin condición de red, modelos empaquetados latín/chino/japonés/coreano y clasificación de idioma.
- Un OCR activo por proceso para evitar ejecutar cuatro modelos para cada carta simultáneamente; cuatro lecturas secuenciales, texto y candidatos conservados.
- Las fotos duraderas sin resultado se reencolan al abrir EdScan; resultados finalizados son idempotentes. Las consultas de precio pendientes viven en WorkManager aunque se cierre Activity.
- Otras vistas de cartas también 2×2 horizontal. Swipe no borra. Botón papelera requiere mantener1000ms, cancelar al soltar/mover/desacoplar/rebind; después pide confirmación. Identidad por collectionItemId.

## Límites explícitos
- Resultado de idioma **review_required**, no modifica Biblio ni el idioma manual. Faltan pantalla de revisión y política de confianza/alias por impresión para autoaplicar con seguridad.
- Ruso/cirílico: pendiente otro motor; no afirmar que MLKit Latin lee ruso. Chino simplificado/tradicional no se asigna automáticamente; candidatos no son prueba del idioma físico.
- Fotografía/compresión en coroutine IO, no bloquear cámara. La persistencia empieza al terminar escritura atómica: una muerte de proceso antes puede perder esa captura aún en memoria. Una foto ya escrita se recupera aunque el cierre ocurra antes de enqueue. No se promete ejecución continua bajo force-stop de Android; reanuda al abrir.
- Foto puede contener texto invertido: OCR actualmente orientación0; falta probar/añadir reintento180. Guardamos fotografía para reprocesarla sin volver a escanear.
- No hay limpieza automática de fotos ni botón para ver cola todavía. Se conservan privadas localmente, no se suben. Controlar espacio durante pruebas largas. Modelos bundled aumentan APK.
- Cola es de precio/OCR, no hace persistente el autoañadido a Biblio si lookup de impresión falla offline.
- Precio mostrado Scryfall puede diferir del proveedor de precios Biblio; respeta acabado y moneda al mostrar.

## Qué se reutiliza de CollectorVision
Contrato de preprocesado, Cornelius2.12 (bordes), Milo1 (embedding128), catálogo109711vectores y búsqueda de similitud, portados a Kotlin/ONNX Runtime/OpenCV. No servidor de inferencia ni backend CollectorVision. Cámara, ciclo de vida, UI, cola y Biblio Android son integración propia. Modelos/catálogo se descargan inicial; después inferencia local. Precios/nombres usan Scryfall. Licencia AGPL y procedencia: ver módulo NOTICE.md.

## Pruebas manuales
1. Abrir EdScan sin pulsar preparar. Probar foto on con miniatura off y viceversa.
2. Escanear latín, japonés, coreano y chino; revisar los resultados OCR antes de decidir automatización.
3. Sin red, reconocer y dejar precio pendiente; cerrar/reabrir y reconectar. Debe completar sin reconocimiento nuevo.
4. Mantener papelera <1s no hace nada; >1s abre confirmación. Cancelar no cambia cantidades; desplazamientohorizontal nunca borra.
5. Probar todas las visualizaciones y retorno del detalle.

## Validación de esta entrega
- 427 pruebas JVM sin fallos; 10 instrumentales Sony OK (13,588 s).
- Instrumentales incluyen foto sintética, ejecución real cuatro modelos OCR en WorkManager, resultado idempotente y pulsación corta/larga; sin insertar cartas en Biblio.
- Compilación e instalación incremental en Sony QV770HG2JD correctas.
- Pendiente prueba física offline/reconexión/muerte de proceso, arranque automático y precisión con cartas de cada idioma. No se afirma rendimiento ni precisión de idioma validados todavía.

## Diagnóstico y cartas valiosas
- `files/edscan/debug/<scanId>.accepted.json`: cada autoaceptación con UUID, modo rápido/filtrado, regla exacta, número lecturas, umbrales y últimas8lecturas con candidatos/puntuaciones, esquinas, presencia, nitidez, tiempos, tamaño/rotación y bloqueos anteriores. No cambia umbrales.
- `.request.json`: biblioteca, idioma/acabado seleccionados y clave de foto opcional. `.stored.json`: collectionItemId, printingUuid, nombre, set, número e idioma/acabado/grupo guardados. `.outcome.json`: éxito/fallo y duración. `.error.json`: impresión/acabado no disponible. `.price.json`: precioEUR fuenteScryfall. Archivos privados; no se suben. No reconstruye scans antiguos. Mantener flagfoto para revisar imagen también.
- Efecto compartido PriceCelebration, confeti1800ms y sonido sintetizado propio800ms al conocer importe unitario estrictamente>10EUR. OCR convierte con PriceCurrency; EdScan usa campoEUR exacto del acabado (sin sustituir foil por nonfoil). No suena en segundo plano. Tokens deduplicados500 y limpieza del overlay/audio al salir. Precio ausente no dispara.
- Arte1columna:1×2; arte2columnas:2×3; completas:2×2, todos horizontales. Precio arte en banda oscura; lista clásica retirada ymigrada. Papelera inline encima nombre; rebindmismaID no cancela la pulsación1s, diferenteID sí.

## Prioridad de cámara (OCR y EdScan)
`EdScanInferencePriority` pospone nuevos pases de idioma/verificación mientras EdScan o CameraSource OCR estén activos. Una operación ya iniciada termina; no es preempción. OCR libera al detener cámara y la Biblio no debe mantener una cámara invisible: `showRecycler` detiene preview y `startCameraSource` exige lector visible.
La actualización masiva de precios usa executor propio de prioridad background y espera antes del índice y de cada lote de 75, sin ocupar la cola de consultas de cartas. Espera interrumpible al cerrar Activity. La consulta de precio de una carta y su guardado no esperan este gate. Los jobs persistentes no se cancelan ni se borran.
Pendiente medir con cola real en el Sony; no implica que todos los procesos background estén ya limitados ni que desaparezca la latencia de red/guardado.

## Guardado, acabado y precios (28/09/2026)
- `EdScanSavePolicy`: si se solicita nonfoil y la impresión exacta solo admite foil, guardar foil. Si admite ambos, respetar selección. No inferir foil por similitud visual, ni convertir etched automáticamente.
- Casos persistentes bajo el scanner: 1 catálogo/consulta exacta; 2 acabado no disponible; 3 candidato `_back`. Este sufijo designa una cara trasera del índice; normalización y validación de dobles caras **pendientes**, no recortar el sufijo a ciegas.
- MTGJSON: SetList TTL24h (consultado al iniciar), contenido de set TTL7d, AtomicCards nombres TTL30d. Si el UUID visual falta, refrescar solo su set, limitado a una vez por15min por proceso. No significa actualizar índices visuales ni descargar todo MTGJSON al inicio.
- El precio de la UI nativa se guardaba solo como JSON de caché y no en biblioteca. `EdScanStoredPriceWorker` en app ahora se encola tras persistir la carta y reutiliza la descarga durable existente: precio de UUID+acabado+moneda exactos a Room y campos de precio legacy. Reintentos WorkManager tras falta de archivo/red; no retrasa la aceptación. Null no se transforma en cero. Guarda con comprobación de item/UUID/acabado para no cambiar una edición editada después. La tira usa PriceCurrency.format y se refresca al terminar.
- No se reescriben automáticamente escaneos históricos: usar Actualizar precios en el menú de Biblio. Prueba física pendiente con foil-only, biblioteca al cerrar scanner y trabajo interrumpido.

## Feedback de copias y umbral de confeti (28/09/2026)
- Ajuste solicitado del rápido moderado: dos lecturas del mismo UUID >=0,55,
  margen >=0,04. Fuerte sigue >=0,80/margen0,08; seguro no cambia. No acelera la
  inferencia: puede reducir espera de aceptación a costa de más falsos positivos.
- EdScan usa `mergeExisting=false` al guardar una copia: nuevo collectionItemId,
  cantidad1, mismo grupo Cornelius. No transforma filas previas ni cambia el OCR.
  La retirada de carta para rearmar sigue siendo necesaria para evitar duplicados.
- Con guardar fotos activo, una miniatura rectificada160x224 de la captura aceptada
  ocupa el pendiente y después el placeholder/error hasta cargar Scryfall.
  Se conservan como máximo30 miniaturas; no hay compresión JPEG/red en UI.
- Botón Confeti en EdScan y ajustes OCR abre slider compartido persistente:
  0,50–20€ en pasos0,50; predeterminado10€. Se dispara al SUPERAR el precioEUR,
  no al igualarlo. Cancelar no guarda. Nuevos niveles de fiesta quedan pendientes.
- Build y pruebas JVM correctos; Sony no conectado durante esta validación,
  instalación y comprobación visual pendientes.

## Total, orientación, precio local y diagnóstico de latencia (28/09/2026)
- Total EdScan sobre toda la sesión, no solo las últimas30miniaturas: copias,
  importe conocido por cantidad/condición/moneda y copias sin precio. Se recalcula
  tras guardar, editar/borrar y recibir precios pendientes.
- Se corrige el descarte de precios en loadCollectorVisionPrinting: snapshotRoom
  y preciosRoom por UUID/acabado, sin preparar/descargar AllPrices. Lookupvisual
  usa executorpropio; precio local aparece y se guarda desde el inicio. Redsolo
  como fallback si falta; JSONScryfall guardado se reutiliza sin esperarWorkManager.
  nonfoil se normaliza a normal en CardPriceEntity; combine admite filas legacy.
- Galería: pendiente→guardada es una sola actualización anterior a liberar el gate.
  Los refrescos de disco antiguos se descartan por generación; sin ItemAnimator.
- Motor ya comparaba normal+180. Ahora devuelve esquinas de orientación ganadora
  para nuevas fotos y miniaturas. No cambia rankings ni modelos. Fotos anteriores
  no se alteran. Deja de copiar bitmapcompleto mientras hay guardado pendiente o
  la misma carta guardada; no baja resolución de inferencia.
- Ajustar: similitud0,50–0,95; margen0–0,20; checkdoslecturas. Pasos0,01. Persisten
  y soloafectan rápidofiltrosOFF; defaults0,55/0,04/2. Fuerte0,80/0,08 sigue inmediato
  si cumple mínimosconfigurados. Resetdisponible. Bajarlos aumenta falsospositivos.
- Recuperación de fotos, calentamiento de precios y autosynccloud esperanidle.
  Hilo de indexadoprecios con prioridadbackground. Una tarea ya iniciada no se
  interrumpe; no afirmar que desapareció toda contención sin nueva prueba física.
- DataUtils ahora usa BufferedInput/OutputStream conservando formato/backup/rename.
  Benchmark Sony sobre1317filas/2011167bytes: sinbufferlectura418/330ms, escritura
  150/156ms; conbufferlectura48/48ms, escritura56/59ms; salidas byteidénticas.
  Prueba no modifica inventario. Lookup precio localKarn41ms en el testaislado.
- Audit privado build/edscan-current-audit: últimosescaneos muestran4–12s desde
  reconocimiento a confirmación de guardado; ejemplos inferenciacompleta~0,55–2,36s.
  No confundir tiempos de reconocimiento con espera de guardar. Karncapturado
  pierde la parte superior; SolitaryCell completo. Recorte sigue pendiente de
  validar, no atribuir toda confusión a foil ni a fotos invertidas.
- Debugstored incorpora lookupMs/queueMs/localSaveMs/selectionMs/localPrice.
  Nueva evidencia .frames.json conserva últimas8observaciones incluidosrechazos,
  reemplazada cada3s; logcatEdScanFrame cada2s. photoRotation visibleendiagnóstico.
- Pruebas en Sony: EdScanFeedbackDeviceTest (galería, precio local y benchmark)
  3/3correctas. Pendiente prueba física de nueva versión para medir mejora total.

## Duración activa y Call for Unity (28/09/2026)
- Sesión muestra mm:ss junto a copias/precio. Cuenta desde cámara vinculada;
  pausa al salir, acumula al volver, conserva tiempo en recreación medianteBundle.
  No cuenta preparaciónmodelos ni background. Tick1s actualiza solo el texto.
- Call for Unity AER9, PAER9s y LTC163 están en el índice base. AuditScryfall/API
  y Milo local en build/call-for-unity (privado). AER referencia top1 .9991435,
  promo .9796714: margen .0194721 < .04. Promo referencia .9983304 vs AER .9718473:
  margen .0264831 < .04. LTC distintoarte margen .4019448, no bloqueado.
- Esto demuestra que el filtro de impresión puede bloquear una carta presente,
  no identifica edición/idioma del ejemplar físico del usuario (preguntados).
  No se altera catálogo ni umbrales globales. Margen .01 puede servir para prueba
  controlada AER, verificando que no se guarde como promo incorrecta. Pendiente
  solución de ambigüedad a nivel de impresión sin inventar certeza.
- Build y495testsJVM correctos; reloj y regresiónAER/promocubiertos. Sony no conectado
  en esta validación; instalación y prueba física pendientes.

## Aviso de margen insuficiente (28/09/2026)
Usuario confirma Call for Unity AER en español. Se añade explicación visible
(no toast) mientras candidatos cumplen score pero no margen: impresión sin
confirmar, no guardada, margen observado/mínimo. No atribuye identidad común sin
metadata ni relaja umbrales. No se muestra para carta ausente/guardada/pendiente.
Siguiente paso propuesto, NO implementado: distinguir carta de impresión, agrupar
por identidad/arte comprobados y ofrecer selección normal/promo cuando corresponda.
Acabado e idioma deben seguir separados; no deducir foil por score visual global.

## Guardado provisional por identidad de carta (28/09/2026)
- Sustituye el plan anterior de bloquear/seleccionar durante la cámara: el usuario
  autoriza guardar la mejor impresión y revisarla después si la carta es la misma.
- Candidatos cercanos deben tener nombre canónico exacto confirmado (no OCR/fuzzy),
  score mínimo, y separación frente a la cola del ranking. Dos lecturas mínimas en
  rápido, tres en seguro; estabilidad por identidad aunque alterne normal/promo.
- Se devuelven ocho candidatos (mismos dos pases neuronales) para cubrir varias
  reimpresiones. Si la cola también está dentro del margen, falta identidad o hay
  otra carta cercana, no se acepta provisionalmente. No rebajar margen global.
- Identidades se consultan asíncronamente: Room, JSON local y Scryfall si falta.
  Una consulta por UUID/sesión, cola limitada a16, sin límite total de cartas de
  sesión. No red ni Room en el hilo de inferencia. Los fallos reintentan otra sesión.
- Marcador durable `<scanId>.provisional.json` por biblioteca/item, con candidatos
  y referencia de captura. Funciona sin fotos. Postprocesado muestra conflicto de
  edición provisional, independiente del idioma. Se corrige con selector existente
  o se confirma la impresión actual. No hay propuesta de cambio automática por lote.
- Bloqueo por identidad impide duplicados si cambia el ganador entre ediciones antes
  de retirar la carta; conserva bloqueos de guardado/fallo. No cambia cartas anteriores.
