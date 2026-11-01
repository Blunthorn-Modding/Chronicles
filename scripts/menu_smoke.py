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


def run(build: bool = True) -> None:
    import workspace as w
    w.require_eula()
    w.validate_server_runtime()
    w.validate_managed_mod_names()
    if build:
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
        operator = _read_result(reports / 'role-A.json')
        required = {'Visible controls stay inside the popup and Debug sits beside editing',
                    'Global Building Mode is explicit and wrong-page requests cannot enable it',
                    'Clean and Debug switch facts without changing server authority',
                    'Debug toggle preserves unsaved prompt and server authority',
                    'Game setup owns the sole managed block policy selector',
                    'Block limits modal preserves the menu and Close discards drafts',
                    'Game setup value edit durably changes the selected cap',
                    'Game setup value edit restores the cap without changing placed blocks',
                    'Locations has no policy editor and forged old policy routes are denied',
                    'deleting all manual references cannot free physical quota',
                    'saved Regions remain independent geometry without obsolete reverse use assignment',
                    'generic opener restored the Region page and validated selected record',
                    'server requests preserve enabled button identity and ignore duplicate navigation',
                    'closing during pending navigation stays closed after the actual response',
                    'native Create saves and selects an empty custom table without filling chests',
                    'new custom table saves item probabilities and lore through the shared pickers',
                    'duplicate loot table creation preserves the table and popup fields',
                    'custom loot table survives authoritative config reload',
                    'forged creation outside Editing is refused without a config write'}
        missing = required.difference(operator.get('checks', []))
        if missing:
            raise RuntimeError('Menu probe ended before required assertions: ' + repr(sorted(missing)))
        player = _read_result(reports / 'role-B.json')
        if 'forged non-operator global Building Mode request is denied' not in player.get('checks', []):
            raise RuntimeError('Missing ordinary-player global Building Mode denial proof')
        report['checks'].append('operator and non-operator native menu probes passed')
        for _, logfile in clients:
            logfile.close()
        clients = []

        # Run the same disposable operator probe at the other logical menu
        # sizes.  Separate evidence folders avoid clobbering result/screenshot
        # names and retain the exact launch dimensions for inspection.
        for label, width, height in [('operator-480', 960, 540), ('operator-640', 1280, 720), ('operator-wide-short', 1920, 574)]:
            layout = reports / label
            layout.mkdir()
            clients = [_client(w, launch, root, layout, 'A', label, width, height, layout_only=True)]
            fabric_smoke._await_clients(clients, [layout / 'role-A.json'])
            clients[0][1].close(); clients = []
            report['checks'].append(label + ': native menu capture passed')

        fabric_smoke._stop(server); server = None
        server_log.close(); server_log = None
        report['checks'].append('clean disposable server stop')
        # Reboot the exact disposable world and read the new table through normal menu transport.
        _reserve_ports()
        restart_log = reports / 'server-restart.log'
        server_log = restart_log.open('w', encoding='utf-8')
        server = subprocess.Popen(w.server_command(), cwd=root, env=w.env(), stdin=subprocess.PIPE,
                                  stdout=server_log, stderr=subprocess.STDOUT, text=True, encoding='utf-8')
        _wait_ready(server, restart_log)
        restart = reports / 'restart'
        restart.mkdir()
        clients = [_client(w, launch, root, restart, 'A', 'restart', 1280, 720, layout_only=True)]
        fabric_smoke._await_clients(clients, [restart / 'role-A.json'])
        result = _read_result(restart / 'role-A.json')
        if 'saved custom loot table remains available with item and lore entries' not in result.get('checks', []):
            raise RuntimeError('Restart did not verify the saved custom loot table')
        clients[0][1].close(); clients = []
        fabric_smoke._stop(server); server = None
        server_log.close(); server_log = None
        report['checks'].append('custom loot table and its item/lore entries survived a clean server restart')
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
