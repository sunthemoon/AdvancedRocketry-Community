#!/usr/bin/env python3
"""The eleven remaining paired-authority H rows, from complete native worlds; cut, two restarts and bounded recovery."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import re
import shutil
import time
import uuid
if __package__:
    from . import run_v180_first_event_moon_cuts as moon
    from . import run_v180_first_event_station_cuts as station
    from . import run_v180_first_event_latch_cuts as latch
else:
    import run_v180_first_event_moon_cuts as moon
    import run_v180_first_event_station_cuts as station
    import run_v180_first_event_latch_cuts as latch
smoke = moon.smoke
CASES = ["FP2", "FP3", "FP4", "FP6", "FP7a", "FP7b", "FP8", "FP9", "FP10", "FP11", "FW8a"]
FAULTS = {"FP7a": moon.PROGRESSION, "FP7b": moon.PROGRESSION, "FP9": moon.PROGRESSION,
          "FP10": moon.JOURNAL, "FP11": station.STATIONS}
HOLDS = {"FP2": ("RECEIPT_MISSING", "RECEIPT_MISSING"), "FP3": ("RECEIPT_MISMATCH", "none"),
         "FP4": ("none", "SOURCE_ROLLBACK"), "FP7a": ("ROOT_UNREADABLE", "ROOT_UNREADABLE"),
         "FP7b": ("ROOT_UNREADABLE", "ROOT_UNREADABLE"), "FP9": ("ROOT_UNREADABLE", "ROOT_UNREADABLE"),
         "FP10": ("ROOT_UNREADABLE", "none"), "FP11": ("none", "ROOT_UNREADABLE")}


def roots(world):
    data = world / "data"
    result = {}
    for name in (moon.JOURNAL, station.STATIONS, moon.PROGRESSION):
        path = data / name
        result[name] = moon.read_root(path, max_tags=1_048_576 if name == station.STATIONS else 4096) if path.exists() else None
    return result


def save(process):
    begin = len(process.lines); process.command("save-all flush")
    process.wait_for(smoke.SAVE_MARKER, 60, start_at=begin)


def stop(process):
    process.command("stop")
    if process.finish(timeout=60) != 0: raise smoke.SmokeError("A native restart did not stop cleanly")


def prepared_without_errors(process):
    errors = [line.rstrip() for line in process.lines if "/ERROR]" in line or "/FATAL]" in line]
    if errors: raise smoke.SmokeError(f"Paired seed preparation had unexpected errors: {errors}")


def halt(process, case, boundary):
    code = process.finish(timeout=120)
    marks = {tuple(mark.groups()) for mark in map(moon.HALT.search, process.lines) if mark}
    if (code != 75 or len(marks) != 1 or next(iter(marks))[:2] != (case, boundary)
            or next(iter(marks))[3].strip() != "Server thread"):
        raise smoke.SmokeError(f"{case}: wrong S cut: {code}, {marks}")
    return dict(exit_code=code, marker=next(iter(marks)))


def statuses(process):
    begin = len(process.lines); process.command("arce progression status")
    a = process.wait_for(moon.STATUS, 20, start_at=begin)
    b = process.wait_for(station.STATUS, 20, start_at=begin)
    return [process.lines[a].rstrip(), process.lines[b].rstrip()]


def check(case, world, expected, recovered=False):
    actual = roots(world)
    journal, registry, progression = actual[moon.JOURNAL], actual[station.STATIONS], actual[moon.PROGRESSION]
    old_journal, old_registry, old_progression = expected[moon.JOURNAL], expected[station.STATIONS], expected[moon.PROGRESSION]
    if case in FAULTS and not recovered:
        if actual != expected: raise smoke.SmokeError(f"{case}: an unavailable-world root changed")
        if case in ("FP7a", "FP7b", "FP9") and (world / "data" / (moon.PROGRESSION + ".arce-pending")).exists():
            raise smoke.SmokeError(f"{case}: unavailable progression staged a receipt")
    elif case in ("FP2", "FP3"):
        if actual != expected: raise smoke.SmokeError(f"{case}: held immutable roots changed")
    elif case == "FP4":
        if journal != old_journal or progression != old_progression or registry[station.OUTBOX] != old_registry[station.OUTBOX]:
            raise smoke.SmokeError("FP4: the rollback hold replaced an event/receipt")
        if registry["stations"] != expected["post_warp_stations"] or registry["warp_energy"] != expected["post_warp_energy"]:
            raise smoke.SmokeError("FP4: the NOT_FIRST warp did not preserve its exact ordinary orbit/debit")
    elif case == "FW8a":
        if actual != expected: raise smoke.SmokeError("FW8a: over-budget warp changed a root")
    else:
        if case == "FP8":
            event = registry[station.OUTBOX].get("event")
            if event is None: raise smoke.SmokeError("FP8: committed override lost its candidate")
            winner = event["station_id"]
            relocated = []
            for before in old_registry["stations"]:
                after = dict(before)
                if before["station_id"] == winner: after["orbit_body"] = "advancedrocketrycommunity:moon"
                relocated.append(after)
            debited = []
            for before in old_registry["warp_energy"]:
                after = dict(before)
                if before["station_id"] == winner: after["energy"] -= event["cost"]
                debited.append(after)
            if (registry["stations"] != relocated or registry["warp_energy"] != debited
                    or registry["reservations"] != old_registry["reservations"]):
                raise smoke.SmokeError("FP8: committed override did not preserve the exact relocated candidate")
        for name, root in ((moon.JOURNAL, journal), (station.STATIONS, registry)):
            key = moon.OUTBOX if name == moon.JOURNAL else station.OUTBOX
            prior = expected[name][key]
            if case == "FP7a" and name == moon.JOURNAL:
                if root[key]["state"] != "DELIVERED" or prior["state"] != "EMPTY":
                    raise smoke.SmokeError("FP7a: repaired actual completion did not deliver")
            elif case == "FP8" and name == station.STATIONS:
                if root[key]["state"] not in ("ELECTED", "DELIVERED"):
                    raise smoke.SmokeError("FP8: committed override exposed EMPTY")
            elif root[key].get("event") != prior.get("event") or root[key]["state"] != "DELIVERED":
                raise smoke.SmokeError(f"{case}: recovered source lost its exact event")
        for key, name in ((moon.MOON_RECEIPT, moon.JOURNAL), (moon.WARP_RECEIPT, station.STATIONS)):
            event = actual[name][moon.OUTBOX if name == moon.JOURNAL else station.OUTBOX].get("event")
            if event is not None and (progression[key].get("occurred") != 1 or progression[key].get("event") != event):
                if not (case == "FP8" and name == station.STATIONS and not recovered):
                    raise smoke.SmokeError(f"{case}: recovered receipt differs from source")
    # Keep the full roots as raw files, not repeated multi-megabyte JSON dumps.
    return dict(moon=journal[moon.OUTBOX], warp=registry[station.OUTBOX], transfers=len(journal["transfers"]),
                stations=len(registry["stations"]), reservations=registry["reservations"], clock=registry["orbital_clock"],
                progression_present=progression is not None)


def run(args):
    session, evidence = args.session_dir.resolve(), args.evidence_dir.resolve()
    intended = Path("D:/GitHub/ARCE-Task-Evidence/Temp").resolve()
    if session.parent != intended or session.exists() or smoke.is_link_or_junction(session): raise smoke.SmokeError("Unsafe session")
    if evidence.exists() or evidence == session or session in evidence.parents: raise smoke.SmokeError("Evidence must be new and outside session")
    for drive in (Path("C:/"), intended):
        if shutil.disk_usage(drive).free < 10 * 1024**3: raise smoke.SmokeError("Need 10 GiB on both drives")
    libraries = args.source_server.resolve() / "libraries"
    if not (libraries / "net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt").is_file(): raise smoke.SmokeError("Missing pinned Forge libraries")
    session.mkdir(); evidence.mkdir(parents=True)
    token = uuid.uuid4().hex; marker = session / ".arce-w44l-paired-owned"; marker.write_text(token, encoding="ascii")
    report = dict(status="IN_PROGRESS", source_commit=args.source_commit, jar_sha256=smoke.digest_file(args.jar),
                  award_oracle="N", connected_players=0, preparations=[], cases=[])
    process = None
    try:
        shutil.copytree(libraries, session / "libraries"); (session / "mods").mkdir()
        shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = smoke.allocate_port(); smoke.write_server_configuration(session, port, True)
        harness = moon.FlightHarness(java=args.java, server=session, port=port, expected_version="1.20.1-1.8.0-dev", startup_timeout=args.startup_timeout)

        def start_world(name, cut, log, station_id=None):
            properties = smoke.expected_server_properties(port, True)
            properties.update({"level-name": name, "level-type": "minecraft:flat", "max-tick-time": "60000",
                               "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}], "biome": "minecraft:plains"})})
            (session / "server.properties").write_text("".join(f"{k}={v}\n" for k,v in sorted(properties.items())), encoding="ascii")
            command = moon.command(args.java, cut)
            if station_id: command.insert(1, "-Darce.releaseTest.firstEventStation=" + station_id)
            return smoke.CapturedProcess(command, session, evidence / log)

        def ready(p): p.wait_for(smoke.READY_MARKER, args.startup_timeout)
        def create_stations(p):
            p.command("arce progression release-test stations")
            p.wait_for(re.compile("first_event_fixture station index=1"), 60)
            return [m.group(2) for m in map(station.STATION.search, p.lines) if m]

        # Real source launch plus two actual station platforms; preserve the exact pre-warp registry encoding.
        world = session / "seed-main"; case = "seed-main"; start = 0; entry = dict(log="seed-main-0.log")
        process = start_world(world.name, "FW9", entry["log"]); ready(process)
        entity = harness.assemble(process)["entity"]; harness.refuel(process, moon.EARTH, entity)
        process.command(f"arce rocket release-test launch {entity} moon")
        moon.delivered(case, process, None, args.delivery_timeout); moon.landed(case, process, args.delivery_timeout)
        ids = create_stations(process); save(process)
        shutil.copy2(world / "data" / station.STATIONS, session / "w44l-before-warp.dat")
        stop(process); prepared_without_errors(process); process = None
        entry = dict(log="seed-main-1.log"); process = start_world(world.name, "FW9", entry["log"]); ready(process)
        process.command("arce progression release-test warp " + ids[0]); station.delivered(process, None, args.delivery_timeout)
        process.command(f"execute in {moon.MOON} run forceload add 384 320"); time.sleep(2)
        process.command("arce progression release-test moon-extra")
        process.wait_for(re.compile("first_event_fixture descent index=2"), 20)
        extra = [m.group(2) for m in map(latch.DESCENT.search, process.lines) if m][-1]
        latch.wait_landed(process, [extra], args.delivery_timeout)
        moon.delivered(case, process, None, args.delivery_timeout); save(process)
        process.command("arce progression release-test export-cases")
        process.wait_for(re.compile("first_event_fixture cases_exported count=9"), 90)
        stop(process); prepared_without_errors(process); process = None
        report["preparations"].append(dict(world="seed-main", roots=moon.snapshot(world, evidence, "seed-main", 0), station=ids[0]))

        # Paired WARP receipt with an EMPTY Moon source and actual DESCENT entities, saved before the delayed election.
        world = session / "seed-wait"; case = "seed-wait"; entry = dict(log="seed-wait.log")
        process = start_world(world.name, "FM9", entry["log"]); ready(process)
        wait_ids = create_stations(process)
        process.command("arce progression release-test warp " + wait_ids[0]); station.delivered(process, None, args.delivery_timeout)
        save(process); process.command(f"execute in {moon.MOON} run forceload add 320 320 352 320"); time.sleep(2)
        process.command("arce progression release-test moon-two"); process.wait_for(latch.WAIT, 25)
        process.command("arce progression release-test halt"); halt(process, "FM9", "fixture_stop")
        prepared_without_errors(process); process = None
        report["preparations"].append(dict(world="seed-wait", roots=moon.snapshot(world, evidence, "seed-wait", 0), station=wait_ids[0]))

        for case in args.case or CASES:
            world = session / ("world-" + case); start = 0
            shutil.copytree(session / ("seed-wait" if case == "FP7a" else "seed-main"), world)
            overlay = session / "w44l-case-roots" / case
            if overlay.exists():
                for file in overlay.glob("*.dat"): shutil.copy2(file, world / "data" / file.name)
                if (overlay / "remove-progression.txt").exists(): (world / "data" / moon.PROGRESSION).unlink()
            expected = roots(world)
            if case == "FP4":
                complete = roots(session / "seed-main")
                expected.update(post_warp_stations=complete[station.STATIONS]["stations"], post_warp_energy=complete[station.STATIONS]["warp_energy"])
            pin = wait_ids[0] if case == "FP7a" else ids[0]
            original_fault_bytes = smoke.digest_file(world / "data" / FAULTS[case]) if case in FAULTS else None
            row = dict(case=case, starts=[], fault_file_sha256=original_fault_bytes,
                       fixture_roots=moon.snapshot(world, evidence, case + "-fixture", 0))
            report["in_progress"] = row
            plan = [case, case, case, None, None] if case in FAULTS else [case, None, None]
            first_recovery = None
            initial_cut = None
            for start, cut in enumerate(plan):
                entry = dict(start=start, cut=cut, log=f"{case}-start-{start}.log")
                process = start_world(world.name, cut, entry["log"], pin)
                if cut and case not in ("FP6", "FW8a", "FP8"):
                    entry.update(halt(process, case, "held" if case in ("FP2", "FP3", "FP4") else "first_step"))
                else:
                    ready(process)
                    if start == 0 and case in ("FW8a", "FP8"):
                        process.command("arce progression release-test warp " + pin)
                        entry.update(halt(process, case, "warp_refused" if case == "FW8a" else "relocation_unknown"))
                    else:
                        if case == "FP6" and start == 0:
                            process.command(f"execute in {moon.MOON} run forceload add 416 320"); time.sleep(2)
                            process.command("arce progression release-test moon-extra")
                            process.wait_for(re.compile("ARCE_TRANSFER_JOURNAL_UNACKNOWLEDGED"), 30)
                            process.command("arce progression release-test journal-proof")
                            proof = process.wait_for(re.compile("first_event_fixture journal_proof result=.*outcome=UNKNOWN"), 20)
                            entry["unknown_proof"] = process.lines[proof].rstrip()
                            entry["unconfirmed_status"] = statuses(process)
                            if "SOURCE_UNCONFIRMED" not in entry["unconfirmed_status"][0] or "SOURCE_ROLLBACK" in entry["unconfirmed_status"][0]:
                                raise smoke.SmokeError("FP6: UNKNOWN was not a temporary proof loss")
                        if case in HOLDS and case not in FAULTS:
                            entry["status"] = statuses(process)
                            for line, hold in zip(entry["status"], HOLDS[case]):
                                if "hold=" + hold not in line: raise smoke.SmokeError(f"{case}: wrong hold: {line}")
                        elif case != "FW8a":
                            moon.delivered(case, process, None, args.delivery_timeout)
                            station.delivered(process, None, args.delivery_timeout, empty=False)
                        if case == "FP6" and start == 0:
                            extra = [m.group(2) for m in map(latch.DESCENT.search, process.lines) if m][-1]
                            entry["extra_rocket"] = latch.wait_landed(process, [extra], args.delivery_timeout)
                        save(process)
                        if start == 0 and case == "FP6":
                            process.command("arce progression release-test halt"); entry.update(halt(process, case, "fixture_stop"))
                        else: stop(process); entry["exit_code"] = 0
                if case == "FP7a" and cut and not any("first_event_fixture completion_wait" in line for line in process.lines):
                    raise smoke.SmokeError("FP7a: no actual completion WAIT was observed")
                if case == "FP11" and cut and not any("unavailable creation=REGISTRY_BLOCKED warp=AUTHORITY_UNAVAILABLE" in line for line in process.lines):
                    raise smoke.SmokeError("FP11: the gated normal operations were not refused")
                if case == "FP8" and start == 0 and not any("frozen mutation_refused=true credited=0 altitude=UNAVAILABLE" in line for line in process.lines):
                    raise smoke.SmokeError("FP8: the committed override gates were not exercised")
                if case in FAULTS and cut and smoke.digest_file(world / "data" / FAULTS[case]) != original_fault_bytes:
                    raise smoke.SmokeError(f"{case}: runtime load fault changed raw bytes")
                entry["disk"] = check(case, world, expected, recovered=cut is None and case in FAULTS or case == "FP8" and start > 0)
                now = roots(world)
                if start == 0: initial_cut = now
                if case == "FP6":
                    if len(now[moon.JOURNAL]["transfers"]) != 3 or any(record["phase"] != "COMMITTED" for record in now[moon.JOURNAL]["transfers"]):
                        raise smoke.SmokeError("FP6: unrelated authority was not retained as a third COMMITTED record")
                    if any(record not in now[moon.JOURNAL]["transfers"] for record in expected[moon.JOURNAL]["transfers"]):
                        raise smoke.SmokeError("FP6: unrelated write replaced an earlier reservation")
                    if now[moon.JOURNAL]["transfers"] != initial_cut[moon.JOURNAL]["transfers"]:
                        raise smoke.SmokeError("FP6: a restart changed the unrelated authority")
                if case == "FP8" and now[station.STATIONS][station.OUTBOX]["event"] != initial_cut[station.STATIONS][station.OUTBOX]["event"]:
                    raise smoke.SmokeError("FP8: restart changed the committed override's event")
                if cut is None and case in FAULTS or case == "FP8" and start > 0:
                    if first_recovery is not None and now != first_recovery: raise smoke.SmokeError(f"{case}: the idempotent recovery changed authority")
                    first_recovery = now
                if sum(moon.ELECTED in line for line in process.lines) != (1 if case == "FP7a" and start == 3 else 0):
                    raise smoke.SmokeError(f"{case}: unexpected new Moon election")
                errors = [line.rstrip() for line in process.lines if "/ERROR]" in line or "/FATAL]" in line]
                allowed = (["Error loading saved data: " + FAULTS[case][:-4], "ARCE_MANAGED_ROOT_UNREADABLE type=" + FAULTS[case][:-4]]
                           if cut and case in FAULTS else ["ARCE_STATION_UPDATE_OUTCOME_UNKNOWN"] if case == "FP8" and start == 0
                           else ["ARCE_TRANSFER_JOURNAL_UNACKNOWLEDGED"] if case == "FP6" and start == 0 else [])
                if case == "FP6" and start == 0 and len(errors) != 1:
                    raise smoke.SmokeError("FP6: expected exactly the one injected journal failure")
                if any(not any(marker in line for marker in allowed) for line in errors): raise smoke.SmokeError(f"{case}: unexpected errors: {errors}")
                entry["errors"] = errors; entry["roots"] = moon.snapshot(world, evidence, case, start)
                process = None; row["starts"].append(entry)
            row["status"] = "PASS"; report["cases"].append(row); print(f"{case}: PASS ({len(plan)} starts)", flush=True)
        report.pop("in_progress", None); report["status"] = "PASS"; return report
    except Exception as failure:
        report.update(status="FAILED", error=str(failure))
        if process is not None: process.abort(); process = None
        if "entry" in locals():
            report["failed_start"] = entry
            try: entry["roots"] = moon.snapshot(world, evidence, case, start)
            except Exception as snapshot_failure: report["snapshot_error"] = str(snapshot_failure)
        raise
    finally:
        if process is not None: process.abort()
        (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        if session.parent == intended and not smoke.is_link_or_junction(session) and marker.read_text(encoding="ascii") == token: shutil.rmtree(session)


def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument("source_server", type=Path)
    for name in ("jar", "session-dir", "evidence-dir"): parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--source-commit", required=True); parser.add_argument("--java", required=True)
    parser.add_argument("--case", action="append", choices=CASES)
    parser.add_argument("--startup-timeout", type=float, default=240); parser.add_argument("--delivery-timeout", type=float, default=120)
    args = parser.parse_args()
    try: run(args); return 0
    except (OSError, smoke.SmokeError) as failure: print(f"FAIL: {failure}"); return 1


if __name__ == "__main__": raise SystemExit(main())
