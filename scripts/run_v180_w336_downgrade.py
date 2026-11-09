#!/usr/bin/env python3
"""Repeat W176 with placed bay/cargo devices and stores; restore the whole backup and restart twice."""
from __future__ import annotations

import shutil
import uuid

import loader_cut_nbt as nbt
import run_v180_bay_forced_stop as bay
import run_v180_fuel_loader_s1 as w176
import w336_downgrade_oracle as oracle
import w336_native as native
from w336_saturation_oracle import DEVICES


def seed_ledger(world):
    path = world / "data/advancedrocketrycommunity_resource_escrow.dat"
    native.require(not path.exists(), "Fixture ledger already exists")
    fields = dict(schema_version=nbt.Tag(nbt.INT, 2),
                  format_epoch=nbt.Tag(nbt.STRING, b"v1.8.0-resource-escrow"),
                  ledger_id=nbt.uuid_tag(uuid.uuid4()), issued_through=nbt.Tag(nbt.LONG, 0),
                  writer_generation=nbt.Tag(nbt.LONG, 0), issued_launch_serial=nbt.Tag(nbt.LONG, 0))
    for key in ("records", "floors", "tombstones", "rocket_authorities", "armed_flights"):
        fields[key] = nbt.Tag(nbt.LIST, (nbt.COMPOUND, []))
    nbt.write_gzip(path, b"", nbt.Tag(nbt.COMPOUND, dict(data=nbt.Tag(nbt.COMPOUND, fields),
                                                       DataVersion=nbt.Tag(nbt.INT, 3465))))


def family(run):
    native.require(native.smoke.digest_file(run.args.old_jar) == oracle.OLD_SHA256,
                   "Historical JAR differs from W176's pinned old artifact")
    world = run.session / "world"
    run.start("seed", properties={"bayCheckpoint": "B18_INTENT"}); run.ready()
    adapter = bay.Session(run.args, run.session, run.evidence)
    adapter.process = run.process
    fixture = bay.launch_fixture(adapter)
    adapter.wait(native.re.compile("ARCE_RELEASE_BAY_PAUSED cut=B18_INTENT op="), run.args.flight_timeout)
    positions = [(128 + i * 16, 80, 128) for i in range(5)]
    run.load(positions)
    for at, kind in zip(positions, ("bay", "item-load", "item-unload", "fluid-load", "fluid-unload")):
        run.send(f"setblock {' '.join(map(str, at))} {native.NS + DEVICES[kind][0]}", "Changed the block")
    run.send("save-all flush", native.SAVED, 180)
    run.stop("seed")
    seed_ledger(world)
    for name in oracle.STORES:
        native.require((world / "data" / name).is_file(), "Missing seeded store: " + name)
    backup = run.session / "whole-world-backup"
    shutil.copytree(world, backup)
    original = w176.world_hashes(world)
    archive = run.capture(world, "before-downgrade")
    rows = []
    for attempt in range(3):
        before = w176.world_hashes(world)
        before_level = run.evidence / f"old-{attempt}-before-level.dat"
        shutil.copy2(world / "level.dat", before_level)
        process = run.start(f"old-{attempt}", jar=run.args.old_jar)
        process.wait_for(w176.DOWNGRADE_GUARD, run.args.startup_timeout)
        code = process.finish(run.args.exit_timeout)
        run.process = None
        latest = run.evidence / f"old-{attempt}-latest.log"
        shutil.copy2(run.session / "logs/latest.log", latest)
        live = w176.check_downgrade_refusal(latest.read_text(encoding="utf-8").splitlines(), attempt == 0)
        after = w176.world_hashes(world)
        after_level = run.evidence / f"old-{attempt}-after-level.dat"
        shutil.copy2(world / "level.dat", after_level)
        # Registry removals occur only once; repeats retain W176's exact byte oracle.
        result = oracle.compare(before, after, before_level, after_level) if attempt == 0 else dict(
            w176_oracle=w176.refusal_storage_result(before, after))
        rows.append(dict(attempt=attempt, exit_code=code, live=live, result=result,
                         world=run.capture(world, f"old-{attempt}-world")))
    # Replace only this owned disposable world, from its entire backup, never one store.
    native.require(world.parent == run.session and backup.parent == run.session, "Unsafe whole-world restore")
    shutil.rmtree(world); shutil.copytree(backup, world)
    native.require(w176.world_hashes(world) == original, "Whole-world backup did not restore exactly")
    restored = []
    for restart in (1, 2):
        run.start(f"restored-{restart}"); run.ready()
        adapter.process = run.process
        status = adapter.await_settled("B18_INTENT", restart, fixture)
        run.send("save-all flush", native.SAVED, 180)
        run.stop(f"restored-{restart}")
        restored.append(dict(status=status, world=run.capture(world, f"restored-{restart}-world")))
    # An expected historical defect is still a failed storage-safety row, not a passing downgrade.
    return dict(status="FAILED", reason="W176 whole-world byte-preservation oracle failed; not waived",
                old_commit=oracle.OLD_COMMIT, old_sha256=oracle.OLD_SHA256, fixture=fixture,
                seed=archive, downgrade=rows, candidate_restored=restored)


def main():
    parser = native.parser(__doc__)
    parser.add_argument("--old-jar", required=True, type=native.Path)
    parser.add_argument("--flight-timeout", type=float, default=120)
    parser.add_argument("--settle-timeout", type=float, default=180)
    return native.execute(parser.parse_args(), family)


if __name__ == "__main__":
    raise SystemExit(main())
