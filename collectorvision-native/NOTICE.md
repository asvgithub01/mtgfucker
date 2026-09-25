# CollectorVision native experimental adapter

Upstream: https://github.com/HanClinto/CollectorVision (HanClinto and contributors).
Behavioral reference: `collector_vision/detectors/neural.py`,
`collector_vision/embedders/neural.py`, deployed `scanner.worker.mjs` (retrieved 2026-09-25).

The Kotlin implementation adapts the upstream preprocessing contract, corner ordering,
confidence gate, perspective rectification, embedding normalization, and float16 catalog
search. This module is marked **AGPL-3.0-or-later**; see LICENSE. It is not a claim that
isolating a module removes copyleft obligations for a linked application. Before external
distribution, audit the combined application and model/data rights or obtain an applicable
commercial license from upstream. The project owner must approve the distribution route.
No commercial license has been purchased or inferred.

Runtime models are downloaded from the official public demo, not bundled in Git/APK.
Model SHA-256 values originate in its manifest. Catalog SHA values were computed over the
retrieved official bytes because the manifest does not publish catalog hashes. These pins
prevent mixing model/catalog releases; they are not independent provenance certification.
Models: Cornelius 2.12, Milo 1.0.0. Catalog: milo1-scryfall-mtg-2026-07-09,
109711 IDs / 128-dimensional float16 vectors. File pins: CollectorVisionAssets.kt.
Source manifest snapshot: upstream-manifest.json. If URLs change bytes, preparation fails
closed; do not remove checks. ONNX Runtime Android (MIT) and OpenCV (Apache-2.0) remain
separate dependencies with their own notices/licenses.

CPU only. This is an experimental native port, not upstream Android code or equivalent
pixel-for-pixel browser preprocessing. Android Bitmap bilinear resizing and OpenCV warp
can differ slightly from browser canvas/Python; compare identification results before
claiming parity. Both 0 and 180 degree crops are embedded; top three candidates are
returned. No edition/language/finish certainty or collection writes are implied.

About 42.1 MB is fetched once into application no-backup private files. On reuse every file
is checked against its pinned length and SHA-256. Inference and nearest-neighbor search
are then local. Initial asset fetch uses only the official HTTPS host. The separate UI can
fetch Scryfall metadata by candidate UUID: this is not a fully-offline UI guarantee.
