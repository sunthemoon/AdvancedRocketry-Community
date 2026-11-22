"""Copy-only K10 endgame fixtures on C6; one externally locked JVM per call.

Only registered native commands change saves. JDWP is loopback-only and used
solely to halt an owned JVM at an existing physical-batch line, without edits.
"""
from __future__ import annotations

import argparse
import hashlib
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
from scripts.campaign_prep import k07_planets as planets
from scripts.campaign_prep import k10_endgame_load as load
from scripts.campaign_prep import k16_core as common

OUTPUT = common.EVIDENCE / "final-campaign/c8fa16e16/fixtures/K10"
PARENT = OUTPUT.parent / "K03/FI08/source-world"
OW, SPACE = "minecraft:overworld", common.NS + ":space"
X, Z = 12296, 12296  # interior of chunk; no inherited region at these sites
VARIANTS = ("idle", "active", "large-range", "batch-cut")
PHYSICAL_CLASS = "io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserPhysicalDrill"
BREAK_LINE = 218  # after one removeBlock, before collected.addAll, inside the nine-cell loop


def named_uuid(name):
    return str(UUID(bytes=hashlib.md5(name.encode("utf-8")).digest(), version=3))


def owner(index):
    return named_uuid(f"arce-refload-owner-{index}")


def actors():
    result = []
    for i in range(44):
        role = ("logical drill menu owner" if i < 4 else "physical drill menu owner" if i < 8 else
                "black-hole device owner" if i < 12 else "gravity menu owner" if i < 20 else
                "railgun request owner" if i < 36 else "elevator/station owner")
        result.append(dict(uuid=owner(i), index=i, role=role,
            classification="embedded-channel Creative fixture player" if i < 8 or 12 <= i < 20 else "UUID authority only; no connection"))
    for i in range(28):
        result.append(dict(uuid=named_uuid(f"arce-refload-visitor-{i}"), index=i,
            role="field visitor" if i < 20 else "station-member rider",
            classification="embedded-channel Creative fixture player; not real client"))
    for i in range(32):
        result.append(dict(uuid=named_uuid(f"arce-refload-synthetic-{i}"), index=i,
            role="synthetic ledger owner", classification="UUID only; not player"))
    return dict(actors=result, physical_break_actor="Forge FakePlayer GameProfile(owner UUID, [ARCE laser]); not a client or field visitor",
        real_connections=dict(count=0, required=2, prerequisite="O7; real authenticated A/B, distinct from all facility UUIDs"),
        owner_binding="O5 unresolved. IDs are fixed by C6 hooks, not --owner parameters. Map real accounts to roles and create/reassign disposable scenes with normal OP commands; no player-file/NBT rewrite.")


def overworld_devices():
    result = []
    for i in range(4):
        result.append(dict(kind="laser_target", pos=[X+48*i, 300, Z-96], owner=owner(4+i)))
    for i in range(64):
        result.append(dict(kind="gravity_field_controller", pos=[(X//16)*16+8+32*(i%16), 200,
            ((Z+512)//16)*16+8+32*(i//16)], owner=owner(12+i//8)))
    for i in range(16):
        for dz in (160, 184):
            result.append(dict(kind="railgun", pos=[X+16*i, 200, Z+dz], owner=owner(20+i)))
    for i in range(8):
        result.append(dict(kind="elevator_anchor", pos=[X+32*i, 200, Z+256], owner=owner(36+i)))
    return result


def ground_check(world):
    """No inherited chunk/structure can exist in an absent inherited region."""
    regions = set()
    for device in overworld_devices():
        x, _, z = device["pos"]
        for dx in (-2, 2):
            for dz in (-2, 3):
                regions.add(f"r.{(x+dx)//512}.{(z+dz)//512}.mca")
    chain.require(all(not (world / "region" / name).exists() for name in regions),
                  "Chosen ground intersects inherited region; do not overwrite")
    return dict(x=X, z=Z, absent_inherited_regions=sorted(regions),
        check="All structure/shaft footprint regions absent in sealed FI08. Native preloads witnessed before build. New space stations are allocated by StationRegistry reserve/commit outside inherited claims.",
        claim_scope="FI08 has native station claims, no third-party claim provider; H04 NOT_COVERED D9/O6",
        shaft_bounds=dict(top=299, bottom=44, width=3, max_depth=256))


def historical_cuts(archive):
    with zipfile.ZipFile(archive) as z:
        cuts = sorted(set(re.findall(r"(?:^|/)cut-([^/]+)/", "\n".join(z.namelist()), re.M)))
    result = {}
    for cut in cuts:
        ride = cut.startswith("ride-")
        result[cut] = dict(status="NOT_COVERED", c6_seam=(
            "ElevatorRides countdown/commit; elevator release-test ride/state and endgame halt" if ride else
            "TransitLedger and cargo endpoints; release-test railguns/fill/launch/endpoint/ledger/persist chunks|root|hold/halt"),
            reason="Existing subsystem seams, but not a physical block-processing batch; no one-to-one match to LaserPhysicalDrill.remove. Not rerun or credited by K10.")
    result["physical-layer"] = dict(c6_seam=f"{PHYSICAL_CLASS}:{BREAK_LINE}",
        method="JDWP line breakpoint after first cell removeBlock, before marker.layerDone; owned-process hard stop",
        historical="Recovery matrix has A0 seven-event vectors and A1 debt/credit test, no native physical cut in the historical archive",
        qualification="See batch-cut receipts; no equivalence claimed for transit/ride cuts")
    return result


def client_steps():
    return dict(status="SCRIPTED_ONLY; no clients started", preconditions=[
        "O5 binds A/B account UUIDs to the fixture role table. Default fixed hook UUIDs are not authenticated accounts. On a disposable campaign copy, OP reassigns each device with /execute in <dimension> run arce endgame device owner <x y z> <online player>; record success and handle ENDPOINT_BUSY, do not bypass it.",
        "For private station scenes recreate an owned station through normal station controls; add B as station member but not device owner. Reference stations remain placeholder-owned until this is completed; membership is not device authority.",
        "online-mode=true, white-list=true, enforce-whitelist=true, RCON off, restricted DS; hook fixture connections never count as A/B. Supply normal FE/fuel/items before positive controls; native reference topups are synthetic.",
        "Use manifest device positions/IDs/targets, add only manifest-required tickets on a copy. Active generator ring is at Cygnus X-1 station, not Earth. Physical drill needs physical enabled on disposable copy and owner confirmation; builder override restores on stop."],
        menus=dict(orbital_laser_drill="Mode, target Previous/Next, Link, Start then danger Confirm for PHYSICAL; Stop; inspect target marker's LaserTargetMenu",
            railgun="Previous/Next endpoint, minimum stack, Launch; inspect both input/outbox and receive buffer",
            black_hole_generator="Fuel slot, buffer/status, redstone control; generation is fuel/context driven, no target selector",
            gravity_field_controller="Start/Stop, radius Down/Up (2..16), multiplier Down/Up (10..200 percent), redstone; trusted-player commands are owner-only",
            elevator="Terminal endpoint Previous/Next and Bind, Ride, Unbind; use normal station invite/accept for member control"),
        H01="For each supported target-sharing system prepare two separately owner-bound devices and a common valid target. A selects/confirms, B selects/confirms; retain both menus, actual ordering, audit and before/after FE/cargo. Physical targets are owner-private unless OP-linked; don't invent an ordinary shared-target permission. Generators/fields have no endpoint selector: compare shared FE sink/overlap policy rather than promise a universal target contention code.",
        H02="B non-OP/non-owner opens all five screens and tries each applicable control; attempts owner OP command. Record receipt plus unchanged target/cargo/energy/station/chunks (R546), then A positive control. Repeat as station member without device ownership. Role scene preparation is a campaign prerequisite, not qualified by hook actors.",
        H03_large_range="Use the maximum-depth 256/3x3 shaft on large-range copy. C6 exposes no arbitrary laser range input: an oversized physical range request is NOT_COVERED. Coordinate grammar refusal is supplementary only. For gravity increase radius to 16, press Up once more; compare read-only radius and policy response, not a fabricated NBT radius.",
        H06="Open all five ordinary screens, successful and refused states; record en_us/zh_cn, scales 2/3/4, effective viewport, window/fullscreen, F3+T/reconnect and second real GPU. View generating Cygnus X-1 ring two blocks below and five above. Railgun/generator/drill/target longest reason at scale 3: fit and full hover. Run together with effects and capture frame samples; owner supplies verdict.",
        H04="NOT_COVERED in v1.8 D9/O6/C27; moves to v1.9. No adapter or real-mod lane built.")


class Console(planets.Console):
    def all(self, command):
        start = len(self.process.lines)
        self.commands.append(command)
        self.process.command(command)
        self.query("say K10_READBACK_END", r"\[Server\] K10_READBACK_END")
        lines = [line.strip() for line in self.process.lines[start:] if "K10_READBACK_END" not in line]
        chain.require(not any("Unknown or incomplete command" in line for line in lines), "Unknown command: " + command)
        return lines


def device_state(world):
    """Read-only native projection, keeping FULL device roots, not synthesized tags."""
    state = chain.snapshot(world)
    devices = []
    kinds = {"orbital_laser_drill", "laser_target", "railgun", "black_hole_generator",
             "gravity_field_controller", "elevator_anchor", "elevator_terminal"}
    for row in state["block_entities"]:
        value = row["value"]
        if value["id"].split(":")[-1] in kinds:
            devices.append(row)
    return dict(devices=devices, endgame=state["roots"][common.NS+"_endgame.dat"]["data"],
        stations=state["roots"][common.NS+"_stations.dat"]["data"]["stations"], forced_chunks=state["forced_chunks"])


def device_keys(state):
    return {(row["region"], row["value"]["x"], row["value"]["y"], row["value"]["z"]): row["value"]
            for row in state["devices"]}


def compare(before, after, exact=True):
    old, new = device_keys(before), device_keys(after)
    chain.require(old.keys() == new.keys(), "Device set changed on restart")
    changes = [str(key) for key in old if old[key] != new[key]]
    if exact:
        chain.require(not changes, "Unloaded device roots changed on restart: " + str(changes))
    chain.require(before["stations"] == after["stations"], "Station roles changed")
    chain.require(before["forced_chunks"] == after["forced_chunks"], "Persistent tickets changed")
    return dict(device_set_equal=True, full_device_roots_equal=not changes, changed_devices=changes,
                stations_equal=True, persistent_tickets_equal=True,
                root_scope="Transit root epochs/reconciliation may progress; retained in full, not asserted byte-identical")


def preload(console):
    coords = set()
    for d in overworld_devices():
        x, _, z = d["pos"]
        coords.add((x//16, z//16))
        if d["kind"] == "laser_target":
            coords.update((x//16+dx, z//16+dz) for dx in (-1,0,1) for dz in (-1,0,1))
    for x, z in sorted(coords):
        console.all(f"forceload add {x*16} {z*16}")
    deadline = time.monotonic()+60
    pending = set(coords)
    while pending:
        for x, z in list(pending):
            if any("[Server] K10_LOADED" in line for line in console.all(
                    f"execute if loaded {x*16} 200 {z*16} run say K10_LOADED")):
                pending.remove((x,z))
        chain.require(not pending or time.monotonic() < deadline, "Fixture chunks did not load")
    return sorted(coords)


def forced(console):
    from scripts.campaign_prep.k08_station_load import forced_reply
    return {dim: forced_reply(console.all(f"execute in {dim} run forceload query")) for dim in (OW, SPACE)}


def unload(console, baseline, receipt):
    current = forced(console)
    added = {dim: sorted(set(current[dim]) - set(baseline[dim])) for dim in current}
    # Only exact UUIDs created by refload, never selectors or other sessions.
    for row in actors()["actors"]:
        if row["classification"].startswith("embedded-channel"):
            console.all("kick " + row["uuid"] + " K10 fixture cleanup")
    for dim, coords in added.items():
        for x,z in coords:
            console.all(f"execute in {dim} run forceload remove {x*16} {z*16}")
    time.sleep(12)  # ordinary unload queue, followed by native witnesses
    remaining = []
    for dim, coords in added.items():
        for x,z in coords:
            if any("[Server] K10_STILL_LOADED" in line for line in console.all(
                f"execute in {dim} if loaded {x*16} 200 {z*16} run say K10_STILL_LOADED")):
                remaining.append([dim,x,z])
    receipt["cleanup"] = dict(added_tickets={dim: coords for dim,coords in added.items()},
        still_loaded=remaining, readbacks=load.readbacks(console))
    chain.require(not remaining, "Fixture chunks still loaded after cleanup")
    chain.require(forced(console) == baseline, "Ticket baseline not restored")


def hard_stop(captured):
    if sys.platform == "win32":
        subprocess.run(["taskkill", "/PID", str(captured.process.pid), "/T", "/F"], check=True,
                       capture_output=True, text=True, timeout=30)
    else:
        captured.process.kill()  # direct Java handle, no shell or descendants
    captured.process.wait(timeout=30)


def batch_cut(console, args, evidence, receipt, debug_port):
    from scripts import run_dedicated_server_smoke as server
    jdb = args.java.with_name("jdb.exe" if sys.platform == "win32" else "jdb")
    debug = server.CapturedProcess([str(jdb), "-attach", f"127.0.0.1:{debug_port}"], evidence, evidence/"debugger.log")
    try:
        debug.command(f"stop at {PHYSICAL_CLASS}:{BREAK_LINE}")
        debug.wait_for(re.compile(r"[Dd]eferred breakpoint|Set breakpoint"), 30)
        console.query("save-all flush", r"Saved the game")
        console.query("save-off", r"Automatic saving is now disabled")
        receipt["before_cut"] = load.readbacks(console)
        # Native save-all completed; saves are now off. Keep the stopped-store
        # baseline before any start/link/payment, without changing a save byte.
        # CapturedProcess deliberately does not expose cwd; the caller supplies
        # the runtime explicitly in the receipt instead of searching processes.
        baseline = device_state(Path(receipt["runtime"]) / "world")
        chain.write(evidence / "pre-cut-disk-state.json", baseline)
        console.query(load.HOOK+"drive on", "ARCE_RELEASE_TEST refload drive on")
        debug.wait_for(re.compile(r"Breakpoint hit:.*LaserPhysicalDrill.*line=218"), 120)
        debug.command("where")
        debug.wait_for(re.compile(r"LaserPhysicalDrill.evaluate"), 30)
        debug.command("locals")
        debug.wait_for(re.compile(r"collected ="), 30)
        receipt["cut"] = dict(seam=f"{PHYSICAL_CLASS}:{BREAK_LINE}", stopped_utc=load.utc_now(),
            semantics="First removed cell in a planned 3x3 layer; before drops appended and marker.layerDone; all threads suspended by jdb. No eval or set performed.",
            pid=console.process.process.pid, stop="taskkill /T /F owned direct Java PID" if sys.platform == "win32" else "Popen.kill owned direct Java PID")
        hard_stop(console.process)
        receipt["cut_disk_comparison"] = compare(baseline, device_state(Path(receipt["runtime"]) / "world"))
    finally:
        debug.abort()


def run(args):
    from scripts import run_dedicated_server_smoke as server
    from scripts import v170_world_fixture as fixture
    chain.require(args.lock_owner.startswith("W444 pid=") and (chain.LOCK/"owner.txt").read_text(
        encoding="utf-8-sig").startswith(args.lock_owner), "W444 heavy-JVM lock required")
    chain.require(min(shutil.disk_usage(p).free for p in (("C:/", "D:/") if sys.platform == "win32" else (args.output,))) >= 10*1024**3,
                  "10 GiB free required")
    chain.require(chain.digest(args.jar) == common.JAR_HASH, "Pinned C6 JAR required")
    load.windows(args.warmup, args.duration, args.qualification)
    folder = args.output / args.variant
    source = PARENT if args.mode == "build" else folder / "source-world"
    if args.mode == "recover-2":
        source = folder / "recover-1/source-world"
    chain.verify_seal(source.parent)
    inventory = fixture.file_inventory(source)
    evidence = folder / args.mode
    evidence.mkdir(parents=True, exist_ok=False)
    runtime = args.output / "_work" / (args.variant+"-"+args.mode)
    chain.require(not runtime.exists(), "Runtime already exists")
    receipt = dict(status="FAIL", mode=args.mode, variant=args.variant, commands=[],
        runtime=str(runtime),
        source=str(source), source_seal_sha256=chain.digest(source.parent/"SHA256SUMS.txt"),
        candidate_commit=common.CANDIDATE, jar_sha256=chain.digest(args.jar),
        driver_commit=subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=common.ROOT, text=True).strip(),
        driver_sha256=chain.digest(Path(__file__)), load_driver_sha256=chain.digest(Path(load.__file__)),
        hooks=["-Dadvancedrocketrycommunity.releaseTestHooks=true", "permission-2 refload build <x> <z>, drive on|off, status",
               "endgame release-test persist root", "satellite release-test perf ticks raw (read only)",
               "JDWP loopback breakpoint only for batch-cut"],
        classification="Native Windows tooling qualification, not REF/V1/V2/Gate evidence")
    process = None
    try:
        before = device_state(source)
        receipt["ground"] = ground_check(source) if args.mode == "build" else None
        fixture.prepare(args.template, runtime, args.jar, source)
        props = runtime/"server.properties"
        props.write_text(props.read_text().replace("max-players=1", "max-players=80") + "function-permission-level=4\n", encoding="ascii")
        argfile = "win_args.txt" if sys.platform == "win32" else "unix_args.txt"
        debug_port = server.allocate_port()
        command = [str(args.java), "-Xms512M", "-Xmx4G", "-Dadvancedrocketrycommunity.releaseTestHooks=true"]
        if args.mode == "build" and args.variant == "batch-cut":
            command.append(f"-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:{debug_port}")
        command += [f"@libraries/net/minecraftforge/forge/1.20.1-47.4.10/{argfile}", "nogui"]
        receipt.update(java_command=command, properties=props.read_text(), started_utc=load.utc_now())
        process = server.CapturedProcess(command, runtime, evidence/"console.log")
        process.wait_for(server.READY_MARKER, 240)
        port = int(server.parse_java_properties(props.read_bytes())["server-port"])
        receipt["server_status"] = chain.ready_status(server, port, "1.20.1-1.8.0-dev")
        chain.require(receipt["server_status"]["players"]["online"] == 0, "Unexpected player")
        console = Console(process, receipt["commands"])
        receipt["boot_readbacks"] = load.readbacks(console)
        baseline = forced(console)
        if args.mode == "build":
            receipt["preloaded_chunks"] = preload(console)
            receipt["build_reply"] = console.query(load.HOOK+f"build {X} {Z}", "ARCE_RELEASE_TEST refload built")
            shutil.copy2(runtime/"config"/(common.NS+"-common.toml"), evidence/"built-common.toml")
            # Identity/registration become authoritative only after chunk save,
            # registration pass and root persistence, not the build return.
            console.query("save-all flush", r"Saved the game")
            time.sleep(7)
            console.query("arce endgame release-test persist root", "ARCE_RELEASE_TEST persist root")
            if args.variant == "large-range":
                receipt["range_refusal"] = console.all(load.HOOK+"build 1000001 0")
                chain.require(any("1000000" in line for line in receipt["range_refusal"]), "Oversized build coordinate not refused")
                receipt["range_scope"] = "Maximum-depth 256/3x3 shaft. Oversized builder coordinate is grammar refusal only, NOT arbitrary physical-range request coverage (no C6 input)."
            if args.variant == "batch-cut":
                batch_cut(console, args, evidence, receipt, debug_port)
            else:
                load.sample(console, evidence, args, receipt)
                unload(console, baseline, receipt)
        else:
            # No build on qualification: re-building makes NEW stations and a
            # fresh root mass. refload's memory-only reset is recorded honestly.
            receipt["unbuilt_status"] = console.query(load.HOOK+"status", "ARCE_RELEASE_TEST refload status built=false")
            receipt["readbacks"] = load.readbacks(console)
        if process.process.poll() is None:
            console.query("save-all flush", r"Saved the game")
            receipt["commands"].append("stop")
            process.command("stop")
        receipt["exit_code"] = process.finish()
        is_cut = args.mode == "build" and args.variant == "batch-cut"
        chain.require(receipt["exit_code"] != 0 if is_cut else receipt["exit_code"] == 0, "Unexpected exit")
        receipt["stopped_utc"] = load.utc_now()
        errors = [line.strip() for line in process.lines if re.search(r"/(ERROR|FATAL)\]", line)]
        chain.require(not errors, "Native ERROR/FATAL: " + str(errors))
        after = device_state(runtime/"world")
        chain.write(evidence/"state.json", after)
        receipt["disk_state_sha256"] = chain.digest(evidence/"state.json")
        if args.mode != "build":
            receipt["comparison"] = compare(before, after)
        else:
            old = device_keys(before)
            created = [row for key,row in device_keys(after).items() if key not in old]
            chain.write(evidence/"created-devices.json", created)
            receipt["created_device_count"] = len(created)
            chain.require(len(created) == 152, "Expected 152 new devices (20+4+16+64+32+16)")
        shutil.copy2(runtime/"config"/(common.NS+"-common.toml"), evidence/"host-common.toml")
        chain.write(evidence/"files.json", fixture.file_inventory(runtime/"world"))
        chain.require(inventory == fixture.file_inventory(source), "Source mutated")
        chain.verify_seal(source.parent)
        if args.mode == "build" or args.mode == "recover-1":
            target = folder/"source-world" if args.mode == "build" else evidence/"source-world"
            shutil.copytree(runtime/"world", target)
        receipt["status"] = "PASS"
        if args.mode == "build":
            chain.write(folder/"manifest.json", dict(receipt, actors=actors(), client_steps=client_steps(),
                observations=load.observation_plan(),
                cut_mapping=historical_cuts(common.ROOT/"docs/work/v1.7.0-c13-closure/root-checks.zip"),
                config_semantics="Builder sets physical=true,maxDepth=256 and restores original values at server stop. Native qualification configs retained. Other budgets unchanged. On restart hooks are unbuilt; do not rebuild archived world."))
    except BaseException as exc:
        receipt["error"] = f"{type(exc).__name__}: {exc}"
        raise
    finally:
        if process is not None:
            process.abort()
            chain.require(process.process.poll() is not None, "Owned JVM alive; retain runtime and lock")
        if (runtime/"logs/debug.log").exists():
            shutil.copy2(runtime/"logs/debug.log", evidence/"debug.log")
        chain.write(evidence/"receipt.json", receipt)
        chain.require(runtime.resolve().is_relative_to((args.output/"_work").resolve()), "Unsafe cleanup")
        if runtime.exists():
            shutil.rmtree(runtime)
    chain.seal(folder if args.mode == "build" else evidence)
    return receipt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--variant", choices=VARIANTS, required=True)
    parser.add_argument("--mode", choices=("build", "qualification", "recover-1", "recover-2"), required=True)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--jar", type=Path, default=common.JAR)
    parser.add_argument("--template", type=Path, required=True)
    parser.add_argument("--java", type=Path, required=True)
    parser.add_argument("--lock-owner", required=True)
    parser.add_argument("--warmup", type=float, default=120)
    parser.add_argument("--duration", type=float, default=600)
    parser.add_argument("--qualification", action="store_true")
    parser.add_argument("--jfr", action="store_true", help="K15 bounded JFR/GC observer on this owned server only")
    args = parser.parse_args()
    chain.require(not args.mode.startswith("recover") or args.variant == "batch-cut", "Recovery only for batch-cut")
    print(json.dumps(run(args), indent=2))


if __name__ == "__main__":
    main()
