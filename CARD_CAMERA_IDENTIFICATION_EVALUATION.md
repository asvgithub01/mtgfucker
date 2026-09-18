# Evaluación: identificación de cartas MTG con la cámara

Fecha: 2026-09-14  
Rama: `codex/camera-card-identification-research` (creada desde `master`, commit `6eca8f8`)

## Decisión propuesta

Investigar primero un **pipeline híbrido centrado en leer la línea inferior de la carta ya
rectificada**, no una reescritura completa de la cámara:

1. Mantener el OCR actual del nombre para reducir el catálogo a una carta.
2. Reutilizar la captura fotográfica de alta resolución y el ajuste de cuatro esquinas que ya
   existen.
3. Añadir OCR específico de la franja inferior para extraer número de coleccionista, código de
   colección e idioma cuando estén impresos.
4. Resolver esa clave contra Room/MTGJSON y usar Scryfall solo para completar imágenes o datos que
   no estén en local.
5. Si la línea inferior no existe o no se lee, usar como fallback el comparador visual actual de
   arte, símbolo y color del borde.
6. Probar OpenCV mediante A/B únicamente para mejorar la detección automática de las cuatro
   esquinas. Migrar toda la cámara a CameraX quedaría como trabajo independiente después de medir
   el beneficio.

Esta es la prueba con mejor relación valor/riesgo: ataca directamente la **impresión exacta** y
aprovecha casi toda la infraestructura que ya está construida.

## Qué tiene ya el proyecto

La propuesta recibida parte de una aplicación nueva, pero `mtgfucker` ya implementa buena parte del
pipeline:

- `OcrCaptureActivity` y `MlKitTextDetector` reconocen el nombre con ML Kit sobre la cámara.
- `CameraSource` usa la API antigua `android.hardware.Camera`, frames NV21, cuatro buffers y conserva
  un solo frame pendiente mientras procesa el anterior. Funcionalmente ya se comporta como una
  cola «último frame».
- `OcrLumaEnhancer` trabaja directamente sobre el plano de luminancia NV21 y enmascara todo salvo la
  zona del título.
- `EditionScanActivity` captura un JPEG a resolución fotográfica y permite corregir cuatro esquinas.
- `CardQuadrilateralDetector` busca cuatro rectas coherentes alrededor de la guía.
- `CardCropAdjustView.extractCardBitmap()` ya aplica una transformación proyectiva de cuatro puntos
  y normaliza la carta a 630 × 880 (proporción 63/88 = 0,7159).
- `CardArtworkIdentifier` ya compara la imagen capturada con impresiones candidatas.
- `CardImageFingerprint` ya implementa dHash; `CardEditionVisualFingerprint` añade símbolo de
  colección y color del borde. No hace falta `opencv_contrib/img_hash` para esta parte.
- Room/MTGJSON ya contienen impresión, colección y número de coleccionista.

Por tanto, introducir a la vez CameraX, OpenCV, otro hash global y una nueva base local duplicaría
componentes y haría difícil saber qué cambio mejora realmente la tasa de acierto.

## Evaluación de la solución recibida

### CameraX

**Técnicamente acertado como destino**, pero no es el primer experimento. La API de cámara actual
está obsoleta y CameraX simplificaría ciclo de vida, rotación y combinación de `Preview`,
`ImageAnalysis` e `ImageCapture`. `STRATEGY_KEEP_ONLY_LATEST` es la política adecuada para visión en
tiempo real.

La optimización del plano Y también es válida, aunque hay que respetar `rowStride` y `pixelStride`:
el primer plano Y de `YUV_420_888` no garantiza siempre un bloque compacto de `width × height`.

**Riesgo en este proyecto:** una migración de cámara toca el OCR que ya funciona, el zoom, enfoque,
flash, orientación, captura y overlay. Debe hacerse después y con pruebas de regresión, no dentro del
primer spike de identificación de edición.

### OpenCV y cuadrilátero

El enfoque de escáner documental es apropiado, pero `Canny → findContours → approxPolyDP` no debe ser
la única ruta. Conviene generar candidatos con varias señales:

- luminancia con CLAHE/Canny;
- saturación o crominancia para cartas oscuras sobre fondos oscuros;
- umbral adaptativo;
- contorno de cuatro puntos cuando sea estable;
- cuatro lados ajustados a segmentos cuando las esquinas redondeadas produzcan más vértices.

`minAreaRect` sirve como fallback frontal, pero elimina la perspectiva. `HoughLinesP` tampoco ofrece
por sí solo precisión subpíxel: sus extremos son enteros y hace falta reagrupar segmentos, ajustar
cada lado (`fitLine` o mínimos cuadrados) y refinar intersecciones. La calidad debe decidirse por
convexidad, área, coherencia de lados y cercanía a 63/88 después del warp, no solo por el aspect ratio.

El AAR oficial de OpenCV está en Maven Central desde 4.9. Para el spike conviene usar la línea 4.x
publicada más reciente y evitar OpenCV 5 hasta comprobar compatibilidad y tamaño. No se necesita una
compilación personalizada ni módulos `contrib` para `imgproc`, `warpPerspective`, `fitLine` y
`cornerSubPix`.

### OCR de impresión

Es el componente de mayor valor. Después de rectificar, las regiones son reproducibles y se pueden
probar varias versiones del mismo recorte inferior:

- color original;
- luminancia con CLAHE;
- binarización adaptativa normal e invertida;
- escalado 2× o 3× antes de ML Kit.

El parser no debe depender de una frase completa. Debe puntuar tokens por separado:

- `collector_number`: admite dígitos, letras, guiones, sufijos y formatos como `123/280`;
- `set_code`: normalmente 3–5 caracteres, validado contra las impresiones locales del nombre;
- `language`: validado contra los idiomas disponibles para esa impresión;
- coherencia con el nombre ya reconocido.

La resolución local debe ser la primera opción. Una consulta remota por colección y número no debe
estar en el camino crítico y, si se usa, tiene que incluir idioma para no convertir una carta física
no inglesa en la impresión inglesa por defecto.

Esta vía no cubre todas las cartas antiguas, promos o marcos especiales. En esos casos el resultado
debe quedar como «probable» y pasar al comparador visual existente o a selección manual.

### Hash perceptual

La idea es útil como fallback, pero en esta aplicación **ya existe** y está mejor situada después del
OCR del nombre. Un hash del arte puede reconocer una ilustración, pero no distingue reimpresiones que
comparten arte; para la edición necesita combinarse con símbolo, borde o texto de impresión.

Los «menos de 1 MB» solo cuentan 100.000 hashes de 64 bits en bruto (800.000 bytes). Falta el índice
que relaciona cada hash con impresión, cara, idioma y versión. Sigue siendo viable en local, pero no
conviene asumir tamaño ni latencia sin medirlos en el Sony objetivo. El hash actual usa 256 bits y
compara como máximo 48 candidatas, lo que es mucho más barato y explicable.

### Grading de bordes y centrado

Debe quedar fuera de este spike. Medir geometría de márgenes es posible con la foto rectificada, pero
calificar desgaste por píxeles claros es muy sensible a reflejos, balance de blancos, funda, suciedad,
foil y fondo. Además, ratios como 55/45 o 70/30 dependen del estándar y del grado buscado; no deben
presentarse como una escala universal.

Si se aborda después, necesitará iluminación guiada, control de enfoque/exposición, anverso y reverso,
calibración por dispositivo y validación contra cartas graduadas por humanos.

## Spike propuesto

### Estado del spike

La fase 1 ya dispone de un punto de entrada aislado: `ExperimentalCardScanActivity`. El nuevo botón
flotante del catálogo abre esta pantalla sin sustituir el escáner actual. La pantalla reutiliza por
ahora la cámara, captura y corrección de esquinas existentes; recorta la franja inferior, ejecuta ML
Kit sobre tres variantes y muestra tanto los tokens interpretados como el texto OCR sin procesar.
Este aislamiento permitirá sustituir después la adquisición por CameraX y comparar el detector con
OpenCV sin alterar el flujo estable.

### Fase 0 — Línea base

- Preparar un conjunto etiquetado con fotos reales, no imágenes de Scryfall.
- Conservar nombre, colección, número, idioma, foil/no foil, tipo de marco, fondo e iluminación.
- Ejecutar el identificador actual y registrar top-1, top-3, tiempo, fallo de recorte y motivo de
  fallback.

### Fase 1 — OCR inferior sobre el warp actual

- Crear `PrintingLineOcr` y `PrintingMetadataParser`.
- Ensayar varias bandas inferiores y preprocesados sobre la carta 630 × 880.
- Resolver contra las impresiones locales del nombre reconocido.
- Fusionar evidencias con prioridad:
  1. colección + número + idioma válidos;
  2. colección + número válidos y un solo idioma posible;
  3. comparador visual actual;
  4. elección manual.

Esta fase no añade OpenCV y permite saber si el cuello de botella real es OCR o geometría.

### Fase 2 — A/B del detector de esquinas con OpenCV

- Mantener la corrección manual como red de seguridad.
- Ejecutar detector actual y detector OpenCV sobre las mismas fotos.
- Comparar error de esquina, porcentaje de capturas corregidas manualmente y efecto final en OCR.
- Adoptar OpenCV solo si reduce de forma clara los fallos de lectura o el ajuste manual.

### Fase 3 — Cámara en tiempo real

- Si las fases anteriores prueban valor, migrar a CameraX en un cambio aislado.
- Usar `Preview + ImageAnalysis + ImageCapture`, YUV por defecto y cola de último frame.
- Detectar geometría en baja resolución y analizar impresión en la captura de alta resolución.
- Añadir estabilidad temporal de esquinas y captura automática únicamente cuando enfoque, tamaño y
  ángulo sean estables.

### Fase 4 — Modelo entrenado, solo si hace falta

Un detector de cuatro keypoints o segmentación se justifica si el detector clásico sigue fallando en
fondos complejos después de medirlo. Antes harían falta dataset, etiquetado, partición por carta y
dispositivo, y pruebas separadas para evitar memorizar arte o fondos. No es el punto de partida.

## Criterios de éxito sugeridos

- Dataset inicial: al menos 200 fotos de 50 impresiones físicas, con varias luces, ángulos y fondos.
- Incluir cartas modernas, anteriores a M15, foil, doble cara, showcase/full-art y bordes claros.
- Exactitud top-1 moderna (con línea legible): objetivo ≥ 95 %.
- Exactitud top-3 global: objetivo ≥ 98 %.
- Ninguna impresión exacta debe confirmarse si la evidencia es ambigua.
- Tiempo de análisis de foto cacheada: objetivo < 1 s en el Sony de prueba.
- Detección geométrica de preview: presupuesto < 100 ms y sin bloquear la interfaz.
- Camino normal sin red; llamadas remotas fuera del bucle de cámara y con caché.

## Riesgos que deben probarse expresamente

- El código de colección impreso no coincide siempre con la clave usada por todos los proveedores.
- Números alfanuméricos, tokens, promos y cartas con múltiples caras.
- Carta en funda, reflejos foil y marcos negros sobre fondo oscuro.
- Idiomas con alfabetos distintos y nombre inglés idéntico en una impresión localizada.
- Recortes que incluyan borde físico o eliminen parte de la microtipografía.
- Distorsión de lente cerca de los bordes y orientación EXIF de la captura.
- Cache de referencias incompleta y rate limiting remoto.

## Fuentes comprobadas

- Android CameraX ImageAnalysis: https://developer.android.com/media/camera/camerax/analyze
- OpenCV4Android y Maven Central: https://opencv.org/opencv4android-usage-models/
- Artefacto oficial OpenCV: https://central.sonatype.com/artifact/org.opencv/opencv
- ML Kit Text Recognition v2: https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- ML Kit Document Scanner: https://developers.google.com/ml-kit/vision/doc-scanner/android
- Límites y bulk data de Scryfall: https://scryfall.com/docs/faqs/i-m-having-trouble-accessing-the-scryfall-api-or-i-m-blocked-17

