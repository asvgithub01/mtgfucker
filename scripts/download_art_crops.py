#!/usr/bin/env python3
"""Cache Scryfall's paper-card art crops for offline hash experiments.

Only the metadata manifest belongs in this repository; keep --data-dir outside Git.
The image download is resumable: valid JPEGs are never requested twice.
"""

from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import gzip
import http.client
import json
from pathlib import Path
import shutil
import sys
import threading
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen


API = "https://api.scryfall.com/bulk-data"
USER_AGENT = "mtgfucker-art-hash-research/0.1"
HEADERS = {"User-Agent": USER_AGENT, "Accept": "application/json;q=0.9,*/*;q=0.8"}
IMAGE_CONNECTIONS = threading.local()


def request(url: str):
    return urlopen(Request(url, headers=HEADERS), timeout=45)


def image_bytes(url: str) -> bytes:
    """Reuse one HTTPS connection per worker instead of a handshake per image."""
    parts = urlsplit(url)
    if parts.scheme != "https" or parts.netloc != "cards.scryfall.io":
        raise ValueError(f"Host de imagen inesperado: {parts.netloc}")
    connection = getattr(IMAGE_CONNECTIONS, "connection", None)
    if connection is None:
        connection = http.client.HTTPSConnection(parts.netloc, timeout=45)
        IMAGE_CONNECTIONS.connection = connection
    try:
        connection.request("GET", parts.path + ("?" + parts.query if parts.query else ""), headers=HEADERS)
        response = connection.getresponse()
        if response.status != 200:
            response.read()
            raise HTTPError(url, response.status, response.reason, response.headers, None)
        data = response.read()
        expected = response.getheader("Content-Length")
        if expected and len(data) != int(expected):
            raise ValueError(f"Imagen incompleta: {len(data)} de {expected} bytes")
        return data
    except (HTTPError, OSError, ValueError, http.client.HTTPException):
        connection.close()
        IMAGE_CONNECTIONS.connection = None
        raise


def fetch_bulk_info() -> dict:
    with request(API) as response:
        entries = json.load(response)["data"]
    return next(item for item in entries if item["type"] == "unique_artwork")


def download_bulk(data_dir: Path, info: dict) -> Path:
    path = data_dir / Path(info["jsonl_download_uri"]).name
    expected = info["compressed_size"]
    if path.exists() and path.stat().st_size == expected:
        return path
    temp = path.with_suffix(path.suffix + ".part")
    with request(info["jsonl_download_uri"]) as response, temp.open("wb") as output:
        shutil.copyfileobj(response, output)
    if temp.stat().st_size != expected:
        raise RuntimeError(f"Bulk incompleto: {temp.stat().st_size} de {expected} bytes")
    temp.replace(path)
    return path


def manifest_entries(bulk_path: Path):
    with gzip.open(bulk_path, "rt", encoding="utf-8") as source:
        for line in source:
            card = json.loads(line)
            if "paper" not in card.get("games", ()):
                continue
            if card.get("image_status") in ("missing", "placeholder"):
                continue
            faces = card.get("card_faces") or []
            if card.get("image_uris", {}).get("art_crop"):
                faces = [card]
            for face_number, face in enumerate(faces):
                uri = face.get("image_uris", {}).get("art_crop")
                if not uri:
                    continue
                key = card["id"] + (f"-{face_number}" if face is not card else "")
                yield {
                    "key": key,
                    "card_id": card["id"],
                    "face": face_number,
                    "illustration_id": face.get("illustration_id"),
                    "name": face.get("name", card["name"]),
                    "layout": card["layout"],
                    "set": card["set"],
                    "collector_number": card["collector_number"],
                    "image_status": card["image_status"],
                    "url": uri,
                    "path": f"images/{card['id'][:2]}/{key}.jpg",
                }


def write_manifest(data_dir: Path, bulk_path: Path) -> list[dict]:
    path = data_dir / "manifest.jsonl"
    entries = list(manifest_entries(bulk_path))
    with path.open("w", encoding="utf-8", newline="\n") as output:
        for entry in entries:
            output.write(json.dumps(entry, ensure_ascii=False, separators=(",", ":")) + "\n")
    print(f"Manifest: {len(entries):,} caras con arte ({path})", flush=True)
    return entries


def valid_jpeg(path: Path) -> bool:
    if not path.exists() or path.stat().st_size < 1024:
        return False
    with path.open("rb") as image:
        return image.read(3) == b"\xff\xd8\xff"


def download_image(data_dir: Path, entry: dict) -> tuple[bool, int, str | None]:
    path = data_dir / entry["path"]
    if valid_jpeg(path):
        return False, path.stat().st_size, None
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix(".jpg.part")
    for attempt in range(4):
        try:
            temp.write_bytes(image_bytes(entry["url"]))
            if not valid_jpeg(temp):
                raise ValueError("Respuesta no JPEG o incompleta")
            size = temp.stat().st_size
            temp.replace(path)
            return True, size, None
        except (HTTPError, URLError, TimeoutError, OSError, ValueError, http.client.HTTPException) as exc:
            temp.unlink(missing_ok=True)
            if isinstance(exc, HTTPError) and exc.code in (400, 403, 404, 410):
                return False, 0, f"{entry['key']}: HTTP {exc.code}"
            if attempt == 3:
                return False, 0, f"{entry['key']}: {exc}"
            time.sleep(min(30, 2 ** attempt))
    raise AssertionError("Unreachable")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, required=True)
    parser.add_argument("--workers", type=int, default=6)
    parser.add_argument("--limit", type=int, help="Solo las primeras N imágenes, para probar")
    parser.add_argument("--metadata-only", action="store_true")
    args = parser.parse_args()
    if not 1 <= args.workers <= 48:
        parser.error("--workers debe estar entre 1 y 48")
    data_dir = args.data_dir.resolve()
    data_dir.mkdir(parents=True, exist_ok=True)
    info = fetch_bulk_info()
    print(f"Bulk Scryfall: {info['updated_at']}, {info['compressed_size']:,} bytes", flush=True)
    bulk_path = download_bulk(data_dir, info)
    entries = write_manifest(data_dir, bulk_path)
    if args.metadata_only:
        return 0
    if args.limit is not None:
        entries = entries[: args.limit]
    downloaded = skipped = total_bytes = 0
    errors = []
    started = time.monotonic()
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = [pool.submit(download_image, data_dir, entry) for entry in entries]
        for completed, future in enumerate(as_completed(futures), 1):
            fresh, size, error = future.result()
            downloaded += int(fresh)
            skipped += int(not fresh and not error)
            total_bytes += size if fresh else 0
            if error:
                errors.append(error)
            if completed % 500 == 0 or completed == len(entries):
                elapsed = max(time.monotonic() - started, 1)
                print(
                    f"{completed:,}/{len(entries):,} | nuevas {downloaded:,} | "
                    f"ya existentes {skipped:,} | errores {len(errors):,} | "
                    f"{total_bytes / 1e9:.2f} GB | {completed / elapsed:.1f} imágenes/s",
                    flush=True,
                )
    error_path = data_dir / "download-errors.txt"
    error_path.write_text("\n".join(errors) + ("\n" if errors else ""), encoding="utf-8")
    if errors:
        print(f"Quedan {len(errors)} errores; repetir el comando para reintentarlos: {error_path}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
