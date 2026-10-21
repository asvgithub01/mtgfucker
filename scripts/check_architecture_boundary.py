"""Conservative P0 gate: existing runtime sources stay byte-identical to master baseline.

New files are allowed. No exemptions yet; future non-scanner migrations must add
explicit reviewed exceptions, never a wildcard exemption for scanner dependencies.
Uses Git's clean filters so Windows CRLF alone is not a source change.
"""
import subprocess
import sys
from pathlib import Path

BASE = "2d8a4adb17fba50a17f26e260775e165414f4d72"
PROTECTED = ("app/src/main/", "collectorvision-native/", "liquid-glass/src/main/")


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
        if not path.startswith(PROTECTED):
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


if __name__ == "__main__":
    root = Path(__file__).resolve().parents[1]
    changed = violations(root)
    if changed:
        print("Protected runtime sources changed:\n" + "\n".join(changed))
        sys.exit(1)
    print("OK: existing app runtime, scanner modules and shared resources unchanged.")
