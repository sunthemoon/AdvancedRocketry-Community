"""Deterministic K05 scene inputs and native idle-scene build/restart readback.

Generate never launches Minecraft. Run one `run` invocation under the detached
WORKER-RULES lock launcher, then release and yield >=90 seconds. Unsupported
variants remain preparation plans, not worlds or claims of campaign coverage.
"""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
from uuid import UUID, uuid5
import zipfile

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.campaign_prep import k16_core as common

NS = common.NS
OUTPUT = common.EVIDENCE / "final-campaign/c8fa16e16/fixtures/K05"
LOCK = common.EVIDENCE / "locks/heavy-jvm"
KINDS = ("lathe", "arc_furnace", "cutting_machine", "crystallizer", "chemical_reactor",
         "precision_laser_etcher", "centrifuge", "observatory", "astrobody_data_processor")
RECIPES = ("lathe_iron_rod", "arc_furnace_titanium", "cutting_silicon_wafer", "crystallizing_dilithium",
           "chemical_reacting_rocket_fuel", "laser_etching_basic_circuit_plate", "centrifuging_enriched_lava")
OWNERS = ("43500000-0000-4000-8000-000000000001", "43500000-0000-4000-8000-000000000002")
SCENES = ("a100-idle", "b144-idle", "b144-half-active", "b144-unload-restart",
          "damage-structure", "damage-controller", "damage-explosion", "damage-piston",
          "two-owners", "declared-limits")
RUNNABLE = ("a100-idle", "b144-idle", "two-owners")
JAVA_ROOT = common.JAVA_ROOT
RUNTIME = JAVA_ROOT / "machine/classic/runtime"
ROWS = ("C01", "C02", "C03", "C04", "C06", "C08", "I02", "I03", "I06", "I08")


def pos(values):
    return " ".join(map(str, values))


def blockpos_long(p):
    value = ((p[0] & 0x3FFFFFF) << 38) | ((p[2] & 0x3FFFFFF) << 12) | (p[1] & 0xFFF)
    return value if value < 2**63 else value - 2**64


def viewpoint(eye, target):
    dx, dy, dz = (b - a for a, b in zip(eye, target))
    return dict(x=eye[0], y=eye[1], z=eye[2], yaw=math.degrees(math.atan2(-dx, dz)),
                pitch=-math.degrees(math.atan2(dy, math.hypot(dx, dz))))


def footprint(jar, kind, origin):
    """W70's NORTH pattern, including its three explicit optional-port choices."""
    path = f"data/{NS}/machine_patterns/{kind}.json"
    raw = jar.read(path)
    pattern = json.loads(raw)
    anchor = pattern["controller_anchor"]
    tags = {"motors": NS + ":motor", "coils": NS + ":copper_coil",
            "arc_furnace_shell_parts": NS + ":blast_brick", "observatory_shell_parts": NS + ":machine_casing",
            "slabs": "minecraft:stone_slab", "machine_clearance": "minecraft:air"}
    cells = []
    for entry in pattern["cells"]:
        local = tuple(entry[k] for k in "xyz")
        at = [origin[i] + local[i] - anchor[k] for i, k in enumerate("xyz")]
        match = entry["matcher"]
        role = None
        if match.get("controller"):
            block = NS + ":" + kind + "[facing=north]"
        elif "optional" in match or match.get("air"):
            block = "minecraft:air"
        elif "block" in match:
            block = match["block"]
        elif "tag" in match:
            block = tags[match["tag"].split(":")[1]]
        elif "port" in match:
            role = match["port"]
            block = NS + ":" + ("power_input_plug" if role == "energy_input" else role + "_hatch")
        else:
            raise ValueError(f"Unsupported matcher: {match}")
        overrides = {("arc_furnace", (1, 1, 0)): "item_input",
                     ("arc_furnace", (3, 1, 0)): "item_output",
                     ("observatory", (1, 0, 0)): "energy_input"}
        if (kind, local) in overrides:
            role = overrides[kind, local]
            block = NS + ":" + ("power_input_plug" if role == "energy_input" else role + "_hatch")
        if [at[0] // 16, at[2] // 16] != [origin[0] // 16, origin[2] // 16]:
            raise ValueError("W70 footprint crossed its chunk")
        cells.append(dict(pos=at, local=list(local), block=block, role=role,
                          controller=bool(match.get("controller"))))
    return cells, dict(path=path, sha256=common.digest(raw))


def layout(jar, name, owners=OWNERS):
    if name not in SCENES:
        raise ValueError("Unknown scene")
    if len(owners) != 2 or len(set(owners)) != 2:
        raise ValueError("Two distinct owner UUIDs required")
    owners = tuple(str(UUID(value)) for value in owners)
    machines = []
    # Same coordinates as W70, ordered copy-major, then W70 kind order. This
    # gives A's first hundred 12 lathes and 11 of every other kind.
    for copy in range(16):
        for index, kind in enumerate(KINDS):
            cell = index * 4 + copy // 4
            chunk = [1600 + cell % 6, 1600 + cell // 6]
            origin = [chunk[0] * 16 + 3 + copy % 2 * 8, 308,
                      chunk[1] * 16 + 2 + copy // 2 % 2 * 8]
            cells, pattern = footprint(jar, kind, origin)
            machines.append(dict(id=f"{kind}-{copy:02}", kind=kind, copy=copy, pos=origin, chunk=chunk,
                                 facing="north", mirrored=False, cells=cells, pattern=pattern,
                                 active_selection=copy % 2 == 0,
                                 recipe=RECIPES[index] if index < 7 else "ground-survey" if index == 7 else "ground-analysis",
                                 owner=(owners[copy % 2] if name == "two-owners" else
                                        str(uuid5(UUID(owners[0]), f"owner-{kind}-{copy}"))) if index >= 7 else None,
                                 owner_policy="Research owner only" if index >= 7 else "No per-player owner field; world interaction policy"))
    if name == "a100-idle":
        machines = machines[:100]
    views = [viewpoint((25648, 318, 25572), (25648, 308, 25648)),
             viewpoint((25722, 317, 25648), (25648, 308, 25648))]
    return dict(schema_version=1, name=name, dimension=common.OW, machines=machines,
                chunks=sorted({tuple(m["chunk"]) for m in machines}), observer_standpoints=views,
                viewport_cells=[dict(width=1280, height=720, requested_gui_scale=s, fov=70) for s in (2, 3)],
                views_status="GEOMETRIC_PROPOSAL_NOT_SEEN_ON_CLIENT",
                owner_uuids=sorted({m["owner"] for m in machines if m["owner"]}),
                owner_accounts="Disposable UUID parameters, not O5 authenticated accounts",
                preparation_status="NATIVE_BUILD_PENDING" if name in RUNNABLE else "PLAN_ONLY_NOT_BUILT",
                grants="Console-built blocks and research identities only; idle scenes have no input or energy grants.",
                client_started=False, required_gates_approved=False)


def build_commands(scene):
    commands = ["gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false",
                "time set 18000", "weather clear", "setworldspawn 0 64 0"]
    for x, z in scene["chunks"]:
        commands.append(f"fill {x*16} 305 {z*16} {x*16+15} 305 {z*16+15} minecraft:smooth_stone")
    for m in scene["machines"]:
        cells = sorted(m["cells"], key=lambda c: c["controller"])
        for cell in cells:
            block = cell["block"]
            if cell["controller"] and m["owner"]:
                root = "arce_observatory" if m["kind"] == "observatory" else "arce_astrobody_data_processor"
                device = str(uuid5(UUID(OWNERS[0]), m["id"]))
                block += ("{" + root + ":{schema_version:1,device_id:" + common.uuid_nbt(device)
                          + ",owner_id:" + common.uuid_nbt(m["owner"]) + ',home_level:"' + common.OW
                          + '",home_pos:' + str(blockpos_long(m["pos"])) + "L,collision:0b}}")
            commands.append(f"setblock {pos(cell['pos'])} {block}")
    return commands


def variant_plan(scene):
    name = scene["name"]
    chosen = [next(m for m in scene["machines"] if m["kind"] == k) for k in KINDS]
    result = dict(executed=False, ledger_rule="Before equals after plus drops, with recipe conversions and FE payments reconciled per machine.")
    if name.startswith("damage-"):
        result.update(targets=[], limitation="No damage or resource/drop conservation is claimed by these proposed commands.")
        for m in chosen:
            solid = next(c for c in m["cells"] if not c["controller"] and c["block"] != "minecraft:air" and c["role"] is None)
            target = m["pos"] if name == "damage-controller" else solid["pos"]
            command = f"setblock {pos(target)} minecraft:air destroy"
            if name == "damage-explosion":
                # A standard primed TNT, not an invented mod explosion strength.
                command = f"summon minecraft:tnt {target[0]+.5} {target[1]+1} {target[2]+.5} {{Fuse:40s}}"
            if name == "damage-piston":
                command = None  # Needs a collision-safe, read-back piston rig; not a fake player break.
            result["targets"].append(dict(machine=m["id"], target=target, proposed_command=command,
                                           before=None, after=None, drops=None, outcome="NOT_OBSERVED"))
    elif name in ("b144-half-active", "b144-unload-restart"):
        result.update(selected=[m["id"] for m in scene["machines"] if m["active_selection"]],
                      blocker="C6 ResearchReleaseCommands targets only (8+16*i,300,8), i=0..2, under researchVerificationPaused. "
                              "It cannot start/read the 16 selected remote research jobs. No arbitrary SavedData or job-progress injection.",
                      unload_witness="NOT_AVAILABLE: W70's ChunkEvent witness lives inside its GameTest, which must not run/export here.")
    return result


def declared_fields(root=common.ROOT):
    """Display projection bounds are not misrepresented as server input limits."""
    paths = [RUNTIME / f for f in ("ClassicControllerMenu.java", "ResearchControllerMenu.java", "ClassicCheckpoint.java",
                                  "ClassicHatchBlockEntity.java", "ResearchReleaseCommands.java")]
    sources = {p.as_posix(): common.file_hash(root / p) for p in paths}
    fields = []
    def add(field, maximum, source, mechanism):
        fields.append(dict(field=field, at_limit=maximum, one_past=maximum + 1 if maximum is not None else None,
                           source=(RUNTIME / source).as_posix(), mechanism=mechanism, executed=False))
    menu = (root / RUNTIME / "ClassicControllerMenu.java").read_text(encoding="utf-8")
    for method in ("progress", "duration", "energy"):
        match = re.search(r"int " + method + r"\(\) \{ return integer\(\d, ([\d_]+)\);", menu)
        if not match:
            raise ValueError("C6 classic display-bound source changed")
        add("classic." + method, int(match[1].replace("_", "")), "ClassicControllerMenu.java", "Client projection returns zero above bound; not a C2S setter")
    research = (root / RUNTIME / "ResearchControllerMenu.java").read_text(encoding="utf-8")
    for field, anchor, bound in (("progress", "wide(data, 2, 72_000)", 72000),
                                 ("duration", "wide(data, 4, 72_000)", 72000),
                                 ("energy", "MAX_FE = 640_000", 640000),
                                 ("required_energy", "data.get(8) <= 10_000", 10000),
                                 ("selection_index", "Math.min(15", 15), ("selection_count", "Math.min(16", 16)):
        if anchor not in research:
            raise ValueError("C6 research display-bound source changed")
        add("research." + field, bound, "ResearchControllerMenu.java", "Read-only display sanitization/clamping; no server field setter")
    for field in ("classic.title", "classic.reason", "research.title", "research.status", "research.phase",
                  "research.analysis", "research.owner", "research.diagnostic_role"):
        add(field, None, "ResearchControllerMenu.java" if field.startswith("research") else "ClassicControllerMenu.java",
            "Fixed translated title/enum label, not a user-editable name; no declared text-length limit")
    add("research.diagnostic_coordinates", None, "ResearchControllerMenu.java", "Signed-short display cast, not a declared coordinate admission limit; no test value invented")
    return dict(source_sha256=sources, fields=fields,
                distance=dict(squared_maximum=64, radius_blocks=8, source=(RUNTIME / "ClassicControllerMenu.java").as_posix(),
                              source_expression="distanceToSqr(controller centre) <= 64",
                              example_controller=[25603, 308, 25602], inside=[25603.5, 306, 25609.5],
                              outside=[25603.5, 306, 25611.5], coordinate_basis="Player feet on y=305 platform, not eye height; all other policy checks still apply",
                              intent_sent=False),
                limitation="Not a complete C06 fixture: representative Electrolyzer/Rolling/Precision and live extreme-value refusals remain unbuilt. "
                           "No read-only projection is relabelled as a server refusal; no arbitrary corrupt NBT is substituted.")


def generate(output, jar_path=common.JAR, owners=OWNERS):
    if common.file_hash(jar_path) != common.JAR_HASH:
        raise ValueError("Pinned C6 JAR required")
    fields = declared_fields()
    with zipfile.ZipFile(jar_path) as jar:
        scenes = [layout(jar, name, owners) for name in SCENES]
        recipes = {name: json.loads(jar.read(f"data/{NS}/recipes/{name}.json")) for name in RECIPES}
    output.mkdir(parents=True, exist_ok=False)
    for scene in scenes:
        folder = output / scene["name"]
        folder.mkdir()
        (folder / "layout.json").write_bytes(common.json_bytes(scene))
        (folder / "commands.txt").write_text("\n".join(build_commands(scene)) + "\n", encoding="ascii", newline="\n")
        (folder / "variant-plan.json").write_bytes(common.json_bytes(variant_plan(scene)))
    manifest = dict(schema_version=1, package="K05", candidate_commit=common.CANDIDATE, jar_sha256=common.JAR_HASH,
                    scenes=list(SCENES), native_idle_builder=list(RUNNABLE), recipes=recipes, declared_fields=fields,
                    requested_rows=list(ROWS), status="PARTIAL_PREPARATION_NOT_CAMPAIGN_ACCEPTANCE",
                    limitations=["No half-active or active unload fixture is delivered until all 72 jobs are natively proven.",
                                 "Damage variants are unexecuted plans without seeded resource/drop ledgers or piston rigs.",
                                 "Two research-owner UUIDs do not prove C03 Precision shared-container permissions.",
                                 "Not all delivered v1.8 kinds/facings, representative v1.2 machines or high-risk assets are placed.",
                                 "No client, V1/V2, REF performance, O5 identity or Gate acceptance is claimed."])
    (output / "manifest.json").write_bytes(common.json_bytes(manifest))
    return manifest


def parse_snbt(raw):
    """Bounded, strict console-NBT reader; preserve scalar spellings and array types."""
    if len(raw.encode("utf-8")) > 262144:
        raise ValueError("Console NBT exceeds bound")
    tokens = re.findall(r'''\s*("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|[{}\[\]:,;]|[^\s{}\[\]:,;]+)''', raw)
    i = 0
    def read(depth=0):
        nonlocal i
        if depth > 32 or i >= len(tokens):
            raise ValueError("Malformed/overdeep SNBT")
        token = tokens[i]
        i += 1
        if token == "{":
            result = {}
            while i < len(tokens) and tokens[i] != "}":
                key = tokens[i]
                i += 1
                if key.startswith(('"', "'")):
                    key = key[1:-1]
                if i >= len(tokens) or tokens[i] != ":" or key in result:
                    raise ValueError("Malformed compound")
                i += 1
                result[key] = read(depth + 1)
                if i < len(tokens) and tokens[i] == ",":
                    i += 1
                elif i >= len(tokens) or tokens[i] != "}":
                    raise ValueError("Missing compound separator")
            if i >= len(tokens):
                raise ValueError("Unclosed compound")
            i += 1
            return result
        if token == "[":
            kind, values = "list", []
            if i + 1 < len(tokens) and tokens[i] in ("B", "I", "L") and tokens[i + 1] == ";":
                kind = tokens[i]
                i += 2
            while i < len(tokens) and tokens[i] != "]":
                values.append(read(depth + 1))
                if i < len(tokens) and tokens[i] == ",":
                    i += 1
                elif i >= len(tokens) or tokens[i] != "]":
                    raise ValueError("Missing list separator")
            if i >= len(tokens):
                raise ValueError("Unclosed list")
            i += 1
            return {"array_type": kind, "values": values}
        if token in ("}", "]", ":", ",", ";"):
            raise ValueError("Unexpected SNBT delimiter")
        return token
    result = read()
    if i != len(tokens):
        raise ValueError("Trailing SNBT")
    return result


class Console:
    def __init__(self, process):
        self.process, self.commands = process, []

    def send(self, commands):
        if isinstance(commands, str):
            commands = [commands]
        start = len(self.process.lines)
        self.commands.extend(commands)
        for command in commands:
            self.process.command(command)
        token = f"K05_BARRIER_{len(self.commands)}"
        self.commands.append("say " + token)
        self.process.command("say " + token)
        self.process.wait_for(re.compile(r"\[Server\] " + token + r"$"), 60, start_at=start)
        lines = self.process.lines[start:]
        if any(any(bad in line for bad in ("Unknown block", "Unknown item", "Incorrect argument", "Expected whitespace",
                                           "does not have property", "Cannot access blocks outside of the world")) for line in lines):
            raise ValueError("Rejected console command; see console log")
        return lines

    def nbt(self, at):
        lines = self.send(f"data get block {pos(at)}")
        values = [line.split("has the following block data: ", 1)[1] for line in lines if "has the following block data: " in line]
        if len(values) != 1:
            raise ValueError(f"Missing/ambiguous block-entity readback at {at}")
        return parse_snbt(values[0])


def load_chunks(console, scene):
    for x, z in scene["chunks"]:
        console.send(f"forceload add {x*16} {z*16}")
    deadline = time.monotonic() + 120
    for x, z in scene["chunks"]:
        token = f"K05_CHUNK_{x}_{z}"
        while not any("[Server] " + token in line for line in console.send(
                f"execute if loaded {x*16} 308 {z*16} run say {token}")):
            if time.monotonic() >= deadline:
                raise ValueError("Fixture chunks not loaded")
            time.sleep(.25)


def idle_ledger(console, machine):
    root = console.nbt(machine["pos"])
    checkpoint = root["arce_classic_machine"]
    if checkpoint["structure"]["formation_state"] != '"FORMED"' or checkpoint["job"] != {} or checkpoint["batch_ordinal"] != "0L":
        raise ValueError(f"Not formed/idle: {machine['id']}")
    resources = root["arce_classic_resources"]
    for bank in resources["banks"]["values"]:
        if "items" in bank and any(bank["items"]["values"]) or "fluid" in bank and bank["fluid"]:
            raise ValueError("Idle machine has resources")
    hatches = []
    for cell in machine["cells"]:
        if cell["role"]:
            hatch = console.nbt(cell["pos"])
            if hatch["arce_classic_hatch"]["energy"] != "0":
                raise ValueError("Idle power is nonzero")
            hatches.append(dict(pos=cell["pos"], nbt=hatch))
    if machine["owner"]:
        key = "arce_observatory" if machine["kind"] == "observatory" else "arce_astrobody_data_processor"
        expected = parse_snbt(common.uuid_nbt(machine["owner"]))
        if root[key]["owner_id"] != expected or root[key]["collision"] != "0b":
            raise ValueError("Research identity differs")
    return dict(machine=machine["id"], controller=root, hatches=hatches)


def run(output, name, stage, cell_path, java, lock_owner):
    """One native idle build or readback, never acquires/releases the caller's lock."""
    from scripts import run_dedicated_server_smoke as smoke
    if name not in RUNNABLE or stage not in ("build", "readback"):
        raise ValueError("Variant has no native implementation; do not substitute an idle scene")
    if not lock_owner.startswith("W435 pid=") or not (LOCK / "owner.txt").read_text(encoding="utf-8-sig").startswith(lock_owner):
        raise ValueError("W435 heavy-JVM ownership required")
    if min(shutil.disk_usage(p).free for p in ("C:/", "D:/")) < 10 * 1024**3:
        raise ValueError("10 GiB required on C: and D:")
    folder = output / name
    if (folder / "SHA256SUMS.txt").exists() or (folder / (stage + ".json")).exists():
        raise ValueError("Do not overwrite a sealed fixture or an existing run")
    scene = json.loads((folder / "layout.json").read_text(encoding="ascii"))
    cell = json.loads(cell_path.read_text(encoding="utf-8-sig"))
    source = Path(cell["server_dir"])
    source_before = common.tree_hashes(source)
    if cell["candidate"]["sha256"] != common.JAR_HASH or cell["forge"] != "47.4.10" or cell["jei"]:
        raise ValueError("Pinned C6 baseline/no-JEI cell required")
    world = folder / "world"
    if stage == "build" and world.exists() or stage == "readback" and not world.is_dir():
        raise ValueError("Missing readback source or occupied build world")
    world_before = common.tree_hashes(world) if stage == "readback" else None
    copy = folder / (stage + "-server")
    if copy.exists():
        raise ValueError("Occupied disposable server")
    result = dict(schema_version=1, status="FAILED", stage=stage, candidate_commit=common.CANDIDATE,
                  jar_sha256=common.JAR_HASH, generator_commit=subprocess.check_output(
                      ["git", "rev-parse", "HEAD"], cwd=common.ROOT, text=True).strip(),
                  generator_sha256=common.file_hash(Path(__file__)), client_started=False)
    process = console = None
    try:
        shutil.copytree(source, copy, ignore=shutil.ignore_patterns("world", "logs", "crash-reports"))
        installed = list((copy / "mods").glob("*.jar"))
        if len(installed) != 1 or common.file_hash(installed[0]) != common.JAR_HASH:
            raise ValueError("Copied server candidate mismatch")
        if stage == "readback":
            shutil.copytree(world, copy / "world")
        props = smoke.expected_server_properties(smoke.allocate_port(), False)
        props.update({"level-name": "world", "level-seed": "20261006", "level-type": "minecraft:flat",
                      "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}], "biome": "minecraft:plains"}),
                      "view-distance": "2", "simulation-distance": "2", "spawn-protection": "0"})
        (copy / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(props.items())), encoding="ascii")
        args = copy.resolve() / "libraries/net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt"
        command = [str(java), "-Xms512M", "-Xmx3G", "@" + str(args), "nogui"]
        result.update(command=command, properties=props)
        process = smoke.CapturedProcess(command, copy, folder / (stage + "-console.log"))
        process.wait_for(smoke.READY_MARKER, 240)
        console = Console(process)
        load_chunks(console, scene)
        if stage == "build":
            commands = (folder / "commands.txt").read_text(encoding="ascii").splitlines()
            for offset in range(0, len(commands), 32):
                console.send(commands[offset:offset + 32])
        # Native validation must settle, not just trust the generator's layout.
        deadline = time.monotonic() + 120
        while True:
            lines = console.send(f"data get block {pos(scene['machines'][-1]['pos'])} arce_classic_machine.structure.formation_state")
            if any('"FORMED"' in line for line in lines):
                break
            if time.monotonic() >= deadline:
                raise ValueError("Formation did not settle")
            time.sleep(.25)
        ledgers = [idle_ledger(console, m) for m in scene["machines"]]
        if stage == "readback":
            previous = json.loads((folder / "build.json").read_text(encoding="ascii"))
            if ledgers != previous["ledgers"]:
                raise ValueError("Restart changed per-machine ledgers")
        console.send("forceload remove all")
        console.send("save-all flush")
        console.commands.append("stop")
        process.command("stop")
        code = process.finish(180)
        process = None
        if code != 0:
            raise ValueError(f"Native server exit {code}")
        log = (copy / "logs/latest.log").read_text(encoding="utf-8")
        if "/ERROR]" in log or "/FATAL]" in log:
            raise ValueError("Native ERROR/FATAL; see console log")
        if stage == "build":
            shutil.copytree(copy / "world", world)
        result.update(status="BUILT_IDLE" if stage == "build" else "MATCHED", exit_code=code, ledgers=ledgers,
                      world_files=common.tree_hashes(world))
    except BaseException as failure:
        result["error"] = str(failure)
        raise
    finally:
        if process is not None:
            common.abort_owned(process, folder)
        result["commands"] = console.commands if console else []
        result["cell_unchanged"] = common.tree_hashes(source) == source_before
        result["source_world_unchanged"] = world_before is None or common.tree_hashes(world) == world_before
        (folder / (stage + ".json")).write_bytes(common.json_bytes(result))
        if copy.exists():
            if copy.resolve().parent != folder.resolve() or copy.name not in ("build-server", "readback-server"):
                raise ValueError("Unsafe cleanup target")
            shutil.rmtree(copy)
    if not result["cell_unchanged"] or not result["source_world_unchanged"]:
        raise ValueError("Immutable source changed")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("generate", "run", "seal"))
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--candidate-jar", type=Path, default=common.JAR)
    parser.add_argument("--owners", nargs=2, default=OWNERS)
    parser.add_argument("--scene", choices=SCENES)
    parser.add_argument("--stage", choices=("build", "readback"))
    parser.add_argument("--cell", type=Path, default=common.CELL)
    parser.add_argument("--java", type=Path, default=Path("C:/Program Files/Java/jdk-17.0.7/bin/java.exe"))
    parser.add_argument("--lock-owner", default="")
    args = parser.parse_args()
    if args.action == "generate":
        generate(args.output, args.candidate_jar, args.owners)
    elif args.action == "run":
        run(args.output, args.scene, args.stage, args.cell, args.java, args.lock_owner)
    else:
        if args.scene not in RUNNABLE:
            raise ValueError("Only native idle scenes may be sealed")
        folder = args.output / args.scene
        readback = json.loads((folder / "readback.json").read_text(encoding="ascii"))
        if readback["status"] != "MATCHED" or common.tree_hashes(folder / "world") != readback["world_files"]:
            raise ValueError("Unverified/changed source world")
        if (folder / "manifest.json").exists() or (folder / "SHA256SUMS.txt").exists():
            raise ValueError("Do not overwrite a scene manifest or sealed evidence")
        scene_manifest = dict(schema_version=1, candidate_commit=common.CANDIDATE, jar_sha256=common.JAR_HASH,
                              scene=args.scene, kind="NATIVE_IDLE_SCENE_ONLY", source_world="world",
                              layout="layout.json", grants="commands.txt", native_build="build.json",
                              native_restart="readback.json", world_files=readback["world_files"],
                              formation_and_idle="C6 block-entity readbacks, not generator assertions",
                              client_started=False, required_gates_approved=False)
        (folder / "manifest.json").write_bytes(common.json_bytes(scene_manifest))
        common.seal(folder)
    print(f"K05 {args.action}: {args.output}")


if __name__ == "__main__":
    main()
