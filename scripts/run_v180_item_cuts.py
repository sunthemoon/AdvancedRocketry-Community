#!/usr/bin/env python3
"""W143: D-S1/D-S2, pinned source, exact typed native readback, forced stop and two restarts."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import re
import shutil
import uuid
import zipfile
import run_v180_cargo_reconciliation as native
import run_v180_cargo_cuts as fluid
import item_cuts_oracle as oracle
import item_cut_fixture as fixture
from run_v060_flight_server_smoke import _server_command
from run_v180_rocket_settlement_completion import require_progress

REPO = Path(__file__).resolve().parents[1]
NORMAL = (("K2", "L1_REPLACED", "K2"), ("K3", "C2_DESTINATION_AFTER", "C2_DESTINATION_AFTER"),
          ("K4", "SOURCE_ONLY_STORE", "BARRIER_FORCE_AFTER"), ("K5", "DESTINATION_ONLY_STORE", "BARRIER_FORCE_AFTER"),
          ("K9", "L2_REPLACED", "K9"))
CASES = {f"IC_{direction}_{cut}": dict(family="D-S1", probe=f"IC_{direction}_{probe}", boundary=checkpoint)
         for direction in ("LOAD", "UNLOAD") for cut, probe, checkpoint in NORMAL}
# Dedicated repeats explicitly observe the partial 10 -> 6 remainder against a matching 60 -> 64 stack.
CASES.update(IC_PARTIAL_APPLY=dict(family="D-S1", probe="IC_LOAD_C2_DESTINATION_AFTER", boundary="C2_DESTINATION_AFTER"),
             IC_PARTIAL_STORE=dict(family="D-S1", probe="IC_UNLOAD_SOURCE_ONLY_STORE", boundary="BARRIER_FORCE_AFTER"))
CASES.update({name: dict(family="D-S2", probe=name, boundary="BEFORE_OFFLINE_DAMAGE") for name in fixture.CASES})
for name in oracle.CASES:
    boundary = name[3:]
    boundary = {"BIND_AFTER": "O_BIND_AFTER", "BIND_VETO": "NORMALIZATION_VETO", "O3J_AFTER": "O3J_REPLACED",
                "ABANDON_ROCKET": "O1_AFTER", "ABANDON_DEVICE": "O1_AFTER"}.get(boundary, boundary)
    CASES[name] = dict(family="D-S2", probe=name, boundary=boundary)
CASES.update(IR_DISASSEMBLY=dict(family="D-S2", probe="IR_DISASSEMBLY", boundary="K1"),
             IR_REBUILD=dict(family="D-S2", probe="IR_REBUILD", boundary="REBUILD_REFUSED"),
             IR_ROOT=dict(family="D-S2", probe="IR_ROOT", boundary="ROOT_PRESERVED"))
HOOKS = fluid.HOOKS + tuple(fluid.HOOK_DIR + name + ".java" for name in (
    "EscrowItemCutProbe", "EscrowItemSettlementCuts", "EscrowItemRecoveryCuts", "EscrowItemCutLaunch", "EscrowItemDisassemblyRequest")) + (
    "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/flight/persistence/RocketTransferSavedData.java",)
HARNESS = fluid.HARNESS + ("scripts/run_v180_item_cuts.py", "scripts/item_cuts_oracle.py", "scripts/item_cut_fixture.py")
require = oracle.require

def marker(case, restart):
    spec = CASES[case]
    if spec["family"] == "D-S1":
        return ("ARCE_RELEASE_CARGO_RESULT" if restart else "ARCE_RELEASE_CARGO_PAUSED") + " cut=" + spec["probe"] + " "
    if not restart and case == "IR_DISASSEMBLY":
        return "ARCE_LANDED_DISASSEMBLY_PAUSED boundary=K1 "
    return ("ARCE_RELEASE_ITEM_CUT_RESULT" if restart else "ARCE_RELEASE_ITEM_CUT_PAUSED") + " cut=" + case + " "

def observations(lines, case, restart):
    matches = [line for line in lines if marker(case, restart) in line]
    require(len(matches) == 1, "Missing or repeated native cut observation")
    if not restart:
        key = "checkpoint" if CASES[case]["family"] == "D-S1" else "boundary"
        require(key + "=" + CASES[case]["boundary"] in matches[0], "Wrong forced-stop boundary")
        probe = CASES[case]["probe"]
        for role in ("SOURCE", "DESTINATION"):
            if probe.endswith(role + "_ONLY_STORE"):
                require("selected=" + role in matches[0], "Wrong selected one-sided store")
    require(not any("/ERROR]" in line or "/FATAL]" in line for line in lines), "Native run logged ERROR/FATAL")
    returns = [int(match[1]) for line in lines if (match := re.search(r"ARCE_RELEASE_CARGO_DISASSEMBLY_RETURN cut=IR_DISASSEMBLY code=(-?\d+)", line))]
    require(all(code == 1 for code in returns), "Production disassembly refused consent")
    if case == "IR_DISASSEMBLY" and not restart:
        require(returns == [1], "Missing real production disassembly return")

def check(world, case, restarted):
    if CASES[case]["family"] == "D-S1":
        result = native.inspect(world, CASES[case]["probe"], restarted)
        load = CASES[case]["probe"].startswith("IC_LOAD_")
        root = result["ledger"]
        require(not root["rocket_authorities"] and not root["armed_flights"] and root["issued_launch_serial"] == 0,
                "Ordinary transfer acquired flight authority")
        block = result["rocket"]["snapshot"]["relative_blocks"][0]["block_entity"]["data"]
        oracle.exact_stack(block["Items"]); oracle.exact_stack(result["device"]["buffer"]["slots"], True)
        for endpoint, role in ((result["rocket"], "DESTINATION" if load else "SOURCE"),
                               (result["device"], "SOURCE" if load else "DESTINATION")):
            state = endpoint["resource_escrow"]
            if restarted or state["resource_epoch"] == 0:
                require(not state["markers"], "Neutral/retired endpoint retained markers")
            elif root["records"]:
                op = root["records"][0]
                require(len(state["markers"]) == 1 and state["markers"][0]["op_id"] == op["op_id"]
                        and state["markers"][0]["seq"] == op["seq"] == 1 and state["markers"][0]["epoch_after"] == 1
                        and state["markers"][0]["role"] == role, "Applied marker differs from operation")
        return result
    result = oracle.inspect(world, case)
    if case in oracle.CASES:
        oracle.check_settlement(case, result, restarted)
    else:
        oracle.check_recovery(case, result, restarted)
    return result

def retained_files(world, destination):
    """Lossless native files preserve all NBT tag types even if an oracle rejects the decoded observation."""
    files = [p for folder in ("data", "entities", "region", "arce_w143_fixture")
             for p in (world / folder).glob("*") if p.is_file() and p.suffix in (".dat", ".mca", ".mcc")]
    manifest = {}
    with zipfile.ZipFile(destination, "x", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in files:
            relative = path.relative_to(world).as_posix(); archive.write(path, relative)
            manifest[relative] = dict(bytes=path.stat().st_size, sha256=native.digest(path))
    with zipfile.ZipFile(destination) as archive:
        import hashlib
        require(all(hashlib.sha256(archive.read(name)).hexdigest() == info["sha256"] for name, info in manifest.items()), "Native archival mismatch")
    return dict(file=destination.name, sha256=native.digest(destination), members=manifest)

def run(args):
    native.assert_pin(args.source_commit); native.require_lock(args.lock_owner); native.preflight()
    session, evidence = args.session_dir.resolve(), args.evidence_dir.resolve()
    require(not session.exists() and not evidence.exists() and session != evidence and session not in evidence.parents and evidence not in session.parents,
            "Existing or overlapping output")
    libraries = args.source_server.resolve() / "libraries"
    require(libraries.is_dir() and not any(p.is_symlink() for p in libraries.rglob("*")), "Unsafe/missing libraries")
    session.mkdir(parents=True); evidence.mkdir(parents=True)
    token = uuid.uuid4().hex; owner = session / ".w143-owned"; owner.write_text(token, encoding="ascii")
    pins = dict(source_commit=args.source_commit, jar_sha256=native.digest(args.jar), hooks={p: native.digest(REPO / p) for p in HOOKS + HARNESS})
    report = dict(status="IN_PROGRESS", pins=pins, cases=[], cleanup=False); process = None
    try:
        shutil.copytree(libraries, session / "libraries"); (session / "mods").mkdir(); shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = native.smoke.allocate_port(); native.smoke.write_server_configuration(session, port, True)
        for case in args.case:
            spec = CASES[case]; row = dict(case=case, **spec, pins=pins, starts=[], findings=[]); report["cases"].append(row)
            properties = native.smoke.expected_server_properties(port, True)
            properties.update({"level-name": "world-" + case, "level-type": "minecraft:flat", "max-tick-time": "60000",
                               "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}], "biome": "minecraft:plains"})})
            (session / "server.properties").write_text("".join(f"{key}={value}\n" for key, value in sorted(properties.items())), encoding="ascii")
            frozen = None
            for restart in range(3):
                native.assert_pin(args.source_commit); native.require_lock(args.lock_owner)
                require(native.digest(args.jar) == pins["jar_sha256"], "JAR changed")
                command = _server_command(args.java); command[2] = "-Xmx4G"
                command[1:1] = ["-Dadvancedrocketrycommunity.escrowScenario=" + ("recover" if restart else "start"),
                                "-Dadvancedrocketrycommunity.escrowCase=" + spec["probe"]]
                if not restart and case == "IR_DISASSEMBLY":
                    command.insert(1, "-Dadvancedrocketrycommunity.landedDisassemblyProbe=LANDED_DISASSEMBLY:K1")
                start = dict(restart=restart, command=command, preflight=native.preflight()); row["starts"].append(start)
                process = native.smoke.CapturedProcess(command, session, evidence / f"{case}-{restart}.log")
                try:
                    process.wait_for(re.compile(re.escape(marker(case, restart))), args.startup_timeout)
                    if restart:
                        process.command("stop"); start["exit_code"] = process.finish()
                    else:
                        process.process.kill(); process.abort(); start["forced_stop"] = True
                except native.smoke.SmokeError as failure:
                    start["process_error"] = str(failure); process.abort()
                lines = process.lines; process = None; world = session / ("world-" + case)
                try:
                    if not restart and case in fixture.CASES:
                        start["pre_edit"] = retained_files(world, evidence / f"{case}-pre-edit.zip")
                        start["edit"] = fixture.edit(world, case, evidence / (case + "-edit"), start, owner, token)
                    start["native"] = retained_files(world, evidence / f"{case}-{restart}-native.zip")
                    raw = evidence / f"{case}-{restart}-raw.json"; raw.write_text(json.dumps(oracle.read(world), indent=2), encoding="utf-8")
                    start["raw"] = dict(file=raw.name, sha256=native.digest(raw))
                    require("process_error" not in start, start.get("process_error", "Native process failed"))
                    observations(lines, case, restart); require(not restart or start["exit_code"] == 0, "Restart failed")
                    disk = check(world, case, bool(restart)); start["disk"] = disk
                    if restart and frozen is not None:
                        if spec["family"] == "D-S1":
                            require(disk["identity"] == frozen["identity"], "Restart changed ordinary identities")
                        elif case in fixture.CASES or case == "IR_ROOT":
                            require(native.same_typed(oracle.preservation(disk), oracle.preservation(frozen)), "Restart overwrote held typed roots")
                        elif case in oracle.CASES:
                            old = frozen["ledger"]
                            require_progress(old, disk["ledger"])
                            digest = old["records"][0]["settlement"]["digest"] if old["records"] else old["tombstones"][0]["decision_digest"]
                            require(disk["ledger"]["tombstones"][0]["decision_digest"] == digest, "Restart changed frozen operator choice")
                            for endpoint in ("rocket", "device"):
                                previous, current = frozen[endpoint], disk[endpoint]
                                require((previous is None) == (current is None), "Restart changed survivors")
                                if previous is not None:
                                    keys = ("logical", "owner", "id") if endpoint == "device" else ("logical", "owner") if case == "IS_O3_AFTER" else ("logical", "owner", "uuid")
                                    require(all(previous[key] == current[key] for key in keys), "Restart changed survivor identity")
                            if case == "IS_O3_AFTER" and restart == 1:
                                proof = [line for line in lines if "ARCE_RELEASE_ITEM_CUT_PROOF cut=IS_O3_AFTER " in line]
                                require(all(any("checkpoint=" + label in line and "digest=" + digest in line for line in proof)
                                            for label in ("O2_AFTER_0", "O2_AFTER_1", "O3J_AFTER", "O4_CAPTURE")), "O3 restart lacks exact reacquired proofs")
                    if not restart: frozen = disk
                    start["status"] = "PASS"
                except (OSError, ValueError, KeyError, native.smoke.SmokeError) as failure:
                    start.update(status="FAILED", error=str(failure)); row["findings"].append(str(failure))
                (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
            row["status"] = "FAILED" if row["findings"] else "PASS"
            require(not row["findings"], "First contradictory cut: " + case + ": " + "; ".join(row["findings"]))
            print(case + ": PASS (forced stop and two restarts)", flush=True)
        report["status"] = "PASS"
    except Exception as failure:
        report.update(status="FAILED", error=str(failure)); raise
    finally:
        if process is not None: process.abort()
        if not session.is_symlink() and owner.is_file() and owner.read_text(encoding="ascii") == token:
            shutil.rmtree(session); report["cleanup"] = not session.exists()
        (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")

def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument("source_server", type=Path)
    for key in ("jar", "session-dir", "evidence-dir"): parser.add_argument("--" + key, type=Path, required=True)
    for key in ("source-commit", "java", "lock-owner"): parser.add_argument("--" + key, required=True)
    parser.add_argument("--case", action="append", choices=CASES, required=True); parser.add_argument("--startup-timeout", type=float, default=240)
    try: run(parser.parse_args()); return 0
    except (OSError, ValueError, native.smoke.SmokeError) as failure: print("FAIL: " + str(failure)); return 1

if __name__ == "__main__": raise SystemExit(main())
