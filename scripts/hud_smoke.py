"""Real HUD editor in an isolated native client and disposable loopback world.

Only the original test helper is compiled. Vendor jars are unmodified official
downloads, loaded in this private test profile and never embedded in a pack.
"""
from __future__ import annotations

import json
import os
from pathlib import Path
import subprocess
import zipfile

import fabric_smoke as f
import hud_dependencies as hud
import menu_smoke as menu
import workspace as w


def helper_jar(launch, root):
    classes = root / 'hud-probe-classes'
    classes.mkdir()
    arguments = ['-proc:none', '-encoding', 'UTF-8', '--release', '21', '-cp', os.pathsep.join(launch['classpath']),
                 '-d', str(classes), str(w.REPO / 'testing/hud/HudPrototypeClient.java')]
    argfile = root / 'hud-javac.args'
    argfile.write_text('\n'.join(map(f._java_arg, arguments)), encoding='utf-8')
    w.run([w.JAVA.with_name('javac.exe'), '@' + str(argfile)])
    target = root / 'chronicle-hud-test-helper.jar'
    with zipfile.ZipFile(target, 'x') as archive:
        archive.writestr('fabric.mod.json', json.dumps({'schemaVersion': 1, 'id': 'chronicle_hud_test',
                         'version': '1', 'environment': 'client',
                         'entrypoints': {'client': ['io.github.chronicle.testing.hud.HudPrototypeClient']}}))
        for source in classes.rglob('*.class'):
            archive.write(source, source.relative_to(classes).as_posix())
    return target


def run():
    w.require_eula()
    w.validate_server_runtime()
    w.validate_managed_mod_names()
    mods = hud.load_lock()
    jars = hud.cached_jars(mods)
    w.run([w.REPO / 'gradlew.bat', '--no-daemon', ':campaigns:quiet-below:writeSmokeClientLaunch'])
    launch = json.loads((w.REPO / 'campaigns/quiet-below/build/smoke/client-launch.json').read_text())
    root = f._stage(w)
    reports = w.REPORTS / ('hud-prototype-' + root.name)
    reports.mkdir()
    helper = helper_jar(launch, root)
    # The helper uses named development APIs; the four official runtime mods
    # are discovered/remapped by Fabric in its ordinary development pipeline.
    launch['classpath'].append(str(helper))
    launch['jvmArgs'].append('-Dfabric.addMods=' + os.pathsep.join(map(str, jars)))
    report = {'result': 'FAIL', 'world': str(root), 'reports': str(reports),
              'mods': [{k: m[k] for k in ('slug', 'version', 'sha512', 'url')} for m in mods]}
    server = server_log = None
    clients = []
    try:
        menu._reserve_ports()
        log = reports / 'server.log'
        server_log = log.open('w', encoding='utf-8')
        server = subprocess.Popen(w.server_command(), cwd=root, env=w.env(), stdin=subprocess.PIPE,
                                  stdout=server_log, stderr=subprocess.STDOUT, text=True, encoding='utf-8')
        menu._wait_ready(server, log)
        clients = [f._client(w, launch, root, reports, 'ChronicleFabricA', 'hud',
                            {'CHRONICLE_HUD_SMOKE': str(reports)})]
        result = reports / 'hud-prototype.json'
        f._await_clients(clients, [result], timeout=240)
        report['native'] = json.loads(result.read_text())
        report['result'] = 'PASS'
        print('PASS: HUD editor prototype; evidence:', reports, flush=True)
    except BaseException as error:
        report['error'] = str(error)
        raise
    finally:
        for process, handle in clients:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill(); process.wait(timeout=15)
            handle.close()
        try:
            f._stop(server)
        finally:
            if server_log:
                server_log.close()
            (reports / 'report.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
            (w.REPORTS / 'hud-prototype-latest.json').write_text(json.dumps(report, indent=2), encoding='utf-8')


if __name__ == '__main__':
    run()
