"""Small authenticated Last Council review, separate from released and test worlds."""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import threading
import time
import uuid
import zipfile
import workspace as w
import scenario_authoring as safe
import client_defaults
from council_ballots_smoke import _fixture

LATEST=w.REPORTS/'council-review-latest.json'

def jars():
    props=dict(line.split('=',1) for line in (w.REPO/'gradle.properties').read_text(encoding='utf-8').splitlines() if '=' in line and not line.startswith('#'))
    version=props['mod_version'].strip()
    result=[w.REPO/'core/build/libs'/f'chronicle_core-{version}.jar',w.REPO/'campaigns/last-council/build/libs'/f'last_council-{version}.jar']
    for jar in result:safe.regular(jar);w.validate_mod_target(jar)
    return version,result

def prepare():
    w.require_eula();w.validate_server_runtime();safe.ports(25589,24469)
    access=safe.auth(w.SERVER)
    version,owned=jars()
    root=w.WORK/'reviews/last-council'/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    root.mkdir(parents=True)
    for name in ('fabric-server-launch.jar','fabric-server-launcher.properties','server.jar','eula.txt','ops.json','whitelist.json'):
        shutil.copy2(safe.regular(w.SERVER/name),root/name)
    safe.copy_tree(w.SERVER/'libraries',root/'libraries')
    (root/'mods').mkdir()
    for mod in w.LOCK['mods']:shutil.copy2(safe.regular(w.SERVER/'mods'/mod['filename']),root/'mods'/mod['filename'])
    for jar in owned:shutil.copy2(jar,root/'mods'/jar.name)
    settings={'server-ip':'127.0.0.1','server-port':'25589','online-mode':'true','white-list':'true','enforce-whitelist':'true',
        'enforce-secure-profile':'true','enable-rcon':'false','enable-query':'false','level-name':'world','level-type':'minecraft:flat',
        'gamemode':'survival','difficulty':'peaceful','spawn-protection':'0','max-players':'8','view-distance':'4','simulation-distance':'4',
        'motd':'Chronicle - Last Council review '+version,'sync-chunk-writes':'true'}
    (root/'server.properties').write_text(''.join(f'{k}={v}\n' for k,v in settings.items()),encoding='utf-8')
    voice=root/'config/voicechat';voice.mkdir(parents=True)
    (voice/'voicechat-server.properties').write_text('bind_address=127.0.0.1\nport=24469\n',encoding='utf-8')
    (root/'config/chronicle/scenario-selection.json').parent.mkdir(parents=True,exist_ok=True)
    (root/'config/chronicle/scenario-selection.json').write_text('{"schema":1,"scenarioId":"last_council:scenario"}\n',encoding='utf-8')
    _fixture(root)
    houses=json.loads((w.REPO/'campaigns/last-council/src/main/resources/data/last_council/chronicle/houses.json').read_text(encoding='utf-8'))['houses']
    ids=sorted(houses)[:3] if isinstance(houses,dict) else [h['id'] for h in houses][:3]
    ops=json.loads((root/'ops.json').read_text(encoding='utf-8'));owner=next(o for o in ops if o['name'].lower()=='schinderrenge')['uuid']
    owners=[str(uuid.uuid5(uuid.NAMESPACE_URL,'chronicle-review:placeholder-1')),str(uuid.uuid5(uuid.NAMESPACE_URL,'chronicle-review:placeholder-2'))]
    assignments={actor:{'owner':actor,'houseId':house,'councilMember':True,'candidate':True} for actor,house in zip(owners,ids)}
    chronicle=root/'world/chronicle/last-council'
    (chronicle/'chancellor.json').write_text(json.dumps({'schema':1,'revision':1,'chancellor':owner})+'\n',encoding='utf-8')
    (chronicle/'roster.json').write_text(json.dumps({'schema':1,'revision':1,'assignments':assignments})+'\n',encoding='utf-8')
    proceedings=chronicle/'proceedings.json';state=json.loads(proceedings.read_text(encoding='utf-8'));motions={}
    for title,body,visibility in [('The northern road','Provide aid to the northern villages.','PUBLIC'),('The treasury','Release funds from the treasury.','SECRET')]:
        identity=str(uuid.uuid4());motions[identity]={'id':identity,'title':title,'text':body,'proposer':owner,'proposerHouse':'','visibility':visibility,
                'threshold':'MAJORITY','hostPrepared':True,'sponsors':[],'status':'AGENDA','ballots':[],'notes':[]}
    state['state']['motions']=motions;proceedings.write_text(json.dumps(state)+'\n',encoding='utf-8')
    meta={'schema':1,'review':str(root),'version':version,'address':'127.0.0.1:25589','port':25589,'voicePort':24469,
        'mods':{p.name:w.digest(p) for p in (root/'mods').glob('*.jar')}}
    safe.atomic(root/'.chronicle-council-review.json',meta);safe.atomic(LATEST,meta)
    print('Prepared Council review:',root,flush=True)
    return root

def resolve():
    _,meta=safe.read_json(LATEST);root=safe.direct_review(meta['review'])
    _,saved=safe.read_json(root/'.chronicle-council-review.json')
    if saved!=meta:raise RuntimeError('Review manifest changed')
    safe.auth(root)
    props=safe.properties(root/'server.properties')
    if props.get('server-port')!='25589' or props.get('level-name')!='world':raise RuntimeError('Review endpoint changed')
    voice=safe.properties(root/'config/voicechat/voicechat-server.properties')
    if voice.get('bind_address')!='127.0.0.1' or voice.get('port')!='24469':raise RuntimeError('Review voice endpoint changed')
    actual={p.name:w.digest(safe.regular(p)) for p in (root/'mods').glob('*.jar')}
    if actual!=meta['mods']:raise RuntimeError('Review mods changed')
    return root,meta

def start():
    root,meta=resolve();w.check_stopped(root);safe.ports(meta['port'],meta['voicePort'])
    env={k:v for k,v in w.env().items() if not k.upper().startswith('CHRONICLE_')}
    process=subprocess.Popen(w.server_command(),cwd=root,env=env,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace',bufsize=1)
    marker=root/'.chronicle-process.json';safe.atomic(marker,{'pid':process.pid,'controller_pid':os.getpid()})
    ready=threading.Event()
    def output():
        with (root/'review-console.log').open('a',encoding='utf-8') as log:
            for line in process.stdout:
                log.write(line);log.flush();print(line,end='',flush=True)
                if 'Done (' in line:ready.set()
    def forward():
        for line in sys.stdin:
            if process.poll() is not None:break
            try:process.stdin.write(line);process.stdin.flush()
            except (OSError,ValueError):break
    threading.Thread(target=output,daemon=True).start();threading.Thread(target=forward,daemon=True).start()
    try:
        if not ready.wait(180) or process.poll() is not None:raise RuntimeError('Review server failed startup')
        prepared=root/'.council-platform-prepared'
        if not prepared.exists():
            process.stdin.write('forceload add -8 -8 8 8\n');process.stdin.flush();time.sleep(2)
            for command in ['gamerule spawnRadius 0','setworldspawn 0 65 0','fill -8 64 -8 8 64 8 minecraft:stone_bricks','setblock 0 65 4 minecraft:lectern','time set day','weather clear','forceload remove -8 -8 8 8','save-all flush']:
                process.stdin.write(command+'\n')
            process.stdin.flush();prepared.write_text('Review platform prepared\n',encoding='utf-8')
        print('Council review ready at '+meta['address']+'; enter stop to save and exit.',flush=True)
        code=process.wait()
        if code:raise RuntimeError(f'Review server exited {code}')
    finally:
        if process.poll() is None:
            process.stdin.write('stop\n');process.stdin.flush()
            try:process.wait(timeout=120)
            except subprocess.TimeoutExpired:print('Saving shutdown still pending; retained process marker.',file=sys.stderr)
        if process.poll() is not None:marker.unlink(missing_ok=True)

def pack():
    root,meta=resolve();version,owned=jars()
    if version!=meta['version'] or any(w.digest(p)!=meta['mods'].get(p.name) for p in owned):raise RuntimeError('Build and review do not match')
    files=[{'path':'mods/'+m['filename'],'hashes':{'sha1':m['sha1'],'sha512':m['sha512']},'env':{'client':'required','server':'required'},'downloads':[m['url']],'fileSize':m['size']} for m in w.LOCK['mods']]
    index={'formatVersion':1,'game':'minecraft','versionId':version+'+mc'+w.LOCK['minecraft'],'name':'Chronicle - The Last Council review',
        'summary':'Local Council development review','files':files,'dependencies':{'minecraft':w.LOCK['minecraft'],'fabric-loader':w.LOCK['loader']}}
    target=w.WORK/'client'/f'Chronicle-Last-Council-{version}.mrpack';target.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(target,'w',compression=zipfile.ZIP_DEFLATED) as zipped:
        zipped.writestr('modrinth.index.json',json.dumps(index,indent=2));zipped.writestr('client-overrides/options.txt',w.client_options())
        for name,contents in client_defaults.payloads(w.WORK,w.LOCK['minecraft_protocol']).items():zipped.writestr('client-overrides/'+name,contents)
        for jar in owned:zipped.write(jar,'overrides/mods/'+jar.name)
    with zipfile.ZipFile(target) as zipped:
        if zipped.testzip() is not None:raise RuntimeError('Client pack CRC failed')
        for jar in owned:
            import hashlib
            if hashlib.sha256(zipped.read('overrides/mods/'+jar.name)).hexdigest()!=w.digest(jar):raise RuntimeError('Client pack mod mismatch')
    print('Council review client pack:',target,flush=True)
    return target

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('action',choices=['prepare','start','pack']);args=parser.parse_args()
    {'prepare':prepare,'start':start,'pack':pack}[args.action]()
