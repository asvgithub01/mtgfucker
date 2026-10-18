"""Resumable complete upper/art index for the EXACT existing EdScan row universe.
Never publishes a partial manifest. Inputs are local bulk metadata, IDs, pinned Milo.
Images are public reference images; user photos/collections are never read here.
"""
import argparse
import concurrent.futures
import hashlib
import io
import json
from pathlib import Path
import sqlite3
import time
import urllib.request

import numpy as np
import onnxruntime as ort
from PIL import Image
from evaluate_edfast_regions import MODEL_SHA, REGIONS, reference_profile, region


def build(work, model, workers):
    assert hashlib.sha256(model.read_bytes()).hexdigest() == MODEL_SHA
    ids = json.loads((work / "target-ids.json").read_text(encoding="utf-8"))
    cards = json.loads((work / "cards.json").read_text(encoding="utf-8"))
    assert len(ids) == len(set(ids)) == 110278
    rows = []
    for key in ids:
        c = cards[key.removesuffix("_back")]
        face = c.get("card_faces", [c])[1 if key.endswith("_back") else 0]
        images = c.get("image_uris") or face.get("image_uris")
        assert images and images.get("normal"), key
        profile = reference_profile(c)
        rows.append({"id": key, "identity": c["name"].strip().lower(), "name": c["name"],
                     "set": c["set"], "released": c["released_at"], "profile": profile,
                     "url": images["normal"], "full_art": c.get("full_art", False)})
    del cards
    metadata = json.dumps(rows, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    if (work / "vectors.sqlite").exists():
        assert (work / "rows.json").read_bytes() == metadata, "Reference metadata changed: use a new work directory"
    (work / "rows.json").write_bytes(metadata)
    db = sqlite3.connect(work / "vectors.sqlite")
    db.execute("CREATE TABLE IF NOT EXISTS vectors(id TEXT PRIMARY KEY, upper BLOB, art BLOB, image_sha TEXT)")
    db.execute("CREATE TABLE IF NOT EXISTS config(key TEXT PRIMARY KEY, value TEXT)")
    config = json.dumps({"model": MODEL_SHA, "regions": REGIONS}, sort_keys=True)
    previous = db.execute("SELECT value FROM config WHERE key='contract'").fetchone()
    assert previous is None or previous[0] == config, "Do not resume vectors with a different model/ROI"
    db.execute("INSERT OR IGNORE INTO config VALUES('contract',?)", (config,)); db.commit()
    done = {r[0] for r in db.execute("SELECT id FROM vectors")}
    pending = [r for r in rows if r["profile"] != "full" and r["id"] not in done]
    opts = ort.SessionOptions(); opts.intra_op_num_threads = 1; opts.inter_op_num_threads = 1
    opts.add_session_config_entry("session.intra_op.allow_spinning", "0")
    session = ort.InferenceSession(str(model), sess_options=opts, providers=["CPUExecutionProvider"])
    mean = np.array([.485, .456, .406], np.float32); std = np.array([.229, .224, .225], np.float32)
    images = work / "images"; images.mkdir(exist_ok=True)

    def process(row):
        image = images / (row["id"] + ".jpg")
        if not image.exists():
            for attempt in range(5):
                try:
                    req = urllib.request.Request(row["url"], headers={"User-Agent": "MtgOcr-OfflineRegionIndex/1.0"})
                    with urllib.request.urlopen(req, timeout=45) as response:
                        data = response.read()
                    with Image.open(io.BytesIO(data)) as im: im.verify()
                    tmp = image.with_suffix(".part"); tmp.write_bytes(data); tmp.replace(image)
                    break
                except Exception:
                    if attempt == 4: raise
                    time.sleep(2 ** attempt)
        data = image.read_bytes()
        with Image.open(io.BytesIO(data)) as raw: rgb = raw.convert("RGB")
        vectors = []
        for profile in ("upper", "art"):
            pixels = np.asarray(region(rgb, profile).resize((448, 448), Image.Resampling.BILINEAR), np.float32)/255
            x = ((pixels-mean)/std).transpose(2, 0, 1)[None].copy()
            v = session.run(["embedding"], {session.get_inputs()[0].name: x})[0].reshape(-1)
            v = v / np.linalg.norm(v)
            assert v.shape == (128,) and np.isfinite(v).all()
            vectors.append(v.astype("<f2").tobytes())
        return row["id"], *vectors, hashlib.sha256(data).hexdigest()

    start = time.time(); count = 0; errors = []
    print(json.dumps({"total": len(rows), "cropRows": sum(r["profile"] != "full" for r in rows),
                      "pending": len(pending), "workers": workers}), flush=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool:
        # Bound queued futures and reference metadata retained by worker machinery.
        for offset in range(0, len(pending), workers*8):
            futures = {pool.submit(process, row): row["id"] for row in pending[offset:offset+workers*8]}
            for future in concurrent.futures.as_completed(futures):
                try: db.execute("INSERT OR REPLACE INTO vectors VALUES(?,?,?,?)", future.result())
                except Exception as exc: errors.append({"id": futures[future], "error": str(exc)})
                count += 1
            db.commit()
            status = {"processed": count, "remaining": len(pending)-count, "errors": len(errors), "seconds": round(time.time()-start)}
            (work / "progress.json").write_text(json.dumps(status)); print(json.dumps(status), flush=True)
    (work / "errors.json").write_text(json.dumps(errors, indent=2))
    complete = {r[0] for r in db.execute("SELECT id FROM vectors")}
    assert not errors and all(r["profile"] == "full" or r["id"] in complete for r in rows), "Incomplete: resume before packaging"
    output = work / "index"; output.mkdir(exist_ok=True)
    files = {}
    # Full-layout rows use original pinned whole-card vectors in Android; zeros never searched.
    for profile in ("upper", "art"):
        path = output / (profile + ".f16.bin")
        with path.with_suffix(".part").open("wb") as f:
            for row in rows:
                vector = bytes(256) if row["profile"] == "full" else db.execute(f"SELECT {profile} FROM vectors WHERE id=?", (row["id"],)).fetchone()[0]
                f.write(vector)
        path.with_suffix(".part").replace(path)
        files[path.name] = {"bytes": path.stat().st_size, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
    compact = [[r["id"], r["identity"], r["set"], r["released"], r["profile"] == "full"] for r in rows]
    path = output / "rows.json"; path.write_text(json.dumps(compact, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    files[path.name] = {"bytes": path.stat().st_size, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
    manifest = {"version": 1, "complete": True, "rows": len(rows), "modelSha256": MODEL_SHA, "regions": REGIONS,
                "files": files, "fullRows": sum(r["profile"] == "full" for r in rows)}
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2))
    print("COMPLETE " + json.dumps(manifest), flush=True)


if __name__ == "__main__":
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--work", type=Path, required=True); p.add_argument("--model", type=Path, required=True)
    p.add_argument("--workers", type=int, default=12)
    a = p.parse_args(); build(a.work, a.model, a.workers)
