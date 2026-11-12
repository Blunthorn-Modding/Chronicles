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

    def phase(root,name,width=640,height=480,expected_pools=1):
        nonlocal server,log,clients
        menu_smoke._reserve_ports()
        log=(reports/(name+'-server.log')).open('w',encoding='utf-8')
        server=subprocess.Popen(w.server_command(),cwd=root,env=w.env(),stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True,encoding='utf-8')
        menu_smoke._wait_ready(server,reports/(name+'-server.log'))
        output=reports/(name+'.json')
        clients=[fabric_smoke._client(w,launch,root,reports,'ChronicleFabricA',name,{
            'CHRONICLE_SCENARIO_SMOKE':str(output),'CHRONICLE_SCENARIO_MODE':'portable',
            'CHRONICLE_ROLES_SERVER_DIR':str(root),'CHRONICLE_MENU_WIDTH':str(width),'CHRONICLE_MENU_HEIGHT':str(height),
            'CHRONICLE_SCENARIO_POOLS':str(expected_pools),'CHRONICLE_SCENARIO_EXTENSIONS':'3'})]
        fabric_smoke._await_clients(clients,[output],timeout=160)
        for _,handle in clients:handle.close()
        clients=[];fabric_smoke._stop(server);server=None;log.close();log=None
        result['checks'].extend(name+': '+c for c in json.loads(output.read_text(encoding='utf-8'))['checks'])
        files=[*(root/'config/chronicle/scenario-exports').glob('*.json'),*(root/'config/chronicle/scenario-content').glob('*/exports/*.json')]
        # Restart may create a second digest after deliberately changing reusable settings.
        return max(files,key=lambda p:p.stat().st_mtime_ns)

    def document(path):
        value=json.loads(path.read_text(encoding='utf-8'))
        return value.get('content',value)

    def house_fields(value):
        return {k:v for k,v in value.items() if k!='note'}

    def refusal(root,label,file):
        nonlocal server,log
        before=file.read_bytes();file.unlink()
        menu_smoke._reserve_ports();path=reports/(label+'-server.log');log=path.open('w',encoding='utf-8')
        server=subprocess.Popen(w.server_command(),cwd=root,env=w.env(),stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True,encoding='utf-8')
        try:
            menu_smoke._wait_ready(server,path)
        except RuntimeError:
            text=path.read_text(encoding='utf-8')
            assert 'missing; restore' in text.lower(),text[-8000:]
            assert not file.exists()
        else:
            raise RuntimeError('Missing initialized configuration was silently reseeded')
        finally:
            fabric_smoke._stop(server);server=None;log.close();log=None
            file.write_bytes(before)
        result['checks'].append(label+': initialized missing file refuses startup without reseeding')

    pool_id='last_council:portable_test'
    settings={'schema':1,'poolId':pool_id,'total':1,'meetings':[2,3],
              'earliestTicks':2400,'latestTicks':4800,'spacingTicks':1200}
    pools={'schema':1,'pools':[{'id':pool_id,'label':'Portable Council pool','entries':[
        {'eventId':'last_council:broken_seal','weight':7},{'eventId':'last_council:missing_ledger','weight':3}]}]}
    houses=json.loads((w.REPO/'campaigns/last-council/src/main/resources/data/last_council/chronicle/houses.json').read_text(encoding='utf-8'))
    for house in houses['houses'].values():house['description']+='\n'+'Portable archive. '*100
    for entry in houses['secrets']:entry['text']+='\n'+'Archive notes. '*250
    assert len(json.dumps(houses).encode())>65536
    try:
        source=fresh();config=source/'config/chronicle'
        banquet={'schema':1,'enabled':True,'onsetMinutes':3,'durationMinutes':11}
        (config/'last-council').mkdir()
        (config/'last-council/houses.json').write_text(json.dumps(houses)+'\n',encoding='utf-8')
        (config/'scenario-selection.json').write_text('{"schema":1,"scenarioId":"last_council:scenario"}\n',encoding='utf-8')
        (config/'event-pools.json').write_text(json.dumps(pools)+'\n',encoding='utf-8')
        (config/'last-council-complications.json').write_text(json.dumps(settings)+'\n',encoding='utf-8')
        (config/'last-council-banquet.json').write_text(json.dumps(banquet)+'\n',encoding='utf-8')
        # Real valid private schedule differs from the reusable settings. It must never be captured.
        private=source/'world/chronicle/last-council';private.mkdir(parents=True)
        secret=str(uuid.uuid4())
        plan={'schemaVersion':1,'state':{'experience':secret,
            'config':{'poolId':'last_council:complications','total':2,'meetings':[1,2,3],'earliestTicks':6000,'latestTicks':30000,'spacingTicks':6000},
            'slots':[{'ordinal':0,'meeting':1,'target':6000,'phase':'WAITING'},{'ordinal':1,'meeting':3,'target':12000,'phase':'WAITING'}],
            'meetingTicks':[0]*64,'councilTicks':0,'lastDelivery':-1,'lastSharedTick':-1,'currentMeeting':0,'advancing':False,'endReason':''}}
        (private/'complications.json').write_text(json.dumps(plan)+'\n',encoding='utf-8')
        owner=fabric_smoke.offline_uuid('ChronicleFabricA');house=next(iter(houses['houses']))
        cast={'schema':1,'castId':secret,'rosterRevision':1,'seed':73971932736431,'papers':{owner:{'houseId':house,'secrets':['PRIVATE-COMMITTED-'+secret],'goals':['PRIVATE-GOAL-'+secret],'knownSecrets':[]}}}
        roster={'schema':1,'revision':1,'assignments':{owner:{'owner':owner,'houseId':house,'councilMember':True,'candidate':False}}}
        (private/'roster.json').write_text(json.dumps(roster)+'\n',encoding='utf-8')
        issued={'id':secret,'kind':'DRINK','issuer':owner,'poisoner':None,'usedBy':None,'useRequest':None}
        operation={'id':str(uuid.uuid4()),'actor':owner,'type':'ISSUE','inputToken':secret,'secondToken':None,'issueKind':'DRINK','timing':None,'repeatPolicy':None}
        (private/'banquet.json').write_text(json.dumps({'schema':1,'revision':1,'supplies':[issued],'illnesses':[],'operations':[operation]})+'\n',encoding='utf-8')
        (private/'banquet-inventory.json').write_text('{"schema":1,"pending":null,"receipts":[]}\n',encoding='utf-8')
        (private/'banquet-world-id').write_bytes((str(uuid.uuid4())+'\n').encode('ascii'))
        (private/'cast.json').write_text(json.dumps(cast)+'\n',encoding='utf-8')
        cast_before=(private/'cast.json').read_bytes();roster_before=(private/'roster.json').read_bytes()
        exported=phase(source,'source',1280,720)
        content=json.loads(exported.read_text(encoding='utf-8'))
        assert content['schemaVersion']==5 and len(content['eventPools'])==1
        assert content['eventPools'][0]==pools['pools'][0]
        assert content['extensions']['last_council:complications']==settings
        assert content['extensions']['last_council:banquet']==banquet
        assert house_fields(content['extensions']['last_council:houses'])==house_fields(houses)
        assert secret not in exported.read_text(encoding='utf-8')
        assert '73971932736431' not in exported.read_text(encoding='utf-8')
        assert (private/'cast.json').read_bytes()==cast_before and (private/'roster.json').read_bytes()==roster_before
        assert 'slots' not in content and 'recipients' not in content
        result['checks'].append('export retains >64KiB House templates, weights/settings and excludes committed private papers, cast seed, roster and schedule')
        profile={'type':'chronicle:scenario_profile','schema':1,'identity':str(uuid.uuid4()),'title':'Portable Council',
                 'mechanics':['last_council:mechanics'],'content':dict(content,scenarioId='custom:portable_council')}
        imported=fresh();(imported/'config/chronicle/scenario-profile.json').write_text(json.dumps(profile)+'\n',encoding='utf-8')
        imported_export=phase(imported,'imported')
        catalogs=imported/'config/chronicle/profile-content'
        assert json.loads((catalogs/'event-pools.json').read_text(encoding='utf-8'))==pools
        assert json.loads((catalogs/'last-council-complications.json').read_text(encoding='utf-8'))==settings
        assert json.loads((catalogs/'last-council-banquet.json').read_text(encoding='utf-8'))==banquet
        assert house_fields(json.loads((catalogs/'last-council/houses.json').read_text(encoding='utf-8')))==house_fields(houses)
        assert not (imported/'world/chronicle/last-council/complications.json').exists()
        assert not (imported/'world/chronicle/last-council/cast.json').exists()
        round_trip=json.loads(imported_export.read_text(encoding='utf-8'))['content']
        assert round_trip['eventPools']==content['eventPools'] and round_trip['extensions']==content['extensions']
        result['checks'].append('fresh custom profile loads imported pools/settings once and starts without a private schedule')
        # Ordinary stopped-server configuration edits remain authoritative over profile seeds.
        pools['pools'][0]['entries'][0]['weight']=9;settings['total']=0
        banquet.update(enabled=False,durationMinutes=7)
        (catalogs/'last-council-banquet.json').write_text(json.dumps(banquet)+'\n',encoding='utf-8')
        houses['knowledgeChancePercent']=19
        houses['houses'][house]['title']='Edited portable House'
        (catalogs/'last-council/houses.json').write_text(json.dumps(houses)+'\n',encoding='utf-8')
        (catalogs/'event-pools.json').write_text(json.dumps(pools)+'\n',encoding='utf-8')
        (catalogs/'last-council-complications.json').write_text(json.dumps(settings)+'\n',encoding='utf-8')
        restarted=phase(imported,'restart')
        round_trip=json.loads(restarted.read_text(encoding='utf-8'))['content']
        assert round_trip['eventPools']==pools['pools'] and round_trip['extensions']['last_council:complications']==settings
        assert house_fields(round_trip['extensions']['last_council:houses'])==house_fields(houses)
        assert round_trip['extensions']['last_council:banquet']==banquet
        result['checks'].append('actual restart and re-export preserve edited catalog weights/disabled settings without reseeding')
        refusal(imported,'missing-settings',catalogs/'last-council-complications.json')
        refusal(imported,'missing-banquet',catalogs/'last-council-banquet.json')
        refusal(imported,'missing-houses',catalogs/'last-council/houses.json')
        # Empty custom authoring never inherits installed scenario defaults.
        empty_content=dict(content,scenarioId='custom:empty_council',events=[],eventPools=[],extensions={},flags={})
        empty_profile=dict(profile,identity=str(uuid.uuid4()),title='Empty Council',content=empty_content)
        empty=fresh();(empty/'config/chronicle/scenario-profile.json').write_text(json.dumps(empty_profile)+'\n',encoding='utf-8')
        empty_export=document(phase(empty,'empty',expected_pools=0))
        empty_houses=empty_export['extensions']['last_council:houses']
        assert empty_export['extensions']['last_council:banquet']=={'schema':1,'enabled':False,'onsetMinutes':2,'durationMinutes':8}
        assert not empty_houses['houses'] and not empty_houses['secrets'] and not empty_houses['goals']
        result['checks'].append('fresh empty authoring profile suppresses installed House/secret/goal defaults')
        # Emulate the dev.7 profile: generic marker exists, House config still uses its old normal path.
        empty_catalog=empty/'config/chronicle/profile-content'
        (empty_catalog/'last-council/houses.json').unlink()
        (empty_catalog/'last-council/houses-profile-v1.marker').unlink()
        (empty_catalog/'last-council-banquet.json').unlink()
        (empty_catalog/'last-council-banquet.initialized').unlink()
        legacy=empty/'config/chronicle/last-council/houses.json';legacy.parent.mkdir(parents=True,exist_ok=True)
        legacy.write_text(json.dumps(houses)+'\n',encoding='utf-8');legacy_before=legacy.read_bytes()
        migrated=document(phase(empty,'legacy',expected_pools=0))
        assert house_fields(migrated['extensions']['last_council:houses'])==house_fields(houses)
        assert migrated['extensions']['last_council:banquet']=={'schema':1,'enabled':False,'onsetMinutes':2,'durationMinutes':8}
        assert (empty_catalog/'last-council/houses.json').read_bytes()==legacy_before and legacy.read_bytes()==legacy_before
        result['checks'].append('old initialized profile migrates exact legacy House file once, preserves source and exports loaded content')
        result['result']='PASS';print('PASS portable Council content:',reports,flush=True)
    finally:
        for process,handle in clients:
            if process.poll() is None:process.terminate()
            handle.close()
        fabric_smoke._stop(server)
        if log:log.close()
        (reports/'report.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')


if __name__=='__main__':run()
