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
Depende de P4.
- [ ] PhotoImportActivity, PhotoGalleryActivity, PhotoScanDetailActivity y
  ScanReviewActivity: UI Compose, progreso/restauración y presentación de evidencias.
- [ ] Reutilizar workers/almacenes existentes; no relanzar trabajos por recomposición.
- [ ] Separar UI de ScanReviewPanel sin modificar su uso legacy.
Salida: importación/revisión cancelable, errores recuperables, ninguna foto o cola perdida.

### P6 — Ajustes, servicios y cierre funcional
Depende de P3; integrar después de P5 para validar el recorrido completo.
- [ ] BuylistActivity, CloudAccountActivity, StorageActivity, ArDownloadsActivity.
- [ ] Ajustes generales del shell, bibliotecas y exportación Cardmarket.
- [ ] Mantener contratos Firebase/Premium, limpieza silenciosa, paquetes AR y monedas.
- [ ] Mantener configuración específica de escáner dentro de sus pantallas existentes.
Salida: todas las rutas no escáner cubiertas, offline y fallos de red incluidos.

### P7 — Orden final y endurecimiento
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

    bash gradlew :app:testDebugUnitTest :collectorvision-native:testDebugUnitTest :app:assembleDebug --console=plain
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
