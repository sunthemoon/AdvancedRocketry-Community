#!/usr/bin/env python3
"""One W164 B-PRE case, using the unchanged W150 cut/restart conservation oracle.

The caller owns the heavy-JVM lock and the 90-second hand-off between cases.
I6a drives the gated FM12a hook; I6b drives the release-test disassembly adapter,
not a player client. No native start may exceed the tranche's 60-start ceiling.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import time
import zipfile

import run_v180_predecessor_matrix as predecessor
from run_v180_predecessor_smoke import require
from run_v180_rocket_settlement_completion import require_lock
from w152_native_inputs import compact_trace

STARTS_PER_CASE = 3
MAX_STARTS = 60
CASES = {f"{site}_{image}_{cut}": (site, f"{image}_{cut}")
         for site in ("I6a", "I6b") for image in ("UNSAVED", "DESCENT") for cut in predecessor.PRE}


def preserve(world: Path, evidence: Path, restart: int) -> dict:
    """Lossless native-file archive: no source tree, no derived region and no lost census slots."""
    archive = evidence / f"raw-{restart}.zip"
    hashes = {}
    with zipfile.ZipFile(archive, "x", compression=zipfile.ZIP_DEFLATED) as output:
        for path in sorted(world.rglob("*")):
            if not path.is_file() or not (path.suffix in (".mca", ".mcc", ".dat", ".arce-pending") or path.name == "level.dat_old"):
                continue
            name = path.relative_to(world).as_posix()
            require(not path.is_symlink(), "Native storage input is a link")
            hashes[name] = predecessor.digest(path); output.write(path, name)
    require(archive.stat().st_size <= 50_000_000, "Native archive exceeds the repository per-file limit")
    with zipfile.ZipFile(archive) as stored:
        for name, expected in hashes.items():
            with stored.open(name) as stream:
                require(hashlib.file_digest(stream, "sha256").hexdigest() == expected, "Native archive changed input bytes")
    (evidence / f"raw-{restart}-hashes.json").write_text(json.dumps(hashes, indent=2), encoding="utf-8")
    return dict(file=archive.name, sha256=predecessor.digest(archive), files=len(hashes), bytes=archive.stat().st_size)


def request(site, process, dimension, entity, fuel, evidence):
    command = (f"arce progression release-test release-reservation {entity}" if site == "I6a" else
               f"execute in {dimension} run arce rocket release-test disassemble {entity} discard-fuel {fuel['amount']}")
    start = len(process.lines); sent = []
    # The existing preproof wait is 180 seconds. Reissuing an explicit test demand does not refresh a pending request.
    deadline = time.monotonic() + 180
    try:
        while time.monotonic() < deadline:
            require(process.process.poll() is None, "Native process ended before its selected cut")
            process.command(command); sent.append(dict(command=command, monotonic=time.monotonic()))
            for _ in range(10):
                if any("ARCE_N3_RETIREMENT_PAUSED boundary=" in line for line in process.lines[start:]): return
                time.sleep(0.1)
            # FM12a has a single demand. Only I6b needs a new demand/consent after non-destructive preparation.
            if site == "I6a": return
        raise RuntimeError("I6 disassembly did not reach its cut within the existing preproof wait")
    finally:
        (evidence / "requests.json").write_text(json.dumps(sent, indent=2), encoding="utf-8")


def command_for(site, args, mode, cut, restart):
    command = predecessor.command_for(args, mode, cut, restart)
    if not restart and site == "I6a": command.insert(1, "-Darce.releaseTest.firstEventCut=FM12a")
    return command


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_server", type=Path)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--case", choices=CASES, required=True)
    parser.add_argument("--lock-owner", required=True)
    parser.add_argument("--startup-timeout", type=float, default=240)
    args = parser.parse_args()
    require_lock(args.lock_owner); predecessor.assert_pin(args.source_commit)
    site, case = CASES[args.case]
    root = args.evidence_dir.resolve(); root.mkdir(parents=True, exist_ok=True)
    evidence = root / args.case
    require(not evidence.exists(), "Do not overwrite or silently rerun a counted case")
    ledger = root / "starts.json"
    rows = json.loads(ledger.read_text(encoding="utf-8")) if ledger.exists() else []
    require(len(rows) + STARTS_PER_CASE <= MAX_STARTS, "This case would exceed the 60-start tranche")
    require(not any(row["case"] == args.case for row in rows), "Case already has counted starts")

    def started(name, restart, entry):
        require_lock(args.lock_owner)
        require(len(rows) < MAX_STARTS, "Native start ceiling reached")
        rows.append(dict(case=args.case, restart=restart, source=args.source_commit, command=entry["command"],
                         status="LAUNCH_ATTEMPT"))
        # Record before launching, so an interrupted launcher cannot silently evade the count.
        ledger.write_text(json.dumps(rows, indent=2), encoding="utf-8")

    def command_builder(args, mode, cut, restart):
        return command_for(site, args, mode, cut, restart)

    row = predecessor.run_case(args, case, evidence, prefix="W164-B-PRE", preserve=preserve, started=started,
            command_builder=command_builder,
            demand=lambda process, dimension, entity, fuel: request(site, process, dimension, entity, fuel, evidence))
    row["site"] = site; row["case"] = args.case
    row["traces"] = [compact_trace(path) for path in sorted(evidence.glob("start-*.log"))]
    (evidence / "result.json").write_text(json.dumps(row, indent=2), encoding="utf-8")
    require(sum(path.stat().st_size for path in root.parent.rglob("*") if path.is_file()) < 100_000_000,
            "W164 evidence exceeds its task budget")
    print(json.dumps(dict(case=args.case, status=row["status"], counted_starts=len(rows))), flush=True)
    return 0 if row["status"] == "PASS" else 1


if __name__ == "__main__": raise SystemExit(main())
