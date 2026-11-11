"""Real-client Quiet Below ecology rehearsal in a retained disposable world."""
import json
import socket
import subprocess
import time
import shutil


def _wait_ready(server, logfile):
    deadline = time.monotonic() + 240
    while True:
        output = logfile.read_text(encoding='utf-8', errors='replace') if logfile.exists() else ''
        if 'Done (' in output and 'Voice chat server started at 127.0.0.1:24455' in output:
            return
        if server.poll() is not None or time.monotonic() > deadline:
            raise RuntimeError('Ecology fixture failed startup: ' + str(logfile))
        time.sleep(.2)


def _reserve_ports():
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', 25575))
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
        probe.bind(('127.0.0.1', 24455))


def _city_world(root):
    """Use a deterministic normal overworld before the first server launch for real structures."""
    properties = root / 'server.properties'
    text = properties.read_text(encoding='utf-8')
    text = text.replace('level-type=minecraft:flat', 'level-type=minecraft:normal')
    text = text.replace('generate-structures=false', 'generate-structures=true')
    properties.write_text(text.replace('level-name=world', 'level-name=world\nlevel-seed=quiet-below-city-soak-v1'), encoding='utf-8')


def run(phases=None):
    import workspace as w
    import fabric_smoke as f
    default = ('initial', 'restart', 'surface', 'organic', 'vitality', 'items', 'crops', 'crops_restart')
    allowed = default + ('city', 'city_restart', 'compact', 'recovery', 'recovery_restart', 'costs', 'costs_restart')
    phases = default if phases is None else tuple(phases)
    if not phases or any(p not in allowed for p in phases) or len(set(phases)) != len(phases):
        raise ValueError('Select distinct known ecology phases')
    if 'restart' in phases and ('initial' not in phases or phases.index('initial') > phases.index('restart')):
        raise ValueError('Restart requires an earlier initial phase in the same run')
    if 'crops_restart' in phases and ('crops' not in phases or phases.index('crops') > phases.index('crops_restart')):
        raise ValueError('Crop restart requires an earlier crop phase in the same run')
    if 'city_restart' in phases and ('city' not in phases or phases.index('city') > phases.index('city_restart')):
        raise ValueError('Ancient-city restart requires an earlier city phase in the same run')
    if 'compact' in phases and not ('initial' in phases and 'restart' in phases and phases.index('initial') < phases.index('compact') < phases.index('restart')):
        raise ValueError('Compaction requires initial, compact, restart in that order')
    if 'recovery_restart' in phases and not ('recovery' in phases and phases.index('recovery') < phases.index('recovery_restart')):
        raise ValueError('Healing restart requires an earlier recovery phase')
    if 'costs_restart' in phases and not ('costs' in phases and phases.index('costs') < phases.index('costs_restart')):
        raise ValueError('Local-cost restart requires an earlier costs phase')
    w.require_eula()
    w.validate_server_runtime()
    w.validate_managed_mod_names()
    w.run([w.REPO / 'gradlew.bat', '--no-daemon', ':core:remapJar',
           ':campaigns:quiet-below:remapJar', ':campaigns:quiet-below:writeSmokeClientLaunch'])
    launch = json.loads((w.REPO / 'campaigns/quiet-below/build/smoke/client-launch.json').read_text(encoding='utf-8'))
    root = f._stage(w)
    if 'city' in phases:
        _city_world(root)
    reports = w.REPORTS / ('ecology-smoke-' + root.name)
    reports.mkdir(parents=True)
    report = {'result': 'FAIL', 'world': str(root), 'reports': str(reports), 'phases': list(phases), 'checks': []}
    server = None
    server_log = None
    clients = []
    crop_root = None
    recovery_root = None
    costs_root = None
    try:
        for phase in phases:
            if phase == 'compact':
                from ecology_maintenance import run as compact
                maintenance = compact(root / 'world', apply=True)
                if not maintenance['applied']:
                    raise RuntimeError('Native compaction fixture did not install a smaller checkpoint')
                (reports / 'compaction.json').write_text(json.dumps(maintenance, indent=2), encoding='utf-8')
                report['checks'].append('compact: stopped-world checkpoint verified, archived and installed')
                continue
            if phase == 'crops':
                crop_root = f._stage(w)
            if phase == 'recovery':
                recovery_root = f._stage(w)
            if phase == 'costs':
                costs_root = f._stage(w)
            phase_root = f._stage(w) if phase in ('surface', 'organic', 'vitality', 'items') else root
            if phase in ('crops', 'crops_restart'):
                phase_root = crop_root
                report['crops_world'] = str(crop_root)
            if phase in ('recovery', 'recovery_restart'):
                phase_root = recovery_root
                report['recovery_world'] = str(recovery_root)
            if phase in ('costs', 'costs_restart'):
                phase_root = costs_root
                report['costs_world'] = str(costs_root)
            if phase in ('surface', 'organic', 'vitality', 'items'):
                report[phase + '_world'] = str(phase_root)
            if phase in ('city', 'city_restart'):
                report['city_world'] = str(root)
            _reserve_ports()
            logfile = reports / ('server-' + phase + '.log')
            server_log = logfile.open('w', encoding='utf-8')
            trial_env=w.env()
            trial_env['CHRONICLE_ECOLOGY_NATIVE_TRIAL']='1'
            server = subprocess.Popen(w.server_command(), cwd=phase_root, env=trial_env, stdin=subprocess.PIPE,
                                      stdout=server_log, stderr=subprocess.STDOUT, text=True, encoding='utf-8')
            _wait_ready(server, logfile)
            variables={'CHRONICLE_ECOLOGY_SMOKE':str(reports),'CHRONICLE_ECOLOGY_PHASE':phase}
            if phase in ('recovery','recovery_restart'):
                variables.update(CHRONICLE_MENU_WIDTH='640' if phase == 'recovery' else '1280',
                                 CHRONICLE_MENU_HEIGHT='480' if phase == 'recovery' else '720')
            if phase in ('costs','costs_restart'):
                variables.update(CHRONICLE_MENU_WIDTH='640' if phase == 'costs' else '1280',
                                 CHRONICLE_MENU_HEIGHT='480' if phase == 'costs' else '720')
            clients = [f._client(w, launch, phase_root, reports, 'ChronicleFabricA', 'ecology-' + phase, variables)]
            f._await_clients(clients, [reports / ('ecology-' + phase + '.json')], timeout=1200 if phase in ('city', 'city_restart') else 300)
            for _, handle in clients:
                handle.close()
            clients = []
            f._stop(server)
            server = None
            server_log.close()
            server_log = None
            if phase == 'restart':
                shutil.copytree(root / 'world', root / 'review-world')
            if phase == 'crops_restart':
                shutil.copytree(crop_root / 'world', crop_root / 'review-world')
            if phase == 'recovery_restart':
                shutil.copytree(recovery_root / 'world', recovery_root / 'review-world')
            if phase == 'costs_restart':
                shutil.copytree(costs_root / 'world', costs_root / 'review-world')
            report['checks'].append(phase + ': native client ecology fixture passed and server saved cleanly')
        report['result'] = 'PASS'
        print('PASS: retained ecology fixture; evidence:', reports, flush=True)
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
                    process.kill()
                    process.wait(timeout=15)
            handle.close()
        try:
            f._stop(server)
        finally:
            if server_log is not None:
                server_log.close()
            (w.REPORTS / 'ecology-smoke-latest.json').write_text(json.dumps(report, indent=2), encoding='utf-8')


if __name__ == '__main__':
    run()
