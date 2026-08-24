# Proveedores de datos

La aplicación separa las responsabilidades para que los precios puedan actualizarse sin
reescribir el catálogo:

- **`MtgJsonCatalogDataProvider`** obtiene los datos de cada impresión desde los ficheros de
  set de MTGJSON v5 y guarda sólo las impresiones consultadas en Room (`card_printings`).
- **`ScryfallImageDataProvider`** descubre los códigos de set disponibles y aporta únicamente
  la URL de imagen, enlazada mediante `scryfallId`. Envía `User-Agent` y `Accept` explícitos.
- **`MtgJsonPriceDataProvider`** descarga `AllPricesToday.json.gz`, conserva el snapshot durante
  24 horas y extrae en streaming sólo los UUID solicitados. Prefiere precios retail de
  Cardmarket (EUR) y usa otro proveedor como fallback.
- **`MtgJsonCardNameResolver`** usa los alias de `AtomicCards` para traducir nombres impresos
  en otros idiomas al nombre canónico y tolera pequeños errores del OCR. El fichero comprimido
  sólo se descarga cuando falla una búsqueda normal y los alias resueltos quedan en Room.
- **`CardRepository`** combina catálogo, imágenes y precios fuera del hilo principal.
- **Room** mantiene catálogo, precios, sincronización por carta/set y la edición/acabado elegido
  para cada elemento de la colección.

Al tocar una carta del listado, la pantalla de detalle muestra una fila por edición y acabado
(`foil`, `nonfoil` o `etched`) y permite marcar la copia que posee el usuario. El botón
**Actualizar precio** fuerza una descarga nueva del snapshot de precios.

El nombre azul de una edición abre el contenido completo de ese set. La pantalla carga el set
MTGJSON, enlaza las imágenes Scryfall y los precios, y permite añadir en lote las cartas marcadas
a la colección. Las imágenes de la pantalla de edición abren un visor con zoom y selector de
idioma cuando Scryfall dispone del escaneo localizado.

El OCR continúa usando Google Mobile Vision (`play-services-vision`).
