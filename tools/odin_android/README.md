# Odin Lab independiente

Paquete `io.asv.odinlab`, nombre `Odin Lab · codex`; rama codex/edscan-fastweb-diego.
No sustituye io.asv.diegolab ni io.asv.mtgocr.ocrreader. No colección, login ni cloud.
Copia de fuentes del lab v27 desde worktree dinov2-benchmark, conservando atribuciones.
No distribuir los modelos/recursos sin revisar sus condiciones; esto no convierte
los detectores externos en MIT. Futura integración de producción: rama de refactor.

## Pantallas
- CameraX: 12 detectores x Milo/Odin = 24 combinaciones. Guarda A/B sobre mismo frame.
- Comparar foto: selector Android, EXIF mediante ImageDecoder, lado máximo2048;
  guarda input/crops/resultados privados photo-ab-TIMESTAMP. Una pasada sin warmup:
  no presentar esos tiempos como benchmark estable ni similitud como precisión.
- Diagnóstico paridad Odin: raw0 estático, no fotos de colección, escribe input,
  tensor, embedding, ranking y Esquinitas en odin-parity-TIMESTAMP.

Diego IDENTIFICADOR sustituido por Odin, no cambiar nombre de detector Diego Pose.
Se conserva Milo como comparador. Odin100626 referencias, Milo123376: galerías
NO idénticas. No atribuir diferencias de cobertura exclusivamente al encoder.
No se ejecutan menús históricos de identificación Diego desde el launcher nuevo.

## Esquinitas EXP
Checkpoint spatial-01/generalization-simcc/final.pt, no entrenado con fotos reales.
Input160x224 RGB/255, Pillow12.3 BILINEAR; presencia experimental>=0.5, no afecta
umbrales originales. Es solo inspección, no promoción. 2combinaciones Milo/Odin.
Export: export_esquinitas.py cambia AdaptiveAvgPool14x10->8 por slices/mean exactos
para exportador legacy ONNX; comprueba PyTorch y ONNX. Artefactos fuera de Git.
La venv actual lleva Pillow13: puede cambiar RGB1nivel; training sintético no
registró Pillow, no afirmar equivalencia numérica a todas las métricas históricas.

## Compilar (Java17/SDK34)
Desde raíz del worktree:
`gradlew.bat -p tools/odin_android :lab:testDebugUnitTest :lab:assembleDebug`
Assets externos fijados en lab/build.gradle: baseline v2 y detectores del checkout
original dinov2-benchmark; ownidentity y Esquinitas en build/odin-lab-assets-v1.
No regenerar ni sobrescribir assets originales. APK ~511MB, debug/ARM64.
`adb -s QV770HG2JD install -r tools/odin_android/lab/build/outputs/apk/debug/lab-debug.apk`
Siempre verificar paquete/compilación terminada; NUNCA desinstalar producción.

## Verificación
- Extra MainActivity odinParity=true ejecuta comparación exportable.
- verify_sony_parity.py recoge SOLO paqueteodinlab. Requiere Pillow12.3 separado:
  `python -m pip install --target build/odin-parity-pillow123 Pillow==12.3.0 --no-deps`
  No cambia entorno global. PC/Android tensor0error; Odinembedding<1e-4 y topIDs
  exactos; Esquinitascorners<1e-6. Primera muestra pasó2026-10-29; no es test físico.
- Extra labSmoke=true recorre12detectores sobre raw0 y ambasidentificaciones si
  hay propuesta. Esto comprueba integración, no éxito físico de24combinaciones.

## Muestras de Triple Odin
Extracción sololectura fuera delrepo:
C:/asv/datasets/mtg-identity-v1/esquinitas-real-intake-20261029-172220
343muestrascompletas,PNGverificados,fechas26–27octubre. ArchivoTAR preservado.
Cupo512MiB: usados536409981bytes, libres460931bytes. El guardado se rechaza si
no cabe siguiente muestra; no borrado automático. No se han eliminado evidencias
ni aumentado cupo ni modificado Triple. No tratar este lote como nuevos escaneos
29oct. Revisiónmanual/etiquetas pendientes; trainingEligible=false.

## Validado en Sony 2026-10-29
- Build + tests JVM pasan. Instalación independiente, sin reemplazar otros paquetes.
- build/odin-lab-parity-20261029-173055/result.json: una imagen, tensorOdin y
  Esquinitas idénticos a PC12.3; Odinembeddingerror2.42e-7,topIDsidénticos;
  esquinasEsquinitaserror0. No prueba de calidad física general.
- build/odin-lab-smoke-01/result.json:12detectores completan sin excepción;
  9proponen recorte y completan Milo/Odin. Los otros3 no proponen carta en raw0.
  No son24aciertos ni benchmark: calentamiento/modelos/escena única.
- Pendiente prueba manualCameraX/fotosmulti y validación ampliada con nuevas muestras.
