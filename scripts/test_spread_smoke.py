import json
import sys
import tempfile
import types
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts import spread_smoke


class FakeProcess:
    def __init__(self, running=True):
        self.returncode = None if running else 0
        self.terminated = False
    def poll(self): return self.returncode
    def terminate(self): self.terminated = True; self.returncode = 1
    def kill(self): self.returncode = 1
    def wait(self, timeout=None): self.returncode = 1; return self.returncode


class FakeHandle:
    def __init__(self): self.closed = False
    def close(self): self.closed = True


class FakeWorkspace:
    def __init__(self): self.source_env = {"KEEP": "yes"}
    def env(self): return dict(self.source_env)
    def server_command(self): return ["server"]


class SpreadSmokeHarnessTest(unittest.TestCase):
    def test_phase_uses_private_trial_environment_and_cleans_all_processes(self):
        workspace, server, client, handle = FakeWorkspace(), FakeProcess(), FakeProcess(), FakeHandle()
        captured = {}
        class Fabric:
            @staticmethod
            def _client(*args): return client, handle
            @staticmethod
            def _await_clients(*args, **kwargs): return None
            @staticmethod
            def _stop(process): process.terminate()
        with tempfile.TemporaryDirectory() as directory, patch.object(spread_smoke, "_reserve_ports"), \
                patch.object(spread_smoke, "_wait_ready"), \
                patch.object(spread_smoke.subprocess, "Popen", side_effect=lambda *args, **kwargs: (captured.update(kwargs["env"]) or server)):
            spread_smoke._run_phase(workspace, Fabric, {}, Path(directory), Path(directory), "initial")
        self.assertEqual("true", captured["CHRONICLE_SPREAD_NATIVE_TRIAL"])
        self.assertNotIn("CHRONICLE_SPREAD_NATIVE_TRIAL", workspace.source_env)
        self.assertTrue(client.terminated and handle.closed and server.terminated)

    def test_phase_cleans_server_when_client_fails(self):
        workspace, server, client, handle = FakeWorkspace(), FakeProcess(), FakeProcess(), FakeHandle()
        class Fabric:
            @staticmethod
            def _client(*args): return client, handle
            @staticmethod
            def _await_clients(*args, **kwargs): raise RuntimeError("spread client failed")
            @staticmethod
            def _stop(process): process.terminate()
        with tempfile.TemporaryDirectory() as directory, patch.object(spread_smoke, "_reserve_ports"), \
                patch.object(spread_smoke, "_wait_ready"), \
                patch.object(spread_smoke.subprocess, "Popen", return_value=server):
            with self.assertRaisesRegex(RuntimeError, "spread client failed"):
                spread_smoke._run_phase(workspace, Fabric, {}, Path(directory), Path(directory), "initial")
        self.assertTrue(client.terminated and handle.closed and server.terminated)

    def test_run_orders_initial_before_restart_and_retains_the_same_world(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); repo = root / "repo"; reports = root / "reports"; staged = root / "staged"; staged.mkdir()
            launch = repo / "campaigns/quiet-below/build/smoke/client-launch.json"; launch.parent.mkdir(parents=True); launch.write_text("{}", encoding="utf-8")
            calls = []
            workspace = types.SimpleNamespace(REPO=repo, REPORTS=reports, require_eula=lambda: None,
                                              validate_server_runtime=lambda: None, validate_managed_mod_names=lambda: None,
                                              run=lambda *args, **kwargs: None)
            class Fabric:
                @staticmethod
                def _stage(value): return staged
            def phase(*args):
                staged_root, phase_name = args[3], args[5]
                calls.append((staged_root, phase_name))
                if phase_name == "initial":
                    source = staged_root / "world" / "quiet-below" / "spread-measurement.json"
                    source.parent.mkdir(parents=True)
                    source.write_text('{"result":"PASS","samples":[{"label":"small_fixed"}]}', encoding="utf-8")
            with patch.dict(sys.modules, {"workspace": workspace, "fabric_smoke": Fabric}), \
                    patch.object(spread_smoke, "_run_phase", side_effect=phase):
                spread_smoke.run()
            self.assertEqual([(staged, "initial"), (staged, "restart")], calls)
            saved = json.loads((reports / "spread-smoke-staged" / "spread-measurement.json").read_text(encoding="utf-8"))
            report = json.loads((reports / "spread-smoke-staged" / "report.json").read_text(encoding="utf-8"))
            self.assertEqual(saved, report["measurement"])


if __name__ == "__main__":
    unittest.main()
