import subprocess
import tempfile
import unittest
from pathlib import Path
from vision_rename_contract import renamed_source, renamed_path
from check_architecture_boundary import (violations, import_violations,
    preview_manifest_violations, preview_source_violations, PREVIEWS,
    module_graph_violations, MODULE_EDGES, RETIRED_LAUNCHER_RESOURCES, RETIRED_UNUSED_DRAWABLES, retire_launcher_strings, retire_unused_palette)


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

    def test_retired_launcher_resources_allow_only_deletion(self):
        for name in RETIRED_LAUNCHER_RESOURCES:
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("<resources/>")
        self.git("add", ".")
        self.git("commit", "-qm", "old launcher resources")
        self.assertEqual(2, len(violations(self.root, "HEAD")))
        for name in RETIRED_LAUNCHER_RESOURCES:
            (self.root / name).unlink()
        self.assertEqual([], violations(self.root, "HEAD"))
        self.file.write_text("scanner changed")
        self.assertEqual(1, len(violations(self.root, "HEAD")))

    def test_retirement_does_not_allow_shared_resource_removal(self):
        path = self.root / "app/src/main/res/drawable/spinner_background.xml"
        path.parent.mkdir(parents=True)
        path.write_text("<shape/>")
        self.git("add", ".")
        self.git("commit", "-qm", "shared resource")
        path.unlink()
        self.assertEqual(1, len(violations(self.root, "HEAD")))

    def test_unused_drawables_cannot_be_reintroduced(self):
        for name in RETIRED_UNUSED_DRAWABLES:
            with self.subTest(name=name):
                path = self.root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(b"obsolete resource")
                self.assertEqual([name + " (retired resource reintroduced)"],
                                 violations(self.root, "HEAD"))
                path.unlink()
                self.assertEqual([], violations(self.root, "HEAD"))

    def test_palette_retirement_cannot_change_scanner_colors(self):
        path = self.root / "app/src/main/res/values/colors.xml"
        path.parent.mkdir(parents=True)
        original = (b'<resources>\n    <color name="mtg_ink">#171B19</color>\n'
                    b'    <color name="scan_total_complete">#43A047</color>\n</resources>\n')
        path.write_bytes(original)
        self.git("add", ".")
        self.git("commit", "-qm", "palette")
        cleaned = retire_unused_palette(original)
        self.assertEqual(b'<resources>\n    <color name="scan_total_complete">#43A047</color>\n</resources>\n', cleaned)
        path.write_bytes(cleaned)
        self.assertEqual([], violations(self.root, "HEAD"))
        path.write_bytes(cleaned.replace(b"#43A047", b"#FFFFFF"))
        self.assertEqual(1, len(violations(self.root, "HEAD")))
        path.write_bytes(original)
        self.assertEqual(1, len(violations(self.root, "HEAD")))
        path.unlink()
        self.assertEqual(1, len(violations(self.root, "HEAD")))

    def test_string_retirement_preserves_every_other_byte(self):
        original = (b'<resources>\r\n'
                    b'  <string name="mode_new_Deck">Old</string>\r\n'
                    b'  <string name="mode_edit_Decks">Old</string>\r\n'
                    b'  <string name="mode_biblio">Keep</string>\r\n</resources>')
        self.assertEqual(b'<resources>\r\n  <string name="mode_biblio">Keep</string>\r\n</resources>',
                         retire_launcher_strings(original))

    def test_shared_strings_only_allow_exact_label_retirement(self):
        path = self.root / "app/src/main/res/values/strings.xml"
        path.parent.mkdir(parents=True)
        original = ('<resources>\n'
                    '<string name="mode_new_Deck">Old</string>\n'
                    '<string name="mode_edit_Decks">Old</string>\n'
                    '<string name="mode_biblio">Keep</string>\n</resources>\n').encode()
        path.write_bytes(original)
        self.git("add", ".")
        self.git("commit", "-qm", "launcher labels")
        self.assertEqual(1, len(violations(self.root, "HEAD")))
        cleaned = retire_launcher_strings(original)
        path.write_bytes(cleaned)
        self.assertEqual([], violations(self.root, "HEAD"))
        path.write_bytes(cleaned.replace(b"Keep", b"Changed"))
        self.assertEqual(1, len(violations(self.root, "HEAD")))
        path.unlink()
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
            target = self.root / renamed_path(name)
            target.parent.mkdir(parents=True, exist_ok=True)
            (self.root / name).rename(target) if target != self.root / name else None
            target.write_text("changed\n", encoding="utf-8")
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


    def test_camera_rename_preserves_literals_comments_and_worker_names(self):
        before = ('val engine = NativeCollectorVisionEngine.prepare(context)\n'
                  'val path = "collectorvision-native/version"\n'
                  'val url = "https://hanclinto.github.io/CollectorVision/assets/model"\n'
                  '// CollectorVisionAssets provenance\n'
                  'class EdScanVerificationWorker : CoroutineWorker()\n')
        after = renamed_source(before)
        self.assertEqual(before.replace('NativeCollectorVisionEngine', 'NativeCameraVisionEngine'), after)

    def test_identifier_rename_does_not_allow_behavioral_or_storage_changes(self):
        old = self.root / 'collectorvision-native/src/main/CollectorVisionAssets.kt'
        old.parent.mkdir(parents=True)
        old.write_text('object CollectorVisionAssets { val path = "collectorvision-native/v1" }\n', encoding='utf-8')
        self.git('add', '.'); self.git('commit', '-qm', 'assets')
        new = self.root / renamed_path(old.relative_to(self.root).as_posix())
        new.parent.mkdir(parents=True)
        old.rename(new)
        new.write_text(renamed_source(new.read_text(encoding='utf-8')), encoding='utf-8')
        self.assertEqual([], violations(self.root, 'HEAD'))
        new.write_text(new.read_text(encoding='utf-8').replace('collectorvision-native/v1', 'cameraVision/v1'), encoding='utf-8')
        self.assertEqual(1, len(violations(self.root, 'HEAD')))


    def test_extracted_features_cannot_access_app_data_or_other_features(self):
        path = self.root / "feature/photos/src/main/kotlin/Screen.kt"
        path.parent.mkdir(parents=True)
        path.write_text("import io.asv.mtgocr.ocrreader.R\n"
                        "import io.asv.mtgocr.ocrreader.data.legacy.PhotoPathPolicy\n"
                        "import io.asv.mtgocr.ocrreader.feature.services.ServicesScreen\n"
                        "import androidx.work.WorkManager\n", encoding="utf-8")
        self.assertEqual(4, len(import_violations(self.root)))
        path.write_text("import io.asv.mtgocr.ocrreader.feature.photos.R\n"
                        "import io.asv.mtgocr.ocrreader.domain.photos.PhotoRecord\n"
                        "import io.asv.mtgocr.ocrreader.core.designsystem.R as CommonR\n", encoding="utf-8")
        self.assertEqual([], import_violations(self.root))

    def test_extracted_readers_reject_writers_android_and_legacy_models(self):
        path = self.root / "data/readers/src/main/kotlin/Reader.kt"
        path.parent.mkdir(parents=True)
        path.write_text("import java.io.File\n"
                        "import io.asv.mtgocr.ocrreader.domain.photos.PhotoRecord\n", encoding="utf-8")
        self.assertEqual([], import_violations(self.root))
        path.write_text("import android.content.Context\n"
                        "import io.asv.mtgocr.ocrreader.model.Biblio\n"
                        "import java.io.FileOutputStream\n"
                        "fun write(file: File) = file.writeText(value)\n"
                        "import java.net.URL\n", encoding="utf-8")
        self.assertEqual(5, len(import_violations(self.root)))

    def test_feature_manifests_cannot_register_preview_even_in_debug(self):
        self.manifest()
        path = self.root / "feature/photos/src/debug/AndroidManifest.xml"
        path.parent.mkdir(parents=True)
        path.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
                        '<application><activity android:name=".PhotoPreviewActivity" '
                        'android:exported="false"/></application></manifest>', encoding="utf-8")
        self.assertEqual(1, len(preview_source_violations(self.root)))

    def write_module_graph(self):
        for folder, edges in MODULE_EDGES.items():
            path = self.root / folder / "build.gradle"
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("\n".join("implementation project('" + edge + "')" for edge in edges), encoding="utf-8")

    def test_module_graph_has_no_reverse_or_feature_data_edges(self):
        self.write_module_graph()
        self.assertEqual([], module_graph_violations(self.root))
        for target in (":app", ":cameraVision", ":data:readers", ":feature:catalog"):
            self.write_module_graph()
            path = self.root / "feature/photos/build.gradle"
            with path.open("a", encoding="utf-8") as out:
                out.write("\nimplementation project('" + target + "')")
            self.assertEqual(1, len(module_graph_violations(self.root)))

    def test_missing_module_fails_closed(self):
        self.write_module_graph()
        (self.root / "data/readers/build.gradle").unlink()
        self.assertEqual(1, len(module_graph_violations(self.root)))

    def test_launcher_library_detail_have_no_sibling_or_legacy_imports(self):
        for name in ("launcher", "library", "carddetail"):
            with self.subTest(name=name):
                path = self.root / ("feature/" + name + "/src/main/kotlin/Screen.kt")
                path.parent.mkdir(parents=True)
                path.write_text("import io.asv.mtgocr.ocrreader.CardImageCache\n"
                                "import io.asv.mtgocr.ocrreader.R\n"
                                "import io.asv.mtgocr.ocrreader.feature.catalog.R\n", encoding="utf-8")
                self.assertEqual(3, len(import_violations(self.root)))
                path.write_text("import io.asv.mtgocr.ocrreader.domain.collection.CollectionRow\n"
                                "import androidx.compose.runtime.Composable\n", encoding="utf-8")
                self.assertEqual([], import_violations(self.root))

    def test_read_orchestration_ports_cannot_smuggle_android_or_serialized_models(self):
        path = self.root / "data/readers/src/main/kotlin/Ports.kt"
        path.parent.mkdir(parents=True)
        path.write_text("package io.asv.mtgocr.ocrreader.data.library\n"
                        "import io.asv.mtgocr.ocrreader.data.services.ServicesReadSource\n"
                        "import io.asv.mtgocr.ocrreader.domain.library.DeckSummaryDefinition\n", encoding="utf-8")
        self.assertEqual([], import_violations(self.root))
        with path.open("a", encoding="utf-8") as out:
            out.write("import android.content.Context\n"
                      "import io.asv.mtgocr.ocrreader.model.DeckCatalog\n"
                      "import io.asv.mtgocr.ocrreader.DataUtils\n")
        self.assertEqual(3, len(import_violations(self.root)))


if __name__ == "__main__":
    unittest.main()
