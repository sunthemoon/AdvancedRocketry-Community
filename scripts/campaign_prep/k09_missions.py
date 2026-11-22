"""K09 copy-only mission preparation on C6, one externally locked server per call.

Only registered commands mutate saves. Source worlds are sealed after shutdown;
later runs always use copies. No client, NBT writer, or configuration override.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
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
from scripts.campaign_prep import k07_planets as planets
from scripts.campaign_prep import k16_core as common

OUTPUT = common.EVIDENCE / "final-campaign/c8fa16e16/fixtures/K09"
PARENT = OUTPUT.parent / "K03/FI07/source-world"
OWNER = "04410000-0000-4000-8000-000000000001"
HOOK = "arce satellite release-test "
WRITER = HOOK + "registry-writer "
SIZES = {"missions-101": 101, "missions-500": 500, "missions-1000": 1000}
TERMINAL = (2048, 100, 2048)


def batch_owners(count=32):
    """Java nameUUIDFromBytes, matching C6; not owner-configurable in that hook."""
    result = []
    for i in range(count):
        raw = hashlib.md5(f"arce:v160:stress-owner:{i}".encode()).digest()
        result.append(str(UUID(bytes=raw, version=3)))
    return result


def batches(size):
    chain.require(type(size) is int and 1 <= size <= 1000, "Unsupported mission set")
    return [HOOK + f"batch {min(100, size - start)} 32" for start in range(0, size, 100)]


def effective_values(jar):
    with zipfile.ZipFile(jar) as archive:
        prefix = f"data/{common.NS}/satellite_definitions/"
        definitions = {Path(n).stem: json.loads(archive.read(n)) for n in archive.namelist()
                       if n.startswith(prefix) and n.endswith(".json")}
    return dict(definitions=definitions, fixed_limits=dict(completions=32, inspections=64, interval_ticks=20,
        unfinished_global=1024, unfinished_per_owner=64, satellites_per_owner=256, mission_admission=3072,
        instance_ttl_default=168000, instance_ttl_min=24000, instance_ttl_max=1728000,
        instance_reclaim_ticks=1200, instance_changes=16, instance_inspections=32),
        limit_authority="Pinned C6 SatelliteLimits, RegistryLimits, InstanceLedger; COMMON readback retained per run",
        deadline_semantics="ACTIVE completes to READY at completes_at_logical_time; missions do not expire. AVAILABLE instances expire at expires_at.")


def config_readback(path):
    values = tomllib.loads(path.read_text(encoding="utf-8"))["satellites"]
    expected = dict(unfinishedMissionsGlobal=1024, unfinishedMissionsPerOwner=64, finishedMissionsPerOwner=128,
        missionsTotal=3072, satellitesGlobal=4096, satellitesPerOwner=256, asteroidInstancesGlobal=2048,
        asteroidInstancesPerOwner=16, intentIntervalTicks=10, selectionIntervalTicks=2,
        asteroidInstanceTtlTicks=168000, asteroidMissionTimePercent=100, gasMissionTimePercent=100)
    chain.require(all(values.get(k) == v for k, v in expected.items()), "Non-default mission limits")
    return values


def same_tick_observation(lines):
    claims = [line.strip() for line in lines if "ARCE_MISSION_DELIVERY" in line and "event=CLAIM " in line]
    return dict(claims=claims, status="NOT_COVERED", tick=None,
        reason="DeliveryAudit records save_epoch, not arrival server tick; timestamps and raw tick polls cannot correlate intents.",
        a0="ResourceMissionsTest.twoOwnersSettleInTheSameTickIndependently",
        a1="SatelliteGameTests.terminalMissionIsServerAuthoritativeAndClaimsExactlyOnce; SatelliteLifecycleGameTests.menuIntentsAreSpacedPerPlayer (per-player same-tick limiter only)",
        a1_same_tick_two_owner="NOT_COVERED: no such A1 test in frozen C6; the two-owner same-tick test is A0, not A1",
        intent_tool="K18")


def scheduler_observation(lines):
    rows = []
    for line in lines:
        match = re.search(r"ARCE_SATELLITE_SCHEDULER completed=(\d+) pruned=(\d+) inspected=(\d+) remaining=(\d+)", line)
        if match:
            values = dict(zip(("completed", "pruned", "inspected", "remaining"), map(int, match.groups())))
            chain.require(values["completed"] <= 32 and values["inspected"] <= 64, "Scheduler exceeded fixed pass budget")
            rows.append(dict(line=line.strip(), **values))
    return dict(passes=rows, scope="Changed ordinary scheduler passes only; hooks may call completeDue without this line",
                max_completed=max((r["completed"] for r in rows), default=None),
                max_inspected=max((r["inspected"] for r in rows), default=None))


def cut_mapping():
    return {
        "A": dict(command="terminal 2048 100 2048 cut chunk", boundary="ReleaseTestCommands.cut: level.getChunkSource().save(true), no registry flush in this command; assert READY/no paid state on stopped disk", coverage="runtime disk oracle required"),
        "B": dict(command="terminal 2048 100 2048 cut registry", boundary="ReleaseTestCommands.cut: data.flush awaits SatelliteRegistryWriter.awaited COMMITTED publication; no chunk save; assert CLAIMED/unacknowledged and empty disk buffer", coverage="runtime disk oracle required"),
        "D": dict(command="terminal 2048 100 2048 cut both", boundary="awaited registry commit, then chunk-source save(true); not merely async request/snapshot", coverage="NOT_COVERED: not selected for this qualification"),
        "C": dict(boundary="A then admin mission cancel while original terminal unloaded; cancellation is RAM until the next commit", coverage="NOT_COVERED: not qualified here"),
        "neither": dict(boundary="No existing command halts between ready claim and both writes", coverage="NOT_COVERED"),
        "async": dict(boundary="SatelliteRegistryWriter.capture/request snapshot is not durable; IO thread stages/forces/readbacks/atomic-moves; owner tick START publishes COMMITTED and durable epoch. SatelliteRegistryWriterCuts CAPTURE/STAGED/MOVED identify distinct points, not terminal reward arrival ticks.", coverage="mapping, not an additional qualified cut"),
    }


def client_steps():
    return dict(preconditions=["O5 supplies two authenticated owner accounts. Placeholder-owned chips are not silently reassigned; assemble new packages/chips as each account.",
        "online-mode=true, whitelist exactly A/B, enforce-whitelist=true, RCON off; no release-test property for ordinary play.",
        "Powered owned loaded terminal within 8 blocks; matching control chip; persisted terminal ID and next durable registry epoch for resource delivery; buffer/receipt capacity available."],
        controls=dict(previous=0, next=1, assemble=2, launch=3, claim=4, cancel=5,
                      decommission=6, unlink=7, option_previous=8, option_next=9, withdraw=10, page=11),
        workflow=["Use the satellite builder to assemble chassis plus kind module, solar/storage/battery and required cargo/intake; retain the bound package and matching chip. Put chip and package into owned terminal and choose Earth/system or discovered gas giant using Previous/Next.",
            "Launch data/survey. Wait for READY; Claim data for research, Claim survey for AVAILABLE logical instances. With an operational miner chip, select an owned same-system AVAILABLE instance via option Previous/Next, then Launch. For gas choose discovered gas_giant and product, then Launch. Wait READY, Claim at the bound terminal, Withdraw into player inventory, and replay Claim to check no duplicate.",
            "G03: A and B each own a separate ready mission/terminal with captured reward. Submit Claim, replay, compare each buffer/account; record actual intent ticks only if K18 supplies them. C6 logs alone do not prove same-tick.",
            "G06: record assembly, progress, target, logical instance/reward and gas product views; separate refused/cancelled/expired-instance controls. Repeat English/Chinese, scales 2/3/4, F3+T and second real GPU. No physical instance destination exists.",
            "G07: A starts/claims/cancels while B opens its own permitted view, then switch roles and reconnect B. Terminals are owner-private: no mission membership/observer grant exists; do not promise B access to A's private screen. Refused access must preserve state."],
        diagnostics=["arce satellite list", "arce satellite inspect <satellite UUID>", "arce satellite research",
            "arce satellite admin mission inspect <mission UUID>", "arce satellite admin instance inspect <instance UUID>"],
        status="SCRIPTED_ONLY; no client or authenticated account qualified")


class Console(planets.Console):
    def all(self, command):
        start = len(self.process.lines)
        self.query(command, r".+")
        self.query("say K09_READBACK_END", r"\[Server\] K09_READBACK_END")
        return [line.strip() for line in self.process.lines[start:] if "K09_READBACK_END" not in line]


def readbacks(console):
    commands = ["arce beta data-report", "arce satellite admin diagnostics", "arce satellite admin evidence",
                WRITER + "status", HOOK + "perf ticks", "time query gametime", "forceload query"]
    result = {cmd: console.all(cmd) for cmd in commands}
    chain.require(any("operational=true" in line for line in result[commands[0]]), "Managed stores unavailable")
    return result


def root_state(world):
    from scripts import v170_world_fixture as fixture
    return fixture.read_nbt(world / "data" / (common.NS + "_satellite_missions.dat"))["data"]


def mission_id(row):
    ids = chain.saved_uuids(row["mission_id"])
    chain.require(len(ids) == 1, "Invalid mission identity")
    return next(iter(ids))


def compare_missions(before, after):
    """Allow ordinary completion only; every other mission field/account stays exact."""
    old, new = ({mission_id(m): m for m in state["missions"]} for state in (before, after))
    chain.require(old.keys() == new.keys(), "Mission identities changed")
    transitions = []
    for key, value in old.items():
        target = new[key]
        if value["status"] == "active" and target["status"] == "ready":
            value = dict(value, status="ready", ready_at=target["ready_at"])
            transitions.append(key)
        chain.require(value == target, "Unexpected mission change: " + key)
    chain.require(before["research_accounts"] == after["research_accounts"], "Research changed without claim")
    instances = json.loads(json.dumps(before["instances"]))
    for instance in instances:
        if instance["schema_version"] == 1:
            instance.update(schema_version=2, origin_kind="survey_mission", origin_id=instance.pop("source_mission"))
    chain.require(instances == after["instances"], "Instance snapshot changed beyond schema-1 migration")
    for field in ("satellites", "ground_surveys"):
        chain.require(before.get(field) == after.get(field), "Unexpected authority change: " + field)
    return dict(ids_equal=True, snapshots_equal=True, accounts_equal=True, completed=transitions)


def clock_check(before, after, boot_game_time=None):
    old, new = before["clock"], after["clock"]
    logical = new["logical_game_time"] - old["logical_game_time"]
    observed = new["last_observed_game_time"] - old["last_observed_game_time"]
    limit = observed if boot_game_time is None else new["last_observed_game_time"] - min(old["last_observed_game_time"], boot_game_time)
    chain.require(0 <= logical <= max(0, limit), "Logical time includes non-game progress")
    return dict(logical_delta=logical, observed_game_delta=observed, maximum_online_game_delta=limit,
                wall_downtime_counted=False, scope="Stopped native clock versus observed game ticks, not wall seconds")


def terminal_disk(world, pos=TERMINAL):
    state = chain.snapshot(world)
    matches = [row["value"]["SatelliteTerminal"] for row in state["block_entities"]
               if tuple(row["value"].get(k) for k in ("x", "y", "z")) == pos
               and "SatelliteTerminal" in row["value"]]
    chain.require(len(matches) == 1, "Terminal missing on disk")
    return matches[0], state["forced_chunks"]


def resource_mission(root, pos=TERMINAL):
    x, y, z = pos
    packed = ((x & 0x3ffffff) << 38) | ((z & 0x3ffffff) << 12) | (y & 0xfff)
    candidates = [m for m in root["missions"] if m.get("kind") == "asteroid" and m.get("terminal_pos") == packed]
    chain.require(len(candidates) == 1, "One bound asteroid mission required")
    return candidates[0]


def run(args):
    from scripts import run_dedicated_server_smoke as server
    from scripts import v170_world_fixture as fixture
    from scripts.campaign_prep import k09_mission_load as load
    chain.require(args.lock_owner.startswith("W441 pid=") and (chain.LOCK / "owner.txt").read_text(
        encoding="utf-8-sig").startswith(args.lock_owner), "W441 heavy-JVM lock required")
    chain.require(min(shutil.disk_usage(p).free for p in ("C:/", "D:/")) >= 10 * 1024**3, "10 GiB required")
    chain.require(chain.digest(args.jar) == common.JAR_HASH, "Pinned C6 JAR required")
    owner = str(UUID(args.owner))
    folder = args.output / args.variant
    source = load.source_for(args)
    chain.verify_seal(source.parent)
    inventory = fixture.file_inventory(source)
    evidence = folder / args.mode
    evidence.mkdir(parents=True, exist_ok=False)
    runtime = args.output / "_work" / (args.variant + "-" + args.mode)
    chain.require(not runtime.exists(), "Runtime already exists")
    receipt = dict(status="FAIL", mode=args.mode, variant=args.variant, owner=owner, commands=[],
        candidate_commit=common.CANDIDATE, jar_sha256=chain.digest(args.jar),
        driver_commit=subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=common.ROOT, text=True).strip(),
        driver_sha256=chain.digest(Path(__file__)), load_sha256=chain.digest(Path(load.__file__)),
        source=str(source), source_seal_sha256=chain.digest(source.parent / "SHA256SUMS.txt"),
        classification="qualification, not campaign measurement" if args.qualification else "campaign procedure, not acceptance",
        hooks=["releaseTestHooks=true; batch, perf ticks/raw, registry-writer status/terminal-place/terminal-launch, terminal cut; advance only for explicitly synthetic reclaim"])
    process = None
    try:
        before = root_state(source)
        boot_time = fixture.read_nbt(source / "level.dat")["Data"]["Time"]
        fixture.prepare(args.template, runtime, args.jar, source)
        properties = runtime / "server.properties"
        properties.write_text(properties.read_text(encoding="ascii") + "function-permission-level=4\n", encoding="ascii")
        argfile = "win_args.txt" if sys.platform == "win32" else "unix_args.txt"
        command = [str(args.java), "-Xms512M", "-Xmx2G", "-Dadvancedrocketrycommunity.releaseTestHooks=true",
                   f"@libraries/net/minecraftforge/forge/1.20.1-47.4.10/{argfile}", "nogui"]
        receipt.update(java_command=command, properties=properties.read_text(), started_utc=load.utc_now())
        process = server.CapturedProcess(command, runtime, evidence / "console.log")
        process.wait_for(server.READY_MARKER, 240)
        port = int(server.parse_java_properties(properties.read_bytes())["server-port"])
        receipt["server_status"] = chain.ready_status(server, port, "1.20.1-1.8.0-dev")
        chain.require(receipt["server_status"]["players"]["online"] == 0, "Unexpected player")
        console = Console(process, receipt["commands"])
        receipt["effective_common"] = config_readback(runtime / "config" / (common.NS + "-common.toml"))
        if args.mode == "build":
            # A single fixed-actor survey: C6 has no generic kind/owner launch hook.
            console.query(WRITER + "terminal-place", r"ARCE_RELEASE_SRW_TERMINAL placed")
            console.query(WRITER + "terminal-launch", r"ARCE_RELEASE_SRW_TERMINAL handled=true")
            deadline = time.monotonic() + 60
            while True:
                line = console.query(WRITER + "status", r"ARCE_RELEASE_SRW_STATUS")
                if "package=empty" in line and "pending=0" in line:
                    break
                chain.require(time.monotonic() < deadline, "Survey registration effect did not settle")
                time.sleep(.1)
            time.sleep(.6)  # Existing per-player 10-tick intent interval, never changed.
            console.query(WRITER + "terminal-launch", r"ARCE_RELEASE_SRW_TERMINAL handled=true.*result=SUCCESS")
            for cmd in batches(SIZES[args.variant]):
                console.query(cmd, r"ARCE_RELEASE_TEST_SATELLITE_BATCH .*code=SUCCESS")
        elif args.mode.startswith("cut-"):
            mode = "chunk" if args.mode == "cut-A" else "registry"
            receipt["pre_cut"] = readbacks(console)
            cmd = HOOK + "terminal 2048 100 2048 cut " + mode
            console.query(cmd, r"ARCE_RELEASE_TEST cut mode=" + mode + r" claim=SUCCESS")
        elif args.mode.startswith("recover-"):
            load.recover(console, receipt, args)
        else:
            receipt["pre_sampling"] = readbacks(console)
            load.sample(console, evidence, args, receipt)
        if not args.mode.startswith("cut-"):
            receipt["readbacks"] = readbacks(console)
            console.query("save-all flush", server.SAVE_MARKER.pattern)
            receipt["commands"].append("stop")
            process.command("stop")
        receipt["exit_code"] = process.finish()
        chain.require(receipt["exit_code"] == (75 if args.mode.startswith("cut-") else 0), "Unexpected server exit")
        receipt["stopped_utc"] = load.utc_now()
        errors = [line.strip() for line in process.lines if re.search(r"/(ERROR|FATAL)\]", line)]
        chain.require(not errors, "Native ERROR/FATAL: " + str(errors))
        after = root_state(runtime / "world")
        chain.write(evidence / "registry.json", after)
        receipt["scheduler"] = scheduler_observation(process.lines)
        receipt["same_tick"] = same_tick_observation(process.lines)
        if args.mode == "build":
            active = [m for m in after["missions"] if m["status"] == "active"]
            chain.require(sum(m["kind"] == "data" for m in active) == SIZES[args.variant], "Requested active data set not retained")
            chain.require(any(m["kind"] == "survey" for m in active), "Active survey not retained")
            receipt["active_count"] = len(active)
            receipt["status"] = "PASS"
            chain.write(folder / "manifest.json", dict(schema_version=1, **receipt,
                effective_values=effective_values(args.jar), batch_owners=batch_owners(),
                owner_policy="Batch and survey actors fixed by C6 hooks; --owner reserves client placeholder only, O5 unresolved",
                missions=after["missions"], instances=after["instances"], clock=after["clock"],
                cut_mapping=cut_mapping(), client_steps=client_steps(),
                not_covered=["Active asteroid and gas construction: no registered generic kind hook; terminal setup produces READY asteroid only.",
                    "Per-kind campaign deadline coverage incomplete: only DATA and SURVEY active; no mission expiry status exists.",
                    "Synthetic worst-case-root not prepared; REF performance and V1/V2 not run." ]))
        elif args.mode.startswith("cut-") or args.mode.startswith("recover-"):
            load.check_cut(args, before, after, runtime / "world", receipt)
        else:
            receipt["comparison"] = compare_missions(before, after)
            receipt["clock_check"] = clock_check(before, after, boot_time)
        shutil.copy2(runtime / "config" / (common.NS + "-common.toml"), evidence / "host-common.toml")
        chain.write(evidence / "files.json", fixture.file_inventory(runtime / "world"))
        chain.require(inventory == fixture.file_inventory(source), "Source changed")
        chain.verify_seal(source.parent)
        if args.mode == "build" or args.mode.startswith(("cut-", "recover-")) or args.mode == "load-start":
            destination = folder / "source-world" if args.mode == "build" else evidence / "source-world"
            shutil.copytree(runtime / "world", destination)
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
    parser.add_argument("--variant", choices=tuple(SIZES), required=True)
    parser.add_argument("--mode", choices=("build", "load-start", "load-restart", "baseline-1", "baseline-2",
        "cut-A", "cut-B", "recover-A-1", "recover-A-2", "recover-B-1", "recover-B-2"), required=True)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--jar", type=Path, default=common.JAR)
    parser.add_argument("--template", type=Path, required=True)
    parser.add_argument("--java", type=Path, required=True)
    parser.add_argument("--lock-owner", required=True)
    parser.add_argument("--owner", default=OWNER)
    parser.add_argument("--warmup-seconds", type=float, default=120)
    parser.add_argument("--sample-seconds", type=float, default=600)
    parser.add_argument("--stop-seconds", type=float, default=1800)
    parser.add_argument("--qualification", action="store_true")
    args = parser.parse_args()
    chain.require(all(math.isfinite(v) and 0 < v <= 14400 for v in
        (args.warmup_seconds, args.sample_seconds, args.stop_seconds)), "Window lengths must be finite in (0,14400]")
    print(json.dumps(run(args), indent=2))


if __name__ == "__main__":
    main()
