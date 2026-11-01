"""Disposable loopback CF22 native Event actions, crash recovery and maximum-bound smoke."""
from __future__ import annotations

import json
from pathlib import Path
import struct
import subprocess
import time

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
    (root / ".event-actions-fixture").write_text("Disposable CF22 Event actions fixture\n", encoding="utf-8")
    (root / "ops.json").write_text(json.dumps([{
        "uuid": fabric_smoke.offline_uuid("ChronicleEventsA"), "name": "ChronicleEventsA",
        "level": 2, "bypassesPlayerLimit": False,
    }], indent=2), encoding="utf-8")
    config = root / "config" / "chronicle"
    config.mkdir(parents=True, exist_ok=True)
    (config / "books.json").write_text(json.dumps({"schemaVersion": 1, "books": [{
        "id": "test:event_managed", "title": "Event evidence", "author": "Fixture",
        "description": "Managed lore removed by aggregate Event action",
        "pages": ["Managed Event evidence."], "rule": "OFF",
    }]}), encoding="utf-8")
    if maximum:
        properties = root / "server.properties"
        text = properties.read_text(encoding="utf-8")
        lines = ["view-distance=10" if line.startswith("view-distance=") else line for line in text.splitlines()]
        properties.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return root


def _start(w, root: Path, reports: Path, label: str, env: dict[str, str]):
    menu_smoke._reserve_ports()
    path = reports / f"server-{label}.log"
    handle = path.open("w", encoding="utf-8")
    process = subprocess.Popen(w.server_command(), cwd=root, env=env, stdin=subprocess.PIPE,
                               stdout=handle, stderr=subprocess.STDOUT, text=True, encoding="utf-8")
    menu_smoke._wait_ready(process, path)
    return process, handle


def _client(w, launch: dict, root: Path, reports: Path, phase: str, role: str = "A"):
    result = reports / f"{phase}-{role}.json"
    process = fabric_smoke._client(w, launch, root, reports, f"ChronicleEvents{role}", f"events-{phase}-{role}", {
        "CHRONICLE_EVENTS_SMOKE": str(result),
        "CHRONICLE_EVENTS_SERVER_DIR": str(root),
        "CHRONICLE_EVENTS_PHASE": phase,
        "CHRONICLE_EVENTS_ROLE": role,
        "CHRONICLE_MENU_WIDTH": "960",
        "CHRONICLE_MENU_HEIGHT": "540",
    })
    return process, result


def _text(data: bytes, offset: int) -> tuple[str, int]:
    if offset + 4 > len(data):
        raise RuntimeError("truncated Event batch journal text length")
    length, = struct.unpack_from(">i", data, offset)
    offset += 4
    if length < 0 or offset + length > len(data):
        raise RuntimeError("invalid Event batch journal text length")
    return data[offset:offset + length].decode("utf-8"), offset + length


def _first_child_saved(root: Path) -> bool:
    path = root / "world" / "chronicle" / "event-batches" / "journal.bin"
    if not path.is_file():
        return False
    data = path.read_bytes()
    try:
        magic, schema, _revision, count = struct.unpack_from(">iiqi", data, 0)
        if magic != 0x4345424A or schema != 1 or not 0 <= count <= 256:
            return False
        offset = 20
        for _ in range(count):
            _batch, offset = _text(data, offset)
            _hash, offset = _text(data, offset)
            phase, offset = _text(data, offset)
            step, offset = _text(data, offset)
            target, cursor = struct.unpack_from(">ii", data, offset)
            offset += 8
            _detail, offset = _text(data, offset)
            _updated, offset = _text(data, offset)
            if phase == "APPLYING" and target == 1 and step in {"WRITE", "VERIFY_TARGET", "VERIFY_ALL"}:
                return True
        return False
    except (UnicodeDecodeError, struct.error, RuntimeError):
        return False


def _wait(path_or_test, timeout: float, label: str) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if callable(path_or_test):
            if path_or_test():
                return
        elif path_or_test.is_file():
            return
        time.sleep(.02)
    raise RuntimeError("Timed out waiting for " + label)


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


def run(build: bool = True) -> None:
    import workspace as w
    w.require_eula()
    w.validate_server_runtime()
    w.validate_managed_mod_names()
    if build:
        w.run([w.REPO / "gradlew.bat", "--no-daemon", ":core:remapJar", ":campaigns:quiet-below:remapJar",
               ":campaigns:quiet-below:writeSmokeClientLaunch"])
    launch = json.loads((w.REPO / "campaigns/quiet-below/build/smoke/client-launch.json").read_text(encoding="utf-8"))
    root = _stage(w)
    reports = w.REPORTS / ("events-" + root.name)
    reports.mkdir(parents=True)
    report = {"result": "FAIL", "world": str(root), "reports": str(reports), "checks": [], "limitations": [
        "Registered sound dispatch is exercised, but automated evidence does not establish human audibility or voice quality.",
        "The media-claim crash is injected after the durable claim; a separate forced SoundManager RuntimeException is source-reviewed, not injected here.",
    ]}
    env = w.env()
    env["CHRONICLE_EVENTS_TRIAL"] = "true"
    server = log = None
    clients = []
    try:
        server, log = _start(w, root, reports, "basic", env)
        a, a_result = _client(w, launch, root, reports, "basic", "A")
        b, b_result = _client(w, launch, root, reports, "basic", "B")
        clients = [a, b]
        fabric_smoke._await_clients(clients, [a_result, b_result], timeout=420)
        for result in (a_result, b_result):
            report["checks"].extend(_proof(result).get("checks", []))
        for client in clients:
            client[1].close()
        clients = []
        fabric_smoke._stop(server)
        server = None
        log.close()
        log = None
        report["checks"].append("combined manual/automatic/refusal fixture saved and stopped cleanly")

        server, log = _start(w, root, reports, "crash", env)
        crash, armed = _client(w, launch, root, reports, "crash", "A")
        clients = [crash]
        _wait(armed, 120, "crash Event arming")
        if json.loads(armed.read_text(encoding="utf-8")).get("result") != "ARMED":
            raise RuntimeError("crash client did not arm")
        _wait(lambda: _first_child_saved(root), 180, "durable first child checkpoint")
        server.kill()
        server.wait(timeout=30)
        if server.returncode == 0:
            raise RuntimeError("abrupt checkpoint termination unexpectedly exited cleanly")
        server = None
        log.close()
        log = None
        _terminate_client(crash)
        clients = []
        report["checks"].append("runner killed the real JVM only after journal proved child zero was durably saved and child one was pending")

        server, log = _start(w, root, reports, "recovery", env)
        recovery, recovery_result = _client(w, launch, root, reports, "recovery", "A")
        clients = [recovery]
        fabric_smoke._await_clients(clients, [recovery_result], timeout=300)
        report["checks"].extend(_proof(recovery_result).get("checks", []))
        recovery[1].close()
        clients = []
        fabric_smoke._stop(server)
        server = None
        log.close()
        log = None

        server, log = _start(w, root, reports, "final", env)
        final, final_result = _client(w, launch, root, reports, "final", "A")
        clients = [final]
        fabric_smoke._await_clients(clients, [final_result], timeout=180)
        report["checks"].extend(_proof(final_result).get("checks", []))
        final[1].close()
        clients = []
        fabric_smoke._stop(server)
        server = None
        log.close()
        log = None

        server, log = _start(w, root, reports, "media-claim", env)
        seed, _ = _client(w, launch, root, reports, "media_seed", "A")
        clients = [seed]
        server.wait(timeout=180)
        if server.returncode != 97:
            raise RuntimeError(f"media-claim fixture did not halt at the explicit durable boundary: {server.returncode}")
        server = None
        log.close()
        log = None
        _terminate_client(seed)
        clients = []
        report["checks"].append("real JVM halted immediately after durable MEDIA_CLAIMED evidence")

        server, log = _start(w, root, reports, "media-uncertain", env)
        uncertain, uncertain_result = _client(w, launch, root, reports, "uncertain", "A")
        clients = [uncertain]
        fabric_smoke._await_clients(clients, [uncertain_result], timeout=180)
        report["checks"].extend(_proof(uncertain_result).get("checks", []))
        uncertain[1].close()
        clients = []
        fabric_smoke._stop(server)
        server = None
        log.close()
        log = None

        server, log = _start(w, root, reports, "absent-outcome", env)
        absent_seed, _ = _client(w, launch, root, reports, "outcome_seed", "A")
        clients = [absent_seed]
        server.wait(timeout=180)
        if server.returncode != 98:
            raise RuntimeError(f"absent-outcome fixture did not halt at the durable EVENT_OUTCOME boundary: {server.returncode}")
        server = None
        log.close()
        log = None
        _terminate_client(absent_seed)
        clients = []
        report["checks"].append("real JVM halted with a durable EVENT_OUTCOME whose original GM and sole recipient were offline")

        server, log = _start(w, root, reports, "absent-outcome-recovery", env)
        absent_recovery, absent_recovery_result = _client(w, launch, root, reports, "outcome_recover", "A")
        clients = [absent_recovery]
        fabric_smoke._await_clients(clients, [absent_recovery_result], timeout=180)
        report["checks"].extend(_proof(absent_recovery_result).get("checks", []))
        absent_recovery[1].close()
        clients = []
        fabric_smoke._stop(server)
        server = None
        log.close()
        log = None

        max_root = _stage(w, maximum=True)
        report["maximum_world"] = str(max_root)
        server, log = _start(w, max_root, reports, "maximum", env)
        maximum, maximum_result = _client(w, launch, max_root, reports, "max", "A")
        clients = [maximum]
        fabric_smoke._await_clients(clients, [maximum_result], timeout=900)
        report["checks"].extend(_proof(maximum_result).get("checks", []))
        maximum[1].close()
        clients = []
        fabric_smoke._stop(server)
        server = None
        log.close()
        log = None

        required = ("zero first-Region writes", "unloaded target", "terrain, NBT, chest binding, managed block and lore",
                    "ordinary player", "automatic timer", "abrupt first-child", "without replay", "without original GM", "262144-cell/64-chunk")
        missing = [needle for needle in required if not any(needle in check for check in report["checks"])]
        if missing:
            raise RuntimeError("Event smoke ended before required evidence: " + repr(missing))
        report["result"] = "PASS"
        print("PASS: Event actions native smoke; evidence:", reports, flush=True)
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
            (w.REPORTS / "events-latest.json").write_text(content, encoding="utf-8")


if __name__ == "__main__":
    run()
