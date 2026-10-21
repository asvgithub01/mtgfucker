# Fixture de compatibilidad binaria

`master-2d8a4ad.ser` se generó con ObjectOutputStream y las clases Java
compiladas de master `2d8a4adb17fba50a17f26e260775e165414f4d72`, antes
de compilar los cambios de P1. Contiene una Biblio y una carta sintéticas,
sin información de usuario, credenciales ni fotos reales.

- Archivo lógico: synthetic-baseline.Json.
- Identidad estable: fixture-row; impresión: synthetic-printing.
- Tres copias foil, portugués, condición good.
- Grupo, mazo, sideboard y evidencia/foto ficticias.
- SHA-256: `131e07c9e3199f952d6471aa4a8c344df2be8a845c9c267c804d4d15814470ae`.

No regenerar el fixture al cambiar modelos: dejarlo fijo permite detectar
incompatibilidad con esta versión. No representa todos los formatos históricos.
La prueba exige lectura, identidad, evidencias y round-trip sin pérdida; los
modelos de almacenamiento y sus serialVersionUID no se modifican.
