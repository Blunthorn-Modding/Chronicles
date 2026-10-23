"""Dedicated CF-10 native Core menu smoke using a fresh loopback-only Fabric world.

This intentionally does not call ``fabric_smoke.run``: its legacy fixture has
precise revision/receipt expectations that are unrelated to the menu redesign.
"""
from __future__ import annotations

import json
from pathlib import Path
import socket
import subprocess
import time

import fabric_smoke


def _wait_ready(server, log: Path) -> None:
    deadline = time.monotonic() + 240
    while True:
        content = log.read_text(encoding='utf-8', errors='replace') if log.exists() else ''
        if 'Done (' in content and 'Voice chat server started at 127.0.0.1:24455' in content:
            return
        if server.poll() is not None or time.monotonic() > deadline:
            raise RuntimeError('Isolated Core menu server failed startup; inspect ' + str(log))
        time.sleep(.2)


def _reserve_ports() -> None:
    # A fixed test port is safe only when no unrelated process owns it.
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', 25575))
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
        probe.bind(('127.0.0.1', 24455))


def _client(w, launch, root, reports, role, label, width, height, layout_only=False):
    variables = {
        'CHRONICLE_MENU_SMOKE': str(reports),
        'CHRONICLE_MENU_ROLE': role,
        'CHRONICLE_MENU_SERVER_DIR': str(root),
        # ``fabric_smoke._client`` remains the single source for launch flags;
        # sizes are consumed by its optional width/height support once present.
        'CHRONICLE_MENU_WIDTH': str(width),
        'CHRONICLE_MENU_HEIGHT': str(height),
        'CHRONICLE_MENU_LAYOUT_ONLY': 'true' if layout_only else 'false',
    }
    return fabric_smoke._client(w, launch, root, reports, 'ChronicleMenu' + role, label, variables)


def _read_result(path: Path) -> dict:
    if not path.is_file():
        raise RuntimeError('Menu client did not write result: ' + str(path))
    data = json.loads(path.read_text(encoding='utf-8'))
    if data.get('result') != 'PASS':
        raise RuntimeError('Menu client failed: ' + json.dumps(data, indent=2))
    return data


def run() -> None:
    import workspace as w
    w.require_eula()
    w.validate_server_runtime()
    w.validate_managed_mod_names()
    w.run([w.REPO / 'gradlew.bat', '--no-daemon', ':core:remapJar', ':campaigns:quiet-below:remapJar', ':campaigns:quiet-below:writeSmokeClientLaunch'])
    launch = json.loads((w.REPO / 'campaigns/quiet-below/build/smoke/client-launch.json').read_text(encoding='utf-8'))
    root = fabric_smoke._stage(w)
    fabric_smoke._loot_fixture(root)
    # This fresh fixture proves operator separation without copying accounts,
    # auth or any play-world data.
    (root / 'ops.json').write_text(json.dumps([{
        'uuid': fabric_smoke.offline_uuid('ChronicleMenuA'), 'name': 'ChronicleMenuA',
        'level': 2, 'bypassesPlayerLimit': False,
    }], indent=2), encoding='utf-8')
    reports = w.REPORTS / ('core-menu-smoke-' + root.name)
    reports.mkdir(parents=True)
    report = {
        'world': str(root), 'reports': str(reports), 'minecraft': w.LOCK['minecraft'],
        'minecraft_protocol': w.LOCK['minecraft_protocol'], 'result': 'FAIL', 'checks': [],
    }
    server = None
    server_log = None
    clients = []
    try:
        _reserve_ports()
        log_path = reports / 'server.log'
        server_log = log_path.open('w', encoding='utf-8')
        server = subprocess.Popen(w.server_command(), cwd=root, env=w.env(), stdin=subprocess.PIPE,
                                  stdout=server_log, stderr=subprocess.STDOUT, text=True, encoding='utf-8')
        _wait_ready(server, log_path)
        report['checks'].append('fresh loopback fixture started with dedicated menu accounts')

        # 640x480 at gui scale 2 yields the first required 320x240 logical
        # capture.  The client harness also verifies the server's normal menu
        # transport rather than constructing a screen locally.
        clients = [
            _client(w, launch, root, reports, 'A', 'operator-320', 640, 480),
            _client(w, launch, root, reports, 'B', 'player', 640, 480),
        ]
        fabric_smoke._await_clients(clients, [reports / 'role-A.json', reports / 'role-B.json'])
        report['checks'].append('operator and non-operator native menu probes passed')
        for _, logfile in clients:
            logfile.close()
        clients = []

        # Run the same disposable operator probe at the other logical menu
        # sizes.  Separate evidence folders avoid clobbering result/screenshot
        # names and retain the exact launch dimensions for inspection.
        for label, width, height in [('operator-480', 960, 540), ('operator-640', 1280, 720)]:
            layout = reports / label
            layout.mkdir()
            clients = [_client(w, launch, root, layout, 'A', label, width, height, layout_only=True)]
            fabric_smoke._await_clients(clients, [layout / 'role-A.json'])
            clients[0][1].close(); clients = []
            report['checks'].append(label + ': native menu capture passed')

        fabric_smoke._stop(server); server = None
        server_log.close(); server_log = None
        report['checks'].append('clean disposable server stop')
        report['result'] = 'PASS'
        print('PASS: Core menu native smoke; evidence:', reports, flush=True)
    except BaseException as error:
        report['error'] = str(error)
        raise
    finally:
        for process, logfile in clients:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill(); process.wait(timeout=15)
            logfile.close()
        try:
            fabric_smoke._stop(server)
        finally:
            if server_log is not None:
                server_log.close()
            content = json.dumps(report, indent=2) + '\n'
            (reports / 'report.json').write_text(content, encoding='utf-8')
            (w.REPORTS / 'core-menu-latest.json').write_text(content, encoding='utf-8')


if __name__ == '__main__':
    run()
