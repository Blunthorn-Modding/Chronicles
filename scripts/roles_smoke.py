"""Disposable loopback CF19 Player Roles native smoke; never targets review worlds."""
from __future__ import annotations
import json
import subprocess
from pathlib import Path
import fabric_smoke
import menu_smoke

def run() -> None:
    import workspace as w
    w.require_eula(); w.validate_server_runtime(); w.validate_managed_mod_names()
    w.run([w.REPO / "gradlew.bat", "--no-daemon", ":core:remapJar", ":campaigns:quiet-below:remapJar", ":campaigns:quiet-below:writeSmokeClientLaunch"])
    launch=json.loads((w.REPO / "campaigns/quiet-below/build/smoke/client-launch.json").read_text(encoding="utf-8"))
    root=fabric_smoke._stage(w); root.joinpath(".player-roles-fixture").write_text("Disposable CF19 Player Roles fixture\n",encoding="utf-8")
    config=root / "config" / "chronicle"; config.mkdir(parents=True,exist_ok=True)
    (config / "books.json").write_text(json.dumps({"schemaVersion":1,"books":[{"id":"test:role_intro","title":"Personal introduction","author":"Fixture","description":"Personal role entitlement","pages":["A private first-entry note."],"rule":"OFF"}]}),encoding="utf-8")
    reports=w.REPORTS / ("player-roles-" + root.name); reports.mkdir(parents=True)
    result=reports / "desktop.json"; log=(reports / "server.log").open("w",encoding="utf-8"); server=None; clients=[]
    report={"result":"FAIL","world":str(root),"checks":[]}
    try:
        menu_smoke._reserve_ports(); env=w.env(); env["CHRONICLE_ROLES_TRIAL"]="true"
        server=subprocess.Popen(w.server_command(),cwd=root,env=env,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True,encoding="utf-8")
        menu_smoke._wait_ready(server,reports / "server.log")
        clients=[fabric_smoke._client(w,launch,root,reports,"ChronicleFabricA","roles-desktop",{"CHRONICLE_ROLES_SMOKE":str(result),"CHRONICLE_ROLES_SERVER_DIR":str(root),"CHRONICLE_MENU_WIDTH":"1280","CHRONICLE_MENU_HEIGHT":"720"})]
        fabric_smoke._await_clients(clients,[result],timeout=180)
        proof=json.loads(result.read_text(encoding="utf-8"))
        if proof.get("result")!="PASS": raise RuntimeError(json.dumps(proof,indent=2))
        report["checks"].extend(proof.get("checks",[]))
        for _,handle in clients:handle.close()
        clients=[]
        fabric_smoke._stop(server);server=None;log.close()
        log=(reports / "server-restart.log").open("w",encoding="utf-8")
        menu_smoke._reserve_ports()
        server=subprocess.Popen(w.server_command(),cwd=root,env=env,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True,encoding="utf-8")
        menu_smoke._wait_ready(server,reports / "server-restart.log")
        result=reports / "restart.json"
        clients=[fabric_smoke._client(w,launch,root,reports,"ChronicleFabricA","roles-restart",{"CHRONICLE_ROLES_SMOKE":str(result),"CHRONICLE_ROLES_SERVER_DIR":str(root),"CHRONICLE_ROLES_RESTART":"true","CHRONICLE_MENU_WIDTH":"960","CHRONICLE_MENU_HEIGHT":"960"})]
        fabric_smoke._await_clients(clients,[result],timeout=180)
        proof=json.loads(result.read_text(encoding="utf-8"))
        if proof.get("result")!="PASS":raise RuntimeError(json.dumps(proof,indent=2))
        report["checks"].extend(proof.get("checks",[]));report["result"]="PASS"
        print("PASS: Player Roles native smoke; evidence:",reports,flush=True)
    finally:
        for process, handle in clients:
            if process.poll() is None: process.terminate()
            handle.close()
        fabric_smoke._stop(server); log.close()
        (reports / "report.json").write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8")

if __name__ == "__main__": run()
