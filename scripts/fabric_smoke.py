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


def _java_arg(value):
    value = str(value)
    if any(char in value for char in '\r\n\0'):
        raise ValueError('Control character in Java launch argument')
    return '"' + value.replace('\\', '\\\\').replace('"', '\\"') + '"'


def _client(w, launch, root, reports, name, label, variables):
    directory = root / ('client-' + label)
    directory.mkdir()
    render_distance = 8 if variables.get('CHRONICLE_STAGE_PHASE') == 'pilot' else 3
    (directory / 'options.txt').write_text('\n'.join([
        f'renderDistance:{render_distance}', 'simulationDistance:3', 'maxFps:30', 'enableVsync:false',
        'guiScale:2', 'tutorialStep:none', 'onboardAccessibility:false', 'lang:en_us', 'pauseOnLostFocus:false',
        'soundCategory_master:0.0', ''
    ]), encoding='utf-8')
    jvm = [arg for arg in launch['jvmArgs'] if not arg.startswith(('-Xmx', '-Xms'))]
    arguments = jvm + ['-Xms256M', '-Xmx2G', '-cp', os.pathsep.join(launch['classpath']),
                       launch['mainClass']] + launch['args'] + [
                           '--username', name, '--gameDir', str(directory), '--width', '640', '--height', '480']
    argfile = directory / 'java-arguments.txt'
    argfile.write_text('\n'.join(map(_java_arg, arguments)), encoding='utf-8')
    environment = w.env()
    for key in list(environment):
        if key.startswith(('CHRONICLE_UI_', 'CHRONICLE_FABRIC_', 'CHRONICLE_CONTENT_', 'CHRONICLE_STAGE_', 'CHRONICLE_PILOT_')):
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
            server = subprocess.Popen(w.server_command(), cwd=root, env=w.env(), stdin=subprocess.PIPE,
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
