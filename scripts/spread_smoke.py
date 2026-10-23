"""One-client native proof for the Quiet Below fixed versus count-based spread rate."""
from __future__ import annotations

import json
from pathlib import Path
import shutil
import socket
import subprocess
import time


def _reserve_ports() -> None:
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 25575))
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
        probe.bind(("127.0.0.1", 24455))


def _wait_ready(server, logfile: Path) -> None:
    deadline = time.monotonic() + 240
    while True:
        content = logfile.read_text(encoding="utf-8", errors="replace") if logfile.exists() else ""
        if "Done (" in content and "Voice chat server started at 127.0.0.1:24455" in content:
            return
        if server.poll() is not None or time.monotonic() > deadline:
            raise RuntimeError("Spread fixture failed startup: " + str(logfile))
        time.sleep(.2)


def _client(w, fabric_smoke, launch, root: Path, reports: Path, phase: str):
    variables = {
        "CHRONICLE_SPREAD_SMOKE": str(reports),
        "CHRONICLE_SPREAD_PHASE": phase,
        "CHRONICLE_SPREAD_ROLE": "A",
        "CHRONICLE_SPREAD_SERVER_DIR": str(root),
        "CHRONICLE_MENU_WIDTH": "1280",
        "CHRONICLE_MENU_HEIGHT": "720",
    }
    return fabric_smoke._client(w, launch, root, reports, "ChronicleFabricA", "spread-" + phase + "-A", variables)


def _close_clients(clients) -> None:
    for process, handle in clients:
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=15)
        handle.close()


def _run_phase(w, fabric_smoke, launch, root: Path, reports: Path, phase: str) -> None:
    _reserve_ports()
    logfile_path = reports / ("server-" + phase + ".log")
    logfile = logfile_path.open("w", encoding="utf-8")
    server = None
    clients = []
    try:
        environment = w.env()
        environment["CHRONICLE_SPREAD_NATIVE_TRIAL"] = "true"
        server = subprocess.Popen(w.server_command(), cwd=root, env=environment, stdin=subprocess.PIPE,
                                  stdout=logfile, stderr=subprocess.STDOUT, text=True, encoding="utf-8")
        _wait_ready(server, logfile_path)
        clients.append(_client(w, fabric_smoke, launch, root, reports, phase))
        fabric_smoke._await_clients(clients, [reports / ("spread-" + phase + "-A.json")], timeout=500)
    finally:
        _close_clients(clients)
        fabric_smoke._stop(server)
        logfile.close()


def _preserve_measurement(root: Path, reports: Path) -> dict:
    source = root / "world" / "quiet-below" / "spread-measurement.json"
    if not source.is_file():
        raise RuntimeError("Initial spread run did not write its measurement matrix")
    value = json.loads(source.read_text(encoding="utf-8"))
    if not isinstance(value, dict) or value.get("result") != "PASS" or not isinstance(value.get("samples"), list):
        raise RuntimeError("Initial spread measurement did not pass: " + str(value))
    target = reports / "spread-measurement.json"
    shutil.copy2(source, target)
    return value


def run() -> None:
    import fabric_smoke
    import workspace as w

    w.require_eula()
    w.validate_server_runtime()
    w.validate_managed_mod_names()
    w.run([w.REPO / "gradlew.bat", "--no-daemon", ":core:remapJar",
           ":campaigns:quiet-below:remapJar", ":campaigns:quiet-below:writeSmokeClientLaunch"])
    launch = json.loads((w.REPO / "campaigns/quiet-below/build/smoke/client-launch.json").read_text(encoding="utf-8"))
    root = fabric_smoke._stage(w)
    reports = w.REPORTS / ("spread-smoke-" + root.name)
    reports.mkdir(parents=True)
    report = {"result": "FAIL", "world": str(root), "reports": str(reports), "checks": [], "phases": []}
    try:
        _run_phase(w, fabric_smoke, launch, root, reports, "initial")
        report["measurement"] = _preserve_measurement(root, reports)
        report["phases"].append({"phase": "initial", "roles": ["A"]})
        report["checks"].append("initial native client recorded and externally preserved the four actual-tick measurement phases before pausing the review fixture")
        _run_phase(w, fabric_smoke, launch, root, reports, "restart")
        report["phases"].append({"phase": "restart", "roles": ["A"]})
        report["checks"].append("restart native client verified the persisted source, bridge, settings and reset attempt counter")
        report["result"] = "PASS"
        print("PASS: count-rate native smoke; retained evidence:", reports, flush=True)
    except BaseException as error:
        report["error"] = str(error)
        raise
    finally:
        reports.mkdir(parents=True, exist_ok=True)
        text = json.dumps(report, indent=2) + "\n"
        (reports / "report.json").write_text(text, encoding="utf-8")
        (w.REPORTS / "spread-smoke-latest.json").write_text(text, encoding="utf-8")


if __name__ == "__main__":
    run()
