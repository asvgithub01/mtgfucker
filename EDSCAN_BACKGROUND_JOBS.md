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
