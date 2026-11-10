"""Opt-in personal-copy hard-halt and restart proof on fresh loopback fixtures.

Requires prebuilt Core jar and the Last Council development client launch manifest.
Never targets a review or played world. The runner does not build artifacts.
"""
from __future__ import annotations

from datetime import datetime, timezone
import json
from pathlib import Path
import shutil
import subprocess
import time

import events_smoke
import fabric_smoke
import menu_smoke


def _start(w, root, reports, label, env):
    menu_smoke._reserve_ports()
    path = reports / ('server-' + label + '.log')
    log = path.open('w', encoding='utf-8')
    server = None
    try:
        server = subprocess.Popen(w.server_command(), cwd=root, env=env, stdin=subprocess.PIPE,
                                  stdout=log, stderr=subprocess.STDOUT, text=True, encoding='utf-8')
        menu_smoke._wait_ready(server, path)
        return server, log
    except BaseException:
        try:
            fabric_smoke._stop(server)
        finally:
            log.close()
        raise


def _stage(w):
    root = w.WORK / "test-runs" / datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    root.mkdir(parents=True)
    for name in ("fabric-server-launch.jar", "fabric-server-launcher.properties", "server.jar", "eula.txt"):
        shutil.copy2(w.SERVER / name, root / name)
    shutil.copytree(w.SERVER / "libraries", root / "libraries")
    (root / "mods").mkdir()
    for jar in (w.SERVER / "mods").glob("*.jar"):
        if not jar.name.startswith(("chronicle_core-", "quiet_below-", "last_council-")):
            shutil.copy2(jar, root / "mods" / jar.name)
    properties = dict(line.split("=", 1) for line in (w.REPO / "gradle.properties").read_text().splitlines() if "=" in line)
    core = w.REPO / "core" / "build" / "libs" / ("chronicle_core-" + properties["mod_version"] + ".jar")
    w.validate_mod_target(core)
    shutil.copy2(core, root / "mods" / core.name)
    (root / "server.properties").write_text("\n".join([
        "server-ip=127.0.0.1", "server-port=25575", "online-mode=false", "white-list=false",
        "enable-rcon=false", "enable-query=false", "view-distance=3", "simulation-distance=3",
        "spawn-protection=0", "level-type=minecraft:flat", "generate-structures=false",
        "gamemode=survival", "difficulty=peaceful", "level-name=world", "max-players=4",
        "sync-chunk-writes=true", "pause-when-empty-seconds=0", "",
    ]), encoding="utf-8")
    (root / ".personal-copy-fixture").write_text("Disposable personal-copy crash fixture\n", encoding="utf-8")
    voice = root / "config/voicechat"
    voice.mkdir(parents=True)
    (voice / "voicechat-server.properties").write_text("bind_address=127.0.0.1\nport=24455\n", encoding="utf-8")
    return root


def _client(w, launch, root, reports, label):
    result = reports / (label + ".json")
    client = fabric_smoke._client(w, launch, root, reports, "ChronicleFabricA", label, {
        "CHRONICLE_PERSONAL_COPY_SMOKE": str(result),
        "CHRONICLE_PERSONAL_COPY_SERVER_DIR": str(root),
    })
    return client, result


def run():
    import workspace as w
    w.require_eula()
    w.validate_server_runtime()
    manifest = w.REPO / "campaigns/last-council/build/smoke/client-launch.json"
    launch = json.loads(manifest.read_text(encoding="utf-8"))
    reports = w.REPORTS / ("personal-copy-crash-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ"))
    reports.mkdir(parents=True)
    report = {"result": "FAIL", "phases": [], "limitations": ["Hard process termination at selected checkpoints does not simulate arbitrary power or disk failure."]}
    server = log = client = None
    try:
        for phase in ("claim", "apply", "save"):
            root = _stage(w)
            env = w.env()
            env["CHRONICLE_PERSONAL_COPY_TRIAL"] = "true"
            env["CHRONICLE_PERSONAL_COPY_HALT"] = phase
            server, log = _start(w, root, reports, phase + "-halt", env)
            client, _ = _client(w, launch, root, reports, phase + "-halt")
            deadline = time.monotonic() + 180
            while server.poll() is None and time.monotonic() < deadline:
                failure = root / ".personal-copy-result.json"
                if failure.exists():
                    raise RuntimeError("Server trial failed before halt: " + failure.read_text(encoding="utf-8"))
                if client[0].poll() is not None:
                    raise RuntimeError("Fixture client exited before halt; inspect its log")
                time.sleep(0.25)
            if server.poll() != 99:
                raise RuntimeError(f"{phase}: expected hard halt 99, got {server.poll()}")
            server = None
            log.close(); log = None
            events_smoke._terminate_client(client); client = None
            marker = json.loads((root / ".personal-copy-halt.json").read_text(encoding="utf-8"))
            if marker.get("phase") != phase:
                raise RuntimeError("Wrong hard-halt marker")
            (reports / (phase + "-halt.json")).write_text(json.dumps(marker, indent=2) + "\n", encoding="utf-8")
            env.pop("CHRONICLE_PERSONAL_COPY_HALT")
            server, log = _start(w, root, reports, phase + "-recover", env)
            client, result = _client(w, launch, root, reports, phase + "-recover")
            fabric_smoke._await_clients([client], [result], timeout=240)
            proof = events_smoke._proof(result)
            client[1].close(); client = None
            fabric_smoke._stop(server); server = None
            log.close(); log = None
            report["phases"].append({"phase": phase, "world": str(root), "proof": proof})
        report["result"] = "PASS"
        print("PASS: personal-copy native crash and restart proof:", reports, flush=True)
    except BaseException as failure:
        report["error"] = str(failure)
        raise
    finally:
        if client is not None:
            events_smoke._terminate_client(client)
        try:
            fabric_smoke._stop(server)
        finally:
            if log is not None:
                log.close()
            (reports / "report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    run()
