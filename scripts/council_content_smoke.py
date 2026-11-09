"""Disposable native reusable Council-content export, profile import and restart proof."""
from __future__ import annotations
import json
import shutil
import subprocess
import uuid
import fabric_smoke
import menu_smoke


def run():
    import workspace as w
    w.require_eula(); w.validate_server_runtime(); w.validate_managed_mod_names()
    launch=json.loads((w.REPO/'campaigns/last-council/build/smoke/client-launch.json').read_text(encoding='utf-8'))
    version=dict(line.split('=',1) for line in (w.REPO/'gradle.properties').read_text(encoding='utf-8').splitlines() if '=' in line and not line.startswith('#'))['mod_version'].strip()
    council=w.REPO/'campaigns/last-council/build/libs'/f'last_council-{version}.jar'
    reports=w.REPORTS/('council-content-'+__import__('datetime').datetime.now(__import__('datetime').timezone.utc).strftime('%Y%m%dT%H%M%S%fZ'))
    reports.mkdir();result={'result':'FAIL','worlds':[],'checks':[]}
    server=log=None;clients=[]

    def fresh():
        root=fabric_smoke._stage(w)
        (root/'.player-roles-fixture').write_text('Disposable portable Council content test\n',encoding='utf-8')
        (root/'.council-content-fixture').write_text('Disposable portable Council content test\n',encoding='utf-8')
        for jar in (root/'mods').glob('quiet_below-*.jar'):jar.unlink()
        shutil.copy2(council,root/'mods'/council.name)
        (root/'config/chronicle').mkdir(parents=True,exist_ok=True)
        result['worlds'].append(str(root));return root

    def phase(root,name,width=640,height=480):
        nonlocal server,log,clients
        menu_smoke._reserve_ports()
        log=(reports/(name+'-server.log')).open('w',encoding='utf-8')
        server=subprocess.Popen(w.server_command(),cwd=root,env=w.env(),stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True,encoding='utf-8')
        menu_smoke._wait_ready(server,reports/(name+'-server.log'))
        output=reports/(name+'.json')
        clients=[fabric_smoke._client(w,launch,root,reports,'ChronicleFabricA',name,{
            'CHRONICLE_SCENARIO_SMOKE':str(output),'CHRONICLE_SCENARIO_MODE':'portable',
            'CHRONICLE_ROLES_SERVER_DIR':str(root),'CHRONICLE_MENU_WIDTH':str(width),'CHRONICLE_MENU_HEIGHT':str(height)})]
        fabric_smoke._await_clients(clients,[output],timeout=160)
        for _,handle in clients:handle.close()
        clients=[];fabric_smoke._stop(server);server=None;log.close();log=None
        result['checks'].extend(name+': '+c for c in json.loads(output.read_text(encoding='utf-8'))['checks'])
        files=[*(root/'config/chronicle/scenario-exports').glob('*.json'),*(root/'config/chronicle/scenario-content').glob('*/exports/*.json')]
        # Restart may create a second digest after deliberately changing reusable settings.
        return max(files,key=lambda p:p.stat().st_mtime_ns)

    pool_id='last_council:portable_test'
    settings={'schema':1,'poolId':pool_id,'total':1,'meetings':[2,3],
              'earliestTicks':2400,'latestTicks':4800,'spacingTicks':1200}
    pools={'schema':1,'pools':[{'id':pool_id,'label':'Portable Council pool','entries':[
        {'eventId':'last_council:broken_seal','weight':7},{'eventId':'last_council:missing_ledger','weight':3}]}]}
    try:
        source=fresh();config=source/'config/chronicle'
        (config/'scenario-selection.json').write_text('{"schema":1,"scenarioId":"last_council:scenario"}\n',encoding='utf-8')
        (config/'event-pools.json').write_text(json.dumps(pools)+'\n',encoding='utf-8')
        (config/'last-council-complications.json').write_text(json.dumps(settings)+'\n',encoding='utf-8')
        # Real valid private schedule differs from the reusable settings. It must never be captured.
        private=source/'world/chronicle/last-council';private.mkdir(parents=True)
        secret=str(uuid.uuid4())
        plan={'schemaVersion':1,'state':{'experience':secret,
            'config':{'poolId':'last_council:complications','total':2,'meetings':[1,2,3],'earliestTicks':6000,'latestTicks':30000,'spacingTicks':6000},
            'slots':[{'ordinal':0,'meeting':1,'target':6000,'phase':'WAITING'},{'ordinal':1,'meeting':3,'target':12000,'phase':'WAITING'}],
            'meetingTicks':[0]*64,'councilTicks':0,'lastDelivery':-1,'lastSharedTick':-1,'currentMeeting':0,'advancing':False,'endReason':''}}
        (private/'complications.json').write_text(json.dumps(plan)+'\n',encoding='utf-8')
        exported=phase(source,'source',1280,720)
        content=json.loads(exported.read_text(encoding='utf-8'))
        assert content['schemaVersion']==5 and len(content['eventPools'])==1
        assert content['eventPools'][0]==pools['pools'][0]
        assert content['extensions']=={'last_council:complications':settings}
        assert secret not in exported.read_text(encoding='utf-8')
        assert 'slots' not in content and 'recipients' not in content
        result['checks'].append('export retains reusable pool weights/settings and excludes differing private schedule identity/allocation')
        profile={'type':'chronicle:scenario_profile','schema':1,'identity':str(uuid.uuid4()),'title':'Portable Council',
                 'mechanics':['last_council:mechanics'],'content':dict(content,scenarioId='custom:portable_council')}
        imported=fresh();(imported/'config/chronicle/scenario-profile.json').write_text(json.dumps(profile)+'\n',encoding='utf-8')
        imported_export=phase(imported,'imported')
        catalogs=imported/'config/chronicle/profile-content'
        assert json.loads((catalogs/'event-pools.json').read_text(encoding='utf-8'))==pools
        assert json.loads((catalogs/'last-council-complications.json').read_text(encoding='utf-8'))==settings
        assert not (imported/'world/chronicle/last-council/complications.json').exists()
        round_trip=json.loads(imported_export.read_text(encoding='utf-8'))['content']
        assert round_trip['eventPools']==content['eventPools'] and round_trip['extensions']==content['extensions']
        result['checks'].append('fresh custom profile loads imported pools/settings once and starts without a private schedule')
        # Ordinary stopped-server configuration edits remain authoritative over profile seeds.
        pools['pools'][0]['entries'][0]['weight']=9;settings['total']=0
        (catalogs/'event-pools.json').write_text(json.dumps(pools)+'\n',encoding='utf-8')
        (catalogs/'last-council-complications.json').write_text(json.dumps(settings)+'\n',encoding='utf-8')
        restarted=phase(imported,'restart')
        round_trip=json.loads(restarted.read_text(encoding='utf-8'))['content']
        assert round_trip['eventPools']==pools['pools'] and round_trip['extensions']=={'last_council:complications':settings}
        result['checks'].append('actual restart and re-export preserve edited catalog weights/disabled settings without reseeding')
        # Once initialized, a deleted settings file must fail startup, never recreate profile defaults.
        settings_file=catalogs/'last-council-complications.json'
        assert (imported/'.council-content-fixture').is_file()
        settings_file.unlink()
        menu_smoke._reserve_ports();missing_log=reports/'missing-settings-server.log'
        log=missing_log.open('w',encoding='utf-8')
        server=subprocess.Popen(w.server_command(),cwd=imported,env=w.env(),stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True,encoding='utf-8')
        try:
            menu_smoke._wait_ready(server,missing_log)
        except RuntimeError:
            assert 'Initialized Council settings are missing' in missing_log.read_text(encoding='utf-8')
            assert not settings_file.exists()
        else:
            raise RuntimeError('Missing initialized settings were silently reseeded')
        fabric_smoke._stop(server);server=None;log.close();log=None
        result['checks'].append('missing initialized Council settings refuse actual startup without recreating the file')
        result['result']='PASS';print('PASS portable Council content:',reports,flush=True)
    finally:
        for process,handle in clients:
            if process.poll() is None:process.terminate()
            handle.close()
        fabric_smoke._stop(server)
        if log:log.close()
        (reports/'report.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')


if __name__=='__main__':run()
