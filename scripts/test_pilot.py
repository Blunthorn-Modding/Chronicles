"""Focused safety tests for the isolated pilot runtime helper."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import zipfile
import sys
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import pilot


VERSION = {
    'id': '1.21.10', 'protocol_version': 773,
    'pack_version': {'resource_major': 69, 'resource_minor': 0, 'data_major': 88, 'data_minor': 0},
}


class PilotTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.work = Path(self.temporary.name)
        self.pilots = self.work / 'pilots'
        self.root = self.pilots / '20261019T123456123456Z'
        self.root.mkdir(parents=True)
        with zipfile.ZipFile(self.root / 'server.jar', 'w') as zipped:
            zipped.writestr('version.json', json.dumps(VERSION))
        mods = self.root / 'mods'
        mods.mkdir()
        sources = []
        for mod_id in ('chronicle_core', 'quiet_below'):
            jar = mods / f'{mod_id}-0.1.0-dev.3.jar'
            with zipfile.ZipFile(jar, 'w') as zipped:
                zipped.writestr('fabric.mod.json', json.dumps({
                    'id': mod_id, 'version': '0.1.0-dev.3',
                    'depends': {'minecraft': VERSION['id']},
                }))
            sources.append({
                'path': f'C:/built/{jar.name}', 'sha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
                'version': '0.1.0-dev.3',
            })
        pilot._write_properties(self.root)
        pilot._write_datapack(self.root, VERSION)
        manifest = {
            'status': 'prepared', 'minecraft': VERSION['id'],
            'pack_data_major': 88, 'pack_data_minor': 0, 'mod_sources': sources,
        }
        (self.root / '.chronicle-pilot.json').write_text(json.dumps(manifest), encoding='utf-8')
        self.report = self.work / 'pilot-latest.json'
        self.report.write_text(json.dumps({
            'status': 'prepared', 'minecraft': VERSION['id'], 'pilot': str(self.root),
        }), encoding='utf-8')
        self.original_pilots, self.original_report = pilot.PILOTS, pilot.REPORT
        pilot.PILOTS, pilot.REPORT = self.pilots, self.report

    def tearDown(self):
        pilot.PILOTS, pilot.REPORT = self.original_pilots, self.original_report
        self.temporary.cleanup()

    def _replace_property(self, key, value):
        path = self.root / 'server.properties'
        content = path.read_text(encoding='utf-8')
        path.write_text('\n'.join(
            value if line.startswith(key + '=') else line for line in content.splitlines()
        ) + '\n', encoding='utf-8')

    def test_resolve_accepts_server_escaped_properties(self):
        self._replace_property('level-type', r'level-type=minecraft\:flat')
        self._replace_property('level-name', r'level-name=wo\u0072ld')
        self.assertEqual(pilot.resolve_latest(), self.root.resolve())
        self.assertEqual(pilot._unescape_property(r'colon\:equals\=slash\\'), 'colon:equals=slash\\')

    def test_unsafe_port_bind_and_auth_edits_are_rejected(self):
        for key, line in [
            ('server-port', 'server-port=255850'),
            ('server-ip', 'server-ip=0.0.0.0'),
            ('online-mode', 'online-mode=false'),
        ]:
            with self.subTest(key=key):
                self._replace_property(key, line)
                with self.assertRaisesRegex(RuntimeError, 'properties'):
                    pilot.resolve_latest()
                pilot._write_properties(self.root)

    def test_report_path_must_be_a_direct_pilot_child(self):
        outside = self.work / 'outside'
        outside.mkdir()
        self.report.write_text(json.dumps({
            'status': 'prepared', 'minecraft': VERSION['id'], 'pilot': str(outside),
        }), encoding='utf-8')
        with self.assertRaisesRegex(RuntimeError, 'directly contained'):
            pilot.resolve_latest()

    def test_site_promotion_never_replaces_an_existing_world(self):
        level = self.root / 'world' / 'level.dat'
        level.write_bytes(b'played world evidence')
        with self.assertRaisesRegex(RuntimeError, 'no existing world'):
            pilot.promote_tested()
        self.assertEqual(level.read_bytes(), b'played world evidence')

    def test_failed_rehearsal_cannot_be_promoted(self):
        (self.work / 'pilot-test-latest.json').write_text(json.dumps({'result': 'FAIL'}), encoding='utf-8')
        with mock.patch.object(pilot.w, 'REPORTS', self.work):
            with self.assertRaisesRegex(RuntimeError, 'must pass'):
                pilot.promote_tested()
        self.assertFalse((self.root / 'world' / 'level.dat').exists())


if __name__ == '__main__':
    unittest.main()
