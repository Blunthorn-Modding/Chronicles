"""Four-client disposable LC06 native regression. Uses built jars; never builds or touches played worlds."""
from __future__ import annotations
import ctypes
from ctypes import wintypes
from contextlib import contextmanager
import json
from pathlib import Path
import subprocess
import time
import fabric_smoke, menu_smoke
from council_conflicts_smoke import Driver, fixture, offline
from council_foreign_smoke import NONE, packet, button, proceedings, open_ballot, cast, close_ballot, setup, expect_notice

@contextmanager
def deny_replace(path):
    path=Path(path).resolve(); root=path.parents[3]
    if root.parent.name!='test-runs' or not (root/'.council-conflicts-fixture').is_file() or not (root/'.council-rebellion-fixture').is_file() or path!=root/'world/chronicle/last-council/rebellions.json':
        raise RuntimeError('Unsafe rebellion journal lock target')
    kernel=ctypes.WinDLL('kernel32',use_last_error=True)
    kernel.CreateFileW.restype=wintypes.HANDLE
    kernel.CreateFileW.argtypes=[wintypes.LPCWSTR,wintypes.DWORD,wintypes.DWORD,ctypes.c_void_p,wintypes.DWORD,wintypes.DWORD,wintypes.HANDLE]
    kernel.CloseHandle.argtypes=[wintypes.HANDLE]
    handle=kernel.CreateFileW(str(path),0x80000000,1,None,3,128,None)
    if handle==ctypes.c_void_p(-1).value: raise ctypes.WinError(ctypes.get_last_error())
    try: yield
    finally: kernel.CloseHandle(handle)

def registry(d,role):
    d.chat(role,'chronicle council rebellions')
    return packet(d.wait(role,lambda s:s.get('screen')=='RebellionScreen' and bool(packet(s,'rebellion').get('token')),'public rebellion registry'),'rebellion')

def rebel(d,role,verb,**kwargs):
    before=packet(d.state(role),'rebellion').get('request')
    d.do(role,'rebellion',verb=verb,**kwargs)
    return packet(d.wait(role,lambda s:packet(s,'rebellion').get('request') not in (None,NONE,before),'rebellion '+verb),'rebellion')

def wait_button(d,role,label):
    return d.wait(role,lambda s:any(b['label']==label and b['active'] for b in s.get('buttons',[])),'button '+label)

def configure_access(d):
    # Geometry and point are ordinary authoring records created in the disposable fixture;
    # the policy binding itself is configured through the real Core menu.
    d.do('A','resize',width=1280,height=900);time.sleep(.5)
    d.chat('A','chronicle')
    d.wait('A',lambda s:s.get('screen')=='CoreMenuScreen','Core menu')
    for _ in range(12):
        if any(b['label']=='Regions' and b['active'] for b in d.state('A').get('buttons',[])):
            button(d,'A','Regions');break
        button(d,'A','v')
    else: raise RuntimeError('Regions navigation unavailable')
    d.do('A','coreSelect',value='council')
    time.sleep(.5); d.do('A','coreTrigger',label='Choose subtypes')
    wait_button(d,'A','[ ] Region access'); button(d,'A','[ ] Region access'); button(d,'A','Apply')
    d.wait('A',lambda s:s.get('screen')=='CoreMenuScreen','return from subtype picker')
    time.sleep(.5); d.do('A','coreTrigger',label='Choose outside Point')
    wait_button(d,'A','outside'); button(d,'A','outside'); button(d,'A','Add')
    d.wait('A',lambda s:s.get('screen')=='CoreMenuScreen','return from Point picker')
    time.sleep(.5); d.do('A','close')

def initial(d,root,checks):
    d.chat('A','fill -12 64 -12 12 64 12 minecraft:stone')
    for r in 'BCD':d.chat('A','tp ChronicleFabric'+r+' 9.5 65 0.5')
    configure_access(d)
    for source, target, activities in [('vote_completed','*',('passed','rejected')),('rebellion_changed','*',('declared','joined','reinstated'))]:
        for activity in activities:
            d.chat('A','chronicle_event_sources_trial arm '+json.dumps('last_council:'+source)+' '+json.dumps(target)+' '+json.dumps(activity))
            d.wait('A',lambda state,a=activity,t=source:any('Event sources trial PASS arm last_council:'+t+' * '+a in m for m in state.get('messages',[])),'political source arm')
    for r in 'BCD':d.chat('A','tp ChronicleFabric'+r+' 0 65 0')
    d.chat('A','chronicle editing off')
    for r in 'BCD': d.chat(r,'chronicle players join')
    time.sleep(1); d.chat('A','chronicle session start')
    d.chat('A','chronicle_council_sources_trial held_guard')
    d.wait('A',lambda state:any('Council sources trial PASS held_guard' in m for m in state.get('messages',[])),'ACTIVE held callback skips unavailable play and source services')
    d.chat('A','chronicle council proceedings')
    d.wait('A',lambda s:s.get('screen')=='CouncilBusinessScreen' and bool(packet(s,'proceedings')),'proceedings')
    proceedings(d,'A','START')
    v=proceedings(d,'A','PREPARE',title='Membership freeze',text='Test captured electorate.')
    ballot=open_ballot(d,v['detail']['id'])
    b=registry(d,'B'); assert not b['canDeclare'],b
    refused=rebel(d,'B','DECLARE',statement='Should be refused while voting.')
    assert refused['refusal'] and not refused['rows'],refused
    cast(d,ballot,'BC'); close_ballot(d,ballot)
    checks.append('Open ballot refuses membership change and preserves the captured electorate')
    d.chat('A','tp ChronicleFabricB 0 65 0')
    d.do('B','resize',width=1280,height=720); d.chat('B','chronicle council')
    d.wait('B',lambda s:s.get('screen')=='PersonalScreen','personal council screen')
    button(d,'B','My house'); button(d,'B','Rebellions')
    d.wait('B',lambda s:bool(packet(s,'rebellion').get('token')),'embedded rebellion registry')
    for width,height,name in ((1280,720,'personal-rebellions-normal.png'),(640,480,'personal-rebellions-compact.png')):
        d.do('B','resize',width=width,height=height);time.sleep(.5);assert not d.state('B')['outside'];d.do('B','capture',name=name)
    checks.append('Personal council My house opens embedded rebellions at normal and compact sizes')
    d.do('B','close');d.chat('B','chronicle council rebellions')
    d.wait('B',lambda s:packet(s,'rebellion').get('canDeclare'),'declaration enabled')
    for width,height,name in ((1280,720,'registry-normal.png'),(640,480,'registry-compact.png')):
        d.do('B','resize',width=width,height=height); time.sleep(.5); assert not d.state('B')['outside']; d.do('B','capture',name=name)
    button(d,'B','Declare...');d.do('B','text',value='The Council must hear our grievances.',edit=True);button(d,'B','Review...')
    assert not d.state('B')['outside'];d.do('B','capture',name='declaration-confirm-compact.png');button(d,'B','Cancel')
    assert d.state('B').get('editText')=='The Council must hear our grievances.'
    button(d,'B','Review...');button(d,'B','Confirm')
    b=packet(d.wait('B',lambda s:bool(packet(s,'rebellion').get('detail')) and packet(s,'rebellion').get('revision',0)>0,'declaration'),'rebellion')
    faction=b['detail']['id']; house_b=b['ownHouse']
    d.wait('B',lambda s:s.get('x',0)>5,'declaring representative ejected')
    c=registry(d,'C');d.do('C','rebellion',verb='STATE',faction=faction)
    d.wait('C',lambda s:(packet(s,'rebellion').get('detail') or {}).get('id')==faction,'select rebellion')
    button(d,'C','Join...');button(d,'C','Confirm')
    c=packet(d.wait('C',lambda s:len((packet(s,'rebellion').get('detail') or {}).get('houses',[]))==2,'join'),'rebellion');house_c=c['ownHouse']
    d.wait('C',lambda s:s.get('x',0)>5,'joining representative ejected')
    assert offline('B') in json.loads((root/'world/chronicle/play-runtime.json').read_text(encoding='utf-8'))['participants']
    checks.append('Native declaration and join publish faction, eject both Houses and preserve participation and public record')
    d.chat('A','gamemode creative ChronicleFabricB');d.chat('A','tp ChronicleFabricB 0 65 0')
    d.wait('B',lambda s:s.get('x',0)>5,'creative teleport denied')
    d.do('B','close');before_x=d.state('B')['x'];d.do('B','walk',yaw=90,ticks=50);time.sleep(3);after_x=d.state('B')['x'];assert 6<=after_x<before_x-1,(before_x,after_x)
    checks.append('Creative mode, teleport and physical entry do not bypass palace restriction')
    # All entities are tagged solely for this disposable fixture.
    d.chat('A','summon minecraft:armor_stand 0 65 0 {Tags:["lc_rebellion_camera"]}')
    d.chat('A','summon minecraft:oak_boat 0 65 0 {Tags:["lc_rebellion_inside_boat"]}')
    d.chat('A','gamemode spectator ChronicleFabricB')
    d.wait('B',lambda s:s.get('isSpectator'),'spectator enabled')
    d.chat('A','tp ChronicleFabricB 0 65 0');time.sleep(1)
    assert d.state('B')['x']>5,d.state('B')
    d.chat('A','spectate @e[tag=lc_rebellion_camera,limit=1] ChronicleFabricB');time.sleep(1)
    state=d.state('B');assert state['x']>5 and state['cameraTarget']==state['playerId'],state
    d.chat('A','gamemode creative ChronicleFabricB')
    d.wait('B',lambda s:not s.get('isSpectator'),'spectator disabled')
    d.chat('A','ride ChronicleFabricB mount @e[tag=lc_rebellion_inside_boat,limit=1]');time.sleep(1)
    state=d.state('B');assert state['x']>5 and not state['mounted'],state
    d.chat('A','summon minecraft:oak_boat 9 65 0 {Tags:["lc_rebellion_outside_boat"]}')
    d.chat('A','ride ChronicleFabricB mount @e[tag=lc_rebellion_outside_boat,limit=1]')
    d.wait('B',lambda s:s.get('mounted'),'outside boat mounted')
    d.chat('A','tp @e[tag=lc_rebellion_outside_boat,limit=1] 0 65 0');time.sleep(2)
    state=d.state('B');assert state['x']>5,state
    d.chat('A','ride ChronicleFabricB dismount')
    d.chat('A','kill @e[tag=lc_rebellion_outside_boat]');time.sleep(1)
    checks.append('Spectator camera and mounted entry cannot carry a rebel into the palace')
    d.chat('A','execute in minecraft:the_nether run tp ChronicleFabricB 0 80 0')
    d.wait('B',lambda s:s.get('dimension')=='minecraft:the_nether','outbound cross-dimension teleport')
    d.chat('A','execute in minecraft:overworld run tp ChronicleFabricB 0 65 0');time.sleep(2)
    state=d.state('B');assert state['dimension']!='minecraft:overworld' or state['x']>5,state
    d.chat('A','execute in minecraft:overworld run tp ChronicleFabricB 9.5 65 0.5')
    d.wait('B',lambda s:s.get('dimension')=='minecraft:overworld' and s.get('x',0)>5,'outside cross-dimension teleport')
    checks.append('Cross-dimension commands allow outbound and outside destinations but deny palace entry')
    d.chat('A','spawnpoint ChronicleFabricB 0 65 0');d.chat('A','kill ChronicleFabricB')
    d.wait('B',lambda s:s.get('health',1)<=0,'rebel death');d.do('B','respawn')
    d.wait('B',lambda s:s.get('health',0)>0 and s.get('x',0)>5,'rebel respawn outside')
    checks.append('Death with a palace spawn point respawns a rebel outside')
    d.chat('A','chronicle council proceedings');d.wait('A',lambda s:s.get('screen')=='CouncilBusinessScreen','proceedings again')
    d.do('A','resize',width=1280,height=720);time.sleep(.5)
    # One Chancellor action opens the real House picker and confirmation.
    button(d,'A','Allow visit...');d.wait('A',lambda s:s.get('screen')=='RebellionVisitPicker','visit picker')
    choices=d.state('A')['buttons']; founder=b['detail']['houses'][0];choice=next(x['label'] for x in choices if founder in x['label'])
    button(d,'A',choice);button(d,'A','Confirm')
    a=packet(d.wait('A',lambda s:any(v['house']==house_b and v['status']=='WAITING' for v in packet(s,'rebellion').get('visits',[])),'visit waiting'),'rebellion')
    d.chat('A','tp ChronicleFabricB 0 65 0');d.wait('B',lambda s:abs(s.get('x',99))<5,'visit entry')
    d.wait('A',lambda s:any(v['house']==house_b and v['status']=='INSIDE' for v in packet(s,'rebellion').get('visits',[])),'visit inside')
    right=proceedings(d,'A','PREPARE',title='Rebel sponsorship refusal',text='Only Council Houses may sponsor.')
    d.do('B','lectern',z=4);d.wait('B',lambda s:s.get('screen')=='CouncilBusinessScreen','rebel reads motion lectern')
    assert not packet(d.state('B'),'proceedings')['canPropose']
    assert 'seat' in expect_notice(d,'B','PROPOSE',title='No seat',text='Forbidden')['message'].lower()
    assert 'seat' in expect_notice(d,'B','SPONSOR',reference=right['detail']['id'])['message'].lower()
    checks.append('Supervised access never restores proposal or sponsorship rights')
    d.chat('A','tp ChronicleFabricB 9 65 0');d.wait('A',lambda s:any(v['house']==house_b and v['status']=='ENDED' for v in packet(s,'rebellion').get('visits',[])),'visit ended')
    d.chat('A','tp ChronicleFabricB 0 65 0');d.wait('B',lambda s:s.get('x',0)>5,'second entry denied')
    checks.append('Chancellor picker grants one visit; entry, exit and attempted reentry enforce its lifetime')
    # The sole loyal voter D still forms the captured electorate; a zero-vote and void result leave membership unchanged.
    d.chat('A','chronicle council proceedings');d.wait('A',lambda s:s.get('screen')=='CouncilBusinessScreen','motion proceedings')
    def prepare(title):
        view=proceedings(d,'A','PREPARE',title=title,text='Reinstate named Houses.',category='REINSTATE_HOUSES',targets=[house_b,house_c])
        assert view['detail']['threshold']=='TWO_THIRDS',view
        return view['detail']['id']
    failed=prepare('Failed reinstatement');ballot=open_ballot(d,failed);close_ballot(d,ballot)
    void=prepare('Void reinstatement');ballot=open_ballot(d,void);proceedings(d,'A','VOID_BALLOT',reference=ballot,text='Fixture void')
    registry(d,'D');assert len(packet(d.state('D'),'rebellion')['detail']['houses'])==2
    # Exercise named multiselect in the actual draft at both UI sizes.
    d.chat('A','chronicle council proceedings');d.wait('A',lambda s:s.get('screen')=='CouncilBusinessScreen','draft proceedings')
    candidates=packet(d.state('A'),'proceedings')['rebelHouses']; titles={h['houseId']:h['title'] for h in candidates}
    button(d,'A','Prepare motion...');d.do('A','text',value='Passed reinstatement');d.do('A','text',value='Reinstate the named Houses.',edit=True)
    button(d,'A','Category: General');button(d,'A','Reinstate Houses')
    button(d,'A','Houses: 0 selected...');button(d,'A','[ ] '+titles[house_b])
    button(d,'A','Houses: 1 selected...');button(d,'A','[ ] '+titles[house_c])
    for width,height,name in ((1280,720,'reinstatement-normal.png'),(640,480,'reinstatement-compact.png')):
        d.do('A','resize',width=width,height=height);time.sleep(.5);assert not d.state('A')['outside'];d.do('A','capture',name=name)
    button(d,'A','Submit')
    view=packet(d.wait('A',lambda s:(packet(s,'proceedings').get('detail') or {}).get('title')=='Passed reinstatement','native reinstatement motion'),'proceedings')
    assert view['detail']['threshold']=='TWO_THIRDS',view
    motion=view['detail']['id'];ballot=open_ballot(d,motion);cast(d,ballot,'D')
    journal=root/'world/chronicle/last-council/rebellions.json'
    with deny_replace(journal):
        d.do('A','proceedings',verb='CLOSE_BALLOT',reference=ballot)
        d.wait('A',lambda s:'recover' in json.dumps(s.get('notice',{})).lower() or 'recover' in json.dumps(s.get('proceedings',{})).lower(),'locked journal hold')
    d.chat('A','chronicle council recover')
    d.wait('A',lambda s:any('pinned operations recovered' in m for m in s.get('messages',[])),'journal recovery')
    registry(d,'D');detail=packet(d.state('D'),'rebellion')['detail'];assert not detail['houses'],detail
    d.chat('A','tp ChronicleFabricB 0 65 0');d.wait('B',lambda s:abs(s.get('x',99))<5,'reinstated access')
    checks.append('Failed and void ballots have no effect; two-thirds reinstatement recovers exact locked journal effect and restores access')
    for source, activities in [('vote_completed',('passed','rejected')),('rebellion_changed',('declared','joined','reinstated'))]:
        for activity in activities:
            d.chat('A','chronicle_event_sources_trial verify '+json.dumps('last_council:'+source)+' "*" '+json.dumps(activity))
            d.wait('A',lambda state,a=activity,t=source:any('Event sources trial PASS verify last_council:'+t+' * '+a in m for m in state.get('messages',[])),'political source Event/flag')
    d.chat('A','chronicle_council_sources_trial snapshot')
    d.wait('A',lambda state:any('Council sources trial PASS snapshot' in m for m in state.get('messages',[])),'source snapshot')
    checks.append('Actual completed votes and rebellion/join/multi-House reinstatement drive Core Events/flags, including interrupted parent recovery')
    return faction

def restart(d,faction,checks):
    v=registry(d,'D');d.do('D','rebellion',verb='STATE',faction=faction)
    v=packet(d.wait('D',lambda s:(packet(s,'rebellion').get('detail') or {}).get('id')==faction,'retained faction'),'rebellion')
    assert not v['detail']['houses'] and len(v['detail']['history'])>=4,v
    checks.append('Restart retains public declaration, joining and reinstatement history')

def prepare_inside_restart(d,checks):
    b=registry(d,'B');b=rebel(d,'B','DECLARE',statement='A second rebellion for restart checks.');faction=b['detail']['id'];house=b['ownHouse']
    registry(d,'C');rebel(d,'C','JOIN',faction=faction)
    registry(d,'A');rebel(d,'A','ALLOW_VISIT',house=house)
    d.chat('A','tp ChronicleFabricB 0 65 0')
    d.wait('A',lambda s:any(v['house']==house and v['status']=='INSIDE' for v in packet(s,'rebellion').get('visits',[])),'visit inside before restart')
    for role in 'ABCD':d.do(role,'close')
    checks.append('Saved an in-progress visit and a separate outside rebel for admission checks')
    return faction,house

def check_inside_restart(d,house,checks):
    registry(d,'A')
    d.wait('A',lambda s:any(v['house']==house and v['status']=='INSIDE' for v in packet(s,'rebellion').get('visits',[])),'inside visit survived restart')
    assert abs(d.state('B')['x'])<5,d.state('B')
    d.chat('A','tp ChronicleFabricC 0 65 0');time.sleep(1);assert d.state('C')['x']>5,d.state('C')
    checks.append('Restart admits the existing visitor but keeps the other rebel outside')

def move_stopped_visitor_outside(w,root):
    # Models playerdata saved outside while the older visit journal still says INSIDE.
    import sys
    sys.path.insert(0,str(w.TOOLS/'map-audit-deps'))
    try:import nbtlib
    finally:sys.path.pop(0)
    w.check_stopped(root)
    file=root/'world/playerdata'/(offline('B')+'.dat')
    data=nbtlib.load(file);data['Pos']=nbtlib.List[nbtlib.Double]([9.5,65.0,0.5]);data.save()

def check_outside_restart(d,house,checks):
    registry(d,'A')
    d.wait('A',lambda s:any(v['house']==house and v['status']=='ENDED' for v in packet(s,'rebellion').get('visits',[])),'outside saved position ends inside grant')
    d.chat('A','tp ChronicleFabricB 0 65 0');time.sleep(1);assert d.state('B')['x']>5,d.state('B')
    checks.append('Restored outside playerdata ends a persisted INSIDE grant before reentry')

def run():
    import workspace as w
    w.require_eula();w.validate_server_runtime();w.validate_managed_mod_names()
    launch=json.loads((w.REPO/'campaigns/last-council/build/smoke/client-launch.json').read_text(encoding='utf-8'))
    root=fixture(w);(root/'.council-rebellion-fixture').write_text('Disposable rebellion fixture\n',encoding='utf-8')
    authoring=root/'world/chronicle/authoring.json';data=json.loads(authoring.read_text(encoding='utf-8'))
    data['sites'].append({'type':'location','id':'outside','dimension':'minecraft:overworld','revision':1,'x':9.5,'y':65.0,'z':0.5,'yaw':0.0,'pitch':0.0})
    authoring.write_text(json.dumps(data)+'\n',encoding='utf-8')
    (root/'.event-sources-fixture').write_text('Disposable source fixture\n',encoding='utf-8');(root/'.political-sources-fixture').write_text('Disposable political source fixture\n',encoding='utf-8')
    reports=w.REPORTS/('council-rebellion-'+root.name);reports.mkdir();report={'result':'FAIL','world':str(root),'checks':[]}
    server=log=None;clients=[]
    try:
        for phase in ('initial','restart','inside-restart','outside-restart'):
            directory=reports/phase;directory.mkdir();menu_smoke._reserve_ports();log=(directory/'server.log').open('w',encoding='utf-8')
            server=subprocess.Popen(w.server_command(),cwd=root,env=dict(w.env(),CHRONICLE_EVENT_SOURCES_TRIAL='true',CHRONICLE_POLITICAL_SOURCES_TRIAL='true'),stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True,encoding='utf-8')
            menu_smoke._wait_ready(server,directory/'server.log')
            for role in 'ABCD':clients.append(fabric_smoke._client(w,launch,root,directory,'ChronicleFabric'+role,phase+'-rebellion-'+role,{'CHRONICLE_CONFLICT_PROBE':str(directory),'CHRONICLE_CONFLICT_ROOT':str(root),'CHRONICLE_CONFLICT_ROLE':role,'CHRONICLE_MENU_WIDTH':'640','CHRONICLE_MENU_HEIGHT':'480'}))
            d=Driver(directory,clients)
            if phase in ('initial','restart'):setup(d,phase=='initial')
            else:
                for role in 'ABCD':d.wait(role,lambda s:True,'reconnection',timeout=100)
            if phase=='restart':
                d.chat('A','chronicle_council_sources_trial unchanged');d.wait('A',lambda state:any('Council sources trial PASS unchanged' in m for m in state.get('messages',[])),'no political source restart replay')
            if phase=='initial':faction=initial(d,root,report['checks'])
            elif phase=='restart':
                restart(d,faction,report['checks']);visit_faction,visit_house=prepare_inside_restart(d,report['checks'])
            elif phase=='inside-restart':check_inside_restart(d,visit_house,report['checks'])
            else:check_outside_restart(d,visit_house,report['checks'])
            for role in 'ABCD':d.do(role,'stop')
            for process,handle in clients:process.wait(timeout=30);handle.close()
            clients.clear();fabric_smoke._stop(server);server=None;log.close();log=None
            if phase=='inside-restart':move_stopped_visitor_outside(w,root)
        report['result']='PASS';print('PASS rebellion native: '+str(reports),flush=True)
    except BaseException as exc:
        report['error']=str(exc);raise
    finally:
        for process,handle in clients:
            if process.poll() is None:
                process.terminate()
                try:process.wait(timeout=15)
                except subprocess.TimeoutExpired:process.kill();process.wait(timeout=15)
            handle.close()
        try:fabric_smoke._stop(server)
        finally:
            if log:log.close()
            (reports/'report.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')

if __name__=='__main__':run()
