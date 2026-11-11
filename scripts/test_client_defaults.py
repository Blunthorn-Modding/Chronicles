from pathlib import Path
import json
import tempfile
import sys
import unittest
from unittest.mock import patch
from scripts import client_defaults as defaults


class ClientDefaultsTest(unittest.TestCase):
    def config(self, root, kind, run, port, **changes):
        target = root / 'reviews' / kind / run / 'server.properties'
        target.parent.mkdir(parents=True, exist_ok=True)
        values = {'server-ip': '127.0.0.1', 'server-port': str(port), 'online-mode': 'true'}
        values.update(changes)
        target.write_text('\n'.join(k+'='+v for k, v in values.items()))
        return target

    def test_only_running_owned_authenticated_loopback_reviews_are_seeded(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.config(root, 'contact', 'now', 25589)
            self.config(root, 'costs', 'old', 25588)
            self.config(root, 'unsafe', 'a', 25590, **{'online-mode':'false'})
            self.config(root, 'external', 'a', 25591, **{'server-ip':'192.0.2.1'})
            self.config(root, 'bot', 'a', 25575)
            with patch.object(defaults, '_status', side_effect=lambda port, protocol: {'version':{'protocol':773}} if port==25589 else (_ for _ in ()).throw(OSError())) as status:
                result = defaults.review_servers(root, 773)
            self.assertEqual(result, [{'name':'Chronicle - Current review','address':'127.0.0.1:25589'}])
            self.assertEqual({call.args[0] for call in status.call_args_list}, {25588,25589})

    def test_wrong_protocol_and_duplicate_historical_ports_are_filtered(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.config(root, 'ecology', 'one', 25585)
            self.config(root, 'ecology', 'two', 25585)
            with patch.object(defaults, '_status', return_value={'version':{'protocol':772}}) as status:
                self.assertEqual(defaults.review_servers(root,773), [])
                self.assertEqual(status.call_count,1)

    def test_native_launch_keeps_username_when_installing_client_defaults(self):
        from scripts import fabric_smoke
        from types import SimpleNamespace
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);reports=root/'reports';reports.mkdir()
            workspace=SimpleNamespace(WORK=root,LOCK={'minecraft_protocol':773},JAVA=Path('java'),
                                      env=lambda:{},client_options=lambda:'guiScale:2\n')
            launch={'jvmArgs':[],'classpath':[],'mainClass':'Main','args':[]}
            with patch.object(fabric_smoke.subprocess,'Popen',return_value=object()), patch.dict(sys.modules, {'client_defaults':defaults}):
                process,log=fabric_smoke._client(workspace,launch,root,reports,'ChronicleFabricA','defaults',{})
                log.close()
            arguments=(root/'client-defaults/java-arguments.txt').read_text()
            self.assertIn('"--username"\n"ChronicleFabricA"',arguments)
            self.assertTrue((root/'client-defaults'/defaults.FANCY_PATH).is_file())

    def test_supported_welcome_option_and_explicit_client_payloads(self):
        with tempfile.TemporaryDirectory() as temp:
            values = defaults.payloads(Path(temp),773)
            defaults.validate_payloads(values)
            self.assertIn(b"B:show_welcome_screen = 'false';",values[defaults.FANCY_PATH])
            self.assertEqual(json.loads(values[defaults.SERVERS_PATH]),{'version':1,'servers':[]})
            bad=dict(values);bad['vendor.class']=b'not allowed'
            with self.assertRaises(ValueError): defaults.validate_payloads(bad)
            bad=dict(values);bad[defaults.SERVERS_PATH]=json.dumps({'version':1,'servers':[{'name':'remote','address':'example.invalid:25565'}]}).encode()
            with self.assertRaises(ValueError): defaults.validate_payloads(bad)


if __name__ == '__main__': unittest.main()
