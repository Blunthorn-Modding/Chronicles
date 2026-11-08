"""Opt-in disposable four-client Council succession smoke; retains evidence outside world."""
import json
import shutil
import subprocess
import uuid
import fabric_smoke
import menu_smoke


from council_ballots_smoke import _fixture


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
    (root / '.council-succession-fixture').write_text('Disposable Council succession smoke\n', encoding='utf-8')
    _fixture(root)
    absent=str(uuid.UUID(bytes=__import__('hashlib').md5(b'OfflinePlayer:CouncilAbsentCandidate').digest(),version=3))
    definition=json.loads((w.REPO/'campaigns/last-council/src/main/resources/data/last_council/chronicle/houses.json').read_text(encoding='utf-8'))
    first=sorted(definition['houses'].values(),key=lambda h:(h['title'],h['id']))[0]['id']
    owner={'owner':absent,'houseId':first,'councilMember':True,'candidate':True}
    (root/'world/chronicle/last-council/roster.json').write_text(json.dumps({'schema':1,'revision':1,'assignments':{absent:owner}})+'\n',encoding='utf-8')
    for prefix in ('quiet_below-', 'last_council-'):
        for jar in (root / 'mods').glob(prefix + '*.jar'):
            jar.unlink()
    shutil.copy2(jars[0], root / 'mods' / jars[0].name)
    reports = w.REPORTS / ('council-succession-' + root.name)
    reports.mkdir(parents=True)
    report = {'result': 'FAIL', 'world': str(root), 'checks': []}
    server = log = None
    clients = []
    try:
        for phase in ('initial', 'restart'):
            menu_smoke._reserve_ports()
            log = (reports / (phase + '-server.log')).open('w', encoding='utf-8')
            server = subprocess.Popen(w.server_command(), cwd=root, env=w.env(), stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True, encoding='utf-8')
            menu_smoke._wait_ready(server, reports / (phase + '-server.log'))
            results = [reports / (phase + '-' + role + '.json') for role in ('A', 'B', 'C', 'D')]
            for role, result in zip(('A', 'B', 'C', 'D'), results):
                clients.append(fabric_smoke._client(w, launch, root, reports, 'ChronicleFabric' + role, phase + '-' + role, {
                    'CHRONICLE_SUCCESSION_SMOKE': str(result), 'CHRONICLE_SUCCESSION_ROOT': str(root),
                    'CHRONICLE_SUCCESSION_ROLE': role, 'CHRONICLE_SUCCESSION_PHASE': phase,
                    'CHRONICLE_MENU_WIDTH': '640', 'CHRONICLE_MENU_HEIGHT': '480'}))
            fabric_smoke._await_clients(clients, results, timeout=240)
            for result in results:
                report['checks'].extend(json.loads(result.read_text(encoding='utf-8'))['checks'])
            for _, handle in clients:
                handle.close()
            clients.clear()
            fabric_smoke._stop(server); server = None
            log.close(); log = None
        report['result'] = 'PASS'
        print('PASS Council succession native smoke:', reports, flush=True)
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
