"""Disposable native CF22 Event editor smoke.

The Java probe drives actual Fabric menu packets against a fresh offline loopback world.
It never reuses a play world, credentials, map, or server port.
"""
from __future__ import annotations

import json
import os
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


def _window_size() -> tuple[str, str, str]:
    """Return the physical window requested for one supported logical editor frame."""
    width = os.environ.get('CHRONICLE_EVENT_EDITOR_WIDTH', '640')
    height = os.environ.get('CHRONICLE_EVENT_EDITOR_HEIGHT', '480')
    logical = {
        ('640', '480'): '320x240',
        ('1280', '720'): '640x360',
        ('960', '960'): '480x480',
    }.get((width, height))
    if logical is None:
        raise RuntimeError(
            'CHRONICLE_EVENT_EDITOR_WIDTH/HEIGHT must be one supported physical pair: '
            '640x480 (logical 320x240), 1280x720 (logical 640x360), or 960x960 (logical 480x480)'
        )
    return width, height, logical


def run() -> None:
    import workspace as w
    w.require_eula()
    w.validate_server_runtime()
    w.validate_managed_mod_names()
    w.run([w.REPO / 'gradlew.bat', '--no-daemon', ':core:remapJar', ':campaigns:quiet-below:remapJar', ':campaigns:quiet-below:writeSmokeClientLaunch'])
    launch = json.loads((w.REPO / 'campaigns/quiet-below/build/smoke/client-launch.json').read_text(encoding='utf-8'))
    width, height, logical_size = _window_size()
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
    report = {'world': str(root), 'reports': str(reports), 'physical_size': width + 'x' + height,
              'logical_size': logical_size, 'result': 'FAIL', 'checks': []}
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
            'CHRONICLE_MENU_WIDTH': width, 'CHRONICLE_MENU_HEIGHT': height,
            'CHRONICLE_EVENTS_TRIAL': 'true',
        }
        client, client_log = fabric_smoke._client(w, launch, root, reports, account, 'event-editor-' + logical_size, variables)
        fabric_smoke._await_clients([(client, client_log)], [reports / 'event-editor.json'])
        client_log.close(); client_log = None
        result = _read(reports / 'event-editor.json')
        required = {
            'opened native Chronicle menu',
            'Up Down reaches every trigger across server pages without separate page controls',
            'Up returns through prior pages and search resets scrolling without losing entries',
            'Create opens the Event flow and Cancel remains local without a write',
            'captured When section',
            'captured Requirements section',
            'captured Delay section',
            'captured flag action editor',
            'captured text action editor',
            'captured sound action editor',
            'selected Book found source through the shared picker',
            'selected Region applied source',
            'typed activity picker selects and clears a specific activity',
            'selected Book opened source',
            'selected flag-only source outcome',
            'Book opened target and flag-only outcome survive save and reopen',
            'selected the flags-On trigger through the shared picker',
            'selected the newly created prerequisite flag',
            'selected a second prerequisite flag',
            'prerequisite picker Cancel preserves the draft',
            'selected the first flag outcome',
            'selected the second flag outcome',
            'selected a Region audience through the shared picker',
            'private preview leaves actions and prerequisites untouched',
            'selected a registered sound through the shared picker and explicit Add',
            'added first Region Saved state through shared picker',
            'added second Region Saved state',
            'action list scrolls beyond four visible rows',
            'duplicate Region target was rejected',
            'duplicate Region target is refused and a target can be removed',
            'native flag-only create returns to the selected Event',
            'two prerequisites, On and Off outcomes, and condition delay survive flag-only save',
            'stale child picker locks while preserving the draft and Close remains usable',
            'flag Cancel and duplicate preserve state; creation persists Off with define audit',
            'stale flag draft locks after Editing revoked; confirmed live On and Off persist while active creation is disabled',
            'Events filters separate Game Events and direct Region Changes and remember selections, including close and reopen',
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
