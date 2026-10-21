import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts import enchantment_smoke


class FakeProcess:
    def __init__(self, running=True):
        self.returncode = None if running else 1
        self.terminated = False
        self.killed = False

    def poll(self):
        return self.returncode

    def terminate(self):
        self.terminated = True
        self.returncode = 1

    def kill(self):
        self.killed = True
        self.returncode = 1

    def wait(self, timeout=None):
        self.returncode = 1
        return self.returncode


class FakeHandle:
    def __init__(self):
        self.closed = False

    def close(self):
        self.closed = True


class EventuallyExitsProcess(FakeProcess):
    def __init__(self):
        super().__init__(running=True)
        self.polls = 0

    def poll(self):
        self.polls += 1
        if self.polls > 1:
            self.returncode = 1
        return self.returncode


class FakeWorkspace:
    def __init__(self):
        self.source_env = {'KEEP': 'yes'}
        self.captured_env = None

    def env(self):
        return dict(self.source_env)

    def server_command(self):
        return ['server']


class EnchantmentSmokeHarnessTest(unittest.TestCase):
    def test_run_phase_cleans_client_and_server_after_client_failure(self):
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
                raise RuntimeError('client failed')

            @staticmethod
            def _stop(process):
                process.terminate()

        with tempfile.TemporaryDirectory() as directory, patch.object(enchantment_smoke, '_reserve_ports'), \
                patch.object(enchantment_smoke, '_wait_ready'), \
                patch.object(enchantment_smoke.subprocess, 'Popen', return_value=server):
            reports = Path(directory)
            with self.assertRaisesRegex(RuntimeError, 'client failed'):
                enchantment_smoke._run_phase(workspace, Fabric, {}, reports, Path(directory), 'initial')

        self.assertTrue(client.terminated)
        self.assertTrue(handle.closed)
        self.assertTrue(server.terminated)

    def test_run_phase_uses_environment_copy(self):
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

        with tempfile.TemporaryDirectory() as directory, patch.object(enchantment_smoke, '_reserve_ports'), \
                patch.object(enchantment_smoke, '_wait_ready'), \
                patch.object(enchantment_smoke.subprocess, 'Popen', side_effect=popen):
            enchantment_smoke._run_phase(workspace, Fabric, {}, Path(directory), Path(directory), 'initial')

        self.assertNotIn('CHRONICLE_CAPS_NATIVE_TRIAL', workspace.source_env)
        self.assertEqual('true', captured['CHRONICLE_CAPS_NATIVE_TRIAL'])

    def test_invalid_config_rejects_before_ready_and_preserves_exact_bytes(self):
        workspace = FakeWorkspace()
        server = FakeProcess()

        class Fabric:
            @staticmethod
            def _stop(process):
                process.terminate()

        def popen(*args, **kwargs):
            kwargs['stdout'].write('Enchantment caps unavailable: invalid config\n')
            kwargs['stdout'].flush()
            server.returncode = 1
            return server

        with tempfile.TemporaryDirectory() as directory, patch.object(enchantment_smoke, '_reserve_ports'), \
                patch.object(enchantment_smoke.subprocess, 'Popen', side_effect=popen):
            root = Path(directory) / 'fresh-root'
            root.mkdir()
            result = enchantment_smoke._run_invalid_config(workspace, Fabric, root, Path(directory) / 'reports')

        self.assertTrue(result['config_preserved'])
        self.assertIn('fresh-root', result['root'])

    def test_invalid_config_does_not_pass_while_rejection_process_is_still_running(self):
        workspace = FakeWorkspace()
        server = EventuallyExitsProcess()

        class Fabric:
            @staticmethod
            def _stop(process):
                process.terminate()

        def popen(*args, **kwargs):
            kwargs['stdout'].write('Enchantment caps unavailable: invalid config\n')
            kwargs['stdout'].flush()
            return server

        with tempfile.TemporaryDirectory() as directory, patch.object(enchantment_smoke, '_reserve_ports'), \
                patch.object(enchantment_smoke.subprocess, 'Popen', side_effect=popen):
            root = Path(directory) / 'fresh-root'
            root.mkdir()
            result = enchantment_smoke._run_invalid_config(workspace, Fabric, root, Path(directory) / 'reports')

        self.assertTrue(result['config_preserved'])
        self.assertGreaterEqual(server.polls, 2)

    def test_invalid_config_rejects_ready_server_and_preserves_exact_bytes(self):
        workspace = FakeWorkspace()
        server = FakeProcess()

        class Fabric:
            @staticmethod
            def _stop(process):
                process.terminate()

        def popen(*args, **kwargs):
            kwargs['stdout'].write('Done (1.0s)! Voice chat server started at 127.0.0.1:24455\n')
            kwargs['stdout'].flush()
            return server

        with tempfile.TemporaryDirectory() as directory, patch.object(enchantment_smoke, '_reserve_ports'), \
                patch.object(enchantment_smoke.subprocess, 'Popen', side_effect=popen):
            root = Path(directory) / 'fresh-root'
            root.mkdir()
            with self.assertRaisesRegex(RuntimeError, 'unexpectedly started'):
                enchantment_smoke._run_invalid_config(workspace, Fabric, root, Path(directory) / 'reports')
            config = root / 'config' / 'chronicle' / 'enchantment-caps.json'
            self.assertEqual(b'{"schemaVersion":1,"enabled":false,"defaultMaxLevel":255,"overrides":{},"overrides":{}}\n', config.read_bytes())
            self.assertTrue(server.terminated)


if __name__ == '__main__':
    unittest.main()
