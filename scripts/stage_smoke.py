"""Real-client preview/apply/pause/restart/conflict/undo rehearsal in a fresh world."""
import json
import socket
import subprocess
import time
import shutil


def run(pilot=False):
    import workspace as w
    import fabric_smoke as f
    w.require_eula()
    w.validate_server_runtime()
    w.validate_managed_mod_names()
    w.run([w.REPO / 'gradlew.bat', '--no-daemon', ':campaigns:quiet-below:writeSmokeClientLaunch'])
    launch = json.loads((w.REPO / 'campaigns/quiet-below/build/smoke/client-launch.json').read_text())
    root = f._stage(w)
    if pilot:
        import pilot as p
        source = p.resolve_latest()
        w.check_stopped(source)
        shutil.copytree(source / 'world', root / 'world')
        properties = p._properties(source / 'server.properties')
        with (root / 'server.properties').open('a', encoding='utf-8') as out:
            for key in ('generator-settings', 'level-seed'):
                out.write(key + '=' + properties[key] + '\n')
            out.write('view-distance=8\n')
    reports = w.REPORTS / (('pilot-smoke-' if pilot else 'stage-smoke-') + root.name)
    reports.mkdir(parents=True)
    report = {'result': 'FAIL', 'world': str(root), 'reports': str(reports), 'checks': []}
    server = None
    server_log = None
    clients = []
    try:
        for phase in (('pilot',) if pilot else ('initial', 'restart')):
            with socket.socket() as probe:
                probe.bind(('127.0.0.1', 25575))
            with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
                probe.bind(('127.0.0.1', 24455))
            logfile = reports / ('server-' + phase + '.log')
            server_log = logfile.open('w', encoding='utf-8')
            server = subprocess.Popen(w.server_command(), cwd=root, env=w.env(), stdin=subprocess.PIPE,
                                      stdout=server_log, stderr=subprocess.STDOUT, text=True, encoding='utf-8')
            deadline = time.monotonic() + 240
            while 'Voice chat server started at 127.0.0.1:24455' not in logfile.read_text(encoding='utf-8', errors='replace'):
                if server.poll() is not None or time.monotonic() > deadline:
                    raise RuntimeError('Stage fixture failed startup: ' + str(logfile))
                time.sleep(0.2)
            clients = [f._client(w, launch, root, reports, 'ChronicleFabricA', 'stage-' + phase, {
                'CHRONICLE_STAGE_SMOKE': str(reports), 'CHRONICLE_STAGE_PHASE': phase
            })]
            f._await_clients(clients, [reports / ('stage-' + phase + '.json')])
            if not pilot:
                state = json.loads((root / 'world/quiet-below/stages.json').read_text())
                operation = state['operation']
                expected = 'APPLYING' if phase == 'initial' else 'UNDONE'
                if operation['status'] != expected:
                    raise RuntimeError('Stage journal did not persist expected phase: ' + str(operation))
            detail = 'site blocks, six native clue signs, survival stair walk and screenshots' if pilot else 'client world states, command results and durable journal'
            report['checks'].append(phase + ': ' + detail + ' passed')
            f._stop(server)
            server_log.close()
            server_log = None
            for _, log in clients:
                log.close()
            clients = []
            report['checks'].append(phase + ': saving server stop')
        report['result'] = 'PASS'
        print('PASS:', 'pilot site real-client checks;' if pilot else 'bounded stage real-client rehearsal;', 'evidence:', reports, flush=True)
    except BaseException as error:
        report['error'] = str(error)
        raise
    finally:
        for process, log in clients:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=15)
            log.close()
        try:
            f._stop(server)
        finally:
            if server_log is not None:
                server_log.close()
            filename = 'pilot-test-latest.json' if pilot else 'stage-latest.json'
            (w.REPORTS / filename).write_text(json.dumps(report, indent=2), encoding='utf-8')
