# Esquinitas — piloto de detector propio

No es Odin, no accede al Sony/Android/colección. Unity2022.3 Built-in, versión de
proyecto2022.3.62f2 (parche de seguridad; no instalar f1); registrar versión efectiva.
No requiere Perception ni assets externos. **Unity ejecutado y piloto01 entrenado; NO apto para integrar.**

Nombre elegido29-10-2026. Conservar `research/card-corners` como ruta técnica.
Cornelius y FastWeb serán teachers/comparadores en una fase posterior: sus salidas
no reemplazan las esquinas geométricas exactas de Unity. Ver plan en08-UNITY-CORNERS.md.
No hay distillation ni generación de pseudoetiquetas conectadas al trainer actualmente.
Integración móvil futura exclusivamente sobre la rama de refactor (confirmar su nombre).

## Copia ejecutable solicitada por el usuario
`C:/asv/proyectos/2026/mtgfucker/mtgfucker/trainning models/unity`.
Contiene proyecto en raíz, scripts/, data/calibration-01/, logs/ y source-manifest.json.
Fuentes canónicas siguen aquí, en el worktree experimental; sincronizar cambios
explícitamente y no sobrescribir datos/Library del proyecto local. El directorio externo
mtg-corners-v1 previo se conserva, no se mueve ni se borra.
`run-calibration.ps1 -Project <ruta-local>` ejecuta con GPU/D3D11 y exige COMPLETE.
Instalación del editor prevista: `C:/asv/tools/unity/2022.3.62f2`.
Necesita licencia válida del usuario; no activar automáticamente Personal.
[Parche oficial](https://unity.com/releases/editor/whats-new/2022.3.62f2).

Calibración inicial sin ilustraciones MTG: `python prepare_smoke.py --root <ruta-nueva>`.
Ya preparada localmente en `C:/asv/datasets/mtg-corners-v1/calibration-01`:
cuatro texturas procedurales propias con marcas de orientación y config120renders.
Las texturas de calibración no son un dataset válido para medir reconocimiento MTG.
El29-10-2026 se generaron120renders calibración válidos en la copia ejecutable local.

## Resultado inicial29-10-2026
`prepare_mtg_pilot.py` seleccionó64train+16val del split agrupado congelado Odin,
mitad fullart en cada partición; verifica SHA y separación grupo/oracle/arte/hash.
Copia texturas de referencia, no fotos reales. Unityv3 generó1600escenas (1280/320),
presencia aleatoria independiente de textura; mantiene ocultas en GT. ValidaciónPASS.
Trainer300pasos RTX5090Laptop:52,515s incluida evaluación; error esquina normalizado
0,1350, visible0,13487/oculta0,13639. Presencia97,5%,recall99,29%,FPR15,79%(6/38negativos).
**Error de recorte elevado; NO promover ni inferir precisión física.** Teachers no usados.
Artefactos locales: `unity/data/mtg-pilot-01`, `unity/runs/pilot-01` y
`unity/reports/pilot-01/report.html` bajo la carpeta ejecutable. Pesos/imágenes fueraGit.
`report_pilot.py` dibuja GTverde y predicciónroja; verifica hashes del dataset congelado.
Siguiente: más diversidad/realismo y comparar arquitectura/pérdida de localización;
medir teachers en el mismo conjunto antes de intentar imitación. No basta presencia.

1. Crear área externa `C:/asv/datasets/mtg-corners-v1` y copiar texturas autorizadas
   a `sources/`, conservando procedencia. No copiar automáticamente fotos privadas.
2. Escribir `textures.json` como lista de `{ "path": "C:/.../sources/carta.jpg",
   "group": "oracle-art-component-id", "split": "train" }` (también validation).
   Agrupar previamente oracle/arte/hash/duplicados y mantener reservas reales fuera.
3. `python prepare.py --textures C:/.../textures.json --config C:/.../config.json --output C:/.../renders/pilot-01 --count 120`
4. PowerShell: `$env:CARD_PILOT_CONFIG='C:/.../config.json'`.
   Ejecutar Unity.exe `-batchmode -quit -projectPath <ruta-absoluta-a-unity>
   -executeMethod CardPilot.Generate -logFile C:/.../unity.log`.
   **No usar -nographics**, necesita GPU para render. No abrir un proyecto ajeno.
   Output nuevo obligatorio; un fallo conserva parciales sin COMPLETE.
5. `python validate.py C:/.../renders/pilot-01 --overlays C:/.../reviews/pilot-01`.
   Inspeccionar visualmente UV/título arriba/corners0..3 y máscaras negativas;
   si no coinciden, no entrenar. Validación numérica sola no prueba alineación visual.
6. `python train_pilot.py --dataset C:/.../renders/pilot-01 --out C:/.../runs/pilot-01 --steps 300 --max-seconds 600`
   PyTorch CUDA, numpy/Pillow. Entrada RGB160×224 [0,1], BCEpresencia+5SmoothL1esquinas,
   AdamW3e-4, batch16, semilla20261028. Red desde cero; NO pesos de otros modelos.
   No es entrenamiento disparado automáticamente ni resultado de calidad final.

Etiquetas schema2 (validador compatible con schema1): normalized-top-left, esquinas semánticas textura TL,TR,BR,BL.
Solo cartas completas, rotación Z±30°, X/Y±42°, margen2,5%; presenciafalse sin esquinas.
Cada registro incluye texturaSHA/grupo/split, imageSHA, luz, pose, funda/foil aproximado.
Oclusiones: en aproximadamente40% de cartas presentes se coloca un proxy elipsoidal
de pulgar, de tamaño/color variable, cerca de una esquina aleatoria. No es una mano
fotorealista. `cornerVisible[4]` se calcula con rayos contra la malla real del proxy;
las cuatro coordenadas GT se conservan incluso tapadas. El trainer sigue supervisando
TODAS las esquinas de cartas presentes, sin eliminar del loss las ocultas.
No incluye todavía estimador de incertidumbre/visibilidad en la red: calibrarlo antes
de uso real, rechazar oclusión excesiva y evaluar error visible/oculto por separado.
Los overlays marcan HIDDEN en cian. Esto estima geometría, NO recupera arte tapado.
Se puede importar el mismo dataset en otras arquitecturas, sin renderizar de nuevo.
Repetibilidad geométrica con seed; no prometer PNG idénticos entre GPUs/editores.

Pruebas: `python -m unittest discover -s tests -v`. Trainer guarda config/log/checkpoints/
métricas sintéticas; no exporta ni instala ONNX. Ver ../../docs/model-lab/08-UNITY-CORNERS.md.
