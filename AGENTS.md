# Guía rápida para agentes

## Cómo empezar sin recorrer todo el proyecto

- Responder en español y hacer el cambio mínimo que resuelva la petición.
- Consultar `git status --short` y la rama actual antes de editar. No sobrescribir ni
  incluir en commits cambios ajenos, especialmente `.idea/` y `*.iml`.
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
| Sincronización cloud | `CloudLibrarySync.kt`, `CloudAccountActivity.kt`; consultar `FIREBASE_SETUP.md` |

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
- Al cambiar URL/impresión, mostrar `CardLoadingDrawable` en lugar del arte anterior; conservar
  la imagen solo en rebinds de la misma URL. Mientras se resuelve imagen localizada, cancelar
  la carga vieja y proteger callbacks con generación+UUID. Un error termina el loading.
- Los controles superiores del scanner se posicionan con `OcrTitleRegion.cardForFrame`.
  Mantener sincronizada esta geometría con la cámara y el overlay; comprobar en pantalla
  pequeña/teclado visible. La sesión ocupa todo el ancho y conserva padding interno.
- El borrado desde sesión, incluido reducir la última copia, requiere confirmación.
- La capa de preparación del índice está en `scanIndexPreparation`, con fondo `#D9000000`
  (~85 % de opacidad). No aplicar alpha a todo el contenedor: también atenuaría el texto.

## Compilar y validar

Desde la raíz, con Java 17 y `sdk.dir` válido en `local.properties`:

```sh
bash gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain
git diff --check
# Prueba concreta cuando el cambio sea acotado:
bash gradlew :app:testDebugUnitTest --tests '*EditionSearchTest'
```

- Usar `bash gradlew`: el wrapper puede no tener permiso de ejecución.
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
  `~/.codex/backups/mtgfucker/sony-20260916-115903/`. Incluye TAR de `databases`, `files`
  y `shared_prefs`, APK anterior y nueva, hashes originales/restaurados y README. Los 8.495
  archivos restaurados coincidieron byte a byte antes de abrir la app; SQLite pasó
  `quick_check`. La reinstalación cambió el UID de `u0_a866` a `u0_a867`; `tar -xof -`
  conservó correctamente el propietario nuevo. Tras abrir la Biblio se verificó la
  migración Room 4→5, las columnas `mcmId`/`mcmMetaId`, la colección visible y la sesión
  Firebase restaurada.
- Para backups consistentes, detener primero la app. Incluir WAL/SHM de SQLite y los archivos
  legacy, no solo `mtg_catalog.db`. No publicar backups ni credenciales en Git.
- `run-as` permite copiar/restaurar en este debug. Al restaurar un TAR usar `tar -xof -`
  para conservar el nuevo propietario y verificar hashes antes del primer arranque.
