"""Prepare K16 inputs, never start a client or claim campaign acceptance.

Generate: python -B -m scripts.campaign_prep.k16_core generate --output <new-directory>
Build: run the build subcommand under WORKER-RULES' heavy-JVM lock, in a detached
launcher. Only the copied server is writable. Generated commands/batches are
deterministic; Minecraft's saved world and native receipts are not byte-reproducible.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import time
from uuid import UUID
import zipfile

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.campaign_client.protocol import validate_request

ROOT = Path(__file__).resolve().parents[2]
JAVA_ROOT = Path("src/main/java/io/github/sunthemoon/advancedrocketrycommunity")
NS = "advancedrocketrycommunity"
OW, MOON = "minecraft:overworld", NS + ":moon"
SPACE, CYGNUS = NS + ":space", NS + ":cygnus_x1"
CANDIDATE = "c8fa16e16be87554f26c402546733df6555d15e6"
JAR_HASH = "9f7e9d1ef708e27fa5778ca3c72ea3b3605f889465ae0d0d1054a890eb808dd4"
EVIDENCE = Path("D:/GitHub/ARCE-Task-Evidence")
CELL = EVIDENCE / "root-queue/P3/cells/47.4.10-nojei/cell.json"
JAR = EVIDENCE / "root-queue/C6/candidate/unpacked/advancedrocketry-community-1.20.1-1.8.0-dev.jar"
OUTPUT = EVIDENCE / "final-campaign/c8fa16e16/fixtures/K16"
PLAYER = "ArceTester1"
OWNER = "843ec7fe-f4c9-34a7-8fe9-935daf79698b"  # OfflinePlayer:ArceTester1, not a premium account.
VIEWPORTS = ((1280, 720, 2), (1280, 720, 3), (1920, 1080, 4))


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def file_hash(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def json_bytes(value):
    return (json.dumps(value, indent=2, sort_keys=True, ensure_ascii=True) + "\n").encode("ascii")


def uuid_nbt(value):
    return "[I;" + ",".join(map(str, struct.unpack(">iiii", UUID(value).bytes))) + "]"


def gui_point(viewport, panel, point):
    """Window.calculateScale, ceil GUI dimensions, AbstractContainerScreen origin.

    Pixel positions address the physical client area, not desktop decorations.
    Controls outside the viewport are reported, never clamped onto another control.
    """
    width, height, requested = viewport
    if any(type(v) is not int or v <= 0 for v in viewport):
        raise ValueError("Positive integer viewport required")
    scale = 1
    while scale < requested and width // (scale + 1) >= 320 and height // (scale + 1) >= 240:
        scale += 1
    # Java integer division truncates toward zero, including negative panel origins.
    left = math.trunc((math.ceil(width / scale) - panel[0]) / 2)
    top = math.trunc((math.ceil(height / scale) - panel[1]) / 2)
    return {"effective_scale": scale, "origin": [left, top],
            "pixel": [int((left + point[0]) * scale), int((top + point[1]) * scale)]}


# Logical coordinates transcribed from the named C6 Screen/Menu implementations,
# not copied from historical pointer batches. Source anchors below fail if moved.
# A slot is its item corner + (8,8); a button is the centre of its bounds.
LAYOUTS = {
    "combustion": ((176, 166), "CombustionGeneratorScreen", {"fuel": (52, 42), "hot0": (16, 150)}),
    "electrolyzer": ((176, 176), "ElectrolyzerScreen", {
        "input": (52, 43), "charge": (28, 43), "hydrogen": (124, 43), "oxygen": (148, 43),
        "hot0": (16, 160), "hot1": (34, 160), "hot2": (52, 160)}),
    "classic": ((220, 128), "ClassicControllerScreen", {"status": (110, 60)}),
    # "energy" is the centre of the gauge rectangle each screen passes to isHovering for its energy tooltip.
    "rolling": ((196, 212), "RollingMachineScreen", {"status": (98, 60), "energy": (64, 48)}),
    "precision": ((244, 225), "PrecisionAssemblerScreen", {"status": (122, 60), "energy": (158, 52)}),
    "suit": ((176, 202), "equipment/SuitWorkstationScreen", {"install": (88, 38), "status": (88, 60)}),
    "flight": ((248, 216), "RocketFlightScreen", {
        "earth": (69, 99), "moon": (179, 99), "other": (71, 124), "station": (177, 124),
        "board": (71, 158), "leave": (177, 158), "launch": (124, 188), "map": (206, 15)}),
    "monitor": ((300, 238), "RocketMonitorScreen", {"status": (150, 100), "quote": (188, 198)}),
    "fluid_loader": ((176, 90), "FluidFuelLoaderScreen", {"status": (88, 45)}),
    "terminal": ((352, 234), "SatelliteTerminalScreen", {
        "previous": (19, 91), "next": (205, 91), "assemble": (28, 113), "launch": (75, 113),
        "claim": (123, 113), "cancel": (181, 113), "status": (110, 62), "energy": (197, 34)}),
    "builder": ((224, 202), "SatelliteBuilderScreen", {"assemble": (153, 72), "status": (110, 85),
                                                       "energy": (206, 40)}),
    "railgun": ((176, 222), "RailgunScreen", {
        "previous": (16, 83), "next": (34, 83), "launch": (66, 83), "auto": (108, 83),
        "redstone": (148, 83), "status": (88, 118), "input0": (16, 26), "hot0": (16, 206)}),
    "blackhole": ((236, 186), "BlackHoleGeneratorScreen", {"status": (118, 83)}),
    "drill": ((176, 222), "OrbitalLaserDrillScreen", {"status": (88, 118)}),
    "target": ((176, 186), "LaserTargetScreen", {"reset": (143, 10), "status": (88, 80)}),
    "elevator": ((176, 222), "ElevatorScreen", {"bind": (63, 83), "ride": (148, 83), "status": (88, 118)}),
    "warp": ((256, 222), "station/StationControllerScreen", {
        "next": (158, 28), "quote": (34, 46), "confirm": (88, 46), "cancel": (142, 46)}),
}


def control_manifest(root=ROOT):
    screens = {}
    for name, (panel, source, controls) in LAYOUTS.items():
        path = JAVA_ROOT / "client" / (source + ".java")
        raw = (root / path).read_bytes()
        screens[name] = {"panel": list(panel), "source": path.as_posix(), "source_sha256": digest(raw),
                         "logical_controls": controls, "viewports": {}}
        for viewport in VIEWPORTS:
            values = {}
            for control, point in controls.items():
                mapped = gui_point(viewport, panel, point)
                x, y = mapped["pixel"]
                mapped["inside_viewport"] = 0 <= x < viewport[0] and 0 <= y < viewport[1]
                values[control] = mapped
            screens[name]["viewports"][viewport_id(viewport)] = values
    additional = ("machine/electrolyzer/ElectrolyzerMenu.java", "client/RocketFlightScreenLayout.java",
                  "endgame/railgun/RailgunMenu.java", "station/controller/StationControllerMenu.java",
                  "station/controller/WarpControllerMenu.java")
    return {"schema_version": 1, "additional_sources": {p: file_hash(root / JAVA_ROOT / p) for p in additional},
            "formula": "effective scale s: increment from 1 while s<requested and "
            "floor(W/(s+1))>=320 and floor(H/(s+1))>=240; GUI=(ceil(W/s),ceil(H/s)); "
            "origin=Java-trunc((GUI-panel)/2); physical=s*(origin+logical control centre). "
            "Default non-Unicode font; forceUnicodeFont must be false. Fullscreen is an operator precondition, "
            "not established by a resize action.", "screens": screens}


def viewport_id(viewport):
    w, h, scale = viewport
    return f"{w}x{h}-s{scale}"


def s(command, **fields):
    return dict(op="server", command=command, **fields)


def key(value):
    return dict(op="key", key=value, settle=0.5)


def shot(name):
    return dict(op="screenshot", name=name)


def click(viewport, screen, control):
    panel, _, points = LAYOUTS[screen]
    x, y = gui_point(viewport, panel, points[control])["pixel"]
    if not (0 <= x < viewport[0] and 0 <= y < viewport[1]):
        raise ValueError(f"Inaccessible {screen}.{control} at {viewport}")
    return dict(op="click", x=x, y=y, settle=0.8)


FLOOR = {OW: 150, MOON: 150, SPACE: 128}  # The y a player stands at on each fixture platform.
ROOM_BREAK_CELL = (34, 152, 36)
REVISION = 2  # 1 is the sealed final/ set; 2 is the W443 route fix. The world is not interchangeable.


def look(pos, dim=OW):
    """Stand on the platform north of the device and look at the centre of its north face.

    The player always stands on the floor: one teleported to a controller one block up (the lathe at
    y 151) falls back to the floor with the same pitch and then aims at the block below it.
    """
    x, y, z = pos
    return s(f"execute in {dim} run tp {PLAYER} {x + .5} {FLOOR[dim]} {z - 2.5} "
             f"facing {x + .5} {y - 1.12:.2f} {z}", settle=0.8)


def open_screen(viewport, pos, dim=OW):
    return [s(f"item replace entity {PLAYER} hotbar.8 with minecraft:air"), key("9"), look(pos, dim),
            dict(op="click", x=viewport[0] // 2, y=viewport[1] // 2, right=True, settle=1)]


def pattern_commands(jar, kind, pos, dimension=OW):
    pattern = json.loads(jar.read(f"data/{NS}/machine_patterns/{kind}.json"))
    anchor = pattern["controller_anchor"]
    tags = {"coils": "copper_coil", "motors": "motor", "arc_furnace_shell_parts": "blast_brick",
            "observatory_shell_parts": "machine_casing",
            "slabs": "minecraft:smooth_stone_slab", "machine_clearance": "minecraft:air"}
    generic = {"item_input": "item_input_hatch", "item_output": "item_output_hatch",
               "fluid_input": "fluid_input_hatch", "fluid_output": "fluid_output_hatch",
               "energy_input": "power_input_plug"}
    commands, cells = [], []
    for cell in pattern["cells"]:
        p = tuple(pos[i] + cell[k] - anchor[k] for i, k in enumerate("xyz"))
        match = cell["matcher"]
        if match.get("controller"):
            continue  # Controller last, as in the old builders.
        if "optional" in match or match.get("air"):
            block = "minecraft:air"
        elif "block" in match:
            block = match["block"]
        elif "tag" in match:
            block = tags[match["tag"].split(":")[1]]
        elif "port" in match:
            port = match["port"]
            if kind in ("rolling_machine", "precision_assembler"):
                block = kind + "_" + port + "_port"
            else:
                block = generic[port]
        else:
            raise ValueError(f"Unsupported matcher {match}")
        if ":" not in block:
            block = NS + ":" + block
        commands.append(f"execute in {dimension} run setblock {' '.join(map(str, p))} {block}")
        cells.append(dict(pos=p, block=block))
    # ElevatorEndpointBlock has no directional property. Its pattern's north
    # rotation is implicit; a facing suffix would reject the whole placement.
    state = "" if kind == "elevator_anchor" else "[facing=north]"
    commands.append(f"execute in {dimension} run setblock {' '.join(map(str, pos))} {NS}:{kind}{state}")
    return commands, cells


def core_world(jar):
    commands = ["gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                "gamerule doMobSpawning false", "gamerule spawnRadius 0", "time set 6000", "weather clear",
                "setworldspawn 4 150 4"]
    devices = []
    for dim in (OW, MOON, SPACE):
        commands += [f"execute in {dim} run forceload add 0 0 95 95",
                     f"execute in {dim} run fill 0 {127 if dim == SPACE else 149} 0 "
                     f"95 {127 if dim == SPACE else 149} 95 minecraft:smooth_stone"]
    # Create on the untouched empty Space level before writing its station platform.
    commands.insert(7, f"arce station admin create {OWNER} {NS}:earth K16 Core Station")
    commands.insert(8, f"arce station admin create {OWNER} {CYGNUS} K16 Cygnus Station")
    commands += [f"execute in {SPACE} run forceload add 1024 0 1055 31",
                 f"execute in {SPACE} run fill 1024 127 0 1055 127 31 minecraft:smooth_stone"]
    def place(name, pos, block=None, dim=OW, **extra):
        block = block or NS + ":" + name
        commands.append(f"execute in {dim} run setblock {' '.join(map(str, pos))} {block}")
        devices.append(dict(name=name, pos=pos, dimension=dim, block=block, owner=None,
                            state="fresh console placement; not survival acquisition", **extra))
    for name, pos in (("combustion_generator", (6, 150, 6)), ("solar_generator", (10, 150, 6)),
                      ("pressurized_tank", (6, 150, 12)), ("pump", (10, 150, 12)),
                      ("electrolyzer", (40, 150, 34)), ("suit_workstation", (46, 150, 34)),
                      ("gas_charge_pad", (50, 150, 34)), ("satellite_terminal", (6, 150, 70)),
                      ("satellite_builder", (12, 150, 70)), ("rocket_monitoring_station", (8, 150, 53)),
                      ("fuel_loader", (12, 150, 53)), ("fluid_fuel_loader", (14, 150, 53)),
                      ("laser_target", (90, 150, 40))):
        place(name, pos)
    place("warp_controller", (6, 128, 6), dim=SPACE)
    place("warp_core", (10, 128, 6), dim=SPACE)
    place("elevator_terminal", (20, 128, 6), dim=SPACE)
    for kind, pos in (("rolling_machine", (20, 150, 20)), ("precision_assembler", (30, 150, 20)),
                      ("lathe", (42, 151, 20)), ("cutting_machine", (54, 150, 20)),
                      ("crystallizer", (66, 150, 20)), ("chemical_reactor", (78, 151, 20)),
                      ("arc_furnace", (22, 151, 46)), ("precision_laser_etcher", (32, 151, 46)),
                      ("observatory", (44, 151, 62)), ("astrobody_data_processor", (58, 151, 62)),
                      ("railgun", (76, 150, 76)), ("elevator_anchor", (60, 150, 76)),
                      ("black_hole_generator", (1044, 128, 20)), ("orbital_laser_drill", (30, 128, 30))):
        dimensions = (OW, MOON) if kind == "railgun" else (
                     (SPACE,) if kind in ("black_hole_generator", "orbital_laser_drill") else (OW,))
        for dim in dimensions:
            cmds, cells = pattern_commands(jar, kind, pos, dim)
            commands.extend(cmds)
            devices.append(dict(name=kind, pos=pos, dimension=dim, pattern_cells=cells,
                                block=NS + ":" + kind, owner=OWNER if kind == "railgun" else None,
                                state="console-built pattern; formation not assumed"))
            if kind == "railgun":
                at = " ".join(map(str, pos))
                identity = "42700000-0000-4000-8000-00000000000" + ("1" if dim == OW else "2")
                for field, value in (("device_id", uuid_nbt(identity)), ("owner_id", uuid_nbt(OWNER)),
                                     ("energy", "1000000")):
                    commands.append(f"execute in {dim} run data modify block {at} endgame.{field} set value {value}")
                devices[-1].update(device_id=identity, granted_energy_fe=1_000_000)
    # Source structures, not already assembled entities. The invalid one lacks guidance.
    for x, valid in ((10, True), (26, False)):
        place("rocket_assembler", (x, 150, 50))
        blocks = ["rocket_motor", "rocket_fuel_tank", "rocket_fuel_tank",
                  "guidance_computer" if valid else "minecraft:stone", "rocket_seat"]
        for y, block in enumerate(blocks, 151):
            commands.append(f"setblock {x} {y} 50 " + (block if ":" in block else NS + ":" + block))
        devices[-1].update(source_blocks=blocks, valid_variant=valid)
    # Moon room matches gen2.py's topology. Boundary is closed; its break/repair cell (34,152,36) is glass,
    # which a player breaks by hand in under a second and places back from one item.
    commands += [f"execute in {MOON} run fill 32 150 32 36 154 36 minecraft:iron_block hollow",
                 f"execute in {MOON} run fill 32 154 32 36 154 36 minecraft:glass",
                 f"execute in {MOON} run setblock {' '.join(map(str, ROOM_BREAK_CELL))} minecraft:glass"]
    # The vent and its generator are placed empty. Version 1 merged {schema_version:1,...} onto the vent's
    # saved schema-2 root, which C6 quarantines as invalid data, so the room never had air (W443).
    # core-room supplies both by normal use actions instead.
    place("oxygen_vent", (34, 150, 34), dim=MOON)
    place("combustion_generator", (33, 150, 34), dim=MOON)
    place("co2_scrubber", (35, 150, 34), dim=MOON)
    commands += [f"execute in {MOON} run setblock 34 151 33 minecraft:torch",
                 f"execute in {MOON} run setblock 39 150 33 minecraft:torch"]
    # Exact recipe/operation supplies in a disclosed chest, never asserted as crafted.
    supplies = [("minecraft:water_bucket", 1), ("minecraft:redstone", 5), ("minecraft:coal", 16),
                (NS + ":rocket_fuel_cell", 16), (NS + ":oxygen_canister", 16), ("minecraft:iron_ingot", 32),
                (NS + ":space_suit_helmet", 1), (NS + ":space_suit_chestplate", 1),
                (NS + ":space_suit_leggings", 1), (NS + ":space_suit_boots", 1), (NS + ":linker", 1),
                (NS + ":satellite_chassis", 1), (NS + ":satellite_solar_module", 1),
                (NS + ":data_storage_unit", 1), (NS + ":satellite_control_chip", 1)]
    chest = ",".join(f'{{Slot:{i}b,id:"{item}",Count:{count}b}}' for i, (item, count) in enumerate(supplies))
    place("supplies", (4, 150, 8), "minecraft:chest{Items:[" + chest + "]}")
    for i, device in enumerate(devices):
        if device["name"] in ("black_hole_generator", "orbital_laser_drill", "laser_target",
                              "elevator_anchor", "elevator_terminal"):
            at = " ".join(map(str, device["pos"]))
            identity = f"42700000-0000-4000-8000-{i + 100:012d}"
            for field, value in (("device_id", uuid_nbt(identity)), ("owner_id", uuid_nbt(OWNER))):
                commands.append(f"execute in {device['dimension']} run data modify block {at} "
                                f"endgame.{field} set value {value}")
            device.update(owner=OWNER, device_id=identity)
    commands.append("arce station admin dump")
    return commands, devices, supplies


ROCKET = f"@e[type={NS}:rocket,limit=1]"
ROOM_STATUS = f"execute as {PLAYER} run arce atmosphere status"
SUIT_PATH = f"entity {PLAYER} Inventory[{{Slot:102b}}].tag.arce_space_suit_oxygen"
MOON_LANDING = "0 32 31 79"  # Block corners of the chunks around the legal rocket's column (10, 50).
WARP_REFUSAL = f"target={CYGNUS} class=INTERSTELLAR"


def require(command, expect, attempts=1, interval=0.5, timeout=1):
    """A bounded readback gate; the harness stops the request with PRECONDITION_FAILED when it is not met."""
    return dict(op="require", command=command, expect=expect, attempts=attempts, interval=interval, timeout=timeout)


def screen_open():
    return dict(op="require_screen", open=True, timeout=5)


def right_click(viewport):
    return dict(op="click", x=viewport[0] // 2, y=viewport[1] // 2, right=True, settle=1)


def prelude():
    """Start from a known state: no held Shift, no open screen (ESC only if one is open), a living player."""
    return [dict(op="keyup", key="SHIFT"), dict(op="close_screen"),
            require(f"execute if entity @e[type=minecraft:player,name={PLAYER}]", "Test passed", attempts=3)]


def aim(stand, target, dim=OW):
    """Teleport to stand and look from the eye (1.62 above the feet) at target."""
    x, y, z = target
    return s(f"execute in {dim} run tp {PLAYER} {stand[0]} {stand[1]} {stand[2]} "
             f"facing {x} {y - 1.62:.2f} {z}", settle=0.8)


def open_gated(viewport, pos, dim=OW):
    return [*open_screen(viewport, pos, dim), screen_open()]


def rocket_data(path, expect, attempts=1, interval=0.5):
    return require(f"data get entity {ROCKET} RocketEntityData.flight_data.{path}", "entity data: " + expect,
                   attempts=attempts, interval=interval)


def suit_ready():
    """A worn chest piece whose oxygen is present and not zero. A failed first test prints nothing at all."""
    return require(f"execute if data {SUIT_PATH} unless data {SUIT_PATH}{{oxygen_units:0}}", "Test passed")


def room(expect, attempts=30):
    return require(ROOM_STATUS, expect, attempts=attempts, interval=0.5)


def batches():
    """A request per checklist step; the index holds metadata forbidden by the wire schema."""
    requests, index = {}, []
    def add(name, row, step, ops, *, prerequisites="Joined fixture copy; the request closes an open screen itself",
            mode="dedicated"):
        request = {"id": name, "ops": ops}
        validate_request(request, "launch", [PLAYER])
        if len(json_bytes(request)) > 256 * 1024:
            raise ValueError("Request exceeds harness byte bound")
        requests["batches/" + name + ".json"] = json_bytes(request)
        index.append(dict(file="batches/" + name + ".json", row=row, step=step, mode=mode,
                          prerequisites=prerequisites, executed=False))
    for viewport in VIEWPORTS:
        suffix = viewport_id(viewport)
        add("core-00-" + suffix, "I01", "2: combustion generator operation", [
            *prelude(), s(f"item replace entity {PLAYER} hotbar.0 with minecraft:coal 16"),
            *open_gated(viewport, (6, 150, 6)), click(viewport, "combustion", "hot0"),
            click(viewport, "combustion", "fuel"), shot("i01-generator-" + suffix), dict(op="close_screen"),
            s("data get block 6 150 6")])
        add("core-01-" + suffix, "I01/A02", "2/3: first generator and electrolyzer batch", [
            *prelude(),
            s(f"gamemode survival {PLAYER}"), s(f"item replace entity {PLAYER} hotbar.0 with minecraft:water_bucket"),
            s(f"item replace entity {PLAYER} hotbar.1 with minecraft:redstone 5"),
            s(f"item replace entity {PLAYER} hotbar.2 with {NS}:empty_canister 2"),
            look((40, 150, 34)), key("1"), right_click(viewport),
            *open_gated(viewport, (40, 150, 34)), click(viewport, "electrolyzer", "hot2"),
            click(viewport, "electrolyzer", "input"), click(viewport, "electrolyzer", "hot1"),
            click(viewport, "electrolyzer", "charge"), {"op": "sleep", "seconds": 12},
            shot("a02-electrolyzer-" + suffix), dict(op="close_screen"), s("data get block 40 150 34")])
        # hover: the layout's control under which the screen draws a tooltip, or None when it draws none.
        for name, pos, layout, hover in (("rolling", (20, 150, 20), "rolling", "energy"),
                                         ("precision", (30, 150, 20), "precision", "energy"),
                                         ("lathe", (42, 151, 20), "classic", None),
                                         ("terminal", (6, 150, 70), "terminal", "energy"),
                                         ("builder", (12, 150, 70), "builder", "energy")):
            hovering = [] if hover is None else [dict(click(viewport, layout, hover), op="move"),
                                                 shot("a13-hover-" + name + "-" + suffix)]
            add("core-screen-" + name + "-" + suffix, "A13/A17/J21",
                "read current screen and hover its energy gauge" if hover else
                "read current screen; ClassicControllerScreen draws no tooltip, so there is no hover capture", [
                    *prelude(), *open_gated(viewport, pos), shot("a13-" + name + "-" + suffix), *hovering,
                    dict(op="close_screen")])
        add("core-rocket-invalid-" + suffix, "A02/J20", "9/7: invalid source is refused", [
            *prelude(), *open_screen(viewport, (26, 150, 50)),
            require("data get block 26 150 50 RocketAssemblerData.code", 'block data: "MISSING_SEAT"', attempts=10),
            shot("a02-invalid-rocket-" + suffix), s("data get block 26 150 50")],
            prerequisites="None. The assembler has no screen: a right-click validates and reports in the action bar. "
            "MISSING_SEAT is C6's code for this source (stone in place of the guidance computer).")
        add("core-rocket-assemble-" + suffix, "A02/I01", "10/4: validate and assemble the legal source", [
            *prelude(), *open_screen(viewport, (10, 150, 50)),
            require("data get block 10 150 50 RocketAssemblerData.code", 'block data: "SUCCESS"', attempts=10),
            # Sneak and use with an empty hand is the assembler's assemble action.
            dict(op="keydown", key="SHIFT", settle=0.5), right_click(viewport),
            dict(op="keyup", key="SHIFT", settle=0.5),
            rocket_data("state", '"ASSEMBLED"', attempts=20), shot("a02-assembled-" + suffix),
            s(f"data get entity {ROCKET} RocketEntityData.flight_data"), s("data get block 10 150 50")],
            prerequisites="The legal source at (10,151..155,50) is still blocks (no rocket assembled from it yet).")
        fuelling = []
        for _ in range(4):
            # Insert one cell, let its 500 units transfer (25 per tick), then take the returned item with an empty hand.
            fuelling += [key("1"), right_click(viewport), {"op": "sleep", "seconds": 1.5}, key("9"), right_click(viewport)]
        add("core-rocket-fuel-" + suffix, "A02/I01", "11/4: fuel the assembled rocket through the fuel loader", [
            *prelude(), rocket_data("fuel.capacity", "2000L"),
            s(f"item replace entity {PLAYER} hotbar.0 with {NS}:rocket_fuel_cell 4"),
            s(f"item replace entity {PLAYER} hotbar.8 with minecraft:air"), look((12, 150, 53)), *fuelling,
            rocket_data("fuel.amount", "2000L", attempts=10), shot("i01-rocket-fueled-" + suffix),
            s(f"data get entity {ROCKET} RocketEntityData.flight_data"), s("data get block 12 150 53")],
            prerequisites="core-rocket-assemble passed: an assembled rocket owned by this player stands within "
            "6 blocks of the fuel loader (12,150,53). The four fuel cells are a disclosed grant.")
        add("core-suit-" + suffix, "A02/A17", "4: wear the suit and refill it from two oxygen canisters", [
            *prelude(),
            *(s(f"item replace entity {PLAYER} armor.{slot} with {NS}:space_suit_{piece}")
              for slot, piece in (("head", "helmet"), ("chest", "chestplate"), ("legs", "leggings"), ("feet", "boots"))),
            s(f"item replace entity {PLAYER} hotbar.0 with {NS}:oxygen_canister 2"),
            # Look at the sky so that the use action reaches the canister and no block.
            s(f"execute in {OW} run tp {PLAYER} 4.5 150 4.5 0 -60", settle=0.8), key("1"),
            right_click(viewport), right_click(viewport),
            require(f"data get {SUIT_PATH}.oxygen_units", "entity data: 2000", attempts=5),
            shot("a02-suit-" + suffix)],
            prerequisites="None. The four suit pieces and two oxygen canisters are disclosed grants; the refill is "
            "the canister's normal use action.")
        add("core-room-" + suffix, "A02/A17", "4-8: supply the room, then open and restore its boundary", [
            *prelude(), suit_ready(),
            s(f"item replace entity {PLAYER} hotbar.0 with minecraft:coal 16"),
            s(f"item replace entity {PLAYER} hotbar.1 with {NS}:oxygen_canister 3"),
            s(f"item replace entity {PLAYER} hotbar.2 with minecraft:glass 1"),
            s(f"item replace entity {PLAYER} hotbar.8 with minecraft:air"),
            # Fuel the generator beside the vent through its own screen.
            aim((34.5, 151, 33.5), (33.5, 151, 34.5), MOON), key("9"), right_click(viewport), screen_open(),
            click(viewport, "combustion", "hot0"), click(viewport, "combustion", "fuel"), dict(op="close_screen"),
            # Three canisters into the vent by hand: a fourth is refused once the vent has used a unit.
            aim((34.5, 151, 33.5), (34.5, 151, 34.5), MOON), key("2"),
            right_click(viewport), right_click(viewport), right_click(viewport),
            room("Atmosphere=BREATHABLE vents=1 providers=1"), shot("a02-room-" + suffix),
            # Break the glass cell of the south wall with an empty hand.
            aim((34.5, 151, 34.5), (34.5, 152.5, 36), MOON), key("9"),
            dict(op="click", x=viewport[0] // 2, y=viewport[1] // 2, hold=1.5, settle=0.5),
            room("Atmosphere=VACUUM"), shot("a02-room-open-" + suffix),
            # Place the glass back on the top face of the wall block below the gap.
            aim((34.5, 151, 34.5), (34.5, 152, 36.5), MOON), key("3"), right_click(viewport),
            room("Atmosphere=BREATHABLE vents=1 providers=1"), shot("a02-room-closed-" + suffix),
            s(f"execute in {MOON} run data get block 34 150 34"),
            s(f"execute in {MOON} run data get block 33 150 34")],
            prerequisites="core-suit passed (the request requires a worn suit with oxygen). The coal, three oxygen "
            "canisters and one glass block are disclosed grants; the vent and the generator are placed empty and are "
            "supplied here by normal use actions. Compare outside vacuum separately; this is not full A02 coverage.")
        # Keep launch/cancel separate: an arbitrary second click could cancel the countdown.
        add("core-flight-plan-" + suffix, "A02/I01/J20", "12-16/4: board and request Moon flight", [
            *prelude(), rocket_data("fuel.amount", "2000L"), suit_ready(),
            s(f"execute in {MOON} run forceload add {MOON_LANDING}"),
            s(f"item replace entity {PLAYER} hotbar.8 with minecraft:air"),
            s(f"execute as {ROCKET} at @s run tp {PLAYER} ~ ~ ~3 facing ~ ~0.9 ~", settle=0.8),
            key("9"), right_click(viewport), screen_open(),
            click(viewport, "flight", "moon"), click(viewport, "flight", "board"),
            shot("a02-flight-boarded-" + suffix), click(viewport, "flight", "launch"),
            rocket_data("state", '"COUNTDOWN"', attempts=8, interval=0.25),
            shot("a02-flight-intent-" + suffix)],
            prerequisites="core-rocket-fuel and core-suit passed (both are required by readback). The request loads "
            "the Moon landing chunks first and never repeats the launch click: the launch and cancel buttons share "
            "their bounds. If the launch is refused the request stops, and core-flight-observe then stops as well.")
        add("core-flight-observe-" + suffix, "A02/I01/J20", "16/4: flight and landing", [
            *prelude(),
            require(f"data get entity {PLAYER} Dimension", f'entity data: "{MOON}"', attempts=40),
            key("F5"), rocket_data("state", '"LANDED"', attempts=40), shot("a02-flight-" + suffix),
            key("F5"), key("F5"),
            s(f"data get entity {PLAYER} Dimension"), s(f"data get entity {PLAYER} Pos"),
            s(f"data get entity {ROCKET} RocketEntityData.flight_data"),
            s(f"execute in {MOON} run forceload remove {MOON_LANDING}")],
            prerequisites="core-flight-plan passed (the request requires the player on the Moon and then a landed "
            "rocket). Use the same console's Earth button for a return, preserving fuel ledgers.")
        add("core-station-" + suffix, "A06/A13/A17/I01",
            "station controls; the inter-system quote is refused for lack of warp energy", [
            *prelude(), suit_ready(), s("arce station admin dump"), *open_gated(viewport, (6, 128, 6), SPACE),
            shot("a13-station-warp-" + suffix), click(viewport, "warp", "quote"),
            dict(op="expect_log", pattern="action=request code=WARP_INSUFFICIENT_ENERGY", timeout=10),
            dict(op="expect_log", pattern=WARP_REFUSAL, timeout=10),
            shot("i01-warp-quote-" + suffix), dict(op="close_screen")],
            prerequisites="core-suit passed. The fixture station is an administrative grant with no warp energy. "
            "The controller opens on its first target, Cygnus X-1, and this request does not change it: its intent "
            "is the refusal WARP_INSUFFICIENT_ENERGY for the inter-system quote. A warp-ready station is package "
            "K08B's subject, not this fixture's.")
        add("core-railgun-" + suffix, "I01/H07", "6: select receiver and launch a disclosed 32-iron payload", [
            *prelude(), s(f"execute in {MOON} run forceload add 76 76"),
            s('data modify block 76 150 76 endgame.input.Items set value '
              '[{Slot:0,id:"minecraft:iron_ingot",Count:32b}]'),
            *open_gated(viewport, (76, 150, 76)), click(viewport, "railgun", "next"),
            shot("i01-railgun-before-" + suffix), click(viewport, "railgun", "launch"),
            shot("i01-railgun-after-" + suffix), dict(op="close_screen"),
            s("data get block 76 150 76"), s("arce endgame transfer list"),
            s(f"execute in {MOON} run data get block 76 150 76"),
            s(f"execute in {MOON} run forceload remove 76 76")])
        add("sp-pause-" + suffix, "E07", "9: integrated pause branch (C22)", [
            s(f"execute in {NS}:mars run tp {PLAYER} 0 180 0"),
            s(f"gamemode creative {PLAYER}"), key("F3"), shot("e07-before-pause-" + suffix),
            key("ESC"), {"op": "sleep", "seconds": 25}, shot("e07-paused-" + suffix),
            key("ESC"), {"op": "sleep", "seconds": 25}, shot("e07-resumed-" + suffix), key("F3")],
            mode="singleplayer", prerequisites="Integrated copy selected and joined; cheats enabled; not opened "
            "to LAN; pauseOnLostFocus=true; normal ambience volume and video/audio capture. "
            "Client tick/audio spacing requires video review, not wall-time or server-menu inference.")
    return requests, index


CHANGES = [
    "Every core request starts by releasing Shift, closing an open screen (ESC only when one is open) and "
    "requiring a living player, and gates its clicks on an open screen or a server readback.",
    "Room: the oxygen vent and its generator are placed empty (revision 1's data merge made the vent's saved "
    "root invalid for C6); core-room fuels the generator and fills the vent by hand, and the boundary cell "
    "(34,152,36) is glass that the player breaks and places back.",
    "Rocket: core-rocket-assemble validates and then assembles with sneak and use; the new core-rocket-fuel "
    "loads four fuel cells through the fuel loader at (12,150,53).",
    "Suit: the new core-suit puts on the four pieces and refills the chest piece from two canisters.",
    "Lathe: the player stands on the floor to aim at the controller one block up; the classic controller "
    "screen draws no tooltip, so core-screen-lathe has no hover capture.",
    "Hover: the rolling machine, precision assembler, satellite terminal and satellite builder requests "
    "move the pointer to the energy gauge, where each screen draws a tooltip.",
    "Station: core-station no longer presses the next-target button; its intent is the refusal "
    "WARP_INSUFFICIENT_ENERGY for the inter-system quote to Cygnus X-1.",
    "Flight: core-flight-plan requires fuel and suit, loads the Moon landing chunks and requires COUNTDOWN "
    "after one launch click; core-flight-observe requires the Moon and LANDED, and restores first person.",
    "J21 recheck B-3: the vent merge no longer names schema_version.",
]

# The order in which one dedicated session runs the core route; each request gates on the ones before it.
CORE_ROUTE = ("core-00", "core-01", "core-screen-rolling", "core-screen-precision", "core-screen-lathe",
              "core-screen-terminal", "core-screen-builder", "core-rocket-invalid", "core-rocket-assemble",
              "core-rocket-fuel", "core-suit", "core-room", "core-flight-plan", "core-flight-observe",
              "core-station", "core-railgun")


def recheck_manifest(root=ROOT):
    """Keep every section-4 recheck visible, including dependencies not prepared here."""
    source = root / "docs/work/v1.8.0-root-queue/records/J21-FINDING-MAP.md"
    section = source.read_text(encoding="utf-8").split("## 4.", 1)[1].split("## 5.", 1)[0]
    rows = []
    for line in section.splitlines():
        if not line.startswith("| ") or line.startswith("| ID "):
            continue
        cells = [v.strip() for v in line.strip("|").split("|")]
        if len(cells) != 4:
            raise ValueError("Finding-map row shape changed")
        rows.append(dict(finding=cells[0], procedure=cells[1], historical_batch=cells[2],
                         checklist=cells[3], executed=False))
    if len(rows) != 21:
        raise ValueError("Review changed finding map before regenerating")
    return rows


def recheck_batches(rows):
    """Regenerated readbacks and actions, with external state requirements explicit.

    Several findings require restart/config edits or another account. Requests do
    not invent these capabilities; the index declares what the operator must do
    between requests. No historical pixel or regex is reused.
    """
    result, index = {}, []
    rocket = f"@e[type={NS}:rocket,limit=1]"
    for viewport in VIEWPORTS:
        suffix = viewport_id(viewport)
        for row in rows:
            finding = row["finding"].lower().replace("-", "-")
            name = "j21-" + finding + "-" + suffix
            before = [s(f"data get entity {PLAYER} Pos"), s(f"data get entity {PLAYER} Inventory")]
            ops, requirement = [], "No screen open; use a fresh run copy for destructive variants. "
            if row["finding"] == "A-F10c":
                ops = [s(f"data get entity {rocket}"), shot(name)]
                requirement += "Assemble the legal rocket first; inspect its actual entity name."
            elif row["finding"] == "A-F10a":
                ops = [s(f"execute in {MOON} run tp {PLAYER} 34.5 151 33.5"), shot(name + "-hud"),
                       key("F3"), shot(name + "-debug"), key("F3"), shot(name + "-restored")]
                requirement += "Run core-suit and core-room first (suit worn and filled, room supplied); "
                requirement += "capture HUD before opening F3."
            elif row["finding"] == "A-F10d":
                ops = [*open_screen(viewport, (26, 150, 50)), shot(name)]
            elif row["finding"] in ("A-F6", "3-6"):
                pos = (6, 150, 70)
                ops = [*open_screen(viewport, pos)]
                if row["finding"] == "3-6":
                    ops += [click(viewport, "terminal", "launch"), {"op": "sleep", "seconds": 11},
                            shot(name + "-before-claim"), click(viewport, "terminal", "claim")]
                    requirement += "Terminal has a bound data satellite, Mars target and claimable discovery state."
                ops += [shot(name), key("ESC"), s("data get block 6 150 70")]
                if row["finding"] == "A-F6":
                    ops += [*open_screen(viewport, (12, 150, 70)), shot(name + "-builder"), key("ESC")]
                    requirement += "Repeat idle and after a legitimate satellite build."
            elif row["finding"] in ("2-5", "B-3"):
                ops = [s(f"execute in {MOON} run tp {PLAYER} 34.5 151 33.5"),
                       s(f"execute in {MOON} run data get block 34 150 34"), shot(name)]
                if row["finding"] == "B-3":
                    # Supply rate variant is separate from the reusable room fixture. The merge names no
                    # schema_version: the saved root keeps its own schema and its scrubbers key, where a
                    # schema_version:1 would make C6 quarantine the whole root as invalid data (W443).
                    ops = [s(f"execute in {MOON} run data merge block 34 150 34 "
                             "{arce_oxygen_vent:{oxygen_canisters:0,empty_canisters:0,"
                             "oxygen_units:1000,energy:40000,oxygen_phase:0}}"), *ops,
                           {"op": "sleep", "seconds": 100},
                           s(f"execute in {MOON} run data get block 34 150 34"), shot(name + "-after")]
                    requirement += "No adjacent power source fuel (run on a copy where core-room has not fuelled "
                    requirement += "the generator). Time the supplied 40000 FE with no replenishment."
                else:
                    requirement += "Run core-room first: the room is supplied by that request, not by the fixture. "
                    requirement += "Run before and after two save/stop/restarts of the same copy; record lit "
                    requirement += "inside (34,151,33) and outdoor (39,150,33) torches separately."
            elif row["finding"] in ("2-7", "2-8"):
                ops = [*open_screen(viewport, (8, 150, 53)), shot(name), key("ESC"),
                       s("data get block 8 150 53")]
                requirement += "Bind monitor with linker to the legal rocket. "
                if row["finding"] == "2-8":
                    requirement += "Fly that logical rocket Moon/Overworld round trip before this readback."
                else:
                    ops += [*open_screen(viewport, (14, 150, 53)), shot(name + "-loader"), key("ESC")]
                    requirement += "Also select the monitor with the linker; capture its action-bar label."
            elif row["finding"] == "3-5":
                ops = [s(f"item replace entity {PLAYER} hotbar.0 with {NS}:lathe"),
                       s(f"setblock 70 149 50 {NS}:machine_casing"),
                       s(f"tp {PLAYER} 70.5 150 47.5 facing 70.5 148.38 50.5"), key("1"),
                       dict(op="click", x=viewport[0] // 2, y=viewport[1] // 2, right=True, settle=1), shot(name),
                       s(f"execute if block 70 150 50 {NS}:lathe run say K16_CASING_PLACEMENT")]
                requirement += "Sneaking is off; verify actual placement without a Shift key action."
            elif row["finding"] == "B-2":
                ops = [s(f"setblock 70 150 54 minecraft:obsidian"),
                       s(f"item replace entity {PLAYER} hotbar.0 with {NS}:jackhammer"),
                       look((70, 150, 54)), key("1"),
                       dict(op="click", x=viewport[0] // 2, y=viewport[1] // 2, hold=2.5, settle=0.5),
                       shot(name), s(f"data get entity {PLAYER} SelectedItem")]
                requirement += "Restart this isolated server with equipment.classicEnabled=false; client defaults."
            elif row["finding"] == "8-3":
                ops = [click(viewport, "warp", "quote"), shot(name + "-quote"),
                       click(viewport, "warp", "confirm"), {"op": "sleep", "seconds": 3},
                       click(viewport, "warp", "cancel"), shot(name)]
                requirement += "Warp controller already open, valid paid inter-system destination selected."
                before = []  # A single-player server action must not type into an open screen.
            elif row["finding"] == "A-F4":
                ops = [s(f"gamemode creative {PLAYER}"),
                       s(f"execute in {SPACE} run tp {PLAYER} 1044.5 126 16.5 facing 1044.5 128.38 20.5"),
                       shot(name + "-below"),
                       s(f"execute in {SPACE} run tp {PLAYER} 1044.5 135 16.5 facing 1044.5 128.38 20.5"),
                       shot(name + "-above")]
                requirement += "Fuel and run the formed Cygnus black-hole generator first; verify the ring is active."
            elif row["finding"] == "A-F5":
                for layout, pos, dim in (("railgun", (76, 150, 76), OW),
                                          ("blackhole", (1044, 128, 20), SPACE),
                                          ("drill", (30, 128, 30), SPACE),
                                          ("target", (90, 150, 40), OW)):
                    ops += [*open_screen(viewport, pos, dim), shot(name + "-" + layout),
                            dict(click(viewport, layout, "status"), op="move"),
                            shot(name + "-" + layout + "-hover"), key("ESC")]
                requirement += "Arrange the longest current refusal/reason on each device; run both languages."
            elif row["finding"] == "A-F7":
                non_owner = "42700000-0000-4000-8000-000000000099"
                ops = [s("data modify block 76 150 76 endgame.owner_id set value " + uuid_nbt(non_owner)),
                       *open_screen(viewport, (76, 150, 76)), click(viewport, "railgun", "launch"),
                       shot(name + "-railgun"), key("ESC"),
                       s("data get block 76 150 76"),
                       s("data modify block 76 150 76 endgame.owner_id set value " + uuid_nbt(OWNER)),
                       *open_screen(viewport, (20, 128, 6), SPACE), click(viewport, "elevator", "ride"),
                       shot(name + "-ride"), key("ESC")]
                requirement += "Disposable variant assigns railgun to a different fixture UUID, not a premium "
                requirement += "account. Elevator pair must be bound/powered and ready before Ride."
            elif row["finding"] in ("4-4", "7-6c"):
                ops = [*open_screen(viewport, (20, 128, 6), SPACE), click(viewport, "elevator", "bind"),
                       shot(name + "-bind")]
                if row["finding"] == "4-4":
                    ops += [click(viewport, "elevator", "ride"), shot(name + "-ride")]
                    requirement += "Anchor (60,150,76) is selected; valid same-body pair, paid energy and "
                    requirement += "durable registrations. Retain native bind/ride_request/ride_commit logs."
                else:
                    requirement += "Select an anchor on a different body than this station's orbit."
                ops += [key("ESC")]
            elif row["finding"] == "A-F3":
                ops = [key("ESC"), s(f"execute as {rocket} at @s run tp {PLAYER} ~20 ~ ~ facing ~ ~ ~"),
                       shot(name + "-ascent-start"), {"op": "sleep", "seconds": 1},
                       shot(name + "-ascent-next"), s(f"execute as {rocket} run data get entity @s Pos")]
                requirement += "A second real client/rider launches the fueled rocket; this foreground client "
                requirement += "is the ground observer. Do not teleport the seated rider to create a viewpoint."
            else:
                # These need other packages' runtime scenes or host-side config editing.
                # Retain a real bounded evidence checkpoint, not a fabricated command receipt.
                ops = [shot(name), {"op": "status", "tail": 12}]
                requirement += "Operator performs the exact procedure in rechecks.json; this batch is "
                requirement += "the evidence checkpoint only, NOT an automated implementation of that procedure."
            request = dict(id=name, ops=before + ops)
            validate_request(request, "launch", [PLAYER])
            path = "batches/" + name + ".json"
            result[path] = json_bytes(request)
            index.append(dict(file=path, row="J21", finding=row["finding"], checklist=row["checklist"],
                              step=row["procedure"], prerequisites=requirement, executed=False,
                              automation="checkpoint-only" if len(ops) == 2 and ops[-1]["op"] == "status" else "partial-action"))
    return result, index


def generate(output, jar_path=JAR, root=ROOT):
    if file_hash(jar_path) != JAR_HASH:
        raise ValueError("Not the pinned C6 JAR")
    with zipfile.ZipFile(jar_path) as jar:
        commands, devices, supplies = core_world(jar)
        recipe_names = sorted(n for n in jar.namelist() if n.startswith(f"data/{NS}/recipes/") and n.endswith(".json"))
        # Keep actual recipe definitions/counts, not inferred survival-acquisition claims.
        recipes = {n: json.loads(jar.read(n)) for n in recipe_names}
    controls = control_manifest(root)
    blobs, steps = batches()
    rechecks = recheck_manifest(root)
    more, more_steps = recheck_batches(rechecks)
    blobs.update(more)
    steps.extend(more_steps)
    manifest = dict(schema_version=1, package="K16", revision=REVISION, core_route=list(CORE_ROUTE),
                    changes_from_revision_1=CHANGES, candidate_commit=CANDIDATE, jar_sha256=JAR_HASH,
                    player=dict(name=PLAYER, offline_uuid=OWNER, premium_identity=False,
                                persisted_playerdata=False, spawn=[4, 150, 4]),
                    dimensions=[OW, MOON, SPACE], devices=devices, supplies=supplies,
                    grants="All placed structures, chest contents and railgun energy are fixture grants, and so "
                           "are the items the core requests put into the player's hands or armour slots (coal, "
                           "canisters, fuel cells, glass, the four suit pieces). The vent, its generator, the suit "
                           "and the rocket are then supplied through normal use actions. None proves I01 survival "
                           "crafting or the owner-selected O4 subset.",
                    checklist_rows=["A02", "A06", "A13", "A17", "E07", "I01", "J21"],
                    client_runs=False, required_gates_approved=False)
    blobs.update({"world-commands.txt": ("\n".join(commands) + "\n").encode("ascii"),
                  "manifest.json": json_bytes(manifest), "controls.json": json_bytes(controls),
                  "batch-index.json": json_bytes(steps), "rechecks.json": json_bytes(rechecks),
                  "candidate-recipes.json": json_bytes(recipes)})
    output.mkdir(parents=True, exist_ok=False)
    for name, raw in sorted(blobs.items()):
        (output / name).parent.mkdir(parents=True, exist_ok=True)
        (output / name).write_bytes(raw)
    return manifest


def tree_hashes(directory):
    result = {}
    for path in sorted(directory.rglob("*")):
        if path.is_symlink() or getattr(path, "is_junction", lambda: False)():
            raise ValueError(f"Linked fixture member: {path}")
        if path.is_file():
            result[path.relative_to(directory).as_posix()] = file_hash(path)
    return result


def prepare_singleplayer(output, cell_path):
    """A separate cheat-enabled save; never change the frozen dedicated source world."""
    from scripts import loader_cut_nbt as nbt
    if (output / "SHA256SUMS.txt").exists():
        raise ValueError("Sealed fixture is immutable")
    cell = json.loads(cell_path.read_text(encoding="utf-8-sig"))
    source = output / "world"
    source_hashes = tree_hashes(source)
    template = output / "p9-client"
    if template.exists():
        raise ValueError("Refusing occupied P9 template")
    # The cell is an isolated W400 template, not an owner's signed-in launcher profile.
    client_source = Path(cell["client_game_dir"])
    client_hashes = tree_hashes(client_source)
    shutil.copytree(client_source, template)
    options_path = template / "options.txt"
    options = options_path.read_text(encoding="utf-8").splitlines() if options_path.exists() else []
    settings = {"pauseOnLostFocus": "true", "soundCategory_master": "1.0", "soundCategory_ambient": "1.0",
                "forceUnicodeFont": "false"}
    options = [line for line in options if line.partition(":")[0] not in settings]
    options_path.write_text("\n".join(options + [f"{k}:{v}" for k, v in settings.items()]) + "\n", encoding="utf-8")
    save = template / "saves/K16-P9"
    shutil.copytree(source, save)
    level = save / "level.dat"
    name, root = nbt.read_gzip(level)
    data = root.value["Data"].value
    before = {key: nbt.plain(data[key]) if key in data else None for key in ("allowCommands", "GameType", "LevelName")}
    data["allowCommands"] = nbt.Tag(nbt.BYTE, 1)
    data["GameType"] = nbt.Tag(nbt.INT, 1)
    data["LevelName"] = nbt.Tag(nbt.STRING, b"K16 P9")
    nbt.write_gzip(level, name, root)
    # Harness runtime validates the candidate independently in each mods directory.
    candidate = template.resolve() / "mods" / Path(cell["candidate"]["path"]).name
    # W400's inventory cell has extra audit fields, whereas the tracked client
    # harness requires its exact nine-field input schema. Project, never change
    # either the original cell or the harness's strict validation.
    keys = ("schema_version", "forge", "jei", "runtime_root", "server_dir", "client_game_dir",
            "version_id", "candidate", "jei_jar")
    cell = {key: cell[key] for key in keys}
    cell["client_game_dir"] = str(template.resolve())
    cell["candidate"] = dict(path=str(candidate), sha256=JAR_HASH)
    (output / "p9-cell.json").write_bytes(json_bytes(cell))
    sessions = []
    for language in ("en_us", "zh_cn"):
        for viewport in VIEWPORTS:
            suffix = language + "-" + viewport_id(viewport)
            session = dict(schema_version=1, mode="launch", cell="p9-cell.json", run_dir="runs/p9-" + suffix,
                           java="C:/Program Files/Java/jdk-17.0.7/bin/java.exe", singleplayer=True,
                           time_limit_seconds=1800, input_interval_seconds=.05,
                           clients=[dict(player_name=PLAYER, language=language, gui_scale=viewport[2],
                                         window_size=list(viewport[:2]))],
                           server={"online-mode": False, "white-list": False, "enforce-whitelist": False,
                                   "server-ip": "127.0.0.1", "server-port": 25565, "whitelist": [],
                                   "ops": [], "jvm_properties": {}, "max_heap": "3G"})
            path = "p9-session-" + suffix + ".json"
            (output / path).write_bytes(json_bytes(session))
            sessions.append(path)
    if source_hashes != tree_hashes(source) or client_hashes != tree_hashes(client_source):
        raise ValueError("P9 preparation changed a source")
    report = dict(source_world_unchanged=True, client_source_unchanged=True,
                  metadata_before=before, metadata_after=dict(allowCommands=1, GameType=1, LevelName="K16 P9"),
                  world_hashes=tree_hashes(save), sessions=sessions, launched=False,
                  preconditions="Select K16 P9 through Singleplayer, not LAN. PauseOnLostFocus=true and "
                  "normal Ambient/Environment volume. Fullscreen 1920x1080/scale4 still needs actual "
                  "mode selection and effective-scale confirmation; session dimensions alone do not prove it.")
    (output / "p9-preparation.json").write_bytes(json_bytes(report))
    return report


def seal(output):
    target = output / "SHA256SUMS.txt"
    if target.exists():
        raise ValueError("Do not overwrite sealed evidence")
    members = tree_hashes(output)
    target.write_text("".join(f"{value}  {name}\n" for name, value in members.items()), encoding="ascii", newline="\n")


def vent_root_current(name, lines):
    """C6 saves a working vent at schema 2 with a scrubbers key; any other root is one it has set aside."""
    if name != "oxygen_vent":
        return True
    text = "\n".join(lines)
    return "arce_oxygen_vent: {" in text and "schema_version: 2" in text and "scrubbers: {" in text


def abort_owned(process, output):
    """Retain the caller's lock if its directly owned Java cannot be confirmed dead."""
    try:
        if process.process.poll() is None:
            subprocess.run(["taskkill", "/PID", str(process.process.pid), "/T", "/F"],
                           check=True, capture_output=True, timeout=30)
        process.abort()
        if process.process.poll() is None:
            raise RuntimeError("Owned Java is still running")
    except BaseException:
        (output / "retain-heavy-jvm-lock.txt").write_text(
            f"W427 cleanup was not confirmed for owned Java pid={process.process.pid}. "
            "Do not release the lock or delete its copied server.\n", encoding="ascii")
        raise


def build(output, cell_path, java, lock_owner):
    """Console-only builder. Caller owns the machine lock until this returns."""
    from scripts import run_dedicated_server_smoke as smoke
    lock = EVIDENCE / "locks/heavy-jvm/owner.txt"
    if not lock_owner or not lock.read_text(encoding="utf-8-sig").startswith(lock_owner):
        raise ValueError("Heavy-JVM ownership is required")
    if (output / "SHA256SUMS.txt").exists():
        raise ValueError("Sealed fixture is immutable")
    if min(shutil.disk_usage(p).free for p in ("C:/", "D:/")) < 10 * 1024**3:
        raise ValueError("Native build requires 10 GiB on both drives")
    cell = json.loads(cell_path.read_text(encoding="utf-8-sig"))
    source = Path(cell["server_dir"])
    source_hashes = tree_hashes(source)
    if cell["candidate"]["sha256"] != JAR_HASH:
        raise ValueError("Cell is not C6")
    copy = output / "build-server"
    if copy.exists() or (output / "world").exists():
        raise ValueError("Refusing occupied build targets")
    process = None
    copied = False
    report = dict(status="FAILED", source=str(source), source_before=source_hashes, client_started=False,
                  builder_source_sha256=file_hash(Path(__file__)))
    try:
        shutil.copytree(source, copy, ignore=shutil.ignore_patterns("world", "logs", "crash-reports"))
        copied = True
        installed = list((copy / "mods").glob("*.jar"))
        if len(installed) != 1 or file_hash(installed[0]) != JAR_HASH:
            raise ValueError("Copied server candidate mismatch")
        properties = smoke.expected_server_properties(smoke.allocate_port(), False)
        properties.update({"level-name": "world", "level-seed": "20261006", "level-type": "minecraft:flat",
                           "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}],
                                                             "biome": "minecraft:plains"}),
                           "view-distance": "2", "simulation-distance": "2", "spawn-protection": "0"})
        (copy / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(properties.items())), encoding="ascii")
        args = copy.resolve() / "libraries/net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt"
        command = [str(java), "-Xms1G", "-Xmx3G", "@" + str(args), "nogui"]
        report.update(command=command, properties=properties, jar_sha256=file_hash(installed[0]))
        process = smoke.CapturedProcess(command, copy, output / "world-build-console.log")
        process.wait_for(smoke.READY_MARKER, 240)
        def send(value):
            start = len(process.lines)
            process.command(value)
            token = "K16_BARRIER_" + str(start)
            process.command("say " + token)
            process.wait_for(re.compile(re.escape(token)), 60, start_at=start)
            return process.lines[start:]
        # forceload requests load asynchronously. Readiness is checked, not guessed from a delay.
        for dim in (OW, MOON, SPACE):
            send(f"execute in {dim} run forceload add 0 0 95 95")
            deadline = time.monotonic() + 120
            checks = " ".join(f"if loaded {x} 149 {z}" for x in range(0, 96, 16) for z in range(0, 96, 16))
            while True:
                receipt = send(f"execute in {dim} {checks} run say K16_CHUNKS_READY")
                if any("[Server] K16_CHUNKS_READY" in line for line in receipt):
                    break
                if time.monotonic() >= deadline:
                    raise ValueError("Scene chunks not loaded")
                time.sleep(.25)
        send(f"execute in {SPACE} run forceload add 1024 0 1055 31")
        deadline = time.monotonic() + 120
        while not any("[Server] K16_BH_CHUNKS_READY" in line for line in send(
                f"execute in {SPACE} if loaded 1024 127 0 if loaded 1055 127 31 run say K16_BH_CHUNKS_READY")):
            if time.monotonic() >= deadline:
                raise ValueError("Black-hole station chunks not loaded")
            time.sleep(.25)
        for value in (output / "world-commands.txt").read_text(encoding="ascii").splitlines():
            receipt = send(value)
            if any(any(marker in line for marker in ("Unknown block", "Unknown item", "Incorrect argument",
                       "Expected whitespace", "does not have property", "Station creation failed")) for line in receipt):
                raise ValueError("Console rejected fixture command: " + value)
        # Verify actual placement/readback before freezing the source world.
        manifest = json.loads((output / "manifest.json").read_text(encoding="ascii"))
        readbacks = []
        for i, device in enumerate(manifest["devices"]):
            dim, pos = device["dimension"], " ".join(map(str, device["pos"]))
            token = f"K16_DEVICE_{i}"
            block = device["block"].split("{", 1)[0]
            receipt = send(f"execute in {dim} if block {pos} {block} run say {token}")
            if not any("[Server] " + token in line for line in receipt):
                raise ValueError(f"Missing device: {device['name']} {dim} {pos}")
            lines = send(f"execute in {dim} run data get block {pos}")
            readbacks.append(dict(name=device["name"], dimension=dim, pos=device["pos"], lines=lines))
            if not vent_root_current(device["name"], lines):
                raise ValueError("The oxygen vent's saved root is not the current schema")
        for dim in (OW, MOON, SPACE):
            send(f"execute in {dim} run forceload remove all")
        station_lines = send("arce station admin dump")
        send("save-all flush")
        process.command("stop")
        code = process.finish(180)
        process = None
        if code != 0:
            raise ValueError(f"Server exit {code}")
        log = (copy / "logs/latest.log").read_text(encoding="utf-8")
        if "/ERROR]" in log or "/FATAL]" in log:
            raise ValueError("Native server logged ERROR/FATAL")
        shutil.copytree(copy / "world", output / "world")
        report.update(status="BUILT", exit_code=code, readbacks=readbacks, stations=station_lines,
                      world_hashes=tree_hashes(output / "world"))
    finally:
        if process is not None:
            abort_owned(process, output)
        report["source_after"] = tree_hashes(source)
        report["source_unchanged"] = report["source_after"] == source_hashes
        (output / "world-build.json").write_bytes(json_bytes(report))
        if copied:
            resolved = copy.resolve()
            if resolved.parent != output.resolve() or resolved.name != "build-server":
                raise ValueError("Unsafe cleanup target")
            shutil.rmtree(resolved)
    if not report["source_unchanged"]:
        raise ValueError("Source template changed during build")
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("generate", "build", "singleplayer", "seal"))
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--candidate-jar", type=Path, default=JAR)
    parser.add_argument("--cell", type=Path, default=CELL)
    parser.add_argument("--java", type=Path, default=Path("C:/Program Files/Java/jdk-17.0.7/bin/java.exe"))
    parser.add_argument("--lock-owner", default="")
    args = parser.parse_args()
    if args.action == "generate":
        generate(args.output, args.candidate_jar)
    elif args.action == "build":
        build(args.output, args.cell, args.java, args.lock_owner)
    elif args.action == "singleplayer":
        prepare_singleplayer(args.output, args.cell)
    else:
        seal(args.output)
    print(f"K16 {args.action}: {args.output}")


if __name__ == "__main__":
    main()
