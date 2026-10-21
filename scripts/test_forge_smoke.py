import sys
import tempfile
import types
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts import forge_smoke


class FakeProcess:
    def __init__(self, running=True):
        self.returncode = None if running else 0
        self.terminated = False

    def poll(self):
        return self.returncode

    def terminate(self):
        self.terminated = True
        self.returncode = 1

    def kill(self):
        self.returncode = 1

    def wait(self, timeout=None):
        self.returncode = 1
        return self.returncode


class FakeHandle:
    def __init__(self):
        self.closed = False

    def close(self):
        self.closed = True


class FakeWorkspace:
    def __init__(self):
        self.source_env = {'KEEP': 'yes'}
        self.captured_env = None

    def env(self):
        return dict(self.source_env)

    def server_command(self):
        return ['server']


class ForgeSmokeHarnessTest(unittest.TestCase):
    def test_run_phase_cleans_client_server_and_log_after_client_failure(self):
        workspace = FakeWorkspace()
        server = FakeProcess()
        client = FakeProcess()
        handle = FakeHandle()

        class Fabric:
            @staticmethod
            def _client(*args):
                return client, handle

            @staticmethod
            def _await_clients(*args, **kwargs):
                raise RuntimeError('Forge client failed')

            @staticmethod
            def _stop(process):
                process.terminate()

        with tempfile.TemporaryDirectory() as directory, patch.object(forge_smoke, '_reserve_ports'), \
                patch.object(forge_smoke, '_wait_ready'), patch.object(forge_smoke.subprocess, 'Popen', return_value=server):
            with self.assertRaisesRegex(RuntimeError, 'Forge client failed'):
                forge_smoke._run_phase(workspace, Fabric, {}, Path(directory), Path(directory), 'initial', ('A',))

        self.assertTrue(client.terminated)
        self.assertTrue(handle.closed)
        self.assertTrue(server.terminated)

    def test_run_phase_uses_copy_for_trial_environment(self):
        workspace = FakeWorkspace()
        server = FakeProcess(running=False)
        child = FakeProcess(running=False)
        handle = FakeHandle()
        captured = {}

        class Fabric:
            @staticmethod
            def _client(*args):
                return child, handle

            @staticmethod
            def _await_clients(*args, **kwargs):
                return None

            @staticmethod
            def _stop(process):
                process.returncode = 0

        def popen(*args, **kwargs):
            captured.update(kwargs['env'])
            return server

        with tempfile.TemporaryDirectory() as directory, patch.object(forge_smoke, '_reserve_ports'), \
                patch.object(forge_smoke, '_wait_ready'), patch.object(forge_smoke.subprocess, 'Popen', side_effect=popen):
            forge_smoke._run_phase(workspace, Fabric, {}, Path(directory), Path(directory), 'initial', ('A', 'B'))

        self.assertNotIn('CHRONICLE_FORGE_NATIVE_TRIAL', workspace.source_env)
        self.assertEqual('true', captured['CHRONICLE_FORGE_NATIVE_TRIAL'])

    def test_second_client_spawn_failure_cleans_first_client(self):
        workspace = FakeWorkspace()
        server = FakeProcess()
        first = FakeProcess()
        first_handle = FakeHandle()
        calls = 0

        class Fabric:
            @staticmethod
            def _client(*args):
                nonlocal calls
                calls += 1
                if calls == 1:
                    return first, first_handle
                raise RuntimeError('second client spawn failed')

            @staticmethod
            def _stop(process):
                process.terminate()

        with tempfile.TemporaryDirectory() as directory, patch.object(forge_smoke, '_reserve_ports'), \
                patch.object(forge_smoke, '_wait_ready'), patch.object(forge_smoke.subprocess, 'Popen', return_value=server):
            with self.assertRaisesRegex(RuntimeError, 'second client spawn failed'):
                forge_smoke._run_phase(workspace, Fabric, {}, Path(directory), Path(directory), 'initial', ('A', 'B'))

        self.assertTrue(first.terminated)
        self.assertTrue(first_handle.closed)
        self.assertTrue(server.terminated)

    def test_run_orders_two_actor_initial_before_single_actor_restart(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            repo = root / 'repo'
            launch_file = repo / 'campaigns' / 'quiet-below' / 'build' / 'smoke' / 'client-launch.json'
            launch_file.parent.mkdir(parents=True)
            launch_file.write_text('{}', encoding='utf-8')
            reports = root / 'reports'
            staged = root / 'staged'
            staged.mkdir()
            calls = []

            fake_workspace = types.SimpleNamespace(
                REPO=repo, REPORTS=reports,
                require_eula=lambda: None,
                validate_server_runtime=lambda: None,
                validate_managed_mod_names=lambda: None,
                run=lambda *args, **kwargs: None,
            )

            class Fabric:
                @staticmethod
                def _stage(workspace):
                    return staged

            def run_phase(*args):
                calls.append((args[5], args[6]))

            with patch.dict(sys.modules, {'workspace': fake_workspace, 'fabric_smoke': Fabric}), \
                    patch.object(forge_smoke, '_run_phase', side_effect=run_phase):
                forge_smoke.run()

            self.assertEqual([('initial', ('A', 'B')), ('restart', ('A',))], calls)


if __name__ == '__main__':
    unittest.main()
