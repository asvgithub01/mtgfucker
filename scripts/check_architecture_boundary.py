"""Conservative P0 gate: existing runtime sources stay identical to master apart from the explicit identifier-only rename.

New files are allowed. MainActivity.java alone is replaced in P2; future non-scanner
migrations need explicit reviewed exceptions, never wildcard scanner exemptions.
Uses Git's clean filters so Windows CRLF alone is not a source change.
"""
import subprocess
import sys
import re
import hashlib
from vision_rename_contract import renamed_source, renamed_path
import argparse
import xml.etree.ElementTree as ET
from pathlib import Path

BASE = "2d8a4adb17fba50a17f26e260775e165414f4d72"
PROTECTED = ("app/src/main/", "collectorvision-native/", "liquid-glass/src/main/")
# P2: only the non-scanner launcher is replaced by Kotlin.
EXCEPTIONS = {"app/src/main/java/io/asv/mtgocr/ocrreader/MainActivity.java"}

# Explicit deletion-only cleanup: replaced launcher layout and its exclusive decoration.
# No general resource exemption; shared spinner_background/strings/artwork stay protected.
RETIRED_LAUNCHER_RESOURCES = {
    "app/src/main/res/layout/activity_main.xml",
    "app/src/main/res/drawable/bg_launch_panel.xml",
}


# Direct project edges are deliberately small. app remains the composition root.
MODULE_EDGES = {
    "core/domain": set(),
    "core/designsystem": set(),
    "data/readers": {":core:domain"},
    **{"feature/" + name: {":core:domain", ":core:designsystem"}
       for name in ("catalog", "photos", "services", "launcher", "library", "carddetail")},
}


def module_graph_violations(root):
    errors = []
    for folder, expected in MODULE_EDGES.items():
        build = root / folder / "build.gradle"
        if not build.is_file():
            errors.append(str(build) + ": missing module build file")
            continue
        code = re.sub(r"//[^\n]*|/\*[\s\S]*?\*/", "", build.read_text(encoding="utf-8"))
        actual = set(re.findall(r"\bproject\s*\(\s*['\"](:[^'\"]+)['\"]\s*\)", code))
        if actual != expected:
            errors.append(folder + ": expected project edges " + str(sorted(expected)) +
                          ", got " + str(sorted(actual)))
    return errors


def git(root, *args):
    return subprocess.check_output(["git", "-C", str(root), *args])


def violations(root, base=BASE):
    entries = git(root, "ls-tree", "-r", "-z", base).split(b"\0")
    changed = [path + " (retired resource reintroduced)"
               for path in sorted(RETIRED_LAUNCHER_RESOURCES) if (root / path).exists()]
    for entry in entries:
        if not entry:
            continue
        metadata, raw_path = entry.split(b"\t", 1)
        path = raw_path.decode("utf-8")
        if not path.startswith(PROTECTED) or path in EXCEPTIONS or path in RETIRED_LAUNCHER_RESOURCES:
            continue
        expected = metadata.split()[2].decode("ascii")
        target = root / renamed_path(path)
        if not target.is_file():
            changed.append(path + " (missing)")
        else:
            actual = git(root, "hash-object", "--path=" + path, "--", str(target)).decode().strip()
            if actual != expected and path.endswith((".kt", ".java")):
                original = git(root, "show", base + ":" + path)
                renamed = renamed_source(original.decode("utf-8")).encode("utf-8")
                expected = hashlib.sha1(b"blob " + str(len(renamed)).encode() + b"\0" + renamed).hexdigest()
            if actual != expected:
                changed.append(path + " (modified)")
    return changed


# Domain/feature must use ports, not bypass adapters through third-party/platform APIs.
# This is intentionally a lexical guard, not a Kotlin/Java semantic analyser.
EFFECT_APIS = re.compile(
    r"\b(?:androidx\.(?:work|room|datastore)|com\.google\.firebase|"
    r"okhttp3|retrofit2|java\.(?:io|nio\.file|net)|kotlin\.io|"
    r"android\.(?:database|app\.(?:DownloadManager|job)|content\.(?:SharedPreferences|ContentResolver)))\b"
)


def import_violations(root):
    package = root / "app/src/main/java/io/asv/mtgocr/ocrreader"
    invalid = []
    prefix = "io.asv.mtgocr.ocrreader"
    # Inspect qualified references too: imports alone miss aliases, static Java imports
    # and calls such as io.asv...OcrCaptureActivity.mBiblio in a method body.
    references = re.compile(r"\bio\.asv\.mtgocr\.ocrreader(?:\.[\w$]+|\.\*)*")
    literals = re.compile(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\x27(?:\\.|[^\x27\\])*\x27|//[^\n]*|/\*[\s\S]*?\*/')
    # Inspect both extracted modules and legacy app locations during migration.
    roots = [("domain", package / "domain"), ("feature", package / "feature"),
             ("domain", root / "core/domain/src/main"),
             ("designsystem", root / "core/designsystem/src/main"),
             ("readers", root / "data/readers/src/main")]
    roots += [("feature:" + folder.name, folder / "src/main")
              for folder in sorted((root / "feature").glob("*")) if folder.is_dir()]
    for layer, folder in roots:
        for source in sorted(folder.rglob("*")):
            if source.suffix not in (".kt", ".java"):
                continue
            raw = source.read_text(encoding="utf-8")
            code = literals.sub(lambda match: "\n" * match.group().count("\n"), raw)
            for number, line in enumerate(code.splitlines(), 1):
                allowed = (("domain", "core.designsystem", "feature." + layer.split(":")[1])
                           if layer.startswith("feature:") else
                           {"domain": ("domain",), "designsystem": ("core.designsystem",),
                            "readers": ("domain", "data.catalog", "data.legacy", "data.library", "data.services"),
                            "feature": ("domain", "feature", "core.designsystem", "R")}[layer])
                forbidden = any(
                    not any(ref == prefix + "." + area or ref.startswith(prefix + "." + area + ".")
                            for area in allowed)
                    for ref in references.findall(line)
                )
                if layer in ("domain", "readers") and re.search(r"\bandroidx?\.", line):
                    forbidden = True
                effects = EFFECT_APIS.findall(line)
                if any(effect != "java.io" or layer != "readers" for effect in effects):
                    forbidden = True
                if layer == "readers" and re.search(
                    r"\b(?:FileOutputStream|FileWriter|ObjectOutputStream|RandomAccessFile|"
                    r"writeText|writeBytes|outputStream|writer|delete|deleteRecursively|"
                    r"renameTo|mkdirs?|createNewFile|setLastModified)\b", line
                ):
                    forbidden = True
                if forbidden:
                    invalid.append(str(source.relative_to(root)) + ":" + str(number) + ": " + line.strip())
    return invalid


ANDROID = "{http://schemas.android.com/apk/res/android}"
PACKAGE = "io.asv.mtgocr.ocrreader"
PREVIEWS = {PACKAGE + "." + name for name in (
    "BiblioShellActivity", "SetCatalogPreviewActivity", "PhotoPreviewActivity", "ServicesPreviewActivity")}


def component_name(name):
    if name.startswith("."):
        return PACKAGE + name
    return PACKAGE + "." + name if "." not in name else name


def preview_manifest_violations(path, debug):
    """Check a source or merged manifest, including aliases to preview activities."""
    try:
        manifest = ET.parse(path).getroot()
    except (ET.ParseError, OSError) as error:
        return [str(path) + ": cannot read manifest: " + str(error)]
    errors, seen = [], []
    for node in manifest.iter():
        name = component_name(node.get(ANDROID + "name", ""))
        target = component_name(node.get(ANDROID + "targetActivity", ""))
        if name not in PREVIEWS and target not in PREVIEWS:
            continue
        seen.append(name)
        if not debug:
            errors.append(str(path) + ": preview present outside debug: " + name)
        elif (node.tag != "activity" or name not in PREVIEWS or
              node.get(ANDROID + "exported") != "false" or node.find("intent-filter") is not None):
            errors.append(str(path) + ": preview must be a private activity without intent filters: " + name)
    if debug:
        for name in sorted(PREVIEWS):
            if seen.count(name) != 1:
                errors.append(str(path) + ": expected exactly one private preview: " + name)
    return errors


def preview_source_violations(root):
    source = root / "app/src"
    debug = source / "debug/AndroidManifest.xml"
    errors = preview_manifest_violations(debug, True)
    manifest_roots = [source, root / "core/designsystem/src"]
    manifest_roots += [folder / "src" for folder in (root / "feature").glob("*") if folder.is_dir()]
    for manifest_root in manifest_roots:
        for path in sorted(manifest_root.rglob("AndroidManifest.xml")):
            if path != debug:
                errors += preview_manifest_violations(path, False)
    return errors


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--merged-debug", type=Path, help="Also validate generated debug manifest")
    parser.add_argument("--merged-release", type=Path, help="Also validate generated release manifest")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    changed = violations(root) + import_violations(root) + preview_source_violations(root) + module_graph_violations(root)
    if args.merged_debug:
        changed += preview_manifest_violations(args.merged_debug, True)
    if args.merged_release:
        changed += preview_manifest_violations(args.merged_release, False)
    if changed:
        print("Architecture boundary violations:\n" + "\n".join(changed))
        sys.exit(1)
    print("OK: protected runtime matches baseline plus approved identifier rename and exact launcher resource retirement; boundaries verified.")
