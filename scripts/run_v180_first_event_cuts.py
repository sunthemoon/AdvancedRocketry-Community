#!/usr/bin/env python3
"""W44 native pre-start (V) rows: world-first migrations and refusals, an initial start plus two restarts each.

The fixtures are the exact production encodings written by FirstEventNativeFixturesTest into
build/w44i-native-fixtures/<ID>/. Every case gets its own world in one disposable server session. A MIGRATE row
must report one migrated root with a byte-exact backup, a current root 5 or root 3 with an EMPTY outbox and its
other fields kept, and two subsequent restarts that migrate nothing. A REFUSE row must be refused before world startup
at all three starts, with the raw file byte-identical and no backup. Server copies are deleted afterwards.
"""
from __future__ import annotations

import argparse
import gzip
import json
from pathlib import Path
import re
import shutil
import uuid

if __package__:
    from . import run_dedicated_server_smoke as smoke
    from .inspect_celestial_saved_data import NbtReader
    from .run_v060_flight_server_smoke import _server_command
else:
    import run_dedicated_server_smoke as smoke
    from inspect_celestial_saved_data import NbtReader
    from run_v060_flight_server_smoke import _server_command

STATIONS = "advancedrocketrycommunity_stations.dat"
JOURNAL = "advancedrocketrycommunity_rocket_transfers.dat"
PROGRESSION = "advancedrocketrycommunity_classic_progression.dat"
NATIVE_TEMP = Path("D:/GitHub/ARCE-Task-Evidence/Temp")
START_PLAN = ("initial", "restart-1", "restart-2")
# Case: (root file, expected outcome, expected current schema, expected current epoch, outbox key).
CASES = {
    "FM11a": (JOURNAL, "MIGRATE", 3, "v1.8.0-first-moon-landing", "classic_first_moon_landing"),
    "FM11b": (JOURNAL, "MIGRATE", 3, "v1.8.0-first-moon-landing", "classic_first_moon_landing"),
    "FW10a": (STATIONS, "MIGRATE", 5, "v1.8.0-station-first-warp", "classic_first_warp"),
    "FW10b": (STATIONS, "MIGRATE", 5, "v1.8.0-station-first-warp", "classic_first_warp"),
    "FW10c": (STATIONS, "REFUSE", None, None, None),
    "FW10d": (STATIONS, "REFUSE", None, None, None),
    "FW10e": (STATIONS, "REFUSE", None, None, None),
    "FW10f": (STATIONS, "REFUSE", None, None, None),
    "FP1a": (PROGRESSION, "REFUSE", None, None, None),
    "FP1b": (PROGRESSION, "REFUSE", None, None, None),
}
MIGRATED = re.compile(r"\[ARCE-BETA-1002\] Beta world data check complete: managed=(\d+), migrated=1, backup=(\S+)")
CURRENT = re.compile(r"\[ARCE-BETA-1001\] Beta world data check complete: managed=(\d+), migrated=0, backup=none")
BLOCKED = re.compile(r"\[(ARCE-BETA-\d+)\] Beta world data check blocked startup")


def read_root(path: Path) -> dict:
    with gzip.open(path, "rb") as stream:
        raw = stream.read(8 * 1024 * 1024)
    return NbtReader(raw).read_root()["data"]


def check_migrated(case: str, before: dict, after: dict) -> None:
    _, _, schema, epoch, outbox = CASES[case]
    if after.get("schema_version") != schema or after.get("format_epoch") != epoch:
        raise smoke.SmokeError(f"{case}: migrated root is not current: {after.get('schema_version')} {after.get('format_epoch')}")
    if after.get(outbox) != {"schema_version": 1, "state": "EMPTY"}:
        raise smoke.SmokeError(f"{case}: the migrated outbox is not EMPTY: {after.get(outbox)}")
    for key in ("transfers", "stations", "reservations", "warp_energy"):
        if key in before and key in after and key != "stations" and before[key] != after[key]:
            raise smoke.SmokeError(f"{case}: migration changed {key}")
    if case == "FM11b" and [entry["phase"] for entry in after["transfers"]] != ["COMMITTED"]:
        raise smoke.SmokeError("FM11b: the COMMITTED reservation was not preserved")
    if case == "FW10b" and after["orbital_clock"] != before["orbital_clock"]:
        raise smoke.SmokeError("FW10b: the pre-release clock changed")


def run(args) -> dict:
    session, evidence = args.session_dir.resolve(), args.evidence_dir.resolve()
    intended = NATIVE_TEMP.resolve()
    if session.parent != intended or session.exists() or smoke.is_link_or_junction(session):
        raise smoke.SmokeError("Unsafe or occupied disposable native directory")
    if evidence.exists() or evidence == session or session in evidence.parents:
        raise smoke.SmokeError("Evidence must be new and outside the disposable directory")
    for drive in (Path("C:/"), intended):
        if shutil.disk_usage(drive).free < 10 * 1024**3:
            raise smoke.SmokeError("Native cuts require 10 GiB on both drives")
    libraries = args.source_server.resolve() / "libraries"
    if not (libraries / "net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt").is_file():
        raise smoke.SmokeError("Missing pinned Forge arguments")
    fixtures = args.fixtures.resolve()
    session.mkdir()
    evidence.mkdir(parents=True)
    token = uuid.uuid4().hex
    marker = session / ".arce-w44i-cuts-owned"
    marker.write_text(token, encoding="ascii")
    report = dict(status="IN_PROGRESS", source_commit=args.source_commit, jar_sha256=smoke.digest_file(args.jar),
                  scope="W44 V rows: pre-start migration or refusal, initial start plus two restarts, no in-game seam", cases=[])
    process = None
    try:
        shutil.copytree(libraries, session / "libraries")
        (session / "mods").mkdir()
        shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = smoke.allocate_port()
        smoke.write_server_configuration(session, port, True)
        for case in args.case or list(CASES):
            root_file, outcome, *_ = CASES[case]
            source = fixtures / case / root_file
            if not source.is_file():
                raise smoke.SmokeError(f"{case}: missing fixture {source}")
            world = session / ("world-" + case)
            (world / "data").mkdir(parents=True)
            target = world / "data" / root_file
            shutil.copy2(source, target)
            original = smoke.digest_file(target)
            # A refused root is compared by its hash only; FW10c's near-4-MiB root exceeds the inspector's array bound.
            before = read_root(target) if outcome == "MIGRATE" else None
            properties = smoke.expected_server_properties(port, True)
            properties.update({"level-name": world.name, "level-type": "minecraft:flat", "max-tick-time": "60000"})
            (session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(properties.items())),
                                                       encoding="ascii")
            row = dict(case=case, outcome=outcome, fixture_sha256=original, starts=[])
            report["in_progress"] = row
            for start, role in enumerate(START_PLAN):
                command = _server_command(args.java)
                log = evidence / f"{case}-start-{start}.log"
                process = smoke.CapturedProcess(command, session, log)
                entry = dict(start=start, role=role, log=log.name)
                if outcome == "MIGRATE":
                    if start == 0:
                        index = process.wait_for(MIGRATED, args.startup_timeout)
                        backup = MIGRATED.search(process.lines[index]).group(2)
                        entry["backup"] = backup
                        saved = world / "advancedrocketrycommunity-backups" / backup / root_file
                        if smoke.digest_file(saved) != original:
                            raise smoke.SmokeError(f"{case}: the migration backup is not byte-exact")
                    else:
                        process.wait_for(CURRENT, args.startup_timeout)
                    process.wait_for(smoke.READY_MARKER, args.startup_timeout)
                    process.command("stop")
                    entry["exit_code"] = process.finish()
                    if entry["exit_code"] != 0:
                        raise smoke.SmokeError(f"{case}: start {start} exited {entry['exit_code']}")
                    after = read_root(target)
                    check_migrated(case, before, after)
                    entry["root_sha256"] = smoke.digest_file(target)
                    if start > 0 and len(list((world / "advancedrocketrycommunity-backups").iterdir())) != 1:
                        raise smoke.SmokeError(f"{case}: restart {start} migrated again")
                else:
                    index = process.wait_for(BLOCKED, args.startup_timeout)
                    entry["diagnostic"] = BLOCKED.search(process.lines[index]).group(1)
                    try:
                        entry["exit_code"] = process.finish(timeout=120)
                    except smoke.SmokeError:
                        process.abort()
                        entry["exit_code"] = "terminated"
                    if any("Done (" in line and "For help" in line for line in process.lines):
                        raise smoke.SmokeError(f"{case}: a refused root reached world startup")
                    if smoke.digest_file(target) != original:
                        raise smoke.SmokeError(f"{case}: a refused root was rewritten")
                    if (world / "advancedrocketrycommunity-backups").exists():
                        raise smoke.SmokeError(f"{case}: a refused root was backed up")
                    entry["root_sha256"] = original
                process = None
                row["starts"].append(entry)
            row["status"] = "PASS"
            report["cases"].append(row)
            print(f"{case}: PASS ({outcome}, initial start plus two restarts)", flush=True)
        report.pop("in_progress", None)
        report["status"] = "PASS"
        return report
    except Exception as failure:
        report.update(status="FAILED", error=str(failure))
        raise
    finally:
        if process is not None:
            process.abort()
        (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        if (session.parent == intended and not smoke.is_link_or_junction(session) and marker.is_file()
                and marker.read_text(encoding="ascii") == token):
            shutil.rmtree(session)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_server", type=Path)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--fixtures", type=Path, required=True)
    parser.add_argument("--session-dir", type=Path, required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--case", action="append", choices=list(CASES))
    parser.add_argument("--startup-timeout", type=float, default=240)
    args = parser.parse_args()
    try:
        run(args)
        return 0
    except (OSError, smoke.SmokeError) as failure:
        print(f"FAIL: {failure}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
