"""Disposable loopback native Region/Location subtype smoke; never targets review worlds."""
from __future__ import annotations
import json
import subprocess
import fabric_smoke
import menu_smoke

def run() -> None:
    import workspace as w
    w.require_eula(); w.validate_server_runtime(); w.validate_managed_mod_names()
    w.run([w.REPO / "gradlew.bat", "--no-daemon", ":core:remapJar", ":campaigns:quiet-below:remapJar", ":campaigns:quiet-below:writeSmokeClientLaunch"])
    launch=json.loads((w.REPO / "campaigns/quiet-below/build/smoke/client-launch.json").read_text(encoding="utf-8"))
    first_root=fabric_smoke._stage(w)
    reports=w.REPORTS / ("subtypes-"+first_root.name); reports.mkdir(parents=True)
    report={"result":"FAIL","worlds":[],"reports":str(reports),"checks":[],"limitations":["This fixture covers editor authoring and one terrain application; it does not exercise departure execution, restart recovery, maximum size, or fault injection."]}
    server=None; log=None; clients=[]
    try:
        env=w.env(); env["CHRONICLE_SUBTYPES_TRIAL"]="true"
        for label,width,height in (("desktop",1280,720),("square",960,960)):
            root=first_root if label=="desktop" else fabric_smoke._stage(w)
            root.joinpath(".subtypes-fixture").write_text("Disposable subtype editor fixture\n",encoding="utf-8")
            root.joinpath("ops.json").write_text(json.dumps([{"uuid":fabric_smoke.offline_uuid("ChronicleFabricA"),"name":"ChronicleFabricA","level":2,"bypassesPlayerLimit":False}],indent=2),encoding="utf-8")
            report["worlds"].append(str(root))
            menu_smoke._reserve_ports(); log=(reports / ("server-"+label+".log")).open("w",encoding="utf-8")
            server=subprocess.Popen(w.server_command(),cwd=root,env=env,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True,encoding="utf-8")
            menu_smoke._wait_ready(server,reports / ("server-"+label+".log"))
            report["checks"].append(label+": isolated offline dedicated server bound to loopback 25575/24455")
            result=reports / (label+".json")
            clients=[fabric_smoke._client(w,launch,root,reports,"ChronicleFabricA","subtypes-"+label,{"CHRONICLE_SUBTYPES_SMOKE":str(result),"CHRONICLE_SUBTYPES_SERVER_DIR":str(root),"CHRONICLE_MENU_WIDTH":str(width),"CHRONICLE_MENU_HEIGHT":str(height)})]
            fabric_smoke._await_clients(clients,[result],timeout=300)
            proof=json.loads(result.read_text(encoding="utf-8"))
            if proof.get("result")!="PASS": raise RuntimeError(json.dumps(proof,indent=2))
            report["checks"].extend(label+": "+check for check in proof.get("checks",[]))
            for _,handle in clients: handle.close()
            clients=[]
            fabric_smoke._stop(server); server=None; log.close(); log=None
        report["result"]="PASS"; print("PASS: native subtype editor smoke; evidence:",reports,flush=True)
    except BaseException as error:
        report["error"]=str(error); raise
    finally:
        for process,handle in clients:
            if process.poll() is None: process.terminate()
            handle.close()
        fabric_smoke._stop(server)
        if log is not None: log.close()
        content=json.dumps(report,indent=2)+"\n"; (reports / "report.json").write_text(content,encoding="utf-8"); (w.REPORTS / "subtypes-latest.json").write_text(content,encoding="utf-8")

if __name__ == "__main__": run()
