# Proveedores de datos

La aplicación separa las responsabilidades para que los precios puedan actualizarse sin
reescribir el catálogo:

- **`MtgJsonCatalogDataProvider`** obtiene los datos de cada impresión desde los ficheros de
  set de MTGJSON v5 y guarda sólo las impresiones consultadas en Room (`card_printings`).
- **`ScryfallImageDataProvider`** descubre los códigos de set disponibles y aporta las variantes
  de imagen por idioma, enlazadas mediante `scryfallId`. Envía `User-Agent` y `Accept` explícitos.
- **`MtgJsonPriceDataProvider`** descarga `AllPricesToday.json.gz`, conserva el snapshot durante
  toda la vida de los datos de la aplicación (hasta una actualización manual) y lo indexa
  completo, en streaming y por lotes, en Room (`price_snapshot`). Cada lectura posterior es una
  consulta local por UUID, acabado y proveedor, sin volver a recorrer el JSON. Expone Cardmarket,
  TCGplayer, Card Kingdom y Cardsphere como fuentes independientes y respeta la prioridad elegida
  en Ajustes para cada acabado.
- **`ScryfallPriceDataProvider`** consulta en lotes las impresiones exactas mediante su
  `scryfallId` y aporta precios EUR normal/foil. Se combina con MTGJSON respetando el mismo orden
  configurable, sin sobrescribir la caché estable de MTGJSON.
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

Cuando el nombre reconocido procede de un alias localizado de `AtomicCards`, la colección
guarda también el código de idioma. Antes de dar el escaneo por terminado se solicita a
Scryfall la imagen de esa misma impresión e idioma; el detalle restaura tanto la imagen como
la selección del idioma guardado. Si la impresión preseleccionada sólo existe en inglés, se
consultan todas las impresiones físicas de ese nombre e idioma y se cambia automáticamente a
una edición compatible antes de guardar y notificar el resultado.

## Prioridad de precios

La pantalla **Ajustes → Fuentes de precio** permite arrastrar las fuentes. Para cada impresión y
acabado se usa la primera que tenga un valor disponible. El orden predeterminado prioriza EUR:
Cardmarket, Scryfall, TCGplayer, Card Kingdom y Cardsphere. Cambiar el orden invalida la caché de
presentación y la siguiente consulta vuelve a elegir el proveedor en el índice local. Scryfall
sólo se consulta por red cuando su posición puede mejorar un precio local o ninguna fuente local
cubre esa impresión/acabado.
Los precios USD se muestran como fallback en la carta, pero no se mezclan en los totales EUR: el
total permanece incompleto hasta disponer de un precio EUR o de una futura conversión de divisa.

CardTrader es el siguiente candidato para precios de anuncios por idioma, estado y acabado, pero
requiere un token personal. Las APIs directas de Cardmarket y TCGplayer no son una base viable
para instalaciones nuevas porque actualmente restringen nuevas credenciales. PriceCharting
también dispone de API para cartas, aunque exige una suscripción de pago y sus precios se expresan
en USD.
