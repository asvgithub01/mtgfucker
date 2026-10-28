# Fuentes históricas Odin, no un instalador

`archive/` conserva byte a byte los scripts de investigación de
`C:/asv/datasets/mtg-identity-v1` (sin scripts de instalación/backup ni fotos/pesos).
`archive-sha256.json` inventaría esta copia. Estos scripts usan rutas históricas y
algunos ROOT=directorio del script; **no ejecutar directamente desde archive**.
Para reproducir, crear un dataset/experimento NUEVO y adaptar rutas explícitamente,
preservando splits/checkpoints existentes; auditar cada entry point antes de ejecutar.
Los locks registran el entorno histórico; torch CUDA usa su índice específico.

Pipeline: download.py → audit_download_snapshot.py → pilot.py → prepare_second.py /
mine_train_negatives.py → train_second.py → export_pilot.py → build_own_complete_gallery.py
→ package_own_complete.py. Leer scripts: no es un comando único ni independiente de
los artefactos previos. Evaluación: evaluate_heldout.py, replay_own_complete.py,
compare_sony_failures.py. Prohibido usar heldout en entrenamiento.

Modelo conservado: pilot-02/own-fp32.onnx. Own02 config y expanded config rechazado
están al lado de este README. Checkpoints, split y galerías siguen en el dataset externo.
Documentos actuales y siguientes pasos: ../../MODEL_LAB.md.
