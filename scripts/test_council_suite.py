import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import council_suite as suite


class CoordinatorTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.jar = self.root / 'source.jar'
        self.jar.write_bytes(b'initial')
        self.patch_reports = patch.object(suite.w, 'REPORTS', self.root)
        self.patch_reports.start()
        self.addCleanup(self.patch_reports.stop)
        self.patch_artifacts = patch.object(suite, 'artifacts', return_value=[self.jar])
        self.patch_artifacts.start()
        self.addCleanup(self.patch_artifacts.stop)

    def child(self, name, result='PASS'):
        path = self.root / (suite.SUITES[name][1] + 'new') / 'report.json'
        path.parent.mkdir()
        path.write_text(json.dumps({'result': result, 'checks': ['verified']}), encoding='utf-8')

    def execute(self, runner, selected=None):
        with contextlib.redirect_stdout(io.StringIO()):
            return suite.run(selected or ['ballots'], runner)

    def test_empty_selection_cannot_certify_success(self):
        def runner(*_):
            raise AssertionError('must not launch')
        with contextlib.redirect_stdout(io.StringIO()):
            result=suite.run([],runner)
        self.assertEqual('FAIL',result['result'])
        self.assertEqual([],result['suites'])

    def test_zero_exit_fail_report(self):
        def runner(script, output):
            self.child('ballots', 'FAIL')
            return 0
        self.assertEqual('FAIL', self.execute(runner)['result'])

    def test_zero_exit_missing_report(self):
        self.assertEqual('FAIL', self.execute(lambda *_: 0)['result'])

    def test_nonzero_pass_report(self):
        def runner(script, output):
            self.child('ballots')
            return 1
        self.assertEqual('FAIL', self.execute(runner)['result'])

    def test_stale_report_rejected(self):
        self.child('ballots')
        self.assertEqual('FAIL', self.execute(lambda *_: 0)['result'])

    def test_changed_jar_refused(self):
        def runner(script, output):
            self.child('ballots')
            self.jar.write_bytes(b'changed')
            return 0
        result = self.execute(runner)
        self.assertEqual('FAIL', result['result'])
        self.assertFalse(result['suites'][0]['artifacts_unchanged'])

    def test_serial_fail_fast_and_checks(self):
        calls = []
        def runner(script, output):
            name = next(name for name, spec in suite.SUITES.items() if spec[0] == script)
            calls.append(name)
            self.child(name, 'FAIL' if name == 'succession' else 'PASS')
            return 0
        result = self.execute(runner, ['ballots', 'succession', 'treaties'])
        self.assertEqual(['ballots', 'succession'], calls)
        self.assertEqual(['verified'], result['suites'][0]['checks'])
        self.assertEqual('FAIL', result['result'])

    def test_default_and_list_do_not_launch(self):
        with patch.object(suite, 'run', side_effect=AssertionError('launched')):
            with contextlib.redirect_stdout(io.StringIO()) as output:
                self.assertEqual(0, suite.main([]))
                self.assertEqual(0, suite.main(['--list']))
            self.assertIn('rebellion', output.getvalue())

    def test_interrupt_records_cancelled(self):
        def runner(*_):
            raise KeyboardInterrupt
        result = self.execute(runner)
        self.assertEqual('CANCELLED', result['result'])
        self.assertTrue(list(self.root.glob('council-suite-*/report.json')))


if __name__ == '__main__':
    unittest.main()
