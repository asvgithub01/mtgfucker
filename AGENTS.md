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

- Plan de migración Kotlin/Compose no escáner: `ARCHITECTURE_MIGRATION_PLAN.md`.
  Launcher P2: `MainActivity.kt`, `feature/launcher/`, `domain/launcher/`,
  `data/legacy/LegacyLauncherRepository.kt`, composición en `app/di/` y puente
  `legacybridge/LegacyLibraryNavigation.kt`. UI común: `core/designsystem/`.
  Contratos y validación: `ARCHITECTURE_CONTRACTS.md`. El host Biblio/OCR no cambia.
  Shell P3 parcial: `BiblioShellActivity.kt`, `feature/library/`, `domain/library/`,
  `data/legacy/LegacyLibraryShellRepository.kt`. Vista previa opt-in solo debug desde
  launcher; la Biblio habitual conserva todas las operaciones. No activar como predeterminada.
  No usar `DeckCatalogStore.load` como lectura: migra grupos y guarda catálogo/Biblio.
  El shell lee por ID explícito sin escribir; no añadir comandos hasta validar doble
  persistencia y concurrencia. La Activity solo se registra en `src/debug/AndroidManifest.xml`.
  Retorno P3: `legacybridge/LegacyShellNavigation.kt` conserva Intents existentes y
  `data/legacy/LegacyLibraryChanges.kt` observa publicaciones por rename mientras el shell
  está visible. RESUMED por sí solo no basta: OCR puede seguir guardando tras onDestroy.
  `LegacyLibraryMutation.kt` solo prepara un grafo completo separado, NO lo guarda.
  No conectar ese preparador a DataUtils hasta impedir sobrescrituras legacy tardías;
  tampoco reconstruir Biblio desde CollectionRow ni borrar fotos durante la preparación.
  No usar proyecciones de `domain/collection/CollectionReader.kt` para guardar/reemplazar Biblio.
  Ejecutar `python scripts/check_architecture_boundary.py` y
  `python scripts/test_architecture_boundary.py`: fuentes runtime existentes protegidas,
  incluidos escáneres y modelos serializados. Única excepción: sustitución de MainActivity.java.
  Imports domain/feature también vigilados. Excepciones futuras solo explícitas y revisadas.
  Consulta P4 debug: `SetCatalogPreviewActivity.kt`, `feature/catalog/`,
  `app/di/SetCatalogViewModelFactory.kt` y `data/catalog/LegacySetSearchMatcher.kt`.
  Reutiliza loadSetCatalog y EditionSearch sin modificarlos: solo caché de catálogo,
  nunca Biblio/owned printings. Detalle local: `feature/carddetail/CollectionCardDetail.kt`;
  muestra precio guardado, no lo actualiza. No sustituye Main2Activity ni sus comandos.
  Guard P7 cubre imports/referencias cualificadas Kotlin/Java en domain/feature;
  es una comprobación heurística, no un analizador semántico ni sustituto de revisión.

- Aplicación Android nativa, Java + Kotlin; interfaz principalmente XML/Views, con Compose
  también habilitado. Módulos Gradle: `app`, `core:domain`, `core:designsystem`, `data:readers`,
  `feature:catalog`, `feature:photos`, `feature:services`, `feature:launcher`,
  `feature:library`, `feature:carddetail`, `liquid-glass` y `cameraVision`.
- CollectorVision nativo vive aislado en `cameraVision/`: CameraX, Cornelius,
  Milo y catálogo ONNX CPU. EdScan (`EdScanDebug.kt` guarda evidencias privadas por escaneo; `PriceCelebration.kt` comparte umbral con OCR; `ScanPartyOptions.kt` acumula niveles inclusivos x1/2/4/8/16 y selecciona un solo MP3; `ScanPartyView.kt` dibuja confeti/globos/trompetas/disco y libera audio al salir; los MP3 `party_*` son aportados por el usuario; `EdScanTuning.kt` permite ajustar mínimos del modo rápido) y su cola persistente están documentados en `EDSCAN_BACKGROUND_JOBS.md` (WorkManager, fotos privadas y OCR revisable; ruso pendiente). Su botón abre el host `CorneliusScanActivity`; el laboratorio
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
| Vistas de colección y borrado | `FourCardLayoutManager.kt`: completas2×2, arte1×2/2×3 horizontal; `HoldToDelete.kt`: botón inline, mantener1s y confirmar porID. Lista clásica retirada. |
| CollectorVision nativo aislado | Host `CorneliusScanActivity.kt` guarda en grupos Cornelius N mediante UUID exacta. Módulo `cameraVision/`: `NativeCollectorVisionActivity.kt`, `NativeCameraVisionEngine.kt`, `CameraVisionAssets.kt`, `CameraVisionAcceptanceGate.kt`; preview separado del análisis, sonido solo al reconocer; flags persistentes de filtros/rápido y miniatura; precio Scryfall asíncrono por UUID/acabado. Demo WebView sin entrada activa |
| Scanner, navegación, colección y sesión | `OcrCaptureActivity.java`; layout `ocr_capture.xml` |
| POC EdScan + ARCore (cámara única) | `EdScannerArActivity.kt` (host copiado), `EdScannerArPreview.kt`, `EdScannerArSurface.kt`, `EdScannerArOverlay.kt`, `ArCardProjection.kt`, `ArYuvRgb.kt`; consultar `EDSCAN_AR_POC.md`. ARCore proporciona CPU frames al worker de EdScan (usesExternalCamera); un frame en vuelo, cerrar Image tras convertir. Cámara mayoritaria con Opciones plegables; AR opcional, overlay solo FULL_TRACKING. |
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
| Postprocesado y revisión de fotos | `ScanReviewActivity.kt`, `ScanReviewPanel.kt`, `ScanReviewStore.kt`, `ReviewPresentationPolicy.kt`, `ReliableReviewWorker.kt`, `ReliableReviewPolicy.kt` y `ReliableReviewHistory.kt`; verificación en `cameraVision/EdScanVerificationWorker.kt` |
| Navegación de postprocesado | Resumen con información/historial; pestañas Pendientes/Resueltos y filtro de origen independiente por pestaña (`ReviewPresentationPolicy.forTab`). `CollectionScreenBackground` compartido. Resolver reutiliza evidencias; reanálisis solo individual y explícito. |
| Diagnóstico Premium del detalle | `PostprocessEvidenceStore.kt`/`PostprocessEvidencePanel.kt`: lectura por biblioteca + collectionItemId, sin lanzar workers ni aplicar resultados; `Main2Activity.kt` sincroniza arte de cabecera con la imagen actual al hacer scroll. |
| Resolución común del idioma Triple | `CardLanguageResolver.kt`, `TriplePhotoLanguageReader.kt`: modelos sobre la foto, decisión compartida con `TripleLanguageFusion`; evidencia `.language-resolution.json`. Replay privado en emulador con `CardLanguagePhotoReplayTest`, nunca sobre el Sony. |
| Detalle de carta y ediciones | `Main2Activity.kt` (incluye `EditionAdapter`), `ScanEvidencePanel.kt`/`ScanEvidenceFormatter.kt` para foto e historial debug; layouts `activity_main2.xml`, `edition_item.xml` |
| Buscador compartido de ediciones | `EditionSearchAdapter.kt` (incluye política `EditionSearch`), `EditionPicker.kt`, `SetSymbolLoader.kt` |
| Corrección de edición en sesión | `EditionPicker.showGrid`, `EditionGridContent.kt`, `EditionGridPolicy.kt` y `HashEditionGridPanel.kt`; sesiones OCR y rápida/hash. Siempre disponible, sin depender de `edition_picker`. Preservar ID/cantidad/idioma y actualizar Room + legacy; en rápida actualizar también las referencias de deshacer. |
| Carga de imágenes y placeholder | `CardImageCache.kt`, `CardLoadingDrawable.kt`, `SetSymbolLoader.kt`; cargas de fotos en `PhotoScan*` |
| Catálogo de sets | `MagicSetCatalogAdapter.kt`, `SetCollectionActivity.kt` |
| Gestión de datos | `StorageActivity.kt` (Ajustes, cinco bloques), `StorageData.kt` (referencias/inventario/guardas), `StorageOperations.kt` (worker serial, loading y MB persistentes). No borrar Room ni modelos compartidos; verificar todas las bibliotecas primarias, fotos y paquetes AR. `PostprocessCleanup.kt` espera trabajos y conserva journals de recuperación. |
| Fotos de Compras | `PhotoCardAnalyzer.kt`: EdScanner en worker, mosaicos y UUID conservadora; `PhotoDetectionGeometry.kt`: geometría normalizada EXIF; `PhotoPriceImageView.kt`: precios del proveedor seleccionado. No autoañade colección, acabado normal revisable. |
| Fotos de compras, cámara, grupos y galería | `PhotoActions.kt`, `PhotoImportActivity.kt`, `PhotoGroups.kt`, `PhotoScanDetailActivity.kt`, `PhotoGalleryActivity.kt`, `PhotoPriceImageView.kt`; `CardSearchActivity.kt` + `PhotoCardPreview.kt` para añadir una carta con confirmación; grupos en `photo_groups`, fotos/detecciones independientes en `PhotoScanStore.kt` |
| Escaneo específico de edición/fotos | `EditionScanActivity.kt`, `PhotoScanDetailActivity.kt`, `PhotoScanStore.kt` |
| Catálogo, precios e imágenes | `data/CardRepository.kt`, proveedores `data/MtgJson*` y `data/Scryfall*`, `data/CardDatabase.kt` |
| Colección persistida | `model/CardInfo.java`, `model/Biblio.java`, `DataUtils.java`, `LibraryCatalog.kt`, `data/LegacyCollectionStore.kt` |
| Exportación de venta Cardmarket | `data/CardmarketCsvExporter.kt`, `data/PriceCurrency.kt`; botón y guardado en `OcrCaptureActivity.java`; consultar `CARDMARKET_EXPORT.md` |
| Web y extensión Cardmarket | `web/`, `extension/`, protocolo compartido `packages/cardmarket-protocol/`; consultar `CARDMARKET_EXPORT.md` |
| Valoración web y catálogo público Itaca | `web/src/itacaCatalog.ts`, `packages/itaca-buylist-scraper/`; consultar su `README.md`. Oferta base, sin ajustes por idioma/condición/foil. |
| Idioma, acabado, condición y moneda | `data/CardLanguage.kt`, `CardFinish.kt`, `model/CardCondition.kt`, `data/PriceCurrency.kt` |
| Sincronización cloud | `CloudLibrarySync.kt`, `CloudAccountActivity.kt`; el backfill Cardmarket vive en `data/CardRepository.kt`; consultar `FIREBASE_SETUP.md` |

Estado consolidado del reconocimiento, reglas y pendientes: `RECOGNITION_RULES_HANDOFF.md`.

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

Los alias OCR de novedades se incluyen en `app/src/main/assets/ocr_aliases/release_supplement.tsv`
(procedencia JSON al lado), importados por `MtgJsonCardNameResolver` mediante `BundledOcrNames`.
Actualizar índices visuales no actualiza este diccionario; comprobar también las colecciones Commander.

## Invariantes que no se deben romper

- Triple conserva `Target.id` como referencia visual (incluido `_back`). Usar
  `TripleVisualReference.catalogId` para catálogo/persistencia; no convertir una cara en
  otra impresión ni perder `visualReference` en las evidencias. El foil sigue siendo manual.
- `CardFaceToggle` alterna solo la imagen en detalle, galería Triple y galería fullscreen
  (`CardImageActivity`); no guardar la cara
  mostrada en la colección ni modificar el target visual. No deducir doble cara por `//`: se
  necesitan dos imágenes físicas. Respetar generación al cambiar edición/idioma o cerrar.

- Una fila se identifica por `collectionItemId`, no por la posición de una lista que puede
  ordenarse o cambiar mientras llega una respuesta asíncrona.
- El catálogo de una edición agrupa partes MTGJSON por `scryfallId` con `PhysicalPrintings`:
  una entrada por impresión, preferencia `side=a`, UUID secundarios como alias de posesión.
  Room v6 conserva todos los UUID y añade `side`; no borrar filas ni fusionar inventario por
  nombre/número. Sin red también se filtra la caché antigua; las caras se completan al cargar el set.
- Impresión (`printingUuid`), acabado (`nonfoil`, `foil`, `etched`), idioma, condición y
  cantidad son conceptos diferentes. Cambiar edición no debe perder cantidades ni grupos.
- Un precio corresponde a una impresión y acabado concretos. Al cambiar acabado, limpiar
  el precio anterior; antes de aplicar respuestas tardías, comprobar UUID y acabado actuales.
- `PriceDetailsDialog` consulta referencias locales de los totales sin red ni escrituras;
  `PriceOriginPolicy` exige UUID, acabado, importe base y moneda coincidentes. No atribuir la
  fecha del snapshot actual a un importe legacy antiguo. Triple conserva estos datos también
  para encuentros fuera de la tira visible. El launcher pregunta por el refresco al abrir
  Biblio una vez por inicio; no repetir al volver de un detalle ni descargar por cada carta.
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
- Una biblioteca vacía con historial de sincronización es un borrado local, no una instalación
  nueva: no restaurar automáticamente sus cartas desde cloud.
- La limpieza no borra una carta de la colección por carecer de foto: retira solo su entrada/evidencia de postprocesado cuando no hay trabajos activos. «Resolver casos fiables» usa la misma política de casos sin cambios que el worker.
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
  cortas. No deducir idioma físico de una edición provisional ni cambiar masivamente cartas PT existentes.
  Excepción Triple: impresión AR/arte fiable con catálogo completo de un único idioma;
  asignarlo sin esperar OCR. OCR posterior contrario marca conflicto de edición, no sobrescribe.
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
# Itaca: pruebas y actualización del catálogo web (sin cambios Android):
npm run test:itaca
npm run refresh:itaca
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

- `TripleSessionMaintenanceTest` es una herramienta instrumentada **opt-in**, omitida en
  pruebas normales. `tripleMaintenance=inspect` solo informa; `remove` requiere biblioteca,
  grupo e IDs exactos. Usar borrado únicamente con autorización del usuario y backup previo;
  verifica que las filas restantes no cambian y archiva las evidencias vinculadas.
- La misma herramienta permite `verifyResume` (comprueba IDs, total y SHA de todas las
  bibliotecas sin escanear) y `resumeNext` (prepara reanudar exactamente ese grupo al abrir
  Triple). `TripleCameraLoadTest`, opt-in `liveTriple=true`, perfila 30 s de cámara con
  persistencia deshabilitada por `triple_diagnostic_only` en debug; no usar como escaneo real.
- Contorno EdScan: `EdScanContourView`/`EdScanContourGeometry`, flag independiente apagado
  por defecto en Fast y Triple. Reutiliza `scan.corners`: no añadir detectores ni frames.
  Triple proyecta con la transformación ARCore de esa captura; Fast usa CameraX OutputTransform.
  Expira a los 2,5 s desde captura, se limpia al desactivar/salir y jamás autoriza un alta.
- Triple sigue analizando tras un alta: bloquea solo otra alta consecutiva de la MISMA
  identidad. Cartas diferentes (incluido A→B→A) entran automáticamente. `triple_rescan`
  autoriza repetir con una captura nueva, nunca copiar el candidato ni sumar cantidad.
  `TripleRecognitionArbiter.beginManualScan` invalida capturas anteriores al toque y el
  nuevo evento se persiste con `mergeExisting=false`. El enriquecimiento por fila continúa.
  Solo Arte descansa 150 ms; revalidación prioritaria con frescura intacta.
- Modo «Arte rápido» en Opciones reutiliza `single_art_reading` (OFF por defecto): una lectura fuerte para identidad, sin cambiar umbrales ni certificación de impresión/idioma. La galería muestra `ic_foil_badge` según `selectedFinish` mediante `CardFinish.isFoil`.
- Experimentos visuales Triple: `TripleIdentityConfirmation` separa votos de identidad y
  edición; `TripleCropTrialPolicy` permite FULL primero y rescate FULL+recorte tras dos
  fallos. Flags `single_art_reading` y `delayed_art_crop` en preferencias `triple_scanner`,
  desactivados por defecto y con controles en opciones. Nunca aplicar el voto único a
  OCR/AR ni certificar edición con ese único voto. Cambiar flags invalida evidencia.
- Rendimiento/total Triple: `TripleSlowFramePolicy`, `TriplePersistencePolicy`,
  `TripleSessionTotals` y `NativeCameraVisionEngine.prepareForCamera`. No ampliar la
  caducidad de lecturas sin validar contra un fotograma fresco; no sumar solo la galería.

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

## Galería y revisiones de escaneo
- Galería compartida: `CardImageActivity.kt`, `activity_card_image.xml`; ViewPager2 horizontal y tira RecyclerView miniatura/precio. No condicionar el gesto a una imagen descargada. Zoom conserva interceptación propia.
- OCR sin set: `ScanPrintingPolicy.recentAffordable`, mínimo precio conocido entre cinco sets recientes del mismo acabado. `CardInfo.provisionalEdition` también debe copiarse en snapshots; selección manual lo desmarca. No sustituir UUID/acabado elegidos en callbacks tardíos.
- Postproceso: `ScanReviewActivity`/`ScanReviewStore` listan evidencia y revisión humana. EdScan analiza solo OCR/idioma también al reanalizar individualmente; `ReviewAnalysisPolicy` separa alcance por origen de reutilización por modo. Reanálisis explícito genera OCR nuevo; autoresolver puede reutilizarlo. No mostrar propuestas de arte antiguas para origen EdScan. No reutilizar archivos de resultados entre intentos. OCR original flag `save_ocr_photos` copia frame NV21 antes de filtros (`OcrFramePhoto`, callback por frameId) y encola matching visual con `EdScanVerificationWorker`. No atribuir una captura posterior a OCR anterior. Ver `SCAN_REVIEW_PLAN.md`.
- Tira recientes compartida `RecentScansView` en OCR original y CorneliusScanActivity: últimas30filas, acciones por collectionItemId. `addHostRecentControls` es el hook inferior del módulo nativo. Confirmar borrado; no sustituir impresión/acabado/idioma por callbacks tardíos.

- Índices FRA: `REALITY_FRACTURE_INDEX_UPDATE.md`, scripts `update_reality_fracture_indices.py` y `merge_reality_fracture_metadata.py`. EdScan conserva catálogo base y carga suplemento empaquetado SHA fijo; no cambiar modelo ni eliminar comprobaciones. Al ampliar hash regenerar APV1/PFR1/TLV1 juntos (PFR1 está ligado al SHA de APV1).

- Fondos: `OcrCaptureActivity.updateSectionBackground` aplica arte a todas las pestañas. `CollectionScreenBackground` lo incorpora en detalle de colección/foto con carga IO y limpieza lifecycle. Respetar pin premium y fallback sin cartas.

- Postprocesado: `ReviewAnalysisBatch` encadena OCR + contraste de título y, solo para origen OCR, verificación visual estricta, con intentos UUID y progreso WorkManager; el reanálisis normal no modifica inventario. El modo explícito Resolver casos fiables añade `ReliableReviewWorker`: para origen OCR requiere acuerdo OCR/arte, score ≥0.70 y margen ≥0.08; para origen EdScan contrasta identidad OCR e idioma contra la impresión guardada sin repetir arte; conserva acabado y valida idioma; excluye múltiples copias/fotos. Guarda snapshot y resultados en `files/review_batches`, comprueba huella antes de escribir y verifica persistencia antes de cerrar revisión. `ReviewPresentationPolicy` agrupa evidencias en una fila por foto sin inventar claves. `ScanReviewPanel` (compartido por `ScanReviewActivity` y el detalle, filtrado por biblioteca + `collectionItemId`) edita borradores y solo guarda explícitamente; selector de idioma exige edición y filtra variantes. Acabado se elige con impresión, no se infiere visualmente. `loadCachedPrinting` recibe UUID MTGJSON; `loadCollectorVisionPrinting` recibe ScryfallID. No confundirlos.

- Autorresolución: contrastar títulos OCR con nombres localizados de la impresión; alias compartidos ES/PT no determinan idioma. Un idioma OCR fiable no disponible bloquea incluso si solo hay una variante. El selector de edición del postprocesador debe tener fallback visible cuando falta SVG (PLST). Filas plegables conservan índices internos dentro de su contenedor; no desplazar hijos sin actualizar el binding.

## Controles compartidos de scanners

- `ScannerSetInput.kt` comparte selector multiedición y símbolos; `ScannerNameInput.kt` comparte sugerencias, voz y añadir entre OCR y `CorneliusScanActivity`.
- El bloqueo EdScan filtra Scryfall IDs exactos antes de comparar embeddings. Scope vacío bloquea reconocimiento mientras carga; `null` significa catálogo completo. Nunca usar fallback global al fallar el set.
- `ScannerIdlePolicy.kt` pausa EdScan a los 30 segundos sin interacción ni cambio de carta estable; libera CameraX y se reanuda explícitamente.

- Postprocesado: `ReviewEvidenceCache.kt` reutiliza OCR/arte por hash de captura, metadata y resultados. Autorresolver omite resultados pendientes sin cambios y distingue presets automáticos de borradores manuales. Reanalizar explícito fuerza nuevas evidencias; no bajar umbrales para vaciar pendientes.

- Autorresolución EdScan: conservar UUID/edición/acabado guardados; OCR con identidad coincidente e idioma disponible resuelve idioma sin exigir otra verificación visual. Origen OCR sí requiere arte. Versionar decisiones (`ReliableReviewPolicy.VERSION`) al cambiar reglas, no recalcular OCR/arte por ese motivo.


### Diagnostico de EdScan AR
- La POC conserva la galeria fuera de Opciones y no aplica el timeout automatico de
  inactividad; pausas manuales/ciclo de vida siguen vigentes. CameraX no cambia.
- En debug, `EdScanPerf` registra tiempos agregados por etapa e hilo (motor, conversion,
  GL, cola/trabajo UI, referencia AR), heartbeat y estado ahorro/termico. Comparar
  ambos scanners con la misma carta/condiciones antes de cambiar umbrales o resolucion.
  Consultar `EDSCAN_AR_POC.md`; no guardar fotos ni datos personales en los logs.


- La estrategia AR de contorno genérico es solo detección 2D sin identidad/precio:
  geometría de Frame actual, caducidad250ms y guardas orientación/tamaño. No utilizarla
  como pose3D ni enlazar precio anterior. Los efectos ArEnergyBorder/ArCardReveal
  requieren FULL_TRACKING. Opciones experimentales OFF por defecto.


- Paquetes AR descargables: `packages/ar-packages/README.md`, generador `npm run build:ar -- --all` (solo cobertura revisada en editions.json), catálogo `web/public/ar`. Gestor `ArDownloadsActivity`/`ArPackageStore`, ajustes Premium; solo nuevo scanner AR los consume. Configurar `-ParPackagesUrl=https://HOST/ar` antes de distribuir. No confundir Premium local con protección de URLs públicas; no publicar sidecars de arcoreimg con rutas locales.

- Descubrimiento AR: `npm run discover:ar` verifica ediciones/DBs; `--generate-missing` genera ausentes (coste alto, explícito). `build:ar -- --sets CODIGO` admite cualquier set, solo principal si falta cobertura en `packages/ar-packages/assets/ar-editions.json`. Asset compartido Android/Node; no adivinar anexos. `ArLocalPackageBuilder` genera en Ajustes cuando no hay paquete publicado; nunca sube a web ni toca colección.

- Laboratorio triple aislado: `TripleScanActivity.kt`, `TripleEncounterGate.kt`, `TripleSnapshotOcr.kt`, `TripleLanguageEvidence.kt`, `TripleScanPolicy.kt`, `TripleArConsensus.kt`, `TripleCardLookup.kt`, `TripleOcrPrintingResolver.kt`, `TripleOcrPrintingPolicy.kt`, `TripleScanTrace.kt`, `TripleTitleGuide.kt`, `TripleArPreview.kt`, `TripleArSurface.kt`, `TripleConfetti.kt`, `TripleRarityColor.kt`, `TripleArOverlay.kt`, `TripleArImageCache.kt`, `TripleArRenderPolicy.kt`, `TripleEnergyBorder.kt`; botón `fabTripleScanner`. Guarda una fila por encuentro mediante `TripleCollectionSession` (Legacy + Room), con foto inmutable en EdScanJobs para postprocesado. Menú por lectura: idioma manual protegido, acabado, cantidad y borrado confirmado. No cambiar `EdScannerAr*` para experimentar. Prueba de integración aislada: `TripleCollectionSessionTest`; las pruebas antiguas TripleScanSmokeTest asumían no guardar y deben migrarse antes de ejecutarlas contra una colección real. Preferencias `triple_scanner`/`triple_ar`, separadas del comparador. AR ofrece preview inmediato; `TripleRecognitionArbiter` confirma encuentros, EdScan fuerte y fresco prevalece sobre AR; nunca conservar un veto histórico tras cambiar carta o desactivar motores. `TripleScanScopePolicy` aplica los sets a los tres motores. Un paquete AR compilado puede cubrir varias ediciones: `TripleArPackagePolicy` comprueba metadatos; conservar todas las entradas al cargar la DB y filtrar identidades recibidas por selección. EdScan filtra por IDs exactos de las ediciones seleccionadas; carga/error con scope vacío nunca usa catálogo global. OCR observa idioma, no lo deduce del idioma de referencia. Triple combina reglas y `TitleLanguageIndex` sin elegir arbitrariamente entre alias ES/PT; una lectura vacía no borra el consenso. Cuadrilátero OCR AR del mismo Frame convertido VIEW→IMAGE_PIXELS. Un frame worker y un OCR de identidad en vuelo; diccionario independiente, nunca bloquear cámara esperando alias. Ver `TRIPLE_SCANNER.md` para pruebas y logs.

- Triple: `TripleReadingPolicy` usa nombre canónico como identidad de encuentro; cambiar impresión no añade copia ni pierde idioma/foto. La ausencia sostenida (tres muestras/1500ms) habilita otra copia; una identidad distinta estable inicia otro encuentro, pero otra impresión del mismo nombre no añade copias. `TripleFrameSignature` detecta cambio estable de apariencia, no identidad; dos copias idénticas sin retirada requieren cantidad explícita. Umbrales físicos experimentales: validar en cámara. `manualEdition`/revisión protegen el selector `EditionPicker.showGrid` frente a callbacks. Idioma: título/alias único validado por TLV1 prevalece sobre inferencia por impresión, marcando conflicto si discrepan; no tomar el primer idioma de un alias ambiguo. Resolver OCR trabaja por fila aunque cambie la carta actual, sin fallback global automático si no coincide el set seleccionado (solo búsqueda manual explícita). Filas sin impresión quedan pendientes, con selector/reintento; nunca inventar UUID Scryfall a partir de UUID MTGJSON.

- Idioma fijo del Triple: `TripleLanguageScope`/`TripleSetLanguageResolver` usan SetList.languages, no lenguas de imágenes. Preferencia ligada al conjunto de sets, origen `scope` manual protegido solo para nuevos encuentros; filas históricas no se reescriben. Con idioma fijo se omite clasificación y se limita modelo OCR por alfabeto; no apagar identificación OCR de cartas desconocidas. Selector idioma en Opciones junto a edición; cabecera solo resumen no pulsable. CLAHE sigue pendiente y desactivado (ver TRIPLE_SCANNER.md).

## Revisión de precios y galería

- `ReviewPriceAnalyzer.kt` obtiene cotizaciones EUR explícitamente; `ReviewPriceRanges.kt` calcula máximos de euros y porcentaje por separado, sin mezclar acabados ni imputar precios ausentes. `ScanReviewActivity.kt` expone reanálisis y filtros.
- Al abrir postprocesado desde una agrupación de Biblio, `ScanReviewActivity.EXTRA_GROUP` y `ReviewCollectionScope` limitan las entradas antes de pestañas, contadores y operaciones por lote. Conservar el filtro tras guardar/releer; una agrupación sin fotos no debe mostrar toda la biblioteca.
- `Main2Activity.EXTRA_AS_POPUP` abre el detalle a pantalla completa sobre la pantalla anterior, sin tema flotante ni margen de ventana; conserva el modo de solo lectura hasta editar explícitamente. Home guarda ancla/estado al pausar y conserva el adaptador si no cambia biblioteca/archivo (fecha+tamaño); los conflictos actualizan solo las filas y solo si cambian. No reconstruir el adaptador desde callbacks de estado. La galería reserva el hueco de acciones desde el primer layout; no alternar GONE durante el layout del pager. Pruebas: `HomeNavigationDeviceTest`, `LibraryGalleryUiDeviceTest`.
- `CardImageActivity.kt` permite cambiar edición de cartas guardadas verificando disponibilidad del idioma, con persistencia Legacy + Room. `CardBackgroundAction.kt` centraliza fijar fondo. La selección múltiple usa `collectionItemId`, nunca posición.

- Triple v7: `TripleVisualConfirmation` exige dos frames individualmente fuertes (.70/.08), no dos top1 con solo el último fuerte. `triple.confirmation` separa razones/tiempos y `scripts/analyze_triple_confirmation.py` resume p50/p95 y casos lentos. Constantes en `TripleScanPolicy`; no bajar umbrales sin evaluar aciertos. Idioma fijo conserva OCR del título. Prueba de rendimiento sin inventario: `TripleRecognitionPerformanceTest`; runner alternativo con `-PtestApplicationIdOverride=io.asv.mtgocr.ocrreader.symbolv2test` si existe firma antigua.

- Triple captura fresca: `TripleFrameScheduler` arbitra lanes seriales OCR/EdScan;
  OCR usa `NativeCameraVisionEngine.detect` sin embeddings previos y geometría
  `TripleOcrGeometry`. No volver a encadenar OCR al bitmap ya analizado por EdScan.
  `TripleOcrStageGate` evita bloqueo por nombre y doble liberación tardía. Preparación
  del índice no bloquea AR/EdScan. Apariencia conserva evidencia independiente;
  ausencia/generación la limpian. Continuidad6s no amplía frescura2500ms.
  `TripleAdaptiveVisualPolicy` es solo sombra, sin autoridad de guardado: falta
  contraste global por identidad/cobertura y validación física. Ver sección v13 de
  `TRIPLE_SCANNER.md` y pruebas `TripleFreshFrameRegressionTest`/`TripleAdaptiveVisualPolicyTest`.

- Triple v14: con OCR o arte habilitados, AR requiere corroboración independiente
  fresca para abrir un encuentro/escena y no veta por sí solo un OCR estable.
  AR-only conserva su política anterior (no promete eliminar sus falsos positivos).
  `TripleLanguageCopyPolicy` completa desconocidos, conserva manual/inferencia
  contradictoria y separa idiomas físicos conocidos tras capturas independientes
  del mismo título, con foto nueva; nunca sobrescribir la copia anterior. La nueva
  impresión queda provisional, sin heredar edición manual ni cantidades.
  `TripleSnapshotLanguageGate` une nombre/reglas de la misma captura aunque el
  diccionario responda después; no perder reglas ES/PT tempranas. Pruebas
  `TripleSonySafetyRegressionTest`, `TripleLanguageCopyPolicyTest` y
  `TripleSonySafetyIntegrationTest` (solo emulador, sin inventario).
  `TRIPLE_SCANNER_PERFORMANCE.md` documenta el baseline y propuestas, no optimizaciones
  aplicadas. `scripts/analyze_triple_pipeline.py` analiza trazas privadas sin importar
  fotos/colecciones al repositorio; `triple.ocrLane` incluye caducados y `traceDropped`
  avisa de pérdida de logs. No sumar percentiles ni tratar intervalos entre cartas
  como latencia de reconocimiento.

## Prioridad de proveedores e histórico

- `PriceSourcePreferences.priorityIds` devuelve todas las fuentes en orden; `selectedId` es solo la primera, no una restricción exclusiva. Tocar una fuente la sube al primer puesto y arrastrar reordena. Usar la primera cotización válida para el mismo UUID/acabado, no la más barata ni otro acabado. `SelectedPrices` cachea por orden completo y conserva el proveedor real; `PriceCurrency` no reetiqueta importes legacy. `PriceSourcePresentation` muestra origen y destaca fallback.
- Room 8 conserva históricos y añade `refreshAt` a la clave de carta: 0 para cotizaciones deduplicadas, timestamp para cada refresco completo (también 0 %). `priceDate` sigue siendo la fecha del proveedor; no generar refrescos al abrir detalle. Carta y grupos se guardan en una transacción. Room 7 añadió históricos sin borrar inventario. `PriceHistoryStore` conserva cotizaciones observadas por UUID/acabado/proveedor/fecha/importe; cada refresco completo registra biblioteca/grupos con las fuentes reales. Totales mixtos se convierten a moneda visible; fingerprint incluye fuentes y tasa de cambio para no etiquetar cambios de fuente/FX como evolución comparable de mercado. No reconstruir fechas desconocidas.
- `PriceHistorySection` y `PriceHistoryChart` muestran gráfico nativo en el detalle y al tocar precio. Series separadas por proveedor/moneda/acabado; `PriceTrendPolicy` usa último dato por fecha de cotización y conserva cada `refreshAt`; el gráfico espacia observaciones uniformemente para distinguir refrescos cercanos. Subida verde↑, bajada roja↓; sin dos fechas no inventar porcentaje. Histórico agrupación vía total→Histórico; contador sinprecio solo dentro del diálogo, no añadirlo bajo el total de Biblio.
- Pruebas: `SelectedPriceHistoryTest`, `PriceFallbackTrendTest`, `PriceHistoryDatabaseTest`. Para firma antigua del paquete test en Sony, usar `'-PtestApplicationIdOverride=io.asv.mtgocr.ocrreader.pricehistorytest'`, no desinstalar. Pruebas de render sintético se ejecutan en emulador, no se insertan en histórico real.

## Compras y búsqueda de catálogo

- `BuylistStore.kt` guarda `files/shopping-lists-v1.json` con AtomicFile y ejecutor serial, separado del inventario/Room. `ShoppingSection` integra Compras en navegación; `BuylistActivity` solo detalle superpuesto a pantalla completa con listId; `CollectionPopupChrome` comparte glass/X con `CardSearchActivity`. Mutaciones de Compras requieren Premium. Importar crea copia nueva, nunca sobrescribe por ID externo. Incluir este archivo en backups de `files`.
- `Main2Activity` con `browseOnly=true` permite consultar sin ID de colección ni guardar selección/condición/idioma. Conservar este límite al ampliar la búsqueda. La barra inferior muestra cinco destinos; Fotos se abre desde Compras (`ShoppingSection`) y vuelve a Compras sin eliminar fotos.
- Cada refresco completo exitoso del índice MTGJSON registra una observación por entrada de cada buylist, incluso cuando falta precio. Solo MKM/EUR y UUID/acabado exactos; NM es condición solicitada, no oferta de vendedor verificada. Cambios de composición no son variación de precio comparable; no sumar ausencias como cartas gratis.
- Contrato app/web v1: `packages/cardmarket-protocol/src/buylist.ts`, `web/src/shopping.ts`. CloudBuylistSync sincroniza listas e históricos en users/{uid}/shopping/state mediante snapshots comprimidos, hash y publicación de cabecera al final. Conflictos se detectan con hash base, nunca se sobrescriben silenciosamente. Web edita las mismas listas e IDs, publica chunks y valida snapshotId/hash en transacción antes de cambiar cabecera. CloudBuylistObserver escucha cambios mientras la pantalla está activa, respetando autosync. Bases de hash local/remoto separadas: JSON JS y Android serializan distinto. Históricos se conservan al editar; importación idempotente, divergencias rechazadas, nunca duplicar IDs. Recuperación del antiguo localStorage requiere acción explícita. Premium sigue siendo el flag local existente, no una suscripción verificada por backend. `extension/src/wants.ts` es asistente de copia manual, separado de BulkListing, sin APIs privadas ni envíos de formularios.
- Texto MKM: máximo150 entradas por bloque y lista vacía por bloque. El usuario debe comprobar NM/expansión/versión/número/idioma/acabado después de importar. Ver `CARDMARKET_EXPORT.md`. Pruebas: `BuyPolicyTest`, `BuylistDeviceTest` (emulador), `npm run test:browser`, `npx vitest run web/test/shoppingCloud.test.ts`. `ShoppingBidirectionalDeviceTest` usa archivos/prefs y ruta cloud shoppingTests aislados para editar/restaurar/conflictos/borrado; nunca inventario real. `ShoppingCloudDeviceTest` solo sube y verifica listas reales cuando se pasa explícitamente `-e verifyShoppingCloud true`; no crea inventario sintético.

- Aviso de precios al arrancar: `StartupPriceRefreshPolicy` usa `mtgjson_price_index.updated_at` (indexado exitoso), solo tras más de7 días o sin actualización previa. No renovar fecha al descartar aviso.
- Galería Triple: cargar `SelectedPrices.ensurePrintings` fuera de UI, con UUID exacto resuelto y acabado actual; no usar pares legacy sin proveedor. `TriplePricePopupTest` valida caché fría y +/−/foil con autocierre reiniciable de1s (solo emulador, modo diagnóstico sin inventario).

## Publicación web en GitHub Pages

- Destino: `C:/Users/nubor/Documents/GitHub/nuborisar.github.io/mtgbiblio`, remoto `asvgithub01/nuborisar.github.io`, rama `master`. URL: `https://asvgithub01.github.io/nuborisar.github.io/mtgbiblio/`. No tocar otras carpetas del sitio.
- Compilar con las seis variables públicas Firebase de `web/config.txt` y `npm --workspace @mtgfucker/web run build -- --base=/nuborisar.github.io/mtgbiblio/`. El script raíz `npm run build:web -- --base=...` NO reenvía correctamente ese argumento; verificar prefijo en dist/index.html.
- Publicación completa: copiar index/assets/data y web/public/ar (~610MB,653 paquetes). AR_PACKAGES_URL por defecto apunta a https://asvgithub01.github.io/nuborisar.github.io/mtgbiblio/ar. Validar bytes/SHA256 de binarios y metadatos; no modificar formato ni finales de línea de artefactos. Mantener todos los archivos referenciados por catalog.json.
- Firebase ya autoriza `asvgithub01.github.io`; no cambiar authDomain a Pages ni abrir reglas Firestore. Verificar Actions Pages y HTTP200 de HTML/JS/CSS/catálogo tras push; el login de cuenta queda para el usuario.

- Todas las pantallas nuevas deben conservar el fondo de carta compartido (`CollectionScreenBackground.install` después de setContentView); no usar fondo sólido en Buscar/Compras.
- Detalle: la altura de editionsBottomSheet debe ajustarse también al cambiar tamaño raíz/teclado, no solo en primera medida. Colecciones usa único Añadir→Biblio/Buylist y check de faltantes (no vuelve a añadir cartas poseídas).

## Coherencia visual y deuda de arquitectura

- Fondo de carta NO equivale a identidad visual completa. Antes de crear una pantalla, reutilizar o extraer cabecera, paneles glass, botones, tipografía, espaciado y estados del área existente; no montar una Activity de controles genéricos como diseño final.
- Validar capturas de la pantalla con datos y vacía, teclado visible/oculto y navegación/sheets antes de dar por terminada UI. Las pruebas funcionales no sustituyen esta revisión.
- Buscar y Compras reutilizan CollectionPopupChrome; Buscar declara también Theme.Mtg.CollectionPopup en AndroidManifest para conservar la Activity inferior visible (el overlay aplicado solo en onCreate no basta). Buylist usa ventana completa no flotante y header único con nota/precio; seleccionar todo es checkbox, historial al tocar precio. Trabajo posterior: extraer estado/presentación y casos de uso fuera de Activities de manera incremental. No reescribir escáneres ni persistencia sin pruebas de contrato y migración. Conservar Room+legacy y las invariantes de impresión/acabado/idioma/cantidad.


### Modo rápido EdScan aislado

- `EdScanFastActivity.kt`/`EdScanFastPolicy.kt`: entrada desde Triple, CameraX+motor
  nativo sin OCR/AR. Scope global explícito; no hereda preferencias de otros scanners.
  Dos lecturas .70/.08 por identidad canónica exacta (`EdScanFastIdentity`), con margen
  contra otra identidad, no otra impresión. Metadata local/caché en `EdScanFastCatalog`,
  sin red en cámara; desconocidos siguen siendo competidores y sin rival falla cerrado.
  Referencia provisional entre impresiones a menos de .08 del mejor: no-PLST primero,
  fecha descendente, precio conocido comparable del acabado ascendente, ID estable.
  No sumar scores, convertir precios ausentes en cero ni modificar políticas Triple.
  CameraX fast limita selección a lado mayor <=1280; fallback mínimo disponible,
  registrar dimensiones reales e `identityMs`. Sony v2 negocia720x960 en vertical.
  `scripts/evaluate_edfast_regions.py` es experimento offline: reconstruye referencias
  y consultas con la misma ROI; no usa índice full-card con query recortada ni autoriza altas.
  `--hybrid --index-dir <privado>` genera índices art/full separados: full-art y layouts
  especiales/desconocidos conservan carta entera; normal sin tratamientos usa ilustración.
  Nunca elegir ROI con el nombre esperado de la foto ni ordenar scores sin calibrar entre
  carriles; discrepancias fuertes quedan en conflicto. Índice piloto no es catálogo global.
- Altas únicamente nuevas mediante `TripleCollectionSession` (constructor original
  conservado), grupo EdScan Fast, edición provisional e idioma desconocido. Foto del
  mismo frame aceptado. No convertir metadatos de referencia en idioma físico.
  Límite8pendientes/fallos y reintento por mismo evento; retirar carta para otra copia.
- `edfast.frame`/`edfast.accept` y lifecycle en traza privada; `candidateSpanMs` no mide
  entrada física de la carta. Botón de fallo conserva hasta30JPEG privados en
  `files/edfast-diagnostics`, sin alta ni subida remota por pulsarlo.
- Pruebas `EdScanFastPolicyTest`, `EdScanFastIntegrationTest` y
  `EdScanFastPersistenceTest`: estas dos últimas solo emulador, inventario aislado.
  La prueba UI exige un frame inferido real, no basta ver pantalla de carga.
  `EdScanFastIdentityTest` cubre scores Sony y selección; `EdScanFastPhotoReplayTest`
  requiere cinco fotos privadas + `names.json` en cache/edfast-replay del emulador.
  No incluir fotos privadas en APK/Git ni ejecutar fixtures en Sony.
- Fast y el carril visual de Triple usan híbrido ART + FULL por defecto. El scheduler
  `EdScanFastAutoPolicy` alterna únicamente ART/UPPER tras dos fallos consecutivos o
  cuatro intentos sin confirmar; FULL se ejecuta siempre. Ausencia/lifecycle reinician ART.
  Cada cambio de perfil borra los votos de confirmación. Los modos manuales de Fast siguen
  disponibles como respaldo; no se cambian preferencias de OCR/AR ni el EdScan lento.
  Triple confirma identidad con `TripleIdentityConfirmation`/`EdScanFastIdentity`, como Fast.
  Un tracker independiente exige dos lecturas FULL con margen entre UUID para confirmar
  impresión. Recorte o empate de ediciones conservan `provisional=true`; no deducir idioma.
  `offerVisual` recibe prueba de identidad explícita por captura, nunca almacenada en Target;
  no confundirla con edición confirmada ni permitir que una lectura débil la herede.
  Mantiene scopes, validación fresca de resultados lentos y arbitraje OCR/AR.
  La traza `triple.hybrid` registra perfil, conflicto y ambos carriles para la siguiente auditoría.
  UPPER usa (0,0,1,.58), ART (.08,.15,.92,.55); FULL consulta referencias full-art/especiales.
  `FastRegionIndex` valida SHA, versión, ROI y orden de 110278 referencias. Solo Fast y
  Triple optan explícitamente por `configureFast`; otros scanners mantienen la ruta anterior.
  Un candidato FULL débil con margen separado puede mostrarse frente a recortes débiles,
  pero no se acepta sin score/margen y lecturas exigidos; dos identidades fuertes en conflicto
  devuelven hits vacíos. No comparar scores de recortes distintos para decidir un ganador.
- `scripts/build_edfast_region_index.py --work <privado> --model <milo> --workers 16`
  prepara ambos índices completos y reanudables; `evaluate_edfast_global.py` compara el
  corpus privado contra todos los competidores. Empaquetar rows.json, manifest.json,
  upper.f16.bin y art.f16.bin en cameraVision/src/main/assets/edfast-regions
  solo tras completar y verificar. Nunca sustituirlo por el piloto de 110 referencias.
- `EdScanFastRegionReplayTest` exige seis fotos privadas + expected.json en
  cache/edfast-region-replay del emulador. El modo recortado hace cuatro inferencias
  (dos orientaciones por carril) en ambos modos híbridos y en el AUTO actual.
  No prometer tiempos de Sony a partir del emulador. `EdScanFastAutoReplayTest` exige
  seis fotos (añade Bartolomé JP 1792157079658.jpg) y verifica dos lecturas híbridas ART. También exige cache/edfast-cycle-replay
  con mountain.jpg y monstrosaur.jpg (diagnósticos Sony 1792165833188/1792166399352),
  para probar cambios reales sin hueco manteniendo FULL y rescate ART→UPPER.
  `TripleHybridIntegrationTest` usa esos fixtures y la Activity real con persistencia diagnóstica
  deshabilitada, comprueba preparación real, ambos carriles y scopes con fotos. La cámara
  ARCore real necesita prueba física (emulador sin servicio ARCore). Solo emulador, nunca en Sony.
- `scripts/extend_edfast_showcase_index.py` añade la referencia LCI301 de Bartolomé
  f82bb486-7009-4b53-973f-477fc13ad7e7 al índice completo recortado, con hashes del
  índice base y foto pública de referencia. Excepción evaluada, NO relajar globalmente
  todos los layouts showcase. El resto de referencias full-art sigue completo.
  Precargar ART y UPPER antes de enlazar cámara evita leer sus matrices en los cambios
  de perfil; la caché adicional de ambos recortes ocupa unos 56 MB.

- AUTO precarga y conserva ambas matrices ART/UPPER antes de activar cámara; se invalidan
  al cambiar de índice y se liberan al cerrar motor. No leerlas de disco en cada vuelta.

- `TripleIdentityConfirmationTest` reproduce márgenes Sony de reimpresiones. Android:
  `TripleIdentityAcceptanceTest` comprueba altas provisionales, frescura, scope y AR sin
  prueba independiente; `EdScanFastPersistenceTest` valida también guardado Triple aislado.
  Todos estos tests solo en emulador. No ejecutar contra datos del Sony.

- Triple v8: `TripleFrameScheduler` reserva una sola segunda lectura de Arte tras un
  primer voto fuerte (`readings`), con descanso de 150 ms y devolución del turno OCR.
  No cambiar score/margen/dos votos ni frescura; no cambiar perfil de recorte entre esa pareja.
  `TriplePhotoLanguageReader` es una cola separada y acotada de fotos ya aceptadas,
  solo cuando OCR está habilitado y falta idioma. No controla `busy`/`ocrScheduled` ni crea
  identidades/copias. Galería muestra globo+spinner; termina en bandera o `?` si no hay
  evidencia suficiente. Exige título compatible con identidad y reglas sin conflicto.
  Callback por fila/foto/revisión, nunca por la carta actual; no sobreescribir idioma manual
  o conocido. Al cerrar Activity se descartan pendientes; la foto persistida permite revisión.
  Pruebas: `TriplePriorityAndPhotoLanguageTest` y `TriplePhotoLanguageTest` (emulador).

- `PostprocessCleanup` limpia fotos/diagnósticos EdScan de revisiones completas o cartas eliminadas, en WorkManager diferido y sin otros trabajos activos. No borra catálogo, inventario ni journals de recuperación; cualquier evidencia pendiente o referencia a otra biblioteca conserva la foto. Se programa tras guardado legacy, revisión y apertura del panel.
- Limpieza automática (también tras borrar cartas): silenciosa, último resultado consultable en
  `StorageActivity`. No reutilizar `storage_manual_results` ni activar progreso global desde el worker.
- Triple prioriza sets no promo únicamente en desempates provisionales de la misma identidad.
  `PromoSetCodes` contiene tipos promo reales de Scryfall (actualizar al ampliar índices), no prefijos.
  Conservar promociones confirmadas por FULL y umbrales; esta preferencia no se activa en Fast.

- Triple: nuevas filas empiezan normal; ni Target.finish ni cambiar edición eligen foil. El check manual controla selectedFinish; restaurar conserva el acabado histórico. Si la impresión no admite el acabado, FinishUnavailable impide guardar y solicita revisión. Contorno temprano usa callback onDetected opcional del motor, con la misma captura/proyección y guardas; no duplicar detección ni aceptar identidad desde ese callback.

- Triple `upright_only` (OFF por defecto): asume nombre arriba en frame vertical ya rotado, reordena el par de esquinas al borde superior antes del warp y omite orientación180 en FULL/ART/UPPER. Mantiene ambos carriles/umbrales y limpia evidencia al alternar. No activar por defecto ni usar para cartas invertidas; comparar ON/OFF en Sony, sin extrapolar tiempos del emulador.

- Triple: `TripleLaserView` es decorativo; flag `triple_scanner/laser_animation` OFF por defecto. No vincularlo al reconocimiento ni a sus votos. Detener animador al pausar/ocultar/desadjuntar; no dibujar el efecto en imágenes de análisis.
- Accesos de escaneo en Biblio: solo OCR original (fabOcrMlKit) y Triple (icono). Los FAB experimentales y el acceso Fast dentro de Triple quedan GONE, con sus handlers/Activities conservados; no reactivarlos desde showSelectedSection.


## Consulta P5 aislada (debug)

- Entrada: `PhotoPreviewActivity`, desde el shell Compose; `domain/photos`, `feature/photos` y `data/legacy/LegacyPhotoReader`. Solo lectura, biblioteca explícita.
- Evidencias de CardInfo por índice original (sin emparejar fotos de otra captura); fotos importadas de `photoScans.bin` en sección global separada. Backup legible no repara el primario. Errores independientes por sección.
- Fotos locales bajo `scan_evidence`/`photo_scans`, rutas canónicas verificadas, decodificación muestreada fuera del hilo principal sin caché persistida. Actualización manual o al volver; no es monitor de jobs.
- No modifica escáneres, guardados, procesamiento, workers ni formatos. No abre paneles legacy con efectos secundarios. No sustituye importación/revisión/resolución existentes ni expone diagnósticos de jobs EdScan: esa parte de P5 sigue pendiente.
- Validación específica: PhotoReadPolicyTest, PhotoViewModelTest, PhotoReadIntegrationTest y PhotoComposeTest; fixtures aislados, nunca datos del Sony.


## Consulta P6 aislada (debug)
- `ServicesPreviewActivity`, `feature/services`, `domain/services`, `data/legacy/LegacyServicesReader`:
  solo preferencias locales y presencia/tamaño de archivos registrados; no inferir salud de backups.
- No llamar servicios de sincronización, StorageData.snapshot, limpieza ni workers desde esta consulta.
  Autosync es una preferencia local, no estado Firebase. Bibliotecas sin Premium se muestran sin abrirlas.
- Validar con ServicesViewModelTest, ServicesReadIntegrationTest, ServicesComposeTest y ServicesActivityTest.


## Endurecimiento P7 sin modificar runtime
- `check_architecture_boundary.py` además impide dependencias directas de red/persistencia/jobs
  conocidas en domain/feature, y exige previews privadas solo en el manifiesto debug (sin aliases/filtros).
  Sigue siendo heurístico: no detecta reflexión, todas las extensiones ni efectos indirectos de APIs.
- Validar también los manifiestos generados, no solo fuentes:
  `bash gradlew :app:processDebugMainManifest :app:processReleaseMainManifest --console=plain`
  y `python scripts/check_architecture_boundary.py --merged-debug app/build/intermediates/merged_manifest/debug/AndroidManifest.xml --merged-release app/build/intermediates/merged_manifest/release/AndroidManifest.xml`.
  Un archivo generado ausente/ilegible falla; esto no sustituye compilar/probar una APK release.
- No retirar componentes legacy ni sus recursos mientras los incrementos Compose sigan siendo consultas.


## Módulos de arquitectura nuevos
- `core/domain`: biblioteca Kotlin/JVM (Java17), puertos/proyecciones/reglas nuevas y pruebas puras.
  Conserva paquetes `io.asv.mtgocr.ocrreader.domain.*`. No contiene Biblio/CardInfo ni DTO serializados.
  Los antiguos paths `app/src/.../domain` del mapa histórico ahora están bajo
  `core/domain/src/main/kotlin/io/asv/mtgocr/ocrreader/domain`.
- `core/designsystem`: biblioteca Android/Compose con `MtgTheme` y `LoadStatus`, sin dependencia de app/domain.
  `CardArtwork` sigue en app como adaptador a CardImageCache y recursos legacy; no mover esa caché.
- `app` depende de core y de los módulos de consulta indicados abajo; composition root,
  hosts/DI, adaptadores legacy, scanners y jobs siguen en app; las seis features Compose
  ya son módulos independientes (no equivale a migración funcional completa).
  No añadir dependencias inversas hacia app ni entre los dos core sin revisar el grafo.
- Validación añade `:core:domain:test` y `:core:designsystem:lintDebug` a las suites existentes.
  Tests puros movidos: `core/domain/build/test-results/test`; no contarlos dos veces como tests de app.
  Guard inspecciona ambas ubicaciones de dominio durante la transición y el manifiesto del design system.


## CameraVision: renombrado compatible
- Módulo Gradle y carpeta: `:cameraVision` / `cameraVision/`. Clases internas:
  `CameraVisionAssets`, `CameraVisionMath`, `CameraVisionAcceptanceGate`, `NativeCameraVisionEngine`.
- El namespace `io.asv.collectorvision`, nombres de Activities/workers, IDs XML y rutas
  privadas históricas se mantienen por compatibilidad. No sustituir esas cadenas masivamente.
- El origen upstream sigue siendo CollectorVision: NOTICE/LICENSE, URLs de modelos y SHA
  permanecen intactos. No afirmar que el cambio de nombre elimina esa dependencia de origen.
- Guard permite únicamente los renombres léxicos enumerados en `scripts/vision_rename_contract.py`;
  el resto del código protegido debe coincidir con baseline. No es una exención funcional.


## Módulos de consulta y lectura (sin migración de datos)
- `feature/catalog`, `feature/photos`, `feature/services`: bibliotecas Android/Compose,
  UI/ViewModels y recursos propios en seis idiomas. Dependen de core/domain y core/designsystem,
  no de app, otros features, datos concretos, cámaras o workers. Activities/DI permanecen en app.
- Los antiguos paths `app/src/.../feature/{catalog,photos,services}` ahora viven bajo
  `feature/<nombre>/src/main/kotlin/io/asv/mtgocr/ocrreader/feature/<nombre>`.
- Cinco textos compartidos viven en `core/designsystem/src/main/res/values*/common_strings.xml`.
  Usar R de su módulo propietario; no depender de R transitiva de app.
- `data/readers`: Kotlin/JVM, adaptador CallbackSetCatalogReader y utilidades PhotoPathPolicy /
  evidenceRecords. No contiene almacenes, DTO serializados, modelos legacy ni escritores.
  Fuentes Android y deserialización se quedan en app; no mover DataUtils/Biblio/CardInfo para
  forzar esta extracción. Los callbacks y rutas privadas conservan el contrato existente.
- Tests de políticas/callback en data/readers y ViewModels fotos/servicios en su feature.
  El test de catálogo con LegacySetSearchMatcher sigue en app como integración; el módulo
  añade pruebas con puertos falsos, sin dependencia inversa.
- Añadir a validación: `:data:readers:test`, `:feature:catalog:testDebugUnitTest`,
  `:feature:photos:testDebugUnitTest`, `:feature:services:testDebugUnitTest` y lintDebug de las
  tres features. Las pruebas Android de integración continúan en app, paquete architecture.
- El guard cubre fuentes/manifiestos extraídos y aristas de proyectos Gradle; es heurístico,
  no un parser Gradle ni análisis semántico de escrituras. No usarlo como único control.


## Launcher, Biblio y detalle como módulos
- `feature/launcher`, `feature/library`, `feature/carddetail`: UI/estado y recursos propios;
  las fuentes `app/src/.../feature` de esas áreas están ahora en `feature/<nombre>/src/main/kotlin`.
  ViewModels/tests de launcher/library se trasladan sin cambiar contenido. Detalle sigue solo lectura.
- Arte se inyecta mediante slots obligatorios; LibraryShell inyecta también el detalle y recibe
  etiquetas de destinos. No importar otro feature ni su R para componer navegación.
- `app/ui/LegacyFeatureContent.kt` conecta branding/etiquetas legacy, CardArtwork y detalle.
  MainActivity/BiblioShellActivity suministran esos adaptadores. CardArtwork y CardImageCache
  permanecen intactos en app; conservar recorte CENTER_CROP y FIT_CENTER del detalle.
- No mover recursos baseline compartidos con escáneres: nombre, fuente y etiquetas históricas
  llegan al launcher mediante LauncherBranding. Strings nuevos sí pertenecen a su módulo.
- Validar también `:feature:launcher:testDebugUnitTest`, `:feature:library:testDebugUnitTest`,
  lintDebug de los tres módulos y pruebas Android architecture de app. No contar tests movidos
  dos veces. Detalle se valida en instrumentación (no tiene ViewModel/unitarias propios).
- CameraVision: generar embeddings de nuevas cartas NO es entrenar Milo/Cornelius. La futura
  autonomía de modelos/índices se especifica en ARCHITECTURE_MIGRATION_PLAN.md; no cambiar
  URLs, índices, pesos o procesamiento como parte de la extracción de UI.


## Coordinación de lecturas extraída (data/readers)
- `data/readers/.../data/library/ReadOnlyLibraryShellRepository.kt` coordina consultas
  por ID explícito y verifica biblioteca activa antes/después. Su puerto
  LibraryShellReadSource devuelve proyecciones, nunca Biblio/DeckCatalog serializados.
- `LegacyLibraryShellRepository` conserva constructor/API y delega; su fuente privada
  Android mantiene DataUtils.readSerializable, filenames/backup y FileObserver existentes.
  No usar DeckCatalogStore.load ni reparar el primario desde una consulta.
- `data/readers/.../data/services/ReadOnlyServicesReader.kt` coordina estado local;
  ServicesReadSource solo aporta preferencias, referencias y tamaños. Convenciones de archivos
  y validación canónica permanecen en AndroidServicesReadSource dentro de app.
- Preservar orden de llamadas, fallos y dispatcher: biblioteca activa → colección → mazos →
  activa; servicios Premium → bibliotecas/archivos → moneda → preferencia autosync.
  Esto NO es un snapshot transaccional ni resuelve la carrera de guardados OCR.
- Tests JVM de ambos coordinadores en data/readers; integración de archivos/backup/prefs
  en LibraryShellIntegrationTest y ServicesReadIntegrationTest de app. Sin cambios de formatos.
