# Paquetes AR descargables

## Arquitectura y límites

- Una DB ARCore activa, hasta 1.000 referencias. No unir binarios ni rotar DBs para fingir búsqueda global.
- El script genera `.imgdb` con `arcoreimg` oficial y un manifiesto de identidad/imagen/acabado. El ancho es 63 mm. No incluye precios: la app consulta precios **locales**, por impresión/acabado.
- Catálogo estático `web/public/ar/catalog.json`, archivos versionados y SHA-256. El índice se reemplaza al terminar cada paquete, nunca antes. Un fallo no anuncia un paquete parcial.
- Se conserva caché de imágenes en `build/ar-source-cache`; repetir es reanudable. La versión depende de referencias, cobertura y binario del compilador.
- `assets/ar-editions.json` es la cobertura revisada, compartida por Node y Android. **`--all` significa todos los paquetes configurados, no todas las ediciones de Magic.** Cualquier código válido se puede generar; sin configuración explícita incluye **solo la edición principal**. Para incluir anexos hay que revisar la configuración. No es seguro adivinarlas por nombres, fecha o prefijo; SPG como anexo exige números explícitos (SPG como paquete independiente incluye todo su set). Si se superan 1.000 referencias, falla sin truncar: hay que definir paquetes alternativos explícitos.
- Cobertura inicial: FRA + FRC + SPG159–168 (574 referencias), FRC separado (103). Imágenes de referencia en inglés, sin fichas. No se promete discriminación automática foil/nofoil ni idioma por una foto parecida.

## Generar o actualizar

Requiere Node20+ y `arcoreimg` del [SDK oficial de Google](https://developers.google.com/ar/develop/augmented-images/arcoreimg). Descargar manualmente la herramienta para el sistema operativo, fuera del repositorio, y conservar/verificar su versión para reproducibilidad. No se ejecutan comandos del catálogo remoto.

```powershell
npm run discover:ar # lista ediciones actuales y verifica DBs/hash en web/public/ar/editions.json
# Generar automáticamente todas las ausentes (puede tardar horas y descargar muchos GB):
npm run discover:ar -- --generate-missing
# Generar cualquier código, no solo los configurados:
npm run build:ar -- --sets PAPC
npm run test:ar
npm run build:ar -- --sets FRA --arcoreimg C:/ruta/arcoreimg.exe
npm run build:ar -- --all --arcoreimg C:/ruta/arcoreimg.exe
# --force recompila aun con el mismo contenido; --output cambia el directorio de publicación local.
npm run build:web
```

Los `.imgdb` y metadatos generados en `web/public/ar` se copian a `web/dist/ar`. La página `/ar/` permite buscar y descargarlos. No subir `build/ar-source-cache` ni los sidecars `*-imglist.txt` del compilador (contienen rutas locales). El script compila dentro de build precisamente para evitar su publicación.

## Publicación

Usar el alojamiento existente, no crear otro sitio. El repositorio contiene `firebase.json` con Hosting `web/dist`, pero **la URL pública y el acceso de publicación están pendientes de confirmar**. No se ha desplegado en producción. Con el proyecto correcto/autenticado, el flujo existente de Hosting publica `web/dist`; no publicar reglas de Firestore por este cambio.

Configurar la APK con el directorio HTTPS exacto del catálogo:

```powershell
bash gradlew :app:assembleDebug '-ParPackagesUrl=https://TU-WEB/ar'
```

Sin esta propiedad la app no consulta un dominio inventado: avisa del fallo del catálogo de paquetes, permite paquetes locales y generar ediciones obtenidas de Scryfall. Verificar que `catalog.json` devuelve JSON, no el fallback HTML de una SPA. Publicar archivos antes del índice y conservar archivos versionados antiguos mientras clientes puedan tener el catálogo anterior.

**Premium actual es un flag local**, no una suscripción validada por servidor. Los archivos de Hosting son públicos. Exclusividad real requiere backend autorizado/URLs firmadas y no forma parte del alojamiento estático.

## Android

Ajustes Premium → Contenido de ediciones. Buscador compartido `EditionSearch`, filas `EditionSearchAdapter`, selección individual/todas las visibles, tamaño, estado/versiones, descarga secuencial y cancelación. Los paquetes completos persisten en `filesDir/ar-packages`, no en caché descartable. SHA-256, tamaño máximo16MiB por archivo, nombres seguros, manifiesto/versiones y conteo se validan; la selección de versión solo cambia tras validar ambos archivos. Cada archivo local usa su hash en el nombre para no sobrescribir una versión activa al fallar otra.

Salir/destruir la pantalla cancela el lote; lo ya instalado permanece y se puede reintentar. No hay servicio de descarga permanente ni consumo en segundo plano programado. Perder Premium impide finalizar nuevas instalaciones. No se modifica la biblioteca.

Solo `EdScannerArActivity` consume los paquetes. Una edición preseleccionada descargada activa su paquete; si falta y es Premium se ofrece abrir descargas. Varias ediciones no se reducen a la primera: se pide una base combinada disponible. El selector explícito también abre el gestor en modo una base. OCR y EdScan normales no incorporan AR.

## Validación

`npm run test:ar`, build web, pruebas JVM `ArPackageIntegrityTest`, y `ArPackageSmokeTest` en Sony. Para este último, copiar catálogo y archivos generados a `build/ar-test-assets/ar/` antes de compilar el APK de tests. Usa assets solo del runner, valida paquete real, rechazo de corrupción conservando versión anterior, carga AR sin inferencia y biblioteca sin cambios; requiere Premium previamente habilitado. No activa Premium silenciosamente ni borra datos. La comprobación física de cartas sigue siendo manual.

## Generación bajo demanda en Ajustes

El listado combina todas las ediciones de papel publicadas en Scryfall (excluye fichas, minijuegos y memorabilia), los paquetes publicados y los instalados. La consulta tiene caché local. Una edición ausente no implica necesariamente novedad: también puede no haberse generado o faltar un archivo.

Si no existe paquete publicado, «Descargar o generar» solicita confirmación y prepara una DB **local al móvil**, usando ARCore instalado, las imágenes EN y ancho físico de63mm. La tarea corre en un worker, muestra progreso, admite cancelación y cierra la sesión AR sin abrir la cámara. Permiso de cámara requerido por ARCore. No publica nada en la web. Un paquete instalado se reutiliza; no vuelve a entrenarse al usarlo. Fallos/cancelación conservan la versión instalada; máximo1000referencias sin recortar. La cancelación durante una petición puede esperar al timeout de red45s.

Los scripts de PC no se ejecutan desde el sitio estático. `discover:ar --generate-missing` llama al generador secuencialmente, continúa tras errores y devuelve código1 si algún paquete falla; no ejecutar dos generadores sobre el mismo directorio simultáneamente. `--all` sigue significando cobertura configurada, no todo el catálogo. El descubridor comprueba archivos y SHA, no garantiza detectar cartas nuevas añadidas a un set ya generado: para actualizarlo repetir `build:ar -- --sets CODIGO`.
