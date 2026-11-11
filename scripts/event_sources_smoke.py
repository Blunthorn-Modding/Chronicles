"""Disposable native block/book source dispatch and exact uncertainty recovery proof.

Station, departure, Region and ecology suites can arm/verify additional real-source
probes through the guarded chronicle_event_sources_trial arm/verify commands.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import events_smoke
import fabric_smoke


def _stage(w) -> Path:
    root = fabric_smoke._stage(w)
    fabric_smoke._loot_fixture(root)
    (root / ".event-sources-fixture").write_text("Disposable Event source provider fixture\n", encoding="utf-8")
    (root / "ops.json").write_text(json.dumps([{
        "uuid": fabric_smoke.offline_uuid("ChronSources"), "name": "ChronSources",
        "level": 2, "bypassesPlayerLimit": False,
    }], indent=2), encoding="utf-8")
    config = root / "config" / "chronicle"
    config.mkdir(parents=True, exist_ok=True)
    (config / "books.json").write_text(json.dumps({"schemaVersion": 1, "books": [{
        "id": identifier, "title": "Source book", "author": "Fixture", "description": "Native source identity proof",
        "pages": ["Actual managed book identity is separate from its display title."], "rule": "OFF",
    } for identifier in ("test:source_book", "test:source_wrong", "test:source_recovery_book")]}), encoding="utf-8")
    return root


def run(only: str = "all", world: Path | None = None) -> None:
    import workspace as w
    allowed = ("basic", "recovery", "stations", "departure", "regions")
    if only not in ("all", *allowed):
        raise ValueError("Unknown Event source phase")
    if only == "recovery" and world is None:
        raise ValueError("Recovery requires --world from a completed basic phase")
    if world is not None:
        world = world.resolve()
        if world.parent.name != "test-runs" or not (world / ".event-sources-fixture").is_file():
            raise ValueError("--world must identify a disposable Event sources fixture")
    w.require_eula()
    w.validate_server_runtime()
    w.validate_managed_mod_names()
    w.run([w.REPO / "gradlew.bat", "--no-daemon", ":core:remapJar", ":campaigns:quiet-below:remapJar",
           ":campaigns:quiet-below:writeSmokeClientLaunch"])
    launch = json.loads((w.REPO / "campaigns/quiet-below/build/smoke/client-launch.json").read_text(encoding="utf-8"))
    root = world if world is not None else _stage(w)
    reports = w.REPORTS / ("event-sources-" + root.name)
    if reports.exists():
        reports = w.REPORTS / ("event-sources-" + root.name + "-" + only + "-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ"))
    reports.mkdir(parents=True)
    report = {"result": "FAIL", "world": str(root), "reports": str(reports), "checks": [], "phases": [],
              "limitations": ["This script covers block interaction/open/break/place, managed book identity/discovery/opening, source recovery, actual prayer/Forge, Established and Heart destruction.",
                              "The departure phase drives real native confirmation UI, its linked Region apply, silent ENDED finalization and skipping mixed effects. Region phase drives direct Area and aggregate Event batch sources.",
                              "The Extensive ecology threshold and a capped unchanged Forge result have separate pure/baseline checks; this script does not drive those native source negatives.",
                              "The recovery seam saves explicit PREPARED evidence without inventing a successful gameplay outcome; the GM then reviews an uncertain acknowledgement/cancellation.",
                              "Product book editor preview exclusion is covered separately; no client editor preview is driven here."]}
    env = w.env()
    env["CHRONICLE_EVENT_SOURCES_TRIAL"] = "true"
    server = log = None
    clients = []
    try:
        for phase in (allowed if only == "all" else (only,)):
            phase_root = _stage(w) if only == "all" and phase in {"stations", "departure", "regions"} else root
            phase_env = dict(env)
            if phase == "stations":
                phase_env["CHRONICLE_FORGE_NATIVE_TRIAL"] = "true"
            server, log = events_smoke._start(w, phase_root, reports, phase, phase_env)
            result = reports / f"{phase}.json"
            client = fabric_smoke._client(w, launch, phase_root, reports, "ChronSources", f"sources-{phase}", {
                "CHRONICLE_EVENT_SOURCES_SMOKE": str(result), "CHRONICLE_EVENT_SOURCES_SERVER_DIR": str(phase_root),
                "CHRONICLE_EVENT_SOURCES_PHASE": phase, "CHRONICLE_MENU_WIDTH": "640", "CHRONICLE_MENU_HEIGHT": "360",
            })
            clients = [client]
            fabric_smoke._await_clients(clients, [result], timeout=240)
            proof = events_smoke._proof(result)
            report["checks"].extend(proof.get("checks", []))
            report["phases"].append({"phase": phase, "world": str(phase_root), "result": str(result)})
            client[1].close()
            clients = []
            fabric_smoke._stop(server)
            server = None
            log.close()
            log = None
            snapshot = reports / f"disk-{phase}"
            snapshot.mkdir()
            for name in ("campaign-state.json", "play-runtime.json", "event-progress.json", "event-sources.bin"):
                (snapshot / name).write_bytes((phase_root / "world" / "chronicle" / name).read_bytes())
        report["result"] = "PASS"
        print("PASS: native Event block/book sources and exact recovery; evidence:", reports, flush=True)
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
            (w.REPORTS / "event-sources-latest.json").write_text(payload, encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--only", choices=("all", "basic", "recovery", "stations", "departure", "regions"), default="all")
    parser.add_argument("--world", type=Path, help="Existing disposable source world; required for targeted recovery")
    arguments = parser.parse_args()
    run(arguments.only, arguments.world)
