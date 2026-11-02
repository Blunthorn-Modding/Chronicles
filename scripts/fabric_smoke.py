"""Real matching Fabric clients, isolated worlds and explicit result files."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import time
import uuid
from datetime import datetime, timezone


def offline_uuid(name):
    raw = bytearray(hashlib.md5(('OfflinePlayer:' + name).encode(), usedforsecurity=False).digest())
    raw[6] = (raw[6] & 15) | 48
    raw[8] = (raw[8] & 63) | 128
    return str(uuid.UUID(bytes=bytes(raw)))


def _stage(w):
    root = w.WORK / 'test-runs' / datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    root.mkdir(parents=True)
    for jar in w.artifacts():
        w.validate_mod_target(jar)
    for name in ('fabric-server-launch.jar', 'fabric-server-launcher.properties', 'server.jar', 'eula.txt'):
        shutil.copy2(w.SERVER / name, root / name)
    for name in ('libraries', 'mods'):
        shutil.copytree(w.SERVER / name, root / name)
    for prefix in ('chronicle_core-', 'quiet_below-'):
        for copied in (root / 'mods').glob(prefix + '*.jar'):
            copied.unlink()
    for jar in w.artifacts():
        shutil.copy2(jar, root / 'mods' / jar.name)
    (root / 'server.properties').write_text('\n'.join([
        'server-ip=127.0.0.1', 'server-port=25575', 'online-mode=false', 'white-list=false',
        'enable-rcon=false', 'enable-query=false', 'view-distance=3', 'simulation-distance=3',
        'spawn-protection=0', 'level-type=minecraft:flat', 'generate-structures=false',
        'gamemode=creative', 'difficulty=peaceful', 'level-name=world', 'max-players=4',
        'sync-chunk-writes=true', 'pause-when-empty-seconds=0', ''
    ]), encoding='utf-8')
    (root / 'ops.json').write_text(json.dumps([{
        'uuid': offline_uuid('ChronicleFabricA'), 'name': 'ChronicleFabricA',
        'level': 2, 'bypassesPlayerLimit': False
    }]), encoding='utf-8')
    voice = root / 'config' / 'voicechat'
    voice.mkdir(parents=True)
    (voice / 'voicechat-server.properties').write_text('bind_address=127.0.0.1\nport=24455\n', encoding='utf-8')
    return root


def _loot_fixture(root):
    loot = root / 'config/chronicle'
    loot.mkdir(parents=True)
    # Explicit test overrides, never copied from or applied to the play server.
    def loot_type(identifier, label, interval, entries):
        return {'id': identifier, 'label': label, 'description': 'Disposable deterministic test type',
                'recommendedChests': 2, 'refillMinutes': interval, 'entries': entries}
    def entry(item, chance, count, components=None):
        result = {'item': item, 'chancePercent': chance, 'minCount': count, 'maxCount': count}
        if components is not None:
            result['components'] = components
        return result
    (loot / 'chest-loot.json').write_text(json.dumps({'schemaVersion': 1, 'types': [
        loot_type('quiet_below:settlement_supplies', 'Fixture supplies', 0, [
            entry('minecraft:bread', 100, 4, {'minecraft:custom_name': 'Typed supply'}),
            entry('minecraft:diamond', 0, 1)]),
        loot_type('test:timed', 'Timed test cache', 0.05, [entry('minecraft:coal', 100, 2)]),
        loot_type('test:empty', 'All-miss cache', 0, [entry('minecraft:diamond', 0, 1)])
    ]}), encoding='utf-8')


def _m8_fixture(root):
    """A valid inactive campaign flag makes the optional condition available to the UI.

    The real command may only edit flags during ACTIVE play, while M8 bindings must be
    authored while inactive.  This isolated world starts with one false test flag and no
    history; the graphical client later proves the cue waits until ACTIVE changes it.
    """
    chronicle = root / 'world/chronicle'
    chronicle.mkdir(parents=True, exist_ok=True)
    (chronicle / 'campaign-state.json').write_text(json.dumps({
        'schemaVersion': 1, 'sessionNumber': 0, 'phase': 'INACTIVE',
        'flags': {'quiet_below:m8_gate': False}, 'completedEvents': [],
        'history': [], 'nextSequence': 0
    }), encoding='utf-8')


def _java_arg(value):
    value = str(value)
    if any(char in value for char in '\r\n\0'):
        raise ValueError('Control character in Java launch argument')
    return '"' + value.replace('\\', '\\\\').replace('"', '\\"') + '"'


def _client(w, launch, root, reports, name, label, variables):
    directory = root / ('client-' + label)
    directory.mkdir()
    render_distance = 8 if variables.get('CHRONICLE_STAGE_PHASE') == 'pilot' else 3
    import client_defaults
    for relative_name, contents in client_defaults.payloads(w.WORK, w.LOCK['minecraft_protocol']).items():
        destination = directory / relative_name
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(contents)
    (directory / 'options.txt').write_text(w.client_options() + '\n' + '\n'.join([
        f'renderDistance:{render_distance}', 'simulationDistance:3', 'maxFps:30', 'enableVsync:false',
        'tutorialStep:none', 'lang:en_us', 'pauseOnLostFocus:false',
        'soundCategory_master:0.0', ''
    ]), encoding='utf-8')
    temporary = directory / 'tmp'
    temporary.mkdir()
    jvm = [arg for arg in launch['jvmArgs'] if not arg.startswith(('-Xmx', '-Xms', '-Djava.io.tmpdir=', '-Djna.tmpdir='))]
    jvm.extend(['-Djava.io.tmpdir=' + str(temporary), '-Djna.tmpdir=' + str(temporary)])
    width = int(variables.get('CHRONICLE_MENU_WIDTH', '640'))
    height = int(variables.get('CHRONICLE_MENU_HEIGHT', '480'))
    if not 320 <= width <= 2560 or not 240 <= height <= 1440:
        raise ValueError('Invalid disposable smoke window size')
    arguments = jvm + ['-Xms256M', '-Xmx2G', '-cp', os.pathsep.join(launch['classpath']),
                       launch['mainClass']] + launch['args'] + [
                           '--username', name, '--gameDir', str(directory), '--width', str(width), '--height', str(height)]
    argfile = directory / 'java-arguments.txt'
    argfile.write_text('\n'.join(map(_java_arg, arguments)), encoding='utf-8')
    environment = w.env()
    for key in list(environment):
        if key.startswith(('CHRONICLE_UI_', 'CHRONICLE_FABRIC_', 'CHRONICLE_CONTENT_', 'CHRONICLE_STAGE_', 'CHRONICLE_PILOT_', 'CHRONICLE_MENU_')):
            environment.pop(key)
    environment.update(variables)
    logfile = (reports / (label + '.log')).open('w', encoding='utf-8')
    try:
        process = subprocess.Popen([str(w.JAVA), '@' + str(argfile)], cwd=directory,
                                   env=environment, stdout=logfile, stderr=subprocess.STDOUT)
    except BaseException:
        logfile.close()
        raise
    return process, logfile


def _await_clients(clients, results, timeout=300):
    deadline = time.monotonic() + timeout
    while any(process.poll() is None for process, _ in clients):
        for (process, _), result in zip(clients, results):
            if process.poll() is not None and (process.returncode or not result.is_file()):
                raise RuntimeError(f'Fabric client exited {process.returncode}; missing or failed {result}')
        for result in results:
            if result.exists():
                data = json.loads(result.read_text(encoding='utf-8'))
                if data.get('result') == 'FAIL':
                    raise RuntimeError(f'Fabric client check failed: {data}')
        if time.monotonic() > deadline:
            raise RuntimeError('Fabric clients timed out; inspect their isolated logs')
        time.sleep(0.2)
    for (process, _), result in zip(clients, results):
        if process.returncode or not result.is_file():
            raise RuntimeError(f'Fabric client exited {process.returncode}; missing or failed {result}')
        data = json.loads(result.read_text(encoding='utf-8'))
        if data.get('result') != 'PASS':
            raise RuntimeError(f'Fabric client did not pass: {data}')


def _stop(server):
    if server is None or server.poll() is not None:
        return
    try:
        server.stdin.write('stop\n')
        server.stdin.flush()
        server.wait(timeout=90)
    except (OSError, subprocess.TimeoutExpired):
        server.kill()
        server.wait(timeout=15)
        raise RuntimeError('Test server did not stop cleanly; inspect its retained world/logs')
    if server.returncode:
        raise RuntimeError(f'Test server exited {server.returncode}')


def run(include_content=True, regression=True):
    import workspace as w
    w.require_eula()
    w.validate_server_runtime()
    w.validate_managed_mod_names()
    # Compile/configure once. Direct Java children avoid competing long-running
    # Gradle tasks holding a shared project lock while waiting for each other.
    w.run([w.REPO / 'gradlew.bat', '--no-daemon', ':campaigns:quiet-below:writeSmokeClientLaunch'])
    launch = json.loads((w.REPO / 'campaigns/quiet-below/build/smoke/client-launch.json').read_text())
    root = _stage(w)
    if regression:
        _loot_fixture(root)
        _m8_fixture(root)
    reports = w.REPORTS / ('fabric-smoke-' + root.name)
    reports.mkdir(parents=True)
    report = {'world': str(root), 'reports': str(reports), 'minecraft': w.LOCK['minecraft'],
              'minecraft_protocol': w.LOCK['minecraft_protocol'], 'result': 'FAIL', 'checks': []}
    server = None
    clients = []
    server_log = None
    try:
        phases = ('initial', 'restart') if regression else ('content',)
        for phase in phases:
            with socket.socket() as probe:
                probe.bind(('127.0.0.1', 25575))
            with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
                probe.bind(('127.0.0.1', 24455))
            log_path = reports / ('server-' + phase + '.log')
            server_log = log_path.open('w', encoding='utf-8')
            server_env = w.env()
            if regression:
                server_env.update(CHRONICLE_FABRIC_SMOKE=str(reports), CHRONICLE_FABRIC_SERVER_DIR=str(root))
            server = subprocess.Popen(w.server_command(), cwd=root, env=server_env, stdin=subprocess.PIPE,
                                      stdout=server_log, stderr=subprocess.STDOUT, text=True, encoding='utf-8')
            deadline = time.monotonic() + 240
            while True:
                content = log_path.read_text(encoding='utf-8', errors='replace')
                if 'Done (' in content and 'Voice chat server started at 127.0.0.1:24455' in content:
                    break
                if server.poll() is not None or time.monotonic() > deadline:
                    raise RuntimeError('Isolated Fabric server failed startup; inspect ' + str(log_path))
                time.sleep(0.2)
            if regression:
                clients = [_client(w, launch, root, reports, 'ChronicleFabric' + role, role + '-' + phase, {
                    'CHRONICLE_FABRIC_SMOKE': str(reports), 'CHRONICLE_FABRIC_ROLE': role,
                    'CHRONICLE_FABRIC_PHASE': phase, 'CHRONICLE_FABRIC_SERVER_DIR': str(root)
                }) for role in ('A', 'B')]
                _await_clients(clients, [reports / f'role-{role}-{phase}.json' for role in ('A', 'B')])
                report['checks'].append(phase + ': GM/non-op matching Fabric clients passed M1/M2 checks')
                authoring = json.loads((root / 'world/chronicle/authoring.json').read_text(encoding='utf-8'))
                sites = {site['id']: site for site in authoring['sites']}
                if set(sites) != {'smoke_city', 'smoke_arrival', 'smoke_chest'}:
                    raise RuntimeError('M6 authoring registry IDs changed or were not durably retained')
                if (sites['smoke_city'].get('type') != 'region' or sites['smoke_arrival'].get('type') != 'location'
                        or sites['smoke_chest'].get('type') != 'anchor'):
                    raise RuntimeError('M6 authoring registry geometry types were not durably retained')
                if sites['smoke_city']['min']['x'] != 16 or sites['smoke_city']['max']['x'] != 19:
                    raise RuntimeError('M6 Region edit geometry was not durably retained')
                if sites['smoke_arrival']['x'] != 16.5 or sites['smoke_arrival']['yaw'] != 90:
                    raise RuntimeError('M6 Point position/facing was not durably retained')
                if sites['smoke_chest'].get('expectedBlock') != 'minecraft:chest':
                    raise RuntimeError('M6 block-site expectation was not durably retained')
                report['checks'].append(phase + ': canonical M6 region/location commands, permissions and durable geometry checks passed')
                chest = json.loads((root / 'world/chronicle/chest-content.json').read_text(encoding='utf-8'))
                if len(chest['templates']) != 1 or len(chest['bindings']) != 4 or len(chest['receipts']) != 4 or chest['pending']:
                    raise RuntimeError('M7 chest templates/receipts/pending persistence mismatch')
                if 'Chronicle supply' not in chest['templates'][0]['contents']['stacks'][0]:
                    raise RuntimeError('M7 item components lost in captured template')
                spawns = json.loads((root / 'world/chronicle/spawns.json').read_text(encoding='utf-8'))
                if len(spawns['bindings']) != 1 or spawns['bindings'][0]['locationId'] != 'smoke_arrival':
                    raise RuntimeError('M7 spawn binding persistence mismatch')
                automatic = json.loads((root / 'world/chronicle/automatic-spawns.json').read_text(encoding='utf-8'))
                if len(automatic['policies']) != 1 or automatic['policies'][0]['player'] != offline_uuid('ChronicleFabricA'):
                    raise RuntimeError('M7 automatic policy/self-leave persistence mismatch')
                expected_arrival = 'MOVED' if phase == 'initial' else 'CLAIMED'
                if len(automatic['arrivals']) != 1 or automatic['arrivals'][0]['status'] != expected_arrival:
                    raise RuntimeError('M7 once/uncertain automatic arrival history mismatch')
                play = json.loads((root / 'world/chronicle/play-runtime.json').read_text(encoding='utf-8'))
                if play.get('schemaVersion') != 3 or len(play.get('participants', [])) != 2 or len(play.get('bindings', [])) != 2:
                    raise RuntimeError('M8 explicit participation/binding persistence mismatch')
                deliveries = {delivery['id']: delivery for delivery in play.get('deliveries', [])}
                expected_city = 'DELIVERED' if phase == 'initial' else 'CLAIMED'
                if set(deliveries) != {'quiet_below:city_presence', 'quiet_below:distant_stir'} or deliveries['quiet_below:city_presence']['status'] != expected_city or deliveries['quiet_below:distant_stir']['status'] != 'DELIVERED':
                    raise RuntimeError('M8 observation completion/uncertain-claim persistence mismatch')
                if deliveries['quiet_below:distant_stir'].get('source') != 'TIMER':
                    raise RuntimeError('M8 reset timer did not replace the earlier manual delivery')
                for delivery in deliveries.values():
                    if set(delivery.get('recipients', [])) != {offline_uuid('ChronicleFabricA'), offline_uuid('ChronicleFabricB')}:
                        raise RuntimeError('M8 observation audience persistence mismatch')
                report['checks'].append(phase + ': typed loot session/timer replacement, components/cycle persistence and legacy/spawn checks passed')
                report['checks'].append(phase + ': M8 opt-in participants, configured cues and durable delivery audience checks passed')
                state = json.loads((root / 'world/chronicle/campaign-state.json').read_text())
                expected = ('PAUSED', 1) if phase == 'initial' else ('ENDED', 2)
                if (state['phase'], state['sessionNumber']) != expected:
                    raise RuntimeError('Unexpected campaign state after Fabric client phase')
                if any(entry['actor'].get('uuid') != offline_uuid('ChronicleFabricA') for entry in state['history']):
                    raise RuntimeError('Non-GM or failed campaign mutations entered audit history')
                for role in ('A', 'B'):
                    card = json.loads((root / 'world/chronicle/profiles' / (offline_uuid('ChronicleFabric' + role) + '.json')).read_text())
                    if card['name'] != 'Fabric ' + role or card['revision'] != 1:
                        raise RuntimeError('Fabric profile identity/revision persistence mismatch')
            if include_content and phase == phases[-1]:
                content_dir = reports / 'content'
                content_dir.mkdir()
                clients.append(_client(w, launch, root, reports, 'ChronicleFabricA', 'content', {
                    'CHRONICLE_CONTENT_SMOKE': str(content_dir)
                }))
                _await_clients([clients[-1]], [content_dir / 'content-smoke.json'])
                report['checks'].append('M3: real survival placement/mining/loot, gated clues and model screenshots passed')
            _stop(server)
            server_log.close()
            server_log = None
            report['checks'].append(phase + ': clean server save/stop')
            if regression and phase == 'initial':
                # Disposable, stopped fixture: model world-save success followed by
                # interruption before the content receipt was committed.
                content_file = root / 'world/chronicle/chest-content.json'
                chest = json.loads(content_file.read_text(encoding='utf-8'))
                binding = next(binding for binding in chest['bindings'] if binding['id'] == 'smoke_stock')
                anchor = next(site for site in authoring['sites'] if site['id'] == binding['anchorId'])
                contents = chest['templates'][0]['contents']
                chest['receipts'] = [receipt for receipt in chest['receipts'] if receipt['bindingId'] != 'smoke_stock']
                chest['pending'] = [{
                    'operationId': '00000000-0000-0000-0000-000000000007',
                    'actorUuid': offline_uuid('ChronicleFabricA'), 'actorName': 'ChronicleFabricA',
                    'bindingId': binding['id'], 'bindingRevision': binding['revision'], 'sessionNumber': 1, 'cycle': -1,
                    'target': {'anchorId': anchor['id'], 'anchorRevision': anchor['revision'],
                               'dimension': anchor['dimension'], 'x': anchor['position']['x'],
                               'y': anchor['position']['y'], 'z': anchor['position']['z'],
                               'expectedBlock': anchor['expectedBlock']},
                    'before': {'size': contents['size'], 'stacks': ['{}'] * contents['size']},
                    'after': contents, 'createdAt': '2026-10-19T12:00:00Z',
                    'purpose': 'FILL', 'sourceBindingId': None, 'sourceBindingRevision': 0,
                    'sourceTarget': None, 'sourceBefore': None, 'sourceAfter': None, 'bookClaims': []}]
                chest['revision'] += 1
                content_file.write_text(json.dumps(chest), encoding='utf-8')
                automatic_file = root / 'world/chronicle/automatic-spawns.json'
                automatic = json.loads(automatic_file.read_text(encoding='utf-8'))
                automatic['arrivals'][0]['status'] = 'CLAIMED'
                automatic['arrivals'][0]['detail'] = 'Stopped disposable fixture: interrupted before arrival outcome was recorded'
                automatic['revision'] += 1
                automatic_file.write_text(json.dumps(automatic), encoding='utf-8')
                play_file = root / 'world/chronicle/play-runtime.json'
                play = json.loads(play_file.read_text(encoding='utf-8'))
                # Retain the actual v2 outcome, then explicitly construct the old
                # v1 receipt-only fixture to exercise backwards reading on restart.
                # This occurs only after the disposable server has saved/stopped.
                (reports / 'play-v2-before-legacy-fixture.json').write_text(json.dumps(play, indent=2), encoding='utf-8')
                city = next(delivery for delivery in play['deliveries'] if delivery['id'] == 'quiet_below:city_presence')
                city['status'] = 'CLAIMED'
                play['revision'] += 1
                play['schemaVersion'] = 1
                play.pop('events', None)
                play.pop('executions', None)
                play_file.write_text(json.dumps(play), encoding='utf-8')
                report['checks'].append('stopped disposable fixture: pending intent after flushed mutation before receipt')
                report['checks'].append('stopped disposable fixture: observation claim after durable claim before delivery completion')
                report['checks'].append('stopped disposable fixture: retained v2 evidence, then seeded v1 legacy receipts for real restart migration')

            for _, logfile in clients:
                logfile.close()
            clients = []
        report['result'] = 'PASS'
        print('PASS: matching Fabric test clients; evidence:', reports, flush=True)
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
                    process.kill()
                    process.wait(timeout=15)
            logfile.close()
        try:
            _stop(server)
        finally:
            if server_log is not None:
                server_log.close()
            filename = 'smoke-latest.json' if regression else 'content-latest.json'
            (w.REPORTS / filename).write_text(json.dumps(report, indent=2), encoding='utf-8')
