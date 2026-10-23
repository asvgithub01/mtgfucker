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

    bash gradlew :app:testDebugUnitTest :cameraVision:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
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

## P3 — Primer incremento, vista previa solo lectura (21-10-2026)

El launcher debug ofrece una entrada secundaria a BiblioShellActivity; release no
registra esa Activity. La entrada principal conserva la Biblio completa y sus funciones.
El shell NO sustituye aún ninguna operación de escritura, valoración o escaneo.

- Puerto LibraryShellRepository y proyección por libraryId explícito; lector IO sin
  DeckCatalogStore.load (ese método migra grupos y guarda Biblio). Catálogo primario
  y backup leídos sin reparación; ilegible se distingue de inexistente.
- ViewModel con cancelación/generación, SavedStateHandle y cálculo en Default;
  no publica otra biblioteca ni respuestas obsoletas. Recarga al volver a RESUMED.
- Cartas por collectionItemId, grupos separados de mazos, mazos vacíos conservados,
  cantidades Long y sideboard. Orden estable reciente, filtro por nombre/set sin
  acentos, detalle de solo lectura. No serializar estas proyecciones.
- Cabecera y contenido desplazables, componentes/tema comunes y seis traducciones.

Validación: 880 tests unitarios, 12 instrumentados de arquitectura en emulator-5554,
8 tests Python y guard de fuentes protegidas sin cambios. APK app/test generadas.
Pruebas nuevas cubren no migración/reescritura de inventario y catálogo, backup
corrupto, eventos UI, pantalla 320x480 con fuente 200 %, entrada/salida real del
preview y comparación byte a byte de archivos Json/backup. Captura revisada:
build/architecture/p3-shell.png. El escenario real usa biblioteca vacía del emulador;
las pruebas de contenido usan fixtures sintéticos, no datos del usuario.
Lint: mismos 29 errores en archivos cuyo hash coincide con master; cero archivos
nuevos/modificados con errores. No equivale a lint verde.
Logs: p3-final-build.log, p3-instrumentation-final.log, p3-lint.log en build/architecture.

Pendiente para cerrar P3: comandos y doble persistencia, crear/renombrar/borrar
grupos/mazos, edición/selección/borrado de cartas y confirmaciones, totales de precio,
paridad de gestos/filtros, bridges OCR/Triple con reconciliación real, concurrencia,
reinicio/rollback y rendimiento con colecciones grandes. No activar el shell por
defecto hasta superar esos gates. No se ha tocado scanner ni instalado en Sony.

## P3 — Preparación de mutaciones y retorno (22-10-2026)

- LibraryMutation / LegacyLibraryMutation preparan cambios por ID: cantidad positiva,
  condición válida, borrado confirmado, asignación, renombrado, retirada y borrado
  confirmado de grupos. Borrar un grupo no borra cartas ni mazos homónimos.
  Grupos legacy vacíos no existen como entidades: crearlos exige asignar cartas.
- Se trabaja sobre el grafo Serializable completo separado, nunca una proyección
  de UI. El round-trip conserva incluso listas null históricas; getters mutables
  se consultan sobre copias. No se borran evidencias ni fotos. Las pruebas comparan
  bytes de filas ajenas y usan el fixture binario de master.
- El preparador NO está conectado a un escritor, a Room ni a botones de edición:
  no afirmar que escritura/edición/borrado desde Compose estén ya disponibles.
- El shell ofrece Biblio completa/OCR y Triple con los Intents existentes y sin
  cambiar biblioteca activa. Biblio/OCR abre su host habitual (no se inventa un extra
  para entrar directamente en cámara). Conserva todas las funciones legacy.
- ActivityResult invalida el snapshot; RESUMED registra FileObserver sobre el
  directorio (DataUtils publica por rename), con debounce/conflación y baja al salir.
  Esto recoge publicaciones posteriores al retorno; no presupone un resultado de
  escaneo ni usa posiciones como identidades. Una biblioteca distinta se rechaza.

**Bloqueo de activación de escrituras:** OcrCaptureActivity.onPause encola snapshots;
onDestroy ejecuta shutdown, no awaitTermination. Un guardado completo viejo puede
publicarse después de una edición nueva. El synchronized de DataUtils solo serializa
la publicación; una comprobación optimista antes del guardado no impide ese overwrite
posterior. No resolverlo con sleeps ni activar escrituras por asumir que la Activity
destruida dejó de escribir. Se ha planteado reforzar persistencia compartida sin
editar fuentes scanner; no se ha aplicado todavía ese refuerzo ni ampliado el guard.

Validación del incremento: tests de preparación/preservación, eventos tardíos del
ViewModel, FileObserver con publicación atómica real en directorio sintético, y
botones/retorno con Intents de ambos scanners interceptados. Esto NO sustituye la
prueba física de escaneo, concurrencia real, reinicio y doble persistencia.
Logs locales: build/architecture/p3-return-final-build.log,
p3-return-instrumentation.log, p3-return-lint.log.
Resultado final: 890 unitarias, 14 instrumentadas en emulator-5554 y 8 tests del
guard, sin fallos. APK debug y de pruebas compiladas; captura p3-return-shell.png
revisada. Lint mantiene 29 errores heredados, ninguno en fuentes nuevas/modificadas.
Sin instalación en Sony, acceso a sus datos, push ni cambios en fuentes scanner.

## P4 lectura y P7 fronteras — 22-10-2026

Con la restricción de no alterar scanners **ni sus guardados**, se completan estos
subconjuntos independientes, no las fases enteras:

- SetCatalogPreviewActivity, solo debug y accesible desde el shell, muestra sets
  ordenados por fecha con búsqueda por código/nombre, detalle de metadatos, estados
  vacío/error/reintento y restauración de filtro/selección. Selección por código,
  no por posición; no modifica owned printing.
- SetCatalogViewModel depende de puertos. LegacySetSearchMatcher delega la misma
  EditionSearch de los scanners sin editarla. CatalogDependencies reutiliza el
  loadSetCatalog existente: puede refrescar la caché Room de sets, no inventario.
  No se modifican repositorio, proveedor, schema, writers ni pantallas antiguas.
- CollectionCardDetail reutilizable: arte sin recorte, UUID, set/número, idioma,
  acabado, condición, cantidades, grupos/mazos/sideboard, descripción, registros
  de escaneo y precio **guardado**, sin actualizarlo. No ofrece edición; ampliar
  CollectionRow no convierte la proyección en modelo persistente.
- Guard P7 reforzado para aliases, wildcard, imports Java static, referencias
  completas dentro de métodos y dependencias de modelos/composición en feature.
  Nuevos tests confirman que DataUtils/Biblio siguen congelados igual que scanners.
  Conserva la única excepción previa de MainActivity.java.

Validación: 895 unitarias, 19 instrumentadas de arquitectura en emulator-5554 y
13 tests Python, todas verdes; APK app/test compiladas y diff-check limpio.
La Activity real del catálogo se abre/recrea, conserva consulta y se comparan
archivos Json/backup byte a byte y owned printings antes/después. El test permite
carga o error recuperable según disponibilidad de red/caché. UI con fuente200%,
eventos por código y ausencia de arte/precio anterior al cambiar detalle probados.
Captura de catálogo con datos sintéticos revisada en build/architecture/p4-catalog.png.
Lint conserva los mismos 29 errores heredados (hashes master), ninguno en archivos
nuevos/modificados; no es una validación lint global verde.
Logs: p4-catalog-state.log, p4-catalog-ui.log, p4-detail.log, p4-p7-final-build.log,
p4-p7-instrumentation.log y p4-p7-lint.log dentro de build/architecture.

### Cuánto queda

No queda solo limpieza final. Hay **cinco bloques mayores abiertos**:
1. P3: publicación segura de comandos, UI de edición/borrado y doble persistencia.
   Diferido por la restricción sobre guardados compartidos.
2. P4: catálogo de cartas/buscador completo, selector de impresiones y reemplazo
   compatible del detalle editable; consulta de sets/detalle guardado ya disponible.
3. P5: fotos, galería y revisión. Puede adelantarse consulta de evidencias; los
   comandos y workers requieren validar su interacción con almacenamiento.
4. P6: ajustes, servicios, bibliotecas, exportación y contratos cloud/Premium.
5. P7: retirada segura de pantallas duplicadas, modularización y validación final
   de todos los recorridos. Guard reforzado completado; no se retira código legacy.

P2 está implementada; P0/P1 aportan la base validada para el launcher, no una
certificación global de cámara/dispositivo. No se da porcentaje temporal ni fecha
fiable: los bloques tienen tamaños distintos y el cierre total depende del alcance
que se retome más adelante sobre persistencia compartida. Próximo bloque aislable:
consulta de fotos/evidencias sin modificar trabajos ni archivos.


## Consulta P5 aislada (debug)

- Entrada: `PhotoPreviewActivity`, desde el shell Compose; `domain/photos`, `feature/photos` y `data/legacy/LegacyPhotoReader`. Solo lectura, biblioteca explícita.
- Evidencias de CardInfo por índice original (sin emparejar fotos de otra captura); fotos importadas de `photoScans.bin` en sección global separada. Backup legible no repara el primario. Errores independientes por sección.
- Fotos locales bajo `scan_evidence`/`photo_scans`, rutas canónicas verificadas, decodificación muestreada fuera del hilo principal sin caché persistida. Actualización manual o al volver; no es monitor de jobs.
- No modifica escáneres, guardados, procesamiento, workers ni formatos. No abre paneles legacy con efectos secundarios. No sustituye importación/revisión/resolución existentes ni expone diagnósticos de jobs EdScan: esa parte de P5 sigue pendiente.
- Validación específica: PhotoReadPolicyTest, PhotoViewModelTest, PhotoReadIntegrationTest y PhotoComposeTest; fixtures aislados, nunca datos del Sony.


## P6: observación local sin efectos de servicios
- Ninguna consulta/recomposición cambia preferencias, archivos, selección activa o Premium.
- No se inicializa Firebase ni se consulta sesión remota; no hay red, limpieza o descargas.
- Los tamaños de archivos son informativos y no validación de backups. No deserializar ni reparar.
- Tests aislados preservan archivos corruptos, backup sin primario, bibliotecas no accesibles sin Premium,
  selección/pin y preferencias desconocidas. La Activity real se recrea/refresca sin modificar datos.


## P7: límites verificables y limitaciones
El guard conserva la misma baseline y única excepción MainActivity.java. Revisa fuentes nuevas
Kotlin/Java de domain/feature contra referencias cualificadas a WorkManager, Room, DataStore,
Firebase, clientes HTTP, archivos/red Java/Kotlin, SQLite y APIs de preferencias/descargas.
Los puertos/adaptadores son la frontera: no prohíbe que los adaptadores legítimos hagan su trabajo.
Es léxico; no detecta todos los accesos indirectos, reflexión, interpolación o APIs nuevas.
Las previews deben existir una vez en debug, sin exportación ni intent-filters/aliases; ningún otro
source set o manifiesto release fusionado debe registrarlas. Los archivos de manifiesto ausentes
producen error. El guard no verifica todo el comportamiento del manifest merger.


## Frontera física core (P7)
- `:core:domain` compila en JVM sin Android ni app. Conserva los contratos y paquetes;
  sus proyecciones NO son modelos serializados ni reemplazos de Biblio/CardInfo.
- `:core:designsystem` no conoce dominio ni app: solo tema/status parametrizados.
  CardArtwork permanece en app para conservar intacto el acceso a caché legacy.
- app depende de core, nunca al revés. No cambian applicationId, namespace app, nombres
  de Activities, manifiesto principal, recursos históricos, firmas ni rutas de datos.
- Tests puros LibraryPresentationTest viven en core/domain; los de integración siguen
  en app y prueban los módulos empaquetados juntos. Guard léxico ampliado a fuentes core.

- Toolchain y versiones Compose se mantienen. Lint del design system termina sin errores,
  con avisos de BOM antiguo y `ObsoleteLintCustomCheck`: parte de los checks Compose del
  runtime no se ejecuta con esta combinación existente de herramientas. No se han suprimido
  avisos ni actualizado dependencias de la app para ocultarlos. App conserva errores lint heredados.


## Contrato CameraVision sin migración de datos
La excepción autorizada cambia el nombre físico/Gradle del módulo y cuatro tipos internos.
No cambia paquetes públicos, nombres de workers almacenados por WorkManager, Activities,
claves de preferencias/trabajos, rutas de modelos/fotos, URLs, versiones o hashes de assets.
`vision_rename_contract.py` transforma solo identificadores fuera de strings/comentarios;
el guard calcula el contenido esperado desde master sin permitir cambios de comportamiento.
La atribución y licencia del upstream permanecen en `cameraVision/NOTICE.md` y `LICENSE`.
No hay dependencia Gradle externa llamada CollectorVision; sí adaptación de código y descarga
inicial de modelos/catálogo de ese proyecto. Renombrar no elimina esas dependencias.


## Frontera física de features y lectura (segundo corte P7)
- Feature catalog/photos/services compilan de forma independiente de app con contratos domain
  y UI de designsystem. No dependen de implementaciones de data ni entre sí. Hosts/Intents/DI
  quedan en app; previews siguen privadas debug y ausentes de release.
- Data readers compila como JVM sin Android: CallbackSetCatalogReader, PhotoPathPolicy,
  evidenceRecords. Se amplía visibilidad de estos puntos para consumo entre módulos, sin
  cambiar algoritmos. Los modelos serializados, deserialización, guardados, jobs y scanners
  no se extraen ni modifican. No es una extracción completa de la capa de datos.
- Los tres ViewModels se trasladan sin cambios de contenido; recursos en seis idiomas
  conservan nombre/texto. Solo cinco strings compartidos pasan al design system; las
  referencias R se ajustan al namespace dueño sin duplicar traducciones ni UI.
- Tests de integración catálogo que usan EditionSearch/LegacySetSearchMatcher siguen app;
  nuevas pruebas de módulo inyectan puertos. El guard revisa las nuevas ubicaciones,
  manifiestos, referencias entre capas y aristas Gradle declaradas explícitamente.
- Reconocimiento CameraVision es ONNX local; su preparación descarga modelos/catálogo de
  hanclinto.github.io/CollectorVision si falta una copia válida. No es una API remota de
  reconocimiento. La demo WebView histórica permanece sin acceso habitual; no afirmar
  que toda la aplicación sea offline (hay servicios separados como Scryfall).

Validación del segundo corte: **903 unitarias** (825 app, 61 CameraVision, 6 domain,
5 readers y 2 por feature), **29 instrumentadas architecture** en emulator-5554 y
**34 tests Python del guard**, sin fallos. Guard de fuentes y manifiestos fusionados
correcto; 29 comprobaciones de paridad preservan recursos traducidos, ViewModels y
pruebas trasladadas. APK debug/androidTest construidas; release unsigned también generada
(no instalada ni validada en dispositivo). Lint de las tres features y designsystem sin
errores, con avisos de versiones/plurales y compatibilidad de checks Compose heredados.
Lint app sigue en 29 errores/540 avisos de fuentes protegidas, no es globalmente verde.
Sin cambios ni instalación en emulator-5556/Sony. Pruebas físicas de cámara pendientes.
Logs locales: build/architecture/modules-{data-build,features-build,full-build,device,
lint-release,release-confirmed}.log. No se borraron datos ni se desinstaló la app.


## Tercer corte: launcher / shell Biblio / detalle
- Extraídos :feature:launcher, :feature:library y :feature:carddetail; todos dependen solo de
  domain/designsystem. Ninguna dependencia entre features o hacia app/data/cameraVision.
- Composition root suministra arte y detalle con slots Composable obligatorios; no defaults
  que invoquen caché global ni placeholders vacíos que oculten falta de configuración.
  Branding histórico y etiquetas de destinos se resuelven en app/ui/LegacyFeatureContent.
- Misma selección por collectionItemId, lifecycle RESUMED, SavedStateHandle y navegación.
  ViewModels y pruebas JVM trasladados sin cambios de contenido; seis idiomas preservados.
- CardArtwork, CardImageCache, escáneres, serialización, guardados y procesamiento sin cambios.
  No reemplazar detalle editable legacy ni activar shell por defecto.

Validación tercer corte: 903 unitarias (811 app, 61 CameraVision, 6 domain, 5 readers,
8 launcher, 6 library y 2 en cada feature catalog/photos/services); 31 instrumentadas
architecture en emulator-5554, incluidas composición real de arte/branding y detalle
inyectado con ID estable; 35 tests Python del guard, todos correctos. Diez comprobaciones
de paridad preservan ViewModels/tests y textos de seis idiomas. APK debug/test y release
unsigned construidas; release no instalada/probada. Lint features sin errores, con avisos;
app mantiene 29 errores heredados y 534 avisos (el reparto cambia al mover fuentes).
Sin supresiones nuevas. Guard runtime y manifiestos fusionados verde. Cámara física pendiente.
Logs: build/architecture/shell-modules-{first,full,device,lint-release,release-confirmed}.log.


## Puertos de coordinación de consultas (cuarto corte)
- data/readers implementa la composición de las lecturas Biblio y servicios mediante puertos
  JVM; app conserva los accesos concretos y las fachadas con firmas originales.
- Biblio: mismo ID explícito para colección/mazos, misma comprobación antes/después, ausencia
  distinta de corrupción, fallos cortocircuitados en el mismo orden. Cambios delegan al mismo
  Flow/FileObserver, sin activar lecturas ni escrituras durante la construcción.
- Servicios: misma secuencia Premium, listado registrado, tamaño de primario/backup, moneda
  y preferencia autosync. Bibliotecas inaccesibles siguen visibles. null/0/tamaños Long se
  conservan; no verificar/reparar contenido ni inferir salud del backup.
- Ambas lecturas en dispatcher IO; sin nuevos clientes de red, jobs, bases o cachés. Los DTO
  serializados, bytes de archivos, claves de preferencias y bibliotecas activas no cambian.
- Estas garantías no hacen atómicas las consultas ni resuelven las escrituras OCR pendientes.

Validación cuarto corte: **916 unitarias** (18 readers, incluidas 13 nuevas; resto sin
cambios), **32 instrumentadas architecture** en emulator-5554 y **36 tests Python** del guard,
todos correctos. Caso nuevo de biblioteca no predeterminada conserva preferencias y todos
los archivos, incluido primario corrupto con backup legible y biblioteca ajena intacta.
APK debug/test/release unsigned construidas; release no instalada ni validada físicamente.
Guard sobre fuentes y manifiestos fusionados correcto; lint app mantiene 29 errores y
534 avisos heredados en fuentes protegidas, sin supresiones. No instalación en5556/Sony.
Logs locales: build/architecture/read-ports-{first,unit,full,device,lint-release,release-confirmed}.log.


### Fuente de fotos aislada (2026-10-23)
- PhotoReadSource entrega solo proyecciones de evidencia/importación. ReadOnlyPhotoReader
  coordina en dispatcher inyectado y conserva fallos independientes por sección.
- AndroidLegacyPhotoSource conserva íntegros los cuerpos de lectura anteriores (solo
  cambia visibilidad y nombre del parámetro); LegacyPhotoReader mantiene constructor/API.
- No cambian deserialización, rutas/backup, orden, imágenes, formatos ni políticas de identidad.
- Seis pruebas JVM nuevas: orden y scope, fallos de cada sección/ambas, carga diferida,
  dispatcher/refresco y conservación de error explícito. Integración ampliada a biblioteca
  no activa con primario corrupto y backup válido, archivos y preferencias idénticos al terminar.
- Validación: 922 pruebas JVM sin fallos; debug, AndroidTest y release unsigned construidas.
  Guard de fuentes/manifiestos y 36 pruebas del guard correctos. Lint app continúa con los
  mismos 29 errores/534 warnings heredados; no se suprimen ni se modifica código protegido.
- Instrumentación: 32 pruebas architecture correctas en emulator-5554; instalación con
  install -r, sin desinstalar ni borrar datos. Ningún dispositivo físico modificado.


### Colección: puerto de lectura y proyección legacy (2026-10-23)
- ReadOnlyCollectionReader en data/readers coordina carga por ID, errores, unicidad y
  lista publicada inmutable. No conoce Biblio, CardInfo, Android ni persistencia concreta.
- LegacyCollectionProjectionSource en app crea snapshot detached y entrega filas diferidas:
  misma validación de identidad y mismos getters/campos en el mismo orden que antes.
  La validación de duplicados precede al mapeo; se detiene al primer fallo, sin resultado parcial.
- LegacyCollectionReader conserva API; AndroidLegacyCollectionSource y su lectura primary/backup,
  DataUtils, modelos serializados, Room, escáneres, escritores y jobs no se modifican.
- Siete pruebas JVM nuevas cubren scope/orden, Missing frente a vacío y fallo, duplicados sin
  mapearlos, error por fila sin consumir siguientes, dispatcher/refresco, lista inmutable y
  propagación de errores/cancelación. Las pruebas legacy de bytes serializados, identidad,
  variantes y fixture binario histórico siguen pasando sin cambios.
- Validación JVM completa: 929 pruebas correctas; APK debug, AndroidTest y release unsigned
  construidas. Guard de fuentes y manifiestos correcto y 36 pruebas del guard superadas.
- Instrumentación: 33 pruebas architecture correctas en emulator-5554. La nueva prueba
  comprueba lectura de biblioteca no activa, variantes/grupos/mazos/banquillo, backup válido
  con primario corrupto y conservación exacta de archivos y preferencias. Install -r; sin
  desinstalar, sin borrar datos y sin modificar dispositivos físicos.
- Lint app mantiene 29 errores/534 warnings heredados; sin supresiones ni cambios protegidos.
  Pruebas físicas de regresión pendientes; no afirmar migración funcional completa.


### Adaptadores Android y lectura de metadatos (2026-10-23)
- AndroidLibraryShellReadSource y AndroidServicesReadSource separados de las fachadas,
  sin nuevas dependencias ni cambios de API de los consumidores. Ambos siguen en app.
- PrivateLibraryFileReader en data/readers extrae exactamente la consulta de tamaño/ruta:
  raíz resuelta cada vez, padre canónico directo, null para ausente/directorio y 0 para vacío.
  No deserializa, crea, repara o elimina archivos; tamaño no indica validez de backup.
- Paridad textual comprobada del cuerpo de la fuente shell y de lectura de tamaño (salvo
  visibilidad y raíz inyectada). No se toca selección del launcher, persistencia ni jobs.
- Siete pruebas JVM nuevas cubren ausencia/directorio/vacío/backup, escape y subdirectorios,
  normalización canónica, raíz dinámica/diferida, raíz ausente y propagación de error.
- Validación: 936 pruebas JVM sin fallos; APK debug, AndroidTest y release unsigned construidas.
  Guard runtime/fuentes y sus 36 pruebas correctos. No se modifica ni silencia código protegido.
- Manifiestos debug/release verificados. 34 pruebas architecture pasan en emulator-5554;
  caso nuevo rechaza symlink hacia fuera y traversal conservando enlace y bytes originales.
  Instalación install -r sin desinstalar ni borrar datos; dispositivos físicos no modificados.
- Lint app sigue en 29 errores/534 warnings heredados. Regresión física pendiente.


### Observación de biblioteca desacoplada (2026-10-23)
- observedLibraryChanges/LibraryDirectoryObserver en data/readers separan coordinación
  de la API FileObserver Android. LegacyLibraryChanges conserva firma y máscara de eventos.
- Mismo contrato: flujo frío, registro por collector, filtro de nombres, señal inicial tras
  registro, buffer conflado y debounce150ms; stop al cancelar. No se añaden lecturas/guardados.
- Siete pruebas nuevas JVM: registro diferido/señal inicial, filtro primary/backup/null,
  ráfagas/debounce, aislamiento entre collectors, cancelación con callback tardío, evento
  síncrono durante registro y propagación de fallo de registro.
- 943 pruebas JVM correctas; debug, AndroidTest y release unsigned construidas.
  Guard de fuentes y 36 pruebas del guard correctos. No cambia procesamiento ni persistencia.
- Manifiestos debug/release verificados. 34 pruebas architecture correctas en emulator-5554,
  incluida publicación tardía por rename y conservación de bytes. Install -r sin desinstalar
  ni borrar datos. Sony conectado durante validación pero no modificado.
- Lint app mantiene 29 errores/534 warnings heredados. Regresión física pendiente.
