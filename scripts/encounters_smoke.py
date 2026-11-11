"""Disposable loopback encounter spawn/maintenance proof; never targets review worlds."""
from __future__ import annotations
import json
import argparse
import subprocess
from pathlib import Path
import fabric_smoke
import menu_smoke

def _proof(path: Path) -> dict:
    value=json.loads(path.read_text(encoding="utf-8"))
    if value.get("result")!="PASS": raise RuntimeError(json.dumps(value,indent=2))
    return value

def _start(w,root:Path,reports:Path,label:str,env:dict[str,str]):
    menu_smoke._reserve_ports();log=(reports/f"server-{label}.log").open("w",encoding="utf-8")
    server=subprocess.Popen(w.server_command(),cwd=root,env=env,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True,encoding="utf-8")
    menu_smoke._wait_ready(server,reports/f"server-{label}.log");return server,log

def _client(w,launch,root,reports,phase):
    result=reports/f"{phase}.json"
    compact=phase=="ui_compact"
    client=fabric_smoke._client(w,launch,root,reports,"ChronEncounter",f"encounters-{phase}",{
        "CHRONICLE_ENCOUNTERS_SMOKE":str(result),"CHRONICLE_ENCOUNTERS_SERVER_DIR":str(root),
        "CHRONICLE_ENCOUNTERS_PHASE":phase,"CHRONICLE_MENU_WIDTH":"640" if compact else "1280","CHRONICLE_MENU_HEIGHT":"480" if compact else "800"})
    return client,result

def run(build:bool=True,only:str="runtime")->None:
    import workspace as w
    w.require_eula();w.validate_server_runtime();w.validate_managed_mod_names()
    if build:w.run([w.REPO/"gradlew.bat","--no-daemon",":core:remapJar",":campaigns:quiet-below:remapJar",":campaigns:quiet-below:writeSmokeClientLaunch"])
    launch=json.loads((w.REPO/"campaigns/quiet-below/build/smoke/client-launch.json").read_text(encoding="utf-8"))
    root=fabric_smoke._stage(w);(root/".encounter-actions-fixture").write_text("Disposable encounter actions fixture\n",encoding="utf-8")
    # The generic staging fixture defaults to Peaceful, which would despawn hostile
    # encounter members after restart even though their durable spawn completed.
    properties=root/"server.properties"
    settings=[line for line in properties.read_text(encoding="utf-8").splitlines() if not line.startswith("difficulty=")]
    properties.write_text("\n".join([*settings,"difficulty=normal",""]),encoding="utf-8")
    (root/"ops.json").write_text(json.dumps([{"uuid":fabric_smoke.offline_uuid("ChronEncounter"),"name":"ChronEncounter","level":2,"bypassesPlayerLimit":False}],indent=2),encoding="utf-8")
    reports=w.REPORTS/("encounters-"+root.name);reports.mkdir(parents=True)
    report={"result":"FAIL","world":str(root),"checks":[],"limitations":["The fixture validates exact UUID identity and at-most-once restart behavior; vanilla mob AI and combat outcomes are intentionally not asserted."]}
    env=w.env();env["CHRONICLE_ENCOUNTERS_TRIAL"]="true";server=log=None;clients=[]
    try:
        phases={"editor":("ui_wide","ui_compact"),"recovery":("uncertain_review",),"runtime":("basic","restart"),"planning_restart_zero":("seed_zero","resume_zero"),"planning_restart_multiple":("seed_multiple","resume_multiple")}.get(only,(only,))
        shared=only in ("editor","recovery")
        if shared:server,log=_start(w,root,reports,only,env)
        for phase in phases:
            if not shared:server,log=_start(w,root,reports,phase,env)
            client,result=_client(w,launch,root,reports,phase);clients=[client]
            fabric_smoke._await_clients(clients,[result],timeout=240);report["checks"].append(_proof(result)["detail"]+f" ({phase})")
            client[1].close();clients=[]
            if not shared:fabric_smoke._stop(server);server=None;log.close();log=None
        if shared:fabric_smoke._stop(server);server=None;log.close();log=None
        report["result"]="PASS";print("PASS: encounter runtime native smoke; evidence:",reports,flush=True)
    finally:
        for process,handle in clients:
            if process.poll() is None:process.terminate()
            handle.close()
        fabric_smoke._stop(server)
        if log is not None:log.close()
        (reports/"report.json").write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8")

if __name__=="__main__":
    parser=argparse.ArgumentParser();parser.add_argument("--only",choices=("runtime","preflight","footprint","invalid_geometry","wide_runtime","editor","recovery","blocked_retry","blocked_skip","region_ground","region_then_point","region_shared","region_blocked","mixed_resolved","mixed_reviewed","planning_restart_zero","planning_restart_multiple","legacy_retry","changed_target"),default="runtime");parser.add_argument("--no-build",action="store_true")
    args=parser.parse_args();run(build=not args.no_build,only=args.only)
