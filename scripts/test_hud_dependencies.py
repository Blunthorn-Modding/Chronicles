"""Distribution boundaries are significant for the selected HUD licenses."""
import copy
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


if __name__ == '__main__':
    unittest.main()
