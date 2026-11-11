"""Disposable loopback CF20 native departure proof; never targets a review or play world."""
from __future__ import annotations

import json
import subprocess

import fabric_smoke
import menu_smoke


def _proof(path):
    value = json.loads(path.read_text(encoding="utf-8"))
    if value.get("result") != "PASS":
        raise RuntimeError(json.dumps(value, indent=2))
    return value


def _client(w, launch, root, reports, role, phase, width, height):
    result = reports / f"{phase}-{role}.json"
    client = fabric_smoke._client(w, launch, root, reports, "ChronicleDepart" + role,
                                  "departures-" + phase + "-" + role, {
        "CHRONICLE_DEPARTURES_SMOKE": str(result),
        "CHRONICLE_DEPARTURES_SERVER_DIR": str(root),
        "CHRONICLE_DEPARTURES_PHASE": phase,
        "CHRONICLE_DEPARTURES_ROLE": role,
        "CHRONICLE_MENU_WIDTH": str(width),
        "CHRONICLE_MENU_HEIGHT": str(height),
    })
    return client, result


def run() -> None:
    import workspace as w
    w.require_eula()
    w.validate_server_runtime()
    w.validate_managed_mod_names()
    w.run([w.REPO / "gradlew.bat", "--no-daemon", ":core:remapJar", ":campaigns:quiet-below:remapJar",
           ":campaigns:quiet-below:writeSmokeClientLaunch"])
    launch = json.loads((w.REPO / "campaigns/quiet-below/build/smoke/client-launch.json").read_text(encoding="utf-8"))
    root = fabric_smoke._stage(w)
    root.joinpath(".departures-fixture").write_text("Disposable CF20 departures fixture\n", encoding="utf-8")
    root.joinpath("ops.json").write_text(json.dumps([{
        "uuid": fabric_smoke.offline_uuid("ChronicleDepartA"), "name": "ChronicleDepartA",
        "level": 2, "bypassesPlayerLimit": False,
    }], indent=2), encoding="utf-8")
    reports = w.REPORTS / ("departures-" + root.name)
    reports.mkdir(parents=True)
    report = {
        "result": "FAIL", "world": str(root), "recoveryWorld": None, "reports": str(reports), "checks": [],
        "limitations": [
            "Staggered recovery uses a clean stop after A's receipt, not a forced process kill during a filesystem write.",
            "Operator /gamemode is administrative authority and is not claimed as blocked; ordinary reconnect is enforced.",
        ],
    }
    env = w.env()
    env["CHRONICLE_DEPARTURES_TRIAL"] = "true"
    server = None
    log = None
    clients = []
    try:
        for phase in ("initial", "restart"):
            menu_smoke._reserve_ports()
            log_path = reports / ("server-" + phase + ".log")
            log = log_path.open("w", encoding="utf-8")
            server = subprocess.Popen(w.server_command(), cwd=root, env=env, stdin=subprocess.PIPE,
                                      stdout=log, stderr=subprocess.STDOUT, text=True, encoding="utf-8")
            menu_smoke._wait_ready(server, log_path)
            report["checks"].append(phase + ": isolated offline server and voice endpoints bound to loopback 25575/24455")
            launched = [_client(w, launch, root, reports, role, phase, 1280, 720) for role in ("A", "B")]
            clients = [entry[0] for entry in launched]
            results = [entry[1] for entry in launched]
            fabric_smoke._await_clients(clients, results, timeout=300)
            for result in results:
                proof = _proof(result)
                report["checks"].extend(phase + "/" + proof["role"] + ": " + check for check in proof.get("checks", []))
            for _, handle in clients:
                handle.close()
            clients = []
            fabric_smoke._stop(server)
            server = None
            log.close()
            log = None
            report["checks"].append(phase + ": clean disposable server save/stop")

        # A separate fresh world proves a committed two-passenger operation can
        # finish across mutually exclusive reconnects. No test code calls the
        # production recovery driver; ordinary ticks and JOIN handling do it.
        recovery_root = fabric_smoke._stage(w)
        recovery_root.joinpath(".departures-fixture").write_text("Disposable CF20 staggered recovery fixture\n", encoding="utf-8")
        recovery_root.joinpath("ops.json").write_text(json.dumps([{
            "uuid": fabric_smoke.offline_uuid("ChronicleDepart" + role), "name": "ChronicleDepart" + role,
            "level": 2, "bypassesPlayerLimit": False,
        } for role in ("A", "B", "C")], indent=2), encoding="utf-8")
        report["recoveryWorld"] = str(recovery_root)
        for phase, role in (("recovery-a", "A"), ("recovery-b", "B"), ("recovery-c", "C")):
            menu_smoke._reserve_ports()
            log_path = reports / ("server-" + phase + ".log")
            log = log_path.open("w", encoding="utf-8")
            server = subprocess.Popen(w.server_command(), cwd=recovery_root, env=env, stdin=subprocess.PIPE,
                                      stdout=log, stderr=subprocess.STDOUT, text=True, encoding="utf-8")
            menu_smoke._wait_ready(server, log_path)
            launched_client, result = _client(w, launch, recovery_root, reports, role, phase, 1280, 720)
            clients = [launched_client]
            fabric_smoke._await_clients(clients, [result], timeout=300)
            proof = _proof(result)
            report["checks"].extend(phase + "/" + role + ": " + check for check in proof.get("checks", []))
            clients[0][1].close()
            clients = []
            fabric_smoke._stop(server)
            server = None
            log.close()
            log = None
            report["checks"].append(phase + ": clean save/stop with only passenger " + role + " online")

        required = ("exact A+B linked departure", "passenger B became Spectator", "durable departure enforced Spectator",
                    "server retained exact departure flags", "operation remained COMMITTED", "completed no-Area/no-conclusion recovery",
                    "C-only native two-confirm departure concluded")
        missing = [needle for needle in required if not any(needle in check for check in report["checks"])]
        if missing:
            raise RuntimeError("Departure smoke ended before required evidence: " + repr(missing))
        report["result"] = "PASS"
        print("PASS: native departure smoke; evidence:", reports, flush=True)
    except BaseException as error:
        report["error"] = str(error)
        raise
    finally:
        for process, handle in clients:
            if process.poll() is None:
                process.terminate()
            handle.close()
        fabric_smoke._stop(server)
        if log is not None:
            log.close()
        content = json.dumps(report, indent=2) + "\n"
        (reports / "report.json").write_text(content, encoding="utf-8")
        (w.REPORTS / "departures-latest.json").write_text(content, encoding="utf-8")


if __name__ == "__main__":
    run()
