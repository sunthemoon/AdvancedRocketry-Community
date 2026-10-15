#!/usr/bin/env python3
"""Complete two-station native first-warp H worlds, cut plus two restarts, no connected players or award claim."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import time
import uuid
if __package__:
    from . import run_v180_first_event_moon_cuts as moon
else:
    import run_v180_first_event_moon_cuts as moon
smoke = moon.smoke
STATIONS = "advancedrocketrycommunity_stations.dat"
OUTBOX = "classic_first_warp"
STATUS = re.compile(r"kind=first_station_warp source=(\S+) event=(\S+) receipt=(\S+) hold=(\S+)")
STATION = re.compile(r"first_event_fixture station index=(\d) id=([0-9a-f-]{36}) orbit=(\S+) energy=(\d+)")
CASES = {
    "FW1": ("before_station_move", "EMPTY", False),
    "FW2": ("after_station_move", "ELECTED", False),
    "FW3": ("receipt_confirmed", "ELECTED", True),
    "FW4a": ("awards_dispatched", "ELECTED", True),
    "FW4b": ("awards_dispatched", "ELECTED", True),
    "FW5": ("delivered_published", "DELIVERED", True),
    "FW6a": ("relocation_unknown", "EMPTY", False),
    "FW6b": ("relocation_unknown", "ELECTED", False),
    "FW7": ("acknowledgement_unknown", "ELECTED", True),
    "FW8b": ("warp_refused", "EMPTY", False),
    "FW9": ("fixture_stop", "DELIVERED", True),
    "FW11": ("delivered_published", "DELIVERED", True),
}


def delivered(process, expected: str | None, timeout: float, empty=False) -> str:
    deadline = time.monotonic() + timeout
    while True:
        begin = len(process.lines)
        process.command("arce progression status")
        index = process.wait_for(STATUS, 15, start_at=begin)
        source, event, receipt, hold = STATUS.search(process.lines[index]).groups()
        if empty and (source, event, receipt, hold) == ("KNOWN:EMPTY", "none", "ABSENT", "none"):
            return process.lines[index].rstrip()
        if not empty and (source, receipt, hold) == ("KNOWN:DELIVERED", "CONFIRMED", "none"):
            if expected is not None and event != expected:
                raise smoke.SmokeError("The recovered station event differs from E")
            return process.lines[index].rstrip()
        if hold not in ("none", "SOURCE_UNCONFIRMED") or time.monotonic() > deadline:
            raise smoke.SmokeError("Station delivery did not settle: " + process.lines[index].rstrip())
        time.sleep(1)


def inspect(world: Path, case: str, state: str, receipt: bool, event: dict | None, baseline: dict) -> dict:
    root = moon.read_root(world / "data" / STATIONS)
    outbox = root[OUTBOX]
    actual = outbox.get("event")
    if root["orbital_clock"]["logical_tick"] < baseline["orbital_clock"]["logical_tick"]:
        raise smoke.SmokeError(f"{case}: orbital clock moved backwards")
    if outbox["state"] != state or event is not None and actual != event:
        raise smoke.SmokeError(f"{case}: station source or exact event changed")
    if state == "EMPTY":
        if outbox != {"schema_version": 1, "state": "EMPTY"}:
            raise smoke.SmokeError(f"{case}: retained an uncommitted candidate")
        for key in ("stations", "reservations", "warp_energy"):
            if root[key] != baseline[key]:
                raise smoke.SmokeError(f"{case}: refused warp changed {key}")
    else:
        if actual["participants"] != moon.uuid_ints(moon.P1) + moon.uuid_ints(moon.P2):
            raise smoke.SmokeError(f"{case}: participants are not P1/P2")
        ident = uuid.UUID(moon.ints_uuid(actual["event_id"]))
        fingerprint = hashlib.sha256(b"first_station_warp\0" + ident.bytes + (2).to_bytes(4, "big")
                                     + moon.P1.bytes + moon.P2.bytes).hexdigest()
        if actual["fingerprint"] != fingerprint:
            raise smoke.SmokeError(f"{case}: invalid fingerprint")
        winner = moon.ints_uuid(actual["station_id"])
        expected_stations = {moon.ints_uuid(entry["station_id"]): entry for entry in baseline["stations"]}
        observed_stations = {moon.ints_uuid(entry["station_id"]): entry for entry in root["stations"]}
        if case == "FW11":
            expected_stations.pop(winner)
        if observed_stations.keys() != expected_stations.keys() or root["reservations"] != baseline["reservations"]:
            raise smoke.SmokeError(f"{case}: station identities/reservations changed unexpectedly")
        for station, before in expected_stations.items():
            after = observed_stations[station]
            # Relocation changes orbit only; controls and region metadata are retained.
            changed = dict(before)
            if station == winner or case == "FW9": changed["orbit_body"] = "advancedrocketrycommunity:moon"
            if changed != after:
                raise smoke.SmokeError(f"{case}: unexpected station mutation for {station}")
        energy = {moon.ints_uuid(entry["station_id"]): entry["energy"] for entry in root["warp_energy"]}
        before_energy = {moon.ints_uuid(entry["station_id"]): entry["energy"] for entry in baseline["warp_energy"]}
        if case == "FW11": before_energy.pop(winner)
        for station in before_energy:
            if station == winner or case == "FW9": before_energy[station] -= actual["cost"]
        if energy != before_energy:
            raise smoke.SmokeError(f"{case}: orbit and FE did not co-commit exactly")
    progression = world / "data" / moon.PROGRESSION
    got = moon.read_root(progression)[moon.WARP_RECEIPT] if progression.exists() else {"occurred": 0}
    if (receipt and (got.get("occurred") != 1 or got.get("event") != actual or got.get("source") != "stations")
            or not receipt and got != {"occurred": 0}):
        raise smoke.SmokeError(f"{case}: durable receipt differs from the cut oracle")
    if progression.exists() and moon.read_root(progression)[moon.MOON_RECEIPT] != {"occurred": 0}:
        raise smoke.SmokeError(f"{case}: Moon receipt changed")
    return dict(event=actual, stations=root["stations"], reservations=root["reservations"], energy=root["warp_energy"],
                clock=root["orbital_clock"], outbox=state, receipt="R(E)" if receipt else "absent")


def run(args):
    session, evidence = args.session_dir.resolve(), args.evidence_dir.resolve()
    intended = Path("D:/GitHub/ARCE-Task-Evidence/Temp").resolve()
    if session.parent != intended or session.exists() or smoke.is_link_or_junction(session):
        raise smoke.SmokeError("Unsafe or occupied session")
    if evidence.exists() or evidence == session or session in evidence.parents:
        raise smoke.SmokeError("Evidence must be new and outside session")
    for drive in (Path("C:/"), intended):
        if shutil.disk_usage(drive).free < 10 * 1024**3: raise smoke.SmokeError("Need 10 GiB on both drives")
    libraries = args.source_server.resolve() / "libraries"
    if not (libraries / "net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt").is_file():
        raise smoke.SmokeError("Missing pinned Forge libraries")
    session.mkdir(); evidence.mkdir(parents=True)
    token = uuid.uuid4().hex
    marker = session / ".arce-w44l-station-owned"
    marker.write_text(token, encoding="ascii")
    report = dict(status="IN_PROGRESS", source_commit=args.source_commit, jar_sha256=smoke.digest_file(args.jar),
                  award_oracle="N", connected_players=0, cases=[])
    process = None
    try:
        shutil.copytree(libraries, session / "libraries")
        (session / "mods").mkdir(); shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = smoke.allocate_port(); smoke.write_server_configuration(session, port, True)
        for case in args.case or list(CASES):
            world = session / ("world-" + case)
            properties = smoke.expected_server_properties(port, True)
            properties.update({"level-name": world.name, "level-type": "minecraft:flat", "max-tick-time": "60000",
                               "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}], "biome": "minecraft:plains"})})
            (session / "server.properties").write_text("".join(f"{k}={v}\n" for k,v in sorted(properties.items())), encoding="ascii")
            row = dict(case=case, starts=[]); report["in_progress"] = row
            event = baseline = None
            for start in range(3):
                entry = dict(start=start, log=f"{case}-start-{start}.log")
                process = smoke.CapturedProcess(moon.command(args.java, case if start == 0 else None), session, evidence / entry["log"])
                process.wait_for(smoke.READY_MARKER, args.startup_timeout)
                if start == 0:
                    process.command("arce progression release-test stations")
                    process.wait_for(re.compile("first_event_fixture station index=1"), 60)
                    station_ids = [match.group(2) for match in map(STATION.search, process.lines) if match]
                    if len(station_ids) != 2: raise smoke.SmokeError("Fixture did not create exactly two stations")
                    begin = len(process.lines); process.command("save-all flush")
                    process.wait_for(smoke.SAVE_MARKER, 60, start_at=begin)
                    baseline = moon.read_root(world / "data" / STATIONS)
                    entry["baseline_roots"] = moon.snapshot(world, evidence, case + "-baseline", start)
                    process.command("arce progression release-test warp " + station_ids[0])
                    if case == "FW9":
                        entry["first_status"] = delivered(process, None, args.delivery_timeout)
                        process.command("arce progression release-test warp " + station_ids[1])
                        process.wait_for(re.compile(r"first_event_fixture warp_result station=" + station_ids[1] + " code=WARP_COMMITTED"), 30)
                        begin = len(process.lines); process.command("save-all flush")
                        process.wait_for(smoke.SAVE_MARKER, 60, start_at=begin)
                        process.command("arce progression release-test halt")
                    code = process.finish(timeout=args.delivery_timeout)
                    marks = {tuple(mark.groups()) for mark in map(moon.HALT.search, process.lines) if mark}
                    if (code != 75 or len(marks) != 1 or next(iter(marks))[:2] != (case, CASES[case][0])
                            or next(iter(marks))[3].strip() != "Server thread"):
                        raise smoke.SmokeError(f"{case}: unexpected cut boundary: {code}, {marks}")
                    entry["halt"] = next(iter(marks))
                    state, receipt = CASES[case][1:]
                else:
                    empty = CASES[case][1] == "EMPTY"
                    entry["status"] = delivered(process, None if event is None else moon.ints_uuid(event["event_id"]), args.delivery_timeout, empty)
                    begin = len(process.lines); process.command("save-all flush")
                    process.wait_for(smoke.SAVE_MARKER, 60, start_at=begin)
                    process.command("stop"); code = process.finish(timeout=60)
                    if code != 0: raise smoke.SmokeError(f"{case}: restart did not stop cleanly")
                    state, receipt = ("EMPTY", False) if empty else ("DELIVERED", True)
                entry["exit_code"] = code
                entry["disk"] = inspect(world, case, state, receipt, event, baseline)
                event = entry["disk"]["event"]
                entry["roots"] = moon.snapshot(world, evidence, case, start)
                if start and any("ARCE_FIRST_EVENT_ELECTED kind=first_station_warp" in line for line in process.lines):
                    raise smoke.SmokeError(f"{case}: restarted world elected a second winner")
                allowed = ("ARCE_STATION_UPDATE_OUTCOME_UNKNOWN",) if case in ("FW6a", "FW6b", "FW7") else ()
                errors = [line.rstrip() for line in process.lines if "/ERROR]" in line or "/FATAL]" in line]
                if any(not any(marker in line for marker in allowed) for line in errors):
                    raise smoke.SmokeError(f"{case}: unexpected ERROR: {errors}")
                entry["errors"] = errors
                process = None; row["starts"].append(entry)
            row["status"] = "PASS"; report["cases"].append(row)
            print(f"{case}: PASS (complete station world, two restarts)", flush=True)
        report.pop("in_progress", None); report["status"] = "PASS"
        return report
    except Exception as failure:
        report.update(status="FAILED", error=str(failure))
        if process is not None: process.abort(); process = None
        if "entry" in locals():
            report["failed_start"] = entry
            try:
                if "roots" not in entry: entry["roots"] = moon.snapshot(world, evidence, case, start)
            except Exception as snapshot_failure: report["snapshot_error"] = str(snapshot_failure)
        raise
    finally:
        if process is not None: process.abort()
        (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        if session.parent == intended and not smoke.is_link_or_junction(session) and marker.read_text(encoding="ascii") == token:
            shutil.rmtree(session)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_server", type=Path)
    for name in ("jar", "session-dir", "evidence-dir"): parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--source-commit", required=True); parser.add_argument("--java", required=True)
    parser.add_argument("--case", action="append", choices=list(CASES))
    parser.add_argument("--startup-timeout", type=float, default=240)
    parser.add_argument("--delivery-timeout", type=float, default=120)
    args = parser.parse_args()
    try: run(args); return 0
    except (OSError, smoke.SmokeError) as failure: print(f"FAIL: {failure}"); return 1


if __name__ == "__main__": raise SystemExit(main())
