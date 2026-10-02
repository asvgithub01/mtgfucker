# Actualizador local de la BuyList de Itaca

Programa de línea de comandos que descarga las expansiones seleccionadas o el
catálogo completo y genera un JSON para la valoración de colecciones en la web.
No modifica ni requiere cambios en la aplicación Android.

No necesita iniciar sesión en Itaca ni añade cartas a una cesta. Solo lee las
páginas públicas de la BuyList, con una pausa predeterminada de cinco segundos
entre peticiones.

## Uso rápido

Desde la raíz del proyecto:

```powershell
npm run scrape:itaca -- --set-codes STX,SOS
```

El resultado queda en `build/itaca-buylist.json`.

Para construir el fichero completo de todas las expansiones:

```powershell
npm run scrape:itaca -- --all --output build/itaca-buylist-full.json
```

El programa guarda un punto de control cada diez expansiones. Si el proceso se
interrumpe, se puede continuar sin repetir lo ya guardado:

```powershell
npm run scrape:itaca -- --all --resume --output build/itaca-buylist-full.json
```

Cuando el fichero esté completo, se comprime y se copia a la web con:

```powershell
npm run publish:itaca
```

El publicador se niega a copiar catálogos parciales o con expansiones fallidas.
Para las siguientes actualizaciones, el flujo completo se puede ejecutar con un
solo comando; reanuda un catálogo parcial y publica únicamente al terminar bien.
Si el catálogo anterior ya estaba completo, vuelve a descargar los precios:

```powershell
npm run refresh:itaca
```

También acepta un JSON exportado de una o varias Biblios. El programa recorre el
documento y toma todos los campos `setCode`:

```powershell
npm run scrape:itaca -- --collection-json C:\ruta\bibliotecas.json
```

Para comprobar únicamente qué códigos reconoce Itaca:

```powershell
npm run scrape:itaca -- --set-codes STX,SOS --dry-run
```

## Datos obtenidos

Por carta conserva el identificador interno de Itaca, el Product ID de
Cardmarket que aparece en la imagen, nombres inglés/localizado, expansión,
imagen, idiomas/estados/acabados disponibles y la oferta visible por defecto
(inglés, NM y no foil normalmente) en efectivo e Itacoins.

Los precios para otras combinaciones de idioma, estado y acabado no se descargan
masivamente. Más adelante la aplicación podrá solicitar solamente las variantes
que existan realmente en las Biblios.

## Pruebas

```powershell
npm run test:itaca
```
