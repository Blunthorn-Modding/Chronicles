"""Disposable two-account native proof for temporary Quiet Below Altar offerings."""
from __future__ import annotations

import json
from pathlib import Path
import socket
import subprocess
import time


def _ports() -> None:
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 25575))
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
        probe.bind(("127.0.0.1", 24455))


def _server(w, root: Path, reports: Path, label: str):
    _ports()
    log_path = reports / f"server-{label}.log"
    handle = log_path.open("w", encoding="utf-8")
    env = w.env()
    env["CHRONICLE_TEMPORARY_ALTAR_TRIAL"] = "true"
    env["CHRONICLE_FORGE_NATIVE_TRIAL"] = "true"
    process = subprocess.Popen(w.server_command(), cwd=root, env=env, stdin=subprocess.PIPE,
                               stdout=handle, stderr=subprocess.STDOUT, text=True, encoding="utf-8")
    deadline = time.monotonic() + 240
    while True:
        text = log_path.read_text(encoding="utf-8", errors="replace") if log_path.exists() else ""
        if "Done (" in text and "Voice chat server started at 127.0.0.1:24455" in text:
            return process, handle
        if process.poll() is not None or time.monotonic() > deadline:
            handle.close()
            raise RuntimeError(f"temporary Altar server failed startup: {log_path}")
        time.sleep(.2)


def _client(w, fabric_smoke, launch, root: Path, reports: Path, phase: str, role: str):
    env = {"CHRONICLE_TEMPORARY_ALTAR_SMOKE": str(reports),
           "CHRONICLE_TEMPORARY_ALTAR_PHASE": phase,
           "CHRONICLE_TEMPORARY_ALTAR_ROLE": role}
    return fabric_smoke._client(w, launch, root, reports, "ChronicleFabric" + role,
                                f"temporary-altar-{phase}-{role}", env)


def _await(path: Path, process, timeout: float = 300) -> dict:
    deadline = time.monotonic() + timeout
    while not path.is_file():
        if process.poll() is not None or time.monotonic() > deadline:
            raise RuntimeError(f"temporary Altar client did not publish {path}")
        time.sleep(.2)
    result = json.loads(path.read_text(encoding="utf-8"))
    if result.get("result") != "PASS":
        raise RuntimeError(str(result))
    return result


def _close(client) -> None:
    process, handle = client
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            process.kill(); process.wait(timeout=15)
    handle.close()


def _config(root: Path) -> None:
    target = root / "config/chronicle/quiet-below-altar-offerings.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps({"schema": 1,
        "emergency": {"enabled": True, "donorHealth": 4, "healing": 4, "radius": 8},
        "repair": {"enabled": True, "percent": 25, "health": 2, "seconds": 60},
        "sight": {"enabled": True, "levels": 3, "exhaustionPercent": 25, "seconds": 60},
        "harvest": {"enabled": True, "bread": 16, "seconds": 60, "radius": 8,
                    "cropPercent": 200, "growthPercent": 150}}, indent=2) + "\n", encoding="utf-8")


def run(build: bool = True, only: str | None = None) -> None:
    import fabric_smoke
    import workspace as w

    w.require_eula(); w.validate_server_runtime(); w.validate_managed_mod_names()
    if build:
        w.run([w.REPO / "gradlew.bat", "--no-daemon", ":core:remapJar", ":campaigns:quiet-below:remapJar",
               ":campaigns:quiet-below:writeSmokeClientLaunch"])
    launch = json.loads((w.REPO / "campaigns/quiet-below/build/smoke/client-launch.json").read_text(encoding="utf-8"))
    root = fabric_smoke._stage(w); _config(root)
    operators_path = root / "ops.json"; operators = json.loads(operators_path.read_text(encoding="utf-8"))
    operators.append({"uuid": fabric_smoke.offline_uuid("ChronicleFabricB"), "name": "ChronicleFabricB",
                      "level": 2, "bypassesPlayerLimit": False})
    operators_path.write_text(json.dumps(operators, indent=2) + "\n", encoding="utf-8")
    reports = w.REPORTS / ("temporary-altar-smoke-" + root.name); reports.mkdir(parents=True)
    report = {"result": "FAIL", "world": str(root), "reports": str(reports), "checks": []}
    server = server_log = None; clients = []
    try:
        if only == "ui":
            server, server_log = _server(w, root, reports, "ui")
            ui = _client(w, fabric_smoke, launch, root, reports, "ui", "A"); clients.append(ui)
            outcome = _await(reports / "temporary-altar-ui-A.json", ui[0])
            report["checks"] = outcome["checks"]; report["result"] = "PASS"
            print("PASS: temporary Altar page-two native smoke; evidence:", reports, flush=True)
            return
        server, server_log = _server(w, root, reports, "initial")
        passive = _client(w, fabric_smoke, launch, root, reports, "initial", "B"); clients.append(passive)
        initial = _client(w, fabric_smoke, launch, root, reports, "initial", "A"); clients.append(initial)
        _await(reports / "temporary-altar-initial-A.json", initial[0]); _close(initial); clients.remove(initial)
        reconnect = _client(w, fabric_smoke, launch, root, reports, "reconnect", "A"); clients.append(reconnect)
        _await(reports / "temporary-altar-reconnect-A.json", reconnect[0]); _await(reports / "temporary-altar-initial-B.json", passive[0])
        _close(reconnect); clients.remove(reconnect); _close(passive); clients.remove(passive)
        fabric_smoke._stop(server); server = None; server_log.close(); server_log = None
        time.sleep(5)  # real downtime must not spend the active clock
        server, server_log = _server(w, root, reports, "restart")
        restarted = _client(w, fabric_smoke, launch, root, reports, "restart", "A"); clients.append(restarted)
        outcome = _await(reports / "temporary-altar-restart-A.json", restarted[0], 360)
        report["checks"] = outcome["checks"]; report["result"] = "PASS"
        print("PASS: temporary Altar native smoke; evidence:", reports, flush=True)
    except BaseException as error:
        report["error"] = str(error); raise
    finally:
        for client in list(clients): _close(client)
        if server is not None: fabric_smoke._stop(server)
        if server_log is not None: server_log.close()
        text = json.dumps(report, indent=2) + "\n"
        (reports / "report.json").write_text(text, encoding="utf-8")
        (w.REPORTS / "temporary-altar-smoke-latest.json").write_text(text, encoding="utf-8")


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", choices=("ui",))
    run(only=parser.parse_args().only)
