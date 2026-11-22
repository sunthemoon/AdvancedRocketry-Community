"""Copy-only K14 preparation on C6. No clients, save editors or forced chunks.

Generate outside Git, then run each native stage under the WORKER-RULES lock.
Only Minecraft writes worlds. Partial inputs never qualify a checklist row.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import time
import zipfile

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.campaign_prep import k03_chain as chain
from scripts.campaign_prep import k04_rockets as rockets
from scripts.campaign_prep import k05_machines as machines
from scripts.campaign_prep import k16_core as common

NS, OW, SPACE, MOON = common.NS, common.OW, common.SPACE, common.MOON
FIXTURES = common.OUTPUT.parent
OUTPUT = FIXTURES / "K14"
HOSTS = ("moon", "space", "mars", "venus", "tau_ceti_f", "tau_ceti_g")
FACINGS = ("north", "east", "south", "west")
SCENES = ("core-scenes", "prior-sleep")
STAGES = ("build", "reopen-1", "reopen-2")
JAVA = Path("C:/Program Files/Java/jdk-17.0.7/bin/java.exe")
FREEZE = "docs/work/v1.8.0-c18a-sleep/DIMENSION-SPAWN-TIME-PROPOSAL-29.zip"
SPAWN_FIELDS = ("SpawnDimension", "SpawnX", "SpawnY", "SpawnZ", "SpawnAngle", "SpawnForced")


def write(path, value):
    path.write_bytes(common.json_bytes(value))


def verify_parent(folder, world_name=None):
    """Verify listed members AND complete world coverage, without changing a seal."""
    chain.verify_seal(folder)
    seen = {line.split("  ", 1)[1] for line in (folder / "SHA256SUMS.txt").read_text().splitlines()}
    if world_name:
        actual = common.tree_hashes(folder / world_name)
        chain.require(bool(actual) and all(world_name + "/" + p in seen for p in actual), "Unsealed world member")
    return dict(path=str(folder), checksum_list_sha256=common.file_hash(folder / "SHA256SUMS.txt"),
                world_name=world_name)


def rotate(offset, turn):
    x, y, z = offset
    for _ in range(turn):
        x, z = -z, x
    return [x, y, z]


def pattern(jar, kind, at, turn=0):
    """Transform C6's tracked pattern, not a legacy hull's visual coordinates."""
    commands, _ = common.pattern_commands(jar, kind, at)
    cells = []
    for command in commands:
        words = command.split()
        p = list(map(int, words[5:8]))
        block = words[8]
        offset = rotate([p[i] - at[i] for i in range(3)], turn)
        p = [at[i] + offset[i] for i in range(3)]
        block = block.replace("facing=north", "facing=" + FACINGS[turn])
        cells.append(dict(pos=p, block=block))
    if kind == "observatory":
        plug = [at[i] + rotate([-1, -1, 0], turn)[i] for i in range(3)]
        next(c for c in cells if c["pos"] == plug)["block"] = NS + ":power_input_plug"
    raw = jar.read(f"data/{NS}/machine_patterns/{kind}.json")
    return dict(kind=kind, pos=list(at), facing=FACINGS[turn], cells=cells,
                pattern_sha256=common.digest(raw), dimension=OW,
                power_plugs=[c["pos"] for c in cells if c["block"] == NS + ":power_input_plug"],
                glass=[c["pos"] for c in cells if c["block"] == "minecraft:glass"])


def core_layout(jar):
    devices = []
    for index, kind in enumerate(("observatory", "astrobody_data_processor", "precision_assembler",
                                  "rolling_machine", "precision_laser_etcher", "railgun",
                                  "black_hole_generator", "orbital_laser_drill", "elevator_anchor")):
        turns = (0,) if kind == "elevator_anchor" else range(4)
        for turn in turns:
            devices.append(pattern(jar, kind, [-112 + turn * 24, 181, -112 + index * 24], turn))
    # Two physical copies support mode changes without changing any rocket NBT.
    tall = []
    for index, mode in enumerate(("survival", "creative")):
        x, y, z = 48 + index * 24, 188, -64
        blocks = ["rocket_motor"] + ["rocket_fuel_tank"] * 7 + ["guidance_computer", "rocket_seat"]
        tall.append(dict(mode=mode, origin=[x, y, z], assembler=[x, y - 1, z],
                         blocks=[dict(pos=[x, y + dy, z], block=NS + ":" + block) for dy, block in enumerate(blocks)],
                         seats=[[0, 9, 0]], expected_blocks=10, section_margin=y % 16,
                         observer=[x + .5, y, z - 20], client_seat_assignments="A top seat; B ground observer"))
    return dict(devices=devices, rockets=tall, client_stands={"A": [-120.5, 181, -120.5],
                                                            "B": [-118.5, 181, -120.5]})


def commands_for(layout):
    commands = []
    for device in layout["devices"]:
        # Two blocks below the controller leave the below-anchor cells intact.
        cells = device["cells"]
        lo = [min(c["pos"][i] for c in cells) - 2 for i in range(3)]
        hi = [max(c["pos"][i] for c in cells) + 2 for i in range(3)]
        commands.append(f"fill {lo[0]} {lo[1]} {lo[2]} {hi[0]} {lo[1]} {hi[2]} minecraft:stone")
        for cell in cells:
            commands.append(f"setblock {rockets.pos(cell['pos'])} {cell['block']}")
        if device["kind"] in ("observatory", "astrobody_data_processor"):
            device["power_sources"] = []
            for plug in device["power_plugs"]:
                generator = [plug[0], plug[1] - 1, plug[2]]
                hopper = [plug[0] + 1, plug[1] - 1, plug[2]]
                device["power_sources"].append(dict(kind="combustion_generator", pos=generator, hopper=hopper,
                                                    grant="one vanilla coal via hopper; native 40 FE/t", reserve_fe=30000))
                commands += [f"setblock {rockets.pos(generator)} {NS}:combustion_generator",
                             f"setblock {rockets.pos(hopper)} minecraft:hopper[facing=west]",
                             f"item replace block {rockets.pos(hopper)} container.0 with minecraft:coal 1"]
    for rocket in layout["rockets"]:
        x, y, z = rocket["origin"]
        commands += [f"fill {x-3} {y-2} {z-22} {x+3} {y-2} {z+3} minecraft:stone",
                     f"setblock {rockets.pos(rocket['assembler'])} {NS}:rocket_assembler"]
        commands += [f"setblock {rockets.pos(c['pos'])} {c['block']}" for c in rocket["blocks"]]
    return commands


def frozen_recipe(root=common.ROOT):
    with zipfile.ZipFile(root / FREEZE) as archive:
        raw = archive.read("PROPOSED-D1-CONTRACT-29.md")
    text = raw.decode("utf-8-sig")
    return dict(archive=FREEZE, archive_sha256=common.file_hash(root / FREEZE),
                member="PROPOSED-D1-CONTRACT-29.md", member_sha256=common.digest(raw),
                V06=next(line for line in text.splitlines() if line.startswith("| D1-V06 |")),
                B1_R1=text.split("**D1-07.**", 1)[1].split("**D1-08.**", 1)[0].strip(),
                status="NOT_COVERED", reason="Freeze requires B1/R1 qualification; it defines outcomes, not a runnable callback hook. "
                "C6 has no registered command for these event/callback controls; no direct sleeping or field writer is substituted.")


def source_values(jar_path=common.JAR, root=common.ROOT):
    source = common.JAVA_ROOT / "machine/classic/runtime/ResearchService.java"
    text = (root / source).read_text(encoding="utf-8")
    match = re.search(r"SURVEY_TICKS = ([\d_]+), ANALYSIS_TICKS = ([\d_]+), ENERGY_PER_TICK = ([\d_]+), CREDIT = ([\d_]+)", text)
    chain.require(match is not None, "C6 research constants not found")
    ticks, analysis, rate, credit = [int(v.replace("_", "")) for v in match.groups()]
    with zipfile.ZipFile(jar_path) as jar:
        lang = {}
        for language in ("en_us", "zh_cn"):
            path = f"assets/{NS}/lang/{language}.json"
            raw = jar.read(path)
            lang[language] = dict(path=path, sha256=common.digest(raw), strings={k: v for k, v in json.loads(raw).items()
                if any(word in k for word in ("observatory", "astrobody_data_processor", "research"))})
        types = {host: json.loads(jar.read(f"data/{NS}/dimension_type/{host}.json")) for host in HOSTS}
    config = common.JAVA_ROOT / "config/CommonConfig.java"
    config_text = (root / config).read_text(encoding="utf-8")
    chain.require('.define("research.groundSurveysEnabled", true)' in config_text, "Research default changed")
    return dict(source=str(source.as_posix()), source_sha256=common.file_hash(root / source),
                config_source=config.as_posix(), config_sha256=common.file_hash(root / config),
                ground_surveys_enabled_default=True, runtime_config="copied unchanged; see native receipt hashes",
                survey_ticks=ticks, analysis_ticks=analysis, energy_per_tick=rate, analysis_credit=credit,
                survey_fe=ticks * rate, analysis_fe=analysis * rate, languages=lang, dimension_types=types,
                provenance="C6 packaged language/type resources and unchanged C6 ResearchService source; not live ContainerData")


def client_steps(layout):
    """Finite recipes, not executed observations. Client/actor setup is explicit."""
    refs = dict(core="core-scenes/world", prior="prior-sleep/world")
    rows = {}
    def add(row, steps):
        for index, (precondition, action, readback) in enumerate(steps, 1):
            rows[f"{row}.{index:02}"] = dict(status="NOT_COVERED", starting_copy=refs["core"],
                precondition=precondition, action=action, expected_readback=readback,
                reason="Scripted client/operator steps only; no real client launched by W449.")
    add("J18", [
        ("Both sealed parents verified; fresh means fresh-C6 ancestry, not a newly generated K14 world.", "Read actual six started Level/type holders and generators on each copy.", "Keys/type fields/catalog/bindings and saved WorldGenSettings; two continuous restarts."),
        ("Controlled override/missing/reused packs; original copies retained.", "Install one pack on a disposable copy; record restart, /reload, diagnostics.", "Reload is not hot retyping; unsupported hosts never count as supported sleep."),
        ("For EACH of six hosts, build supplied sealed bed room by ordinary play; ambient f allowed; Space live VISIT.", "Use bed with a valid Overworld home, then on an independent no-home player copy.", "Actual rest; exact nullable Spawn fields, raw angle bits and backup unchanged."),
        ("Separate daytime, monster, vacuum, PENDING, incomplete-air and no-VISIT copies.", "Attempt the corresponding ordinary bed interaction.", "Native refusal and unchanged tuple/backup, including late refusal."),
        ("FI08-derived copy; establish forced, off-world, invalid-home and pending R-036 backup by ordinary play.", "Save, inspect, attempt sleep and perform native respawn for each distinct case.", "Existing R-036 fallback/residual and exact tuple/backup; no new planet point."),
        ("Same modified V02/V04-V07/V13 copies, no reset between cycles.", "save-all flush; stop; restart twice; compare disk and behavior each time.", "Typed field presence/values and backup stable, not whole changing player blobs."),
        ("A and B on same host, actual ordinary sleep.", "Use playersSleepingPercentage 100, 50, 101; separately spectator, disconnect and manual wake.", "Native active/sleep/deep counts, 100-tick threshold, no stale counter."),
        ("Each six hosts, ordinary/lodestone/recovery compasses and clock.", "Inspect; move shared spawn; apply one native border fallback control.", "Native shared-spawn target, fixed 18000 Moon/Space, four cyclic clocks; no safe-arrival promise."),
        ("Already-loaded portal built in ordinary play; no forced chunks.", "Separate doMobSpawning on/off and peaceful/nonpeaceful controls; exercise existing Forge veto.", "Conditional STRUCTURE attempt, floor/random checks retained; no guaranteed spawn."),
        ("Frozen V06 B1/R1 recipe in frozen-recipe.json; callback qualification still absent.", "Enumerate unrelated, nested, null/admin/restore and indeterminate cases; retain failures.", "Cancellation/newer tuple/nullable fields preserved; missing callback stays NOT_COVERED."),
        ("Two real clients; admitted ordinary beds and original tuples.", "Immediate save; leave bed; replacement; logout; orderly stop on separate copies.", "No stale restore over admin/unrelated setter; pose/counter reconciliation."),
        ("Two Space stations and two distinct surfaces; both clients.", "Off-world-only completion, then simultaneous Overworld completion.", "Shared Space quorum, separate surfaces; game/day/fixed time and weather, primary ownership."),
        ("One fixed and one cyclic host.", "doDaylightCycle false/true and separate rain/doWeatherCycle controls.", "Native local wake independent of day write; primary time/weather unchanged by ARCE."),
        ("Both restart cycles completed for each player-bearing case.", "Compare disk Spawn fields/backup and final player/world state.", "No loss; same-window pause/crash/third-party-save residuals remain visible.")])
    add("J19", [
        ("Four rotations in layout.json; A first breaks/replaces controllers using ordinary items to establish ResearchIdentity.", "Open both menus en_us/zh_cn at scales 2/3/4.", "ContainerData matches status/phase/progress/FE/owner/selection/missing cell."),
        ("A-owned observatory, clear night, charged plug plus combustion reserve >=20000 FE.", "Press START once; retain native job and instance UUIDs.", "ACTIVE/PENDING, WAITING_SAVE before paid advance; normal barrier ~100 ticks, no IO deadline claim."),
        ("Independent copies at same mid-survey state.", "Obstruct recorded highest lens, set day/rain, isolate power, walk away until required chunk unloads.", "NO_SKY/DAYTIME/WEATHER/NO_POWER/UNLOADED and no forced load; dry-biome rules retained."),
        ("Survey running, 1000 paid ticks, 20 FE/t.", "Complete survey under valid conditions.", "READY/AVAILABLE once, exactly 20000 FE across plug/generator ledger."),
        ("A processor, 4000 FE, server-held READY/WAITING job list.", "PREVIOUS/NEXT then CLAIM.", "RUNNING, WAITING_SAVE before payment; at most 16 owner jobs."),
        ("B is not A and is not operator; preserve pre-attempt ledger.", "Try foreign START/CANCEL/CLAIM/REBIND independently.", "Refusal, no balance/progress mutation."),
        ("One claim already RUNNING; second A-owned processor.", "Try CLAIM on same job.", "ALREADY_CLAIMED/refusal; no duplicate credit."),
        ("Valid claim; 200 paid ticks, 20 FE/t.", "Complete and replay CLAIM.", "4000 FE, +120 points exactly once. Full-account variant must earn its balance normally; never edit it."),
        ("Separate mid-job copies with job/device UUID readbacks.", "Break source; place replacement by A; REBIND_HERE; independently CANCEL pending survey.", "SOURCE_MISSING; progress preserved on rebind; cancellation removes PENDING with no refund/award."),
        ("Use K16 revision 2 K16 Core Station at Space landing_pad (0,128,0); owner has live VISIT.", "A builds/owns both research patterns there by ordinary play; survey then relocate station inter-system.", "Station bypasses sky/day/weather; orbit-derived system and SYSTEM_CHANGED pause."),
        ("Separate ordinary mid-survey/mid-analysis copies with complete ledgers.", "Save, stop, reopen, complete and replay.", "Progress/FE coherent, credit once; real V1/V2 still required.")])
    add("J20", [
        ("layout.json tall rocket, player-granted fuel cells through ordinary loader GUI; A and ground observer.", "Fly while observer moves 20 blocks away; record ASCENT and DESCENT.", "Rocket/trail tracking aligned; record actual stutter."),
        ("Same native assembled rocket; no invented movement.", "Record parked, COUNTDOWN and TRANSIT.", "No invented velocity/inactive-state cadence."),
        ("On Moon K16 floor: (48.5,150,48.5) clear area must be loaded/checked by A in play.", "From rest at (48.5,156,48.5), effect give A minecraft:slow_falling 30 0 true; separately rising/ordinary/liquid/disabled controls.", "No low-gravity dead-zone sticking; health/position; ordinary controls retained. Spot is a proposal until visited."),
        ("Use matching mode rocket; actual top anchor is verified by build receipt.", "A boards top seat, closes console; B observes from ground.", "Actual assigned seat equals origin+[0,9,0], rider feet seat+(.5,.15,.5)."),
        ("Rocket origin y=188, section remainder 12; mode set by /gamemode survival|creative A.", "Close/reopen while facing N/E/S/W/up/down in each mode; do not use F3 Targeted Entity as proof.", "Console opens through section-margin fallback and rider stays in assigned seat."),
        ("A builds/registers pad named K14 Unloaded Pad at Space (224,128,224), inside K16 Core Station; then everyone leaves until execute unless loaded proves unloaded.", "Select /arcepad list entry and request flight toward that registered pad.", "Actual refusal remains until next button/plan change. Name/UUID/epoch/unloaded must be read back first; not supplied natively."),
        ("Capture native server log and real translated console.", "Compare name and refusal/invalid-assembler feedback in both languages.", "Entity name is not menu key; no coordinate_space=absolute in player-facing messages; bounded operator detail.")])
    add("I07", [
        ("Pattern shells in core-scenes plus K16 Space structures; no formation assumed for endgame shells.", "A/B approach each six hulls in all supported facings; elevator anchor NORTH only; verify server formation.", "Full-scale UV/light/culling/clearance on both clients; complete actual formation readback."),
        ("Both observers with live access; six validated hulls.", "Walk around/through elevator platform and all sides of each hull.", "Record cross-cell overlap, clipping, z-fighting and clearance."),
        ("Independent precision copy, ordinary valid mirrored pattern.", "Build mirrored precision formation and inspect both clients.", "JSON/world-block fallback, not mirrored OBJ mesh."),
        ("Validated retained directional controller; record facing.", "Rotate with ordinary wrench; restore original validated facing.", "Hull clears on mismatch and restores; visual flags do not rewrite server formation."),
        ("A manipulates while B watches; independent body copies.", "Break one required cell, repair, walk away until unloaded, return and reload.", "Both clients agree; no stale hull or laser/accretion/tether effects."),
        ("Four etcher patterns, recorded motor cells.", "Rotate/restore controller; break/repair motor in each facing.", "Validated facing follows hull; no coplanar/light/culling faults."),
        ("Existing K16 anchor (60,150,76) and terminal Space (20,128,6); A links/charges endpoints ordinarily; B observes.", "Start/cancel/commit rides separately; remove endpoint and change Level; then load K13 missing-resource pack on scene copy and F3+T/reconnect.", "One Capsule per pending endpoint, NORTH doorway, no Door; clears on terminal states; fallback line cage. Pack load NOT_COVERED here.")])
    rows["J18.05"]["starting_copy"] = refs["prior"]
    return rows


def disk_state(world):
    """Read only, preserving SpawnAngle raw float32 bits and nullable presence."""
    from scripts import v170_world_fixture as fixture
    level = fixture.read_nbt(world / "level.dat")["Data"]
    players = {}
    for path in sorted((world / "playerdata").glob("*.dat")):
        raw = fixture.read_nbt(path)
        fields = {key: raw[key] for key in SPAWN_FIELDS if key in raw}
        if "SpawnAngle" in fields:
            fields["SpawnAngle_raw_float32"] = struct.pack(">f", fields["SpawnAngle"]).hex()
        players[path.name] = dict(spawn=fields, backup={key: value for key, value in raw.items()
            if "respawn" in key.lower() or "backup" in key.lower()},
            forge_persisted=raw.get("ForgeData", {}).get("PlayerPersisted", {}))
    roots = {}
    for suffix in ("stations", "satellite_missions", "planetary_bindings", "endgame"):
        path = world / "data" / f"{NS}_{suffix}.dat"
        if path.exists():
            roots[suffix] = fixture.read_nbt(path).get("data", {})
    stations = roots.get("stations", {}).get("stations", [])
    missions = roots.get("satellite_missions", {})
    return dict(players=players, worldgen=level.get("WorldGenSettings", {}),
                stations=[{k: row.get(k) for k in ("station_id", "owner_id", "name", "region", "landing_pad", "members", "orbit_body")}
                          for row in stations],
                research_accounts=missions.get("research_accounts", []),
                ground_surveys=missions.get("ground_surveys", []),
                player_coverage="No player records; tuple absence is inventory only, not an absent-point bed test." if not players else "Native records inventoried; no bed operation inferred.")


def generate(output, fixtures=FIXTURES, jar_path=common.JAR):
    chain.require(common.file_hash(jar_path) == common.JAR_HASH, "C6 JAR mismatch")
    parents = {"core-scenes": (fixtures / "K16/v2", "world"),
               "prior-sleep": (fixtures / "K03/FI08", "source-world")}
    pins = {name: verify_parent(folder, world) for name, (folder, world) in parents.items()}
    pack_pin = verify_parent(fixtures / "K13")
    # K05 sealed scenes are citations, not claims that they contain all six hulls/facings.
    k05 = {name: verify_parent(fixtures / "K05/r608" / name, "world") for name in ("a100-idle", "two-owners")}
    with zipfile.ZipFile(jar_path) as jar:
        layout = core_layout(jar)
    commands = commands_for(layout)
    output.mkdir(parents=True, exist_ok=False)
    write(output / "values.json", source_values(jar_path))
    write(output / "frozen-recipe.json", frozen_recipe())
    write(output / "client-steps.json", client_steps(layout))
    write(output / "dependencies.json", dict(parents=pins, k05=k05, k13=pack_pin,
        missing_resource_pack=dict(path=str(fixtures / "K13/k13-missing-resource-pack.zip"),
            sha256=common.file_hash(fixtures / "K13/k13-missing-resource-pack.zip"),
            scene="core-scenes; existing Space elevator terminal/anchor via K16 copy",
            status="NOT_COVERED", reason="Actual client pack load not run; W402 fallback expectations are not observations.")))
    for name in SCENES:
        folder = output / name
        folder.mkdir()
        write(folder / "layout.json", layout if name == "core-scenes" else dict(devices=[], rockets=[]))
        (folder / "commands.txt").write_text("\n".join(commands if name == "core-scenes" else []) + "\n", encoding="ascii")
        write(folder / "parent.json", pins[name])
        write(folder / "parent-state.json", disk_state(parents[name][0] / parents[name][1]))
    return layout


def check_command(command):
    """Do not permit save mutation, balance fixtures, force tickets or data injection."""
    chain.require(not re.search(r"\b(forceload|summon|spawnpoint|research-test)\b", command), "Forbidden fixture mutation")
    chain.require(not re.search(r"\bdata\s+(merge|modify|remove)\b", command) and "{" not in command,
                  "NBT writes are not allowed")
    chain.require(command.startswith(("execute ", "setblock ", "fill ", "item replace block ", "say ",
        "save-all", "stop", "list", "arce rocket assemble ", "arce rocket status ", "arce rocket release-test report ",
        "arce station admin dump", "arce celestial ", "datapack list")), "Unreviewed command")


class Console(rockets.Console):
    def send(self, command):
        check_command(command)
        return super().send(command)

    def block(self, at):
        lines = self.send("execute in " + OW + " run data get block " + rockets.pos(at))
        raw = [line.split("has the following block data: ", 1)[1] for line in lines if "has the following block data: " in line]
        chain.require(len(raw) == 1, "Missing block readback: " + rockets.pos(at))
        return machines.parse_snbt(raw[0])


def native_state(console, layout):
    result = dict(devices=[], rockets=[], hosts={}, stations=console.send("arce station admin dump"),
                  catalog=console.send("arce celestial list"), packs=console.send("datapack list"))
    for host in HOSTS:
        token = "K14_LOADED_" + host
        lines = console.send(f"execute in {NS}:{host} if loaded 34 150 34 run say {token}")
        result["hosts"][host] = dict(sample_loaded=any("[Server] " + token in line for line in lines),
            runtime_holder_status="NOT_COVERED", reason="No C6 holder inspection console hook; disk type references are not actual holder flags.")
    for device in layout["devices"]:
        root = console.block(device["pos"])
        row = dict(kind=device["kind"], pos=device["pos"], facing=device["facing"], nbt=root)
        if "arce_classic_machine" in root:
            row["formation"] = root["arce_classic_machine"]["structure"]["formation_state"]
            chain.require(row["formation"] == '"FORMED"', "Unformed classic pattern: " + str(device["pos"]))
        else:
            row["formation"] = "NOT_COVERED: endgame root does not persist live formation"
        if device["kind"] in ("observatory", "astrobody_data_processor"):
            row["research_identity"] = root.get("arce_" + device["kind"])
            row["identity_status"] = "NOT_COVERED: console setblock does not call player setPlacedBy; A must replace controller"
            row["power"] = []
            for source, plug in zip(device["power_sources"], device["power_plugs"], strict=True):
                hatch, generator = console.block(plug), console.block(source["pos"])
                row["power"].append(dict(plug=hatch, generator=generator))
        result["devices"].append(row)
    for rocket in layout["rockets"]:
        x, y, z = rocket["origin"]
        selector = f"@e[type={NS}:rocket,x={x-2},y={y-2},z={z-2},dx=4,dy=14,dz=4,limit=1]"
        lines = console.send("arce rocket release-test report " + selector)
        report = rockets.parse_report(lines)
        chain.require(report["blocks"] == 10 and report["y"] % 16 in range(12, 16), "High-seat rocket origin/count mismatch")
        seats = console.send(f"execute in {OW} run data get entity {report['entity']} RocketEntityData.snapshot.passenger_anchors")
        chain.require(any(re.search(r"\[I;\s*0,\s*9,\s*0\]", line) for line in seats), "Top seat differs")
        report.update(mode=rocket["mode"], seats=seats, fuel_status="UNFUELLED: ordinary loader client pre-step required",
                      actual_top_seat=[report["x"], report["y"] + 9, report["z"]],
                      rider_feet=[report["x"] + .5, report["y"] + 9.15, report["z"] + .5])
        result["rockets"].append(report)
    return result


def comparable(state):
    """Only qualified stable fields; never compare clock, tick, or log prefixes."""
    return dict(devices=[{k: row[k] for k in ("kind", "pos", "facing", "nbt")} for row in state["devices"]],
                rockets=[{k: row[k] for k in ("entity", "logical", "snapshot", "state", "fuel", "capacity", "blocks", "x", "y", "z")}
                         for row in state["rockets"]])


def require_lock(token, lock=chain.LOCK):
    chain.require(bool(re.fullmatch(r"W449 pid=\d+ [\w-]+", token)), "W449 lock token required")
    owner = (lock / "owner.txt").read_text(encoding="utf-8-sig").splitlines()[0]
    chain.require(owner.startswith(token + " "), "Heavy-JVM ownership required")


def run(output, scene, stage, cell_path, java, lock_owner):
    from scripts import run_dedicated_server_smoke as smoke
    from scripts import run_v060_flight_server_smoke as flight
    chain.require(scene in SCENES and stage in STAGES, "Unknown stage/scene")
    require_lock(lock_owner)
    chain.require(min(shutil.disk_usage(p).free for p in ("C:/", "D:/")) >= 10 * 1024**3, "10 GiB required on both drives")
    folder = output / scene
    receipt_path = folder / (stage + ".json")
    chain.require(not (folder / "SHA256SUMS.txt").exists() and not receipt_path.exists(), "Refusing existing receipt/sealed scene")
    parent = json.loads((folder / "parent.json").read_text())
    verify_parent(Path(parent["path"]), parent["world_name"])
    source_world = Path(parent["path"]) / parent["world_name"] if stage == "build" else folder / "world"
    if stage == "build":
        chain.require(not (folder / "world").exists(), "World already built")
    elif stage == "reopen-2":
        chain.require(json.loads((folder / "reopen-1.json").read_text())["status"] == "MATCHED", "First reopen missing")
    before = common.tree_hashes(source_world)
    cell = json.loads(cell_path.read_text(encoding="utf-8-sig"))
    template = Path(cell["server_dir"])
    template_before = common.tree_hashes(template)
    rockets.validate_candidate(cell, Path(cell["mods"]["server"][0]["path"]))
    runtime = folder / "_server"
    # Reopen 2 uses the SAME saved lineage, not a reset from the sealed source.
    lineage = folder / "_restart-world"
    chain.require(not runtime.exists(), "Disposable server occupied")
    layout = json.loads((folder / "layout.json").read_text())
    result = dict(status="FAILED", stage=stage, scene=scene, parent=parent, client_started=False,
        candidate_commit=common.CANDIDATE, jar_sha256=common.JAR_HASH,
        generator_sha256=common.file_hash(Path(__file__)), generator_commit=subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=common.ROOT, text=True).strip(), lock_owner=lock_owner)
    process = console = None
    try:
        shutil.copytree(template, runtime, ignore=shutil.ignore_patterns("world", "logs", "crash-reports"))
        installed = list((runtime / "mods").glob("*.jar"))
        chain.require(len(installed) == 1 and common.file_hash(installed[0]) == common.JAR_HASH, "Copied JAR mismatch")
        load_world = lineage if stage == "reopen-2" else source_world
        shutil.copytree(load_world, runtime / "world")
        props = smoke.expected_server_properties(smoke.allocate_port(), False)
        props.update({"level-name": "world", "view-distance": "2", "simulation-distance": "2", "spawn-protection": "0"})
        (runtime / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(props.items())), encoding="ascii")
        args = runtime.resolve() / "libraries/net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt"
        command = [str(java), "-Xms512M", "-Xmx3G", "-Dadvancedrocketrycommunity.releaseTestHooks=true", "@" + str(args), "nogui"]
        result.update(command=command, config_files={p: h for p, h in common.tree_hashes(runtime).items() if p.startswith("config/")})
        process = smoke.CapturedProcess(command, runtime, folder / (stage + "-console.log"))
        process.wait_for(smoke.READY_MARKER, 240)
        console = Console(process)
        chain.require(any("0 of a max" in line for line in console.send("list")), "Unexpected connected player")
        if stage == "build" and layout["devices"]:
            # Loaded spawn chunks only. No new ticket is introduced to prepare a remote scene.
            for device in layout["devices"]:
                token = "K14_SPAWN_READY"
                console.until(f"execute if loaded {rockets.pos(device['pos'])} run say {token}", "[Server] " + token)
            for value in (folder / "commands.txt").read_text().splitlines():
                if value:
                    lines = console.send(value)
                    chain.require(not any("Cannot access blocks outside" in line for line in lines), "Unloaded block placement")
            for rocket in layout["rockets"]:
                start = len(process.lines)
                console.send("arce rocket assemble " + rockets.pos(rocket["assembler"]))
                process.wait_for(flight.ASSEMBLY_LOG, 90, start_at=start)
            # Vanilla hopper -> generator item capability -> native burn -> claimed plug.
            # Wait for full buffers; no research balance/identity or FE value is injected.
            for device in layout["devices"]:
                for source in device.get("power_sources", []):
                    console.until(f"execute if data block {rockets.pos(source['pos'])} arce_combustion_generator run say K14_GENERATOR_ROOT", "[Server] K14_GENERATOR_ROOT", 30)
            deadline = time.monotonic() + 120
            while True:
                powers = [console.block(source["pos"]) for device in layout["devices"] for source in device.get("power_sources", [])]
                if all(machines.number(row["arce_combustion_generator"]["energy"]) == 20000 for row in powers):
                    break
                chain.require(time.monotonic() < deadline, "Native power buffers not full")
                time.sleep(1)
        state = native_state(console, layout)
        result["native"] = state
        if stage != "build":
            baseline = json.loads((folder / "build.json").read_text())
            chain.require(comparable(state) == comparable(baseline["native"]), "Native stable readbacks changed after restart")
        console.send("save-all flush")
        console.commands.append("stop")
        process.command("stop")
        code = process.finish(180)
        process = None
        chain.require(code == 0, "Native exit " + str(code))
        log = (runtime / "logs/latest.log").read_text(encoding="utf-8")
        result["error_lines"] = [line for line in log.splitlines() if "/ERROR]" in line or "/FATAL]" in line]
        chain.require(not result["error_lines"], "Native ERROR/FATAL; retained console log")
        saved = disk_state(runtime / "world")
        result["disk"] = saved
        if stage == "build":
            shutil.copytree(runtime / "world", folder / "world")
        else:
            baseline = json.loads((folder / "build.json").read_text())["disk"]
            chain.require(saved == baseline, "Saved tuple/registry/type projection changed")
        if stage == "reopen-1":
            shutil.copytree(runtime / "world", lineage)
        result.update(status="BUILT" if stage == "build" else "MATCHED", exit_code=code,
                      world_files=common.tree_hashes(folder / "world"),
                      loaded_from=str(load_world), saved_world_files=common.tree_hashes(runtime / "world"))
    except BaseException as failure:
        result["error"] = str(failure)
        raise
    finally:
        if process is not None:
            common.abort_owned(process, folder)
        result["commands"] = console.commands if console else []
        result["parent_unchanged"] = common.tree_hashes(source_world) == before
        result["cell_unchanged"] = common.tree_hashes(template) == template_before
        write(receipt_path, result)
        if runtime.exists():
            chain.require(runtime.resolve().parent == folder.resolve() and runtime.name == "_server", "Unsafe cleanup target")
            shutil.rmtree(runtime)
        if stage == "reopen-2" and lineage.exists():
            chain.require(lineage.resolve().parent == folder.resolve(), "Unsafe lineage cleanup")
            shutil.rmtree(lineage)
    chain.require(result["parent_unchanged"] and result["cell_unchanged"], "Immutable input changed")
    return result


def seal(output, scene):
    folder = output / scene
    chain.require(scene in SCENES and not (folder / "SHA256SUMS.txt").exists(), "Refusing seal overwrite")
    for stage in STAGES:
        receipt = json.loads((folder / (stage + ".json")).read_text())
        chain.require(receipt["status"] == ("BUILT" if stage == "build" else "MATCHED")
                      and receipt["parent_unchanged"] and receipt["cell_unchanged"], "Incomplete native qualification")
        chain.require(receipt["world_files"] == common.tree_hashes(folder / "world"), "Source changed since receipt")
    inventory = common.tree_hashes(folder)
    chain.require(not any(name.startswith("_server/") or name.startswith("_restart-world/") for name in inventory), "Disposable output retained")
    write(folder / "manifest.json", dict(schema_version=1, status="PARTIAL_INPUT_NOT_CAMPAIGN_ACCEPTANCE",
        parent=json.loads((folder / "parent.json").read_text()), candidate_commit=common.CANDIDATE,
        jar_sha256=common.JAR_HASH, rows=["J18"] if scene == "prior-sleep" else ["I07", "J18", "J19", "J20"],
        layout=json.loads((folder / "layout.json").read_text()), inventory_before_manifest=inventory,
        recipes="../client-steps.json", frozen_recipe="../frozen-recipe.json",
        native_receipts=[stage + ".json" for stage in STAGES],
        limitations="No real players, sleep callbacks, supplied off-world beds, research owner/balance variants, named pad or visual acceptance qualified."))
    chain.seal(folder)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("generate", "run", "seal"))
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--scene", choices=SCENES)
    parser.add_argument("--stage", choices=STAGES)
    parser.add_argument("--cell", type=Path, default=common.CELL)
    parser.add_argument("--java", type=Path, default=JAVA)
    parser.add_argument("--lock-owner", default="")
    args = parser.parse_args()
    if args.action == "generate":
        generate(args.output)
    else:
        parser.error("--scene is required") if not args.scene else None
        if args.action == "seal":
            seal(args.output, args.scene)
        else:
            parser.error("--stage is required") if not args.stage else None
            run(args.output, args.scene, args.stage, args.cell, args.java, args.lock_owner)


if __name__ == "__main__":
    main()
