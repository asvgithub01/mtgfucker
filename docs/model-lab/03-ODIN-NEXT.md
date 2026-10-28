# Odin Next: identidad robusta, no reemplazo automático

Objetivo: mejorar casos reales difíciles sin perder rendimiento móvil. Mantener Odin
actual como baseline congelada. Más pasos/datos no garantizan mejora: expanded-run-01
(4000 pasos,37,35min RTX5090Laptop) bajó desarrollo46/53 frente a Odin51/53; rechazado.
23/23 heldout representan solo6identidades; no basta para promover.

Fuentes/configs: research/odin; datos y checkpoints históricos en mtg-identity-v1.
No entrenar con1475fotos históricas sin revisar,23heldout ni nuevas capturas de prueba
reservadas. Dividir por componentes oracle/ilustración/duplicados y sesión física,
preservar grupos de validación anteriores. Evitar versiones de un mismo arte cruzadas.

Primer experimento futuro: fallos etiquetados de desenfoque, reflejos, fundas y álbum;
mezcla sintético+real revisado, hard negatives, un factor por corrida, presupuesto
congelado. Versionar dataset/config/software/semilla y checkpoints; no elegir mirando
repetidamente el test final. Comparar misma galería y mismas fotos contra Odin/Milo/Diego.
Medir top1 por identidad/arte, rechazo correcto, falsas aceptaciones, p50/p95 y coste
sostenido Sony, no solo inferencia PC. Umbrales se calibran en validación independiente.

Edición e idioma no son requisitos de este encoder: consultar04/05. Cambiar encoder
implica regenerar galería y verificar paridad Android antes de promoción explícita.
