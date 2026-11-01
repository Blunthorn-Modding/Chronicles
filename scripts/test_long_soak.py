import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from unittest.mock import Mock
import subprocess
import long_soak


class LongSoakTest(unittest.TestCase):
    def test_operation_accounting_rejects_incomplete_or_misclassified_v2_counts(self):
        counts = {kind: 0 for kind in long_soak.ACTIVE_WORK | long_soak.INACTIVE_WORK}
        counts['GROWTH_ATTEMPT'] = 90
        counts['TOPOLOGY_UNKNOWN'] = 10
        unexpected = {kind: 0 for kind in counts}
        unexpected['TOPOLOGY_UNKNOWN'] = 2
        value = {'telemetryVersion': 2, 'ticks': 100, 'activeEcologyTicks': 90,
                 'unexpectedHeldTicks': 2, 'tickWorkCounts': counts, 'unexpectedTickWorkCounts': unexpected}
        self.assertTrue(long_soak.accounting_consistent(value))
        for replacement in ({'tickWorkCounts': {}}, {'activeEcologyTicks': 100},
                            {'unexpectedHeldTicks': 10}, {'telemetryVersion': 3},
                            {'tickWorkCounts': counts | {'TOPOLOGY_UNKNOWN': -1}},
                            {'unexpectedTickWorkCounts': unexpected | {'GROWTH_ATTEMPT': 1}}):
            with self.subTest(replacement=replacement):
                self.assertFalse(long_soak.accounting_consistent(value | replacement))

    def test_completed_run_handshake_timeout_is_bounded(self):
        with tempfile.TemporaryDirectory() as directory:
            process = Mock();process.poll.return_value = None
            manifest = {}
            with patch('long_soak.time.monotonic', side_effect=[0, 26]), patch('long_soak.time.sleep') as sleep:
                self.assertEqual('FAIL', long_soak.await_completed_clients({'state': 'FINISHED'},
                    Path(directory), [(process, None), (process, None)], manifest, 'PASS'))
            sleep.assert_not_called()
            self.assertFalse(manifest['clientCompletionAcknowledged'])

    def test_completed_run_waits_for_clients_even_when_performance_failed(self):
        with tempfile.TemporaryDirectory() as directory:
            reports = Path(directory)
            processes = [Mock(), Mock()]
            for process in processes:
                process.poll.return_value = None
            def acknowledge(_):
                for role, process in zip(('anchor', 'roamer'), processes):
                    long_soak.atomic_json(reports / ('client-' + role + '.json'), {'result': 'PASS'})
                    process.poll.return_value = 0
            manifest = {}
            with patch('long_soak.time.sleep', side_effect=acknowledge) as sleep:
                outcome = long_soak.await_completed_clients({'state': 'FINISHED'}, reports,
                    [(process, None) for process in processes], manifest, 'FAIL')
            sleep.assert_called_once()
            self.assertEqual('FAIL', outcome, 'client success must not erase a failed performance budget')
            self.assertTrue(manifest['clientCompletionAcknowledged'])
            self.assertEqual({'anchor', 'roamer'}, set(manifest['clientResults']))

    def test_missing_or_crashed_client_fails_successful_completed_run(self):
        with tempfile.TemporaryDirectory() as directory:
            reports = Path(directory)
            processes = [Mock(), Mock()]
            processes[0].poll.return_value = 0
            processes[1].poll.return_value = 1
            for role in ('anchor', 'roamer'):
                long_soak.atomic_json(reports / ('client-' + role + '.json'), {'result': 'PASS'})
            manifest = {}
            self.assertEqual('FAIL', long_soak.await_completed_clients({'state': 'FINISHED'},
                reports, [(p, None) for p in processes], manifest, 'PASS'))
            self.assertFalse(manifest['clientCompletionAcknowledged'])
            (reports / 'client-roamer.json').unlink()
            processes[1].poll.return_value = 0
            self.assertEqual('FAIL', long_soak.await_completed_clients({'state': 'FINISHED'},
                reports, [(p, None) for p in processes], {}, 'PASS'))

    def test_interrupted_run_does_not_wait_for_success_acknowledgements(self):
        with tempfile.TemporaryDirectory() as directory, patch('long_soak.time.sleep') as sleep:
            self.assertEqual('CANCELLED', long_soak.await_completed_clients({'state': 'INTERRUPTED'},
                Path(directory), [], {}, 'CANCELLED'))
            sleep.assert_not_called()

    def test_corrupt_acknowledgement_records_failure_without_aborting_shutdown(self):
        with tempfile.TemporaryDirectory() as directory:
            reports = Path(directory)
            (reports / 'client-anchor.json').write_text('{broken', encoding='utf-8')
            process = Mock();process.poll.return_value = 0
            manifest = {}
            self.assertEqual('FAIL', long_soak.await_completed_clients({'state': 'FINISHED'},
                reports, [(process, None), (process, None)], manifest, 'PASS'))
            self.assertFalse(manifest['clientCompletionAcknowledged'])
            self.assertIn('Cannot read', manifest['clientCompletionError'])

    def test_wrong_json_shape_cannot_abort_completion_cleanup(self):
        with tempfile.TemporaryDirectory() as directory:
            reports = Path(directory)
            process = Mock();process.poll.return_value = 0
            long_soak.atomic_json(reports / 'client-roamer.json', {'result': 'PASS'})
            for value in ([1], 'acknowledged', 1, True, None):
                with self.subTest(value=value):
                    (reports / 'client-anchor.json').write_text(json.dumps(value), encoding='utf-8')
                    manifest = {}
                    self.assertEqual('FAIL', long_soak.await_completed_clients({'state': 'FINISHED'},
                        reports, [(process, None), (process, None)], manifest, 'PASS'))
                    self.assertFalse(manifest['clientCompletionAcknowledged'])

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
