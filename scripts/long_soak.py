"""Supervise a bounded, disposable native two-client vanilla-world performance soak."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import zipfile

RUN_ID = re.compile(r"[0-9]{8}T[0-9]{12,}Z")
TERMINAL = {"FINISHED", "FAILED", "INTERRUPTED", "CANCELLED"}


def atomic_json(path: Path, value: dict) -> None:
    pending = path.with_suffix(path.suffix + ".pending")
    with pending.open("w", encoding="utf-8") as out:
        json.dump(value, out, indent=2)
        out.write("\n")
        out.flush()
        os.fsync(out.fileno())
    pending.replace(path)


def read_json(path: Path) -> dict | None:
    return json.loads(path.read_text(encoding="utf-8")) if path.is_file() else None


def configure_world(root: Path) -> None:
    path = root / "server.properties"
    values = dict(line.split("=", 1) for line in path.read_text(encoding="utf-8").splitlines()
                  if line and not line.startswith(("#", "!")) and "=" in line)
    values.update({"level-type": "minecraft:normal", "generate-structures": "true",
                   "level-seed": "quiet-below-city-soak-v1", "difficulty": "normal",
                   "view-distance": "3", "simulation-distance": "3", "allow-flight": "true",
                   "player-idle-timeout": "0", "max-players": "2"})
    assert values["online-mode"] == "false" and values["server-ip"] == "127.0.0.1"
    assert values["server-port"] == "25575"
    path.write_text("\n".join(f"{key}={value}" for key, value in values.items()) + "\n", encoding="utf-8")


def evaluate(summary: dict, seconds: int) -> dict:
    elapsed = float(summary.get("elapsedSeconds", 0))
    ticks = int(summary.get("ticks", 0))
    tps = ticks / elapsed if elapsed else 0.0
    complete = summary.get("state") == "FINISHED" and elapsed >= seconds - 1
    budgets = {"durationComplete": complete, "meanTpsAtLeast19": tps >= 19,
               "callbackP99Below50ms": float(summary.get("callbackP99Nanos", 1e20)) < 50_000_000,
               "ecologyP99Below25ms": float(summary.get("ecologyP99Nanos", 1e20)) < 25_000_000,
               "eligibleBothClients": int(summary.get("missedEligibleTicks", 1)) == 0,
               "activeEcologyAtLeast80Percent": ticks > 0 and int(summary.get("activeEcologyTicks", 0)) / ticks >= .8,
               "unexpectedHeldAtMost5Percent": ticks > 0 and int(summary.get("unexpectedHeldTicks", ticks)) / ticks <= .05,
               "growthAttempted": int(summary.get("spreadAttemptsGrowth", 0)) > 0,
               "newSculkObserved": int(summary.get("maxOwned", 0)) > int(summary.get("initialOwned", 0)),
               "noRecordedFailure": not summary.get("failure")}
    return {"completed": complete, "meanTps": tps, "budgets": budgets,
            "result": "PASS" if all(budgets.values()) else "FAIL"}


def review_artifacts(work: Path) -> dict[str, str]:
    # Read-only guard against accidental deployment to the organizer's running server.
    folder = work / "reviews/events/20261028T102730212325Z/mods"
    return {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in folder.glob("*.jar")}


def summarize_recording(java: Path, reports: Path, manifest: dict) -> bool:
    recording = reports / "server.jfr"
    manifest["jfrReadable"] = False
    try:
        if not recording.is_file() or not recording.stat().st_size:
            raise RuntimeError("JFR recording is missing or empty")
        jfr_tool = java.parent / ("jfr.exe" if os.name == "nt" else "jfr")
        with (reports / "jfr-summary.txt").open("w", encoding="utf-8") as out:
            result = subprocess.run([str(jfr_tool), "summary", str(recording)], stdout=out,
                                    stderr=subprocess.STDOUT, timeout=60)
        manifest["jfrReadable"] = result.returncode == 0
    except (OSError, RuntimeError, subprocess.TimeoutExpired) as error:
        manifest["jfrError"] = str(error)
    return manifest["jfrReadable"]


def snapshot_launch(launch: dict, repo: Path, root: Path) -> dict:
    """Freeze mutable project classes/resources and Loom launch configuration for both clients."""
    snapshot = root / "client-runtime"
    replacements = {}
    for module in ("core", "campaigns/quiet-below"):
        for folder in ("classes", "resources", "devlibs"):
            source = repo / module / "build" / folder
            target = snapshot / module / folder
            if source.is_dir():
                shutil.copytree(source, target)
                replacements[str(source)] = str(target)
    def rewrite(value: str) -> str:
        for old, new in sorted(replacements.items(), key=lambda pair: -len(pair[0])):
            value = value.replace(old, new)
        return value
    result = json.loads(json.dumps(launch))
    result["classpath"] = [rewrite(value) for value in result["classpath"]]
    config_arg = next(value for value in result["jvmArgs"] if value.startswith("-Dfabric.dli.config="))
    original = Path(config_arg.split("=", 1)[1].replace("@@0020", " "))
    config = original.read_text(encoding="utf-8")
    target_dir = snapshot / "config"
    target_dir.mkdir(parents=True)
    for filename in ("log4j.xml", "remapClasspath.txt"):
        source = original.parent / filename
        target = target_dir / filename
        target.write_text(rewrite(source.read_text(encoding="utf-8")), encoding="utf-8")
        config = config.replace(str(source), str(target))
    target = target_dir / "launch.cfg"
    target.write_text(rewrite(config), encoding="utf-8")
    result["jvmArgs"][result["jvmArgs"].index(config_arg)] = "-Dfabric.dli.config=" + str(target).replace(" ", "@@0020")
    return result


def run(seconds: int, run_id: str) -> dict:
    import workspace as w
    import fabric_smoke as f
    import ecology_smoke
    if not 30 <= seconds <= 10800 or not RUN_ID.fullmatch(run_id):
        raise ValueError("Use 30..10800 seconds and a UTC timestamp run ID")
    reports = (w.REPORTS / ("long-soak-" + run_id)).resolve()
    if reports.parent != w.REPORTS.resolve():
        raise ValueError("Report directory escaped workspace")
    reports.mkdir(parents=True, exist_ok=True)
    lock = reports / "controller-owner.json"
    with lock.open("x", encoding="utf-8") as out:
        json.dump({"pid": os.getpid(), "seconds": seconds}, out)
    if shutil.disk_usage(w.WORK).free < 8 * 1024**3:
        raise RuntimeError("Eight GiB free workspace disk required for world, bounded JFR and diagnostics")
    before = review_artifacts(w.WORK)
    for key in list(os.environ):
        if key.startswith("CHRONICLE_"):
            os.environ.pop(key)
    w.require_eula()
    w.validate_server_runtime()
    w.validate_managed_mod_names()
    launch = json.loads((w.REPO / "campaigns/quiet-below/build/smoke/client-launch.json").read_text(encoding="utf-8"))
    jars = w.artifacts()
    for jar in jars:
        w.validate_mod_target(jar)
    with zipfile.ZipFile(next(p for p in jars if p.name.startswith("quiet_below-"))) as archive:
        assert "io/github/chronicle/quietbelow/ecology/EcologySoakTrial.class" in archive.namelist(), "Build soak harness before running"
    ecology_smoke._reserve_ports()
    root = f._stage(w)
    configure_world(root)
    launch = snapshot_launch(launch, w.REPO, root)
    (root / ".long-soak-fixture").write_text(json.dumps({"reports": str(reports), "durationSeconds": seconds}) + "\n", encoding="utf-8")
    (root / "ops.json").write_text(json.dumps([{"uuid": f.offline_uuid(name), "name": name,
                                                "level": 2, "bypassesPlayerLimit": False}
                                               for name in ("ChronicleSoakA", "ChronicleSoakB")]), encoding="utf-8")
    manifest = {"result": "PREPARING", "controllerPid": os.getpid(), "world": str(root), "reports": str(reports),
                "durationSeconds": seconds, "seed": "quiet-below-city-soak-v1", "minecraft": w.LOCK["minecraft"],
                "clients": 2, "nativeClientFpsCap": 30, "serverHeapMax": "2G", "clientHeapMaxEach": "2G",
                "targetTps": 20, "viewDistance": 12, "simulationDistance": 8, "preparationViewDistance": 3, "difficulty": "normal",
                "jfr": str(reports / "server.jfr"), "artifacts": {p.name: w.digest(p) for p in jars},
                "createdUTC": datetime.now(timezone.utc).isoformat(),
                "limitations": ["Two controlled native clients on the same host, not a human group/voice or final-map certification.",
                                "Test accounts are fed, invulnerable and permitted flight; natural world/mobs/daylight remain enabled.",
                                "One reinforced-deepslate fixture Heart is bound to an actual vanilla Ancient City; no authored spread routes.",
                                "Callback span is measured through Fabric tick hooks; JFR adds profiling overhead.",
                                "Planned 90-second city-unload phases every20 minutes are reported separately from resolved growth."]}
    def checkpoint(state: str, **extra) -> None:
        manifest.update(result=state, updatedUTC=datetime.now(timezone.utc).isoformat(), **extra)
        atomic_json(reports / "controller-status.json", manifest)
        atomic_json(w.REPORTS / "long-soak-latest.json", manifest)
    checkpoint("PREPARING")
    server = None
    clients = []
    server_log = None
    failure = None
    outcome = "FAIL"
    try:
        environment = w.env()
        temp = root / "temp"
        temp.mkdir()
        environment.update(TEMP=str(temp), TMP=str(temp))
        environment.update(CHRONICLE_ECOLOGY_NATIVE_TRIAL="1", CHRONICLE_LONG_SOAK_SECONDS=str(seconds),
                           CHRONICLE_LONG_SOAK_REPORTS=str(reports))
        jfr = "-XX:StartFlightRecording=filename=\"" + str(reports / "server.jfr").replace("\\", "/") + "\",settings=profile,dumponexit=true,maxsize=512m,maxage=3h"
        repository = reports / "jfr-repository"
        repository.mkdir()
        command = [str(w.JAVA), "-Xms512M", "-Xmx2G", "-Djava.io.tmpdir=" + str(temp),
                   "-XX:FlightRecorderOptions=repository=\"" + str(repository).replace("\\", "/") + "\"",
                   jfr, "-XX:+HeapDumpOnOutOfMemoryError",
                   "-XX:HeapDumpPath=" + str(reports / "server-oom.hprof"), "-jar", "fabric-server-launch.jar", "nogui"]
        server_log = (reports / "server.log").open("w", encoding="utf-8")
        server = subprocess.Popen(command, cwd=root, env=environment, stdin=subprocess.PIPE, stdout=server_log,
                                  stderr=subprocess.STDOUT, text=True, encoding="utf-8",
                                  creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        checkpoint("PREPARING", serverPid=server.pid)
        ecology_smoke._wait_ready(server, reports / "server.log")
        for role, name in (("anchor", "ChronicleSoakA"), ("roamer", "ChronicleSoakB")):
            variables = {"CHRONICLE_ECOLOGY_NATIVE_TRIAL": "1", "CHRONICLE_LONG_SOAK_SECONDS": str(seconds),
                         "CHRONICLE_LONG_SOAK_REPORTS": str(reports), "CHRONICLE_LONG_SOAK_WORLD": str(root),
                         "CHRONICLE_LONG_SOAK_ROLE": role, "CHRONICLE_MENU_WIDTH": "640", "CHRONICLE_MENU_HEIGHT": "480"}
            clients.append(f._client(w, launch, root, reports, name, "soak-" + role, variables))
        checkpoint("PREPARING", clientPids=[p.pid for p, _ in clients])
        setup_deadline = time.monotonic() + 1500
        run_deadline = None
        last_heartbeat = time.monotonic()
        last_tick = -1
        while True:
            if (reports / "stop.request").exists():
                outcome = "CANCELLED"
                break
            if server.poll() is not None:
                raise RuntimeError(f"Soak server exited early: {server.returncode}")
            status = read_json(reports / "status.json")
            if status and status.get("state") in TERMINAL:
                summary = read_json(reports / "summary.json") or status
                decision = evaluate(summary, seconds)
                manifest["measurement"] = decision
                manifest["summary"] = summary
                outcome = decision["result"]
                break
            for role, (process, _) in zip(("anchor", "roamer"), clients):
                client = read_json(reports / ("client-" + role + ".json"))
                if client and client.get("result") == "FAIL":
                    raise RuntimeError(f"Native {role} failed: {client.get('detail')}")
                if process.poll() is not None:
                    raise RuntimeError(f"Native {role} exited before server completion: {process.returncode}")
            if status and status.get("state") == "RUNNING":
                if run_deadline is None:
                    run_deadline = time.monotonic() + seconds + 180
                if int(status["ticks"]) > last_tick:
                    last_tick = int(status["ticks"])
                    last_heartbeat = time.monotonic()
                if time.monotonic() - last_heartbeat > 120:
                    raise RuntimeError("No advancing server telemetry for120 seconds")
                if time.monotonic() > run_deadline:
                    raise RuntimeError("Wall-clock soak deadline exceeded")
                checkpoint("RUNNING", startUTC=status["startUTC"], endUTCDue=status["endUTCDue"],
                           elapsedSeconds=status["elapsedSeconds"], sampledTicks=status["ticks"])
            elif time.monotonic() > setup_deadline:
                raise RuntimeError("Vanilla city preparation exceeded25 minutes")
            time.sleep(5)
    except BaseException as error:
        failure = str(error)
        raise
    finally:
        checkpoint("STOPPING", error=failure, outcome=outcome)
        if outcome == "PASS":
            # Let native clients observe FINISHED and close before disconnecting the server.
            deadline = time.monotonic() + 25
            while any(process.poll() is None for process, _ in clients) and time.monotonic() < deadline:
                time.sleep(1)
            final_clients = {role: read_json(reports / ("client-" + role + ".json"))
                             for role in ("anchor", "roamer")}
            manifest["clientResults"] = final_clients
            if any(not value or value.get("result") != "PASS" for value in final_clients.values()):
                outcome = "FAIL"
                manifest["clientCompletionError"] = "Native clients did not acknowledge measured completion"
        try:
            f._stop(server)
            manifest["cleanServerStop"] = True
        except BaseException as error:
            manifest["shutdownError"] = str(error)
            outcome = "FAIL"
        for process, handle in clients:
            try:
                process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=10)
            handle.close()
        if server_log is not None:
            server_log.close()
        manifest["reviewArtifactsUnchanged"] = before == review_artifacts(w.WORK)
        if not manifest["reviewArtifactsUnchanged"]:
            outcome = "FAIL"
        if not summarize_recording(w.JAVA, reports, manifest) and outcome == "PASS":
            outcome = "FAIL"
        checkpoint(outcome, finishedUTC=datetime.now(timezone.utc).isoformat())
        atomic_json(reports / "report.json", manifest)
        print(json.dumps({key: manifest.get(key) for key in ("result", "reports", "world", "error", "measurement")}, indent=2), flush=True)
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--seconds", type=int, default=10800)
    parser.add_argument("--run-id", default=datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ"))
    args = parser.parse_args()
    result = run(args.seconds, args.run_id)
    sys.exit(0 if result["result"] == "PASS" else 1)
