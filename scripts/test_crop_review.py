import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch
import crop_review as review


class CropReviewTests(unittest.TestCase):
    def test_refuses_missing_or_failed_native_crop_restart_evidence(self):
        for report in ({'result':'FAIL'},{'result':'PASS','phases':['crops']},{'result':'PASS','phases':['initial','restart']}):
            with self.assertRaises(RuntimeError):review.snapshot(report)

    def test_copied_world_must_be_paused_and_have_default_tuning(self):
        with TemporaryDirectory() as tmp:
            work=Path(tmp);root=work/'test-runs/20261021T084717415282Z';source=root/'review-world'
            (source/'quiet-below').mkdir(parents=True);(source/'chronicle').mkdir();(source/'level.dat').touch()
            report={'result':'PASS','phases':['crops','crops_restart'],'crops_world':str(root)}
            with patch.object(review.w,'WORK',work),patch.object(review.w,'check_stopped'):
                for phase,seconds,cleanup in [('ACTIVE',10,False),('PAUSED',2,False),('PAUSED',10,True),('PAUSED',10,False)]:
                    (source/'chronicle/campaign-state.json').write_text(json.dumps({'phase':phase}))
                    (source/'quiet-below/ecology.jsonl').write_text('\n'.join(json.dumps(e) for e in [
                        {'type':'CROP_RULES','payload':{'enabled':True,'seconds':seconds}},
                        {'type':'CLEANUP_MODE','payload':{'enabled':cleanup}}]))
                    if phase=='PAUSED' and seconds==10 and not cleanup:self.assertEqual(source,review.snapshot(report))
                    else:
                        with self.assertRaises(RuntimeError):review.snapshot(report)

    def test_unsafe_auth_or_bind_is_rejected(self):
        with TemporaryDirectory() as tmp:
            parent=Path(tmp);root=parent/'20261021T084717415282Z';root.mkdir();report=parent/'latest.json'
            manifest={'review':str(root),'minecraft':review.w.LOCK['minecraft'],'mods':{}}
            report.write_text(json.dumps(manifest));(root/'.chronicle-crop-review.json').write_text(json.dumps(manifest))
            with patch.object(review,'ROOTS',parent),patch.object(review,'REPORT',report):
                for key,value in [('online-mode','false'),('server-ip','0.0.0.0'),('server-port','25565'),('level-name','../server/world')]:
                    (root/'server.properties').write_text('\n'.join(k+'='+v for k,v in dict(review.REQUIRED,**{key:value}).items()))
                    with self.assertRaises(RuntimeError):review.resolve()
