"""Authenticated crop comparison in a separate disposable review; existing ecology map is retained."""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import ecology_review as ecology
import workspace as w

ROOTS=w.WORK/'reviews/crops'
REPORT=w.REPORTS/'crop-review-latest.json'
REQUIRED=dict(ecology.REQUIRED, **{'server-port':'25586'})


def snapshot(report):
    if report.get('result')!='PASS' or not {'crops','crops_restart'}.issubset(report.get('phases',[])):
        raise RuntimeError('Run successful crop and crop restart trials before preparing this review')
    root=ecology.direct(Path(report['crops_world']),w.WORK/'test-runs');w.check_stopped(root)
    source=root/'review-world'
    if not (source/'level.dat').is_file():raise RuntimeError('Crop review snapshot is missing')
    if json.loads((source/'chronicle/campaign-state.json').read_text())['phase']!='PAUSED':raise RuntimeError('Crop review must be paused')
    rules={'enabled':True,'seconds':10};cleanup=False
    for line in (source/'quiet-below/ecology.jsonl').read_text().splitlines():
        entry=json.loads(line)
        if entry['type']=='CROP_RULES':rules=entry['payload']
        if entry['type']=='CLEANUP_MODE':cleanup=entry['payload']['enabled']
    if rules!={'enabled':True,'seconds':10} or cleanup:raise RuntimeError('Review requires default crop tuning and cleanup off')
    return source


def prepare():
    w.require_eula();w.validate_server_runtime();w.validate_managed_mod_names()
    source=snapshot(json.loads((w.REPORTS/'ecology-smoke-latest.json').read_text()))
    root=ecology.direct(ROOTS/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ'),ROOTS);root.mkdir(parents=True)
    for name in ('fabric-server-launch.jar','fabric-server-launcher.properties','server.jar','eula.txt','ops.json','whitelist.json'):
        shutil.copy2(w.SERVER/name,root/name)
    for name in ('libraries','mods'):shutil.copytree(w.SERVER/name,root/name)
    for prefix in ('chronicle_core-','quiet_below-'):
        for old in (root/'mods').glob(prefix+'*.jar'):old.unlink()  # Only copied jars in the new runtime.
    jars=w.artifacts()
    for jar in jars:w.validate_mod_target(jar);shutil.copy2(jar,root/'mods'/jar.name)
    shutil.copytree(source,root/'world')
    properties=dict(REQUIRED, **{'gamemode':'creative','difficulty':'normal','spawn-protection':'0','max-players':'12',
        'view-distance':'6','simulation-distance':'4','sync-chunk-writes':'true','pause-when-empty-seconds':'60',
        'motd':'Chronicle - isolated living sculk crop review'})
    (root/'server.properties').write_text('\n'.join(k+'='+v for k,v in properties.items())+'\n',encoding='utf-8')
    voice=root/'config/voicechat';voice.mkdir(parents=True)
    (voice/'voicechat-server.properties').write_text('bind_address=127.0.0.1\nport=24466\n',encoding='utf-8')
    manifest={'schema':1,'review':str(root),'source':str(source),'minecraft':w.LOCK['minecraft'],
        'mods':{jar.name:w.digest(jar) for jar in jars},'port':25586}
    (root/'.chronicle-crop-review.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
    REPORT.write_text(json.dumps(manifest,indent=2),encoding='utf-8');print('Prepared authenticated crop review:',root,flush=True)
    return root


def resolve():
    manifest=json.loads(REPORT.read_text());root=ecology.direct(Path(manifest['review']),ROOTS)
    if json.loads((root/'.chronicle-crop-review.json').read_text())!=manifest or manifest['minecraft']!=w.LOCK['minecraft']:
        raise RuntimeError('Crop review manifest mismatch')
    def props(path):return dict(line.split('=',1) for line in path.read_text().splitlines() if line and not line.startswith(('#','!')) and '=' in line)
    if any(props(root/'server.properties').get(k)!=v for k,v in REQUIRED.items()):raise RuntimeError('Crop review authentication/binding/world settings changed')
    voice=props(root/'config/voicechat/voicechat-server.properties')
    if voice.get('bind_address')!='127.0.0.1' or voice.get('port')!='24466':raise RuntimeError('Crop voice binding changed')
    for name,sha in manifest['mods'].items():
        if Path(name).name!=name or w.digest(root/'mods'/name)!=sha:raise RuntimeError('Crop review artifact changed')
    return root


def start():
    root=resolve();w.check_stopped(root)
    for kind,port in ((socket.SOCK_STREAM,25586),(socket.SOCK_DGRAM,24466)):
        with socket.socket(socket.AF_INET,kind) as probe:probe.bind(('127.0.0.1',port))
    marker=root/'.chronicle-process.json';process=None
    with marker.open('x') as out:json.dump({'controller_pid':os.getpid(),'state':'starting'},out)
    try:
        process=subprocess.Popen(w.server_command(),cwd=root,env=w.env())
        marker.write_text(json.dumps({'controller_pid':os.getpid(),'pid':process.pid}))
        try:result=process.wait()
        except KeyboardInterrupt:result=process.wait(timeout=90)
        if result:raise RuntimeError('Crop review exited '+str(result))
    finally:
        if process is None or process.poll() is not None:marker.unlink(missing_ok=True)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('action',choices=['prepare','start'])
    {'prepare':prepare,'start':start}[parser.parse_args().action]()
