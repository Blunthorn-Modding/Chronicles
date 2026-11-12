"""Opt-in disposable two-client banquet settings smoke; retains evidence outside world."""
import json
import shutil
import subprocess
import hashlib
import uuid
import time
import fabric_smoke
import menu_smoke


def _fixture(root):
    chronicle = root / 'world/chronicle'
    council = chronicle / 'last-council'
    council.mkdir(parents=True)
    position = lambda x, y, z: {'x': x, 'y': y, 'z': z}
    common = {'dimension': 'minecraft:overworld', 'revision': 1}
    sites = [dict(common, type='region', id='council', min=position(-5, 64, -5), max=position(5, 80, 5)),
             dict(common, type='anchor', id='motions', position=position(0, 65, 4), expectedBlock='minecraft:lectern')]
    (chronicle / 'authoring.json').write_text(json.dumps({'schemaVersion': 1, 'revision': 1, 'sites': sites}) + '\n', encoding='utf-8')
    state = {'revision': 0, 'meeting': 0, 'phase': 'BETWEEN', 'votingRegion': 'council',
             'lecterns': ['motions'], 'motions': {}, 'attempts': {}, 'activeBallot': None,
             'pending': None, 'calls': {}}
    (council / 'proceedings.json').write_text(json.dumps({'schemaVersion': 1, 'state': state}) + '\n', encoding='utf-8')
    assignments = {}
    for name, house in [('ChronicleFabricA', 'ash'), ('BanquetOfflineHouse', 'vale')]:
        owner = str(uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:' + name).encode()).digest(), version=3))
        assignments[owner] = {'owner': owner, 'houseId': 'last_council:' + house, 'councilMember': True, 'candidate': True}
    (council / 'roster.json').write_text(json.dumps({'schema': 1, 'revision': 1, 'assignments': assignments}) + '\n', encoding='utf-8')


def run():
    import workspace as w
    w.require_eula(); w.validate_server_runtime(); w.validate_managed_mod_names()
    launch = json.loads((w.REPO / 'campaigns/last-council/build/smoke/client-launch.json').read_text(encoding='utf-8'))
    props = dict(line.split('=', 1) for line in (w.REPO / 'gradle.properties').read_text(encoding='utf-8').splitlines() if '=' in line and not line.lstrip().startswith('#'))
    jars = list((w.REPO / 'campaigns/last-council/build/libs').glob('last_council-' + props['mod_version'].strip() + '.jar'))
    if len(jars) != 1:
        raise RuntimeError('Expected the remapped Last Council jar for the configured version')
    w.validate_mod_target(jars[0])
    root = fabric_smoke._stage(w)
    (root / '.council-banquet-fixture').write_text('Disposable banquet smoke\n', encoding='utf-8')
    _fixture(root)
    chancellor = str(uuid.UUID(bytes=hashlib.md5(b'OfflinePlayer:ChronicleFabricB').digest(), version=3))
    (root / 'world/chronicle/last-council/chancellor.json').write_text(json.dumps({'schema': 1, 'revision': 1, 'chancellor': chancellor}) + '\n', encoding='utf-8')
    for prefix in ('quiet_below-', 'last_council-'):
        for jar in (root / 'mods').glob(prefix + '*.jar'):
            jar.unlink()
    shutil.copy2(jars[0], root / 'mods' / jars[0].name)
    reports = w.REPORTS / ('council-banquet-' + root.name)
    reports.mkdir(parents=True)
    report = {'result': 'FAIL', 'world': str(root), 'checks': []}
    server = log = None
    clients = []
    try:
        for phase in ('initial', 'restart'):
            menu_smoke._reserve_ports()
            log = (reports / (phase + '-server.log')).open('w', encoding='utf-8')
            server_env=w.env();server_env.update({'CHRONICLE_BANQUET_TRIAL':str(root), 'CHRONICLE_BANQUET_PHASE':phase, 'CHRONICLE_BANQUET_TRIAL_REPORT':str(reports / (phase + '-trial.json')), 'CHRONICLE_BANQUET_DROP_GIFT_REPLY':'1' if phase=='initial' else '0'})
            server = subprocess.Popen(w.server_command(), cwd=root, env=server_env, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True, encoding='utf-8')
            menu_smoke._wait_ready(server, reports / (phase + '-server.log'))
            results = [reports / (phase + '-' + role + '.json') for role in ('A', 'B')]
            for role, result in zip(('A', 'B'), results):
                clients.append(fabric_smoke._client(w, launch, root, reports, 'ChronicleFabric' + role, phase + '-' + role, {
                    'CHRONICLE_BANQUET_SMOKE': str(result), 'CHRONICLE_BANQUET_ROOT': str(root),
                    'CHRONICLE_BANQUET_ROLE': role, 'CHRONICLE_BANQUET_PHASE': phase,
                    'CHRONICLE_MENU_WIDTH': '640', 'CHRONICLE_MENU_HEIGHT': '480'}))
            fabric_smoke._await_clients(clients, results, timeout=300)
            for result in results:
                report['checks'].extend(json.loads(result.read_text(encoding='utf-8'))['checks'])
            for _, handle in clients:
                handle.close()
            clients.clear()
            if phase == 'initial':
                server.stdin.write('chronicle session pause\n'); server.stdin.flush(); time.sleep(0.3)
            ledger = root / 'world/chronicle/last-council/banquet.json'
            before_offline = json.loads(ledger.read_text(encoding='utf-8'))['illnesses']
            time.sleep(2)
            assert json.loads(ledger.read_text(encoding='utf-8'))['illnesses'] == before_offline
            report['checks'].append('illness clocks remain unchanged with both clients offline')
            fabric_smoke._stop(server); server = None
            log.close(); log = None
        report['result'] = 'PASS'
        print('PASS Council banquet settings native smoke:', reports, flush=True)
    finally:
        for process, handle in clients:
            try:
                if process.poll() is None:
                    process.terminate()
                    try: process.wait(timeout=15)
                    except subprocess.TimeoutExpired:
                        process.kill(); process.wait(timeout=15)
            finally:
                handle.close()
        try:
            fabric_smoke._stop(server)
        finally:
            if log: log.close()
            (reports / 'report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    run()
