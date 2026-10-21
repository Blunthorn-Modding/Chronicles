import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

import costs_review as review


def journal(*entries):
    return '\n'.join(json.dumps(entry) for entry in entries)


class CostsReviewTests(unittest.TestCase):
    def source(self, work):
        root = work / 'test-runs/20261021T084717415282Z'
        source = root / 'review-world'
        (source / 'quiet-below').mkdir(parents=True)
        (source / 'chronicle').mkdir()
        (source / 'level.dat').touch()
        (source / 'chronicle/campaign-state.json').write_text('{"phase":"PAUSED"}', encoding='utf-8')
        return root, source

    def report(self, root):
        return {'result': 'PASS', 'phases': ['costs', 'costs_restart'], 'costs_world': str(root)}

    def test_refuses_missing_failed_or_reordered_costs_evidence(self):
        for report in ({'result': 'FAIL'}, {'result': 'PASS', 'phases': ['costs']},
                       {'result': 'PASS', 'phases': ['costs_restart', 'costs'], 'costs_world': 'x'}):
            with self.assertRaises(RuntimeError):
                review.snapshot(report)

    def test_snapshot_requires_paused_defaults_and_cleanup_off(self):
        with TemporaryDirectory() as tmp:
            work = Path(tmp); root, source = self.source(work)
            with patch.object(review.w, 'WORK', work), patch.object(review.w, 'check_stopped'):
                for phase, recovery, costs, cleanup, accepted in [
                    ('ACTIVE', review.DEFAULT_RECOVERY, review.DEFAULT_COSTS, False, False),
                    ('PAUSED', {'enabled': True, 'seconds': 2}, review.DEFAULT_COSTS, False, False),
                    ('PAUSED', review.DEFAULT_RECOVERY, {'hunger': {'enabled': False, 'seconds': 40}, 'darkness': {'enabled': True, 'seconds': 40}}, False, False),
                    ('PAUSED', review.DEFAULT_RECOVERY, review.DEFAULT_COSTS, True, False),
                    ('PAUSED', review.DEFAULT_RECOVERY, review.DEFAULT_COSTS, False, True),
                ]:
                    (source / 'chronicle/campaign-state.json').write_text(json.dumps({'phase': phase}), encoding='utf-8')
                    (source / 'quiet-below/ecology.jsonl').write_text(journal(
                        {'type': 'RECOVERY_RULES', 'payload': recovery},
                        {'type': 'COST_RULES', 'payload': costs},
                        {'type': 'CLEANUP_MODE', 'payload': {'enabled': cleanup}}), encoding='utf-8')
                    if accepted:
                        self.assertEqual(source, review.snapshot(self.report(root)))
                    else:
                        with self.assertRaises(RuntimeError):
                            review.snapshot(self.report(root))

    def test_checkpoint_defaults_and_later_effective_override_are_checked(self):
        with TemporaryDirectory() as tmp:
            work = Path(tmp); root, source = self.source(work)
            checkpoint = {'type': 'CHECKPOINT_BEGIN', 'payload': {
                'recovery': review.DEFAULT_RECOVERY, 'costs': review.DEFAULT_COSTS, 'cleanup': False}}
            with patch.object(review.w, 'WORK', work), patch.object(review.w, 'check_stopped'):
                (source / 'quiet-below/ecology.jsonl').write_text(journal(checkpoint), encoding='utf-8')
                self.assertEqual(source, review.snapshot(self.report(root)))
                (source / 'quiet-below/ecology.jsonl').write_text(journal(checkpoint, {
                    'type': 'COST_RULES', 'payload': {'hunger': {'enabled': True, 'seconds': 10},
                                                       'darkness': {'enabled': True, 'seconds': 40}}}), encoding='utf-8')
                with self.assertRaises(RuntimeError):
                    review.snapshot(self.report(root))

    def test_malformed_cost_checkpoint_is_rejected(self):
        with TemporaryDirectory() as tmp:
            work = Path(tmp); root, source = self.source(work)
            (source / 'quiet-below/ecology.jsonl').write_text(journal({'type': 'CHECKPOINT_BEGIN', 'payload': {
                'recovery': review.DEFAULT_RECOVERY, 'costs': {'hunger': review.DEFAULT_COSTS['hunger']}, 'cleanup': False}}), encoding='utf-8')
            with patch.object(review.w, 'WORK', work), patch.object(review.w, 'check_stopped'):
                with self.assertRaises(RuntimeError):
                    review.snapshot(self.report(root))

    def test_resolve_rejects_unsafe_auth_bind_port_and_path(self):
        with TemporaryDirectory() as tmp:
            parent = Path(tmp); root = parent / '20261021T084717415282Z'; root.mkdir(); report = parent / 'latest.json'
            manifest = {'schema': 1, 'review': str(root), 'minecraft': review.w.LOCK['minecraft'],
                        'mods': {}, 'port': review.PORT, 'voice_port': review.VOICE_PORT}
            report.write_text(json.dumps(manifest), encoding='utf-8')
            (root / review.MARKER).write_text(json.dumps(manifest), encoding='utf-8')
            voice = root / 'config/voicechat'; voice.mkdir(parents=True)
            (voice / 'voicechat-server.properties').write_text(f'bind_address=127.0.0.1\nport={review.VOICE_PORT}\n', encoding='utf-8')
            with patch.object(review, 'ROOTS', parent), patch.object(review, 'REPORT', report):
                for key, value in [('online-mode', 'false'), ('server-ip', '0.0.0.0'),
                                   ('server-port', '25565'), ('level-name', '../server/world'),
                                   ('enforce-secure-profile', 'false')]:
                    properties = dict(review.REQUIRED, **{key: value})
                    (root / 'server.properties').write_text('\n'.join(f'{k}={v}' for k, v in properties.items()), encoding='utf-8')
                    with self.assertRaises(RuntimeError):
                        review.resolve()

    def test_resolve_rejects_voice_port_tampering(self):
        with TemporaryDirectory() as tmp:
            parent = Path(tmp); root = parent / '20261021T084717415282Z'; root.mkdir(); report = parent / 'latest.json'
            manifest = {'schema': 1, 'review': str(root), 'minecraft': review.w.LOCK['minecraft'],
                        'mods': {}, 'port': review.PORT, 'voice_port': review.VOICE_PORT}
            report.write_text(json.dumps(manifest), encoding='utf-8')
            (root / review.MARKER).write_text(json.dumps(manifest), encoding='utf-8')
            (root / 'server.properties').write_text('\n'.join(f'{k}={v}' for k, v in review.REQUIRED.items()), encoding='utf-8')
            voice = root / 'config/voicechat'; voice.mkdir(parents=True)
            (voice / 'voicechat-server.properties').write_text('bind_address=127.0.0.1\nport=24454\n', encoding='utf-8')
            with patch.object(review, 'ROOTS', parent), patch.object(review, 'REPORT', report):
                with self.assertRaises(RuntimeError):
                    review.resolve()
