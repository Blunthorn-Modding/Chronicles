import json
import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from bundled_audio import AudioValidationError, validate_resources


RESOURCES = Path(__file__).resolve().parents[1] / "campaigns/quiet-below/src/main/resources"
MANIFEST = "data/quiet_below/chronicle/event-sounds.json"
REGISTRY = "assets/quiet_below/sounds.json"


class BundledAudioTest(unittest.TestCase):
    def copied_resources(self):
        temporary = tempfile.TemporaryDirectory()
        root = Path(temporary.name) / "resources"
        shutil.copytree(RESOURCES, root)
        self.addCleanup(temporary.cleanup)
        return root

    def manifest(self, root):
        path = root / MANIFEST
        value = json.loads(path.read_text(encoding="utf-8"))
        return path, value

    def test_source_resources_validate(self):
        self.assertEqual(8, len(validate_resources(RESOURCES)))

    def test_unreviewed_license_and_partial_attribution_are_rejected(self):
        root = self.copied_resources()
        path, manifest = self.manifest(root)
        manifest["sounds"][0]["license"] = "CC BY-NC 4.0"
        path.write_text(json.dumps(manifest), encoding="utf-8")
        with self.assertRaisesRegex(AudioValidationError, "license differs"):
            validate_resources(root)
        root = self.copied_resources()
        credits = root / "quiet_below-sound-credits.txt"
        credits.write_text(credits.read_text(encoding="utf-8").replace("Arto Koivisto", "missing"), encoding="utf-8")
        with self.assertRaisesRegex(AudioValidationError, "source-requested attribution"):
            validate_resources(root)
        root = self.copied_resources()
        credits = root / "quiet_below-sound-credits.txt"
        credits.write_text(credits.read_text(encoding="utf-8").replace("License URL: https://creativecommons.org/licenses/by/4.0/", "License URL: missing", 1), encoding="utf-8")
        with self.assertRaisesRegex(AudioValidationError, "per-clip attribution"):
            validate_resources(root)

    def test_built_jar_layout_validates(self):
        with tempfile.TemporaryDirectory() as temporary:
            jar = Path(temporary) / "quiet-below.jar"
            with zipfile.ZipFile(jar, "w") as archive:
                for file in RESOURCES.rglob("*"):
                    if file.is_file():
                        archive.write(file, file.relative_to(RESOURCES).as_posix())
            self.assertEqual(8, len(validate_resources(jar)))

    def test_corrupt_audio_is_rejected(self):
        root = self.copied_resources()
        audio = root / "assets/quiet_below/sounds/events/sinkhole_opening.ogg"
        audio.write_bytes(b"not an ogg")
        with self.assertRaisesRegex(AudioValidationError, "SHA256 mismatch"):
            validate_resources(root)

    def test_duplicate_jar_resources_are_rejected_before_loader_order_can_differ(self):
        with tempfile.TemporaryDirectory() as temporary:
            jar = Path(temporary) / "duplicate.jar"
            import warnings
            with warnings.catch_warnings():
                warnings.simplefilter("ignore", UserWarning)
                with zipfile.ZipFile(jar, "w") as archive:
                    archive.writestr(MANIFEST, "first")
                    archive.writestr(MANIFEST, "second")
            with self.assertRaisesRegex(AudioValidationError, "duplicate resource"):
                validate_resources(jar)

    def test_non_vorbis_ogg_is_rejected_after_rehash(self):
        root = self.copied_resources()
        audio = root / "assets/quiet_below/sounds/events/sinkhole_opening.ogg"
        audio.write_bytes(b"OggS\x00" + b"\0" * 40)
        path, manifest = self.manifest(root)
        import hashlib
        manifest["sounds"][0]["sha256"] = hashlib.sha256(audio.read_bytes()).hexdigest()
        path.write_text(json.dumps(manifest), encoding="utf-8")
        with self.assertRaisesRegex(AudioValidationError, "not Ogg Vorbis"):
            validate_resources(root)

    def test_missing_audio_is_rejected(self):
        root = self.copied_resources()
        (root / "assets/quiet_below/sounds/events/sinkhole_opening.ogg").unlink()
        with self.assertRaisesRegex(AudioValidationError, "missing resource"):
            validate_resources(root)

    def test_path_escape_is_rejected(self):
        root = self.copied_resources()
        path, manifest = self.manifest(root)
        manifest["sounds"][0]["file"] = "../outside"
        path.write_text(json.dumps(manifest), encoding="utf-8")
        with self.assertRaisesRegex(AudioValidationError, "unsafe namespaced path"):
            validate_resources(root)

    def test_wrong_registry_mapping_is_rejected(self):
        root = self.copied_resources()
        path = root / REGISTRY
        registry = json.loads(path.read_text(encoding="utf-8"))
        registry["event.sculk_growth"]["sounds"][0]["name"] = "quiet_below:events/heart_destroyed"
        path.write_text(json.dumps(registry), encoding="utf-8")
        with self.assertRaisesRegex(AudioValidationError, "sounds.json reference mismatch"):
            validate_resources(root)

    def test_missing_metadata_and_duplicate_id_are_rejected(self):
        root = self.copied_resources()
        path, manifest = self.manifest(root)
        del manifest["sounds"][0]["creator"]
        path.write_text(json.dumps(manifest), encoding="utf-8")
        with self.assertRaisesRegex(AudioValidationError, "metadata keys"):
            validate_resources(root)
        root = self.copied_resources()
        path, manifest = self.manifest(root)
        manifest["sounds"][1]["id"] = manifest["sounds"][0]["id"]
        path.write_text(json.dumps(manifest), encoding="utf-8")
        with self.assertRaisesRegex(AudioValidationError, "duplicate"):
            validate_resources(root)


if __name__ == "__main__":
    unittest.main()
