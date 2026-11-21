"""K04 console-built C6 rocket scenes; no client and no product-code changes.

Generate a new package, then invoke one `run --scene ... --stage ...` per
WORKER-RULES lock acquisition. A source world is never opened by the server.
Readback uses a disposable copy and must match the stopped builder's receipts.
"""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
import re
import shutil
import sys
import time
import zipfile

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.campaign_prep import k16_core as common

NS = common.NS
OW, MOON = common.OW, common.MOON
SIZES = {64: (4, 4, 4), 512: (8, 8, 8), 2048: (8, 16, 16)}
OWNER = "00000000-0000-0000-0000-000000000005"  # RocketCommands.SERVER_CONSOLE_OWNER
OUTPUT = common.EVIDENCE / "final-campaign/c8fa16e16/fixtures/K04"
LOCK = common.EVIDENCE / "locks/heavy-jvm"
ROOT_KEY = "RocketEntityData"


def pos(values):
    return " ".join(map(str, values))


def viewpoint(eye, target):
    dx, dy, dz = (b - a for a, b in zip(eye, target))
    return dict(x=eye[0], y=eye[1], z=eye[2],
                yaw=math.degrees(math.atan2(-dx, dz)),
                pitch=-math.degrees(math.atan2(dy, math.hypot(dx, dz))))


def rocket(size, origin):
    """Exact proposed count, not an assertion that C6 admits the structure."""
    width, height, depth = SIZES[size]
    x, y, z = origin
    blocks = []
    for dy in range(height):
        for dz in range(depth):
            for dx in range(width):
                name = "rocket_motor" if dy == 0 else "rocket_fuel_tank" if dy == 1 else "minecraft:iron_block"
                if (dx, dy, dz) == (0, height - 1, 0):
                    name = "rocket_seat"
                if (dx, dy, dz) == (1, height - 1, 0):
                    name = "guidance_computer"
                blocks.append(dict(pos=[x + dx, y + dy, z + dz], block=name if ":" in name else NS + ":" + name))
    centre = (x + width / 2, y + height / 2, z + depth / 2)
    return dict(expected_blocks=size, origin=list(origin), dimensions=[width, height, depth],
                assembler=[x, y - 1, z], loader=[x - 2, y, z], blocks=blocks,
                seat_blocks=[[x, y + height - 1, z]], relative_seat_anchors=[[0, height - 1, 0]],
                capacity=width * depth * 1000,
                observer=viewpoint((x + width / 2, y + height / 2 + 4, z - 36), centre))


def scene(name):
    if name == "multi":
        rockets = [rocket(n, (256 + i * 32, 100, 256)) for i, n in enumerate(SIZES)]
    else:
        n = int(name.split("-", 1)[0])
        rockets = [rocket(n, (256, 100, 256))]
    return dict(schema_version=1, name=name, dimension=OW, rockets=rockets,
                variant="relaunch" if name.endswith("-relaunch") else "first-use",
                viewport=dict(width=1280, height=720, gui_scale=2, fov=70),
                overview=viewpoint((292, 125, 174), (292, 106, 264)),
                camera_status="GEOMETRIC_PROPOSAL_NOT_VISUALLY_VERIFIED",
                rows=["A12", "I09", "K17"] + (["A10"] if any(r["expected_blocks"] == 2048 for r in rockets) else []),
                grants=["Console places all blocks, pads and assemblers.",
                        "Console claims each Fuel Loader for SERVER_CONSOLE_OWNER and supplies one native fuel cell at a time.",
                        "The normal loader tick consumes every cell; no direct rocket fuel or snapshot edits. "
                        "The previous empty canister is overwritten by the next grant; the final canister is retained.",
                        "release-test report reads state; release-test launch drives only relaunch copies."])


def build_commands(layout):
    commands = ["gamerule doDaylightCycle false", "time set day", "weather clear", "gamerule doMobSpawning false"]
    for r in layout["rockets"]:
        x, y, z = r["origin"]
        w, h, d = r["dimensions"]
        commands.extend([f"fill {x-4} {y-2} {z-4} {x+w+3} {y+h+3} {z+d+3} minecraft:air",
                         f"fill {x-4} {y-2} {z-4} {x+w+3} {y-2} {z+d+3} minecraft:stone",
                         f"setblock {pos(r['assembler'])} {NS}:rocket_assembler"])
        # Compact fills place exactly the same structure as the explicit inventory.
        commands.extend([f"fill {x} {y} {z} {x+w-1} {y+h-1} {z+d-1} minecraft:iron_block",
                         f"fill {x} {y} {z} {x+w-1} {y} {z+d-1} {NS}:rocket_motor",
                         f"fill {x} {y+1} {z} {x+w-1} {y+1} {z+d-1} {NS}:rocket_fuel_tank",
                         f"setblock {x} {y+h-1} {z} {NS}:rocket_seat",
                         f"setblock {x+1} {y+h-1} {z} {NS}:guidance_computer"])
    return commands


def generate(output):
    output.mkdir(parents=True, exist_ok=False)
    for name in [str(n) for n in SIZES] + ["multi"] + [f"{n}-relaunch" for n in SIZES]:
        folder = output / name
        folder.mkdir()
        layout = scene(name)
        (folder / "layout.json").write_bytes(common.json_bytes(layout))
        (folder / "commands.txt").write_text("\n".join(build_commands(layout)) + "\n", encoding="ascii")
    # CargoNativeFixtures belongs to w144NativeJar, not the pinned candidate.
    # Never pass its synthetic stats off as an assembled player rocket.
    boundary = dict(schema_version=1, status="NOT_COVERED", rows=["J08"],
                    construction="src/test/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/CargoNativeFixtures.java",
                    expected_blocks=2048, payloads=128, aggregate_bound=1048576,
                    reason="The benchmark's synthetic snapshot builder is test-only (w144NativeJar). "
                    "Its aggregate=1 MiB specimen is refused because the complete framed snapshot exceeds 1 MiB. "
                    "No boundary world is claimed from C6's release JAR.")
    (output / "payload-boundary.json").write_bytes(common.json_bytes(boundary))


class Console:
    def __init__(self, process):
        self.process = process
        self.commands = []

    def send(self, command):
        start = len(self.process.lines)
        self.commands.append(command)
        self.process.command(command)
        token = f"K04_BARRIER_{len(self.commands)}"
        self.commands.append("say " + token)
        self.process.command("say " + token)
        self.process.wait_for(re.compile(r"\[Server\] " + token + r"\b"), 60, start_at=start)
        lines = self.process.lines[start:]
        if any(any(m in line for m in ("Unknown block", "Unknown item", "Incorrect argument", "Expected whitespace",
                                       "does not have property", "Unknown or incomplete command")) for line in lines):
            raise ValueError("Rejected console command: " + command)
        return lines

    def until(self, command, marker, timeout=120):
        deadline = time.monotonic() + timeout
        while True:
            lines = self.send(command)
            if any(marker in line for line in lines):
                return lines
            if time.monotonic() >= deadline:
                raise ValueError("Readback deadline: " + command)
            time.sleep(.1)


def load_chunks(console, layout):
    for dim in (OW, MOON):
        for r in layout["rockets"]:
            x, _, z = r["origin"]
            console.send(f"execute in {dim} run forceload add {x-16} {z-16} {x+31} {z+31}")
            checks = " ".join(f"if loaded {cx} 100 {cz}" for cx in range(x-16, x+32, 16) for cz in range(z-16, z+32, 16))
            console.until(f"execute in {dim} {checks} run say K04_LOADED", "[Server] K04_LOADED")


def fuel(console, r, entity, dimension=OW, initial_fuel=0):
    at = pos(r["loader"])
    prefix = f"execute in {dimension} run "
    console.send(prefix + f"setblock {at} {NS}:fuel_loader")
    missing = r["capacity"] - initial_fuel
    for cell in range(math.ceil(missing / 500)):
        root = "{schema_version:2,slot_role:1,item:{id:\"" + NS + ":rocket_fuel_cell\",Count:1b},buffered_units:0L,owner_id:" + common.uuid_nbt(OWNER) + "}"
        console.send(prefix + f"data merge block {at} {{arce_fuel_loader:{root}}}")
        if cell == math.ceil(missing / 500) - 1 and missing % 500:
            console.until(f"execute in {dimension} if data entity {entity} "
                          f"{{{ROOT_KEY}:{{flight_data:{{fuel:{{amount:{r['capacity']}L}}}}}}}} run say K04_FULL",
                          "[Server] K04_FULL", 30)
        else:
            console.until(f"execute in {dimension} if data block {at} {{arce_fuel_loader:{{slot_role:2,buffered_units:0L}}}} run say K04_CELL_CONSUMED",
                          "[Server] K04_CELL_CONSUMED", 30)
    # Retain final canister or partially spent cell buffer; never discard it.
    console.send(prefix + f"data get block {at} arce_fuel_loader")


def parse_report(lines):
    from scripts import run_v060_flight_server_smoke as flight
    matches = [flight.REPORT_LOG.search(line) for line in lines]
    match = next((m for m in matches if m), None)
    if match is None:
        raise ValueError("No native flight report")
    names = ("entity", "logical", "snapshot", "dimension", "state", "fuel", "capacity", "passengers", "transfer", "x", "y", "z", "blocks")
    result = dict(zip(names, match.groups()))
    for key in ("fuel", "capacity", "passengers", "x", "y", "z", "blocks"):
        result[key] = int(result[key])
    return result


def read_rocket(console, entity, r, dimension=OW):
    prefix = f"execute in {dimension} run "
    report = parse_report(console.send(prefix + f"arce rocket release-test report {entity}"))
    if report["blocks"] != r["expected_blocks"] or report["fuel"] != r["capacity"] or report["capacity"] != r["capacity"]:
        raise ValueError("Native count/fuel/capacity differs")
    if report["state"] != "FUELED" or report["passengers"] != 0 or report["transfer"] != "none":
        raise ValueError("Rocket is not stationary and fuelled")
    # FUELED is accepted by RocketFlightData.launchReady(); full route legality
    # remains separate and is exercised only by the relaunch variant.
    anchor = f"{ROOT_KEY}.snapshot.passenger_anchors"
    lines = console.send(prefix + f"data get entity {entity} {anchor}")
    expected = r["relative_seat_anchors"][0]
    pattern = r"\[I;\s*" + r",\s*".join(map(str, expected)) + r"\]"
    if not any(re.search(pattern, line) for line in lines):
        raise ValueError("Native seat anchor differs")
    report.update(seat_readback=lines, launch_readiness="FUELED (RocketFlightData.launchReady)",
                  current_seat_block=[report[k] + delta for k, delta in zip(("x", "y", "z"), expected)],
                  seated_rider_feet=[report["x"] + expected[0] + .5, report["y"] + expected[1] + .15,
                                     report["z"] + expected[2] + .5],
                  seat_position_basis="Native anchor plus C6 RocketPassengerPosition offset; no player boarded.")
    return report


def launch_receipt(lines, report, source, destination):
    from scripts import run_v060_flight_server_smoke as flight
    match = next((flight.LAUNCH_LOG.search(line) for line in lines if flight.LAUNCH_LOG.search(line)), None)
    if match is None:
        raise ValueError("Missing native launch receipt")
    if (match.group(2) != report["entity"] or match.group(3) != report["logical"]
            or match.group(4) != source or match.group(5) != destination or match.group(6) != "none"
            or match.group(7) not in ("SUCCESS", "JOURNAL_PENDING")
            or int(match.group(9)) != report["fuel"] or not 0 < int(match.group(8)) <= report["fuel"]):
        raise ValueError("Native launch authority/route/fuel refused: " + match.group(0))
    # C6's normal checked-journal route acknowledges admission asynchronously.
    # Pending is not completion: the caller still requires the actual landing.
    return match


def launch_return(console, report, r):
    from scripts import run_v060_flight_server_smoke as flight
    legs = []
    for source, destination, name in ((OW, MOON, "MOON"), (MOON, OW, "EARTH")):
        start = len(console.process.lines)
        lines = console.send(f"execute in {source} run arce rocket release-test launch {report['entity']} {name}")
        match = launch_receipt(lines, report, source, destination)
        transfer = match.group(1)
        marker = re.compile(r"ARCE_TRANSFER_PHASE transfer=" + re.escape(transfer) + r" .*event=landed_reservation_retained ")
        console.process.wait_for(marker, 30, start_at=start)
        land = next(flight.PHASE_LOG.search(line) for line in console.process.lines[start:]
                    if "event=landing_complete " in line and transfer in line)
        entity = land.group(5)
        report = parse_report(console.send(f"execute in {destination} run arce rocket release-test report {entity}"))
        if report["logical"] != match.group(3) or report["blocks"] != r["expected_blocks"] or report["state"] != "LANDED":
            raise ValueError("Landing identity/count/state mismatch")
        if report["fuel"] != int(match.group(9)) - int(match.group(8)):
            raise ValueError("Landing fuel debit differs")
        legs.append(dict(launch=match.group(0), landed=report.copy()))
        endpoint = dict(r, loader=[report["x"] - 2, report["y"], report["z"]])
        fuel(console, endpoint, entity, destination, report["fuel"])
        report = parse_report(console.send(f"execute in {destination} run arce rocket release-test report {entity}"))
    return report["entity"], legs


def comparable(report):
    return {k: v for k, v in report.items() if k not in ("seat_readback",)}


def validate_candidate(cell, jar):
    if cell["candidate"]["sha256"] != common.JAR_HASH or common.file_hash(jar) != common.JAR_HASH:
        raise ValueError("C6 candidate mismatch")
    with zipfile.ZipFile(jar) as archive:
        if any("CargoNativeFixtures.class" in n for n in archive.namelist()):
            raise ValueError("Unexpected benchmark helper in release candidate")


def run(output, name, stage, cell_path, java, lock_owner):
    """One server run. Caller must use the detached, atomic WORKER-RULES loop."""
    from scripts import run_dedicated_server_smoke as smoke
    from scripts import run_v060_flight_server_smoke as flight
    if not lock_owner.startswith("W434 pid=") or not (LOCK / "owner.txt").read_text(encoding="utf-8-sig").startswith(lock_owner):
        raise ValueError("W434 heavy-JVM ownership required")
    if min(shutil.disk_usage(p).free for p in ("C:/", "D:/")) < 10 * 1024**3:
        raise ValueError("10 GiB required on C: and D:")
    folder = output / name
    if (folder / "SHA256SUMS.txt").exists():
        raise ValueError("Sealed scene is immutable")
    layout = json.loads((folder / "layout.json").read_text(encoding="ascii"))
    if stage not in ("build", "readback"):
        raise ValueError("Unknown stage")
    cell = json.loads(cell_path.read_text(encoding="utf-8-sig"))
    source = Path(cell["server_dir"])
    source_before = common.tree_hashes(source)
    validate_candidate(cell, Path(cell["mods"]["server"][0]["path"]))
    source_world = folder / "world"
    if stage == "build" and source_world.exists():
        raise ValueError("Never overwrite a source world")
    before_world = common.tree_hashes(source_world) if stage == "readback" else None
    copy = folder / (stage + "-server")
    if copy.exists():
        raise ValueError("Occupied disposable server")
    process = console = None
    result = dict(schema_version=1, status="FAILED", stage=stage, candidate_commit=common.CANDIDATE,
                  jar_sha256=common.JAR_HASH, generator_commit=__import__("subprocess").check_output(
                      ["git", "rev-parse", "HEAD"], cwd=common.ROOT, text=True).strip(),
                  generator_sha256=common.file_hash(Path(__file__)), client_started=False)
    try:
        shutil.copytree(source, copy, ignore=shutil.ignore_patterns("world", "logs", "crash-reports"))
        validate_candidate(cell, next((copy / "mods").glob("*.jar")))
        if stage == "readback":
            shutil.copytree(source_world, copy / "world")
        props = smoke.expected_server_properties(smoke.allocate_port(), False)
        props.update({"level-name": "world", "level-seed": "20261006", "level-type": "minecraft:flat",
                      "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}], "biome": "minecraft:plains"}),
                      "view-distance": "6", "simulation-distance": "6", "spawn-protection": "0"})
        (copy / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(props.items())), encoding="ascii")
        args = copy.resolve() / "libraries/net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt"
        command = [str(java), "-Xms512M", "-Xmx3G", "-Dadvancedrocketrycommunity.releaseTestHooks=true", "@" + str(args), "nogui"]
        result.update(command=command, properties=props)
        process = smoke.CapturedProcess(command, copy, folder / (stage + "-console.log"))
        process.wait_for(smoke.READY_MARKER, 240)
        console = Console(process)
        load_chunks(console, layout)
        if stage == "readback":
            baseline = json.loads((folder / "build.json").read_text(encoding="ascii"))
            # The landing allocator, not the generator, chooses return coordinates.
            for previous in baseline["rockets"]:
                actual = dict(rockets=[dict(origin=[previous[k] for k in ("x", "y", "z")])])
                load_chunks(console, actual)
        reports = []
        if stage == "build":
            for command in (folder / "commands.txt").read_text(encoding="ascii").splitlines():
                console.send(command)
            for r in layout["rockets"]:
                start = len(process.lines)
                console.send(f"arce rocket assemble {pos(r['assembler'])}")
                index = process.wait_for(flight.ASSEMBLY_LOG, 90, start_at=start)
                assembled = flight.ASSEMBLY_LOG.search(process.lines[index])
                if int(assembled.group(1)) != r["expected_blocks"]:
                    raise ValueError("C6 assembled a different count")
                entity = assembled.group(3)
                process.wait_for(re.compile(r"ARCE_ROCKET_ENTITY_ACTIVE entity=" + entity + r" operational=true "), 45, start_at=index)
                console.send(f"arce rocket status {pos(r['assembler'])}")
                fuel(console, r, entity)
                report = read_rocket(console, entity, r)
                if layout["variant"] == "relaunch":
                    entity, legs = launch_return(console, report, r)
                    result["flights"] = legs
                    result["additional_grant"] = "Console places an owner-bound native Fuel Loader and grants fuel cells at both landed endpoints; final partial buffers remain saved."
                    report = read_rocket(console, entity, r)
                reports.append(report)
        else:
            for r, previous in zip(layout["rockets"], baseline["rockets"], strict=True):
                report = read_rocket(console, previous["entity"], r)
                if comparable(report) != comparable(previous):
                    raise ValueError("Restart changed native readbacks")
                reports.append(report)
        for dim in (OW, MOON):
            console.send(f"execute in {dim} run forceload remove all")
        console.send("save-all flush")
        console.commands.append("stop")
        process.command("stop")
        code = process.finish(180)
        process = None
        if code != 0:
            raise ValueError(f"Native exit {code}")
        log = (copy / "logs/latest.log").read_text(encoding="utf-8")
        if "/ERROR]" in log or "/FATAL]" in log:
            raise ValueError("Native ERROR/FATAL; retain console log")
        if stage == "build":
            shutil.copytree(copy / "world", source_world)
        result.update(status="BUILT" if stage == "build" else "MATCHED", exit_code=code, rockets=reports,
                      world_files=common.tree_hashes(source_world))
    except BaseException as failure:
        result["error"] = str(failure)
        raise
    finally:
        if process is not None:
            common.abort_owned(process, folder)
        result["commands"] = console.commands if console else []
        result["cell_unchanged"] = common.tree_hashes(source) == source_before
        result["source_world_unchanged"] = before_world is None or common.tree_hashes(source_world) == before_world
        (folder / (stage + ".json")).write_bytes(common.json_bytes(result))
        if copy.exists():
            if copy.resolve().parent != folder.resolve():
                raise ValueError("Unsafe cleanup target")
            shutil.rmtree(copy)
    if not result["cell_unchanged"] or not result["source_world_unchanged"]:
        raise ValueError("Immutable input changed")
    return result


def seal(folder):
    if (folder / "SHA256SUMS.txt").exists():
        raise ValueError("Never overwrite a seal")
    for stage, status in (("build", "BUILT"), ("readback", "MATCHED")):
        receipt = json.loads((folder / (stage + ".json")).read_text(encoding="ascii"))
        if receipt["status"] != status or not receipt["cell_unchanged"] or not receipt["source_world_unchanged"]:
            raise ValueError("Unqualified scene")
        if receipt["world_files"] != common.tree_hashes(folder / "world"):
            raise ValueError("Source world changed since native receipt")
    inventory = common.tree_hashes(folder)
    manifest = dict(schema_version=1, candidate_commit=common.CANDIDATE, jar_sha256=common.JAR_HASH,
                    layout=json.loads((folder / "layout.json").read_text(encoding="ascii")),
                    build=json.loads((folder / "build.json").read_text(encoding="ascii")),
                    readback=json.loads((folder / "readback.json").read_text(encoding="ascii")),
                    inventory_before_manifest=inventory)
    (folder / "manifest.json").write_bytes(common.json_bytes(manifest))
    inventory = common.tree_hashes(folder)
    (folder / "SHA256SUMS.txt").write_text("".join(f"{h}  {p}\n" for p, h in inventory.items()), encoding="ascii")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="action", required=True)
    for action in ("generate", "run", "seal"):
        p = sub.add_parser(action)
        p.add_argument("--output", type=Path, default=OUTPUT)
        if action != "generate":
            p.add_argument("--scene", required=True, choices=[str(n) for n in SIZES] + ["multi"] + [f"{n}-relaunch" for n in SIZES])
        if action == "run":
            p.add_argument("--stage", required=True, choices=("build", "readback"))
            p.add_argument("--cell", type=Path, default=common.CELL)
            p.add_argument("--java", type=Path, default=Path("C:/Program Files/Java/jdk-17.0.7/bin/java.exe"))
            p.add_argument("--lock-owner", required=True)
    args = parser.parse_args()
    if args.action == "generate":
        generate(args.output)
    elif args.action == "run":
        run(args.output, args.scene, args.stage, args.cell, args.java, args.lock_owner)
    else:
        seal(args.output / args.scene)


if __name__ == "__main__":
    main()
