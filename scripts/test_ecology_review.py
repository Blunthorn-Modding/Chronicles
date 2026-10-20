import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch
import ecology_review as review


class EcologyReviewTests(unittest.TestCase):
    def test_failed_smoke_cannot_be_prepared(self):
        with self.assertRaises(RuntimeError):
            review.snapshot({'result': 'FAIL', 'world': 'irrelevant'})

    def test_source_and_destination_must_be_direct_timestamped_children(self):
        with TemporaryDirectory() as tmp:
            parent = Path(tmp)
            accepted = parent / '20261020T103530363844Z'
            self.assertEqual(accepted.resolve(), review.direct(accepted, parent))
            for candidate in (parent / '..' / accepted.name, parent / 'nested' / accepted.name, parent / 'server'):
                with self.assertRaises(RuntimeError):
                    review.direct(candidate, parent)

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
