"""Native static sculk gallery, legacy-world migration and incompatible-peer rehearsal."""
import copy
import json
from pathlib import Path
import shutil
import subprocess
import zipfile
import fabric_smoke as f
import ecology_smoke as e
import workspace as w

LEGACY = w.WORK/'test-fixtures/sculk-layout-dev18'


def legacy_artifacts(kind):
    manifest=json.loads((LEGACY/'manifest.json').read_text())
    paths=[]
    for name,sha in manifest.items():
        if name.startswith(kind+'-'):
            path=LEGACY/name
            if w.digest(path)!=sha:raise RuntimeError('Retained legacy fixture artifact changed')
            paths.append(path)
    if len(paths)!=2:raise RuntimeError('Retain both dev.18 fixture artifacts before testing compatibility')
    return paths


def legacy_launch(current,root):
    result=copy.deepcopy(current)
    modules=[(w.REPO/name/'build').resolve() for name in ('core','campaigns/quiet-below')]
    def owned(path):
        p=Path(path).resolve()
        return any(p.is_relative_to(module) for module in modules)
    jars=legacy_artifacts('named')
    result['classpath']=[p for p in result['classpath'] if not owned(p)]+[str(p) for p in jars]
    helper=root/'compatibility-probe.jar'
    classes=w.REPO/'campaigns/quiet-below/build/classes/java/client'
    name='io/github/chronicle/quietbelow/client/QuietBelowCompatibilitySmoke'
    with zipfile.ZipFile(helper,'x') as zip:
        for path in classes.glob(name+'*.class'):zip.write(path,path.relative_to(classes).as_posix())
        zip.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'chronicle_layout_test','version':'1',
                    'environment':'client','entrypoints':{'client':[name.replace('/','.')]}}))
    result['classpath'].append(str(helper))
    config=w.REPO/'campaigns/quiet-below/.gradle/loom-cache/launch.cfg'
    lines=[]
    for line in config.read_text().splitlines():
        if line.strip().startswith('fabric.classPathGroups='):
            groups=line.split('=',1)[1].split(';;')
            groups=[group for group in groups if not any(owned(p) for p in group.split(';'))]
            line='\tfabric.classPathGroups='+';;'.join(groups+[str(p) for p in jars]+[str(helper)])
        lines.append(line)
    target=root/'legacy-launch.cfg';target.write_text('\n'.join(lines)+'\n',encoding='utf-8')
    result['jvmArgs']=[a for a in result['jvmArgs'] if not a.startswith('-Dfabric.dli.config=')]
    result['jvmArgs'].append('-Dfabric.dli.config='+str(target).replace(' ','@@0020'))
    return result


def run():
    w.require_eula();w.validate_server_runtime();w.validate_managed_mod_names()
    w.run([w.REPO/'gradlew.bat','--no-daemon',':core:remapJar',':campaigns:quiet-below:remapJar',':campaigns:quiet-below:writeSmokeClientLaunch'])
    launch=json.loads((w.REPO/'campaigns/quiet-below/build/smoke/client-launch.json').read_text())
    first=f._stage(w);reports=w.REPORTS/('appearance-smoke-'+first.name);reports.mkdir()
    report={'result':'FAIL','reports':str(reports),'worlds':{},'checks':[]}
    server=log=None;clients=[]
    try:
        for phase in ('appearance','appearance_legacy','old-client','old-server'):
            root=first if phase=='appearance' else f._stage(w)
            report['worlds'][phase]=str(root)
            if phase=='appearance_legacy':
                source=LEGACY/'world'
                snapshot=json.loads((LEGACY/'world-source.json').read_text())
                if snapshot['phase']!='PAUSED' or w.digest(source/'quiet-below/ecology.jsonl')!=snapshot['journalSha256']:
                    raise RuntimeError('Retained dev.18 vitality snapshot changed')
                shutil.copytree(source,root/'world')
            if phase=='old-server':
                for jar in w.artifacts():(root/'mods'/jar.name).unlink()
                for jar in legacy_artifacts('remapped'):shutil.copy2(jar,root/'mods'/jar.name.removeprefix('remapped-'))
            e._reserve_ports();logfile=reports/('server-'+phase+'.log');log=logfile.open('w',encoding='utf-8')
            env=w.env();env['CHRONICLE_ECOLOGY_NATIVE_TRIAL']='1'
            server=subprocess.Popen(w.server_command(),cwd=root,env=env,stdin=subprocess.PIPE,
                                    stdout=log,stderr=subprocess.STDOUT,text=True,encoding='utf-8')
            e._wait_ready(server,logfile)
            rejection=phase.startswith('old-')
            variables={'CHRONICLE_COMPATIBILITY_SMOKE':str(reports),'CHRONICLE_COMPATIBILITY_CASE':phase} if rejection else {
                'CHRONICLE_ECOLOGY_SMOKE':str(reports),'CHRONICLE_ECOLOGY_PHASE':phase}
            client_launch=legacy_launch(launch,root) if phase=='old-client' else launch
            clients=[f._client(w,client_launch,root,reports,'ChronicleFabricA',phase,variables)]
            result_path=reports/(('compatibility-' if rejection else 'ecology-')+phase+'.json')
            f._await_clients(clients,[result_path],timeout=240)
            result=json.loads(result_path.read_text())
            report['checks'].extend(phase+': '+check for check in result['checks'])
            for _,handle in clients:handle.close()
            clients=[];f._stop(server);server=None;log.close();log=None
            if rejection and 'logged in with entity id' in logfile.read_text(errors='replace'):
                raise RuntimeError('Incompatible peer reached PLAY before rejection')
            if not rejection:
                output=(reports/(phase+'.log')).read_text(errors='replace')
                if any(line for line in output.splitlines() if ('[WARN]' in line or '[ERROR]' in line)
                       and ('Unable to load model' in line or 'Missing textures' in line or 'Missing model' in line)):
                    raise RuntimeError('Native appearance client reported missing model/texture resources')
        report['result']='PASS';print('PASS: sculk appearance and compatibility; evidence:',reports,flush=True)
    except BaseException as error:
        report['error']=str(error);raise
    finally:
        for process,handle in clients:
            if process.poll() is None:
                process.terminate()
                try:process.wait(timeout=15)
                except subprocess.TimeoutExpired:process.kill();process.wait(timeout=15)
            handle.close()
        try:f._stop(server)
        finally:
            if log is not None:log.close()
            (w.REPORTS/'appearance-smoke-latest.json').write_text(json.dumps(report,indent=2),encoding='utf-8')


if __name__=='__main__':run()
