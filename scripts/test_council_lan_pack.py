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
        for schema in (1, 2, 3, 4, 5):
            with self.subTest(schema=schema):
                source = self.fixture(schema)
                saved = repr(source)
                clean = pack.review_proceedings(source)
                self.assertEqual(saved, repr(source))
                self.assertEqual(clean['schemaVersion'], 5)
                state = clean['state']
                self.assertEqual(set(state['motions']), {'ordinary'})
                self.assertEqual(state['motions']['ordinary']['title'], 'Road')
                self.assertEqual(state['motions']['ordinary']['ballots'], [])
                self.assertEqual(state['motions']['ordinary']['sponsors'], [])
                self.assertEqual(state['attempts'], {})
                self.assertIsNone(state['activeBallot'])
                self.assertIsNone(state['pending'])
                self.assertEqual(state['succession']['stage'], 'NOT_STARTED')

    def test_reinstatement_with_live_memberships_is_not_exported(self):
        source=self.fixture(5)
        source['state']['motions']['ordinary']['reinstatement']={'targets': ['live-target']}
        self.assertNotIn('ordinary',pack.review_proceedings(source)['state']['motions'])

    def test_unknown_schema_is_not_silently_exported(self):
        with self.assertRaises(RuntimeError):
            pack.review_proceedings({'schemaVersion': 6, 'state': {}})

    def test_guarded_map_travels_without_live_rebellion_progress(self):
        with TemporaryDirectory() as directory:
            root=Path(directory);world=root/'world';council=world/'chronicle/last-council';council.mkdir(parents=True)
            (world/'level.dat').write_bytes(b'level')
            (council/'proceedings.json').write_text(json.dumps(self.fixture(5)),encoding='utf-8')
            for name in ('chancellor.json','roster.json'):(council/name).write_text('{}',encoding='utf-8')
            bindings=b'{"schemaVersion":1,"bindings":[]}'
            (world/'chronicle/region-access.json').write_bytes(bindings)
            (world/'chronicle/region-access.initialized').write_bytes(b'chronicle-region-access-v1\n')
            (world/'chronicle/voice-rooms.json').write_bytes(bindings)
            (world/'chronicle/voice-rooms.initialized').write_bytes(b'chronicle-voice-rooms-v1\n')
            (council/'rebellions.json').write_text('live memberships and grants',encoding='utf-8')
            (council/'rebellions-initialized').write_bytes(b'1\n')
            with patch.object(pack.w,'check_stopped'),patch.object(pack,'level_data',return_value=b'clean level'):
                result=pack.snapshot(root)
            self.assertEqual(result['chronicle/region-access.json'],bindings)
            self.assertEqual(result['chronicle/region-access.initialized'],b'chronicle-region-access-v1\n')
            self.assertEqual(result['chronicle/voice-rooms.json'],bindings)
            self.assertEqual(result['chronicle/voice-rooms.initialized'],b'chronicle-voice-rooms-v1\n')
            self.assertNotIn('chronicle/last-council/rebellions.json',result)
            self.assertNotIn('chronicle/last-council/rebellions-initialized',result)

    def test_voice_review_instructions_do_not_claim_e4mc_relays_voice(self):
        default = pack.instructions('test')
        voice = pack.instructions('test', voice=True)
        self.assertIn('Simple Voice Chat is omitted', default)
        self.assertNotIn('Simple Voice Chat is omitted', voice)
        self.assertIn('group automatically', voice)
        self.assertIn('own working UDP connection', voice)

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
