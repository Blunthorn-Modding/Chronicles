"""Opt-in, isolated native Last Council smoke. Retains world and reports for inspection."""
import json
import hashlib
import shutil
import subprocess
import fabric_smoke
import menu_smoke


def run():
    import workspace as w
    w.require_eula(); w.validate_server_runtime(); w.validate_managed_mod_names()
    launch = json.loads((w.REPO / 'campaigns/last-council/build/smoke/client-launch.json').read_text(encoding='utf-8'))
    jars = list((w.REPO / 'campaigns/last-council/build/libs').glob('last_council-*.jar'))
    jars = [p for p in jars if not any(s in p.name for s in ('-sources.jar', '-dev.jar', '-javadoc.jar'))]
    if len(jars) != 1: raise RuntimeError('Expected exactly one remapped Last Council jar')
    w.validate_mod_target(jars[0])
    root = fabric_smoke._stage(w)
    (root / '.last-council-fixture').write_text('Disposable Last Council native smoke\n', encoding='utf-8')
    for jar in (root / 'mods').glob('quiet_below-*.jar'): jar.unlink()
    for jar in (root / 'mods').glob('last_council-*.jar'): jar.unlink()
    shutil.copy2(jars[0], root / 'mods' / jars[0].name)
    reports = w.REPORTS / ('last-council-' + root.name); reports.mkdir(parents=True)
    report = {'result': 'FAIL', 'world': str(root), 'checks': []}
    server = log = None; clients = []; committed_hash = None
    try:
        for phase in ('initial', 'restart'):
            menu_smoke._reserve_ports()
            log = (reports / (phase + '-server.log')).open('w', encoding='utf-8')
            server = subprocess.Popen(w.server_command(), cwd=root, env=w.env(), stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True, encoding='utf-8')
            menu_smoke._wait_ready(server, reports / (phase + '-server.log'))
            results = [reports / (phase + '-' + role + '.json') for role in ('A', 'B')]
            clients = [fabric_smoke._client(w, launch, root, reports, 'ChronicleFabric' + role, phase + '-' + role, {
                'CHRONICLE_COUNCIL_SMOKE': str(result), 'CHRONICLE_COUNCIL_ROOT': str(root),
                'CHRONICLE_COUNCIL_ROLE': role, 'CHRONICLE_COUNCIL_PHASE': phase,
                'CHRONICLE_MENU_WIDTH': '1280' if role == 'A' else '640', 'CHRONICLE_MENU_HEIGHT': '720' if role == 'A' else '480'
            }) for role, result in zip(('A', 'B'), results)]
            fabric_smoke._await_clients(clients, results, timeout=180)
            for result in results: report['checks'].extend(json.loads(result.read_text(encoding='utf-8'))['checks'])
            for _, handle in clients: handle.close()
            clients = []
            fabric_smoke._stop(server); server = None; log.close(); log = None
            current_hash = hashlib.sha256((root / 'world/chronicle/last-council/cast.json').read_bytes()).hexdigest()
            if phase == 'initial': committed_hash = current_hash
            elif current_hash != committed_hash: raise RuntimeError('Committed private cast changed across restart')
            else: report['checks'].append('committed private cast bytes unchanged across a real restart')
        report['result'] = 'PASS'
        print('PASS Last Council native smoke:', reports, flush=True)
    finally:
        for process, handle in clients:
            if process.poll() is None: process.terminate(); process.wait(timeout=15)
            handle.close()
        fabric_smoke._stop(server)
        if log: log.close()
        (reports / 'report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__': run()
