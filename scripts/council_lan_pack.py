"""Build portable Council LAN-review packs from a stopped, disposable review world."""
from __future__ import annotations

import gzip
import hashlib
import io
import json
from pathlib import Path
import zipfile

import client_defaults
import council_review
import scenario_authoring as safe
import workspace as w

WORLD = 'Chronicle-Council-Review'
WORLD_MAP_FILES = {
    'authoring.json', 'map-assignments.json', 'scenario-selection.json',
    'managed-blocks.json', 'player-roles.json', 'player-roles.yaml',
    'player-roles.yaml.initialized', 'encounter-authoring.json',
    'encounter-authoring.initialized',
}
TERRAIN = {'region', 'entities', 'poi', 'DIM-1', 'DIM1', 'data', 'datapacks'}


def encoded(value):
    return (json.dumps(value, indent=2, ensure_ascii=False) + '\n').encode('utf-8')


def level_data(raw):
    # Reuse the workspace's installed NBT tool; no Minecraft binaries are bundled.
    import sys
    sys.path.insert(0, str(w.TOOLS / 'map-audit-deps'))
    try:
        import nbtlib
    finally:
        sys.path.pop(0)
    level = nbtlib.File.parse(io.BytesIO(gzip.decompress(raw)))
    data = level['Data']
    if int(data['DataVersion']) != 4556:
        raise RuntimeError('Expected the reviewed Minecraft 1.21.10 world')
    data.pop('Player', None)
    data['LevelName'] = nbtlib.String('Chronicle - Council review (e4mc)')
    data['allowCommands'] = nbtlib.Byte(1)
    data['GameType'] = nbtlib.Int(1)
    buffer = io.BytesIO()
    level.write(buffer)
    return gzip.compress(buffer.getvalue(), mtime=0)


def snapshot(root):
    """Preserve map/authoring, reset only the exported fixture's live progress."""
    w.check_stopped(root)
    source = root / 'world'
    files = {}
    for p in sorted(source.rglob('*')):
        if p.is_symlink() or p.is_junction():
            raise RuntimeError('World contains a link or junction')
        if not p.is_file():
            continue
        rel = p.relative_to(source)
        if p.name in {'session.lock', 'level.dat_old'} or p.suffix in {'.bak', '.lock'}:
            continue
        if rel.parts[0] in TERRAIN or (rel.parts[0] == 'chronicle' and len(rel.parts) == 2 and p.name in WORLD_MAP_FILES):
            files[rel.as_posix()] = safe.regular(p).read_bytes()
    files['level.dat'] = level_data(safe.regular(source / 'level.dat').read_bytes())
    council = source / 'chronicle/last-council'
    # Review-only office and placeholder assignments, before automatic papers issue.
    for name in ('chancellor.json', 'roster.json'):
        files['chronicle/last-council/' + name] = safe.regular(council / name).read_bytes()
    # A ceremony binding is map authoring, unlike the private treaties journal.
    if (council / 'treaty-lecterns.json').exists():
        files['chronicle/last-council/treaty-lecterns.json'] = safe.regular(council / 'treaty-lecterns.json').read_bytes()
    document = json.loads(safe.regular(council / 'proceedings.json').read_text(encoding='utf-8'))
    files['chronicle/last-council/proceedings.json'] = encoded(review_proceedings(document))
    return files


def review_proceedings(document):
    """Reset the exported fixture, accepting each supported on-disk schema."""
    schema = document['schemaVersion']
    if schema not in (1, 2, 3):
        raise RuntimeError('Unsupported Council proceedings schema')
    state = document['state']
    rounds = {} if schema == 1 else state['succession']['rounds']
    regency_motions = {state['attempts'][identity]['motion'] for identity in rounds}
    motions = {}
    for identity, motion in state['motions'].items():
        if not motion['hostPrepared'] or identity in regency_motions:
            continue
        clean = dict(motion, sponsors=[], status='AGENDA', ballots=[], notes=[])
        motions[identity] = clean
    return {
        'schemaVersion': 3,
        'state': {'revision': 0, 'meeting': 0, 'phase': 'BETWEEN',
                  'votingRegion': state['votingRegion'], 'lecterns': state['lecterns'],
                  'motions': motions, 'attempts': {}, 'activeBallot': None,
                  'pending': None, 'calls': {}, 'deferredAt': {},
                  'succession': {'stage': 'NOT_STARTED', 'initial': [], 'remaining': [],
                                 'crowned': '', 'rounds': {}, 'withdrawals': []}},
    }


def instructions(version):
    return f'''Chronicle Council review {version} - e4mc

ONE PERSON HOSTS. Everyone imports their pack as a NEW launcher instance.

HOST: Import the Host .mrpack, then Singleplayer > Chronicle - Council review
(e4mc). It includes the review platform and bound motion lectern at 0,65,4.
Press Escape > Open to LAN. Set Allow Commands to OFF for guests; keep Creative for setup.
Open the world and send the public e4mc address from chat to your reviewer.
The world already has cheats enabled for its local host. Do not run a separate server.

PLAYER: Import the Player .mrpack. Multiplayer > Direct Connection, paste the
host's e4mc address. Do not use localhost and do not open another world.
If you received only the Host pack you can use it to join too; leave its world
unopened. Only the person actually hosting loads the bundled world.

SchinderRenge is preset as Chancellor without a house. The host can change
this before play using /chronicle council characters > Chancellor.
The host has setup permissions; joining players do not automatically become OP.
If the Chancellor is a guest, the host handles operator-only setup.

BEFORE STARTING PLAY: /chronicle editing on; open /chronicle council characters
and assign each house player an unoccupied house. The Chancellor cannot also
own a house. Offline placeholders never enter a Regency slate: assign at least
two real, present house players as candidates to test Regency.
Players join participation with /chronicle players join. Turn Editing off.
Start play with /chronicle session start. House papers issue automatically.
I = Chancellor proceedings; R = current ballot; both are rebindable.
Use the lectern to view/propose motions. Voters must be inside the Council
Region (x/z -5..5, y 64..80) when the ballot opens. Creative mode is allowed.
Later Regency ballots wait for every remaining candidate to return.

The separate treaty lectern is at 0,65,2. Two or more House representatives
open it together to create an agreement, then sign the exact terms individually.
The appointed Chancellor opens the same lectern to sanctify after everyone
has signed and returned. Signatures survive leaving/reconnecting. Changed
drafts clear signatures. Private agreements and breaches remain private to
their parties and the Chancellor. Realm ratification/amendments are not included.

LATE ARRIVAL: pause play, enable Editing, and assign an unused House to an
unassigned online player. Existing papers and historical ballots stay fixed.
Once Regency starts, late arrivals can join as voters but not new candidates.
The source catalogue is in Advanced > Game setup > Source catalogue (OP only).

This is a FRESH REVIEW COPY: session progress, cast, discoveries, ballots,
player inventories and personal cards were omitted. Terrain, map bindings,
scenario settings, Chancellor and placeholder setup are retained. The source
dedicated review world is unchanged. Use a fresh import to repeat setup later.

Simple Voice Chat is omitted for this test. Use your usual external call.
Only the host pack includes e4mc. The host's game must remain running.
e4mc opens a temporary public game connection through its relay. Share the
address with your intended reviewers. No launcher accounts, access tokens,
server operator lists or machine-specific server presets are bundled.

Minecraft 1.21.10 / Fabric {w.LOCK['loader']} / e4mc 6.2.3 (MIT).
Third-party mods are referenced using official Modrinth downloads; their
licenses remain their own. See licenses/chronicle and licenses/e4mc.
'''


def build():
    root, meta = council_review.resolve()
    w.check_stopped(root)
    version, owned = council_review.jars()
    if version != meta['version'] or any(w.digest(p) != meta['mods'].get(p.name) for p in owned):
        raise RuntimeError('Build and source review differ')
    lock = json.loads((w.REPO / 'config/e4mc-review.lock.json').read_text(encoding='utf-8'))
    if lock['minecraft'] != w.LOCK['minecraft'] or lock['loader'] != 'fabric':
        raise RuntimeError('e4mc target differs from review')
    entry = lock['file']
    jar = w.download(entry['url'], w.CACHE / 'e4mc' / lock['versionId'] / entry['filename'], entry['hashes']['sha512'], 'sha512')
    if jar.stat().st_size != entry['size'] or w.digest(jar, 'sha1') != entry['hashes']['sha1']:
        raise RuntimeError('e4mc artifact mismatch')
    license_path = w.REPO / 'config/third-party/e4mc-LICENSE.txt'
    if w.digest(license_path) != lock['licenseSHA256']:
        raise RuntimeError('e4mc license changed')
    world = snapshot(root)
    readme = instructions(version).encode('utf-8')
    common = {'options.txt': w.client_options().encode('utf-8'),
              client_defaults.FANCY_PATH: (w.REPO / 'config/client/fancymenu/options.txt').read_bytes(),
              client_defaults.SERVERS_PATH: encoded({'version': 1, 'servers': []}),
              'CHRONICLE-REVIEW.txt': readme,
              'licenses/chronicle/LICENSE.txt': (w.REPO / 'LICENSE').read_bytes(),
              'licenses/chronicle/NOTICE.txt': (w.REPO / 'NOTICE').read_bytes(),
              'licenses/e4mc/LICENSE.txt': license_path.read_bytes()}
    files = [{'path': 'mods/' + m['filename'], 'hashes': {'sha1': m['sha1'], 'sha512': m['sha512']},
              'env': {'client': 'required', 'server': 'required'}, 'downloads': [m['url']], 'fileSize': m['size']}
             for m in w.LOCK['mods'] if m['slug'] == 'fabric-api']
    e4mc = {'path': 'mods/' + entry['filename'], 'hashes': entry['hashes'],
            'env': {'client': 'required', 'server': 'unsupported'},
            'downloads': [entry['url']], 'fileSize': entry['size']}
    result = {'result': 'PASS', 'sourceReview': str(root), 'version': version,
              'e4mcVersion': lock['versionId'], 'worldFiles': len(world), 'artifacts': []}
    for kind in ('Host', 'Player'):
        index = {'formatVersion': 1, 'game': 'minecraft', 'versionId': version + '-e4mc-' + kind.lower(),
                 'name': 'Chronicle Council review - ' + kind,
                 'summary': 'Council review; voice chat omitted; one person hosts through e4mc',
                 'files': files + ([e4mc] if kind == 'Host' else []),
                 'dependencies': {'minecraft': w.LOCK['minecraft'], 'fabric-loader': w.LOCK['loader']}}
        target = w.WORK / 'client' / f'Chronicle-Last-Council-{version}-e4mc-{kind}.mrpack'
        overrides = dict(common)
        overrides.update({'mods/' + p.name: p.read_bytes() for p in owned})
        if kind == 'Host':
            overrides.update({'saves/' + WORLD + '/' + name: raw for name, raw in world.items()})
            for p in (root / 'config/chronicle').rglob('*.json'):
                rel = p.relative_to(root).as_posix()
                overrides[rel] = safe.regular(p).read_bytes()
        with zipfile.ZipFile(target, 'w', compression=zipfile.ZIP_DEFLATED) as z:
            z.writestr('modrinth.index.json', encoded(index))
            for name, raw in sorted(overrides.items()):
                z.writestr('overrides/' + name, raw)
        with zipfile.ZipFile(target) as z:
            if z.testzip() is not None or set(z.namelist()) != {'modrinth.index.json', *('overrides/' + name for name in overrides)}:
                raise RuntimeError('Pack archive integrity failed')
            for name, raw in overrides.items():
                if z.read('overrides/' + name) != raw:
                    raise RuntimeError('Pack payload changed')
            if any('voicechat' in name.lower() or 'token' in name.lower() or 'cast.json' in name for name in z.namelist()):
                raise RuntimeError('Unexpected private/runtime payload')
        result['artifacts'].append({'kind': kind, 'path': str(target), 'bytes': target.stat().st_size, 'sha256': w.digest(target)})
        print('Built:', target, flush=True)
    guide = w.WORK / 'client' / f'Chronicle-Last-Council-{version}-e4mc-README.txt'
    guide.write_bytes(readme)
    safe.atomic(w.REPORTS / 'council-e4mc-pack.json', result)
    return result


if __name__ == '__main__':
    build()
