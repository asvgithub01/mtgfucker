# Contratos de la migración y validación P0/P1/P2

Base: master 2d8a4ad. Rama codex/compose-solid-architecture. Actualización: 21-10-2026.

## Frontera protegida

check_architecture_boundary.py compara los blobs Git de todas las fuentes runtime
existentes de app/src/main, collectorvision-native y liquid-glass/src/main con el
baseline. Solo MainActivity.java tiene excepción: se sustituye por MainActivity.kt
con el mismo nombre de clase. activity_main.xml queda intacto aunque ya no se infla.
No hay excepciones para scanner, modelos serializados, Room, preferencias,
persistencia, workers, loaders de imágenes ni recursos antiguos.

El gate también prohíbe imports Android/modelos legacy en domain y repositorios
concretos/bridge en feature. No sustituye revisión de código ni prueba física.

## Inventario de rutas y propiedad del estado

| Origen/destino | Contrato existente que se mantiene | Validación de esta entrega |
| --- | --- | --- |
| Splash → MainActivity | Clase y manifiesto iguales; splash muestra BuildConfig.GIT_BRANCH | Fuente splash/manifiesto protegida |
| MainActivity → OcrCaptureActivity/Biblio | App.INTENT_PERSISTOR_MODE = "0", autofocus/flash de ScannerSettings, askStartupPriceRefresh | Intent instrumentado; destino interceptado, sin iniciar cámara |
| Launcher: escoger Biblio | LibraryCatalog.availableLibraries/active/select; mismos IDs, archivos y preferencias | Integración en namespace aislado, Premium activado/desactivado |
| Launcher: fondo | LaunchBackgroundPolicy, pin por collectionItemId, LaunchArtworkUrl; fallback mtgback | Pin probado y captura visual de Activity real |
| Biblio → OCR | fabOcrMlKit controla sesión/cámara en el host existente | Sin cambios; gate de fuentes |
| Biblio → Triple | Intent TripleScanActivity; no RESULT_OK nuevo | Sin cambios; gate de fuentes |
| Biblio → escáneres experimentales | Handlers y Activities conservados; accesos ocultos siguen ocultos | Sin cambios; gate de fuentes |
| Rapid → Biblio | RC_RAPID_EDITION_SCAN y EXTRA_SESSION_CARD_IDS; resuelve por identidad | Sin cambios; contrato leído en onActivityResult |
| EditionScan → Biblio | RC_SCAN_EDITION; setCode, printingUuid, finish, language; nombre pendiente | Sin cambios; contrato leído en onActivityResult |
| Biblio/sets → detalle | Main2Activity: cardName, collectionItemId, cardDetailPopup; browseOnly | Inventario estático; UI sigue legacy |
| Detalle → sets | SetCollectionActivity: setCode, setName | Inventario estático; UI sigue legacy |
| Detalle → imagen | CardImageActivity: imagen + metadatos por impresión; arrays de ediciones | Inventario estático; UI sigue legacy |
| Biblio → grupos/mazos | GroupBuilderActivity y archivos de catálogo legacy | Inventario estático; UI sigue legacy |
| Biblio → fotos/revisión | PhotoImport, PhotoGallery, PhotoScanDetail(photoScanId), ScanReview(review_group) | Inventario estático; workers/almacenes intactos |
| Biblio → ajustes/servicios | StorageActivity, ArDownloadsActivity, CloudAccountActivity, BuylistActivity; ajustes generales embebidos | Inventario estático; UI sigue legacy |
| Exportación Cardmarket | ACTION_CREATE_DOCUMENT/RC_SAVE_CARDMARKET_CSV; escribe URI elegida | Sin cambios; no exportación ejecutada |

La nueva pantalla no es todavía el shell P3. Seguir entrando por el host original
preserva biblioteca, sesiones y navegación sin cambiar ni un método del scanner.
La vuelta al launcher vuelve a consultar repositorio en RESUMED. La recarga de
colección al regresar al host OCR sigue siendo su onResume original.

## Persistencia y seguridad

- CollectionReader es exclusivamente una proyección de lectura; no serializarla.
- LegacyLauncherRepository solo escribe la preferencia de selección al seleccionar
  explícitamente. Cargar pantalla, recrearla y leer fondo no escriben inventario.
- Si existen primaria/backup ilegibles, no habilitar entrada al host: el host legacy
  crea una colección cuando su lectura devuelve null. Recuperación pendiente se
  muestra como error, nunca como colección vacía.
- Los tests de IO usan ContextWrapper con applicationContext propio, archivos
  sintéticos en cache y preferencias prefijadas por UUID. No invocan la activación
  Premium que conecta Firebase. No leen ni escriben datos del Sony.
- Room: fixtures sintéticos de esquema v1 y migraciones originales construyen
  cada versión inicial 1–7. La apertura Room v8 valida esquema y conserva identidad,
  impresión, acabado y precio. Las bases de prueba se eliminan por nombre único.
- Fixture Serializable fijo de master continúa legible y sus bytes no se alteran
  al proyectar. Esto no constituye certificación de todos los binarios históricos.

## Arquitectura implementada

- Puertos domain: CollectionReader, LauncherRepository y SetCatalogReader.
- Adaptadores: colección legacy detached, launcher IO/Mutex, catálogo callback.
- Catálogo usa solo loadSetCatalog (terminal). Callback duplicado/tardío se descarta;
  cancelación no produce resultados, errores no se convierten en listas vacías.
  loadCard multientrega NO se envuelve como terminal: futura migración de detalle
  debe exponer flujo y preservar todas las entregas.
- ViewModel del launcher: StateFlow, carga single-flight, estado de error, bloqueo
  de doble tap y SavedStateHandle para pricePromptSent. Factories con CreationExtras.
- Compose: Route recoge estado con lifecycle; Screen solo estado/eventos; tema
  adaptado de MagicPalette, CardArtwork y LoadStatus reutilizables.
- CardArtwork usa un ImageView acotado mediante AndroidView para reutilizar
  CardImageCache/Glide y sus placeholders/cancelación. No es una pantalla XML.
  Se libera Glide al salir; no se introduce un segundo caché.
- Las seis traducciones nuevas están en launcher_strings.xml, sin editar strings
  del scanner. Conservadas las dependencias Kotlin/Compose compiler/BOM existentes;
  añadidos lifecycle 2.6.2 y dependencias de prueba compatibles.

## Resultados reproducibles

Java 17 local; bash de Git en Windows. No usar el bash WSL del PATH.

    bash gradlew :app:testDebugUnitTest :collectorvision-native:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
    python scripts/test_architecture_boundary.py
    python scripts/check_architecture_boundary.py
    git diff --check

Resultados: 869 tests unitarios, cero fallos; 8 tests Python; APK app/test generadas.
En emulator-5554 (sdk_gphone_x86_64), install -r de ambas APK tras build completo y:

    adb -s emulator-5554 shell am instrument -w -r -e package io.asv.mtgocr.ocrreader.architecture io.asv.mtgocr.ocrreader.test/androidx.test.runner.AndroidJUnitRunner

Resultado: 8 tests instrumentados, incluyendo Room 1–7, corrupción/backup/Premium,
Compose, Activity real con recreación y pantalla 320×480dp con fuente al 200 %.
La navegación al scanner se intercepta deliberadamente. Captura de launcher revisada
en build/architecture/launcher-compose.png; no hay botón cortado ni solapamientos.

Lint sigue devolviendo 29 errores heredados en fuentes protegidas; no se suprime
ninguna regla. Los errores nuevos introducidos durante desarrollo fueron corregidos.
Logs finales en build/architecture/p2-build.log, p2-instrumentation.log y p2-lint.log.

## Límites y gate siguiente

P0/P1 tienen implementada y probada la base necesaria para el launcher; P2 está
implementada. No equivale a certificar todos los flujos de la app.
Antes de P3 siguen siendo obligatorios: paridad completa del shell, retorno con
escaneo real, concurrencia de escritores y rollback. La validación física de cámara,
muerte real del proceso en dispositivo, rendimiento de colecciones grandes y todos
los idiomas/teclados queda pendiente. La recreación y SavedStateHandle sí se probaron.

Los 29 errores lint heredados permanecen como deuda explícita. No marcar la calidad
global como verde ni tocar scanners para eliminarlos sin ampliar el alcance.
No se ha instalado en Sony, modificado datos del usuario, fusionado a master ni hecho push.
