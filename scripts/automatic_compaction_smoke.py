"""Native, opt-in automatic ecology journal-maintenance rehearsal on a disposable world."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re
import socket
import subprocess
import time
import uuid


def _json(path: Path) -> dict:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError("Expected a JSON evidence object: " + path.name)
    return value


def _guard_root(root: Path, reports: Path) -> None:
    if root.is_symlink() or root.is_junction() or root.resolve() != root.absolute():
        raise ValueError("Automatic-maintenance runtime must be a real, unlinked directory")
    if root.parent.name != "test-runs" or not re.fullmatch(r"[0-9]{8}T[0-9]{12,}Z", root.name):
        raise ValueError("Automatic-maintenance runtime must be a timestamped test-runs child")
    expected = root.parent.parent / "reports" / ("automatic-compaction-" + root.name)
    if reports.resolve() != expected or reports.is_symlink() or reports.is_junction():
        raise ValueError("Unexpected automatic-maintenance reports directory")
    properties = dict(line.split("=", 1) for line in (root / "server.properties").read_text(encoding="utf-8").splitlines()
                      if line and not line.startswith(("#", "!")) and "=" in line)
    required = {"server-ip": "127.0.0.1", "server-port": "25575", "online-mode": "false", "level-name": "world"}
    if any(properties.get(key) != value for key, value in required.items()):
        raise ValueError("Automatic-maintenance server must be the owned offline loopback fixture")
    if not (root / ".ecology-auto-maintenance-fixture").is_file():
        raise ValueError("Automatic-maintenance fixture marker is absent")


def _reserve_ports() -> None:
    for kind, port in ((socket.SOCK_STREAM, 25575), (socket.SOCK_DGRAM, 24455)):
        with socket.socket(socket.AF_INET, kind) as probe:
            probe.bind(("127.0.0.1", port))


def _start(w, root: Path, reports: Path, phase: str):
    _guard_root(root, reports)
    _reserve_ports()
    import fabric_smoke
    log_path = reports / ("server-" + phase + ".log")
    handle = log_path.open("w", encoding="utf-8")
    server = None
    try:
        environment = w.env()
        environment["CHRONICLE_ECOLOGY_AUTO_MAINTENANCE_TRIAL"] = "true"
        server = subprocess.Popen(w.server_command(), cwd=root, env=environment, stdin=subprocess.PIPE,
                                  stdout=handle, stderr=subprocess.STDOUT, text=True, encoding="utf-8")
        deadline = time.monotonic() + 240
        while True:
            text = log_path.read_text(encoding="utf-8", errors="replace")
            if "Done (" in text and "Voice chat server started at 127.0.0.1:24455" in text:
                return server, handle
            if server.poll() is not None or time.monotonic() > deadline:
                raise RuntimeError("Automatic-maintenance fixture did not start: " + str(log_path))
            time.sleep(.2)
    except BaseException:
        try:
            fabric_smoke._stop(server)
        finally:
            handle.close()
        raise


def _phase(w, fabric, launch, root: Path, reports: Path, phase: str) -> dict:
    server = client = handle = None
    try:
        server, handle = _start(w, root, reports, phase)
        client = fabric._client(w, launch, root, reports, "AutoMaintenance", "automatic-" + phase, {
            "CHRONICLE_ECOLOGY_AUTO_MAINTENANCE_SMOKE": str(reports),
            "CHRONICLE_ECOLOGY_AUTO_MAINTENANCE_ROOT": str(root),
            "CHRONICLE_ECOLOGY_AUTO_MAINTENANCE_PHASE": phase,
        })
        result = reports / ("client-" + phase + ".json")
        try:
            fabric._await_clients([client], [result], timeout=210)
        except RuntimeError:
            detail = str(_json(result).get("detail", "Client proof failed"))[:500] if result.is_file() else "Client did not produce proof"
            raise RuntimeError(f"Automatic maintenance {phase}: {detail}; evidence: {result}") from None
        proof = _json(result)
        required = {
            "initial": {"verify held barrier rejects mutation and terrain writes", "verify archive, shrink, append and identity",
                        "leave a genuinely pending worker for normal server stop"},
            "restart": {"reload compacted journal through ordinary server startup"},
        }[phase]
        if proof.get("result") != "PASS" or not required.issubset(proof.get("checks", [])):
            raise RuntimeError("Automatic-maintenance client did not finish required assertions: " + phase)
        return proof
    finally:
        try:
            if client is not None:
                process, log = client
                try:
                    if process.poll() is None:
                        process.terminate()
                        process.wait(timeout=15)
                finally:
                    log.close()
        finally:
            try:
                fabric._stop(server)
            finally:
                if handle is not None:
                    handle.close()


def _sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run() -> None:
    import workspace as w
    import fabric_smoke as fabric
    w.require_eula()
    w.validate_server_runtime()
    w.validate_managed_mod_names()
    # Use the ordinary smoke stage/client; no test supplies a ticket or touches a running review world.
    w.run([w.REPO / "gradlew.bat", "--no-daemon", ":core:remapJar", ":campaigns:quiet-below:remapJar",
           ":campaigns:quiet-below:writeSmokeClientLaunch"])
    launch = _json(w.REPO / "campaigns/quiet-below/build/smoke/client-launch.json")
    root = fabric._stage(w)
    reports = w.REPORTS / ("automatic-compaction-" + root.name)
    reports.mkdir(parents=True)
    (root / ".ecology-auto-maintenance-fixture").write_text(str(uuid.uuid4()), encoding="utf-8")
    (root / "ops.json").write_text(json.dumps([{"uuid": fabric.offline_uuid("AutoMaintenance"), "name": "AutoMaintenance",
                                             "level": 2, "bypassesPlayerLimit": False}]), encoding="utf-8")
    report = {"result": "FAIL", "world": str(root), "reports": str(reports),
              "mods": {jar.name: w.digest(root / "mods" / jar.name) for jar in w.artifacts()}, "phases": []}
    try:
        _phase(w, fabric, launch, root, reports, "initial")
        queued = _json(reports / "queued-unload.json")
        if queued.get("result") != "PASS" or not queued.get("busy"):
            raise RuntimeError("Held synthetic unload callback was not exercised")
        post_queue = _json(reports / "post-queue-complete.json")
        if post_queue.get("result") != "PASS" or post_queue.get("busy"):
            raise RuntimeError("Queued callback was not retained through maintenance completion")
        unloaded = _json(reports / "natural-unload.json")
        if unloaded.get("result") != "PASS" or unloaded.get("sourceLoaded") or unloaded.get("busy") or unloaded.get("phase") != "ACTIVE":
            raise RuntimeError("Ordinary active play did not prove natural source unload")
        active_held = _json(reports / "active-held.json")
        if active_held.get("result") != "PASS" or active_held.get("forgeHeat") != 2 or active_held.get("activeHeldTicks", 0) < 20:
            raise RuntimeError("Active maintenance did not preserve the powered Forge for twenty ticks")
        shutdown = _json(reports / "shutdown-complete.json")
        if shutdown.get("result") != "PASS" or shutdown.get("busy") or shutdown.get("completed", 0) < 3:
            raise RuntimeError("Normal stop did not join the pending maintenance worker")
        active = _json(reports / "active-complete.json")
        if (active.get("phase") != "ACTIVE" or active.get("beforeBytes", 0) <= active.get("afterBytes", 0)
                or active.get("beforeRecords", 0) <= active.get("afterRecords", 0)):
            raise RuntimeError("Active route lacks compaction reduction evidence")
        archive = Path(active["archive"])
        if not archive.is_file() or _sha(archive) != active.get("sourceHash"):
            raise RuntimeError("Retained source archive does not match the sealed source hash")
        journal = root / "world/quiet-below/ecology.jsonl"
        before_restart = _sha(journal)
        report["phases"].append("initial-and-clean-stop")
        _phase(w, fabric, launch, root, reports, "restart")
        restart = _json(reports / "restart.json")
        if restart.get("result") != "PASS" or restart.get("busy") or not isinstance(restart.get("engineIdentity"), int):
            raise RuntimeError("Restart did not reopen the compacted journal with an idle engine")
        if _sha(journal) != before_restart:
            raise RuntimeError("Ordinary restart changed the compacted journal without a new maintenance route")
        if list((root / "world/quiet-below").glob("*.prepared")) or list((root / "world/quiet-below").glob("*.snapshot")):
            raise RuntimeError("Maintenance left temporary journal artifacts behind")
        report["phases"].append("ordinary-restart")
        report["result"] = "PASS"
        print("PASS: native automatic ecology journal maintenance rehearsal; evidence:", reports, flush=True)
    except BaseException as error:
        report["error"] = str(error)
        raise
    finally:
        text = json.dumps(report, indent=2) + "\n"
        (reports / "report.json").write_text(text, encoding="utf-8")
        (w.REPORTS / "automatic-compaction-latest.json").write_text(text, encoding="utf-8")


if __name__ == "__main__":
    run()
