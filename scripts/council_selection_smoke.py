from __future__ import annotations
import hashlib, json, shutil, socket, subprocess, time, traceback
from datetime import datetime, timezone
from pathlib import Path
import fabric_smoke as f
import workspace as w

PORT=25577; VOICE=24457

def reserve():
    with socket.socket() as s: s.bind(('127.0.0.1',PORT))
    with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as s: s.bind(('127.0.0.1',VOICE))

def choose(root, selected):
    p=root/'config/chronicle/scenario-selection.json'
    if selected:
        p.parent.mkdir(parents=True,exist_ok=True)
        p.write_text(json.dumps({'schema':1,'scenarioId':selected}),encoding='utf-8')
    elif p.exists(): p.unlink()

def launch(root, label, reportdir, expect_ready):
    reserve(); path=reportdir/(label+'.log')
    with path.open('w',encoding='utf-8') as log:
        proc=subprocess.Popen(w.server_command(),cwd=root,env=w.env(),stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True,encoding='utf-8')
        try:
            end=time.monotonic()+240
            while time.monotonic()<end:
                text=path.read_text(encoding='utf-8',errors='replace')
                if 'Done (' in text: break
                if proc.poll() is not None: break
                time.sleep(.3)
            ready='Done (' in path.read_text(encoding='utf-8',errors='replace')
            if ready != expect_ready: raise RuntimeError(f'{label}: ready={ready}; inspect {path}')
            if not ready and proc.poll() is None: raise RuntimeError(f'{label}: did not exit')
        finally:
            if proc.poll() is None: f._stop(proc)
    text=path.read_text(encoding='utf-8',errors='replace')
    errors=[line for line in text.splitlines() if '/ERROR]' in line or 'Runtime hold' in line]
    return {'ready':ready,'exit':proc.returncode,'errors':errors,'log':str(path)}

def files(root):
    result={}
    for base in (root/'config/chronicle',root/'world/chronicle'):
        if base.exists():
            for p in base.rglob('*'):
                if p.is_file():
                    b=p.read_bytes(); result[str(p.relative_to(root))]={'bytes':len(b),'quiet_below_mentions':b.count(b'quiet_below:'),'sha256':hashlib.sha256(b).hexdigest()}
    return result

def main():
    w.require_eula(); w.validate_server_runtime(); w.validate_managed_mod_names()
    version=dict(line.split('=',1) for line in (w.REPO/'gradle.properties').read_text().splitlines() if '=' in line)['mod_version']
    jars=w.artifacts(); lc=[w.REPO/'campaigns/last-council/build/libs'/('last_council-'+version+'.jar')]
    if not lc[0].is_file(): raise RuntimeError('Build the matching Last Council jar first')
    w.validate_mod_target(lc[0]); jars+=lc
    stamp=datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    out=w.REPORTS/('council-selection-'+stamp); out.mkdir(parents=True)
    report={'result':'FAIL','artifacts':{p.name:w.digest(p) for p in jars},'checks':{},'fixtures':[], 'limitations':['server-only; no client or interactive draw; QB adapters may initialize even when LC is selected']}
    try:
        for label, mods, selection in [('core',('core',),None),('qb',('core','qb'),None),('lc',('core','lc'),None),('both_lc',('core','qb','lc'),'last_council:scenario'),('both_qb',('core','qb','lc'),'quiet_below:scenario'),('both_unselected',('core','qb','lc'),None)]:
            root=f._stage(w); report['fixtures'].append(str(root))
            (root/'.council-selection-fixture').write_text('Disposable server-only selection proof\n',encoding='utf-8')
            for jar in (root/'mods').glob('chronicle_core-*.jar'):
                if 'core' not in mods: jar.unlink()
            for jar in (root/'mods').glob('quiet_below-*.jar'):
                if 'qb' not in mods: jar.unlink()
            for jar in (root/'mods').glob('last_council-*.jar'): jar.unlink()
            if 'lc' in mods: shutil.copy2(lc[0],root/'mods'/lc[0].name)
            props=root/'server.properties'; props.write_text(props.read_text().replace('server-port=25575',f'server-port={PORT}'),encoding='utf-8')
            (root/'config/voicechat/voicechat-server.properties').write_text(f'bind_address=127.0.0.1\nport={VOICE}\n',encoding='utf-8')
            choose(root,selection)
            result=launch(root,label,out,label!='both_unselected'); result['files']=files(root); result['fixture']=str(root)
            report['checks'][label]=result
            if label=='both_unselected':
                log=Path(result['log']).read_text(encoding='utf-8',errors='replace')
                if 'Several scenario mods are installed' not in log: raise RuntimeError('No explicit selection refusal evidence')
                if (root/'world').exists(): raise RuntimeError('Missing-selection boot created world directory')
            else:
                unexpected=[line for line in result['errors'] if not any(known in line for known in ('Failed to request yggdrasil public key','No key layers in MapLike[{}]'))]
                if unexpected: raise RuntimeError(f'{label}: unexpected error or runtime hold: {unexpected}')
                if label in ('lc','both_lc','core'):
                    for name,data in result['files'].items():
                        if data['quiet_below_mentions'] and ('books' in name or 'loot' in name or 'roles' in name or 'event' in name): raise RuntimeError(f'{label}: QB content in {name}')
                if label in ('qb','both_qb') and not any(data['quiet_below_mentions'] for name,data in result['files'].items() if any(s in name for s in ('books','loot','roles','event'))): raise RuntimeError('QB defaults absent')
            if label=='both_lc': lcroot=root
        choose(lcroot,'quiet_below:scenario')
        result=launch(lcroot,'mismatched_world',out,False); report['checks']['mismatched_world']=result
        log=Path(result['log']).read_text(encoding='utf-8',errors='replace')
        if 'This map belongs to another scenario' not in log: raise RuntimeError('No world mismatch refusal evidence')
        marker=json.loads((lcroot/'world/chronicle/scenario-selection.json').read_text(encoding='utf-8'))
        if marker['scenarioId']!='last_council:scenario': raise RuntimeError('World marker changed')
        report['result']='PASS'
    except Exception as e:
        report['error']=str(e); report['traceback']=traceback.format_exc()
    finally:
        (out/'report.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
        print(json.dumps({'result':report['result'],'report':str(out/'report.json'),'error':report.get('error')}),flush=True)
    if report['result']!='PASS': raise SystemExit(1)

if __name__=='__main__': main()
