#!/usr/bin/env python3
"""Run pinned cargo and shared-infrastructure families on a supplied candidate, without building a JAR."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
import zipfile

import run_v180_cargo_cuts as fluid
import run_v180_cargo_s4_s5_cuts as settlement
import run_v180_item_cuts as item
import run_v180_cargo_owner_cap as cap
import run_v180_loader_cuts as loader
import run_v180_loader_s6 as loader_s6
import run_v180_fuel_loader_s1 as loader_s1

REPO = Path(__file__).resolve().parents[1]
PRODUCTION_PATHS = ("src/main", "src/generated", "build.gradle", "settings.gradle", "gradle.properties")
RUNNERS = (fluid, settlement, item, cap)
REUSE = {
    "B3-S1-REUSE": (loader, tuple("LF_M_" + name for name in loader.oracle.CREATION if name != "N1") + ("LF_M_N3_ELIGIBILITY",)),
    "B3-S4-REUSE": (loader, loader.oracle.S4),
    "B3-S5-REUSE": (loader, tuple("LF_M_R3_LEDGER_" + name for name in ("MISSING", "REPLACED", "BEHIND")) + loader.oracle.S5_FLOORS),
    "B3-S6-REUSE": (loader_s6, loader_s6.CASES),
}
RECOVERY_UNLOAD = frozenset(("FF_DISASSEMBLY_REMOVAL", "FF_DISASSEMBLY_RESTORATION", "FF_DISASSEMBLY_RELEASE",
                            "FF_REBUILD_DESTINATION", "FF_KEEP_SOURCE", "FF_REBUILD_SOURCE", "FF_STALE_PREPARED", "FF_RECONNECT"))


def inventory():
    rows = []
    for runner in RUNNERS:
        for case, spec in runner.CASES.items():
            probe = spec.get("probe", case)
            unload = ("_UNLOAD_" in probe or case.startswith(("FS_", "IS_", "IR_")) or case in RECOVERY_UNLOAD) and "_LOAD_" not in case
            medium = "ITEM" if runner is item else "FLUID"
            boundary = spec.get("checkpoint", spec.get("boundary", settlement.BOUNDARIES.get(case, case[3:])))
            rows.append(dict(family=spec["family"], case=case, probe=probe, boundary=boundary,
                             kind=spec.get("kind", f"{medium}_CARGO_{'UNLOAD' if unload else 'LOAD'}"),
                             runner="scripts/" + Path(runner.__file__).name))
    for family, (runner, cases) in REUSE.items():
        rows.extend(dict(family=family, case=case, probe=case, boundary=case, kind="SHARED_INFRASTRUCTURE",
                         runner="scripts/" + Path(runner.__file__).name) for case in cases)
    # G2 is not exposed by loader_cuts or loader_s6. Preserve its existing S1 campaign and historical-JAR requirements.
    rows.append(dict(family="G2-REUSE", case="G2_S1_CAMPAIGN", probe="G2_S1_CAMPAIGN", boundary="EXISTING_S1_STAGES",
                     kind="SHARED_INFRASTRUCTURE", runner="scripts/run_v180_fuel_loader_s1.py"))
    return rows


def verify_candidate(jar, source, candidate_commit, candidate_sha256):
    if not re.fullmatch(r"[0-9a-fA-F]{7,40}", candidate_commit) or not re.fullmatch(r"[0-9a-fA-F]{64}", candidate_sha256):
        raise ValueError("Candidate commit and JAR SHA-256 must be hexadecimal pins")
    fluid.native.assert_pin(source)
    resolved = subprocess.run(["git", "rev-parse", "--verify", candidate_commit + "^{commit}"], cwd=REPO,
                              check=True, capture_output=True, text=True).stdout.strip()
    command = ["git", "diff", "--quiet", resolved, "HEAD", "--", *PRODUCTION_PATHS]
    subprocess.run(command, cwd=REPO, check=True)
    actual = fluid.native.digest(jar)
    if actual != candidate_sha256.lower():
        raise ValueError("The supplied JAR does not match the required candidate SHA-256")
    return dict(source_commit=source, candidate_commit=resolved, candidate_jar_sha256=candidate_sha256.lower(), jar_sha256=actual,
                production_diff_command=command,
                production_diff_exit=0)


def run_loader(args, pin):
    """Delegate exact run_cut inputs/oracles; no retry or nested lock, and stop at the first contradiction."""
    runner = REUSE[args.family][0]
    args.session_parent = args.session_dir.resolve()
    if not args.session_parent.is_dir() or args.evidence_dir.exists():
        raise ValueError("Loader reuse requires an existing session parent and new evidence directory")
    args.evidence_dir.mkdir(parents=True)
    args.follow_ups = False
    args.require_initial_prefix = None
    args.diagnose_unload = False
    pins = dict(pin, hooks={p: fluid.native.digest(REPO / p) for p in (runner.HOOK_FILES if runner is loader else runner.HOOKS)},
                harness={p: fluid.native.digest(REPO / p) for p in (runner.HARNESS_FILES if runner is loader else runner.HARNESSES)})
    report = dict(status="IN_PROGRESS", pins=pins, cases=[])
    try:
        for index, case in enumerate(args.case):
            if index:
                time.sleep(90)
            owner = f"W330-integrated {case} pid={os.getpid()}"
            with loader.heavy_lock(owner):
                verify_candidate(args.jar, args.source_commit, args.candidate_commit, args.candidate_sha256)
                row = runner.run_cut(args, case, args.evidence_dir / case, owner, pins)
            report["cases"].append(row)
            if row["status"] != "PASS":
                raise ValueError("First contradictory shared-infrastructure cut: " + case)
        report["status"] = "PASS_SELECTED"
    except Exception:
        report["status"] = "FAILED"
        raise
    finally:
        (args.evidence_dir / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")


def archive_native(world, destination):
    """Preserve stopped-world bytes before decoding; include every region, not just selected entity slots."""
    members = {}
    with zipfile.ZipFile(destination, "x", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for folder in ("data", "entities", "region"):
            for path in sorted((world / folder).glob("*")):
                if path.is_file() and path.suffix in (".dat", ".mca", ".mcc"):
                    relative = path.relative_to(world).as_posix()
                    members[relative] = dict(bytes=path.stat().st_size, sha256=fluid.native.digest(path))
                    archive.write(path, relative)
        archive.writestr("MEMBERS.json", json.dumps(members, indent=2))
    with zipfile.ZipFile(destination) as archive:
        for name, info in members.items():
            if hashlib.sha256(archive.read(name)).hexdigest() != info["sha256"]:
                raise ValueError("Native archive readback differs: " + name)
    return dict(file=destination.name, sha256=fluid.native.digest(destination), members=members)


def run(args):
    pin = verify_candidate(args.jar, args.source_commit, args.candidate_commit, args.candidate_sha256)
    selected = [row for row in inventory() if row["family"] == args.family and (not getattr(args, "case", None) or row["case"] in args.case)]
    if not selected or getattr(args, "case", None) and set(args.case) != {row["case"] for row in selected}:
        raise ValueError("Selected case is not in the requested family")
    args.case = [row["case"] for row in selected]
    runner = next((module for module in RUNNERS if selected[0]["case"] in module.CASES), None)
    args.startup_timeout = 420 if runner is settlement else 240
    args.dev = False
    args.candidate_pin = pin
    # No oracle, fixture, checkpoint, timeout or Java property is changed by this wrapper.
    # The ordinary runners retain their own first-contradiction stop and both restarts.
    original = getattr(runner, "raw_snapshot", None)
    counts = {}
    archives = []
    def retained(world):
        case = world.name.removeprefix("world-")
        index = counts.get(case, 0); counts[case] = index + 1
        archives.append(archive_native(world, args.evidence_dir / f"{case}-read-{index}-native.zip"))
        return original(world)
    if original is not None:
        runner.raw_snapshot = retained
    try:
        if args.family in REUSE:
            run_loader(args, pin)
        elif args.family == "G2-REUSE":
            if any(getattr(args, key, None) is None for key in ("old_jar", "old_source_commit", "start_counter")):
                raise ValueError("G2 reuse requires the existing campaign's old JAR/source and start counter")
            if args.session_dir.exists():
                raise ValueError("G2 session must be new")
            try:
                result = loader_s1.run(args)
                if result["status"] != "PASS":
                    raise ValueError("G2 S1 campaign failed")
            finally:
                sentinel = args.session_dir / ".arce-w176-owned"
                if args.session_dir.resolve().parent == Path("D:/GitHub/ARCE-Task-Evidence/Temp").resolve() and not args.session_dir.is_symlink() and sentinel.is_file():
                    shutil.rmtree(args.session_dir)
        else:
            runner.run(args)
    finally:
        if original is not None:
            runner.raw_snapshot = original
        if args.evidence_dir.exists():
            (args.evidence_dir / "integrated-pin.json").write_text(json.dumps(dict(
                **pin, family=args.family, cases=args.case, wrapper_sha256=fluid.native.digest(Path(__file__)),
                native_archives=archives), indent=2), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_server", type=Path, nargs="?")
    parser.add_argument("--inventory", action="store_true")
    parser.add_argument("--family", choices=tuple(dict.fromkeys(row["family"] for row in inventory())))
    parser.add_argument("--case", action="append", help="Run only named cases in the selected family; not full-family evidence")
    for key in ("jar", "session-dir", "evidence-dir", "old-jar", "start-counter"):
        parser.add_argument("--" + key, type=Path)
    for key in ("source-commit", "java", "lock-owner", "candidate-commit", "candidate-sha256", "old-source-commit"):
        parser.add_argument("--" + key)
    args = parser.parse_args()
    if args.inventory:
        print(json.dumps(inventory(), indent=2)); return 0
    if any(getattr(args, key) is None for key in ("source_server", "family", "jar", "session_dir", "evidence_dir", "source_commit", "java", "candidate_commit", "candidate_sha256")):
        parser.error("Native execution requires the family, source server, pinned JAR/source, outputs, Java and lock owner")
    if args.family not in REUSE and args.lock_owner is None:
        parser.error("Cargo and G2 execution require the caller's heavy-JVM lock owner; loader reuse takes its own lock")
    try:
        run(args); return 0
    except (OSError, ValueError, subprocess.CalledProcessError, fluid.native.smoke.SmokeError) as failure:
        print("FAIL: " + str(failure)); return 1


if __name__ == "__main__":
    raise SystemExit(main())
