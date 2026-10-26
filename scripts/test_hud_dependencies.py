"""Distribution boundaries are significant for the selected HUD licenses."""
import copy
import io
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import hud_dependencies as hud


class HudDependenciesTests(unittest.TestCase):
    def test_lock_rejects_wrong_game_missing_dependency_and_unofficial_origin(self):
        original = json.loads(hud.LOCK_PATH.read_text())
        variants = []
        v = copy.deepcopy(original); v['minecraft'] = '1.21.11'; variants.append(v)
        v = copy.deepcopy(original); v['mods'].pop(); variants.append(v)
        v = copy.deepcopy(original); v['mods'][0]['url'] = 'https://mirror.example/mod.jar'; variants.append(v)
        v = copy.deepcopy(original); v['mods'][0]['sha512'] = '0'; variants.append(v)
        v = copy.deepcopy(original); v['mods'][0]['filename'] = '../vendor.jar'; variants.append(v)
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'lock.json'
            for data in variants:
                with self.subTest(data=data):
                    path.write_text(json.dumps(data))
                    with self.assertRaises(ValueError):
                        hud.load_lock(path)

    def test_pack_references_vendors_and_rejects_embedded_or_renamed_copies(self):
        mods = hud.load_lock()
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            artifacts = []
            for identifier in ('chronicle_core', 'quiet_below'):
                jar = root / (identifier + '.jar')
                with zipfile.ZipFile(jar, 'w') as archive:
                    archive.writestr('fabric.mod.json', json.dumps({'id': identifier, 'version': 'test',
                                      'depends': {'minecraft': hud.w.LOCK['minecraft']}}))
                artifacts.append(jar)
            with patch.object(hud.w, 'WORK', root), patch.object(hud.w, 'artifacts', return_value=artifacts):
                target = hud.pack()
                with zipfile.ZipFile(target) as archive:
                    index = json.loads(archive.read('modrinth.index.json'))
                    refs = [e for e in index['files'] if e['env']['server'] == 'unsupported']
                    self.assertEqual(refs, hud.index_entries(mods))
                    notice = archive.read('client-overrides/Chronicle-THIRD-PARTY.txt').decode()
                    for mod in mods:
                        self.assertIn(mod['license_url'], notice)
                for unexpected in ('client-overrides/mods/fancymenu.jar',
                                   'overrides/renamed.class', 'client-overrides/assets/vendor.png'):
                    with self.subTest(path=unexpected):
                        hud.pack()
                        with zipfile.ZipFile(target, 'a') as archive:
                            archive.writestr(unexpected, b'forbidden vendor bytes')
                        with self.assertRaises(RuntimeError):
                            hud.verify_pack(target, mods)

    def test_own_mod_cannot_hide_vendor_code_or_nested_jars(self):
        with tempfile.TemporaryDirectory() as temporary:
            jar = Path(temporary) / 'chronicle_core.jar'
            for path in ('de/keksuccino/fancymenu/Copied.class', 'META-INF/jars/vendor.jar',
                         'assets/spiffyhud/copied.png'):
                with self.subTest(path=path):
                    with zipfile.ZipFile(jar, 'w') as archive:
                        archive.writestr('fabric.mod.json', json.dumps({'id': 'chronicle_core', 'version': 'test',
                                         'depends': {'minecraft': hud.w.LOCK['minecraft']}}))
                        archive.writestr(path, b'forbidden vendor bytes')
                    with self.assertRaises(RuntimeError):
                        hud.verify_owned_jar(jar)

    def test_only_pinned_core_library_with_full_license_is_allowed(self):
        lock = copy.deepcopy(hud.bundled_library_lock())
        license_bytes = b'full approved test license'
        lock['licenseSha256'] = hashlib.sha256(license_bytes).hexdigest()
        payload = io.BytesIO()
        with zipfile.ZipFile(payload, 'w') as library:
            library.writestr('org/snakeyaml/engine/Approved.class', b'approved upstream bytes')
            library.writestr('fabric.mod.json', json.dumps({'schemaVersion': 1, 'id': lock['modId'],
                'version': lock['version'], 'name': 'snakeyaml-engine', 'custom': {'fabric-loom:generated': True}}))
        approved = payload.getvalue()
        with zipfile.ZipFile(io.BytesIO(approved)) as library:
            lock['payloadSha256'] = hud._library_payload(library)
        with tempfile.TemporaryDirectory() as temporary, patch.object(hud, 'bundled_library_lock', return_value=lock):
            jar = Path(temporary) / 'core.jar'
            def write(identifier='chronicle_core', data=approved, license_data=license_bytes):
                with zipfile.ZipFile(jar, 'w') as archive:
                    archive.writestr('fabric.mod.json', json.dumps({'id': identifier, 'version': 'test',
                        'depends': {'minecraft': hud.w.LOCK['minecraft']}, 'jars': [{'file': lock['nestedPath']}]}))
                    archive.writestr(lock['nestedPath'], data)
                    if license_data is not None:
                        archive.writestr(lock['licensePath'], license_data)
            write()
            hud.verify_owned_jar(jar)
            for bad_license in (None, b'abbreviated or different license'):
                write(license_data=bad_license)
                with self.assertRaises(RuntimeError):
                    hud.verify_owned_jar(jar)
            write(identifier='quiet_below')
            with self.assertRaises(RuntimeError):
                hud.verify_owned_jar(jar)
            altered = io.BytesIO(approved)
            with zipfile.ZipFile(altered, 'a') as library:
                library.writestr('extra/vendor/Hidden.class', b'unapproved bytes')
            write(data=altered.getvalue())
            with self.assertRaises(RuntimeError):
                hud.verify_owned_jar(jar)

    def test_substituted_library_lock_cannot_authorize_another_payload(self):
        approved = hud.bundled_library_lock()
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'library.json'
            path.write_text(json.dumps(approved), encoding='utf-8')
            self.assertEqual(hud.bundled_library_lock(path), approved)
            for key, value in [('coordinate', 'other:vendor:1'), ('version', '9.9.9'),
                               ('nestedPath', 'META-INF/jars/vendor.jar'), ('modId', 'vendor'),
                               ('payloadSha256', '0' * 64), ('licenseSha256', '0' * 64)]:
                changed = dict(approved)
                changed[key] = value
                path.write_text(json.dumps(changed), encoding='utf-8')
                with self.subTest(key=key), self.assertRaises(ValueError):
                    hud.bundled_library_lock(path)

    def test_nested_library_pin_matches_build_version(self):
        lock = hud.bundled_library_lock()
        properties = dict(line.split('=', 1) for line in (hud.w.REPO / 'gradle.properties').read_text().splitlines() if '=' in line)
        self.assertEqual(lock['version'], properties['snakeyaml_engine_version'])
        self.assertEqual(lock['nestedPath'], 'META-INF/jars/snakeyaml-engine-' + lock['version'] + '.jar')
        license_bytes = (hud.w.REPO / 'core/src/main/resources' / lock['licensePath']).read_bytes()
        self.assertEqual(hashlib.sha256(license_bytes).hexdigest(), lock['licenseSha256'])


if __name__ == '__main__':
    unittest.main()
