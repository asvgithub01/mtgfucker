# Validación física y captura voluntaria

Usar «Triple Odin · laboratorio», no las rutas E2E que guardan en colección.
Guardar muestras es opt-in OFF: frame completo, crop, esquinas, candidatos, tiempos,
hash/versiones, sesión y etiquetas. Cuota512MiB, reserva256MiB, una escritura en vuelo,
intervalo mínimo1500ms, no borrado automático; exportación ZIP revisable.
Cada muestra nace unreviewed/trainingEligible=false. Ninguna predicción es etiqueta GT.

Para el usuario: guardar fallos Y aciertos emparejados de la misma carta, título arriba,
con/sin funda, iluminación difusa/directa, álbum, trípode/a pulso y varios fondos.
Anotar carta exacta/edición/idioma y condición; para foil/firma revisar físicamente.
También transiciones, dorsos y mesa vacía. Evitar caras/documentos privados en el fondo.
No hace falta modificar el scanner ahora; conservar fallos originales antes de mejorar
el encuadre. Tiempos con guardado no son equivalentes a sin guardado.

Reservar sesiones/cartas para evaluación ANTES de entrenar. Revisar consentimiento y
privacidad antes de compartir; exportación local, no subir fotos a Git.
Informe por carta y condición (no inflar n con100frames de una sola carta), precisión de
aceptaciones, rechazo, tiempo a primer candidato correcto/confirmación, p50/p95,
errores de esquinas y recorte, consumo sostenido/temperatura.

Logs actuales separan detector/warp/resize/encoder/búsqueda. CPU Sony limitada en
pruebas sostenidas; registrar frecuencias y alternar A/B. No culpar al guardado por una
ralentización que ya ocurrió antes de activarlo. No cambiar gestión térmica del móvil.
