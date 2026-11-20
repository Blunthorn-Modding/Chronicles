"""Disposable four-client LC05B native workflow and exact recovery checks.

Requires the opt-in ConflictNativeProbe extensions. Does not build artifacts. The
world and diagnostics remain under test-runs/reports for inspection.
"""
from __future__ import annotations
import ctypes
from ctypes import wintypes
from contextlib import contextmanager
import json
from pathlib import Path
import subprocess
import time
import uuid

import fabric_smoke
import menu_smoke
from council_conflicts_smoke import Driver, fixture, offline

NONE = '00000000-0000-0000-0000-000000000000'


@contextmanager
def deny_replace(path):
    path = Path(path).resolve()
    root = path.parents[3]
    if (root.parent.name != 'test-runs' or
            not (root / '.council-conflicts-fixture').is_file() or
            not (root / '.council-foreign-fixture').is_file() or
            path != root / 'world/chronicle/last-council/foreign-affairs.json'):
        raise RuntimeError('Unsafe foreign journal lock target')
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel.CreateFileW.restype = wintypes.HANDLE
    kernel.CreateFileW.argtypes = [wintypes.LPCWSTR, wintypes.DWORD, wintypes.DWORD,
                                  ctypes.c_void_p, wintypes.DWORD, wintypes.DWORD, wintypes.HANDLE]
    kernel.CloseHandle.argtypes = [wintypes.HANDLE]
    handle = kernel.CreateFileW(str(path), 0x80000000, 1, None, 3, 128, None)
    if handle == ctypes.c_void_p(-1).value:
        raise ctypes.WinError(ctypes.get_last_error())
    try:
        yield
    finally:
        kernel.CloseHandle(handle)


def packet(state, key):
    return state.get(key) or {}


def changed(d, role, key, before, why, predicate=lambda v: True, timeout=45):
    response_key = key + 'Reply' if key in ('proceedings', 'vote', 'notice') else key
    return packet(d.wait(role, lambda s: packet(s, response_key).get('request') not in (None, NONE, before)
                         and predicate(packet(s, response_key)), why, timeout), response_key)


def foreign(d, role, verb, **kwargs):
    before = packet(d.state(role), 'foreign').get('request')
    d.do(role, 'foreign', verb=verb, **kwargs)
    return changed(d, role, 'foreign', before, 'foreign ' + verb)


def proceedings(d, role, verb, **kwargs):
    before = packet(d.state(role), 'proceedingsReply').get('request')
    d.do(role, 'proceedings', verb=verb, **kwargs)
    return changed(d, role, 'proceedings', before, 'proceedings ' + verb)


def expect_notice(d, role, verb, **kwargs):
    before = packet(d.state(role), 'noticeReply').get('request')
    d.do(role, 'proceedings', verb=verb, **kwargs)
    return changed(d, role, 'notice', before, 'refused ' + verb)


def button(d, role, label):
    d.wait(role, lambda s: any(b['label'] == label and b['active'] for b in s.get('buttons', [])),
           'button ' + label)
    d.do(role, 'click', label=label)


def setup(d, initial):
    for role in 'ABCD':
        d.wait(role, lambda s: True, 'connection', timeout=100)
    for command in ('fill -6 64 -6 6 64 6 minecraft:stone',
                    'setblock 0 65 2 minecraft:lectern', 'setblock 0 65 4 minecraft:lectern',
                    'tp ChronicleFabricA 0 65 -1', 'tp ChronicleFabricB 0 65 0',
                    'tp ChronicleFabricC 1 65 0', 'tp ChronicleFabricD 2 65 0'):
        d.chat('A', command)
    if initial:
        d.chat('A', 'chronicle editing on')


def record(d, role, realm):
    view = foreign(d, role, 'STATE', realm=realm)
    assert view['detail']['id'] == realm, view
    return view['detail']


def prepare(d, title, category, realm, revision):
    result = proceedings(d, 'A', 'PREPARE', title=title, text='A public Council decision.',
                         category=category, realm=realm, realmRevision=revision, thirds=False)
    assert result['detail']['title'] == title, result
    return result['detail']['id']


def open_ballot(d, motion):
    view = proceedings(d, 'A', 'OPEN_BALLOT', reference=motion)
    ballot = view['activeBallot']
    assert ballot and ballot != NONE, view
    return ballot


def cast(d, ballot, roles):
    for role in roles:
        d.chat(role, 'chronicle council vote')
        d.wait(role, lambda s: packet(s, 'vote').get('ballot', {}).get('id') == ballot,
               'open vote for ' + role)
        before = packet(d.state(role), 'voteReply').get('request')
        d.do(role, 'proceedings', verb='CAST', reference=ballot, site='vote', text='yes')
        vote = changed(d, role, 'vote', before, 'cast yes')
        assert vote['ballot']['ownChoice'] == 'yes', vote
        d.do(role, 'close')


def close_ballot(d, ballot):
    return proceedings(d, 'A', 'CLOSE_BALLOT', reference=ballot)


def open_public(d, role):
    d.chat(role, 'chronicle council foreign-affairs')
    return packet(d.wait(role, lambda s: s.get('screen') == 'ForeignScreen' and
                         bool(packet(s, 'foreign').get('token')), 'public foreign registry'), 'foreign')


def initial(d, root, checks):
    d.chat('A', 'chronicle council foreign-affairs settings')
    d.wait('A', lambda s: s.get('screen') == 'ForeignScreen' and packet(s, 'foreign').get('editable'),
           'editable foreign catalogue')
    button(d, 'A', 'Create realm...')
    d.do('A', 'text', value='Northreach')
    d.do('A', 'text', value='A neighbouring realm.', edit=True)
    button(d, 'A', 'Save')
    view = packet(d.wait('A', lambda s: s.get('screen') == 'ForeignScreen' and
                         len(packet(s, 'foreign').get('rows', [])) == 1, 'native create'), 'foreign')
    realm = view['detail']['id']
    assert realm and view['detail']['name'] == 'Northreach'
    revision = view['catalogRevision']
    button(d, 'A', 'Edit...'); button(d, 'A', 'Save')
    view = packet(d.wait('A', lambda s: s.get('screen') == 'ForeignScreen' and
                         packet(s, 'foreign').get('saved'), 'unchanged native save'), 'foreign')
    assert view['catalogRevision'] == revision
    checks.append('Native creation and unchanged update acknowledge success without exposing an ID field')
    d.do('A', 'close')
    public = open_public(d, 'B')
    assert not d.state('B')['outside']; d.do('B', 'capture', name='foreign-public-compact.png')
    assert not public['editing'] and not public['detail']['atWar']
    rejected = foreign(d, 'B', 'UPDATE', realm=realm, name='Forged', description='Denied')
    assert not rejected['saved'] and rejected['detail']['name'] == 'Northreach' and rejected['message']
    checks.append('Public foreign session rejects a forged catalogue update')
    d.do('B', 'close')
    d.chat('A', 'chronicle editing off')
    for role in 'BCD': d.chat(role, 'chronicle players join')
    time.sleep(1); d.chat('A', 'chronicle session start')
    d.chat('A', 'chronicle council proceedings')
    d.wait('A', lambda s: s.get('screen') == 'CouncilBusinessScreen' and bool(packet(s, 'proceedings')),
           'Chancellor proceedings')
    proceedings(d, 'A', 'START')
    stale = prepare(d, 'Old war proposal', 'DECLARE_WAR', realm, 0)
    # Exercise the real category/target UI before submitting this prepared motion.
    d.do('A', 'resize', width=1280, height=720); time.sleep(.5)
    button(d, 'A', 'Prepare motion...')
    d.do('A', 'text', value='Council declares war')
    d.do('A', 'text', value='A public Council decision.', edit=True)
    button(d, 'A', 'Category: General'); button(d, 'A', 'Declare foreign war')
    button(d, 'A', 'Choose foreign realm...'); button(d, 'A', 'Northreach')
    for width, height, name in ((1280, 720, 'foreign-motion-normal.png'), (640, 480, 'foreign-motion-compact.png')):
        d.do('A', 'resize', width=width, height=height); time.sleep(.5)
        assert not d.state('A')['outside']; d.do('A', 'capture', name=name)
    button(d, 'A', 'Submit')
    view = packet(d.wait('A', lambda s: (packet(s, 'proceedings').get('detail') or {}).get('title') == 'Council declares war',
                         'native foreign motion'), 'proceedings')
    motion = view['detail']['id']; assert view['detail']['threshold'] == 'TWO_THIRDS'
    ballot = open_ballot(d, motion); cast(d, ballot, 'BC')
    journal = root / 'world/chronicle/last-council/foreign-affairs.json'
    with deny_replace(journal):
        d.do('A', 'proceedings', verb='CLOSE_BALLOT', reference=ballot)
        d.wait('A', lambda s: 'recover' in json.dumps(s.get('notice', {})).lower() or
               'recover' in json.dumps(s.get('proceedings', {})).lower(), 'real I/O hold')
        open_public(d, 'D')
        held = packet(d.wait('D', lambda s: not packet(s, 'foreign').get('rows') and
                             'recover' in packet(s, 'foreign').get('message', '').lower(),
                             'held foreign projection'), 'foreign')
        assert held.get('detail') is None
    d.chat('A', 'chronicle council recover')
    d.wait('A', lambda s: any('pinned operations recovered' in m for m in s.get('messages', [])),
           'operator exact recovery')
    open_public(d, 'D'); war = record(d, 'D', realm)
    assert war['atWar'] and war['revision'] == 1 and len(war['history']) == 1
    checks.append('Two of three war votes force two-thirds; real locked journal holds and redacts until exact recovery')
    # Recover may close foreign sessions; reopen proceedings to obtain its latest revision.
    d.chat('A', 'chronicle council proceedings')
    d.wait('A', lambda s: s.get('screen') == 'CouncilBusinessScreen', 'reopen proceedings')
    fail = prepare(d, 'Failed peace', 'MAKE_PEACE', realm, 1)
    b = open_ballot(d, fail); cast(d, b, 'B'); close_ballot(d, b)
    assert record(d, 'D', realm)['revision'] == 1
    void = prepare(d, 'Voided peace', 'MAKE_PEACE', realm, 1)
    b = open_ballot(d, void)
    proceedings(d, 'A', 'VOID_BALLOT', reference=b, text='Withdrawn for review')
    assert record(d, 'D', realm)['revision'] == 1
    peace = prepare(d, 'Council makes peace', 'MAKE_PEACE', realm, 1)
    last = open_ballot(d, peace); cast(d, last, 'BC'); close_ballot(d, last)
    detail = record(d, 'D', realm)
    assert not detail['atWar'] and detail['revision'] == 2 and len(detail['history']) == 2
    notice = expect_notice(d, 'A', 'OPEN_BALLOT', reference=stale)
    assert notice.get('message') and ('stale' in notice['message'].lower() or 'changed' in notice['message'].lower())
    assert packet(d.state('A'), 'proceedings').get('activeBallot') in (None, NONE)
    checks.append('Failed and void peace have no effect; passed peace and stale ABA opening preserve exactly two decisions')
    d.chat('A', 'chronicle session pause'); d.chat('A', 'chronicle editing on')
    d.chat('A', 'chronicle council foreign-affairs settings')
    d.wait('A', lambda s: s.get('screen') == 'ForeignScreen' and packet(s, 'foreign').get('editable'),
           'paused catalogue')
    refusal = foreign(d, 'A', 'DELETE', realm=realm)
    assert refusal['message'] and not refusal['saved'] and any(r['id'] == realm for r in refusal['rows'])
    checks.append('Referenced realm deletion is refused in Editing')
    return realm, peace, last


def reconstruct_pending(root, motion, ballot):
    # Recreate the exact durable CLOSE boundary after a completed, already-applied peace.
    path = root / 'world/chronicle/last-council/proceedings.json'
    doc = json.loads(path.read_text(encoding='utf-8')); state = doc['state']
    assert doc['schemaVersion'] == 6 and state['pending'] is None and state['activeBallot'] is None
    assert state['motions'][motion]['status'] == 'RECORDED'
    assert state['attempts'][ballot]['motion'] == motion and state['phase'] == 'DISCUSSION'
    state['motions'][motion]['status'] = 'VOTING'; state['activeBallot'] = ballot
    state['pending'] = {'operation': 'CLOSE', 'ballot': ballot, 'actor': offline('A'), 'reason': '', 'result': None}
    path.write_text(json.dumps(doc) + '\n', encoding='utf-8')


def restart(d, root, realm, checks):
    open_public(d, 'D'); detail = record(d, 'D', realm)
    assert not detail['atWar'] and detail['revision'] == 2 and len(detail['history']) == 2
    effects = json.loads((root / 'world/chronicle/last-council/foreign-affairs.json').read_text(encoding='utf-8'))['effects']
    assert [e['kind'] for e in effects] == ['DECLARE_WAR', 'MAKE_PEACE']
    d.chat('A', 'chronicle council proceedings')
    v = packet(d.wait('A', lambda s: s.get('screen') == 'CouncilBusinessScreen' and
                      bool(packet(s, 'proceedings').get('motions')), 'retained motions'), 'proceedings')
    assert any(m['title'] == 'Council makes peace' for m in v['motions'])
    checks.append('Restart replays already-applied CLOSE idempotently and retains exactly two public effects and motions')


def run():
    import workspace as w
    w.require_eula(); w.validate_server_runtime(); w.validate_managed_mod_names()
    launch = json.loads((w.REPO / 'campaigns/last-council/build/smoke/client-launch.json').read_text(encoding='utf-8'))
    root = fixture(w); (root / '.council-foreign-fixture').write_text('Disposable foreign native fixture\n', encoding='utf-8')
    reports = w.REPORTS / ('council-foreign-' + root.name); reports.mkdir()
    report = {'result': 'FAIL', 'world': str(root), 'checks': []}
    server = log = None; clients = []
    try:
        journal = root / 'world/chronicle/last-council/foreign-affairs.json'
        saved_journal = None
        for phase in ('initial', 'restart', 'missing-journal'):
            directory = reports / phase; directory.mkdir(); menu_smoke._reserve_ports()
            log = (directory / 'server.log').open('w', encoding='utf-8')
            server = subprocess.Popen(w.server_command(), cwd=root, env=w.env(), stdin=subprocess.PIPE,
                                      stdout=log, stderr=subprocess.STDOUT, text=True, encoding='utf-8')
            menu_smoke._wait_ready(server, directory / 'server.log')
            for role in 'ABCD':
                clients.append(fabric_smoke._client(w, launch, root, directory, 'ChronicleFabric' + role,
                    phase + '-foreign-' + role, {'CHRONICLE_CONFLICT_PROBE': str(directory),
                    'CHRONICLE_CONFLICT_ROOT': str(root), 'CHRONICLE_CONFLICT_ROLE': role,
                    'CHRONICLE_MENU_WIDTH': '640', 'CHRONICLE_MENU_HEIGHT': '480'}))
            d = Driver(directory, clients); setup(d, phase == 'initial')
            if phase == 'initial': realm, motion, ballot = initial(d, root, report['checks'])
            elif phase == 'restart': restart(d, root, realm, report['checks'])
            else:
                d.chat('A', 'chronicle council recover')
                d.wait('A', lambda s: any('recovery remains held' in m.lower() for m in s.get('messages', [])), 'missing journal remains held')
                assert saved_journal is not None and not journal.exists()
                journal.write_bytes(saved_journal)
                d.chat('A', 'chronicle council recover')
                d.wait('A', lambda s: any('pinned operations recovered' in m for m in s.get('messages', [])), 'restored journal recovery')
                open_public(d, 'D'); detail = record(d, 'D', realm)
                assert detail['revision'] == 2 and len(detail['history']) == 2 and not detail['atWar']
                report['checks'].append('Missing initialized journal holds startup; restoring exact bytes and recovering restores both effects')
            for role in 'ABCD': d.do(role, 'stop')
            for process, handle in clients: process.wait(timeout=30); handle.close()
            clients.clear(); fabric_smoke._stop(server); server = None; log.close(); log = None
            if phase == 'initial': reconstruct_pending(root, motion, ballot)
            if phase == 'restart':
                saved_journal = journal.read_bytes()
                journal.unlink()
        report['result'] = 'PASS'
        print('PASS Council foreign affairs native: ' + str(reports), flush=True)
    finally:
        for process, handle in clients:
            if process.poll() is None:
                process.terminate()
                try: process.wait(timeout=15)
                except subprocess.TimeoutExpired: process.kill(); process.wait(timeout=15)
            handle.close()
        try: fabric_smoke._stop(server)
        finally:
            if saved_journal is not None and not journal.exists(): journal.write_bytes(saved_journal)
            if log: log.close()
            (reports / 'report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    run()
