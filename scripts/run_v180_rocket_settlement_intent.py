#!/usr/bin/env python3
"""Cut O1-only real-rocket intents before replacement/after owner acknowledgement; restart twice."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import uuid

import run_dedicated_server_smoke as smoke
from run_v060_flight_server_smoke import _server_command
from run_v180_escrow_forced_stop import digest, ledger, record, LEDGER

CASES = ("SO_ACCEPT_BEFORE", "SO_ACCEPT_AFTER", "SO_ABANDON_BEFORE", "SO_ABANDON_AFTER",
         "SO_CHAIN_BEFORE", "SO_CHAIN_AFTER")
_ACTOR = uuid.UUID(bytes=hashlib.md5(b"arce:escrow:server-console").digest(), version=3).int
ACTOR = [(((_ACTOR >> shift) & 0xffffffff) + 2**31) % 2**32 - 2**31 for shift in (96, 64, 32, 0)]


def inspect(world: Path, cut: str) -> dict:
    root = ledger(world / "data" / LEDGER)
    count = 2 if cut.startswith("SO_CHAIN_") else 1
    frozen = cut.endswith("_AFTER")
    abandon = cut.startswith("SO_ABANDON_")
    if len(root["records"]) != count or root["floors"] or root["tombstones"]:
        raise smoke.SmokeError("O1 lost records or performed an unproved retirement")
    records = root["records"]
    if any(op["kind"] != "FLUID_PROPULSION_FILL" for op in records):
        raise smoke.SmokeError("O1 changed the admitted operation kind")
    if frozen:
        intents = [op.get("settlement") for op in records]
        if not intents[0] or any(intent != intents[0] for intent in intents):
            raise smoke.SmokeError("Connected records do not share an exact frozen intent")
        intent = intents[0]
        choice = "ABANDON" if abandon else "ACCEPT_CURRENT"
        expected_phase = "SETTLING_ABANDON" if abandon else "SETTLING_ACCEPT"
        if (intent["choice"] != choice or intent["ledger_id"] != root["ledger_id"]
                or intent["actor_name"] != "Server" or intent["actor"] != ACTOR
                or not re.fullmatch(r"[0-9a-f]{64}", intent["digest"])
                or {tuple(member["op_id"]) for member in intent["members"]} != {tuple(op["op_id"]) for op in records}
                or any(op["phase"] != expected_phase for op in records)):
            raise smoke.SmokeError("Wrong O1 choice, component, actor or phase")
        missing = sum(observation["missing"] for observation in intent["observations"])
        if missing != int(abandon) or len(intent["observations"]) != count + 1:
            raise smoke.SmokeError("O1 did not freeze every survivor/missing observation")
        if (intent["confirmed_tick"] < intent["preview_tick"]
                or abandon and intent["confirmed_tick"] - intent["preview_tick"] < 200):
            raise smoke.SmokeError("O1 used an unconfirmed missing observation")
        if any(observation.get("quantity") != (0 if observation["endpoint"]["kind"] == "ENTITY" or observation["missing"] else 5000)
               for observation in intent["observations"]):
            raise smoke.SmokeError("Wrong frozen resource quantities")
    elif (any("settlement" in op for op in records) or records[0]["phase"] != "REPAIR"
          or records[0]["reason"] != "OTHER_STATE" or any(op["phase"] != "PREPARED" for op in records[1:])):
        raise smoke.SmokeError("Pre-replacement cut invented an operator decision")
    entities = record(world / "entities/r.0.0.mca")["Entities"]
    rockets = [entry for entry in entities if "RocketEntityData" in entry]
    if len(rockets) != 1:
        raise smoke.SmokeError("Lost or duplicated the protected real rocket")
    rocket = rockets[0]
    wrapper = rocket["RocketEntityData"]
    if (rocket["UUID"] != records[0]["destination"]["identity"] or wrapper["schema_version"] != 3
            or wrapper["assembly_transaction_id"] != records[0]["destination"]["logical_rocket"]
            or wrapper["flight_data"]["fuel"]["amount"] != 0 or rocket["Pos"] != [64.5, 100., 64.5]
            or wrapper["resource_escrow"] != {"schema_version": 1, "resource_epoch": 0, "markers": []}):
        raise smoke.SmokeError("O1 mutated protected rocket resources, identity or placement")
    sources = [entry for entry in record(world / "region/r.0.0.mca")["block_entities"]
               if entry.get("ForgeData", {}).get("arce_escrow_fixture")]
    if len(sources) != (0 if abandon else count) or any(entry["ForgeData"]["arce_escrow_fixture"]["amount"] != 5000 for entry in sources):
        raise smoke.SmokeError("Wrong surviving source authority")
    return dict(ledger=root, wrapper=wrapper, source_count=len(sources), fuel=0,
                files={name: digest(world / name) for name in ("data/" + LEDGER, "region/r.0.0.mca", "entities/r.0.0.mca")})


def run(args) -> dict:
    session, evidence = args.session_dir.resolve(), args.evidence_dir.resolve()
    if session.exists() or session.is_symlink() or evidence == session or session in evidence.parents:
        raise smoke.SmokeError("Unsafe or occupied disposable session")
    libraries = args.source_server.resolve() / "libraries"
    if (not libraries.is_dir() or any(path.is_symlink() for path in libraries.rglob("*"))
            or not (libraries / "net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt").is_file()):
        raise smoke.SmokeError("Unsafe or missing pinned Forge libraries")
    for drive in (Path("C:/"), Path("D:/")):
        if shutil.disk_usage(drive).free < 10 * 1024**3:
            raise smoke.SmokeError("Native cuts require 10 GiB free on C: and D:")
    session.mkdir(); evidence.mkdir(parents=True, exist_ok=True)
    token = uuid.uuid4().hex
    marker = session / ".arce-w11b2-settlement-owned"
    marker.write_text(token, encoding="ascii")
    report = dict(status="IN_PROGRESS", source_commit=args.source_commit, jar_sha256=digest(args.jar), cases=[],
                  scope="O1 ONLY; no O2-O4, durable absence, tombstone, journal removal, release or full settlement claim")
    process = None
    try:
        shutil.copytree(libraries, session / "libraries")
        (session / "mods").mkdir(); shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = smoke.allocate_port(); smoke.write_server_configuration(session, port, True)
        for cut in args.case or CASES:
            properties = smoke.expected_server_properties(port, True)
            properties.update({"level-name": "world-" + cut, "level-type": "minecraft:flat", "max-tick-time": "60000",
                               "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}], "biome": "minecraft:plains"})})
            (session / "server.properties").write_text("".join(f"{key}={value}\n" for key, value in sorted(properties.items())), encoding="ascii")
            row = dict(cut=cut, restarts=[]); report["in_progress"] = row
            for restart in range(3):
                free = {str(drive): shutil.disk_usage(drive).free for drive in (Path("C:/"), Path("D:/"))}
                if min(free.values()) < 10 * 1024**3:
                    raise smoke.SmokeError("Native start refused below 10 GiB free")
                command = _server_command(args.java); command[2] = "-Xmx4G"
                command.insert(1, "-Dadvancedrocketrycommunity.escrowScenario=" + ("recover" if restart else "start"))
                command.insert(1, "-Dadvancedrocketrycommunity.escrowCase=" + cut)
                process = smoke.CapturedProcess(command, session, evidence / f"{cut}-restart-{restart}.log")
                row.setdefault("starts", []).append(dict(command=command, free_bytes=free, restart=restart))
                if restart == 0:
                    process.wait_for(re.compile(r"ARCE_RELEASE_SETTLEMENT_PAUSED cut=" + cut), args.startup_timeout)
                    process.process.kill(); process.abort(); process = None
                    row["cut_disk"] = inspect(session / ("world-" + cut), cut)
                else:
                    process.wait_for(re.compile(r"ARCE_RELEASE_SETTLEMENT_RESULT cut=" + cut + r" .*locked=true scope=O1_ONLY"), args.startup_timeout)
                    process.command("stop"); code = process.finish()
                    if code != 0 or any("/ERROR]" in line or "/FATAL]" in line for line in process.lines):
                        raise smoke.SmokeError(f"Native restart failed or logged ERROR: {cut}/{restart}")
                    process = None; disk = inspect(session / ("world-" + cut), cut)
                    if disk["wrapper"] != row["cut_disk"]["wrapper"] or disk["ledger"]["records"] != row["cut_disk"]["ledger"]["records"]:
                        raise smoke.SmokeError("Restart changed the frozen decision or protected rocket")
                    row["restarts"].append(dict(restart=restart, exit_code=code, disk=disk))
            row["status"] = "PASS"; report["cases"].append(row)
            print(f"{cut}: PASS (O1 only, forced stop, two restarts, retained locks)", flush=True)
        report.pop("in_progress", None); report["status"] = "PASS"; return report
    except Exception as failure:
        report.update(status="FAILED", error=str(failure)); raise
    finally:
        if process is not None: process.abort()
        (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        if (session.exists() and not session.is_symlink() and marker.is_file() and not marker.is_symlink()
                and marker.read_text(encoding="ascii") == token):
            shutil.rmtree(session)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_server", type=Path)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--session-dir", type=Path, required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--case", action="append", choices=CASES)
    parser.add_argument("--startup-timeout", type=float, default=240)
    args = parser.parse_args()
    try: run(args); return 0
    except (OSError, smoke.SmokeError) as failure: print(f"FAIL: {failure}"); return 1


if __name__ == "__main__":
    raise SystemExit(main())
