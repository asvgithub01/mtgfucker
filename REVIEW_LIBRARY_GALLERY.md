# Revisión de precios, biblioteca y galería

Rama: `codex/review-prices-library-gallery`, desde `develop` local (ac7ae07).

- Reanálisis explícito en postprocesado; filtros mínimos en EUR y porcentaje, combinables con pendientes/resueltos y origen.
- Compara ediciones dentro del mismo idioma e idiomas dentro de la misma impresión. Normal y foil separados; etched sin sustituciones. Porcentaje respecto al mínimo; mínimo cero no produce infinito. Muestra cobertura de cotizaciones disponibles. No cambia la valoración guardada.
- Fuente de comparación: cotizaciones EUR disponibles en Scryfall, con caché local. No representa un catálogo completo de precios de todos los idiomas. Ausencia de cotización no significa igualdad.
- Detalle flotante: no escribe al abrir sin editar; home no reconstruye la biblioteca al cerrar sin cambios. Si se edita, sí refresca.
- Checkboxes por ID y barra de selección: todos/ninguno, borrado con confirmación, añadir a mazo existente, quitar agrupación/mazo.
- Galería: X, menú detalle/fondo para cartas guardadas, símbolo de edición editable, precio debajo del idioma. Cambio de edición pide idioma disponible si el previo no existe; conserva cantidades y grupos mediante la ruta de revisión existente.

## Validación

Pruebas JVM y dispositivo: política de rangos, precio exacto Stingerquill FRA434 (red + caché), navegación/recreación de galería, controles y detalle flotante sin modificar el archivo de colección. Las pruebas no borran cartas reales.
Pendiente de prueba manual: selección/borrado masivo y cambio de edición con distintos idiomas, UX completa en Sony. Capturas iniciales parcialmente tapadas por panel de volumen del sistema.
