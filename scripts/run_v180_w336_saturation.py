#!/usr/bin/env python3
"""Observe shared 255/256/257-coordinate saturation on a supplied JAR, then restart twice."""
from __future__ import annotations

import re
import time

import loader_cut_nbt as nbt
import w336_native as native
import w336_saturation_oracle as oracle

REASONS = tuple("Refusing chunk save with " + name + " root that native serialization cannot keep; back up and repair offline"
                for name in ("a satellite bay", "an item cargo", "a fluid cargo", "a fluid fuel loader"))
SATURATED = "Refusing chunk save after denial capacity exhausted; back up and repair first"


def audit(lines):
    """Allow only the expected guard exceptions and ChunkMap failures at fixture coordinates."""
    allowed = {(x >> 4, z >> 4) for x, _, z in oracle.BACKGROUND + [oracle.TARGET, oracle.OVERFLOW, oracle.TERRAIN]}
    errors = 0
    for line in lines:
        if "/ERROR]" not in line and "/FATAL]" not in line:
            continue
        event = "Exception caught during firing event: "
        chunk = re.search(r"Failed to save chunk (?:\[)?(-?\d+), ?(-?\d+)(?:\])?\s*$", line)
        if "/ERROR]" in line and event in line and line.split(event, 1)[1].strip() in (*REASONS, SATURATED):
            errors += 1
        elif "/ERROR]" in line and chunk and tuple(map(int, chunk.groups())) in allowed:
            errors += 1
        else:
            raise ValueError("Unexpected saturation ERROR/FATAL: " + line)
    return errors


def save(run):
    run.send("save-all flush", native.SAVED, 180)


def mutate(run, block):
    at = " ".join(map(str, oracle.TERRAIN))
    run.send(f"setblock {at} {block}", r"Changed the block|Could not set the block")
    token = "W336_TERRAIN_" + block.split(":")[1]
    run.send(f"execute if block {at} {block} run say {token}", token)
    save(run)


def disk_terrain(run, expected):
    # Flush has acknowledged storage, so this is a disk read, never RAM status.
    actual = oracle.terrain(run.session / "world")
    native.require(actual == expected, f"Terrain byte oracle saw {actual}, expected {expected}")
    return actual


def await_denials(run, positions):
    expected = set(positions)
    deadline = time.monotonic() + 60
    while True:
        observed = {tuple(map(int, match.groups())) for line in run.process.lines
                    if (match := re.search(r"_(?:UNSUPPORTED|GUARDED) pos=(-?\d+), (-?\d+), (-?\d+) (?:status|guard)=", line))}
        if expected <= observed:
            return
        native.require(time.monotonic() < deadline, "Missing distinct unsupported loads: " + str(expected - observed))
        time.sleep(0.1)


def family(run):
    kind = run.args.kind
    background_kind = "bay" if kind == "fuel" else "fuel"
    world = run.session / "world"
    run.start("seed"); run.ready()
    run.send("gamerule randomTickSpeed 0")
    run.load(oracle.BACKGROUND + [oracle.TARGET, oracle.OVERFLOW, oracle.TERRAIN])
    for at in oracle.BACKGROUND + [oracle.TARGET, oracle.OVERFLOW]:
        which = background_kind if at in oracle.BACKGROUND else kind
        run.send(f"setblock {' '.join(map(str, at))} {native.NS + oracle.DEVICES[which][0]}")
    mutate(run, "minecraft:stone")
    run.send("forceload remove all")
    save(run); run.stop("seed")
    fixtures = oracle.inject(world, oracle.BACKGROUND, background_kind)
    fixtures += oracle.inject(world, [oracle.TARGET, oracle.OVERFLOW], kind)
    native.write_json(run.evidence / "offline-fixtures.json", fixtures)
    seed = run.capture(world, "unsupported-seed")
    observed = {}
    run.start("boundary"); run.ready()
    run.load([oracle.TERRAIN]); run.load(oracle.BACKGROUND)
    # Every distinct background coordinate must have been observed by its own live loader.
    await_denials(run, oracle.BACKGROUND)
    mutate(run, "minecraft:gold_block")
    observed[255] = disk_terrain(run, "minecraft:gold_block")
    run.load([oracle.TARGET]); await_denials(run, [oracle.TARGET]); mutate(run, "minecraft:diamond_block")
    observed[256] = disk_terrain(run, "minecraft:diamond_block")
    run.load([oracle.OVERFLOW]); await_denials(run, [oracle.OVERFLOW]); mutate(run, "minecraft:emerald_block")
    observed[257] = disk_terrain(run, "minecraft:diamond_block")
    # Avoid reload immediately re-saturating the new Level: persist removal of vanilla tickets.
    run.send("forceload remove all"); save(run)
    run.stop("boundary", audit)
    boundary = oracle.check_boundary(observed)
    oracle.check_stage(world, fixtures, "minecraft:diamond_block")
    snapshots = [run.capture(world, "boundary-world")]
    restarts = []
    for restart in (1, 2):
        run.start(f"restart-{restart}"); run.ready()
        # No unsupported device is loaded here: Level's transient denial pool is fresh.
        run.load([oracle.TERRAIN]); mutate(run, "minecraft:emerald_block" if restart == 1 else "minecraft:gold_block")
        run.send("forceload remove all"); save(run)
        lines = run.stop(f"restart-{restart}")
        native.require(not any("_UNSUPPORTED pos=" in line or "ARCE_SATELLITE_BAY_GUARDED pos=" in line
                               for line in lines), "Restart reloaded device fixtures")
        expected = "minecraft:emerald_block" if restart == 1 else "minecraft:gold_block"
        restarts.append(oracle.check_stage(world, fixtures, expected))
        snapshots.append(run.capture(world, f"restart-{restart}-world"))
    return dict(kind=kind, background_kind=background_kind, boundary=boundary,
                observed=observed, seed=seed, restarts=restarts, snapshots=snapshots)


def main():
    parser = native.parser(__doc__)
    parser.add_argument("--kind", choices=oracle.DEVICES, required=True)
    args = parser.parse_args()
    return native.execute(args, family)


if __name__ == "__main__":
    raise SystemExit(main())
