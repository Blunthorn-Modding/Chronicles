import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

from scripts import eradication_recovery_smoke as recovery


class RecoveryProcessSafetyTest(unittest.TestCase):
    def fixture(self, directory):
        base = Path(directory).resolve()
        root = base / "test-runs/20261025T000000000000Z"
        reports = base / "reports" / ("eradication-recovery-" + root.name)
        root.mkdir(parents=True)
        reports.mkdir(parents=True)
        (root / "server.properties").write_text(
            "server-ip=127.0.0.1\nserver-port=25575\nonline-mode=false\nlevel-name=world\n", encoding="utf-8")
        (root / ".eradication-recovery-fixture").write_text("owned test fixture", encoding="utf-8")
        job = "8f169ef1-11c6-4d51-9f46-4f87942938ea"
        saved = {"result": "PASS", "phase": "PAUSED", "job": job, "total": 4098,
                 "savedCompleted": 65, "chunksFlushed": True}
        ready = {"result": "PASS", "phase": "PAUSED", "job": job, "total": 4098,
                 "savedCompleted": 65, "unsavedCompleted": 129, "maxDelta": 4}
        for name, value in (("save-point.json", saved), ("crash-ready.json", ready)):
            (reports / name).write_text(json.dumps(value), encoding="utf-8")
        return root, reports, ready

    @staticmethod
    def server():
        server = Mock()
        server.poll.return_value = None
        server.returncode = None
        server.kill.side_effect = lambda: setattr(server, "returncode", -9)
        return server

    def test_only_verified_partial_checkpoint_permits_intentional_termination(self):
        with tempfile.TemporaryDirectory() as directory:
            root, reports, ready = self.fixture(directory)
            server = self.server()
            self.assertEqual(ready, recovery._force_crash(server, root, reports))
            server.kill.assert_called_once_with()
            server.wait.assert_called_once_with(timeout=20)

    def test_authenticated_or_wrong_port_runtime_is_never_terminated(self):
        for change in ("online-mode=true", "server-port=25589"):
            with self.subTest(change=change), tempfile.TemporaryDirectory() as directory:
                root, reports, _ = self.fixture(directory)
                path = root / "server.properties"
                content = path.read_text(encoding="utf-8")
                key = change.split("=")[0]
                path.write_text("\n".join(change if line.startswith(key + "=") else line
                                           for line in content.splitlines()), encoding="utf-8")
                server = self.server()
                with self.assertRaises(ValueError):
                    recovery._force_crash(server, root, reports)
                server.kill.assert_not_called()

    def test_missing_fixture_marker_refuses_termination(self):
        with tempfile.TemporaryDirectory() as directory:
            root, reports, _ = self.fixture(directory)
            (root / ".eradication-recovery-fixture").unlink()
            server = self.server()
            with self.assertRaises(ValueError):
                recovery._force_crash(server, root, reports)
            server.kill.assert_not_called()

    def test_missing_flush_or_nonpartial_or_mismatched_progress_never_terminates(self):
        for change in ({"unsavedCompleted": 65}, {"unsavedCompleted": 4098}, {"maxDelta": 5},
                       {"savedCompleted": 64}, {"phase": "ACTIVE"},
                       {"job": "614d0300-4fd6-4b36-bc31-5cd57d32329c"}):
            with self.subTest(change=change), tempfile.TemporaryDirectory() as directory:
                root, reports, ready = self.fixture(directory)
                ready.update(change)
                (reports / "crash-ready.json").write_text(json.dumps(ready), encoding="utf-8")
                server = self.server()
                with self.assertRaises(ValueError):
                    recovery._force_crash(server, root, reports)
                server.kill.assert_not_called()
        with tempfile.TemporaryDirectory() as directory:
            root, reports, _ = self.fixture(directory)
            path = reports / "save-point.json"
            saved = json.loads(path.read_text(encoding="utf-8"))
            saved["chunksFlushed"] = False
            path.write_text(json.dumps(saved), encoding="utf-8")
            server = self.server()
            with self.assertRaises(ValueError):
                recovery._force_crash(server, root, reports)
            server.kill.assert_not_called()

    def test_already_exited_server_is_not_counted_as_intentional_crash(self):
        with tempfile.TemporaryDirectory() as directory:
            root, reports, _ = self.fixture(directory)
            server = self.server()
            server.poll.return_value = 1
            with self.assertRaises(RuntimeError):
                recovery._force_crash(server, root, reports)
            server.kill.assert_not_called()

    def test_client_failure_requests_saving_stop_without_forced_crash(self):
        with tempfile.TemporaryDirectory() as directory:
            root, reports, _ = self.fixture(directory)
            server, server_log, client, client_log = Mock(), Mock(), Mock(), Mock()
            client.poll.return_value = 0
            fabric = SimpleNamespace(_client=Mock(return_value=(client, client_log)),
                                     _await_clients=Mock(side_effect=RuntimeError("native client failed")),
                                     _stop=Mock())
            with patch.object(recovery, "_start", return_value=(server, server_log)), \
                    patch.object(recovery, "_force_crash") as forced:
                with self.assertRaisesRegex(RuntimeError, "native client failed"):
                    recovery._phase(Mock(), fabric, {}, root, reports, "initial")
                forced.assert_not_called()
            fabric._stop.assert_called_once_with(server)
            server_log.close.assert_called_once_with()
            client_log.close.assert_called_once_with()


if __name__ == "__main__":
    unittest.main()
