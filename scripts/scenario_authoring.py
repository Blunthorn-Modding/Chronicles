"""Prepare and run an isolated, authenticated custom-scenario authoring server."""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import sys
import threading
from collections import deque
import time
import uuid

import workspace as w

MAX_JSON = 16 * 1024 * 1024 + 4096
STAMP = re.compile(r'^\d{8}T\d{12}Z$')
ID = re.compile(r'^[a-z0-9_.-]+:[a-z0-9/._-]+$')
NAME = re.compile(r'^[A-Za-z0-9_]{1,16}$')


def regular(path):
    path = Path(path)
    if path.is_symlink() or not path.is_file() or any(p.is_symlink() for p in path.parents):
        raise RuntimeError(f'Expected a regular, non-link file: {path}')
    return path


def read_json(path):
    file = regular(path)
    if file.stat().st_size > MAX_JSON:
        raise RuntimeError(f'JSON too large: {path}')
    raw = file.read_bytes()
    if len(raw) > MAX_JSON:
        raise RuntimeError(f'JSON too large: {path}')
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError('duplicate JSON key: ' + key)
            result[key] = value
        return result
    try:
        return raw, json.loads(raw.decode('utf-8'), object_pairs_hook=pairs,
                               parse_constant=lambda _: (_ for _ in ()).throw(ValueError('non-finite number')))
    except (ValueError, UnicodeError, RecursionError) as exc:
        raise RuntimeError(f'Invalid JSON: {path}: {exc}') from exc


def profile(path):
    raw, data = read_json(path)
    if not isinstance(data, dict) or set(data) != {'type', 'schema', 'identity', 'title', 'mechanics', 'content'}:
        raise RuntimeError('Invalid scenario profile envelope')
    try:
        valid_uuid = isinstance(data['identity'], str) and str(uuid.UUID(data['identity'])) == data['identity']
    except ValueError:
        valid_uuid = False
    if (data['type'] != 'chronicle:scenario_profile' or type(data['schema']) is not int or data['schema'] != 1
            or not valid_uuid or not isinstance(data['title'], str) or not data['title'].strip()
            or len(data['title']) > 160 or not isinstance(data['content'], dict)
            or not ID.fullmatch(str(data['content'].get('scenarioId', '')))
            or not isinstance(data['mechanics'], list) or len(data['mechanics']) > 16 or len(set(x for x in data['mechanics'] if isinstance(x, str))) != len(data['mechanics'])
            or any(not isinstance(x, str) or not ID.fullmatch(x) for x in data['mechanics'])):
        raise RuntimeError('Invalid scenario profile envelope')
    return raw, data


def check_profile_identity(data, meta):
    # Content changes under the same identity are normal authoring exports. Existing
    # world/config catalogs remain authoritative; Java checks their identity on start.
    if (data['identity'] != meta['identity'] or data['content'].get('scenarioId') != meta['scenarioId']
            or sorted(data['mechanics']) != sorted(meta['mechanics'])):
        raise RuntimeError('Profile identity or required mechanics changed; use a separate server')


def properties(path):
    result = {}
    for line in regular(path).read_text(encoding='utf-8').splitlines():
        line = line.strip()
        if line and not line.startswith(('#', '!')):
            if '=' not in line:
                raise RuntimeError('Unsupported server property')
            key, value = line.split('=', 1)
            if key.strip() in result:
                raise RuntimeError('Duplicate server property')
            result[key.strip()] = value.strip()
    return result


def access(root):
    result = []
    for filename in ('whitelist.json', 'ops.json'):
        raw, entries = read_json(root / filename)
        if not isinstance(entries, list) or not entries:
            raise RuntimeError(f'{filename} must contain entries')
        seen = set()
        for entry in entries:
            if not isinstance(entry, dict) or not isinstance(entry.get('name'), str) or not NAME.fullmatch(entry['name']):
                raise RuntimeError(f'Invalid {filename} entry')
            try:
                ident = str(uuid.UUID(entry['uuid']))
            except (ValueError, TypeError, KeyError, AttributeError) as exc:
                raise RuntimeError(f'Invalid {filename} UUID') from exc
            if ident != entry['uuid'] or ident in seen:
                raise RuntimeError(f'Invalid or duplicate {filename} UUID')
            seen.add(ident)
            if filename == 'ops.json' and (type(entry.get('level')) is not int or not 1 <= entry['level'] <= 4 or type(entry.get('bypassesPlayerLimit')) is not bool):
                raise RuntimeError('Invalid operator privileges')
        result.append((raw, entries))
    whitelist = {(e['uuid'], e['name'].lower()) for e in result[0][1]}
    if any((e['uuid'], e['name'].lower()) not in whitelist for e in result[1][1]):
        raise RuntimeError('Every operator must be whitelisted')
    return result


def direct_review(path):
    base = (w.WORK / 'reviews').resolve()
    path = Path(path).absolute()
    if any(p.is_symlink() for p in (path, *path.parents)) or path.resolve().parent.parent != base:
        raise RuntimeError('Source must be a direct timestamped review under workspace reviews')
    if not STAMP.fullmatch(path.name) or not path.is_dir():
        raise RuntimeError('Invalid review directory')
    return path


def ports(port, voice):
    if any(type(p) is not int or p < 1 or p > 65535 or p in (25575, 24455) for p in (port, voice)) or port == voice:
        raise RuntimeError('Invalid or reserved port')
    for number, kind in ((port, socket.SOCK_STREAM), (voice, socket.SOCK_DGRAM)):
        with socket.socket(socket.AF_INET, kind) as probe:
            probe.bind(('127.0.0.1', number))


def copy_tree(source, target):
    if source.is_symlink() or not source.is_dir():
        raise RuntimeError(f'Invalid runtime directory: {source}')
    for item in source.rglob('*'):
        if item.is_symlink() or (not item.is_file() and not item.is_dir()):
            raise RuntimeError(f'Invalid runtime entry: {item}')
    shutil.copytree(source, target)


def atomic(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_name(path.name + f'.{os.getpid()}.pending')
    with temp.open('x', encoding='utf-8') as out:
        json.dump(data, out, indent=2, ensure_ascii=False)
        out.write('\n')
    os.replace(temp, path)


def auth(root):
    expected = {'server-ip': '127.0.0.1', 'online-mode': 'true', 'white-list': 'true',
                'enforce-whitelist': 'true', 'enforce-secure-profile': 'true', 'enable-rcon': 'false', 'enable-query': 'false'}
    props = properties(root / 'server.properties')
    if any(props.get(k) != v for k, v in expected.items()):
        raise RuntimeError('Source authentication or binding is not safe')
    if any(root.glob('.chronicle-test*')):
        raise RuntimeError('Test server cannot be an authoring source')
    return access(root)


def prepare(profile_path, source_server=None, port=25590, voice_port=24470):
    raw, data = profile(profile_path)
    if source_server is None:
        _, latest = read_json(w.REPORTS / 'deferred-review-latest.json')
        source_server = latest['review']
    source = direct_review(source_server)
    access_data = auth(source)
    if 'eula=true' not in regular(source / 'eula.txt').read_text(encoding='utf-8').splitlines():
        raise RuntimeError('Source EULA is not accepted')
    ports(port, voice_port)
    artifacts = w.artifacts()
    owned = {}
    for jar in artifacts:
        regular(jar)
        ident = w.validate_mod_target(jar)['id']
        if ident not in ('chronicle_core', 'quiet_below') or ident in owned:
            raise RuntimeError('Unexpected built artifact identity')
        owned[ident] = jar
    if len(owned) != 2:
        raise RuntimeError('Both Chronicle artifacts are required')
    source_mods = source / 'mods'
    if source_mods.is_symlink() or not source_mods.is_dir():
        raise RuntimeError('Invalid source mods')
    selected = []
    for jar in source_mods.iterdir():
        regular(jar)
        if jar.suffix != '.jar':
            raise RuntimeError('Unexpected source mods entry')
        if not any(jar.name.startswith(key + '-') for key in owned):
            selected.append(jar)
    selected.extend(owned.values())
    if len({p.name for p in selected}) != len(selected):
        raise RuntimeError('Duplicate mod filename')
    parent = w.WORK / 'reviews' / 'authoring'
    parent.mkdir(parents=True, exist_ok=True)
    if parent.is_symlink() or parent.resolve().parent != (w.WORK / 'reviews').resolve():
        raise RuntimeError('Invalid authoring parent')
    root = parent / datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    root.mkdir()  # Never reuse or remove an existing server.
    for name in ('fabric-server-launch.jar', 'fabric-server-launcher.properties', 'server.jar', 'eula.txt'):
        shutil.copy2(regular(source / name), root / name)
    copy_tree(source / 'libraries', root / 'libraries')
    for name in ('versions', '.fabric'):
        if (source / name).exists():
            copy_tree(source / name, root / name)
    (root / 'mods').mkdir()
    for jar in selected:
        shutil.copy2(jar, root / 'mods' / jar.name)
        if w.digest(jar) != w.digest(root / 'mods' / jar.name):
            raise RuntimeError('Copied mod hash mismatch')
    for name, (contents, _) in zip(('whitelist.json', 'ops.json'), access_data):
        (root / name).write_bytes(contents)
    conf = root / 'config' / 'chronicle'
    conf.mkdir(parents=True)
    (conf / 'scenario-profile.json').write_bytes(raw)
    if (conf / 'scenario-profile.json').read_bytes() != raw:
        raise RuntimeError('Copied profile mismatch')
    voice = root / 'config' / 'voicechat'
    voice.mkdir()
    (voice / 'voicechat-server.properties').write_text(f'bind_address=127.0.0.1\nport={voice_port}\n', encoding='utf-8')
    (root / 'server.properties').write_text(f'''server-ip=127.0.0.1
server-port={port}
online-mode=true
white-list=true
enforce-whitelist=true
enforce-secure-profile=true
enable-rcon=false
enable-query=false
level-name=world
level-type=minecraft:normal
generate-structures=true
pause-when-empty-seconds=-1
''', encoding='utf-8')
    meta = {'root': str(root), 'identity': data['identity'], 'title': data['title'],
            'scenarioId': data['content'].get('scenarioId'), 'mechanics': data['mechanics'], 'port': port,
            'voicePort': voice_port, 'profileHash': w.digest(conf / 'scenario-profile.json'),
            'mods': {jar.name: w.digest(root / 'mods' / jar.name) for jar in selected}, 'status': 'PREPARED'}
    atomic(root / '.chronicle-authoring.json', meta)
    atomic(w.REPORTS / 'scenario-authoring-latest.json', meta)
    print(f'Prepared authoring server: {root}')
    return root


def start(directory):
    root = direct_review(directory)
    if root.parent.name != 'authoring':
        raise RuntimeError('Not an authoring server')
    _, meta = read_json(root / '.chronicle-authoring.json')
    if meta['root'] != str(root) or meta['status'] not in ('PREPARED', 'STOPPED', 'START_FAILED'):
        raise RuntimeError('Invalid authoring metadata or status')
    auth(root)
    props = properties(root / 'server.properties')
    if props.get('server-port') != str(meta['port']) or props.get('level-name') != 'world' or props.get('pause-when-empty-seconds') != '-1':
        raise RuntimeError('Server configuration changed')
    voice = properties(root / 'config/voicechat/voicechat-server.properties')
    if voice.get('bind_address') != '127.0.0.1' or voice.get('port') != str(meta['voicePort']):
        raise RuntimeError('Voice configuration changed')
    _, p = profile(root / 'config/chronicle/scenario-profile.json')
    check_profile_identity(p, meta)
    # Record a same-identity update only after every launch preflight has passed.
    profile_hash = w.digest(root / 'config/chronicle/scenario-profile.json')
    actual = list((root / 'mods').iterdir())
    if {x.name for x in actual} != set(meta['mods']) or any(w.digest(regular(x)) != meta['mods'][x.name] for x in actual):
        raise RuntimeError('Mods changed')
    w.check_stopped(root)
    ports(meta['port'], meta['voicePort'])
    environment = {k: v for k, v in w.env().items() if not k.upper().startswith('CHRONICLE_')}
    marker = root / '.chronicle-process.json'
    with marker.open('x', encoding='utf-8') as out:
        json.dump({'controller_pid': os.getpid(), 'state': 'starting'}, out)
    process = None
    lines = deque(maxlen=256)
    ready = threading.Event()
    voice_ready = threading.Event()
    try:
        process = subprocess.Popen(w.server_command(), cwd=root, env=environment, stdin=subprocess.PIPE,
                                   stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace', bufsize=1)
        atomic(marker, {'pid': process.pid, 'controller_pid': os.getpid()})
        if profile_hash != meta['profileHash']:
            print('Accepted updated content with the same scenario identity; existing world/config edits are retained.', flush=True)
            meta['profileHash'] = profile_hash
        def output():
            with (root / 'authoring-console.log').open('a', encoding='utf-8') as log:
                for line in process.stdout:
                    lines.append(line)
                    if 'Done (' in line:
                        ready.set()
                    if f'Voice chat server started at 127.0.0.1:{meta["voicePort"]}' in line:
                        voice_ready.set()
                    log.write(line); log.flush()
                    print(line, end='', flush=True)
        def forward():
            for line in sys.stdin:
                if process.poll() is not None:
                    break
                try:
                    process.stdin.write(line); process.stdin.flush()
                except (OSError, ValueError):
                    break
        threading.Thread(target=output, daemon=True).start()
        threading.Thread(target=forward, daemon=True).start()
        deadline = time.monotonic() + 180
        while not (ready.is_set() and voice_ready.is_set()):
            if process.poll() is not None or time.monotonic() > deadline:
                raise RuntimeError('Authoring server did not reach readiness')
            time.sleep(.2)
        meta.update(status='LIVE', pid=process.pid)
        atomic(root / '.chronicle-authoring.json', meta)
        atomic(w.REPORTS / 'scenario-authoring-latest.json', meta)
        print('Authoring server ready; enter stop to save and exit.', flush=True)
        code = process.wait()
        if code:
            raise RuntimeError(f'Server exited {code}')
        meta.update(status='STOPPED', exitCode=code)
    except BaseException:
        meta['status'] = 'START_FAILED'
        raise
    finally:
        if process is not None and process.poll() is None:
            try:
                process.stdin.write('stop\n'); process.stdin.flush()
                process.wait(timeout=180)
            except (OSError, subprocess.TimeoutExpired):
                print('Server did not stop cleanly; process marker retained.', file=sys.stderr)
        atomic(root / '.chronicle-authoring.json', meta)
        atomic(w.REPORTS / 'scenario-authoring-latest.json', meta)
        if process is None or process.poll() is not None:
            marker.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='action', required=True)
    p = sub.add_parser('prepare')
    p.add_argument('profile'); p.add_argument('--source-server'); p.add_argument('--port', type=int, default=25590); p.add_argument('--voice-port', type=int, default=24470)
    p = sub.add_parser('start'); p.add_argument('directory')
    args = parser.parse_args()
    if args.action == 'prepare':
        prepare(args.profile, args.source_server, args.port, args.voice_port)
    else:
        start(args.directory)


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, ValueError, KeyError, subprocess.SubprocessError) as error:
        print(f'ERROR: {error}', file=sys.stderr)
        sys.exit(1)
