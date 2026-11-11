"""Abrupt-stop and natural-unload recovery proof on one owned offline fixture."""
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
        raise ValueError("Expected a recovery proof object: " + path.name)
    return value


def _guard_root(root: Path, reports: Path) -> None:
    root = Path(root)
    if root.is_symlink() or root.is_junction() or root.resolve() != root.absolute():
        raise ValueError("Recovery runtime must be a real, unlinked directory")
    if root.parent.name != "test-runs" or not re.fullmatch(r"[0-9]{8}T[0-9]{12,}Z", root.name):
        raise ValueError("Recovery runtime must be a timestamped test-runs child")
    expected = root.parent.parent / "reports" / ("eradication-recovery-" + root.name)
    if reports.resolve() != expected or reports.is_symlink() or reports.is_junction():
        raise ValueError("Unexpected recovery reports directory")
    properties = dict(line.split("=", 1) for line in (root / "server.properties").read_text(encoding="utf-8").splitlines()
                      if line and not line.startswith(("#", "!")) and "=" in line)
    if any(properties.get(key) != value for key, value in {
            "server-ip": "127.0.0.1", "server-port": "25575", "online-mode": "false", "level-name": "world"}.items()):
        raise ValueError("Recovery server must be the dedicated offline loopback fixture")
    if not (root / ".eradication-recovery-fixture").is_file():
        raise ValueError("Recovery fixture marker is absent")


def _force_crash(server, root: Path, reports: Path) -> dict:
    """Only called with the private Popen created by _start, never an external PID."""
    _guard_root(root, reports)
    saved, ready = _json(reports / "save-point.json"), _json(reports / "crash-ready.json")
    fields = ("total", "savedCompleted", "unsavedCompleted", "maxDelta")
    if any(type(ready.get(field)) is not int for field in fields):
        raise ValueError("Crash checkpoint lacks integer progress evidence")
    if saved.get("result") != "PASS" or ready.get("result") != "PASS" or saved.get("chunksFlushed") is not True:
        raise ValueError("Partial chunk flush was not verified")
    if saved.get("phase") != "PAUSED" or ready.get("phase") != "PAUSED":
        raise ValueError("Crash checkpoint must be paused after partial cleanup")
    if not (0 < ready["savedCompleted"] < ready["unsavedCompleted"] < ready["total"] and ready["total"] >= 4098):
        raise ValueError("Crash must interrupt genuinely partial work beyond a saved checkpoint")
    if ready["maxDelta"] > 4 or ready["maxDelta"] < 1:
        raise ValueError("Cleanup mutation budget was not proved")
    if saved.get("savedCompleted") != ready["savedCompleted"] or saved.get("total") != ready["total"]:
        raise ValueError("Crash checkpoint does not match saved progress")
    if saved.get("job") != ready.get("job"):
        raise ValueError("Crash checkpoint belongs to another terminal job")
    uuid.UUID(ready["job"])
    if server.poll() is not None:
        raise RuntimeError("Fixture server exited before its intentional crash")
    server.kill()
    server.wait(timeout=20)
    if server.returncode == 0:
        raise RuntimeError("Forced termination unexpectedly returned success")
    return ready


def _reserve_ports() -> None:
    for kind, port in ((socket.SOCK_STREAM, 25575), (socket.SOCK_DGRAM, 24455)):
        with socket.socket(socket.AF_INET, kind) as probe:
            probe.bind(("127.0.0.1", port))


def _start(w, root: Path, reports: Path, phase: str):
    import fabric_smoke
    _guard_root(root, reports)
    _reserve_ports()
    log_path = reports / ("server-" + phase + ".log")
    handle = log_path.open("w", encoding="utf-8")
    server = None
    try:
        environment = w.env()
        environment["CHRONICLE_ERADICATION_RECOVERY_TRIAL"] = "true"
        server = subprocess.Popen(w.server_command(), cwd=root, env=environment, stdin=subprocess.PIPE,
                                  stdout=handle, stderr=subprocess.STDOUT, text=True, encoding="utf-8")
        deadline = time.monotonic() + 240
        while True:
            text = log_path.read_text(encoding="utf-8", errors="replace")
            if "Done (" in text and "Voice chat server started at 127.0.0.1:24455" in text:
                return server, handle
            if server.poll() is not None or time.monotonic() > deadline:
                raise RuntimeError("Recovery fixture did not start: " + str(log_path))
            time.sleep(.2)
    except BaseException:
        try:
            fabric_smoke._stop(server)
        finally:
            handle.close()
        raise


def _phase(w, fabric, launch, root: Path, reports: Path, phase: str) -> dict | None:
    server = client = handle = None
    try:
        server, handle = _start(w, root, reports, phase)
        client = fabric._client(w, launch, root, reports, "EradRecovery", "recovery-" + phase, {
            "CHRONICLE_ERADICATION_RECOVERY_SMOKE": str(reports),
            "CHRONICLE_ERADICATION_RECOVERY_ROOT": str(root),
            "CHRONICLE_ERADICATION_RECOVERY_PHASE": phase,
        })
        path = reports / ("client-" + phase + ".json")
        fabric._await_clients([client], [path], timeout=330)
        proof = _json(path)
        required = {
            "initial": {"save partial receipts with completed and pending foreign edits", "reach partial unsaved crash checkpoint"},
            "recover": {"verify terminal state and receipt terrain agreement after crash", "prove natural unload holds unfinished cleanup", "finish cleanup and preserve a later completed target edit"},
            "final": {"verify every restored target and later edit after final restart"},
        }[phase]
        if proof.get("result") != "PASS" or not required.issubset(proof.get("checks", [])):
            raise RuntimeError("Recovery phase did not reach all required assertions: " + phase)
        if phase == "initial":
            return _force_crash(server, root, reports)
        return None
    finally:
        try:
            if client is not None:
                process, log = client
                try:
                    if process.poll() is None:
                        process.terminate()
                        try:
                            process.wait(timeout=15)
                        except subprocess.TimeoutExpired:
                            process.kill()
                            process.wait(timeout=15)
                finally:
                    log.close()
        finally:
            try:
                fabric._stop(server)
            finally:
                if handle is not None:
                    handle.close()


def run() -> None:
    import workspace as w
    import fabric_smoke as fabric
    w.require_eula()
    w.validate_server_runtime()
    w.validate_managed_mod_names()
    w.run([w.REPO / "gradlew.bat", "--no-daemon", ":core:remapJar", ":campaigns:quiet-below:remapJar",
           ":campaigns:quiet-below:writeSmokeClientLaunch"])
    launch = _json(w.REPO / "campaigns/quiet-below/build/smoke/client-launch.json")
    root = fabric._stage(w)
    reports = w.REPORTS / ("eradication-recovery-" + root.name)
    reports.mkdir(parents=True)
    (root / ".eradication-recovery-fixture").write_text(str(uuid.uuid4()), encoding="utf-8")
    (root / "ops.json").write_text(json.dumps([{"uuid": fabric.offline_uuid("EradRecovery"), "name": "EradRecovery",
                                             "level": 2, "bypassesPlayerLimit": False}]), encoding="utf-8")
    report = {"result": "FAIL", "world": str(root), "reports": str(reports), "phases": []}
    try:
        report["crashCheckpoint"] = _phase(w, fabric, launch, root, reports, "initial")
        journal = root / "world/quiet-below/ecology.jsonl"
        terminal_hash = hashlib.sha256(journal.read_bytes()).hexdigest()
        report["terminalJournalSha256"] = terminal_hash
        report["phases"].append("intentional-crash")
        for phase in ("recover", "final"):
            _phase(w, fabric, launch, root, reports, phase)
            if hashlib.sha256(journal.read_bytes()).hexdigest() != terminal_hash:
                raise RuntimeError("Terminal journal changed after crash recovery: " + phase)
            report["phases"].append(phase)
        unloaded = _json(reports / "unloaded.json")
        if unloaded.get("occupiedUnloadedTicks", 0) < 100 or unloaded.get("loadedPlans") != 0:
            raise RuntimeError("No native occupied natural-unload proof")
        completed = _json(reports / "final-restart.json")
        if completed.get("completed") != completed.get("total") or completed.get("allTargetsVerified") != completed.get("total"):
            raise RuntimeError("Final restoration identity is incomplete")
        report["result"] = "PASS"
        print("PASS: interrupted eradication/native unload recovery; evidence:", reports, flush=True)
    except BaseException as error:
        report["error"] = str(error)
        raise
    finally:
        text = json.dumps(report, indent=2) + "\n"
        (reports / "report.json").write_text(text, encoding="utf-8")
        (w.REPORTS / "eradication-recovery-latest.json").write_text(text, encoding="utf-8")


if __name__ == "__main__":
    run()
