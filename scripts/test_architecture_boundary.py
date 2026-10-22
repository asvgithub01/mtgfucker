import subprocess
import tempfile
import unittest
from pathlib import Path
from check_architecture_boundary import (violations, import_violations,
    preview_manifest_violations, preview_source_violations, PREVIEWS)


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


    def test_direct_effect_apis_cannot_bypass_domain_ports(self):
        apis = ["androidx.work.WorkManager", "androidx.room.Room", "androidx.datastore.core.DataStore",
                "com.google.firebase.auth.FirebaseAuth", "okhttp3.OkHttpClient", "retrofit2.Retrofit",
                "java.io.File", "java.nio.file.Files", "java.net.URL", "kotlin.io.path.writeText",
                "android.content.SharedPreferences", "android.content.ContentResolver",
                "android.database.sqlite.SQLiteDatabase", "android.app.DownloadManager",
                "android.app.job.JobScheduler"]
        for layer in ("domain", "feature"):
            for api in apis:
                with self.subTest(layer=layer, api=api):
                    self.write_layer(layer, "Direct.kt", "import " + api + " as Direct\n")
                    self.assertTrue(import_violations(self.root))
                    (self.root / "app/src/main/java/io/asv/mtgocr/ocrreader" / layer / "Direct.kt").unlink()

    def test_effect_fully_qualified_calls_and_static_imports_rejected(self):
        self.write_layer("feature", "Direct.java", "import static java.nio.file.Files.write;\n"
                         "class X { Object x = androidx.work.WorkManager.getInstance(null); }\n")
        self.assertEqual(2, len(import_violations(self.root)))

    def test_pure_utilities_ui_and_effect_names_in_comments_allowed(self):
        self.write_layer("feature", "Good.kt", "import androidx.lifecycle.ViewModel\n"
                         "import androidx.compose.runtime.Composable\nimport java.util.Locale\n"
                         '// java.io.File\nval label = "androidx.work.WorkManager"\n')
        self.assertEqual([], import_violations(self.root))

    def manifest(self, variant="debug", extra="", exported="false"):
        path = self.root / "app/src" / variant / "AndroidManifest.xml"
        path.parent.mkdir(parents=True, exist_ok=True)
        activities = "".join('<activity android:name="' + name + '" android:exported="' + exported + '"/>'
                             for name in sorted(PREVIEWS))
        path.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
                        '<application>' + activities + extra + '</application></manifest>', encoding="utf-8")
        return path

    def test_private_debug_previews_allowed(self):
        self.manifest()
        self.assertEqual([], preview_source_violations(self.root))

    def test_preview_in_release_or_flavor_rejected(self):
        for variant in ("main", "release", "paid"):
            with self.subTest(variant=variant):
                path = self.manifest(variant)
                self.assertEqual(4, len(preview_manifest_violations(path, False)))

    def test_exported_missing_duplicate_or_filtered_debug_preview_rejected(self):
        path = self.manifest(exported="true")
        self.assertEqual(4, len(preview_manifest_violations(path, True)))
        path = self.manifest(extra='<activity android:name=".PhotoPreviewActivity" android:exported="false"/>')
        self.assertTrue(preview_manifest_violations(path, True))
        path.write_text('<manifest/>', encoding="utf-8")
        self.assertEqual(4, len(preview_manifest_violations(path, True)))
        path = self.manifest()
        text = path.read_text(encoding="utf-8").replace('android:exported="false"/>',
                  'android:exported="false"><intent-filter/></activity>', 1)
        path.write_text(text, encoding="utf-8")
        self.assertTrue(preview_manifest_violations(path, True))

    def test_alias_to_preview_is_rejected_even_with_unrelated_name(self):
        path = self.manifest(extra='<activity-alias android:name=".Shortcut" '
            'android:targetActivity=".PhotoPreviewActivity" android:exported="false"/>')
        self.assertTrue(preview_manifest_violations(path, True))
        self.assertEqual(5, len(preview_manifest_violations(path, False)))

    def test_missing_or_malformed_manifest_fails_closed(self):
        path = self.root / "missing.xml"
        self.assertTrue(preview_manifest_violations(path, False))
        path.write_text("<broken", encoding="utf-8")
        self.assertTrue(preview_manifest_violations(path, False))

    def test_release_without_preview_is_allowed(self):
        path = self.root / "release.xml"
        path.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
                        '<application><activity android:name=".OcrCaptureActivity"/></application></manifest>', encoding="utf-8")
        self.assertEqual([], preview_manifest_violations(path, False))


    def test_source_scan_finds_preview_in_nested_variant(self):
        self.manifest()
        self.manifest("paid/release")
        self.assertEqual(4, len(preview_source_violations(self.root)))

    def test_background_jobs_and_scanner_module_remain_frozen(self):
        paths = ["app/src/main/java/io/asv/mtgocr/ocrreader/ReliableReviewWorker.kt",
                 "collectorvision-native/src/main/java/EdScanVerificationWorker.kt"]
        for name in paths:
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("original\n", encoding="utf-8")
        self.git("add", ".")
        self.git("commit", "-qm", "protected jobs")
        for name in paths:
            (self.root / name).write_text("changed\n", encoding="utf-8")
        self.assertEqual(2, len(violations(self.root, "HEAD")))


    def test_extracted_domain_is_checked_outside_app(self):
        path = self.root / "core/domain/src/main/kotlin/example/Contract.kt"
        path.parent.mkdir(parents=True)
        path.write_text("import android.content.Context\nimport io.asv.mtgocr.ocrreader.data.CardRepository\n", encoding="utf-8")
        self.assertEqual(2, len(import_violations(self.root)))
        path.write_text("import java.util.Locale\nimport kotlinx.coroutines.flow.Flow\n", encoding="utf-8")
        self.assertEqual([], import_violations(self.root))

    def test_extracted_designsystem_cannot_depend_on_app_domain_or_features(self):
        path = self.root / "core/designsystem/src/main/kotlin/example/Component.kt"
        path.parent.mkdir(parents=True)
        path.write_text("import io.asv.mtgocr.ocrreader.CardImageCache\n"
                        "import io.asv.mtgocr.ocrreader.R\n"
                        "import io.asv.mtgocr.ocrreader.domain.photos.PhotoRecord\n"
                        "import io.asv.mtgocr.ocrreader.feature.photos.PhotoScreen\n", encoding="utf-8")
        self.assertEqual(4, len(import_violations(self.root)))
        path.write_text("import androidx.compose.runtime.Composable\n"
                        "import io.asv.mtgocr.ocrreader.core.designsystem.MtgTheme\n", encoding="utf-8")
        self.assertEqual([], import_violations(self.root))


    def test_library_manifest_cannot_register_a_preview(self):
        self.manifest()
        target = self.root / "core/designsystem/src/main/AndroidManifest.xml"
        target.parent.mkdir(parents=True)
        target.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
                          '<application><activity android:name=".PhotoPreviewActivity"/></application></manifest>', encoding="utf-8")
        self.assertEqual(1, len(preview_source_violations(self.root)))


if __name__ == "__main__":
    unittest.main()
