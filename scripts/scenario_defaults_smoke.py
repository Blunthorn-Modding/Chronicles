"""Disposable native defaults proof: world copy, ACTIVE restart guard and UI save."""
from __future__ import annotations
import hashlib
import json
import shutil
import subprocess
from pathlib import Path
import fabric_smoke
import menu_smoke


def _hashes(root: Path) -> dict[str, str]:
    return {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest() for p in root.rglob('*') if p.is_file()}


def run(build: bool = False) -> None:
    import workspace as w
    w.require_eula(); w.validate_server_runtime(); w.validate_managed_mod_names()
    if build:
        w.run([w.REPO / 'gradlew.bat', '--offline', '--no-daemon', ':core:remapJar', ':campaigns:quiet-below:remapJar', ':campaigns:quiet-below:writeSmokeClientLaunch'])
    launch = json.loads((w.REPO / 'campaigns/quiet-below/build/smoke/client-launch.json').read_text(encoding='utf-8'))
    original = fabric_smoke._stage(w)
    (original / '.scenario-defaults-fixture').write_text('Disposable scenario defaults proof\n', encoding='utf-8')
    reports = w.REPORTS / ('scenario-defaults-' + original.name); reports.mkdir(parents=True)
    report = {'result': 'FAIL', 'world': str(original), 'checks': [], 'limitations': [],
              'artifacts': {p.name: w.digest(p) for p in w.artifacts()}}
    server = None; log = None; clients = []
    try:
        env = w.env(); env['CHRONICLE_SCENARIO_DEFAULTS_TRIAL'] = 'true'
        followup_roots = {}
        for phase in ('fresh', 'copied', 'active_prepare', 'active_restart', 'ui_save', 'ui_restart'):
            if phase == 'fresh':
                root = original
            elif phase == 'copied':
                before = _hashes(original / 'world')
                root = fabric_smoke._stage(w)
                (root / '.scenario-defaults-fixture').write_text('Disposable copied-world proof\n', encoding='utf-8')
                if _hashes(original / 'mods') != _hashes(root / 'mods'): raise RuntimeError('staged servers do not have identical mods')
                shutil.copytree(original / 'world', root / 'world')
                report['copiedWorld'] = str(root)
            elif phase in ('active_prepare', 'ui_save'):
                root = fabric_smoke._stage(w)
                (root / '.scenario-defaults-fixture').write_text('Disposable native follow-up proof\n', encoding='utf-8')
                followup_roots[phase] = root
                report[phase + 'World'] = str(root)
            else:
                root = followup_roots['active_prepare' if phase == 'active_restart' else 'ui_save']
            menu_smoke._reserve_ports()
            log = (reports / (phase + '-server.log')).open('w', encoding='utf-8')
            server = subprocess.Popen(w.server_command(), cwd=root, env=env, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True, encoding='utf-8')
            menu_smoke._wait_ready(server, reports / (phase + '-server.log'))
            result = reports / (phase + '-client.json')
            client_env = {'CHRONICLE_SCENARIO_DEFAULTS_ROOT': str(root), 'CHRONICLE_SCENARIO_DEFAULTS_SMOKE': str(result), 'CHRONICLE_SCENARIO_DEFAULTS_PHASE': phase, 'CHRONICLE_MENU_WIDTH': '1280', 'CHRONICLE_MENU_HEIGHT': '720'}
            clients = [fabric_smoke._client(w, launch, root, reports, 'ChronicleFabricA', phase, client_env)]
            fabric_smoke._await_clients(clients, [result], timeout=160)
            proof = json.loads(result.read_text(encoding='utf-8'))
            if proof.get('result') != 'PASS': raise RuntimeError(str(proof))
            report['checks'].extend(proof['checks'])
            for _, handle in clients: handle.close()
            clients = []
            fabric_smoke._stop(server); server = None; log.close(); log = None
            if phase == 'copied':
                if _hashes(original / 'world') != before: raise RuntimeError('original stopped world changed during copy proof')
                report['checks'].append('original stopped world hashes unchanged while copied world ran')
        report['result'] = 'PASS'
        print('PASS scenario defaults native proof including ACTIVE restart and UI-save follow-ups:', reports, flush=True)
    finally:
        for process, handle in clients:
            if process.poll() is None: process.terminate()
            handle.close()
        fabric_smoke._stop(server)
        if log: log.close()
        (reports / 'report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__': run()
