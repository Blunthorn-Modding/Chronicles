"""Disposable two-client native Relic Heart ending rehearsal.

This runner deliberately stages a fresh offline fixture and never addresses a
review world.  Server-side trial commands are enabled only for this child
process through ``CHRONICLE_HEART_NATIVE_TRIAL``.
"""
from __future__ import annotations

import json
from pathlib import Path
import socket
import subprocess
import time


def _reserve_ports() -> None:
    with socket.socket() as probe: probe.bind(("127.0.0.1", 25575))
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe: probe.bind(("127.0.0.1", 24455))


def _wait_ready(server, logfile: Path) -> None:
    deadline = time.monotonic() + 240
    while True:
        content = logfile.read_text(encoding="utf-8", errors="replace") if logfile.exists() else ""
        if "Done (" in content and "Voice chat server started at 127.0.0.1:24455" in content: return
        if server.poll() is not None or time.monotonic() > deadline:
            raise RuntimeError("Heart fixture failed startup: " + str(logfile))
        time.sleep(.2)


def _close(clients) -> None:
    for process, handle in clients:
        if process.poll() is None:
            process.terminate()
            try: process.wait(timeout=15)
            except subprocess.TimeoutExpired: process.kill(); process.wait(timeout=15)
        handle.close()


def _client(w, fabric_smoke, launch, root: Path, reports: Path, role: str, phase: str):
    return fabric_smoke._client(w, launch, root, reports, "ChronicleFabric" + role, "heart-" + phase + "-" + role, {
        "CHRONICLE_HEART_SMOKE": str(reports), "CHRONICLE_HEART_ROLE": role,
        "CHRONICLE_HEART_PHASE": phase, "CHRONICLE_HEART_SERVER_DIR": str(root),
    })


def _start(w, root: Path, reports: Path, label: str):
    _reserve_ports(); path = reports / ("server-heart-" + label + ".log"); log = path.open("w", encoding="utf-8")
    server = None
    try:
        env = w.env(); env["CHRONICLE_HEART_NATIVE_TRIAL"] = "true"
        server = subprocess.Popen(w.server_command(), cwd=root, env=env, stdin=subprocess.PIPE, stdout=log,
                                  stderr=subprocess.STDOUT, text=True, encoding="utf-8")
        _wait_ready(server, path); return server, log
    except BaseException:
        try:
            if server is not None:
                import fabric_smoke
                fabric_smoke._stop(server)
        finally: log.close()
        raise


def _await(fabric_smoke, clients, reports: Path, phase: str, roles: tuple[str, ...]):
    fabric_smoke._await_clients(clients, [reports / ("heart-" + phase + "-" + role + ".json") for role in roles], timeout=700)


def _run(w, fabric_smoke, launch, root: Path, reports: Path) -> None:
    """Initial A/B establishes cross-actor safety; restart A/B proves offline receipt removal."""
    for phase, roles in (("initial", ("A", "B")), ("restart", ("A", "B"))):
        server = log = None; clients = []
        try:
            server, log = _start(w, root, reports, phase)
            for role in roles: clients.append(_client(w, fabric_smoke, launch, root, reports, role, phase))
            _await(fabric_smoke, clients, reports, phase, roles)
        finally:
            _close(clients)
            if server is not None: fabric_smoke._stop(server)
            if log is not None: log.close()


def run() -> None:
    import fabric_smoke
    import workspace as w
    w.require_eula(); w.validate_server_runtime(); w.validate_managed_mod_names()
    w.run([w.REPO / "gradlew.bat", "--no-daemon", ":core:remapJar", ":campaigns:quiet-below:remapJar",
           ":campaigns:quiet-below:writeSmokeClientLaunch"])
    launch = json.loads((w.REPO / "campaigns/quiet-below/build/smoke/client-launch.json").read_text(encoding="utf-8"))
    root = fabric_smoke._stage(w)
    # Only this disposable trial command requires B to inspect its saved private authority.
    import hashlib, uuid
    ops_file = root / "ops.json"; ops = json.loads(ops_file.read_text(encoding="utf-8"))
    actor = str(uuid.UUID(bytes=hashlib.md5(b"OfflinePlayer:ChronicleFabricB").digest(), version=3))
    ops = [entry for entry in ops if entry["uuid"] != actor]
    ops.append({"uuid": actor, "name": "ChronicleFabricB", "level": 2, "bypassesPlayerLimit": False})
    ops_file.write_text(json.dumps(ops, indent=2)+"\n", encoding="utf-8")
    reports = w.REPORTS / ("heart-smoke-" + root.name); reports.mkdir(parents=True)
    report = {"result": "FAIL", "world": str(root), "reports": str(reports), "phases": [], "checks": []}
    try:
        _run(w, fabric_smoke, launch, root, reports)
        import shutil
        shutil.copyfile(root / "world" / "heart-cleanup-measured.json", reports / "cleanup-performance.json")
        report["result"] = "PASS"; report["phases"] = [{"phase": "initial", "roles": ["A", "B"]}, {"phase": "restart", "roles": ["A", "B"]}]
        report["checks"].append("disposable two-player Heart confirmation and restart rehearsal passed")
        print("PASS: Relic Heart native smoke; evidence:", reports, flush=True)
    except BaseException as error:
        report["error"] = str(error); raise
    finally:
        content = json.dumps(report, indent=2) + "\n"; (reports / "report.json").write_text(content, encoding="utf-8")
        (w.REPORTS / "heart-smoke-latest.json").write_text(content, encoding="utf-8")


if __name__ == "__main__": run()
