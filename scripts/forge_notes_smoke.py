"""Native written-book discovery through normal typed chest filling and use."""
import hashlib
import json
import subprocess
from pathlib import Path


def run():
    import workspace as w
    import fabric_smoke as fabric
    import forge_smoke
    w.require_eula()
    w.validate_server_runtime()
    w.validate_managed_mod_names()
    w.run([w.REPO / 'gradlew.bat', '--no-daemon', ':core:remapJar', ':campaigns:quiet-below:remapJar',
           ':campaigns:quiet-below:writeSmokeClientLaunch'])
    launch = json.loads((w.REPO / 'campaigns/quiet-below/build/smoke/client-launch.json').read_text(encoding='utf-8'))
    root = fabric._stage(w)
    reports = w.REPORTS / ('forge-notes-' + root.name)
    reports.mkdir(parents=True)
    (root / '.forge-notes-fixture').write_text('Disposable notes fixture', encoding='utf-8')
    # Simulate an older server config without the newly registered type.
    config = root / 'config/chronicle/chest-loot.json'
    config.parent.mkdir(parents=True, exist_ok=True)
    config.write_text('{"schemaVersion":1,"types":[]}\n', encoding='utf-8')
    report = {'result': 'FAIL', 'world': str(root), 'reports': str(reports), 'phases': []}
    try:
        for phase in ('initial', 'restart'):
            forge_smoke._reserve_ports()
            server = client = None
            handle = (reports / ('server-' + phase + '.log')).open('w', encoding='utf-8')
            before = hashlib.sha256(config.read_bytes()).hexdigest()
            try:
                environment = w.env()
                environment['CHRONICLE_FORGE_NOTES_TRIAL'] = 'true'
                server = subprocess.Popen(w.server_command(), cwd=root, env=environment, stdin=subprocess.PIPE,
                                          stdout=handle, stderr=subprocess.STDOUT, text=True, encoding='utf-8')
                forge_smoke._wait_ready(server, reports / ('server-' + phase + '.log'))
                if hashlib.sha256(config.read_bytes()).hexdigest() != before:
                    raise RuntimeError('Startup rewrote existing loot configuration')
                client = fabric._client(w, launch, root, reports, 'ChronicleFabricA', 'notes-' + phase, {
                    'CHRONICLE_FORGE_NOTES_SMOKE': str(reports), 'CHRONICLE_FORGE_NOTES_PHASE': phase})
                fabric._await_clients([client], [reports / ('client-' + phase + '.json')], timeout=240)
                proof = json.loads((reports / ('client-' + phase + '.json')).read_text(encoding='utf-8'))
                if proof.get('result') != 'PASS':
                    raise RuntimeError('Notes client failed: ' + proof.get('detail', 'no detail'))
                report['phases'].append({'phase': phase, 'startupConfigSha256': before, 'checks': len(proof['checks'])})
            finally:
                if client:
                    process, logfile = client
                    if process.poll() is None:
                        process.terminate()
                        try:
                            process.wait(timeout=15)
                        except subprocess.TimeoutExpired:
                            process.kill()
                            process.wait(timeout=15)
                    logfile.close()
                try:
                    fabric._stop(server)
                finally:
                    handle.close()
        report['result'] = 'PASS'
        print('PASS: Forge recipe books; evidence:', reports, flush=True)
    except BaseException as error:
        report['error'] = str(error)
        raise
    finally:
        text = json.dumps(report, indent=2) + '\n'
        (reports / 'report.json').write_text(text, encoding='utf-8')
        (w.REPORTS / 'forge-notes-latest.json').write_text(text, encoding='utf-8')


if __name__ == '__main__':
    run()
