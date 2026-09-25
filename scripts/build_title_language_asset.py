#!/usr/bin/env python3
"""Offline exact-title language multimap. Does not collapse shared ES/PT aliases.
Requires ijson. No artwork hashes or Room schema changes.
"""
import argparse, gzip, hashlib, json, unicodedata
from pathlib import Path
import ijson
LANGUAGES = {"English":"en", "Spanish":"es", "Portuguese (Brazil)":"pt", "French":"fr", "German":"de", "Italian":"it", "Japanese":"ja", "Korean":"ko", "Russian":"ru", "Chinese Simplified":"zhs", "Chinese Traditional":"zht", "Hebrew":"he", "Arabic":"ar", "Latin":"la", "Ancient Greek":"grc", "Sanskrit":"sa", "Phyrexian":"phyrexian"}
def build(source, output):
    rows = set()
    def add(canonical, title, language):
        code = LANGUAGES.get(language)
        if canonical and title and code:
            if any(c in canonical+title for c in "\t\r\n"): raise ValueError("Invalid TSV name")
            key = " ".join("".join(c for c in unicodedata.normalize("NFD", title) if not unicodedata.category(c).startswith("M")).lower().split())
            rows.add((key, canonical, title, code))
    with gzip.open(source, "rb") as stream:
        for _, card_set in ijson.kvitems(stream, "data"):
            for card in card_set.get("cards", []):
                canonical = card.get("name", "")
                if card.get("language", "English") == "English":
                    add(canonical, card.get("faceName") or canonical, "English")
                for foreign in card.get("foreignData", []):
                    add(canonical, foreign.get("faceName") or foreign.get("name"), foreign.get("language"))
    with source.open("rb") as stream: sha = hashlib.file_digest(stream, "sha256").hexdigest()
    payload = ("TLV1\t"+sha+"\n" + "".join("\t".join(row)+"\n" for row in sorted(rows))).encode("utf-8")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(gzip.compress(payload, mtime=0))
    report = dict(schema="TLV1", source="MTGJSON AllPrintings", sourceSha256=sha, rows=len(rows), bytes=output.stat().st_size)
    output.with_suffix(".provenance.json").write_text(json.dumps(report, indent=2)+"\n", encoding="utf-8")
    print(json.dumps(report))
if __name__ == "__main__":
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--input", required=True, type=Path)
    p.add_argument("--output", required=True, type=Path)
    a=p.parse_args();build(a.input,a.output)
