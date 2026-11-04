"""Disposable two-client audio and quiet-period history-maintenance verification."""
from __future__ import annotations
import json
import subprocess
import time
import fabric_smoke
import menu_smoke


def run(build=True):
    import workspace as w
    w.require_eula(); w.validate_server_runtime(); w.validate_managed_mod_names()
    if build:
        w.run([w.REPO/'gradlew.bat','--offline','--no-daemon',':core:remapJar',':campaigns:quiet-below:remapJar',':campaigns:quiet-below:writeSmokeClientLaunch'])
    launch=json.loads((w.REPO/'campaigns/quiet-below/build/smoke/client-launch.json').read_text(encoding='utf-8'))
    root=fabric_smoke._stage(w);(root/'.foundation-fixture').write_text('Disposable foundation verification\n',encoding='utf-8')
    # A real Warden plus an allowed pig encounter exercise normal and Event-owned spawn sources.
    properties=root/'server.properties'
    properties.write_text(properties.read_text(encoding='utf-8').replace('difficulty=peaceful','difficulty=normal'),encoding='utf-8')
    config=root/'config/chronicle';config.mkdir()
    (config/'event-history-maintenance.json').write_text(json.dumps({'schemaVersion':1,'enabled':True,'quietSeconds':1,'intervalSeconds':2}),encoding='utf-8')
    reports=w.REPORTS/('foundation-'+root.name);reports.mkdir()
    report={'result':'FAIL','world':str(root),'checks':[]};server=None;clients=[];log=None
    env=w.env();env['CHRONICLE_FOUNDATION_TRIAL']='true'

    def start(name):
        nonlocal log,server
        menu_smoke._reserve_ports();log=(reports/name).open('w',encoding='utf-8')
        server=subprocess.Popen(w.server_command(),cwd=root,env=env,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True,encoding='utf-8')
        menu_smoke._wait_ready(server,reports/name)

    def verify(name):
        path=reports/name;deadline=time.monotonic()+20
        while time.monotonic()<deadline:
            server.stdin.write('chronicle_foundation verify_auto\n');server.stdin.flush();time.sleep(.3)
            text=path.read_text(encoding='utf-8')
            if 'Foundation trial FAIL' in text:raise RuntimeError('Native foundation verification failed; inspect '+str(path))
            if 'Foundation trial PASS verify_auto' in text:return
        raise RuntimeError('Automatic archival did not complete within bounded wait')

    try:
        start('server.log')
        b=reports/'observer.json';a=reports/'operator.json'
        common={'CHRONICLE_FOUNDATION_ROOT':str(root)}
        clients.append(fabric_smoke._client(w,launch,root,reports,'ChronicleFabricB','observer',dict(common,CHRONICLE_FOUNDATION_SMOKE=str(b),CHRONICLE_FOUNDATION_OBSERVER='true')))
        deadline=time.monotonic()+80
        while 'ChronicleFabricB joined the game' not in (reports/'server.log').read_text(encoding='utf-8'):
            if time.monotonic()>deadline or clients[0][0].poll() is not None:raise RuntimeError('Observer did not join')
            time.sleep(.2)
        clients.append(fabric_smoke._client(w,launch,root,reports,'ChronicleFabricA','operator',dict(common,CHRONICLE_FOUNDATION_SMOKE=str(a))))
        fabric_smoke._await_clients(clients,[b,a],timeout=220)
        for result in (b,a):
            proof=json.loads(result.read_text(encoding='utf-8'))
            if proof.get('result')!='PASS':raise RuntimeError(str(proof))
            report['checks'].extend(proof['checks'])
        for _,handle in clients:handle.close()
        clients=[]
        verify('server.log');report['checks'].append('automatic empty-server maintenance archived completed source/flag evidence while retaining campaign flag')
        fabric_smoke._stop(server);server=None;log.close();log=None
        start('restart.log');verify('restart.log');report['checks'].append('fresh restart retained archived authority, campaign flag and completed Event without replay')
        fabric_smoke._stop(server);server=None;log.close();log=None
        report['result']='PASS';print('PASS foundation native proof:',reports,flush=True)
    finally:
        for process,handle in clients:
            if process.poll() is None:process.terminate()
            handle.close()
        fabric_smoke._stop(server)
        if log:log.close()
        (reports/'report.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')


if __name__=='__main__':run()
