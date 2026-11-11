import json
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import uuid

import scenario_authoring as a


class AuthoringTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.work = Path(self.tmp.name)
        self.source = self.work / 'reviews/events/20261101T191147509015Z'
        self.source.mkdir(parents=True)
        self.reports = self.work / 'reports'
        self.reports.mkdir()
        self.patchers = [patch.object(a.w, 'WORK', self.work), patch.object(a.w, 'REPORTS', self.reports),
                         patch.object(a, 'ports'), patch.object(a.w, 'artifacts', return_value=[])]
        for p in self.patchers:
            p.start(); self.addCleanup(p.stop)
        (self.source / 'server.properties').write_text('\n'.join(f'{k}={v}' for k, v in {
            'server-ip':'127.0.0.1', 'online-mode':'true', 'white-list':'true', 'enforce-whitelist':'true',
            'enforce-secure-profile':'true', 'enable-rcon':'false', 'enable-query':'false'}.items()))
        ident = str(uuid.uuid4())
        (self.source / 'whitelist.json').write_text(json.dumps([{'uuid': ident, 'name': 'Player'}]))
        (self.source / 'ops.json').write_text(json.dumps([{'uuid': ident, 'name':'Player', 'level':4, 'bypassesPlayerLimit':False}]))
        for name in ('fabric-server-launch.jar', 'fabric-server-launcher.properties', 'server.jar'):
            (self.source / name).write_bytes(name.encode())
        (self.source / 'eula.txt').write_text('eula=true\n')
        for name in ('libraries', 'mods', 'world', 'config'):
            (self.source / name).mkdir()
            (self.source / name / ('other.jar' if name == 'mods' else 'secret')).write_bytes(b'original')
        self.artifacts = []
        for name in ('chronicle_core', 'quiet_below'):
            path = self.work / (name + '-new.jar'); path.write_bytes(name.encode()); self.artifacts.append(path)
        a.w.artifacts.return_value = self.artifacts
        self.validate = patch.object(a.w, 'validate_mod_target', side_effect=lambda p: {'id': p.name.split('-')[0]})
        self.validate.start(); self.addCleanup(self.validate.stop)
        self.profile = self.work / 'profile.json'
        self.data = {'type':'chronicle:scenario_profile', 'schema':1, 'identity':str(uuid.uuid4()),
                     'title':'Custom', 'mechanics':['quiet_below:mechanic'], 'content':{'scenarioId':'custom:scenario'}}
        self.profile.write_text(json.dumps(self.data), encoding='utf-8')

    def test_fresh_isolation_and_hashes(self):
        root = a.prepare(self.profile, self.source)
        self.assertFalse((root / 'world').exists())
        self.assertFalse((root / 'config/secret').exists())
        self.assertEqual((root / 'config/chronicle/scenario-profile.json').read_bytes(), self.profile.read_bytes())
        self.assertEqual((root / 'mods/other.jar').read_bytes(), b'original')
        self.assertEqual((self.source / 'world/secret').read_bytes(), b'original')
        meta = json.loads((root / '.chronicle-authoring.json').read_text())
        self.assertEqual(meta['mods']['other.jar'], a.w.digest(self.source / 'mods/other.jar'))
        self.assertEqual(meta['status'], 'PREPARED')

    def test_invalid_profile_and_duplicate_json(self):
        self.profile.write_text('{"type":1,"type":2}')
        with self.assertRaises(RuntimeError): a.prepare(self.profile, self.source)
        self.assertFalse((self.work / 'reviews/authoring').exists())

    def test_invalid_access_and_untrusted_path(self):
        (self.source / 'ops.json').write_text('[]')
        with self.assertRaises(RuntimeError): a.prepare(self.profile, self.source)
        with self.assertRaises(RuntimeError): a.direct_review(self.work)

    def test_reserved_ports(self):
        self.patchers[2].stop()
        for port, voice in ((25575, 24470), (25590, 24455), (0, 24470), (25590, 25590), (65536, 24470)):
            with self.subTest(port=port, voice=voice), self.assertRaises(RuntimeError):
                a.ports(port, voice)

    def test_existing_target_preserved(self):
        class FixedDate:
            @classmethod
            def now(cls, tz):
                from datetime import datetime
                return datetime(2026, 1, 1, tzinfo=tz)
        target = self.work / 'reviews/authoring/20260101T000000000000Z'
        target.mkdir(parents=True); (target / 'keep').write_text('safe')
        with patch.object(a, 'datetime', FixedDate), self.assertRaises(FileExistsError):
            a.prepare(self.profile, self.source)
        self.assertEqual((target / 'keep').read_text(), 'safe')

    def test_same_identity_export_update_and_wrong_identity_refusal(self):
        root = a.prepare(self.profile, self.source)
        meta = json.loads((root / '.chronicle-authoring.json').read_text())
        edited = dict(self.data, content=dict(self.data['content'], books=[{'title': 'Edited'}]))
        a.check_profile_identity(edited, meta)
        for changed in (dict(edited, identity=str(uuid.uuid4())),
                        dict(edited, mechanics=['other:mechanics']),
                        dict(edited, content={'scenarioId': 'other:scenario'})):
            with self.subTest(changed=changed), self.assertRaises(RuntimeError):
                a.check_profile_identity(changed, meta)

    def test_start_accepts_export_and_records_hash_without_replacing_world(self):
        root = a.prepare(self.profile, self.source)
        live = root / 'config/chronicle/scenario-profile.json'
        updated = dict(self.data, content=dict(self.data['content'], books=[{'title': 'Exported'}]))
        live.write_text(json.dumps(updated), encoding='utf-8')
        (root / 'world').mkdir(); (root / 'world/keep').write_text('existing map')
        class Process:
            pid = 123
            stdin = io.StringIO()
            stdout = iter(['Done (1s)!\n', 'Voice chat server started at 127.0.0.1:24470\n'])
            code = None
            def poll(self): return self.code
            def wait(self, timeout=None): self.code = 0; return 0
        with patch.object(a.w, 'check_stopped'), patch.object(a.w, 'server_command', return_value=['mock']), \
                patch.object(a.w, 'env', return_value={}), patch.object(a.subprocess, 'Popen', return_value=Process()), \
                patch.object(a.sys, 'stdin', io.StringIO('')):
            a.start(root)
        meta = json.loads((root / '.chronicle-authoring.json').read_text())
        self.assertEqual(meta['profileHash'], a.w.digest(live))
        self.assertEqual(meta['status'], 'STOPPED')
        self.assertEqual((root / 'world/keep').read_text(), 'existing map')
        self.assertFalse((root / '.chronicle-process.json').exists())


if __name__ == '__main__': unittest.main()
