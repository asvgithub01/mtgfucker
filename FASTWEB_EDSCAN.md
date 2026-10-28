# EdScanner FastWeb + Diego: prueba E2E

Rama `codex/edscan-fastweb-diego`, base master `2d8a4adb17fba50a17f26e260775e165414f4d72`.

## Entrada y aislamiento
En Biblio, menú de acciones de colección → **EdScanner FastWeb + Diego (experimental)**.
El escáner original y Triple no cambian. La nueva actividad conserva OCR/revisión de idioma,
precios por impresión/acabado, sonido, recientes y guardado mediante los repositorios existentes.
Usa preferencias `fastweb_scanner` / `fastweb_sessions`, grupos automáticos `FastWeb N`;
si se abre desde un grupo filtrado, guarda en ese grupo explícito.

**Esta es una integración real, no una colección simulada**: al utilizarla escribe en la Biblio
activa y puede entrar en la sincronización existente. Instalada en el Sony mediante actualizaci�n sin borrado, tras backup completo verificado. Probar en una Biblio de pruebas. La configuración local sin google-services.json no valida Firebase.

## Motor y datos
- FastWeb 1.39, RGB ImageNet 384², Pillow bilinear equivalente, ORT BASIC_OPT.
- Sharpness ≥0,02, geometría convexa dentro de frame y orientación vertical, título arriba.
- Warp OpenCV 640×896, Diego FP32 320×448, una pasada upright, vector 256d normalizado.
- 123.376 referencias congeladas del catálogo del lab; no actualización automática.
- TopK=8 **después** de aplicar el scope de IDs del bloqueo de ediciones. No filtrar solo top5.
- Manifest SHA256 en `tools/fastweb/manifest.json`. Los assets privados se extraen y validan
  fuera de los directorios de Cornelius. No se carga Cornelius/Milo como fallback.
- Umbral inicial conservador de aceptación 0,70 + margen 0,08 + tres lecturas en modo filtrado.
  No está calibrado como garantía de precisión: varias capturas correctas dan menos de 0,70.
  Ajustes del modo rápido independientes. No confundir top1 con aceptación/guardado.
- Fotos llevan `engine=FastWeb1.39+DiegoFP32`; el reanálisis explícito usa ese mismo motor.
  Las fotos antiguas mantienen su ruta anterior. OCR no confirma automáticamente el idioma.

## Reproducir
`tools/fastweb/prepare_local.py` usa los assets existentes del worktree dinov2-benchmark;
requiere Python con onnx y numpy. No descarga modelos ni toca datos de usuario.
Los pesos generados viven en `build/fastweb-e2e-assets`, fuera de Git.
Compilar con Java17/SDK34. El wrapper shell de este checkout tiene CRLF; usar gradlew.bat en
Windows si `bash gradlew` falla por CRLF, sin modificar el wrapper.

Pruebas instrumentadas: `FastWebDiegoDeviceTest` usa un APK de biblioteca separado
`io.asv.collectorvision.test`, sin host ni acceso a Biblio. Fixtures locales en
`build/fastweb-test-assets`, nunca dentro de la APK de aplicación. Compara siete capturas
contra el replay Sony v27 (esquinas, top5 y scores) y bloqueo de catálogo.

## Licencias
No se ha relicenciado nada. Se conservan los avisos AGPL existentes del módulo y las partes
adaptadas del EdScanner; el cambio de lenguaje no elimina los derechos de origen.
FastWeb: revisión histórica HF 66ffd4976ec57bda0f6ea2d83e15ca6a3add7dd9 declaraba MIT;
revisión 2ef37ff78ee80ad4c3558e4a15341e447ff8a1be declara AGPL-3.0. El archivo ONNX no
incluye una declaración MIT. Resolver la procedencia/licencia aplicable antes de distribuir;
esta rama NO acredita una solución íntegramente MIT. Diego declara MIT en su repositorio.
El redimensionado conserva atribución/licencia Pillow en collectorvision-native/PILLOW-LICENSE.txt.

## Pendiente de validación E2E
Cámara real + aceptar/guardar + precios + idioma/revisión + recreación + copia repetida +
bloqueo de edición, en Biblio de pruebas. No extrapolar a este flujo las latencias del lab.

## Resultado de esta validación
- APK debug compilada; 789 tests de app y 61 de biblioteca sin fallos.
- Sony QV770HG2JD: FastWebDiegoDeviceTest OK (7 capturas, top5/esquinas/scores y scope); instalado exclusivamente io.asv.collectorvision.test.
- NativeCollectorVisionActivity, NativeCollectorVisionEngine y CorneliusScanActivity idénticos a master.
- No instalación de app principal ni guardados en colección durante las pruebas.


## Instalaci�n Sony (25/10/2026)
Backup externo: C:/Users/nubor/.codex/backups/mtgfucker/sony-before-fastweb-20261025-174403/.
Restauraci�n v�lida: private-verified.tar + previous-0.apk (NO usar private-data.tar ni before.sha256 iniciales incompletos).
46.203 archivos verificados en el tar; quick_check SQLite OK, cat�logo versi�n8. Misma firma APK,
misma configuraci�n Firebase. Instalaci�n -r Success, APK instalada validada SHA256.
Aplicaci�n dejada detenida tras verificaci�n: el usuario puede abrirla y seleccionar una Biblio de pruebas.

## Copia del Triple: FastWeb Triple
Entrada independiente en acciones de Biblio: Triple FastWeb + Diego (experimental).
FastWebTripleScanActivity conserva AR/OCR, decisiones por identidad/edición, sesiones,
precios, idioma y revisiones; FastWebTripleCollectionSession crea grupos FastWeb Triple.
Preferencias fastweb_triple_scanner y trazas files/fastweb-triple-trace separadas.
Originales TripleScanActivity, TripleCollectionSession y políticas sin cambios.

El carril visual usa FastWeb1.39 + Diego FP32 (solo upright / carta completa). No carga
vectores Milo, no hace fallback Cornelius y no alterna ART/UPPER: no tenemos un catálogo
Diego coherente para esos recortes. FastRegionIndex se usa SOLO como metadatos de identidad;
UUIDs ausentes se consideran identidades independientes printing:UUID, nunca se fusionan.
Detector-only para OCR fresco y callback de contorno antes del encoder. La confirmación
conserva score .70 / margen .08 / dos frames independientes; identidad y edición se evalúan
por separado. No se rebajan umbrales de guardado por las bandas shadow.

Diagnóstico visible (debug): score, margen, motivo, votos, detect/identify ms.
Shadow registra alternativas .55/.30/5, .60/.20/3, .70/.08/2 y .85/.25/1 SIN autorizar
escrituras. Para calibrar: cartas normales/full art, cambios entre cartas parecidas,
escenas vacías y fondos; etiquetar aciertos/errores y latencia hasta candidato/guardado.
Más pasadas de la misma imagen no mejoran un embedding determinista: deben ser capturas
independientes y mantenerse separadas las pruebas de identidad y de impresión.

Evidencias iniciales extraídas: 16 frames únicos, 7 con hits; cinco superan .70 y uno cae
al .675 con margen .202. Esa caída reinicia la racha aunque haya buen margen. Los siete
frames con hits tardan detect153-217ms / identify169-219ms. Muestra pequeña, no una medida
de precisión ni latencia completa. Diagnóstico local: build/triple-fastweb-01/diagnosis.json.

Fuentes consultadas:
https://huggingface.co/dhvazquez/mtg_card_identification_embeddings
https://github.com/diegovazquez/mtg_train_card_identification_embeddings
https://raw.githubusercontent.com/diegovazquez/mtg_train_card_identification_embeddings/master/scripts/embed_lib.py
La ficha especifica 448x320 y embeddings L2 de 256D; Recall@1 94.1% es de su validación,
no garantía en nuestro Sony/catálogo. El conf_threshold=.3 del wrapper es del detector de
pose, NO un umbral de similitud del identificador. No documenta un umbral universal.

Triple FastWeb instalado en Sony mediante install-r (25/10/2026), tras backup nuevo sony-before-fastweb-triple-20261025-182309. 46.368 archivos respaldados; antes de abrir, 44.513 archivos de datos/configuración y 2.184 imágenes idénticos. Tests unitarios 850 OK; replay Sony 7 capturas con callback temprano OK. Cámara/guardado del Triple nuevo pendiente de prueba manual.

Preparación sin Sony: check_triple_isolation.py verifica los 47 archivos Triple originales contra la base. Manifest de la copia conserva portrait y Theme.Mtg. APK local build/triple-fastweb-offline-01/mtgfucker-triple-fastweb-debug.apk (no instalada). Build y unit tests repetidos correctamente sin ADB.

Contorno copia FastWeb Triple: activado por defecto (respeta apagado manual), amarillo al detectar y verde al confirmar. OCR detector-only también actualiza/limpia geometría; no espera identificación. Proyección/frescura originales conservadas. Build y tests OK; APK build/triple-fastweb-contour-01 pendiente de instalación y comprobación visual Sony. Sin acceso a dispositivo ni datos.

Contorno instalado en Sony QV770HG2JD con install-r. Backup sony-before-fastweb-contour-20261025-200238 verificado (46.370 archivos). Después: 44.515 archivos de datos/configuración y 2.184 imágenes idénticos; ningún archivo ausente/nuevo. Preferencia de contorno sin valor previo: nuevo default ON. Pendiente usuario abrir Biblio de pruebas para observar alineación quieto y a pulso; no se ha validado visualmente todavía.


### Diagnóstico del recorte real (solo debug, copia Triple)
`FastWebTripleProbe` se activa con `cache/fastweb-triple-probe/enabled`; un archivo
`request` consume una única captura en el worker. Guarda raw/upright PNG, crop exacto
640×896, input RGB320×448 y tensor float32 little-endian antes del encoder.
Mientras está activo los frames retornan antes de OCR/confirmación/guardado y
`saveEntry` no escribe colección. No usar sus tiempos (incluyen IO). No modificar
preferencias ni fotos existentes. Desactivar quitando únicamente `enabled` tras
cerrar la cámara; preservar evidencia local. Comparador: `tools/fastweb/compare_probe.py`.
El lab muestra top1 sin puerta de similitud; no equivale a aceptación automática.

Comparación real Sony (25/10): tres capturas de Juego de manos, crop/RGB/tensor
idénticos bit a bit a receta labPC; scores0.594893,0.386269(incorrecto Jeska),0.471285.
Sin fallo de preprocesado reproducido. El lab presenta top1 sin aceptación; cinco
de siete fixtures también quedan por debajo0.70. No cambiar umbrales sin calibración.
851 unit tests+replay7capturasSonyOK. Evidencias build/triple-diego-input-01.

Protección preparada: OcrCaptureActivity.consolidateIdenticalCopies excluye los
grupos FastWebN/FastWebTriplefecha mediante FastWebGroups.ownsPhysicalCopy, igual
que la excepción previa CorneliusN. Evita fusionar filas físicas de tests al
reabrir Biblio. No cambia la ruta Triple original. Compilada, pendiente instalar.
Durante comprobación postapertura se observaron430→422filas/502→502copias, usuario
confirma altasOCR. No restaurar sobre esas altas. Una foto antigua ausente queda
conservada en backup; no afirmar todos los datos idénticos tras apertura.
