"""Disposable real-client LC05 consent/physical interaction/restart checks. No existing world is edited."""
from __future__ import annotations
import hashlib,json,shutil,subprocess,time,uuid,ctypes
from ctypes import wintypes
from contextlib import contextmanager
from pathlib import Path
import fabric_smoke,menu_smoke
from council_ballots_smoke import _fixture

def offline(role):
    return str(uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:ChronicleFabric'+role).encode()).digest(),version=3))

@contextmanager
def deny_replace(path):
    """Deny replacement of one unchanged disposable ledger to exercise real Java I/O recovery."""
    path=Path(path).resolve();root=path.parents[3]
    if root.parent.name!='test-runs' or not (root/'.council-conflicts-fixture').is_file() or path.name!='conflicts.json':raise RuntimeError('Unsafe native lock target')
    kernel=ctypes.WinDLL('kernel32',use_last_error=True);kernel.CreateFileW.restype=wintypes.HANDLE;kernel.CreateFileW.argtypes=[wintypes.LPCWSTR,wintypes.DWORD,wintypes.DWORD,ctypes.c_void_p,wintypes.DWORD,wintypes.DWORD,wintypes.HANDLE];kernel.CloseHandle.argtypes=[wintypes.HANDLE]
    handle=kernel.CreateFileW(str(path),0x80000000,1,None,3,128,None)
    if handle==ctypes.c_void_p(-1).value:raise ctypes.WinError(ctypes.get_last_error())
    try:yield
    finally:kernel.CloseHandle(handle)

class Driver:
    def __init__(self,directory,clients):self.dir=directory;self.clients=clients;self.sequence={r:0 for r in 'ABCD'}
    def state(self,role):
        p=self.dir/(role+'-state.json')
        try:return json.loads(p.read_text(encoding='utf-8'))
        except (OSError,json.JSONDecodeError):return {}
    def wait(self,role,condition,why,timeout=45):
        until=time.monotonic()+timeout
        while time.monotonic()<until:
            for process,_ in self.clients:
                if process.poll() is not None:raise RuntimeError('Client exited while waiting for '+why)
            s=self.state(role)
            if s.get('error'):raise RuntimeError(role+': '+s['error']+' while '+why)
            if s and condition(s):return s
            time.sleep(.12)
        raise RuntimeError('Timeout '+role+' '+why+' '+json.dumps(self.state(role))[:1800])
    def do(self,role,action,**args):
        self.sequence[role]+=1;number=self.sequence[role];q=dict(id=number,action=action,**args)
        tmp=self.dir/(role+'-command.tmp');target=self.dir/(role+'-command.json');tmp.write_text(json.dumps(q),encoding='utf-8')
        for attempt in range(20):
            try:tmp.replace(target);break
            except PermissionError:
                if attempt==19:raise
                time.sleep(.05)
        return {} if action=='stop' else self.wait(role,lambda s:s.get('last')==number,action)
    def chat(self,role,text):return self.do(role,'chat',text=text)
    def treaty(self,role,verb,**args):return self.do(role,'treaty',verb=verb,**args)
    def conflict(self,role,verb,**args):return self.do(role,'conflict',verb=verb,**args)
    def detail(self,role):return self.state(role).get('treaty',{}).get('detail')
    def open_all(self):
        for role in 'ABCD':self.do(role,'lectern')
        for role in 'ABCD':self.wait(role,lambda s:s.get('treatyOpen') and len(s['treaty']['people'])==3,'all lectern attendees')
    def sign(self,roles):
        treaty=self.detail('B')['id']
        for role in 'A'+roles:
            self.treaty(role,'STATE',treaty=treaty)
            self.wait(role,lambda s:s.get('treatyOpen') and s['treaty']['detail'] is not None and s['treaty']['detail']['id']==treaty,'select exact agreement')
        for role in roles:
            self.wait(role,lambda s:s.get('treatyOpen') and s['treaty']['detail'] is not None and s['treaty']['detail']['canSign'],'own sign affordance')
            self.treaty(role,'SIGN')
        self.wait('A',lambda s:s['treaty']['detail'] is not None and all(p['signed'] for p in s['treaty']['detail']['parties']),'all signatures')
    def close_all(self):
        for role in 'ABCD':self.do(role,'close')
    def setup(self,phase):
        for role in 'ABCD':self.wait(role,lambda s:True,'connection',timeout=100)
        for command in ['fill -6 64 -6 6 64 6 minecraft:stone','setblock 0 65 2 minecraft:lectern','tp ChronicleFabricA 0 65 -1','tp ChronicleFabricB 0 65 0','tp ChronicleFabricC 1 65 0','tp ChronicleFabricD 2 65 0']:
            self.chat('A',command)
        if phase=='initial':
            self.chat('A','chronicle editing on')
            self.chat('A','chronicle council conflicts settings');self.wait('A',lambda s:s.get('conflictOpen') and s['conflict']['settings'] and s['conflict']['editable'],'operator settings');self.do('A','text',value='3');self.do('A','click',label='Save');self.wait('A',lambda s:s['conflict']['threshold']==3,'threshold saved');self.do('A','close')
            for activity in ('declared','joined','status_changed'):
                self.chat('A','chronicle_event_sources_trial arm "last_council:conflict_changed" "*" '+json.dumps(activity))
                self.wait('A',lambda s,a=activity:any('Event sources trial PASS arm last_council:conflict_changed * '+a in m for m in s.get('messages',[])),'source arm '+activity)
            self.chat('A','chronicle editing off')
            for role in 'BCD':self.chat(role,'chronicle players join')
            time.sleep(1);self.chat('A','chronicle session start')
        self.chat('A','item replace entity ChronicleFabricB hotbar.0 with last_council:challenge_token')
        time.sleep(1)
    def make_war(self,checks,private=False,authority_tests=False,fault_path=None):
        self.close_all();self.chat('A','tp ChronicleFabricC 1 65 0');self.chat('A','item replace entity ChronicleFabricB hotbar.0 with last_council:challenge_token');time.sleep(.5)
        self.do('B','challenge',target='C');v=self.wait('B',lambda s:s.get('conflictOpen') and s['conflict'].get('challenge') is not None,'physical token offer')['conflict'];assert bool(v['challenge']['privateWarning'])==private
        if authority_tests:
            self.chat('A','tp ChronicleFabricC 20 65 0');self.conflict('B','DECLARE',reason='Out of reach');self.wait('B',lambda s:'nearby' in s['conflict']['message'],'distance revalidation');assert not self.state('B')['conflict']['rows'];self.chat('A','tp ChronicleFabricC 1 65 0');time.sleep(.5);self.do('B','challenge',target='C');self.wait('B',lambda s:s['conflict'].get('challenge') is not None,'new offer')
            self.chat('A','item replace entity ChronicleFabricB hotbar.0 with minecraft:air');self.conflict('B','DECLARE',reason='No token');self.wait('B',lambda s:'nearby' in s['conflict']['message'],'held-item revalidation');assert not self.state('B')['conflict']['rows'];self.chat('A','item replace entity ChronicleFabricB hotbar.0 with last_council:challenge_token');time.sleep(.5);self.do('B','challenge',target='C');self.wait('B',lambda s:s['conflict'].get('challenge') is not None,'refreshed physical offer');checks.append('Actual entity interaction revalidates distance and held token before declaration')
        self.do('B','text',value='The eastern road is closed.',edit=True);self.do('B','click',label='Review declaration');assert not self.state('B')['outside'];self.do('B','capture',name='challenge-confirm.png')
        if fault_path is not None:
            with deny_replace(fault_path):
                self.do('B','click',label='Confirm');self.wait('B',lambda s:s['conflict'].get('detail') is None and 'recovery' in s['conflict']['message'].lower(),'real I/O hold and publication redaction');time.sleep(.7)
            self.chat('A','chronicle council conflicts recover');self.wait('A',lambda s:any('pinned operations recovered' in m for m in s.get('messages',[])),'operator recovery acknowledgment');self.chat('B','chronicle council conflicts')
            self.wait('B',lambda s:s.get('conflictOpen') and s['conflict'].get('detail') is not None and s['conflict']['detail']['canAct'],'single-command exact recovery releases holds')
            checks.append('Real Windows replacement failure after private breach holds/redacts; one conflict recovery command completes pinned war and source evidence')
        else:self.do('B','click',label='Confirm')
        state=self.wait('B',lambda s:s.get('conflictOpen') and s['conflict'].get('challenge') is None and s['conflict'].get('detail') is not None and 'ACTIVE' in s['conflict']['detail']['status'],'war committed')['conflict'];id=state['detail']['id'];assert state['detail']['founder'];assert not any(b['label']=='Withdraw' for b in self.state('B')['buttons']);assert not self.state('B')['outside']
        self.conflict('B','WITHDRAW');self.wait('B',lambda s:'Founders' in s['conflict']['message'],'founder withdrawal refusal');assert self.state('B')['conflict']['detail']['revision']==1
        self.conflict('B','DECLARE',reason='Remote attempt');self.wait('B',lambda s:'expired' in s['conflict']['message'].lower(),'remote declaration refusal');assert self.state('B')['conflict']['detail']['id']==id
        self.chat('D','chronicle council conflicts');self.wait('D',lambda s:s.get('conflictOpen') and s['conflict'].get('detail') is not None,'public record');assert 'Private road pact' not in json.dumps(self.state('D')['conflict']);checks.append('Physical unilateral declaration, private warning, public history and founder/remote refusal verified')
        return id
    def agreement(self,id,purpose,roles,checks,stale=False):
        self.open_all();choice=next(c for c in self.state('B')['treaty']['conflicts'] if c['id']==id)
        if purpose=='PEACE':
            self.do('B','click',label='+ Create treaty');self.do('B','text',value='Private peace');self.do('B','text',value='Promises remain roleplayed.',edit=True);self.do('B','click',label='Type: General')
            if not any(b['label']=='Peace' for b in self.state('B')['buttons']):self.do('B','click',label='Down')
            self.do('B','click',label='Peace');self.do('B','click',label='Public');self.do('B','click',label='Required houses ('+str(len(roles))+')');assert not self.state('B')['outside'];self.do('B','click',label='Done');assert not self.state('B')['outside'];self.do('B','capture',name='peace-draft-compact.png');self.do('B','click',label='Submit');checks.append('Native agreement-type picker and automatic required-house form remain bounded in compact window')
        else:self.treaty('B','CREATE',title='Private '+purpose.lower(),terms='Promises remain roleplayed.',private=True,parties=['B'],purpose=purpose,conflict=id,membership=choice['membershipRevision'])
        self.wait('B',lambda s:s['treaty']['detail'] is not None and s['treaty']['detail']['purpose']==purpose,'linked draft');assert {p['owner'] for p in self.detail('B')['parties']}=={offline(r) for r in roles};self.sign(roles)
        if stale:
            self.treaty('B','EDIT',revision=1,title='Private peace',terms='Promises remain roleplayed, revised.',private=True,parties=['B'],purpose=purpose,conflict=id,membership=choice['membershipRevision']);self.wait('B',lambda s:s['treaty']['detail']['revision']==2 and not any(p['signed'] for p in s['treaty']['detail']['parties']),'edit invalidates signatures');self.sign(roles);self.treaty('A','SANCTIFY',revision=1);self.wait('A',lambda s:'changed' in s['treaty']['message'].lower(),'stale Chancellor consent refused');assert self.detail('A')['status']=='Draft';checks.append('Linked stale Chancellor request cannot sanctify newer re-signed terms')
        self.treaty('A','SANCTIFY');self.wait('B',lambda s:s['treaty']['detail']['status']=='Active','agreement sanctified');self.chat('B','chronicle council conflicts');status='ENDED' if purpose=='PEACE' else 'PAUSED';v=self.wait('B',lambda s:s.get('conflictOpen') and s['conflict'].get('detail') is not None and status in s['conflict']['detail']['status'],'conflict status')['conflict'];assert not v['detail']['canAct'];assert 'Private peace' not in json.dumps(v);checks.append('All current houses required; '+purpose+' records only '+status+' with private terms excluded')

def fixture(w):
    root=fabric_smoke._stage(w);(root/'.event-sources-fixture').write_text('Disposable source integration fixture\n',encoding='utf-8');(root/'.council-conflicts-fixture').write_text('Disposable conflicts native fixture\n',encoding='utf-8');_fixture(root)
    chronicle=root/'world/chronicle';council=chronicle/'last-council';a=json.loads((chronicle/'authoring.json').read_text(encoding='utf-8'));a['sites'].append({'dimension':'minecraft:overworld','revision':1,'type':'anchor','id':'treaty','position':{'x':0,'y':65,'z':2},'expectedBlock':'minecraft:lectern'});(chronicle/'authoring.json').write_text(json.dumps(a),encoding='utf-8');(council/'treaty-lecterns.json').write_text(json.dumps({'schema':1,'lecterns':['treaty']}),encoding='utf-8')
    houses=json.loads((w.REPO/'campaigns/last-council/src/main/resources/data/last_council/chronicle/houses.json').read_text(encoding='utf-8'))['houses'];houses=sorted(houses.values(),key=lambda h:(h['title'],h['id']));assignments={offline(r):{'owner':offline(r),'houseId':h['id'],'councilMember':True,'candidate':True} for r,h in zip('BCD',houses)};(council/'roster.json').write_text(json.dumps({'schema':1,'revision':1,'assignments':assignments}),encoding='utf-8');(council/'chancellor.json').write_text(json.dumps({'schema':1,'revision':1,'chancellor':offline('A')}),encoding='utf-8')
    config=root/'config/chronicle/last-council-complications.json';config.parent.mkdir(exist_ok=True,parents=True);config.write_text(json.dumps({'schema':1,'poolId':'last_council:complications','total':0,'meetings':[1],'earliestTicks':30000,'latestTicks':30000,'spacingTicks':0}),encoding='utf-8')
    for prefix in ('quiet_below-','last_council-'):
        for jar in (root/'mods').glob(prefix+'*.jar'):jar.unlink()
    props=dict(line.split('=',1) for line in (w.REPO/'gradle.properties').read_text(encoding='utf-8').splitlines() if '=' in line);jar=w.REPO/'campaigns/last-council/build/libs'/('last_council-'+props['mod_version'].strip()+'.jar');w.validate_mod_target(jar);shutil.copy2(jar,root/'mods'/jar.name)
    return root

def run():
    import workspace as w
    w.require_eula();w.validate_server_runtime();w.validate_managed_mod_names();launch=json.loads((w.REPO/'campaigns/last-council/build/smoke/client-launch.json').read_text(encoding='utf-8'));root=fixture(w);reports=w.REPORTS/('council-conflicts-'+root.name);reports.mkdir();report={'result':'FAIL','world':str(root),'checks':[]};server=log=None;clients=[]
    try:
        for phase in ('initial','restart','renewal'):
            directory=reports/phase;directory.mkdir();menu_smoke._reserve_ports();log=(directory/'server.log').open('w',encoding='utf-8');server=subprocess.Popen(w.server_command(),cwd=root,env=dict(w.env(),CHRONICLE_EVENT_SOURCES_TRIAL='true'),stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True,encoding='utf-8');menu_smoke._wait_ready(server,directory/'server.log')
            for role in 'ABCD':clients.append(fabric_smoke._client(w,launch,root,directory,'ChronicleFabric'+role,phase+'-'+role,{'CHRONICLE_CONFLICT_PROBE':str(directory),'CHRONICLE_CONFLICT_ROOT':str(root),'CHRONICLE_CONFLICT_ROLE':role,'CHRONICLE_MENU_WIDTH':'640','CHRONICLE_MENU_HEIGHT':'480'}))
            d=Driver(directory,clients);d.setup(phase)
            if phase=='initial':
                d.open_all();d.treaty('B','CREATE',title='Private road pact',terms='Hidden non-aggression promise.',private=True,parties=['B','C'],purpose='NON_AGGRESSION');d.wait('B',lambda s:s['treaty']['detail'] is not None,'NAP draft');d.sign('BC');d.treaty('A','SANCTIFY');d.wait('B',lambda s:s['treaty']['detail']['status']=='Active','NAP active');assert d.state('D')['treaty']['rows']==[]
                d.treaty('C','CREATE',title='Public pact between other houses',terms='Other parties only.',private=False,parties=['C','D'],purpose='NON_AGGRESSION');d.wait('C',lambda s:s['treaty']['detail'] is not None and s['treaty']['detail']['title']=='Public pact between other houses','unrelated public pact');other=d.detail('C')['id'];d.treaty('B','STATE',treaty=other);d.wait('B',lambda s:s['treaty']['detail']['id']==other,'visible public pact');d.sign('CD');d.treaty('A','SANCTIFY');d.wait('C',lambda s:s['treaty']['detail']['status']=='Active','public pact active')
                id=d.make_war(report['checks'],private=True,authority_tests=True,fault_path=root/'world/chronicle/last-council/conflicts.json');d.conflict('D','JOIN',side=d.state('D')['conflict']['detail']['first'][0]['id']);d.wait('B',lambda s:len(s['conflict']['detail']['first'])==2,'supporter joins');assert 'Civil war' in d.state('B')['conflict']['detail']['status'];report['checks'].append('Native settings threshold and supporter join produce Civil war without changing founder rights');d.agreement(id,'PEACE','BCD',report['checks'],stale=True)
                for activity in ('declared','joined','status_changed'):
                    d.chat('A','chronicle_event_sources_trial verify "last_council:conflict_changed" "*" '+json.dumps(activity))
                    d.wait('A',lambda s,a=activity:any('Event sources trial PASS verify last_council:conflict_changed * '+a in m for m in s.get('messages',[])),'source verification '+activity)
                report['checks'].append('Conflict declarations, support and neutral status changes drive actual Core source Events/flags')
                d.do('B','resize',width=1280,height=720);time.sleep(.7);assert not d.state('B')['outside'];d.do('B','capture',name='conflicts-normal.png');d.do('B','resize',width=640,height=480);time.sleep(.7);assert not d.state('B')['outside'];d.do('B','capture',name='conflicts-compact.png')
            elif phase=='restart':
                id=d.make_war(report['checks']);assert len(d.state('B')['conflict']['rows'])==2;d.agreement(id,'CEASEFIRE','BC',report['checks']);report['checks'].append('Saved peace and completed intent survived restart without replaying consent/history')
            else:
                d.make_war(report['checks']);assert len(d.state('B')['conflict']['rows'])==3;assert sum(r['status']=='PAUSED' for r in d.state('B')['conflict']['rows'])==1;report['checks'].append('Normal physical declaration after saved ceasefire creates a new conflict, preserving history')
            for role in 'ABCD':d.do(role,'stop')
            for process,handle in clients:process.wait(timeout=30);handle.close()
            clients.clear();fabric_smoke._stop(server);server=None;log.close();log=None
            if phase=='initial':
                council=root/'world/chronicle/last-council';conf=json.loads((council/'conflicts.json').read_text(encoding='utf-8'))['conflicts'][0]['record'];treaties=json.loads((council/'treaties.json').read_text(encoding='utf-8'))['treaties'];document=next(h[-1]['document'] for h in treaties if h[-1]['document']['id']==conf['agreement']['treatyId']);pending={'kind':'AGREEMENT','id':str(uuid.uuid4()),'actor':offline('A'),'initiator':None,'target':None,'reason':'','breaches':[],'document':document,'present':[offline(r) for r in 'ABCD'],'conflictRevision':conf['revision']-1};(council/'conflict-operations.json').write_text(json.dumps({'schema':1,'pending':pending}),encoding='utf-8')
            print('PASS conflict native phase '+phase,flush=True)
        report['result']='PASS';print('PASS Council conflicts native smoke: '+str(reports),flush=True)
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
