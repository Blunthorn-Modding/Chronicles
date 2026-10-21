"""Real-client Quiet Below ecology rehearsal in a retained disposable world."""
import json
import socket
import subprocess
import time
import shutil


def _wait_ready(server, logfile):
    deadline = time.monotonic() + 240
    while True:
        output = logfile.read_text(encoding='utf-8', errors='replace') if logfile.exists() else ''
        if 'Done (' in output and 'Voice chat server started at 127.0.0.1:24455' in output:
            return
        if server.poll() is not None or time.monotonic() > deadline:
            raise RuntimeError('Ecology fixture failed startup: ' + str(logfile))
        time.sleep(.2)


def _reserve_ports():
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', 25575))
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
        probe.bind(('127.0.0.1', 24455))


def run(phases=None):
    import workspace as w
    import fabric_smoke as f
    allowed = ('initial', 'restart', 'surface', 'organic', 'vitality', 'items', 'crops', 'crops_restart')
    phases = allowed if phases is None else tuple(phases)
    if not phases or any(p not in allowed for p in phases) or len(set(phases)) != len(phases):
        raise ValueError('Select distinct known ecology phases')
    if 'restart' in phases and ('initial' not in phases or phases.index('initial') > phases.index('restart')):
        raise ValueError('Restart requires an earlier initial phase in the same run')
    if 'crops_restart' in phases and ('crops' not in phases or phases.index('crops') > phases.index('crops_restart')):
        raise ValueError('Crop restart requires an earlier crop phase in the same run')
    w.require_eula()
    w.validate_server_runtime()
    w.validate_managed_mod_names()
    w.run([w.REPO / 'gradlew.bat', '--no-daemon', ':core:remapJar',
           ':campaigns:quiet-below:remapJar', ':campaigns:quiet-below:writeSmokeClientLaunch'])
    launch = json.loads((w.REPO / 'campaigns/quiet-below/build/smoke/client-launch.json').read_text(encoding='utf-8'))
    root = f._stage(w)
    reports = w.REPORTS / ('ecology-smoke-' + root.name)
    reports.mkdir(parents=True)
    report = {'result': 'FAIL', 'world': str(root), 'reports': str(reports), 'phases': list(phases), 'checks': []}
    server = None
    server_log = None
    clients = []
    crop_root = None
    try:
        for phase in phases:
            if phase == 'crops':
                crop_root = f._stage(w)
            phase_root = f._stage(w) if phase in ('surface', 'organic', 'vitality', 'items') else root
            if phase in ('crops', 'crops_restart'):
                phase_root = crop_root
                report['crops_world'] = str(crop_root)
            if phase in ('surface', 'organic', 'vitality', 'items'):
                report[phase + '_world'] = str(phase_root)
            _reserve_ports()
            logfile = reports / ('server-' + phase + '.log')
            server_log = logfile.open('w', encoding='utf-8')
            trial_env=w.env()
            trial_env['CHRONICLE_ECOLOGY_NATIVE_TRIAL']='1'
            server = subprocess.Popen(w.server_command(), cwd=phase_root, env=trial_env, stdin=subprocess.PIPE,
                                      stdout=server_log, stderr=subprocess.STDOUT, text=True, encoding='utf-8')
            _wait_ready(server, logfile)
            clients = [f._client(w, launch, phase_root, reports, 'ChronicleFabricA', 'ecology-' + phase, {
                'CHRONICLE_ECOLOGY_SMOKE': str(reports), 'CHRONICLE_ECOLOGY_PHASE': phase
            })]
            f._await_clients(clients, [reports / ('ecology-' + phase + '.json')], timeout=300)
            for _, handle in clients:
                handle.close()
            clients = []
            f._stop(server)
            server = None
            server_log.close()
            server_log = None
            if phase == 'restart':
                shutil.copytree(root / 'world', root / 'review-world')
            if phase == 'crops_restart':
                shutil.copytree(crop_root / 'world', crop_root / 'review-world')
            report['checks'].append(phase + ': native client ecology fixture passed and server saved cleanly')
        report['result'] = 'PASS'
        print('PASS: retained ecology fixture; evidence:', reports, flush=True)
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
            (w.REPORTS / 'ecology-smoke-latest.json').write_text(json.dumps(report, indent=2), encoding='utf-8')


if __name__ == '__main__':
    run()
