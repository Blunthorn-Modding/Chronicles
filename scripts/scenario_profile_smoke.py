"""Native isolated custom scenario lifecycle; never targets a play/review server."""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import fabric_smoke
import menu_smoke


def hashes(directory):
    return {str(p.relative_to(directory)): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in directory.rglob('*') if p.is_file()}


def run(build=True):
    import workspace as w
    w.require_eula(); w.validate_server_runtime(); w.validate_managed_mod_names()
    if build:
        w.run([w.REPO/'gradlew.bat', '--offline', '--no-daemon', 'build', ':campaigns:quiet-below:writeSmokeClientLaunch'])
    launch = json.loads((w.REPO/'campaigns/quiet-below/build/smoke/client-launch.json').read_text(encoding='utf-8'))
    source = fabric_smoke._stage(w)
    source.joinpath('.player-roles-fixture').write_text('Disposable scenario profile fixture\n', encoding='utf-8')
    reports = w.REPORTS/('scenario-profile-'+source.name); reports.mkdir()
    report = {'result': 'FAIL', 'source': str(source), 'checks': []}
    server = None; clients = []; log = None

    def phase(root, name, mode, width=640, height=480):
        nonlocal server, clients, log
        menu_smoke._reserve_ports()
        log = (reports/(name+'-server.log')).open('w', encoding='utf-8')
        env = w.env(); env['CHRONICLE_ROLES_TRIAL'] = 'true'
        server = subprocess.Popen(w.server_command(), cwd=root, env=env, stdin=subprocess.PIPE,
                                  stdout=log, stderr=subprocess.STDOUT, text=True, encoding='utf-8')
        menu_smoke._wait_ready(server, reports/(name+'-server.log'))
        result = reports/(name+'.json')
        clients = [fabric_smoke._client(w, launch, root, reports, 'ChronicleFabricA', name, {
            'CHRONICLE_SCENARIO_SMOKE': str(result), 'CHRONICLE_SCENARIO_MODE': mode,
            'CHRONICLE_ROLES_SERVER_DIR': str(root), 'CHRONICLE_MENU_WIDTH': str(width),
            'CHRONICLE_MENU_HEIGHT': str(height)})]
        fabric_smoke._await_clients(clients, [result], timeout=160)
        proof = json.loads(result.read_text(encoding='utf-8'))
        if proof.get('result') != 'PASS':
            raise RuntimeError(json.dumps(proof))
        for _, handle in clients: handle.close()
        clients = []
        fabric_smoke._stop(server); server = None; log.close(); log = None
        report['checks'].extend(name+': '+check for check in proof.get('checks', []))

    def fresh(profile):
        root = fabric_smoke._stage(w)
        root.joinpath('.player-roles-fixture').write_text('Disposable isolated profile fixture\n', encoding='utf-8')
        config = root/'config/chronicle'; config.mkdir()
        shutil.copy2(profile, config/'scenario-profile.json')
        return root

    try:
        phase(source, 'create', 'create', 640, 480)
        kits = list((source/'config/chronicle/scenario-setups').glob('*.json'))
        if len(kits) != 1: raise RuntimeError('Expected one kit')
        kit = json.loads(kits[0].read_text(encoding='utf-8'))
        if any(kit['content'][key] for key in ('books', 'lootTables', 'events', 'encounterGroups')):
            raise RuntimeError('Kit was not empty')
        source_hashes = hashes(source/'world')
        root = fresh(kits[0]); report['authoring'] = str(root)
        phase(root, 'author', 'profile', 1280, 720)
        exports = list((root/'config/chronicle/scenario-exports').glob('*.json'))
        if len(exports) != 1: raise RuntimeError('Expected one profile export')
        shutil.copy2(exports[0], root/'config/chronicle/scenario-profile.json')
        phase(root, 'author-restart', 'edited')
        imported = fresh(exports[0]); report['imported'] = str(imported)
        phase(imported, 'imported', 'imported')
        # Normal stopped-server edits must survive restart despite older profile defaults.
        books = imported/'config/chronicle/profile-content/books.json'
        content = json.loads(books.read_text(encoding='utf-8'))
        content['books'][0]['title'] = 'Edited journal'
        books.write_text(json.dumps(content), encoding='utf-8')
        roles = imported/'world/chronicle/player-roles.yaml'
        # The native normal delete operation is exercised in the main role suite.
        # This exact empty catalog is the production encoder's representation.
        roles.write_text('schemaVersion: 1\nroles: []\n', encoding='utf-8')
        phase(imported, 'edited', 'edited')
        if hashes(source/'world') != source_hashes: raise RuntimeError('Source world changed during isolated authoring')
        if (root/'config/chronicle/books.json').exists(): raise RuntimeError('Profile used legacy catalog path')
        report['sourceWorldUnchanged'] = True
        report['result'] = 'PASS'
        print('PASS scenario lifecycle:', reports, flush=True)
    finally:
        for process, handle in clients:
            if process.poll() is None: process.terminate()
            handle.close()
        fabric_smoke._stop(server)
        if log: log.close()
        (reports/'report.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')


if __name__ == '__main__': run()
