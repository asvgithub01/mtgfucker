# Borde físico de carta: diagnóstico y plan

## Diagnóstico del lote v17

30JSON y30JPEG copiados a build/capture-v17-live-review. Diez capturas físicas nuevas, excluidas tres instrumentales: cinco Twilight ycinco Titanic(incluye una ambigua Titania/Titanic). Twilight artista5/5 JasonChan;Titanic3WayneEnglandREAD,1CONFLICT,1UNREADABLE. Fracciones0/10. AñosOCR incluyenTwilight2000 yTitanic2005 conestadoREAD: READ es lectura no veracidad; no usar consensoOCR como validación de catálogo.

Inspección1790330042118: recorteTwilight sin borde físico completo, copyright pegadoabajo; el2000OCR contradice2009impreso.1790330331655: Titanic cortada arriba yotra cartaocluyeabajo; problema distinto del marcointerior. No hay eventos rules_capture_retry enlogcatdisponible(no prueba histórica de que nunca ocurrieran).

## Código actual y limitación

OpenCvCardDetector usa Canny/DoG/adaptive, RETR_EXTERNAL por mapa yelige pronto mejorcandidato. Guiarefina cada lado por gradiente local penalizando distancia; puede preferir marcointerior fuerte a borde físicomásdébil. Es mecanismo plausible, no causalidad verificada sinframeoriginal. V17 mira título casi vacío yfootertexturado; no detecta un marcointerior con título perfectamentevisible.

## Enfoque elegido: automático primero, referencia manual auxiliar

1. **Instrumentar antes de cambiar umbrales.** Guardar opt-in/local acotado original sinrectificar, resolución/rotación, quadantes/después, origen(contorno/guía), puntuaciones porlado, zoomyrectificada. Snapshot ya existe para correcciónUI pero corpus rules_scan solo garantiza rectificada. Sin original no se puede recuperar exterior ya descartado.
2. **Varios candidatos.** Evaluar jerarquía de contornos ycuadriláteros contenidos similares, no solo primer mejor. RETR_TREE como experimento, no sustituciónciega por elmayor contorno: mesa/funda/sombra también pueden contenercarta.
3. **Refinamiento hacia exterior con evidencia.** Buscar líneas paralelas afuera de cada lado, continuidad ytransición con fondo, coherencia de las4esquinas ygeometría; penalizar recorte yextensión no respaldada. No sumar margen fijo ni exigir borde negro(blanco,borderless,etc.). Separar confianza marco yconfianza borde físico.
4. **Seguimiento temporal.** Usar geometría anterior como preferencia mientras mantenga evidenciaactual; reset conmovimiento/escala/zoom/lente/orientación/cambioescena. Estabilidad no demuestra borde correcto.
5. **Referencia manual opcional.** Corrección4esquinas guarda forma,escala,posicion encoordenadasnormalizadas ycontextocámara durante sesión. Prior blando para elegir candidatos, nunca imposición. Modo posiciónfija solo explícito para soporte/mesa fija. No reutilizar features de una carta como si fueran las de la siguiente.
6. **Fallback.** Si exteriorincierto pedircorrección/reencuadre, no guardar impresiónpor recorte dudoso. Cambio experimental limitado scannerreglas; preservar anteriores.

## Pruebas ycriterios

Corpus etiquetado manualmente4esquinas físicas enoriginal: cartablanca/negra, fondoclaro/oscuro,tapete,funda,borderless, inclinación, cartaparcial/ocluida, mismoarteedicionesdiferentes. Compararbaselineypropuesta mismasimágenes: errornormalizadodeesquinas, bordeperdido, fondoincluido, cambiosabruptos, precisiónOCRaño/número, hashidentity,latenciap50/p95yabstención. No prometerporcentajesantesde medir. Conservarcasosde bajo contraste yno contarloscomofalloOCR cuando pieestáfuera.

## Orden próximo

Instrumentación+corpusoriginal → A/Bcandidatos/refinadoexterno → usuario prueba → prior manual si automáticoinsuficiente. A/Bfooter pospuesto detrás geometría. Vuforia/OCRCPU-GPU/hashesnuevos sigueninvestigaciónfutura.

## Fuentes

- OpenCV contornosyjerarquía: https://docs.opencv.org/doc/doxygen/html/d3/dc0/group__imgproc__shape.html
- OpenCVseguimiento: https://docs.opencv.org/4.7.0/dc/d6b/group__video__track.html

No se cambia detector niAPK en esta revisión; se documenta el enfoque ylosdatos necesariospara validarlo.

## v18 — primer experimento instalado para A/B

Checkbox «Bordes fijos (desmarcado: automático experimental)» solo reglas. Modo persiste, coordenadas no: cada nueva Activity exige calibrar. Marcado: primera captura live estable abre corrección4esquinas sinOCRautomático; usuario pulsaLeer para confirmar yusar esquinas en próximosframes. Colocar siguientescartas enmismaposición,mantenermóvil fijo. Desmarcar/marcar recalibra; solo se cambia modo fuera de captura/corrección/análisis. Zoomgesto o rotación invalida; rebind mismozoom no. En fijo no se usa gatev17(la plantilla es manual), ni detectorposteriorpara mover esquinas. Siguehash/OCRparaidentidad; posiciónfija no prueba presenciacarta.

Desmarcado: primera heurística exterior automática deOpenCVsolo reglas. RETR_LIST permite explorar candidatos geométricos del mismo mapa; siuno contienealmejorconárea1.02–1.30y scorealmenosbest-.06,eligeelmayoradmisible. Refinado no reduceárea>1% para evitarvolverhaciaadentro. No reconoce semánticamente bordefísico: sombra/funda pueden confundir. No expansiónfijadepixeles. Guía todavía compite concontornos; investigaciónmayorcoberturapendiente.

Liveaccepted conserva original.jpg junto card.jpg; JSONboundaryMode yboundaryQuad normalizado TL/TR/BR/BL. Retención30privadaexistente. Manualstillno reutilizaoriginalanterior; campoausente permitido. Aún no registra todoscandidatos ni puntuacionesporlado niambigüedadorigencontorno/guía: instrumentaciónparcialexplícita. Snapshotactual esscalagrisesdelplanoY.

Prueba: autoOFF,3Twilight+3Titanic enautomático, luegoactivar fijo,ajustarbordeexterior enprimerafoto,pulsarLeer y3+3cartas enmismaposición. Revisar si footercompleto ysinarrastrefalso. Si cambiaposición,móvil/zoom recalibrar. No probarautoañadidohasta verificargeometría. ComparaciónA/Brealpendiente; testsintéticono sustituyeoriginalesdelSony.
