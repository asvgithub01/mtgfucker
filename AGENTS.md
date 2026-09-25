# Guía rápida para agentes

## Cómo empezar sin recorrer todo el proyecto

- Responder en español y hacer el cambio mínimo que resuelva la petición.
- Todo texto fijo visible nuevo debe vivir en recursos y traducirse en `values` (español),
  `values-en`, `values-de`, `values-fr`, `values-pt` y `values-it`. La única excepción son
  los textos de depuración de los escáneres (`scan_debug_*`,
  `scan_edition_debug_*` y diagnósticos OCR marcados `translatable="false"`), que se
  mantienen solo en español. Marcar de igual modo cualquier texto nuevo exclusivo de debug.
- Consultar `git status --short` y la rama actual antes de editar. No sobrescribir ni
  incluir en commits cambios ajenos, especialmente `.idea/` y `*.iml`.
- Cuando se trabaje desde una rama distinta de `master`, mostrar el nombre de la rama en
  la splash screen para identificar claramente la APK instalada.
- Leer esta guía y abrir únicamente los archivos del área afectada. Usar `rg` para
  localizar métodos; no empezar leyendo todo `OcrCaptureActivity.java` ni los backups.
- Si Engram está disponible, consultar `mem_context` del proyecto `mtgfucker` y buscar
  antecedentes concretos. Guardar decisiones, descubrimientos y resumen al finalizar.
- Actualizar esta guía cuando cambien los puntos de entrada, los comandos o las
  precauciones importantes. No convertirla en un historial de cada tarea.

## Stack y estructura

- Aplicación Android nativa, Java + Kotlin; interfaz principalmente XML/Views, con Compose
  también habilitado. Módulos Gradle: `app`, `liquid-glass` y `collectorvision-native`.
- CollectorVision nativo vive aislado en `collectorvision-native/`: CameraX, Cornelius,
  Milo y catálogo ONNX CPU. EdScan y su cola persistente están documentados en `EDSCAN_BACKGROUND_JOBS.md` (WorkManager, fotos privadas y OCR revisable; ruso pendiente). Su botón abre el host `CorneliusScanActivity`; el laboratorio
  WebView previo ya no es la ruta activa. Ver `COLLECTORVISION_MOBILE_PLAN.md` y el
  `NOTICE.md` del módulo antes de distribuir. No conectar autoañadido sin validar
  impresión/idioma/acabado; no quitar los SHA de assets para resolver una descarga fallida.
- Java 17, Kotlin 1.9.22, Gradle 8.5, Android Gradle Plugin 8.2.2.
- SDK de compilación 34, mínimo 23, target 33; comprobar `app/build.gradle` si cambian.
- Paquete y applicationId: `io.asv.mtgocr.ocrreader`.
- En el mapa siguiente, los archivos de código son relativos a
  `app/src/main/java/io/asv/mtgocr/ocrreader/`.
- Recursos: `app/src/main/res/`; pruebas locales: `app/src/test/java/io/asv/mtgocr/ocrreader/`.
- `projectFilesBackup/` y `projectFilesBackup1/` no son los módulos activos.

## Mapa por tarea

| Área | Archivos de entrada |
| --- | --- |
| CollectorVision nativo aislado | Host `CorneliusScanActivity.kt` guarda en grupos Cornelius N mediante UUID exacta. Módulo `collectorvision-native/`: `NativeCollectorVisionActivity.kt`, `NativeCollectorVisionEngine.kt`, `CollectorVisionAssets.kt`, `CollectorVisionAcceptanceGate.kt`; preview separado del análisis, sonido solo al reconocer; flags persistentes de filtros/rápido y miniatura; precio Scryfall asíncrono por UUID/acabado. Demo WebView sin entrada activa |
| Scanner, navegación, colección y sesión | `OcrCaptureActivity.java`; layout `ocr_capture.xml` |
| Nuevo escáner automático de edición | `ExperimentalCardScanActivity.kt`, `OpenCvCardDetector.kt`, `AutoCaptureStability.kt`, `SetSymbolShapeMatcher.kt`, `PrintingLineOcr.kt`, `CardTitleOcr.kt`; layout `activity_experimental_card_scan.xml` |
| Símbolo V2 del laboratorio hash | `SetSymbolHashMatcher.kt`, `SetSymbolHashPolicy.kt`, `PrintedSetSymbolPolicy.kt`, `HashSymbolPrintingEvidence.kt`, `SymbolRetryPolicy.kt`, `SetSymbolSvgSource.kt`, `SymbolScanDiagnostics.kt`; flag persistente `symbol_v2`, apagado por defecto. Últimos 30 intentos privados en `files/symbol_scan_v2/`. |
| Hash con OCR/símbolo opcionales | `HashScanAnalysis.kt`, `HashScanEvidence.kt`, `HashPrintingVariantPolicy.kt`; controles, sesión y metadatos de captura en `RapidEditionScanActivity.kt`/`activity_rapid_edition_scan.xml`. Ver `ART_HASH_EVALUATION.md`; no atribuir al hash una edición exacta. |
| OCR primero y edición con popup | Flag persistente `edition_picker`, apagado por defecto; `HashEditionResolutionPolicy.kt`, `HashEditionGridPanel.kt` y `resolveHashEdition` en `RapidEditionScanActivity.kt`. Fuerza OCR, conserva cámara no-HD y analiza la misma foto; símbolo V2 es opcional. |
| Edición probable y autoañadido | Flag `probable_edition`, apagado por defecto; `HashProbableEditionPolicy.kt`. Estimación explícita: prevalece sobre popup/reintentos y autoriza añadir sin `auto_add_first`. No cambia cámara ni fuerza OCR; conserva protección de identidad y contradicciones. |
| Copias consecutivas en laboratorio hash | `RepeatedScanCopies.kt`, `ScanCopiesPanel.kt`; segunda detección pausa cámara y edita total del lote, incluida la primera ya guardada. Guardado por `LegacyCollectionStore.addCopy(..., copies)`. |
| Laboratorio de identificación por reglas | `RulesScanActivity.kt`, `RulesScanPolicy.kt`, `RulesAutoAddPolicy.kt`, `StructuredPrintingEvidence.kt`, `HistoricalFooterEvidence.kt`, `RulesScanReport.kt`, `RulesScanReview.kt`; cuarto FAB en `ocr_capture.xml`. Ver `RULES_SCANNER_PROGRESS.md`. Reutiliza captura/sesión. Auto propio `auto_unique_art` en `rules_scanner`: arte fuerte+margen, arte exclusivo o pie completo corroborado en2familiasROI, idioma observado y acabado del checkFoil existente. La ruta de pie exige tipos de set soportados y bloquea PLST/MB1/FMB1/MB2/promos antes de filtrar; perfiles desconocidos van a revisión. Ver `RULES_SCANNER_TEST_PLAN.md`. No usa autoañadido/edición retenida del hash. |
| Prueba de escáner solo por hash | `HashOnlyScanActivity.kt` reutiliza la captura/rectificación de `RapidEditionScanActivity.kt`, salta OCR/red y ejecuta `ArtHashMatcher.kt`/`ArtHashIndex.kt`; tercer FAB en `ocr_capture.xml` |
| Identificación experimental por arte | `ArtHashIndex.kt`, `ArtHashMatcher.kt`, `ArtPrintingIndex.kt`, `RapidEditionScanActivity.kt`; índices APK `art_hash_index.bin`/`art_printing_index.bin`, generadores `scripts/build_art_hash_asset.py`/`scripts/build_art_printing_asset.py`, prueba Sony `app/src/androidTest/java/io/asv/mtgocr/ocrreader/ArtHashDeviceEvaluationTest.kt`; corpus y muestras fuera de Git en `../mtgfucker-art-hash-data/` |
| OCR y cámara | `ScanLanguagePolicy.kt`, `CardTextLanguageDetector.kt`, `MlKitOcrDetectorProcessor.java`, `MlKitTextDetector.java`, `CardScanStability.kt`, `ScannerSettings.kt`, `ui/camera/` |
| Filas y total de sesión | `ScanSessionAdapter.java`, `ScanSessionCounts.kt`, `ScanSessionSort.kt`, `ScanSessionRefreshCoordinator.kt`; layout `scan_session_item.xml` |
| Detalle de carta y ediciones | `Main2Activity.kt` (incluye `EditionAdapter`), `ScanEvidencePanel.kt`/`ScanEvidenceFormatter.kt` para foto e historial debug; layouts `activity_main2.xml`, `edition_item.xml` |
| Buscador compartido de ediciones | `EditionSearchAdapter.kt` (incluye política `EditionSearch`), `EditionPicker.kt`, `SetSymbolLoader.kt` |
| Corrección de edición en sesión | `EditionPicker.showGrid`, `EditionGridContent.kt`, `EditionGridPolicy.kt` y `HashEditionGridPanel.kt`; sesiones OCR y rápida/hash. Siempre disponible, sin depender de `edition_picker`. Preservar ID/cantidad/idioma y actualizar Room + legacy; en rápida actualizar también las referencias de deshacer. |
| Carga de imágenes y placeholder | `CardImageCache.kt`, `CardLoadingDrawable.kt`, `SetSymbolLoader.kt`; cargas de fotos en `PhotoScan*` |
| Catálogo de sets | `MagicSetCatalogAdapter.kt`, `SetCollectionActivity.kt` |
| Escaneo específico de edición/fotos | `EditionScanActivity.kt`, `PhotoScanDetailActivity.kt`, `PhotoScanStore.kt` |
| Catálogo, precios e imágenes | `data/CardRepository.kt`, proveedores `data/MtgJson*` y `data/Scryfall*`, `data/CardDatabase.kt` |
| Colección persistida | `model/CardInfo.java`, `model/Biblio.java`, `DataUtils.java`, `LibraryCatalog.kt`, `data/LegacyCollectionStore.kt` |
| Exportación de venta Cardmarket | `data/CardmarketCsvExporter.kt`, `data/PriceCurrency.kt`; botón y guardado en `OcrCaptureActivity.java`; consultar `CARDMARKET_EXPORT.md` |
| Web y extensión Cardmarket | `web/`, `extension/`, protocolo compartido `packages/cardmarket-protocol/`; consultar `CARDMARKET_EXPORT.md` |
| Idioma, acabado, condición y moneda | `data/CardLanguage.kt`, `CardFinish.kt`, `model/CardCondition.kt`, `data/PriceCurrency.kt` |
| Sincronización cloud | `CloudLibrarySync.kt`, `CloudAccountActivity.kt`; el backfill Cardmarket vive en `data/CardRepository.kt`; consultar `FIREBASE_SETUP.md` |

Documentación adicional, solo cuando sea relevante: `DATA_PROVIDERS.md` para datos y
`SCANNER_ROADMAP.md` para planes futuros. Contrastar con el código: el roadmap no implica
funcionalidad implementada y las notas antiguas sobre OCR pueden estar desactualizadas.

## Experimento OCR de calidad

- `OcrImageEnhancement.kt` añade CLAHE solo a OCR; no aplicarlo a hashes. `OcrCaptureQuality.kt` registra nitidez sin inferir oclusión.
- `ScannerSettings.enhancedOcr`/`enhanced_ocr_experiment` está OFF por defecto para el scanner original; control en `scanActions` de su pantalla de cámara (no en Ajustes). OFF debe conservar `OcrLumaEnhancer.enhance` y resolución solicitada1280×1024; ON usa `enhanceExperimental` y1600×1280. Mantener reglas/máscara/UV; prueba `OcrLumaExperimentDeviceTest` cubre4rotaciones.
- Rules usa1600×1200 solicitado, rectificado945×1320 y hash normalizado630×880. Otros modos no cambian. PieCLAHE7/8 no son familias nuevas;9/10 recorte estrecho. Ver `RULES_SCANNER_TEST_PLAN.md` para A/B físico pendiente.

- `EditionPicker` muestra ediciones locales antes de precios (`deliverEditionsBeforePrices`); conservar resultados ante errores posteriores y callbacks cancelados al cerrar.
- `StructuredPrintingEvidence.PARTIAL` retiene año/fracción histórica sin inventar set/idioma. ZoomCameraX trasero enRapid/Hash/Rules pausa análisis durante gesto/600ms y reinicia estabilidad enexecutor cámara.

- ZoomCameraX persiste ratio porActivity/trasera en `camera_zoom`; no guardar el1x inicial delobserver. Restaurar antes de analizar y descartar callbacks de binds anteriores.
- `RulesRetryPolicy`: máximo3capturas live+auto recuperables, jamáscombinar evidencias entre fotos ni rebajar umbrales. Solo `returnToCamera(preserveRulesRetry=true)` conserva contador; no confundirconSymbolRetryPolicylegacy.

## Invariantes que no se deben romper

- Una fila se identifica por `collectionItemId`, no por la posición de una lista que puede
  ordenarse o cambiar mientras llega una respuesta asíncrona.
- Impresión (`printingUuid`), acabado (`nonfoil`, `foil`, `etched`), idioma, condición y
  cantidad son conceptos diferentes. Cambiar edición no debe perder cantidades ni grupos.
- Un precio corresponde a una impresión y acabado concretos. Al cambiar acabado, limpiar
  el precio anterior; antes de aplicar respuestas tardías, comprobar UUID y acabado actuales.
- La selección de edición vive en Room y la colección también utiliza persistencia legacy.
  Seguir las rutas existentes de `CardRepository.selectEdition` y actualización de colección;
  no actualizar solo una de las dos representaciones.
- En símbolo V2, separar silueta externa rellena y apariencia interior gris: usar solo
  el contorno hace indistinguibles M14/M15. El contraste interior admite polaridad
  inversa en plata/oro. Comparar todos los sets sobre el mismo glifo localizado.
  La banda x=.60–.995, y=.43–.71 admite símbolos anchos (hasta 140×80 a ancho 744).
  Conservar el frame completo para «Corregir bordes»; al reanalizar salir de live-result.
  Los encuadres de arte modernos y futurista (excluye maná lateral de Future Sight) se
  prueban automáticamente, también sin OCR/símbolo. No acoplar geometría del arte al flag V2;
  este sigue controlando el matcher y la resolución de rectificación, no la cámara ni las plantillas de arte.
  Detalle interior requiere soporte >=.10 salvo siluetas confundibles; no volver a dar
  peso dominante a agujeros diminutos de símbolos únicos como TMP.
- `SymbolResolutionPolicy` conserva carta V2 a 1260×1760 y segmentación a ancho 1488;
  entradas antiguas menores de 1000px conservan ancho 744. Escalar también kernels y geometría.
  CameraX usa siempre `setTargetResolution(1280×960)`, igual que OCR sin V2; activar
  símbolo no reenlaza la cámara ni cambia encuadre. Comprobar resolución efectiva; no asumir la solicitada.
- No bloquear la cámara con red, indexado o guardados pesados. Reutilizar ejecutores y los
  métodos de persistencia existentes; respetar el ciclo de vida de Activity y los callbacks.
- Antes de publicar la proyección web, `CloudLibrarySync` completa una sola vez los IDs de
  Cardmarket antiguos por UUID exacto de Scryfall y refresca los IDs de set desde MTGJSON. No
  volver a emparejar por nombre ni omitir el guardado local que fuerza una nueva copia cloud.
- Los lotes web de Cardmarket se separan por edición y por la página que ocupa cada producto en
  el catálogo completo de MTGJSON ordenado por nombre inglés (100 productos por página), que es
  el orden efectivo del DOM de BulkListing aunque su filtro visual sugiera collector number. No
  volver a partir cada 100 cartas seleccionadas: productos válidos pueden quedar fuera del DOM.
- El bloqueo de sets del scanner admite varios códigos. El autocompletado usa comas y el
  parser existente admite comas/espacios. No reducirlo accidentalmente a una sola edición.
- La búsqueda compartida interpreta MAYÚSCULAS como código; los nombres se comparan sin
  acentos. La sesión ya tiene check de foil: reutilizarlo, no duplicarlo.
- Conservar el nombre impreso del OCR al enriquecer una impresión; el nombre canónico inglés
  se usa para consultar el catálogo, no para reemplazar el nombre localizado de sesión.
- Un alias de título puede ser idéntico en ES y PT. El scanner conserva la región de reglas
  en `OcrLumaEnhancer`, la extrae sin mezclarla con candidatos de título y clasifica el idioma
  una vez por lectura aceptada. `ScanLanguagePolicy` cubre confianza y habilidades de maná
  cortas. No deducir idioma físico de la edición ni cambiar masivamente cartas PT existentes.
- El modo lento devuelve UUID, acabado e idioma; no volver a elegir la primera carta del set,
  porque puede tener otro arte. Los submenús muestran miniaturas por impresión.
- El autoañadido por hash usa el primer candidato de arte cuyo nombre coincida con OCR, aunque sea
  el resultado 2 o 3. Si ninguno coincide, muestra la confusión y reanuda el autoscan. Nunca
  preseleccionar automáticamente LEA, LEB, ARN, ATQ, LEG ni DRK; elegir otra impresión segura.
- Dos conflictos consecutivos con el mismo nombre OCR confirman la identidad por OCR y permiten
  autoañadirla con una impresión segura; un nombre distinto reinicia este consenso temporal.
- El laboratorio hash inicia el matching desde un frame live rectificado antes del JPEG. No
  volver a introducir captura/decodificación/corrección en su camino normal; la foto queda como
  fallback. El hash solo identifica arte; la impresión local se resuelve después filtrando las
  variantes por idioma/borde o por nombre OCR+código+collector. Ante conflicto no autoañadir.
- Antes del hash, el quad debe respetar aproximadamente el aspect ratio físico 63:88. No volver
  a ampliar el antiguo rango 0,54–0,85: los recortes demasiado altos desplazan el arte y generan
  falsos candidatos aunque pHash/dHash funcionen correctamente.
- Al cambiar URL/impresión, mostrar `CardLoadingDrawable` en lugar del arte anterior; conservar
  la imagen solo en rebinds de la misma URL. Mientras se resuelve imagen localizada, cancelar
  la carga vieja y proteger callbacks con generación+UUID. Un error termina el loading.
- Los controles superiores del scanner se posicionan con `OcrTitleRegion.cardForFrame`.
  Mantener sincronizada esta geometría con la cámara y el overlay; comprobar en pantalla
  pequeña/teclado visible. La sesión ocupa todo el ancho y conserva padding interno.
- El borrado desde sesión, incluido reducir la última copia, requiere confirmación.
- Con `symbol_v2` activo, comparar símbolos solo de la identidad elegida por OCR+arte. Sin `edition_picker`, V2
  sustituye V1; no volver al candidato indexado ni al consenso OCR si el símbolo no resuelve.
  Exigir distancia absoluta, referencias completas y un único UUID compatible; con varios
  sets exigir también margen. Un único set no confirma visualmente el símbolo, pero `uniqueArtwork` permite resolver
  sin él si el catálogo completo del arte tiene un solo set y una impresión normal compatible.
  Exigir identidad OCR coincidente o hash fuerte (p≤10/d≤16); no convertir top1 débil en certeza.
  Conservar exclusiones históricas.
  Símbolos reutilizados (incluido Chronicles) no prueban edición. No subir fotos de diagnóstico a Git.
  Dentro del set confirmado, preferir UUID con acabado nonfoil (9ED separa normal/foil);
  si quedan varios UUID normales, no elegir arbitrariamente. OCR apagado en V2 usa EN.
  No asumir que el SVG se llama como el set: ante 404 usar `icon_svg_uri` de Scryfall.
  PLST conserva el símbolo del set original del collector (`ZEN-193` → ZEN); no comparar
  el logo del catálogo The List ni excluir esa alternativa de una posible ambigüedad.
- Con `edition_picker`, solo una identidad coincidente entre OCR y arte permite resolver edición.
  Código+número estructurados únicos o arte reconocido de edición única pueden resolverla sin símbolo;
  el año por sí solo no basta. Comprobar unicidad antes de filtrar idioma/borde y conservar contradicciones.
  Ante ambigüedad se pausa y abre grid de sets del arte identificado. Selección manual permite
  sets históricos; autoañadido conserva exclusiones. Si quedan varios UUID normales, pedir
  impresión con miniatura. Cancelar no añade ni reanuda cámara; doble toque guarda una vez.
  No reciclar/recapturar entre etapas ni cambiar cámara al activar símbolo V2 en este modo.
- `probable_edition` permite estimar entre ediciones compatibles, no declarar confirmación.
  Pie fiable y símbolo confirmado restringen; después priorizar año, símbolo tentativo usable,
  impresión indexada del arte y fecha. Guardar la elección probable separada de `resolvedVariant`.
  Mantener exclusiones históricas y no relajar identidad (OCR coincidente o hash fuerte).
  Con ambos flags activos, se estima y añade sin grid; el grid de sesión sigue siempre disponible.
- En V2, no comparar logos de catálogo de LEA/LEB/2ED/3ED/4ED: no están impresos.
  5ED sí lleva símbolo en chino simplificado; sin idioma fiable conservar ambas posibilidades.
  Una lectura fallida no demuestra ausencia. Nunca elegir una edición antigua por descarte ciego.
- En V2, solo un pie estructurado `SET IDIOMA` autoriza filtros OCR de código/número/idioma;
  las palabras de reglas (`de`, `it`) y fuerza/resistencia no son metadatos de impresión.
  `HashSymbolPrintingEvidence` limita este cambio a V2; no debilitar el parser global.
- `symbol_retry` es opt-in y requiere V2: hasta cinco capturas live nuevas, no cinco análisis
  de la misma foto corregida. Esperar estabilidad completa y 900ms tras enlazar cámara.
  `quadHistory` conserva siete entradas aunque estabilidad requiere ocho detecciones.
  Detener tras edición resuelta (también por arte único), falta de referencias o símbolo no aplicable; pausa/corrección cancela serie.
- `PreviewView.meteringPointFactory` recibe píxeles de vista, no coordenadas normalizadas:
  centro = ancho/2, alto/2; `.5f,.5f` enfoca cerca de la esquina.
- Cada autoañadido hash conserva JSON de evidencias y la foto rectificada en las listas emparejadas
  de `CardInfo`; las fotos viven bajo `files/scan_evidence/`. Copiar las listas en
  `snapshotForPersistence` y borrar la foto correspondiente al deshacer ese escaneo.
- La segunda detección consecutiva de UUID/acabado/idioma iguales no añade copia: abre selector
  total del lote (1–99), separado de cantidades antiguas de la biblioteca. Guardar solo diferencia;
  cancelar no borra la primera. Aceptar/cancelar reanuda cámara; al guardar esperar persistencia.
  No reiniciar contador al repetir
  cámara sobre la misma carta. Deshacer actualiza contador. Guardar lote una sola vez, con evidencia
  por copia y sesión/Room coherentes; impedir guardados simultáneos y rebind mientras hay diálogo.
- El panel de copias permite elegir edición con `EditionPicker.showGrid`; solo OK aplica
  el cambio a todas las copias del lote (incluida la primera). `correctScanBatch` separa el lote
  de existencias previas, mueve evidencia sin borrar fotos y guarda una vez. Actualizar Room,
  sesión, deshacer y continuidad por ID; extras posteriores deben ir al ID exacto del lote.
  Cancelar descarta la edición provisional y no altera las copias guardadas.
- `ConsecutiveArtworkEdition` conserva la impresión guardada para lecturas consecutivas del mismo
  `illustrationId`, antes de nuevas estimaciones/símbolos. No confundir con confirmación visual.
  Otro arte reconocido rompe continuidad; corrección manual de sesión actualiza UUID y deshacer
  la última copia la limpia. Nunca fijar edición antes de un guardado exitoso.
- La capa de preparación del índice está en `scanIndexPreparation`, con fondo `#D9000000`
  (~85 % de opacidad). No aplicar alpha a todo el contenedor: también atenuaría el texto.

## Compilar y validar

Desde la raíz, con Java 17 y `sdk.dir` válido en `local.properties`:

```sh
bash gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain
# En PowerShell/Windows con gradlew en CRLF:
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain
git diff --check
# Corpus de artes Scryfall (descarga reanudable, fuera de Git):
python scripts/download_art_crops.py --data-dir ../mtgfucker-art-hash-data --workers 12
python scripts/art_hash_poc.py --data-dir ../mtgfucker-art-hash-data build
python scripts/build_art_hash_asset.py --input ../mtgfucker-art-hash-data/hashes.jsonl --output app/src/main/assets/art_hash_index.bin
python scripts/build_art_printing_asset.py --input ../mtgfucker-art-hash-data/AllPrintings.json.gz --art-hashes ../mtgfucker-art-hash-data/hashes.jsonl --output app/src/main/assets/art_printing_index.bin
python scripts/art_hash_poc.py --data-dir ../mtgfucker-art-hash-data match RUTA_AL_ART_CROP.jpg
python scripts/pull_sony_art_samples.py --output ../mtgfucker-art-hash-data/evaluation/sony-live
# Prueba instrumental (solo si el Sony tiene capturas privadas retenidas):
.\gradlew.bat :app:assembleDebugAndroidTest --console=plain
adb -s QV770HG2JD install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s QV770HG2JD shell am instrument -w -r -e class io.asv.mtgocr.ocrreader.ArtHashDeviceEvaluationTest io.asv.mtgocr.ocrreader.test/androidx.test.runner.AndroidJUnitRunner
# Prueba concreta cuando el cambio sea acotado:
bash gradlew :app:testDebugUnitTest --tests '*EditionSearchTest'
```

El PoC de hashes requiere Python con `cv2` y `numpy`; los JPEG y el JSONL del PC
no se versionan. Los índices binarios compactos **sí** se versionan y se incluyen en
la APK. El de impresiones contiene metadatos MTGJSON, no imágenes ni precios, y solo
autoriza una impresión cuando queda una variante compatible. Ver `ART_HASH_EVALUATION.md`.
Solo el debug de `codex/art-hash-identification` conserva en `files/art_hash_samples`
las últimas 30 fotos JPEG de los escáneres rápido y experimental. Son privadas
de la app y se copian con `run-as` mediante el script anterior; no subirlas a Git.
Los JPEG CameraX llevan EXIF orientación 6 en el Sony; la prueba instrumental
debe reproducir `decodePhoto` antes de detectar bordes.

- Usar `bash gradlew`: el wrapper puede no tener permiso de ejecución.
- En este worktree Windows, `bash gradlew` falla por CRLF; usar `gradlew.bat`.
- La primera compilación puede descargar Gradle, dependencias y SDK 34.
- Sin `app/google-services.json`, la compilación local funciona pero Firebase queda
  deshabilitado. No añadir credenciales ni cambiar Firebase solo para compilar.
- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Resultados JUnit: `app/build/test-results/testDebugUnitTest/`.
- Los corpus privados opt-in de `SetSymbolSavedScanDeviceTest` viven en
  `no_backup/symbol_v2_replay`, `no_backup/symbol_v2_latest`, `no_backup/symbol_v23_replay`
  `no_backup/symbol_v24_ocr` (reproduce OCR activo/M13) y `no_backup/symbol_v25_replay`
  (Tempest/Future Sight). `SymbolCameraResolutionDeviceTest` verifica frames reales sin colección.
  No usar `cache`: Android puede eliminar fixtures durante install-r con poco espacio.
  Mantener fotos/manifiestos fuera de Git y comprobar que los replays no se omiten.
- No editar recursos durante una compilación; si se hizo, repetirla para regenerar `R`.
- Informar de las pruebas ejecutadas y de cualquier verificación de dispositivo pendiente.

## Instalación y protección de datos

```sh
adb devices -l
adb -s SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
```

- Verificar siempre el dispositivo; no asumir que el único conectado es el solicitado.
- No instalar mientras Gradle esté regenerando el APK: puede fallar la verificación de firma
  por leer un archivo a medio escribir. Esperar a BUILD SUCCESSFUL; si se automatiza una
  espera de USB, usar una copia inmutable del APK validado.
- Sony conocido: XQ-DQ54, serial `QV770HG2JD`; volver a comprobar su presencia.
- En `feature/scan-fixes` se resolvió `INSTALL_FAILED_UPDATE_INCOMPATIBLE` con autorización
  explícita del usuario: backup verificado, desinstalación, instalación debug y restauración.
  Las próximas actualizaciones deberían admitir `install -r` mientras se conserve la misma
  clave local; comprobar siempre el resultado.
- El paquete instrumental antiguo `io.asv.mtgocr.ocrreader.test` puede tener otra firma:
  no desinstalarlo para probar. Usar un `testApplicationId` separado (`io.asv.mtgocr.ocrreader.symbolv2test`)
  mediante un init script local de Gradle; el target sigue siendo la app instalada.
- **No desinstalar ni borrar datos por una incompatibilidad futura sin nueva autorización.**
  La autorización anterior fue para esa reinstalación, no un permiso permanente.
- Backup de recuperación más reciente (fuera del repositorio):
  `~/.codex/backups/mtgfucker/sony-20260920-105615-hash-scanner/`. Tras nueva
  autorización explícita se reinstaló desde el Mac por diferencia de firma. Incluye
  `data.tar`, APK anterior/nueva, manifiestos SHA-256 y marcadores de verificación.
  Los **8.908 archivos** restaurados coincidieron byte a byte antes de abrir la app;
  la copia SQLite pasó `quick_check`, esquema Room 5. `tar -xof -` conservó el nuevo
  propietario. La autorización solo cubre esa reinstalación, no futuras pérdidas de firma.
- Para backups consistentes, detener primero la app. Incluir WAL/SHM de SQLite y los archivos
  legacy, no solo `mtg_catalog.db`. No publicar backups ni credenciales en Git.
- `run-as` permite copiar/restaurar en este debug. Al restaurar un TAR usar `tar -xof -`
  para conservar el nuevo propietario y verificar hashes antes del primer arranque.

- La tabla de reglas evalúa el arte con `RulesArtworkEvidence` compartido con autoañadido: arte fuerte no implica edición exacta. El fallback `fullTitleDictionary` de `matchLocalPhotoText` se activa solo en reglas cuando no hay coincidencias rápidas; usa alias locales completos por variante OCR separada. No concatenar pasadas ni restringir por arte supuesto; conservar overload Java y ruta original por defecto.

- `HistoricalPrintingComparison`/`HistoricalPrintingComparisonView` comparan pie histórico con variantes de la shortlist visual solo para revisión. No alimentan autoañadido ni eliminan candidatos; año de lanzamiento no equivale a copyright impreso. APV1/Room no tienen artista ni denominador impreso. Artista se obtiene del sidecar PFR1 por UUID de impresión+ilustración; denominador sigue sin contrastar, nunca inferirlo del recuento de variantes. No usar candidatos priorizados como catálogo completo.

- `PrintingArtistIndex` carga `printing_artist_index.bin` solo en el worker de reglas, ligado por SHA256 al APV1; si falta/falla no autoriza ni bloquea guardado, deja artista sin contrastar. Generador `scripts/build_printing_artist_asset.py --input PATH/AllPrintings.json.gz --printing-index app/src/main/assets/art_printing_index.bin --output app/src/main/assets/printing_artist_index.bin`; pruebas `python scripts/test_printing_artist_asset.py`. No regenerar hashes para actualizar estos metadatos. Conservar `.provenance.json`; nombres conflictivos o ausentes se omiten.

- `TitleLanguageIndex`/TLV1 aporta títulos exactos multilingües solo a reglas. No tomar idioma del primer alias Room (`normalizedAlias` es PK y puede colapsar ES/PT). Generar con `scripts/build_title_language_asset.py --input PATH/AllPrintings.json.gz --output app/src/main/assets/title_language_index.bin`; conservar procedencia. L02/L03/L09 cruzan conjuntos título/pie/reglas; ambigüedad y conflicto no permiten fallback EN. Índice compacto cacheado en worker; sin red ni nuevas pasadas OCR. APV1 solo acredita compatibilidad positiva idioma-impresión: ausencia UNKNOWN, no eliminación. `RulesScanReview` propone idioma editable y `RulesScanReport.localizedOption` preserva UUID/acabado; no modificar masivamente idiomas guardados.

- El asset TLV1 contiene gzip pero debe llamarse `title_language_index.bin`: AAPT transforma nombres terminados en `.gz`. Verificar bytes empaquetados y carga por AssetManager además de tests JVM.

- `HistoricalFooterRead.collectorNumbers` y `printedTotals` son proyecciones independientes de fracciones ya aceptadas; conservar siempre `fractions` y su procedencia. Comparador histórico usa numerador, no el estado de toda la fracción. Conflicto del total no se repara ni descarta; total no se compara con tamaño del catálogo y no alimenta autoañadido.

- Copyright histórico: `TM/M/™ & C` solo se normaliza a © como prefijo inmediatamente anterior a candidato de año de4caracteres. Confusión inicial I/l/|→1 solo en año con ancla copyright, limitado1993–2099. Conservar raw; nunca dividir números pegados (`1162`, `129143`) para inventar fracción.

- `ArtistNameDictionary` reutiliza vocabulario global PFR1 solo en worker de reglas. No restringir diccionario al artista de una impresión candidata. Aproximación requiere candidato único y otra pasada exacta de la misma captura; conservar `source`, `originalValue` y `normalization`. No convertir corroboración de preprocesados en prueba de edición ni eliminar conflictos reales.

- `RulesCaptureGate` solo en captura live reglas, tras rectificar y antes snapshot/unbind/OCR: títuloLaplacian<10 con footer>100 pide nuevo encuadre, máximo2rechazos consecutivos y después deja analizar. No prueba ausencia/oclusión; datos no finitos/desconocidos pasan, blanco uniforme pasa. Rechazo recicla bitmap yreseteaestabilidad/historial, no cuenta intentoOCR ni escribe colección. Manual yotros scannerssin cambios. Umbralesexperimentales corpuslimitado, no generalizar a layouts sin título.

- v18bordesA/B: flagrules_scanner.fixed_bounds,coordenadassoloActivity. AutoOpenCvCardDetector(preferOuter=true) solo reglas; otrosmodosfalse. Fijocalibra desdeframeYlive con4esquinasmanualmente,no reutilizarstillJPEGdedistintoFOV. Guardarzoom/rotación; conservarreferenciarebindmismozoom. Toggleinhibido durantecaptura/corrección. RulesScanReport retieneoriginal.jpg+boundaryQuad+boundaryMode juntocard.jpg,30carpetasprivadas. No confundir plantilla fijacon detecciónactualdepresenciacarta.
