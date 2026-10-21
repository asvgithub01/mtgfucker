import subprocess
import tempfile
import unittest
from pathlib import Path
from check_architecture_boundary import violations, import_violations


class ArchitectureBoundaryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.git("init", "-q")
        self.git("config", "user.name", "Boundary Test")
        self.git("config", "user.email", "boundary@example.invalid")
        self.git("config", "core.autocrlf", "false")
        self.file = self.root / "app/src/main/Scanner.java"
        self.file.parent.mkdir(parents=True)
        self.file.write_text("original\n")
        self.git("add", ".")
        self.git("commit", "-qm", "baseline")

    def git(self, *args):
        subprocess.run(["git", "-C", str(self.root), *args], check=True, capture_output=True)

    def test_clean(self):
        self.assertEqual([], violations(self.root, "HEAD"))

    def test_unstaged_edit(self):
        self.file.write_text("changed\n")
        self.assertEqual(1, len(violations(self.root, "HEAD")))

    def test_staged_edit(self):
        self.file.write_text("changed\n")
        self.git("add", ".")
        self.assertEqual(1, len(violations(self.root, "HEAD")))

    def test_deleted(self):
        self.file.unlink()
        self.assertEqual(1, len(violations(self.root, "HEAD")))

    def test_new_source_allowed(self):
        (self.file.parent / "New.kt").write_text("new\n")
        self.assertEqual([], violations(self.root, "HEAD"))

    def test_launcher_exception_does_not_allow_scanner_changes(self):
        folder = self.root / "app/src/main/java/io/asv/mtgocr/ocrreader"
        folder.mkdir(parents=True)
        launcher = folder / "MainActivity.java"
        scanner = folder / "OcrCaptureActivity.java"
        launcher.write_text("launcher\n")
        scanner.write_text("scanner\n")
        self.git("add", ".")
        self.git("commit", "-qm", "entry points")
        launcher.unlink()
        self.assertEqual([], violations(self.root, "HEAD"))
        scanner.write_text("changed\n")
        self.assertEqual(1, len(violations(self.root, "HEAD")))

    def test_domain_cannot_import_android_or_legacy_data(self):
        folder = self.root / "app/src/main/java/io/asv/mtgocr/ocrreader/domain"
        folder.mkdir(parents=True)
        (folder / "Bad.kt").write_text("import android.content.Context\nimport io.asv.mtgocr.ocrreader.data.CardRepository\n")
        self.assertEqual(2, len(import_violations(self.root)))

    def test_feature_depends_on_domain_not_concrete_repository(self):
        folder = self.root / "app/src/main/java/io/asv/mtgocr/ocrreader/feature"
        folder.mkdir(parents=True)
        source = folder / "Screen.kt"
        source.write_text("import io.asv.mtgocr.ocrreader.domain.collection.CollectionReader\n")
        self.assertEqual([], import_violations(self.root))
        source.write_text("import io.asv.mtgocr.ocrreader.data.CardRepository\n")
        self.assertEqual(1, len(import_violations(self.root)))


if __name__ == "__main__":
    unittest.main()
