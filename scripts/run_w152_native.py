#!/usr/bin/env python3
"""Sealed C0-C5 process cuts with independent native images and two stable restarts.

Run only under the outer W152 atomic heavy-JVM lock, using a committed diagnostic JAR.
Consumer-only saves are reported as a residual, separately from conservation passes.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import uuid
import zipfile
from collections import Counter

import run_v180_escrow_forced_stop as escrow
from run_v180_landed_disassembly_cuts import block_at, chunk_record, nbt_file, uuid_of
from sealed_cut_oracle import elect, inventory, require_inventory

smoke = escrow.smoke
CUTS = ("NONE", "C0_BASELINE_SAVED", "PREPARE_PROOF_CURRENT", "C1_RETAIN_BEFORE", "C1_RETAIN_AFTER",
        "C1_MOVE_BEFORE", "C1_MOVE_AFTER", "C1_ACK", "C2_STAGE_BEFORE", "C2_STAGE_AFTER", "PARTIAL_C_SAVED",
        "SEALED:TERRAIN:BARRIER_STORE_AFTER", "SEALED:TERRAIN:BARRIER_FORCE_AFTER", "SEALED:TERRAIN:BARRIER_READBACK_AFTER",
        "SEALED_PROOF_CURRENT", "C3_ELECT_BEFORE", "C3_MOVE_BEFORE", "C3_MOVE_AFTER", "C3_UNKNOWN", "C3_ACK",
        "C4_DISCARD_BEFORE", "C4_DISCARD_AFTER", "COMPLETE:ENTITY:BARRIER_STORE_AFTER",
        "COMPLETE:ENTITY:BARRIER_FORCE_AFTER", "COMPLETE:ENTITY:BARRIER_READBACK_AFTER", "COMPLETE_PROOF_CURRENT",
        "C5_REMOVE_BEFORE", "C5_MOVE_BEFORE", "C5_MOVE_AFTER", "C5_UNKNOWN", "C5_ACK",
        "UNSEAL_PREPARE_BEFORE", "UNSEAL_PREPARE_AFTER", "UNSEAL_PUBLISH_BEFORE", "UNSEAL_PUBLISH_AFTER",
        "ROLLBACK_ERASE_BEFORE", "ROLLBACK_ERASE_AFTER", "UNSEAL_PREPARE_FAILED", "RECOVERY_LOADED")
CONSUMER_CUTS = ("C_SOURCE_CONSUMER_ONLY", "VANILLA_SOURCE_CONSUMER_ONLY", "BOTH_SIDES_SAVED")
MARKER = re.compile(r"W152_NATIVE_(DONE status=(\S+)|CUT boundary=(\S+))")
ORIGIN = (15, 100, 15)


def block_state_at(chunk: dict, x: int, y: int, z: int) -> dict:
    """Read the complete native palette entry using 1.20.1's padded-long indices."""
    for section in chunk.get("sections", []):
        if section.get("Y") != y >> 4: continue
        states = section.get("block_states", {}); palette = states.get("palette", [])
        if not palette: raise ValueError("Native block has no palette")
        if len(palette) == 1: return palette[0]
        bits = max(4, (len(palette) - 1).bit_length()); per_long = 64 // bits
        index = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15)
        value = (states["data"][index // per_long] & 0xFFFFFFFFFFFFFFFF) >> ((index % per_long) * bits)
        return palette[value & ((1 << bits) - 1)]
    raise ValueError("Native block section is absent")


def consumer_verdict(state: dict, experiment: str) -> str:
    if experiment not in CONSUMER_CUTS:
        raise ValueError("Unknown consumer experiment")
    if state["phase"] is not None or state["entity_images"] or state["authority"] != "BLOCKS":
        raise ValueError("Consumer experiment precedes checked publication")
    if state["recipient_inventory"] != {"original": 14} or state["other_recipients"]:
        raise ValueError("Independent recipient identity/count differs")
    source = Counter(state["sealed_images"]); source.update(state["native_images"])
    expected = {"foreign": 6} if experiment == "BOTH_SIDES_SAVED" else {"original": 14}
    if dict(source) != expected:
        raise ValueError("Consumer experiment source image differs")
    require_inventory(state, {"foreign": 6, "original": 14} if experiment == "BOTH_SIDES_SAVED" else {"original": 28})
    return "PASS" if experiment == "BOTH_SIDES_SAVED" else "DOCUMENTED_RESIDUAL"


def inspect(world: Path, experiment: str = "") -> dict:
    journal = world / "data/advancedrocketrycommunity_rocket_transactions.dat"
    records = nbt_file(journal, 67_108_864)["data"].get("transactions", []) if journal.exists() else []
    if len(records) > 1 or any(row.get("type") != "SEALED_DISASSEMBLY" for row in records):
        raise ValueError("Unexpected native journal cohort")
    phase = records[0]["phase"] if records else None
    entities, sealed, native, recipients, blocks, files = [], [], [], [], {}, {}
    for x in range(-1, 3):
        for z in range(-1, 3):
            region = world / "region" / f"r.{x >> 5}.{z >> 5}.mca"
            entity_file = world / "entities" / f"r.{x >> 5}.{z >> 5}.mca"
            terrain = chunk_record(region, x, z) or {}
            image = chunk_record(entity_file, x, z) or {}
            for row in image.get("Entities", []):
                if "RocketEntityData" in row:
                    entities.append(row)
                elif row.get("id") == "minecraft:item":
                    recipients.append(row)
            for dx in range(2):
                for dz in range(2):
                    at = (15 + dx, 100, 15 + dz)
                    if (at[0] >> 4, at[2] >> 4) != (x, z):
                        continue
                    name = block_at(terrain or None, *at); blocks[",".join(map(str, at))] = name
                    be = next((row for row in terrain.get("block_entities", []) if tuple(row.get(k) for k in ("x", "y", "z")) == at), None)
                    if name == "advancedrocketrycommunity:sealed_rocket_block":
                        if not be or "arce_sealed_rocket_member" not in be:
                            raise ValueError("Carrier lacks its native root")
                        sealed.append(be)
                    elif name not in ("minecraft:air", "<chunk-not-stored>", "<section-not-stored>"):
                        native.append(dict(state=block_state_at(terrain, *at), block_entity=be))
            for file in (region, entity_file):
                if file.exists() and str(file.relative_to(world)) not in files:
                    files[str(file.relative_to(world))] = escrow.digest(file)
    if journal.exists():
        files[str(journal.relative_to(world))] = escrow.digest(journal)
    for file in (world / "playerdata").glob("*.dat"):
        recipients.append(nbt_file(file, 4_194_304))
        files[str(file.relative_to(world))] = escrow.digest(file)
    recipient = None
    if experiment:
        region = world / "region/r.0.0.mca"
        terrain = chunk_record(region, 5, 0) or {}
        recipient = next((row for row in terrain.get("block_entities", [])
                          if tuple(row.get(k) for k in ("x", "y", "z")) == (80, 100, 15)), None)
        if not recipient or recipient.get("id") != "minecraft:chest":
            raise ValueError("Independent recipient chest is absent")
        # Include entities in the recipient's independently stored chunk too.
        for row in (chunk_record(world / "entities/r.0.0.mca", 5, 0) or {}).get("Entities", []):
            if "RocketEntityData" in row: entities.append(row)
            elif row.get("id") == "minecraft:item": recipients.append(row)
    other_recipients = inventory(recipients)
    if recipient: recipients.append(recipient)
    observed = elect(phase, entities, sealed, native, recipients, retained=records[0].get("snapshot") if records else None)
    observed.update(phase=phase, blocks=blocks, entities=sorted(uuid_of(row["UUID"]) for row in entities),
                    entity_data=sorted(({key: row[key] for key in ("id", "UUID", "Pos", "RocketEntityData")} for row in entities),
                                       key=lambda row: uuid_of(row["UUID"])),
                    records=records, native=native, sealed=sealed, files=files)
    if experiment:
        observed.update(recipient_data=recipient, recipient_inventory=inventory(recipient), other_recipients=other_recipients,
                        recipient_state=block_state_at(terrain, 80, 100, 15))
        observed["consumer_verdict"] = consumer_verdict(observed, experiment)
    else:
        require_inventory(observed, {"original": 14})
    return observed


def comparable(state: dict) -> dict:
    return {key: value for key, value in state.items() if key != "files"}


def compare_consumers(sealed: Path, vanilla: Path, both: Path) -> dict:
    paths = (sealed, vanilla, both)
    results = [json.loads((path / "results.json").read_text(encoding="utf-8")) for path in paths]
    if [row["status"] for row in results] != ["DOCUMENTED_RESIDUAL", "DOCUMENTED_RESIDUAL", "PASS"]:
        raise ValueError("Consumer comparison requires both residual observations and a both-sides-saved pass")
    if len({(row["source_commit"], row["jar_sha256"]) for row in results}) != 1:
        raise ValueError("Consumer comparison implementation differs")
    states = [[comparable(json.loads((path / (stage + "-native.json")).read_text(encoding="utf-8")))
               for stage in ("recover1", "recover2")] for path in paths]
    if states[0][0] != states[0][1] or states[0] != states[1] or states[2][0] != states[2][1]:
        raise ValueError("Consumer comparison native identity or second restart differs")
    return dict(status="DOCUMENTED_RESIDUAL_MATCHES_VANILLA", conservation_exact=False,
                source_commit=results[0]["source_commit"], jar_sha256=results[0]["jar_sha256"],
                both_sides_saved="PASS", evidence=[str(path) for path in paths])


def preserve_native(world: Path, evidence: Path, stage: str, state: dict) -> dict:
    """Keep bounded raw inputs, not a world/export copy. Hashes cover the original file bytes."""
    names = set(state["files"])
    names.update(str(file.relative_to(world)) for file in (world / "poi").glob("*.mca"))
    if (world / "level.dat").is_file(): names.add("level.dat")
    if len(names) > 128 or sum((world / name).stat().st_size for name in names) > 100_000_000:
        raise smoke.SmokeError("Native observation input archive exceeds its evidence bound")
    archive = evidence / (stage + "-native-inputs.zip")
    with zipfile.ZipFile(archive, "x", compression=zipfile.ZIP_DEFLATED) as output:
        for name in sorted(names): output.write(world / name, name)
    if archive.stat().st_size > 50_000_000:
        raise smoke.SmokeError("Native observation archive exceeds the per-file evidence limit")
    return dict(file=archive.name, sha256=escrow.digest(archive),
                inputs={name: escrow.digest(world / name) for name in sorted(names)})


def run(args) -> dict:
    repo = Path(__file__).resolve().parents[1]
    commit = args.source_commit
    if args.source_export:
        if (repo / ".w152-export-source").read_text(encoding="ascii").strip() != commit:
            raise smoke.SmokeError("Pinned export marker differs from the tested source")
    elif (subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip() != commit
          or subprocess.check_output(["git", "status", "--porcelain"], cwd=repo, text=True)):
        raise smoke.SmokeError("Exact clean committed source required")
    token = os.environ.get("W152_LOCK_TOKEN", "")
    lock = Path("D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm/owner.txt")
    if not token or not lock.read_text(encoding="utf-8-sig").startswith(token):
        raise smoke.SmokeError("Outer atomic-lock ownership missing")
    evidence = args.evidence_dir.resolve(); evidence.mkdir(exist_ok=False)
    temp = Path("D:/GitHub/ARCE-Task-Evidence/Temp").resolve()
    session = temp / ("w152-native-" + uuid.uuid4().hex)
    owned = uuid.uuid4().hex; marker = session / ".w152-owned"
    result = dict(status="IN_PROGRESS", source_commit=commit, jar_sha256=escrow.digest(args.jar),
                  cut=args.cut, recovery_cut=args.recovery_cut, initial_d=args.initial_d, commands=[], stages=[])
    experiment = args.cut if args.cut in CONSUMER_CUTS else ""
    try:
        session.mkdir(); marker.write_text(owned, encoding="ascii")
        shutil.copytree(args.source_server / "libraries", session / "libraries")
        (session / "mods").mkdir(); shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = smoke.allocate_port(); smoke.write_server_configuration(session, port, True)
        props = smoke.expected_server_properties(port, True)
        props.update({"sync-chunk-writes": "true", "enable-rcon": "false", "level-name": "world", "level-type": "minecraft:flat",
                      "max-tick-time": "60000", "view-distance": "3", "simulation-distance": "3",
                      "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}], "biome": "minecraft:plains"})})
        (session / "server.properties").write_text("".join(f"{key}={value}\n" for key, value in sorted(props.items())), encoding="ascii")
        stages = ["start"] + (["recovery-cut"] if args.recovery_cut else []) + ["recover1", "recover2"]
        stable = []
        for stage in stages:
            for drive in (Path("C:/"), Path("D:/")):
                if shutil.disk_usage(drive).free < 10 * 1024**3:
                    raise smoke.SmokeError("Insufficient native-server disk headroom")
            cut = args.cut if stage == "start" else args.recovery_cut if stage == "recovery-cut" else "NONE"
            command = escrow._server_command(args.java); command[2] = "-Xmx4G"
            command[1:1] = ["-XX:ActiveProcessorCount=4", "-Dadvancedrocketrycommunity.w152Native=true",
                            "-Dadvancedrocketrycommunity.w152Stage="+stage, "-Dadvancedrocketrycommunity.w152Cut="+cut,
                            "-Dadvancedrocketrycommunity.w152Experiment="+experiment,
                            "-Dadvancedrocketrycommunity.w152InitialD="+args.initial_d,
                            "-Dadvancedrocketrycommunity.w152Commit="+commit]
            result["commands"].append(command)
            process = smoke.CapturedProcess(command, session, evidence / (stage + ".log"))
            try:
                line = process.wait_for(MARKER, 300); match = MARKER.search(process.lines[line])
                if match[3]:
                    if match[3] != cut or cut == "NONE":
                        raise smoke.SmokeError("Unexpected native cut")
                    process.abort()
                else:
                    process.command("stop")
                    if process.finish() != 0:
                        raise smoke.SmokeError("Native stop failed")
            finally:
                if process.process.poll() is None: process.abort()
                report = session / "world/w152-native.json"
                if report.exists(): shutil.copy2(report, evidence / (stage + ".json"))
            raw = json.loads((evidence / (stage + ".json")).read_text(encoding="utf-8"))
            expected = "STABLE" if cut == "NONE" else "CUT"
            if raw["status"] != expected:
                raise smoke.SmokeError(f"{stage}: {raw['status']}: {raw.get('detail')}")
            if any("/ERROR]" in line or "/FATAL]" in line for line in process.lines):
                raise smoke.SmokeError("Native ERROR/FATAL observed")
            state = inspect(session / "world", experiment)
            (evidence / (stage + "-native.json")).write_text(json.dumps(state, indent=2), encoding="utf-8")
            raw_inputs = preserve_native(session / "world", evidence, stage, state)
            result["stages"].append(dict(stage=stage, status=raw["status"], authority=state["authority"], raw_inputs=raw_inputs))
            if stage in ("recover1", "recover2"):
                if state["phase"] is not None or state["sealed"]:
                    raise smoke.SmokeError("Stable restart left retained U or C")
                stable.append(comparable(state))
        if stable[0] != stable[1]:
            raise smoke.SmokeError("Second restart changed the elected native state")
        result["status"] = stable[1].get("consumer_verdict", "PASS")
        if experiment:
            result.update(experiment=experiment, conservation_exact=result["status"] == "PASS",
                          vanilla_comparison="REQUIRED" if experiment != "BOTH_SIDES_SAVED" else "NOT_APPLICABLE")
    except Exception as failure:
        result.update(status="FAIL", error=str(failure))
    finally:
        (evidence / "results.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
        if session.is_dir() and not session.is_symlink() and session.resolve().parent == temp and marker.read_text(encoding="ascii") == owned:
            shutil.rmtree(session)
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-server", type=Path, required=True)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--source-export", action="store_true", help="Outer lock owner supplied an exact git archive and source marker")
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--cut", choices=CUTS + CONSUMER_CUTS, required=True)
    parser.add_argument("--recovery-cut", choices=CUTS)
    parser.add_argument("--initial-d", choices=("SAVED", "NEVER_SAVED"), default="SAVED")
    result = run(parser.parse_args()); print(json.dumps(result, indent=2))
    return 0 if result["status"] in ("PASS", "DOCUMENTED_RESIDUAL") else 1


if __name__ == "__main__":
    raise SystemExit(main())
