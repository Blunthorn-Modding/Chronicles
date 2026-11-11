"""Real-client vanilla Warden rehearsal in one retained disposable world."""
import json
import subprocess


def _hard_fixture(root):
    """Change only the fresh staged server from the shared peaceful default."""
    properties = root / 'server.properties'
    content = properties.read_text(encoding='utf-8')
    marker = 'difficulty=peaceful'
    if content.count(marker) != 1:
        raise RuntimeError('Fresh smoke fixture does not contain the expected peaceful difficulty pin')
    properties.write_text(content.replace(marker, 'difficulty=hard'), encoding='utf-8')


def run():
    import workspace as w
    import fabric_smoke as f
    import ecology_smoke as e

    w.require_eula()
    w.validate_server_runtime()
    w.validate_managed_mod_names()
    w.run([
        w.REPO / 'gradlew.bat', '--no-daemon',
        ':core:remapJar', ':campaigns:quiet-below:remapJar',
        ':campaigns:quiet-below:writeSmokeClientLaunch'
    ])
    launch = json.loads((w.REPO / 'campaigns/quiet-below/build/smoke/client-launch.json').read_text(encoding='utf-8'))
    root = f._stage(w)
    _hard_fixture(root)
    reports = w.REPORTS / ('warden-smoke-' + root.name)
    reports.mkdir(parents=True)
    report = {
        'result': 'FAIL',
        'world': str(root),
        'reports': str(reports),
        'minecraft': w.LOCK['minecraft'],
        'checks': [],
        'screenshots': []
    }
    server = None
    server_log = None
    clients = []
    try:
        e._reserve_ports()
        logfile = reports / 'server.log'
        server_log = logfile.open('w', encoding='utf-8')
        trial_env = w.env()
        trial_env['CHRONICLE_WARDEN_NATIVE_TRIAL'] = '1'
        server = subprocess.Popen(
            w.server_command(), cwd=root, env=trial_env, stdin=subprocess.PIPE,
            stdout=server_log, stderr=subprocess.STDOUT, text=True, encoding='utf-8'
        )
        e._wait_ready(server, logfile)
        clients = [f._client(w, launch, root, reports, 'ChronicleFabricA', 'warden', {
            'CHRONICLE_WARDEN_SMOKE': str(reports)
        })]
        client_report = reports / 'warden-smoke.json'
        f._await_clients(clients, [client_report], timeout=300)
        result = json.loads(client_report.read_text(encoding='utf-8'))
        screenshots = [reports / path for path in result.get('screenshots', [])]
        if len(screenshots) < 2 or not all(path.is_file() for path in screenshots):
            raise RuntimeError('Warden rehearsal passed without both emergence and digging screenshots')
        report['checks'].extend(result.get('checks', []))
        report['checks'].append('fresh hard loopback fixture completed the unmodified vanilla warning/Warden lifecycle')
        report['screenshots'] = [str(path) for path in screenshots]
        for _, handle in clients:
            handle.close()
        clients = []
        f._stop(server)
        server = None
        server_log.close()
        server_log = None
        report['checks'].append('isolated server saved and stopped cleanly')
        report['result'] = 'PASS'
        print('PASS: vanilla Warden rehearsal; evidence:', reports, flush=True)
    except BaseException as error:
        report['error'] = str(error)
        raise
    finally:
        for process, handle in clients:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=15)
            handle.close()
        try:
            f._stop(server)
        finally:
            if server_log is not None:
                server_log.close()
            (w.REPORTS / 'warden-smoke-latest.json').write_text(json.dumps(report, indent=2), encoding='utf-8')


if __name__ == '__main__':
    run()
