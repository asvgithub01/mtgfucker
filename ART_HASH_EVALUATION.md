# Prueba de identificación por arte (Sony, 2026-09-19)

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
