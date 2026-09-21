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
  también habilitado. Módulos Gradle: `app` y `liquid-glass`.
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
| Scanner, navegación, colección y sesión | `OcrCaptureActivity.java`; layout `ocr_capture.xml` |
| Nuevo escáner automático de edición | `ExperimentalCardScanActivity.kt`, `OpenCvCardDetector.kt`, `AutoCaptureStability.kt`, `SetSymbolShapeMatcher.kt`, `PrintingLineOcr.kt`, `CardTitleOcr.kt`; layout `activity_experimental_card_scan.xml` |
| Hash con OCR/símbolo opcionales | `HashScanAnalysis.kt`, `HashScanEvidence.kt`, `HashPrintingVariantPolicy.kt`; controles, sesión y metadatos de captura en `RapidEditionScanActivity.kt`/`activity_rapid_edition_scan.xml`. Ver `ART_HASH_EVALUATION.md`; no atribuir al hash una edición exacta. |
| Prueba de escáner solo por hash | `HashOnlyScanActivity.kt` reutiliza la captura/rectificación de `RapidEditionScanActivity.kt`, salta OCR/red y ejecuta `ArtHashMatcher.kt`/`ArtHashIndex.kt`; tercer FAB en `ocr_capture.xml` |
| Identificación experimental por arte | `ArtHashIndex.kt`, `ArtHashMatcher.kt`, `ArtPrintingIndex.kt`, `RapidEditionScanActivity.kt`; índices APK `art_hash_index.bin`/`art_printing_index.bin`, generadores `scripts/build_art_hash_asset.py`/`scripts/build_art_printing_asset.py`, prueba Sony `app/src/androidTest/java/io/asv/mtgocr/ocrreader/ArtHashDeviceEvaluationTest.kt`; corpus y muestras fuera de Git en `../mtgfucker-art-hash-data/` |
| OCR y cámara | `ScanLanguagePolicy.kt`, `CardTextLanguageDetector.kt`, `MlKitOcrDetectorProcessor.java`, `MlKitTextDetector.java`, `CardScanStability.kt`, `ScannerSettings.kt`, `ui/camera/` |
| Filas y total de sesión | `ScanSessionAdapter.java`, `ScanSessionCounts.kt`, `ScanSessionSort.kt`, `ScanSessionRefreshCoordinator.kt`; layout `scan_session_item.xml` |
| Detalle de carta y ediciones | `Main2Activity.kt` (incluye `EditionAdapter`); layouts `activity_main2.xml`, `edition_item.xml` |
| Buscador compartido de ediciones | `EditionSearchAdapter.kt` (incluye política `EditionSearch`), `EditionPicker.kt`, `SetSymbolLoader.kt` |
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
- El autoañadido por hash se bloquea si el nombre OCR no coincide con el nombre del arte. Nunca
  preseleccionar automáticamente LEA, LEB, ARN, ATQ, LEG ni DRK; elegir otra impresión segura.
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
- Cada autoañadido hash conserva JSON de evidencias y la foto rectificada en las listas emparejadas
  de `CardInfo`; las fotos viven bajo `files/scan_evidence/`. Copiar las listas en
  `snapshotForPersistence` y borrar la foto correspondiente al deshacer ese escaneo.
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
