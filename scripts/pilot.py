"""Prepare and run an isolated, manually-built Quiet Below pilot fixture.

The workspace pilot command delegates here; direct invocation remains available
for local troubleshooting. ``prepare`` creates a fresh runtime under ../pilots;
the pilot's datapack contains a manual function and has no load/tick hook.
``start`` runs only the most recently prepared pilot on its own loopback port,
preserving the ordinary play server and world.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
from datetime import datetime, timezone
import zipfile

import workspace as w


PILOTS = w.WORK / 'pilots'
REPORT = w.REPORTS / 'pilot-latest.json'
FUNCTION_SOURCE = w.REPO / 'campaigns' / 'quiet-below' / 'scenario' / 'pilot-build.mcfunction'
PILOT_NAME = re.compile(r'^\d{8}T\d{6}\d{6}Z$')
PILOT_PORT = 25585
VOICE_PORT = 24465


def _version(server_jar: Path) -> dict:
    with zipfile.ZipFile(server_jar) as zipped:
        return json.loads(zipped.read('version.json'))


def _direct_pilot(path: Path) -> Path:
    root = Path(path).resolve()
    pilots = PILOTS.resolve()
    if root.parent != pilots or not PILOT_NAME.fullmatch(root.name):
        raise RuntimeError('Pilot path must be one directly contained timestamp directory under ../pilots')
    return root


def _copy_required(source: Path, destination: Path) -> None:
    if not source.is_file():
        raise RuntimeError(f'Required installed runtime file is missing: {source}')
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)


def _write_properties(root: Path) -> None:
    flat = ('{"layers":[{"block":"minecraft:bedrock","height":1},'
            '{"block":"minecraft:stone","height":125},'
            '{"block":"minecraft:dirt","height":2},'
            '{"block":"minecraft:grass_block","height":1}],'
            '"biome":"minecraft:plains","features":false,"lakes":false}')
    (root / 'server.properties').write_text('\n'.join([
        'server-ip=127.0.0.1', f'server-port={PILOT_PORT}', 'online-mode=true',
        'white-list=true', 'enforce-whitelist=true', 'enable-rcon=false',
        'enable-query=false', 'pvp=false', 'difficulty=peaceful',
        'gamemode=survival', 'level-type=minecraft:flat', f'generator-settings={flat}',
        'level-seed=0', 'generate-structures=false', 'level-name=world',
        'view-distance=8', 'simulation-distance=6', 'spawn-protection=0',
        'max-players=12', 'sync-chunk-writes=true',
        'motd=Chronicle - The Quiet Below pilot', '',
    ]), encoding='utf-8')
    voice = root / 'config' / 'voicechat' / 'voicechat-server.properties'
    voice.parent.mkdir(parents=True, exist_ok=True)
    voice.write_text(f'bind_address=127.0.0.1\nport={VOICE_PORT}\n', encoding='utf-8')


def _write_datapack(root: Path, version: dict) -> Path:
    pack = root / 'world' / 'datapacks' / 'chronicle_pilot'
    function = pack / 'data' / 'chronicle_pilot' / 'function' / 'build.mcfunction'
    function.parent.mkdir(parents=True, exist_ok=True)
    data_format = [version['pack_version']['data_major'], version['pack_version']['data_minor']]
    pack_meta = {
        'pack': {
            'min_format': data_format,
            'max_format': data_format,
            'description': 'Chronicle isolated pilot fixture; invoke chronicle_pilot:build manually once.',
        }
    }
    # 1.21.9 introduced required min_format/max_format pairs and prohibited the
    # old supported_formats field for modern packs.  1.21.10 pins data 88.0.
    # https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-9
    (pack / 'pack.mcmeta').parent.mkdir(parents=True, exist_ok=True)
    (pack / 'pack.mcmeta').write_text(json.dumps(pack_meta, indent=2) + '\n', encoding='utf-8')
    shutil.copy2(FUNCTION_SOURCE, function)
    return function


def prepare() -> Path:
    """Create a non-overwriting pilot runtime, ready for a manual datapack build."""
    w.require_eula()
    installed = w.validate_server_runtime()
    w.validate_managed_mod_names()
    built = w.artifacts()
    for jar in built:
        w.validate_mod_target(jar)
    if not FUNCTION_SOURCE.is_file():
        raise RuntimeError(f'Pilot function is missing: {FUNCTION_SOURCE}')
    if installed is None:
        raise RuntimeError('Installed server runtime is missing; run workspace setup first')
    version = _version(w.SERVER / 'server.jar')
    if version['id'] != w.LOCK['minecraft'] or version['pack_version'] != {
        'resource_major': 69, 'resource_minor': 0, 'data_major': 88, 'data_minor': 0
    }:
        raise RuntimeError('Installed server version metadata does not match the pinned 1.21.10 pilot format')

    PILOTS.mkdir(parents=True, exist_ok=True)
    root = _direct_pilot(PILOTS / datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ'))
    root.mkdir()
    try:
        for name in ('fabric-server-launch.jar', 'fabric-server-launcher.properties', 'server.jar', 'eula.txt'):
            _copy_required(w.SERVER / name, root / name)
        for name in ('libraries', 'mods'):
            source = w.SERVER / name
            if not source.is_dir():
                raise RuntimeError(f'Required installed runtime directory is missing: {source}')
            shutil.copytree(source, root / name)
        for name in ('whitelist.json', 'ops.json'):
            source = w.SERVER / name
            target = root / name
            if source.is_file():
                shutil.copy2(source, target)
            else:
                target.write_text('[]\n', encoding='utf-8')

        copied_mods = root / 'mods'
        for prefix in ('chronicle_core-', 'quiet_below-'):
            for jar in copied_mods.glob(prefix + '*.jar'):
                jar.unlink()
        for jar in built:
            shutil.copy2(jar, copied_mods / jar.name)
        _write_properties(root)
        function = _write_datapack(root, version)
        manifest = {
            'schema': 1,
            'status': 'prepared',
            'minecraft': version['id'],
            'pack_data_major': version['pack_version']['data_major'],
            'pack_data_minor': version['pack_version']['data_minor'],
            'server_port': PILOT_PORT,
            'voice_port': VOICE_PORT,
            'function': str(function.relative_to(root)),
            'mod_sources': [
                {'path': str(jar), 'sha256': w.digest(jar), 'version': w.validate_mod_target(jar)['version']}
                for jar in built
            ],
        }
        (root / '.chronicle-pilot.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
        w.REPORTS.mkdir(parents=True, exist_ok=True)
        report = {'schema': 1, 'status': 'prepared', 'pilot': str(root), **manifest}
        REPORT.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
        print(f'Prepared isolated pilot runtime: {root}')
        print('After the server is ready, run in its console: function chronicle_pilot:build')
        return root
    except Exception:
        # A partially copied directory is retained for diagnosis rather than
        # deleted; a retry always creates a new timestamped pilot directory.
        raise


def _unescape_property(value: str) -> str:
    """Decode the bounded Java .properties escapes used by server.properties."""
    result: list[str] = []
    index = 0
    short = {'t': '\t', 'n': '\n', 'r': '\r', 'f': '\f'}
    while index < len(value):
        character = value[index]
        if character != '\\':
            result.append(character)
            index += 1
            continue
        index += 1
        if index >= len(value):
            raise RuntimeError('Malformed trailing escape in pilot server property')
        escaped = value[index]
        if escaped == 'u':
            digits = value[index + 1:index + 5]
            if len(digits) != 4 or not re.fullmatch(r'[0-9A-Fa-f]{4}', digits):
                raise RuntimeError('Malformed Unicode escape in pilot server property')
            result.append(chr(int(digits, 16)))
            index += 5
        else:
            result.append(short.get(escaped, escaped))
            index += 1
    return ''.join(result)


def _properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in path.read_text(encoding='utf-8').splitlines():
        stripped = line.lstrip(' \t\f')
        if not stripped or stripped.startswith(('#', '!')):
            continue
        trailing = len(stripped) - len(stripped.rstrip('\\'))
        if trailing % 2:
            raise RuntimeError('Multiline pilot server properties are not accepted')
        separator = None
        escaped = False
        for index, character in enumerate(stripped):
            if escaped:
                escaped = False
            elif character == '\\':
                escaped = True
            elif character in '=:':
                separator = index
                break
        if separator is None:
            raise RuntimeError(f'Malformed pilot server property: {line!r}')
        key = _unescape_property(stripped[:separator])
        value = _unescape_property(stripped[separator + 1:].lstrip(' \t\f'))
        if not key or key in values:
            raise RuntimeError(f'Duplicate or empty pilot server property: {key!r}')
        values[key] = value
    return values


def _validate_prepared_mods(root: Path, manifest: dict) -> None:
    sources = manifest.get('mod_sources')
    if not isinstance(sources, list) or len(sources) != 2:
        raise RuntimeError('Pilot manifest must contain exactly two Chronicle mod sources')
    expected_names: set[str] = set()
    expected_ids: set[str] = set()
    for source in sources:
        if not isinstance(source, dict) or not isinstance(source.get('path'), str):
            raise RuntimeError('Pilot manifest has an invalid mod source entry')
        name = Path(source['path']).name
        if name != source['path'].replace('\\', '/').rsplit('/', 1)[-1] or not name.endswith('.jar'):
            raise RuntimeError('Pilot manifest mod source must name a direct jar file')
        copied = root / 'mods' / name
        if not copied.is_file() or w.digest(copied) != source.get('sha256'):
            raise RuntimeError(f'Pilot Chronicle mod hash does not match the prepared manifest: {name}')
        metadata = w.validate_mod_target(copied)
        if metadata.get('version') != source.get('version'):
            raise RuntimeError(f'Pilot Chronicle mod version does not match the prepared manifest: {name}')
        expected_names.add(name)
        expected_ids.add(metadata.get('id'))
    if expected_ids != {'chronicle_core', 'quiet_below'}:
        raise RuntimeError('Pilot manifest does not contain exactly Chronicle Core and The Quiet Below')
    actual_names = {
        jar.name for prefix in ('chronicle_core-', 'quiet_below-')
        for jar in (root / 'mods').glob(prefix + '*.jar')
    }
    if actual_names != expected_names:
        raise RuntimeError('Pilot Chronicle mod set differs from the prepared manifest')


def resolve_latest() -> Path:
    """Resolve only a validated prepared pilot recorded by this tool."""
    if not REPORT.is_file():
        raise RuntimeError('No pilot preparation report exists; run prepare first')
    report = json.loads(REPORT.read_text(encoding='utf-8'))
    if report.get('status') != 'prepared' or report.get('minecraft') != w.LOCK['minecraft']:
        raise RuntimeError('Latest pilot report is not a prepared runtime for the pinned Minecraft version')
    root = _direct_pilot(Path(report.get('pilot', '')))
    manifest_path = root / '.chronicle-pilot.json'
    if not manifest_path.is_file():
        raise RuntimeError('Latest pilot is missing its preparation manifest')
    manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
    if manifest.get('status') != 'prepared' or manifest.get('minecraft') != w.LOCK['minecraft']:
        raise RuntimeError('Latest pilot manifest is not compatible with the pinned Minecraft version')
    version = _version(root / 'server.jar')
    if version['id'] != w.LOCK['minecraft'] or version.get('protocol_version') != w.LOCK['minecraft_protocol']:
        raise RuntimeError('Latest pilot server jar is not the pinned Minecraft version')
    if [manifest.get('pack_data_major'), manifest.get('pack_data_minor')] != [
        version['pack_version']['data_major'], version['pack_version']['data_minor']
    ]:
        raise RuntimeError('Latest pilot datapack version does not match its server jar')
    expected_properties = {
        'server-ip': '127.0.0.1', 'server-port': str(PILOT_PORT), 'online-mode': 'true',
        'white-list': 'true', 'enforce-whitelist': 'true', 'enable-rcon': 'false',
        'enable-query': 'false', 'level-name': 'world', 'level-type': 'minecraft:flat',
        'level-seed': '0', 'difficulty': 'peaceful', 'pvp': 'false',
    }
    properties = _properties(root / 'server.properties')
    if any(properties.get(key) != value for key, value in expected_properties.items()):
        raise RuntimeError('Latest pilot server properties no longer match the isolated prepared configuration')
    pack_meta = root / 'world' / 'datapacks' / 'chronicle_pilot' / 'pack.mcmeta'
    if not pack_meta.is_file():
        raise RuntimeError('Latest pilot datapack metadata is missing')
    pack = json.loads(pack_meta.read_text(encoding='utf-8'))
    if not isinstance(pack, dict) or not isinstance(pack.get('pack'), dict):
        raise RuntimeError('Latest pilot datapack metadata has no valid pack object')
    pack_details = pack['pack']
    expected_format = [version['pack_version']['data_major'], version['pack_version']['data_minor']]
    if pack_details.get('min_format') != expected_format or pack_details.get('max_format') != expected_format:
        raise RuntimeError('Latest pilot datapack metadata is not pinned to the pilot server format')
    if 'pack_format' in pack_details or 'supported_formats' in pack_details:
        raise RuntimeError('Latest pilot datapack uses an obsolete pack metadata field')
    _validate_prepared_mods(root, manifest)
    return root


def start() -> None:
    """Run the prepared pilot interactively; Ctrl+C lets Minecraft save normally."""
    root = resolve_latest()
    w.require_eula()
    w.check_stopped(root)
    marker = root / '.chronicle-process.json'
    with marker.open('x', encoding='utf-8') as out:
        json.dump({'controller_pid': os.getpid(), 'state': 'starting'}, out)
    process = None
    try:
        process = subprocess.Popen(w.server_command(), cwd=root, env=w.env())
        marker.write_text(json.dumps({
            'pid': process.pid, 'controller_pid': os.getpid(),
            'started': datetime.now(timezone.utc).isoformat(), 'server_port': PILOT_PORT,
        }), encoding='utf-8')
        try:
            result = process.wait()
        except KeyboardInterrupt:
            result = process.wait(timeout=90)
        if result:
            raise RuntimeError(f'Minecraft pilot server exited {result}')
    finally:
        if process is None or process.poll() is not None:
            marker.unlink(missing_ok=True)


def promote_tested() -> Path:
    """Copy verified site data into a still-virgin pilot; never replace an existing world."""
    root = resolve_latest()
    w.check_stopped(root)
    if (root / 'world' / 'level.dat').exists() or any((root / 'world').glob('region/*')):
        raise RuntimeError('Site promotion requires a fresh prepared pilot with no existing world')
    result = json.loads((w.REPORTS / 'pilot-test-latest.json').read_text(encoding='utf-8'))
    if result.get('result') != 'PASS':
        raise RuntimeError('The latest real-client pilot test must pass before site promotion')
    tested = Path(result['world']).resolve()
    if tested.parent != (w.WORK / 'test-runs').resolve() or not PILOT_NAME.fullmatch(tested.name):
        raise RuntimeError('Tested site must be a direct timestamp child of test-runs')
    w.check_stopped(tested)
    relative_function = Path('world/datapacks/chronicle_pilot/data/chronicle_pilot/function/build.mcfunction')
    if (root / relative_function).read_bytes() != (tested / relative_function).read_bytes():
        raise RuntimeError('Tested pilot function differs from the fresh prepared site')
    manifest_path = root / '.chronicle-pilot.json'
    manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
    _validate_prepared_mods(tested, manifest)
    campaign_file = tested / 'world/chronicle/campaign-state.json'
    if campaign_file.exists():
        campaign = json.loads(campaign_file.read_text(encoding='utf-8'))
        if campaign['phase'] != 'INACTIVE' or campaign['sessionNumber'] != 0 or campaign['history'] or campaign['flags']:
            raise RuntimeError('Pilot rehearsal changed campaign state; prepare a clean trial instead')
    stage_file = tested / 'world/quiet-below/stages.json'
    if stage_file.exists():
        stage = json.loads(stage_file.read_text(encoding='utf-8'))
        if stage['operation'] is not None or stage['zones']:
            raise RuntimeError('Pilot rehearsal configured environmental stages; use a clean trial')
    # Player identities, cards, advancements and stats stay in the disposable test.
    # The destination has no level.dat/regions, so no played world is overwritten.
    for name in ('region', 'entities', 'poi', 'data'):
        source = tested / 'world' / name
        if source.exists():
            shutil.copytree(source, root / 'world' / name)
    shutil.copy2(tested / 'world/level.dat', root / 'world/level.dat')
    manifest['site_built'] = True
    manifest['verified_site_source'] = str(tested)
    manifest['verified_site_report'] = result['reports']
    manifest_path.write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    report = json.loads(REPORT.read_text(encoding='utf-8'))
    report.update({key: manifest[key] for key in ('site_built', 'verified_site_source', 'verified_site_report')})
    REPORT.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print('Promoted verified site into fresh authenticated pilot:', root)
    return root


def backup() -> Path:
    root = resolve_latest()
    w.check_stopped(root)
    destination = w.WORK / 'backups' / f"pilot-{datetime.now(timezone.utc):%Y%m%dT%H%M%S%fZ}.zip"
    destination.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(destination, 'x', compression=zipfile.ZIP_DEFLATED) as zipped:
        for path in root.rglob('*'):
            if path.is_file() and path.relative_to(root).parts[0] not in {'libraries', 'logs', 'versions', 'cache'}:
                zipped.write(path, path.relative_to(root))
        zipped.write(w.REPO / 'bootstrap.lock.json', 'chronicle-bootstrap.lock.json')
    with zipfile.ZipFile(destination) as zipped:
        if zipped.testzip():
            raise RuntimeError('Pilot backup failed CRC verification')
    print('Pilot backup verified:', destination)
    return destination


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('prepare', 'start', 'promote', 'backup'))
    action = parser.parse_args().action
    {'prepare': prepare, 'start': start, 'promote': promote_tested, 'backup': backup}[action]()


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, subprocess.SubprocessError, zipfile.BadZipFile) as error:
        print(f'ERROR: {error}', file=sys.stderr)
        sys.exit(1)
