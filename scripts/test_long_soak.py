import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import subprocess
import long_soak


class LongSoakTest(unittest.TestCase):
    def test_elapsed_without_real_ticks_is_not_a_performance_pass(self):
        value = {"state": "FINISHED", "elapsedSeconds": 10800, "ticks": 0,
                 "callbackP99Nanos": 1, "ecologyP99Nanos": 1, "missedEligibleTicks": 0}
        result = long_soak.evaluate(value, 10800)
        self.assertTrue(result["completed"])
        self.assertEqual("FAIL", result["result"])

    def test_completion_and_performance_are_separate(self):
        value = {"state": "FINISHED", "elapsedSeconds": 10800, "ticks": 216000,
                 "callbackP99Nanos": 4_000_000, "ecologyP99Nanos": 1_000_000, "missedEligibleTicks": 0,
                 "activeEcologyTicks": 200000, "unexpectedHeldTicks": 100,
                 "spreadAttemptsGrowth": 500, "initialOwned": 80000, "maxOwned": 81000}
        self.assertEqual("PASS", long_soak.evaluate(value, 10800)["result"])
        for patch in ({"elapsedSeconds": 100}, {"state": "FAILED"}, {"ticks": 100000},
                      {"callbackP99Nanos": 60_000_000}, {"ecologyP99Nanos": 30_000_000},
                      {"missedEligibleTicks": 1}, {"failure": "journal held"},
                      {"activeEcologyTicks": 0}, {"unexpectedHeldTicks": 20000},
                      {"spreadAttemptsGrowth": 0}, {"maxOwned": 80000}):
            with self.subTest(patch=patch):
                self.assertEqual("FAIL", long_soak.evaluate(value | patch, 10800)["result"])

    def test_recording_failure_is_reported_without_losing_terminal_report(self):
        with tempfile.TemporaryDirectory() as directory:
            reports = Path(directory);manifest = {}
            self.assertFalse(long_soak.summarize_recording(reports / "java.exe", reports, manifest))
            self.assertIn("missing", manifest["jfrError"])
            (reports / "server.jfr").write_bytes(b"fixture")
            with patch("long_soak.subprocess.run", side_effect=subprocess.TimeoutExpired("jfr", 60)):
                self.assertFalse(long_soak.summarize_recording(reports / "java.exe", reports, manifest))
            self.assertIn("timed out", manifest["jfrError"])

    def test_normal_generation_preserves_network_isolation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / "server.properties"
            path.write_text("server-ip=127.0.0.1\nserver-port=25575\nonline-mode=false\nlevel-type=minecraft:flat\ngenerate-structures=false\n", encoding="utf-8")
            long_soak.configure_world(root)
            text = path.read_text(encoding="utf-8")
            for line in ("server-ip=127.0.0.1", "online-mode=false", "level-type=minecraft:normal",
                         "generate-structures=true", "difficulty=normal", "view-distance=3"):
                self.assertIn(line, text)
            self.assertNotIn("minecraft:flat", text)

    def test_status_publication_replaces_whole_json(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "status.json"
            long_soak.atomic_json(path, {"result": "PREPARING"})
            long_soak.atomic_json(path, {"result": "RUNNING", "ticks": 200})
            self.assertEqual({"result": "RUNNING", "ticks": 200}, json.loads(path.read_text()))
            self.assertFalse(path.with_suffix(".json.pending").exists())

    def test_client_snapshot_survives_project_output_changes(self):
        with tempfile.TemporaryDirectory(prefix="soak snapshot ") as directory:
            base = Path(directory);repo = base / "repo";root = base / "run"
            classes = repo / "core/build/classes";classes.mkdir(parents=True)
            (classes / "Driver.class").write_bytes(b"original")
            config = repo / "launch";config.mkdir()
            for filename in ("launch.cfg", "remapClasspath.txt", "log4j.xml"):
                (config / filename).write_text(str(classes), encoding="utf-8")
            launch = {"classpath": [str(classes)], "jvmArgs": ["-Dfabric.dli.config=" + str(config / "launch.cfg").replace(" ", "@@0020")]}
            copied = long_soak.snapshot_launch(launch, repo, root)
            (classes / "Driver.class").write_bytes(b"changed")
            self.assertEqual(b"original", (Path(copied["classpath"][0]) / "Driver.class").read_bytes())
            self.assertNotIn(str(classes), (root / "client-runtime/config/launch.cfg").read_text())
            self.assertEqual([str(classes)], launch["classpath"])


if __name__ == "__main__":
    unittest.main()
