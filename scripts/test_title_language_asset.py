import gzip
import json
import tempfile
import unittest
from pathlib import Path
from build_title_language_asset import build


class TitleLanguageAssetTest(unittest.TestCase):
    def test_shared_aliases_without_scryfall_ids_survive_and_output_is_deterministic(self):
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "source.gz"
            source.write_bytes(gzip.compress(json.dumps({"data": {"SET": {"cards": [
                {"name": "Llanowar Elves", "foreignData": [
                    {"name": "Elfos de Llanowar", "language": "Spanish"},
                    {"name": "Elfos de Llanowar", "language": "Portuguese (Brazil)"}]},
                {"name": "Foreign Only", "language": "Japanese", "foreignData": []}
            ]}}}).encode()))
            output = Path(tmp) / "index.gz"
            build(source, output)
            first = output.read_bytes()
            build(source, output)
            self.assertEqual(first, output.read_bytes())
            text = gzip.decompress(first).decode()
            self.assertIn("elfos de llanowar\tLlanowar Elves\tElfos de Llanowar\tes", text)
            self.assertIn("elfos de llanowar\tLlanowar Elves\tElfos de Llanowar\tpt", text)
            self.assertNotIn("Foreign Only", text)  # canonical English name is not a Japanese printed title

    def test_face_names_are_preferred_and_invalid_tsv_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "source.gz"
            card = {"name": "Front // Back", "faceName": "Front", "foreignData": [
                {"name": "Frente // Trás", "faceName": "Frente", "language": "Portuguese (Brazil)"}]}
            def save():
                source.write_bytes(gzip.compress(json.dumps({"data": {"S": {"cards": [card]}}}).encode()))
            save()
            output = Path(tmp) / "index.gz"
            build(source, output)
            self.assertIn("frente\tFront // Back\tFrente\tpt", gzip.decompress(output.read_bytes()).decode())
            card["faceName"] = "Bad\tName"
            save()
            with self.assertRaises(ValueError):
                build(source, output)


if __name__ == "__main__":
    unittest.main()
