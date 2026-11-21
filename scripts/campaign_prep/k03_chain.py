"""Copy-only historical K03 chain. The caller holds one heavy-JVM lock per run.

No save writer is used here: the pinned host alone changes the disposable copy.
Sealed sources and prior campaign inputs are never opened by a server.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
import time
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
NS = "advancedrocketrycommunity"
C6_COMMIT = "c8fa16e16be87554f26c402546733df6555d15e6"
C6_HASH = "9f7e9d1ef708e27fa5778ca3c72ea3b3605f889465ae0d0d1054a890eb808dd4"
PINS = {
    "FI05": ("v1.4.0", "d33552a5374272b3b0669090ec4466075662ee4c", "576eaaf8a5e23df1b049fd5fa5029f1cd6d6ffcfd0cf50bd5980fc280c31d475"),
    "FI06": ("v1.5.0", "940a5ed3b90a4da0ca4e43417b2bddf41ebb7307", "26a25d024cdf33b31ec96be9534b34f47ab73099458472da7e751faba4060433"),
    "FI07": ("v1.6.0", "ab10fb53a580e53a9a1c24097a487f2a7c54ad52", "452885f0aeccb1396fb2694d149c8add703d1cdfc5f822ed71ef51d4e760dfe0"),
    "FI08": ("v1.7.0", "55da6a58842762c382edce0a5a842d06bb76ff6e", "4326d3d67edf417f4073af86329508fbfa19f8b9dc4d57548e82331a9ac13bbe"),
}
PREVIOUS = dict(zip(PINS, ("FI04", "FI05", "FI06", "FI07")))
DEFAULT_OWNER = "04360000-0000-4000-8000-000000000001"
DEFAULT_MEMBER = "04360000-0000-4000-8000-000000000002"
LOCK = Path("D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm")


def require(ok, message):
    if not ok:
        raise ValueError(message)


def digest(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def verify_seal(folder):
    """Validate only sealed members; later qualification has its own separate seal."""
    rows = (folder / "SHA256SUMS.txt").read_text(encoding="utf-8").splitlines()
    require(bool(rows), "Empty checksum manifest")
    seen = set()
    for row in rows:
        match = re.fullmatch(r"([a-f0-9]{64})  (.+)", row)
        require(match is not None, "Malformed checksum line")
        expected, relative = match.groups()
        path = folder / relative
        require(not Path(relative).is_absolute() and ".." not in Path(relative).parts
                and "\\" not in relative and ":" not in relative, "Unsafe checksum member")
        require(relative not in seen and path.resolve().is_relative_to(folder.resolve()), "Duplicate/escaping member")
        seen.add(relative)
        require(path.is_file() and not path.is_symlink() and digest(path) == expected, "Sealed member changed: " + relative)
    actual_world = {p.relative_to(folder).as_posix() for p in (folder / "source-world").rglob("*") if p.is_file()}
    require(actual_world <= seen, "Unsealed source-world member")


def seal(folder):
    require(not (folder / "SHA256SUMS.txt").exists(), "Refusing to replace seal")
    rows = [f"{digest(p)}  {p.relative_to(folder).as_posix()}\n"
            for p in sorted(folder.rglob("*")) if p.is_file()]
    (folder / "SHA256SUMS.txt").write_text("".join(rows), encoding="utf-8")
    verify_seal(folder)


def host_pin(name, historical):
    version, commit, sha = PINS[name]
    jar = historical / version / f"advancedrocketry-community-1.20.1-{version[1:]}-dev.jar"
    require(digest(jar) == sha, "Historical JAR does not equal W429 pin")
    return jar, dict(version=version, commit=commit, sha256=sha,
                     acquisition="recovered development artifact" if name == "FI08" else "rebuilt, not the released bytes")


def actors(owner, member):
    owner, member = str(uuid.UUID(owner)), str(uuid.UUID(member))
    require(owner != member, "Owner and member must differ")
    return owner, member


def derived_actor(actor, offset):
    return str(uuid.UUID(int=uuid.UUID(actor).int + offset))


def uncovered(name):
    result = ["Historical player activity and a complete historically played/pre-sky source are NOT_COVERED; this is a reconstruction."]
    if name == "FI06":
        result += ["Membership, charged core and executed warp are NOT_COVERED: v1.5 host console has no membership/energy mutation hook; ordinary invite/accept/warp require player actors. The archived run used an external adapter probe, not supplied by W429. No client or substitute probe is started."]
    if name == "FI07":
        result += ["RUNNING resource missions and gas missions are NOT_COVERED: the archived host terminal setup hook advances its asteroid mission to READY. No arbitrary deadline/status editing is permitted."]
    if name == "FI08":
        result += ["Drill, black-hole and gravity variants are NOT_COVERED: the host offers only the combined refload builder, which changes the physical-drill toggle and maximum-depth configuration and inserts synthetic root mass. TASK prohibits budget changes. No standalone owner/target setup command for those devices is registered; K10 must qualify its reference-load procedure separately."]
    return result


class Console:
    def __init__(self, process, commands):
        self.process, self.commands = process, commands

    def query(self, command, marker, timeout=60):
        start = len(self.process.lines)
        self.commands.append(command)
        self.process.command(command)
        index = self.process.wait_for(re.compile(marker), timeout, start_at=start)
        return self.process.lines[index].strip()


def ready_status(server, port, version):
    """W431/R607 pattern: retry only Forge's exact starting reply, within 30 s."""
    deadline = time.monotonic() + 30
    starting = {"text": "Server is still starting! Please wait before reconnecting."}
    while True:
        remaining = deadline - time.monotonic()
        require(remaining > 0, "Server status did not become ready within 30 seconds")
        status = server.wait_for_status(port, timeout=remaining)
        if status != starting:
            server.validate_status_identity(status, version)
            return status
        time.sleep(min(0.1, remaining))


def populate(console, name, owner, member):
    q, made = console.query, {}
    if name == "FI05":
        missions = []
        for body in ("mars", "venus"):
            line = q(f"arce satellite release-test launch {owner} {body}", r"ARCE_RELEASE_TEST_SATELLITE_LAUNCH .*code=SUCCESS")
            mission = re.search(r"mission=([a-f0-9-]{36})", line).group(1)
            missions.append(dict(body=body, mission=mission, launch=line))
        # The historical hook creates 200-tick missions. Its own scheduler is authoritative.
        time.sleep(11)
        for entry in missions:
            entry["claim"] = q("arce satellite release-test claim " + entry["mission"],
                               r"ARCE_RELEASE_TEST_SATELLITE_CLAIM .*code=SUCCESS .*discovered=true")
        made["research"] = missions
        made["station_request"] = q(f"arce station admin create {owner} {NS}:mars K03 researched Mars",
                                     r"Created .* id=[a-f0-9-]{36}")
    elif name == "FI06":
        # Distinct owners avoid silently bypassing the per-owner limit.
        stations = []
        for index, actor in enumerate((derived_actor(owner, 256), derived_actor(member, 256))):
            line = q(f"arce station admin create {actor} {NS}:earth K03 orbital {index}", r"Created .* id=[a-f0-9-]{36}")
            station = re.search(r"id=([a-f0-9-]{36})", line).group(1)
            detail = q("arce station admin inspect " + station, r"region=\S+ pad=")
            # The inspect pad is read back, never guessed from a cell allocation.
            pad = re.search(r"pad=(-?\d+),(-?\d+),(-?\d+)", detail)
            require(pad is not None, "Station inspect omitted pad")
            x, y, z = map(int, pad.groups())
            q(f"execute in {NS}:space run forceload add {x} {z}", "force load")
            q(f"execute in {NS}:space run setblock {x+4} {y} {z+4} {NS}:warp_core", "Changed the block")
            stations.append(dict(id=station, owner=actor, requested_member=member if index == 0 else owner,
                                 inspect=detail, core=[x+4, y, z+4], core_energy=0))
        made["stations"] = stations
        made["warp_route"] = dict(source=NS + ":earth", target=NS + ":moon", status="catalog route only; not activated")
        made["warp_diagnostics"] = q("arce station admin warp", r"warp_enabled=|warp enabled=|countdowns=")
    elif name == "FI07":
        made["terminals"] = []
        for i in range(3):
            x, y, z = 2048 + i * 32, 100, 2048
            # setup loads one terminal chunk itself; no new persistent force ticket.
            actor = derived_actor(owner, 16 + i)
            line = q(f"arce satellite release-test terminal {x} {y} {z} setup {actor}",
                     rf"ARCE_RELEASE_TEST setup pos={x},{y},{z} .*status=READY")
            made["terminals"].append(dict(pos=[x,y,z], owner=actor, result=line))
        made["diagnostics"] = q("arce satellite admin diagnostics", r"ARCE_SATELLITE_DIAGNOSTICS")
    else:
        q("forceload add 4096 4096 4176 4176", "force load")
        made["railguns"] = q(f"arce endgame release-test railguns 4096 100 4096 {owner} 64", r"ARCE_RELEASE_TEST railguns")
        made["elevator_owner"] = derived_actor(owner, 512)
        made["elevator"] = q(f"arce endgame release-test elevator build 4160 100 4160 {made['elevator_owner']} {member}", r"ARCE_RELEASE_TEST elevator built")
        station = re.search(r"station=([a-f0-9-]{36})", made["elevator"]).group(1)
        terminal = re.search(r"terminal_pos=(-?\d+),(-?\d+),(-?\d+)", made["elevator"])
        deadline = time.monotonic() + 150
        for command in ("arce endgame release-test endpoint 4160 100 4160",
                        f"execute in {NS}:space run arce endgame release-test endpoint {' '.join(terminal.groups())}"):
            while True:
                line = q(command, r"ARCE_RELEASE_TEST endpoint")
                if "status=OK" in line:
                    break
                require(time.monotonic() < deadline, "Elevator endpoint registration did not finish")
                time.sleep(0.5)
        q("arce endgame release-test persist root", r"ARCE_RELEASE_TEST persist root")
        made["elevator_binding"] = q("arce endgame release-test elevator bind " + station,
                                      r"ARCE_RELEASE_TEST elevator bind code=OK")
        made["railgun_source"] = q("arce endgame release-test endpoint 4096 100 4096", r"ARCE_RELEASE_TEST endpoint")
        made["railgun_target"] = q("arce endgame release-test endpoint 4160 100 4096", r"ARCE_RELEASE_TEST endpoint")
    return made


def snapshot(world):
    """Read closed native roots, entity regions and block-entity bindings only."""
    from scripts import v170_world_fixture as fixture
    from scripts import v120_precision_world_fixture as region
    from scripts.run_v130_adapter_flight_smoke import decode_entities
    result = dict(roots={}, entities=[], block_entities=[], forced_chunks={})
    for path in sorted((world / "data").glob(NS + "_*.dat")):
        result["roots"][path.name] = fixture.read_nbt(path)
    for path in sorted(world.rglob("chunks.dat")):
        result["forced_chunks"][path.relative_to(world).as_posix()] = fixture.read_nbt(path)
    for kind in ("entities", "region"):
        for path in sorted(world.rglob(kind + "/r.*.*.mca")):
            raw = region._read_regular(path, region.MAX_REGION_BYTES)
            rx, rz = map(int, path.stem.split(".")[1:])
            for slot in range(1024):
                if not int.from_bytes(raw[slot*4:slot*4+3], "big"):
                    continue
                cx, cz = rx*32 + slot%32, rz*32 + slot//32
                compressed = region._region_chunk(raw, cx, cz)
                chunk = decode_entities(compressed, cx, cz) if kind == "entities" else region._decode_chunk(compressed, cx, cz)
                for entity in chunk.get("Entities" if kind == "entities" else "block_entities", []):
                    if str(entity.get("id", "")).startswith(NS + ":"):
                        result["entities" if kind == "entities" else "block_entities"].append(
                            dict(region=path.relative_to(world).as_posix(), value=entity))
    return result


def projection(state):
    """Only stable authority; tick clocks and machine processing are not frozen."""
    roots = {name: payload["data"] for name, payload in state["roots"].items()}
    missions = roots.get(NS + "_satellite_missions.dat", {})
    endgame = roots.get(NS + "_endgame.dat", {})
    return dict(research_accounts=missions.get("research_accounts", []),
                stations=roots.get(NS + "_stations.dat", {}).get("stations", []),
                missions=missions.get("missions", []), instances=missions.get("instances", []),
                device_endpoints=endgame.get("endpoints", []), device_pairs=endgame.get("elevator_pairs", []),
                warp_energy=roots.get(NS + "_stations.dat", {}).get("warp_energy", []),
                rockets=[{k: row["value"].get(k) for k in ("UUID", "RocketEntityData")}
                         for row in state["entities"] if row["value"]["id"] == NS + ":rocket"])


def check_qualification(source, loaded):
    before, after = projection(source), projection(loaded)
    return {key: before[key] == after[key] for key in before}


def saved_uuids(value):
    """Decode UUID int arrays from read-only NBT projections, not a save mutation."""
    result = set()
    if isinstance(value, dict):
        for entry in value.values():
            result.update(saved_uuids(entry))
    elif isinstance(value, list):
        if len(value) == 4 and all(type(v) is int and -(2**31) <= v < 2**31 for v in value):
            result.add(str(uuid.UUID(int=sum((v & 0xffffffff) << ((3-i)*32) for i,v in enumerate(value)))))
        else:
            for entry in value:
                result.update(saved_uuids(entry))
    return result


def check_created_ids(made, state):
    # Only IDs explicitly returned by mutation commands; actors and requested members
    # do not imply memberships and are intentionally not treated as created records.
    text = json.dumps(made)
    created = set(re.findall(r"\b(?:id|mission|satellite|terminal|instance|station|source|destination|anchor|pair)=([a-f0-9-]{36})", text))
    created.update(re.findall(r'"(?:id|mission)":\s*"([a-f0-9-]{36})"', text))
    require(created <= saved_uuids(state), "Command-returned IDs missing from saved readback: " + str(sorted(created - saved_uuids(state))))
    return sorted(created)


def run(args):
    from scripts import v170_world_fixture as fixture
    from scripts import run_dedicated_server_smoke as server
    owner, member = actors(args.owner, args.member)
    require(args.lock_owner.startswith("W436 pid=") and
            (LOCK / "owner.txt").read_text(encoding="utf-8-sig").startswith(args.lock_owner), "W436 heavy-JVM lock required")
    require(min(shutil.disk_usage(p).free for p in ("C:/", "D:/")) >= 10 * 1024**3, "10 GiB free required")
    folder = args.output / args.fixture
    if args.mode == "build":
        jar, pin = host_pin(args.fixture, args.historical)
        source = args.fi04 if args.fixture == "FI05" else args.output / PREVIOUS[args.fixture] / "source-world"
        require(not (folder / "source-world").exists() and not (folder / "SHA256SUMS.txt").exists(), "Refusing to replace source")
        evidence = folder / "creation"
    else:
        jar, pin = args.c6, dict(version="v1.8.0", commit=C6_COMMIT, sha256=C6_HASH)
        require(digest(jar) == C6_HASH, "C6 JAR mismatch")
        source, evidence = folder / "source-world", folder / "qualification"
    verify_seal(source.parent)
    before = fixture.file_inventory(source)
    evidence.mkdir(parents=True, exist_ok=False)
    runtime = args.output / "_work" / (args.fixture + "-" + args.mode)
    require(not runtime.exists(), "Disposable server already exists")
    receipt = dict(status="FAIL", host=pin, console_commands=[], readbacks={}, hooks=args.mode == "build",
                   generator_sha256=digest(Path(__file__)), generator_commit=subprocess.check_output(
                       ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip())
    p = None
    try:
        fixture.prepare(args.template, runtime, jar, source)
        # The inherited capture function contains save/stop, but is never invoked.
        with (runtime / "server.properties").open("a", encoding="ascii") as stream:
            stream.write("function-permission-level=4\n")
        argfile = "win_args.txt" if sys.platform == "win32" else "unix_args.txt"
        command = [str(args.java), "-Xms512M", "-Xmx2G"]
        if args.mode == "build":
            command.append("-Dadvancedrocketrycommunity.releaseTestHooks=true")
        command += [f"@libraries/net/minecraftforge/forge/1.20.1-47.4.10/{argfile}", "nogui"]
        receipt.update(command=command, properties=(runtime / "server.properties").read_text())
        p = server.CapturedProcess(command, runtime, evidence / "console.log")
        p.wait_for(server.READY_MARKER, 240)
        port = int(server.parse_java_properties((runtime / "server.properties").read_bytes())["server-port"])
        status = ready_status(server, port, "1.20.1-" + pin["version"][1:] + "-dev")
        require(status.get("players", {}).get("online") == 0, "Unexpected player")
        receipt["server_status"] = status
        c = Console(p, receipt["console_commands"])
        receipt["first_load_report"] = c.query("arce beta data-report", r"ARCE-BETA-.*operational=")
        require("build=1.20.1-" + pin["version"][1:] + "-dev" in receipt["first_load_report"], "Host build identity differs")
        receipt["first_load_migration_lines"] = [line.strip() for line in p.lines if re.search(r"migrat|refus|quarantin", line, re.I)]
        # This checkpoint precedes all population commands. The host's migration is not bypassed.
        operational = "operational=true" in receipt["first_load_report"]
        if args.mode == "build":
            require(operational, "Historical host refused managed roots")
        if args.mode == "build":
            receipt["made"] = populate(c, args.fixture, owner, member)
        for label, command, pattern in (
            ("stations", "arce station admin dump", r"Station|station|ARCE_STATION"),
            ("satellites", "arce satellite admin evidence", r"ARCE_SATELLITE_EVIDENCE"),
            ("forced_overworld", "forceload query", r"force loaded|force load"),
            ("forced_space", f"execute in {NS}:space run forceload query", r"force loaded|force load")):
            if operational:
                receipt["readbacks"][label] = c.query(command, pattern)
        c.query("save-all flush", server.SAVE_MARKER.pattern)
        receipt["console_commands"].append("stop")
        p.command("stop")
        receipt["exit_code"] = p.finish()
        require(receipt["exit_code"] == 0, "Server did not exit cleanly")
        receipt["errors"] = [line.strip() for line in p.lines if re.search(r"/(ERROR|FATAL)\]", line)]
        receipt["warnings"] = [line.strip() for line in p.lines if "/WARN]" in line]
        state = snapshot(runtime / "world")
        write(evidence / "state.json", state)
        write(evidence / "files.json", fixture.file_inventory(runtime / "world"))
        require(before == fixture.file_inventory(source), "Source input changed")
        receipt["source_unchanged"] = True
        if args.mode == "qualify":
            saved = json.loads((folder / "source-state.json").read_text(encoding="utf-8"))
            receipt["authority_comparison"] = check_qualification(saved, state)
            receipt["status"] = "OBSERVATION" if not operational or receipt["errors"] or not all(receipt["authority_comparison"].values()) else "PASS"
        else:
            require(not receipt["errors"], "Historical host logged ERROR/FATAL")
            receipt["created_ids_verified"] = check_created_ids(receipt["made"], state)
            shutil.copytree(runtime / "world", folder / "source-world")
            write(folder / "source-state.json", state)
            write(folder / "source-files.json", fixture.file_inventory(folder / "source-world"))
            write(folder / "manifest.json", dict(fixture=args.fixture, source=PREVIOUS[args.fixture], host=pin,
                classification=f"reconstructed on JAR ({pin['sha256']}), not historically played",
                actors=dict(owner=owner, member=member, classification="disposable placeholders pending owner item O5"),
                source_inventory_sha256=digest(evidence / "files.json"), content=receipt["made"],
                parent_seal_sha256=digest(source.parent / "SHA256SUMS.txt"),
                not_covered=uncovered(args.fixture), serves=["E-10", {"FI05":"K07", "FI06":"K08", "FI07":"K09", "FI08":"K10"}[args.fixture]],
                inherited_coordinates=dict(paused=[0,100,0], running=[4,100,0], earth_rocket=[384,101,384], moon_rocket=[8,80,8]),
                forced_chunk_baseline=snapshot(source)["forced_chunks"], forced_chunks_after=state["forced_chunks"],
                first_load=receipt["first_load_report"], commands=receipt["console_commands"]))
            receipt["status"] = "PASS"
    except BaseException as exc:
        receipt["error"] = f"{type(exc).__name__}: {exc}"
        raise
    finally:
        if p is not None:
            p.abort()
            require(p.process.poll() is not None, "Owned JVM still alive; keep lock and server")
        if (runtime / "logs/debug.log").exists():
            shutil.copy2(runtime / "logs/debug.log", evidence / "debug.log")
        write(evidence / "receipt.json", receipt)
        require(runtime.resolve().is_relative_to((args.output / "_work").resolve()), "Cleanup outside own work area")
        if runtime.exists():
            shutil.rmtree(runtime)
    if args.mode == "build":
        seal(folder)
    else:
        verify_seal(folder)
        seal(evidence)
    return receipt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixture", choices=PINS, required=True)
    parser.add_argument("--mode", choices=("build", "qualify"), required=True)
    for name in ("output", "historical", "fi04", "c6", "template", "java"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--lock-owner", required=True)
    parser.add_argument("--owner", default=DEFAULT_OWNER)
    parser.add_argument("--member", default=DEFAULT_MEMBER)
    print(json.dumps(run(parser.parse_args()), indent=2))


if __name__ == "__main__":
    sys.path.insert(0, str(ROOT))
    main()
