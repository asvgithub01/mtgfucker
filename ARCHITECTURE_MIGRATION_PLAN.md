# Plan de arquitectura: Kotlin, Compose, reutilización y SOLID

Fecha: 21-10-2026. Base auditada: master `2d8a4adb17fba50a17f26e260775e165414f4d72`.
Rama: `codex/compose-solid-architecture`.
Estado: **base P0/P1 implementada y validada para launcher; P2 Compose implementada**.
Contratos, resultados y límites: `ARCHITECTURE_CONTRACTS.md`. No implica cerrar
los gates físicos/globales de la migración ni corregir el lint heredado.

## 1. Objetivo y límites

Migrar toda la experiencia **no escáner** a Kotlin/Compose y separar presentación,
reglas de negocio y acceso a datos. Mantener los escáneres Views/XML operativos,
sin convertirlos, moverlos ni refactorizar su cámara, reconocimiento, sesión o configuración.

No equivale a «cero Java/cero XML»: el Java del escáner y los DTO de serialización
son excepciones explícitas. La petición actual sustituye el objetivo antiguo de
convertir también el Java OCR. Tampoco abarca web, extensión ni scraper Itaca.

Entrega inicial: diagnóstico y plan. Tras autorización del usuario se inicia
implementación incremental. No actualizar herramientas, instalar APK, tocar datos
del Sony ni publicar cambios durante esta primera entrega.

## 2. Diagnóstico sobre el código actual

Inventario de app/src/main/java/io/asv/mtgocr/ocrreader: 31 archivos Java,
283 Kotlin. Son archivos, no funcionalidades ni pruebas ejecutadas.
Tres módulos: app, liquid-glass, collectorvision-native.
Room versión 8; migraciones registradas de 1 a 8.

| Evidencia | Problema arquitectónico | Prioridad |
| --- | --- | --- |
| OcrCaptureActivity.java, 4.789 líneas; mBiblio estático, showSelectedSection, guardados y onActivityResult | Shell, biblioteca y cámara comparten propietario y ciclo de vida | Crítica |
| LegacyCollectionStore importa OcrCaptureActivity y actualiza mBiblio; GroupBuilderActivity también | Datos dependen de presentación; nuevas pantallas no pueden reutilizarlo sin frontera explícita | Crítica |
| Biblio.java y CardInfo.java implementan Serializable con serialVersionUID explícito | Mover clases o convertir campos puede romper bibliotecas aunque compile | Crítica |
| CardRepository.kt, 1.299 líneas; catálogo, precios, selección, imágenes, OCR y múltiples ejecutores | API demasiado amplia; sustituir ejecutores indiscriminadamente afecta latencia del escáner | Alta |
| Main2Activity.kt, 748 líneas; selección, condición, copias, idioma y adaptador | UI y comandos de negocio mezclados; operaciones repetidas y difíciles de probar | Alta |
| Solo SplashActivity contiene @Composable/setContent | Habilitar Compose en Gradle no implica una arquitectura Compose | Alta |
| ViewModel localizado en PhotoImportActivity y ScanReviewPanel; sin StateFlow encontrado en app | Estado y trabajo ligados principalmente a Activities/callbacks | Alta |
| MainActivity.java selecciona biblioteca, lee serializados, elige arte y navega | Buen primer corte vertical para probar el patrón nuevo | Media |

Estos son indicios estructurales, no afirmaciones de fallos observados en dispositivo.
Existen políticas puras y pruebas útiles: EditionSearch, LaunchBackgroundPolicy,
ReviewPresentationPolicy, PriceCurrency y políticas de identidad. Reutilizar antes
de duplicar reglas. No hacer una conversión automática masiva Java → Kotlin.

### Alcance revisado y límites de la auditoría

Inspección de manifiesto, Gradle, splash, launcher, firmas/rutas del monolito,
repositorio, almacén legacy y consumidores de detalle, sets y grupos.
No se han leído todos los motores de reconocimiento, ejecutado pruebas ni medido rendimiento.
El antecedente de septiembre (94 tests, Room antiguo, menos archivos) no es la línea base actual.

## 3. Arquitectura destino

Primero paquetes dentro de app; extraer módulos solo cuando las dependencias estén saneadas:

    app/di                 Composition root, factories y dependencias Android
    app/navigation         Rutas, argumentos por ID y lanzadores legacy
    core/model             Modelos Kotlin inmutables, sin Views ni entidades Room
    core/designsystem      Tema, tipografía, espaciado y componentes Compose
    core/testing           Fakes y fixtures sintéticos
    domain/collection      Puertos y operaciones de colección
    domain/catalog         Consulta, impresión y precios
    domain/account         Contratos de cuenta/Premium
    data/legacy            Adaptadores del almacenamiento existente
    data/catalog           Adaptadores de CardRepository
    data/settings          Preferencias compatibles con las claves actuales
    feature/launcher
    feature/library
    feature/groups
    feature/catalog
    feature/carddetail
    feature/photos
    feature/review
    feature/buylist
    feature/settings
    feature/account
    legacybridge           Única frontera autorizada con Activities/estado legacy

Son ubicaciones propuestas, no clases ya creadas. Evitar mover archivos legacy
en la primera fase: conservar nombres completos, extras, recursos y referencias Java.

Dependencias de código nuevas:

    Screen → ViewModel → puerto/caso de uso ← adaptador de datos
    app/di → implementaciones concretas
    app/navigation → legacybridge → Activities existentes

Domain no importa Android, Compose, Room, Firebase ni OcrCaptureActivity.
Los módulos actuales liquid-glass y collectorvision-native se conservan.
El segundo queda fuera de la migración.

### SOLID aplicado, sin sobreingeniería

- **SRP:** Screen dibuja; ViewModel transforma acciones en estado; el adaptador hace IO.
- **OCP:** proveedores de precio/imagen se intercambian detrás de contratos pequeños,
  sin introducir switches de proveedor en composables.
- **LSP:** fakes y adaptadores respetan los mismos errores, identidad y cancelación;
  probar esos contratos, no solo que implementan la interfaz.
- **ISP:** CollectionReader/CollectionCommands, CatalogSearch, EditionSelection,
  PriceReader e ImageResolver; no crear otro MegaRepository.
- **DIP:** inyección por constructor y factories desde AppContainer inicialmente.
  No exigir Hilt ni cambiar el toolchain para obtener inyección.

Crear casos de uso donde hay reglas u orquestación compartida:
ChangePrinting, ChangeFinish, ChangeLanguage, RemoveCopies, ExportCollection.
No una clase vacía por cada getter.

### Patrón de presentación

Cada feature tiene Route (integración), Screen (estado + callbacks),
ViewModel, UiState inmutable y componentes específicos.
ViewModel expone StateFlow; Route recoge con ciclo de vida.
Estado transitorio visual con remember/rememberSaveable; argumentos pequeños y
filtros restaurables con SavedStateHandle. No guardar bibliotecas completas en Bundle.
Las operaciones importantes se reflejan en estado persistente/confirmable, no en
eventos de un solo disparo que puedan perderse al recrear la Activity.

No IO en composición ni repositorios/contextos globales en Screen.
Claves de listas por collectionItemId; callbacks por identidad, nunca por posición.
ViewModel por pantalla, no un AppViewModel monolítico.
Resources para todos los textos y las seis traducciones existentes; accesibilidad,
semántica, teclado, insets y escalado de fuente desde el primer corte.

### Reutilización con límites

Tema Compose que adapte MagicPalette sin modificar el tema de los scanners.
Componentes: CardArtwork, CardTile, EditionTile, PriceLabel, FinishSelector,
LanguageSelector, ConditionSelector, QuantityControl, Loading/Empty/Error y
ConfirmDeleteDialog. Parámetros tipados, Modifier y eventos; no acceso a repositorio.
Mantener variantes realmente comunes, no un componente universal con veinte booleanos.
Imagen: placeholder inmediato al cambiar UUID/URL, cancelar trabajo anterior,
proteger respuesta por generación y cerrar loading también en error.
Los adaptadores Views existentes siguen disponibles para los escáneres.

## 4. Frontera con los escáneres (condición de aceptación)

Congelar OcrCaptureActivity.java y ocr_capture.xml completos durante este plan:
aunque contienen pantallas no OCR, «extraerlas» editando el monolito violaría el límite.
Construir un shell paralelo no escáner, sin borrar las vistas antiguas.

Congelar también las Activities de captura (Triple, Edition, Experimental, Rapid,
EdScanFast, EdScannerAr, Cornelius, CollectorVisionLab, Rules, HashOnly), sus layouts,
ui/camera, analizadores, políticas de reconocimiento, overlays, sesiones y el módulo
collectorvision-native. El inventario de protección debe incluir sus dependencias
compartidas sensibles, no limitarse a un glob *Scan*.

El bridge usa Intents/extras ya existentes. Antes de activar el shell:
1. Caracterizar cómo se entra hoy en OCR/Triple, qué biblioteca toman y cómo vuelven.
2. No inventar un RESULT_OK: varios flujos guardan directamente y no devuelven resultado.
3. Al volver, recargar la biblioteca persistida mediante el adaptador con su ID explícito.
4. Evitar escrituras de la UI nueva mientras se cede el control al scanner.
5. No reactivar accesos experimentales ocultos: mantener OCR y Triple visibles.
6. Si entrar directamente a OCR requiere cambiar la Activity congelada, conservar
   temporalmente la navegación legacy y registrar el bloqueo; no saltarse la restricción.

La arquitectura nueva no depende de mBiblio. El adaptador de compatibilidad puede
mantenerlo sincronizado por las rutas existentes; esa deuda queda aislada y documentada.
No prometer eliminar todo acoplamiento del código viejo sin permiso para modificarlo.

Foto/revisión: migrar la UI de galería, importación y postprocesado, pero conservar
workers, captura, evidencias, reanálisis y paneles embebidos usados por scanners.
Si un panel es compartido, implementar una presentación nueva sobre políticas comunes,
sin reemplazar el panel legacy en los escáneres.

## 5. Datos, concurrencia y compatibilidad

- Mantener inicialmente Room + legacy y sus rutas de actualización. No migrar
  formato de datos a la vez que navegación y UI.
- Mapear CardInfo/Biblio a snapshots Kotlin; no exponer objetos mutables serializados
  a Compose ni renombrar clases/campos serializados.
- Puertos reciben libraryId y collectionItemId. Validar biblioteca activa antes de
  aplicar resultados tardíos; UUID, acabado, idioma y cantidad siguen separados.
- Selección de impresión coordina CardRepository.selectEdition con actualización
  legacy existente. Un Mutex nuevo no hace atómicos SQLite y archivos ni coordina
  automáticamente escritores antiguos: caracterizar orden, fallo parcial y recuperación
  antes de permitir edición desde el shell.
- Serializar comandos nuevos por biblioteca; preservar workers y ejecutores existentes.
  Probar carreras con cloud y postprocesado: no asumir que desaparecen al cerrar scanner.
- Wrappers suspend para callbacks terminales; flujos para APIs multientrega.
  CardRepository.loadCard puede entregar catálogo y luego precios: no envolverlo
  ingenuamente con una continuación que se reanuda dos veces.
- Cancelar Future si existe; en APIs sin cancelación descartar callbacks obsoletos
  con token de solicitud. No reemplazar los pools del reconocimiento.
- Mantener backfill Cardmarket por UUID exacto y persistencia previa a cloud.
  No cambiar política Premium, claves de preferencias, nombres de archivos ni autoridades.
- La migración definitiva de almacenamiento es un proyecto posterior: requiere
  fixtures, backups y permiso para adaptar todos los escritores, incluidos escáneres.
  No es requisito para declarar migrada la UI no escáner.

## 6. Plan de ejecución por entregas pequeñas

Cada fase se divide en PRs compilables. No mezclar cambio visual, herramienta y
formato de datos en un mismo PR. Casillas pendientes salvo revisión documental.

### P0 — Línea base y contratos (antes de funcionalidad)
- [x] Crear rama aislada desde master e inventariar acoplamientos.
- [x] Compilar baseline, ejecutar suites y guardar resultados actuales (846 tests).
- [x] Enumerar pantallas/rutas, extras, modos y dependencias compartidas de scanners.
- [x] Capturar contratos estáticos de flujos actuales: selección de Biblio, colección, grupos, detalle,
  exportación, fotos, cuenta y retorno de OCR/Triple.
  Inventario en ARCHITECTURE_CONTRACTS.md; la prueba física end-to-end queda pendiente.
- [x] Fixtures sintéticos de bibliotecas serializadas y Room; nunca datos privados en Git.
  Binario master fijo y migraciones Room 1–7→8 comprobadas con datos sintéticos.
- [x] Guard de diff para archivos congelados y baseline de imports legacy permitidos.
Salida: reproducibilidad, contratos de navegación/datos probados y lista de protección revisada.

### P1 — Fundaciones y adaptadores
Depende de P0.
- [x] Composición por feature, puertos mínimos, snapshots, dispatchers inyectables y fakes.
- [x] Adaptadores de lectura/selección de biblioteca y catálogo sobre APIs actuales.
- [x] Tema Compose y estados comunes; añadir dependencias lifecycle/testing compatibles
  con Kotlin 1.9.22, Compose compiler 1.5.8 y BOM actual tras verificar resolución.
- [x] Pruebas de contratos, callbacks duplicados/tardíos, cancelación y biblioteca cambiada.
  loadCard multientrega se mantiene sin adaptar; el nuevo puerto usa loadSetCatalog terminal.
Salida: Screen de prueba renderiza sin Activity legacy ni IO; scanner sin cambios.

### P2 — Primer corte: launcher
Depende de P1.
- [x] Reemplazar MainActivity.java por host Kotlin conservando nombre completo e Intents.
- [x] LauncherRoute/Screen/ViewModel: bibliotecas, fondo, Premium y entrada.
- [x] Mantener LaunchBackgroundPolicy, fondo fijado y prompt de precios una sola vez.
- [x] Splash mantiene BuildConfig.GIT_BRANCH; ya muestra ramas no master/main.
Salida: paridad de selección, restauración y navegación; tests Compose y rotación.
No cambiar el destino de Biblio al nuevo shell hasta P3.

### P3 — Shell, biblioteca y grupos
Depende de P2 y del contrato scanner P0.
- [x] Primer incremento: shell opt-in debug, solo lectura, cartas/grupos/mazos,
  búsqueda y orden reciente/nombre. Validado sin activar reemplazo de Biblio.
  P3 permanece abierta: los criterios siguientes requieren paridad de operaciones.
- [x] Segundo incremento: reglas de edición/borrado y grupos sobre grafo separado
  con pruebas de preservación; todavía SIN publicación. Puentes a Biblio/OCR y Triple,
  recarga al volver y observación de guardados tardíos, con Intents interceptados en tests.
- [ ] Resolver la coordinación compartida de guardados completos legacy antes de
  activar comandos: shutdown del executor OCR no garantiza que se haya vaciado.
- [ ] Host Compose no escáner con navegación por IDs.
- [ ] Biblioteca y grupos/mazos reutilizando reglas, no copiando el monolito.
- [ ] Conservar orden reciente, rejillas, filtros, totales, selección y confirmaciones
  de borrado por ID, incluido gesto/mantenimiento donde corresponda.
- [ ] Adaptadores de comandos con doble persistencia y guardas de biblioteca.
- [ ] Entry points de OCR/Triple vía bridge; reconciliación al volver.
Salida: abrir, escanear y volver sin duplicar/pisotear filas; ambas representaciones
persistidas coinciden después de reinicio. Si falla, mantener shell desactivado.

### P4 — Catálogo y detalle
Depende de P3.
- [x] Consulta aislada opt-in debug: catálogo de sets Compose con búsqueda compartida,
  estado restaurable/error/retry; detalle de carta guardada con impresión, arte, precio
  y evidencias, sin solicitudes de precio ni cambios en colección.
  Este subconjunto de lectura no activa los comandos pendientes de P3.
- [ ] SetCollectionActivity, CardSearchActivity, Main2Activity y CardImageActivity
  como wrappers compatibles o destinos Compose.
- [ ] Un solo selector Compose de ediciones, miniaturas por impresión y búsqueda común.
- [ ] Casos de uso de edición/acabado/idioma/condición/copias y precios coherentes.
Salida: mismas cantidades/grupos; ningún precio ni arte de impresión anterior tras cambio.
Los selectores Views consumidos por scanners permanecen intactos.

### P5 — Fotos y postprocesado
- [x] Consulta opt-in debug de fotos globales y evidencias por biblioteca, sin guardados ni jobs.
Depende de P4.
- [ ] PhotoImportActivity, PhotoGalleryActivity, PhotoScanDetailActivity y
  ScanReviewActivity: UI Compose, progreso/restauración y presentación de evidencias.
- [ ] Reutilizar workers/almacenes existentes; no relanzar trabajos por recomposición.
- [ ] Separar UI de ScanReviewPanel sin modificar su uso legacy.
Salida: importación/revisión cancelable, errores recuperables, ninguna foto o cola perdida.

### P6 — Ajustes, servicios y cierre funcional
- [x] Consulta opt-in debug local de Premium/moneda/preferencia autosync y archivos de todas las bibliotecas, sin cambiar ajustes ni inicializar Firebase.
Depende de P3; integrar después de P5 para validar el recorrido completo.
- [ ] BuylistActivity, CloudAccountActivity, StorageActivity, ArDownloadsActivity.
- [ ] Ajustes generales del shell, bibliotecas y exportación Cardmarket.
- [ ] Mantener contratos Firebase/Premium, limpieza silenciosa, paquetes AR y monedas.
- [ ] Mantener configuración específica de escáner dentro de sus pantallas existentes.
Salida: todas las rutas no escáner cubiertas, offline y fallos de red incluidos.

### P7 — Orden final y endurecimiento
- [x] Endurecimiento seguro: guard de dependencias externas con efectos en domain/feature;
  previews privadas debug, sin aliases/filtros y ausentes del manifiesto release generado.
  Sin cambios runtime ni retirada de funciones. Guard heurístico, no garantía semántica.
Depende de P2–P6.
- [ ] Retirar Activities/adaptadores/layouts no escáner solo tras demostrar cero
  consumidores legacy; conservar wrappers si los Intents existentes los necesitan.
- [ ] Convertir Java no protegido restante a Kotlin con pruebas; dejar DTO binarios.
- [x] Reglas automáticas para las nuevas capas domain/feature: límites de imports y
  referencias cualificadas Kotlin/Java; acceso legacy solo por composición/adaptadores.
  No sustituye análisis semántico ni revisión de futuras excepciones.
- [ ] Extraer módulos core/domain/designsystem únicamente si no crea ciclos;
  adaptadores que dependen de la Activity legacy permanecen en app.
- [ ] Actualizar mapa AGENTS y documentación de excepciones.
Salida: definición de terminado completa. Toolchain nuevo en una iniciativa separada.

## 7. Validación y rollback

Comandos base (Java 17 y SDK válido; ejecutar desde este worktree):

    bash gradlew :app:testDebugUnitTest :cameraVision:testDebugUnitTest :app:assembleDebug --console=plain
    bash gradlew :app:lintDebug --console=plain
    git diff --check

Pruebas instrumentadas en emulador seleccionado y limpio, no en Sony:
configurar explícitamente ANDROID_SERIAL y ejecutar :app:connectedDebugAndroidTest.
Añadir compose-ui-test-junit4 y manifiesto debug de tests en P1.
No usar el total histórico de tests como resultado de esta rama.

Matriz obligatoria:
- Serialización antigua → lectura/escritura → lectura: IDs, cantidades, grupos,
  idiomas, acabados y fotos iguales; Room migration tests con fixtures.
- Comandos de edición: éxito, fallo de Room, fallo de archivo, reintento y recuperación.
- Rotación/muerte de proceso, cambio de Biblio, back stack, offline y callbacks tardíos.
- Composición/recomposición no dispara altas, pagos, exports ni workers duplicados.
- Colecciones grandes: medir arranque, scroll, memoria y tiempo de retorno al scanner
  contra P0 en mismo dispositivo/corpus; fijar tolerancias antes del cambio, no inventar mejoras.
- Accesibilidad, seis idiomas, teclado visible, pantalla pequeña y fuentes grandes.
- Smoke OCR/Triple: entrada, guardado, vuelta y estado; cámara/AR físico queda pendiente
  hasta validación autorizada en dispositivo.

Rollback por corte:
- Mantener rutas legacy mientras una feature no cumpla sus gates.
- P3 introduce un selector local de shell para desarrollo, apagado hasta validación;
  un rollback devuelve navegación, no convierte datos de vuelta.
- Sin cambios de esquema/serialización, revertir un PR no debe requerir borrar nada.
- Si hay divergencia de persistencia, parar el despliegue y recuperar copia verificada;
  nunca desinstalar, vaciar Room o cambiar credenciales como atajo.

## 8. Definición de terminado y excepciones

1. Toda UI no escáner activa usa Kotlin/Compose, con inventario de rutas validado.
2. Nuevas pantallas solo dependen de puertos; ningún acceso directo a mBiblio,
   DAO, serialización, Firebase o CardRepository.get desde composables.
3. ViewModels probados con fakes y pruebas de integración de adaptadores.
4. Reglas de edición/precio/cantidad tienen un propietario reutilizable.
5. Cero cambios en archivos protegidos del escáner; sin regresiones de sus contratos.
6. Datos antiguos legibles, doble persistencia consistente y recuperación verificada.
7. Build/tests/lint sin regresiones nuevas, instrumentadas verdes y validación física anotada.
8. Excepciones registradas: scanner Java/XML, DTO Serializable, wrappers de Activities,
   puente estático y vistas legacy inaccesibles desde el shell pero necesarias para OCR.

No declarar «todo migrado» si una ruta no escáner sigue activa en Views sin excepción
explicada. No borrar el contenido no OCR del monolito para mejorar métricas:
su extracción definitiva necesitaría ampliar explícitamente el permiso del usuario.

## 9. Referencias

La propuesta usa separación de UI/datos, flujo unidireccional, ViewModel y recogida
de estado con ciclo de vida, coherente con las
[recomendaciones oficiales Android](https://developer.android.com/topic/architecture/recommendations).
Las fronteras congeladas, fases y excepciones son decisiones específicas de este proyecto,
no requisitos de Android. No se propone instalar las versiones «latest» sin verificar
compatibilidad con el toolchain existente.

## 10. Primera implementación (21-10-2026, estado histórico del commit 2d061e0)

Completado, sin modificar ninguna fuente runtime que ya existiera en master:
- Puerto CollectionReader y proyección Kotlin de solo lectura por libraryId explícito.
- LegacyCollectionReader con dispatcher inyectable, snapshots separados de modelos
  mutables y listas no modificables. No ofrece comandos de escritura.
- AndroidLegacyCollectionSource reutiliza lectura primaria/backup de DataUtils.
  No usa mBiblio, no cambia biblioteca activa y no confunde archivo ilegible con vacío.
- Identidades ausentes/duplicadas se rechazan sin reparación automática ni guardado.
- CollectionDependencies es la raíz de composición nueva; todavía no conectada al runtime.
- Fixture binario sintético generado con las clases compiladas de master 2d8a4ad;
  prueba de round-trip, serialVersionUID, evidencias y datos de carta.
- Guard conservador de fuentes existentes: scripts/check_architecture_boundary.py.
  Protege todo app/src/main, collectorvision-native y liquid-glass/src/main del baseline.
  Archivos nuevos permitidos; no hay excepciones para modificar fuentes antiguas.
  Antes de migrar MainActivity debe añadirse su excepción explícita revisada.

Validación ejecutada:
- Baseline: assembleDebug + tests de app y collectorvision-native, 846 tests.
- P1: assembleDebug generado y 858 tests, cero fallos/errores.
- Cinco tests Python del guard: limpio, edición, staging, borrado y archivo nuevo.
- Guard sobre el worktree: fuentes protegidas sin cambios.
- Lint final: 29 errores en archivos existentes, todos contrastados por hash con master.
  Categorías NewApi, UnsafeOptInUsageError, UseAppTint y MissingTranslation.
  Ningún error restante señala archivos nuevos. No se han suprimido checks ni creado
  baseline de lint para ocultarlos. La tarea combinada termina fallida por lint,
  aunque compilación/APK y pruebas se completan.
- Logs locales ignorados: build/architecture/baseline-build.log,
  p1-final-build.log y p1-final-validation.log. Informe lint en app/build/reports.

Pendiente antes de avanzar/activar pantallas:
1. P0: inventario de Intents/rutas, pruebas de integración de lectura primaria/backup,
   selección de biblioteca y fixtures Room; validación de ciclo de vida/retorno de scanners.
2. P1: puertos de launcher/catálogo, ViewModels/factories y tema/componentes Compose.
3. P2: launcher Kotlin/Compose, conservando contrato con el scanner.
4. Resolver o acordar tratamiento del lint heredado sin ampliar unilateralmente el
   alcance sobre escáneres. No afirmar que P0 o P1 completos están cerrados.

No se ha instalado APK, ejecutado instrumentación ni accedido a datos del dispositivo.
La proyección es parcial para presentación: jamás serializarla para sustituir Biblio,
porque no representa todos sus campos persistentes.


## Consulta P5 aislada (debug)

- Entrada: `PhotoPreviewActivity`, desde el shell Compose; `domain/photos`, `feature/photos` y `data/legacy/LegacyPhotoReader`. Solo lectura, biblioteca explícita.
- Evidencias de CardInfo por índice original (sin emparejar fotos de otra captura); fotos importadas de `photoScans.bin` en sección global separada. Backup legible no repara el primario. Errores independientes por sección.
- Fotos locales bajo `scan_evidence`/`photo_scans`, rutas canónicas verificadas, decodificación muestreada fuera del hilo principal sin caché persistida. Actualización manual o al volver; no es monitor de jobs.
- No modifica escáneres, guardados, procesamiento, workers ni formatos. No abre paneles legacy con efectos secundarios. No sustituye importación/revisión/resolución existentes ni expone diagnósticos de jobs EdScan: esa parte de P5 sigue pendiente.
- Validación específica: PhotoReadPolicyTest, PhotoViewModelTest, PhotoReadIntegrationTest y PhotoComposeTest; fixtures aislados, nunca datos del Sony.


## Estado tras el incremento seguro P6

P0/P1 base y launcher P2 implementados; P3–P6 tienen incrementos de consulta,
no equivalen a fases completas. Quedan cinco bloques de cierre:
1. P3: escrituras/edición/borrado y activación del shell; requiere resolver la carrera
   de guardados legacy. No permitido bajo la restricción actual.
2. P4: resto de catálogo, búsqueda y detalle editable/selector de ediciones.
3. P5: importación, revisión/resolución y UI completa de postprocesado.
4. P6: Buylist, cuenta cloud, gestión de almacenamiento/AR, ajustes editables y exportación.
5. P7: retirada segura de UI antigua, modularización y regresión final/dispositivo.

No estimar finalización por número de fase: gran parte de las operaciones de escritura
sigue en legacy. No es posible cerrar toda la migración manteniendo indefinidamente
congelada su coordinación de persistencia. Los escáneres siguen fuera del refactor.

### Contrato de consulta P6
`ServicesPreviewActivity` solo debug desde shell; `ServicesReader` →
`LegacyServicesReader` → `ServicesViewModel`/`ServicesScreen`. Consultas en IO,
refresh explícito/RESUMED, cancelación y generación, error sin estado anterior.
No llama StorageData.snapshot (incluye planificación de limpieza), CloudLibrarySync,
Firebase, workers, descargas ni Activities de servicios. Lee la preferencia local
`cloud_library_sync/auto_sync_enabled` (default true) sin inferir sesión/backup remoto.
Muestra bibliotecas registradas incluso si Premium no permite abrirlas; no selecciona
ni crea ninguna. Tamaño/presencia de primario y .bak no certifican integridad ni
permiten restauración. Moneda usa getter legacy, sin actualización de cotizaciones.


### Pendiente tras P7 seguro (no es cierre de migración)
- Se conservan todas las Activities/layouts/adaptadores legacy y almacenes: no se han
  demostrado cero consumidores y siguen proporcionando operaciones no migradas.
- P3: coordinación de guardados legacy y comandos reales; P4: catálogo/búsqueda/detalle editable;
  P5: importación/revisión/resolución; P6: operaciones Buylist/cloud/almacenamiento/AR/exportación.
- P7: extracción de módulos sin ciclos, retirada de UI no escáner únicamente tras paridad funcional,
  regresión completa en dispositivo y validación release. No modificar formatos binarios.
- Generar y verificar un manifiesto release no equivale a construir, firmar ni probar una APK release.
- Con la restricción vigente no se resolverá la carrera de persistencia; no activar el shell como
  sustituto predeterminado ni presentar las fases de consulta como migración completa.


## Modularización física P7 — primer corte seguro (histórico)

Grafo real de Gradle (los paquetes Kotlin mantienen su nombre):

```text
:app
  ├── :core:domain         Kotlin/JVM: contratos, proyecciones y reglas nuevas
  ├── :core:designsystem   Android/Compose: tema y estado de carga/error
  ├── :liquid-glass       existente, sin cambios
  └── :cameraVision  existente, sin cambios
```

`core:domain` solo necesita Kotlin/coroutines-core (Flow es API pública); no Android,
Room, Firebase, app ni escáneres. `core:designsystem` solo Android/Compose, sin app ni dominio.
Se han movido fuentes/tests sin cambiar contenido o paquetes. No hay migraciones de datos.
La aplicación conserva `app/di`, `feature/*`, `data/legacy`, `legacybridge` y el adaptador
`core/designsystem/CardArtwork` que depende de caché/recursos históricos. No convertir esa
excepción en una dependencia core→app.

### Para inspeccionar en Android Studio
Abrir la raíz del worktree, sincronizar Gradle y usar vista **Project** para ver
`core/domain`, `core/designsystem` y `app`. En `app/src/main/java/io/asv/mtgocr/ocrreader`:
`feature` presenta, `app/di` conecta dependencias y `data/legacy` implementa los puertos.
Este era el primer corte; el segundo corte de abajo extrae tres de esas features.

### Sigue pendiente
Extraer features/data requiere separar antes recursos y adaptadores ligados a legacy.
No se ha modularizado ni refactorizado código de cámaras, persistencia o workers.
Siguen pendientes los comandos P3, detalle editable P4, operaciones P5/P6 y retirada de UI
antigua tras paridad, pruebas físicas y validación completa release. Los módulos nuevos
no resuelven la carrera de guardados legacy ni habilitan nuevas escrituras.


## Modularización física P7 — segundo corte de consulta

```text
:app (hosts, navegación y composition root)
  ├── :feature:catalog  ──┬── :core:domain
  ├── :feature:photos   ──┤
  ├── :feature:services ──┴── :core:designsystem (UI y cinco textos comunes)
  ├── :data:readers ───────── :core:domain
  ├── :core:domain / :core:designsystem
  └── :cameraVision / :liquid-glass (sin cambios en este corte)
```

Cada feature depende de ambos core; no hay aristas entre features ni hacia data/app.
Las tres contienen sus recursos traducidos, pantallas y ViewModels. Se conserva el paquete
Kotlin; el cambio de R apunta al propietario real del recurso, sin modificar texto o eventos.
El módulo JVM de lectores contiene la adaptación terminal callback→suspend del catálogo,
la validación canónica de rutas de fotos y la proyección de historial por índice original.
No contiene DataUtils, Room, Biblio/CardInfo ni funciones de guardado. Los adaptadores Android
con esos tipos permanecen en app; las nuevas utilidades se consumen desde la misma DI/ruta.

### Alcance restante real
- Launcher, shell Biblio y detalle guardado siguen en app: dependen de CardArtwork/caché legacy
  y recursos compartidos. Próximo corte seguro: inyectar el renderizado de arte y aislar recursos,
  sin mover ni cambiar CardImageCache o sus consumidores escáneres.
- Persistencia/servicios no están modularizados: separar fuentes de lectura por puertos sin
  mover DTO serializados ni alterar los guardados requiere otro corte revisado.
- Siguen abiertos comandos P3 (carrera de snapshots OCR), detalle editable P4, operaciones
  de fotos/revisión P5, servicios P6, retirada legacy y regresión física completa P7.
- Estas tres features siguen siendo consultas opt-in; no se cambia la navegación habitual,
  no se activa escritura ni se migran datos. El renombrado CameraVision ya estaba validado;
  ahora no se modifica ningún archivo suyo.


## Tercer corte P7: seis features aisladas

```text
:app ── :feature:{launcher,library,carddetail,catalog,photos,services}
                 └── :core:domain + :core:designsystem
:app ── :data:readers ── :core:domain
```

Sin dependencias entre features. El shell recibe el renderizador de detalle desde app;
no conoce CollectionCardDetail. Launcher recibe branding y arte; detalle recibe arte
con nombre, y app conserva FIT_CENTER. Los adaptadores de composición viven en
app/ui/LegacyFeatureContent.kt. No se alteran CardArtwork/CardImageCache ni recursos
históricos. Las Activities y eventos de navegación/retorno permanecen en app.

### Pendiente tras este corte (sustituye el estado anterior de launcher/Biblio/detalle)
- Las seis features Compose existentes ya son módulos; la Biblio/detalle extraídos son
  las consultas opt-in, no reemplazos de OCR ni de Main2Activity editable.
- Sigue en app la mayor parte de data legacy, Room, servicios e integración: avanzar
  mediante puertos de lectura sin mover DTO serializados/guardados compartidos.
- P3 comandos y carrera de snapshots OCR; P4 catálogo de cartas y detalle editable;
  P5 importación/revisión/resolución; P6 operaciones cloud/exportación/almacenamiento;
  P7 retirada legacy solo tras paridad, lint heredado y regresión física/release instalada.

## Independencia operativa futura de CameraVision (plan, NO implementado)

1. **Origen frente a ejecución.** Cornelius detector.onnx y Milo milo.onnx son modelos
   preentrenados upstream. ONNX es el formato/runtime, no una BD de cartas. La inferencia
   actual es local; prepare obtiene pesos y catálogo base del alojamiento CollectorVision
   si falta una copia verificada. No hay API de reconocimiento por cada foto en esa ruta.
2. **Lo que generamos.** Índices visuales (vectores + UUID/metadatos) y suplementos con
   nuestros scripts, ejecutando Milo sobre imágenes de referencia. Por ejemplo FRA y
   las regiones ART/UPPER. No confundir esa inferencia con entrenamiento de pesos.
   El generador de regiones actual está fijado a 110278 IDs; no es aún actualizador general.
3. **Objetivo solicitado.** No depender de disponibilidad/publicaciones de CollectorVision
   para instalar ni ampliar colecciones. Conservar modelos verificados bajo nuestro control
   y distribuirlos desde infraestructura propia o empaquetados, respetando sus avisos/licencias
   y revisando las condiciones de distribución antes de publicar. Esto no cambia su origen.
4. **Pipeline propio.** Ingerir metadatos/imágenes de referencia; generar catálogo FULL y
   regiones ART/UPPER y suplementos compatibles. Mantener SHA del modelo, normalización,
   dimensiones, orden ID/vector, ROI y versión en manifest. Generalizar límites rígidos y
   validar nuevos tipos/layouts, sin atribuir certeza de impresión/idioma por arte.
5. **Publicación segura.** Builds reproducibles/reanudables, catálogo completo consistente,
   hashes, actualización atómica y rollback; prueba de primera instalación con upstream
   inaccesible y regresión de colecciones antiguas/nuevas en dispositivo.
6. **Alcance separado.** Agregar cartas normalmente requiere indexar imágenes con el mismo
   modelo, no entrenar uno nuevo; evaluar calidad cada expansión. Entrenar/reemplazar redes
   propias es un proyecto distinto, exige datos, evaluación y regenerar índices compatibles.
   Nada de esto se ha activado en este refactor porque modificaría preparación/procesamiento.


## Cuarto corte seguro: coordinación de consultas en data/readers

Se extraen ReadOnlyLibraryShellRepository y ReadOnlyServicesReader a Kotlin/JVM. Sus puertos
LibraryShellReadSource / ServicesReadSource no conocen Context, Biblio, CardInfo, DeckCatalog,
DataUtils, Room o WorkManager. Las fuentes Android concretas y las fachadas compatibles
LegacyLibraryShellRepository / LegacyServicesReader siguen en app, sin cambiar los consumidores.
No se cambia ninguna escritura ni se autoriza el shell como ruta predeterminada.

```text
feature → contrato domain
app/DI → fachada compatible → coordinador data/readers
                              ↓ puerto de lectura
                       fuente Android/legacy en app
```

La dependencia de compilación sigue app→data/readers→domain, no una dependencia inversa
por pasar una implementación del puerto. La comprobación doble de biblioteca activa se mantiene,
pero no equivale a transacción ni impide que OCR publique un snapshot pendiente.
Sigue pendiente separar fuentes/proyección de fotos y colección con sus propias pruebas;
la mayor parte de persistencia, servicios operativos y trabajos continúa intacta en app.
Los bloques funcionales P3/P4/P5/P6 y la autonomía operativa CameraVision no se cierran con esto.

### Aclaración de modelos e índices
Los vectores de cartas nuevas se añaden a un catálogo externo, no a los pesos ONNX. Cornelius
localiza esquinas; Milo genera embeddings para compararlos con ese catálogo. No tenemos aquí
un pipeline validado para entrenar sustitutos propios de ambos modelos. Independencia operativa
(alojamiento propio + generación de catálogos) no exige reemplazar sus pesos; independencia
respecto de esos pesos sí requiere alternativas y regenerar índices al cambiar de embedder.

Para investigar entrenamiento propio: corpus de fotos/esquinas para detección; imágenes de
cartas con etiquetas y variantes para aprendizaje métrico; particiones separadas de evaluación,
aumentos de iluminación/perspectiva/reflejos, exportación ONNX y benchmark móvil. Puede partirse
de una red visual general ajena a CollectorVision. No iniciar entrenamiento ni sustituir modelos
bajo la restricción actual de no tocar escáneres/procesamiento.
Tiempo: sin GPU/dataset y piloto no hay estimación medible. Horas/días por ejecución de modelo
pequeño y semanas/meses de preparación/iteración son solo orientación, no un presupuesto.
Medir primero una época y la calidad de un piloto; no extrapolar de tiempos de inferencia.
Fuentes técnicas consultadas: https://huggingface.co/HanClinto/cornelius,
https://huggingface.co/HanClinto/milo y
https://docs.pytorch.org/tutorials/beginner/transfer_learning_tutorial.html.


### Quinto corte seguro de datos: fuente de fotos (2026-10-23)
- Extraída coordinación de PhotoReader a data/readers mediante PhotoReadSource, sin
  dependencia Android, DTO serializados ni comandos de procesamiento.
- AndroidLegacyPhotoSource mantiene acceso legacy y proyección de fotos/evidencias en app;
  LegacyPhotoReader es una fachada compatible. Sin cambios de rutas, formatos o guardados.
- Conservados orden de consultas, errores por sección y alcance global de importaciones.
- Pendiente: fuentes/proyecciones de colección y acceso legacy/Room aún en app. No se
  extraen escritores ni se resuelve la carrera de snapshots OCR en este corte.
- Siguen pendientes paridad editable P3/P4, operaciones P5/P6, retirada legacy P7,
  pruebas físicas y autonomía de distribución de modelos. Los previews no sustituyen
  las pantallas operativas legacy.


### Sexto corte seguro: lectura de colección (2026-10-23)
- Coordinación, validación de duplicados y publicación de snapshots en data/readers
  mediante CollectionReadSource/ReadOnlyCollectionReader.
- Fuente de proyección legacy aislada en app: copia detached, identidad, getters y listas
  de grupos/mazos/banquillo conservan contrato. API LegacyCollectionReader compatible.
- Secuencia de lectura diferida mantiene el orden de validación y detiene la consulta en
  el primer error, sin publicar una colección parcial ni mapear filas duplicadas.
- Sin cambios en archivos/backup, DTOs serializados, Room, escáneres, guardados o jobs.
- Pendientes: acceso físico legacy/Room y resto de adaptadores; escrituras P3 y coordinación
  con snapshots OCR siguen fuera del alcance autorizado. Paridad editable P4, operaciones
  P5/P6, retirada legacy P7, lint heredado y pruebas físicas siguen pendientes.


### Séptimo corte seguro: adaptadores y metadatos privados (2026-10-23)
- Fuentes Android del shell Biblio y servicios separadas de sus fachadas en archivos propios.
  El cuerpo del adaptador Biblio se conserva literalmente salvo visibilidad interna.
- Consulta física de tamaño/ruta canónica extraída a PrivateLibraryFileReader (data/readers);
  fuente Android suministra filesDir en cada llamada. Misma distinción null/0, mismos errores.
- No modifica deserialización, selección de biblioteca, guardados, Room, escáneres ni jobs.
- LauncherRepository permanece en app: mezcla lectura y selección que escribe preferencias;
  se deja fuera de esta extracción para respetar el alcance de solo lectura.
- Pendientes acceso legacy/Room y adaptadores con efectos, escrituras P3/coordinación OCR,
  paridad editable P4, operaciones P5/P6 y retirada legacy P7. Lint y pruebas físicas pendientes.
