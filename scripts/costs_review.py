"""Prepare or start an authenticated, disposable combined benefits-and-costs review."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess

import ecology_review as ecology
import workspace as w


ROOTS = w.WORK / 'reviews' / 'costs'
REPORT = w.REPORTS / 'costs-review-latest.json'
PORT = 25588
VOICE_PORT = 24468
MARKER = '.chronicle-costs-review.json'
DEFAULT_RECOVERY = {'enabled': True, 'seconds': 10}
DEFAULT_COSTS = {
    'hunger': {'enabled': True, 'seconds': 40},
    'darkness': {'enabled': True, 'seconds': 40},
}
REQUIRED = dict(ecology.REQUIRED, **{
    'server-port': str(PORT),
    'enforce-secure-profile': 'true',
})


def _object(value, label: str) -> dict:
    if not isinstance(value, dict):
        raise RuntimeError(f'Invalid {label} in review snapshot')
    return value


def _rule(value, label: str) -> dict:
    value = _object(value, label)
    if set(value) != {'enabled', 'seconds'} or not isinstance(value['enabled'], bool) or type(value['seconds']) is not int:
        raise RuntimeError(f'Invalid {label} in review snapshot')
    return value


def _costs(value, label: str) -> dict:
    value = _object(value, label)
    if set(value) != {'hunger', 'darkness'}:
        raise RuntimeError(f'Invalid {label} in review snapshot')
    return {'hunger': _rule(value['hunger'], label + '.hunger'),
            'darkness': _rule(value['darkness'], label + '.darkness')}


def _snapshot_rules(journal: Path) -> tuple[dict, dict, bool]:
    recovery = dict(DEFAULT_RECOVERY)
    costs = {kind: dict(rule) for kind, rule in DEFAULT_COSTS.items()}
    cleanup = False
    try:
        lines = journal.read_text(encoding='utf-8').splitlines()
        for line in lines:
            entry = _object(json.loads(line), 'journal record')
            kind = entry.get('type')
            payload = entry.get('payload')
            if kind == 'CHECKPOINT_BEGIN':
                payload = _object(payload, 'checkpoint header')
                if 'recovery' in payload:
                    recovery = _rule(payload['recovery'], 'checkpoint recovery')
                if 'costs' in payload:
                    costs = _costs(payload['costs'], 'checkpoint costs')
                if 'cleanup' not in payload or not isinstance(payload['cleanup'], bool):
                    raise RuntimeError('Invalid checkpoint cleanup in review snapshot')
                cleanup = payload['cleanup']
            elif kind == 'RECOVERY_RULES':
                recovery = _rule(payload, 'recovery rules')
            elif kind == 'COST_RULES':
                costs = _costs(payload, 'cost rules')
            elif kind == 'CLEANUP_MODE':
                payload = _object(payload, 'cleanup rules')
                if set(payload) != {'enabled'} or not isinstance(payload['enabled'], bool):
                    raise RuntimeError('Invalid cleanup rules in review snapshot')
                cleanup = payload['enabled']
    except (OSError, json.JSONDecodeError, TypeError) as error:
        raise RuntimeError('Cannot read review snapshot journal') from error
    return recovery, costs, cleanup


def snapshot(report: dict) -> Path:
    phases = report.get('phases', [])
    if report.get('result') != 'PASS' or not {'costs', 'costs_restart'}.issubset(phases):
        raise RuntimeError('Run successful costs and costs restart trials before preparing this review')
    if phases.index('costs') >= phases.index('costs_restart'):
        raise RuntimeError('Costs restart evidence must follow the costs trial')
    try:
        root = ecology.direct(Path(report['costs_world']), w.WORK / 'test-runs')
    except (KeyError, TypeError) as error:
        raise RuntimeError('Costs review report is missing its same-run world') from error
    w.check_stopped(root)
    source = root / 'review-world'
    if not (source / 'level.dat').is_file() or not (source / 'quiet-below/ecology.jsonl').is_file():
        raise RuntimeError('Costs review snapshot is missing')
    try:
        campaign = _object(json.loads((source / 'chronicle/campaign-state.json').read_text(encoding='utf-8')), 'campaign state')
    except (OSError, json.JSONDecodeError) as error:
        raise RuntimeError('Costs review campaign state is missing or invalid') from error
    if campaign.get('phase') != 'PAUSED':
        raise RuntimeError('Costs review must be paused')
    recovery, costs, cleanup = _snapshot_rules(source / 'quiet-below/ecology.jsonl')
    if recovery != DEFAULT_RECOVERY or costs != DEFAULT_COSTS or cleanup:
        raise RuntimeError('Review requires default healing, both default local costs, and cleanup off')
    return source


def prepare() -> Path:
    w.require_eula()
    w.validate_server_runtime()
    w.validate_managed_mod_names()
    source = snapshot(json.loads((w.REPORTS / 'ecology-smoke-latest.json').read_text(encoding='utf-8')))
    root = ecology.direct(ROOTS / datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ'), ROOTS)
    root.mkdir(parents=True)
    for name in ('fabric-server-launch.jar', 'fabric-server-launcher.properties', 'server.jar', 'eula.txt', 'ops.json', 'whitelist.json'):
        shutil.copy2(w.SERVER / name, root / name)
    for name in ('libraries', 'mods'):
        shutil.copytree(w.SERVER / name, root / name)
    for prefix in ('chronicle_core-', 'quiet_below-'):
        for old in (root / 'mods').glob(prefix + '*.jar'):
            old.unlink()  # Only jars inside this new disposable runtime are removed.
    jars = w.artifacts()
    for jar in jars:
        w.validate_mod_target(jar)
        shutil.copy2(jar, root / 'mods' / jar.name)
    shutil.copytree(source, root / 'world')
    properties = dict(REQUIRED, **{
        'gamemode': 'survival', 'difficulty': 'normal', 'spawn-protection': '0', 'max-players': '12',
        'view-distance': '6', 'simulation-distance': '4', 'sync-chunk-writes': 'true',
        'pause-when-empty-seconds': '60', 'motd': 'Chronicle - isolated living sculk costs review',
    })
    (root / 'server.properties').write_text('\n'.join(k + '=' + v for k, v in properties.items()) + '\n', encoding='utf-8')
    voice = root / 'config/voicechat'
    voice.mkdir(parents=True)
    (voice / 'voicechat-server.properties').write_text(f'bind_address=127.0.0.1\nport={VOICE_PORT}\n', encoding='utf-8')
    manifest = {'schema': 1, 'review': str(root), 'source': str(source), 'minecraft': w.LOCK['minecraft'],
                'mods': {jar.name: w.digest(jar) for jar in jars}, 'port': PORT, 'voice_port': VOICE_PORT}
    (root / MARKER).write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    REPORT.write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print('Prepared authenticated costs review:', root, flush=True)
    return root


def _properties(path: Path) -> dict:
    return dict(line.split('=', 1) for line in path.read_text(encoding='utf-8').splitlines()
                if line and not line.startswith(('#', '!')) and '=' in line)


def resolve() -> Path:
    try:
        manifest = _object(json.loads(REPORT.read_text(encoding='utf-8')), 'review manifest')
        root = ecology.direct(Path(manifest['review']), ROOTS)
        stored = _object(json.loads((root / MARKER).read_text(encoding='utf-8')), 'stored review manifest')
    except (OSError, KeyError, TypeError, json.JSONDecodeError) as error:
        raise RuntimeError('Costs review manifest is missing or invalid') from error
    if stored != manifest or manifest.get('minecraft') != w.LOCK['minecraft'] or manifest.get('port') != PORT or manifest.get('voice_port') != VOICE_PORT:
        raise RuntimeError('Costs review manifest mismatch')
    try:
        properties = _properties(root / 'server.properties')
        voice = _properties(root / 'config/voicechat/voicechat-server.properties')
    except OSError as error:
        raise RuntimeError('Costs review properties are missing') from error
    if any(properties.get(key) != value for key, value in REQUIRED.items()):
        raise RuntimeError('Costs review authentication, loopback binding or world settings changed')
    if voice.get('bind_address') != '127.0.0.1' or voice.get('port') != str(VOICE_PORT):
        raise RuntimeError('Costs review voice binding changed')
    mods = manifest.get('mods')
    if not isinstance(mods, dict):
        raise RuntimeError('Costs review artifact manifest is invalid')
    for name, sha in mods.items():
        if Path(name).name != name or not isinstance(sha, str) or w.digest(root / 'mods' / name) != sha:
            raise RuntimeError('Costs review artifact changed; prepare a fresh review')
    return root


def start() -> None:
    root = resolve()
    w.check_stopped(root)
    for kind, port in ((socket.SOCK_STREAM, PORT), (socket.SOCK_DGRAM, VOICE_PORT)):
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
            raise RuntimeError('Costs review exited ' + str(result))
    finally:
        if process is None or process.poll() is not None:
            marker.unlink(missing_ok=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['prepare', 'start'])
    {'prepare': prepare, 'start': start}[parser.parse_args().action]()
