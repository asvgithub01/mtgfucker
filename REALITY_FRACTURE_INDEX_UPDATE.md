# Actualización de índices: Reality Fracture (FRA)

## Alcance
Actualización puntual de FRA, no sincronización automática global ni actualización de precios.
Mantiene los modelos Cornelius 2.12/Milo 1.0.0 y el catálogo base de julio. Añade un
suplemento local de embeddings con la misma normalización RGB/ImageNet 448×448,
normalización L2 y almacenamiento float16 little-endian. No se mezclan modelos.

Los índices de hash se amplían por illustration_id, conservando los artes anteriores.
Se regeneran juntos APV1, diccionario de artistas ligado al SHA de APV1 y títulos TLV1.
El snapshot de trabajo conserva todos los sets originales y sustituye únicamente FRA
por MTGJSON del 27/09/2026. No atribuir esa fecha al resto del catálogo.

## Reproducibilidad
Scripts de generación (Python con numpy, Pillow, onnxruntime, OpenCV e ijson):
- `scripts/update_reality_fracture_indices.py`: descarga paginada Scryfall FRA,
  MTGJSON FRA, imágenes de referencia, embeddings nuevos y hashes por arte.
- `scripts/merge_reality_fracture_metadata.py`: conserva el snapshot anterior y
  genera el lote completo de assets relacionados en staging.
- `scripts/validate_fra_embeddings.py`: prueba de humo con brillo 0.8 y JPEG70;
  no es una prueba de cámara ni garantía de edición/idioma.

Fuentes/imágenes se cachean en `build/reality-fracture-indices/`, fuera de Git;
solo se empaquetan índices y manifiestos, nunca las fotografías de referencia.
Scripts usan rutas del corpus local previo `../mtgfucker-art-hash-data/` y de
modelos validados `build/collectorvision-native-assets/`. No borrar esos originales.
Para otra edición o fecha, generar otro suplemento y actualizar sus SHA/filas;
no sobrescribir silenciosamente un suplemento publicado.

## Seguridad
La app valida SHA/tamaño de ambos archivos del suplemento antes de cargarlo,
y valida dimensiones y UUIDs únicos del índice combinado. Las imágenes y modelos
base no se cambian; las filas viejas mantienen su orden.
No se relajan umbrales ni se interpreta similitud como prueba de cobertura global.
FRA no equivale a FRC (Commander), SPG o toda novedad publicada desde julio.

## Fuentes
- https://api.scryfall.com/sets/fra
- https://mtgjson.com/api/v5/FRA.json
- https://hanclinto.github.io/CollectorVision/assets/manifest.json
- Preprocesado de referencia: CollectorVision `collector_vision/embedders/neural.py`.

## Resultado de generación 28/09/2026
- FRA:461IDs en Scryfall,7 ya en EdScanbase y454 añadidos. Índice combinado110165vectores.
- 424ilustraciones FRA:417ya en hashes y7nuevas;51997artes totales.
- APV1:49944ilustraciones/533654variantes; artistas103330paresresueltos/22faltantespreexistentes; títulos294755.
- EdScan prueba perturbada:16/16UUIDs top1 correctos; no sustituye pruebas físicas.
- Incluye FRA; no afirmar cobertura de FRC/SPG ni todos los idiomas/tratamientos.

## Validación Android y límite observado
- Build APK y471testsJVM correctos; pruebas de SHA/dimensiones/normas del suplemento y lectura FRA en AHI1/APV1.
- Instalación SonyQV770HG2JD `install -r`: Success. No datos de colección modificados por las pruebas.
- Inferencia nativa con3escenas sintéticas de referencia:2/3impresiones exactas top1. Academic Ascent y Blossom-Blessed Angel correctas.
- Emrakul #1 devuelve Emrakul #423 como top1; #1 es top2. Comparten ilustración. La primera prueba estricta deUUIDfalló y se registra aquí; NO se considera resuelta la discriminación de variantes.
- La prueba instrumental final comprueba carga/inferencia/presencia y registra el ranking, no afirma100%de precisión ni hace autoañadido. Datos `build/fra-device-run.log`.
- Pendiente prueba con cartas físicas y corrección de variantes visualmente próximas. No bajar umbrales ni ocultar el fallo de Emrakul.

## Diagnóstico de rendimiento tras FRA (28/09/2026)
- Logs reales: detección+reconocimiento inicialmente ~200–400 ms, después hasta ~4,5 s. Fast seguía activo. También hay guardados completos de 7–9 s; son otra fase, pendiente de aislar.
- A/B ABBA en Sony QV770HG2JD: 2 calentamientos y 6 muestras por bloque, 4 bloques, mismo bitmap sintético y modelos. Mediana total sin suplemento 262 ms; con FRA 230,5 ms. No interpretar la diferencia como mejora: no aparece regresión multisegundo por añadir 454 vectores (0,41 %). Datos privados en build/edscan-timing-abba.json.
- Prioridad cooperativa: mientras EdScan está abierto, los siguientes pases OCR/verificación visual de WorkManager esperan. Una inferencia ya iniciada termina; los jobs persisten y continúan al salir. No se cambian umbrales, modelos ni resolución.
- Debug registra ahora CollectorVisionAssets.VERSION, no la etiqueta antigua fija de julio.
- 473 tests JVM sin fallos y prueba instrumental A/B OK. APK actualizada en Sony sin borrar datos.
- Capturas EdScan examinadas: una borrosa (Abejas asesinas IT) y dos rectificadas invertidas (Hurricane PT, Northern Paladin EN). No son evidencia de que foil cause las confusiones FRA.
- Pendiente repetir cartas físicas antiguas/FRA con cámara priorizada; separar latencia de inferencia, espera de guardado y precisión de variantes antes de dar el problema por resuelto.

## Diccionario OCR: FRA y FRC
Omnath, Locus of the Void es FRC (Commander), no FRA. El suplemento visual FRA no actualizaba el diccionario OCR Room/AtomicCards. Se incluyen 393 alias ingleses de FRA+FRC en `ocr_aliases/release_supplement.tsv`, con procedencia y SHA al lado. Se importan una vez por instancia del resolver, antes de búsquedas exactas, fuzzy y autocompletado; se invalida el índice fuzzy previo. No requiere red ni bloqueo de edición. No cambia índices visuales FRC ni inventa nombres localizados (no disponibles en estos snapshots). Las restricciones explícitas de colección se conservan: seleccionar FRC para Omnath, no solo FRA.

## Corrección de cobertura Collector y modo rápido (28/09/2026)
La actualización FRA anterior era incompleta para sobres Collector. Se añaden
103 impresiones FRC y las 10 SPG de este lanzamiento (fecha 2026-10-02): ninguna
estaba en el índice base. Total EdScan: 110278 vectores. No implica cobertura de
todas las SPG ni actualiza el pHash del scanner de reglas.

Generador: `scripts/build_collector_supplement.py`. Usa el mismo modelo Milo y
preprocesado que FRA. Archivos `collector-20260928-*` en los assets `edscan-fra`,
con procedencia de imágenes/modelo y validación SHA/tamaño al instalar el índice.
Los suplementos FRA ya publicados no se sobrescriben.

El modo rápido anterior aceptaba una lectura >= 0,50 sin margen. Ahora exige:
- Una lectura: similitud >= 0,80 y ventaja sobre el segundo candidato >= 0,08.
- O dos lecturas consecutivas del mismo UUID: similitud >= 0,65 y margen >= 0,04.
- Sin segundo candidato finito no se acepta. El modo seguro no cambia.
Son umbrales iniciales, no probabilidades calibradas. Pueden rechazar variantes
parecidas y no garantizan eliminar falsos positivos. Pendiente ajustar con capturas físicas.

Validación: build correcto, 481 tests JVM sin fallos. APK instalada en Sony
QV770HG2JD sin borrar datos. Prueba instrumental con referencias sintéticas:
Omnath FRC #3 top1 0,76128 frente a 0,64120; Eye of Ugin SPG #159 top1 0,90083
frente a 0,57674. No se guardaron cartas. La primera ejecución falló por una
transferencia de JPEG inválida; corregida con adb push y run-as cp, la prueba pasó.
Esto valida carga e inferencia de dos referencias, no precisión física global.

Aerid Konstrari FRA #407: Wizards describe Japan Showcase en acabado foil
(https://magic.wizards.com/en/news/feature/collecting-reality-fracture).
El usuario observa nonfoil en su ejemplar. Se ha pedido foto del pie y con luz
inclinada; discrepancia pendiente, sin alterar el catálogo ni las cartas guardadas.
