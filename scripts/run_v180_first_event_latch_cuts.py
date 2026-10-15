#!/usr/bin/env python3
"""FM9 and FP5: bounded journal delay, online release, then two complete-world restarts. N oracle only."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import shutil
import time
import uuid

if __package__:
    from . import run_dedicated_server_smoke as smoke
    from . import run_v180_first_event_moon_cuts as moon
else:
    import run_dedicated_server_smoke as smoke
    import run_v180_first_event_moon_cuts as moon

DESCENT = re.compile(r"first_event_fixture descent index=(\d) entity=([0-9a-f-]{36}) transfer=([0-9a-f-]{36})")
WAIT = re.compile(r"first_event_fixture latch_wait id=(\S+) thread=ARCE-rocket-journal")


def status(process) -> tuple[str, tuple]:
    begin = len(process.lines)
    process.command("arce progression status")
    index = process.wait_for(moon.STATUS, 15, start_at=begin)
    return process.lines[index].rstrip(), moon.STATUS.search(process.lines[index]).groups()


def inspect(world: Path, case: str, event: dict | None) -> dict:
    journal = moon.read_root(world / "data" / moon.JOURNAL)
    outbox = journal[moon.OUTBOX]
    actual = outbox.get("event")
    if outbox["state"] != "DELIVERED" or actual is None or event is not None and actual != event:
        raise smoke.SmokeError(f"{case}: source is not the exact DELIVERED event")
    progression = moon.read_root(world / "data" / moon.PROGRESSION)
    if progression[moon.MOON_RECEIPT] != {"occurred": 1, "event": actual}:
        raise smoke.SmokeError(f"{case}: receipt differs from source")
    if progression[moon.WARP_RECEIPT] != {"occurred": 0}:
        raise smoke.SmokeError(f"{case}: unrelated warp receipt changed")
    if case == "FM9":
        moon.inspect(world, case, moon.ints_uuid(actual["event_id"]), "DELIVERED", True)
        if len(journal["transfers"]) != 2 or any(record["phase"] != "COMMITTED" for record in journal["transfers"]):
            raise smoke.SmokeError("FM9: two committed transfer authorities were not preserved")
    elif journal["transfers"]:
        raise smoke.SmokeError("FP5: a zero-record source acquired a transfer")
    return dict(event=actual, transfers=journal["transfers"], outbox="DELIVERED", receipt="R(E)")


def wait_landed(process, entities: list[str], timeout: float) -> list[dict]:
    deadline = time.monotonic() + timeout
    while True:
        reports = [moon.FlightHarness.report(process, moon.MOON, entity) for entity in entities]
        if all(report["state"] == "LANDED" for report in reports):
            return reports
        if time.monotonic() > deadline:
            raise smoke.SmokeError("FM9: the released rockets did not land")
        time.sleep(1)


def run(args) -> dict:
    session, evidence = args.session_dir.resolve(), args.evidence_dir.resolve()
    intended = Path("D:/GitHub/ARCE-Task-Evidence/Temp").resolve()
    if session.parent != intended or session.exists() or smoke.is_link_or_junction(session):
        raise smoke.SmokeError("Unsafe or occupied session directory")
    if evidence.exists() or evidence == session or session in evidence.parents:
        raise smoke.SmokeError("Evidence must be new and outside the session")
    for drive in (Path("C:/"), intended):
        if shutil.disk_usage(drive).free < 10 * 1024**3:
            raise smoke.SmokeError("Native cuts require 10 GiB on both drives")
    libraries = args.source_server.resolve() / "libraries"
    if not (libraries / "net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt").is_file():
        raise smoke.SmokeError("Missing pinned Forge libraries")
    session.mkdir()
    evidence.mkdir(parents=True)
    token = uuid.uuid4().hex
    marker = session / ".arce-w44l-latch-owned"
    marker.write_text(token, encoding="ascii")
    report = dict(status="IN_PROGRESS", source_commit=args.source_commit, jar_sha256=smoke.digest_file(args.jar),
                  award_oracle="N", connected_players=0, cases=[])
    process = None
    try:
        shutil.copytree(libraries, session / "libraries")
        (session / "mods").mkdir()
        shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = smoke.allocate_port()
        smoke.write_server_configuration(session, port, True)
        for case in args.case or ["FM9", "FP5"]:
            world = session / ("world-" + case)
            if case == "FP5":
                (world / "data").mkdir(parents=True)
                for path in args.fixtures.resolve().joinpath(case).glob("*.dat"):
                    shutil.copy2(path, world / "data" / path.name)
                if not (world / "data" / moon.JOURNAL).is_file():
                    raise smoke.SmokeError("Missing production-codec FP5 fixture")
            properties = smoke.expected_server_properties(port, True)
            properties.update({"level-name": world.name, "level-type": "minecraft:flat", "max-tick-time": "60000",
                               "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}],
                                                                 "biome": "minecraft:plains"})})
            (session / "server.properties").write_text("".join(f"{key}={value}\n" for key, value in sorted(properties.items())),
                                                       encoding="ascii")
            event, authorities, entities = None, None, []
            row = dict(case=case, starts=[])
            report["in_progress"] = row
            for start in range(3):
                entry = dict(start=start, log=f"{case}-start-{start}.log")
                process = smoke.CapturedProcess(moon.command(args.java, case if start == 0 else None), session,
                                                evidence / entry["log"])
                process.wait_for(smoke.READY_MARKER, args.startup_timeout)
                if start == 0:
                    if case == "FM9":
                        process.command(f"execute in {moon.MOON} run forceload add 320 320 352 320")
                        time.sleep(2)
                        process.command("arce progression release-test moon-two")
                        process.wait_for(re.compile(r"first_event_fixture descent index=1"), 20)
                        entities = [match.group(2) for match in map(DESCENT.search, process.lines) if match]
                        if len(entities) != 2:
                            raise smoke.SmokeError("FM9: did not publish two fixture entities")
                    index = process.wait_for(WAIT, 25)
                    if WAIT.search(process.lines[index]).group(1) != case:
                        raise smoke.SmokeError("The wrong latch was delayed")
                    entry["blocked_status"], values = status(process)
                    if values[0] != "SOURCE_UNCONFIRMED:UNCONFIRMED" or values[3] != "SOURCE_UNCONFIRMED":
                        raise smoke.SmokeError(f"{case}: blocked source is not SOURCE_UNCONFIRMED: {values}")
                    if case == "FM9":
                        entry["blocked_rockets"] = [moon.FlightHarness.report(process, moon.MOON, entity) for entity in entities]
                        time.sleep(3)
                        entry["still_blocked_rockets"] = [moon.FlightHarness.report(process, moon.MOON, entity) for entity in entities]
                        if any(value["state"] != "DESCENT" for value in entry["blocked_rockets"] + entry["still_blocked_rockets"]):
                            raise smoke.SmokeError("FM9: a rocket landed before first-election confirmation")
                        if any("event=landing_complete" in line for line in process.lines):
                            raise smoke.SmokeError("FM9: a landing effect ran while the writer was blocked")
                    else:
                        initial = moon.read_root(world / "data" / moon.JOURNAL)
                        event = initial[moon.OUTBOX]["event"]
                        if initial[moon.OUTBOX]["state"] != "ELECTED" or initial["transfers"]:
                            raise smoke.SmokeError("FP5: initial source is not ELECTED with zero records")
                        if values[2] != "CONFIRMED":
                            raise smoke.SmokeError("FP5: loaded R(E) was not independently confirmed")
                        entry["blocked_roots"] = moon.snapshot(world, evidence, case + "-blocked", start)
                    process.command("arce progression release-test release")
                entry["delivered_status"] = moon.delivered(case, process, None if event is None else
                                                          moon.ints_uuid(event["event_id"]), args.delivery_timeout)
                if case == "FM9":
                    entry["rockets"] = wait_landed(process, entities, args.delivery_timeout)
                if sum(moon.ELECTED in line for line in process.lines) != (1 if case == "FM9" and start == 0 else 0):
                    raise smoke.SmokeError(f"{case}: unexpected number of elections")
                begin = len(process.lines)
                process.command("save-all flush")
                process.wait_for(smoke.SAVE_MARKER, 60, start_at=begin)
                if start == 0:
                    process.command("arce progression release-test halt")
                    code = process.finish(timeout=60)
                    marks = {tuple(mark.groups()) for mark in map(moon.HALT.search, process.lines) if mark}
                    if (code != 75 or len(marks) != 1 or next(iter(marks))[:2] != (case, "fixture_stop")
                            or next(iter(marks))[3].strip() != "Server thread"):
                        raise smoke.SmokeError(f"{case}: did not halt at the final S cut")
                    entry["halt"] = next(iter(marks))
                else:
                    process.command("stop")
                    code = process.finish(timeout=60)
                    if code != 0:
                        raise smoke.SmokeError(f"{case}: restart exited {code}")
                entry["exit_code"] = code
                entry["disk"] = inspect(world, case, event)
                event = entry["disk"]["event"]
                if authorities is not None and entry["disk"]["transfers"] != authorities:
                    raise smoke.SmokeError(f"{case}: subsequent restart changed transfer authority")
                authorities = entry["disk"]["transfers"]
                entry["roots"] = moon.snapshot(world, evidence, case, start)
                errors = [line for line in process.lines if "/ERROR]" in line or "/FATAL]" in line]
                if errors:
                    raise smoke.SmokeError(f"{case}: unexpected error: {errors[0].rstrip()}")
                process = None
                row["starts"].append(entry)
            row["status"] = "PASS"
            report["cases"].append(row)
            print(f"{case}: PASS (online latch release and two restarts)", flush=True)
        report.pop("in_progress", None)
        report["status"] = "PASS"
        return report
    except Exception as failure:
        report.update(status="FAILED", error=str(failure))
        if process is not None:
            process.abort()
            process = None
        if "entry" in locals():
            report["failed_start"] = entry
            try:
                if "roots" not in entry:
                    entry["roots"] = moon.snapshot(world, evidence, case, start)
            except Exception as snapshot_failure:
                report["snapshot_error"] = str(snapshot_failure)
        raise
    finally:
        if process is not None:
            process.abort()
        (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        if session.parent == intended and not smoke.is_link_or_junction(session) and marker.read_text(encoding="ascii") == token:
            shutil.rmtree(session)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_server", type=Path)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--session-dir", type=Path, required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--fixtures", type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--case", action="append", choices=["FM9", "FP5"])
    parser.add_argument("--startup-timeout", type=float, default=240)
    parser.add_argument("--delivery-timeout", type=float, default=120)
    args = parser.parse_args()
    try:
        run(args)
        return 0
    except (OSError, smoke.SmokeError) as failure:
        print(f"FAIL: {failure}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
