# Tmikonen Android laboratory port

`TmikonenGeometry` and `TmikonenDetector` translate the segmentation algorithms of
Timo Ikonen, https://github.com/tmikonen/magic_card_detector, pinned revision
`78728930bd8494aeed892e87200f5e06ac90ac51`. MIT notice: `TMIKONEN-LICENSE.txt`,
also bundled in the private APK assets.

OpenCV Android replaces Python OpenCV; JTS 1.20.0 replaces Shapely/GEOS geometry.
JTS is licensed under EPL-2.0 / EDL-1.0; dependency archive includes its notices.
This is a port, not execution of the unmodified Python source.

Preserved: Lab CLAHE2/8x8, gray70/adaptive/RGB110 modes, convex hull,
short-edge simplification15%, enclosing quadrilateral enumeration, corner-region
roundness test0.35, form factor0.25–0.33, minimum area and early exit.

Explicit laboratory additions / differences:
- Original versus `reject-frame` (remove exact image-edge contours before the
  area heuristic). Variant learned on the initial16-card photo, not held out.
- Largest-area-first polygon NMS IoU0.5 (same PC lab wrapper).
- Increasing 4-index combinations replace upstream exhaustive Cartesian product
  with rejected unordered tuples: same accepted candidates, less wasted work.
- JTS geometry failures counted; simplified polygons >32 vertices rejected;
  >30seconds per inference aborts the mode, not silently counted as success.
- No pHash recognition or corrected-color crop warping: comparison measures
  detection/quadrilaterals, not upstream recognition runtime.
- All detectors receive the same normalized image (short edge at most1000).
  Cornelius returns only one proposal; it is not magically made multicard.

Distribution of the full APK also requires checking ONNX model/catalog and image
licenses. This private lab contains user test photos; do not publish it.


## Experimental neural pose detectors (private Sony lab)
- YOLO Pose: https://huggingface.co/jackttv/card-scanner-yolo-pose, revision
  adc1d113ae512edf3102ea0f110ab90d574b28fc. Model card declares AGPL-3.0.
  Exported with training version Ultralytics 8.4.137, FP32/opset17/static640,
  output [1,300,18]. RGB INTER_LINEAR letterbox114 /255; threshold .25;
  end-to-end output, no additional NMS. Hashes in pose-models.json.
- Diego Pose: https://github.com/diegovazquez/mtg_train_card_pose_estimation
  and https://huggingface.co/dhvazquez/mtg_card_pose_estimation revision
  ef6d7912e7e2db79ce24976cb3ea84984e43721a, exports/mtg_4kp_s_r4b.onnx.
  Repository MIT license with additional third-party notices; retain upstream
  LICENSE when distributing. RGB Pillow-equivalent bilinear hard stretch640
  /255, sigmoid threshold .3, highest query only (single-card behavior).
- Both use lab upright geometry validation and common640x896 warp.
  No rotated identification passes. Invalid quads rejected, not clamped.
  Experimental options do not replace Cornelius or imply better accuracy.
  License declarations are attribution, not clearance to publish this APK.

## FastWeb Single 1.39 (laboratorio v26)
Autor: HanClinto. Fuente: https://huggingface.co/HanClinto/ccgdetector-fastweb-single . Ficha HF declara MIT. SHA-256 05d2b90b928a5a3bf0f49aa90aa86211b2103d9c347c238fd18b4f544b3cb8ca. Solo pesos1.39; model.onnx es alias. Preservada ficha en fastweb-source.txt. No implica relicenciar CollectorVision ni otros componentes. PreprocesamientoRGB384ImageNet y salidacornersnormalizados;presenceconstante noesconfianza; sharpness>=.02 según documentación detector upstream. No incorporar código upstream nuevo.
