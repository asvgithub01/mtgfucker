# Colecciones nuevas, galería y Google Play

## Decisión propuesta, aún no desplegada
Separar versión APK, encoder, preprocesado y galería. Nuevas cartas: imágenes de
referencia → mismo Odin congelado → vectores y metadata → validación → paquete.
Normalmente no se necesita entrenar. Revisar también cartas añadidas/corregidas en
sets antiguos, calidad de imágenes provisional/final y caras alternativas; detectar
solo un nuevo código de set no basta. No asumimos una fecha para ninguna expansión.

Proceso futuro diario (CI/worker, no en cada teléfono): consultar sets y snapshot bulk
Scryfall, diff por printing UUID/cara/image_status/hash, descargar respetando límites,
validar imágenes, crear delta y publicar **tras pruebas**. `research/catalog/check_sets.py`
solo genera un informe manual; no descarga imágenes, no publica ni agenda nada.

Paquete: schemaVersion, catalogVersion, encoderSHA, preprocessingVersion, dimension,
dtype, referencias, hashes/tamaños, minAppVersion, fecha snapshot y procedencia.
Firma Ed25519 con clave pública anclada en APK; SHA solo detecta corrupción, no autoría.
Validar firma/esquema/compatibilidad/límites, descargar a staging, verificar, abrir
índice de prueba y cambiar de versión atómicamente. Conservar versión anterior para
rollback. Sin cambios de colección. Offline con última versión válida; Wi-Fi opcional,
progreso, reanudación, espacio mínimo, cuotas y pruebas de corte/paquete adulterado.
No implementar backend de inferencia: el reconocimiento sigue en el dispositivo.

## Play / hosting
APK, código y librerías nativas: actualización mediante Play; se puede ofrecer el
flujo In-App Updates de Play, NO instalar otra APK descargada por nuestra cuenta.
La galería son datos descargables independientes; diseñar el canal como datos, sin
ejecutables ni operadores nativos descargados. Modelos/versiones runtime requieren
revisión de compatibilidad y políticas antes de publicar, no una garantía automática.
[Política oficial](https://support.google.com/googleplay/android-developer/answer/16559646?hl=en).

No hace falta un servidor permanente: job de generación + almacenamiento HTTPS/CDN
estático. Pages podría alojar documentación/manifiesto pequeño, pero no es un worker
ni la opción recomendada para galerías de producción: límite sitio1GB, ancho de banda
suave100GB/mes y condiciones de uso. Solo vectores100626×256×4≈103MB por descarga,
antes de metadata/índice: 1000 descargas ya≈103GB. Preferir object storage/CDN y
presupuestar tráfico; no elegir/provisionar proveedor sin decisión posterior.
[Límites oficiales](https://docs.github.com/en/pages/getting-started-with-github-pages/github-pages-limits).

## Para retomar
Definir derechos de distribución de imágenes/metadata/pesos; no publicar el corpus.
Implementar build incremental y firma; probar versiones cruzadas/corrupción/rollback;
después Android update client y scheduler. Ninguna credencial en Git.
