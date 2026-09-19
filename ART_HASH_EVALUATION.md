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
de cada recorte tardaron unos pocos milisegundos adicionales. Estos tiempos
**no se han medido en Android**; la CLI actual vuelve a leer el JSONL en cada
invocación y es más lenta que una búsqueda con el índice residente.

## Conclusión y siguiente paso

Usar el hash como **generador de candidatos de ilustración**, no como respuesta
única. Aprovechar la rectificación ya existente en
`CardCropAdjustView.extractCardBitmap()`, detectar/ajustar el área de arte en
la carta corregida y contrastar candidatos con nombre OCR y símbolo/número de
edición. El mismo arte se reutiliza en distintas impresiones e idiomas: el
hash **no identifica por sí solo la impresión exacta**. Antes de incorporarlo
al escáner, validar el recorte automático con más cartas, marcos antiguos,
full art, mala luz, fundas y arte repetido, y medir la latencia en el Sony.
