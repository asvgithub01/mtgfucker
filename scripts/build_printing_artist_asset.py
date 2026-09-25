#!/usr/bin/env python3
"""PFR1 artist sidecar bound to an existing APV1 index. No images/hashes regenerated.
Source: local MTGJSON AllPrintings.json.gz. Keys: base printing UUID + illustration UUID.
Missing or conflicting artist values are omitted, never guessed. Requires ijson.
"""
import argparse
import gzip
import hashlib
import json
import struct
import uuid
from pathlib import Path
import ijson


def indexed_keys(data):
    if data[:4] != b"APV1":
        raise ValueError("Expected APV1")
    sets, strings, groups, rows = struct.unpack_from(">HIII", data, 4)
    offset = 18
    def skip_text(size):
        nonlocal offset
        length = int.from_bytes(data[offset:offset + size], "big")
        offset += size + length
    for _ in range(sets):
        for size in (1, 2, 1, 2): skip_text(size)
        offset += 8
    for _ in range(strings): skip_text(2)
    keys = set()
    for _ in range(groups):
        art = data[offset:offset + 16]
        count = struct.unpack_from(">I", data, offset + 16)[0]
        offset += 20
        for _ in range(count):
            keys.add(data[offset:offset + 16] + art)
            offset += 60
    if offset != len(data): raise ValueError("APV1 length mismatch")
    return keys


def build(source, printing_index, output):
    with source.open("rb") as input_file:
        source_hash = hashlib.file_digest(input_file, "sha256").digest()
    printing_data = printing_index.read_bytes()
    printing_hash = hashlib.sha256(printing_data).digest()
    wanted = indexed_keys(printing_data)
    artists = {}
    with gzip.open(source, "rb") as stream:
        for code, card_set in ijson.kvitems(stream, "data"):
            cards = card_set.get("cards", [])
            front = {str(c.get("number", "")): c["uuid"] for c in cards if c.get("side") == "a" and c.get("uuid")}
            for card in cards:
                art = card.get("identifiers", {}).get("scryfallIllustrationId")
                printing = front.get(str(card.get("number", ""))) or card.get("uuid")
                if not art or not printing: continue
                key = uuid.UUID(printing).bytes + uuid.UUID(art).bytes
                if key not in wanted: continue
                artist = str(card.get("artist") or "").strip()
                artists.setdefault(key, set()).add(artist)
    resolved = {key: next(iter(values)) for key, values in artists.items() if len(values) == 1 and "" not in values}
    pool = sorted(set(resolved.values()))
    indices = {name: i for i, name in enumerate(pool)}
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_suffix(output.suffix + ".part")
    with temporary.open("wb") as target:
        target.write(b"PFR1" + printing_hash + source_hash)
        target.write(struct.pack(">II", len(pool), len(resolved)))
        for value in pool:
            encoded = value.encode("utf-8")
            if len(encoded) > 1024: raise ValueError("Artist too long")
            target.write(struct.pack(">H", len(encoded)) + encoded)
        for key, value in sorted(resolved.items()):
            target.write(key + struct.pack(">I", indices[value]))
    temporary.replace(output)
    report = dict(schema="PFR1", source="MTGJSON AllPrintings", sourceSha256=source_hash.hex(),
        printingIndexSha256=printing_hash.hex(), artistCount=len(pool), indexedPairs=len(wanted),
        resolvedPairs=len(resolved), missingOrConflicting=len(wanted)-len(resolved), bytes=output.stat().st_size)
    output.with_suffix(".provenance.json").write_text(json.dumps(report, indent=2)+"\n", encoding="utf-8")
    print(json.dumps(report))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--printing-index", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    build(args.input, args.printing_index, args.output)
