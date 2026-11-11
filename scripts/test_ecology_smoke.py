import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

import ecology_smoke


class EcologySmokePhaseTests(unittest.TestCase):
    def test_costs_restart_requires_costs_first(self):
        for phases in [('costs_restart',), ('costs_restart', 'costs')]:
            with self.assertRaisesRegex(ValueError, 'Local-cost restart'):
                ecology_smoke.run(phases)
    def test_recovery_restart_requires_recovery_first(self):
        for phases in [('recovery_restart',), ('recovery_restart', 'recovery')]:
            with self.assertRaisesRegex(ValueError, 'Healing restart'):
                ecology_smoke.run(phases)
    def test_compaction_requires_stopped_initial_then_restart(self):
        for phases in [('compact',), ('initial', 'restart', 'compact'), ('compact', 'initial', 'restart')]:
            with self.assertRaisesRegex(ValueError, 'Compaction requires'):
                ecology_smoke.run(phases)
    def test_city_restart_requires_city_first(self):
        with self.assertRaisesRegex(ValueError, 'Ancient-city restart'):
            ecology_smoke.run(('city_restart',))
        with self.assertRaisesRegex(ValueError, 'Ancient-city restart'):
            ecology_smoke.run(('city_restart', 'city'))

    def test_city_phases_are_known_and_unique(self):
        with self.assertRaisesRegex(ValueError, 'distinct known ecology phases'):
            ecology_smoke.run(('city', 'city'))
        with self.assertRaisesRegex(ValueError, 'distinct known ecology phases'):
            ecology_smoke.run(('unknown',))

    def test_city_world_switches_the_fresh_fixture_to_fixed_normal_generation(self):
        with TemporaryDirectory() as temporary:
            root = Path(temporary)
            properties = root / 'server.properties'
            properties.write_text('level-type=minecraft:flat\ngenerate-structures=false\nlevel-name=world\n')
            ecology_smoke._city_world(root)
            text = properties.read_text()
            self.assertIn('level-type=minecraft:normal', text)
            self.assertIn('generate-structures=true', text)
            self.assertIn('level-seed=quiet-below-city-soak-v1', text)
