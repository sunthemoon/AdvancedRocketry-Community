#!/usr/bin/env python3
"""W196 B10 release cuts: REPAIR and blocked INTENT, STAGED/MOVED flags, then two native restarts.

Every case uses four counted starts: a clean INTENT seed, the forced release cut, and two restarts.
The existing bay harness supplies the bounded process, fixture and status machinery. Its older hold assertions
are unchanged. Only the gated fixture loads the existing recorded ends before the first released flag.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import shutil
import time
import uuid

import run_v180_bay_forced_stop as bay

CASES = [f"B10_{kind}_{cut}" for kind in ("BLOCKED", "REPAIR") for cut in ("STAGED", "MOVED")]
FLAGS = re.compile(r"ARCE_RELEASE_BAY_FLAGS logical=(\S+) rows=\[([^\]]*)\]")
ENDS = re.compile(r"ARCE_RELEASE_BAY_ENDS logical=(\S+) loaded=(true|false)")


def flags(run, logical):
    match = FLAGS.search(run.send(f"arce bay release-test released-status {logical}", FLAGS))
    rows = [row.split(":") for row in match[2].split(",") if row]
    if match[1] != logical or any(len(row) != 4 for row in rows):
        raise bay.smoke.SmokeError(f"Malformed released status: {match[0]}")
    return rows


def ready(run, logical):
    deadline = time.monotonic() + run.args.settle_timeout
    while True:
        line = run.send(f"arce bay release-test released-ends {logical}", ENDS)
        if ENDS.search(line)[2] == "true":
            return
        if time.monotonic() > deadline:
            raise bay.smoke.SmokeError("Recorded ends never became ready")
        time.sleep(1)


def check_landed(status, fixture, phase, op):
    if (status["state"] != "LANDED" or status["transfer"] != "COMMITTED" or status["locked"] != "true"
            or status["packages"] != fixture["packages"] or status["consumed"] != [None, None]
            or status["owned"] != 0 or status["rocket"] == fixture["rocket"]):
        raise bay.smoke.SmokeError(f"Released landing invariants failed: {status}")
    rows = status["receipts"]
    if (len(rows) != 1 or rows[0]["op"] != op or rows[0]["phase"] != phase
            or rows[0]["reason"] != ("REGISTRY_SIGNATURE_CONFLICT" if phase == "REPAIR" else "NONE")
            or rows[0]["satellite"] != fixture["data"] or not rows[0]["acknowledged"] or rows[0]["registered"]):
        raise bay.smoke.SmokeError(f"Released receipt changed or registered: {rows}")


def stop(run, process):
    code = run.end("stop", run.args.exit_timeout)
    if code != 0 or any("/ERROR]" in line or "/FATAL]" in line for line in process.lines):
        raise bay.smoke.SmokeError(f"Native start exited {code} or logged ERROR/FATAL")
    return code


def run_case(run, case):
    world = run.session / ("world-" + case)
    (run.session / "server.properties").write_text(
        f"online-mode=false\nserver-port={run.port}\nlevel-name={world.name}\nlevel-type=minecraft:flat\n"
        "generate-structures=false\nspawn-protection=0\nview-distance=3\nsimulation-distance=3\n"
        "enable-rcon=false\nmax-tick-time=60000\n", encoding="utf-8")
    fixture, seed = bay.first_start(run, "B18_INTENT", world)
    phase = "REPAIR" if "REPAIR" in case else "INTENT"
    cut = "B10_RELEASE_" + case.rsplit("_", 1)[1]
    process = run.start(case, 0, dict(bayRegistryBlocked="true", bayCheckpoint=cut))
    before = run.status(fixture["logical"])
    op = before["receipts"][0]["op"]
    if phase == "REPAIR":
        run.send(f"arce bay release-test seed-repair {fixture['logical']}",
                 re.compile(r"ARCE_RELEASE_BAY_REPAIR logical=\S+ outcome=COMMITTED"))
    ready(run, fixture["logical"])
    run.wait(re.compile(rf"ARCE_RELEASE_BAY_PAUSED cut={cut} op={op}"), run.args.flight_timeout)
    at_cut = run.status(fixture["logical"])
    cut_flags = flags(run, fixture["logical"])
    if (at_cut["transfer"] != "PREPARED" or at_cut["state"] != "TRANSIT" or at_cut["locked"] != "true"
            or at_cut["packages"] != fixture["packages"] or at_cut["owned"] != 0
            or cut_flags != [[op, phase, "true", "false"]]):
        raise bay.smoke.SmokeError(f"Delegation before release acknowledgement: {at_cut} {cut_flags}")
    run.end("kill", run.args.exit_timeout)
    result = dict(case=case, fixture=fixture, seed=seed, at_cut=at_cut, cut_flags=cut_flags, restarts=[])
    for restart in (1, 2):
        # STAGED has not made released durable: the blocked view remains until the release can be acknowledged.
        process = run.start(case, restart, dict(bayRegistryBlocked="true"))
        ready(run, fixture["logical"])
        deadline = time.monotonic() + run.args.settle_timeout
        while True:
            status = run.status(fixture["logical"])
            if status["state"] == "LANDED":
                break
            if time.monotonic() > deadline:
                raise bay.smoke.SmokeError(f"Released flight never landed: {status}")
            time.sleep(1)
        check_landed(status, fixture, phase, op)
        if result["restarts"] and status["rocket"] != result["restarts"][0]["status"]["rocket"]:
            raise bay.smoke.SmokeError("Restart rebound the retained destination UUID")
        observed = flags(run, fixture["logical"])
        pending = run.pending()
        if observed != [[op, phase, "true", "true"]] or pending != dict(pending=1, retained=1, work=False):
            raise bay.smoke.SmokeError(f"Released authority or pending charge changed: {observed} {pending}")
        result["restarts"].append(dict(restart=restart, status=status, flags=observed, pending=pending,
                                       exit_code=stop(run, process), receipts_sha256=bay.digest(world / "data" / bay.RECEIPTS)))
    result["status"] = "PASS"
    return result


def run(args):
    bay.assert_pin(args.source_commit)
    session, evidence = args.session_dir.resolve(), args.evidence_dir.resolve()
    intended = Path("D:/GitHub/ARCE-Task-Evidence/Temp").resolve()
    if session.parent != intended or session.exists() or bay.smoke.is_link_or_junction(session):
        raise bay.smoke.SmokeError("Unsafe or occupied disposable native directory")
    if evidence.exists() or evidence == session or session in evidence.parents:
        raise bay.smoke.SmokeError("Evidence must be new and outside the disposable directory")
    libraries = args.source_server.resolve() / "libraries"
    if not libraries.is_dir() or any(bay.smoke.is_link_or_junction(p) for p in [libraries, *libraries.rglob("*")]):
        raise bay.smoke.SmokeError("Missing or unsafe Forge libraries")
    cases = args.case or CASES
    if len(cases) * 4 > 60:
        raise bay.smoke.SmokeError("Tranche exceeds 60 counted starts")
    session.mkdir(); evidence.mkdir(parents=True)
    token = uuid.uuid4().hex
    marker = session / ".arce-w196-owned"
    marker.write_text(token, encoding="ascii")
    report = dict(status="IN_PROGRESS", source_commit=args.source_commit, jar_sha256=bay.digest(args.jar),
                  counted_starts_planned=len(cases) * 4, cases=[], lock_owner=args.lock_owner)
    runner = bay.Session(args, session, evidence)
    try:
        shutil.copytree(libraries, session / "libraries")
        (session / "mods").mkdir(); shutil.copy2(args.jar, session / "mods" / args.jar.name)
        runner.port = bay.smoke.allocate_port()
        bay.smoke.write_server_configuration(session, runner.port, True)
        for case in cases:
            try:
                report["cases"].append(run_case(runner, case))
                print(f"{case}: PASS (release cut, two restarts)", flush=True)
            except bay.smoke.SmokeError as failure:
                if runner.process is not None:
                    runner.process.abort(); runner.process = None
                report["cases"].append(dict(case=case, status="FAILED", error=str(failure)))
                print(f"{case}: FAIL {failure}", flush=True)
            # Each case's seed uses the same helper name; preserve all four distinct start logs.
            seed_log = evidence / "B18_INTENT-0.log"
            if seed_log.exists():
                seed_log.rename(evidence / f"{case}-seed.log")
            (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
            shutil.rmtree(session / ("world-" + case), ignore_errors=True)
        bay.assert_pin(args.source_commit)
        report["status"] = "PASS" if all(row["status"] == "PASS" for row in report["cases"]) else "FAILED"
        return report
    finally:
        if runner.process is not None:
            runner.process.abort()
        (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        if session.parent == intended and not bay.smoke.is_link_or_junction(session) and marker.read_text(encoding="ascii") == token:
            shutil.rmtree(session)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_server", type=Path)
    for key in ("jar", "session-dir", "evidence-dir"):
        parser.add_argument("--" + key, type=Path, required=True)
    for key in ("source-commit", "java", "lock-owner"):
        parser.add_argument("--" + key, required=True)
    parser.add_argument("--case", action="append", choices=CASES)
    for key, default in (("startup-timeout", 240), ("exit-timeout", 180), ("flight-timeout", 120), ("settle-timeout", 180)):
        parser.add_argument("--" + key, type=float, default=default)
    args = parser.parse_args()
    try:
        return 0 if run(args)["status"] == "PASS" else 1
    except (OSError, bay.smoke.SmokeError) as failure:
        print(f"FAIL: {failure}", flush=True)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
