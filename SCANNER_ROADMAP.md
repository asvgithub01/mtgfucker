# Próximos modos de escaneo

El siguiente objetivo de mejora es separar el escáner actual en dos flujos especializados. Ambos
reutilizarán OCR, reconocimiento visual, caché local y feedback, pero no deben compartir las mismas
reglas de sesión ni de guardado.

## 1. Apertura de sobres

Objetivo: capturar rápidamente un sobre completo conservando el orden en el que aparecen las cartas
y mostrar un resumen antes de decidir dónde guardarlo.

Puntos clave:

- Elegir antes de empezar el producto/colección, idioma y número de sobres; mantener ese contexto
  bloqueado durante la apertura.
- No interrumpir entre cartas: confirmación sonora/háptica, miniatura, deshacer y detección clara de
  duplicados.
- Conservar el orden y los slots del sobre (comunes, infrecuentes, rara/mítica, foil, tierras,
  tokens) para detectar faltas o lecturas dobles.
- Diferenciar impresión, idioma y acabado. El brillo foil no puede depender solo del OCR y debe
  poder corregirse durante la revisión.
- Mostrar valor por carta, valor total del sobre y cartas destacadas sin bloquear la siguiente
  captura.
- Revisar el sobre completo antes de añadirlo a una colección; el guardado final debe ser atómico.
- Permitir pausar/reanudar una apertura sin perder el estado de la sesión.

## 2. Escaneo de colecciones

Objetivo: inventariar muchas cartas, potencialmente mezcladas, agregando cantidades y corrigiendo
ediciones desde una sesión de trabajo.

Puntos clave:

- No asumir una colección única, salvo que el usuario la bloquee; aceptar lotes con ediciones
  mezcladas.
- Añadir inmediatamente una impresión no foil provisional y revisar después edición, acabado,
  idioma y estado de conservación desde la sesión.
- Agrupar duplicados exactos por impresión/acabado y aumentar cantidades sin crear filas ambiguas.
- Mantener por carta el original capturado, candidato OCR, confianza y correcciones manuales para
  poder auditar errores.
- Incluir acciones masivas: aplicar colección/idioma/estado, seleccionar varias, eliminar falsos
  positivos y confirmar pendientes.
- Guardar por lotes con deshacer, recuperación ante cierre y resumen de nuevas cartas, duplicados y
  valor total.
- Priorizar caché local y precarga de catálogos/precios para sostener una sesión larga con mala red.

## Base compartida

- Máquina de estados explícita: detectando, candidata, confirmada, duplicada, pendiente y error.
- Sesiones persistentes separadas de la colección definitiva hasta confirmar el lote.
- Feedback inequívoco y no modal; ningún selector debe abrirse automáticamente durante el ritmo de
  captura.
- Métricas de tiempo por carta, tasa de reconocimiento, correcciones y reintentos para poder ajustar
  OCR y umbrales con datos reales.
