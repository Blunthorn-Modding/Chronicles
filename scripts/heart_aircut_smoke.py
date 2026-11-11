"""Archived pre-dev46 Heart air-cut reproduction on an isolated offline clone."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path, PurePosixPath
import socket
import subprocess
import time
import zipfile


ARCHIVE_NAME = "contact-pre-dev46.zip"


def _digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _reserve_ports() -> None:
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 25575))
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
        probe.bind(("127.0.0.1", 24455))


def _extract_fixture(archive: Path, root: Path) -> str:
    before = _digest(archive)
    with zipfile.ZipFile(archive) as bundle:
        for member in bundle.infolist():
            relative = PurePosixPath(member.filename)
            mode = member.external_attr >> 16
            if not relative.parts or "\\" in member.filename or ":" in member.filename or relative.is_absolute() or ".." in relative.parts or (mode & 0o170000) == 0o120000:
                raise RuntimeError("Archive contains an unsafe fixture member: " + member.filename)
            allowed = relative.parts[0] == "world" or relative.parts[:2] == ("config", "chronicle")
            if not allowed or member.is_dir():
                continue
            target = root.joinpath(*relative.parts)
            if not target.resolve().is_relative_to(root.resolve()):
                raise RuntimeError("Refusing archive member outside disposable root")
            target.parent.mkdir(parents=True, exist_ok=True)
            with bundle.open(member) as source, target.open("xb") as destination:
                destination.write(source.read())
    if _digest(archive) != before:
        raise RuntimeError("Archived source changed during extraction")
    return before


def _normal_difficulty(root: Path) -> None:
    path = root / "server.properties"
    text = path.read_text(encoding="utf-8")
    if "difficulty=peaceful" not in text:
        raise RuntimeError("Fresh disposable server did not have the expected difficulty")
    path.write_text(text.replace("difficulty=peaceful", "difficulty=normal", 1), encoding="utf-8")


def _replace_participant(root: Path, player_uuid: str) -> None:
    path = root / "world" / "chronicle" / "play-runtime.json"
    state = json.loads(path.read_text(encoding="utf-8"))
    state["participants"] = [player_uuid]
    state["revision"] += 1
    path.write_text(json.dumps(state, indent=2) + "\n", encoding="utf-8")


def _wait_ready(server, log: Path) -> None:
    deadline = time.monotonic() + 240
    while True:
        text = log.read_text(encoding="utf-8", errors="replace") if log.exists() else ""
        if "Done (" in text and "Voice chat server started at 127.0.0.1:24455" in text:
            return
        if server.poll() is not None or time.monotonic() > deadline:
            raise RuntimeError("Air-cut server failed startup: " + str(log))
        time.sleep(.2)


def run() -> None:
    import fabric_smoke
    import workspace as w

    w.require_eula()
    w.validate_server_runtime()
    w.validate_managed_mod_names()
    w.run([w.REPO / "gradlew.bat", "--no-daemon", ":core:remapJar", ":campaigns:quiet-below:remapJar", ":campaigns:quiet-below:writeSmokeClientLaunch"])
    launch = json.loads((w.REPO / "campaigns/quiet-below/build/smoke/client-launch.json").read_text(encoding="utf-8"))
    archive = w.WORK / "backups" / ARCHIVE_NAME
    if not archive.is_file():
        raise RuntimeError("Required archived fixture is unavailable: " + str(archive))
    root = fabric_smoke._stage(w)
    reports = w.REPORTS / ("heart-aircut-" + root.name)
    reports.mkdir(parents=True)
    report = {"result": "FAIL", "world": str(root), "reports": str(reports), "archive": str(archive)}
    server = client = handle = None
    try:
        report["archiveSha256"] = _extract_fixture(archive, root)
        _normal_difficulty(root)
        player = "HeartAirCut"
        player_uuid = fabric_smoke.offline_uuid(player)
        _replace_participant(root, player_uuid)
        (root / "ops.json").write_text(json.dumps([{"uuid": player_uuid, "name": player, "level": 2, "bypassesPlayerLimit": False}]) + "\n", encoding="utf-8")
        _reserve_ports()
        log_path = reports / "server.log"
        handle = log_path.open("w", encoding="utf-8")
        environment = w.env()
        environment["CHRONICLE_HEART_AIRCUT_TRIAL"] = "true"
        server = subprocess.Popen(w.server_command(), cwd=root, env=environment, stdin=subprocess.PIPE, stdout=handle, stderr=subprocess.STDOUT, text=True, encoding="utf-8")
        _wait_ready(server, log_path)
        client = fabric_smoke._client(w, launch, root, reports, player, "heart-aircut", {"CHRONICLE_HEART_AIRCUT_SMOKE": str(reports), "CHRONICLE_HEART_AIRCUT_SERVER_DIR": str(root)})
        result_path = reports / "heart-aircut.json"
        fabric_smoke._await_clients([client], [result_path], timeout=220)
        result = json.loads(result_path.read_text(encoding="utf-8"))
        required = {"air-cut owned Heart-window network", "wait for complete zero-living air cut", "verify local growth and direct Warden"}
        if result.get("result") != "PASS" or not required.issubset(result.get("checks", [])):
            raise RuntimeError("Air-cut fixture did not prove the final attempt: " + str(result))
        if _digest(archive) != report["archiveSha256"]:
            raise RuntimeError("Archived source changed while the disposable clone ran")
        report["result"] = "PASS"
        report["checks"] = ["archive bytes unchanged", "only world and config/chronicle extracted into fresh test-runs root", "offline participant substituted only in clone", "player-loaded Heart chunks", "air cut reached complete zero living", "final attempt wrote journaled local growth and spawned one live Warden"]
        print("PASS: archived Heart air-cut reproduction; evidence:", reports, flush=True)
    except BaseException as error:
        report["error"] = str(error)
        raise
    finally:
        if client is not None:
            process, client_log = client
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill(); process.wait(timeout=15)
            client_log.close()
        try:
            fabric_smoke._stop(server)
        finally:
            if handle is not None:
                handle.close()
            text = json.dumps(report, indent=2) + "\n"
            (reports / "report.json").write_text(text, encoding="utf-8")
            (w.REPORTS / "heart-aircut-latest.json").write_text(text, encoding="utf-8")


if __name__ == "__main__":
    run()
