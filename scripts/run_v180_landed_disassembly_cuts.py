#!/usr/bin/env python3
"""W85 S2 (P1-F8): landed-disassembly cuts on a packaged dedicated server, each with two native restarts.

Each case assembles, refuels and flies a real rocket to the Moon, keeps the arrival's chunk loaded, optionally saves
the arrival's entity section ("E saved"), and then disassembles it through the gated release-test hook. A gated probe
pauses at one boundary and the harness kills the server (K10 instead stops it normally while the injected removal
refusal holds). Two clean restarts follow, each loading the arrival's chunk, letting ordinary recovery act, and
stopping normally. The raw journal, entity, block and inventory NBT must be the same after both restarts and must
match the case's recorded outcome (W85-DESIGN section 5).
"""
from __future__ import annotations

import argparse
import gzip
import json
from pathlib import Path
import re
import shutil
import struct
import time
import uuid
import zlib

import run_dedicated_server_smoke as smoke
from inspect_celestial_saved_data import NbtReader
from run_v060_flight_server_smoke import EARTH, MOON, LAUNCH_LOG, PHASE_LOG, FlightHarness, _in_dimension, _server_command
from run_v180_escrow_forced_stop import digest

JOURNAL = "advancedrocketrycommunity_rocket_transfers.dat"
MOON_DIR = Path("dimensions") / "advancedrocketrycommunity" / "moon"
PROBE = "advancedrocketrycommunity.landedDisassemblyProbe"
FAILURE = "advancedrocketrycommunity.rocketJournalFailure"
PAUSED = re.compile(r"ARCE_LANDED_DISASSEMBLY_PAUSED boundary=(K\d+)")
REFUSED = re.compile(r"ARCE_RELEASE_JOURNAL_COMMIT_REFUSED mode=LANDED_REMOVAL")
PENDING = re.compile(r"Release-test disassembly pending")
OBSTRUCTED = re.compile(r"ARCE_RELEASE_TEST_DISASSEMBLY_OBSTRUCTED entity=([0-9a-f-]{36}) at=(-?\d+),(-?\d+),(-?\d+)")
RECEIPT = re.compile(r"ARCE_RELEASE_TEST_DISASSEMBLY entity=([0-9a-f-]{36}) logical=([0-9a-f-]{36}) code=([A-Z_]+) "
                     r"blocks=(\d+) rolled_back=(\d+)")
# Footprint of the FlightHarness rocket relative to its snapshot origin (the motor): fuel tank, motor, chest, seat,
# guidance computer (run_v060_flight_server_smoke.disassemble_and_verify).
FOOTPRINT = ((-1, 0, 0), (0, 0, 0), (1, 0, 0), (0, 1, 0), (0, 2, 0))

# The case table is W85-DESIGN section 7 S2 "Required". saved: E's section is saved before the request.
CASES = {
    "K1": dict(properties={PROBE: "LANDED_DISASSEMBLY:K1"}, saved=False, obstruct=False, stop="kill",
               journal=True, rocket=True, outcome="claim kept; a never-saved E is rebuilt; no block"),
    "K2": dict(properties={PROBE: "LANDED_DISASSEMBLY:K2"}, saved=False, obstruct=False, stop="kill",
               journal=False, rocket=False, outcome="recorded loss: removal on disk, E never saved"),
    "K4": dict(properties={PROBE: "LANDED_DISASSEMBLY:K4"}, saved=False, obstruct=True, stop="kill",
               journal=True, rocket=True, outcome="write-back on disk; a never-saved E is rebuilt; no block"),
    "K5": dict(properties={PROBE: "LANDED_DISASSEMBLY:K5"}, saved=True, obstruct=False, stop="kill",
               journal=False, rocket=True, outcome="recorded residual: the saved E is back without T; no block"),
    "K9": dict(properties={FAILURE: "LANDED_REMOVAL"}, saved=True, obstruct=False, stop="kill",
               journal=True, rocket=True, outcome="late removal: no restoration, T written back and kept"),
    "K10": dict(properties={FAILURE: "LANDED_REMOVAL"}, saved=True, obstruct=False, stop="stop",
                journal=True, rocket=True, outcome="normal stop while refused: T in the final capture, E saved"),
}


def uuid_of(value: object) -> str | None:
    """NBT stores a UUID as four signed ints; the release-test log prints the canonical form."""
    if isinstance(value, str):
        return value
    if isinstance(value, list) and len(value) == 4 and all(isinstance(part, int) for part in value):
        number = 0
        for part in value:
            number = (number << 32) | (part & 0xFFFFFFFF)
        return str(uuid.UUID(int=number))
    return None


def nbt_file(path: Path, limit: int) -> dict:
    if not path.is_file() or path.is_symlink() or path.stat().st_size > limit:
        raise smoke.SmokeError(f"Missing or oversized NBT file: {path}")
    with gzip.open(path, "rb") as stream:
        raw = stream.read(limit * 16 + 1)
    if len(raw) > limit * 16:
        raise smoke.SmokeError(f"Oversized decoded NBT file: {path}")
    return NbtReader(raw, max_tags=1_048_576).read_root()


def chunk_record(region: Path, chunk_x: int, chunk_z: int) -> dict | None:
    """One inline region record, or None when the chunk was never stored."""
    if not region.is_file() or region.stat().st_size < 8192:
        return None
    slot = (chunk_x & 31) + (chunk_z & 31) * 32
    with region.open("rb") as stream:
        stream.seek(slot * 4)
        location = struct.unpack(">I", stream.read(4))[0]
        if location == 0:
            return None
        if location >> 8 < 2 or location & 255 == 0:
            raise smoke.SmokeError(f"Corrupt region location in {region}")
        stream.seek((location >> 8) * 4096)
        length, compression = struct.unpack(">IB", stream.read(5))
        if not 1 < length <= 1_048_577 or length + 4 > (location & 255) * 4096 or compression != 2:
            raise smoke.SmokeError(f"Unsupported region record in {region}")
        compressed = stream.read(length - 1)
    decoder = zlib.decompressobj()
    raw = decoder.decompress(compressed, 16_777_217)
    if len(raw) > 16_777_216 or not decoder.eof or decoder.unused_data:
        raise smoke.SmokeError(f"Invalid region record envelope in {region}")
    return NbtReader(raw, max_tags=1_048_576).read_root()


def block_at(chunk: dict | None, x: int, y: int, z: int) -> str:
    if chunk is None:
        return "<chunk-not-stored>"
    for section in chunk.get("sections", []):
        if section.get("Y") != y >> 4:
            continue
        states = section.get("block_states", {})
        palette = states.get("palette", [])
        if not palette:
            return "<no-palette>"
        if len(palette) == 1:
            return palette[0]["Name"]
        bits = max(4, (len(palette) - 1).bit_length())
        per_long = 64 // bits
        index = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15)
        value = (states["data"][index // per_long] & 0xFFFFFFFFFFFFFFFF) >> ((index % per_long) * bits)
        return palette[value & ((1 << bits) - 1)]["Name"]
    return "<section-not-stored>"


def region_path(world: Path, kind: str, chunk_x: int, chunk_z: int) -> Path:
    return world / MOON_DIR / kind / f"r.{chunk_x >> 5}.{chunk_z >> 5}.mca"


def inspect(world: Path, origin: tuple[int, int, int], logical: str, transfer: str) -> dict:
    """The raw state that a restart must reproduce: journal records, Moon rockets, footprint blocks and chests."""
    journal_path = world / "data" / JOURNAL
    records = []
    if journal_path.exists():
        journal = nbt_file(journal_path, 146_866_176)["data"]
        records = [dict(transfer=uuid_of(entry.get("transfer_id")), logical=uuid_of(entry.get("logical_rocket_id")),
                        phase=entry.get("phase"), checksum=entry.get("checksum"),
                        destination=uuid_of(entry.get("destination_entity_id")))
                   for entry in journal.get("transfers", [])]
    rockets = []
    blocks = {}
    block_entities = []
    ox, oy, oz = origin
    chunks = {((ox + dx) >> 4, (oz + dz) >> 4) for dx, _, dz in FOOTPRINT}
    for chunk_x, chunk_z in sorted(chunks):
        entities = chunk_record(region_path(world, "entities", chunk_x, chunk_z), chunk_x, chunk_z)
        for entity in (entities or {}).get("Entities", []):
            data = entity.get("RocketEntityData")
            if isinstance(data, dict):
                rockets.append(dict(uuid=uuid_of(entity.get("UUID")), logical=uuid_of(data.get("assembly_transaction_id")),
                                    snapshot=data.get("snapshot"), fuel=data.get("flight_data", {}).get("fuel"),
                                    state=data.get("flight_data", {}).get("state"), position=entity.get("Pos")))
        terrain = chunk_record(region_path(world, "region", chunk_x, chunk_z), chunk_x, chunk_z)
        for dx, dy, dz in FOOTPRINT:
            x, y, z = ox + dx, oy + dy, oz + dz
            if (x >> 4, z >> 4) == (chunk_x, chunk_z):
                blocks[f"{x},{y},{z}"] = block_at(terrain, x, y, z)
        for entity in (terrain or {}).get("block_entities", []):
            position = (entity.get("x"), entity.get("y"), entity.get("z"))
            if any(position == (ox + dx, oy + dy, oz + dz) for dx, dy, dz in FOOTPRINT):
                block_entities.append(entity)
    files = {}
    for path in sorted([journal_path] + [region_path(world, kind, cx, cz) for cx, cz in chunks
                                         for kind in ("entities", "region")]):
        if path.exists():
            files[path.relative_to(world).as_posix()] = digest(path)
    ours = [record for record in records if record["logical"] == logical]
    return dict(records=records, landed=[record for record in ours if record["transfer"] == transfer],
                rockets=[rocket for rocket in rockets if rocket["logical"] == logical], blocks=blocks,
                block_entities=block_entities, files=files)


def restored(state: dict) -> list[str]:
    """Footprint positions that hold anything but air or the K4 obstruction."""
    return [position for position, name in state["blocks"].items()
            if name not in ("minecraft:air", "minecraft:stone", "<chunk-not-stored>", "<section-not-stored>")]


def comparable(state: dict) -> dict:
    return {key: state[key] for key in ("records", "rockets", "blocks", "block_entities")}


def check_expected(case: str, state: dict, obstruction: str | None) -> None:
    spec = CASES[case]
    if bool(state["landed"]) != spec["journal"] or len(state["landed"]) > 1:
        raise smoke.SmokeError(f"{case}: journal holds T={state['landed']}, expected {spec['journal']}")
    if len(state["rockets"]) != (1 if spec["rocket"] else 0):
        raise smoke.SmokeError(f"{case}: Moon rockets {state['rockets']}, expected {int(spec['rocket'])}")
    if restored(state):
        raise smoke.SmokeError(f"{case}: restored blocks exist with the outcome {spec['outcome']}: {state['blocks']}")
    stones = [position for position, name in state["blocks"].items() if name == "minecraft:stone"]
    if stones and stones != [obstruction]:
        raise smoke.SmokeError(f"{case}: unexpected stone in the footprint {stones}")
    if any(entity.get("id") == "minecraft:chest" for entity in state["block_entities"]):
        raise smoke.SmokeError(f"{case}: a restored chest exists next to the rocket's own inventory")


def start(args, session: Path, evidence: Path, name: str, properties: dict[str, str]) -> smoke.CapturedProcess:
    command = _server_command(args.java)
    command[2] = "-Xmx4G"
    for key, value in sorted(properties.items()):
        command.insert(1, f"-D{key}={value}")
    process = smoke.CapturedProcess(command, session, evidence / f"{name}.log")
    process.wait_for(smoke.READY_MARKER, args.startup_timeout)
    return process


def stop(process: smoke.CapturedProcess) -> int:
    start_at = len(process.lines)
    process.command("save-all flush")
    process.wait_for(smoke.SAVE_MARKER, 90, start_at=start_at)
    process.command("stop")
    return process.finish(180)


def fly_and_land(harness: FlightHarness, process: smoke.CapturedProcess) -> dict:
    entity = harness.assemble(process)["entity"]
    harness.refuel(process, EARTH, entity)
    start_at = len(process.lines)
    process.command(_in_dimension(EARTH, f"arce rocket release-test launch {entity} moon"))
    index = process.wait_for(LAUNCH_LOG, 45, start_at=start_at)
    launch = LAUNCH_LOG.search(process.lines[index])
    if launch.group(7) not in ("JOURNAL_PENDING", "SUCCESS"):
        raise smoke.SmokeError(f"Launch refused: {launch.group(7)}")
    transfer = launch.group(1)
    retained = re.compile(rf"ARCE_TRANSFER_PHASE transfer={re.escape(transfer)} .*event=landed_reservation_retained ")
    end = process.wait_for(retained, 180, start_at=index)
    landing = [PHASE_LOG.search(line) for line in process.lines[index:end + 1]]
    landing = [match for match in landing if match and match.group(1) == transfer and match.group(4) == "landing_complete"]
    if len(landing) != 1:
        raise smoke.SmokeError("The flight did not report exactly one landing")
    arrival = landing[0].group(5)
    report = harness.report(process, MOON, arrival)
    if report["state"] != "LANDED" or report["fuel"] <= 0:
        raise smoke.SmokeError(f"Arrival is not a fueled landed rocket: {report}")
    return dict(transfer=transfer, logical=launch.group(3), entity=arrival, report=report,
                origin=tuple(int(value) for value in report["origin"]))


def load_arrival(process: smoke.CapturedProcess, origin: tuple[int, int, int]) -> None:
    # An operator keeps the arrival's chunk loaded; forceload never saves an entity section.
    marker = f"W92_FORCELOAD_{uuid.uuid4().hex[:8]}"
    process.command(_in_dimension(MOON, f"forceload add {origin[0]} {origin[2]}"))
    FlightHarness.command_marker(process, _in_dimension(MOON, f"say {marker}"), marker, 30)


def restart(args, session: Path, evidence: Path, case: str, number: int, landed: dict) -> dict:
    process = start(args, session, evidence, f"{case}-restart-{number}", {})
    try:
        load_arrival(process, landed["origin"])
        # Let ordinary recovery classify, keep or rebuild the arrival while its chunk is loaded.
        deadline = time.monotonic() + 30
        recovered = re.compile(rf"ARCE_TRANSFER_RECOVERY transfer={re.escape(landed['transfer'])} .*status=RECOVERED")
        while time.monotonic() < deadline:
            if any(recovered.search(line) for line in process.lines):
                break
            time.sleep(1)
        time.sleep(3)
        code = stop(process)
        if code != 0:
            raise smoke.SmokeError(f"{case}: restart {number} exited {code}")
        return dict(exit_code=code, recovered=any(recovered.search(line) for line in process.lines),
                    errors=[line.rstrip() for line in process.lines if "/ERROR]" in line or "/FATAL]" in line])
    except BaseException:
        process.abort()
        raise


def run_case(args, session: Path, evidence: Path, case: str) -> dict:
    spec = CASES[case]
    properties = smoke.expected_server_properties(args.port, True)
    properties.update({"level-name": "world-" + case, "level-type": "minecraft:flat", "max-tick-time": "60000",
                       "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}],
                                                         "biome": "minecraft:plains"})})
    (session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(properties.items())),
                                               encoding="ascii")
    harness = FlightHarness(java=args.java, server=session, port=args.port, expected_version=args.expected_version,
                            startup_timeout=args.startup_timeout)
    row = dict(case=case, outcome=spec["outcome"], saved=spec["saved"], cut=spec["stop"], restarts=[])
    process = start(args, session, evidence, f"{case}-cut", spec["properties"])
    try:
        landed = fly_and_land(harness, process)
        row["landed"] = {key: value for key, value in landed.items() if key != "report"}
        load_arrival(process, landed["origin"])
        if spec["saved"]:
            start_at = len(process.lines)
            process.command("save-all flush")
            process.wait_for(smoke.SAVE_MARKER, 90, start_at=start_at)
        start_at = len(process.lines)
        fuel = landed["report"]["fuel"]
        suffix = " obstruct" if spec["obstruct"] else ""
        process.command(_in_dimension(MOON, f"arce rocket release-test disassemble {landed['entity']} "
                                            f"discard-fuel {fuel}{suffix}"))
        process.wait_for(PENDING, 30, start_at=start_at)
        obstruction = None
        if spec["obstruct"]:
            match = OBSTRUCTED.search(process.lines[process.wait_for(OBSTRUCTED, 30, start_at=start_at)])
            obstruction = f"{match.group(2)},{match.group(3)},{match.group(4)}"
        row["obstruction"] = obstruction
        if case in ("K1", "K2", "K4", "K5"):
            paused = PAUSED.search(process.lines[process.wait_for(PAUSED, 60, start_at=start_at)])
            if paused.group(1) != case:
                raise smoke.SmokeError(f"{case}: paused at {paused.group(1)}")
        elif case == "K9":
            process.wait_for(REFUSED, 60, start_at=start_at)
            receipt = RECEIPT.search(process.lines[process.wait_for(RECEIPT, 60, start_at=start_at)])
            if receipt.group(3) != "REGION_BUSY" or receipt.group(4) != "0":
                raise smoke.SmokeError(f"K9: the late removal restored: {receipt.group(0)}")
            process.wait_for(re.compile(rf"ARCE_TRANSFER_PHASE transfer={re.escape(landed['transfer'])} "
                                        r".*event=landed_reservation_restored "), 60, start_at=start_at)
        else:
            process.wait_for(REFUSED, 60, start_at=start_at)
        row["cut_lines"] = [line.rstrip() for line in process.lines[start_at:]
                            if "ARCE_LANDED" in line or "ARCE_RELEASE" in line or "landed_reservation" in line]
        if spec["stop"] == "kill":
            process.process.kill()
            process.abort()
            row["cut_exit"] = None
        else:
            process.command("stop")
            row["cut_exit"] = process.finish(180)
            if row["cut_exit"] != 0:
                raise smoke.SmokeError(f"{case}: the normal stop exited {row['cut_exit']}")
        process = None
        world = session / ("world-" + case)
        after_cut = inspect(world, landed["origin"], landed["logical"], landed["transfer"])
        row["after_cut"] = after_cut
        if not spec["saved"] and after_cut["rockets"]:
            raise smoke.SmokeError(f"{case}: the never-saved arrival's section was saved before the cut")
        states = []
        for number in (1, 2):
            result = restart(args, session, evidence, case, number, landed)
            state = inspect(world, landed["origin"], landed["logical"], landed["transfer"])
            check_expected(case, state, obstruction)
            states.append(state)
            row["restarts"].append(dict(result, disk=state))
        if comparable(states[0]) != comparable(states[1]):
            raise smoke.SmokeError(f"{case}: the second restart changed the raw state")
        row["status"] = "PASS"
        return row
    finally:
        if process is not None:
            process.abort()


def run(args) -> dict:
    session, evidence = args.session_dir.resolve(), args.evidence_dir.resolve()
    intended = Path("D:/GitHub/ARCE-Task-Evidence/Temp").resolve()
    if session.parent != intended or session.exists() or smoke.is_link_or_junction(session):
        raise smoke.SmokeError("Unsafe or occupied disposable native directory")
    if evidence.exists() or evidence == session or session in evidence.parents:
        raise smoke.SmokeError("Evidence must be new and outside the disposable directory")
    for drive in (Path("C:/"), intended):
        if shutil.disk_usage(drive).free < 10 * 1024**3:
            raise smoke.SmokeError("Native landed cuts require 10 GiB on both drives")
    libraries = args.source_server.resolve() / "libraries"
    if not libraries.is_dir() or smoke.is_link_or_junction(libraries) or any(
            smoke.is_link_or_junction(p) for p in libraries.rglob("*")):
        raise smoke.SmokeError("Unsafe or missing Forge libraries")
    if not (libraries / "net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt").is_file():
        raise smoke.SmokeError("Missing pinned Forge arguments")
    session.mkdir()
    evidence.mkdir(parents=True)
    token = uuid.uuid4().hex
    marker = session / ".arce-w92-landed-owned"
    marker.write_text(token, encoding="ascii")
    report = dict(status="IN_PROGRESS", source_commit=args.source_commit, jar_sha256=digest(args.jar), cases=[],
                  scope="W85 section 7 S2 required cuts; no flush is forced after the request")
    try:
        shutil.copytree(libraries, session / "libraries")
        (session / "mods").mkdir()
        shutil.copy2(args.jar, session / "mods" / args.jar.name)
        args.port = smoke.allocate_port()
        smoke.write_server_configuration(session, args.port, True)
        for case in args.case or list(CASES):
            row = run_case(args, session, evidence, case)
            report["cases"].append(row)
            print(f"{case}: PASS ({row['outcome']})", flush=True)
            shutil.rmtree(session / ("world-" + case), ignore_errors=True)
        report["status"] = "PASS"
        return report
    except Exception as failure:
        report.update(status="FAILED", error=str(failure))
        raise
    finally:
        (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        if (session.parent == intended and not smoke.is_link_or_junction(session) and marker.is_file()
                and not smoke.is_link_or_junction(marker) and marker.read_text(encoding="ascii") == token):
            shutil.rmtree(session)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_server", type=Path)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--session-dir", type=Path, required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--case", action="append", choices=list(CASES))
    parser.add_argument("--expected-version", default="1.20.1-1.8.0-dev")
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
