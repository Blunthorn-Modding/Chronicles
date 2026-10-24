import tempfile
import unittest
import zipfile
from pathlib import Path

from scripts.heart_aircut_smoke import _digest, _extract_fixture


class AirCutArchiveTest(unittest.TestCase):
    def archive(self, directory: Path, entries: dict[str, bytes]) -> Path:
        path = directory / "fixture.zip"
        with zipfile.ZipFile(path, "w") as bundle:
            for name, content in entries.items():
                bundle.writestr(name, content)
        return path

    def test_extracts_only_allowlisted_fixture_and_leaves_archive_unchanged(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "run"; root.mkdir()
            archive = self.archive(Path(directory), {
                "world/chronicle/campaign-state.json": b"{}",
                "config/chronicle/heart.json": b"{}",
                "mods/quiet_below.jar": b"jar",
                "ops.json": b"credential-like",
                "whitelist.json": b"credential-like",
            })
            before = _digest(archive)
            self.assertEqual(before, _extract_fixture(archive, root))
            self.assertEqual(b"{}", (root / "world/chronicle/campaign-state.json").read_bytes())
            self.assertEqual(b"{}", (root / "config/chronicle/heart.json").read_bytes())
            self.assertFalse((root / "mods").exists())
            self.assertFalse((root / "ops.json").exists())
            self.assertEqual(before, _digest(archive))

    def test_rejects_traversal_without_writing_outside_root(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory); root = base / "run"; root.mkdir()
            archive = self.archive(base, {"world/../escape.txt": b"no"})
            before = _digest(archive)
            with self.assertRaisesRegex(RuntimeError, "unsafe fixture member"):
                _extract_fixture(archive, root)
            self.assertFalse((base / "escape.txt").exists())
            self.assertEqual(before, _digest(archive))

    def test_refuses_overwrite_of_existing_allowed_file(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory); root = base / "run"; target = root / "world/chronicle/campaign-state.json"
            target.parent.mkdir(parents=True); target.write_bytes(b"keep")
            archive = self.archive(base, {"world/chronicle/campaign-state.json": b"replace"})
            before = _digest(archive)
            with self.assertRaises(FileExistsError):
                _extract_fixture(archive, root)
            self.assertEqual(b"keep", target.read_bytes())
            self.assertEqual(before, _digest(archive))

    def test_rejects_symlink_member_before_extracting_it(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory); root = base / "run"; root.mkdir(); archive = base / "fixture.zip"
            link = zipfile.ZipInfo("world/chronicle/link")
            link.external_attr = 0o120777 << 16
            with zipfile.ZipFile(archive, "w") as bundle:
                bundle.writestr(link, b"../../outside")
            with self.assertRaisesRegex(RuntimeError, "unsafe fixture member"):
                _extract_fixture(archive, root)
            self.assertFalse((root / "world").exists())


if __name__ == "__main__":
    unittest.main()
