#!/usr/bin/env python3
"""Package K13 of the final campaign's preparation (TASK W402).

Reads one candidate JAR as a ZIP, never changes it, and writes under ``--out``:

* ``assets.csv``, ``lang-keys.csv``, ``lang-missing.csv``, ``sound-events.csv``: the JAR's presentation assets;
* ``classification.csv`` and ``model-families.csv``: choice C30's high-risk list and one sample per model family;
* ``screens.csv``, ``sound-producers.csv``, ``access-matrix.csv``: tables curated from the source of the same build,
  each row with source locations that this script resolves again on every run;
* ``k13-missing-resource-pack.zip`` and ``k13-long-text-pack.zip`` with ``pack-contents.csv``;
* ``summary.json`` and ``SHA256SUMS.txt``.

The JAR decides what exists. The curated tables are checked against it and against ``src/main``: a menu, a sound
event or a client-to-server message without a row, or a row whose source text is gone, is an error.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import io
import json
import re
import sys
import zipfile
from collections import defaultdict
from pathlib import Path, PurePosixPath

MOD = "advancedrocketrycommunity"
SRC = "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/"
RECORDS = "docs/work/v1.8.0-root-queue/records/"
NETWORK_PIN = "src/test/resources/network-protocols.txt"
EXEMPTIONS = "src/test/resources/menu-authority-exemptions.txt"
PACK_FORMAT = 15  # Minecraft 1.20.1 resource packs.
ZIP_TIME = (1980, 1, 1, 0, 0, 0)

# --------------------------------------------------------------------------------------------------------------
# Rules of choice C30. The record W402.md states them in words.
# --------------------------------------------------------------------------------------------------------------

#: Armour and worn-component art: any asset whose path below ``assets/<namespace>/`` starts with one of these.
ARMOUR_PREFIXES = (
    "textures/models/armor/",
    "textures/entity/suit_components",
    "models/item/space_suit_",
    "textures/item/space_suit_",
)

#: Assets that W244, W261 and W265 corrected, below ``assets/advancedrocketrycommunity/``. Textures that a listed
#: model references are added by the script.
CORRECTED = {
    "W244": ("models/spaceelevator.obj", "models/spaceelevator.mtl", "textures/models/spaceelevatorcapsule.png"),
    "W261": ("blockstates/landing_pad.json", "models/block/landing_pad.json", "models/item/landing_pad.json",
             "textures/block/rocketpad_.png", "textures/block/landing_pad.png"),
    "W265": ("models/item/basic_laser_gun.json", "textures/item/basic_laser_gun.png",
             "textures/item/basiclasergun.png"),
}

#: The missing-resource pack (checklist I07 step 15): these textures are replaced by bytes that are not a PNG, and
#: this one model by a model whose parent does not exist. Paths are below ``assets/advancedrocketrycommunity/``.
MISSING_TEXTURES = (
    ("textures/block/rocketpad_.png", "ordinary block sprite of the landing pad: no project fallback is promised"),
    ("textures/models/armor/space_suit_layer_1.png", "worn armour layer: no project fallback is promised"),
    ("textures/models/blackholegenerator.png", "formed hull: W241 names railgun_front.png as the fallback"),
    ("textures/models/railgun.png", "formed hull: W241 names the railgun's own controller face as the fallback"),
    ("textures/models/rollingmachine.png", "formed hull: W241 names lathe.png as the fallback"),
    ("textures/models/spaceelevatorcapsule.png", "elevator capsule: W244 keeps the per-rider line cage"),
)
MISSING_MODEL = ("models/block/rocket_motor.json", "ordinary baked motor model: no runtime fallback is promised")
MISSING_TEXTURE_BYTES = b"K13 missing-resource fixture: deliberately not a PNG.\n"
MISSING_PARENT = MOD + ":block/k13_missing_parent"

#: The long-text pack (checklists J12 to J16): a value shorter than this many characters is lengthened.
LONG_TEXT_CAP = 256
_FORMAT_SPECIFIER = re.compile(r"%(?:\d+\$)?[A-Za-z]|%%|%")


# --------------------------------------------------------------------------------------------------------------
# Curated tables. ``A(file, needle)`` is a source location: the first line of ``SRC + file`` containing ``needle``.
# --------------------------------------------------------------------------------------------------------------

def A(file: str, needle: str) -> tuple[str, str]:
    return (file, needle)


ANYONE = "any connected, living, non-spectator player"
VANILLA_REACH = "8 blocks from the block's centre (vanilla stillValid, distanceToSqr <= 64)"
SILENT_CLOSE = "none: the menu is not valid, the server closes it at the player's next tick; no message, no log line"
ENDGAME_REFUSAL = ("action-bar status line advancedrocketrycommunity.endgame.status_line with the stable code "
                   "(NOT_A_PLAYER, ROOT_UNAVAILABLE, WRONG_LEVEL, CHUNK_UNLOADED, DEVICE_CHANGED, TOO_FAR, "
                   "DEVICE_QUARANTINED, UNAUTHORIZED, STATION_UNAVAILABLE, SYSTEM_DISABLED, RATE_LIMITED); "
                   "no log line")
ENDGAME_OPEN = ("device owner, or operator level 2; inside a committed station also the station owner, and a "
                "member with a public view (no targets or coordinates); outside stations any player gets the public "
                "view; in an unavailable station only the owner or an operator, for WITHDRAW")
ENDGAME_ACT = ("CONFIGURE, OPERATE and WITHDRAW: device owner or operator level 2 (inside a station the owner must "
               "still be the station's owner or a member; the gravity field needs the station owner); station "
               "membership alone grants VIEW only")
ENDGAME_ANCHORS = (
    A("endgame/device/EndgameDeviceMenu.java", "public boolean stillValid(Player player)"),
    A("endgame/device/EndgameDeviceMenu.java", "public static EndgameCode openRefusal("),
    A("endgame/intent/EndgameIntentGuard.java", "public static Result check(Player player, Target target"),
    A("endgame/intent/EndgameIntentRules.java", "public static EndgameCode check(Snapshot snapshot)"),
    A("endgame/authority/EndgameAuthority.java", "public static Decision decide(Request request)"),
    A("endgame/model/EndgameLimits.java", "INTENT_DISTANCE_BLOCKS ="),
)
STATION_ANCHORS = (
    A("station/controller/StationControllerBlock.java", "StationLocalActor.visibleStationAt("),
    A("station/controller/StationControllerHolder.java", "public static Optional<Refusal> check(Probe probe)"),
    A("station/controller/StationControllerMenu.java", "public final void acceptClassicControl("),
    A("station/service/StationManager.java", "public boolean admitControllerAction(ServerPlayer player)"),
    A("station/model/StationLimits.java", "CONTROLLER_REACH ="),
)
CARGO_REFUSAL = ("at open: action-bar message with the device status key (NOT_OWNER for another player, or the "
                 "data refusal); a distant or obstructed owner gets none; afterwards the menu is closed silently")


def _endgame(menu: str, screen: str, blocks: str, keywords: tuple[str, ...], extra: str = "") -> dict:
    return dict(
        menu_class=f"endgame/{menu}", screen_class=f"client/{screen}", opened_by="block use", holders=blocks,
        keywords=keywords, open=ENDGAME_OPEN, act=ENDGAME_ACT, operator="2 (full authority)",
        distance="8.0 blocks from the device's centre (EndgameLimits.INTENT_DISTANCE_BLOCKS), at open, while open "
                 "and for every intent", chunk="getChunkNow on the device's chunk and the same device ID; never "
                 "loads a chunk", rate="per player and intent kind, the configured interval (minimum 10 ticks)",
        refusal=ENDGAME_REFUSAL, anchors=ENDGAME_ANCHORS, notes=extra)


def _station(kind: str, keywords: tuple[str, ...]) -> dict:
    return dict(
        menu_class="station/controller/StationControllerMenu", screen_class="client/station/StationControllerScreen",
        opened_by="block use", holders=kind, keywords=keywords,
        open="the station's owner, a member, or an operator (VISIT access to the station the block stands in)",
        act="navigation: the holder; every mutation is checked again by the station services (station owner or "
            "operator level 2, region, state and range)",
        operator="2", distance="8.0 blocks from the controller's centre (StationLimits.CONTROLLER_REACH)",
        chunk="the controller's chunk must already be loaded and the exact block present; nothing is loaded",
        rate="one controller mutation per player per 10 ticks (shared with the monitoring station)",
        refusal="at open: action-bar UNAUTHORIZED or NOT_IN_STATION; an invalid menu drops the intent silently; a "
                "refused mutation returns its StationManagementCode in the view packet (for example "
                "CONTROL_RATE_LIMITED); no log line",
        anchors=STATION_ANCHORS, notes="actions arrive as classic_controls intents, never as vanilla menu buttons")


def _machine(menu: str, screen: str, block: str, keywords: tuple[str, ...], chunk: str, anchors: tuple,
             act: str = "vanilla slot clicks only", notes: str = "") -> dict:
    return dict(menu_class=menu, screen_class=screen, opened_by="block use", holders=block, keywords=keywords,
                open=ANYONE + "; the block has no owner", act=act, operator="none", distance=VANILLA_REACH,
                chunk=chunk, rate="none (no button or intent)", refusal=SILENT_CLOSE, anchors=anchors, notes=notes)


MENUS: dict[str, dict] = {
    "ground_research": dict(
        menu_class="machine/classic/runtime/ResearchControllerMenu", screen_class="client/ResearchControllerScreen",
        opened_by="block use", holders="observatory, astrobody_data_processor",
        keywords=("observatory", "data processor", "ground survey", "research controller"),
        open=ANYONE + " who may interact at the position (spawn protection)",
        act="buttons 0 to 5: only the player the menu was opened for, and only the controller's research owner",
        operator="none", distance="8 blocks from the block's centre (distanceToSqr <= 64)",
        chunk="hasChunkAt at open; getChunkNow and the same block entity while open (physical())",
        rate="one button per player per 2 server ticks; at most 4,096 tracked players",
        refusal="at open: none; another player's button: ResearchStatus.UNAUTHORIZED in the controller's view; no "
                "log line",
        anchors=(A("machine/classic/runtime/ClassicControllerBlock.java", "ClassicControllerMenu.canOpen(serverPlayer"),
                 A("machine/classic/runtime/ClassicControllerMenu.java", "public static boolean canOpen("),
                 A("machine/classic/runtime/ResearchControllerMenu.java", "public boolean clickMenuButton("),
                 A("machine/classic/runtime/ResearchService.java", "boolean intent(ResearchControllerBlockEntity"),
                 A("machine/classic/runtime/ResearchService.java", "boolean allowIntent(UUID player)")),
        notes=""),
    "lathe": dict(
        menu_class="machine/classic/runtime/ClassicControllerMenu", screen_class="client/ClassicControllerScreen",
        opened_by="block use",
        holders="lathe, arc_furnace, cutting_machine, crystallizer, chemical_reactor, precision_laser_etcher, "
                "centrifuge",
        keywords=("lathe", "arc furnace", "cutting machine", "crystallizer", "crystalliser", "chemical reactor",
                  "laser etcher", "centrifuge", "classic controller", "classic machine"),
        open=ANYONE + " who may interact at the position (spawn protection); the block has no owner",
        act="none: the scalar menu has no slot, button or intent", operator="none",
        distance="8 blocks from the block's centre (distanceToSqr <= 64)",
        chunk="hasChunkAt at open; getChunkNow and the same block entity while open (physical())",
        rate="not applicable", refusal=SILENT_CLOSE,
        anchors=(A("machine/classic/runtime/ClassicControllerBlock.java", "ClassicControllerMenu.canOpen(serverPlayer"),
                 A("machine/classic/runtime/ClassicControllerMenu.java", "public static boolean canOpen("),
                 A("machine/classic/runtime/ClassicControllerBlockEntity.java", "boolean physical()")),
        notes="one menu type, named lathe, serves the seven classic processing controllers"),
    "combustion_generator": _machine(
        "machine/combustion/CombustionGeneratorMenu", "client/CombustionGeneratorScreen", "combustion_generator",
        ("combustion generator",), "getChunkNow on the generator's chunk (available())",
        (A("machine/combustion/CombustionGeneratorMenu.java", "public boolean stillValid(Player player)"),
         A("machine/combustion/CombustionGeneratorBlockEntity.java", "boolean available()"))),
    "solar_generator": dict(
        menu_class="machine/solar/SolarGeneratorMenu", screen_class="client/SolarGeneratorScreen",
        opened_by="block use", holders="solar_generator", keywords=("solar generator", "solar panel"),
        open=ANYONE + "; the block has no owner", act="none: the scalar menu has no slot, button or intent",
        operator="none", distance="8 blocks from the block's centre (distanceToSqr <= 64)",
        chunk="the generator must be available()", rate="not applicable", refusal=SILENT_CLOSE,
        anchors=(A("machine/solar/SolarGeneratorMenu.java", "public static boolean admitted(Player player"),
                 A("machine/solar/SolarGeneratorMenu.java", "public boolean stillValid(Player player)")), notes=""),
    "solar_array": dict(
        menu_class="machine/solar/SolarArrayMenu", screen_class="client/SolarArrayScreen", opened_by="block use",
        holders="solar_array", keywords=("solar array",), open=ANYONE + "; the block has no owner",
        act="none: the scalar menu has no slot, button or intent", operator="none",
        distance="8 blocks from the block's centre (distanceToSqr <= 64)", chunk="the array must be available()",
        rate="not applicable", refusal=SILENT_CLOSE,
        anchors=(A("machine/solar/SolarArrayMenu.java", "public static boolean admitted(Player player"),
                 A("machine/solar/SolarArrayMenu.java", "public boolean stillValid(Player player)")), notes=""),
    "electrolyzer": _machine(
        "machine/electrolyzer/ElectrolyzerMenu", "client/ElectrolyzerScreen", "electrolyzer",
        ("electrolyzer", "electrolyser"), "hasChunkAt and the same block entity",
        (A("machine/electrolyzer/ElectrolyzerMenu.java", "public boolean stillValid(Player player)"),)),
    "rolling_machine": _machine(
        "machine/rolling/RollingMachineMenu", "client/RollingMachineScreen", "rolling_machine",
        ("rolling machine",), "hasChunkAt and the same block entity",
        (A("machine/rolling/RollingMachineMenu.java", "public boolean stillValid(Player player)"),)),
    "precision_assembler": _machine(
        "machine/precision/PrecisionAssemblerMenu", "client/PrecisionAssemblerScreen", "precision_assembler",
        ("precision assembler",), "hasChunkAt, the same block entity, generation and machine instance",
        (A("machine/precision/PrecisionAssemblerMenu.java", "static boolean canOpen(PrecisionAssemblerBlockEntity"),
         A("machine/precision/PrecisionAssemblerMenu.java", "public boolean stillValid(Player player)"))),
    "satellite_builder": _machine(
        "satellite/builder/SatelliteBuilderMenu", "client/SatelliteBuilderScreen", "satellite_builder",
        ("satellite builder",), "hasChunkAt, the same block entity and the same catalog generation",
        (A("satellite/builder/SatelliteBuilderMenu.java", "public boolean stillValid(Player player)"),
         A("satellite/builder/SatelliteBuilderMenu.java", "public boolean clickMenuButton(Player player")),
        act="vanilla slot clicks and the assemble button, each only while the menu is valid",
        notes="unsupported saved data refuses the open with status.advancedrocketrycommunity.satellite."
              "unsupported_data"),
    "microwave_receiver": _machine(
        "satellite/receiver/MicrowaveReceiverMenu", "client/MicrowaveReceiverScreen", "microwave_receiver",
        ("microwave receiver",), "hasChunkAt and the same block entity",
        (A("satellite/receiver/MicrowaveReceiverMenu.java", "public boolean stillValid(Player player)"),)),
    "satellite_terminal": dict(
        menu_class="satellite/terminal/SatelliteTerminalMenu", screen_class="client/SatelliteTerminalScreen",
        opened_by="block use", holders="satellite_terminal", keywords=("satellite terminal", "satellite-terminal", "terminal menu", "powered terminal"),
        open="the terminal's owner or operator level 2; an unowned terminal: anyone",
        act="the same players: slot clicks and buttons, each only while the menu is valid", operator="2",
        distance=VANILLA_REACH, chunk="hasChunkAt, the same block entity and the same catalog generation",
        rate="none in the menu; the operations have their own state checks",
        refusal="at open: an action-bar message (SatelliteTerminalBlock) for a player without access; afterwards "
                "the menu is closed silently; no log line",
        anchors=(A("satellite/terminal/SatelliteTerminalBlock.java", "terminal.canAccess(serverPlayer)"),
                 A("satellite/terminal/SatelliteTerminalBlockEntity.java", "public boolean canAccess(Player player)"),
                 A("satellite/terminal/SatelliteTerminalMenu.java", "public boolean stillValid(Player player)"),
                 A("satellite/terminal/SatelliteTerminalMenu.java", "public boolean clickMenuButton(Player player")),
        notes=""),
    "orbital_laser_drill": _endgame("laser/OrbitalLaserDrillMenu", "OrbitalLaserDrillScreen", "orbital_laser_drill",
                                    ("laser drill", "orbital laser")),
    "laser_target": _endgame("laser/LaserTargetMenu", "LaserTargetScreen", "laser_target", ("laser target",)),
    "gravity_field_controller": _endgame(
        "gravity/GravityFieldMenu", "GravityFieldScreen", "gravity_field_controller",
        ("gravity field", "gravity-field", "gravity_controller"),
        "inside a station CONFIGURE and OPERATE need the station owner or an operator (ADR-058)"),
    "black_hole_generator": _endgame("blackhole/BlackHoleGeneratorMenu", "BlackHoleGeneratorScreen",
                                     "black_hole_generator", ("black-hole generator", "black hole generator"),
                                     "no button or intent: slot clicks are decided per click"),
    "railgun": _endgame("railgun/RailgunMenu", "RailgunScreen", "railgun", ("railgun",)),
    "elevator": _endgame("elevator/ElevatorMenu", "ElevatorScreen", "elevator_anchor, elevator_terminal",
                         ("elevator",)),
    "warp_controller": _station("warp_controller", ("warp",)),
    "station_gravity_controller": _station("station_gravity_controller", ("station gravity", "gravity controller")),
    "station_altitude_controller": _station("station_altitude_controller", ("altitude controller",)),
    "station_orientation_controller": _station("station_orientation_controller", ("orientation controller",)),
    "rocket_flight": dict(
        menu_class="rocket/menu/RocketFlightMenu", screen_class="client/RocketFlightScreen",
        opened_by="entity use", holders="rocket entity (use the assembled rocket)",
        keywords=("flight screen", "flight menu", "rocket screen", "flight console", "high-seat console",
                  "navigation screen"),
        open="any living, non-spectator player riding the rocket or within 8 blocks of it",
        act="BOARD and LEAVE: the holder of the valid menu; LAUNCH and CANCEL: the rocket's owner, a creative "
            "player, or operator level 2",
        operator="2 (LAUNCH and CANCEL)",
        distance="riding the rocket, or distanceToSqr(rocket) <= 64 (8 blocks, MAX_INTERACTION_DISTANCE_SQUARED)",
        chunk="hasChunkAt at the rocket's position; the rocket is found by entity ID in the sender's own Level, "
              "so no chunk is loaded",
        rate="8 intents per player per 20 ticks",
        refusal="action-bar reply (RocketFlightFeedback) and the server log line ARCE_FLIGHT_INTENT with the code "
                "(ENTITY_UNAVAILABLE, OUT_OF_RANGE, UNAUTHORIZED, RATE_LIMITED when audited)",
        anchors=(A("rocket/server/RocketFlightService.java", "void openMenu(ServerPlayer player, RocketEntity"),
                 A("rocket/menu/RocketFlightMenu.java", "public boolean stillValid(Player player)"),
                 A("rocket/server/RocketFlightService.java", "private static Access access(ServerPlayer player"),
                 A("rocket/server/RocketFlightService.java", "private static boolean authorized(ServerPlayer"),
                 A("rocket/flight/RocketFlightLimits.java", "MAX_INTENTS_PER_WINDOW =")),
        notes="see the channel row rocket_flight for the two refusals that leave no message and no log line"),
    "rocket_satellite_bay": dict(
        menu_class="rocket/bay/SatelliteBayMenu", screen_class="client/SatelliteBayScreen", opened_by="block use",
        holders="rocket_satellite_bay", keywords=("satellite bay", "bay menu", "bay screen"),
        open="the owner of the bay's contents, or operator level 2; the player must be allowed to interact at the "
             "position",
        act="the same player, the one the menu was opened for: slot clicks on the package slot", operator="2",
        distance="8.0 blocks from the block's centre (SatelliteBayBlockEntity.MAX_MENU_DISTANCE)",
        chunk="the bay must be live() and not guarded", rate="none (slot clicks only)",
        refusal="at open: action-bar message.advancedrocketrycommunity.rocket_satellite_bay.unauthorized, or the "
                "data guard's message; afterwards the menu is closed silently; no log line",
        anchors=(A("rocket/bay/SatelliteBayBlock.java", "rocket_satellite_bay.unauthorized"),
                 A("rocket/bay/SatelliteBayBlockEntity.java", "public boolean admitted(Player player)"),
                 A("rocket/bay/SatelliteBayBlockEntity.java", "MAX_MENU_DISTANCE ="),
                 A("rocket/bay/SatelliteBayMenu.java", "public boolean stillValid(Player player)")), notes=""),
    "fluid_fuel_loader": dict(
        menu_class="rocket/fuel/loader/FluidFuelLoaderMenu", screen_class="client/FluidFuelLoaderScreen",
        opened_by="block use with an empty hand", holders="fluid_fuel_loader", keywords=("fuel loader", "fuel-loader"),
        open="the loader's owner only; an operator gets no extra access to the menu",
        act="none in the menu (no slot, button or intent); the owner exchanges containers by block use",
        operator="none for the menu (level 2 only lets a placer keep a carried owner)",
        distance="8.0 blocks from the block's centre (MAX_ACTOR_DISTANCE), and mayInteract at the position",
        chunk="isLoaded at open; the device must be live() with the same device identity",
        rate="not applicable", refusal=CARGO_REFUSAL,
        anchors=(A("rocket/fuel/loader/FluidFuelLoaderBlock.java", "if (!loader.admitted(player))"),
                 A("rocket/fuel/loader/FluidFuelLoaderBlockEntity.java", "public boolean admitted(Player player)"),
                 A("rocket/fuel/loader/FluidFuelLoaderBlockEntity.java", "MAX_ACTOR_DISTANCE ="),
                 A("rocket/fuel/loader/FluidFuelLoaderMenu.java", "public boolean stillValid(Player player)")),
        notes=""),
    "rocket_fluid_cargo": dict(
        menu_class="rocket/cargo/device/RocketFluidCargoMenu", screen_class="client/RocketFluidCargoScreen",
        opened_by="block use", holders="rocket_fluid_loader, rocket_fluid_unloader",
        keywords=("fluid cargo", "fluid loader", "fluid unloader", "cargo device", "cargo devices"),
        open="the device's owner only", act="none in the menu (no slot, button or intent)",
        operator="none for the menu",
        distance="8.0 blocks from the block's centre (MAX_ACTOR_DISTANCE), and mayInteract at the position",
        chunk="the device must be live(), claimed, not a duplicate, with the same device identity",
        rate="not applicable", refusal=CARGO_REFUSAL,
        anchors=(A("rocket/cargo/device/RocketFluidCargoBlock.java", "if (!loader.admitted(player))"),
                 A("rocket/cargo/device/RocketFluidCargoBlockEntity.java", "public boolean admitted(Player player)"),
                 A("rocket/cargo/device/RocketFluidCargoBlockEntity.java", "MAX_ACTOR_DISTANCE ="),
                 A("rocket/cargo/device/RocketFluidCargoMenu.java", "public boolean stillValid(Player player)")),
        notes=""),
    "rocket_item_cargo": dict(
        menu_class="rocket/cargo/device/RocketItemCargoMenu", screen_class="client/RocketItemCargoScreen",
        opened_by="block use", holders="rocket_item_loader, rocket_item_unloader",
        keywords=("item cargo", "item loader", "item-loader", "item unloader", "cargo device", "cargo devices"),
        open="the device's owner only", act="the owner: vanilla slot clicks on the buffer view; no button",
        operator="none for the menu",
        distance="8.0 blocks from the block's centre (MAX_ACTOR_DISTANCE), and mayInteract at the position",
        chunk="the device must be live(), claimed, not a duplicate, with the same device identity",
        rate="none (slot clicks only)", refusal=CARGO_REFUSAL,
        anchors=(A("rocket/cargo/device/RocketItemCargoBlock.java", "if (!device.admitted(player))"),
                 A("rocket/cargo/device/RocketItemCargoBlockEntity.java", "public boolean admitted(Player player)"),
                 A("rocket/cargo/device/RocketItemCargoBlockEntity.java", "MAX_ACTOR_DISTANCE ="),
                 A("rocket/cargo/device/RocketItemCargoMenu.java", "public boolean stillValid(Player player)")),
        notes=""),
    "rocket_monitoring_station": dict(
        menu_class="rocket/monitor/RocketMonitorMenu", screen_class="client/RocketMonitorScreen",
        opened_by="block use", holders="rocket_monitoring_station",
        keywords=("monitoring station", "rocket monitor", "monitor"),
        open="the station's owner, or operator level 2", act="the same player, the one the menu was opened for",
        operator="2", distance="8 blocks from the block's centre (distanceToSqr <= 64)",
        chunk="the monitor must be live() with the same device identity",
        rate="one action per player per 10 ticks (shared with the station controllers)",
        refusal="an invalid menu drops the intent silently and is closed at the next broadcast; a refused action "
                "returns its StationManagementCode in the view packet (CONTROL_RATE_LIMITED, "
                "AUTHORITY_UNAVAILABLE, CONFIRMATION_MISMATCH, CONFIRMATION_EXPIRED); no log line",
        anchors=(A("rocket/monitor/RocketMonitorBlockEntity.java", "public boolean admitted(Player player)"),
                 A("rocket/monitor/RocketMonitorMenu.java", "public boolean stillValid(Player player)"),
                 A("rocket/monitor/RocketMonitorMenu.java", "public void acceptClassicControl(ServerPlayer sender")),
        notes="actions arrive as classic_controls intents"),
    "suit_workstation": dict(
        menu_class="equipment/workstation/SuitWorkstationMenu",
        screen_class="client/equipment/SuitWorkstationScreen", opened_by="block use", holders="suit_workstation",
        keywords=("suit workstation", "workstation"),
        open=ANYONE + " who may interact at the position; the block has no owner",
        act="the player the menu was opened for: slot clicks and buttons 0 to 6 on the armour that player put in",
        operator="none", distance="8 blocks from the block's centre (distanceToSqr <= 64)",
        chunk="the workstation must be available()", rate="none", refusal=SILENT_CLOSE,
        anchors=(A("equipment/workstation/SuitWorkstationBlockEntity.java", "public boolean admitted(Player player)"),
                 A("equipment/workstation/SuitWorkstationMenu.java", "public boolean stillValid(Player player)"),
                 A("equipment/workstation/SuitWorkstationMenu.java", "public boolean clickMenuButton(Player player")),
        notes=""),
}

#: Screens and overlays that have no menu type.
OTHER_SCREENS = (
    dict(id="survey_scan", kind="screen", screen_class="client/SurveyScanScreen",
         opened_by="the server's SurveyScanResultPacket (satellite channel) after a survey scan; the client opens "
                   "the screen from its result cache", keywords=("survey scan", "scan result", "survey screen", "survey result"),
         anchors=(A("client/ClientSatelliteEvents.java", "new SurveyScanScreen(result)"),)),
    dict(id="life_support", kind="hud overlay", screen_class="client/LifeSupportHud",
         opened_by="always registered; drawn while the server's status applies",
         keywords=("hud",), anchors=(A("client/ClientBootstrap.java", 'registerAboveAll("life_support"'),)),
    dict(id="beacon_finder", kind="hud overlay", screen_class="client/beacon/BeaconFinderOverlay",
         opened_by="always registered; drawn from the beacon marker snapshot",
         keywords=("beacon",),
         anchors=(A("client/beacon/BeaconFinderOverlay.java", 'registerAboveAll("beacon_finder"'),)),
    dict(id="screen_action_banner", kind="banner over mod screens", screen_class="client/ScreenActionMessages",
         opened_by="any action-bar message received while a mod screen is open (six seconds)",
         keywords=("banner",), anchors=(A("client/ScreenActionMessages.java", "public final class ScreenActionMessages"),)),
)

#: Checklist rows that cover every screen by their own text (choice C30).
BLANKET_ROWS = ("I06", "I08")

#: One row per code site that makes a sound audible. ``event`` without a namespace is a mod event.
SOUND_PRODUCERS = (
    dict(event="air_hiss_loop", producer="block", what="oxygen_vent while its LIT state is true (client loop)",
         anchors=(A("client/sound/ClassicSoundClient.java", "? ClassicSound.AIR_HISS_LOOP : null"),)),
    dict(event="basic_laser_gun", producer="item", what="basic_laser_gun fired: the server plays the event at the "
         "player, the client forwards it to the capped one-shot player",
         anchors=(A("equipment/laser/LaserGunService.java", "ModSounds.classic(ClassicSound.BASIC_LASER_GUN)"),
                  A("equipment/laser/client/LaserGunSoundDelivery.java", "ClassicSoundClient.oneShot(ClassicSound.BASIC_LASER_GUN"))),
    dict(event="combustion_rocket", producer="entity", what="rocket entity in ascent or descent (client loop)",
         anchors=(A("client/sound/ClassicSoundClient.java", "ClassicSound.COMBUSTION_ROCKET,"),)),
    dict(event="electric_shock_small", producer="block", what="electric_mushroom flash the client accepted "
         "(follows effects.electricMushroomFlashes)",
         anchors=(A("client/exoplanet/ClientExoplanetEffects.java", "ClassicSoundClient.oneShot(ClassicSound.ELECTRIC_SHOCK_SMALL"),)),
    dict(event="gravity_controller", producer="block", what="gravity_field_controller while active (client loop)",
         anchors=(A("client/sound/ClassicSoundClient.java", "? ClassicSound.GRAVITY_CONTROLLER : null"),)),
    dict(event="laser_drill", producer="block", what="orbital_laser_drill while active for rendering (client loop)",
         anchors=(A("client/sound/ClassicSoundClient.java", "? ClassicSound.LASER_DRILL : null"),)),
    dict(event="lathe", producer="block", what="lathe and precision_laser_etcher while processing (client loop)",
         anchors=(A("sound/MachineSoundProfile.java", "-> ClassicSound.LATHE;"),
                  A("machine/classic/runtime/ClassicControllerBlockEntity.java", "public ClassicSound activeProcessingSound()"))),
    dict(event="machine_large", producer="none", what="no code site plays it: registered and defined, preview only "
         "(/playsound advancedrocketrycommunity:machine_large)", anchors=(A("sound/ClassicSound.java", "MACHINE_LARGE("),)),
    dict(event="railgun_bang", producer="block", what="railgun launch effect the client accepted (one-shot at the "
         "muzzle); the arrival effect plays no bang",
         anchors=(A("endgame/railgun/RailgunBlockEntity.java", "ClassicSoundClient.oneShot(ClassicSound.RAILGUN_BANG"),)),
    dict(event="rolling_machine", producer="block", what="rolling_machine and chemical_reactor while processing "
         "(client loop)",
         anchors=(A("sound/MachineSoundProfile.java", "-> ClassicSound.ROLLING_MACHINE;"),
                  A("machine/rolling/RollingMachineBlockEntity.java", "public ClassicSound activeProcessingSound()"))),
    dict(event="ui_select", producer="block", what="machine_casing used with a hand that holds no block item "
         "(server plays it at the block)",
         anchors=(A("content/MachineCasingBlock.java", "ModSounds.UI_SELECT.get()"),)),
    dict(event="minecraft:item.firecharge.use / minecraft:item.flintandsteel.use", producer="block",
         what="lighting an unlit torch", anchors=(A("atmosphere/torch/TorchEvents.java", "SoundEvents.FIRECHARGE_USE"),)),
    dict(event="minecraft:entity.lightning_bolt.thunder", producer="client effect",
         what="exoplanet lightning effect", anchors=(A("client/exoplanet/ClientExoplanetEffects.java", "SoundEvents.LIGHTNING_BOLT_THUNDER"),)),
    dict(event="minecraft:block.beacon.activate, entity.firework_rocket.launch, entity.firework_rocket.blast, "
               "entity.enderman.teleport, block.anvil.land, block.beacon.deactivate", producer="entity",
         what="rocket flight feedback at the rocket (countdown, launch, transfer, arrival, landing, cancel)",
         anchors=(A("rocket/server/RocketFlightFeedback.java", "SoundEvents.BEACON_ACTIVATE"),
                  A("rocket/server/RocketFlightFeedback.java", "SoundEvents.BEACON_DEACTIVATE"))),
    dict(event="minecraft:item.armor.equip_iron", producer="item", what="equipping a space suit piece",
         anchors=(A("atmosphere/content/SpaceSuitMaterial.java", "SoundEvents.ARMOR_EQUIP_IRON"),)),
    dict(event="minecraft bucket fill and empty events", producer="item", what="buckets of the classic fluids",
         anchors=(A("fluid/ClassicFluidType.java", "SoundEvents.BUCKET_FILL"),)),
    dict(event="planetary ambience (data-driven event)", producer="client effect",
         what="local ambience chosen by the celestial visual profile",
         anchors=(A("client/sky/PlanetaryAmbience.java", "SimpleSoundInstance.forLocalAmbience("),)),
)

#: What W16 recorded, for the comparison of section "since W16".
W16_PREVIEW_NAMESPACE = MOD + "_v180"
W16_UNBOUND = ("lathe", "rolling_machine", "machine_large", "basic_laser_gun")

#: One row per client-to-server message of the network pin.
CHANNELS = {
    ("rocket_flight", "RocketFlightIntentPacket"): dict(
        who="the sender, with its own open and valid rocket_flight menu for that rocket; LAUNCH and CANCEL also "
            "need the rocket's owner, a creative player or operator level 2",
        operator="2 (LAUNCH and CANCEL)",
        distance="riding the rocket or distanceToSqr(rocket) <= 64 (8 blocks)",
        chunk="hasChunkAt at the rocket; the rocket is looked up by entity ID in the sender's Level; the packet "
              "carries no coordinates",
        bounds="frame at most MAX_ENCODED_BYTES; canonical VarInt; no trailing bytes",
        rate="8 per player per 20 ticks",
        refusal="reported refusals: action-bar reply and log line ARCE_FLIGHT_INTENT with the code; two refusals "
                "leave no message and no log line: no open valid flight menu (UNAUTHORIZED, returned before "
                "report()), and RATE_LIMITED unless the limiter asks for an audit",
        anchors=(A("rocket/network/RocketFlightIntentPacket.java", "public void handle(Supplier<NetworkEvent.Context>"),
                 A("rocket/server/RocketFlightService.java", "return RocketFlightRequestResult.failure(RocketFlightRequestCode.UNAUTHORIZED);"),
                 A("rocket/server/RocketFlightService.java", "private static Access access(ServerPlayer player"),
                 A("rocket/server/RocketFlightService.java", "private static boolean authorized(ServerPlayer"),
                 A("rocket/server/RocketFlightService.java", '"ARCE_FLIGHT_INTENT request={}')),
        finding="K18 cannot prove these two refusals from the server log (choice C14); it has to compare state"),
    ("classic_controls", "ClassicControlIntentPacket"): dict(
        who="the sender, only for its open menu with the same container ID and kind (the four station controllers "
            "and the rocket monitoring station); the menu then applies its own rules",
        operator="as the target menu's row",
        distance="the target menu's stillValid: 8 blocks",
        chunk="the target menu's rule; the packet names a container ID, never a position",
        bounds="exactly 13 bytes, at most 512; schema byte 1; action, value and token ranges checked in decoding",
        rate="one mutation per player per 10 ticks (StationManager.admitControllerAction)",
        refusal="a packet for no open matching menu, or for an invalid menu, is dropped with no message and no log "
                "line; a refused mutation returns its StationManagementCode in the view packet",
        anchors=(A("classiccontrol/network/ClassicControlIntentPacket.java", "public static void handle(ClassicControlIntentPacket packet"),
                 A("station/controller/StationControllerMenu.java", "public final void acceptClassicControl("),
                 A("rocket/monitor/RocketMonitorMenu.java", "public void acceptClassicControl(ServerPlayer sender"),
                 A("station/service/StationManager.java", "public boolean admitControllerAction(ServerPlayer player)")),
        finding="no server log line for any refusal on this channel: K18 has to read the view packet or the "
                "unchanged state (choice C14)"),
    ("suit_motion", "JetpackIntentPacket"): dict(
        who="the sender, for its own worn jetpack only: a connected, living, non-spectator player",
        operator="none",
        distance="not applicable: the packet has no target and no position; it acts on the sender",
        chunk="not applicable",
        bounds="exactly 2 bytes; unknown action or value refused in decoding",
        rate="10 packets per player per 20 ticks",
        refusal="RATE_LIMITED and IGNORED (not live, or thrust while disabled) leave no message and no log line; "
                "NO_JETPACK, NO_HOVER_UPGRADE and EQUIPMENT_DISABLED send the status message "
                "message.advancedrocketrycommunity.jetpack.<code>",
        anchors=(A("equipment/motion/JetpackIntentPacket.java", "public static void handle(JetpackIntentPacket packet"),
                 A("equipment/motion/SuitMotionService.java", "public Reply receive(ServerPlayer player, JetpackIntentPacket intent"),
                 A("equipment/motion/SuitMotionService.java", "static boolean live(ServerPlayer player)"),
                 A("equipment/motion/MotionRateLimiter.java", "MAX_PACKETS =")),
        finding="the channel has no distance check because it has no target; the rate limit and the two silent "
                "replies leave no log line (choice C14)"),
}

#: Devices that checklist I11 names and that have no menu type and no client-to-server message: block use only.
MENULESS = (
    dict(id="planetary_beacon", who="the beacon's owner, or operator level 2",
         distance="vanilla block reach", refusal="action-bar message.advancedrocketrycommunity.beacon.not_owner, or "
         "the NO_OWNER status", anchors=(A("beacon/PlanetaryBeaconBlock.java", "!owner && !actor.hasPermissions(2)"),)),
    dict(id="landing_pad", who="a valid connected player registers or (sneaking) removes the pad; the pad service "
         "decides with the player's UUID and operator level 2; also the command of the same class",
         distance="distanceToSqr <= 64 (8 blocks); the pad must be loaded (loadedPad); one interaction per player "
         "under the pad index's rate", refusal="chat message from LandingPadCommands (the 'unavailable' text)",
         anchors=(A("station/pad/LandingPadBlock.java", "LandingPadCommands.interact(serverPlayer"),
                  A("station/pad/LandingPadCommands.java", "public static boolean interact(ServerPlayer player"))),
    dict(id="force_field_projector", who="operator level 2 only: block use binds the projector",
         distance="distanceToSqr <= 64 (8 blocks)", refusal="none: the use passes with no message and no log line",
         anchors=(A("forcefield/ForceFieldProjectorBlock.java", "connected.hasPermissions(2)"),)),
    dict(id="pump", who="any non-spectator player reads the status; only the owner or operator level 2 retries",
         distance="distanceToSqr <= 64 (8 blocks)", refusal="action-bar message.advancedrocketrycommunity.pump.status",
         anchors=(A("machine/pump/PumpBlock.java", "actor.getUUID().equals(pump.owner()) || actor.hasPermissions(2)"),)),
)


class K13Error(Exception):
    """A defect in the input or in the curated tables."""


# --------------------------------------------------------------------------------------------------------------
# The JAR
# --------------------------------------------------------------------------------------------------------------

def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def read_jar(jar: Path) -> dict[str, bytes]:
    """Every file entry of the JAR; directory entries are skipped."""
    with zipfile.ZipFile(jar) as archive:
        return {info.filename: archive.read(info) for info in archive.infolist() if not info.is_dir()}


def asset_kind(path: str) -> str | None:
    """The inventory kind of an entry, or None when it is not a presentation asset."""
    parts = path.split("/")
    if parts[0] == "assets" and len(parts) == 3 and parts[2] == "sounds.json":
        return "sound_definitions"
    if parts[0] != "assets" or len(parts) < 4:
        return "root_image" if "/" not in path and path.endswith(".png") else None
    area, name = parts[2], parts[-1]
    if area == "textures" and name.endswith(".png"):
        return "texture"
    if area == "textures" and name.endswith(".mcmeta"):
        return "texture_metadata"
    if area == "blockstates" and name.endswith(".json"):
        return "blockstate"
    if area == "models":
        if name.endswith(".obj"):
            return "model_obj"
        if name.endswith(".mtl"):
            return "model_mtl"
        if name.endswith(".json"):
            return "item_model" if len(parts) > 4 and parts[3] == "item" else "block_model"
    if area == "particles" and name.endswith(".json"):
        return "particle"
    if area == "sounds" and name.endswith(".ogg"):
        return "sound_file"
    if area == "lang" and name.endswith(".json"):
        return "language_file"
    return "other_asset"


def inventory(files: dict[str, bytes]) -> list[dict]:
    rows = []
    for path in sorted(files):
        kind = asset_kind(path)
        if kind is None:
            continue
        parts = path.split("/")
        rows.append(dict(path=path, kind=kind, namespace=parts[1] if parts[0] == "assets" else "",
                         size=len(files[path]), sha256=sha256_bytes(files[path])))
    return rows


def _json(files: dict[str, bytes], path: str):
    try:
        return json.loads(files[path].decode("utf-8-sig"))
    except (UnicodeDecodeError, json.JSONDecodeError) as failure:
        raise K13Error(f"{path} is not JSON: {failure}") from failure


def _is_language_file(parts: list[str]) -> bool:
    return (len(parts) == 4 and parts[0] == "assets" and parts[2] == "lang"
            and parts[3] in ("en_us.json", "zh_cn.json"))


def languages(files: dict[str, bytes]) -> tuple[list[dict], list[dict]]:
    """Rows per namespace and key for en_us and zh_cn, and the keys missing from either side."""
    tables: dict[str, dict[str, dict[str, str]]] = defaultdict(lambda: {"en_us": {}, "zh_cn": {}})
    for path in sorted(files):
        parts = path.split("/")
        if _is_language_file(parts):
            table = _json(files, path)
            if not isinstance(table, dict):
                raise K13Error(f"{path} is not a JSON object")
            tables[parts[1]][parts[3][:-5]] = table
    rows, missing = [], []
    for namespace in sorted(tables):
        english, chinese = tables[namespace]["en_us"], tables[namespace]["zh_cn"]
        for key in sorted(set(english) | set(chinese)):
            row = dict(namespace=namespace, key=key, in_en_us=int(key in english), in_zh_cn=int(key in chinese),
                       en_us_chars=len(str(english.get(key, ""))), zh_cn_chars=len(str(chinese.get(key, ""))))
            rows.append(row)
            if not (row["in_en_us"] and row["in_zh_cn"]):
                missing.append(dict(namespace=namespace, key=key,
                                    missing_from="zh_cn" if row["in_en_us"] else "en_us"))
    return rows, missing


def sound_events(files: dict[str, bytes], language_rows: list[dict]) -> list[dict]:
    present = {row["key"]: row for row in language_rows}
    rows = []
    for path in sorted(files):
        parts = path.split("/")
        if len(parts) != 3 or parts[0] != "assets" or parts[2] != "sounds.json":
            continue
        for event, definition in sorted(_json(files, path).items()):
            names, ranges = [], []
            for sound in definition.get("sounds", []):
                name = sound if isinstance(sound, str) else sound.get("name", "")
                names.append(name)
                if isinstance(sound, dict) and "attenuation_distance" in sound:
                    ranges.append(str(sound["attenuation_distance"]))
                namespace, _, rest = name.rpartition(":")
                target = f"assets/{namespace or 'minecraft'}/sounds/{rest}.ogg"
                if (namespace or "minecraft") != "minecraft" and target not in files:
                    raise K13Error(f"{path}: event {event} names {name}, but {target} is not in the JAR")
            subtitle = definition.get("subtitle", "")
            entry = present.get(subtitle, {})
            rows.append(dict(namespace=parts[1], event=event, sound_files=" ".join(names),
                             attenuation_distance=" ".join(ranges), subtitle_key=subtitle,
                             subtitle_in_en_us=int(bool(entry.get("in_en_us"))),
                             subtitle_in_zh_cn=int(bool(entry.get("in_zh_cn")))))
    return rows


# --------------------------------------------------------------------------------------------------------------
# Models, references and classification (choice C30)
# --------------------------------------------------------------------------------------------------------------

def _location(value: str, default_namespace: str = "minecraft") -> str:
    return value if ":" in value else f"{default_namespace}:{value}"


def _model_path(location: str) -> str:
    namespace, _, path = location.partition(":")
    return f"assets/{namespace}/models/{path}.json"


def _texture_path(location: str) -> str:
    namespace, _, path = location.partition(":")
    return f"assets/{namespace}/textures/{path}.png"


class Models:
    """The JAR's JSON models with their parents, loaders and references."""

    def __init__(self, files: dict[str, bytes]):
        self.files = files
        self.json: dict[str, dict] = {}
        for path in files:
            parts = path.split("/")
            if len(parts) > 4 and parts[0] == "assets" and parts[2] == "models" and path.endswith(".json"):
                self.json[path] = _json(files, path)
        self.children: dict[str, list[str]] = defaultdict(list)
        for path, model in self.json.items():
            parent = self.parent(path)
            if parent in self.json:
                self.children[parent].append(path)

    def kind(self, path: str) -> str:
        return "item" if path.split("/")[3] == "item" else "block"

    def parent(self, path: str) -> str | None:
        """The parent's JAR path, an external location such as ``minecraft:block/cube_all``, or None."""
        value = self.json[path].get("parent")
        if not isinstance(value, str):
            return None
        candidate = _model_path(_location(value))
        return candidate if candidate in self.json else _location(value)

    def chain(self, path: str) -> list[str]:
        """The model and its ancestors inside the JAR, nearest first."""
        seen = [path]
        while True:
            parent = self.parent(seen[-1])
            if parent not in self.json or parent in seen:
                return seen
            seen.append(parent)

    def is_obj(self, path: str) -> bool:
        return any(self.json[step].get("loader") == "forge:obj" for step in self.chain(path))

    def family(self, path: str) -> str:
        """
        The parent model shared with other models. A parent outside the JAR, or one in the JAR with at least two
        children, is the family. A parent in the JAR with this model as its only child (an item inheriting its own
        block's model) passes its own family on. A model without a parent is its own family.
        """
        seen = {path}
        current = path
        while True:
            parent = self.parent(current)
            if parent is None:
                return "none:" + current
            if parent not in self.json or len(self.children[parent]) >= 2 or parent in seen:
                return parent
            seen.add(parent)
            current = parent

    def references(self, path: str) -> set[str]:
        """The JAR files one model needs: its ancestors, its textures, and for OBJ models the mesh and material."""
        found: set[str] = set()
        for step in self.chain(path):
            found.add(step)
            model = self.json[step]
            for value in (model.get("textures") or {}).values():
                if isinstance(value, str) and not value.startswith("#"):
                    found.add(_texture_path(_location(value)))
            mesh = model.get("model")
            if isinstance(mesh, str) and mesh.endswith(".obj"):
                namespace, _, rest = _location(mesh).partition(":")
                found |= obj_references(self.files, f"assets/{namespace}/{rest}")
        return {item for item in found if item in self.files}


def obj_references(files: dict[str, bytes], obj_path: str) -> set[str]:
    """An OBJ file, the MTL files it names and the textures those bind."""
    found = {obj_path}
    if obj_path not in files:
        return found
    folder = str(PurePosixPath(obj_path).parent)
    for line in files[obj_path].decode("utf-8", "replace").splitlines():
        if line.startswith("mtllib "):
            material = f"{folder}/{line.split(None, 1)[1].strip()}"
            found.add(material)
            found |= mtl_textures(files, material)
    return found


def mtl_textures(files: dict[str, bytes], mtl_path: str) -> set[str]:
    found = set()
    for line in files.get(mtl_path, b"").decode("utf-8", "replace").splitlines():
        fields = line.split()
        if len(fields) >= 2 and fields[0].startswith("map_"):
            found.add(_texture_path(_location(fields[-1])))
    return found


def blockstate_models(files: dict[str, bytes], path: str) -> set[str]:
    found: set[str] = set()

    def walk(node):
        if isinstance(node, dict):
            for key, value in node.items():
                if key == "model" and isinstance(value, str):
                    found.add(_model_path(_location(value)))
                else:
                    walk(value)
        elif isinstance(node, list):
            for value in node:
                walk(value)

    walk(_json(files, path))
    return found


def multiblock_ids(files: dict[str, bytes]) -> list[str]:
    ids = set()
    for path in files:
        parts = path.split("/")
        if len(parts) == 4 and parts[0] == "data" and parts[2] == "machine_patterns" and path.endswith(".json"):
            ids.add((parts[1], parts[3][:-5]))
    return sorted(ids)


def classify(files: dict[str, bytes], corrected: dict[str, tuple[str, ...]] = CORRECTED,
             strict: bool = True) -> tuple[list[dict], list[dict]]:
    """
    The class of every visual asset and the family table. Classes: HIGH_RISK (checked in full), SAMPLE (the one
    picked model of a family, or a file that only picked models need), FAMILY_MEMBER (represented by its family's
    sample) and UNREFERENCED (a texture or material that no model, blockstate or MTL in the JAR names).
    """
    models = Models(files)
    visual = {path for path in files if asset_kind(path) in (
        "texture", "texture_metadata", "blockstate", "block_model", "item_model", "model_obj", "model_mtl",
        "particle")}
    reasons: dict[str, list[str]] = defaultdict(list)

    def mark(path: str, reason: str) -> None:
        if path in visual and reason not in reasons[path]:
            reasons[path].append(reason)

    def mark_model(path: str, reason: str) -> None:
        mark(path, reason)
        if path in models.json:
            for needed in models.references(path):
                mark(needed, reason)

    for path in sorted(visual):
        kind = asset_kind(path)
        if kind in ("model_obj", "model_mtl"):
            for needed in (obj_references(files, path) if kind == "model_obj" else {path} | mtl_textures(files, path)):
                mark(needed, "OBJ model")
        elif path in models.json and models.is_obj(path):
            mark_model(path, "OBJ model")
        below = path.split("/", 2)[2] if path.startswith("assets/") else path
        if below.startswith(ARMOUR_PREFIXES):
            mark_model(path, "armour")
    for namespace, name in multiblock_ids(files):
        reason = f"multiblock {name}"
        state = f"assets/{namespace}/blockstates/{name}.json"
        candidates = {f"assets/{namespace}/models/item/{name}.json", f"assets/{namespace}/models/block/{name}.json"}
        if state in files:
            mark(state, reason)
            candidates |= blockstate_models(files, state)
        for candidate in sorted(candidates):
            mark_model(candidate, reason)
    for record, paths in sorted(corrected.items()):
        for below in paths:
            path = f"assets/{MOD}/{below}"
            if path not in files:
                if strict:
                    raise K13Error(f"{record} corrected {path}, which is not in the JAR")
                continue
            reason = f"corrected by {record}"
            if asset_kind(path) == "model_obj":
                for needed in obj_references(files, path):
                    mark(needed, reason)
            elif asset_kind(path) == "blockstate":
                mark(path, reason)
                for candidate in blockstate_models(files, path):
                    mark_model(candidate, reason)
            else:
                mark_model(path, reason)
    # A blockstate is high-risk when one of its models is.
    for path in sorted(visual):
        if asset_kind(path) == "blockstate":
            for candidate in sorted(blockstate_models(files, path)):
                if reasons.get(candidate):
                    mark(path, "names a high-risk model")
    high = {path for path in visual if reasons.get(path)}

    families: dict[tuple[str, str], list[str]] = defaultdict(list)
    for path in models.json:
        families[(models.kind(path), models.family(path))].append(path)
    picks: dict[str, tuple[str, str]] = {}
    family_rows = []
    for (kind, family), members in sorted(families.items()):
        ordinary = sorted(member for member in members if member not in high)
        pick = ordinary[0] if ordinary else ""
        if pick:
            picks[pick] = (kind, family)
        family_rows.append(dict(kind=kind, family=family, members=len(members),
                                high_risk_members=len(members) - len(ordinary), pick=pick))
    needed_by_pick: set[str] = set()
    needed_by_any: set[str] = set()
    for path in models.json:
        needed = models.references(path)
        needed_by_any |= needed
        if path in picks:
            needed_by_pick |= needed
    for path in visual:
        if asset_kind(path) == "model_mtl":
            needed_by_any |= mtl_textures(files, path)
    state_models = {path: blockstate_models(files, path) for path in visual if asset_kind(path) == "blockstate"}

    rows = []
    for path in sorted(visual):
        kind = asset_kind(path)
        family = ""
        if path in models.json:
            family = f"{models.kind(path)}:{models.family(path)}"
        if path in high:
            cls, reason = "HIGH_RISK", "; ".join(reasons[path])
        elif path in picks:
            cls, reason = "SAMPLE", "first path in sorted order of its model family"
        elif path in models.json:
            cls, reason = "FAMILY_MEMBER", "represented by its family's sample"
        elif kind == "blockstate":
            sampled = sorted(candidate for candidate in state_models[path] if candidate in picks)
            cls, reason = (("SAMPLE", "names the sampled model " + sampled[0]) if sampled
                           else ("FAMILY_MEMBER", "its models are represented by their families' samples"))
        elif path in needed_by_pick:
            cls, reason = "SAMPLE", "needed by a sampled model"
        elif path in needed_by_any:
            cls, reason = "FAMILY_MEMBER", "needed only by models that their families' samples represent"
        elif kind == "texture_metadata" and path[:-len(".mcmeta")] in visual:
            cls, reason = "FAMILY_MEMBER", "metadata of " + path[:-len(".mcmeta")]
        else:
            cls, reason = "UNREFERENCED", "no model, blockstate or MTL in the JAR names it; only code could"
        rows.append(dict(path=path, kind=kind, **{"class": cls}, reason=reason, family=family))
    return rows, family_rows


# --------------------------------------------------------------------------------------------------------------
# Deterministic resource packs
# --------------------------------------------------------------------------------------------------------------

def write_zip(entries: dict[str, bytes]) -> bytes:
    """A ZIP whose bytes depend only on the entries: sorted names, stored, fixed time and attributes."""
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_STORED) as archive:
        for name in sorted(entries):
            info = zipfile.ZipInfo(name, ZIP_TIME)
            info.compress_type = zipfile.ZIP_STORED
            info.create_system = 3
            info.external_attr = 0o644 << 16
            archive.writestr(info, entries[name])
    return buffer.getvalue()


def _mcmeta(description: str) -> bytes:
    return (json.dumps({"pack": {"description": description, "pack_format": PACK_FORMAT}}, indent=2,
                       sort_keys=True) + "\n").encode("utf-8")


def missing_resource_pack(files: dict[str, bytes], textures=MISSING_TEXTURES, model=MISSING_MODEL
                          ) -> tuple[bytes, list[dict]]:
    entries = {"pack.mcmeta": _mcmeta("ARCE K13 missing-resource fixture (campaign I07); never ship")}
    rows = []
    replacements = [(below, MISSING_TEXTURE_BYTES, "texture replaced by bytes that are not a PNG", why)
                    for below, why in textures]
    broken = (json.dumps({"parent": MISSING_PARENT}, indent=2) + "\n").encode("utf-8")
    replacements.append((model[0], broken, f"model replaced by one whose parent {MISSING_PARENT} does not exist",
                         model[1]))
    for below, data, action, why in replacements:
        path = f"assets/{MOD}/{below}"
        if path not in files:
            raise K13Error(f"The missing-resource pack overrides {path}, which is not in the JAR")
        entries[path] = data
        rows.append(dict(pack="k13-missing-resource-pack.zip", path=path, action=action, reason=why,
                         original_sha256=sha256_bytes(files[path]), pack_sha256=sha256_bytes(data)))
    return write_zip(entries), rows


def lengthen(value: str, cap: int = LONG_TEXT_CAP) -> str:
    """
    The long variant of one translation: the value, then a space and the value again without its format
    specifiers, repeated until the result is at least twice as long, cut at ``cap`` characters. The first copy is
    never cut, so every placeholder appears exactly once. A value of ``cap`` characters or more, and a value with no
    text besides its specifiers, stays as it is.
    """
    if len(value) >= cap:
        return value
    filler = _FORMAT_SPECIFIER.sub("", value).strip()
    if not filler:
        return value
    result = value
    while len(result) < 2 * len(value):
        result += " " + filler
    return result[:cap].rstrip("§").rstrip()


def long_text_pack(files: dict[str, bytes]) -> tuple[bytes, list[dict]]:
    entries = {"pack.mcmeta": _mcmeta("ARCE K13 long-text fixture (campaign J12 to J16); never ship")}
    rows = []
    for path in sorted(files):
        parts = path.split("/")
        if not _is_language_file(parts):
            continue
        table = _json(files, path)
        changed = unchanged = 0
        longer = {}
        for key, value in table.items():
            longer[key] = lengthen(value) if isinstance(value, str) else value
            if longer[key] == value:
                unchanged += 1
            else:
                changed += 1
        data = (json.dumps(longer, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
        entries[path] = data
        rows.append(dict(pack="k13-long-text-pack.zip", path=path,
                         action=f"{changed} values lengthened, {unchanged} unchanged",
                         reason="every key of the file is kept", original_sha256=sha256_bytes(files[path]),
                         pack_sha256=sha256_bytes(data)))
    return write_zip(entries), rows


# --------------------------------------------------------------------------------------------------------------
# Curated tables against the source and the JAR
# --------------------------------------------------------------------------------------------------------------

class Source:
    def __init__(self, repo: Path):
        self.repo = repo
        self._lines: dict[str, list[str]] = {}

    def lines(self, relative: str) -> list[str]:
        if relative not in self._lines:
            path = self.repo / relative
            if not path.is_file():
                raise K13Error(f"Source file {relative} does not exist")
            self._lines[relative] = path.read_text(encoding="utf-8").splitlines()
        return self._lines[relative]

    def locate(self, anchor: tuple[str, str]) -> str:
        relative = SRC + anchor[0]
        for number, line in enumerate(self.lines(relative), 1):
            if anchor[1] in line:
                return f"{relative}:{number}"
        raise K13Error(f"{relative} no longer contains: {anchor[1]}")

    def locations(self, anchors) -> str:
        return " ".join(self.locate(anchor) for anchor in anchors)


def registered_menus(source: Source) -> set[str]:
    """Menu IDs that ``src/main`` registers: literal IDs, and the station controller kinds' IDs."""
    found: set[str] = set()
    pattern = re.compile(r'MENUS\s*\.register\(\s*"([a-z0-9_]+)"', re.S)
    root = source.repo / SRC
    for path in sorted(root.rglob("*.java")):
        text = path.read_text(encoding="utf-8")
        if "MenuType" in text and "MENUS" in text:
            found.update(pattern.findall(text))
    kinds = "\n".join(source.lines(SRC + "station/controller/StationControllerKind.java"))
    found.update(re.findall(r'^\s*[A-Z]+\("([a-z_]+)"', kinds, re.M))
    return found


def pinned_messages(source: Source) -> list[dict]:
    rows = []
    for line in source.lines(NETWORK_PIN):
        if line.startswith("#") or not line.strip():
            continue
        channel, protocol, index, message, direction = line.strip().split("|")
        rows.append(dict(channel=channel, protocol=protocol, index=index, message=message, direction=direction))
    return rows


def exemptions(source: Source) -> dict[str, list[str]]:
    table: dict[str, list[str]] = defaultdict(list)
    for line in source.lines(EXEMPTIONS):
        if line.startswith("#") or not line.strip():
            continue
        menu, check, kind, _ = line.split(None, 3)
        table[menu.partition(":")[2]].append(f"{check}:{kind}")
    return table


def checklist_sections(source: Source) -> dict[str, str]:
    """Row ID to lower-case text for every ``## X01`` section of the campaign checklists."""
    sections: dict[str, str] = {}
    for path in sorted((source.repo / RECORDS).glob("CAMPAIGN-CHECKLISTS-*.md")):
        for part in re.split(r"(?m)^## ", path.read_text(encoding="utf-8"))[1:]:
            match = re.match(r"([A-Z]\d{2})\b", part)
            if match:
                sections[match.group(1)] = part.lower()
    return sections


def naming_rows(sections: dict[str, str], keywords) -> list[str]:
    patterns = [re.compile(r"(?<![a-z])" + re.escape(word.lower()) + r"(?![a-z])") for word in keywords]
    return sorted(row for row, text in sections.items() if any(pattern.search(text) for pattern in patterns))


def _class_in_jar(files: dict[str, bytes], relative: str) -> int:
    return int(f"io/github/sunthemoon/{MOD}/{relative}.class" in files)


def screens(files: dict[str, bytes], source: Source) -> list[dict]:
    registered = registered_menus(source)
    if registered != set(MENUS):
        raise K13Error("Menu types differ from the curated table: only in source "
                       f"{sorted(registered - set(MENUS))}, only in table {sorted(set(MENUS) - registered)}")
    sections = checklist_sections(source)
    rows = []
    for menu, entry in sorted(MENUS.items()):
        named = [row for row in naming_rows(sections, entry["keywords"]) if row not in BLANKET_ROWS]
        rows.append(dict(id=f"{MOD}:{menu}", kind="menu screen", menu_class=entry["menu_class"].split("/")[-1],
                         screen_class=entry["screen_class"].split("/")[-1],
                         menu_class_in_jar=_class_in_jar(files, entry["menu_class"]),
                         screen_class_in_jar=_class_in_jar(files, entry["screen_class"]),
                         opened_by=f"{entry['opened_by']}: {entry['holders']}", rows_naming_it=" ".join(named),
                         blanket_rows=" ".join(BLANKET_ROWS), named_by_a_row=int(bool(named)),
                         source=source.locations(entry["anchors"][:1])))
    for entry in OTHER_SCREENS:
        named = [row for row in naming_rows(sections, entry["keywords"]) if row not in BLANKET_ROWS]
        rows.append(dict(id=entry["id"], kind=entry["kind"], menu_class="",
                         screen_class=entry["screen_class"].split("/")[-1], menu_class_in_jar="",
                         screen_class_in_jar=_class_in_jar(files, entry["screen_class"]),
                         opened_by=entry["opened_by"], rows_naming_it=" ".join(named),
                         blanket_rows=" ".join(BLANKET_ROWS), named_by_a_row=int(bool(named)),
                         source=source.locations(entry["anchors"])))
    absent = [row["id"] for row in rows if row["menu_class_in_jar"] == 0 or row["screen_class_in_jar"] == 0]
    if absent:
        raise K13Error(f"The JAR has no class for these curated screens: {absent}")
    return rows


def sound_producers(event_rows: list[dict], source: Source) -> list[dict]:
    events = {row["event"]: row for row in event_rows if row["namespace"] == MOD}
    curated = {entry["event"] for entry in SOUND_PRODUCERS if ":" not in entry["event"] and " " not in entry["event"]}
    if curated != set(events):
        raise K13Error("Sound events differ from the curated producers: only in the JAR "
                       f"{sorted(set(events) - curated)}, only in the table {sorted(curated - set(events))}")
    rows = []
    for entry in SOUND_PRODUCERS:
        event = events.get(entry["event"])
        rows.append(dict(event=(f"{MOD}:{entry['event']}" if event else entry["event"]),
                         defined_in_jar=int(bool(event)), producer=entry["producer"], what=entry["what"],
                         subtitle_key=event["subtitle_key"] if event else "(vanilla or data-driven)",
                         subtitle_in_en_us=event["subtitle_in_en_us"] if event else "",
                         subtitle_in_zh_cn=event["subtitle_in_zh_cn"] if event else "",
                         unbound_in_w16=(int(entry["event"] in W16_UNBOUND) if event else ""),
                         source=source.locations(entry["anchors"])))
    return rows


ACCESS_FIELDS = ("subject", "id", "holder", "who_may_open", "who_may_act", "operator_level", "distance_check",
                 "loaded_chunk_check", "rate_limit", "refusal_message_or_log", "gametest_matrix_exemptions",
                 "finding", "source")


def access_matrix(source: Source) -> list[dict]:
    exempt = exemptions(source)
    rows = []
    for menu, entry in sorted(MENUS.items()):
        rows.append(dict(subject="menu", id=f"{MOD}:{menu}", holder=entry["holders"], who_may_open=entry["open"],
                         who_may_act=entry["act"], operator_level=entry["operator"],
                         distance_check=entry["distance"], loaded_chunk_check=entry["chunk"],
                         rate_limit=entry["rate"], refusal_message_or_log=entry["refusal"],
                         gametest_matrix_exemptions=" ".join(exempt.get(menu, [])), finding=entry["notes"],
                         source=source.locations(entry["anchors"])))
    pinned = [row for row in pinned_messages(source) if row["direction"] == "PLAY_TO_SERVER"]
    keys = {(row["channel"].partition(":")[2], row["message"]) for row in pinned}
    if keys != set(CHANNELS):
        raise K13Error("Client-to-server messages differ from the curated table: only in the pin "
                       f"{sorted(keys - set(CHANNELS))}, only in the table {sorted(set(CHANNELS) - keys)}")
    for row in pinned:
        entry = CHANNELS[(row["channel"].partition(":")[2], row["message"])]
        rows.append(dict(subject="client-to-server message",
                         id=f"{row['channel']} protocol {row['protocol']} index {row['index']} {row['message']}",
                         holder="the sender", who_may_open="not applicable", who_may_act=entry["who"],
                         operator_level=entry["operator"], distance_check=entry["distance"],
                         loaded_chunk_check=entry["chunk"], rate_limit=f"{entry['rate']}; {entry['bounds']}",
                         refusal_message_or_log=entry["refusal"], gametest_matrix_exemptions="",
                         finding=entry["finding"], source=source.locations(entry["anchors"])))
    for entry in MENULESS:
        rows.append(dict(subject="block use without a menu", id=f"{MOD}:{entry['id']}", holder=entry["id"],
                         who_may_open="not applicable: no menu type", who_may_act=entry["who"], operator_level="",
                         distance_check=entry["distance"], loaded_chunk_check="vanilla block use",
                         rate_limit="", refusal_message_or_log=entry["refusal"], gametest_matrix_exemptions="",
                         finding="checklist I11 names this device; it has no menu and no client-to-server message",
                         source=source.locations(entry["anchors"])))
    return rows


def pinned_channels(source: Source) -> list[str]:
    return sorted({row["channel"] for row in pinned_messages(source)})


# --------------------------------------------------------------------------------------------------------------
# Output
# --------------------------------------------------------------------------------------------------------------

def write_csv(path: Path, rows: list[dict], fields: tuple[str, ...]) -> None:
    with open(path, "w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields, lineterminator="\n")
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


def _count(rows: list[dict], field: str) -> dict[str, int]:
    counts: dict[str, int] = defaultdict(int)
    for row in rows:
        counts[str(row[field])] += 1
    return dict(sorted(counts.items()))


def generate(jar: Path, out: Path, repo: Path | None, expect_sha256: str | None = None, strict: bool = True) -> dict:
    jar_hash = sha256_file(jar)
    if expect_sha256 and jar_hash != expect_sha256.lower():
        raise K13Error(f"{jar} has SHA-256 {jar_hash}, not {expect_sha256}")
    files = read_jar(jar)
    out.mkdir(parents=True, exist_ok=True)
    assets = inventory(files)
    language_rows, missing = languages(files)
    events = sound_events(files, language_rows)
    classes, families = classify(files, strict=strict)
    missing_zip, missing_rows = missing_resource_pack(files)
    long_zip, long_rows = long_text_pack(files)

    write_csv(out / "assets.csv", assets, ("path", "kind", "namespace", "size", "sha256"))
    write_csv(out / "lang-keys.csv", language_rows,
              ("namespace", "key", "in_en_us", "in_zh_cn", "en_us_chars", "zh_cn_chars"))
    write_csv(out / "lang-missing.csv", missing, ("namespace", "key", "missing_from"))
    write_csv(out / "sound-events.csv", events, ("namespace", "event", "sound_files", "attenuation_distance",
                                                 "subtitle_key", "subtitle_in_en_us", "subtitle_in_zh_cn"))
    write_csv(out / "classification.csv", classes, ("path", "kind", "class", "reason", "family"))
    write_csv(out / "model-families.csv", families, ("kind", "family", "members", "high_risk_members", "pick"))
    (out / "k13-missing-resource-pack.zip").write_bytes(missing_zip)
    (out / "k13-long-text-pack.zip").write_bytes(long_zip)
    write_csv(out / "pack-contents.csv", missing_rows + long_rows,
              ("pack", "path", "action", "reason", "original_sha256", "pack_sha256"))

    summary = dict(
        jar=jar.name, jar_sha256=jar_hash, jar_entries=len(files),
        assets_by_kind=_count(assets, "kind"),
        language=dict(en_us_keys=sum(row["in_en_us"] for row in language_rows),
                      zh_cn_keys=sum(row["in_zh_cn"] for row in language_rows), missing=len(missing)),
        sound_events=len(events), classes=_count(classes, "class"),
        classes_by_kind={kind: _count([row for row in classes if row["kind"] == kind], "class")
                         for kind in sorted({row["kind"] for row in classes})},
        model_families=len(families), family_picks=sum(1 for row in families if row["pick"]),
        packs={"k13-missing-resource-pack.zip": sha256_bytes(missing_zip),
               "k13-long-text-pack.zip": sha256_bytes(long_zip)},
        long_text=dict(cap=LONG_TEXT_CAP, files=len(long_rows)))
    if repo is not None:
        source = Source(repo)
        screen_rows = screens(files, source)
        producer_rows = sound_producers(events, source)
        access_rows = access_matrix(source)
        write_csv(out / "screens.csv", screen_rows,
                  ("id", "kind", "menu_class", "screen_class", "menu_class_in_jar", "screen_class_in_jar",
                   "opened_by", "rows_naming_it", "blanket_rows", "named_by_a_row", "source"))
        write_csv(out / "sound-producers.csv", producer_rows,
                  ("event", "defined_in_jar", "producer", "what", "subtitle_key", "subtitle_in_en_us",
                   "subtitle_in_zh_cn", "unbound_in_w16", "source"))
        write_csv(out / "access-matrix.csv", access_rows, ACCESS_FIELDS)
        summary.update(
            screens=len(screen_rows), menu_types=len(MENUS),
            screens_no_row_names=[row["id"] for row in screen_rows if not row["named_by_a_row"]],
            sound_producer_rows=len(producer_rows),
            events_without_producer=[row["event"] for row in producer_rows if row["producer"] == "none"],
            access_rows=len(access_rows), pinned_channels=pinned_channels(source),
            client_to_server_messages=len(CHANNELS))
    (out / "summary.json").write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8",
                                      newline="\n")
    names = sorted(path.name for path in out.iterdir() if path.is_file() and path.name != "SHA256SUMS.txt")
    (out / "SHA256SUMS.txt").write_text(
        "".join(f"{sha256_file(out / name)}  {name}\n" for name in names), encoding="utf-8", newline="\n")
    return summary


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--expect-sha256")
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[2],
                        help="the checkout whose src/main matches the JAR")
    arguments = parser.parse_args(argv)
    try:
        summary = generate(arguments.jar, arguments.out, arguments.repo, arguments.expect_sha256)
    except K13Error as failure:
        print(f"K13: {failure}", file=sys.stderr)
        return 1
    print(json.dumps(summary, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
