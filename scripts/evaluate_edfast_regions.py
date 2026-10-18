"""Offline experimental Milo region comparison; NEVER an acceptance path or global benchmark.

manifest JSON: references[{id,identity,path}], queries[{path,identity,corners:[[x,y],...]}].
Paths are relative to manifest. Corners are normalized TL,TR,BR,BL from the same photo.
Each profile rebuilds BOTH reference and query embeddings. Keep private images outside Git.
python evaluate_edfast_regions.py --model milo.onnx --manifest corpus.json --output results.json
Requires Pillow, numpy, opencv-python, onnxruntime. No network or Android mutations.
"""
import argparse
import hashlib
import json
from pathlib import Path
import time

import numpy as np
from PIL import Image

MODEL_SHA = "bd13d8d60383c69da04dce261f32e93fdaeaa8fd618fbc991e7385f71b3d45df"
REGIONS = {"full": (0, 0, 1, 1), "upper": (0, 0, 1, .58), "art": (.08, .15, .92, .55)}


def reference_profile(reference):
    """Routing uses REFERENCE metadata, never the expected identity of the query.

    Non-full-art is insufficient for split/adventure/showcase layouts. Unknowns retain
    the whole card until a suitable ROI is independently validated.
    """
    return "art" if (reference.get("full_art") is False and reference.get("layout") == "normal"
                     and not reference.get("frame_effects", [])
                     and reference.get("border_color") != "borderless") else "full"


def lane_summary(refs, scores):
    if not refs:
        return None
    order = np.argsort(-scores)
    best = refs[int(order[0])]
    other = next((int(i) for i in order if refs[int(i)]["identity"] != best["identity"]), None)
    gap = float(scores[order[0]]-scores[other]) if other is not None else None
    return {"identity": best["identity"], "id": best["id"], "score": float(scores[order[0]]),
            "margin": gap, "strongHypothesis": gap is not None and float(scores[order[0]]) >= .70 and gap >= .08}


def shadow_choice(lanes):
    """Do not rank raw scores across differently cropped embedding distributions."""
    strong = [x for x in lanes.values() if x and x["strongHypothesis"]]
    identities = {x["identity"] for x in strong}
    if len(identities) > 1:
        return {"status": "conflict", "candidate": None}
    if identities:
        return {"status": "agreement" if len(strong) > 1 else "single_lane_candidate", "candidate": strong[0]["identity"]}
    return {"status": "weak", "candidate": None}


def region(image, profile):
    x0, y0, x1, y1 = REGIONS[profile]
    w, h = image.size
    return image.crop((round(x0*w), round(y0*h), round(x1*w), round(y1*h)))


def rectify(image, corners):
    import cv2
    xy = np.asarray(corners, np.float32)
    if xy.shape != (4, 2) or not np.isfinite(xy).all() or (xy < 0).any() or (xy > 1).any():
        raise ValueError("Four normalized corners required")
    src = xy * np.array(image.size, np.float32)
    dst = np.array([[0, 0], [447, 0], [447, 447], [0, 447]], np.float32)
    return Image.fromarray(cv2.warpPerspective(np.asarray(image), cv2.getPerspectiveTransform(src, dst),
                                              (448, 448), flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_REPLICATE))


def evaluate(model, manifest, hybrid=False, index_dir=None):
    import onnxruntime as ort
    if hashlib.sha256(model.read_bytes()).hexdigest() != MODEL_SHA:
        raise ValueError("Unexpected Milo model; recalibrate explicitly")
    corpus = json.loads(manifest.read_text(encoding="utf-8"))
    options = ort.SessionOptions()
    options.intra_op_num_threads = 2
    options.inter_op_num_threads = 1
    session = ort.InferenceSession(str(model), sess_options=options, providers=["CPUExecutionProvider"])
    means = np.array([.485, .456, .406], np.float32)
    std = np.array([.229, .224, .225], np.float32)

    def embedding(image, profile):
        # Same fixed tensor => cropping alone does NOT reduce model FLOPs.
        pixels = np.asarray(region(image, profile).resize((448, 448), Image.Resampling.BILINEAR), np.float32)/255
        tensor = ((pixels-means)/std).transpose(2, 0, 1)[None].copy()
        v = session.run(["embedding"], {session.get_inputs()[0].name: tensor})[0].reshape(-1)
        return v / max(float(np.linalg.norm(v)), 1e-12)

    refs = corpus["references"]
    results = []
    matrices = {}
    for profile in REGIONS:
        vectors = np.stack([embedding(Image.open(manifest.parent/r["path"]).convert("RGB"), profile) for r in refs])
        matrices[profile] = vectors
        for query in corpus["queries"]:
            image = rectify(Image.open(manifest.parent/query["path"]).convert("RGB"), query["corners"])
            started = time.perf_counter()
            scores = np.maximum(vectors @ embedding(image, profile),
                                vectors @ embedding(image.rotate(180), profile))
            elapsed = (time.perf_counter()-started)*1000
            order = np.argsort(-scores)
            best = refs[int(order[0])]
            other = next((int(i) for i in order if refs[int(i)]["identity"] != best["identity"]), None)
            results.append({"profile": profile, "query": query["path"], "expected": query["identity"],
                            "topIdentity": best["identity"], "correct": best["identity"] == query["identity"],
                            "score": float(scores[order[0]]),
                            "identityMargin": float(scores[order[0]]-scores[other]) if other is not None else None,
                            "embeddingAndSearchMsDesktop": elapsed,
                            "top": [{"id": refs[int(i)]["id"], "identity": refs[int(i)]["identity"],
                                     "score": float(scores[i])} for i in order[:8]]})
    mixed = []
    if hybrid:
        lanes = {profile: [i for i, ref in enumerate(refs) if reference_profile(ref) == profile] for profile in ("full", "art")}
        if index_dir is not None:
            index_dir.mkdir(parents=True, exist_ok=True)
            provenance = {"experimental": True, "autoAccept": False, "modelSha256": MODEL_SHA, "regions": REGIONS, "lanes": {}}
            for profile, indices in lanes.items():
                data = matrices[profile][indices].astype("<f2").tobytes()
                (index_dir / (profile+".f16.bin")).write_bytes(data)
                entries = [{**refs[i], "imageSha256": hashlib.sha256((manifest.parent/refs[i]["path"]).read_bytes()).hexdigest()} for i in indices]
                (index_dir / (profile+".json")).write_text(json.dumps(entries, indent=2), encoding="utf-8")
                provenance["lanes"][profile] = {"rows": len(indices), "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}
            (index_dir / "manifest.json").write_text(json.dumps(provenance, indent=2), encoding="utf-8")
        for query in corpus["queries"]:
            image = rectify(Image.open(manifest.parent/query["path"]).convert("RGB"), query["corners"])
            started = time.perf_counter()
            summaries = {}
            for profile, indices in lanes.items():
                if not indices:
                    summaries[profile] = None
                    continue
                scores = np.maximum(matrices[profile][indices] @ embedding(image, profile),
                                    matrices[profile][indices] @ embedding(image.rotate(180), profile))
                summaries[profile] = lane_summary([refs[i] for i in indices], scores)
            choice = shadow_choice(summaries)
            mixed.append({"query": query["path"], "expected": query["identity"], "lanes": summaries, **choice,
                          "correctCandidate": choice["candidate"] == query["identity"], "autoAccept": False,
                          "embeddingAndSearchMsDesktop": (time.perf_counter()-started)*1000})
    return {"experimental": True, "note": "Small reference corpus, NOT global precision or mobile latency. No autoaccept.",
            "modelSha256": MODEL_SHA, "referenceCount": len(refs), "regions": REGIONS, "results": results,
            "hybrid": mixed, "routingCounts": {p: sum(reference_profile(r) == p for r in refs) for p in ("art", "full")}}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--hybrid", action="store_true", help="Experimental art/whole-card routing by reference metadata")
    parser.add_argument("--index-dir", type=Path, help="Write separate experimental indexes; never an Android production asset")
    args = parser.parse_args()
    args.output.write_text(json.dumps(evaluate(args.model, args.manifest, args.hybrid, args.index_dir), indent=2), encoding="utf-8")
