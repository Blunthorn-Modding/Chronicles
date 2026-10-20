"""Authenticated, isolated human review of a successfully rehearsed ecology snapshot."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import shutil
import socket
import subprocess
import os
import workspace as w

ROOTS = w.WORK / 'reviews' / 'ecology'
REPORT = w.REPORTS / 'ecology-review-latest.json'
STAMP = re.compile(r'[0-9]{8}T[0-9]{12,}Z')
REQUIRED = {'server-ip': '127.0.0.1', 'server-port': '25585', 'online-mode': 'true',
            'white-list': 'true', 'enforce-whitelist': 'true', 'level-name': 'world',
            'enable-rcon': 'false', 'enable-query': 'false'}


def direct(path: Path, parent: Path) -> Path:
    resolved = path.resolve()
    if resolved.parent != parent.resolve() or not STAMP.fullmatch(resolved.name):
        raise RuntimeError('Expected a timestamped direct child of ' + str(parent))
    return resolved


def snapshot(report: dict) -> Path:
    if report.get('result') != 'PASS':
        raise RuntimeError('Run a successful ecology-smoke before preparing a review')
    if 'phases' in report and not {'initial', 'restart'}.issubset(report['phases']):
        raise RuntimeError('A focused ecology trial has no review snapshot; run the full ecology-smoke first')
    root = direct(Path(report['world']), w.WORK / 'test-runs')
    w.check_stopped(root)
    source = root / 'review-world'
    if not (source / 'level.dat').is_file() or not (source / 'quiet-below/ecology.jsonl').is_file():
        raise RuntimeError('Stopped review snapshot is missing')
    campaign=json.loads((source / 'chronicle/campaign-state.json').read_text(encoding='utf-8'))
    cleanup=False
    for line in (source / 'quiet-below/ecology.jsonl').read_text(encoding='utf-8').splitlines():
        entry=json.loads(line)
        if entry['type']=='CLEANUP_MODE':
            cleanup=entry['payload']['enabled']
    if cleanup or campaign.get('phase')!='PAUSED':
        raise RuntimeError('Human review snapshot must be paused with non-campaign cleanup disabled; rerun ecology-smoke')
    return source


def prepare() -> Path:
    w.require_eula()
    w.validate_server_runtime()
    jars = w.artifacts()
    for jar in jars:
        w.validate_mod_target(jar)
    source = snapshot(json.loads((w.REPORTS / 'ecology-smoke-latest.json').read_text(encoding='utf-8')))
    root = direct(ROOTS / datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ'), ROOTS)
    root.mkdir(parents=True)
    for name in ('fabric-server-launch.jar', 'fabric-server-launcher.properties', 'server.jar', 'eula.txt', 'ops.json', 'whitelist.json'):
        shutil.copy2(w.SERVER / name, root / name)
    for name in ('libraries', 'mods'):
        shutil.copytree(w.SERVER / name, root / name)
    for prefix in ('chronicle_core-', 'quiet_below-'):
        for old in (root / 'mods').glob(prefix + '*.jar'):
            old.unlink()  # Only copied managed jars in this newly created runtime.
    for jar in jars:
        shutil.copy2(jar, root / 'mods' / jar.name)
    shutil.copytree(source, root / 'world')
    # Manual, one-time review-only viewing gallery. No load/tick function is installed.
    pack = root / 'world/datapacks/chronicle_ecology_review'
    function = pack / 'data/chronicle_ecology_review/function/prepare.mcfunction'
    function.parent.mkdir(parents=True)
    (pack / 'pack.mcmeta').write_text(json.dumps({'pack': {'min_format': [88, 0], 'max_format': [88, 0],
        'description': 'Disposable sculk viewing gallery; run prepare once manually.'}}), encoding='utf-8')
    function.write_text('\n'.join([
        'fill -1 64 -3 15 68 -1 stone',
        'fill 0 65 -2 14 67 -1 air',
        'fill 0 65 0 14 66 0 glass',
        'fill 0 68 -2 14 68 -1 glowstone',
        'fill 10 64 1 13 64 1 oak_planks',
        'fill 3 64 -5 5 64 -3 stone',
        'fill 3 65 -5 5 72 -3 air',
        'setworldspawn 4 65 -4',
        'gamerule spawnRadius 0',
    ]) + '\n', encoding='utf-8')
    properties = dict(REQUIRED, **{'gamemode': 'creative', 'difficulty': 'normal', 'spawn-protection': '0',
        'max-players': '12', 'view-distance': '6', 'simulation-distance': '4', 'sync-chunk-writes': 'true',
        'motd': 'Chronicle - isolated sculk ecology review', 'pause-when-empty-seconds': '60'})
    (root / 'server.properties').write_text('\n'.join(k + '=' + v for k, v in properties.items()) + '\n', encoding='utf-8')
    voice = root / 'config/voicechat'
    voice.mkdir(parents=True)
    (voice / 'voicechat-server.properties').write_text('bind_address=127.0.0.1\nport=24465\n', encoding='utf-8')
    manifest = {'schema': 1, 'review': str(root), 'source': str(source), 'minecraft': w.LOCK['minecraft'],
                'mods': {jar.name: w.digest(jar) for jar in jars}, 'port': 25585}
    (root / '.chronicle-ecology-review.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    REPORT.write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print('Prepared isolated authenticated ecology review:', root, flush=True)
    print('Once ready, run: function chronicle_ecology_review:prepare', flush=True)
    return root


def resolve() -> Path:
    report = json.loads(REPORT.read_text(encoding='utf-8'))
    root = direct(Path(report['review']), ROOTS)
    manifest = json.loads((root / '.chronicle-ecology-review.json').read_text(encoding='utf-8'))
    if manifest != report or manifest['minecraft'] != w.LOCK['minecraft']:
        raise RuntimeError('Review manifest does not match the current runtime')
    properties = {}
    for line in (root / 'server.properties').read_text(encoding='utf-8').splitlines():
        if line and not line.startswith(('#', '!')) and '=' in line:
            key, value = line.split('=', 1)
            properties[key.strip()] = value.strip()
    if any(properties.get(k) != v for k, v in REQUIRED.items()):
        raise RuntimeError('Review authentication, loopback binding or world settings changed')
    voice = dict(line.split('=', 1) for line in (root / 'config/voicechat/voicechat-server.properties').read_text(encoding='utf-8').splitlines() if line and not line.startswith('#') and '=' in line)
    if voice.get('bind_address') != '127.0.0.1' or voice.get('port') != '24465':
        raise RuntimeError('Review voice loopback binding changed')
    for name, expected in manifest['mods'].items():
        if Path(name).name != name or w.digest(root / 'mods' / name) != expected:
            raise RuntimeError('Review artifacts changed; prepare a fresh review')
    return root


def start() -> None:
    root = resolve()
    w.check_stopped(root)
    for kind, port in ((socket.SOCK_STREAM, 25585), (socket.SOCK_DGRAM, 24465)):
        with socket.socket(socket.AF_INET, kind) as probe:
            probe.bind(('127.0.0.1', port))
    marker = root / '.chronicle-process.json'
    process = None
    with marker.open('x', encoding='utf-8') as out:
        json.dump({'controller_pid': os.getpid(), 'state': 'starting'}, out)
    try:
        process = subprocess.Popen(w.server_command(), cwd=root, env=w.env())
        marker.write_text(json.dumps({'controller_pid': os.getpid(), 'pid': process.pid}), encoding='utf-8')
        try:
            result = process.wait()
        except KeyboardInterrupt:
            result = process.wait(timeout=90)
        if result:
            raise RuntimeError('Review server exited ' + str(result))
    finally:
        if process is None or process.poll() is not None:
            marker.unlink(missing_ok=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['prepare', 'start'])
    {'prepare': prepare, 'start': start}[parser.parse_args().action]()
