import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch
import ecology_review as review


class EcologyReviewTests(unittest.TestCase):
    def test_failed_smoke_cannot_be_prepared(self):
        with self.assertRaises(RuntimeError):
            review.snapshot({'result': 'FAIL', 'world': 'irrelevant'})

    def test_focused_trial_cannot_replace_the_full_review_snapshot(self):
        with patch.object(review.w, 'check_stopped') as stopped:
            with self.assertRaisesRegex(RuntimeError, 'focused ecology trial'):
                review.snapshot({'result': 'PASS', 'phases': ['vitality'], 'world': 'irrelevant'})
            stopped.assert_not_called()

    def test_source_and_destination_must_be_direct_timestamped_children(self):
        with TemporaryDirectory() as tmp:
            parent = Path(tmp)
            accepted = parent / '20261020T103530363844Z'
            self.assertEqual(accepted.resolve(), review.direct(accepted, parent))
            for candidate in (parent / '..' / accepted.name, parent / 'nested' / accepted.name, parent / 'server'):
                with self.assertRaises(RuntimeError):
                    review.direct(candidate, parent)

    def test_snapshot_refuses_live_or_cleanup_enabled_world(self):
        import json
        with TemporaryDirectory() as tmp:
            work=Path(tmp)
            root=work/'test-runs/20261020T103530363844Z'
            source=root/'review-world'
            (source/'quiet-below').mkdir(parents=True)
            (source/'chronicle').mkdir()
            (source/'level.dat').touch()
            report={'result':'PASS','world':str(root)}
            with patch.object(review.w,'WORK',work):
                for phase,cleanup in (('ACTIVE',False),('PAUSED',True),('PAUSED',False)):
                    (source/'chronicle/campaign-state.json').write_text(json.dumps({'phase':phase}))
                    (source/'quiet-below/ecology.jsonl').write_text(json.dumps({'type':'CLEANUP_MODE','payload':{'enabled':cleanup}})+'\n')
                    if phase=='PAUSED' and not cleanup:
                        self.assertEqual(source,review.snapshot(report))
                    else:
                        with self.assertRaises(RuntimeError):review.snapshot(report)

    def test_unsafe_auth_or_bind_is_rejected_before_launch(self):
        import json
        with TemporaryDirectory() as tmp:
            parent = Path(tmp)
            root = parent / '20261020T103530363844Z'
            root.mkdir()
            manifest = {'review': str(root), 'minecraft': review.w.LOCK['minecraft'], 'mods': {}}
            report = parent / 'latest.json'
            report.write_text(json.dumps(manifest))
            (root / '.chronicle-ecology-review.json').write_text(json.dumps(manifest))
            with patch.object(review, 'ROOTS', parent), patch.object(review, 'REPORT', report):
                for key, value in (('online-mode', 'false'), ('server-ip', '0.0.0.0'), ('level-name', '../server/world')):
                    properties = dict(review.REQUIRED, **{key: value})
                    (root / 'server.properties').write_text('\n'.join(k + '=' + v for k, v in properties.items()))
                    with self.assertRaises(RuntimeError):
                        review.resolve()
