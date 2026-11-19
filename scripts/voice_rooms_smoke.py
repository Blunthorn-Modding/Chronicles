"""Opt-in disposable two-player voice-room server trial; retains world and logs."""
from __future__ import annotations

import json
import os
from pathlib import Path
import socket
import subprocess
import shutil
import time

import fabric_smoke as fabric
import workspace as w
from council_conflicts_smoke import Driver


def wait_for(path: Path, predicate, process, timeout: int, message: str):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        text = path.read_text(encoding='utf-8', errors='replace') if path.exists() else ''
        if predicate(text):
            return text
        if process.poll() is not None:
            break
        time.sleep(0.2)
    raise RuntimeError(message + ': ' + str(path))


def native_ui(driver, reports):
    """Exercise the real shared Region-subtype picker, including local Cancel."""
    def click(label):
        driver.wait('A', lambda state: any(b['label'] == label and b['active'] for b in state.get('buttons', [])), label)
        driver.do('A', 'click', label=label)
    driver.do('A', 'resize', width=1280, height=900)
    driver.chat('A', 'chronicle')
    driver.wait('A', lambda state: state.get('screen') == 'CoreMenuScreen', 'Core menu')
    for _ in range(12):
        if any(b['label'] == 'Regions' and b['active'] for b in driver.state('A').get('buttons', [])):
            click('Regions'); break
        click('v')
    else:
        raise RuntimeError('Regions navigation unavailable')
    driver.do('A', 'coreSelect', value='room_a')
    time.sleep(.5)
    driver.do('A', 'coreTrigger', label='Choose subtypes')
    click('[x] Voice room')
    click('Cancel')
    driver.wait('A', lambda state: state.get('screen') == 'CoreMenuScreen', 'cancelled subtype draft')
    time.sleep(.5)
    driver.do('A', 'coreTrigger', label='Choose subtypes')
    driver.wait('A', lambda state: any(b['label'] == '[x] Voice room' for b in state.get('buttons', [])), 'Cancel retained assigned subtype')
    for width, height, name in ((1280, 900, 'voice-subtype-normal.png'), (640, 480, 'voice-subtype-compact.png')):
        driver.do('A', 'resize', width=width, height=height)
        time.sleep(.5)
        driver.do('A', 'capture', name=name)
    click('[x] Voice room'); click('Apply')
    driver.wait('A', lambda state: state.get('screen') == 'CoreMenuScreen', 'disabled voice subtype')
    time.sleep(.5)
    driver.do('A', 'coreTrigger', label='Choose subtypes')
    click('[ ] Voice room'); click('Apply')
    driver.wait('A', lambda state: state.get('screen') == 'CoreMenuScreen', 'enabled voice subtype')
    time.sleep(.5)
    driver.do('A', 'coreTrigger', label='Choose subtypes')
    driver.wait('A', lambda state: any(b['label'] == '[x] Voice room' for b in state.get('buttons', [])), 'persisted voice subtype')
    click('Cancel')
    driver.do('A', 'close')


def run():
    w.require_eula()
    w.validate_server_runtime()
    root = fabric._stage(w)
    (root / '.voice-rooms-fixture').write_text('disposable voice rooms fixture\n', encoding='utf-8')
    (root / '.council-conflicts-fixture').write_text('Enables the existing loopback-only native client driver\n', encoding='utf-8')
    launch = json.loads((w.REPO / 'campaigns/last-council/build/smoke/client-launch.json').read_text(encoding='utf-8'))
    # Use the same content registry as the existing native Council client driver.
    # The room feature itself is Core-owned and does not require House participation.
    properties = dict(line.split('=', 1) for line in (w.REPO / 'gradle.properties').read_text(encoding='utf-8').splitlines() if '=' in line)
    council = w.REPO / 'campaigns/last-council/build/libs' / ('last_council-' + properties['mod_version'].strip() + '.jar')
    w.validate_mod_target(council)
    for jar in (root / 'mods').glob('quiet_below-*.jar'): jar.unlink()
    for jar in (root / 'mods').glob('last_council-*.jar'): jar.unlink()
    shutil.copy2(council, root / 'mods' / council.name)
    reports = w.REPORTS / ('voice-rooms-' + root.name)
    reports.mkdir(parents=True)
    report = {'result': 'FAIL', 'world': str(root), 'checks': [], 'limitations': [
        'Drives production event handlers with proxy events and real API connections; no microphone or UDP transport is tested.',
        'Two native Fabric clients exercise the real server; microphone and UDP audibility remain unverified.',
        'No tamper recovery, real client packet injection, or human acceptance is claimed.']}
    server = None
    clients = []
    try:
        for phase in ('initial', 'restart', 'without_voice'):
            if phase == 'without_voice':
                jars = list((root / 'mods').glob('voicechat-fabric-*.jar'))
                if len(jars) != 1:
                    raise RuntimeError('Expected exactly one fixture Simple Voice Chat jar')
                jars[0].unlink()
            for port, kind in ((25575, socket.SOCK_STREAM), (24455, socket.SOCK_DGRAM)):
                with socket.socket(socket.AF_INET, kind) as probe:
                    probe.bind(('127.0.0.1', port))
            server_path = reports / ('server-' + phase + '.log')
            directory = reports / phase
            directory.mkdir()
            with server_path.open('w', encoding='utf-8') as server_log:
                environment = w.env()
                environment['CHRONICLE_VOICE_ROOMS_TRIAL'] = 'true'
                server = subprocess.Popen(w.server_command(), cwd=root, env=environment, stdin=subprocess.PIPE,
                                          stdout=server_log, stderr=subprocess.STDOUT, text=True, encoding='utf-8')
                wait_for(server_path, lambda s: 'Done (' in s and (phase == 'without_voice' or 'Voice chat server started at 127.0.0.1:24455' in s),
                         server, 240, 'Server startup failed')
                client_launch = dict(launch)
                if phase != 'without_voice':
                    voice_jars = list((root / 'mods').glob('voicechat-fabric-*.jar'))
                    if len(voice_jars) != 1: raise RuntimeError('Expected the locked voice runtime for native clients')
                    client_launch['jvmArgs'] = [*launch['jvmArgs'], '-Dfabric.addMods=' + str(voice_jars[0])]
                for role in 'AB':
                    clients.append(fabric._client(w, client_launch, root, directory, 'ChronicleFabric' + role,
                        phase + '-voice-' + role, {'CHRONICLE_CONFLICT_PROBE': str(directory),
                        'CHRONICLE_CONFLICT_ROOT': str(root), 'CHRONICLE_CONFLICT_ROLE': role,
                        'CHRONICLE_MENU_WIDTH': '640', 'CHRONICLE_MENU_HEIGHT': '480'}))
                driver = Driver(directory, clients)
                for role in 'AB':
                    driver.wait(role, lambda state: bool(state.get('playerId')), 'native voice fixture joined', timeout=180)
                steps = (('prepare', 'same', 'different', 'outside', 'gates', 'race') if phase == 'initial'
                         else ('restart', 'remove', 'first_room_race') if phase == 'restart' else ('without_voice',))
                for step in steps:
                    server.stdin.write('execute as ChronicleFabricA at ChronicleFabricA run chronicle_voice_rooms_trial ' + step + '\n')
                    server.stdin.flush()
                    marker = 'Voice rooms trial PASS ' + step
                    failure = 'Voice rooms trial FAIL ' + step
                    output = wait_for(server_path, lambda s: marker in s or failure in s, server, 45, 'Trial timed out: ' + step)
                    if failure in output:
                        raise RuntimeError('Trial failed: ' + step + '; inspect ' + str(server_path))
                    report['checks'].append(phase + ': ' + step)
                    if phase == 'initial' and step == 'prepare':
                        native_ui(driver, reports)
                        report['checks'].append('Native Regions subtype: Cancel, disable, enable, reopen; normal/compact captures')
                for role in 'AB': driver.do(role, 'stop')
                for process, handle in clients:
                    process.wait(timeout=30)
                    handle.close()
                clients.clear()
                fabric._stop(server)
                server = None
        report['result'] = 'PASS'
    finally:
        for process, handle in clients:
            if process.poll() is None:
                process.terminate()
                process.wait(timeout=30)
            handle.close()
        if server is not None:
            fabric._stop(server)
        (reports / 'report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    run()
