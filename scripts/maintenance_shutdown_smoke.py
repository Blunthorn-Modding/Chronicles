"""Focused native shutdown/reopen regression on a disposable captured-city world copy."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import threading
import time
from datetime import datetime, timezone

import workspace as w
import fabric_smoke as fabric
import ecology_smoke
import long_soak

CAPTURED = w.WORK / 'test-runs/20261101T091854451345Z'
CAPTURED_HASH = '599e570eb8db8547707c786b7a3aa27562d2456d588bb2d73428f499ab4af835'


def hashes(folder: Path) -> dict[str, str]:
    return {str(p.relative_to(folder)): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in folder.rglob('*') if p.is_file()}


def await_file(path: Path, process, timeout=180) -> dict:
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        if path.is_file():
            value = json.loads(path.read_text(encoding='utf-8'))
            if value.get('result') != 'PASS':
                raise RuntimeError('Native proof failed: ' + str(path))
            return value
        if process.poll() is not None:
            raise RuntimeError('Native server exited before proof: ' + str(path))
        time.sleep(.1)
    raise RuntimeError('Native proof deadline: ' + str(path))


def run() -> dict:
    w.require_eula()
    w.validate_server_runtime()
    w.validate_managed_mod_names()
    source = CAPTURED.resolve()
    if source.parent != (w.WORK/'test-runs').resolve() or not long_soak.RUN_ID.fullmatch(source.name):
        raise ValueError('Captured input must remain a timestamped test-runs child')
    journal = source/'world/quiet-below/ecology.jsonl'
    if w.digest(journal) != CAPTURED_HASH:
        raise ValueError('Captured input no longer matches the recorded journal')
    before = hashes(source/'world')
    old_report = w.WORK/'reports/long-soak-20261101T091854118022Z/report.json'
    report_hash = w.digest(old_report)
    review_before = long_soak.review_artifacts(w.WORK)
    ecology_smoke._reserve_ports()
    root = fabric._stage(w)
    shutil.copytree(source/'world', root/'world')
    long_soak.configure_world(root)
    (root/'.captured-shutdown-fixture').write_text(CAPTURED_HASH, encoding='utf-8')
    (root/'.ecology-auto-maintenance-fixture').write_text('owned captured shutdown fixture', encoding='utf-8')
    reports = w.REPORTS/('maintenance-shutdown-'+root.name)
    reports.mkdir()
    result = {'result': 'FAIL', 'source': str(source), 'world': str(root), 'reports': str(reports),
              'sourceJournalSha256': CAPTURED_HASH, 'phases': {}}
    try:
        for phase in ('drain', 'restart'):
            ecology_smoke._reserve_ports()
            phase_reports = reports/phase
            phase_reports.mkdir()
            temp = root/('temp-'+phase);temp.mkdir()
            repository = phase_reports/'jfr-repository';repository.mkdir()
            environment = w.env()
            for key in list(environment):
                if key.startswith('CHRONICLE_'):environment.pop(key)
            environment.update(CHRONICLE_ECOLOGY_AUTO_MAINTENANCE_TRIAL='true',
                CHRONICLE_CAPTURED_SHUTDOWN_REPORTS=str(reports), CHRONICLE_CAPTURED_SHUTDOWN_PHASE=phase,
                TEMP=str(temp), TMP=str(temp))
            recording = phase_reports/'server.jfr'
            command = [str(w.JAVA), '-Xms512M', '-Xmx2G', '-Djava.io.tmpdir='+str(temp),
                '-XX:FlightRecorderOptions=repository="'+str(repository).replace('\\','/')+'"',
                '-XX:StartFlightRecording=filename="'+str(recording).replace('\\','/')+'",settings=profile,dumponexit=true,maxsize=256m',
                '-jar', 'fabric-server-launch.jar', 'nogui']
            phase_result = {'outcome': 'PENDING'};result['phases'][phase]=phase_result
            def checkpoint(state, **extra):
                phase_result.update(state=state, **extra)
                long_soak.atomic_json(phase_reports/'status.json', phase_result)
            process = None
            release = {'observedShutdownWait': False}
            with (phase_reports/'server.log').open('w', encoding='utf-8') as output:
                try:
                    process = subprocess.Popen(command, cwd=root, env=environment, stdin=subprocess.PIPE,
                        stdout=output, stderr=subprocess.STDOUT, text=True, encoding='utf-8',
                        creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
                    ecology_smoke._wait_ready(process, phase_reports/'server.log')
                    proof = await_file(reports/('ready.json' if phase=='drain' else 'restart.json'), process)
                    phase_result['startupProof']=proof
                    if phase=='drain':
                        def release_after_stop():
                            end = time.monotonic()+60
                            while process.poll() is None and time.monotonic()<end:
                                content=(phase_reports/'server.log').read_text(encoding='utf-8',errors='replace')
                                if 'Waiting for ecology maintenance before releasing world ownership' in content:
                                    release['observedShutdownWait']=True
                                    (reports/'release.request').write_text('release after production stop entered drain',encoding='utf-8')
                                    return
                                time.sleep(.05)
                        threading.Thread(target=release_after_stop, daemon=True).start()
                    clean = long_soak.stop_owned_server(process,w.JAVA,phase_reports,phase_result,checkpoint)
                    phase_result['drainProof']=await_file(reports/('drained.json' if phase=='drain' else 'restart-drained.json'),process,timeout=1)
                    phase_result['profileReadable']=long_soak.summarize_recording(w.JAVA,phase_reports,phase_result)
                    backup=phase_reports/'server-pre-stop.jfr'
                    phase_result['preStopSnapshotExists']=backup.is_file() and backup.stat().st_size>0
                    if phase_result['preStopSnapshotExists']:
                        with (phase_reports/'pre-stop-jfr-summary.txt').open('w',encoding='utf-8') as profile_summary:
                            verification=subprocess.run([str(w.JAVA.parent/'jfr.exe'),'summary',str(backup)],
                                stdout=profile_summary,stderr=subprocess.STDOUT,timeout=30)
                        phase_result['preStopSnapshotReadable']=verification.returncode==0
                    content=(phase_reports/'server.log').read_text(encoding='utf-8',errors='replace')
                    phase_result['vanillaSaveCompleted']='All dimensions are saved' in content
                    phase_result.update(release if phase=='drain' else {})
                    assert clean and process.returncode==0 and phase_result['profileReadable']
                    assert phase_result.get('preStopSnapshotReadable'), 'Pre-stop profiler snapshot missing or unreadable'
                    assert phase_result['vanillaSaveCompleted'], 'Native final world-save completion was not logged'
                    assert phase!='drain' or release['observedShutdownWait']
                    phase_result.update(result='PASS',outcome='PASS')
                    checkpoint('FINISHED')
                finally:
                    if process is not None and process.poll() is None:
                        long_soak.stop_owned_server(process,w.JAVA,phase_reports,phase_result,checkpoint)
            print(json.dumps({'phase':phase,'result':phase_result.get('result'),
                              'shutdown':phase_result.get('shutdown')},indent=2),flush=True)
        result['result']='PASS'
    except BaseException as error:
        result['error']=str(error)
        raise
    finally:
        result['originalWorldUnchanged']=before==hashes(source/'world')
        result['originalReportUnchanged']=report_hash==w.digest(old_report)
        result['reviewArtifactsUnchanged']=review_before==long_soak.review_artifacts(w.WORK)
        if not all(result[key] for key in ('originalWorldUnchanged','originalReportUnchanged','reviewArtifactsUnchanged')):
            result['result']='FAIL'
        long_soak.atomic_json(reports/'report.json',result)
        long_soak.atomic_json(w.REPORTS/'maintenance-shutdown-latest.json',result)
        print(json.dumps({'result':result['result'],'reports':str(reports),'error':result.get('error')},indent=2),flush=True)
    return result


if __name__=='__main__':
    raise SystemExit(0 if run()['result']=='PASS' else 1)
