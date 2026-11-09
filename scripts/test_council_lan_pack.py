"""Fresh review exports preserve authored motions, never live ballot state."""
import unittest
import json
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch
import council_lan_pack as pack


class CouncilLanPackTest(unittest.TestCase):
    def fixture(self, schema):
        state = {'votingRegion': 'council', 'lecterns': ['motions'],
                 'motions': {'ordinary': {'hostPrepared': True, 'title': 'Road', 'sponsors': ['ash'],
                                          'status': 'RECORDED', 'ballots': ['old'], 'notes': ['private']},
                             'player': {'hostPrepared': False},
                             'crown': {'hostPrepared': True}},
                 'attempts': {'old': {'motion': 'ordinary'}, 'royal': {'motion': 'crown'}}}
        if schema > 1:
            state['succession'] = {'rounds': {'royal': {}}}
        else:
            del state['motions']['crown']
        return {'schemaVersion': schema, 'state': state}

    def test_supported_schemas_export_clean_authored_content(self):
        for schema in (1, 2, 3):
            with self.subTest(schema=schema):
                source = self.fixture(schema)
                saved = repr(source)
                clean = pack.review_proceedings(source)
                self.assertEqual(saved, repr(source))
                self.assertEqual(clean['schemaVersion'], 3)
                state = clean['state']
                self.assertEqual(set(state['motions']), {'ordinary'})
                self.assertEqual(state['motions']['ordinary']['title'], 'Road')
                self.assertEqual(state['motions']['ordinary']['ballots'], [])
                self.assertEqual(state['motions']['ordinary']['sponsors'], [])
                self.assertEqual(state['attempts'], {})
                self.assertIsNone(state['activeBallot'])
                self.assertIsNone(state['pending'])
                self.assertEqual(state['succession']['stage'], 'NOT_STARTED')

    def test_unknown_schema_is_not_silently_exported(self):
        with self.assertRaises(RuntimeError):
            pack.review_proceedings({'schemaVersion': 4, 'state': {}})

    def test_treaty_map_binding_travels_without_private_consent(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            council = root / 'world/chronicle/last-council'
            council.mkdir(parents=True)
            (root / 'world/level.dat').write_bytes(b'level')
            for name in ('chancellor.json', 'roster.json'):
                (council / name).write_text('{}', encoding='utf-8')
            (council / 'proceedings.json').write_text(json.dumps(self.fixture(3)), encoding='utf-8')
            binding = b'{"schema":1,"lecterns":["treaty"]}'
            (council / 'treaty-lecterns.json').write_bytes(binding)
            (council / 'treaties.json').write_text('private terms and signatures', encoding='utf-8')
            with patch.object(pack.w, 'check_stopped'), patch.object(pack, 'level_data', return_value=b'clean level'):
                result = pack.snapshot(root)
            self.assertEqual(result['chronicle/last-council/treaty-lecterns.json'], binding)
            self.assertNotIn('chronicle/last-council/treaties.json', result)


if __name__ == '__main__':
    unittest.main()
