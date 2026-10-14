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
   ordena la edición completa por nombre inglés y agrupa solo las cartas que aparecen
   juntas en cada página de 100 productos de Cardmarket. Las variantes del mismo `mcmId` nunca
   comparten lote.
5. En `chrome://extensions`, activa el modo desarrollador y carga `extension/dist` como extensión
   descomprimida.
6. Para cada lote, usa **Abrir esta edición en Cardmarket**. El enlace incluye la página exacta
   y fuerza la ordenación `name_asc`; después pulsa **Importar lote MTGFucker**.
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

## Valoración con la BuyList de Itaca

La misma web incluye **Valorar mi colección**. Usa la Biblio seleccionada —o todas— y cruza
el `mcmId` de cada impresión con el Product ID de Cardmarket publicado por Itaca. Muestra el
total estimado en efectivo e Itacoins, la cobertura del matching y todas las filas con precio
unitario y total por copias.

El catálogo estático se actualiza y publica en `web/public/data/itaca-buylist.json.gz` con:

```powershell
npm run refresh:itaca
```

La valoración usa la oferta base visible en el listado de Itaca, normalmente inglés, NM y no
foil. Idioma, estado y acabado se muestran para revisar la colección, pero todavía no ajustan
esa oferta; obtener precios exactos de cada variante requerirá una integración acordada con
Itaca o consultas puntuales posteriores.

## Compras / Wants Lists (no venta)

La pestaña **Compras** mantiene listas independientes del inventario. Desde una colección,
**Exportar buylist MKM · cartas faltantes** permite crear o elegir una lista. El buscador
flotante consulta cartas y reimpresiones sin añadir copias poseídas; los menús permiten
agregar la impresión concreta a una buylist. Selección múltiple y borrado afectan solo a Compras.

Los totales son **referencias MKM en EUR**, sin portes. NM es el estado solicitado; MTGJSON
no proporciona aquí ofertas disponibles de vendedores filtradas por NM. No confundir esta
referencia con el precio final del Shopping Wizard. El índice actualizado registra cada
entrada (también ausencias) y el total; los cambios de miembros/cantidad no se presentan
como una variación de mercado comparable.

### Intercambio app y web

- Exportar **JSON · app / web** conserva impresión, acabado, idioma, condición NM, cantidad e
  histórico. La web importa ese JSON y permite selección/borrado, refresco MKM e histórico.
- Exportar desde la web e importar en Android crea una copia independiente, sin sobrescribir
  listas anteriores. No hay sincronización automática de Compras con Firebase.
- La web guarda listas en localStorage. Exportar JSON antes de limpiar datos del navegador.
- El refresco web descarga `AllPricesToday.json.gz` de MTGJSON; puede consumir datos y memoria.

### Crear la lista también en Cardmarket

1. Exportar texto MKM desde Android o abrir los bloques de la lista en la web.
2. Crear una Wants List vacía en Cardmarket; cada bloque tiene como máximo150 entradas.
3. Pegar el bloque en el importador de decklists. El texto usa cantidad, nombre y expansión
   cuando esta se conoce. Revisar la respuesta de Cardmarket, incluidos posibles duplicados.
4. **Revisar/fijar manualmente NM, expansión, versión/número, idioma y foil/etched.** El formato
   oficial de texto no conserva todos estos filtros. No garantizar coincidencia por mcmId
   con este flujo manual ni sustituirlo por el importador de venta BulkListing.

La extensión0.2.0 añade un asistente en páginas `/Magic/Wants*`: carga JSON, divide y copia
bloques. No modifica formularios Cardmarket ni crea listas automáticamente. El flujo contra
una cuenta real y el DOM actual de MKM queda pendiente de validación manual. No se ha
publicado la web ni instalado la extensión automáticamente.

Fuentes oficiales revisadas:
- https://help.cardmarket.com/en/wants-list (límite150 entradas)
- https://help.cardmarket.com/en/how-to-add-a-mtg-decklist-to-wants (formato de importación)
- https://help.cardmarket.com/en/add-cards-to-wants (filtros de condición, idioma y acabado)
