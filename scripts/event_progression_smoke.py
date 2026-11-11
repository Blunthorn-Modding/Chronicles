"""Fresh loopback Event flag/countdown runtime proof, including real JVM restarts."""
from __future__ import annotations

import json
from pathlib import Path
import time

import events_smoke
import fabric_smoke


def _stage(w) -> Path:
    root = fabric_smoke._stage(w)
    fabric_smoke._loot_fixture(root)
    (root / ".event-progression-fixture").write_text("Disposable Event progression runtime fixture\n", encoding="utf-8")
    (root / "ops.json").write_text(json.dumps([{
        "uuid": fabric_smoke.offline_uuid("ChronProgress"), "name": "ChronProgress",
        "level": 2, "bypassesPlayerLimit": False,
    }], indent=2), encoding="utf-8")
    return root


def _client(w, launch, root: Path, reports: Path, phase: str):
    result = reports / f"{phase}.json"
    client = fabric_smoke._client(w, launch, root, reports, "ChronProgress", f"progression-{phase}", {
        "CHRONICLE_EVENT_PROGRESSION_SMOKE": str(result),
        "CHRONICLE_EVENT_PROGRESSION_SERVER_DIR": str(root),
        "CHRONICLE_EVENT_PROGRESSION_PHASE": phase,
        "CHRONICLE_MENU_WIDTH": "640", "CHRONICLE_MENU_HEIGHT": "360",
    })
    return client, result


def _snapshot(root: Path, reports: Path, label: str) -> dict:
    """Keep raw disk checkpoints; no parser result replaces the original evidence."""
    snapshots = reports / f"disk-{label}"
    snapshots.mkdir()
    for name in ("campaign-state.json", "play-runtime.json", "event-progress.json"):
        source = root / "world" / "chronicle" / name
        payload = source.read_bytes()
        (snapshots / name).write_bytes(payload)
        json.loads(payload)
    marker = root / ".event-progression-trial.json"
    if marker.exists():
        payload = marker.read_bytes()
        (snapshots / "trial.json").write_bytes(payload)
        return json.loads(payload)
    return {}


def run() -> None:
    import workspace as w
    w.require_eula()
    w.validate_server_runtime()
    w.validate_managed_mod_names()
    w.run([w.REPO / "gradlew.bat", "--no-daemon", ":core:remapJar", ":campaigns:quiet-below:remapJar",
           ":campaigns:quiet-below:writeSmokeClientLaunch"])
    launch = json.loads((w.REPO / "campaigns/quiet-below/build/smoke/client-launch.json").read_text(encoding="utf-8"))
    root = _stage(w)
    reports = w.REPORTS / ("event-progression-" + root.name)
    reports.mkdir(parents=True)
    report = {"result": "FAIL", "world": str(root), "reports": str(reports), "checks": [], "phases": [],
              "limitations": ["This suite proves configured flag/countdown runtime behavior. Real book, block, departure, prayer and forge source hooks have separate verification.",
                              "The interruption uses an explicit test-only hard halt after forced campaign receipt persistence; arbitrary disk or power faults are not simulated."]}
    env = w.env()
    env["CHRONICLE_EVENT_PROGRESSION_TRIAL"] = "true"
    server = log = None
    clients = []
    try:
        for phase in ("basic", "restart", "crash", "recover", "final"):
            phase_env = dict(env)
            if phase == "crash":
                phase_env["CHRONICLE_EVENT_PROGRESSION_HALT_AFTER_FLAGS"] = "true"
            else:
                phase_env.pop("CHRONICLE_EVENT_PROGRESSION_HALT_AFTER_FLAGS", None)
            server, log = events_smoke._start(w, root, reports, phase, phase_env)
            native, result = _client(w, launch, root, reports, phase)
            clients = [native]
            if phase == "crash":
                server.wait(timeout=180)
                if server.returncode != 99:
                    raise RuntimeError(f"flag outcome seam did not hard halt before phase advance: {server.returncode}")
                server = None
                log.close()
                log = None
                events_smoke._terminate_client(native)
                clients = []
                marker_path = root / ".event-progression-halt.json"
                marker = json.loads(marker_path.read_text(encoding="utf-8"))
                (reports / "halt.json").write_bytes(marker_path.read_bytes())
                if marker.get("phase") != "EVENT_OUTCOME":
                    raise RuntimeError("halt did not preserve original Event outcome phase")
                _snapshot(root, reports, phase)
                report["checks"].append("actual JVM halted after durable atomic flag receipt and later GM override, before play phase advance")
                report["phases"].append({"phase": phase, "halt": marker})
                continue
            fabric_smoke._await_clients(clients, [result], timeout=300)
            proof = events_smoke._proof(result)
            report["checks"].extend(proof.get("checks", []))
            native[1].close()
            clients = []
            fabric_smoke._stop(server)
            server = None
            log.close()
            log = None
            checkpoint = _snapshot(root, reports, phase)
            report["phases"].append({"phase": phase, "clock": checkpoint.get("clock"),
                                     "restartStart": checkpoint.get("restartStart"), "result": str(result)})
            # Deliberate stopped time must never count as occupied play time.
            if phase == "basic":
                time.sleep(3)
        required = ("Region ENTRY", "100 occupied-tick", "old deadline", "next eligible turn", "zero-audience",
                    "Building Mode", "no eligible players", "actual restart", "later GM override", "same operation")
        missing = [text for text in required if not any(text in check for check in report["checks"])]
        if missing:
            raise RuntimeError("missing required runtime evidence: " + repr(missing))
        report["result"] = "PASS"
        print("PASS: native Event progression proof; evidence:", reports, flush=True)
    except BaseException as failure:
        report["error"] = str(failure)
        raise
    finally:
        for client in clients:
            events_smoke._terminate_client(client)
        try:
            fabric_smoke._stop(server)
        finally:
            if log is not None:
                log.close()
            payload = json.dumps(report, indent=2) + "\n"
            (reports / "report.json").write_text(payload, encoding="utf-8")
            (w.REPORTS / "event-progression-latest.json").write_text(payload, encoding="utf-8")


if __name__ == "__main__":
    run()
