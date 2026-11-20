"""Server-only qualification of one prepared campaign cell on a disposable copy.

The detached caller owns the heavy-JVM lock for the entire invocation, releases
it in its finally block, and waits 90 seconds before acquiring it for another cell.
No clients, downloads, source-cell edits or Gate approvals are performed.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import shutil
import sys
import time
import tomllib
import zipfile

SCRIPTS = Path(__file__).resolve().parents[1]
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))

import loader_cut_nbt as nbt
import prepare_campaign_cells as prep
from campaign_client.cells import read_object
from campaign_client.processes import CapturedProcess
from campaign_client.server import server_command, write_settings
from campaign_observe.raw_ticks import PREFIX, RAW_TICK_COMMAND, parse_raw_tick_reply, summarize_raw_ticks
from run_dedicated_server_smoke import scan_log, V180_STARTUP_VERSION

LOCK = Path("D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm")
READY = 'Done ('
HEADER = re.compile(r"\[(?:[^\]]+)/(?P<level>WARN|ERROR|FATAL)\] \[(?P<logger>[^\]]+)\]: (?P<message>.*)")
OFFLINE_WARNINGS = {
    "**** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!",
    "The server will make no attempt to authenticate usernames. Beware.",
    "While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.",
    'To change this, set "online-mode" to "true" in the server.properties file.',
}


def now():
    return datetime.now(timezone.utc).isoformat()


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def tree_hashes(root):
    prep.check_tree(root)
    return {p.relative_to(root).as_posix(): prep.digest_file(p)
            for p in sorted(root.rglob("*")) if p.is_file()}


def declared_mods(jar):
    """Read actual [[mods]] IDs, not optional dependency modId declarations."""
    with zipfile.ZipFile(jar) as archive:
        info = archive.getinfo("META-INF/mods.toml")
        if info.file_size > 1024 * 1024:
            raise ValueError("Oversized mod metadata")
        value = tomllib.loads(archive.read(info).decode("utf-8"))
    mods = value.get("mods")
    if not isinstance(mods, list) or not 1 <= len(mods) <= 256:
        raise ValueError("Missing or oversized [[mods]] declarations")
    return {mod["modId"] for mod in mods}


def validate_cell(path, expected):
    path = prep.safe_path(path)
    cell = path.parent
    prep.check_tree(cell)
    value = read_object(path)
    forge, jei = value["forge"], value["jei"]
    if forge not in ("47.4.10", "47.4.23") or type(jei) is not bool:
        raise ValueError("Unsupported Forge/JEI fields")
    if type(value["schema_version"]) is not int or value["schema_version"] != 1:
        raise ValueError("Unsupported schema")
    if cell.name != prep.cell_name(forge, jei):
        raise ValueError("Cell name disagrees with fields")
    runtime = prep.safe_path(Path(value["runtime_root"]))
    candidate = value["candidate"]
    if candidate["sha256"] != prep.sha256(expected):
        raise ValueError("Cell is not the requested candidate")
    actual = prep.manifest(runtime, cell, forge, jei, f"1.20.1-forge-{forge}",
                           prep.jar_name(Path(candidate["path"]).name), expected)
    if value != actual:
        raise ValueError("Manifest differs from installed hashes, paths or fields")
    for side, key in (("server", "server_dir"), ("client", "client_game_dir")):
        mods = [declared_mods(p) for p in (Path(value[key]) / "mods").iterdir()]
        if sum("jei" in ids for ids in mods) != int(side == "client" and jei):
            raise ValueError("JEI metadata placement disagrees with cell")
    server = Path(value["server_dir"])
    if (server / "world").exists() or (server / "logs").exists():
        raise ValueError("Source cell is not unused")
    if (server / "eula.txt").read_text(encoding="utf-8").strip() != "eula=true":
        raise ValueError("Prepared EULA acceptance missing")
    return value


def check_lock(token):
    if not token.startswith("W425 pid=") or "\n" in token or "\r" in token:
        raise ValueError("Expected the W425 caller's lock token")
    owner = (LOCK / "owner.txt").read_text(encoding="utf-8-sig").splitlines()[0]
    if not owner.startswith(token + " "):
        raise ValueError("Caller does not own heavy-JVM lock")


def disk_check(output):
    roots = {output.anchor, "C:/"} if os.name == "nt" else {output.anchor}
    result = {root: shutil.disk_usage(root).free for root in roots}
    if any(free < 10 * 1024**3 for free in result.values()):
        raise ValueError("Less than 10 GiB free before server start")
    return result


def analyze_polls(lines):
    polls = [parse_raw_tick_reply(line) for line in lines if PREFIX in line]
    summary = summarize_raw_ticks(polls, 500)
    overlaps = [len({t for t, _ in a.samples} & {t for t, _ in b.samples})
                for a, b in zip(polls, polls[1:])]
    summary.update(poll_count=len(polls), consecutive_overlap_ticks=overlaps,
                   all_consecutive_polls_overlap=bool(overlaps) and all(x > 0 for x in overlaps))
    return summary


def audit_log(path):
    """Compare infrastructure WARNs with existing startup expectations, not negative tests.

    Packaged logs must not need userdev's missing-refmap allowance. Offline-mode
    notices are expected only because the caller explicitly selects loopback/offline.
    Unknown warnings remain findings. All matched native lines are retained.
    """
    lines = path.read_text(encoding="utf-8").splitlines()
    specs = read_object(SCRIPTS / "gametest_expected_log.json")["expectations"]
    specs = [x for x in specs if x["level"] == "WARN" and x.get("test", "").startswith("environment:")
             and x["id"] != "mixin-userdev-refmap-unread"]
    counts, warnings, unexpected, errors = {}, [], [], []
    for line in lines:
        match = HEADER.search(line)
        if not match:
            continue
        if match["level"] != "WARN":
            errors.append(line)
            continue
        identifier = None
        for spec in specs:
            if match["logger"] == spec["logger"] and re.fullmatch(spec["message"], match["message"]):
                identifier = spec["id"]
                counts[identifier] = counts.get(identifier, 0) + 1
                if counts[identifier] > spec["max"]:
                    unexpected.append(line)
                break
        if identifier is None and match["logger"] == "net.minecraft.server.dedicated.DedicatedServer/" \
                and match["message"] in OFFLINE_WARNINGS:
            identifier = "explicit-offline-mode"
            counts[identifier] = counts.get(identifier, 0) + 1
            if counts[identifier] > len(OFFLINE_WARNINGS):
                unexpected.append(line)
        if identifier is None and "ARCE_FIRST_EVENT_HELD" in match["message"]:
            # The native scanner below enforces the exact logger, message and 0..1 limit.
            identifier = "native-startup-hold"
        warnings.append({"expectation": identifier, "line": line})
        if identifier is None:
            unexpected.append(line)
    return {"errors_and_fatals": errors, "warnings": warnings, "expected_counts": counts,
            "unexpected_warnings": unexpected,
            "native_findings": scan_log(lines, expected_mod_version=V180_STARTUP_VERSION)}


def world_identity(path):
    data = nbt.plain(nbt.read_gzip(path)[1])["Data"]
    identity = {key: data[key] for key in ("LevelName", "WorldGenSettings", "SpawnX", "SpawnY", "SpawnZ", "DataVersion")}
    return {"identity": identity, "time": data["Time"], "level_dat_sha256": prep.digest_file(path)}


def stop_server(process, commands):
    commands.append({"utc": now(), "command": "save-all flush"})
    start = process.count
    process.command("save-all flush")
    process.wait_for("Saved the game", 60, time.sleep, start=start)
    commands.append({"utc": now(), "command": "stop"})
    process.command("stop")
    process.process.wait(timeout=60)
    if process.process.returncode != 0:
        raise ValueError("Server did not stop cleanly")


def run_cell(manifest, output, java, expected, token, port):
    output = prep.safe_path(output)
    source_cell = prep.safe_path(manifest).parent
    if output == source_cell or output.is_relative_to(source_cell) or source_cell.is_relative_to(output):
        raise ValueError("Evidence and source cell must not overlap")
    output.mkdir(parents=True, exist_ok=False)
    result = {"started_utc": now(), "checker_sha256": prep.digest_file(Path(__file__)),
              "checks": {}, "commands": [], "processes": [], "findings": []}
    server, active, cleanup_safe = output / "server-copy", None, True
    before = tree_hashes(source_cell)
    write_json(output / "source-before.json", before)
    try:
        check_lock(token)
        cell = validate_cell(manifest, expected)
        write_json(output / "cell-input.json", cell)
        result["manifest_sha256"] = prep.digest_file(manifest)
        result["checks"]["preflight"] = "PASS"
        shutil.copytree(Path(cell["server_dir"]), server)
        copied = tree_hashes(server)
        if copied != tree_hashes(Path(cell["server_dir"])):
            raise ValueError("Server copy differs from source")
        write_json(output / "copy-before.json", copied)
        spec = {"online-mode": False, "white-list": True, "enforce-whitelist": True,
                "server-ip": "127.0.0.1", "server-port": port, "whitelist": [], "ops": [],
                "max_heap": "3G", "jvm_properties": {"advancedrocketrycommunity.releaseTestHooks": "true"}}
        write_settings(server, spec)
        # Preserve vanilla generation/settings: this is a cell start, not a tuned performance fixture.
        write_json(output / "settings.json", spec)
        command = server_command(java, server, cell["forge"], spec)
        write_json(output / "launch.json", {"command": command, "java_sha256": prep.digest_file(java)})
        for epoch in ("first", "restart"):
            check_lock(token)
            result.setdefault("free_bytes", {})[epoch] = disk_check(output)
            started = time.monotonic()
            active = CapturedProcess(command, server, output / f"{epoch}-console.log", console=True)
            entry = {"epoch": epoch, "pid": active.process.pid, "started_utc": now()}
            result["processes"].append(entry)
            try:
                entry["ready_line"] = active.wait_for(READY, 300, time.sleep)
                entry["ready_seconds"] = time.monotonic() - started
                result["checks"][epoch + "_ready"] = "PASS"
                initial = audit_log(server / "logs/latest.log")
                if initial["errors_and_fatals"] or initial["native_findings"]:
                    raise ValueError("Server startup product/log finding; stop this cell")
                if epoch == "first":
                    # Readiness can precede the first completed END snapshot. Allow
                    # one nominal ring before requiring overlap, then collect 180 s.
                    time.sleep(5)
                    poll_start = time.monotonic()
                    with (output / "raw-replies.log").open("x", encoding="utf-8") as replies:
                        # Endpoint polls at t=0 and t=180 cover a full three-minute interval.
                        for index in range(91):
                            time.sleep(max(0, poll_start + index * 2 - time.monotonic()))
                            start = active.count
                            sent = time.monotonic()
                            result["commands"].append({"utc": now(), "command": RAW_TICK_COMMAND,
                                                       "elapsed_seconds": sent - poll_start})
                            active.command(RAW_TICK_COMMAND)
                            line = active.wait_for(PREFIX, 10, time.sleep, start=start)
                            replies.write(line + "\n")
                            replies.flush()
                    raw = analyze_polls((output / "raw-replies.log").read_text(encoding="utf-8").splitlines())
                    write_json(output / "raw-summary.json", raw)
                    result["raw_summary"] = raw
                    result["checks"]["raw_ticks"] = ("PASS" if raw["poll_count"] == 91 and
                        raw["tick_count"] > 0 and not raw["missing_tick_count"] and
                        raw["all_consecutive_polls_overlap"] else "FAIL")
                stop_server(active, result["commands"])
                entry["exit_code"] = active.process.returncode
            finally:
                try:
                    active.close(server=True)
                except BaseException:
                    cleanup_safe = False
                    (output / "CLEANUP-UNCONFIRMED.txt").write_text("Retain lock and copy; owned job cleanup failed.\n")
                    raise
                active = None
                for name in ("latest.log", "debug.log"):
                    if (server / "logs" / name).exists():
                        shutil.copy2(server / "logs" / name, output / f"{epoch}-{name}")
                if (server / "server.properties").exists():
                    shutil.copy2(server / "server.properties", output / f"{epoch}-server.properties")
                audit = audit_log(output / f"{epoch}-latest.log")
                audit["console_errors_and_fatals"] = [line for line in
                    (output / f"{epoch}-console.log").read_text(encoding="utf-8").splitlines()
                    if re.search(r"\[[^\]]+/(?:ERROR|FATAL)\]", line)]
                write_json(output / f"{epoch}-log-audit.json", audit)
                result["checks"][epoch + "_logs"] = ("FAIL" if audit["errors_and_fatals"] or
                    audit["console_errors_and_fatals"] or audit["unexpected_warnings"] or
                    audit["native_findings"] else "PASS")
            shutil.copy2(server / "world/level.dat", output / f"{epoch}-level.dat")
            identity = world_identity(output / f"{epoch}-level.dat")
            write_json(output / f"{epoch}-world.json", identity)
            if epoch == "first":
                first_identity = identity
            else:
                result["checks"]["world_identity"] = ("PASS" if
                    first_identity["identity"] == identity["identity"] and
                    identity["time"] > first_identity["time"] else "FAIL")
    except Exception as error:
        result["findings"].append(f"{type(error).__name__}: {error}")
    finally:
        # A failed owned-job cleanup is the only case that must retain copy and lock.
        if cleanup_safe and server.exists():
            resolved = prep.safe_path(server)
            if resolved.parent != output or resolved.name != "server-copy":
                raise ValueError("Refusing out-of-evidence cleanup")
            prep.check_tree(resolved)
            shutil.rmtree(resolved)
        after = tree_hashes(source_cell)
        write_json(output / "source-after.json", after)
        result["checks"]["source_unchanged"] = "PASS" if before == after else "FAIL"
        result["copy_removed"] = not server.exists()
        result["cleanup_confirmed"] = cleanup_safe
        result["finished_utc"] = now()
        required = {"preflight", "first_ready", "first_logs", "raw_ticks", "restart_ready",
                    "restart_logs", "world_identity", "source_unchanged"}
        result["passed"] = (not result["findings"] and required <= result["checks"].keys()
                            and all(x == "PASS" for x in result["checks"].values()) and result["copy_removed"])
        write_json(output / "result.json", result)
        write_json(output / "artifacts.json", tree_hashes(output))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--java", type=Path, required=True)
    parser.add_argument("--candidate-sha256", required=True)
    parser.add_argument("--lock-token", required=True)
    parser.add_argument("--port", type=int, default=25585)
    args = parser.parse_args()
    result = run_cell(args.manifest, args.output, args.java, args.candidate_sha256, args.lock_token, args.port)
    print(json.dumps(result, indent=2), flush=True)
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
