#!/usr/bin/env python3
"""Offline pHash/dHash experiment over downloaded Scryfall art crops.

This only compares *art crops*. A photographed card still needs perspective
rectification and an artwork-region crop before it can be queried reliably.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

import cv2
import numpy as np


def load_gray(path: Path) -> np.ndarray:
    image = cv2.imdecode(np.fromfile(str(path), dtype=np.uint8), cv2.IMREAD_GRAYSCALE)
    if image is None:
        raise ValueError(f"Imagen ilegible: {path}")
    return image


def bits(values) -> int:
    result = 0
    for value in values:
        result = (result << 1) | int(value)
    return result


def hashes(gray: np.ndarray) -> tuple[int, int]:
    small = cv2.resize(gray, (32, 32), interpolation=cv2.INTER_AREA)
    coefficients = cv2.dct(small.astype(np.float32))[:8, :8].ravel()
    # The DC component mostly measures overall brightness and is excluded.
    phash = bits(coefficients[1:] > np.median(coefficients[1:]))
    strip = cv2.resize(gray, (9, 8), interpolation=cv2.INTER_AREA)
    dhash = bits((strip[:, :-1] > strip[:, 1:]).ravel())
    return phash, dhash


def build(data_dir: Path) -> int:
    manifest = data_dir / "manifest.jsonl"
    output = data_dir / "hashes.jsonl"
    temp = data_dir / "hashes.jsonl.part"
    count = missing = 0
    with manifest.open(encoding="utf-8") as source, temp.open("w", encoding="utf-8", newline="\n") as target:
        for line in source:
            item = json.loads(line)
            path = data_dir / item["path"]
            if not path.exists():
                missing += 1
                continue
            try:
                phash, dhash = hashes(load_gray(path))
            except ValueError as error:
                print(error, file=sys.stderr)
                missing += 1
                continue
            target.write(json.dumps({
                "key": item["key"], "illustration_id": item["illustration_id"],
                "name": item["name"], "set": item["set"],
                "collector_number": item["collector_number"],
                "phash": f"{phash:016x}", "dhash": f"{dhash:016x}",
            }, ensure_ascii=False, separators=(",", ":")) + "\n")
            count += 1
            if count % 5000 == 0:
                print(f"Hashes: {count:,}", flush=True)
    temp.replace(output)
    print(f"Índice: {count:,} imágenes, faltan {missing:,}; {output.stat().st_size:,} bytes ({output})")
    return 0 if missing == 0 else 1


def match(data_dir: Path, image: Path, top: int) -> int:
    phash, dhash = hashes(load_gray(image))
    results = []
    with (data_dir / "hashes.jsonl").open(encoding="utf-8") as source:
        for line in source:
            item = json.loads(line)
            pdistance = (phash ^ int(item["phash"], 16)).bit_count()
            ddistance = (dhash ^ int(item["dhash"], 16)).bit_count()
            # pHash is primary; dHash breaks ties.
            results.append((pdistance, ddistance, item))
    for pdistance, ddistance, item in sorted(results, key=lambda row: (row[0], row[1]))[:top]:
        print(f"p={pdistance:2} d={ddistance:2} | {item['name']} | {item['set']} #{item['collector_number']} | {item['key']}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, required=True)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("build")
    query = commands.add_parser("match")
    query.add_argument("image", type=Path)
    query.add_argument("--top", type=int, default=10)
    args = parser.parse_args()
    return build(args.data_dir) if args.command == "build" else match(args.data_dir, args.image, args.top)


if __name__ == "__main__":
    raise SystemExit(main())
