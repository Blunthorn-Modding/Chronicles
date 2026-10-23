"""Pinned client HUD dependencies; distribute official references, never vendor jars.

The local cache is for installation/testing only. Neither this module nor the
normal server bootstrap installs these client tools on a server.
"""
from __future__ import annotations

import argparse
import json
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


def verify_owned_jar(jar):
    if w.validate_mod_target(jar)['id'] not in {'chronicle_core', 'quiet_below'}:
        raise RuntimeError('Only Chronicle-owned artifacts may be embedded')
    with zipfile.ZipFile(jar) as archive:
        for name in archive.namelist():
            if name.endswith('.jar') or name.startswith(('de/keksuccino/', 'assets/fancymenu/',
                    'assets/spiffyhud/', 'assets/konkrete/', 'assets/melody/')):
                raise RuntimeError('Nested/vendor files in Chronicle artifact; review licensing before distribution')


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
