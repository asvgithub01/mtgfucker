# Odin estable: conservar y reproducir

Nombre técnico Own02, encoder MobileNetV3 Small (576 → 256, normalización L2).
RGB, bilinear compatible con Pillow, entrada 320×448, normalización ImageNet, FP32.
SHA encoder: `d7dc898744ce9ce530e9b6344e043dd9315b426b513bb6f5e66f6367f6ca9fd6`.
Galería: 100626 anversos; 1334 reversos conservados aparte. No son 100626 nombres únicos.
Similitud coseno: no es una probabilidad. Mantener score, margen entre distintas
identidades y estabilidad; no bajar umbrales para hacer desaparecer errores.

Código: OwnIdentityEngine.kt / OwnIdentityPreprocess.java en collectorvision-native;
OwnTripleScanActivity.kt en app. El laboratorio Odin no añade a colección.
**FastWeb + Diego E2E sí puede guardar**; no confundir las dos pantallas.

Fuentes de entrenamiento: research/odin/archive (copia histórica, leer README antes
de ejecutar). Config 1000 pasos, 2048 artes de entrenamiento; la cobertura de galería
no equivale a haber entrenado con todas esas cartas.
Encoder nuevo exige recalcular toda la galería; no mezclar espacios de embeddings.

Resultados locales: sony-failures-02-20261027-095636: Odin y Milo 40/40, Diego13/40
en galería compartida18419 y esas muestras; Odin6,70ms/Milo24,95ms son PC, no Sony.
Comparativa detector-odin-pc-01: FastWeb+Odin138/149 y Cornelius+Odin137/149 top1
por arte; corpus pequeño, correlacionado y sin esquinas GT humanas. No certifica
superioridad universal. Ambos detectores requieren warp externo.

Pruebas antes de otra instalación: aislamiento47 originales, unitarias, fixtures
Android19, resize byteexact y rival de distinta identidad. Backup consistente +
hashes, APK inmutable y install-r; nunca uninstall/clear/restore sin autorización.
Último APK validado: odin-speed-01/odin-optimized.apk, SHA
`a636a31ef65b7541288e4e36a5ca5909af910cf68398a76079df9941442bae94`.
45227 archivos privados idénticos tras esa instalación.

Siguiente paso: evaluar nuevas muestras físicas sin entrenar con ellas automáticamente.
