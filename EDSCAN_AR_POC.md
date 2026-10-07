# EdScan + ARCore POC

Rama codex/edscanner-arcore-poc, derivada de master con postprocesado integrado.
Entrada independiente EdScan AR; el scanner CameraX original se conserva.

## Flujo
- ARCore es el propietario unico de la camara desde entrar. Preview mayoritaria.
- CPU images YUV_420_888 alimentan el mismo motor Cornelius/OpenCV/Milo de EdScan.
  Un solo frame en vuelo; cierre de Image antes de inferencia; liberacion antes de cerrar Session.
- Configuracion trasera de hasta 1280x960; autofocus AUTO, update LATEST_CAMERA_IMAGE.
- Identificar/guardar usa el flujo original; cambiar referencia no recrea Session.
- Augmented Images sigue una carta usando su imagen de referencia y ancho 63 mm.
  Precio de la impresion/acabado actuales y GIF original local sobre la carta.
- No se usan cloud anchors ni se suben fotos. La referencia se obtiene con Glide.
- Opciones plegables conservan controles originales. Galeria horizontal siempre visible
  encima de la camara, reutilizando RecentScansView y sus pendientes/precios/acciones.
- El banner normal ARCore/EdScan se sustituye por la galeria; carga/errores siguen visibles.

## Rendimiento
- YUV lee planos en bloque sin alterar posiciones, recortes ni strides.
- Overlay acelerado: solo GIF de 96x96 rasterizado por software; no toda la pantalla.
- No se repinta sin tracking ni existe un temporizador de animacion duplicado.
- EdScanFrame registra AR decode/pipeline cada 15 analisis.
- No se han reducido umbrales ni calidad. Falta comparacion fisica de latencia con CameraX.

## Validacion
Java 17: bash gradlew :app:testDebugUnitTest :collectorvision-native:testDebugUnitTest :app:assembleDebug
Smoke: bash gradlew :app:assembleDebugAndroidTest '-PtestApplicationIdOverride=io.asv.mtgocr.ocrreader.arcoretest'
Instalar app y test APK solo despues de BUILD SUCCESSFUL:
adb -s QV770HG2JD install -r app/build/outputs/apk/debug/app-debug.apk
adb -s QV770HG2JD install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s QV770HG2JD shell am instrument -w -e class io.asv.mtgocr.ocrreader.EdScannerArSmokeTest io.asv.mtgocr.ocrreader.arcoretest/androidx.test.runner.AndroidJUnitRunner

El override evita reemplazar el runner antiguo con otra firma. No desinstalar ni borrar datos.
El smoke necesita Sony desbloqueado, permiso camara y ARCore. Usa referencia sintetica,
inferencia real y edscan_test_no_save=true (solo debug) para no guardar cartas.
Comprueba camara, inferencia, pausa/resume, galeria y opciones, y Session estable.

Prueba manual: detectar dos cartas distintas, verificar edicion/precio, mover/retirar
carta, abrir/cerrar opciones y volver tras pausar. No se necesita Siguiente carta.

## Referencias
- https://developers.google.com/ar/develop/java/machine-learning
- https://developers.google.com/ar/develop/java/augmented-images/guide
- https://developers.google.com/ar/develop/java/enable-arcore


## Diagnostico de rendimiento
En debug, EdScanPerf agrega medias/maximos por etapa e hilo cada 3 segundos:
- AR: intervalo/trabajo GL, Session.update, adquisicion CPU, disponibles/omitidos,
  cola worker y conversion. CameraX tambien mide decode/pipeline para comparar.
- Motor: detector, warp OpenCV, embedding normal/180 grados, busqueda.
- UI: espera del resultado, trabajo del callback, retraso heartbeat.
- AR referencia: espera worker, imagen Glide, construccion BD, cola/configuracion GL,
  primer tracking; camera config efectiva. Power saver, bateria y estado termico.
Sin fotos/identificadores en logs; sin escritura por frame ni cambios de umbral.

La POC AR no aplica la pausa automatica de 30 segundos sin carta NUEVA/toque.
Se conservan pausas manuales y de ciclo de vida. CameraX mantiene su politica.
La referencia AR se prepara tras validar impresion/acabado y consultar el precio
local, antes de encolar el guardado. Sigue habiendo una imagen activa; colores por precio y multiples cartas quedan pendientes.

Comparacion: con Sony desbloqueado, misma carta/luz/distancia, probar 30 segundos
CameraX y 30 segundos AR, incluir retirar/presentar de nuevo la carta. Evitar
cambiar filtros/opciones entre pruebas. Guardar logcat EdScanPerf/EdScanFrame/
CorneliusTiming y dumpsys battery/thermalservice. No atribuir lentitud a bateria
sin contrastar ahorro/estado termico. No borrar logcat global.


### Referencia anticipada y cache
- AR arranca antes de guardar, con precio local inmediato si existe; si falta, el
  precio remoto se actualiza sin bloquear la referencia.
- Repetir impresion+URL mantiene tracking incluso si cambia precio/acabado.
- Cache LRU de cuatro bases AugmentedImageDatabase ligada a la Session; volver
  a una carta evita Glide y addImage. No se comparte entre sesiones.
- Callbacks de precios verifican generacion y la solicitud actual; fallos permiten retry.
- Timers model.detector/embedder.preprocess/tensor/onnx separan preparacion y modelo.


## Prueba sin GIF
GIF retirado temporalmente del overlay y de Opciones: no se carga Movie ni se
reserva el bitmap/Canvas de animacion. Solo marco y precio. Asset conservado
para una posible recuperacion, sin referencias desde el scanner.
Mantener el resto del pipeline para comparar el retraso AR sin cambiar variables.


## Preparacion anticipada y cache persistente
- EdScan avisa candidato visual con score>=0.75 y margen>=0.08 solo para preparar
  referencia. NO cambia gates de aceptacion, coleccion, precio visible ni target activo.
- Consulta especulativa usa Room local, sin catalogo/red/precios; imagen especulativa
  tiene espera maxima250ms y solo una tarea pendiente. Fallar no afecta aceptacion.
- Referencias compiladas serializadas en cache privada ar-references:32entradas/32MB,
  8MBmaxporarchivo. Clave UUID+URL+ancho+resolucion/version. Bytes se deserializan
  contra la Session actual; formato incompatible/corrupto se reconstruye.
- Persistencia se encola tras preparacion; no bloquea activacion con escritura.
- No se dibujan contornos viejos de inferencia sobre preview viva: coord2D no equivale
  a AugmentedImage FULL_TRACKING. Sin GIF, sin relajar precision.
- Smoke recrea Activity/Session y comprueba cache persistente sin guardar cartas.


## Borde holografico opcional
Opciones > Borde holografico 3D (experimental), apagado por defecto y persistente.
Shader GLES2 sin texturas/GIF: halo cyan/violeta, pulsos y destellos, dos planos
en pose real de la carta, con borde elevado3mm. Solo FULL_TRACKING; desaparece
al perderlo. Dos draw calls sobre area carta, sin nuevo executor ni trabajo por
pixel CPU. Compilacion diferida; fallo del efecto no detiene camara/inferencia.
EdScanPerf AR.energyBorder permite comparar coste CPU de envioGL (no tiempoGPU).
Smoke compila/dibuja shader enGLrealSony ycomprueba flag sin alterar preferencia.


## Estrategias y apertura 3D
Dos flags adicionales OFF por defecto y persistentes, traducidos:
- Contorno rapido generico2D: OpenCvCardDetector sobre luma320px sin modelos ni
  esperar identidad, max8Hz/untrabajo. Copia acotada enGL; deteccion enworker.
  Contornoambar sinprecio/identidad solo mientras ARnoFULL. CoordenadasCPU
  transformadasaVIEW conFrameactual;resultado descartado tras250ms desdecaptura,
  alrotar/redimensionar oalreanudar. Puede detectarrectangulosnocartas.
  NOes AugmentedImagegenerica ni ancla3D. No cambia model/gates/persistencia.
- Abrir cartaen3D: cargaimagenoptinfuera hiloUI/GL, congeneracion+UUID; dospaneles
  texturados sobre poseARseabrenpormitad (bisagraslaterales) y revelanoctaedro
  luminoso rotatorio conshader. SinGIF. Se reinicia alrecuperartracking; soloFULL.
  Efecto independiente delcontornogenérico, no prometer efectos3Dantesdelpose.

Medir AR.genericContour/AR.reveal frente flagsOFF; generic agregaCPU ylosshadersGPU.
PruebaSony: ambosenOFF, luego solo contorno, luego abrir3D. Retirar/mover carta,
cambiar impresión, rotar móvil ypausar. No mostrar preciodelaúltimacarta enunquad
genérico. Smoke procesaYreal ycompila/dibujaambosestadosdelshaderapertura.


## AR autonomo precargado (ultimo lote)
Opciones > AR precargado: ultimo lote (solo visual,singuardar).
Lee bibliotecaactiva, eligegrupoCornelius masrecienteporaddedAt, deduplicaUUID.
Primera pruebaSony: Cornelius28,17registros14impresionesdistintas devariossets.
NOson500cartasRealityFracture todavia. No se añaden, editan ni guardan copias.

PausaEdScan ydesactivalumaOpenCV enestemodo, construyeUNA AugmentedImageDatabase
con todaslasreferencias(ancho63mm), luego ARbusca autonomamente, sin UUID/contorno
previoEdScan. Progreso ycontadorready/total; referenciasfallidasnotificadasencontador.
SerializaBDcompleta(cacheprivada)parareapertura. Al detectar, usametadatos/precio
localsnapshot ypermiteshaders3D. Imagen/pose debeFULL_TRACKING. POCdibujauna
carta seguidaalavez, elegidaentre todaslasprecargadas; noimplementadomultioverlay.
Alquitarflag vuelvepipelineEdScan. No asumirfoil/idioma distinto deentrada.
No inferirprecision500niproducciondelresultado14.

Smoke preloadsLatestBatchWithoutEdScanOrCollectionWrites compilaBDrealultimolote,
compruebatodasready, externalAnalyzedFrames=0/cpuFrames=0 yIDscolecciónintactos.
La generacion inicial exige descargarimagenes si nocacheadas; esperarcontadorlisto.


El lote de prueba queda fijado en preferencias por biblioteca al cargarlo por primera
vez: nuevos escaneos no cambian la base silenciosamente. Boton Cambiar prueba al
lote mas reciente permite sustituirlo explicitamente. Sony fijado a Cornelius28;
se creo despues Cornelius29 con una carta y no debe sustituir esta prueba.
Validacionreal14/14: preparacion inicial1964ms, reapertura cache70ms (no es tiempo
deteccionfisica). Cero framesEdScan/cpuFrames ysin cambiosIDscoleccion en smoke.

## Base AR por edición
Opciones → Seleccionar base AR abre `ArDownloadsActivity` (Premium). Paquetes compilados fuera del móvil y persistentes: consultar `packages/ar-packages/README.md`. Reality Fracture ahora incluye FRA+FRC+SPG159–168,574referencias. No se construye cada base en el teléfono ni se usa el último grupo Cornelius. El modo legacy por lote permanece solo para pruebas explícitas. Spinner/progreso y tiempo total visibles; un paquete activo, máximo1000sin truncado. Precios locales por impresión/acabado, sin guardados de colección.
