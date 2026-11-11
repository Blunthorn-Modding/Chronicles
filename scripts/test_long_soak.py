import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from unittest.mock import Mock
import subprocess
import long_soak


class FakeClock:
    def __init__(self):
        self.value = 0.0

    def __call__(self):
        return self.value

    def sleep(self, seconds):
        self.value += seconds


class FakeServer:
    def __init__(self, clock, stop_after=None, returncode=None):
        self.clock = clock
        self.stop_after = stop_after
        self.returncode = returncode
        self.pid = 4242
        self.stop_requested = False
        self.kill_calls = 0
        self.writes = []
        self.stdin = self

    def write(self, value):
        self.writes.append(value)
        self.stop_requested = True

    def flush(self):
        pass

    def poll(self):
        if self.returncode is None and self.stop_requested and self.stop_after is not None and self.clock() >= self.stop_after:
            self.returncode = 0
        return self.returncode

    def kill(self):
        self.kill_calls += 1
        self.returncode = -9

    def wait(self, timeout):
        return self.returncode


class LongSoakTest(unittest.TestCase):
    def test_jcmd_filename_argument_keeps_quotes_for_workspace_with_spaces(self):
        with tempfile.TemporaryDirectory() as directory:
            reports = Path(directory) / "workspace with spaces"
            reports.mkdir()
            commands = []
            def command_runner(args, **_kwargs):
                commands.append(args)
                Path(args[-1].removeprefix('filename="').removesuffix('"')).write_bytes(b"JFR")
                return subprocess.CompletedProcess(args, 0)
            diagnostics = []
            long_soak.capture_shutdown_diagnostic(Path(directory) / "java.exe", 4242, reports,
                "server-pre-stop.jfr", "JFR.dump", diagnostics, command_runner)
            expected = str(reports / "server-pre-stop.jfr").replace("\\", "/")
            self.assertEqual('filename="' + expected + '"', commands[0][-1])
            self.assertTrue(diagnostics[0]["captured"])

    def test_jcmd_zero_exit_without_jfr_file_is_not_a_capture(self):
        with tempfile.TemporaryDirectory() as directory:
            diagnostics = []
            long_soak.capture_shutdown_diagnostic(Path(directory) / "java.exe", 4242, Path(directory),
                "server-pre-stop.jfr", "JFR.dump", diagnostics,
                lambda args, **_kwargs: subprocess.CompletedProcess(args, 0))
            self.assertFalse(diagnostics[0]["captured"])
            self.assertIn("missing or empty", diagnostics[0]["error"])

    def test_checkpoint_failure_does_not_abort_owned_cleanup(self):
        with tempfile.TemporaryDirectory() as directory:
            clock = FakeClock();server = FakeServer(clock, stop_after=1)
            manifest = {}
            def checkpoint(*_args, **_kwargs):
                raise OSError("status volume unavailable")
            self.assertTrue(long_soak.stop_owned_server(server, Path(directory) / "java.exe", Path(directory), manifest,
                checkpoint, clock, clock.sleep, lambda args, **_kwargs: subprocess.CompletedProcess(args, 0)))
            self.assertTrue(manifest["cleanServerStop"])
            self.assertEqual(["status volume unavailable"], manifest["shutdownProgressErrors"])

    def test_slow_drain_under_three_hundred_seconds_is_clean(self):
        with tempfile.TemporaryDirectory() as directory:
            clock = FakeClock();server = FakeServer(clock, stop_after=95)
            diagnostics = []
            def command_runner(args, **kwargs):
                diagnostics.append(args[2])
                return subprocess.CompletedProcess(args, 0)
            progress = []
            manifest = {"outcome": "PASS"}
            self.assertTrue(long_soak.stop_owned_server(server, Path(directory) / "java.exe", Path(directory), manifest,
                lambda state, **extra: progress.append((state, extra)), clock, clock.sleep, command_runner))
            self.assertEqual(["JFR.dump"], diagnostics)
            self.assertEqual(["stop\n"], server.writes)
            self.assertEqual(0, server.kill_calls)
            self.assertTrue(manifest["cleanServerStop"])
            self.assertGreaterEqual(manifest["shutdown"]["elapsedSeconds"], 95)
            self.assertTrue(progress)

    def test_timeout_captures_diagnostics_before_killing_owned_server(self):
        with tempfile.TemporaryDirectory() as directory:
            clock = FakeClock();server = FakeServer(clock)
            actions = []
            def command_runner(args, **kwargs):
                actions.append(args[2])
                return subprocess.CompletedProcess(args, 0)
            def checkpoint(*_args, **_kwargs):
                pass
            manifest = {}
            self.assertFalse(long_soak.stop_owned_server(server, Path(directory) / "java.exe", Path(directory), manifest,
                checkpoint, clock, clock.sleep, command_runner))
            self.assertEqual(["JFR.dump", "JFR.dump", "Thread.print"], actions)
            self.assertEqual(1, server.kill_calls)
            self.assertEqual("killed-after-timeout", manifest["shutdown"]["state"])
            self.assertFalse(manifest["cleanServerStop"])

    def test_jcmd_failure_does_not_prevent_clean_stop(self):
        with tempfile.TemporaryDirectory() as directory:
            clock = FakeClock();server = FakeServer(clock, stop_after=1)
            def failing_runner(*_args, **_kwargs):
                raise OSError("jcmd unavailable")
            manifest = {}
            self.assertTrue(long_soak.stop_owned_server(server, Path(directory) / "java.exe", Path(directory), manifest,
                lambda *_args, **_kwargs: None, clock, clock.sleep, failing_runner))
            self.assertTrue(manifest["cleanServerStop"])
            self.assertFalse(manifest["shutdownDiagnostics"][0]["captured"])
            self.assertIn("jcmd unavailable", manifest["shutdownDiagnostics"][0]["error"])

    def test_early_nonzero_server_exit_is_not_clean(self):
        with tempfile.TemporaryDirectory() as directory:
            clock = FakeClock();server = FakeServer(clock, returncode=7)
            manifest = {}
            self.assertFalse(long_soak.stop_owned_server(server, Path(directory) / "java.exe", Path(directory), manifest,
                lambda *_args, **_kwargs: None, clock, clock.sleep))
            self.assertFalse(manifest["cleanServerStop"])
            self.assertIn("code 7", manifest["shutdownError"])
            self.assertEqual("FAIL", long_soak.finalize_outcome("PASS", False, True, True))

    def test_final_outcome_agrees_with_post_cleanup_verdict(self):
        self.assertEqual("PASS", long_soak.finalize_outcome("PASS", True, True, True))
        self.assertEqual("FAIL", long_soak.finalize_outcome("PASS", True, True, False))
        self.assertEqual("FAIL", long_soak.finalize_outcome("FAIL", True, True, True))
        self.assertEqual("CANCELLED", long_soak.finalize_outcome("CANCELLED", True, True, False))

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
