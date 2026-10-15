#!/usr/bin/env python3
"""W63 clean native restart matrix. Run under the heavy-JVM lock on committed sources.

Creates only a new owned disposable server. It never edits saved data between starts.
On any failed live/disk check the matrix stops, preserving logs and compact native NBT.
The caller removes the owned session after reviewing the evidence (also on failure).
"""
from __future__ import annotations

import argparse
import gzip
import json
from pathlib import Path
import re
import shutil
import struct
import subprocess

if __package__:
    from . import run_dedicated_server_smoke as smoke
    from .inspect_celestial_saved_data import NbtReader
    from .run_v060_flight_server_smoke import _server_command
    from .run_v180_escrow_forced_stop import record
else:
    import run_dedicated_server_smoke as smoke
    from inspect_celestial_saved_data import NbtReader
    from run_v060_flight_server_smoke import _server_command
    from run_v180_escrow_forced_stop import record

CASES = {
    "F1": ("seed", "check", "check", "disassemble", "empty"),
    "F2": ("seed", "check", "check", "check", "reconnect-first"),
    "F2_TWO": ("seed", "check", "check", "check", "reconnect-first", "reconnect-second"),
}
JOURNAL = "advancedrocketrycommunity_rocket_transfers.dat"
OWNER = "00630000-0000-4000-8000-000000000001"
PASSENGERS = ("00630000-0000-4000-8000-000000000002", "00630000-0000-4000-8000-000000000003")
NATIVE_TEMP = Path("D:/GitHub/ARCE-Task-Evidence/Temp")
MARKER = re.compile(r"ARCE_W63_(?:RESULT|FAILURE) case=")


def read_nbt(path: Path) -> dict:
    with gzip.open(path, "rb") as stream:
        raw = stream.read(4_194_305)
    if len(raw) > 4_194_304:
        raise smoke.SmokeError("Oversized native NBT")
    return NbtReader(raw).read_root()


def entity_slots(path: Path) -> list[int]:
    with path.open("rb") as stream:
        header = stream.read(4096)
    # Vanilla can open an entity region without ever writing an entity to it.
    if not header:
        return []
    if len(header) != 4096:
        raise smoke.SmokeError("Truncated entity region header")
    return [slot for slot in range(1024) if struct.unpack_from(">I", header, slot * 4)[0]]


def capture(world: Path, target: Path) -> dict:
    target.mkdir()
    files = {}
    rockets = []
    # Inspect every populated entity slot, not just the expected rocket chunk.
    for path in sorted(world.rglob("entities/*.mca")):
        for slot in entity_slots(path):
            for entity in record(path, slot).get("Entities", []):
                if "RocketEntityData" in entity:
                    rockets.append(dict(file=path.relative_to(world).as_posix(), slot=slot, entity=entity))
        destination = target / path.relative_to(world)
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, destination)
        files[path.relative_to(world).as_posix()] = smoke.digest_file(path)
    for path in (world / "data" / JOURNAL, world / "playerdata" / (OWNER + ".dat")):
        destination = target / path.relative_to(world)
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, destination)
        files[path.relative_to(world).as_posix()] = smoke.digest_file(path)
    for identity in PASSENGERS:
        path = world / "playerdata" / (identity + ".dat")
        if path.exists():
            destination = target / path.relative_to(world)
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, destination)
            files[path.relative_to(world).as_posix()] = smoke.digest_file(path)
    journal = read_nbt(world / "data" / JOURNAL)["data"]
    owner = read_nbt(world / "playerdata" / (OWNER + ".dat"))
    loader = []
    terrain = world / "region/r.0.0.mca"
    if terrain.exists():
        loader = [entry for entry in record(terrain).get("block_entities", [])
                  if [entry.get(key) for key in ("x", "y", "z")] == [75, 100, 72]]
        files["region/r.0.0.mca"] = smoke.digest_file(terrain)
    result = dict(rockets=rockets, journal=journal, owner_inventory=owner.get("Inventory", []),
                  loader=loader, original_file_sha256=files)
    (target / "inspection.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    return result


def verify_disk(disk: dict, case: str, stage: str) -> dict:
    rockets, journal = disk["rockets"], disk["journal"]["transfers"]
    retired = case == "F1" and stage in ("disassemble", "empty")
    if len(rockets) != (0 if retired else 1):
        raise smoke.SmokeError("Saved rocket count differs")
    inventory = disk["owner_inventory"]
    buckets = sum(item["Count"] for item in inventory
                  if item["id"] == "advancedrocketrycommunity:rocket_fuel_bucket")
    if case == "F1" and buckets != 2:
        raise smoke.SmokeError(f"Withdrawn bucket count differs: {buckets}")
    released = retired or case == "F2" and stage == "reconnect-first"
    if len(journal) != (0 if released else 1):
        raise smoke.SmokeError("Saved journal count differs")
    if retired:
        return dict(rockets=0, journal=0, buckets=buckets)
    entity = rockets[0]["entity"]
    wrapper = entity["RocketEntityData"]
    fuel = wrapper["flight_data"]["fuel"]
    banks = fuel["propulsion"]
    vector = [banks[key]["amount"] for key in ("primary", "oxidizer")]
    expected = [0, 0] if case == "F1" else [1000, 1000] if released else [2000, 1000]
    if vector != expected:
        raise smoke.SmokeError(f"Saved bank vector differs: {vector} != {expected}")
    if case != "F1":
        canisters = sum(item["Count"] for item in inventory
                        if item["id"] == "advancedrocketrycommunity:hydrogen_canister")
        if canisters != (2 if released else 1) or sum(vector) + canisters * 1000 != 4000:
            raise smoke.SmokeError("Saved rocket/player fuel is not conserved")
        if not released:
            loaders = disk["loader"]
            if len(loaders) != 1:
                raise smoke.SmokeError("Saved pending loader is missing or duplicated")
            loader = loaders[0]["arce_fuel_loader"]
            if (loader["buffered_units"] != 0 or "target_rocket_id" in loader
                    or loader["slot_role"] != 1
                    or loader["item"] != {"id": "advancedrocketrycommunity:rocket_fuel_cell", "Count": 1}):
                raise smoke.SmokeError("Saved pending loader selected a target or changed its resources")
    if journal:
        entry = journal[0]
        if entry["phase"] != ("COMMITTED" if case == "F1" else "PREPARED"):
            raise smoke.SmokeError("Wrong saved transfer phase")
        if wrapper["assembly_transaction_id"] != entry["logical_rocket_id"]:
            raise smoke.SmokeError("Saved logical identity differs from journal")
        expected_id = entry["destination_entity_id" if case == "F1" else "source_entity_id"]
        if entity["UUID"] != expected_id:
            raise smoke.SmokeError("Saved entity identity differs from journal")
        if case != "F1" and fuel != entry["source_flight"]["fuel"]:
            raise smoke.SmokeError("Saved full source fuel vector differs from journal")
    return dict(rockets=1, journal=len(journal), vector=vector, buckets=buckets, entity_uuid=entity["UUID"])


def run(args) -> dict:
    repo = Path(__file__).resolve().parents[1]
    def git(*values):
        return subprocess.check_output(["git", *values], cwd=repo, text=True).strip()
    if git("rev-parse", "HEAD") != args.source_commit or git("status", "--porcelain"):
        raise smoke.SmokeError("Native run requires the exact committed, clean source")
    session, evidence = args.session_dir.resolve(), args.evidence_dir.resolve()
    if session.parent != NATIVE_TEMP.resolve() or session.exists() or smoke.is_link_or_junction(session):
        raise smoke.SmokeError("Unsafe or occupied disposable W63 session")
    if evidence.exists() or session == evidence or session in evidence.parents:
        raise smoke.SmokeError("Evidence must be new and separate")
    for drive in (Path("C:/"), NATIVE_TEMP):
        if shutil.disk_usage(drive).free < 10 * 1024**3:
            raise smoke.SmokeError("W63 requires at least 10 GiB free on both drives")
    libraries = args.source_server.resolve() / "libraries"
    if not (libraries / "net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt").is_file():
        raise smoke.SmokeError("Missing pinned Forge arguments")
    if any(smoke.is_link_or_junction(path) for path in libraries.rglob("*")):
        raise smoke.SmokeError("Source libraries contain links")
    session.mkdir(); evidence.mkdir(parents=True)
    (session / ".arce-w63-owned").write_text(args.source_commit, encoding="ascii")
    report = dict(status="IN_PROGRESS", source_commit=args.source_commit, jar_sha256=smoke.digest_file(args.jar),
                  source_server=str(args.source_server.resolve()), session=str(session), cases=[],
                  scope="Clean JVM restarts of seeded checkpoints; embedded-channel players, not V2 or torn-save evidence")
    process = None
    try:
        shutil.copytree(libraries, session / "libraries")
        (session / "mods").mkdir(); shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = smoke.allocate_port(); smoke.write_server_configuration(session, port, True)
        for case in args.case or CASES:
            properties = smoke.expected_server_properties(port, True)
            properties.update({"level-name": "world-W63-" + case, "level-type": "minecraft:flat", "max-tick-time": "60000",
                               "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}], "biome": "minecraft:plains"})})
            (session / "server.properties").write_text("".join(f"{key}={value}\n" for key, value in sorted(properties.items())), encoding="ascii")
            row = dict(case=case, starts=[]); report["cases"].append(row)
            for index, stage in enumerate(CASES[case]):
                command = _server_command(args.java); command[2] = "-Xmx4G"
                command[1:1] = ["-Dadvancedrocketrycommunity.resourceRestartCase=" + case,
                                "-Dadvancedrocketrycommunity.resourceRestartStage=" + stage]
                name = f"{case}-{index}-{stage}"
                entry = dict(index=index, stage=stage, command=command); row["starts"].append(entry)
                process = smoke.CapturedProcess(command, session, evidence / (name + ".log"))
                entry["pid"] = process.process.pid
                found = process.wait_for(MARKER, args.startup_timeout)
                entry["live"] = process.lines[found].strip()
                process.command("stop"); entry["exit_code"] = process.finish()
                errors = [line.strip() for line in process.lines if "/ERROR]" in line or "/FATAL]" in line]
                entry["errors"] = errors
                process = None
                disk = capture(session / ("world-W63-" + case), evidence / (name + "-saved"))
                entry["disk"] = verify_disk(disk, case, stage)
                if index and entry["disk"].get("entity_uuid") is not None:
                    if entry["disk"]["entity_uuid"] != row["starts"][0]["disk"]["entity_uuid"]:
                        raise smoke.SmokeError("Restart replaced the original rocket entity")
                if entry["exit_code"] != 0 or errors or "ARCE_W63_FAILURE" in entry["live"]:
                    raise smoke.SmokeError(f"Native {name} failed: {entry['live']}")
                print(f"{name}: PASS (clean stop, live and saved state)", flush=True)
            row["status"] = "PASS"
        report["status"] = "PASS"
        return report
    except Exception as failure:
        report.update(status="FAILED", error=str(failure)); raise
    finally:
        if process is not None:
            process.abort()
        (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        # Retain only compact reproduction evidence; the caller deletes this owned server.
        report_files = sorted(path for path in evidence.rglob("*") if path.is_file())
        (evidence / "inventory.json").write_text(json.dumps([
            dict(file=path.relative_to(evidence).as_posix(), bytes=path.stat().st_size, sha256=smoke.digest_file(path))
            for path in report_files], indent=2), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_server", type=Path)
    for name in ("jar", "session-dir", "evidence-dir"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--case", action="append", choices=CASES)
    parser.add_argument("--startup-timeout", type=float, default=240)
    args = parser.parse_args()
    try:
        run(args); return 0
    except (OSError, ValueError, smoke.SmokeError) as failure:
        print(f"FAIL: {failure}", flush=True); return 1


if __name__ == "__main__":
    raise SystemExit(main())
