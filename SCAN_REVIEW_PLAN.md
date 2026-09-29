# Ediciones provisionales, revisión y galería

## Implementado en esta iteración
- OCR original sin bloqueo de set: elegir menor precio conocido entre los **cinco sets más recientes**, respetando acabado y convirtiendo precios a moneda común. Si faltan todos los precios, usar la más reciente. En ruta rápida se usa precio cacheado, sin bloquear cámara por red.
- Etiqueta «Edición provisional». Seleccionar manualmente edición la elimina. No se afirma que un nombre OCR confirme una edición. No se cambia retrospectivamente la biblioteca existente.
- Idioma restringe las opciones disponibles antes de la selección económica; set fijado conserva su política.
- Botón **Actualizar precios**: toda biblioteca activa, aunque haya filtro visible. UUID exacto y acabado, sin modificar cantidades, idioma, condición ni agrupaciones. Sin precio disponible conserva el anterior; usa prioridad de proveedores configurada y sus snapshots/cachés. No es una cotización en tiempo real. El refresco se cancela al destruir la pantalla: esta operación aún no es una cola duradera.
- Galería compartida ViewPager2, una carta, swipe nativo sin esperar imágenes; tira horizontal inferior con miniatura, precio, selección y salto directo. Pinch/zoom mantiene control de la imagen.
- Con «guardar fotos» EdScan activo: WorkManager encola comprobación visual **cuando Android declara el dispositivo inactivo**, no durante cámara visible. Serializa revisión visual y OCR con mutex. Guarda `.verification.json` junto a foto/metadata/link. No sube fotos.
- Revisión visual: compara UUID candidato con original; presencia, score>=0.70 y margen>=0.08. Resultado `consistent`, `conflict` o `inconclusive`. No altera datos automáticamente. UI muestra conflictos al volver a Biblio/sesión (snapshot refrescado en onResume), también en detalle.

## Límites importantes
Repetir el mismo modelo sobre una foto rectificada no es una segunda fuente independiente. `consistent` no confirma idioma, acabado, autenticidad ni ediciones visualmente idénticas. `inconclusive` no se considera correcto ni conflicto. La primera versión marca discrepancias fuertes para revisión humana.

Una foto se vincula a biblioteca + collectionItemId + UUID guardados. Cambiar a otra edición no arrastra el conflicto anterior. Falta acción explícita para aceptar la misma impresión y cerrar conflicto con historial.

## Próxima fase: OCR original + revisión asistida
1. Flag de fotos OCR, apagado por defecto. Copiar NV21 **antes** de mejora de luminancia y asociarlo a frameId; entregar ese ID junto al candidato de texto.
2. Congelar evidencia al aceptar lectura, antes de consultas asíncronas. Cola limitada para no retener frames por cada análisis; JPEG/rectificación fuera de cámara/UI.
3. Persistir atómicamente foto, texto bruto, frameId/rotación, idioma propuesto, candidatos/score, biblioteca/item/UUID; reusar workers duraderos y recuperar entradas huérfanas.
4. Validación cruzada, no solo repetir embedding: nombre localizado, set/número, año, autor y disponibilidad de idioma. Separar «sin evidencia» de «contradicción».
5. Pantalla de pendientes/conflictos con foto original, elección guardada y alternativas. Confirmar/corregir explícitamente, actualizando Room+legacy por identidad vigente. Guardar decisión y permitir descartar evidencia/fotos.
6. Pruebas de desconexión, cierre forzado, borrado/cambio manual durante revisión, cartas idénticas de distintas ediciones y fotos de la siguiente carta. Planificar retención/límite de almacenamiento.

## Pruebas manuales pendientes
1. OCR sin set: carta con varias reimpresiones -> económica reciente y aviso provisional. Elegir impresión cara -> no debe volver a cambiar al llegar metadata.
2. Repetir con set fijado, foil y PT; comprobar restricción de idioma.
3. Actualizar precios con filtro de agrupación: confirmar que actualiza también filas fuera del filtro y conserva cantidades/acabados.
4. Abrir galería desde Biblio, sesión, set, mazo y detalle: swipe rápido, red desconectada, pulsar miniaturas, zoom y giro de pantalla.
5. Fotos EdScan activadas: escanear, dejar móvil inactivo para revisión, volver a Biblio. Inspeccionar evidencia de cualquier conflicto. No prometer precisión antes de corpus físico.

## Validación
439 pruebas JVM sin fallos; APK y APK de instrumentación compiladas. Validación de gestos, UI y revisión física pendiente de Sony conectado; no se han instalado estos cambios en esta iteración.


## Actualización: origen del postproceso y tira reciente
Esta sección sustituye el alcance anterior de verificación visual EdScan:
- **EdScan → idioma**: cuatro OCR agrupados por idioma; propuesta conservadora (texto>=20, confianza>=0,80 y ventaja>=0,15). No aplica automáticamente. El usuario confirma; la aplicación valida que la impresión tenga imagen en ese idioma. Chino queda manual (no deducir simplificado/tradicional de `zh`); ruso sigue sin OCR soportado.
- **OCR original → imagen**: flag «Guardar foto OCR para revisar arte», apagado por defecto en la pantalla del scanner. Copia NV21 original antes de filtros, frameId exacto propagado hasta lectura aceptada, JPEG orientado en executor. También se conserva en el flujo lento de selección de edición. No toma una foto posterior.
- Las fotos son privadas en `files/edscan`; enlaces por biblioteca/collectionItemId. Sin flag no copia ni guarda bytes. Entradas canceladas/no guardadas pueden quedar sin enlace y no se muestran. Pendiente política de retención/cuota de fotos.
- Botón «Postprocesar escaneos» junto al refresco de precios y en EdScan. Pantalla con todos los estados y filtro «Solo conflictos y propuestas». Arranca trabajo duradero de idioma o de imagen según origen. Los jobs se reanudan con WorkManager; resolución de catálogo del candidato visual se completa al abrir la pantalla y reintenta tras errores de red.
- Matching OCR reutiliza Cornelius/Milo local sobre foto completa orientada; compara impresión candidata con la guardada usando UUID del catálogo. El precio no determina el resultado. No usarlo como autenticación ni confirmación infalible de reimpresiones visualmente idénticas.
- Se puede ver foto, abrir detalle para corregir edición, confirmar idioma propuesto o conservar explícitamente los datos actuales. Aceptaciones de revisión ligadas a UUID+acabado+idioma+versión de evidencia; cambio posterior exige revisar otra vez.
- `RecentScansView`: últimos 30 registros de sesión, una fila horizontal con miniatura/precio/cantidad; pulsar abre +1, −1, foil/no foil, eliminar o detalle. EdScan usa su sesión Cornelius; OCR reutiliza acciones originales. Borrar o bajar última copia pide confirmación. Mutaciones por collectionItemId, nunca por posición.
- La UI no se ha probado físicamente todavía en esta iteración; APK e instrumentales se compilan, validar distribución en Sony. No cambia scanners experimentales de reglas/fotos ajenos a OCR original y EdScan.

### Prueba sugerida
1. OCR flag apagado: escanear, no debe aparecer foto nueva en postproceso. Activarlo y escanear dos cartas distintas seguidas: la foto de cada fila debe corresponder al nombre leído.
2. OCR sin set: una reimpresión antigua reconocida provisionalmente como barata; postprocesar y revisar propuesta de arte/edición, abrir foto y detalle. No modifica la edición solo por encontrar candidato.
3. EdScan foto activada: escanear PT/ES/EN con idioma desconocido o manual distinto; revisar propuesta/conflicto, aplicar idioma solo cuando corresponda. Probar texto corto/ilegible y verificar «sin evidencia suficiente».
4. Apagar red después de encolar, cerrar/reabrir y reconectar: los análisis locales y la resolución pendiente deben recuperarse sin escanear de nuevo.
5. En ambas tiras: modificar copias, foil (limpiar precio anterior), cancelar eliminación y confirmar eliminación. Volver a Biblio y comprobar cantidades/agrupaciones.


### Validación de esta actualización
- BUILD SUCCESSFUL: APK, APK de instrumentación y 446 pruebas JVM sin fallos; `git diff --check` correcto.
- Instaladas ambas APK en Sony XQ-DQ54 QV770HG2JD mediante `install -r`, sin borrar datos.
- `OcrFramePhotoDeviceTest` pasa en Sony: flag apagado no guarda; flag activo guarda frameId654, orientación90°, dimensiones60×40 y píxeles originales incluso tras modificar buffer fuente. No inserta ninguna carta en biblioteca.
- `CardGalleryPagerDeviceTest` llegó a recreación tras comprobar página inicial y swipe, pero falló esperando RESUMED (Activity STOPPED); dispositivo mostraba NotificationShade/keyguard (`mDreamingLockscreen=true`). Repetir desbloqueado para validar recreación y selección de miniaturas. No se da esta prueba por pasada.
- Pendiente prueba física de precisión de idioma/arte, UI de las tiras en ambas cámaras y recuperación tras muerte de proceso/red.


## Aplicación seleccionada y comparación visual
- Cada fila muestra captura y carta identificada/propuesta en dos paneles de260dp, ampliables al tocar (galería con zoom).
- Check «Aplicar» habilitado solo para conflicto/propuesta accionable. Botón «Aplicar seleccionados» con contador y confirmación, ejecución serial e informe aplicados/noaplicados.
- Selección por clave de foto y versión de evidencia, nunca por posición; una propuesta por fila de inventario. Se invalida si cambia UUID, acabado, idioma o resultado antes de aplicar.
- EdScan aplica idioma validado para la impresión. OCR aplica candidato visual exacto, conserva acabado e idioma (valida disponibilidad), cantidades/grupos/condición; corrige nombre y descripción cuando se trata de otra carta. Actualiza legacy y selección Room.
- Sin red/datos suficientes o con una edición ya cambiada se omite y no se pisa información nueva. Los casos pendientes/inconclusos no son seleccionables. Chino simplificado/tradicional y ruso siguen manuales.


## Corrección manual de edición e idioma
- Cada fila incluye Ediciones (EditionPicker.showGrid reutilizado, símbolos de sets y subselector de impresiones) e Idioma (GalleryLanguageSpinnerAdapter con banderas, incluso si OCR es inconcluso).
- Idiomas obtenidos por set+número de impresión, no catálogo general de lenguas. Sin lista confirmada no se cambia edición/idioma.
- Al cambiar edición se conserva idioma si existe; si ya tenía idioma y no existe, se usa EN si está disponible o el primer código disponible ordenado. Notificación explícita del cambio manual; el lote también advierte esta política. Idioma desconocido permanece desconocido, no se infiere de la edición.
- Se conserva acabado exacto; si la edición elegida no lo tiene se bloquea el cambio. UUID/idioma/acabado actuales se verifican antes de guardar. Cambios de edición mantienen Room y legacy.
- Investigación borde negro/dorado: ArtHashMatcher usa recortes del arte en gris; HashBorderPolicy solo considera blanco/negro y deja otros sin resolver. Postprocesado OCR actual usa Cornelius/Milo, no pHash. Repetir hash idéntico no aporta evidencia de color; queda pendiente análisis de borde exterior en color + candidatos/set/símbolo, con corpus negro/dorado y control de reflejos. No implementada nueva corrección automática de bordes en esta entrega.

## Reanálisis visual y acciones de revisión
- Cada fila muestra el origen real: EdScan u OCR original, separado del tipo de análisis.
- Reanalizar carta crea un intento UUID independiente con Cornelius/Milo sobre la foto guardada. La fila visual nueva no sustituye la revisión de idioma; solo se muestra el último intento adicional. Archivos previos quedan para auditoría.
- Los resultados y mapeos versionados impiden que callbacks anteriores contaminen el intento nuevo. WorkManager persiste el trabajo; al abrir postproceso se recupera también un manifiesto pendiente de encolado.
- No se modifica la biblioteca automáticamente. Misma foto/modelo puede repetir un error; no implica corrección garantizada de bordes dorados.
- Borrar confirma eliminación de toda la fila y sus copias, valida ID/impresión/acabado/idioma/cantidad y limpia selección Room. Fotos conservadas como evidencia privada.
- Todo correcto confirma el estado actual para ese análisis, sin cambiar carta. No resuelve un resultado nuevo recibido después.
- Selector de idioma con área completa pulsable; textos ES/EN/DE/FR/IT/PT.

## Estados de imagen en revisión
- URL vacía no implica descarga: análisis inconcluso muestra «Sin identificación fiable»; pendiente visual y consulta de catálogo tienen mensajes distintos, sin spinner ficticio.
- Errores de ficha o espera superior a30s se muestran explícitamente. Reintento manual cuando concluye la petición anterior, evitando duplicar tareas en la cola compartida.
- Resolver metadatos depende de candidateLabel ausente, no de URL ausente. Se libera resolving tras éxito/error y se controlan errores de escritura; ficha sin imagen queda como no disponible.
- Evidencia Sony:41 fotosOCR,31 inconclusas,10 candidatas en la lectura inicial;9 sin match y1 con match. Prueba exacta Arcbound Worker/Divine Offering resuelve catálogo local sin error. No bajar umbrales para ocultar inconclusos.


## Flujo de revisión 2026-09-29 (codex/postprocessing-review)
- Reanalizar todas trabaja sobre fotos asociadas de la biblioteca, aunque el filtro visible sea otro. Cada foto tiene tres trabajos duraderos: verificación visual, OCR y contraste del título canónico. Progreso cuenta pasos WorkManager terminados, con fallidos/cancelados separados. Fotografías ausentes no se pueden reanalizar.
- EdScan revisa con similitud >=0.70 y margen >=0.08, independientemente del modo rápido. No es una segunda fuente independiente ni prueba de acabado/impresiones indistinguibles. Resultado ambiguo sigue sin confirmar.
- OCR EdScan usa líneas del título superior y texto de reglas para idioma. Se guardan las lecturas por script; título sin alias local o ambiguo no crea una falsa confirmación. Discordancia de nombre conocido bloquea aplicación masiva y requiere decisión humana.
- Una fila por foto agrupa evidencias de idioma/arte/provisional. Símbolo junto al nombre abre ediciones; bandera abre idiomas. Ambos editan un borrador; Guardar cambios valida la impresión/idioma y conserva ID, cantidad, condición y grupos. Acabado admite cambio explícito al elegir impresión; propuestas automáticas no lo cambian. Precio anterior se limpia.
- No se sustituye silenciosamente el idioma al editar: si no existe en la nueva impresión, queda pendiente de selección. Los borradores sobreviven a rotación, no a muerte del proceso; salir solicita descartarlos. Orden de sesión/scroll se conservan. La captura admite pinch/pan y ampliación completa.
- Prueba Sony real sin escribir inventario: Aerid Konstrari FRA407, cadena3/3 sin errores; OCR corroboró nombre e inglés (~0.997). Visual top0.74668/segundo0.73391, margen0.01278: inconcluso, correctamente NO confirma edición. Pendiente corpus más amplio (foil/no foil, fotos OCR lejanas, errores de título y cambios en fila120).
