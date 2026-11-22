"""K08B copy-only ordinary solar-array charging on the pinned C6 server.

The detached caller owns one heavy-JVM lock per invocation. Minecraft alone
writes saves; projections below only read a stopped world. No client or hook.
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
import zipfile

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.campaign_prep import k08_stations as stations
from scripts.campaign_observe.common import safe_path, utc_now

chain, planets, common = stations.chain, stations.planets, stations.common
NS, SPACE = common.NS, common.SPACE
OUTPUT = stations.OUTPUT.with_name("K08B")
VARIANTS = ("two", "target-removal", "cost-change")
ARRAYS, COLLECTORS, RATE, FOLD_TICKS = 32, 63, 126, 200
IN_SYSTEM, INTERSTELLAR = 2_000_000, 8_000_000
MAX_RUN_SECONDS = 1800  # Includes startup/commands/stop; well below a two-hour hold.


def energy_plan(balance, target, count=ARRAYS):
    chain.require(type(balance) is int and 0 <= balance <= 10_000_000, "Invalid balance")
    chain.require(target in (IN_SYSTEM, INTERSTELLAR), "Unsupported target")
    chain.require(type(count) is int and 1 <= count <= ARRAYS, "Invalid array count")
    deficit = max(0, target - balance)
    ticks = (deficit + RATE*count - 1) // (RATE*count)
    return dict(initial_balance=balance, target=target, arrays=count,
                collectors_per_array=COLLECTORS, fe_per_array_tick=RATE,
                fe_per_station_tick=RATE * count, generation_ticks=ticks,
                generation_seconds_at_20tps=ticks / 20,
                generation_and_fold_seconds_at_20tps=(ticks + FOLD_TICKS) / 20,
                single_array_seconds_at_20tps=((deficit+RATE-1)//RATE+FOLD_TICKS)/20)


def bank(row):
    """Nonoverlapping 3x23 air footprints, inside the station's own claim."""
    lo_x, lo_z, hi_x, hi_z = row["region"]
    result = []
    for i in range(ARRAYS):
        x, y, z = lo_x + 33 + 5*(i % 8), 200, lo_z + 32 + 24*(i // 8)
        chain.require(lo_x <= x-1 <= x+1 <= hi_x and lo_z <= z-1 <= z+21 <= hi_z,
                      "Array bank escapes station claim")
        result.append(dict(controller=[x,y,z], core=[x-1,y,z-1],
                           minimum=[x-1,y,z-1], maximum=[x+1,y,z+21]))
    return result


def cells(unit):
    x, y, z = unit["controller"]
    return [(a,y,b) for a in range(x-1,x+2) for b in range(z-1,z+22)]


def pos(value):
    return " ".join(map(str, value))


def build_commands(unit):
    x, y, z = unit["controller"]
    return [f"fill {x-1} {y} {z+1} {x+1} {y} {z+21} {NS}:solar_array_panel",
            f"setblock {x-1} {y} {z} {NS}:power_output_plug",
            f"setblock {x+1} {y} {z} {NS}:power_output_plug",
            f"setblock {pos(unit['core'])} {NS}:warp_core",
            f"setblock {x} {y} {z} {NS}:solar_array[facing=north]"]


def air_command(unit):
    return f"execute in {SPACE} " + " ".join(
        f"if block {pos(p)} minecraft:air" for p in cells(unit)) + " run say K08B_AIR"


def tickets(units):
    return sorted({(x//16,z//16) for u in units for x,_,z in cells(u)})


def inherited_tickets(state):
    result = set()
    for path, root in state["forced_chunks"].items():
        if path == f"dimensions/{NS}/space/data/chunks.dat":
            for word in root["data"]["Forced"]:
                x, z = word & 0xffffffff, (word >> 32) & 0xffffffff
                result.add((x if x < 2**31 else x-2**32, z if z < 2**31 else z-2**32))
    return result


def selected_rows(rows, variant):
    owners = (stations.OWNER, stations.OTHER) if variant == "two" else (stations.OWNER,)
    result = []
    for owner in owners:
        matches = [r for r in rows if r["owner"] == owner]
        chain.require(len(matches) == 1 and matches[0]["cores"], "Expected K08 owner/core missing")
        row = matches[0]
        chain.require(row["orbit"] in (NS+":earth", NS+":moon"), "Unsupported solar orbit")
        target = INTERSTELLAR if variant == "cost-change" else IN_SYSTEM
        result.append(dict(station=row["id"], owner=owner, orbit=row["orbit"],
                           energy=energy_plan(row["energy"], target), bank=bank(row)))
    return result


def balance(console, station):
    lines = console.all("arce station admin warp " + station)
    matches = [re.search(r"station=" + re.escape(station) + r" .*?balance=(\d+) pending=(\d+)", s)
               for s in lines]
    matches = [m for m in matches if m]
    chain.require(len(matches) == 1, "Missing/ambiguous native balance: " + str(lines))
    return dict(balance=int(matches[0][1]), pending=int(matches[0][2]), lines=lines)


def wait_balances(console, plan, *, settled=False):
    deadline = time.monotonic() + (30 if settled else 600)
    while True:
        result = {r["station"]: balance(console,r["station"]) for r in plan}
        if all(result[r["station"]]["balance"] >= r["energy"]["target"] and
               (not settled or result[r["station"]]["pending"] == 0) for r in plan):
            return result
        chain.require(time.monotonic() < deadline, "Folded energy target not reached within fixed window")
        time.sleep(2)


def charge(console, plan, parent_state):
    started = time.monotonic()
    start_utc = utc_now()
    units = [u for r in plan for u in r["bank"]]
    added = sorted(set(tickets(units)) - inherited_tickets(parent_state))
    for x,z in added:
        console.all(f"execute in {SPACE} run forceload add {x*16} {z*16}")
    for x,z in tickets(units):
        stations.await_loaded(console,x*16,200,z*16)
    # Check every touched cell before any placement. A collision fails the run.
    for unit in units:
        lines = console.all(air_command(unit))
        chain.require(any("[Server] K08B_AIR" in s for s in lines), "Temporary site is not empty")
    for unit in units:
        for command in build_commands(unit):
            console.all(f"execute in {SPACE} run {command}")
    built = time.monotonic()
    reached = wait_balances(console,plan)
    for unit in units:
        console.all(f"execute in {SPACE} run fill {pos(unit['minimum'])} {pos(unit['maximum'])} minecraft:air")
        chain.require(any("[Server] K08B_AIR" in s for s in console.all(air_command(unit))),
                      "Temporary blocks remain")
    settled = wait_balances(console,plan,settled=True)
    for x,z in added:
        console.all(f"execute in {SPACE} run forceload remove {x*16} {z*16}")
    return dict(start_utc=start_utc, total_seconds=time.monotonic()-started,
                placement_seconds=built-started, reached=reached, settled=settled,
                added_chunk_tickets=added, temporary_arrays=len(units),
                temporary_blocks=len(units)*67)


def compare_rows(before, after, plan):
    chain.require(len(before) == len(after), "Station count changed")
    planned = {p["station"]:p for p in plan}
    for old,new in zip(before,after):
        expected = dict(old)
        if old["id"] in planned:
            chain.require(new["energy"] >= planned[old["id"]]["energy"]["target"], "Saved balance below target")
            expected["energy"] = new["energy"]
        chain.require(expected == new, "Station authority or unselected energy changed")


def check_lock(token):
    chain.require(token.startswith("W442 pid=") and (chain.LOCK/"owner.txt").read_text(
        encoding="utf-8-sig").startswith(token), "W442 heavy-JVM lock required")
    if sys.platform == "win32":
        chain.require(min(shutil.disk_usage(p).free for p in ("C:/","D:/")) >= 10*1024**3,
                      "10 GiB free on C: and D: required")


def verify_candidate(jar):
    chain.require(chain.digest(jar) == common.JAR_HASH, "Pinned C6 JAR required")
    with zipfile.ZipFile(jar) as archive:
        for name in ("earth","moon"):
            body = json.loads(archive.read(f"data/{NS}/celestial_bodies/{name}.json"))
            chain.require(body.get("solar_intensity",1) == 1, "Expected unit solar intensity")
    return True


def run(args):
    from scripts import run_dedicated_server_smoke as server
    from scripts import v170_world_fixture as fixture
    for name in ("output","parents","jar"):
        setattr(args,name,safe_path(getattr(args,name)))
    verify_candidate(args.jar)
    folder = args.output/args.variant
    source = (args.parents/args.variant if args.mode != "qualify" else folder)/"source-world"
    chain.verify_seal(source.parent)
    parent_state, parent_authority = planets.authority(source)
    before = fixture.file_inventory(source)
    rows = stations.station_rows(parent_state)
    plan = selected_rows(rows,args.variant)
    if args.mode == "plan":
        return dict(variant=args.variant,parent=str(source),parent_seal_sha256=chain.digest(source.parent/"SHA256SUMS.txt"),
                    plan=plan,serves=["F02","F03","F07"] if args.variant == "two" else ["F05","F07"],
                    coverage="Energy fixture only; live consent, target knowledge, pending/due timing and V1/V2 NOT_COVERED")
    check_lock(args.lock_owner)
    for name in ("template","java"):
        setattr(args,name,safe_path(getattr(args,name)))
    evidence = folder/("creation" if args.mode == "build" else "qualification")
    chain.require(not evidence.exists() and (args.mode != "build" or not folder.exists()), "Output already exists")
    evidence.mkdir(parents=True)
    runtime = args.output/"_work"/(args.variant+"-"+args.mode)
    chain.require(not runtime.exists(), "Existing runtime is not owned")
    receipt = dict(status="FAIL",start_utc=utc_now(),mode=args.mode,variant=args.variant,commands=[],
        candidate_commit=common.CANDIDATE,jar_sha256=common.JAR_HASH,hooks=[],plan=plan,
        driver_commit=subprocess.check_output(["git","rev-parse","HEAD"],cwd=common.ROOT,text=True).strip(),
        driver_sha256=chain.digest(Path(__file__)),parent_source=str(source),
        parent_seal_sha256=chain.digest(source.parent/"SHA256SUMS.txt"),lock_owner=args.lock_owner)
    chain.write(evidence/"plan-before-run.json",receipt)
    process = None
    run_started = time.monotonic()
    try:
        fixture.prepare(args.template,runtime,args.jar,source)
        properties = runtime/"server.properties"
        properties.write_text(planets.host_properties(properties.read_text(encoding="ascii")),encoding="ascii")
        argfile = "win_args.txt" if sys.platform == "win32" else "unix_args.txt"
        command = [str(args.java),"-Xms512M","-Xmx2G",
                   f"@libraries/net/minecraftforge/forge/1.20.1-47.4.10/{argfile}","nogui"]
        receipt.update(command=command,properties=properties.read_text(),forge="47.4.10",jei="absent")
        process = server.CapturedProcess(command,runtime,evidence/"console.log")
        process.wait_for(server.READY_MARKER,240)
        config = runtime/"config"/(NS+"-common.toml")
        raw_config = config.read_text(encoding="utf-8")
        chain.require(re.search(r"solarGeneratorEnabled\s*=\s*true",raw_config) and
                      re.search(r"solarGeneratorMultiplier\s*=\s*1\s*(?:\n|$)",raw_config),
                      "Ordinary solar configuration changed")
        shutil.copy2(config,evidence/config.name)
        port = int(server.parse_java_properties(properties.read_bytes())["server-port"])
        receipt["server_status"] = chain.ready_status(server,port,"1.20.1-1.8.0-dev")
        chain.require(receipt["server_status"]["players"]["online"] == 0,"Unexpected player")
        console = stations.Console(process,receipt["commands"])
        receipt["first_load"] = stations.readbacks(console,rows)
        settings = " ".join(receipt["first_load"]["arce station admin warp"])
        chain.require("enabled=true cost_in_system=2000000 cost_interstellar=8000000" in settings,
                      "Effective warp settings changed")
        receipt["before_balances"] = {r["id"]:balance(console,r["id"]) for r in rows}
        if args.mode == "build":
            receipt["charge"] = charge(console,plan,parent_state)
        receipt["readbacks"] = stations.readbacks(console,rows)
        receipt["balances"] = {r["id"]:balance(console,r["id"]) for r in rows}
        console.query("save-all flush",server.SAVE_MARKER.pattern)
        receipt["commands"].append("stop")
        process.command("stop")
        receipt["exit_code"] = process.finish()
        chain.require(receipt["exit_code"] == 0,"Unclean native exit")
        receipt["errors"] = [s.strip() for s in process.lines if re.search(r"/(ERROR|FATAL)\]",s)]
        chain.require(not receipt["errors"],"Native ERROR/FATAL")
        state, authority = planets.authority(runtime/"world")
        after = stations.station_rows(state)
        compare_rows(rows,after,plan if args.mode == "build" else [])
        chain.require(stations.forced_projection(state) == stations.forced_projection(parent_state),
                      "Inherited force tickets changed")
        for row in after:
            chain.require(receipt["balances"][row["id"]]["balance"] == row["energy"] and
                          receipt["balances"][row["id"]]["pending"] == 0,"Console/saved energy differs")
        expected = dict(parent_authority)
        if args.mode == "build":
            expected["warp_energy"] = authority["warp_energy"]
        receipt["authority_matches"] = planets.comparison(expected,authority,"station")
        chain.require(parent_state["block_entities"] == state["block_entities"],"Temporary BE remains or old BE changed")
        for name,value in (("state",state),("authority",authority),("stations",after),
                           ("files",fixture.file_inventory(runtime/"world"))):
            chain.write(evidence/(name+".json"),value)
        chain.require(before == fixture.file_inventory(source),"Parent changed")
        chain.verify_seal(source.parent)
        receipt["source_unchanged"] = True
        receipt["elapsed_seconds"] = time.monotonic()-run_started
        chain.require(receipt["elapsed_seconds"] < MAX_RUN_SECONDS,"Run exceeded fixed half-hour bound")
        receipt["status"] = "PASS"
        if args.mode == "build":
            shutil.copytree(runtime/"world",folder/"source-world")
            chain.write(folder/"manifest.json",dict(schema_version=1,**receipt,stations=after,
                campaign_acceptance=False,required_gates_approved=False,
                serves=["F02","F03","F07"] if args.variant == "two" else ["F05","F07"],
                not_covered=["Real account binding and consent", "Live warp/countdown/due commit",
                             "Target knowledge", "Docked rocket and passengers", "V1/V2 and campaign acceptance"],
                classification="C6 ordinary-energy preparation of K08 operator-created stations; not a played-world or warp result"))
    except BaseException as exc:
        receipt["error"] = f"{type(exc).__name__}: {exc}"
        raise
    finally:
        if process:
            process.abort()
            chain.require(process.process.poll() is not None,"Owned JVM alive; keep lock/runtime")
        if (runtime/"logs/debug.log").exists():
            shutil.copy2(runtime/"logs/debug.log",evidence/"debug.log")
        receipt["end_utc"] = utc_now()
        chain.write(evidence/"receipt.json",receipt)
        chain.require(runtime.resolve().is_relative_to((args.output/"_work").resolve()),"Unsafe cleanup")
        if runtime.exists():
            shutil.rmtree(runtime)
    chain.seal(folder if args.mode == "build" else evidence)
    return receipt


def parser():
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--variant",choices=VARIANTS,required=True)
    result.add_argument("--mode",choices=("plan","build","qualify"),required=True)
    result.add_argument("--output",type=Path,default=OUTPUT)
    result.add_argument("--parents",type=Path,default=stations.OUTPUT)
    result.add_argument("--jar",type=Path,default=common.JAR)
    result.add_argument("--template",type=Path)
    result.add_argument("--java",type=Path)
    result.add_argument("--lock-owner",default="")
    return result


if __name__ == "__main__":
    print(json.dumps(run(parser().parse_args()),indent=2))
