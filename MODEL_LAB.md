# Modelos MTG: índice para retomar en chats separados

Estado al 28-10-2026. Rama `codex/edscan-fastweb-diego`. No mezclar este trabajo con
`feature/newEditionScanner` ni promover a master sin una integración explícita.

Actualización29-10-2026: el detector propio se llama **Esquinitas**. Por indicación
del usuario, las integraciones futuras de modelos se harán sobre la **rama de refactor**,
no sobre master. Confirmar nombre exacto y estado de esa rama antes de integrar; no
fusionar esta rama completa a ciegas ni arrastrar las copias históricas de Activities.
Portar contratos/adaptadores y pruebas a la arquitectura refactorizada.

| Chat / tema | Documento | Estado |
|---|---|---|
| Odin estable | [01-ODIN](docs/model-lab/01-ODIN.md) | Probado en laboratorio; mantener encoder y umbrales |
| Nuevas colecciones / distribución Play | [02-CATALOG-UPDATES](docs/model-lab/02-CATALOG-UPDATES.md) | Arquitectura y comprobador manual; sin servicio desplegado |
| Odin Next | [03-ODIN-NEXT](docs/model-lab/03-ODIN-NEXT.md) | Investigación; ampliado anterior rechazado |
| Edición / impresión | [04-PRINTING](docs/model-lab/04-PRINTING.md) | Propuesta de segundo estadio |
| Idioma | [05-LANGUAGE](docs/model-lab/05-LANGUAGE.md) | Comparar OCR selectivo y modelo, no decidido |
| Foil / firmas | [06-FINISH-SIGNATURE](docs/model-lab/06-FINISH-SIGNATURE.md) | Investigación futura, no inferencia fiable implementada |
| Pruebas físicas / evidencias | [07-PHYSICAL-TESTS](docs/model-lab/07-PHYSICAL-TESTS.md) | Captura opt-in existente, revisión humana pendiente |
| Esquinitas / Unity | [08-UNITY-CORNERS](docs/model-lab/08-UNITY-CORNERS.md) | Unity operativo; piloto sintético01 entrenado, recortes insuficientes, NO promovido |

Código Android conservado en `app/` y `collectorvision-native/`, copias `Own*` y
`FastWeb*`. Fuentes de entrenamiento Odin archivadas en `research/odin/`.
Generador, validación y entrenamiento de esquinas juntos en `research/card-corners/`.
Fotos, imágenes Scryfall, pesos, galerías y resultados privados **no se suben a Git**.
Git no sustituye el backup de esos artefactos externos.

Datos existentes: `C:/asv/datasets/mtg-identity-v1`.
Nuevos renders propuestos: `C:/asv/datasets/mtg-corners-v1`, separados de Odin.
No hay entrenamiento ni seguimiento periódico nuevos arrancados por estos documentos.

Para retomar: pedir «Lee MODEL_LAB.md y el documento del tema X; comprueba estado
real antes de ejecutar; no toques originales, colección, fotos ni Odin estable».

## Odin Lab comparador separado
`tools/odin_android/README.md`: paquete io.asv.odinlab,12detectoresxMilo/Odin=24,
EsquinitasEXPsoloexperimento; conservaDiegoLab/Tripleoriginal/Odin. Entradacámara,
fotoA/B yparidad. GaleríasMilo/Odindiferentes, no inferir precisión directamente.
