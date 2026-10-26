"""Disposable loopback CF21 paired-Area native smoke; never targets review worlds."""
from __future__ import annotations

import json
import subprocess

import fabric_smoke
import menu_smoke


def _proof(path):
    value=json.loads(path.read_text(encoding="utf-8"))
    if value.get("result")!="PASS": raise RuntimeError(json.dumps(value,indent=2))
    return value


def run() -> None:
    import workspace as w
    w.require_eula();w.validate_server_runtime();w.validate_managed_mod_names()
    w.run([w.REPO / "gradlew.bat","--no-daemon",":core:remapJar",":campaigns:quiet-below:remapJar",":campaigns:quiet-below:writeSmokeClientLaunch"])
    launch=json.loads((w.REPO / "campaigns/quiet-below/build/smoke/client-launch.json").read_text(encoding="utf-8"))
    root=fabric_smoke._stage(w);fabric_smoke._loot_fixture(root);root.joinpath(".area-changes-fixture").write_text("Disposable CF21 Area changes fixture\n",encoding="utf-8")
    config=root / "config" / "chronicle";config.mkdir(parents=True,exist_ok=True)
    (config / "books.json").write_text(json.dumps({"schemaVersion":1,"books":[{"id":"test:area_managed","title":"Managed fixture","author":"Fixture","description":"Area provenance refusal fixture","pages":["Managed Area evidence."],"rule":"OFF"}]}),encoding="utf-8")
    reports=w.REPORTS / ("area-changes-"+root.name);reports.mkdir(parents=True)
    report={"result":"FAIL","world":str(root),"reports":str(reports),"checks":[],"limitations":["No interrupted mid-apply kill/recovery is claimed by this bounded fixture."]}
    env=w.env();env["CHRONICLE_AREAS_TRIAL"]="true";server=None;log=None;clients=[]
    try:
        for phase,width,height in (("initial",1280,720),("restart",960,960)):
            menu_smoke._reserve_ports();log_path=reports / ("server-"+phase+".log");log=log_path.open("w",encoding="utf-8")
            server=subprocess.Popen(w.server_command(),cwd=root,env=env,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True,encoding="utf-8")
            menu_smoke._wait_ready(server,log_path)
            result=reports / (phase+".json")
            clients=[fabric_smoke._client(w,launch,root,reports,"ChronicleFabricA","areas-"+phase,{"CHRONICLE_AREAS_SMOKE":str(result),"CHRONICLE_AREAS_SERVER_DIR":str(root),"CHRONICLE_AREAS_PHASE":phase,"CHRONICLE_MENU_WIDTH":str(width),"CHRONICLE_MENU_HEIGHT":str(height)})]
            fabric_smoke._await_clients(clients,[result],timeout=300);proof=_proof(result);report["checks"].extend(phase+": "+check for check in proof.get("checks",[]))
            for _,handle in clients:handle.close()
            clients=[];fabric_smoke._stop(server);server=None;log.close();log=None
            report["checks"].append(phase+": clean disposable server save/stop")
        required=("debug shovel","ordinary-book container and sign NBT","discarded replaced chest contents","recaptured Start","non-spectator","unloaded capture","authentic managed lore","stale shovel","managed-block quota refusal","delete applied","leave-unchanged deletion")
        missing=[needle for needle in required if not any(needle in check for check in report["checks"])]
        if missing:raise RuntimeError("Area smoke ended before required evidence: "+repr(missing))
        report["result"]="PASS";print("PASS: Area changes native smoke; evidence:",reports,flush=True)
    except BaseException as error:
        report["error"]=str(error);raise
    finally:
        for process,handle in clients:
            if process.poll() is None:process.terminate()
            handle.close()
        fabric_smoke._stop(server)
        if log is not None:log.close()
        content=json.dumps(report,indent=2)+"\n";(reports / "report.json").write_text(content,encoding="utf-8");(w.REPORTS / "area-changes-latest.json").write_text(content,encoding="utf-8")


if __name__=="__main__":run()
