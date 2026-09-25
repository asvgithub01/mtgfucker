import contextlib
import gzip
import io
import json
from pathlib import Path
import struct
import tempfile
import unittest
import uuid
from build_printing_artist_asset import build, indexed_keys


class PrintingArtistAssetTest(unittest.TestCase):
    def test_binding_determinism_and_conflicts(self):
        art = uuid.UUID(int=3)
        a = uuid.UUID(int=1); b = uuid.UUID(int=2)
        index = b"APV1" + struct.pack(">HIII", 0, 0, 1, 2) + art.bytes + struct.pack(">I", 2)
        index += a.bytes + bytes(44) + b.bytes + bytes(44)
        self.assertEqual({a.bytes + art.bytes, b.bytes + art.bytes}, indexed_keys(index))
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp); source = folder / "source.gz"; printing = folder / "printing.bin"; target = folder / "artists.bin"
            printing.write_bytes(index)
            def card(id, artist):
                return dict(uuid=str(id), number=str(id.int), artist=artist, identifiers=dict(scryfallIllustrationId=str(art)))
            with gzip.open(source, "wt", encoding="utf-8") as stream:
                json.dump(dict(data=dict(TEST=dict(cards=[card(a, "Wayne England"), card(b, "A"), card(b, "B")]))), stream)
            with contextlib.redirect_stdout(io.StringIO()): build(source, printing, target)
            first = target.read_bytes()
            self.assertEqual(b"PFR1", first[:4])
            self.assertEqual((1, 1), struct.unpack_from(">II", first, 68))
            self.assertEqual(1, json.loads(target.with_suffix(".provenance.json").read_text())["missingOrConflicting"])
            with contextlib.redirect_stdout(io.StringIO()): build(source, printing, target)
            self.assertEqual(first, target.read_bytes())

    def test_invalid_index_rejected(self):
        with self.assertRaises(ValueError): indexed_keys(b"wrong")


if __name__ == "__main__": unittest.main()
