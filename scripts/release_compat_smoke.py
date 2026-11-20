"""Exercise new Core and unchanged released Quiet Below JARs on disposable peers.

Fabric's ordinary development runtime remaps the release inputs for its named
Minecraft client. No current Core/QB source classes or direct YAML dependency
remain on that client's classpath. The dedicated server loads production JARs.
This runner never builds, publishes, or changes an existing world/release.
"""
from __future__ import annotations
import copy
import json
import os
from pathlib import Path
import subprocess
import time
import zipfile
from unittest.mock import patch

import bargain_smoke
import ecology_smoke
import fabric_smoke as fabric
import forge_smoke
import hud_dependencies
import temporary_altar_smoke
import workspace as w

CORE=w.REPO/'core/build/libs/chronicle_core-0.2.0.jar'
QB=w.WORK/'releases/0.1.0-beta/quiet_below-0.1.0-beta.jar'
QB_HASH='cec92b8752a651b3cf101eb54b257143020b28ac0978a9e63daaf5b6b0c1d89a'

def release_launch(current,root,artifacts):
    result=copy.deepcopy(current)
    modules=[(w.REPO/name/'build').resolve() for name in ('core','campaigns/quiet-below')]
    def owned(path):return any(Path(path).resolve().is_relative_to(module) for module in modules)
    result['classpath']=[p for p in result['classpath'] if not owned(p) and 'snakeyaml-engine' not in p]
    lines=[]
    for line in (w.REPO/'campaigns/quiet-below/.gradle/loom-cache/launch.cfg').read_text(encoding='utf-8').splitlines():
        if line.strip().startswith('fabric.classPathGroups='):
            groups=line.split('=',1)[1].split(';;')
            line='\tfabric.classPathGroups='+';;'.join(group for group in groups if not any(owned(p) for p in group.split(';')))
        lines.append(line)
    config=root/'release-client-launch.cfg';config.write_text('\n'.join(lines)+'\n',encoding='utf-8')
    result['jvmArgs']=[a for a in result['jvmArgs'] if not a.startswith(('-Dfabric.dli.config=','-Dfabric.addMods='))]
    result['jvmArgs']+=['-Dfabric.dli.config='+str(config).replace(' ','@@0020'),'-Dfabric.addMods='+os.pathsep.join(str(p.resolve()) for p in artifacts)]
    assert not any(owned(p) for p in result['classpath'])
    assert not any(str(module) in config.read_text(encoding='utf-8') for module in modules)
    (root/'client-provenance.json').write_text(json.dumps(dict(releaseInputs={str(p):w.digest(p) for p in artifacts},classpath=result['classpath'],jvmArgs=result['jvmArgs'],note='Fabric runtime remaps these release JARs; this is not an installed Minecraft Launcher test.'),indent=2)+'\n',encoding='utf-8')
    return result

def stage(artifacts,worlds,label):
    with patch.object(w,'artifacts',return_value=artifacts):root=fabric._stage(w)
    (root/'.release-compat-fixture').write_text('Disposable Core release compatibility fixture\n',encoding='utf-8')
    for path in (root/'mods').glob('last_council-*.jar'):path.unlink()
    for p in artifacts:assert w.digest(root/'mods'/p.name)==w.digest(p)
    worlds[label]=str(root)
    return root

def reconnect_hud_launch(current,_):
    result=copy.deepcopy(current)
    prefix='-Dfabric.addMods='
    inputs=[a[len(prefix):] for a in result['jvmArgs'] if a.startswith(prefix)]
    assert len(inputs)==1
    result['jvmArgs']=[a for a in result['jvmArgs'] if not a.startswith(prefix)]
    result['jvmArgs'].append(prefix+inputs[0]+os.pathsep+os.pathsep.join(map(str,hud_dependencies.cached_jars(hud_dependencies.load_lock()))))
    return result

def paused_restart(root,directory,checks):
    """Check saved ecology without the old probe's forbidden Heart overwrite."""
    journal=root/'world/quiet-below/ecology.jsonl'
    state=root/'world/chronicle/campaign-state.json'
    before=w.digest(journal);saved=json.loads(state.read_text(encoding='utf-8'))
    assert saved['phase']=='PAUSED'
    server=log=None
    try:
        ecology_smoke._reserve_ports();path=directory/'server.log';log=path.open('w',encoding='utf-8')
        server=subprocess.Popen(w.server_command(),cwd=root,env=w.env(),stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True,encoding='utf-8')
        ecology_smoke._wait_ready(server,path);time.sleep(1.2)
        text=path.read_text(encoding='utf-8')
        assert 'chronicle_core 0.2.0' in text and 'quiet_below 0.1.0-beta' in text
        assert 'Quiet Below ecology ready; configured=true, enabled=true' in text
    finally:
        try:fabric._stop(server)
        finally:
            if log:log.close()
    assert json.loads(state.read_text(encoding='utf-8'))==saved
    assert w.digest(journal)==before,'Paused ecology journal changed across restart'
    checks.append('Ecology restart: exact Core/QB versions reload configured living network; paused session/flags/history and ecology journal remain unchanged, without catch-up')

def forge_driver(current,root,launch):
    """Keep real user flows; omit the stale legacy server transaction matrix.

    That indivisible command opens a container while paused without Building
    Mode, which is rejected by both released Core baselines. Its other matrix
    assertions are not claimed as covered by this release runner.
    """
    original=w.REPO/'campaigns/quiet-below/src/client/java/io/github/chronicle/quietbelow/client/AncientForgeSmoke.java'
    source=original.read_text(encoding='utf-8')
    omitted='        command("run Forge sequential-offer authority matrix", "quiet_below_forge_trial transactions", "transactions");'
    assert source.count(omitted)==1
    source=source.replace(omitted,'').replace('package io.github.chronicle.quietbelow.client;','package io.github.chronicle.testing.release;').replace('AncientForgeSmoke','ReleaseForgeSmoke').replace('CHRONICLE_FORGE_','CHRONICLE_RELEASE_FORGE_')
    # Repeat the real full-inventory recovery flow instead of accepting just
    # one passing rerun of the legacy probe's intermittent handler-loss case.
    anchor='        command("render Forge facings and models", "quiet_below_forge_trial render", "render");'
    assert source.count(anchor)==1
    repetitions='''        for (int repetition=1; repetition<=8; repetition++) {
            String label="full-inventory repeat " + repetition + ": ";
            command(label+"start", "quiet_below_forge_trial owned_close_begin", "owned_close_begin");
            delay(label+"wait for result",40);
            command(label+"committed result", "quiet_below_forge_trial owned_close_ready", "owned_close_ready");
            step(label+"close result", () -> ((ItemEnchantingScreen) client.currentScreen).close(), () -> client.currentScreen==null, 5_000);
            command(label+"exact recovery", "quiet_below_forge_trial owned_close_check", "owned_close_check");
        }
'''
    source=source.replace(anchor,repetitions+anchor)
    directory=root/'release-driver';directory.mkdir();classes=directory/'classes';classes.mkdir()
    java=directory/'ReleaseForgeSmoke.java';java.write_text(source,encoding='utf-8')
    entry=directory/'ReleaseDriver.java';entry.write_text('package io.github.chronicle.testing.release; public final class ReleaseDriver implements net.fabricmc.api.ClientModInitializer { public void onInitializeClient() { ReleaseForgeSmoke.register(); } }',encoding='utf-8')
    trace=w.REPO/'testing/release/ScreenTrace.java'
    arguments=['-proc:none','-encoding','UTF-8','--release','21','-cp',os.pathsep.join(current['classpath']),'-d',str(classes),str(java),str(entry),str(trace)]
    argfile=directory/'javac.args';argfile.write_text('\n'.join(map(fabric._java_arg,arguments)),encoding='utf-8')
    subprocess.run([str(w.JAVA.with_name('javac.exe')),'@'+str(argfile)],env=w.env(),check=True)
    helper=directory/'release-forge-driver.jar'
    with zipfile.ZipFile(helper,'x') as z:
        z.writestr('fabric.mod.json',json.dumps(dict(schemaVersion=1,id='chronicle_release_test',version='1',environment='client',entrypoints={'client':['io.github.chronicle.testing.release.ReleaseDriver']},depends={'chronicle_core':'>=0.1.0-alpha.1','quiet_below':'0.1.0-beta'})))
        for p in classes.rglob('*.class'):
            name=p.relative_to(classes).as_posix();assert name.startswith('io/github/chronicle/testing/release/')
            if p.name!='ScreenTrace.class':z.write(p,name)
    with zipfile.ZipFile(root/'mods/release-screen-trace.jar','x') as z:
        z.writestr('fabric.mod.json',json.dumps(dict(schemaVersion=1,id='chronicle_release_trace',version='1',environment='server',entrypoints={'main':['io.github.chronicle.testing.release.ScreenTrace']},depends={'chronicle_core':'>=0.1.0-alpha.1'})))
        z.write(classes/'io/github/chronicle/testing/release/ScreenTrace.class','io/github/chronicle/testing/release/ScreenTrace.class')
    result=copy.deepcopy(launch);result['classpath'].append(str(helper))
    proof=root/'client-provenance.json';data=json.loads(proof.read_text(encoding='utf-8'));data['classpath']=result['classpath'];data['testDriver']=dict(jarSHA256=w.digest(helper),sourceSHA256=w.digest(java),originalSourceSHA256=w.digest(original),omittedCheck=omitted.strip(),reason='The legacy transaction command includes forbidden paused container opens without Building Mode; its entire server matrix is omitted. Separate active native entry/offering/recipe/escrow/render/restart flows remain. No product implementation is replaced.')
    proof.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
    return result

def ecology(current,artifacts,reports,worlds,checks):
    shared=stage(artifacts,worlds,'ecology')
    for phase in ('initial','restart','vitality'):
        root=stage(artifacts,worlds,'vitality') if phase=='vitality' else shared
        launch=release_launch(current,root,artifacts);directory=reports/phase;directory.mkdir()
        if phase=='restart':
            print('Release compatibility ecology paused restart',flush=True)
            paused_restart(root,directory,checks)
            continue
        server=log=None;clients=[]
        print('Release compatibility ecology '+phase,flush=True)
        try:
            ecology_smoke._reserve_ports();logfile=directory/'server.log';log=logfile.open('w',encoding='utf-8')
            env=dict(w.env(),CHRONICLE_ECOLOGY_NATIVE_TRIAL='1')
            server=subprocess.Popen(w.server_command(),cwd=root,env=env,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True,encoding='utf-8')
            ecology_smoke._wait_ready(server,logfile)
            # The old probe selects Game setup without scrolling the newer
            # Advanced navigation. Use a viewport where every tab is visible.
            clients=[fabric._client(w,launch,root,directory,'ChronicleFabricA','ecology-'+phase,{'CHRONICLE_ECOLOGY_SMOKE':str(directory),'CHRONICLE_ECOLOGY_PHASE':phase,'CHRONICLE_MENU_WIDTH':'1280','CHRONICLE_MENU_HEIGHT':'960'})]
            outcome=directory/('ecology-'+phase+'.json');fabric._await_clients(clients,[outcome],timeout=360)
            checks.extend('Ecology '+phase+': '+c for c in json.loads(outcome.read_text(encoding='utf-8'))['checks'])
        finally:
            bargain_smoke._close_clients(clients)
            try:fabric._stop(server)
            finally:
                if log:log.close()

def run(sections=('ecology','forge','altar')):
    w.require_eula();w.validate_server_runtime();w.validate_managed_mod_names()
    assert w.digest(QB)==QB_HASH
    artifacts=[CORE,QB];before={str(p):w.digest(p) for p in artifacts}
    assert [w.validate_mod_target(p)['version'] for p in artifacts]==['0.2.0','0.1.0-beta']
    current=json.loads((w.REPO/'campaigns/quiet-below/build/smoke/client-launch.json').read_text(encoding='utf-8'))
    from datetime import datetime,timezone
    reports=w.REPORTS/('release-compat-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ'));reports.mkdir()
    report=dict(result='FAIL',artifacts=before,sections=list(sections),worlds={},checks=[],note='Original release JARs on server; same release inputs remapped by Fabric on client. Forge uses a test-only UI driver without the inapplicable paused-reload matrix. No human/map/remote voice acceptance.')
    try:
        if 'ecology' in sections:
            directory=reports/'ecology';directory.mkdir();ecology(current,artifacts,directory,report['worlds'],report['checks'])
        if 'forge' in sections:
            root=stage(artifacts,report['worlds'],'forge');launch=forge_driver(current,root,release_launch(current,root,artifacts))
            directory=reports/'forge';directory.mkdir();ordinary_client=fabric._client
            def client(*args):
                values=list(args);values[-1]={k.replace('CHRONICLE_FORGE_','CHRONICLE_RELEASE_FORGE_'):v for k,v in values[-1].items()}
                return ordinary_client(*values)
            for phase,roles in (('initial',('A','B')),('restart',('A',))):
                print('Release compatibility Forge '+phase,flush=True)
                with patch.object(fabric,'_client',side_effect=client):forge_smoke._run_phase(w,fabric,launch,root,directory,phase,roles)
                for role in roles:
                    result=json.loads((directory/('forge-'+phase+'-'+role+'.json')).read_text(encoding='utf-8'))
                    report['checks'].extend('Forge '+phase+' '+role+': '+c for c in result['checks'])
        if 'altar' not in sections:
            assert {str(p):w.digest(p) for p in artifacts}==before
            report['result']='PASS';return
        root=stage(artifacts,report['worlds'],'altar');temporary_altar_smoke._config(root)
        ops=root/'ops.json';people=json.loads(ops.read_text(encoding='utf-8'));people.append(dict(uuid=fabric.offline_uuid('ChronicleFabricB'),name='ChronicleFabricB',level=2,bypassesPlayerLimit=False));ops.write_text(json.dumps(people),encoding='utf-8')
        launch=release_launch(current,root,artifacts);directory=reports/'altar';directory.mkdir()
        print('Release compatibility Altar initial/reconnect/restart/ended',flush=True)
        # The released reconnect probe exercises HUD editing. Merge its pinned
        # optional mods with the release inputs, never overwrite addMods.
        with patch.object(bargain_smoke,'_optional_launch',side_effect=reconnect_hud_launch):
            bargain_smoke._run(w,fabric,None,launch,root,directory)
        for phase,role in (('initial','A'),('initial','B'),('reconnect','A'),('restart','A'),('ended','B')):
            result=json.loads((directory/('bargain-'+phase+'-'+role+'.json')).read_text(encoding='utf-8'))
            report['checks'].extend('Altar '+phase+' '+role+': '+c for c in result['checks'])
        assert {str(p):w.digest(p) for p in artifacts}==before
        report['result']='PASS';print('PASS release compatibility: '+str(reports),flush=True)
    except BaseException as failure:
        report['error']=str(failure);raise
    finally:
        (reports/'report.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')

if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--section',action='append',choices=('ecology','forge','altar'))
    args=parser.parse_args();run(tuple(args.section) if args.section else ('ecology','forge','altar'))
