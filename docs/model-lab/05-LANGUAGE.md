# Idioma físico: modelo o OCR selectivo

No deducir idioma de la edición ni del nombre canónico de Scryfall. Comparar primero
OCR de título+reglas una vez por carta estable con clasificador de regiones de texto.
Un clasificador visual puede aprender fuente/layout/set en lugar de idioma; separar
cartas/sets entre train/test y cubrir idiomas con mismo título (ES/PT), textos mínimos,
cartas sin texto, japonesas, símbolos y reflejos. Clase desconocido obligatoria.

Modelo independiente facilita entrenamiento y ejecución solo cuando haga falta.
Alternativa: cabezal compartido con edición; elegir por datos y medidas, no por nombre
Odin Next. Mantener corrección manual y no sobreescribir idioma conocido.
Comparar coste total, tasa de idioma incorrecto y cobertura; no basta accuracy entre
casos fáciles aceptados. OCR sigue como respaldo hasta demostrar mejora.

Estado: propuesta; no hay modelo nuevo ni cambios en OCR actual.
