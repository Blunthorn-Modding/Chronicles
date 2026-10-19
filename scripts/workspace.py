"""Workspace-local Minecraft toolchain. Python standard library only; Windows x64."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import queue
import re
import shutil
import socket
import subprocess
import sys
import threading
import time
import urllib.request
import zipfile
from datetime import datetime, timezone

REPO = Path(__file__).resolve().parents[1]
WORK = REPO.parent
TOOLS = WORK / 'tools'
CACHE = WORK / 'cache'
SERVER = WORK / 'server'
REPORTS = WORK / 'reports'
LOCK = json.loads((REPO / 'bootstrap.lock.json').read_text(encoding='utf-8'))
JAVA = TOOLS / 'java' / 'current' / 'bin' / 'java.exe'
GRADLE = TOOLS / f"gradle-{LOCK['gradle']['version']}" / 'bin' / 'gradle.bat'
BOT = REPO / 'testing' / 'minecraft-bot'
MILESTONE = 'M7-spawns'
ALLOWED = {'api.modrinth.com', 'cdn.modrinth.com', 'maven.fabricmc.net',
           'services.gradle.org', 'downloads.gradle.org', 'github.com',
           'release-assets.githubusercontent.com', 'objects.githubusercontent.com'}


def digest(path, algorithm='sha256'):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, algorithm).hexdigest()


class DownloadPolicy(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        check_url(newurl)
        return super().redirect_request(request, fp, code, msg, headers, newurl)


def check_url(url):
    from urllib.parse import urlsplit
    parsed = urlsplit(url)
    if parsed.scheme != 'https' or parsed.hostname not in ALLOWED or parsed.username or parsed.password:
        raise RuntimeError(f'Unapproved download origin: {parsed.hostname}')


def download(url, target, expected, algorithm='sha256'):
    check_url(url)
    target = Path(target)
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists() and digest(target, algorithm) == expected:
        return target
    partial = target.with_suffix(target.suffix + '.part')
    for attempt in range(3):
        try:
            print(f'Downloading {target.name}', flush=True)
            request = urllib.request.Request(url, headers={'User-Agent': 'Chronicle-bootstrap/0.1 (local development)'})
            with urllib.request.build_opener(DownloadPolicy()).open(request, timeout=60) as response, partial.open('wb') as out:
                shutil.copyfileobj(response, out)
            if digest(partial, algorithm) != expected:
                raise RuntimeError(f'Checksum mismatch: {target.name}')
            partial.replace(target)
            return target
        except (OSError, RuntimeError):
            if attempt == 2:
                raise
            time.sleep(2 ** attempt)
    raise AssertionError('Unreachable')


def extract(archive, destination):
    destination = Path(destination).resolve()
    destination.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive) as zipped:
        for member in zipped.infolist():
            target = (destination / member.filename).resolve()
            if not target.is_relative_to(destination) or (member.external_attr >> 16) & 0o170000 == 0o120000:
                raise RuntimeError('Unsafe archive path')
        zipped.extractall(destination)


def env():
    result = os.environ.copy()
    result.pop('DEBUG', None)
    result['JAVA_HOME'] = str(JAVA.parents[1])
    result['GRADLE_USER_HOME'] = str(CACHE / 'gradle')
    result['PATH'] = str(JAVA.parent) + os.pathsep + result.get('PATH', '')
    return result


def run(args, cwd=REPO, **kwargs):
    print('Running:', subprocess.list2cmdline([str(arg) for arg in args]), flush=True)
    # Windows CreateProcess resolves .bat via cmd; subprocess quotes the complete
    # argument vector. Only fixed repository paths/commands are used here.
    subprocess.run([str(arg) for arg in args], cwd=cwd, env=env(), check=True, **kwargs)


def write_initial(path, text):
    path = Path(path)
    if not path.exists():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding='utf-8')


def seed_wrapper():
    # Reuse setup's verified archive. Java's downloader can fail on GitHub
    # redirects even when Python succeeds; the wrapper rechecks SHA-256 itself.
    gradle = LOCK['gradle']
    source = CACHE / f"gradle-{gradle['version']}-bin.zip"
    if not source.exists() or digest(source) != gradle['sha256']:
        raise RuntimeError('Verified Gradle archive missing; run setup')
    number = int(hashlib.md5(gradle['url'].encode(), usedforsecurity=False).hexdigest(), 16)
    alphabet = '0123456789abcdefghijklmnopqrstuvwxyz'
    encoded = ''
    while number:
        number, remainder = divmod(number, 36)
        encoded = alphabet[remainder] + encoded
    destination = CACHE / 'gradle' / 'wrapper' / 'dists' / f"gradle-{gradle['version']}-bin" / encoded / source.name
    if not destination.with_suffix(destination.suffix + '.ok').exists():
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)


def require_eula(accept=False):
    path = SERVER / 'eula.txt'
    if accept:
        path.write_text('# Accepted explicitly by the workspace operator.\n# https://www.minecraft.net/eula\neula=true\n', encoding='utf-8')
    if not path.exists() or 'eula=true' not in path.read_text(encoding='utf-8').splitlines():
        raise RuntimeError('Minecraft EULA acceptance is required. Review https://www.minecraft.net/eula then run setup --accept-eula (PowerShell: -AcceptEula).')


def validate_server_runtime():
    jar = SERVER / 'server.jar'
    if not jar.exists():
        return None
    with zipfile.ZipFile(jar) as zipped:
        installed = json.loads(zipped.read('version.json'))
    if installed['id'] != LOCK['minecraft'] or installed['protocol_version'] != LOCK['minecraft_protocol']:
        raise RuntimeError(f"Installed server is {installed['id']}, requested {LOCK['minecraft']}. Back up and stage a separate runtime before upgrading; setup will not overwrite a different Minecraft version.")
    marker = SERVER / '.chronicle-runtime.json'
    if marker.exists() and json.loads(marker.read_text())['loader'] != LOCK['loader']:
        raise RuntimeError('Installed Fabric Loader differs from the lock. Back up and stage a separate runtime before upgrading.')
    return installed


def validate_mod_target(jar):
    with zipfile.ZipFile(jar) as zipped:
        metadata = json.loads(zipped.read('fabric.mod.json'))
    if metadata['depends']['minecraft'] != LOCK['minecraft']:
        raise RuntimeError(f"{jar.name} targets {metadata['depends']['minecraft']}; build for {LOCK['minecraft']} before deploying or packing")
    return metadata


def validate_managed_mod_names():
    prefixes = {'fabric-api': 'fabric-api-', 'simple-voice-chat': 'voicechat-'}
    for mod in LOCK['mods']:
        for jar in (SERVER / 'mods').glob(prefixes[mod['slug']] + '*.jar'):
            if jar.name != mod['filename']:
                raise RuntimeError(f'Different managed mod version present: {jar.name}. Back up and move it aside before installing the locked version.')


def setup(accept=False):
    if sys.platform != 'win32':
        raise RuntimeError('This portable toolchain lock targets Windows x64.')
    check_stopped(SERVER)
    validate_server_runtime()
    validate_managed_mod_names()
    jdk = LOCK['java']
    archive = download(jdk['url'], CACHE / jdk['filename'], jdk['sha256'])
    if not JAVA.exists():
        staging = TOOLS / 'java' / 'unpack'
        extract(archive, staging)
        roots = [p for p in staging.iterdir() if (p / 'bin' / 'java.exe').exists()]
        if len(roots) != 1:
            raise RuntimeError('Expected one JDK root')
        roots[0].rename(JAVA.parents[1])
    gradle = LOCK['gradle']
    archive = download(gradle['url'], CACHE / f"gradle-{gradle['version']}-bin.zip", gradle['sha256'])
    if not GRADLE.exists():
        extract(archive, TOOLS)
    seed_wrapper()
    installer = LOCK['installer']
    jar = download(installer['url'], CACHE / f"fabric-installer-{installer['version']}.jar", installer['sha256'])
    SERVER.mkdir(parents=True, exist_ok=True)
    loader_jar = SERVER / 'libraries' / 'net' / 'fabricmc' / 'fabric-loader' / LOCK['loader'] / f"fabric-loader-{LOCK['loader']}.jar"
    if not all(path.exists() for path in [SERVER / 'fabric-server-launch.jar', SERVER / 'server.jar', loader_jar]):
        run([JAVA, '-jar', jar, 'server', '-mcversion', LOCK['minecraft'], '-loader', LOCK['loader'], '-downloadMinecraft', '-dir', SERVER])
    for mod in LOCK['mods']:
        download(mod['url'], SERVER / 'mods' / mod['filename'], mod['sha512'], 'sha512')
    installed = validate_server_runtime()
    (SERVER / '.chronicle-runtime.json').write_text(json.dumps({
        'minecraft': installed['id'], 'minecraft_protocol': installed['protocol_version'],
        'loader': LOCK['loader'], 'installer': LOCK['installer']['version']
    }, indent=2) + '\n', encoding='utf-8')
    write_initial(SERVER / 'eula.txt', '# Review https://www.minecraft.net/eula before accepting.\neula=false\n')
    write_initial(SERVER / 'server.properties', '\n'.join([
        'server-ip=127.0.0.1', 'server-port=25565', 'online-mode=true',
        'white-list=true', 'enforce-whitelist=true', 'enable-rcon=false',
        'enable-query=false', 'motd=Chronicle - The Quiet Below', 'max-players=12',
        'view-distance=8', 'simulation-distance=6', 'spawn-protection=16',
        'difficulty=normal', 'gamemode=survival', 'level-name=world',
        'enable-command-block=false', 'pvp=false', ''
    ]))
    write_initial(SERVER / 'config' / 'voicechat' / 'voicechat-server.properties',
                  'bind_address=127.0.0.1\nport=24454\n')
    if accept:
        require_eula(True)
    npm = shutil.which('npm.cmd')
    node = shutil.which('node')
    if not npm or not node:
        raise RuntimeError('Node.js 22+ and npm are needed for the local test clients.')
    version = subprocess.check_output([node, '--version'], text=True).strip()
    if int(version.lstrip('v').split('.')[0]) < 22:
        raise RuntimeError(f'Node.js 22+ is required; found {version}')
    command = 'ci' if (BOT / 'package-lock.json').exists() else 'install'
    run([npm, command, '--ignore-scripts', '--no-fund', '--no-audit'], cwd=BOT)
    print('Setup complete. EULA: ' + ('accepted' if 'eula=true' in (SERVER / 'eula.txt').read_text().splitlines() else 'pending') + '. Next: build, deploy, smoke.')


def build():
    if not JAVA.exists():
        raise RuntimeError('Run setup first')
    seed_wrapper()
    run([REPO / 'gradlew.bat', '--no-daemon', 'build'])


def artifacts():
    result = []
    for directory, prefix in [('core', 'chronicle_core'), ('campaigns/quiet-below', 'quiet_below')]:
        properties = dict(line.split('=', 1) for line in (REPO / 'gradle.properties').read_text().splitlines() if '=' in line)
        matches = list((REPO / directory / 'build' / 'libs').glob(f"{prefix}-{properties['mod_version']}.jar"))
        if len(matches) != 1:
            raise RuntimeError(f'Expected one built {prefix} jar; run clean build if versions changed')
        result.extend(matches)
    return result


def check_stopped(directory):
    lockfile = Path(directory) / '.chronicle-process.json'
    if lockfile.exists():
        raise RuntimeError(f'Server process marker exists: {lockfile}. Stop it first; after a crash verify the recorded PID before removing the marker.')
    # Minecraft locks its world/session.lock on Windows. Nonblocking exclusive
    # lock detects a server started outside our scripts, independently of ports.
    properties = Path(directory) / 'server.properties'
    level_name = 'world'
    if properties.exists():
        for line in properties.read_text(encoding='utf-8').splitlines():
            if line.strip().startswith('level-name='):
                level_name = line.split('=', 1)[1].strip()
    if not re.fullmatch(r'[A-Za-z0-9_-][A-Za-z0-9_.-]*', level_name):
        raise RuntimeError('Backup/deploy requires a simple relative level-name in server.properties')
    session = Path(directory) / level_name / 'session.lock'
    if session.exists():
        import msvcrt
        with session.open('r+b') as stream:
            try:
                msvcrt.locking(stream.fileno(), msvcrt.LK_NBLCK, 1)
                msvcrt.locking(stream.fileno(), msvcrt.LK_UNLCK, 1)
            except OSError as error:
                raise RuntimeError('World is in use; stop the server first') from error


def deploy():
    check_stopped(SERVER)
    validate_server_runtime()
    validate_managed_mod_names()
    jars = artifacts()
    previous = []
    mods = SERVER / 'mods'
    mods.mkdir(parents=True, exist_ok=True)
    for jar in jars:
        metadata = validate_mod_target(jar)
        for old in mods.glob(metadata['id'] + '-*.jar'):
            if validate_mod_target(old)['id'] != metadata['id']:
                raise RuntimeError(f'Unexpected mod identity; inspect before deploying: {old}')
            previous.append(old)
    if previous:
        archive = WORK / 'backups' / f"mods-{datetime.now(timezone.utc):%Y%m%dT%H%M%S%fZ}.zip"
        archive.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(archive, 'x', compression=zipfile.ZIP_DEFLATED) as zipped:
            for old in previous:
                zipped.write(old, old.name)
        with zipfile.ZipFile(archive) as zipped:
            if zipped.testzip():
                raise RuntimeError('Previous mod archive failed CRC verification')
        print(f'Previous Chronicle jars archived: {archive}')
    for jar in jars:
        destination = mods / jar.name
        temporary = destination.with_suffix('.jar.pending')
        shutil.copy2(jar, temporary)
        temporary.replace(destination)
    current_names = {jar.name for jar in jars}
    for old in previous:
        if old.name not in current_names:
            old.unlink()
    pack()
    print('Deployed both Chronicle mods to the local server.')


def server_command():
    return [str(JAVA), '-Xms512M', '-Xmx2G', '-jar', 'fabric-server-launch.jar', 'nogui']


def start():
    require_eula()
    check_stopped(SERVER)
    validate_server_runtime()
    validate_managed_mod_names()
    marker = SERVER / '.chronicle-process.json'
    with marker.open('x', encoding='utf-8') as out:
        json.dump({'controller_pid': os.getpid(), 'state': 'starting'}, out)
    process = None
    try:
        process = subprocess.Popen(server_command(), cwd=SERVER, env=env())
        marker.write_text(json.dumps({'pid': process.pid, 'controller_pid': os.getpid(),
                                     'started': datetime.now(timezone.utc).isoformat()}), encoding='utf-8')
        try:
            result = process.wait()
        except KeyboardInterrupt:
            # Java receives Ctrl+C through the same console and saves the world.
            result = process.wait(timeout=90)
        if result:
            raise RuntimeError(f'Minecraft server exited {result}')
    finally:
        if process is None or process.poll() is not None:
            marker.unlink(missing_ok=True)


def backup():
    check_stopped(SERVER)
    destination = WORK / 'backups' / f"server-{datetime.now(timezone.utc):%Y%m%dT%H%M%S%fZ}.zip"
    destination.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(destination, 'x', compression=zipfile.ZIP_DEFLATED) as zipped:
        for path in SERVER.rglob('*'):
            if path.is_file() and path.relative_to(SERVER).parts[0] not in {'libraries', 'logs', 'versions', 'cache'}:
                zipped.write(path, path.relative_to(SERVER))
        zipped.write(REPO / 'bootstrap.lock.json', 'chronicle-bootstrap.lock.json')
    with zipfile.ZipFile(destination) as zipped:
        if zipped.testzip():
            raise RuntimeError('Backup archive failed CRC verification')
    print(f'Backup verified: {destination}')
    return destination


def restore(archive):
    if not archive:
        raise RuntimeError('Pass --archive with a backup zip path')
    archive = Path(archive).resolve()
    if not archive.is_relative_to((WORK / 'backups').resolve()) or archive.suffix != '.zip':
        raise RuntimeError('Restore accepts only zip archives inside this workspace backups directory')
    with zipfile.ZipFile(archive) as zipped:
        if zipped.testzip():
            raise RuntimeError('Backup failed CRC verification')
    destination = WORK / 'restores' / datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    extract(archive, destination)
    print(f'Restored to a separate directory: {destination}. Stop the play server before manually promoting a restored world/config.')
    return destination


def pack():
    jars = artifacts()
    versions = {validate_mod_target(jar)['version'] for jar in jars}
    if len(versions) != 1:
        raise RuntimeError('Both Chronicle artifacts must have the same version')
    files = [{'path': 'mods/' + mod['filename'], 'hashes': {'sha1': mod['sha1'], 'sha512': mod['sha512']},
              'env': {'client': 'required', 'server': 'required'}, 'downloads': [mod['url']], 'fileSize': mod['size']}
             for mod in LOCK['mods']]
    index = {'formatVersion': 1, 'game': 'minecraft', 'versionId': versions.pop() + '+mc' + LOCK['minecraft'],
             'name': f'Chronicle - The Quiet Below ({MILESTONE})', 'summary': 'Local development pack',
             'files': files, 'dependencies': {'minecraft': LOCK['minecraft'], 'fabric-loader': LOCK['loader']}}
    target = WORK / 'client' / f"Chronicle-{MILESTONE}-{LOCK['minecraft']}.mrpack"
    target.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(target, 'w', compression=zipfile.ZIP_DEFLATED) as zipped:
        zipped.writestr('modrinth.index.json', json.dumps(index, indent=2))
        for jar in jars:
            zipped.write(jar, 'overrides/mods/' + jar.name)
    print(f'Modrinth client pack: {target}')


def legacy_smoke():
    require_eula()
    validate_server_runtime()
    validate_managed_mod_names()
    # Unique world per run; no user world copied, modified, or deleted.
    directory = WORK / 'test-runs' / datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    directory.mkdir(parents=True)
    for jar in artifacts():
        validate_mod_target(jar)
    for source in [SERVER / 'fabric-server-launch.jar', SERVER / 'fabric-server-launcher.properties', SERVER / 'server.jar']:
        if source.exists():
            shutil.copy2(source, directory)
    for folder in ['libraries', 'mods']:
        shutil.copytree(SERVER / folder, directory / folder)
    # The smoke server is an isolated overlay of the installed runtime.  It can
    # exercise newly built Chronicle jars before deploy, without changing the
    # play server or requiring its managed mod versions to be current.
    for prefix in ('chronicle_core-', 'quiet_below-'):
        for copied in (directory / 'mods').glob(prefix + '*.jar'):
            copied.unlink()
    for jar in artifacts():
        shutil.copy2(jar, directory / 'mods' / jar.name)
    shutil.copy2(SERVER / 'eula.txt', directory)
    (directory / 'server.properties').write_text('\n'.join([
        'server-ip=127.0.0.1', 'server-port=25575', 'online-mode=false',
        'white-list=false', 'enable-rcon=false', 'enable-query=false',
        'view-distance=3', 'simulation-distance=3', 'spawn-protection=0',
        'level-type=minecraft:flat', 'generate-structures=false', 'gamemode=creative',
        'difficulty=peaceful', 'level-name=world', 'max-players=4', 'sync-chunk-writes=true', ''
    ]), encoding='utf-8')
    offline_name = 'ChronicleTestA'
    offline_bytes = bytearray(hashlib.md5(f'OfflinePlayer:{offline_name}'.encode('utf-8'), usedforsecurity=False).digest())
    offline_bytes[6] = (offline_bytes[6] & 0x0f) | 0x30
    offline_bytes[8] = (offline_bytes[8] & 0x3f) | 0x80
    offline_uuid = f'{offline_bytes[0:4].hex()}-{offline_bytes[4:6].hex()}-{offline_bytes[6:8].hex()}-{offline_bytes[8:10].hex()}-{offline_bytes[10:16].hex()}'
    (directory / 'ops.json').write_text(json.dumps([{
        'uuid': offline_uuid, 'name': offline_name, 'level': 2, 'bypassesPlayerLimit': False
    }], indent=2), encoding='utf-8')
    (directory / 'config' / 'voicechat').mkdir(parents=True)
    (directory / 'config' / 'voicechat' / 'voicechat-server.properties').write_text('bind_address=127.0.0.1\nport=24455\n', encoding='utf-8')
    REPORTS.mkdir(parents=True, exist_ok=True)
    report = {'world': str(directory), 'minecraft': LOCK['minecraft'], 'minecraft_protocol': LOCK['minecraft_protocol'], 'result': 'FAIL', 'checks': []}
    try:
        for iteration in range(2):
            # Refuse to test against an unrelated process on the fixed bot port.
            with socket.socket() as probe:
                probe.bind(('127.0.0.1', 25575))
            with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
                probe.bind(('127.0.0.1', 24455))
            output = []
            lines = queue.Queue()
            process = subprocess.Popen(server_command(), cwd=directory, env=env(), stdin=subprocess.PIPE,
                                       stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace')
            def reader():
                for line in process.stdout:
                    output.append(line)
                    lines.put(line)
            thread = threading.Thread(target=reader, daemon=True)
            thread.start()
            try:
                deadline = time.monotonic() + 240
                ready = voice_ready = False
                while True:
                    if time.monotonic() > deadline or process.poll() is not None:
                        raise RuntimeError('Server failed to reach ready state; inspect smoke log')
                    try:
                        line = lines.get(timeout=1)
                    except queue.Empty:
                        continue
                    ready = ready or 'Done (' in line
                    voice_ready = voice_ready or 'Voice chat server started at 127.0.0.1:24455' in line
                    if ready and voice_ready:
                        break
                log = ''.join(output)
                for expected in ['Chronicle Core initialized', 'The Quiet Below initialized']:
                    if expected not in log:
                        raise RuntimeError(f'Missing mod initialization: {expected}')
                print(f'Server ready (pass {iteration + 1}); both mods initialized.', flush=True)
                process.stdin.write('chronicle status\n')
                process.stdin.flush()
                if iteration == 0:
                    bot_env = env()
                    bot_env['CHRONICLE_TRACE_DIR'] = str(REPORTS)
                    bot_env['CHRONICLE_SMOKE_PHASE'] = 'initial'
                    bot_env['CHRONICLE_STATE_PATH'] = str(directory / 'world' / 'chronicle' / 'campaign-state.json')
                    bot_env['CHRONICLE_PROFILES_DIR'] = str(directory / 'world' / 'chronicle' / 'profiles')
                    result = subprocess.run([shutil.which('node'), str(BOT / 'smoke.cjs')], cwd=BOT,
                                            env=bot_env, capture_output=True, text=True, timeout=180)
                    (REPORTS / f'smoke-{directory.name}-bots.log').write_text(result.stdout + result.stderr, encoding='utf-8')
                    print(result.stdout)
                    if result.returncode:
                        raise RuntimeError(result.stderr or 'Minecraft test clients failed')
                    report['checks'].append('GM/non-op clients verified session permissions, simulation/mob freeze, mobile players, actionbar status and M1 audit/events')
                    report['checks'].append('M2 commands and actual payloads verified own edits, public readonly views, throttling and stale/invalid save rejection')
                else:
                    bot_env = env()
                    bot_env['CHRONICLE_TRACE_DIR'] = str(REPORTS)
                    bot_env['CHRONICLE_SMOKE_PHASE'] = 'restart'
                    bot_env['CHRONICLE_STATE_PATH'] = str(directory / 'world' / 'chronicle' / 'campaign-state.json')
                    bot_env['CHRONICLE_PROFILES_DIR'] = str(directory / 'world' / 'chronicle' / 'profiles')
                    result = subprocess.run([shutil.which('node'), str(BOT / 'smoke.cjs')], cwd=BOT,
                                            env=bot_env, capture_output=True, text=True, timeout=180)
                    (REPORTS / f'smoke-{directory.name}-bots-restart.log').write_text(result.stdout + result.stderr, encoding='utf-8')
                    print(result.stdout)
                    if result.returncode:
                        raise RuntimeError(result.stderr or 'Restart Minecraft test clients failed')
                    report['checks'].append('restart preserved paused/frozen session, flags/events/audit and both UUID profiles before session increment')
                process.stdin.write('stop\n')
                process.stdin.flush()
                process.wait(timeout=90)
                if process.returncode:
                    raise RuntimeError(f'Server exited {process.returncode}')
                report['checks'].append(f'pass {iteration + 1}: both mods loaded, voice server bound, clean server stop')
            finally:
                if process.poll() is None:
                    try:
                        process.stdin.write('stop\n')
                        process.stdin.flush()
                        process.wait(timeout=60)
                    except (OSError, subprocess.TimeoutExpired):
                        process.kill()
                        process.wait(timeout=15)
                thread.join(timeout=5)
                (REPORTS / f'smoke-{directory.name}-{iteration + 1}.log').write_text(''.join(output), encoding='utf-8')
            if 'NoClassDefFoundError' in ''.join(output) and ('net/minecraft/client' in ''.join(output) or 'net.minecraft.client' in ''.join(output)):
                raise RuntimeError('Dedicated server attempted to load a client-only Chronicle class')
            expected_status = 'inactive' if iteration == 0 else 'paused'
            if f'Chronicle session status: {expected_status}' not in ''.join(output):
                raise RuntimeError('Console command response missing')
        report['result'] = 'PASS'
        print('PASS: dedicated server, two clients, command, clean stop and restart.')
    except Exception as error:
        report['error'] = str(error)
        raise
    finally:
        (REPORTS / 'smoke-latest.json').write_text(json.dumps(report, indent=2), encoding='utf-8')


def verify():
    for item, path in [(LOCK['java'], CACHE / LOCK['java']['filename']),
                       (LOCK['gradle'], CACHE / f"gradle-{LOCK['gradle']['version']}-bin.zip"),
                       (LOCK['installer'], CACHE / f"fabric-installer-{LOCK['installer']['version']}.jar")]:
        if not path.exists() or digest(path) != item['sha256']:
            raise RuntimeError(f'Hash verification failed: {path}')
    for mod in LOCK['mods']:
        path = SERVER / 'mods' / mod['filename']
        if not path.exists() or digest(path, 'sha512') != mod['sha512']:
            raise RuntimeError(f'Mod verification failed: {path}')
    for jar in artifacts():
        metadata = validate_mod_target(jar)
        print(f"Verified mod artifact: {metadata['id']} {metadata['version']} for {LOCK['minecraft']}")
    validate_server_runtime()
    validate_managed_mod_names()
    print('PASS: pinned download hashes and both built jar metadata.')


def doctor():
    print(f'Repository: {REPO}\nRuntime workspace: {WORK}')
    print(f"Minecraft {LOCK['minecraft']}; Fabric {LOCK['loader']}; Java {LOCK['java']['version']}")
    for label, path in [('Java', JAVA), ('Gradle', GRADLE), ('Server launcher', SERVER / 'fabric-server-launch.jar'),
                        ('Bot dependencies', BOT / 'node_modules' / 'mineflayer'), ('Client pack', WORK / 'client' / f"Chronicle-{MILESTONE}-{LOCK['minecraft']}.mrpack")]:
        print(f"{'OK' if path.exists() else 'MISSING'}: {label}: {path}")
    if JAVA.exists():
        run([JAVA, '-version'])
    try:
        require_eula()
        print('EULA: accepted')
    except RuntimeError:
        print('EULA: pending')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['setup','build','deploy','start','smoke','content-smoke','stage-smoke','legacy-smoke','backup','restore','pack','doctor','client','verify','pilot-prepare','pilot-start','pilot-smoke','pilot-promote','pilot-backup'])
    parser.add_argument('--accept-eula', action='store_true', help='Explicitly accept the Minecraft EULA (setup only)')
    parser.add_argument('--archive', help='Backup zip inside the workspace backups directory (restore only)')
    args = parser.parse_args()
    if args.accept_eula and args.action != 'setup':
        parser.error('--accept-eula is only valid with setup')
    actions = {'setup': lambda: setup(args.accept_eula), 'build': build, 'deploy': deploy,
               'start': start, 'smoke': lambda: __import__('fabric_smoke').run(),
               'content-smoke': lambda: __import__('fabric_smoke').run(regression=False),
               'stage-smoke': lambda: __import__('stage_smoke').run(),
               'pilot-smoke': lambda: __import__('stage_smoke').run(pilot=True),
               'pilot-prepare': lambda: __import__('pilot').prepare(),
               'pilot-start': lambda: __import__('pilot').start(),
               'pilot-promote': lambda: __import__('pilot').promote_tested(),
               'pilot-backup': lambda: __import__('pilot').backup(),
               'legacy-smoke': legacy_smoke, 'backup': backup, 'restore': lambda: restore(args.archive), 'pack': pack,
               'doctor': doctor, 'verify': verify,
               'client': lambda: run([REPO / 'gradlew.bat', '--no-daemon', ':campaigns:quiet-below:runClient'])}
    actions[args.action]()


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, subprocess.SubprocessError, zipfile.BadZipFile) as error:
        print(f'ERROR: {error}', file=sys.stderr)
        sys.exit(1)
