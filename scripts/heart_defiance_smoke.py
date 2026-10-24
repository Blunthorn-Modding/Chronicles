"""Native, real-time proof for the Heart's one-per-cooldown last stand."""
from __future__ import annotations

import json
import socket
import subprocess
import time
from pathlib import Path


def _reserve_ports() -> None:
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 25575))
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
        probe.bind(("127.0.0.1", 24455))


def _normal_difficulty(root: Path) -> None:
    path = root / "server.properties"
    text = path.read_text(encoding="utf-8")
    if "difficulty=peaceful" not in text:
        raise RuntimeError("Disposable server did not retain the expected stage difficulty setting")
    path.write_text(text.replace("difficulty=peaceful", "difficulty=normal", 1), encoding="utf-8")


def _start(w, root: Path, report_dir: Path, phase: str):
    _reserve_ports()
    log_path = report_dir / ("server-" + phase + ".log")
    handle = log_path.open("w", encoding="utf-8")
    environment = w.env()
    environment["CHRONICLE_HEART_DEFIANCE_TRIAL"] = "true"
    server = subprocess.Popen(w.server_command(), cwd=root, env=environment, stdin=subprocess.PIPE,
                              stdout=handle, stderr=subprocess.STDOUT, text=True, encoding="utf-8")
    deadline = time.monotonic() + 240
    while True:
        content = log_path.read_text(encoding="utf-8", errors="replace") if log_path.exists() else ""
        if "Done (" in content and "Voice chat server started at 127.0.0.1:24455" in content:
            return server, handle
        if server.poll() is not None or time.monotonic() > deadline:
            handle.close()
            raise RuntimeError("Heart defiance test server failed startup: " + str(log_path))
        time.sleep(.2)


def _client(w, launch, root: Path, reports: Path, phase: str):
    import fabric_smoke
    return fabric_smoke._client(w, launch, root, reports, "HeartDefiance", "heart-defiance-" + phase, {
        "CHRONICLE_HEART_DEFIANCE_SMOKE": str(reports),
        "CHRONICLE_HEART_DEFIANCE_PHASE": phase,
        "CHRONICLE_HEART_DEFIANCE_SERVER_DIR": str(root),
    })


def run() -> None:
    import fabric_smoke
    import workspace as w

    w.require_eula()
    w.validate_server_runtime()
    w.validate_managed_mod_names()
    w.run([w.REPO / "gradlew.bat", "--no-daemon", ":core:remapJar", ":campaigns:quiet-below:remapJar",
           ":campaigns:quiet-below:writeSmokeClientLaunch"])
    launch = json.loads((w.REPO / "campaigns/quiet-below/build/smoke/client-launch.json").read_text(encoding="utf-8"))
    root = fabric_smoke._stage(w)
    _normal_difficulty(root)
    reports = w.REPORTS / ("heart-defiance-" + root.name)
    reports.mkdir(parents=True)
    player = "HeartDefiance"
    player_uuid = fabric_smoke.offline_uuid(player)
    (root / "ops.json").write_text(json.dumps([{"uuid": player_uuid, "name": player, "level": 2,
                                                   "bypassesPlayerLimit": False}], indent=2) + "\n", encoding="utf-8")
    report = {"result": "FAIL", "world": str(root), "reports": str(reports), "checks": []}
    server = None
    handle = None
    client = None
    try:
        for phase in ("initial", "restart"):
            server, handle = _start(w, root, reports, phase)
            client = _client(w, launch, root, reports, phase)
            result = reports / ("heart-defiance-" + phase + ".json")
            fabric_smoke._await_clients([client], [result], timeout=300 if phase == "initial" else 90)
            client[1].close()
            parsed = json.loads(result.read_text(encoding="utf-8"))
            if parsed.get("result") != "PASS":
                raise RuntimeError("Heart defiance " + phase + " client failed: " + str(parsed))
            required = {"verify warning has no durable claim or effects", "recut after the aborted warning", "verify first durable last stand", "verify cooldown suppressed all second effects"} if phase == "initial" else {"verify persisted defiance receipt after restart"}
            if not required.issubset(parsed.get("checks", [])):
                raise RuntimeError("Heart defiance " + phase + " lacked required proof steps")
            fabric_smoke._stop(server)
            server = None
            handle.close()
            handle = None
            client = None
        report["checks"] = ["fresh timestamped offline world only", "256-cell source reached protected proof", "interrupted warning emitted one heartbeat without a durable cooldown claim, Warden or burst", "reconnect reset the warning cycle before its final effort", "second cut made the first actual 50-second last stand with one Warden and all24 eligible surface columns of a5x5 patch around the Heart", "receipt cooldown begins at final-effort claim", "physical reconnect then recut remained suppressed during persisted cooldown", "same world restart retained receipt and did not replay last stand"]
        report["result"] = "PASS"
        print("PASS: real-time Heart defiance proof completed; evidence:", reports, flush=True)
    except BaseException as error:
        report["error"] = str(error)
        raise
    finally:
        if client is not None:
            process, client_log = client
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=15)
            client_log.close()
        try:
            fabric_smoke._stop(server)
        finally:
            if handle is not None:
                handle.close()
            content = json.dumps(report, indent=2) + "\n"
            (reports / "report.json").write_text(content, encoding="utf-8")
            (w.REPORTS / "heart-defiance-latest.json").write_text(content, encoding="utf-8")


if __name__ == "__main__":
    run()
