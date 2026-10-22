"""Conservative P0 gate: existing runtime sources stay byte-identical to master baseline.

New files are allowed. MainActivity.java alone is replaced in P2; future non-scanner
migrations need explicit reviewed exceptions, never wildcard scanner exemptions.
Uses Git's clean filters so Windows CRLF alone is not a source change.
"""
import subprocess
import sys
import re
from pathlib import Path

BASE = "2d8a4adb17fba50a17f26e260775e165414f4d72"
PROTECTED = ("app/src/main/", "collectorvision-native/", "liquid-glass/src/main/")
# P2: only the non-scanner launcher is replaced by Kotlin. Its XML remains frozen.
EXCEPTIONS = {"app/src/main/java/io/asv/mtgocr/ocrreader/MainActivity.java"}


def git(root, *args):
    return subprocess.check_output(["git", "-C", str(root), *args])


def violations(root, base=BASE):
    entries = git(root, "ls-tree", "-r", "-z", base).split(b"\0")
    changed = []
    for entry in entries:
        if not entry:
            continue
        metadata, raw_path = entry.split(b"\t", 1)
        path = raw_path.decode("utf-8")
        if not path.startswith(PROTECTED) or path in EXCEPTIONS:
            continue
        expected = metadata.split()[2].decode("ascii")
        target = root / path
        if not target.is_file():
            changed.append(path + " (missing)")
        else:
            actual = git(root, "hash-object", "--path=" + path, "--", str(target)).decode().strip()
            if actual != expected:
                changed.append(path + " (modified)")
    return changed


def import_violations(root):
    package = root / "app/src/main/java/io/asv/mtgocr/ocrreader"
    invalid = []
    prefix = "io.asv.mtgocr.ocrreader"
    # Inspect qualified references too: imports alone miss aliases, static Java imports
    # and calls such as io.asv...OcrCaptureActivity.mBiblio in a method body.
    references = re.compile(r"\bio\.asv\.mtgocr\.ocrreader(?:\.[\w$]+|\.\*)*")
    literals = re.compile(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\x27(?:\\.|[^\x27\\])*\x27|//[^\n]*|/\*[\s\S]*?\*/')
    for layer in ("domain", "feature"):
        for source in sorted((package / layer).rglob("*")):
            if source.suffix not in (".kt", ".java"):
                continue
            raw = source.read_text(encoding="utf-8")
            code = literals.sub(lambda match: "\n" * match.group().count("\n"), raw)
            for number, line in enumerate(code.splitlines(), 1):
                allowed = ("domain",) if layer == "domain" else ("domain", "feature", "core.designsystem", "R")
                forbidden = any(
                    not any(ref == prefix + "." + area or ref.startswith(prefix + "." + area + ".")
                            for area in allowed)
                    for ref in references.findall(line)
                )
                if layer == "domain" and re.search(r"\bandroidx?\.", line):
                    forbidden = True
                if forbidden:
                    invalid.append(str(source.relative_to(root)) + ":" + str(number) + ": " + line.strip())
    return invalid


if __name__ == "__main__":
    root = Path(__file__).resolve().parents[1]
    changed = violations(root) + import_violations(root)
    if changed:
        print("Protected runtime sources changed:\n" + "\n".join(changed))
        sys.exit(1)
    print("OK: existing app runtime, scanner modules and shared resources unchanged.")
