"""Two-client native Ancient Forge rehearsal on a fresh disposable world."""
from __future__ import annotations

import json
from pathlib import Path
import socket
import subprocess
import time


def _wait_ready(server, logfile: Path) -> None:
    deadline = time.monotonic() + 240
    while True:
        content = logfile.read_text(encoding='utf-8', errors='replace') if logfile.exists() else ''
        if 'Done (' in content and 'Voice chat server started at 127.0.0.1:24455' in content:
            return
        if server.poll() is not None or time.monotonic() > deadline:
            raise RuntimeError('Forge fixture failed startup: ' + str(logfile))
        time.sleep(.2)


def _reserve_ports() -> None:
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', 25575))
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
        probe.bind(('127.0.0.1', 24455))


def _client(w, fabric_smoke, launch, root: Path, reports: Path, role: str, phase: str):
    variables = {
        'CHRONICLE_FORGE_SMOKE': str(reports),
        'CHRONICLE_FORGE_PHASE': phase,
        'CHRONICLE_FORGE_ROLE': role,
        'CHRONICLE_FORGE_SERVER_DIR': str(root),
    }
    name = 'ChronicleFabric' + role
    return fabric_smoke._client(w, launch, root, reports, name, 'forge-' + phase + '-' + role, variables)


def _run_phase(w, fabric_smoke, launch, root: Path, reports: Path, phase: str, roles: tuple[str, ...]) -> None:
    _reserve_ports()
    logfile_path = reports / ('server-' + phase + '.log')
    logfile = logfile_path.open('w', encoding='utf-8')
    server = None
    clients = []
    try:
        environment = w.env()
        environment['CHRONICLE_FORGE_NATIVE_TRIAL'] = 'true'
        server = subprocess.Popen(w.server_command(), cwd=root, env=environment, stdin=subprocess.PIPE,
                                  stdout=logfile, stderr=subprocess.STDOUT, text=True, encoding='utf-8')
        _wait_ready(server, logfile_path)
        for role in roles:
            clients.append(_client(w, fabric_smoke, launch, root, reports, role, phase))
        results = [reports / ('forge-' + phase + '-' + role + '.json') for role in roles]
        fabric_smoke._await_clients(clients, results, timeout=500)
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
        fabric_smoke._stop(server)
        logfile.close()


def run() -> None:
    import fabric_smoke
    import workspace as w

    w.require_eula()
    w.validate_server_runtime()
    w.validate_managed_mod_names()
    w.run([w.REPO / 'gradlew.bat', '--no-daemon', ':core:remapJar',
           ':campaigns:quiet-below:remapJar', ':campaigns:quiet-below:writeSmokeClientLaunch'])
    launch = json.loads((w.REPO / 'campaigns/quiet-below/build/smoke/client-launch.json').read_text(encoding='utf-8'))
    root = fabric_smoke._stage(w)
    reports = w.REPORTS / ('forge-smoke-' + root.name)
    reports.mkdir(parents=True)
    report = {'result': 'FAIL', 'world': str(root), 'reports': str(reports), 'checks': [], 'phases': []}
    try:
        _run_phase(w, fabric_smoke, launch, root, reports, 'initial', ('A', 'B'))
        report['phases'].append({'phase': 'initial', 'roles': ['A', 'B']})
        report['checks'].append('initial Forge A/B native clients passed and stopped cleanly')
        _run_phase(w, fabric_smoke, launch, root, reports, 'restart', ('A',))
        report['phases'].append({'phase': 'restart', 'roles': ['A']})
        report['checks'].append('restart Forge A native client passed and stopped cleanly')
        report['result'] = 'PASS'
        print('PASS: Ancient Forge native smoke; evidence:', reports, flush=True)
    except BaseException as error:
        report['error'] = str(error)
        raise
    finally:
        (reports / 'report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
        (w.REPORTS / 'forge-smoke-latest.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    run()
