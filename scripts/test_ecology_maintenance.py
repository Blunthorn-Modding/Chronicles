import json
import os
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch, Mock
import ecology_maintenance as m


class EcologyMaintenanceScriptTests(unittest.TestCase):
    def test_documented_parent_relative_world_path_is_normalized_before_link_checks(self):
        with TemporaryDirectory() as temporary:
            root=Path(temporary).resolve();(root/'scripts').mkdir();world=root/'world';world.mkdir()
            with patch.object(m.w,'WORK',root):
                self.assertEqual(world,m.world_path(root/'scripts'/'..'/'world'))
                with self.assertRaises(ValueError):m.world_path(root/'..')

    def test_rejects_missing_and_outside_worlds(self):
        with TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            with patch.object(m.w, 'WORK', root / 'workspace'):
                with self.assertRaises(ValueError):
                    m.world_path(root)
                with self.assertRaises(ValueError):
                    m.world_path(root / 'missing')

    def test_dry_run_is_default_and_apply_is_explicit(self):
        with TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            world = root / 'world'
            world.mkdir()
            launch = root / 'campaigns/quiet-below/build/maintenance/launch.json'
            launch.parent.mkdir(parents=True)
            launch.write_text(json.dumps({'classpath': ['one', 'two'], 'mainClass': 'Maintenance'}))
            with patch.object(m.w, 'WORK', root), patch.object(m.w, 'REPO', root), patch.object(m.w, 'run'), patch.object(m.subprocess, 'run', return_value=Mock(returncode=0, stdout='{"applied": false}')) as execute, patch('builtins.print'):
                m.run(world)
                self.assertNotIn('--apply', execute.call_args.args[0])
                self.assertIn(os.pathsep.join(['one', 'two']), execute.call_args.args[0])
                m.run(world, apply=True)
                self.assertEqual('--apply', execute.call_args.args[0][-1])

    def test_failed_java_is_reported_without_success(self):
        with TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            launch = root / 'campaigns/quiet-below/build/maintenance/launch.json'
            launch.parent.mkdir(parents=True)
            launch.write_text('{"classpath": [], "mainClass": "Maintenance"}')
            with patch.object(m.w, 'WORK', root.parent), patch.object(m.w, 'REPO', root), patch.object(m.w, 'run'), patch.object(m.subprocess, 'run', return_value=Mock(returncode=1, stderr='World is in use')):
                with self.assertRaisesRegex(RuntimeError, 'World is in use'):
                    m.run(root)
