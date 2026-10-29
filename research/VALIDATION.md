# Verificación del handoff — 2026-10-28

## Actualización29-10-2026 — Esquinitas
- Unity2022.3.62f2 compiló y renderizó120calibración +1600MTG, validadoresPASS.
- Revisados overlays (esquinas ocultas conservadas, funda no confundida con carta).
- Generadorv3 desacopla negativos del índice de textura;9testsPythonPASS.
- EntrenamientoCNN300pasos enRTX5090Laptop52,515s. Error medio0,135normalizado:
  NO apto, no promoción. Presencia97,5% no equivale a calidad de esquinas.
- Sin fotos reales en entrenamiento, sin teachers, sin cambios Android/Odin/colección.
- Fuentes versionadas; imágenes/pesos/resultados detallados en proyectoUnity local.

- 7 unit tests de configuración/etiquetas: PASS (hash, winding, completitud, fuga).
- 1 unit test de diff de sets: PASS; no consulta programada ni publicación activada.
- CornerNet: forward/backward CPU con tensor aleatorio y shapes correctos: PASS.
  Es prueba de código, NO entrenamiento ni evaluación visual.
- `compileall research`: PASS. Archivo histórico no ejecutado/importado por ese comando.
- `tools/fastweb/check_triple_isolation.py`: PASS,47 fuentes originales intactas.
- `gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain`:
  BUILD SUCCESSFUL (12s, mayoría de tareas up-to-date); no instalación ni acceso Sony.
- Ningún peso, foto, catálogo privado o credencial incluido en el commit.

Pendiente: Unity editor compile, render, overlays, entrenamiento sintético acotado,
validación con fotos reales reservadas y paridad Android del futuro detector.
No se ha medido precisión/velocidad del detector nuevo. El Odin instalado no cambia.
Logs locales de build: `build/model-handoff-validation.log` (no en Git).
