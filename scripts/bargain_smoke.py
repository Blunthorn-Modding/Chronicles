"""Native two-account lasting-bargain rehearsal on a disposable Fabric world.

The initial and reconnect clients share one server process deliberately: it
proves the account ledger survives a real client reconnect before the world is
saved and restarted.  The optional HUD dependencies are only loaded for that
reconnect client and are never copied into the fixture or a pack.
"""
from __future__ import annotations

import copy
import json
from pathlib import Path
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
            raise RuntimeError("Bargain fixture failed startup: " + str(logfile))
        time.sleep(.2)


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


def _client(w, fabric_smoke, launch, root: Path, reports: Path, role: str, phase: str):
    variables = {
        "CHRONICLE_BARGAIN_SMOKE": str(reports),
        "CHRONICLE_BARGAIN_ROLE": role,
        "CHRONICLE_BARGAIN_PHASE": phase,
        "CHRONICLE_BARGAIN_SERVER_DIR": str(root),
    }
    return fabric_smoke._client(w, launch, root, reports, "ChronicleFabric" + role,
                                "bargain-" + phase + "-" + role, variables)


def _server_environment(w):
    environment = w.env()
    environment["CHRONICLE_FORGE_NATIVE_TRIAL"] = "true"
    environment["CHRONICLE_BARGAIN_NATIVE_TRIAL"] = "true"
    return environment


def _start_server(w, reports: Path, root: Path, label: str, stop):
    _reserve_ports()
    logfile_path = reports / ("server-" + label + ".log")
    logfile = logfile_path.open("w", encoding="utf-8")
    server = None
    try:
        server = subprocess.Popen(w.server_command(), cwd=root, env=_server_environment(w), stdin=subprocess.PIPE,
                                  stdout=logfile, stderr=subprocess.STDOUT, text=True, encoding="utf-8")
        _wait_ready(server, logfile_path)
        return server, logfile
    except BaseException:
        try:
            if server is not None:
                stop(server)
        finally:
            logfile.close()
        raise


def _await(fabric_smoke, clients, reports: Path, phase: str, roles: tuple[str, ...]) -> None:
    results = [reports / ("bargain-" + phase + "-" + role + ".json") for role in roles]
    fabric_smoke._await_clients(clients, results, timeout=650)


def _optional_launch(launch, hud_dependencies):
    result = copy.deepcopy(launch)
    jars = hud_dependencies.cached_jars(hud_dependencies.load_lock())
    result.setdefault("jvmArgs", []).append("-Dfabric.addMods=" + __import__("os").pathsep.join(map(str, jars)))
    return result


def _run(w, fabric_smoke, hud_dependencies, launch, root: Path, reports: Path) -> None:
    """Run initial A/B, reconnect A on the same server, then restarted A."""
    server = None
    server_log = None
    clients = []
    try:
        server, server_log = _start_server(w, reports, root, "initial-reconnect", fabric_smoke._stop)
        for role in ("A", "B"):
            clients.append(_client(w, fabric_smoke, launch, root, reports, role, "initial"))
        _await(fabric_smoke, clients, reports, "initial", ("A", "B"))
        _close_clients(clients)
        clients = []

        # This is intentionally before the first save/stop.  It covers the
        # ledger's client reconnect path separately from world persistence.
        reconnect_launch = _optional_launch(launch, hud_dependencies)
        clients.append(_client(w, fabric_smoke, reconnect_launch, root, reports, "A", "reconnect"))
        _await(fabric_smoke, clients, reports, "reconnect", ("A",))
        _close_clients(clients)
        clients = []
    finally:
        _close_clients(clients)
        if server is not None:
            fabric_smoke._stop(server)
        if server_log is not None:
            server_log.close()

    server = None
    server_log = None
    clients = []
    try:
        server, server_log = _start_server(w, reports, root, "restart", fabric_smoke._stop)
        clients.append(_client(w, fabric_smoke, launch, root, reports, "A", "restart"))
        _await(fabric_smoke, clients, reports, "restart", ("A",))
        _close_clients(clients)
        clients = []
        clients.append(_client(w, fabric_smoke, launch, root, reports, "B", "ended"))
        _await(fabric_smoke, clients, reports, "ended", ("B",))
    finally:
        _close_clients(clients)
        if server is not None:
            fabric_smoke._stop(server)
        if server_log is not None:
            server_log.close()


def run() -> None:
    import fabric_smoke
    import hud_dependencies
    import workspace as w

    w.require_eula()
    w.validate_server_runtime()
    w.validate_managed_mod_names()
    w.run([w.REPO / "gradlew.bat", "--no-daemon", ":core:remapJar",
           ":campaigns:quiet-below:remapJar", ":campaigns:quiet-below:writeSmokeClientLaunch"])
    launch = json.loads((w.REPO / "campaigns/quiet-below/build/smoke/client-launch.json").read_text(encoding="utf-8"))
    root = fabric_smoke._stage(w)
    # Only the isolated helper requires the passive account to invoke identity.
    ops_file = root / "ops.json"
    operators = json.loads(ops_file.read_text(encoding="utf-8"))
    operators.append({"uuid": fabric_smoke.offline_uuid("ChronicleFabricB"), "name": "ChronicleFabricB",
                      "level": 2, "bypassesPlayerLimit": False})
    ops_file.write_text(json.dumps(operators, indent=2) + "\n", encoding="utf-8")
    reports = w.REPORTS / ("bargain-smoke-" + root.name)
    reports.mkdir(parents=True)
    report = {"result": "FAIL", "world": str(root), "reports": str(reports), "checks": [], "phases": []}
    try:
        _run(w, fabric_smoke, hud_dependencies, launch, root, reports)
        report["phases"] = [{"phase": "initial", "roles": ["A", "B"]},
                            {"phase": "reconnect", "roles": ["A"], "optionalHud": True},
                            {"phase": "restart", "roles": ["A"]}, {"phase": "ended", "roles": ["B"]}]
        report["checks"].append("initial A/B, optional-HUD reconnect, and persisted restart native clients passed")
        report["result"] = "PASS"
        print("PASS: lasting-bargain native smoke; evidence:", reports, flush=True)
    except BaseException as error:
        report["error"] = str(error)
        raise
    finally:
        reports.mkdir(parents=True, exist_ok=True)
        content = json.dumps(report, indent=2) + "\n"
        (reports / "report.json").write_text(content, encoding="utf-8")
        (w.REPORTS / "bargain-smoke-latest.json").write_text(content, encoding="utf-8")


if __name__ == "__main__":
    run()
