"""Disposable CF22 committed-conflict recovery and exact-maximum Event APPLY smoke."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import subprocess

import fabric_smoke
import menu_smoke


def _proof(path: Path) -> dict:
    value = json.loads(path.read_text(encoding="utf-8"))
    if value.get("result") != "PASS":
        raise RuntimeError(json.dumps(value, indent=2))
    return value


def _stage(w, *, maximum: bool = False) -> Path:
    root = fabric_smoke._stage(w)
    fabric_smoke._loot_fixture(root)
    (root / ".event-actions-fixture").write_text(
        "Disposable CF22 Event actions fixture\n", encoding="utf-8")
    (root / ".event-recovery-fixture").write_text(
        "Disposable CF22 Event recovery and volume fixture\n", encoding="utf-8")
    operators = [
        {"uuid": fabric_smoke.offline_uuid(name), "name": name,
         "level": 2, "bypassesPlayerLimit": False}
        for name in ("ChronicleRecA", "ChronicleRecB")
    ]
    (root / "ops.json").write_text(json.dumps(operators, indent=2), encoding="utf-8")
    config = root / "config" / "chronicle"
    config.mkdir(parents=True, exist_ok=True)
    (config / "books.json").write_text(json.dumps({"schemaVersion": 1, "books": [{
        "id": "test:event_managed", "title": "Event evidence", "author": "Fixture",
        "description": "Managed lore removed by aggregate Event action",
        "pages": ["Managed Event evidence."], "rule": "OFF",
    }]}), encoding="utf-8")
    if maximum:
        properties = root / "server.properties"
        lines = ["view-distance=10" if line.startswith("view-distance=") else line
                 for line in properties.read_text(encoding="utf-8").splitlines()]
        properties.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return root


def _start(w, root: Path, reports: Path, label: str, env: dict[str, str]):
    menu_smoke._reserve_ports()
    path = reports / f"server-{label}.log"
    handle = path.open("w", encoding="utf-8")
    process = subprocess.Popen(
        w.server_command(), cwd=root, env=env, stdin=subprocess.PIPE,
        stdout=handle, stderr=subprocess.STDOUT, text=True, encoding="utf-8",
    )
    menu_smoke._wait_ready(process, path)
    return process, handle


def _client(w, launch: dict, root: Path, reports: Path, phase: str, name: str):
    result = reports / f"{phase}-{name}.json"
    process = fabric_smoke._client(w, launch, root, reports, name, f"event-recovery-{phase}", {
        "CHRONICLE_EVENT_RECOVERY_SMOKE": str(result),
        "CHRONICLE_EVENT_RECOVERY_SERVER_DIR": str(root),
        "CHRONICLE_EVENT_RECOVERY_PHASE": phase,
        "CHRONICLE_MENU_WIDTH": "960",
        "CHRONICLE_MENU_HEIGHT": "540",
    })
    return process, result


def _close_client(client) -> None:
    if client is not None:
        client[1].close()


def _terminate_client(client) -> None:
    if client is None:
        return
    process, handle = client
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=15)
    handle.close()


def run(only: str = "all") -> None:
    import workspace as w

    w.require_eula()
    w.validate_server_runtime()
    w.validate_managed_mod_names()
    w.run([w.REPO / "gradlew.bat", "--no-daemon", ":core:remapJar",
           ":campaigns:quiet-below:remapJar", ":campaigns:quiet-below:writeSmokeClientLaunch"])
    launch = json.loads(
        (w.REPO / "campaigns/quiet-below/build/smoke/client-launch.json").read_text(encoding="utf-8"))
    root = _stage(w) if only in {"all", "conflict"} else None
    maximum_root = _stage(w, maximum=True) if only == "maximum" else None
    report_key = root.name if root is not None else maximum_root.name
    reports = w.REPORTS / ("event-recovery-" + report_key)
    reports.mkdir(parents=True)
    report = {
        "result": "FAIL",
        "world": str(root) if root is not None else None,
        "reports": str(reports),
        "checks": [],
        "limitations": [
            "This suite injects a live foreign-cell conflict; abrupt-process recovery remains covered by the separate Event actions suite.",
            "Registered sound dispatch is exercised, but automated evidence does not establish human audibility.",
            "The two worlds are disposable generated fixtures, not the organizer's handcrafted campaign map.",
        ],
    }
    env = w.env()
    env["CHRONICLE_EVENTS_TRIAL"] = "true"
    env["CHRONICLE_EVENT_RECOVERY_TRIAL"] = "true"
    server = log = None
    clients = []
    try:
        if only in {"all", "conflict"}:
            server, log = _start(w, root, reports, "conflict-recovery", env)
            conflict, conflict_result = _client(
                w, launch, root, reports, "conflict", "ChronicleRecA")
            clients = [conflict]
            fabric_smoke._await_clients(clients, [conflict_result], timeout=600)
            report["checks"].extend(_proof(conflict_result).get("checks", []))
            _close_client(conflict)
            clients = []

            recovery, recovery_result = _client(
                w, launch, root, reports, "recovery", "ChronicleRecB")
            clients = [recovery]
            fabric_smoke._await_clients(clients, [recovery_result], timeout=600)
            report["checks"].extend(_proof(recovery_result).get("checks", []))
            _close_client(recovery)
            clients = []
            fabric_smoke._stop(server)
            server = None
            log.close()
            log = None
            report["checks"].append("conflict/recovery world stopped cleanly after exact completion")

        if only in {"all", "maximum"}:
            if maximum_root is None:
                maximum_root = _stage(w, maximum=True)
            report["maximum_world"] = str(maximum_root)
            server, log = _start(w, maximum_root, reports, "maximum-apply", env)
            maximum, maximum_result = _client(
                w, launch, maximum_root, reports, "maximum", "ChronicleRecA")
            clients = [maximum]
            fabric_smoke._await_clients(clients, [maximum_result], timeout=1_200)
            report["checks"].extend(_proof(maximum_result).get("checks", []))
            _close_client(maximum)
            clients = []
            fabric_smoke._stop(server)
            server = None
            log.close()
            log = None

        required = []
        if only in {"all", "conflict"}:
            required.extend(("target 0 durably saved", "distinct live OP", "same UUID"))
        if only in {"all", "maximum"}:
            required.extend(("262144 cells", "64 player-loaded chunks", "no forced chunks"))
        missing = [needle for needle in required
                   if not any(needle in check for check in report["checks"])]
        if missing:
            raise RuntimeError("Event recovery smoke ended before required evidence: " + repr(missing))
        report["result"] = "PASS"
        print("PASS: Event recovery/maximum native smoke; evidence:", reports, flush=True)
    except BaseException as error:
        report["error"] = str(error)
        raise
    finally:
        for client in clients:
            _terminate_client(client)
        try:
            fabric_smoke._stop(server)
        finally:
            if log is not None:
                log.close()
            content = json.dumps(report, indent=2) + "\n"
            (reports / "report.json").write_text(content, encoding="utf-8")
            (w.REPORTS / "event-recovery-latest.json").write_text(content, encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--only", choices=("all", "conflict", "maximum"), default="all",
                        help="run both proofs, only committed conflict/recovery, or only full maximum APPLY")
    run(parser.parse_args().only)
