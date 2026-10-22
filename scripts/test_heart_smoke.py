import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from scripts import heart_smoke


class Process:
    def __init__(self): self.returncode = None; self.terminated = False
    def poll(self): return self.returncode
    def terminate(self): self.terminated = True; self.returncode = 1
    def kill(self): self.returncode = 1
    def wait(self, timeout=None): self.returncode = 1
class Handle:
    def __init__(self): self.closed = False
    def close(self): self.closed = True
class Workspace:
    def __init__(self): self.source = {"KEEP": "yes"}
    def env(self): return dict(self.source)
    def server_command(self): return ["server"]


class HeartHarnessTest(unittest.TestCase):
    def test_two_clients_then_restart_are_isolated_and_cleaned(self):
        workspace = Workspace(); servers = [Process(), Process()]; saved = list(servers); clients = []; envs = []; labels = []
        class Fabric:
            @staticmethod
            def _client(w, launch, root, reports, name, label, variables):
                labels.append(label); pair = (Process(), Handle()); clients.append(pair); return pair
            @staticmethod
            def _await_clients(*args, **kwargs): return None
            @staticmethod
            def _stop(server): server.terminate()
        with tempfile.TemporaryDirectory() as directory, patch.object(heart_smoke, "_reserve_ports"), patch.object(heart_smoke, "_wait_ready"), patch.object(heart_smoke.subprocess, "Popen", side_effect=lambda *a, **kw: (envs.append(kw["env"]) or servers.pop(0))):
            heart_smoke._run(workspace, Fabric, {}, Path(directory), Path(directory))
        self.assertEqual(["heart-initial-A", "heart-initial-B", "heart-restart-A", "heart-restart-B"], labels)
        self.assertTrue(all(pair[0].terminated and pair[1].closed for pair in clients))
        self.assertTrue(all(server.terminated for server in saved))
        self.assertEqual("true", envs[0]["CHRONICLE_HEART_NATIVE_TRIAL"])
        self.assertNotIn("CHRONICLE_HEART_NATIVE_TRIAL", workspace.source)

    def test_second_client_failure_stops_the_disposable_server(self):
        workspace = Workspace(); server = Process(); first = (Process(), Handle()); calls = 0
        class Fabric:
            @staticmethod
            def _client(*args):
                nonlocal calls; calls += 1
                if calls == 1: return first
                raise RuntimeError("second client failed")
            @staticmethod
            def _stop(value): value.terminate()
        with tempfile.TemporaryDirectory() as directory, patch.object(heart_smoke, "_reserve_ports"), patch.object(heart_smoke, "_wait_ready"), patch.object(heart_smoke.subprocess, "Popen", return_value=server):
            with self.assertRaisesRegex(RuntimeError, "second client failed"):
                heart_smoke._run(workspace, Fabric, {}, Path(directory), Path(directory))
        self.assertTrue(first[0].terminated and first[1].closed and server.terminated)


if __name__ == "__main__": unittest.main()
