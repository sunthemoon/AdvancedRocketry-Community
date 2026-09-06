#!/usr/bin/env python3
"""Run the pinned Forge/JEI matrix with real packaged loopback clients.

No launcher accounts are read. This records compatibility observations, not
authenticated multiplayer behavior, a visual review or a release approval.
"""

from __future__ import annotations

import argparse
import ctypes
from ctypes import wintypes
from datetime import datetime, timezone
import os
from pathlib import Path
import re
import shutil
import sys

if __package__:
    from . import run_dedicated_server_smoke as server
    from . import v100_compatibility_runtime as runtime
    from .validate_build_artifact import validate_artifact
else:
    import run_dedicated_server_smoke as server
    import v100_compatibility_runtime as runtime
    from validate_build_artifact import validate_artifact


CELLS = (("47.4.10", True, "V100Jei10"), ("47.4.10", False, "V100NoJei10"),
         ("47.4.23", True, "V100Jei23"), ("47.4.23", False, "V100NoJei23"))
MAX_LOG = 16 * 1024 * 1024
CLIENT_OPTIONS = "renderDistance:4\nsimulationDistance:5\nmaxFps:60\nguiScale:2\nfullscreen:false\n"


def now() -> str:
    return datetime.now(timezone.utc).isoformat()


def marker(message: str) -> re.Pattern:
    return re.compile(r"^(?:\[[^\]\r\n]+\]\s*)+[^\r\n]*?:\s+" + re.escape(message) + r"\s*$")


def client_markers(forge: str, jei: bool, username: str, port: int, version: str) -> list[str]:
    values = [f"Setting user: {username}", forge_message(forge),
              f"Advanced Rocketry: Community Edition {version} initialized",
              f"Connecting to 127.0.0.1, {port}", "Connected to a modded server."]
    values.append(f"ARCE-BETA-1100 optional_compat=jei status=present version={runtime.JEI}" if jei
                  else "ARCE-BETA-1100 optional_compat=jei status=absent version=absent")
    if jei:
        values.append("ARCE-BETA-1100 optional_compat=jei status=registered recipes=1")
    return values


def forge_message(forge: str) -> str:
    return f"Forge mod loading, version {forge}, for MC 1.20.1 with MCP 20230612.114412"


def audit_client(lines: list[str], forge: str, jei: bool, username: str, port: int, version: str) -> dict:
    positions = {}
    for value in client_markers(forge, jei, username, port, version):
        indices = [index for index, line in enumerate(lines) if marker(value).search(line)]
        if not indices:
            raise server.SmokeError(f"Packaged client is missing native log receipt: {value}")
        positions[value] = indices
    connected = positions["Connected to a modded server."][-1]
    if connected < positions[f"Connecting to 127.0.0.1, {port}"][-1]:
        raise server.SmokeError("Client connection receipts are out of order")
    if jei and positions["ARCE-BETA-1100 optional_compat=jei status=registered recipes=1"][-1] < connected:
        raise server.SmokeError("JEI recipe was not observed after server connection")
    for line in lines:
        if any(value in line for value in ("Unknown recipe category", "NoClassDefFoundError", "ClassNotFoundException",
                                            "Attempted to load class net/minecraft/client")):
            raise server.SmokeError(f"Client linkage/category finding: {line.strip()}")
        if re.search(r"/(?:ERROR|FATAL)\]", line) and re.search(r"advancedrocketrycommunity|\[io\.gi\.su\.ad\.", line, re.I):
            raise server.SmokeError(f"Project client error: {line.strip()}")
        if re.search(r"/(?:ERROR|FATAL)\]", line):
            raise server.SmokeError(f"External client error requires investigation: {line.strip()}")
    opposite = "status=absent version=absent" if jei else "status=present version="
    if any("optional_compat=jei " + opposite in line for line in lines):
        raise server.SmokeError("Client reports conflicting JEI presence")
    return {"required_receipt_line_numbers": {value: [i + 1 for i in indices] for value, indices in positions.items()},
            "log_audit": server.log_audit_counts(lines),
            "external_error_or_fatal_lines": [line.strip() for line in lines if re.search(r"/(?:ERROR|FATAL)\]", line)],
            "renderer_lines": [line.strip() for line in lines if "Renderer:" in line or "GL info:" in line],
            "jei_synchronized_recipe_count": 1 if jei else None}


def check_mods(game: Path, artifact: Path, jei: Path | None) -> list[dict]:
    expected = {artifact.name: server.digest_file(artifact)}
    if jei:
        expected["jei.jar"] = server.digest_file(jei)
    actual = {}
    for path in (game / "mods").iterdir():
        runtime.verify_file(path)
        actual[path.name] = server.digest_file(path)
    if actual != expected:
        raise server.SmokeError("Installed mods differ from the exact matrix inputs")
    return [{"file": name, "sha256": digest} for name, digest in sorted(actual.items())]


def install_mods(game: Path, artifact: Path, jei: Path | None) -> list[dict]:
    (game / "mods").mkdir(exist_ok=False)
    shutil.copy2(artifact, game / "mods" / artifact.name)
    if jei:
        shutil.copy2(jei, game / "mods/jei.jar")
    return check_mods(game, artifact, jei)


def select_owned_window(windows: list[dict], pid: int) -> int:
    targets = [value["handle"] for value in windows if value["pid"] == pid and value["visible"]
               and value["class"].startswith("GLFW")]
    if len(targets) != 1:
        raise server.SmokeError(f"Expected one visible owned GLFW client window, observed {len(targets)}: {windows}")
    return targets[0]


def close_owned_window(process: server.CapturedProcess) -> dict:
    if os.name != "nt" or process.process.poll() is not None:
        raise server.SmokeError("Cannot close a missing owned Windows client")
    user32 = ctypes.WinDLL("user32", use_last_error=True)
    callback_type = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
    user32.EnumWindows.argtypes = [callback_type, wintypes.LPARAM]
    user32.GetWindowThreadProcessId.argtypes = [wintypes.HWND, ctypes.POINTER(wintypes.DWORD)]
    user32.GetClassNameW.argtypes = [wintypes.HWND, wintypes.LPWSTR, ctypes.c_int]
    user32.GetWindowTextW.argtypes = [wintypes.HWND, wintypes.LPWSTR, ctypes.c_int]
    user32.IsWindowVisible.argtypes = [wintypes.HWND]
    user32.PostMessageW.argtypes = [wintypes.HWND, wintypes.UINT, wintypes.WPARAM, wintypes.LPARAM]
    windows = []

    @callback_type
    def visit(handle, _):
        pid = wintypes.DWORD()
        user32.GetWindowThreadProcessId(handle, ctypes.byref(pid))
        if pid.value == process.process.pid:
            name = ctypes.create_unicode_buffer(256)
            title = ctypes.create_unicode_buffer(256)
            user32.GetClassNameW(handle, name, len(name))
            user32.GetWindowTextW(handle, title, len(title))
            windows.append({"handle": handle, "pid": pid.value, "class": name.value, "title": title.value,
                            "visible": bool(user32.IsWindowVisible(handle))})
        return True

    user32.EnumWindows(visit, 0)
    target = select_owned_window(windows, process.process.pid)
    if process.process.poll() is not None:
        raise server.SmokeError("Owned client exited before window close")
    if not user32.PostMessageW(target, 0x0010, 0, 0):
        raise server.SmokeError("Owned client close message failed")
    return {"observed_owned_windows": windows, "closed_handle": target}


def argument_file(path: Path, command: list[str]) -> None:
    if any(any(char in value for char in "\r\n\0") for value in command):
        raise server.SmokeError("Unsafe Java argument-file value")
    with path.open("x", encoding="utf-8", newline="\n") as stream:
        stream.write("\n".join('"' + value.replace("\\", "\\\\").replace('"', '\\"') + '"' for value in command) + "\n")


def receipt(process: server.CapturedProcess, log: Path, started: str, code: int) -> dict:
    if not 0 < log.stat().st_size <= MAX_LOG:
        raise server.SmokeError("Runtime log size is outside diagnostic bound")
    return {"pid": process.process.pid, "started_at": started, "completed_at": now(), "exit_code": code,
            "full_log_file": log.name, "full_log_bytes": log.stat().st_size, "full_log_sha256": server.digest_file(log)}


def stop_server(process: server.CapturedProcess) -> int:
    start = len(process.lines)
    process.command("save-all flush")
    process.wait_for(server.SAVE_MARKER, 60, start_at=start)
    process.command("stop")
    code = process.finish()
    findings = server.scan_log(process.lines)
    if code or findings:
        raise server.SmokeError(f"Packaged server stop/audit failed: exit={code}; {findings[:1]}")
    return code


def run_lane(args, forge: str, artifact: Path, summary: dict) -> None:
    source = runtime.safe_path(args.runtime / "servers" / forge)
    entries = list(source.rglob("*"))
    if len(entries) > 20000 or any(server.is_link_or_junction(p) for p in entries):
        raise server.SmokeError("Unsafe installed server template")
    if sum(path.stat().st_size for path in entries if path.is_file()) > 2 * 1024 * 1024 * 1024:
        raise server.SmokeError("Server template exceeds diagnostic byte bound")
    if any((source / name).exists() for name in ("world", "mods", "server.properties")):
        raise server.SmokeError("Server template is not a pristine installer output")
    game = args.work / f"server-{forge}"
    shutil.copytree(source, game)
    install_mods(game, artifact, None)
    port = server.allocate_port()
    properties = server.write_server_configuration(game, port, True)
    args_file = game / f"libraries/net/minecraftforge/forge/1.20.1-{forge}/win_args.txt"
    runtime.verify_file(args_file)
    command = [args.java, "-Xms512M", "-Xmx2G", f"@{args_file}", "nogui"]
    for cycle in ("matrix", "restart"):
        log = args.evidence / f"server-{forge}-{cycle}.txt"
        started = now()
        process = server.CapturedProcess(command, game, log)
        try:
            process.wait_for(server.READY_MARKER, 240)
            process.wait_for(marker(forge_message(forge)), 10)
            status = server.wait_for_status(port)
            server.validate_status_identity(status, args.version)
            if cycle == "matrix":
                for _, jei, username in (cell for cell in CELLS if cell[0] == forge):
                    run_client(args, forge, jei, username, port, artifact, process, summary)
            code = stop_server(process)
            summary["servers"].append({"forge": forge, "cycle": cycle, "port": port,
                                       "server_properties_startup_sha256": properties, "offline_loopback": True,
                                       "mods": check_mods(game, artifact, None), "status": status,
                                       "log_audit": server.log_audit_counts(process.lines),
                                       **receipt(process, log, started, code)})
        finally:
            process.abort()
            summary["owned_processes"].append(receipt(process, log, started, process.process.returncode))


def run_client(args, forge: str, jei: bool, username: str, port: int, artifact: Path,
               dedicated: server.CapturedProcess, summary: dict) -> None:
    cell = f"forge-{forge}-jei-{'present' if jei else 'absent'}"
    game = args.work / cell
    game.mkdir(exist_ok=False)
    dependency = args.runtime / "jei.jar" if jei else None
    installed = install_mods(game, artifact, dependency)
    (game / "options.txt").write_text(CLIENT_OPTIONS,
                                      encoding="utf-8", newline="\n")
    command = runtime.client_command(args.runtime, game, forge, username, port, args.java)
    argument_file(game / "client.args", command[1:])
    shutil.copy2(game / "client.args", args.evidence / f"{cell}-client.args")
    log = args.evidence / f"{cell}.txt"
    started = now()
    start = len(dedicated.lines)
    process = server.CapturedProcess([args.java, "@client.args"], game, log)
    try:
        process.wait_for(marker("Connected to a modded server."), 240)
        dedicated.wait_for(marker(f"{username} joined the game"), 60, start_at=start)
        if jei:
            process.wait_for(marker("ARCE-BETA-1100 optional_compat=jei status=registered recipes=1"), 60)
        audit = audit_client(process.lines, forge, jei, username, port, args.version)
        handles = close_owned_window(process)
        code = process.finish()
        if code != 0:
            raise server.SmokeError(f"Packaged client exited with {code}")
        dedicated.wait_for(marker(f"{username} left the game"), 60, start_at=start)
        audit = audit_client(process.lines, forge, jei, username, port, args.version)
        if check_mods(game, artifact, dependency) != installed:
            raise server.SmokeError("Client mods changed while running")
        summary["clients"].append({"cell": cell, "forge": forge, "jei": runtime.JEI if jei else "absent",
                                   "username": username, "kind": "packaged_forge_client", "mods": installed,
                                   "launch_target": "forgeclient", "clean_close": "owned_GLFW_WM_CLOSE",
                                   "window_close": handles, "server_join_leave_observed": True,
                                   **audit, **receipt(process, log, started, code)})
        print(f"[PASS] {cell}: native connection/JEI receipts; client exit={code}", flush=True)
    finally:
        process.abort()
        summary["owned_processes"].append(receipt(process, log, started, process.process.returncode))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime", type=Path, required=True)
    parser.add_argument("--work", type=Path, required=True)
    parser.add_argument("--evidence", type=Path, required=True)
    parser.add_argument("--artifact", type=Path, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--java", required=True)
    args = parser.parse_args()
    summary = {"schema_version": 1, "target_version": "v1.0.0", "source_state": "development_worktree",
               "started_at": now(), "status": "FAIL", "clients": [], "servers": [], "owned_processes": [],
               "authentication_tested": False, "visual_or_multiplayer_behavior_approved": False}
    created = False
    try:
        if os.name != "nt" or any(os.environ.get(key) for key in ("MOD_CLASSES", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS")):
            raise server.SmokeError("Requires Windows with no inherited Java/userdev launch injection")
        args.java, summary["java_version"] = server.resolve_java(args.java)
        for key in ("runtime", "work", "evidence", "artifact"):
            setattr(args, key, runtime.safe_path(getattr(args, key)))
        paths = (args.runtime, args.work, args.evidence)
        if any(a == b or a.is_relative_to(b) or b.is_relative_to(a) for i, a in enumerate(paths) for b in paths[i + 1:]):
            raise server.SmokeError("Runtime, work and evidence directories must be disjoint")
        if args.work.exists() or args.evidence.exists():
            raise server.SmokeError("Refusing to overwrite a matrix work/evidence directory")
        prepared = runtime.read_json(args.runtime / "prepared.json")
        if prepared.get("forge") != list(runtime.FORGE_HASHES) or prepared.get("jei") != runtime.JEI:
            raise server.SmokeError("Runtime preparation does not match the pinned matrix")
        if server.digest_file(args.runtime / "jei.jar") != prepared.get("jei_sha256"):
            raise server.SmokeError("Prepared JEI changed")
        runtime.verify_file(args.runtime / "jei.jar", runtime.JEI_SHA1)
        errors, details = validate_artifact(args.artifact, args.version)
        if errors:
            raise server.SmokeError(f"Artifact audit: {errors[0]}")
        summary.update({"artifact": details, "build": args.version, "prepared_runtime": prepared})
        args.work.mkdir(parents=True)
        args.evidence.mkdir(parents=True)
        created = True
        for source in (Path(__file__), Path(runtime.__file__)):
            shutil.copy2(source, args.evidence / source.name)
        for forge in runtime.FORGE_HASHES:
            run_lane(args, forge, args.artifact, summary)
        if server.digest_file(args.artifact) != details["sha256"]:
            raise server.SmokeError("Input artifact changed during the matrix")
        summary["status"] = "PASS"
        print("[PASS] Four packaged clients and both pinned dedicated-server lanes")
        return 0
    except (OSError, ValueError, KeyError, server.SmokeError) as exc:
        summary["failure"] = str(exc)
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1
    finally:
        if created:
            summary["completed_at"] = now()
            runtime.write_json(args.evidence / "summary.json", summary)


if __name__ == "__main__":
    sys.exit(main())
