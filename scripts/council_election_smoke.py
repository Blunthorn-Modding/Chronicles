"""Disposable native election-management smoke; all worlds are freshly staged fixtures."""
from __future__ import annotations

import json
import hashlib
from pathlib import Path
import subprocess
import time

import fabric_smoke
import menu_smoke
from council_conflicts_smoke import Driver, fixture
from council_foreign_smoke import proceedings as send_proceedings, button
from council_foreign_smoke import cast, open_ballot, close_ballot


NONE = "00000000-0000-0000-0000-000000000000"


def packet(state, key):
    value = state.get(key)
    return value if isinstance(value, dict) else {}


def proceedings(d, verb, **kwargs):
    return send_proceedings(d, 'A', verb, **kwargs)


def election(d, role, verb, **kwargs):
    """Use the probe's normal client send unless an explicit raw field is supplied."""
    return d.do(role, 'election', verb=verb, **kwargs)


def proceedings_hash(root):
    return hashlib.sha256((root / 'world/chronicle/last-council/proceedings.json').read_bytes()).hexdigest()


def open_personal_election(d, role, stage='Contested'):
    d.do(role, 'resize', width=1280, height=720)
    d.do(role,'close')
    d.chat(role, 'chronicle council')
    d.wait(role, lambda s: s.get('screen') == 'PersonalScreen', 'Council personal menu')
    button(d, role, 'My house')
    d.wait(role, lambda s: s.get('screen') == 'PersonalScreen', 'My house tab')
    button(d, role, 'Election')
    return d.wait(role, lambda s: packet(s, 'election').get('stage') == stage, 'personal Election panel')


def open_proceedings_election(d, stage='Contested'):
    d.do('A', 'resize', width=1280, height=720)
    d.do('A','close')
    d.chat('A', 'chronicle council proceedings')
    d.wait('A', lambda s: s.get('screen') == 'CouncilBusinessScreen' and bool(packet(s, 'proceedings')), 'Chancellor proceedings')
    button(d, 'A', 'Election')
    return d.wait('A', lambda s: packet(s, 'election').get('stage') == stage, 'proceedings Election panel')


def setup(d, initial):
    for role in 'ABCD':
        d.wait(role, lambda s: bool(s), 'connection', timeout=100)
    for command in ('fill -6 64 -6 6 64 6 minecraft:stone',
                    'tp ChronicleFabricA 0 65 -1', 'tp ChronicleFabricB 0 65 0',
                    'tp ChronicleFabricC 1 65 0', 'tp ChronicleFabricD 2 65 0'):
        d.chat('A', command)
    if not initial:
        return
    d.chat('A', 'chronicle editing on')
    for activity in ('withdrawn', 'unresolved'):
        d.chat('A', 'chronicle_event_sources_trial arm "last_council:succession_result" "election" '+json.dumps(activity))
        d.wait('A', lambda s, a=activity: any('Event sources trial PASS arm last_council:succession_result election '+a in m for m in s.get('messages', [])), 'election source arm '+activity)
    d.chat('A', 'chronicle editing off')
    for role in 'BCD':
        d.chat(role, 'chronicle players join')
    time.sleep(1)
    d.chat('A', 'chronicle session start')
    d.chat('A', 'chronicle council proceedings')
    d.wait('A', lambda s: s.get('screen') == 'CouncilBusinessScreen' and bool(packet(s, 'proceedings')), 'initial proceedings')
    proceedings(d, 'START')
    d.wait('A', lambda s: packet(s, 'proceedings').get('meeting') == 1, 'first meeting')


def void_history(d, root, checks):
    proceedings(d, 'OPEN_REGENCY')
    first = d.wait('A', lambda s: packet(s, 'proceedings').get('activeBallot') not in (None, NONE), 'first Regency ballot')
    ballot = packet(first, 'proceedings')['activeBallot']
    open_personal_election(d, 'B')
    b = packet(d.state('B'), 'election')
    assert b['ownCandidate'] and not b['canWithdraw'] and 'Close or void' in b['withdrawRefusal']
    open_proceedings_election(d)
    a = packet(d.state('A'), 'election')
    assert a['chancellor'] and not a['canEnd'] and 'Close or void' in a['endRefusal']
    # Deliberately raw: the client ignores unsolicited replies, so unchanged durable bytes prove refusal.
    before = proceedings_hash(root)
    election(d, 'B', 'WITHDRAW', reason='Too early.', token=b['token'], revision=b['revision'])
    time.sleep(.5)
    assert proceedings_hash(root) == before
    proceedings(d, 'VOID_BALLOT', reference=ballot, text='Fixture history entry 1')
    d.wait('A', lambda s: packet(s, 'proceedings').get('activeBallot') in (None, NONE), 'first void')
    open_personal_election(d, 'B')
    b = packet(d.state('B'), 'election')
    before = proceedings_hash(root)
    election(d, 'B', 'WITHDRAW', reason='Same meeting.', token=b['token'], revision=b['revision'])
    time.sleep(.5)
    assert proceedings_hash(root) == before
    open_proceedings_election(d)
    assert packet(d.state('A'), 'election')['canEnd']
    # Seventeen retained voids exercise the sixteen-line public-history page boundary.
    for number in range(2, 18):
        d.chat('A', 'chronicle council proceedings')
        d.wait('A', lambda s: s.get('screen') == 'CouncilBusinessScreen', 'reopen proceedings for void history')
        proceedings(d, 'OPEN_REGENCY')
        view = d.wait('A', lambda s: packet(s, 'proceedings').get('activeBallot') not in (None, NONE), 'replacement ballot')
        proceedings(d, 'VOID_BALLOT', reference=packet(view, 'proceedings')['activeBallot'], text='Fixture history entry '+str(number))
        d.wait('A', lambda s: packet(s, 'proceedings').get('activeBallot') in (None, NONE), 'replacement void')
    open_proceedings_election(d)
    a = packet(d.state('A'), 'election')
    assert a['pages'] > 1 and len(a['history']) == 16
    d.do('A', 'click', label='Next')
    d.wait('A', lambda s: packet(s, 'election').get('page') == 1 and len(packet(s, 'election').get('history', [])) >= 1, 'second public history page')
    checks.append('Raw authenticated open-ballot and same-meeting-void withdrawal refusals leave proceedings unchanged while the Chancellor may end; retained public history pages after sixteen entries')


def initial(d, root, checks):
    void_history(d, root, checks)
    d.chat('A', 'chronicle council proceedings')
    d.wait('A', lambda s: s.get('screen') == 'CouncilBusinessScreen', 'close first meeting')
    proceedings(d, 'FINISH')
    d.wait('A', lambda s: packet(s, 'proceedings').get('phase') == 'BETWEEN', 'first meeting closed')
    proceedings(d, 'START')
    d.wait('A', lambda s: packet(s, 'proceedings').get('meeting') == 2, 'second meeting')
    open_personal_election(d, 'B')
    d.do('B', 'resize', width=1280, height=720)
    time.sleep(.5)
    assert not d.state('B')['outside']
    d.do('B', 'capture', name='election-normal.png')
    d.do('B', 'resize', width=640, height=480)
    time.sleep(.5)
    assert not d.state('B')['outside']
    d.do('B', 'capture', name='election-compact.png')
    # The actual reason dialog keeps a cancellation and required-reason path, then sends normally.
    d.do('B', 'click', label='Withdraw candidacy...')
    d.wait('B', lambda s: s.get('screen') == 'ElectionReasonScreen', 'withdrawal confirmation')
    d.do('B', 'click', label='Cancel')
    d.wait('B', lambda s: s.get('screen') == 'PersonalScreen', 'withdrawal cancellation')
    d.do('B', 'click', label='Withdraw candidacy...')
    d.do('B', 'text', value='B withdraws voluntarily after deliberation.', edit=True)
    reason='B withdraws voluntarily after deliberation.'
    for width,height,name in ((1280,720,'withdraw-reason-normal.png'),(640,480,'withdraw-reason-compact.png')):
        d.do('B','resize',width=width,height=height); time.sleep(.5)
        assert not d.state('B')['outside']
        assert d.state('B')['editText']==reason
        d.do('B','capture',name=name)
    d.chat('A','chronicle session pause')
    d.wait('B',lambda state: not packet(state,'election').get('canWithdraw'),'paused election guard')
    d.do('B','click',label='Confirm')
    d.wait('B',lambda state: state.get('screen')=='ElectionReasonScreen' and 'Resume active play' in state.get('feedback',''),'refused reason retained')
    assert d.state('B')['editText']==reason
    d.chat('A','chronicle session resume')
    d.wait('B',lambda state: packet(state,'election').get('canWithdraw'),'resumed election permission')
    d.do('B', 'click', label='Confirm')
    d.wait('B', lambda s: packet(s, 'election').get('stage') == 'Contested' and not packet(s, 'election').get('ownCandidate'), 'saved B withdrawal')
    assert not packet(d.state('B'), 'election')['ownCandidate']
    b=packet(d.state('B'),'election'); before=proceedings_hash(root)
    election(d,'B','END',reason='A House cannot end the election.',token=b['token'],revision=b['revision'])
    time.sleep(.5); assert proceedings_hash(root)==before
    roster = json.loads((root / 'world/chronicle/last-council/roster.json').read_text(encoding='utf-8'))
    assert any(v['councilMember'] for v in roster['assignments'].values() if v['owner'] == d.state('B')['playerId'])
    participants=json.loads((root/'world/chronicle/play-runtime.json').read_text(encoding='utf-8'))['participants']
    assert d.state('B')['playerId'] in participants
    open_proceedings_election(d)
    stale = packet(d.state('A'), 'election')
    open_personal_election(d, 'C')
    c = packet(d.state('C'), 'election')
    election(d, 'C', 'WITHDRAW', reason='C withdraws so one candidate remains.')
    d.wait('C', lambda s: not packet(s, 'election').get('ownCandidate'), 'saved C withdrawal')
    before = proceedings_hash(root)
    election(d, 'A', 'END', reason='Stale reason must not be retained.', token=stale['token'], revision=stale['revision'])
    time.sleep(.5)
    assert proceedings_hash(root) == before
    open_proceedings_election(d)
    a = packet(d.state('A'), 'election')
    assert a['canEnd'] and len(a['candidates']) == 1 and a['warning']
    for width,height,name in ((1280,720,'election-chancellor-normal.png'),(640,480,'election-chancellor-compact.png')):
        d.do('A','resize',width=width,height=height); time.sleep(.5)
        assert not d.state('A')['outside']; d.do('A','capture',name=name)
    d.do('A', 'click', label='End unresolved election...')
    d.wait('A', lambda s: s.get('screen') == 'ElectionReasonScreen', 'end confirmation')
    d.do('A', 'click', label='Confirm')
    d.wait('A', lambda s: 'Enter a public reason.' in s.get('feedback', ''), 'required end reason')
    d.do('A', 'text', value='The remaining candidacy is unresolved.', edit=True)
    d.do('A', 'click', label='Confirm')
    d.wait('A', lambda s: packet(s, 'election').get('stage') == 'Ended unresolved', 'saved unresolved end')
    for activity in ('withdrawn', 'unresolved'):
        d.chat('A', 'chronicle_event_sources_trial verify "last_council:succession_result" "election" '+json.dumps(activity))
        d.wait('A', lambda s, a=activity: any('Event sources trial PASS verify last_council:succession_result election '+a in m for m in s.get('messages', [])), 'election Event/flag '+activity)
    d.do('A','close');d.chat('A','chronicle council proceedings')
    d.wait('A',lambda state: state.get('screen')=='CouncilBusinessScreen','ordinary Council after unresolved end')
    motion=proceedings(d,'PREPARE',title='Business after the election',text='Ordinary Council business continues.')['detail']['id']
    ballot=open_ballot(d,motion);cast(d,ballot,'BCD');close_ballot(d,ballot)
    checks.append('Ordinary Council motion passes after unresolved ending; both withdrawn candidates retain their votes')
    d.chat('A', 'chronicle_council_sources_trial snapshot')
    d.wait('A', lambda s: any('Council sources trial PASS snapshot' in m for m in s.get('messages', [])), 'election source snapshot')
    checks.append('Real My house Election uses cancellation, required reason and retained input on a paused-play refusal; forged House END leaves durable bytes unchanged; B remains a Council participant, stale raw end leaves its reason unretained, two voluntary withdrawals leave one candidate, and the Chancellor ends unresolved with public Event/flag evidence')


def restart(d, checks):
    d.chat('A', 'chronicle_council_sources_trial unchanged')
    d.wait('A', lambda s: any('Council sources trial PASS unchanged' in m for m in s.get('messages', [])), 'election source restart stability')
    open_personal_election(d, 'B', 'Ended unresolved')
    b = packet(d.state('B'), 'election')
    assert b['stage'] == 'Ended unresolved' and not b['ownCandidate'] and b['pages'] > 1
    open_proceedings_election(d, 'Ended unresolved')
    a = packet(d.state('A'), 'election')
    assert a['stage'] == 'Ended unresolved' and a['chancellor'] and not a['canEnd']
    checks.append('Restart retains withdrawals, unresolved ending, role separation, and paged public history without replaying election sources')


def run():
    import workspace as w
    w.require_eula(); w.validate_server_runtime(); w.validate_managed_mod_names()
    launch = json.loads((w.REPO / 'campaigns/last-council/build/smoke/client-launch.json').read_text(encoding='utf-8'))
    root = fixture(w)
    (root / '.political-sources-fixture').write_text('Disposable political source fixture\n', encoding='utf-8')
    (root / '.council-election-fixture').write_text('Disposable election native fixture\n', encoding='utf-8')
    reports = w.REPORTS / ('council-election-' + root.name)
    reports.mkdir()
    report = {'result': 'FAIL', 'world': str(root), 'checks': []}
    server = log = None
    clients = []
    try:
        for phase in ('initial', 'restart'):
            print('Council election native phase '+phase, flush=True)
            directory = reports / phase
            directory.mkdir()
            menu_smoke._reserve_ports()
            log = (directory / 'server.log').open('w', encoding='utf-8')
            server = subprocess.Popen(w.server_command(), cwd=root,
                env=dict(w.env(), CHRONICLE_EVENT_SOURCES_TRIAL='true', CHRONICLE_POLITICAL_SOURCES_TRIAL='true'),
                stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True, encoding='utf-8')
            menu_smoke._wait_ready(server, directory / 'server.log')
            for role in 'ABCD':
                clients.append(fabric_smoke._client(w, launch, root, directory, 'ChronicleFabric'+role, phase+'-election-'+role,
                    {'CHRONICLE_CONFLICT_PROBE': str(directory), 'CHRONICLE_CONFLICT_ROOT': str(root),
                     'CHRONICLE_CONFLICT_ROLE': role, 'CHRONICLE_MENU_WIDTH': '640', 'CHRONICLE_MENU_HEIGHT': '480'}))
            d = Driver(directory, clients)
            setup(d, phase == 'initial')
            if phase == 'initial':
                initial(d, root, report['checks'])
            else:
                restart(d, report['checks'])
            for role in 'ABCD': d.do(role, 'stop')
            for process, handle in clients:
                process.wait(timeout=30); handle.close()
            clients.clear(); fabric_smoke._stop(server); server = None; log.close(); log = None
        report['result'] = 'PASS'
        print('PASS Council election native smoke: '+str(reports), flush=True)
    finally:
        for process, handle in clients:
            if process.poll() is None:
                process.terminate()
                try: process.wait(timeout=15)
                except subprocess.TimeoutExpired: process.kill(); process.wait(timeout=15)
            handle.close()
        try:
            fabric_smoke._stop(server)
        finally:
            if log: log.close()
            (reports / 'report.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')


if __name__ == '__main__':
    run()
