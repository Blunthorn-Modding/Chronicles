"""Disposable native CF22 Event editor smoke.

The Java probe drives actual Fabric menu packets against a fresh offline loopback world.
It never reuses a play world, credentials, map, or server port.
"""
from __future__ import annotations

import json
from pathlib import Path
import socket
import subprocess
import time

import fabric_smoke


def _reserve_ports() -> None:
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', 25575))
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
        probe.bind(('127.0.0.1', 24455))


def _ready(server: subprocess.Popen, log: Path) -> None:
    deadline = time.monotonic() + 240
    while server.poll() is None and time.monotonic() < deadline:
        if log.exists() and 'Done (' in log.read_text(encoding='utf-8', errors='replace'):
            return
        time.sleep(.2)
    raise RuntimeError('Event editor disposable server did not become ready: ' + str(log))


def _read(path: Path) -> dict:
    if not path.is_file():
        raise RuntimeError('Event editor client produced no result: ' + str(path))
    result = json.loads(path.read_text(encoding='utf-8'))
    if result.get('result') != 'PASS':
        raise RuntimeError('Event editor smoke failed: ' + json.dumps(result, indent=2))
    return result


def run() -> None:
    import workspace as w
    w.require_eula()
    w.validate_server_runtime()
    w.validate_managed_mod_names()
    w.run([w.REPO / 'gradlew.bat', '--no-daemon', ':core:remapJar', ':campaigns:quiet-below:remapJar', ':campaigns:quiet-below:writeSmokeClientLaunch'])
    launch = json.loads((w.REPO / 'campaigns/quiet-below/build/smoke/client-launch.json').read_text(encoding='utf-8'))
    root = fabric_smoke._stage(w)
    # The test-only trial command refuses to prepare without this marker.
    (root / '.event-actions-fixture').write_text('disposable CF22 native UI fixture\n', encoding='utf-8')
    # Minecraft's profile name limit is sixteen characters; keep this aligned with the Java probe.
    account = 'ChronicleEventUI'
    (root / 'ops.json').write_text(json.dumps([{
        'uuid': fabric_smoke.offline_uuid(account), 'name': account, 'level': 2, 'bypassesPlayerLimit': False,
    }], indent=2), encoding='utf-8')
    reports = w.REPORTS / ('event-editor-smoke-' + root.name)
    reports.mkdir(parents=True)
    report = {'world': str(root), 'reports': str(reports), 'result': 'FAIL', 'checks': []}
    server = None
    server_log = None
    client = None
    client_log = None
    try:
        _reserve_ports()
        server_log = (reports / 'server.log').open('w', encoding='utf-8')
        environment = w.env()
        environment['CHRONICLE_EVENTS_TRIAL'] = 'true'
        server = subprocess.Popen(w.server_command(), cwd=root, env=environment, stdin=subprocess.PIPE,
                                  stdout=server_log, stderr=subprocess.STDOUT, text=True, encoding='utf-8')
        _ready(server, reports / 'server.log')
        variables = {
            'CHRONICLE_EVENT_EDITOR_SMOKE': str(reports),
            'CHRONICLE_EVENT_EDITOR_SERVER_DIR': str(root),
            'CHRONICLE_MENU_WIDTH': '640', 'CHRONICLE_MENU_HEIGHT': '480',
            'CHRONICLE_EVENTS_TRIAL': 'true',
        }
        client, client_log = fabric_smoke._client(w, launch, root, reports, account, 'event-editor-320', variables)
        fabric_smoke._await_clients([(client, client_log)], [reports / 'event-editor.json'])
        client_log.close(); client_log = None
        result = _read(reports / 'event-editor.json')
        required = {
            'opened native Chronicle menu',
            'Create opens the native four-page Event editor and Cancel remains local without a write',
            'media preview works before audience and trigger are configured',
            'selected a registered optional prerequisite flag',
            'added first Region Saved state through shared picker',
            'added second Region Saved state',
            'duplicate Region target was rejected',
            'duplicate Region target is refused and a target can be removed',
            'native create/save returns to the updated selected Event without reopening a stale editor',
        }
        missing = required.difference(result.get('checks', []))
        if missing:
            raise RuntimeError('Native Event editor probe missed assertions: ' + repr(sorted(missing)))
        report['checks'].append('real native editor/picker/create transport checks passed')
        fabric_smoke._stop(server); server = None
        server_log.close(); server_log = None
        report['checks'].append('clean disposable server stop')
        report['result'] = 'PASS'
        print('PASS: native Event editor smoke; evidence:', reports, flush=True)
    except BaseException as error:
        report['error'] = str(error)
        raise
    finally:
        if client is not None and client.poll() is None:
            client.terminate()
            try:
                client.wait(timeout=15)
            except subprocess.TimeoutExpired:
                client.kill(); client.wait(timeout=15)
        if client_log is not None:
            client_log.close()
        fabric_smoke._stop(server)
        if server_log is not None:
            server_log.close()
        payload = json.dumps(report, indent=2) + '\n'
        (reports / 'report.json').write_text(payload, encoding='utf-8')
        (w.REPORTS / 'event-editor-latest.json').write_text(payload, encoding='utf-8')


if __name__ == '__main__':
    run()
