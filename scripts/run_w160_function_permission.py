#!/usr/bin/env python3
"""One disposable dedicated start proving release-absent fails function compilation at level two."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import uuid

import run_dedicated_server_smoke as smoke
import run_w158_release_cuts as release
from run_v060_flight_server_smoke import _server_command

MESSAGE = "Failed to load function advancedrocketrycommunity:w160_level_two"
REASON = "Whilst parsing command on line 1: Incorrect argument for command at position 57: ...000000160 <--[HERE]"


def declaration():
    return dict(scope="Only this compile-level-2 disposable start", logger="minecraft/ServerFunctionLibrary",
                level="ERROR", message=MESSAGE, expected=1,
                exceptions=["java.util.concurrent.CompletionException: java.lang.IllegalArgumentException: " + REASON,
                            "Caused by: java.lang.IllegalArgumentException: " + REASON])


def audit_log(lines):
    expected = declaration(); count = 0; remainder = []; events = []
    for line in lines:
        header = release.HEADER.fullmatch(line.rstrip())
        if header or not events: events.append([header, [line]])
        else: events[-1][1].append(line)
    for header, event in events:
        if header and header["message"] == MESSAGE:
            if (header["logger"] != expected["logger"] or header["level"] != "ERROR"
                    or not re.fullmatch(r"(?:Worker-Main-\d+|Server thread)", header["thread"])):
                raise smoke.SmokeError("Unexpected function compilation logger, thread or level")
            body = [line.rstrip() for line in event[1:] if line.strip()]
            exceptions = [line for line in body if not re.fullmatch(r"\s+(?:at .+|\.\.\. \d+ more)", line)]
            if exceptions != expected["exceptions"] or not any("CommandFunction." in line for line in body):
                raise smoke.SmokeError("Unexpected function compilation exception or stack")
            count += 1
        else:
            remainder.extend(event)
    findings = smoke.scan_log(remainder, expected_mod_version=smoke.V180_STARTUP_VERSION)
    if count != 1 or findings:
        raise smoke.SmokeError(f"Function-load manifest mismatch: count={count}, findings={findings}")
    return dict(observed=count, expectation=expected, raw=smoke.log_audit_counts(lines))


def run(args):
    repo = Path(__file__).resolve().parents[1]
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip()
    if commit != args.source_commit or subprocess.check_output(["git", "status", "--porcelain"], cwd=repo, text=True):
        raise smoke.SmokeError("Native run requires exact clean committed source")
    token = os.environ.get("W160_LOCK_TOKEN", "")
    lock = Path("D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm/owner.txt")
    if not token or not lock.read_text(encoding="utf-8-sig").startswith(token):
        raise smoke.SmokeError("Caller does not own the heavy-JVM lock")
    evidence = args.evidence_dir.resolve(); evidence.mkdir(exist_ok=False)
    parent = Path("D:/GitHub/ARCE-Task-Evidence/Temp").resolve(); session = parent / ("w160-function-" + uuid.uuid4().hex)
    result = dict(status="IN_PROGRESS", source_commit=commit, jar_sha256=smoke.digest_file(args.jar), starts=[])
    process = None
    try:
        session.mkdir(); shutil.copytree(args.source_server / "libraries", session / "libraries")
        (session / "mods").mkdir(); shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = smoke.allocate_port(); smoke.write_server_configuration(session, port, True)
        props = smoke.expected_server_properties(port, True)
        props.update({"level-name": "world-W160-functions", "function-permission-level": "2", "level-type": "minecraft:flat",
                      "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}], "biome": "minecraft:plains"})})
        (session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(props.items())), encoding="ascii")
        pack = session / props["level-name"] / "datapacks" / "w160"
        function = pack / "data" / "advancedrocketrycommunity" / "functions" / "w160_level_two.mcfunction"
        function.parent.mkdir(parents=True)
        (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": "W160 compile permission fixture"}}), encoding="utf-8")
        function.write_text("arce rocket recover 00000000-0000-0000-0000-000000000160 release-absent\n", encoding="ascii")
        (evidence / "expected-log.json").write_text(json.dumps(declaration(), indent=2), encoding="utf-8")
        command = _server_command(args.java); command[2] = "-Xmx4G"
        row = dict(case="compile-level-2", command=command, **release.resources()); result["starts"].append(row)
        (evidence / "results.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
        process = smoke.CapturedProcess(command, session, evidence / "server.log")
        process.wait_for(re.compile(r"Done \(.+\)! For help, type"), 300)
        process.command("stop"); row["exit"] = process.finish()
        if row["exit"] != 0: raise smoke.SmokeError("Dedicated server did not stop cleanly")
        row["log_audit"] = audit_log(process.lines)
        sidecar = session / props["level-name"] / "data" / release.SIDECAR
        if sidecar.exists(): raise smoke.SmokeError("Failed function load created a sidecar")
        row["sidecar_absent"] = True; result["status"] = "PASS"
    except Exception as failure:
        result.update(status="FAIL", error=str(failure))
    finally:
        if process is not None and process.process.poll() is None: process.abort()
        (evidence / "results.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
        if session.is_dir() and session.resolve().parent == parent and not smoke.is_link_or_junction(session): shutil.rmtree(session)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("source-server", "jar", "evidence-dir"): parser.add_argument("--" + name, type=Path, required=True)
    for name in ("java", "source-commit"): parser.add_argument("--" + name, required=True)
    result = run(parser.parse_args()); print(json.dumps(result, indent=2)); return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
