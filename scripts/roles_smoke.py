"""Disposable loopback CF19 Player Roles native smoke; never targets review worlds."""
from __future__ import annotations
import json
import hashlib
import shutil
import zipfile
import subprocess
from pathlib import Path
import fabric_smoke
import menu_smoke

def _world_hashes(root: Path) -> dict[str, str]:
    return {str(path.relative_to(root)): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in root.rglob('*') if path.is_file()}


def _copy_map_with_packaged_content(w, source: Path) -> tuple[Path, dict[str, str]]:
    # This is a fresh test server, not a promotion of or write into the original fixture.
    role_file = source / 'world/chronicle/player-roles.yaml'
    if not role_file.is_file():
        raise RuntimeError('Map-owned role YAML is missing before the portability proof')
    source_hashes = _world_hashes(source / 'world')
    exports = list((source / 'config/chronicle/scenario-content').glob('*/exports/*.json'))
    if len(exports) != 1:
        raise RuntimeError('Portability proof requires one reviewed content export')
    target = fabric_smoke._stage(w)
    target.joinpath('.player-roles-fixture').write_text('Disposable copied-map role fixture\n', encoding='utf-8')
    shutil.copytree(source / 'world', target / 'world')
    jars = list((target / 'mods').glob('quiet_below-*.jar'))
    if len(jars) != 1 or not jars[0].resolve().is_relative_to(target.resolve()):
        raise RuntimeError('Unexpected copied scenario artifact path')
    resource = 'data/quiet_below/chronicle/scenario-content.json'
    candidate = jars[0].with_suffix('.prepared')
    with zipfile.ZipFile(jars[0]) as original, zipfile.ZipFile(candidate, 'w') as output:
        for entry in original.infolist():
            if entry.filename != resource:
                output.writestr(entry, original.read(entry))
        output.writestr(resource, exports[0].read_bytes(), compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(candidate) as check:
        if check.testzip() is not None:
            raise RuntimeError('Isolated scenario artifact has a corrupt entry')
    candidate.replace(jars[0])
    if (target / 'config/chronicle/player-roles.yaml').exists():
        raise RuntimeError('Fresh-server proof accidentally retained legacy server role configuration')
    return target, source_hashes


def run(build: bool = True) -> None:
    import workspace as w
    w.require_eula(); w.validate_server_runtime(); w.validate_managed_mod_names()
    if build:
        w.run([w.REPO / "gradlew.bat", "--no-daemon", ":core:remapJar", ":campaigns:quiet-below:remapJar", ":campaigns:quiet-below:writeSmokeClientLaunch"])
    launch=json.loads((w.REPO / "campaigns/quiet-below/build/smoke/client-launch.json").read_text(encoding="utf-8"))
    root=fabric_smoke._stage(w); root.joinpath(".player-roles-fixture").write_text("Disposable CF19 Player Roles fixture\n",encoding="utf-8")
    config=root / "config" / "chronicle"; config.mkdir(parents=True,exist_ok=True)
    # Runtime portability fixtures must not depend on provisional campaign roles.
    (config / 'player-roles.yaml').write_text('''schemaVersion: 1
roles:
  - id: quiet_below:island_resident
    name: Fixture resident
    description: Disposable role entitlement proof.
    selectable: true
    capacity: 32
    starting: null
    respawn: null
    items:
      - '{"id":"minecraft:bread","count":4}'
      - '{"id":"minecraft:torch","count":8}'
    books: []
''',encoding='utf-8')
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
        original_root=root
        root,source_world_hashes=_copy_map_with_packaged_content(w,original_root)
        report["restartWorld"]=str(root)
        report["restartUsesPackagedContent"]=True
        log=(reports / "server-restart.log").open("w",encoding="utf-8")
        menu_smoke._reserve_ports()
        server=subprocess.Popen(w.server_command(),cwd=root,env=env,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True,encoding="utf-8")
        menu_smoke._wait_ready(server,reports / "server-restart.log")
        result=reports / "restart.json"
        clients=[fabric_smoke._client(w,launch,root,reports,"ChronicleFabricA","roles-restart",{"CHRONICLE_ROLES_SMOKE":str(result),"CHRONICLE_ROLES_SERVER_DIR":str(root),"CHRONICLE_ROLES_RESTART":"true","CHRONICLE_MENU_WIDTH":"960","CHRONICLE_MENU_HEIGHT":"960"})]
        fabric_smoke._await_clients(clients,[result],timeout=180)
        proof=json.loads(result.read_text(encoding="utf-8"))
        if proof.get("result")!="PASS":raise RuntimeError(json.dumps(proof,indent=2))
        report["checks"].extend(proof.get("checks",[]))
        fabric_smoke._stop(server);server=None;log.close()
        if _world_hashes(original_root / 'world') != source_world_hashes:
            raise RuntimeError('Copied-map proof changed the original stopped fixture')
        if (root / 'config/chronicle/player-roles.yaml').exists():
            raise RuntimeError('Copied map depended on a server-wide role YAML')
        materialized=json.loads((root / 'config/chronicle/books.json').read_text(encoding='utf-8'))
        if not any(book['id']=='test:role_intro' for book in materialized['books']):
            raise RuntimeError('Exported content was not loaded from the isolated scenario artifact')
        report["originalWorldUnchanged"]=True
        report["checks"].append("fresh server loaded packaged content and copied-map role bindings without legacy role YAML; original map hashes unchanged")
        report["result"]="PASS"
        print("PASS: Player Roles native smoke; evidence:",reports,flush=True)
    finally:
        for process, handle in clients:
            if process.poll() is None: process.terminate()
            handle.close()
        fabric_smoke._stop(server); log.close()
        (reports / "report.json").write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8")

if __name__ == "__main__": run()
