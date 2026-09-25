# Prueba de identificación por arte (Sony, 2026-09-19)

## Segunda tanda: capturas nuevas del Sony

El 19 de septiembre se copiaron **24 JPEG nuevos** del Sony, aunque el usuario
contó 23 cartas (podría haber una captura repetida). La app conservaba 30 JPEG
en total por su límite circular; los originales copiados, las hojas de contacto
y `sony-30-device-eval.log` están en
`../mtgfucker-art-hash-data/evaluation/`, fuera de Git. Se volvió a ejecutar
`ArtHashDeviceEvaluationTest` en el Sony, con el índice empaquetado y el flujo
automático de detección, rectificación y cuatro recortes. El nombre verdadero se
leyó de las cartas fotografiadas, no del resultado del matcher.

| Medida sobre las 24 capturas nuevas | Resultado |
| --- | ---: |
| Nombre correcto en primera sugerencia de arte | 19/24 (79 %) |
| Nombre correcto entre las tres primeras | 21/24 (88 %) |
| Tiempo solo de cuatro hashes y búsqueda, índice cargado | mediana 8 ms; rango 7–13 ms |
| Primera carga del índice en esta ejecución | 194 ms |

Los cinco fallos en primer puesto fueron **Giant Growth** con arte de ardilla
(fuera del top 3), **Prey Upon** (puesto 2), **Chandra's Fury** (fuera del top 3),
**Wildcall** (puesto 3) y un **Eidolon of Blossoms** alemán (fuera del top 3).
Las referencias correctas de Giant Growth y Chandra's Fury sí existen en el
corpus; son fallos de recorte/comparación, no de cobertura del índice. El
Eidolon italiano con el mismo arte sí quedó primero, indicio de sensibilidad
a la foto, la iluminación o la rectificación. En esta tanda, las 15 primeras
sugerencias con distancia pHash ≤10 fueron correctas, pero **no** es un umbral
validado para aceptación automática: la muestra es pequeña y el OCR/símbolo
deben corroborarlo.

Estas cifras no miden el tiempo total de escaneo. En la APK actual, el hash
solo informa candidatos de **nombre/arte**; cuando el OCR ya encontró nombre,
el flujo de decisión de edición sigue siendo el anterior. En particular,
`CardRepository.identifyCardArtwork` puede comparar hasta 48 imágenes de
referencia mediante `CardArtworkIdentifier` y obtener los SVG de los símbolos
de set; los recursos no cacheados implican red. Es una fuente plausible de
esperas ocasionales, todavía no cuantificada por fase con estas 24 capturas.
La siguiente iteración debe medir ese tiempo y utilizar el hash, corroborado
con OCR y metadatos de impresión, para reducir candidatos **antes** de descargar
imágenes, sin confundir una ilustración compartida con la edición exacta.

Evaluación exploratoria con siete JPEG originales capturados por el escáner rápido
en el Sony XQ-DQ54. Las fotos y los recortes permanecen **fuera de Git** en
`../mtgfucker-art-hash-data/evaluation/sony-live/`. No se modificaron datos del
teléfono. El índice local contiene 51.990 art crops de Scryfall, excluyendo Art
Series, y usa el pHash/dHash de `scripts/art_hash_poc.py`.

## Resultado

El nombre de referencia se leyó visualmente en cada carta. Para aislar la calidad
del *matching*, se recortó manualmente solo el arte de cada foto. Se ordenó por
distancia de Hamming de pHash y, en empate, dHash.

| Carta fotografiada | Primer resultado del arte | pHash | dHash | Segundo pHash | Segundo dHash |
| --- | --- | ---: | ---: | ---: | ---: |
| Twilight Shepherd | Twilight Shepherd | 6 | 4 | 14 | 24 |
| Vines of Vastwood | Vines of Vastwood | 2 | 8 | 16 | 22 |
| Archmage Emeritus | Archmage Emeritus | 12 | 15 | 12 | 16 |
| Ebony Horse | Ebony Horse | 6 | 10 | 16 | 19 |
| Llanowar Elves | Llanowar Elves | 8 | 5 | 14 | 24 |
| Colossus of Sardia | Colossus of Sardia | 12 | 12 | 16 | 29 |
| Frogmite | Frogmite | 2 | 6 | 16 | 25 |

**7/7** nombres/ilustraciones en top-1 con el arte recortado manualmente, pero
**0/7** al pasar el JPEG completo directamente a `match`. No son cifras de
precisión extremo a extremo: la extracción automática del arte aún no está
implementada ni medida. Tampoco son una estimación estadística fiable con solo
siete cartas.

Como prueba de sensibilidad del recorte, se generaron 27 variaciones por imagen
(escala 95/100/105 % y desplazamientos horizontal/vertical de -2/0/+2 % del
ancho/alto del recorte). Aciertos top-1 por carta: 26, 26, 14, 22, 22, 7 y 26
de 27, respectivamente. Estos derivados **no son capturas independientes**.
Archmage Emeritus empata a 12 bits de pHash con otra carta y gana solo por un
bit de dHash; Colossus of Sardia, muy oscuro, también es frágil. No conviene
aceptar automáticamente un top-1 solo por una distancia pequeña.

## Tiempo y tamaño

En el PC de desarrollo, con el índice ya cargado en memoria y comparación
vectorizada NumPy, la búsqueda entre las 51.990 entradas tardó **0,15 ms de
mediana** (p95 0,31 ms, 700 búsquedas). Cargar el JSONL tardó unos 95 ms; las
dos matrices de hashes de 64 bits ocupan **831.840 bytes**. La lectura y el hash
de cada recorte tardaron unos pocos milisegundos adicionales. Son tiempos de PC;
la medición Android aparece abajo. La CLI actual vuelve a leer el JSONL en cada
invocación y es más lenta que una búsqueda con el índice residente.

## Índice y prueba automática en el Sony

Se empaquetaron los 51.990 artes en `app/src/main/assets/art_hash_index.bin`
(**3.859.873 bytes**, hashes y metadatos, sin JPEG). El escáner rápido de esta
rama de pruebas busca candidatos en segundo plano después de enderezar la carta
y los muestra como diagnóstico. Si el OCR no identifica el nombre, ofrece una
lista de sugerencias para que el usuario elija antes de consultar ediciones;
**nunca selecciona automáticamente la impresión por hash**.
La prueba instrumental `ArtHashDeviceEvaluationTest` reprodujo la decodificación
EXIF, el detector de bordes, la rectificación a 630×880 y cuatro recortes de arte
sobre los siete JPEG conservados. Resultado top-1:

| Carta | Primer candidato automático | Resultado |
| --- | --- | --- |
| Twilight Shepherd | Mindmoil | Error |
| Vines of Vastwood | Vines of Vastwood | Correcto |
| Archmage Emeritus | Archmage Emeritus | Correcto |
| Ebony Horse | Ebony Horse | Correcto |
| Llanowar Elves | Llanowar Elves | Correcto |
| Colossus of Sardia | Colossus of Sardia | Correcto |
| Frogmite | Frogmite | Correcto |

**6/7** con el flujo automático en estas muestras; insuficiente para aceptar
cartas sin corroborar OCR. La primera versión con tres recortes dio 5/7; el
cuarto recorte, más bajo para arte antiguo, recuperó Colossus of Sardia.
**Se ajustó usando estas mismas fotos**, por lo que 6/7 no es una validación
independiente. En el Sony, cargar el índice por primera vez tardó 203–465 ms
en varias ejecuciones. Con el índice cargado, cuatro hashes y la búsqueda
tardaron 7–13 ms después del primer uso (primera búsqueda ~45–51 ms). Estas cifras
no incluyen la captura, detección de esquinas, rectificación ni OCR. Las dos
fallidas muestran que aún hay que mejorar la localización exacta del arte,
especialmente en marcos oscuros o correcciones de perspectiva imperfectas.

## Conclusión y siguiente paso

Usar el hash como **generador de candidatos de ilustración**, no como respuesta
única. El escáner ya lo ejecuta como diagnóstico tras
`CardCropAdjustView.extractCardBitmap()`, pero debe mejorarse la delimitación
del arte y contrastar candidatos con nombre OCR y símbolo/número de edición.
El mismo arte se reutiliza en distintas impresiones e idiomas: el hash **no
identifica por sí solo la impresión exacta**. Antes de permitir identificación
automática, validar con más cartas, marcos antiguos, full art, mala luz, fundas
y arte repetido; conservar confirmación humana cuando las señales discrepen.

## Laboratorio `feature/improbe-hash-scanner`

El tercer FAB abre `HashOnlyScanActivity`: **sin checks**, solo usa el índice local
para proponer nombres/artes. No ejecuta OCR, consulta el catálogo, compara símbolos
ni descarga imágenes. No confundir este flujo con el escáner rápido normal descrito
arriba. Se precarga el índice al entrar, con loading; tocar dentro de la guía hace
lo mismo que **Capturar ahora**. Se conserva el botón y la corrección de esquinas.

- **OCR: nombre y año**: prepara el índice de nombres locales al entrar/al activar
  el check. Ejecuta `CardTitleOcr` y `PrintingLineOcr`, y contrasta los nombres con
  los alias existentes. Sin catálogo local, avisa y permite desactivar OCR/reintentar.
- **Comparar símbolo**: compara la imagen de la banda de símbolo con los SVG de
  las ediciones locales de los candidatos de arte (más la referencia del índice).
  No hace OCR del dibujo. Los SVG no cacheados requieren internet; cada descarga
  tiene timeout. La cobertura depende de las impresiones guardadas localmente.
- El hash y las dos pasadas OCR arrancan en tres workers. La comparación de símbolos
  depende únicamente de obtener los candidatos del hash, **no espera al OCR**.
  Se publica un único resultado cuando todos los trabajos habilitados terminan,
  incluidos errores parciales; el bitmap no se recicla antes. Los checks quedan
  bloqueados durante el análisis y se conservan entre sesiones.
- Cada fila muestra el candidato hash, texto OCR, nombres canónicos encontrados,
  año/código leído, ediciones locales compatibles con ese año y, si se activó,
  sugerencia visual del símbolo con nombre, icono y distancia. `?` significa que
  no hay corroboración/confianza suficiente, no que la carta esté descartada.
- El año de copyright es solo orientativo. Un símbolo compartido o una ilustración
  reutilizada tampoco identifica una impresión exacta. No hay aceptación automática,
  cambios de colección ni nueva decisión basada en el color del borde.

Los tiempos visibles separan hash, OCR (incluye búsqueda local), símbolo, análisis
completo y tiempo desde captura. El OCR actual procesa varias variantes (10 de
nombre y 7 de impresión): puede dominar la latencia con los checks activados.

### Validación del 20/09/2026

166 tests JVM y dos tests instrumentales iniciales pasan en el Sony: barrera de
finalización, toque dentro/fuera de guía (sin confundir arrastre con click), liberación
del bitmap y las cuatro combinaciones de checks. La prueba del pipeline usa una
carta **sintética** con «Giant Growth» y año 2014; comprueba OCR/contraste local,
no mide precisión de identificación ni de símbolos en cartas reales.

| Checks (prueba sintética) | Hash | OCR + catálogo | Símbolo | Análisis total |
| --- | ---: | ---: | ---: | ---: |
| Ninguno, primera llamada | 189 ms | — | — | 191 ms |
| Símbolo | 23 ms | — | 4.968 ms | 5.106 ms |
| OCR | 14 ms | 2.503 ms | — | 2.507 ms |
| Ambos | 15 ms | 3.372 ms | 3.965 ms | 4.077 ms |

Son cuatro ejecuciones de humo, no un benchmark. Confirman el solapamiento de
OCR/símbolo y que las comprobaciones opcionales dominan el coste frente al hash
caliente. No incluyen captura/rectificación. Falta medirlas con cartas físicas.

También pasa `HashScanActivityDeviceTest`: arranque real del laboratorio, fin de
loading y visibilidad de los dos checks y del botón. Se revisó el layout renderizado
en el Sony; el área de cámara queda libre entre controles y botones.

### Inicio desde preview, antes de la foto

La siguiente iteración elimina del camino normal del laboratorio hash la captura
JPEG. Cuando OpenCV lleva al menos tres detecciones coherentes y alcanza el 34 %
de la estabilidad exigida para disparar la cámara, se rectifica directamente el
plano Y del frame de `ImageAnalysis` a 630×880 y comienza el hash. Por tanto ya no
espera `ImageCapture`, escritura/decodificación JPEG, segunda detección de bordes,
pantalla de corrección ni los 200 ms del autoanálisis. El botón de captura conserva
el flujo de foto y corrección como fallback manual.

El primer resultado hash se publica en cuanto termina aunque estén activados OCR
o símbolo; esas comprobaciones continúan en paralelo. Si OCR confirma a la vez el
nombre, código de set y collector number y queda una única impresión local, la fila
la marca como edición resuelta. Año, arte o símbolo aislados siguen sin aceptarse
como prueba inequívoca. Falta validar la calidad del recorte live con cartas físicas;
el JPEG tiene más detalle y puede seguir siendo preferible como fallback en casos
difíciles.

### Geometría 63:88 antes del hash

Una captura posterior de Twilight Shepherd mostró el fallo dominante: el detector
colocó las dos esquinas superiores bastante por encima del borde físico, produjo un
quad demasiado alto y el hash devolvió Banding Sliver. El detector anterior admitía
ratios entre 0,54 y 0,85 pese a que una carta MTG mide 63:88 (0,716).

Ahora se rechazan quads que se alejen más de un 12 % del aspect ratio físico o tengan
lados opuestos excesivamente desequilibrados. La búsqueda asistida amplía el rango
horizontal del 10 al 20 %, conserva hasta cinco líneas superiores/inferiores distintas
y evalúa conjuntamente sus pares; solo los cuatro pares geométricos mejores pasan por
la comparación cara de bordes. Si las detecciones live y JPEG discrepan, se elige la
de mejor geometría+confianza en vez de preferir siempre el JPEG.

Como comprobación regresiva, la nueva política geométrica aplicada a las esquinas
guardadas en `sony-30-device-eval.log` rechaza 3/30 recortes antiguos. Eran precisamente
los tres ratios anómalos (0,562, 0,593 y 0,619) y sus top-1 habían sido Torrent of Lava,
Gravecrawler y Enigma Eidolon, no identificaciones válidas conocidas. Los otros 27
quedan dentro del umbral. Falta repetir la detección completa en dispositivo para
comprobar que la búsqueda ampliada encuentra el borde correcto en vez de limitarse a
rechazar el encuadre.


### Símbolo V2 opt-in (22/09/2026)

En el panel plegable del laboratorio hash, activar **Símbolo V2: resolver colección**.
Está apagado por defecto, persiste al reiniciar y sustituye el comparador V1 cuando
ambos checks están activos. Se recomienda activar también OCR; sin OCR, la identidad
es el primer candidato de arte y conserva el umbral de autoañadido existente.

1. Esperar al hash y OCR. Con OCR activo, exigir un candidato de arte cuyo nombre
   coincida; no aceptar el primer hash si OCR lo contradice o no leyó nombre.
2. Limitar referencias a los sets de las variantes locales de esa ilustración.
3. Buscar contornos en x=70–98 %, y=48–68 % de la carta rectificada, normalizada a
   744 px de ancho. Separar siluetas con umbral adaptativo y Canny (incluyendo grupos de piezas
   desconectadas), recortar y
   normalizar cada una con padding y relación de aspecto conservada.
4. Comparar silueta binaria (75 %), pHash DCT de 63 bits (15 %) y aspecto (10 %)
   frente a los SVG cacheados de Scryfall. Las distancias pertenecen al mismo recorte,
   no a dos posiciones independientes de una banda amplia.
5. Exigir distancia <=0,24 y margen >=0,055 frente a otro set. Una sola referencia,
   descarga fallida, empate o posible Chronicles mantiene la edición sin confirmar.
   El presupuesto de carga es 6 s, máximo 64 sets por intento; los ausentes se registran
   y bloquean la confirmación. Las referencias se reutilizan en disco y memoria.
6. Cruzar el set ganador con filtros existentes (OCR set+número, idioma, borde).
   Solo autoañadir si queda un UUID, sin fallback a variantes sin filtrar ni a consenso
   OCR. Las exclusiones LEA/LEB/ARN/ATQ/LEG/DRK siguen vigentes.

**Evidencias:** el guardado anterior sí existe: cada copia autoañadida conserva JPEG
rectificado y JSON en el historial de `CardInfo`, visible en el detalle. No guardaba
intentos rechazados. V2 añade un buffer independiente, local y privado con los últimos
30 intentos en `files/symbol_scan_v2/<timestamp-uuid>/`: `card.jpg`, `metadata.json` y
`symbol.png` si hubo un recorte comparado. JSON incluye identidad, OCR, candidatos,
banda, coordenadas XYWH por set, hashes, puntuaciones, margen mínimo y causa del rechazo.
Las copias añadidas conservan además el bloque `symbolV2` en su historial habitual;
la rotación del buffer no borra esas fotos de colección.

Los umbrales son experimentales: falta medir acierto/rechazo con cartas físicas.
No se soportan aún símbolos fuera de esa banda, y se rechazan símbolos compartidos
que no permitan distinguir sets. El recorte live 630×880 sigue siendo el origen normal;
V2 no introduce captura JPEG previa. Sí guarda un JPEG de ese frame después del análisis.
Las tareas de imagen, red y persistencia se ejecutan fuera del hilo UI.

Validación local: `gradlew.bat :app:testDebugUnitTest :app:assembleDebug
:app:assembleDebugAndroidTest --console=plain`. La suite incluye 8 pruebas nuevas de
política V2; `SetSymbolHashMatcherDeviceTest` prueba segmentación/escalas, imágenes vacías,
referencias iguales/ausentes y Chronicles sin red ni cambios de colección.

La actualización de la app se instaló con `adb install -r` en el Sony QV770HG2JD,
sin desinstalar ni borrar datos. Se verificaron 298 fotos anteriores en `scan_evidence`.
El paquete de pruebas preexistente tenía otra firma; se instaló un paquete independiente
`io.asv.mtgocr.ocrreader.symbolv2test` mediante un init script local (no versionado):

```groovy
allprojects {
    plugins.withId('com.android.application') {
        android.defaultConfig.testApplicationId = 'io.asv.mtgocr.ocrreader.symbolv2test'
    }
}
```

Pasarlo con `--init-script build/symbol-v2-test-id.gradle`; el runner de instrumentación
es `io.asv.mtgocr.ocrreader.symbolv2test/androidx.test.runner.AndroidJUnitRunner`.
`HashSymbolV2PipelineDeviceTest` valida un intento rechazado, liberación del frame y
persistencia de foto/JSON sin autoañadido; no escribe en la colección.

Resultado final del 22/09: **200 tests JVM y 6 tests instrumentales en el Sony pasan**,
y `git diff --check` no detecta errores. El test de símbolo multipartes detectó y permitió
corregir pérdida de contornos interiores: V2 usa `RETR_LIST` y no agrupa un contorno
contenedor como si fuera otra pieza del símbolo. La APK corregida quedó instalada con
`install -r`; queda pendiente medir precisión con cartas físicas y calibrar umbrales.


### Corrección con capturas reales de Origins y Modern Masters (22/09)

Los recortes eran correctos, pero el veto `referencias_incompletas` ocultaba una URL
incorrecta: los archivos `ps11.svg`, `plst.svg` y `sld.svg` no existen. El recurso
`icon_svg_uri` de `/sets/{code}` apunta respectivamente a `psal.svg`, `planeswalker.svg`
y `star.svg`. `SetSymbolSvgSource` conserva el intento directo rápido y ante HTTP 404
consulta esa URL oficial; mantiene la caché por código de set. Un fallo de red real
sigue bloqueando la confirmación, no se ignoran referencias arbitrariamente.

Además, PLST se compara por su glifo **impreso**, no por el logo de catálogo: el índice
identifica `Vines of Vastwood` de The List como `ZEN-193`, así que comparte referencia
con ZEN. Si el símbolo es ZEN las dos ediciones empatan y se abstiene; si es MM2 puede
confirmarse Modern Masters 2015. Un collector PLST sin origen reconocible queda pendiente.

La silueta tiene más peso que el DCT de un símbolo de apenas unas decenas de píxeles:
75/15/10, con los mismos límites de distancia 0,24 y margen 0,055. El algoritmo se guarda
como `symbol-silhouette-phash-v2.1`, con pesos y `referenceSet` en los metadatos. La UI
muestra también el mejor candidato cuando no confirma, el margen y los códigos ausentes.

Se conservó fuera de Git un corpus privado de ocho fotos reales (tres positivas y cinco
controles no confirmados), y se reprodujo en el Sony sin escribir cartas en la colección:

| Captura | Resultado V2.1 | Distancia / siguiente |
| --- | --- | --- |
| Vines of Vastwood | MM2 #168 | 0,236 / 0,333 |
| Negar, captura 1 | ORI #65 | 0,154 / 0,416 |
| Negar, captura 2 | ORI #65 | 0,151 / 0,406 |
| Cinco intentos dudosos | No confirmados | Se mantiene abstención |

`SetSymbolSavedScanDeviceTest` usa un manifiesto opt-in en `cache/symbol_v2_replay/`
con `file`, `illustrationId`, `expectedSet`, `expectedUuid`; si no está instalado lo omite.
Las imágenes no se incluyen en los assets ni en Git. El replay no sustituye más pruebas
de cámara en vivo ni demuestra precisión general con todas las colecciones.

Validación final de la corrección: **202 tests JVM y 15 instrumentales OK**. El replay
incluye el pipeline completo de las tres capturas positivas con OCR, borde e idioma
apagados y V2 encendido, exactamente como en los checks del usuario: obtiene el UUID
esperado y supera la política de autoañadido (sin escribir en la colección en el test).
APK instalada de nuevo con `install -r` sin borrar datos. Falta repetir capturas live.


### V2.2: nuevas capturas Sony (22/09/2026)

La revisión de 30 intentos posteriores encontró dos problemas adicionales: banda derecha
que cortaba una mitad de MM2 y encuadres de arte que descartaban la zona inferior de la
ilustración moderna. El hash elegía otra carta y el matcher ni siquiera comparaba MM2.

- Banda x=.70–.995; los contornos solo delimitan, no rellenan el interior de la máscara.
- Máscara de tinta a 64×64, umbral adaptativo y Otsu local para conservar trazos/huecos.
- Tres encuadres modernos adicionales de arte solo bajo `symbolV2`; otros modos intactos.
- Umbral máximo .24, margen .055, pesos .75/.15/.10 y guardas de edición sin cambios.
- Referencias incompletas, símbolos compartidos, Chronicles y UUID no único siguen bloqueando.

Corpus privado: `../mtgfucker-art-hash-data/evaluation/sony-symbol-v2-20260922-latest/`.
Resultados en las mismas 30 fotos (no una sesión de cámara nueva): MM2 pasa de **2 a 10**
confirmaciones, ORI de **2 a 3**. No hubo confirmación errónea en esta muestra; no es una
estimación general de precisión. **17 abstenciones**, incluidas las **7 fotos M15**,
reversos, encuadre incorrecto y varias MM2 aún no fiables. M15 ya conserva información
interior, pero la separación entre referencias sigue siendo insuficiente: pendiente.

`SetSymbolSavedScanDeviceTest.replaysLatestFramesAndPreservesAbstentions` usa un segundo
manifest privado en `cache/symbol_v2_latest/manifest.json`, con el mismo formato del
corpus anterior. Verifica arte/UUID en positivos y mantiene abstenciones en el resto.
El test del flujo completo reproduce los 3 positivos originales y los 13 nuevos con
OCR/V1/borde/idioma apagados, comprueba UUID y elegibilidad de autoañadido sin modificar
la colección. Se inspeccionaron las fotos para verificar las etiquetas; nunca se incluyen
en APK/Git. Añadidas regresiones sintéticas con idéntico contorno externo y distinto
interior, y símbolo multipartes más allá del antiguo límite derecho.

Validación: 202 tests JVM, 18 instrumentales en Sony, build debug correcto. APK instalada
mediante `install -r` sin desinstalar ni borrar datos. Nuevas pruebas de cámara pendientes.


### V2.3: rarezas metálicas, M14/M15 y corrección de bordes (22/09/2026)

La comparación sigue limitada a las ediciones de la identidad de carta elegida por arte/OCR,
no a todo el catálogo. Ahora el panel y los metadatos muestran esa identidad y `comparedSets`.
Si el arte identifica otra carta, este filtro también será incorrecto: el símbolo no sustituye
la identificación inicial.

- Silueta externa e interior fotográfico se normalizan por separado a 64×64. Para símbolos
  con detalles, correlación interior absoluta (polaridad común/metálica), alineación ±1px
  y regiones discriminantes entre referencias con contorno parecido, como M14/M15.
- Pesos de detalles .35 silueta/.55 contraste/.10 aspecto; símbolos abiertos conservan
  .75 silueta/.15 pHash/.10 aspecto. Todos los sets compiten sobre el mismo glifo localizado.
- Segmentación original y CLAHE, bordes/adaptativo, contornos crudos y cerrados; banda
  x=.60–.995, y=.43–.71 y máximo 140×80 a ancho normalizado 744 para logos anchos.
- Distancia máxima .24 y margen .055 sin relajar. Con un único set candidato se exige
  distancia válida, pero no un segundo set inexistente. Referencias ausentes, glifos
  reutilizados y UUID ambiguo siguen bloqueando.
- Al fallar V2 se ofrece «Corregir bordes», usando el frame completo retenido, no el
  recorte ya truncado. Ajustar esquinas y reanalizar no vuelve accidentalmente a cámara.

Reproducción de **68 fotos privadas reales** del Sony, no una sesión nueva de cámara:

| Corpus | Confirmaciones correctas V2.3 | Rechazadas |
| --- | --- | --- |
| Ocho originales | 3 (MM2 y ORI) | 5 |
| Treinta anteriores | 20 (12 MM2, 5 M15, 3 ORI) | 10 |
| Treinta de nueva Biblio/rareza | 15 (8 ORI, 2 M13, 3 DDF, JOU, HML) | 15 |

Incluye Runed Servitor ORI **plata** y dos capturas Serra Avenger M13 **oro**. Cinco
M15 antes ambiguas confirman M15. Frogmite/Ranácaro fotografiado es **DDF**, no MM2:
etiqueta revisada en foto completa. No hubo confirmaciones incorrectas en esta muestra;
no es una estimación de precisión general. **Siguen fallando 9ED oro**, algunos MM2/M15
y recortes borrosos/incompletos. Mayor tiempo de símbolo observado en estos replays: 471ms.

Fixtures migrados de `cache/` a `no_backup/`: el Sony casi lleno eliminó archivos de caché
al instalar. Los tres directorios son `symbol_v2_replay`, `symbol_v2_latest` y
`symbol_v23_replay`; fotos/manifiestos conservados externamente bajo
`../mtgfucker-art-hash-data/evaluation/sony-symbol-v23-20260922/` y corpus previos.
El manifiesto exige `expectedSet`/UUID/arte para positivos; si antes se rechazaba, solo
admite una mejora hacia `allowedSet` físicamente verificado. Mano/reverso/encuadre vacío
siguen exigiendo rechazo. No se incluyen fotos en APK ni Git.

Validación: **202 JVM y 21 instrumentales OK** (45,905s), incluidos 38 positivos por
pipeline completo con los checks del usuario, rarezas/números sintéticos en ambas
polaridades, restricción de referencias a candidatos, corrección de esquinas y liberación
de bitmap. Los tests no añaden cartas a la colección. Instalación `install -r` en Sony;
pendiente validar el botón de corrección con cámara física y nuevas cartas en vivo.


### V2.4: símbolo ausente, veto OCR y ráfaga de cinco intentos (22/09/2026)

Nuevos 30 intentos privados en
`../mtgfucker-art-hash-data/evaluation/sony-symbol-v24-20260922-162901/`.
Esta vez **OCR estaba activado**, pero borde/idioma desactivados. La captura Serra Avenger
M13 con distancia **.135** ya tenía `simbolo_confirmado`, rival FDC .429, pero **cero
variantes compatibles**. Otras tres M13 repetían el problema. El pie OCR mezclaba reglas,
`3/3`, palabras como `de` y copyright: el parser heurístico inventaba metadatos que
vetaban una impresión correctamente reconocida. No era un fallo de distancia/margen.

`HashSymbolPrintingEvidence` solo autoriza filtros V2 de pie ante una fila corta explícita
`SET IDIOMA`; el collector debe estar en la fila inmediatamente anterior y tener formato
válido. No toma fuerza/resistencia, un número remoto ni palabras de reglas como idioma.
Mantiene texto bruto/año/nombre para diagnóstico. El parser de otros escáneres no cambia;
V2 respeta el flag de idioma al filtrar variantes. El panel distingue símbolo confirmado
con impresión aún bloqueada, distancia excesiva y margen insuficiente, mostrando top3.

`PrintedSetSymbolPolicy` excluye logos retrospectivos de LEA/LEB/2ED/3ED/4ED. Para 5ED
conserva la excepción chino simplificado; con idioma desconocido conserva ambos casos.
Fuente primaria: https://magic.wizards.com/en/news/feature/fifth-edition-symbol-2002-12-12
No todas las expansiones antiguas carecen de símbolo. Si todos los sets candidatos carecen
de él, se omite la lectura sin confirmar edición; si hay mezcla, se muestran las alternativas
sin símbolo y no se convierte un fallo de captura en prueba de ausencia. Alcance conservador:
no cubre todavía todas las promociones, tratamientos alternativos o básicos antiguos.

Nuevo flag persistente `symbol_retry`, apagado por defecto, exige V2. Máximo cinco frames
live nuevos con estabilidad completa y 900ms de espera desde enlace de cámara. Se detiene
al confirmar símbolo o ante condiciones que otra foto no arregla (referencias ausentes,
Chronicles, no aplicable). Corregir la misma foto no inicia una ráfaga; pausa/corrección y
repetición manual reinician/cancelan la serie. Se guarda el número de intento en evidencias.
Se corrigió además el enfoque: `PreviewView` recibe coordenadas en píxeles y antes se usaba
`.5,.5`, cerca de esquina, en vez de ancho/2,alto/2. La espera no garantiza por sí sola foco
óptico perfecto: es necesaria validación física.

El corpus nuevo también muestra recortes con mucho fondo (identidad no confirmada),
Frogmite MRD, Dark Ritual TMP y Archmage Emeritus SPG rechazados por distancia. Feral
Throwback empata LGN/PLGN (mismo símbolo); no forzar una edición bajando umbrales. No se
ha declarado resuelto el fondo ni se han recalibrado umbrales con estas imágenes.
Prueba opt-in `confirmedM13IsNotVetoedByRulesTextInFooterOcr` reproduce seis capturas
con OCR activo, cuatro de ellas M13, desde `no_backup/symbol_v24_ocr` sin añadir cartas.

Validación V2.4: **212 JVM y 23 instrumentales OK** (64,091s). Los seis positivos nuevos
con OCR activo (cuatro M13, ORI y GTC) resuelven impresión y política de autoañadido.
Los 68 replays anteriores conservan sus requisitos. La cobertura de reintentos es de
política/estado, no sustituye la prueba física de la ráfaga y del reenfoque.

### V2.5: Tempest, marco futurista y resolución (22/09/2026)

Treinta intentos preservados fuera de Git en
`../mtgfucker-art-hash-data/evaluation/sony-symbol-v25-20260922-170853/`.
Lumespectro/Ghostfire FUT fallaba antes del símbolo: el recorte de arte estándar incluía
la columna de maná izquierda y elegía otra identidad, aun con título OCR correcto.
V2 añade el encuadre `futurista` (.20,.13,.98,.55), sin habilitar identificación por OCR solo.
Dark Ritual TMP sí identificaba el arte, pero ponderaba al 55% los diminutos huecos del
rayo (aprox. 4,8% de soporte). El detalle interior requiere ahora >=10% de soporte salvo
familias de silueta similar; estas siguen diferenciando números M14/M15 y huecos pequeños.
Se excluye la propia referencia al buscar familias similares. Distancia .24 y margen .055
no cambian; tampoco la obligación de una impresión inequívoca.

Replay con las opciones OCR originales de cada captura, sin añadir cartas:
- **24/24 Ghostfire FUT** resuelven la impresión correcta.
- **4/5 Dark Ritual TMP** resuelven; el JPEG nº26 sigue en .252 y se rechaza.
- Control Zhalfirin Commander MIR sigue sin confirmar.
La prueba exige los 28 positivos y solo admite TMP/null para nº26, con la limitación
documentada en el manifiesto. No es una medición de precisión general ni una validación
de todas las cartas con marco futurista. Fixtures: `no_backup/symbol_v25_replay`.

Con V2 activo se pide cámara 1920×1440 mediante `ResolutionSelector` 4:3, con fallback
a resolución soportada. En Sony, prueba real de Preview+ImageAnalysis+ImageCapture entrega
**1440×1080**; no 1920×1440. La primera prueba con `setTargetResolution` entregaba
1080×1080 y se descartó. La resolución efectiva puede depender del dispositivo/use cases.
La carta se rectifica a **1260×1760** (antes630×880) y el matcher segmenta a ancho
**1488** (antes744), escalando kernels y límites geométricos. Entradas antiguas <1000px
conservan el camino744; V2 apagado conserva cámara/rectificación anteriores.
El panel y metadatos muestran tamaño de carta y ancho de segmentación; el log `hash_frame`
conserva tamaño de frame real. Esto evita descartar detalle disponible, no crea detalle
óptico por interpolación. El detector de bordes sigue reducido para limitar coste.

La prueba HD sintética verifica coordenadas/tamaños y reconocimiento, no calidad óptica.
Los replays anteriores son JPEG de baja resolución: hace falta volver a fotografiar las
cartas para evaluar el beneficio de HD, el foco y los cinco intentos en condiciones reales.

Validación final: **214 JVM** sin fallos/errores/omisiones y **27 instrumentales OK**
(117,913s), incluyendo todos los corpus anteriores, el nuevo replay y cámara real.
Caso sintético HD1488×2080: 262ms de matcher en Sony (una medición, no benchmark general).
`BUILD SUCCESSFUL` y `git diff --check` limpio. APK actualizada con `install -r`, sin borrar
datos; SHA256 local/base.apk coincidente
`cdada19495e8bf1f49239fe5d08cffd83ff15df6c2a3b325dea0e9ba3cdbcdc5`.
Sony QV770HG2JD, última actualización 22/09/2026 17:22:04; rama
`feature/improbe-hash-scanner` visible en splash.

### Selección no foil y copias consecutivas (22/09/2026)

El catálogo APK de Aladdin’s Ring 9ED contiene 10 filas, no 10 diseños: nueve idiomas
del UUID normal `22fc1065-f041-57cd-931f-1ceb4b9a5745` (286, borde blanco) y una fila
del UUID foil `98fcbe88-c5a6-542c-9b9e-41c6b8b88cc9` (286★, borde negro). Ambos comparten arte.
V2 prefiere registros que admitan nonfoil dentro de los candidatos compatibles del set;
si aún quedan varios UUID normales, mantiene el bloqueo. No relaja matching de símbolo
ni exclusiones históricas. OCR apagado usa EN; con evidencia de idioma se prioriza esa fila.
La carga posterior de caché no sustituye el acabado normal por otra opción foil.

`RepeatedScanCopies` intercepta la segunda detección consecutiva de impresión/acabado/idioma
iguales en el laboratorio hash. No añade otra copia: desvincula cámara y muestra carta,
colección y «Número de copias» con botones −/+ y campo editable. El total inicial incluye
la primera copia ya guardada; elegir 4 añade 3. Las copias anteriores de la biblioteca
no forman parte de ese total. Cancelar/volver atrás no borra ni reanuda la cámara.
Tras confirmar se mantiene pausa hasta pulsar «Escanear otra carta»; si sigue la misma
carta, reabre con el total guardado sin volver a añadirlo. Una impresión distinta inicia
un lote nuevo. El límite es 99; reducir por debajo de lo ya guardado requiere «Deshacer».

El lote se persiste con una sola lectura/guardado legacy, evidencia/foto independiente
por copia para que deshacer no borre fotos compartidas, selección Room exacta y contador
de sesión por copia. Se bloquea guardar dos veces y relanzar cámara durante el diálogo.
No modifica bibliotecas antiguas ni fotos previas. Textos disponibles en ES/EN/DE/FR/PT/IT.
Pruebas nuevas cubren secuencia repetida, total/diferencia, input inválido, idiomas/acabados,
deshacer, cantidades antiguas y los controles reales del panel; el catálogo APK real
verifica elección de 9ED 286 normal EN. Pendiente prueba física del diálogo integrado
con autoañadido; las pruebas instrumentales no añaden cartas a la biblioteca del usuario.

Validación: **219 JVM** sin fallos/errores/omisiones, **29 instrumentales OK** (121,46s)
y `git diff --check` limpio. APK instalada `install -r` en Sony QV770HG2JD a las 20:50:00,
SHA256 local/base.apk idéntico `f48791545994aaa452dc8f927a882ab01a55064408be84534c21087435bd684b`.
Diagnósticos previos a pruebas preservados fuera de Git en
`../mtgfucker-art-hash-data/evaluation/sony-before-copies-20260922-204953/attempts.tar`.

### Recortes automáticos independientes del símbolo (23/09/2026)

`ArtHashMatcher.match` prueba las ocho plantillas de arte también con OCR y símbolo
apagados. Se elimina `extendedFrames`: el flag V2 sigue controlando el reconocimiento
de símbolo y la resolución HD, pero no el encuadre interior del arte. No cambia la
detección de bordes exteriores, el índice, las distancias ni el autoañadido.

El Sony tenía `symbol_v2=false`: antes omitía el encuadre futurista. La imagen italiana
de Sarcomite Myr proporcionada por el usuario, aislada manualmente del margen exterior
para la prueba, se reconoce como primer candidato en el pipeline Android sin OCR ni
símbolos. Fixture privado: `no_backup/art_frame_replay/sarcomite-myr.png`; no incluir
la imagen en Git/APK. No equivale a validar la detección automática de bordes en cámara.

El símbolo FUT de esa imagen también se confirma con la banda existente x=.60–.995,
y=.43–.71 (distancia .0973, máximo .24). No se amplía la región ni se relajan umbrales.
`ArtHashFrameDeviceTest` cubre encuadres sintéticos normal/moderno/futurista, el pipeline
sin símbolos y el símbolo de la imagen privada. `SetSymbolHashMatcherDeviceTest` añade
glifos próximos al borde derecho en anchos 744 y 1488. Las dos pruebas que requieren
la imagen privada se omiten si falta el fixture; comprobar que no se omitan al validar
este caso. La prueba pendiente es escanear la carta física sin ajuste manual.

Validación: **219 JVM** y **22 instrumentales** correctos, sin omisiones (15 de
encuadres/símbolos y 7 del pipeline/replays anteriores). `BUILD SUCCESSFUL` y
`git diff --check` limpio. Actualizada mediante `install -r` en Sony QV770HG2JD,
23/09/2026 09:21:11, sin borrar datos ni cambiar sus opciones. SHA256 local/instalada:
`fa2c3765281b03282a87a8fa18a41fd1cb59b3b7b8f7f23c8bb705a3ec193359`.
Diagnósticos anteriores preservados fuera de Git en
`../mtgfucker-art-hash-data/evaluation/sony-myr-before-20260923-091749/`.

### OCR primero y selector de edición opt-in (23/09/2026)

Nuevo check **«OCR primero + selector de edición (prueba)»**, preferencia
`hash_scanner/edition_picker`, apagado por defecto. Con él activo:

- Se fuerza OCR de nombre/pie sin modificar la preferencia anterior del check OCR.
  Se mantiene la configuración de cámara del modo sin V2 y la carta a 630×880;
  activar símbolo V2 no cambia cámara/encuadre. Símbolo V1 y reintentos live quedan
  fuera de este flujo. V2, si se marca, analiza la misma captura tras confirmar identidad.
- Solo una identidad coincidente entre OCR y candidatos de arte permite continuar.
  Nunca se usa el primer hash por defecto ni el atajo antiguo de consenso OCR. Si varios
  artes candidatos tienen el mismo nombre, sus variantes se conservan juntas para no
  confundir identidad con impresión exacta.
- Pie estructurado con código/número únicos puede resolver impresión sin símbolo.
  Si falta, se intenta V2 opcional. Año, existencia en caché y un único set en el catálogo
  no autorizan por sí solos el autoañadido. Conflictos o varios UUID mantienen la duda.
- Si no hay edición inequívoca, se pausa y muestra un grid desplazable de tres columnas
  con símbolo, código y nombre de los sets candidatos. También se pide elección si el
  usuario tiene autoañadir apagado. Un toque añade por la ruta habitual legacy/Room;
  varios UUID normales en el set abren otra selección con miniaturas/número de carta.
  Se prefiere nonfoil como en V2. Sets históricos solo se aceptan mediante elección manual.
- Cancelar no añade ni reanuda la cámara; se continúa con «Escanear otra carta». El grid
  consume un único toque y se bloquean rebind/otra selección mientras llega la impresión.
  La evidencia guarda `editionPicker` y `manualSetCode`; también se conservan diagnósticos
  de intentos pendientes. No se modifica la colección antigua ni las cartas erróneas previas.

`HashEditionResolutionPolicyTest` cubre identidad, ambigüedad, conflicto, UUID, idiomas,
acabados y sets históricos. `HashEditionPickerDeviceTest` reproduce las capturas reales
Myr/SPG (privadas en `no_backup/two_stage_replay`) a anchos 630 y 1260 con V2 on/off;
también prueba selección única y grid de 18 sets a ancho 320dp. El PNG sintético se
inspeccionó visualmente. En el replay SPG, el OCR confirma nombre/set/idioma pero no
número: se conserva la identidad y debe pedirse SPG en el popup en vez de adivinar.
Las pruebas no añaden cartas a la biblioteca; queda la comprobación física del popup
y del guardado al tocar una colección. Fotos/logs fuera de Git en
`../mtgfucker-art-hash-data/evaluation/sony-two-stage-20260923-110317/`.

Validación final: **227 JVM** sin fallos/errores/omisiones y **25 instrumentales OK**
(142,049s, sin omisiones), `BUILD SUCCESSFUL` y `git diff --check` limpio. APK actualizada
con `install -r`, sin borrar datos, Sony QV770HG2JD, 23/09/2026 11:09:31. SHA256
local/base.apk idéntico `c6457db342c8e945edc8951d1790015cf25842c6311d4f6bbf8ea0f02074972d`.
El nuevo flag se deja apagado por defecto y las preferencias previas no se modifican.


## Auditoría de resolución de referencias (2026-09-24)

El código actual descarga `art_crop` de Scryfall sin redimensionarlo en `download_art_crops.py`. Excluye missing/placeholder, pero no exige highres_scan. `art_hash_poc.py` convierte a gris, reduce a32×32 y usa63comparaciones DCT (DC excluido), más64bits dHash desde9×8. `ArtHashMatcher.kt` reproduce esas operaciones; el empaquetador almacena dos enteros64bits. No se han regenerado assets ni modificado umbrales.

Mayor resolución de entrada puede ayudar si la referencia es mala, pero no aumenta la información conservada por la firma fija. El reescalado artificial no recupera detalle. Dos ediciones con idéntica ilustración seguirán necesitando otras evidencias. Véase [diseño de pHash](https://www.phash.org/docs/design.html).

Experimento propuesto, pendiente: seleccionar capturas reales con confusiones y controles, conservar baseline y etiquetas verificadas de ilustración; auditar resolución/image_status/recortes de sus referencias; comparar origen actual y mejor fuente disponible con EXACTAMENTE las mismas fotos. Medir top1/top5, margen, falsas aceptaciones y latencia. Separar ajuste de evaluación reservada. Si no mejora, evaluar alineación/recorte antes que descarga masiva; descriptor más largo o segunda etapa requieren nuevo formato/versionado, implementación Android y recalibración de umbrales. Solo regenerar globalmente tras mejora comprobada sin regresiones. No hay todavía evidencia A/B de que referencias mayores mejoren este corpus.
