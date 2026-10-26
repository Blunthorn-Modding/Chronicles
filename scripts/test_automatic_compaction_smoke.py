from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

from scripts import automatic_compaction_smoke as maintenance


class AutomaticMaintenanceRunnerTest(unittest.TestCase):
    def fixture(self, directory):
        base = Path(directory).resolve()
        root = base / "test-runs/20261026T000000000000Z"
        reports = base / "reports" / ("automatic-compaction-" + root.name)
        root.mkdir(parents=True)
        reports.mkdir(parents=True)
        (root / "server.properties").write_text(
            "server-ip=127.0.0.1\nserver-port=25575\nonline-mode=false\nlevel-name=world\n", encoding="utf-8")
        (root / ".ecology-auto-maintenance-fixture").write_text("owned fixture", encoding="utf-8")
        return root, reports

    def test_authenticated_public_or_other_port_world_refused_before_launch(self):
        for change in ("online-mode=true", "server-ip=0.0.0.0", "server-port=25589", "level-name=production"):
            with self.subTest(change=change), tempfile.TemporaryDirectory() as directory:
                root, reports = self.fixture(directory)
                path = root / "server.properties"
                key = change.split("=")[0]
                path.write_text("\n".join(change if line.startswith(key + "=") else line
                                           for line in path.read_text(encoding="utf-8").splitlines()), encoding="utf-8")
                with patch.object(maintenance.subprocess, "Popen") as launch:
                    with self.assertRaises(ValueError):
                        maintenance._start(Mock(), root, reports, "initial")
                    launch.assert_not_called()

    def test_missing_marker_or_unrelated_report_directory_refused(self):
        with tempfile.TemporaryDirectory() as directory:
            root, reports = self.fixture(directory)
            maintenance._guard_root(root, reports)
            with self.assertRaises(ValueError):
                maintenance._guard_root(root, reports.parent)
            (root / ".ecology-auto-maintenance-fixture").unlink()
            with self.assertRaises(ValueError):
                maintenance._guard_root(root, reports)

    def test_client_failure_requests_saving_stop_and_closes_handles(self):
        with tempfile.TemporaryDirectory() as directory:
            root, reports = self.fixture(directory)
            server, server_log, client, client_log = Mock(), Mock(), Mock(), Mock()
            client.poll.return_value = 0
            fabric = SimpleNamespace(_client=Mock(return_value=(client, client_log)),
                                     _await_clients=Mock(side_effect=RuntimeError("native client failed")),
                                     _stop=Mock())
            with patch.object(maintenance, "_start", return_value=(server, server_log)):
                with self.assertRaisesRegex(RuntimeError, "Client did not produce proof"):
                    maintenance._phase(Mock(), fabric, {}, root, reports, "initial")
            fabric._stop.assert_called_once_with(server)
            server.kill.assert_not_called()
            server_log.close.assert_called_once_with()
            client_log.close.assert_called_once_with()


if __name__ == "__main__":
    unittest.main()
