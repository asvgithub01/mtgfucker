import subprocess
import tempfile
import unittest
from pathlib import Path
from check_architecture_boundary import violations


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


if __name__ == "__main__":
    unittest.main()
