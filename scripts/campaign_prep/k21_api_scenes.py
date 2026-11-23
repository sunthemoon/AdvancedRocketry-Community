"""Copy-only K21 API scenes on C6, one externally locked server per invocation.

No NBT mutation commands or save writers. Unreachable player-only setup is
reported, never replaced by the old runners' direct NBT setup. Client steps are
prospective; the driver never starts a client or changes a campaign cell.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import tomllib
from uuid import UUID
import zipfile

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.campaign_prep import k03_chain as chain
from scripts.campaign_prep import k16_core as common
from scripts import run_dedicated_server_smoke as server
from scripts import v170_world_fixture as fixture

NS, FX = common.NS, "arce_adapter_test"
OW, MOON = "minecraft:overworld", NS + ":moon"
OUTPUT = common.EVIDENCE / "final-campaign/c8fa16e16/fixtures/K21"
CANDIDATE = OUTPUT.parents[1] / "candidate"
FIXTURE = CANDIDATE / "arce-adapter-compat-test-1.0.0.jar"
FIXTURE_HASH = "4396196bbb7d1ccef81e55f274605e06d773fd80256dc83f81e2bad2ca5191fa"
API = CANDIDATE / "advancedrocketry-community-1.20.1-1.8.0-dev-api.jar"
API_HASH = "371beb93d87f3c673c76bfd9b02b9a9706976a0a49f3f61daa27a31fff771b8c"
PACKS = {
    "atmosphere_tag_enabled.zip": "c6ecc70d5f57927bfddfa136efa1f27318bcbc48dfc422ebd264a3f88d10eb1a",
    "atmosphere_tag_disabled.zip": "a95ef0dd725b39dd92e626126255a4005bd17d754bc952e41e9925932fd28081",
    "satellite_payload.zip": "feac2b6a20d1fc241eaea4100525851829a8ffea2606ca5204b5627cfa30b37b",
    "satellite_payload_changed.zip": "b6cafb4e45e3b97c53513c217137e3de8b8af9507fbff73f4dab10fb8f55570a",
}
SCENES = ("component", "moon", "payload", "flight-recovery")
MODES = ("build", "qualify", "changed", "changed-qualify", "remove")
OWNER = "c6fcdf97-8f10-450e-ad36-67a644d23c2b"
CONSOLE_OWNER = "00000000-0000-0000-0000-000000000005"
COMPONENTS = {(264,101,264): "minecraft:diamond_block", (263,101,264): "minecraft:emerald_block",
              (264,102,264): "minecraft:oak_planks", (264,103,264): "minecraft:gold_block",
              (265,101,264): "minecraft:quartz_block"}
FLIGHT = {(264,101,264): NS+":rocket_motor", (263,101,264): NS+":rocket_fuel_tank",
          (264,102,264): NS+":rocket_seat", (264,103,264): NS+":guidance_computer",
          (265,101,264): FX+":cargo_container"}


def pos(value):
    chain.require(len(value) == 3 and all(type(n) is int for n in value), "Integer position required")
    chain.require(all(abs(n) <= 4096 for n in (value[0], value[2])) and 1 <= value[1] <= 250,
                  "Position outside finite scene bounds")
    return " ".join(map(str, value))


def identity(jar, fixture_jar, api, packs):
    for path, expected in ((jar, common.JAR_HASH), (fixture_jar, FIXTURE_HASH), (api, API_HASH)):
        chain.require(path.is_file() and not server.is_link_or_junction(path) and chain.digest(path) == expected,
                      "Unpinned artifact: " + str(path))
    chain.verify_seal(packs)
    for name, expected in PACKS.items():
        chain.require(chain.digest(packs / name) == expected, "Unpinned K06 pack: " + name)
    with zipfile.ZipFile(fixture_jar) as archive:
        raw = archive.read("META-INF/mods.toml").decode("utf-8")
    metadata = tomllib.loads(raw)
    chain.require(metadata["mods"][0]["modId"] == FX, "Fixture mod identity differs")
    dependencies = metadata["dependencies"][FX]
    chain.require(all(d["mandatory"] and d["side"] == "BOTH" for d in dependencies),
                  "Fixture dependency declaration differs")
    chain.require("displayTest" not in metadata["mods"][0], "Unexpected display-test exemption")
    return dict(candidate_commit=common.CANDIDATE, release=dict(path=str(jar), sha256=chain.digest(jar)),
                fixture=dict(path=str(fixture_jar), sha256=chain.digest(fixture_jar), metadata=metadata,
                             metadata_text=raw), api=dict(path=str(api), sha256=chain.digest(api), installed=False),
                packs=PACKS, client_policy="Required on both sides, no display-test exemption: expectation only, not a measured connection")


def plan(scene, terminal=(264,101,264), owner=OWNER, moon_revision=1):
    chain.require(scene in SCENES, "Unknown scene")
    chain.require(moon_revision in (1,2) and (moon_revision == 1 or scene == "moon"), "Invalid Moon revision")
    terminal, owner = list(terminal), str(UUID(owner))
    pos(terminal)
    # The existing independent fixture's finite command has a fixed actor. Never
    # relabel its package as belonging to an authenticated campaign account.
    chain.require(owner == OWNER, "Pinned fixture create command has a fixed owner; arbitrary owners need real player manufacture")
    layout = dict(schema_version=1, scene=scene, world_origin="Fresh C6 flat world; no inherited discoveries, bindings or synthetic FI08 endgame devices",
        terminal=terminal, payload_owner=owner, rocket_owner=CONSOLE_OWNER,
        owners="Disposable fixture/console actors, not authenticated campaign accounts",
        positions=dict(assembler=[264,100,264], component_loader=[268,101,264],
                       completion_origin=[265,101,268], moon_vent=[264,220,264],
                       moon_boundary=[264,222,264], suit_chest=[268,220,264]),
        limitations=["R638: no NBT setup. The item loader is unclaimed and component fuel remains zero, not F=750; D06 claim/fuelling runs through ordinary player actions in the campaign.",
                    "The Moon vent starts empty/unpowered; D04 supply, revocation and recovery are scripted campaign actions, not native preparation results.",
                    "The payload create command uses a fixed FakePlayer and assembles through a production menu. Real A must manufacture its own package/chip; no ownership rewriting.",
                    "Real clients, GPU, reconnects, two-restart campaign cases, slow/rejected callbacks, hostile intents and REF measurements are not executed here."],
        client_steps=client_steps(), required_gates_approved=False)
    if moon_revision == 2:
        layout.update(moon_revision=2, world_origin="Copy of sealed K21/moon/source-world, originally Fresh C6 flat; R638 ordinary empty blocks only")
        layout["positions"].update(moon_boundary=[265,222,264], moon_generator=[263,220,264],
                                   moon_supply=[268,220,266], moon_breach=[264,221,263], player_feet=[264,221,264])
        layout["geometry_change"] = "Keep the original vent/root. Raise the roof to y=223 for two-block player headroom; move the open boundary to the east wall. No NBT writes."
    return layout


def client_steps():
    return dict(status="SCRIPTED_ONLY_NOT_EXECUTED", connections=[
        dict(case="installed", steps=["Use a fresh copy of a scene, host plus the exact mandatory fixture on both sides, online-mode=true and the campaign A/B whitelist; RCON off and setup JVM properties removed.",
            "Connect authenticated A then B with the installed fixture. Capture full mod inventories, handshake/client/server logs and join result. Reconnect B; compare inventory and scene authority."]),
        dict(case="omitted", steps=["Use another fresh server copy with the fixture installed. Use an isolated disposable client copy with only its copied fixture JAR omitted; do not edit campaign cells.",
            "Attempt connection, keep actual diagnostics and outcome. Required-both-sides/no-exemption metadata predicts refusal; a successful omitted-fixture connection is a finding, not an inferred pass."])],
        equipment=dict(items=["minecraft:leather_"+p for p in ("helmet","chestplate","leggings","boots")],
            capacity=2000, initial_oxygen=0, refill_units=1000, interval_ticks=20,
            steps=["A equips all four correctly slotted leather pieces from the supply chest. Capture complete armor and full/empty canister counts using read-only data get entity A Inventory.",
                "A uses one full canister normally before entering Moon vacuum. Record actual oxygen, remaining full canisters and empty shells, Health and time query gametime; no historical actor count is an expectation.",
                "Observe at least 20 actual server ticks in Survival vacuum, capture oxygen/health/HUD and refill again through normal item use. Compute consumption from measured before/after values and elapsed ticks, not wall time.",
                "Disconnect and reconnect the same real account; retain armor payload, oxygen, counts and HUD before/after without a repeated grant/refill."]),
        atmosphere=dict(case="D04", source="moon-r2/source-world", status="SCRIPTED_ONLY_NOT_EXECUTED",
            read_only_commands=[f"execute in {MOON} run data get block 264 220 264 arce_oxygen_vent",
                f"execute in {MOON} run data get block 263 220 264 arce_combustion_generator",
                f"execute in {MOON} run data get block 268 220 266 Items", "arce atmosphere status", "time query gametime"],
            steps=[dict(action="On a fresh campaign copy, put real A at 264.5 221 264.5 in Survival with no armor/canister in either hand. Capture health, HUD and gametime. Use operator read-only block commands from the console; atmosphere status must run as A inside the room, not as console.",
                        expected_readback="Sealing tag true, permeable false at boundary 265 222 264; vent schema_version=2 with intact scrubbers, oxygen/energy=0. Generator is empty, energy/duration/remaining=0. Chest has coal16, full oxygen canisters3 and glass4. Unsupplied Moon room is VACUUM, not BREATHABLE. Keep D05 equipment separate."),
                   dict(action="A takes the coal and canisters from 268 220 266. Open the adjacent generator at 263 220 264 and put coal in its normal fuel slot; close the menu. Use three full canisters normally on the vent at 264 220 264, retaining all empty shells. Wait for bounded room processing; record actual before/after resource counts and server ticks.",
                        expected_readback="Generator consumes ordinary coal and supplies FE to the adjacent vent. Three successful canister uses consume three full items and return three empties; oxygen additions total3000 before measured consumption, never a fixed post-delay value. Vent has positive oxygen and energy, no quarantined/invalid root. As A: Atmosphere=BREATHABLE vents=1 providers=1; unarmored A's health/HUD confirms protection."),
                   dict(action="While resources remain positive, operator disables only file/atmosphere_tag_enabled.zip and reloads, capturing reload generation and read-only tag queries at 265 222 264. A stays inside; capture status/health/HUD immediately and after the measured interval.",
                        expected_readback="Sealing membership false, permeable false: the registered open=true API boundary now permits a leak. A reports VACUUM and protection is revoked despite remaining vent resources; preserve exact resource/root readbacks. No data merge or replacement vent."),
                   dict(action="Operator enables the same hashed K06 enabled pack last, reloads and confirms sealing membership true. Observe A inside with continuing supply, then A normally breaks the glass at 264 221 263 and repairs it with one supplied glass block. Do not substitute setblock for the break/repair event.",
                        expected_readback="Tag restoration recovers BREATHABLE. Real wall break revokes protection (VACUUM); normal glass placement recovers BREATHABLE, vents=1 providers=1. Capture each transition plus health/HUD, gametime, vent and generator roots. Failure to recover is a finding, not a presumed pass."),
                   dict(action="Save/stop/restart the campaign copy twice, reconnecting the same authenticated A and sampling the same room/status and inventories after each. Replenish only through recorded ordinary actions if needed.",
                        expected_readback="Sealing membership remains true and vent schema2/scrubber identity is retained. Supplied closed room recovers BREATHABLE without replayed grants/refills; account for actual tick consumption. These restarts are campaign work, not the native empty-scene reopen.")]),
        component=dict(initial_target=750, capacity=1500, half_fill_items={"minecraft:coal":3,"minecraft:stick":41},
            claim_and_half_fill=[
                dict(action="Use component/source-world as a fresh campaign copy. Keep its console-owned zero-fuel rocket unchanged. From ordinary inventory A places assembler at 272 100 264 and diamond at 272 101 264, emerald at 271 101 264, oak planks at 272 102 264, gold at 272 103 264 and quartz at 273 101 264; A shift-right-clicks the assembler empty-handed to assemble normally. Leave the preplaced completion structure at 265 101 268 for later.",
                     expected_readback="New rocket UUID differs from console rocket; owner_id equals authenticated A, operational=true, state=ASSEMBLED, captured mass254/thrust2400/fuel_capacity1500 and fuel amount0. Ordinary loader 268 101 264 is within6 blocks; loaded chunks, owner, state, capacity and recovery admission all permit transfer. The console-owned rocket is ineligible."),
                dict(action="A uses the already placed ordinary item loader 268 101 264 empty-handed, before any fuel insert. Capture UUID/Inventory, loader arce_fuel_loader root and both complete RocketEntityData roots with read-only data get. Use Survival, not Creative, for every fuel use.",
                     expected_readback="Loader schema_version=2, owner_id equals A (was absent); buffered_units=0, slot_role=0, item={}, no batch/target. Fuel remains0. This is a real empty-hand claim, not a fluid loader, escrow or NBT owner edit."),
                dict(action="Take exactly3 coal and41 sticks from chest 270 101 264, leaving the blaze powder for the subsequent test. Use one coal on the loader, await item/buffer/batch drainage, repeat until3; then feed41 sticks individually, waiting for drainage each time. Capture after each accepted item; never queue the blaze powder early.",
                     expected_readback="Each coal adds127, so cumulative fuel127,254,381. Stick n adds9 to381, for n=1..41; final fuel750 of1500, remaining capacity750. Exactly3 coal and41 sticks consumed; loader item/batch empty, buffered_units0, no pending target; console rocket still zero. No elapsed-time guess or inferred F750." )],
            read_only_commands=["data get block 268 101 264 arce_fuel_loader", "data get block 270 101 264 Items",
                f"data get entity @e[type={NS}:rocket,sort=nearest,limit=1,x=272,y=101,z=264] RocketEntityData",
                "data get entity A UUID", "data get entity A Inventory"],
            steps=["A assembles the independent initial target at 272 101 264 and claims the existing ordinary loader as detailed above. Do not use the console-owned rocket as A's ownership control.",
                "Feed 3 coal and 41 sticks individually through the ordinary loader (3*127+41*9=750). Read fuel=750 and loader empty before inserting one blaze powder.",
                "After tank reaches 1500, record captured large_fuel total=1573, buffer=823, no early stick. Save/stop and restart this copy.",
                "Assemble the separate five-block completion structure at 265 101 268 for the same owner. Read operational/state/remaining capacity/distance and actual transfer of all 823 units; exactly one stick, then a second restart with no replay."]),
        payload=dict(terminal_parameter="--terminal X Y Z", owner_parameter="--owner; only pinned actor accepted by native create; real owners manufacture normally",
            steps=["A uses its own powered terminal, untagged amethyst shard, chassis, solar module and unbound chip. Manufacture and launch Earth/Moon under satellite_payload.zip; record mission 400 ticks/reward137/conditional fee11 before any definition change.",
                "Keep A's menu open. Enable satellite_payload_changed.zip above the baseline and reload; read current generation/20/211/11 definition and the old mission's captured 400/137/fee fields.",
                "Use K18's bounded stale/range/non-owner controls with receipt plus before/after authority and loaded chunks; disabled controls are not refusal evidence.",
                "Reopen menu, wait for READY, claim once and replay; retain exact balances/discovery/receipt. Perform two clean restarts on the campaign copy."]),
        controls=dict(provider="K18", probes=["stale menu generation", "out of range", "non-owner", "replayed claim"],
                      status="NOT_COVERED: K21 does not send hostile intents"),
        flight_recovery=["Use the external cargo source copy, inventory every slot and metadata, then ordinary assembly/flight with measured C6 quotes, Moon restart and Earth restart; discard only the observed remaining fuel with exact consent.",
                         "For provider-only D10 use a new cargo copy with skipRocketAdapter=true, capture refusal and opaque payload, restore the provider and recover once; this setup is separate from the two fixture-removal copies."])


def build_commands(layout):
    scene = layout["scene"]
    if scene == "moon":
        prefix = f"execute in {MOON} run "
        if layout.get("moon_revision") == 2:
            return [prefix+c for c in (
                "fill 263 223 263 265 223 265 minecraft:iron_block",
                "setblock 264 222 264 minecraft:air",
                f"setblock 265 222 264 {FX}:state_boundary[open=true]",
                "setblock 264 221 263 minecraft:glass",
                f"setblock 263 220 264 {NS}:combustion_generator",
                "setblock 268 220 266 minecraft:chest",
                "item replace block 268 220 266 container.0 with minecraft:coal 16",
                f"item replace block 268 220 266 container.1 with {NS}:oxygen_canister 3",
                "item replace block 268 220 266 container.2 with minecraft:glass 4")]
        commands = [prefix+"fill 263 220 263 265 222 265 minecraft:iron_block",
                    prefix+"setblock 264 221 264 minecraft:air",
                    prefix+f"setblock 264 222 264 {FX}:state_boundary[open=true]",
                    prefix+f"setblock 264 220 264 {NS}:oxygen_vent",
                    prefix+"setblock 268 220 264 minecraft:chest"]
        items = client_steps()["equipment"]["items"] + [NS+":oxygen_canister"]
        commands += [prefix+f"item replace block 268 220 264 container.{i} with {item} {2 if i == 4 else 1}"
                     for i,item in enumerate(items)]
        return commands
    if scene == "payload":
        return [f"arce_sat_fixture create {pos(layout['terminal'])}"]
    blocks = COMPONENTS if scene == "component" else FLIGHT
    commands = [f"setblock 264 100 264 {NS}:rocket_assembler"]
    commands += [f"setblock {pos(p)} {name}" for p,name in blocks.items()]
    if scene == "component":
        commands += [f"setblock 268 101 264 {NS}:fuel_loader", "setblock 270 101 264 minecraft:chest"]
        commands += [f"item replace block 270 101 264 container.{i} with {name} {count}" for i,(name,count) in
                     enumerate((("minecraft:coal",3),("minecraft:stick",41),("minecraft:blaze_powder",1)))]
        for (x,y,z),name in COMPONENTS.items():
            commands.append(f"setblock {x+1} {y} {z+4} {name}")
        commands.append(f"setblock 265 100 268 {NS}:rocket_assembler")
    else:
        commands += ["item replace block 265 101 264 container.0 with minecraft:diamond 17",
                     "item replace block 265 101 264 container.1 with minecraft:iron_ingot 64"]
    commands.append("arce rocket assemble 264 100 264")
    return commands


class Console:
    def __init__(self, process, commands):
        self.process, self.commands = process, commands

    def all(self, command):
        chain.require(not re.search(r"\bdata (merge|modify|remove)\b|\bsummon\b", command), "NBT mutation forbidden")
        start = len(self.process.lines)
        self.commands.append(command)
        self.process.command(command)
        token = "K21_END_"+str(len(self.commands))
        self.commands.append("say "+token)
        self.process.command("say "+token)
        self.process.wait_for(re.compile(r"\[Server\] "+token+r"\b"), 60, start_at=start)
        lines = [s.strip() for s in self.process.lines[start:] if token not in s]
        errors = ("Unknown or incomplete command", "Incorrect argument", "Unknown block", "Unknown item",
                  "That position is not loaded", "That position is out of this world", "is not a container",
                  "No entity was found", "Test failed", "An unexpected error occurred")
        chain.require(not any(e in s for e in errors for s in lines), "Command failed: "+command+" "+str(lines))
        return lines

    def until(self, command, marker, timeout=60):
        deadline = time.monotonic()+timeout
        while True:
            lines = self.all(command)
            if any(marker in s for s in lines):
                return lines
            chain.require(time.monotonic() < deadline, "Missing native witness: "+marker)
            time.sleep(.1)


def native_data(lines):
    found = [s.split(" data: ",1)[1] for s in lines if " data: " in s]
    chain.require(len(found) == 1, "Expected one native data response: "+str(lines))
    return found[0]


def reload_pack(console, command="reload"):
    start = len(console.process.lines)
    console.all(command)
    console.process.wait_for(re.compile(r"Accepted planetary catalog generation \d+ with 11 bodies"),
                             60, start_at=start)
    # Complete another server-thread command after the listener publication.
    console.all("arce celestial validate")


def readbacks(console, layout):
    scene = layout["scene"]
    result = dict(managed=console.all("arce beta data-report"), packs=console.all("datapack list enabled"))
    chain.require(any("operational=true" in s for s in result["managed"]), "Managed state unavailable")
    if scene in ("component", "flight-recovery"):
        selector = f"@e[type={NS}:rocket,limit=1]"
        result["rocket"] = native_data(console.all(f"data get entity {selector} RocketEntityData"))
        if scene == "component":
            result["loader"] = native_data(console.all("data get block 268 101 264 arce_fuel_loader"))
            result["supply"] = native_data(console.all("data get block 270 101 264 Items"))
        return result
    if scene == "payload":
        result["terminal"] = native_data(console.all(f"data get block {pos(layout['terminal'])} SatelliteTerminal"))
        return result
    prefix = f"execute in {MOON} run "
    result["vent"] = native_data(console.all(prefix+"data get block 264 220 264 arce_oxygen_vent"))
    result["equipment"] = native_data(console.all(prefix+"data get block 268 220 264 Items"))
    boundary = pos(layout["positions"]["moon_boundary"])
    for tag in ("atmosphere_sealing", "atmosphere_permeable"):
        command = f"execute in {MOON} if block {boundary} #{NS}:{tag} run say K21_TAG_{tag}"
        result[tag] = any(f"[Server] K21_TAG_{tag}" in s for s in console.all(command))
    if layout.get("moon_revision") == 2:
        result["moon_revision"] = 2
        result["generator"] = native_data(console.all(prefix+"data get block 263 220 264 arce_combustion_generator"))
        result["room_supply"] = native_data(console.all(prefix+"data get block 268 220 266 Items"))
        result["geometry"] = {}
        cells = (("feet","264 221 264 minecraft:air"), ("head","264 222 264 minecraft:air"),
                 ("roof","264 223 264 minecraft:iron_block"), ("breach","264 221 263 minecraft:glass"),
                 ("boundary",f"{boundary} {FX}:state_boundary[open=true]"))
        for name, cell in cells:
            result["geometry"][name] = any(f"[Server] K21_CELL_{name}" in s for s in
                console.all(f"execute in {MOON} if block {cell} run say K21_CELL_{name}"))
    return result


def compare(before, after, scene):
    fields = {"component":("rocket","loader","supply"), "flight-recovery":("rocket",),
              "payload":("terminal",), "moon":("vent","equipment","atmosphere_sealing","atmosphere_permeable")}[scene]
    if scene == "moon" and ("moon_revision" in before or "moon_revision" in after):
        fields += ("moon_revision","generator","room_supply","geometry")
    result = {key: before[key] == after[key] for key in fields}
    chain.require(all(result.values()), "Restart readbacks differ: "+str(result))
    return result


def validate_readbacks(value, scene):
    """Check supporting-scene contents, without claiming missing fuel/supply setup."""
    if scene in ("component", "flight-recovery"):
        text = value["rocket"]
        capacity, mass, thrust, entities = (1500,254,2400,0) if scene == "component" else (1000,210,1000,1)
        for field, number in (("fuel_capacity",capacity),("mass",mass),("thrust",thrust),
                              ("block_count",5),("block_entity_count",entities)):
            chain.require(re.search(rf"\b{field}: {number}(?:L)?[,}}]", text), "Missing captured metric: "+field)
        chain.require('state: "ASSEMBLED"' in text and "fuel: {amount: 0L," in text,
                      "Supporting rocket must be assembled and zero-fuel")
        if scene == "component":
            chain.require("owner_id" not in value["loader"] and "buffered_units: 0L" in value["loader"],
                          "Supporting loader must be explicitly unclaimed, not an F=750 claim")
        else:
            chain.require(FX+":cargo_inventory" in text and 'id: "minecraft:diamond"' in text
                          and 'id: "minecraft:iron_ingot"' in text and "Count: 17b" in text
                          and "Count: 64b" in text, "Opaque cargo inventory incomplete")
    elif scene == "moon":
        chain.require(value["atmosphere_sealing"] and "oxygen_units: 0" in value["vent"]
                      and "energy: 0" in value["vent"], "Expected tagged, unsupplied supporting room")
        if value.get("moon_revision") == 2:
            chain.require("schema_version: 2," in value["vent"] and "scrubbers: {generation:" in value["vent"]
                          and "banks: []" in value["vent"], "Expected intact schema2 vent/scrubber root")
            chain.require(value["generator"] == "{schema: 1, duration: 0, fuel: {}, remaining: 0, energy: 0}",
                          "Expected empty generator root")
            expected = ('[{Slot: 0b, id: "minecraft:coal", Count: 16b}, '
                        f'{{Slot: 1b, id: "{NS}:oxygen_canister", Count: 3b}}, '
                        '{Slot: 2b, id: "minecraft:glass", Count: 4b}]')
            chain.require(value["room_supply"] == expected, "Expected exact ordinary room supplies")
            chain.require(value["geometry"] == dict(feet=True, head=True, roof=True, breach=True, boundary=True)
                          and not value["atmosphere_permeable"], "Expected player headroom and closed shell")
    elif scene == "payload":
        chain.require(FX+":research_payload" in value["terminal"] and "SatelliteIdentity" in value["terminal"],
                      "No registered payload/chip identity")


def checked_copy(source, target):
    chain.require(source.is_dir() and not target.exists(), "Copy requires a source and a fresh destination")
    before = fixture.file_inventory(source)
    shutil.copytree(source, target)
    chain.require(before == fixture.file_inventory(source) == fixture.file_inventory(target), "Copy/source inventory changed")
    return before


def run(args):
    chain.require(args.scene in SCENES and args.mode in MODES, "Unknown run")
    chain.require(not args.mode.startswith("changed") or args.scene == "payload", "Changed pack is payload-only")
    chain.require(args.mode != "remove" or args.scene in ("component","payload"), "Only two removal scenes")
    chain.require(args.moon_revision == 1 or (args.moon_revision == 2 and args.scene == "moon"
                  and args.mode in ("build","qualify")), "Invalid Moon revision run")
    label = args.qualification_name or args.mode
    chain.require(args.qualification_name is None or (args.mode in ("qualify","changed-qualify")
                  and re.fullmatch(r"(?:changed-)?qualify-[a-z0-9-]{1,40}", label)), "Invalid qualification name")
    # Check authority before creating an output or starting a process.
    lock = args.lock / "owner.txt"
    chain.require(args.lock_owner.startswith("W445 pid=") and lock.read_text(encoding="utf-8-sig").startswith(args.lock_owner),
                  "W445 heavy-JVM lock required")
    drives = ("C:/","D:/") if sys.platform == "win32" else (str(args.output.parent),)
    chain.require(min(shutil.disk_usage(p).free for p in drives) >= 10*1024**3, "10 GiB free required")
    pins = identity(args.jar, args.fixture, args.api, args.packs)
    folder = args.output / ("moon-r2" if args.moon_revision == 2 else args.scene)
    if args.mode == "build":
        chain.require(not folder.exists(), "Refusing occupied source scene")
        layout = plan(args.scene, args.terminal, args.owner, args.moon_revision)
        source, source_inventory = None, None
        if args.moon_revision == 2:
            source_folder = args.output / "moon"
            chain.verify_seal(source_folder)
            source = source_folder / "source-world"
            source_inventory = fixture.file_inventory(source)
            layout["parent"] = dict(source=str(source), seal_sha256=chain.digest(source_folder / "SHA256SUMS.txt"))
        folder.mkdir(parents=True)
        evidence = folder / "creation"
    else:
        chain.verify_seal(folder)
        layout = json.loads((folder / "manifest.json").read_text())["layout"]
        source_folder = folder / "changed" if args.mode == "changed-qualify" else folder
        chain.verify_seal(source_folder)
        source = source_folder / "source-world"
        source_inventory = fixture.file_inventory(source)
        evidence = args.output / ("removal-"+args.scene) if args.mode == "remove" else folder / label
    evidence.mkdir(parents=True, exist_ok=False)
    runtime = args.output / "_work" / (folder.name+"-"+label)
    chain.require(not runtime.exists(), "Occupied runtime is not ours")
    receipt = dict(status="FAIL", scene=args.scene, mode=args.mode, commands=[], identity=pins,
                   driver_commit=subprocess.check_output(["git","rev-parse","HEAD"], cwd=common.ROOT,text=True).strip(),
                   driver_sha256=chain.digest(Path(__file__)), client_started=False)
    process = None
    try:
        fixture.prepare(args.template, runtime, args.jar, source)
        shutil.copy2(args.fixture, runtime / "mods" / args.fixture.name)
        if args.scene in ("moon","payload") and args.mode == "build" and args.moon_revision == 1:
            pack = "atmosphere_tag_disabled.zip" if args.scene == "moon" else "satellite_payload.zip"
            (runtime / "world/datapacks").mkdir(parents=True, exist_ok=True)
            shutil.copy2(args.packs / pack, runtime / "world/datapacks" / pack)
        if args.mode == "remove":
            chain.write(evidence / "before-removal-files.json", fixture.file_inventory(runtime))
            chain.write(evidence / "before-removal-world.json", source_inventory)
            # The full server inventory above includes the still-installed fixture.
            target = runtime / "mods" / args.fixture.name
            chain.require(chain.digest(target) == FIXTURE_HASH, "Wrong removal fixture")
            target.unlink()
            chain.write(evidence / "after-removal-files.json", fixture.file_inventory(runtime))
        properties = runtime / "server.properties"
        argfile = "win_args.txt" if sys.platform == "win32" else "unix_args.txt"
        command = [str(args.java),"-Xms512M","-Xmx2G"]
        if args.scene == "payload" and args.mode == "build":
            command.append("-Darce_adapter_test.satelliteSmoke=true")
        command += [f"@libraries/net/minecraftforge/forge/1.20.1-47.4.10/{argfile}", "nogui"]
        receipt.update(command=command, properties=properties.read_text())
        process = server.CapturedProcess(command, runtime, evidence / "console.log")
        process.wait_for(server.READY_MARKER, 240)
        port = int(server.parse_java_properties(properties.read_bytes())["server-port"])
        receipt["server_status"] = chain.ready_status(server, port, "1.20.1-1.8.0-dev")
        chain.require(receipt["server_status"]["players"]["online"] == 0, "Unexpected connected player")
        console = Console(process, receipt["commands"])
        dimension = MOON if args.scene == "moon" else OW
        x,y,z = layout["terminal"] if args.scene == "payload" else (264,220 if args.scene == "moon" else 101,264)
        console.all(f"execute in {dimension} run forceload add {x-16} {z-16} {x+16} {z+16}")
        console.until(f"execute in {dimension} if loaded {x} {y} {z} run say K21_LOADED", "[Server] K21_LOADED")
        if args.moon_revision == 2 and args.mode == "build":
            parent = json.loads((source_folder / "creation/receipt.json").read_text())["readbacks"]
            old_layout = json.loads((source_folder / "manifest.json").read_text())["layout"]
            receipt["parent_matches"] = compare(parent, readbacks(console, old_layout), "moon")
        if args.mode == "build":
            for command in build_commands(layout):
                lines = console.all(command)
                if command.startswith("arce rocket assemble"):
                    console.until("arce rocket status 264 100 264", "SUCCESS")
                if command.startswith("arce_sat_fixture"):
                    chain.require(any('"result":0' in line for line in lines), "Payload create unsuccessful")
        elif args.mode != "remove":
            baseline_path = folder / ("changed/receipt.json" if args.mode == "changed-qualify" else "creation/receipt.json")
            baseline = json.loads(baseline_path.read_text())["readbacks"]
            receipt["matches"] = compare(baseline, readbacks(console, layout), args.scene)
        if args.scene == "moon" and args.mode == "build" and args.moon_revision == 1:
            pack = "atmosphere_tag_enabled.zip"
            shutil.copy2(args.packs / pack, runtime / "world/datapacks" / pack)
            receipt["before_tag"] = readbacks(console, layout)
            console.all("datapack list available")
            reload_pack(console, f'datapack enable "file/{pack}" last')
            console.until(f"execute in {MOON} if block 264 222 264 #{NS}:atmosphere_sealing run say K21_TAG_READY", "[Server] K21_TAG_READY")
        if args.mode == "changed":
            pack = "satellite_payload_changed.zip"
            shutil.copy2(args.packs / pack, runtime / "world/datapacks" / pack)
            console.all("datapack list available")
            reload_pack(console, f'datapack enable "file/{pack}" last')
            console.until("datapack list enabled", pack)
        # Explicit reload and command barrier; receipt retains actual listener logs.
        reload_pack(console)
        receipt["readbacks"] = readbacks(console, layout)
        if args.moon_revision == 2 and args.mode == "build":
            chain.require(all(receipt["readbacks"][key] == parent[key] for key in ("vent","equipment")),
                          "R638 revision changed the inherited vent/equipment root")
        if args.mode != "remove":
            validate_readbacks(receipt["readbacks"], args.scene)
        if args.mode in ("qualify","changed","changed-qualify"):
            receipt["matches_after_reload"] = compare(baseline, receipt["readbacks"], args.scene)
        console.all("save-all flush")
        receipt["commands"].append("stop")
        process.command("stop")
        receipt["exit_code"] = process.finish()
        chain.require(receipt["exit_code"] == 0, "Server exit not clean")
        receipt["errors"] = [line.strip() for line in process.lines if re.search(r"/(ERROR|FATAL)\]",line)]
        if args.mode != "remove":
            chain.require(not receipt["errors"], "Unexpected server ERROR/FATAL")
        receipt["registration_and_reload"] = [line.strip() for line in process.lines if any(s in line for s in
            ("Registered ", "Rejected ", "Accepted satellite", "Accepted planetary", "Unidentified mapping", "missing"))]
        chain.write(evidence / "world-files.json", fixture.file_inventory(runtime / "world"))
        if source:
            chain.require(source_inventory == fixture.file_inventory(source), "Source world changed")
            chain.verify_seal(source_folder)
            receipt["source_unchanged"] = True
        if args.mode in ("build","changed","remove"):
            destination = folder / "source-world" if args.mode == "build" else evidence / (
                "source-world" if args.mode == "changed" else "outcome-world")
            checked_copy(runtime / "world", destination)
            if args.mode == "changed":
                chain.write(evidence / "manifest.json", dict(identity=pins, layout=layout,
                    variant="changed payload definition; no started mission", readbacks=receipt["readbacks"],
                    commands=receipt["commands"], inventory="world-files.json"))
        receipt["status"] = "QUALIFIED_SUPPORTING_SCENE_NOT_CAMPAIGN_PASS" if args.mode != "remove" else "REMOVAL_OUTCOME_RECORDED_NOT_GATE"
    except BaseException as error:
        receipt["error"] = f"{type(error).__name__}: {error}"
        raise
    finally:
        if process:
            if sys.platform == "win32":
                common.abort_owned(process, evidence)
            else:
                process.abort()
            chain.require(process.process.poll() is not None, "Owned JVM alive; retain lock/runtime")
        if runtime.exists():
            # A refused removal start may already have changed native files.
            # Preserve that stopped outcome as well; never throw it away merely
            # because there was no READY marker or normal readback.
            if args.mode == "remove" and (runtime / "world").is_dir() and not (evidence / "outcome-world").exists():
                checked_copy(runtime / "world", evidence / "outcome-world")
            for name in ("logs/debug.log", "config/advancedrocketrycommunity-common.toml"):
                if (runtime / name).is_file():
                    shutil.copy2(runtime / name, evidence / Path(name).name)
            chain.require(runtime.resolve().is_relative_to((args.output / "_work").resolve()), "Unsafe cleanup")
            shutil.rmtree(runtime)
        chain.write(evidence / "receipt.json", receipt)
    if args.mode == "build":
        chain.write(folder / "manifest.json", dict(identity=pins, layout=layout, commands=receipt["commands"],
            readbacks=receipt["readbacks"], inventory="creation/world-files.json", completeness="PARTIAL: see layout limitations"))
    chain.seal(folder if args.mode == "build" else evidence)
    return receipt


def parser():
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--scene", choices=SCENES, required=True)
    result.add_argument("--mode", choices=MODES, required=True)
    result.add_argument("--output",type=Path,default=OUTPUT)
    result.add_argument("--jar",type=Path,default=common.JAR)
    result.add_argument("--fixture",type=Path,default=FIXTURE)
    result.add_argument("--api",type=Path,default=API)
    result.add_argument("--packs",type=Path,default=OUTPUT.parent / "K06")
    result.add_argument("--lock",type=Path,default=chain.LOCK)
    result.add_argument("--lock-owner",required=True)
    result.add_argument("--template",type=Path,required=True)
    result.add_argument("--java",type=Path,required=True)
    result.add_argument("--terminal",type=int,nargs=3,default=(264,101,264))
    result.add_argument("--owner",default=OWNER)
    result.add_argument("--moon-revision",type=int,choices=(1,2),default=1,
                        help="2 builds a new sibling from a copy of the sealed original Moon source")
    result.add_argument("--qualification-name", help="Fresh qualification directory; never replaces a prior receipt")
    return result


if __name__ == "__main__":
    print(json.dumps(run(parser().parse_args()), indent=2))
