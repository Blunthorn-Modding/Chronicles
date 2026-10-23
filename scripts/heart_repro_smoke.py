"""Reproduce the saved Heart delay on a disposable clone of the accepted review world."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import shutil
import socket
import subprocess
import time


SOURCE_NAME = "20261023T160001131644Z"


def _reserve_ports() -> None:
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 25575))
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
        probe.bind(("127.0.0.1", 24455))


def _wait_ready(server, logfile: Path) -> None:
    deadline = time.monotonic() + 240
    while True:
        content = logfile.read_text(encoding="utf-8", errors="replace") if logfile.exists() else ""
        if "Done (" in content and "Voice chat server started at 127.0.0.1:24455" in content:
            return
        if server.poll() is not None or time.monotonic() > deadline:
            raise RuntimeError("Heart reproduction clone failed startup: " + str(logfile))
        time.sleep(.2)


def _digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _source_fingerprints(world: Path) -> dict[str, str]:
    base = world.resolve()
    files = {}
    for path in world.rglob("*"):
        if not path.resolve().is_relative_to(base) or path.is_symlink() or path.is_junction():
            raise RuntimeError("Refusing linked source-world paths")
        if path.is_file():
            files[path.relative_to(world).as_posix()] = _digest(path)
    return files


def _copy_review_world(w, root: Path) -> tuple[Path, dict[str, str], bool]:
    source = w.WORK / "reviews" / "contact" / SOURCE_NAME
    w.check_stopped(source)
    world = source / "world"
    if not world.is_dir():
        raise RuntimeError("Accepted review world is unavailable: " + str(world))
    target = root / "world"
    resolved_root = root.resolve()
    if not target.resolve().is_relative_to(resolved_root) or not resolved_root.is_relative_to((w.WORK / "test-runs").resolve()):
        raise RuntimeError("Refusing to replace a world outside the disposable test-runs root")
    source_files = _source_fingerprints(world)
    if target.exists():
        raise RuntimeError("Expected a fresh disposable world target")
    shutil.copytree(world, target)
    source_config = source / "config" / "chronicle"
    if source_config.is_dir():
        shutil.copytree(source_config, root / "config" / "chronicle", dirs_exist_ok=True)
    campaign = json.loads((target / "chronicle" / "campaign-state.json").read_text(encoding="utf-8"))
    return source, source_files, campaign.get("phase") == "PAUSED"


def _replace_participant(root: Path, player_uuid: str) -> None:
    path = root / "world" / "chronicle" / "play-runtime.json"
    state = json.loads(path.read_text(encoding="utf-8"))
    state["participants"] = [player_uuid]
    state["revision"] += 1
    path.write_text(json.dumps(state, indent=2) + "\n", encoding="utf-8")


def _start(w, root: Path, report_dir: Path):
    _reserve_ports()
    logfile_path = report_dir / "server.log"
    logfile = logfile_path.open("w", encoding="utf-8")
    server = None
    try:
        server = subprocess.Popen(w.server_command(), cwd=root, env=w.env(), stdin=subprocess.PIPE,
                                  stdout=logfile, stderr=subprocess.STDOUT, text=True, encoding="utf-8")
        _wait_ready(server, logfile_path)
        return server, logfile
    except BaseException:
        if server is not None:
            import fabric_smoke
            fabric_smoke._stop(server)
        logfile.close()
        raise


def run() -> None:
    import fabric_smoke
    import workspace as w

    w.require_eula()
    w.validate_server_runtime()
    w.validate_managed_mod_names()
    launch_path = w.REPO / "campaigns" / "quiet-below" / "build" / "smoke" / "client-launch.json"
    if not launch_path.is_file():
        raise RuntimeError("Build the matching smoke client before running heart_repro_smoke.py")
    launch = json.loads(launch_path.read_text(encoding="utf-8"))
    root = fabric_smoke._stage(w)
    reports = w.REPORTS / ("heart-repro-" + root.name)
    reports.mkdir(parents=True)
    report = {"result": "FAIL", "world": str(root), "reports": str(reports), "source": "", "checks": []}
    server = logfile = None
    clients = []
    try:
        source, before, resume = _copy_review_world(w, root)
        report["source"] = str(source)
        player = "HeartRepro"
        player_uuid = fabric_smoke.offline_uuid(player)
        _replace_participant(root, player_uuid)
        (root / "ops.json").write_text(json.dumps([{"uuid": player_uuid, "name": player, "level": 2,
                                                       "bypassesPlayerLimit": False}], indent=2) + "\n", encoding="utf-8")
        server, logfile = _start(w, root, reports)
        variables = {"CHRONICLE_HEART_REPRO_SMOKE": str(reports), "CHRONICLE_HEART_REPRO_SERVER_DIR": str(root),
                     "CHRONICLE_HEART_REPRO_RESUME": "true" if resume else "false"}
        clients = [fabric_smoke._client(w, launch, root, reports, player, "heart-repro", variables)]
        result_path = reports / "heart-repro.json"
        fabric_smoke._await_clients(clients, [result_path], timeout=240)
        result = json.loads(result_path.read_text(encoding="utf-8"))
        required = {"mine the vulnerable Heart with the held pickaxe", "confirm Heart was removed after its local cue",
                    "bound Heart anchor refuses a privileged legacy deletion",
                    "bound Heart assignment refuses a privileged legacy clear",
                    "destroyed Heart releases physical quota while preserving history",
                    "ended network refuses a new root without consuming the item"}
        if result.get("result") != "PASS" or not required.issubset(result.get("checks", [])):
            raise RuntimeError("Native reproduction did not prove physical Heart destruction")
        if result.get("initialElapsed", -1) < 0 or result.get("maxElapsed", -1) <= result["initialElapsed"]:
            raise RuntimeError("Heart delay clock did not advance in the cloned review world")
        after = _source_fingerprints(source / "world")
        if after != before:
            raise RuntimeError("The accepted source world changed during clone reproduction")
        for relative in ("chronicle/authoring.json", "chronicle/map-assignments.json"):
            if _digest(root / "world" / relative) != before[relative]:
                raise RuntimeError("Managed migration or destruction changed historical map metadata: " + relative)
        report["checks"] = ["accepted review world cloned only under test-runs", "offline reproduction player substituted only in clone",
                            "actual Heart status clock advanced to vulnerability", "Survival iron-pickaxe mining produced the local terminal cue",
                            "managed migration preserves authoring and assignment bytes", "destroyed root quota released; ended replacement denied without item loss"]
        report["sourceFilesVerified"] = len(before)
        report["result"] = "PASS"
        print("PASS: Heart delay reproduced from saved review world; evidence:", reports, flush=True)
    except BaseException as error:
        report["error"] = str(error)
        raise
    finally:
        for process, handle in clients:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill(); process.wait(timeout=15)
            handle.close()
        try:
            fabric_smoke._stop(server)
        finally:
            if logfile is not None:
                logfile.close()
            content = json.dumps(report, indent=2) + "\n"
            (reports / "report.json").write_text(content, encoding="utf-8")
            (w.REPORTS / "heart-repro-latest.json").write_text(content, encoding="utf-8")


if __name__ == "__main__":
    run()
