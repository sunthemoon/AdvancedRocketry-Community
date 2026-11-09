#!/usr/bin/env python3
"""Run the existing cargo cut families on the fixed integrated candidate, without building a JAR."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

import run_v180_cargo_cuts as fluid
import run_v180_cargo_s4_s5_cuts as settlement
import run_v180_item_cuts as item

REPO = Path(__file__).resolve().parents[1]
CANDIDATE_COMMIT = "70dd5d43e"
CANDIDATE_SHA256 = "fa5ec1812995ce3cca9d74e447301c66b6d5c9117789c2daae71cac4598e4832"
PRODUCTION_PATHS = ("src/main", "src/generated", "build.gradle", "settings.gradle", "gradle.properties")
RUNNERS = (fluid, settlement, item)
RECOVERY_UNLOAD = frozenset(("FF_DISASSEMBLY_REMOVAL", "FF_DISASSEMBLY_RESTORATION", "FF_DISASSEMBLY_RELEASE",
                            "FF_REBUILD_DESTINATION", "FF_KEEP_SOURCE", "FF_REBUILD_SOURCE", "FF_STALE_PREPARED", "FF_RECONNECT"))


def inventory():
    rows = []
    for runner in RUNNERS:
        for case, spec in runner.CASES.items():
            probe = spec.get("probe", case)
            unload = "_UNLOAD_" in probe or case.startswith(("FS_", "IS_", "IR_")) or case in RECOVERY_UNLOAD
            medium = "ITEM" if runner is item else "FLUID"
            boundary = spec.get("checkpoint", spec.get("boundary", settlement.BOUNDARIES.get(case, case[3:])))
            rows.append(dict(family=spec["family"], case=case, probe=probe, boundary=boundary,
                             kind=f"{medium}_CARGO_{'UNLOAD' if unload else 'LOAD'}",
                             runner="scripts/" + Path(runner.__file__).name))
    return rows


def verify_candidate(jar, source):
    fluid.native.assert_pin(source)
    subprocess.run(["git", "diff", "--quiet", CANDIDATE_COMMIT, "HEAD", "--", *PRODUCTION_PATHS], cwd=REPO, check=True)
    actual = fluid.native.digest(jar)
    if actual != CANDIDATE_SHA256:
        raise ValueError("The supplied JAR is not candidate C1")
    return dict(source_commit=source, candidate_commit=CANDIDATE_COMMIT, jar_sha256=actual,
                production_diff_command=["git", "diff", "--quiet", CANDIDATE_COMMIT, "HEAD", "--", *PRODUCTION_PATHS],
                production_diff_exit=0)


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
    pin = verify_candidate(args.jar, args.source_commit)
    selected = [row for row in inventory() if row["family"] == args.family]
    runner = next(module for module in RUNNERS if selected[0]["case"] in module.CASES)
    args.case = [row["case"] for row in selected]
    args.startup_timeout = 420 if runner is settlement else 240
    args.dev = False
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
    for key in ("jar", "session-dir", "evidence-dir"):
        parser.add_argument("--" + key, type=Path)
    for key in ("source-commit", "java", "lock-owner"):
        parser.add_argument("--" + key)
    args = parser.parse_args()
    if args.inventory:
        print(json.dumps(inventory(), indent=2)); return 0
    if any(getattr(args, key) is None for key in ("source_server", "family", "jar", "session_dir", "evidence_dir", "source_commit", "java", "lock_owner")):
        parser.error("Native execution requires the family, source server, pinned JAR/source, outputs, Java and lock owner")
    try:
        run(args); return 0
    except (OSError, ValueError, subprocess.CalledProcessError, fluid.native.smoke.SmokeError) as failure:
        print("FAIL: " + str(failure)); return 1


if __name__ == "__main__":
    raise SystemExit(main())
