"""Isolated native regression for Chronicle's paused player gameplay boundary."""
from __future__ import annotations

import json
import socket
import subprocess
import time
from datetime import datetime, timezone
from pathlib import Path


def _reserve_ports() -> None:
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 25575))
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
        probe.bind(("127.0.0.1", 24455))


def _survival_fixture(root: Path) -> None:
    path = root / "server.properties"
    text = path.read_text(encoding="utf-8")
    if "gamemode=creative" not in text:
        raise RuntimeError("staged fixture did not retain the expected default game mode")
    path.write_text(text.replace("gamemode=creative", "gamemode=survival", 1), encoding="utf-8")


def _wait_path(path: Path, predicate, detail: str, timeout: float = 45) -> dict:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if path.is_file():
            try:
                value = json.loads(path.read_text(encoding="utf-8"))
                if value.get("result") == "FAIL":
                    raise RuntimeError("pause bot failed: " + str(value))
                if predicate(value):
                    return value
            except json.JSONDecodeError:
                pass
        time.sleep(.1)
    raise RuntimeError("timed out waiting for " + detail)


def _wait_log(log: Path, needle: str, timeout: float = 45) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if log.is_file():
            contents = log.read_text(encoding="utf-8", errors="replace")
            if "Pause trial FAIL" in contents:
                raise RuntimeError("Native pause assertion failed; see " + str(log))
            if needle in contents:
                return
        time.sleep(.1)
    raise RuntimeError("timed out waiting for server feedback: " + needle)


def _command(server: subprocess.Popen, log: Path, command: str, expected: str) -> None:
    if server.stdin is None:
        raise RuntimeError("pause fixture server has no console input")
    server.stdin.write(command + "\n")
    server.stdin.flush()
    _wait_log(log, expected)


def _start(w, root: Path, reports: Path):
    _reserve_ports()
    log_path = reports / "server.log"
    handle = log_path.open("w", encoding="utf-8")
    environment = w.env()
    environment["CHRONICLE_PAUSE_NATIVE_TRIAL"] = "true"
    server = subprocess.Popen(w.server_command(), cwd=root, env=environment, stdin=subprocess.PIPE,
                              stdout=handle, stderr=subprocess.STDOUT, text=True, encoding="utf-8")
    try:
        _wait_log(log_path, "Done (", timeout=240)
        _wait_log(log_path, "Voice chat server started at 127.0.0.1:24455", timeout=30)
        return server, handle, log_path
    except BaseException:
        try:
            import fabric_smoke
            fabric_smoke._stop(server)
        finally:
            handle.close()
        raise


def run() -> None:
    import fabric_smoke
    import workspace as w

    w.require_eula()
    w.validate_server_runtime()
    w.validate_managed_mod_names()
    w.run([w.REPO / "gradlew.bat", "--no-daemon", ":core:remapJar", ":campaigns:quiet-below:remapJar", ":campaigns:quiet-below:writeSmokeClientLaunch"])
    launch = json.loads((w.REPO / "campaigns/quiet-below/build/smoke/client-launch.json").read_text(encoding="utf-8"))
    root = fabric_smoke._stage(w)
    _survival_fixture(root)
    reports = w.REPORTS / ("pause-gameplay-" + root.name)
    reports.mkdir(parents=True)
    control = reports / "control.json"
    result = reports / "bot.json"
    control.write_text(json.dumps({"stage": "wait"}), encoding="utf-8")
    report = {"result": "FAIL", "world": str(root), "reports": str(reports), "checks": []}
    server = None
    handle = None
    bot = None
    try:
        server, handle, log = _start(w, root, reports)
        environment = w.env()
        environment.update({"CHRONICLE_PAUSE_CONTROL": str(control), "CHRONICLE_PAUSE_SMOKE": str(result)})
        bot_log = (reports / "bot.log").open("w", encoding="utf-8")
        try:
            bot, ignored = fabric_smoke._client(w, launch, root, reports, "PauseTrial", "pause-client", environment)
            bot_log.close()
            bot_log = ignored
            _wait_path(result, lambda value: value.get("result") == "CONNECTED", "pause bot connection")
            _command(server, log, "chronicle_pause_trial setup PauseTrial", "Pause trial PASS setup")
            time.sleep(1)
            _command(server, log, "chronicle_pause_trial snapshot PauseTrial", "Pause trial PASS snapshot")

            control.write_text(json.dumps({"stage": "paused"}), encoding="utf-8")
            _wait_path(result, lambda value: value.get("result") == "READY" and value.get("stage") == "paused", "paused real-player actions")
            _command(server, log, "chronicle_pause_trial paused_check PauseTrial", "Pause trial PASS paused_check")
            _command(server, log, "chronicle_pause_trial paused_damage PauseTrial", "Pause trial PASS paused_damage")
            report["checks"].append("paused survival movement remained possible while normal placement, mining and food-use packets left world/inventory unchanged")
            report["checks"].append("paused health, hunger, exhaustion and status duration were preserved through direct server seams")

            _command(server, log, "chronicle_pause_trial transition PauseTrial chest_active", "Pause trial PASS chest_active")
            for stage in ("chest_open_active", "chest_pause_closed", "chest_paused_open"):
                if stage == "chest_pause_closed":
                    _command(server, log, "chronicle_pause_trial transition PauseTrial chest_pause", "Pause trial PASS chest_pause")
                control.write_text(json.dumps({"stage": stage}), encoding="utf-8")
                _wait_path(result, lambda value: value.get("result") == "READY" and value.get("stage") == stage, stage)
            _command(server, log, "chronicle_pause_trial transition PauseTrial chest_check", "Pause trial PASS chest_check")
            _command(server, log, "chronicle_pause_trial snapshot PauseTrial", "Pause trial PASS snapshot")
            report["checks"].append("a real ACTIVE-opened chest closes on pause; new paused right-clicks cannot open it or change its inventory")

            _command(server, log, "chronicle_pause_trial author_only PauseTrial", "Pause trial PASS author_only")
            control.write_text(json.dumps({"stage": "author_only"}), encoding="utf-8")
            _wait_path(result, lambda value: value.get("result") == "READY" and value.get("stage") == "author_only", "author-only paused placement denial")
            _command(server, log, "chronicle_pause_trial author_only_check PauseTrial", "Pause trial PASS author_only_check")
            _command(server, log, "chronicle_pause_trial non_operator_enable PauseTrial", "Pause trial PASS non_operator_enable")
            _command(server, log, "chronicle_pause_trial building_enable PauseTrial", "Pause trial PASS building_enable")
            control.write_text(json.dumps({"stage": "building"}), encoding="utf-8")
            _wait_path(result, lambda value: value.get("result") == "READY" and value.get("stage") == "building", "global Building Mode real-player actions")
            _command(server, log, "chronicle_pause_trial building_check PauseTrial", "Pause trial PASS building_check")
            control.write_text(json.dumps({"stage": "chest_building"}), encoding="utf-8")
            _wait_path(result, lambda value: value.get("result") == "READY" and value.get("stage") == "chest_building", "Building Mode chest transfer")
            _command(server, log, "chronicle_pause_trial transition PauseTrial chest_build_check", "Pause trial PASS chest_build_check")
            _command(server, log, "chronicle_pause_trial building_disable PauseTrial", "Pause trial PASS building_disable")
            control.write_text(json.dumps({"stage": "chest_disabled_closed"}), encoding="utf-8")
            _wait_path(result, lambda value: value.get("result") == "READY" and value.get("stage") == "chest_disabled_closed", "Building Mode chest closure")
            _command(server, log, "chronicle_pause_trial transition PauseTrial chest_disabled_check", "Pause trial PASS chest_disabled_check")
            control.write_text(json.dumps({"stage": "building_off"}), encoding="utf-8")
            _wait_path(result, lambda value: value.get("result") == "READY" and value.get("stage") == "building_off", "global Building Mode revocation")
            _command(server, log, "chronicle_pause_trial building_disabled_check PauseTrial", "Pause trial PASS building_disabled_check")
            report["checks"].append("author mode alone and a non-operator command could not enable paused building; the real global Building Mode command let a de-opped non-author survival player place and mine, then mode-off revoked it")
            report["checks"].append("Building Mode advances the vanilla world clock and entity gravity, holds server/client survival and Chronicle clocks, permits real chest transfers, and closes the chest when disabled")

            _command(server, log, "chronicle_pause_trial resume PauseTrial", "Pause trial PASS resume")
            control.write_text(json.dumps({"stage": "active"}), encoding="utf-8")
            _wait_path(result, lambda value: value.get("result") == "READY" and value.get("stage") == "active", "active real-player actions")
            _command(server, log, "chronicle_pause_trial active_check PauseTrial", "Pause trial PASS active_check")
            report["checks"].append("resume cleared global Building Mode before the same survival player placed, mined and consumed food normally; the ACTIVE actionbar appeared once without periodic repetition")
            _command(server,log,"chronicle_pause_trial transition PauseTrial prepare_food","Pause trial PASS prepare_food")
            control.write_text(json.dumps({"stage":"inflight"}),encoding="utf-8")
            _wait_path(result,lambda value:value.get("result")=="USING","active food use before pause")
            _command(server,log,"chronicle_pause_trial transition PauseTrial pause_food","Pause trial PASS pause_food")
            control.write_text(json.dumps({"stage":"wait"}),encoding="utf-8")
            time.sleep(2)
            _command(server,log,"chronicle_pause_trial transition PauseTrial check_food","Pause trial PASS check_food")
            _command(server,log,"chronicle_pause_trial transition PauseTrial hold","Pause trial PASS hold")
            _command(server,log,"chronicle_pause_trial transition PauseTrial end","Pause trial PASS end")
            report["checks"].append("pausing actual in-flight food use cancels it without consumption; resume, ACTIVE runtime holds, and ENDED phase clear global Building Mode and deny gameplay; releasing a hold resumes")
            control.write_text(json.dumps({"stage": "stop"}), encoding="utf-8")
            bot.wait(timeout=30)
            if bot.returncode:
                raise RuntimeError("pause bot exited " + str(bot.returncode))
            report["result"] = "PASS"
            print("PASS: isolated paused gameplay native proof; evidence:", reports, flush=True)
        finally:
            if bot is not None and bot.poll() is None:
                bot.terminate()
                try:
                    bot.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    bot.kill()
                    bot.wait(timeout=15)
            bot_log.close()
    except BaseException as error:
        report["error"] = str(error)
        raise
    finally:
        try:
            fabric_smoke._stop(server)
        finally:
            if handle is not None:
                handle.close()
            text = json.dumps(report, indent=2) + "\n"
            (reports / "report.json").write_text(text, encoding="utf-8")
            (w.REPORTS / "pause-gameplay-latest.json").write_text(text, encoding="utf-8")


if __name__ == "__main__":
    run()
