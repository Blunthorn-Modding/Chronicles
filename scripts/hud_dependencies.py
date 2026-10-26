"""Pinned client HUD dependencies; distribute official references, never vendor jars.

The local cache is for installation/testing only. Neither this module nor the
normal server bootstrap installs these client tools on a server.
"""
from __future__ import annotations

import argparse
import json
import hashlib
import io
from pathlib import Path
import re
from urllib.parse import urlsplit, unquote
import zipfile

import workspace as w
import client_defaults

LOCK_PATH = w.REPO / 'config/hud-dependencies.lock.json'
PROJECTS = {'spiffyhud': 'wtOPHICQ', 'fancymenu': 'Wq5SjeWM',
            'konkrete': 'J81TRJWm', 'melody': 'CVT4pFB2'}


def load_lock(path=LOCK_PATH):
    data = json.loads(Path(path).read_text(encoding='utf-8'))
    if (data.get('schema') != 1 or data.get('minecraft') != w.LOCK['minecraft']
            or data.get('distribution') != 'official-download-references-only'):
        raise ValueError('Unsupported HUD lock or distribution policy')
    mods = data['mods']
    if len(mods) != len(PROJECTS) or {m['slug'] for m in mods} != PROJECTS.keys():
        raise ValueError('HUD dependency set must be reviewed together')
    for mod in mods:
        parsed = urlsplit(mod['url'])
        expected = f"/data/{mod['project_id']}/versions/{mod['version_id']}/{mod['filename']}"
        if (mod['project_id'] != PROJECTS[mod['slug']] or parsed.scheme != 'https'
                or parsed.netloc != 'cdn.modrinth.com' or unquote(parsed.path) != expected
                or parsed.query or parsed.fragment or Path(mod['filename']).name != mod['filename']
                or '/' in mod['filename'] or '\\' in mod['filename']
                or not mod['filename'].endswith('.jar')
                or not isinstance(mod['size'], int) or not 0 < mod['size'] < 20_000_000
                or not re.fullmatch('[a-f0-9]{40}', mod['sha1'])
                or not re.fullmatch('[a-f0-9]{128}', mod['sha512'])):
            raise ValueError('HUD dependency is not a pinned official Modrinth artifact')
    return mods


def index_entries(mods):
    return [{'path': 'mods/' + mod['filename'],
             'hashes': {'sha1': mod['sha1'], 'sha512': mod['sha512']},
             'env': {'client': 'required', 'server': 'unsupported'},
             'downloads': [mod['url']], 'fileSize': mod['size']} for mod in mods]


def notices(mods):
    lines = ['Chronicle client HUD - third-party dependencies',
             'Downloaded separately from the authors\' official Modrinth releases.',
             'Their code, assets and jars are not included in this pack or owned by Chronicle.',
             'FancyMenu / SpiffyHUD / Konkrete / Melody: Keksuccino and their contributors.', '']
    for mod in mods:
        lines += [f"{mod['slug']} {mod['version']} - {mod['license']}",
                  mod['project_url'], 'License: ' + mod['license_url'], '']
    library = bundled_library_lock()
    lines += ['Core bundled library: ' + library['coordinate'] + ' - ' + library['license'],
              library['upstreamUrl'], 'Full license inside chronicle_core.jar: ' + library['licensePath'], '']
    return '\n'.join(lines)


def cached_jars(mods):
    """Private installation cache only; never use these paths as pack overrides."""
    result = []
    for mod in mods:
        target = w.CACHE / 'hud-prototype' / mod['filename']
        w.download(mod['url'], target, mod['sha512'], 'sha512')
        if target.stat().st_size != mod['size']:
            raise RuntimeError('HUD dependency size mismatch: ' + mod['slug'])
        result.append(target)
    return result


# Updating the approved library requires a reviewed code change as well as a lock edit.
BUNDLED_LIBRARY_PIN = "d991e1e83c5c43313fa23e13875de6f6c8df884ec534edb172c681f93e7db073"


def bundled_library_lock(path=None):
    """Code-sealed repository pin for the sole Apache-2.0 Core library."""
    lock = json.loads(Path(path or w.REPO / 'config/bundled-library.lock.json').read_text(encoding='utf-8'))
    fingerprint = hashlib.sha256(json.dumps(lock, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
    if fingerprint != BUNDLED_LIBRARY_PIN:
        raise ValueError('Unapproved bundled library pin; review identity, upstream hashes and full license before changing it')
    return lock


def _library_payload(archive):
    files = [entry for entry in archive.infolist() if not entry.is_dir()]
    if len({entry.filename for entry in files}) != len(files) or sum(entry.file_size for entry in files) > 8 * 1024 * 1024:
        raise RuntimeError('Duplicate or oversized bundled library payload')
    manifest = {entry.filename: hashlib.sha256(archive.read(entry)).hexdigest()
                for entry in files if entry.filename != 'fabric.mod.json'}
    return hashlib.sha256(json.dumps(manifest, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def _verify_bundled_library(archive, metadata, name):
    lock = bundled_library_lock()
    if metadata['id'] != 'chronicle_core' or name != lock['nestedPath'] or metadata.get('jars') != [{'file': name}]:
        raise RuntimeError('Unapproved nested library or declaration')
    if hashlib.sha256(archive.read(lock['licensePath'])).hexdigest() != lock['licenseSha256']:
        raise RuntimeError('Bundled library full license is missing or changed')
    data = archive.read(name)
    if len(data) > 2 * 1024 * 1024:
        raise RuntimeError('Oversized nested library')
    with zipfile.ZipFile(io.BytesIO(data)) as library:
        if _library_payload(library) != lock['payloadSha256']:
            raise RuntimeError('Bundled library differs from the pinned upstream payload')
        generated = json.loads(library.read('fabric.mod.json'))
        if generated != {'schemaVersion': 1, 'id': lock['modId'], 'version': lock['version'],
                         'name': 'snakeyaml-engine', 'custom': {'fabric-loom:generated': True}}:
            raise RuntimeError('Unexpected Fabric library metadata')


def verify_owned_jar(jar):
    metadata = w.validate_mod_target(jar)
    if metadata['id'] not in {'chronicle_core', 'quiet_below'}:
        raise RuntimeError('Only Chronicle-owned artifacts may be embedded')
    with zipfile.ZipFile(jar) as archive:
        if len(set(archive.namelist())) != len(archive.namelist()):
            raise RuntimeError('Duplicate artifact entries')
        nested = []
        for name in archive.namelist():
            if name.startswith(('de/keksuccino/', 'assets/fancymenu/', 'assets/spiffyhud/', 'assets/konkrete/', 'assets/melody/')):
                raise RuntimeError('Vendor files in Chronicle artifact; review licensing before distribution')
            if name.endswith('.jar'):
                try:
                    _verify_bundled_library(archive, metadata, name)
                except (KeyError, ValueError, zipfile.BadZipFile) as failure:
                    raise RuntimeError('Invalid or unlicensed nested library') from failure
                nested.append(name)
        if {entry.get('file') for entry in metadata.get('jars', [])} != set(nested):
            raise RuntimeError('Nested artifact declarations do not match files')


def pack():
    """Separate prototype; preserve the current review client pack."""
    mods = load_lock()
    jars = w.artifacts()
    versions = {w.validate_mod_target(jar)['version'] for jar in jars}
    if len(versions) != 1:
        raise RuntimeError('Chronicle artifacts must have matching versions')
    target = w.WORK / 'client' / f"Chronicle-HUD-Prototype-{w.LOCK['minecraft']}.mrpack"
    target.parent.mkdir(parents=True, exist_ok=True)
    files = [{'path': 'mods/' + m['filename'], 'hashes': {'sha1': m['sha1'], 'sha512': m['sha512']},
              'env': {'client': 'required', 'server': 'required'}, 'downloads': [m['url']], 'fileSize': m['size']}
             for m in w.LOCK['mods']] + index_entries(mods)
    index = {'formatVersion': 1, 'game': 'minecraft', 'versionId': versions.pop() + '+hud-prototype',
             'name': 'Chronicle HUD prototype', 'summary': 'Isolated HUD integration, not Altar gameplay',
             'dependencies': {'minecraft': w.LOCK['minecraft'], 'fabric-loader': w.LOCK['loader']}, 'files': files}
    # Only our own two built mods may be embedded. All four HUD dependencies
    # remain index download references, including the permissive dependencies.
    if {w.validate_mod_target(jar)['id'] for jar in jars} != {'chronicle_core', 'quiet_below'}:
        raise RuntimeError('Only Chronicle-owned artifacts may be embedded')
    for jar in jars:
        verify_owned_jar(jar)
    with zipfile.ZipFile(target, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr('modrinth.index.json', json.dumps(index, indent=2))
        archive.writestr('client-overrides/options.txt', w.client_options())
        for name, contents in client_defaults.payloads(w.WORK, w.LOCK['minecraft_protocol']).items():
            archive.writestr('client-overrides/' + name, contents)
        archive.writestr('client-overrides/Chronicle-THIRD-PARTY.txt', notices(mods))
        for jar in jars:
            archive.write(jar, 'overrides/mods/' + jar.name)
    verify_pack(target, mods)
    print('HUD prototype pack:', target)
    return target


def verify_pack(target, mods, artifacts=None):
    with zipfile.ZipFile(target) as archive:
        if archive.testzip():
            raise RuntimeError('HUD pack CRC failure')
        index = json.loads(archive.read('modrinth.index.json'))
        for entry in index_entries(mods):
            if entry not in index['files']:
                raise RuntimeError('Missing exact official client dependency reference')
        expected = {'overrides/mods/' + jar.name for jar in (w.artifacts() if artifacts is None else artifacts)}
        actual = {n for n in archive.namelist() if n.endswith('.jar')}
        if actual != expected:
            raise RuntimeError('Unexpected embedded jar: HUD dependencies must remain references')
        # Also reject renamed/copied vendor classes or assets outside jars.
        defaults = {name: archive.read('client-overrides/' + name) for name in (client_defaults.FANCY_PATH, client_defaults.SERVERS_PATH)}
        client_defaults.validate_payloads(defaults)
        allowed = expected | {'client-overrides/' + name for name in defaults} | {'modrinth.index.json', 'client-overrides/options.txt',
                              'client-overrides/Chronicle-THIRD-PARTY.txt'}
        if set(archive.namelist()) != allowed:
            raise RuntimeError('Unexpected pack override; review provenance before distributing')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['pack', 'download'])
    args = parser.parse_args()
    if args.action == 'pack':
        pack()
    else:
        cached_jars(load_lock())
