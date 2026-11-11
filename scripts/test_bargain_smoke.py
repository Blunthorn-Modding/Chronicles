import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts import bargain_smoke


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


class FakeHud:
    @staticmethod
    def load_lock(): return ["pins"]
    @staticmethod
    def cached_jars(pins): return [Path("one.jar"), Path("two.jar")]


class BargainSmokeHarnessTest(unittest.TestCase):
    def test_run_keeps_server_for_reconnect_and_only_loads_optional_mods_there(self):
        workspace = FakeWorkspace()
        server_instances = [FakeProcess(), FakeProcess()]
        servers = list(server_instances)
        launches = []
        clients = []
        environments = []

        class Fabric:
            @staticmethod
            def _client(w, launch, root, reports, name, label, variables):
                launches.append((label, launch, variables))
                pair = (FakeProcess(), FakeHandle())
                clients.append(pair)
                return pair
            @staticmethod
            def _await_clients(*args, **kwargs): return None
            @staticmethod
            def _stop(process): process.terminate()

        with tempfile.TemporaryDirectory() as directory, \
                patch.object(bargain_smoke, "_reserve_ports"), \
                patch.object(bargain_smoke, "_wait_ready"), \
                patch.object(bargain_smoke.subprocess, "Popen", side_effect=lambda *args, **kwargs: (environments.append(kwargs["env"]) or servers.pop(0))):
            bargain_smoke._run(workspace, Fabric, FakeHud(), {"jvmArgs": ["-Xmx1G"]}, Path(directory), Path(directory))

        self.assertEqual(["bargain-initial-A", "bargain-initial-B", "bargain-reconnect-A", "bargain-restart-A", "bargain-ended-B"],
                         [item[0] for item in launches])
        self.assertTrue(all(pair[0].terminated and pair[1].closed for pair in clients))
        self.assertTrue(all(server.terminated for server in server_instances))
        self.assertEqual(["-Xmx1G"], launches[0][1]["jvmArgs"])
        self.assertIn("-Dfabric.addMods=one.jar", launches[2][1]["jvmArgs"][-1])
        self.assertEqual(["-Xmx1G"], launches[3][1]["jvmArgs"])
        self.assertNotIn("CHRONICLE_BARGAIN_NATIVE_TRIAL", workspace.source_env)
        self.assertEqual("true", environments[0]["CHRONICLE_BARGAIN_NATIVE_TRIAL"])
        self.assertEqual("true", environments[0]["CHRONICLE_FORGE_NATIVE_TRIAL"])

    def test_second_initial_client_failure_closes_first_and_stops_server(self):
        workspace = FakeWorkspace()
        first = (FakeProcess(), FakeHandle())
        server = FakeProcess()
        calls = 0

        class Fabric:
            @staticmethod
            def _client(*args):
                nonlocal calls
                calls += 1
                if calls == 1: return first
                raise RuntimeError("second client spawn failed")
            @staticmethod
            def _stop(process): process.terminate()

        with tempfile.TemporaryDirectory() as directory, \
                patch.object(bargain_smoke, "_reserve_ports"), \
                patch.object(bargain_smoke, "_wait_ready"), \
                patch.object(bargain_smoke.subprocess, "Popen", return_value=server):
            with self.assertRaisesRegex(RuntimeError, "second client spawn failed"):
                bargain_smoke._run(workspace, Fabric, FakeHud(), {}, Path(directory), Path(directory))

        self.assertTrue(first[0].terminated)
        self.assertTrue(first[1].closed)
        self.assertTrue(server.terminated)

    def test_optional_launch_does_not_mutate_base_launch(self):
        base = {"jvmArgs": ["-Xmx1G"]}
        result = bargain_smoke._optional_launch(base, FakeHud())
        self.assertEqual(["-Xmx1G"], base["jvmArgs"])
        self.assertEqual(2, len(result["jvmArgs"]))

    def test_startup_failure_stops_created_server_and_closes_log(self):
        workspace = FakeWorkspace()
        server = FakeProcess()
        handles = []
        def spawn(*args, **kwargs):
            handles.append(kwargs['stdout'])
            return server
        with tempfile.TemporaryDirectory() as directory, patch.object(bargain_smoke, '_reserve_ports'), \
                patch.object(bargain_smoke.subprocess, 'Popen', side_effect=spawn), \
                patch.object(bargain_smoke, '_wait_ready', side_effect=RuntimeError('not ready')):
            with self.assertRaisesRegex(RuntimeError, 'not ready'):
                bargain_smoke._start_server(workspace, Path(directory), Path(directory), 'failed', lambda process: process.terminate())
        self.assertTrue(server.terminated)
        self.assertTrue(handles[0].closed)


if __name__ == "__main__":
    unittest.main()
