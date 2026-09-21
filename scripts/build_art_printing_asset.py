#!/usr/bin/env python3
"""Build the compact offline printing index used after an art-hash match.

The source is MTGJSON AllPrintings.json.gz. Only illustration ids present in the
art-hash JSONL are retained. The output contains metadata, never card images or
prices. It deliberately stores the base MTGJSON printing UUID for every language;
language is a separate physical-card property in the Android collection model.

Requires the generation-only dependency ``ijson`` (``python -m pip install ijson``).
Format v1 is documented next to ``ArtPrintingIndex`` in the Android reader.
"""

from __future__ import annotations

import argparse
import gzip
import json
from pathlib import Path
import sqlite3
import struct
import tempfile
import uuid

try:
    import ijson
except ImportError as exc:  # pragma: no cover - developer setup guard
    raise SystemExit("Falta ijson: ejecuta `python -m pip install ijson`") from exc


MAGIC = b"APV1"
LANGUAGES = {
    "English": 1, "Spanish": 2, "French": 3, "German": 4, "Italian": 5,
    "Portuguese (Brazil)": 6, "Japanese": 7, "Korean": 8, "Russian": 9,
    "Chinese Simplified": 10, "Chinese Traditional": 11, "Hebrew": 12,
    "Latin": 13, "Ancient Greek": 14, "Arabic": 15, "Sanskrit": 16,
    "Phyrexian": 17,
}
BORDERS = {"black": 1, "white": 2, "borderless": 3, "gold": 4, "silver": 5, "yellow": 6}
RARITIES = {"common": 1, "uncommon": 2, "rare": 3, "mythic": 4, "special": 5, "bonus": 6}
FINISHES = {"nonfoil": 1, "foil": 2, "etched": 4}
ROW = struct.Struct(">16s16sIIIHBBBBBBii")  # fixed 60-byte variant record


def art_illustrations(path: Path) -> set[str]:
    result: set[str] = set()
    with path.open(encoding="utf-8") as source:
        for line in source:
            illustration = json.loads(line).get("illustration_id")
            if illustration:
                result.add(illustration.lower())
    return result


def text(output, value: str, maximum: int, length_format: str) -> None:
    encoded = value.encode("utf-8")
    if len(encoded) > maximum:
        raise ValueError(f"Texto supera {maximum} bytes: {value!r}")
    output.write(struct.pack(length_format, len(encoded)))
    output.write(encoded)


def integer(value) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return -1


def finish_flags(values) -> int:
    result = 0
    for value in values or ():
        result |= FINISHES.get(value, 0)
    return result or FINISHES["nonfoil"]


def face_number(side) -> int:
    return {"a": 0, "b": 1}.get(side, 255)


def build(source: Path, hashes: Path, target: Path) -> tuple[int, int]:
    wanted = art_illustrations(hashes)
    if not wanted:
        raise ValueError("El índice hash no contiene illustration_id")
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix(target.suffix + ".part")
    strings: set[str] = set()
    sets: dict[str, tuple[str, str, str, int, int]] = {}

    with tempfile.TemporaryDirectory(prefix="art-printings-") as folder:
        database = Path(folder) / "rows.sqlite"
        connection = sqlite3.connect(database)
        connection.execute("PRAGMA journal_mode=OFF")
        connection.execute("PRAGMA synchronous=OFF")
        connection.execute("PRAGMA temp_store=MEMORY")
        connection.execute("""
            CREATE TABLE variants(
              illustration BLOB NOT NULL, printing BLOB NOT NULL, scryfall BLOB NOT NULL,
              canonical TEXT NOT NULL, display TEXT NOT NULL, collector TEXT NOT NULL,
              set_code TEXT NOT NULL, language INTEGER NOT NULL, border INTEGER NOT NULL,
              finishes INTEGER NOT NULL, rarity INTEGER NOT NULL, face INTEGER NOT NULL,
              mcm_id INTEGER NOT NULL, mcm_meta_id INTEGER NOT NULL,
              PRIMARY KEY(illustration, printing, language)
            ) WITHOUT ROWID
        """)
        with gzip.open(source, "rb") as data:
            for set_code, card_set in ijson.kvitems(data, "data"):
                if card_set.get("isOnlineOnly") is True:
                    continue
                code = str(set_code).upper()
                sets[code] = (
                    str(card_set.get("name") or code),
                    str(card_set.get("releaseDate") or ""),
                    str(card_set.get("mcmName") or card_set.get("name") or code),
                    integer(card_set.get("mcmId")),
                    integer(card_set.get("mcmIdExtras")),
                )
                cards = card_set.get("cards") or ()
                front_by_number = {
                    str(card.get("number") or ""): str(card.get("uuid") or "")
                    for card in cards if card.get("side") == "a" and card.get("uuid")
                }
                batch = []
                for card in cards:
                    if "paper" not in (card.get("availability") or ()):
                        continue
                    identifiers = card.get("identifiers") or {}
                    illustration = str(identifiers.get("scryfallIllustrationId") or "").lower()
                    scryfall = str(identifiers.get("scryfallId") or "")
                    if illustration not in wanted or not scryfall:
                        continue
                    number = str(card.get("number") or "")
                    printing = front_by_number.get(number) or str(card.get("uuid") or "")
                    if not printing:
                        continue
                    canonical = str(card.get("name") or card.get("faceName") or "")
                    if not canonical:
                        continue
                    border = BORDERS.get(str(card.get("borderColor") or ""), 0)
                    finishes = finish_flags(card.get("finishes"))
                    rarity = RARITIES.get(str(card.get("rarity") or ""), 0)
                    face = face_number(card.get("side"))
                    mcm_id = integer(identifiers.get("mcmId"))
                    mcm_meta_id = integer(identifiers.get("mcmMetaId"))
                    base = (
                        uuid.UUID(illustration).bytes, uuid.UUID(printing).bytes,
                        canonical, number, code, border, finishes, rarity, face,
                        mcm_id, mcm_meta_id,
                    )
                    variants = [(scryfall, canonical, 1)]
                    for foreign in card.get("foreignData") or ():
                        language = LANGUAGES.get(str(foreign.get("language") or ""))
                        foreign_id = str((foreign.get("identifiers") or {}).get("scryfallId") or "")
                        display = str(foreign.get("name") or foreign.get("faceName") or "")
                        if language and foreign_id and display:
                            variants.append((foreign_id, display, language))
                    for image_id, display, language in variants:
                        try:
                            batch.append((
                                base[0], base[1], uuid.UUID(image_id).bytes,
                                canonical, display, number, code, language,
                                border, finishes, rarity, face, mcm_id, mcm_meta_id,
                            ))
                        except ValueError:
                            continue
                        strings.update((canonical, display, number))
                    if len(batch) >= 10_000:
                        connection.executemany("INSERT OR IGNORE INTO variants VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)", batch)
                        batch.clear()
                if batch:
                    connection.executemany("INSERT OR IGNORE INTO variants VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)", batch)
        connection.commit()

        set_rows = sorted(sets.items())
        string_rows = sorted(strings)
        string_index = {value: index for index, value in enumerate(string_rows)}
        set_index = {code: index for index, (code, _) in enumerate(set_rows)}
        row_count = connection.execute("SELECT count(*) FROM variants").fetchone()[0]
        group_count = connection.execute("SELECT count(DISTINCT illustration) FROM variants").fetchone()[0]
        try:
            with temporary.open("wb") as output:
                output.write(MAGIC)
                output.write(struct.pack(">HIII", len(set_rows), len(string_rows), group_count, row_count))
                for code, (name, release, mcm_name, mcm_id, mcm_extras) in set_rows:
                    text(output, code, 255, ">B")
                    text(output, name, 65535, ">H")
                    text(output, release, 255, ">B")
                    text(output, mcm_name, 65535, ">H")
                    output.write(struct.pack(">ii", mcm_id, mcm_extras))
                for value in string_rows:
                    text(output, value, 65535, ">H")

                current = None
                rows = []
                query = """SELECT illustration,printing,scryfall,canonical,display,collector,
                           set_code,language,border,finishes,rarity,face,mcm_id,mcm_meta_id
                           FROM variants ORDER BY illustration,printing,language"""
                def flush_group() -> None:
                    if current is None:
                        return
                    output.write(current)
                    output.write(struct.pack(">I", len(rows)))
                    for row in rows:
                        output.write(ROW.pack(
                            row[1], row[2], string_index[row[3]], string_index[row[4]],
                            string_index[row[5]], set_index[row[6]], row[7], row[8],
                            row[9], row[10], row[11], 0, row[12], row[13]
                        ))
                for row in connection.execute(query):
                    if row[0] != current:
                        flush_group()
                        current = row[0]
                        rows = []
                    rows.append(row)
                flush_group()
            temporary.replace(target)
        finally:
            temporary.unlink(missing_ok=True)
            connection.close()
    print(
        f"{group_count:,} ilustraciones; {row_count:,} variantes; "
        f"{target.stat().st_size:,} bytes -> {target}", flush=True
    )
    return group_count, row_count


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True, help="AllPrintings.json.gz")
    parser.add_argument("--art-hashes", type=Path, required=True, help="hashes.jsonl")
    parser.add_argument("--output", type=Path, required=True, help="art_printing_index.bin")
    args = parser.parse_args()
    build(args.input, args.art_hashes, args.output)


if __name__ == "__main__":
    main()
