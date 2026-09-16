# Exportación de venta a Cardmarket

La Biblio puede crear CSV compatibles con la extensión abierta
[Cardmarket Bulk Import](https://github.com/PedroPerpetua/cardmarket-bulk-import).

## Flujo seguro

1. En **Biblio**, aplica búsqueda o filtros y pulsa **Exportar para vender en MKM**.
2. Para validar el proceso, elige primero **PRUEBA · 1 copia**. Después se pueden guardar
   lotes agrupados por edición y de un máximo de 100 filas.
3. En Cardmarket abre **Vender → Insertar cartas por edición**, elige la misma edición e
   importa el CSV con la extensión.
4. Revisa nombre, idioma, estado, foil, cantidad y precio. La app no pulsa **Poner en venta**
   ni publica artículos automáticamente.

Los precios se convierten siempre a EUR y se ajustan al estado guardado en la app. Por
seguridad se omiten las filas sin nombre, edición, precio o idioma compatible. Un lote limita
las filas de producto; una fila puede representar varias copias de la misma impresión.

La integración no usa credenciales ni el API privado de Cardmarket. El CSV se guarda mediante
el selector de archivos de Android para que el usuario decida dónde conservarlo o sincronizarlo.

## Web y extensión propia

La alternativa recomendada vive en `web/` y `extension/`:

1. Instala y construye los paquetes con `npm install` y `npm run build:browser`.
2. Abre la web local con `npm run dev:web`, entra con la misma cuenta de Android y selecciona
   cartas de una o varias Biblios. Hay una selección rápida de 150 filas para probar el flujo.
3. Elige el multiplicador global de precio. `×1` conserva el precio de la app y `×10`/`×100`
   permiten hacer pruebas con precios deliberadamente altos. La web muestra ambos importes.
4. Pulsa **Preparar lotes seleccionados**. La web consulta el catálogo público de MTGJSON,
   ordena la edición completa por número de coleccionista y agrupa solo las cartas que aparecen
   juntas en cada página de 100 productos de Cardmarket. Las variantes del mismo `mcmId` nunca
   comparten lote.
5. En `chrome://extensions`, activa el modo desarrollador y carga `extension/dist` como extensión
   descomprimida.
6. Para cada lote, usa **Abrir esta edición en Cardmarket**. El enlace incluye la página exacta
   y fuerza la ordenación `collectorsnumber_asc`; después pulsa **Importar lote MTGFucker**.
   Pega el código, comprueba la coincidencia exacta y rellena.
7. Revisa el formulario. La extensión nunca pulsa el botón final de publicación.

Cardmarket muestra una sola expansión y una ventana del catálogo en cada formulario BulkListing.
Por eso «mixto» significa que el usuario puede seleccionar juntas cartas de cualquier expansión,
pero la web crea una cola de formularios exactos por expansión y por página del catálogo completo.
Un conjunto de 150 filas puede producir más de dos formularios aunque ninguna página contenga más
de 100 cartas seleccionadas. La cola y su posición se conservan localmente para poder continuar
tras recargar la web.

La coincidencia usa el campo oculto `idProduct[]` de Cardmarket contra el `mcmId` de MTGJSON. Si
falta el producto, la edición no coincide o cambia la estructura de la página, se cancela el lote
completo sin escribir parcialmente. El CSV anterior se conserva como plan alternativo.

Los campos `mcmId`, `mcmMetaId`, `mcmSetId`, `mcmSetIdExtras` y `mcmSetName` se guardan en Room y
en el modelo serializado de la colección. Cada sincronización guarda además una proyección JSON
comprimida (`webChunks`) que la web puede leer sin intentar descodificar la serialización Java.
Los borradores creados desde la web se guardan, cuando hay sesión, en
`users/{uid}/cardmarketDrafts/{batchId}`.
