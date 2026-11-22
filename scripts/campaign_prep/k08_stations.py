"""Copy-only K08 station preparation on C6; caller owns one heavy-JVM hold.

Minecraft alone writes saves. Player-only warp consent is a campaign step, not
a persisted pending fixture. No client, save editor or synthetic actor is used.
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

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.campaign_prep import k03_chain as chain
from scripts.campaign_prep import k07_planets as planets
from scripts.campaign_prep import k16_core as common
from scripts.campaign_observe.common import safe_path, utc_now

NS, SPACE = common.NS, common.SPACE
OUTPUT = common.EVIDENCE / "final-campaign/c8fa16e16/fixtures/K08"
VARIANTS = ("two", "ten", "hundred", "charged", "target-removal", "cost-change")
OWNER = "04400000-0000-4000-8000-000000000001"
OTHER = "04400000-0000-4000-8000-000000000002"
MEMBER = "04400000-0000-4000-8000-000000000003"
OUTSIDER = "04400000-0000-4000-8000-000000000004"
PACKS = ("station_baseline.zip", "station_target_removal.zip", "station_cost_change.zip")


def uuid_text(value):
    if isinstance(value, str):
        return str(UUID(value))
    chain.require(len(value) == 4, "UUID must have four words")
    return str(UUID(int=sum((v & 0xffffffff) << ((3-i)*32) for i, v in enumerate(value))))


def station_rows(state):
    root = state["roots"][NS + "_stations.dat"]["data"]
    energy = {uuid_text(row["station_id"]): row["energy"] for row in root["warp_energy"]}
    cores = [row["value"] for row in state["block_entities"]
             if row["value"]["id"] == NS + ":warp_core"
             and row["region"].startswith(f"dimensions/{NS}/space/region/")]
    result = []
    for row in root["stations"]:
        bounds = row["region"]
        chain.require(len(bounds) == 4, "Invalid saved region")
        station = uuid_text(row["station_id"])
        result.append(dict(id=station, owner=uuid_text(row["owner_id"]), name=row["name"],
            orbit=row["orbit_body"], region=bounds, pad=row["landing_pad"],
            members=[uuid_text(v["id"]) for v in row["members"]],
            invitations=[uuid_text(v["id"]) for v in row["invitations"]], energy=energy.get(station, 0),
            cores=[[c["x"], c["y"], c["z"]] for c in cores
                   if bounds[0] <= c["x"] <= bounds[2] and bounds[1] <= c["z"] <= bounds[3]]))
    return sorted(result, key=lambda row: row["id"])


def creation_plan(variant, inherited, owner, other, member, outsider):
    actors = [uuid_text(v) for v in (owner, other, member, outsider)]
    chain.require(actors[0] != actors[1], "Two distinct station owners required")
    chain.require(actors[0] not in actors[2:], "Member/non-member must not be station A's owner")
    occupied = {row["owner"] for row in inherited}
    chain.require(not occupied.intersection(actors[:2]), "New owner already owns an inherited station")
    total = {"ten": 10, "hundred": 100}.get(variant)
    count = total - len(inherited) if total else 2
    chain.require(0 < count <= 100, "Cannot reach requested count from parent")
    owners = actors[:2] if total is None else [str(UUID(int=UUID(owner).int + 256 + i)) for i in range(count)]
    chain.require(not occupied.intersection(owners) and len(set(owners)) == count, "Owner limit conflict")
    return [dict(owner=actor, orbit=NS + (":moon" if total is None and i == 1 else ":earth"),
                 name=("K08 " + "station" * 6 + "xx") if i == 0 else f"K08 {variant} {i:03}")
            for i, actor in enumerate(owners)]


def client_steps(rows, actors, variant, member_name=None):
    owner, other, member, outsider = actors
    if member_name is not None:
        chain.require(re.fullmatch(r"[A-Za-z0-9_]{1,16}", member_name) is not None, "Invalid online player name")
    invitee = member_name or f"<online-name-for-{member}>"
    selected = [next(r for r in rows if r["owner"] == actor) for actor in (owner, other)]
    first, second = selected
    station = first["id"]
    target = "k06:warp_target" if variant in ("target-removal", "cost-change") else NS + ":moon"
    steps = [dict(actor=owner, command=f"/arce station invite {station} {invitee}", precondition=f"Owner and invitee online; verify player name maps to UUID {member}. EntityArgument.player requires the online name, not a bare UUID. Invitation is not membership"),
        dict(actor=member, command=f"/arce station decline {station}", precondition="Invitation exists; reinvite for the accept case"),
        dict(actor=member, command=f"/arce station accept {station}", precondition="A new invitation exists"),
        dict(actor=member, command=f"/arce station warp {target}", precondition="Accepted member, not owner/operator, local at station A's core; membership alone does not grant warp authority"),
        dict(actor=owner, command=f"/arce station remove {station} uuid {member}", precondition="Member accepted; UUID removal also works offline"),
        dict(actor=outsider, command=f"/arce station warp {target}", precondition="Non-operator, not owner/member, at this station, aiming at its core within 5 blocks"),
        dict(actor=outsider, command=f"/arce station warp confirm {station}", precondition="Same local non-operator; record refusal and unchanged authority"),
        dict(actor=outsider, command=f"/arce station admin delete {station} confirm", precondition="Non-operator; permission-2 admin node must be unavailable"),
        dict(actor=owner, command=f"/arce station warp {target}", precondition="Own local command, Space region, aim at core within 5 blocks; enabled, known offered target, no rocket motion, enough folded FE"),
        dict(actor=owner, command=f"/arce station warp confirm {station}", precondition="Same actor/station; confirm within 200 ticks; memory-only 200-tick countdown starts"),
        dict(actor=owner, command="/arce station warp cancel", precondition="Separate run: cancel 3 seconds into countdown; capture GUI scales 2 and 3"),
        dict(actor=other, command=f"/arce station warp {NS}:earth", precondition=f"Independent run at Moon station {second['id']}, its core and sufficient folded energy"),
        dict(actor=other, command=f"/arce station warp confirm {second['id']}", precondition="Confirm its own quote within 200 ticks; capture independent due/commit ticks"),
        dict(actor=other, command=f"/arce station warp {NS}:tau_ceti_e", precondition="Separate interstellar run at station B; target legitimately known, enough folded FE for effective interstellar cost; do not substitute shared Space as target"),
        dict(actor=other, command=f"/arce station warp confirm {second['id']}", precondition="Confirm that interstellar quote within 200 ticks; capture server-selected class, charge and unchanged physical positions")]
    overlay = None
    if variant in ("target-removal", "cost-change"):
        pack = "station_" + variant.replace("-", "_") + ".zip"
        overlay = dict(station=station, target=target, command=f'datapack enable "file/{pack}" after "file/station_baseline.zip"',
            preconditions="Baseline active. Player requests/confirms first; record due tick. Activate before due commit; await catalog adoption, not just Enabling text. Read admin warp effective costs and folded balance >= max(costs).",
            expected="WARP_TARGET_UNAVAILABLE" if variant == "target-removal" else "WARP_QUOTE_CHANGED only if effective costs differ and other guards do not mask it",
            actual="NOT_COVERED: no local player consent, therefore no pending warp or due-commit observation",
            recovery=f'datapack disable "file/{pack}"; wait for catalog adoption; two ordinary restarts; verify orbit/energy and no revived countdown')
    return dict(classification="SCRIPTED_CLIENT_STEPS_NOT_EXECUTED", stations=selected, actions=steps, overlay=overlay,
        role_binding="The second owner may also be station A's invitee/member, then its non-member after removal; two real accounts suffice. Roles are sequential states, not four required simultaneous accounts.",
        approach=[dict(station=row["id"], feet=[row["pad"][0]+4.5,row["pad"][1],row["pad"][2]+2.5],
                       look_at=[row["pad"][0]+4.5,row["pad"][1]+.5,row["pad"][2]+4.5])
                  for row in selected if "pad" in row],
        ordinary_creation="On a fresh FI06 copy, each eligible O5 account on a catalog surface Level (not Space), owning zero stations, uses advancedrocketrycommunity:station_deployment_kit. Server resolves its surface body; 1 kit consumed outside Creative. Do not run this on an account already owning an operator-created K08 station.",
        pending="NOT_COVERED: request/confirm require getPlayerOrException and the player's own non-silent source; an /execute wrapper is not local consent. Quotes/countdowns are lifecycle-owned memory, never SavedData and never retained by restart.",
        docked_rocket="NOT_COVERED: no C6 station docking command/hook found; use ordinary flight/landing with matching stationary rocket authority, then F04's countdown stop and committed two-restart cases. Teleporting/summoning an entity is not docking.",
        clients="F02/F03/F06/F07 client views, reconnect/passengers, controller, sky/cache and V1/V2 remain campaign observations")


class Console(planets.Console):
    def all(self, command):
        # A false /execute predicate legitimately has no output. Bound every
        # reply with a later queued echo, not a wait for the command itself.
        start = len(self.process.lines)
        self.commands.append(command)
        self.process.command(command)
        self.query("say K08_READBACK_END", r"\[Server\] K08_READBACK_END")
        lines = [s.strip() for s in self.process.lines[start:] if "K08_READBACK_END" not in s]
        failures = ("Station creation failed:", "Station not found", "Unknown or incomplete command",
                    "That position is out of this world!", "That position is not loaded")
        chain.require(not any(message in s for s in lines for message in failures), command + " " + str(lines))
        return lines


def readbacks(console, rows):
    commands = ["arce beta data-report", "arce celestial validate", "arce station admin dump", "arce station admin warp",
                "datapack list enabled", f"execute in {SPACE} run forceload query"]
    result = {command: console.all(command) for command in commands}
    chain.require(any("operational=true" in s for s in result[commands[0]]), "Roots unavailable")
    for row in rows:
        for suffix in ("inspect", "warp"):
            command = f"arce station admin {suffix} {row['id']}"
            result[command] = console.all(command)
    return result


def await_loaded(console, x, y, z):
    deadline = time.monotonic() + 60
    while True:
        lines = console.all(f"execute in {SPACE} if loaded {x} {y} {z} run say K08_LOADED")
        if any("[Server] K08_LOADED" in s for s in lines):
            return
        chain.require(time.monotonic() < deadline, "Station chunk did not load within 60 seconds")
        time.sleep(.1)


def populate(console, variant, plan):
    made = []
    for entry in plan:
        line = console.query(f"arce station admin create {entry['owner']} {entry['orbit']} {entry['name']}",
                             r"Created .* id=[a-f0-9-]{36}")
        station = re.search(r"id=([a-f0-9-]{36})", line)[1]
        details = console.all("arce station admin inspect " + station)
        match = re.search(r"pad=(-?\d+),(-?\d+),(-?\d+)", " ".join(details))
        chain.require(match is not None, "Missing pad")
        made.append(dict(entry, id=station, pad=list(map(int, match.groups())), creation=line, inspect=details))
    if variant not in ("ten", "hundred"):
        for row in made:
            x, y, z = row["pad"]
            console.all(f"execute in {SPACE} run forceload add {x} {z}")
            await_loaded(console, x, y, z)
            for dx, block in ((4, "warp_core"), (2, "warp_controller")):
                console.all(f"execute in {SPACE} run setblock {x+dx} {y} {z+4} {NS}:{block}")
            if variant != "two":
                # Ordinary FE output. No NBT energy field or synthetic credit.
                console.all(f"execute in {SPACE} run setblock {x+5} {y} {z+4} {NS}:solar_generator")
        if variant != "two":
            time.sleep(12)
        for row in made:
            x, y, z = row["pad"]
            if variant != "two":
                console.all(f"execute in {SPACE} run setblock {x+5} {y} {z+4} minecraft:air")
            console.all(f"execute in {SPACE} run forceload remove {x} {z}")
    return made


def check_created(variant, made, rows, inherited):
    by_id = {row["id"]: row for row in rows}
    chain.require(len(rows) == {"ten":10, "hundred":100}.get(variant, len(inherited)+2), "Wrong station total")
    chain.require(len({row["owner"] for row in rows}) == len(rows), "Per-owner limit violated")
    for item in made:
        row = by_id[item["id"]]
        chain.require(row["owner"] == item["owner"] and row["orbit"] == item["orbit"] and row["name"] == item["name"]
                      and not row["members"] and not row["invitations"], "Created station authority differs")
        if variant not in ("ten", "hundred"):
            x, y, z = item["pad"]
            chain.require([x+4,y,z+4] in row["cores"], "Core missing from native save")
            chain.require(row["energy"] > 0 if variant != "two" else row["energy"] == 0, "Unexpected folded energy")
    for row in inherited:
        chain.require(by_id[row["id"]] == row, "Inherited station changed")


def check_lock(token):
    chain.require(token.startswith("W440 pid=") and (chain.LOCK / "owner.txt").read_text(
        encoding="utf-8-sig").startswith(token), "W440 heavy-JVM lock required")
    if sys.platform == "win32":
        chain.require(min(shutil.disk_usage(p).free for p in ("C:/", "D:/")) >= 10*1024**3, "10 GiB free required")


def forced_projection(state):
    # Vanilla serializes a set as a long array; insertion/rehash order is not
    # chunk authority. Retain every other field and the exact distinct tickets.
    value = json.loads(json.dumps(state["forced_chunks"]))
    for root in value.values():
        tickets = root["data"]["Forced"]
        chain.require(len(tickets) == len(set(tickets)), "Duplicate native force ticket")
        root["data"]["Forced"] = sorted(tickets)
    return value


def run(args, load_action=None):
    from scripts import run_dedicated_server_smoke as server
    from scripts import v170_world_fixture as fixture
    check_lock(args.lock_owner)
    for name in ("output", "fixtures", "jar", "template", "java"):
        setattr(args, name, safe_path(getattr(args, name)))
    chain.require(chain.digest(args.jar) == common.JAR_HASH, "Pinned C6 JAR required")
    folder = args.output / args.variant
    source = args.fixtures / "K03/FI06/source-world" if args.mode == "build" else folder / "source-world"
    chain.verify_seal(source.parent)
    before = fixture.file_inventory(source)
    parent_state = chain.snapshot(source)
    inherited = station_rows(parent_state)
    actors = [uuid_text(v) for v in (args.owner, args.other, args.member, args.outsider)]
    plan = creation_plan(args.variant, inherited, *actors) if args.mode == "build" else []
    evidence = folder / ("creation" if args.mode == "build" else "qualification" if args.mode == "qualify" else args.run_label)
    chain.require(args.mode != "build" or not (folder / "source-world").exists(), "Source already exists")
    evidence.mkdir(parents=True, exist_ok=False)
    runtime = args.output / "_work" / (args.variant + "-" + evidence.name)
    chain.require(not runtime.exists(), "Existing runtime is not owned")
    receipt = dict(status="FAIL", commands=[], variant=args.variant, mode=args.mode,
        start_utc=utc_now(), template=str(args.template), forge="47.4.10", jei="absent",
        candidate_commit=common.CANDIDATE, jar_sha256=common.JAR_HASH, hooks=[],
        driver_commit=subprocess.check_output(["git","rev-parse","HEAD"], cwd=common.ROOT, text=True).strip(),
        driver_sha256=chain.digest(Path(__file__)), parent_source=str(source),
        parent_seal_sha256=chain.digest(source.parent / "SHA256SUMS.txt"))
    process = None
    try:
        fixture.prepare(args.template, runtime, args.jar, source)
        properties = runtime / "server.properties"
        properties.write_text(planets.host_properties(properties.read_text(encoding="ascii")), encoding="ascii")
        if args.mode == "build" and args.variant in ("target-removal", "cost-change"):
            packs = args.fixtures / "K06"
            chain.verify_seal(packs)
            receipt["packs"] = {name:chain.digest(packs / name) for name in PACKS}
            receipt["pack_parent_seal_sha256"] = chain.digest(packs / "SHA256SUMS.txt")
            for name in PACKS:
                destination = runtime / "world/datapacks" / name
                chain.require(not destination.exists(), "Pack already present")
                shutil.copy2(packs / name, destination)
        argfile = "win_args.txt" if sys.platform == "win32" else "unix_args.txt"
        command = [str(args.java), "-Xms512M", "-Xmx2G"]
        if load_action:
            command.append("-Dadvancedrocketrycommunity.releaseTestHooks=true")
            receipt["hooks"] = ["arce satellite release-test perf ticks raw"]
        command += [f"@libraries/net/minecraftforge/forge/1.20.1-47.4.10/{argfile}", "nogui"]
        receipt.update(command=command, properties=properties.read_text())
        process = server.CapturedProcess(command, runtime, evidence / "console.log")
        process.wait_for(server.READY_MARKER, 240)
        port = int(server.parse_java_properties(properties.read_bytes())["server-port"])
        receipt["server_status"] = chain.ready_status(server, port, "1.20.1-1.8.0-dev")
        chain.require(receipt["server_status"]["players"]["online"] == 0, "Unexpected player")
        console = Console(process, receipt["commands"])
        receipt["first_load"] = readbacks(console, inherited)
        if args.mode == "build":
            if args.variant in ("target-removal", "cost-change"):
                # New packs are auto-enabled on discovery. Explicitly disable both overlays.
                for name in PACKS[1:]:
                    console.all(f'datapack disable "file/{name}"')
                console.all('datapack enable "file/station_baseline.zip"')
                receipt["catalog_baseline"] = planets.await_catalog(console, "is valid with 12 bodies")
            receipt["made"] = populate(console, args.variant, plan)
            # Console can really delete a station. Use an independently created
            # disposable witness, never remove inherited or delivered records.
            witness_owner = str(UUID(int=UUID(args.owner).int + 4096))
            chain.require(witness_owner not in {v["owner"] for v in inherited+plan}, "Witness owner conflict")
            line = console.query(f"arce station admin create {witness_owner} {NS}:earth K08 delete witness",
                                 r"Created .* id=[a-f0-9-]{36}")
            witness = re.search(r"id=([a-f0-9-]{36})", line)[1]
            receipt["delete_witness"] = dict(id=witness, owner=witness_owner, created=line,
                inspect=console.all("arce station admin inspect " + witness),
                deleted=console.all(f"arce station admin delete {witness} confirm"))
        elif load_action:
            receipt["load"] = load_action(console, inherited, evidence, args)
        rows = receipt.get("made", inherited)
        receipt["readbacks"] = readbacks(console, rows)
        console.query("save-all flush", server.SAVE_MARKER.pattern)
        receipt["commands"].append("stop")
        process.command("stop")
        receipt["exit_code"] = process.finish()
        receipt["end_utc"] = utc_now()
        chain.require(receipt["exit_code"] == 0, "Unclean native exit")
        receipt["errors"] = [s.strip() for s in process.lines if re.search(r"/(ERROR|FATAL)\]", s)]
        chain.require(not receipt["errors"], "Native ERROR/FATAL")
        state, authority = planets.authority(runtime / "world")
        rows = station_rows(state)
        chain.write(evidence / "state.json", state)
        chain.write(evidence / "authority.json", authority)
        chain.write(evidence / "stations.json", rows)
        chain.write(evidence / "files.json", fixture.file_inventory(runtime / "world"))
        chain.require(before == fixture.file_inventory(source), "Source input changed")
        chain.verify_seal(source.parent)
        receipt["source_unchanged"] = True
        if args.mode == "build":
            check_created(args.variant, receipt["made"], rows, inherited)
            shutil.copytree(runtime / "world", folder / "source-world")
            receipt["status"] = "PASS"
            chain.write(folder / "manifest.json", dict(schema_version=1, **receipt,
                stations=rows, actors=actors, owners="Disposable placeholders pending O5; distinct operator-created owners, not ordinary purchases",
                client_steps=client_steps(rows, actors, args.variant, args.member_name) if args.variant not in ("ten", "hundred") else None,
                serves=["B-2","F-1","F-2","F-3","F-4","F01","F02","F03","F04","F05","F06","F07"],
                classification="C6 copy of reconstructed FI06, not historically played; pending and docked-rocket states NOT_COVERED",
                source_inventory_sha256=chain.digest(evidence / "files.json"),
                preparation_status="BUILT", campaign_acceptance=False, required_gates_approved=False))
        else:
            expected = json.loads((folder / "creation/authority.json").read_text())
            receipt["authority_matches"] = planets.comparison(expected, authority, "station")
            chain.require(rows == json.loads((folder / "creation/stations.json").read_text()), "Station readback changed")
            chain.require(forced_projection(state) == forced_projection(parent_state), "Force tickets not restored")
        receipt["status"] = "PASS"
    except BaseException as exc:
        receipt["error"] = f"{type(exc).__name__}: {exc}"
        raise
    finally:
        if process:
            process.abort()
            chain.require(process.process.poll() is not None, "Owned JVM alive; keep lock/runtime")
        if (runtime / "logs/debug.log").exists():
            shutil.copy2(runtime / "logs/debug.log", evidence / "debug.log")
        chain.write(evidence / "receipt.json", receipt)
        chain.require(runtime.resolve().is_relative_to((args.output / "_work").resolve()), "Unsafe cleanup")
        if runtime.exists():
            shutil.rmtree(runtime)
    chain.seal(folder if args.mode == "build" else evidence)
    return receipt


def parser():
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--variant", choices=VARIANTS, required=True)
    result.add_argument("--mode", choices=("build", "qualify"), required=True)
    result.add_argument("--output", type=Path, default=OUTPUT)
    result.add_argument("--fixtures", type=Path, default=OUTPUT.parent)
    result.add_argument("--jar", type=Path, default=common.JAR)
    result.add_argument("--template", type=Path, required=True)
    result.add_argument("--java", type=Path, required=True)
    result.add_argument("--lock-owner", required=True)
    result.add_argument("--member-name", help="Online name matching --member for the scripted invite; unresolved until O5")
    for name, default in (("owner", OWNER), ("other", OTHER), ("member", MEMBER), ("outsider", OUTSIDER)):
        result.add_argument("--" + name, default=default)
    return result


if __name__ == "__main__":
    print(json.dumps(run(parser().parse_args()), indent=2))
