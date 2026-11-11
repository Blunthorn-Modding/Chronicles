"""Native placement, unloaded quotas, policy UI and restart proof on one disposable world."""
from __future__ import annotations

import json
import socket
import subprocess
import time


def _phase(w, fabric, launch, root, reports, phase):
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 25575))
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
        probe.bind(("127.0.0.1", 24455))
    log = reports / ("server-" + phase + ".log")
    handle = log.open("w", encoding="utf-8")
    server = client = None
    try:
        server = subprocess.Popen(w.server_command(), cwd=root, env=w.env(), stdin=subprocess.PIPE,
                                  stdout=handle, stderr=subprocess.STDOUT, text=True, encoding="utf-8")
        deadline = time.monotonic() + 240
        while True:
            content = log.read_text(encoding="utf-8", errors="replace")
            if "Done (" in content and "Voice chat server started at 127.0.0.1:24455" in content:
                break
            if server.poll() is not None or time.monotonic() > deadline:
                raise RuntimeError("Managed block test server failed startup: " + str(log))
            time.sleep(.2)
        client = fabric._client(w, launch, root, reports, "ManagedSites", "managed-" + phase, {
            "CHRONICLE_MANAGED_SMOKE": str(reports), "CHRONICLE_MANAGED_PHASE": phase,
            "CHRONICLE_MANAGED_SERVER_DIR": str(root),
        })
        result = reports / ("managed-" + phase + ".json")
        fabric._await_clients([client], [result], timeout=330)
        data = json.loads(result.read_text(encoding="utf-8"))
        required = {
            "initial": {"second Altar is refused without consuming its item", "relocation preserves alias identity and advances its unbound revision"},
            "restart": {"unloaded Altar still consumes its quota after restart", "below-placed limit disables modal Save", "forged below-placed limit is rejected by the authoritative service", "verified removal frees the raised slot for a replacement", "direct placement is counted even when over limit", "rejected policy decrease never deletes discovered or placed blocks"},
            "final": {"persisted policy refuses additional placements after discovered overage"},
        }[phase]
        if data.get("result") != "PASS" or not required.issubset(data.get("checks", [])):
            raise RuntimeError("Managed block phase lacked required native proof: " + phase)
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
            fabric._stop(server)
        finally:
            handle.close()


def run():
    import fabric_smoke as fabric
    import workspace as w
    w.require_eula()
    w.validate_server_runtime()
    w.validate_managed_mod_names()
    w.run([w.REPO / "gradlew.bat", "--no-daemon", ":core:remapJar", ":campaigns:quiet-below:remapJar", ":campaigns:quiet-below:writeSmokeClientLaunch"])
    launch = json.loads((w.REPO / "campaigns/quiet-below/build/smoke/client-launch.json").read_text(encoding="utf-8"))
    root = fabric._stage(w)
    reports = w.REPORTS / ("managed-blocks-" + root.name)
    reports.mkdir(parents=True)
    (root / "ops.json").write_text(json.dumps([{"uuid": fabric.offline_uuid("ManagedSites"), "name": "ManagedSites", "level": 2, "bypassesPlayerLimit": False}]), encoding="utf-8")
    report = {"result": "FAIL", "world": str(root), "reports": str(reports), "phases": []}
    try:
        for phase in ("initial", "restart", "final"):
            _phase(w, fabric, launch, root, reports, phase)
            report["phases"].append(phase)
        report["result"] = "PASS"
        print("PASS: managed block native proof; evidence:", reports, flush=True)
    except BaseException as error:
        report["error"] = str(error)
        raise
    finally:
        text = json.dumps(report, indent=2) + "\n"
        (reports / "report.json").write_text(text, encoding="utf-8")
        (w.REPORTS / "managed-blocks-latest.json").write_text(text, encoding="utf-8")


if __name__ == "__main__":
    run()
