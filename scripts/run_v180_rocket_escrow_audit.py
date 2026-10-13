#!/usr/bin/env python3
"""Cut a pending real rocket's relocation repair before/after persistence, then restart twice."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import shutil
import uuid

import run_dedicated_server_smoke as smoke
from run_v060_flight_server_smoke import _server_command
from run_v180_escrow_forced_stop import digest, ledger, record, LEDGER

CASES = ("RA_BEFORE_REPAIR", "RA_AFTER_REPAIR")


def inspect(world: Path, repaired: bool) -> dict:
    root = ledger(world / "data" / LEDGER)
    if len(root["records"]) != 1:
        raise smoke.SmokeError("Relocation discarded the unretired operation")
    op = root["records"][0]
    expected = ("REPAIR", "RELOCATED_ENDPOINT") if repaired else ("PREPARED", "NONE")
    if (op["phase"], op["reason"]) != expected:
        raise smoke.SmokeError("Wrong durable relocation state")
    entities = record(world / "entities/r.0.0.mca")["Entities"]
    rockets = [entry for entry in entities if "RocketEntityData" in entry]
    if len(rockets) != 1 or rockets[0]["UUID"] != op["destination"]["identity"]:
        raise smoke.SmokeError("Lost or duplicated real rocket identity")
    rocket = rockets[0]
    wrapper = rocket["RocketEntityData"]
    if (wrapper["schema_version"] != 3 or rocket["Pos"] != [66.5, 100.0, 64.5]
            or wrapper["flight_data"]["fuel"]["amount"] != 0
            or wrapper["resource_escrow"] != {"schema_version": 1, "resource_epoch": 0, "markers": []}
            or wrapper["assembly_transaction_id"] != op["destination"]["logical_rocket"]):
        raise smoke.SmokeError("The moved rocket's protected authority changed")
    sources = [entry for entry in record(world / "region/r.0.0.mca")["block_entities"]
               if [entry.get(key) for key in ("x", "y", "z")] == [64, 100, 64]]
    if len(sources) != 1 or sources[0]["ForgeData"]["arce_escrow_fixture"]["amount"] != 5000:
        raise smoke.SmokeError("Relocation mutated the source")
    files = ("data/" + LEDGER, "region/r.0.0.mca", "entities/r.0.0.mca")
    return dict(ledger=root, wrapper=wrapper, position=rocket["Pos"], source=5000, fuel=0,
                files={name: digest(world / name) for name in files})


def run(args) -> dict:
    session, evidence = args.session_dir.resolve(), args.evidence_dir.resolve()
    if session.exists() or session.is_symlink() or evidence == session or session in evidence.parents:
        raise smoke.SmokeError("Unsafe or occupied disposable session")
    if shutil.disk_usage(session.parent).free < 10 * 1024**3:
        raise smoke.SmokeError("Native cuts require 10 GiB free")
    libraries = args.source_server.resolve() / "libraries"
    if not libraries.is_dir() or any(path.is_symlink() for path in libraries.rglob("*")):
        raise smoke.SmokeError("Unsafe or missing Forge libraries")
    if not (libraries / "net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt").is_file():
        raise smoke.SmokeError("Missing pinned Forge arguments")
    session.mkdir(); evidence.mkdir(parents=True, exist_ok=True)
    token = uuid.uuid4().hex
    marker = session / ".arce-w11b2-audit-owned"
    marker.write_text(token, encoding="ascii")
    report = dict(status="IN_PROGRESS", source_commit=args.source_commit, jar_sha256=digest(args.jar), cases=[],
                  scope="Pending real rocket only; no credited reconciliation or operator settlement claim")
    process = None
    try:
        shutil.copytree(libraries, session / "libraries")
        (session / "mods").mkdir(); shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = smoke.allocate_port(); smoke.write_server_configuration(session, port, True)
        for cut in args.case or CASES:
            properties = smoke.expected_server_properties(port, True)
            properties.update({"level-name": "world-" + cut, "level-type": "minecraft:flat", "max-tick-time": "60000",
                               "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}], "biome": "minecraft:plains"})})
            (session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(properties.items())), encoding="ascii")
            row = dict(cut=cut, restarts=[]); report["in_progress"] = row
            for restart in range(3):
                command = _server_command(args.java); command[2] = "-Xmx4G"
                command.insert(1, "-Dadvancedrocketrycommunity.escrowScenario=" + ("recover" if restart else "start"))
                command.insert(1, "-Dadvancedrocketrycommunity.escrowCase=" + cut)
                log = evidence / f"{cut}-restart-{restart}.log"
                process = smoke.CapturedProcess(command, session, log)
                row.setdefault("commands", []).append(command)
                if restart == 0:
                    process.wait_for(re.compile(r"ARCE_RELEASE_ROCKET_AUDIT_PAUSED cut=" + cut), args.startup_timeout)
                    process.process.kill(); process.abort(); process = None
                    row["cut_disk"] = inspect(session / ("world-" + cut), cut == "RA_AFTER_REPAIR")
                else:
                    process.wait_for(re.compile(r"ARCE_RELEASE_ROCKET_AUDIT_RESULT records=1 reason=RELOCATED_ENDPOINT fuel=0 x=66\.5"), args.startup_timeout)
                    process.command("stop"); code = process.finish()
                    if code != 0 or any("/ERROR]" in line or "/FATAL]" in line for line in process.lines):
                        raise smoke.SmokeError(f"Native restart failed or logged ERROR: {cut}/{restart}")
                    process = None
                    disk = inspect(session / ("world-" + cut), True)
                    if disk["wrapper"] != row["cut_disk"]["wrapper"]:
                        raise smoke.SmokeError("Restart changed the protected real-rocket wrapper")
                    row["restarts"].append(dict(restart=restart, exit_code=code, disk=disk))
            row["status"] = "PASS"; report["cases"].append(row)
            print(f"{cut}: PASS (hard stop, two restarts, real rocket, retained pending authority)", flush=True)
        report.pop("in_progress", None); report["status"] = "PASS"
        return report
    except Exception as failure:
        report.update(status="FAILED", error=str(failure)); raise
    finally:
        if process is not None:
            process.abort()
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
    try:
        run(args); return 0
    except (OSError, smoke.SmokeError) as failure:
        print(f"FAIL: {failure}"); return 1


if __name__ == "__main__":
    raise SystemExit(main())
