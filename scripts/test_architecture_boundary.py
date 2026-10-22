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

    def write_layer(self, layer, name, content):
        path = self.root / "app/src/main/java/io/asv/mtgocr/ocrreader" / layer / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    def test_alias_wildcard_and_fully_qualified_bypasses_are_rejected(self):
        self.write_layer("feature", "Bad.kt",
            "  import io.asv.mtgocr.ocrreader.data.CardRepository as Repo\n"
            "import io.asv.mtgocr.ocrreader.*\n"
            "fun bad() = io.asv.mtgocr.ocrreader.OcrCaptureActivity.mBiblio\n")
        self.assertEqual(3, len(import_violations(self.root)))

    def test_java_static_import_and_model_dependency_are_rejected(self):
        self.write_layer("feature", "Bad.java",
            "import static io.asv.mtgocr.ocrreader.OcrCaptureActivity.mBiblio;\n"
            "import io.asv.mtgocr.ocrreader.model.CardInfo;\n")
        self.assertEqual(2, len(import_violations(self.root)))

    def test_domain_rejects_platform_and_designsystem_qualified_references(self):
        self.write_layer("domain", "Bad.kt",
            "fun bad(a: android.content.Context) = a\n"
            "import androidx.lifecycle.ViewModel\n"
            "import io.asv.mtgocr.ocrreader.core.designsystem.CardArtwork\n")
        self.assertEqual(3, len(import_violations(self.root)))

    def test_comments_strings_resources_and_designsystem_are_not_false_positives(self):
        self.write_layer("feature", "Good.kt",
            "package io.asv.mtgocr.ocrreader.feature.example\n"
            "// io.asv.mtgocr.ocrreader.OcrCaptureActivity\n"
            'val text = "io.asv.mtgocr.ocrreader.DataUtils"\n'
            "/* import io.asv.mtgocr.ocrreader.model.CardInfo */\n"
            "import io.asv.mtgocr.ocrreader.R\n"
            "import io.asv.mtgocr.ocrreader.core.designsystem.CardArtwork\n")
        self.assertEqual([], import_violations(self.root))

    def test_shared_persistence_is_protected_like_scanners(self):
        folder = self.root / "app/src/main/java/io/asv/mtgocr/ocrreader"
        (folder / "model").mkdir(parents=True)
        data = folder / "DataUtils.java"
        model = folder / "model/Biblio.java"
        data.write_text("data\n")
        model.write_text("biblio\n")
        self.git("add", ".")
        self.git("commit", "-qm", "shared persistence")
        data.write_text("changed\n")
        model.write_text("changed\n")
        self.assertEqual(2, len(violations(self.root, "HEAD")))


if __name__ == "__main__":
    unittest.main()
