"""Copy-only K07 planetary fixtures on pinned C6; one externally locked run per call.

Sources are immutable. Only Minecraft writes saves. Qualification opens another
copy with no hooks; a pending receipt is expected to recover, not remain pending.
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
from uuid import UUID
import zipfile

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.campaign_prep import k03_chain as chain
from scripts.campaign_prep import k16_core as common

ROOT, NS = common.ROOT, common.NS
OUTPUT = common.EVIDENCE / "final-campaign/c8fa16e16/fixtures/K07"
FIXTURES = OUTPUT.parent
VARIANTS = ("discovered", "undiscovered", "private-station", "paid-pending", "blocked-binding", "pre-sky", "hazards")
OWNER = "04380000-0000-4000-8000-000000000001"
OTHER = "04380000-0000-4000-8000-000000000002"
FI05_OWNER = chain.DEFAULT_OWNER
WRITER = "arce satellite release-test registry-writer "
BINDINGS = NS + "_planetary_bindings.json"


def source_for(name, fixtures=FIXTURES):
    chain.require(name in VARIANTS, "Unknown K07 variant")
    return fixtures / ("K02/FI04" if name in ("undiscovered", "paid-pending", "pre-sky") else "K03/FI05") / "source-world"


def owners(owner, other):
    owner, other = str(UUID(owner)), str(UUID(other))
    chain.require(owner != other, "Two distinct disposable owners required")
    chain.require(FI05_OWNER not in (owner, other), "Do not reuse FI05's station owner")
    return owner, other


def values(jar):
    """Read shipped values, not numbers inherited from an old checklist."""
    result = dict(bodies={}, routes={}, satellites={}, sky_resources={})
    with zipfile.ZipFile(jar) as archive:
        for name in sorted(archive.namelist()):
            if name.endswith("/"):
                continue
            category = next((key for directory, key in (("celestial_bodies", "bodies"),
                ("travel_routes", "routes"), ("satellite_definitions", "satellites"))
                if name.startswith("data/") and f"/{directory}/" in name and name.endswith(".json")), None)
            if category:
                result[category][name] = json.loads(archive.read(name))
            if name.startswith("assets/") and "/celestial_visuals/" in name:
                result["sky_resources"][name] = common.digest(archive.read(name))
    return result


def hazard_plan():
    # These are prospective player experiments, not fake-player observations.
    sites = []
    for name, body, x, roof, hazards in (
        ("mars-open", "mars", 8192, False, ["cold", "vacuum"]),
        ("venus-open", "venus", 8192, False, ["heat", "pressure", "solar", "vacuum"]),
        ("venus-roof", "venus", 8224, True, ["heat", "pressure", "vacuum"]),
        ("moon-vacuum", "moon", 8192, False, ["vacuum"]),
    ):
        sites.append(dict(name=name, dimension=NS + ":" + body, feet=[x + 2.5, 305, 8194.5],
                          platform=[x, 304, 8192, x + 6, 304, 8198], roof=roof,
                          unprotected_hazards=hazards, observed_player_damage=False))
    return dict(sites=sites, interval_ticks=20, environmental_damage=2.0,
                priority=["pressure", "heat", "cold", "solar"], warning_heartbeat_ticks=100,
                vacuum_damage=2.0, difficulty="normal", modifiers="No effects, enchantments or configuration overrides; vanilla game rules retained",
                equipment=[NS + ":space_suit_" + part for part in ("helmet", "chestplate", "leggings", "boots")],
                campaign_steps=[
                    "Use an O5-bound account in Survival. Supply and fill the complete suit through normal equipment actions; record oxygen and equipment before each sample.",
                    "At moon-vacuum, compare no suit with the oxygen-filled complete suit (vacuum alone).",
                    "At mars-open, compare no suit, an empty complete suit (vacuum alone because passive protection remains), and an oxygen-filled complete suit (protected control).",
                    "At venus-open and venus-roof, sample unprotected combined exposure, then empty and oxygen-filled complete suits; use normal daytime, read time before each sample, never freeze the game rules.",
                    "Record damage source, invulnerability/effects, oxygen, sky visibility, elapsed server ticks and health. A suffocation attempt suppresses the coincident environmental attempt, so masks are not additive damage claims."],
                not_covered=["Isolated cold, heat, pressure and solar player damage: built-in complete armor grants all three passive tags, while incomplete armor loses oxygen protection. No selective tags, custom body catalog or oxygen NBT is invented.",
                             "A controlled, oxygen-supplied room and player equipment remain campaign preconditions, not built by these NBT-free platform commands.",
                             "Client hazard HUD, actual player damage and protected controls have not been run."])


def site_commands():
    commands = []
    for site in hazard_plan()["sites"]:
        prefix = f"execute in {site['dimension']} run "
        x, y, z, xx, yy, zz = site["platform"]
        commands += [prefix + f"forceload add {x} {z}",
                     prefix + f"fill {x} {y} {z} {xx} {yy} {zz} minecraft:smooth_stone",
                     prefix + f"fill {x} {y+1} {z} {xx} {y+5} {zz} minecraft:air"]
        if site["roof"]:
            commands.append(prefix + f"fill {x} {y+4} {z} {xx} {y+4} {zz} minecraft:stone")
    return commands


def host_properties(original):
    # The inherited capture function contains save-all/stop (never called here).
    # It still has to parse with permission 4, as in K03's C6 qualification.
    return original.replace("difficulty=peaceful", "difficulty=normal") + "function-permission-level=4\n"


def authority(world):
    """Stopped, read-only native projection; never a save serializer."""
    state = chain.snapshot(world)
    projected = chain.projection(state)
    projected["celestial"] = state["roots"][NS + "_celestial.dat"]["data"]
    projected["bindings"] = json.loads((world / "data" / BINDINGS).read_text(encoding="utf-8"))
    return state, projected


def comparison(before, after, name):
    # Preserve every field except the one documented automatic recovery transition.
    before = json.loads(json.dumps(before))
    if name == "paid-pending":
        pending = [m for m in before["missions"] if m["status"] == "claim_pending_discovery"]
        chain.require(len(pending) == 1, "Exactly one paid receipt required")
        mission = pending[0]
        matching = [m for m in after["missions"] if m["mission_id"] == mission["mission_id"]]
        chain.require(len(matching) == 1 and matching[0]["status"] == "claimed", "Pending receipt did not recover")
        mission["status"] = "claimed"
        old_bodies, new_bodies = before["celestial"]["bodies"], after["celestial"]["bodies"]
        added = [b for b in new_bodies if b["id"] not in {b["id"] for b in old_bodies}]
        chain.require(len(added) == 1 and added[0]["id"] == NS + ":mars", "Unexpected discovery recovery")
        before["celestial"]["bodies"] = sorted(old_bodies + added, key=lambda b: b["id"])
    result = {k: before[k] == after[k] for k in before}
    chain.require(all(result.values()), "Restart authority differs: " + str(result))
    return result


class Console(chain.Console):
    def all(self, command):
        # Console commands may emit several lines. A later console echo bounds them.
        start = len(self.process.lines)
        self.query(command, r".+")
        self.query("say K07_READBACK_END", r"\[Server\] K07_READBACK_END")
        return [line.strip() for line in self.process.lines[start:]
                if "K07_READBACK_END" not in line and "[K07] " not in line]


def blocked_attempt(console):
    console.query('datapack enable "file/data_only_test_mars.zip"', r"Enabled data pack")
    first = await_catalog(console, "is valid with 12 bodies")
    console.query('datapack disable "file/data_only_test_mars.zip"', r"Disabled data pack")
    await_catalog(console, "is valid with 11 bodies")
    console.query('datapack enable "file/catalog_128.zip"', r"Enabled data pack")
    refused = await_catalog(console, "Planetary lifetime bindings exceed 128")
    console.query('datapack disable "file/catalog_128.zip"', r"Disabled data pack")
    restored = await_catalog(console, "is valid with 11 bodies")
    return dict(adopt=first, refused=refused, restored=restored,
                meaning="Catalog binding refused by lifetime reservation limit; not a corrupt/blocked SavedData store. Refused catalog is not retained across restart.")


def await_catalog(console, marker):
    deadline = time.monotonic() + 60
    while True:
        lines = console.all("arce celestial validate")
        if any(marker in line for line in lines):
            return lines
        chain.require(time.monotonic() < deadline, "Catalog did not report " + marker)
        time.sleep(.1)


def check_creation(name, made, state, projected):
    if name == "private-station":
        stations = [s for s in projected["stations"] if made["station"] in chain.saved_uuids(s["station_id"])]
        chain.require(len(stations) == 1, "Created station ID not saved")
        station = stations[0]
        chain.require(chain.saved_uuids(station["owner_id"]) == {made["owner"]}
                      and station["orbit_body"] == NS + ":mars"
                      and not station["members"] and not station["invitations"], "Private station rights differ")
    if name == "paid-pending":
        pending = [m for m in projected["missions"] if m["status"] == "claim_pending_discovery"]
        chain.require(len(pending) == 1 and chain.saved_uuids(pending[0]["owner_id"]) == {made["owner"]},
                      "Stopped world lacks one owned paid-pending receipt")
        accounts = [a for a in projected["research_accounts"] if made["owner"] in chain.saved_uuids(a["owner_id"])]
        chain.require(len(accounts) == 1 and all(accounts[0][key] == value for key, value in
                      (("balance", 20), ("lifetime_earned", 120), ("lifetime_spent", 100))), "Paid receipt research totals differ")


def audit_errors(name, lines):
    errors = [line.strip() for line in lines if re.search(r"/(ERROR|FATAL)\]", line)]
    expected = [line for line in errors if name == "blocked-binding" and "/ERROR]" in line
                and "Rejected planetary catalog; last valid generation remains active: Planetary lifetime bindings exceed 128" in line]
    chain.require(len(expected) == (1 if name == "blocked-binding" else 0)
                  and errors == expected, "Unexpected native ERROR/FATAL or missing binding refusal")
    return dict(all_errors=errors, expected_binding_refusals=expected)


def populate(console, name, owner):
    if name == "private-station":
        line = console.query(f"arce station admin create {owner} {NS}:mars K07 private Mars",
                             r"Created .* id=([a-f0-9-]{36})")
        station = re.search(r"id=([a-f0-9-]{36})", line)[1]
        return dict(station=station, owner=owner, creation=line,
                    inspect=console.all("arce station admin inspect " + station))
    if name == "paid-pending":
        console.query(WRITER + "hold-discovery true", r"Registry writer cut command done")
        line = console.query(WRITER + "discovery-launch", r"ARCE_RELEASE_SRW_DISCOVERY step=launch result=SUCCESS")
        # Let the ordinary scheduler complete the 200-tick mission.
        deadline = time.monotonic() + 60
        while True:
            status = console.all(WRITER + "status")
            if any("research=READY" in row for row in status):
                break
            chain.require(time.monotonic() < deadline, "Mission did not finish within native budget")
            time.sleep(1)
        claim = console.all(WRITER + "discovery-claim")
        chain.require(any("PENDING_DISCOVERY" in row for row in claim), "Claim did not retain a paid pending receipt")
        return dict(owner="00000000-0000-0000-0000-000000129b01", owner_policy="Fixed actor in C6's existing hook; cannot be parameterized without changing frozen code",
                    launch=line, claim=claim,
                    hook="registry-writer hold-discovery true (volatile replay hold only)")
    if name == "blocked-binding":
        return blocked_attempt(console)
    if name == "hazards":
        console.all("gamerule doDaylightCycle true")
        for command in site_commands():
            console.all(command)
        # No persistent tickets in a campaign source; subsequent readbacks explicitly load their small areas.
        for site in hazard_plan()["sites"]:
            x, _, z, *_ = site["platform"]
            console.all(f"execute in {site['dimension']} run forceload remove {x} {z}")
        return hazard_plan()
    return {}


def readbacks(console, name):
    commands = ["arce beta data-report", "arce celestial validate", "arce celestial list", "arce celestial systems",
                "arce celestial route earth_mars", "arce celestial route earth_venus",
                "arce satellite admin evidence", "arce station admin dump", "difficulty",
                "time query daytime", *["gamerule " + rule for rule in
                    ("doDaylightCycle", "doWeatherCycle", "naturalRegeneration", "fallDamage", "fireDamage",
                     "drowningDamage", "freezeDamage", "doMobSpawning")]]
    result = {command: console.all(command) for command in commands}
    chain.require(any("operational=true" in line for line in result[commands[0]]), "Managed roots unavailable")
    if name == "hazards":
        for site in hazard_plan()["sites"]:
            x, y, z, xx, yy, zz = site["platform"]
            prefix = f"execute in {site['dimension']} run "
            console.all(prefix + f"forceload add {x} {z}")
            witnesses = [prefix + f"execute if block {x+2} {y} {z+2} minecraft:smooth_stone run say K07_PLATFORM_{site['name']}",
                         prefix + f"execute if block {x+2} {y+4} {z+2} minecraft:{'stone' if site['roof'] else 'air'} run say K07_ROOF_{site['name']}"]
            for command in witnesses:
                result[command] = [console.query(command, r"\[Server\] K07_(PLATFORM|ROOF)_")]
            console.all(prefix + f"forceload remove {x} {z}")
    return result


def run(args):
    from scripts import run_dedicated_server_smoke as server
    from scripts import v170_world_fixture as fixture
    owner, other = owners(args.owner, args.other)
    chain.require(args.lock_owner.startswith("W438 pid=") and (chain.LOCK / "owner.txt").read_text(
        encoding="utf-8-sig").startswith(args.lock_owner), "W438 heavy-JVM lock required")
    chain.require(min(shutil.disk_usage(p).free for p in ("C:/", "D:/")) >= 10 * 1024**3, "10 GiB free required")
    chain.require(chain.digest(args.jar) == common.JAR_HASH, "Pinned C6 JAR required")
    folder = args.output / args.variant
    source = source_for(args.variant, args.fixtures) if args.mode == "build" else folder / "source-world"
    chain.require(not server.is_link_or_junction(source), "Linked source root")
    chain.verify_seal(source.parent)
    before_files = fixture.file_inventory(source)
    evidence = folder / ("creation" if args.mode == "build" else "qualification")
    chain.require(not (folder / "SHA256SUMS.txt").exists() or args.mode == "qualify", "Source is sealed")
    evidence.mkdir(parents=True, exist_ok=False)
    runtime = args.output / "_work" / (args.variant + "-" + args.mode)
    chain.require(not runtime.exists(), "Existing runtime is not owned by this invocation")
    receipt = dict(status="FAIL", commands=[], hooks=[], variant=args.variant, mode=args.mode,
                   driver_commit=subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
                   driver_sha256=chain.digest(Path(__file__)))
    process = None
    try:
        fixture.prepare(args.template, runtime, args.jar, source)
        # prepare's peaceful fixture override is unsuitable for C20. Use vanilla default difficulty.
        properties = runtime / "server.properties"
        properties.write_text(host_properties(properties.read_text(encoding="ascii")), encoding="ascii")
        argfile = "win_args.txt" if sys.platform == "win32" else "unix_args.txt"
        command = [str(args.java), "-Xms512M", "-Xmx2G"]
        if args.mode == "build" and args.variant == "paid-pending":
            command.append("-Dadvancedrocketrycommunity.releaseTestHooks=true")
            receipt["hooks"] = ["satellite release-test registry-writer " + action for action in
                                ("discovery-launch", "status", "discovery-claim", "hold-discovery true")]
        command += [f"@libraries/net/minecraftforge/forge/1.20.1-47.4.10/{argfile}", "nogui"]
        receipt.update(command=command, properties=properties.read_text())
        process = server.CapturedProcess(command, runtime, evidence / "console.log")
        process.wait_for(server.READY_MARKER, 240)
        port = int(server.parse_java_properties(properties.read_bytes())["server-port"])
        receipt["server_status"] = chain.ready_status(server, port, "1.20.1-1.8.0-dev")
        chain.require(receipt["server_status"]["players"]["online"] == 0, "Unexpected player")
        console = Console(process, receipt["commands"])
        receipt["first_load"] = readbacks(console, args.variant if args.variant != "hazards" else "discovered")
        if args.variant == "blocked-binding" and args.mode == "build":
            packs = args.fixtures / "K06"
            chain.verify_seal(packs)
            for pack in ("data_only_test_mars.zip", "catalog_128.zip"):
                destination = runtime / "world/datapacks" / pack
                destination.parent.mkdir(exist_ok=True)
                shutil.copy2(packs / pack, destination)
            console.all("datapack list available")
        if args.mode == "build":
            receipt["made"] = populate(console, args.variant, owner)
        elif args.variant == "blocked-binding":
            receipt["repeat_refusal"] = blocked_attempt(console)
        receipt["readbacks"] = readbacks(console, args.variant)
        console.query("save-all flush", server.SAVE_MARKER.pattern)
        receipt["commands"].append("stop")
        process.command("stop")
        receipt["exit_code"] = process.finish()
        chain.require(receipt["exit_code"] == 0, "Server exit was not clean")
        receipt["error_audit"] = audit_errors(args.variant, process.lines)
        config = runtime / "config" / (NS + "-common.toml")
        chain.require(config.is_file(), "Missing generated COMMON configuration")
        shutil.copy2(config, evidence / "host-common.toml")
        receipt["common_config_sha256"] = chain.digest(config)
        state, projected = authority(runtime / "world")
        chain.write(evidence / "state.json", state)
        chain.write(evidence / "authority.json", projected)
        chain.write(evidence / "files.json", fixture.file_inventory(runtime / "world"))
        chain.require(before_files == fixture.file_inventory(source), "Parent source changed")
        chain.verify_seal(source.parent)
        receipt["source_unchanged"] = True
        if args.mode == "qualify":
            saved = json.loads((folder / "creation/authority.json").read_text())
            receipt["authority_matches"] = comparison(saved, projected, args.variant)
            receipt["recovery"] = "PAID_PENDING_TO_CLAIMED_NO_SECOND_PAYMENT" if args.variant == "paid-pending" else "UNCHANGED"
        else:
            discovered = {b["id"] for b in projected["celestial"]["bodies"]}
            if args.variant in ("undiscovered", "pre-sky", "paid-pending"):
                chain.require(NS + ":mars" not in discovered and NS + ":venus" not in discovered, "FI04 was not undiscovered")
            else:
                chain.require({NS + ":mars", NS + ":venus"} <= discovered, "FI05 discoveries missing")
            check_creation(args.variant, receipt["made"], state, projected)
            shutil.copytree(runtime / "world", folder / "source-world")
            chain.write(folder / "manifest.json", dict(schema_version=1, variant=args.variant,
                candidate_commit=common.CANDIDATE, jar_sha256=common.JAR_HASH, parent_source=str(source),
                parent_seal_sha256=chain.digest(source.parent / "SHA256SUMS.txt"),
                owner=owner, other=other, inherited_research_owner=FI05_OWNER, accounts="Disposable placeholders, O5 unresolved",
                classification="C6 copy of a reconstructed historical source, not historically played",
                commands=receipt["commands"], readbacks=receipt["readbacks"], made=receipt["made"],
                effective_values=values(args.jar), hazard_plan=hazard_plan() if args.variant == "hazards" else None,
                sky_observation="NOT_COVERED: sky profiles are client-only resources; hashes identify C6 bytes, not a rendered first-load sky",
                source_inventory_sha256=chain.digest(evidence / "files.json"), hooks=receipt["hooks"],
                client_started=False, required_gates_approved=False))
        receipt["status"] = "PASS"
    except BaseException as exc:
        receipt["error"] = f"{type(exc).__name__}: {exc}"
        raise
    finally:
        if process is not None:
            process.abort()
            chain.require(process.process.poll() is not None, "Owned JVM alive; retain lock and runtime")
        if (runtime / "logs/debug.log").exists():
            shutil.copy2(runtime / "logs/debug.log", evidence / "debug.log")
        chain.write(evidence / "receipt.json", receipt)
        chain.require(runtime.resolve().is_relative_to((args.output / "_work").resolve()), "Unsafe cleanup")
        if runtime.exists():
            shutil.rmtree(runtime)
    chain.seal(folder if args.mode == "build" else evidence)
    return receipt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--variant", choices=VARIANTS, required=True)
    parser.add_argument("--mode", choices=("build", "qualify"), required=True)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--fixtures", type=Path, default=FIXTURES)
    parser.add_argument("--jar", type=Path, default=common.JAR)
    parser.add_argument("--template", type=Path, required=True)
    parser.add_argument("--java", type=Path, required=True)
    parser.add_argument("--lock-owner", required=True)
    parser.add_argument("--owner", default=OWNER)
    parser.add_argument("--other", default=OTHER)
    print(json.dumps(run(parser.parse_args()), indent=2))


if __name__ == "__main__":
    main()
