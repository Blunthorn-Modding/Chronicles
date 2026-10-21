"""Real-client CF-11 enchantment-cap rehearsal on a fresh disposable world."""
from __future__ import annotations

import json
from pathlib import Path
import socket
import subprocess
import time
import zipfile


def _wait_ready(server, logfile: Path) -> None:
    deadline = time.monotonic() + 240
    while True:
        content = logfile.read_text(encoding='utf-8', errors='replace') if logfile.exists() else ''
        if 'Done (' in content and 'Voice chat server started at 127.0.0.1:24455' in content:
            return
        if server.poll() is not None or time.monotonic() > deadline:
            raise RuntimeError('Enchantment fixture failed startup: ' + str(logfile))
        time.sleep(.2)


def _reserve_ports() -> None:
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', 25575))
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
        probe.bind(('127.0.0.1', 24455))


def _server_version(server_jar: Path) -> dict:
    with zipfile.ZipFile(server_jar) as archive:
        return json.loads(archive.read('version.json'))


def _write_fixture(root: Path, version: dict) -> None:
    pack = root / 'world' / 'datapacks' / 'chronicle_cap_fixture'
    loot = pack / 'data' / 'test' / 'loot_table' / 'capped.json'
    loot.parent.mkdir(parents=True, exist_ok=True)
    data_format = [version['pack_version']['data_major'], version['pack_version']['data_minor']]
    (pack / 'pack.mcmeta').write_text(json.dumps({'pack': {
        'min_format': data_format, 'max_format': data_format,
        'description': 'Chronicle disposable enchantment-cap fixture',
    }}, indent=2) + '\n', encoding='utf-8')
    loot.write_text(json.dumps({
        'pools': [{
            'rolls': 1,
            'entries': [{
                'type': 'minecraft:item', 'name': 'minecraft:diamond_sword',
                'functions': [{
                    'function': 'minecraft:set_components',
                    'components': {'minecraft:enchantments': {'minecraft:sharpness': 5}},
                }],
            }],
        }],
    }, indent=2) + '\n', encoding='utf-8')
    recipe = pack / 'data' / 'test' / 'recipe' / 'capped.json'
    recipe.parent.mkdir(parents=True)
    recipe.write_text(json.dumps({'type': 'minecraft:crafting_shapeless',
        'ingredients': ['minecraft:iron_nugget'], 'result': {'id': 'minecraft:diamond_sword',
        'components': {'minecraft:enchantments': {'minecraft:sharpness': 5}}}}), encoding='utf-8')


def _run_phase(w, fabric_smoke, launch, root: Path, reports: Path, phase: str) -> None:
    _reserve_ports()
    logfile_path = reports / ('server-' + phase + '.log')
    logfile = logfile_path.open('w', encoding='utf-8')
    server = None
    clients = []
    try:
        environment = w.env()
        environment['CHRONICLE_CAPS_NATIVE_TRIAL'] = 'true'
        server = subprocess.Popen(w.server_command(), cwd=root, env=environment, stdin=subprocess.PIPE,
                                  stdout=logfile, stderr=subprocess.STDOUT, text=True, encoding='utf-8')
        _wait_ready(server, logfile_path)
        variables = {
            'CHRONICLE_CAPS_SMOKE': str(reports),
            'CHRONICLE_CAPS_PHASE': phase,
            'CHRONICLE_CAPS_SERVER_DIR': str(root),
        }
        clients = [fabric_smoke._client(w, launch, root, reports, 'ChronicleFabricA', 'cap-' + phase, variables)]
        fabric_smoke._await_clients(clients, [reports / ('cap-' + phase + '.json')], timeout=300)
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


def _run_invalid_config(w, fabric_smoke, root: Path, reports: Path) -> dict:
    """Prove a malformed cap file aborts startup without rewriting the file."""
    reports.mkdir(parents=True, exist_ok=True)
    config = root / 'config' / 'chronicle' / 'enchantment-caps.json'
    original = b'{"schemaVersion":1,"enabled":false,"defaultMaxLevel":255,"overrides":{},"overrides":{}}\n'
    config.parent.mkdir(parents=True, exist_ok=True)
    config.write_bytes(original)
    _reserve_ports()
    logfile_path = reports / 'server-invalid-config.log'
    logfile = logfile_path.open('w', encoding='utf-8')
    server = None
    ready = False
    rejected = False
    try:
        environment = w.env()
        environment['CHRONICLE_CAPS_NATIVE_TRIAL'] = 'true'
        server = subprocess.Popen(w.server_command(), cwd=root, env=environment, stdin=subprocess.PIPE,
                                  stdout=logfile, stderr=subprocess.STDOUT, text=True, encoding='utf-8')
        deadline = time.monotonic() + 60
        while True:
            content = logfile_path.read_text(encoding='utf-8', errors='replace') if logfile_path.exists() else ''
            ready = 'Done (' in content and 'Voice chat server started at 127.0.0.1:24455' in content
            rejected = rejected or 'Enchantment caps unavailable' in content
            if ready:
                raise RuntimeError('Malformed cap config unexpectedly started a ready server')
            if server.poll() is not None:
                break
            if time.monotonic() > deadline:
                raise RuntimeError('Malformed cap config did not reject startup within 60 seconds')
            time.sleep(.2)
        if not rejected and 'Enchantment caps unavailable' not in logfile_path.read_text(encoding='utf-8', errors='replace'):
            raise RuntimeError('Malformed cap config exited without the expected Enchantment caps unavailable reason')
        return {'root': str(root), 'config': str(config), 'expected': 'startup rejected malformed duplicate cap config',
                'config_preserved': config.read_bytes() == original}
    finally:
        try:
            if server is not None and server.poll() is None:
                fabric_smoke._stop(server)
            if config.exists() and config.read_bytes() != original:
                raise RuntimeError('Malformed cap config was rewritten')
        finally:
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
    _write_fixture(root, _server_version(w.SERVER / 'server.jar'))
    reports = w.REPORTS / ('enchantment-smoke-' + root.name)
    reports.mkdir(parents=True)
    report = {'result': 'FAIL', 'world': str(root), 'reports': str(reports), 'checks': []}
    try:
        _run_phase(w, fabric_smoke, launch, root, reports, 'initial')
        report['checks'].append('initial native client trial passed and stopped cleanly')
        # The same staged world is deliberately reused only after the saving stop.
        _run_phase(w, fabric_smoke, launch, root, reports, 'restart')
        report['checks'].append('restart native client trial passed and stopped cleanly')
        negative_root = fabric_smoke._stage(w)
        report['negative_fixture'] = {
            'root': str(negative_root),
            'config': str(negative_root / 'config' / 'chronicle' / 'enchantment-caps.json'),
        }
        report['negative_fixture'].update(_run_invalid_config(w, fabric_smoke, negative_root, reports))
        report['checks'].append('fresh malformed cap config rejected before ready without rewrite')
        report['result'] = 'PASS'
        print('PASS: enchantment caps native smoke; evidence:', reports, flush=True)
    except BaseException as error:
        report['error'] = str(error)
        raise
    finally:
        (reports / 'report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
        (w.REPORTS / 'enchantment-smoke-latest.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    run()
