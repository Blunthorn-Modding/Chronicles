import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import release_compat_smoke as smoke


class ReleaseClasspathTests(unittest.TestCase):
    def test_only_release_inputs_supply_core_and_campaign_implementations(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);repo=root/'source';fixture=root/'fixture';fixture.mkdir()
            config=repo/'campaigns/quiet-below/.gradle/loom-cache/launch.cfg';config.parent.mkdir(parents=True)
            core=str(repo/'core/build/classes/java/main');qb=str(repo/'campaigns/quiet-below/build/resources/main')
            game=str(root/'framework/game.jar');yaml=str(root/'cache/snakeyaml-engine/3.1.1/library.jar')
            config.write_text('commonProperties\n\tfabric.development=true\n\tfabric.classPathGroups='+core+';;'+qb+';;'+game+'\n')
            jars=[root/'core-release.jar',root/'old-qb.jar']
            for p in jars:p.write_bytes(p.name.encode())
            original=dict(classpath=[core,qb,game,yaml],jvmArgs=['-Dfabric.dli.config=old','-Dfabric.addMods=stale','-Dtest=preserved'],mainClass='Launcher',args=[])
            with patch.object(smoke.w,'REPO',repo):result=smoke.release_launch(original,fixture,jars)
            self.assertEqual([game],result['classpath'])
            self.assertEqual([core,qb,game,yaml],original['classpath'])
            self.assertIn('-Dtest=preserved',result['jvmArgs'])
            self.assertEqual(['-Dfabric.addMods='+os.pathsep.join(str(p.resolve()) for p in jars)],[a for a in result['jvmArgs'] if a.startswith('-Dfabric.addMods=')])
            staged=(fixture/'release-client-launch.cfg').read_text()
            self.assertNotIn(core,staged);self.assertNotIn(qb,staged);self.assertIn(game,staged)
            self.assertEqual(2,len(json.loads((fixture/'client-provenance.json').read_text())['releaseInputs']))

    def test_optional_hud_reconnect_keeps_release_inputs_and_one_mod_argument(self):
        original=dict(jvmArgs=['-Dfabric.addMods=core.jar'+os.pathsep+'qb.jar','-Dtest=preserved'])
        with patch.object(smoke.hud_dependencies,'load_lock',return_value=[]),patch.object(smoke.hud_dependencies,'cached_jars',return_value=[Path('hud.jar'),Path('menu.jar')]):
            result=smoke.reconnect_hud_launch(original,None)
        self.assertEqual(['-Dfabric.addMods='+os.pathsep.join(['core.jar','qb.jar','hud.jar','menu.jar'])],[a for a in result['jvmArgs'] if a.startswith('-Dfabric.addMods=')])
        self.assertEqual(2,len(original['jvmArgs']))


if __name__=='__main__':unittest.main()
