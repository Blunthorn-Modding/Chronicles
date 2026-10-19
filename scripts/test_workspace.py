"""Offline regression tests for install and world-safety boundaries."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location('workspace', Path(__file__).with_name('workspace.py'))
workspace = importlib.util.module_from_spec(spec)
spec.loader.exec_module(workspace)


class WorkspaceTests(unittest.TestCase):
    def test_deploy_archives_previous_custom_jars_and_preserves_unrelated_mods(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            server = root / 'server'
            mods = server / 'mods'
            mods.mkdir(parents=True)
            (mods / 'unrelated.jar').write_bytes(b'keep me')
            built = []
            for mod_id in ('chronicle_core', 'quiet_below'):
                for version, folder in [('old', mods), ('new', root)]:
                    jar = folder / f'{mod_id}-{version}.jar'
                    with zipfile.ZipFile(jar, 'w') as zipped:
                        zipped.writestr('fabric.mod.json', json.dumps({
                            'id': mod_id, 'version': version,
                            'depends': {'minecraft': workspace.LOCK['minecraft']}}))
                    if version == 'new':
                        built.append(jar)
            with patch.object(workspace, 'SERVER', server), patch.object(workspace, 'WORK', root), \
                    patch.object(workspace, 'artifacts', return_value=built), patch.object(workspace, 'pack'):
                workspace.deploy()
            self.assertEqual((mods / 'unrelated.jar').read_bytes(), b'keep me')
            self.assertFalse(list(mods.glob('*-old.jar')))
            self.assertEqual(len(list(mods.glob('*-new.jar'))), 2)
            archives = list((root / 'backups').glob('mods-*.zip'))
            self.assertEqual(len(archives), 1)
            with zipfile.ZipFile(archives[0]) as zipped:
                self.assertIsNone(zipped.testzip())
                self.assertEqual(set(zipped.namelist()), {'chronicle_core-old.jar', 'quiet_below-old.jar'})

    def test_download_origins_and_transport(self):
        for url in ['http://cdn.modrinth.com/a', 'https://cdn.modrinth.com.attacker.example/a', 'https://user@cdn.modrinth.com/a', 'https://localhost/a']:
            with self.subTest(url=url), self.assertRaises(RuntimeError):
                workspace.check_url(url)
        workspace.check_url('https://cdn.modrinth.com/data/a.jar')

    def test_archive_cannot_escape_destination(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            archive = root / 'malicious.zip'
            with zipfile.ZipFile(archive, 'w') as zipped:
                zipped.writestr('../outside.txt', 'bad')
            with self.assertRaises(RuntimeError):
                workspace.extract(archive, root / 'install')
            self.assertFalse((root / 'outside.txt').exists())

    def test_setup_preserves_existing_configuration(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'server.properties'
            path.write_text('custom config')
            workspace.write_initial(path, 'default config')
            self.assertEqual(path.read_text(), 'custom config')

    def test_eula_requires_an_actual_property(self):
        with tempfile.TemporaryDirectory() as temp, patch.object(workspace, 'SERVER', Path(temp)):
            path = Path(temp) / 'eula.txt'
            for content in ['eula=false\n', '# eula=true\n', 'eula=true-malformed\n']:
                path.write_text(content)
                with self.assertRaises(RuntimeError):
                    workspace.require_eula()
            workspace.require_eula(True)
            workspace.require_eula()

    def test_running_marker_blocks_backup_and_deploy(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp)
            (path / '.chronicle-process.json').write_text('{"pid":1}')
            with self.assertRaises(RuntimeError):
                workspace.check_stopped(path)

    def test_level_name_cannot_escape_workspace(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp)
            (path / 'server.properties').write_text('level-name=../other-world')
            with self.assertRaises(RuntimeError):
                workspace.check_stopped(path)

    def test_development_version_is_a_deployable_artifact(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / 'gradle.properties').write_text('mod_version=0.1.0-dev\n')
            expected = []
            for directory, prefix in [('core', 'chronicle_core'), ('campaigns/quiet-below', 'quiet_below')]:
                folder = root / directory / 'build' / 'libs'
                folder.mkdir(parents=True)
                target = folder / (prefix + '-0.1.0-dev.jar')
                target.touch()
                (folder / (prefix + '-0.1.0-dev-sources.jar')).touch()
                expected.append(target)
            with patch.object(workspace, 'REPO', root):
                self.assertEqual(workspace.artifacts(), expected)

    def test_setup_refuses_to_reuse_another_minecraft_runtime(self):
        with tempfile.TemporaryDirectory() as temp:
            server = Path(temp)
            with zipfile.ZipFile(server / 'server.jar', 'w') as zipped:
                zipped.writestr('version.json', json.dumps({'id': '1.21.1', 'protocol_version': 767}))
            with patch.object(workspace, 'SERVER', server), self.assertRaisesRegex(RuntimeError, 'Installed server is 1.21.1'):
                workspace.validate_server_runtime()

    def test_stale_mod_cannot_be_deployed_to_new_game_version(self):
        with tempfile.TemporaryDirectory() as temp:
            jar = Path(temp) / 'chronicle_core-0.1.0-dev.jar'
            with zipfile.ZipFile(jar, 'w') as zipped:
                zipped.writestr('fabric.mod.json', json.dumps({'depends': {'minecraft': '1.21.1'}}))
            with self.assertRaisesRegex(RuntimeError, 'build for 1.21.10'):
                workspace.validate_mod_target(jar)

    def test_stale_managed_mod_is_rejected_without_removing_other_mods(self):
        with tempfile.TemporaryDirectory() as temp:
            server = Path(temp)
            (server / 'mods').mkdir()
            unrelated = server / 'mods' / 'unrelated.jar'
            unrelated.touch()
            with patch.object(workspace, 'SERVER', server):
                workspace.validate_managed_mod_names()
                stale = server / 'mods' / 'fabric-api-old.jar'
                stale.touch()
                with self.assertRaisesRegex(RuntimeError, 'Different managed mod version'):
                    workspace.validate_managed_mod_names()
                self.assertTrue(stale.exists())
                self.assertTrue(unrelated.exists())

    def test_backup_includes_world_and_excludes_libraries(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            server = root / 'server'
            (server / 'world').mkdir(parents=True)
            (server / 'libraries').mkdir()
            (server / 'world' / 'level.dat').write_bytes(b'world state')
            (server / 'libraries' / 'dependency.jar').write_bytes(b'library')
            (server / 'server.properties').write_text('online-mode=true')
            with patch.object(workspace, 'SERVER', server), patch.object(workspace, 'WORK', root):
                result = workspace.backup()
            with zipfile.ZipFile(result) as zipped:
                self.assertEqual(zipped.read('world/level.dat'), b'world state')
                self.assertIn('server.properties', zipped.namelist())
                self.assertNotIn('libraries/dependency.jar', zipped.namelist())
            with patch.object(workspace, 'WORK', root):
                restored = workspace.restore(result)
            self.assertEqual((restored / 'world' / 'level.dat').read_bytes(), b'world state')


if __name__ == '__main__':
    unittest.main()
