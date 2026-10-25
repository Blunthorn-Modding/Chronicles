"""Focused CF18 native Books & Lore menu smoke on a disposable loopback server.

This uses the standard Fabric staging and launcher plus explicitly guarded
server trial commands in its own disposable offline loopback fixture. No
authenticated review world or production server is modified.
"""
from __future__ import annotations

import json
import subprocess
from pathlib import Path

import fabric_smoke
import menu_smoke


def _client(w, launch: dict, root: Path, reports: Path, label: str, width: int, height: int, delivery: bool):
    # fabric_smoke stages this deterministic account as the only fixture
    # operator; no real account, auth, or review-world permission is reused.
    return fabric_smoke._client(w, launch, root, reports, "ChronicleFabricA", label, {
        "CHRONICLE_BOOKS_SMOKE": str(reports / (label + ".json")),
        "CHRONICLE_BOOKS_SERVER_DIR": str(root),
        "CHRONICLE_MENU_WIDTH": str(width),
        "CHRONICLE_MENU_HEIGHT": str(height),
        "CHRONICLE_BOOKS_DELIVERY": "true" if delivery else "false",
        "CHRONICLE_BOOKS_RESTART": "true" if label == "narrow-480" else "false",
    })


def _result(path: Path) -> dict:
    if not path.is_file():
        raise RuntimeError("Books client did not write result: " + str(path))
    value = json.loads(path.read_text(encoding="utf-8"))
    if value.get("result") != "PASS":
        raise RuntimeError("Books client failed: " + json.dumps(value, indent=2))
    return value


def run() -> None:
    import workspace as w
    w.require_eula()
    w.validate_server_runtime()
    w.validate_managed_mod_names()
    w.run([w.REPO / "gradlew.bat", "--no-daemon", ":core:remapJar",
           ":campaigns:quiet-below:remapJar", ":campaigns:quiet-below:writeSmokeClientLaunch"])
    launch = json.loads((w.REPO / "campaigns/quiet-below/build/smoke/client-launch.json").read_text(encoding="utf-8"))
    root = fabric_smoke._stage(w)
    root.joinpath(".books-lore-fixture").write_text("Disposable CF18 Books & Lore fixture\n", encoding="utf-8")
    config = root / "config" / "chronicle"; config.mkdir(parents=True, exist_ok=True)
    (config / "books.json").write_text(json.dumps({"schemaVersion": 1, "books": [
        {"id": "test:kept", "title": "Kept", "author": "", "description": "fixture", "pages": ["kept page"], "rule": "UNTIL_FOUND"},
        {"id": "test:once", "title": "Once", "author": "", "description": "fixture", "pages": ["once page"], "rule": "ONCE"},
        {"id": "test:gift", "title": "Gift", "author": "Fixture author", "description": "GM delivery fixture", "pages": ["A deliberate gift."], "rule": "OFF"},
        {"id": "test:editing", "title": "Editing", "author": "Fixture author", "description": "editor fixture", "pages": ["alpha", "", "omega"], "rule": "OFF"},
    ]}), encoding="utf-8")
    (config / "chest-loot.json").write_text(json.dumps({"schemaVersion": 1, "types": [
        {"id": "test:books", "label": "Books", "description": "Disposable Books fixture", "recommendedChests": 2, "refillMinutes": .05,
         "entries": [{"item": "minecraft:bread", "chancePercent": 100, "minCount": 1, "maxCount": 1}, {"item": "minecraft:written_book", "chancePercent": 100, "minCount": 1, "maxCount": 1}],
         "books": [{"bookId": "test:kept", "chancePercent": 0}, {"bookId": "test:once", "chancePercent": 100}]},
        {"id": "test:other", "label": "Other", "description": "Other table", "recommendedChests": 1, "refillMinutes": .05,
         "entries": [{"item": "minecraft:stick", "chancePercent": 100, "minCount": 1, "maxCount": 1}], "books": []},
    ]}), encoding="utf-8")
    reports = w.REPORTS / ("books-lore-" + root.name)
    reports.mkdir(parents=True)
    report = {"result": "FAIL", "world": str(root), "reports": str(reports), "checks": []}
    server = None
    handle = None
    clients = []
    try:
        menu_smoke._reserve_ports()
        handle = (reports / "server.log").open("w", encoding="utf-8")
        environment = w.env(); environment["CHRONICLE_BOOKS_TRIAL"] = "true"
        server = subprocess.Popen(w.server_command(), cwd=root, env=environment, stdin=subprocess.PIPE,
                                  stdout=handle, stderr=subprocess.STDOUT, text=True, encoding="utf-8")
        menu_smoke._wait_ready(server, reports / "server.log")
        report["checks"].append("fresh isolated loopback server started")
        # GUI scale 2 makes these 640-wide desktop and 480-wide narrow captures.
        # Separate clients avoid retaining a local menu draft between layouts.
        for label, width, height in (("desktop", 1280, 720), ("narrow-480", 960, 960)):
            clients = [_client(w, launch, root, reports, label, width, height, label == "desktop")]
            result = reports / (label + ".json")
            fabric_smoke._await_clients(clients, [result], timeout=240)
            proof = _result(result)
            if "Books & Lore page rendered through normal menu transport" not in proof.get("checks", []):
                raise RuntimeError("Books probe ended before page render assertion: " + label)
            report["checks"].append(label + ": Books & Lore native capture passed")
            for _, logfile in clients:
                logfile.close()
            clients = []
            if label == "desktop":
                fabric_smoke._stop(server); server = None
                if (root / ".books-gift-offline").read_text(encoding="utf-8") != "PASS":
                    raise RuntimeError("Offline historical finder status failed")
                report["checks"].append("first finder remains visible after the actual recipient disconnects")
                handle.close(); handle = (reports / "server-restart.log").open("w", encoding="utf-8")
                menu_smoke._reserve_ports()
                server = subprocess.Popen(w.server_command(), cwd=root, env=environment, stdin=subprocess.PIPE,
                                          stdout=handle, stderr=subprocess.STDOUT, text=True, encoding="utf-8")
                menu_smoke._wait_ready(server, reports / "server-restart.log")
                report["checks"].append("saved Books delivery fixture restarted")
        report["result"] = "PASS"
        print("PASS: Books & Lore native menu smoke; evidence:", reports, flush=True)
    except BaseException as error:
        report["error"] = str(error)
        raise
    finally:
        for process, logfile in clients:
            if process.poll() is None:
                process.terminate()
            logfile.close()
        try:
            fabric_smoke._stop(server)
        finally:
            if handle:
                handle.close()
        text = json.dumps(report, indent=2) + "\n"
        (reports / "report.json").write_text(text, encoding="utf-8")
        (w.REPORTS / "books-lore-latest.json").write_text(text, encoding="utf-8")


if __name__ == "__main__":
    run()
