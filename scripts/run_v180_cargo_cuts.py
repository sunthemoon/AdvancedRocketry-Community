#!/usr/bin/env python3
"""Fluid cargo C-S cuts on pinned source; stop at the first contradictory cut, retaining both restarts."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import shutil
import uuid

import run_v180_cargo_reconciliation as native
import cargo_storage_oracle as storage
from run_v060_flight_server_smoke import _server_command

REPO = Path(__file__).resolve().parents[1]
# (design cut, probe case suffix, checkpoint, raw-oracle boundary). K0 is preparation, not admission.
NORMAL = (
    ("K0", "PREPARING", "PREPARING", "PREPARING"),
    ("K1", "L1_BEFORE", "K1", "L1_BEFORE"),
    ("K2", "L1_REPLACED", "K2", "L1_REPLACED"),
    ("K2_ACK", "L1_ACK", "L1_ACK", "L1_ACK"),
    ("K3_DEBIT", "C2_SOURCE_AFTER", "C2_SOURCE_AFTER", "C2_SOURCE_AFTER"),
    ("K3", "C2_DESTINATION_AFTER", "C2_DESTINATION_AFTER", "C2_DESTINATION_AFTER"),
    ("K4", "SOURCE_ONLY_STORE", "BARRIER_FORCE_AFTER", "SOURCE_ONLY_STORE"),
    ("K5", "DESTINATION_ONLY_STORE", "BARRIER_FORCE_AFTER", "DESTINATION_ONLY_STORE"),
    ("K6", "SECOND_FORCE_BEFORE", "BARRIER_FORCE_BEFORE", "C6_JOINT_READBACK"),
    ("K7", "C6_JOINT_READBACK", "C6_JOINT_READBACK", "C6_JOINT_READBACK"),
    ("K8", "K8", "K8", "C6_JOINT_READBACK"),
    ("K9", "L2_REPLACED", "K9", "L2_REPLACED"),
    ("K10_ACK", "C7_ACKNOWLEDGED", "C7_ACKNOWLEDGED", "L2_REPLACED"),
    ("K10", "C8_RELEASED", "C8_RELEASED", "C8_RELEASED"),
)
CASES = {f"FC_{direction}_{suffix}": dict(family="C-S1", cut=cut, direction=direction,
          checkpoint=checkpoint, oracle=f"FC_{direction}_{oracle}")
         for direction in ("LOAD", "UNLOAD") for cut, suffix, checkpoint, oracle in NORMAL}
D2 = (("C2", "C2_DESTINATION_AFTER", "C2_DESTINATION_AFTER"),
      ("C5", "BARRIER_FORCE_AFTER", "C6_JOINT_READBACK"), ("C6", "C6_JOINT_READBACK", "C6_JOINT_READBACK"),
      ("C7", "K9", "L2_REPLACED"))
CASES.update({f"FC_{direction}_D2_{cut}": dict(family="C-S2", cut="D2-" + cut, direction=direction,
              checkpoint=checkpoint, oracle=f"FC_{direction}_{oracle}")
              for direction in ("LOAD", "UNLOAD") for cut, checkpoint, oracle in D2})
for cut, checkpoint, oracle, reason in (
        ("E1_OWN_STORE", "E1_OWN_STORE", "storage", "BARRIER_IO"),
        ("E2_CAPTURE", "E2_CAPTURE", "storage", "SUPERSEDING_SAVE"),
        ("E2_ROCKET_EXTERNAL", "E2_ROCKET_EXTERNAL", "storage", "BARRIER_EXTERNAL"),
        ("E4L1_FAIL", "WRITE_FAILED_BEFORE_REPLACE", "FC_LOAD_L1_BEFORE", None),
        ("E4L1_UNKNOWN", "WRITE_UNKNOWN", "FC_LOAD_L1_BEFORE", None),
        ("E4L2_UNKNOWN", "WRITE_UNKNOWN", "FC_LOAD_C6_JOINT_READBACK", None)):
    CASES["FC_LOAD_" + cut] = dict(family="C-S2", cut=cut, direction="LOAD", checkpoint=checkpoint, oracle=oracle, reason=reason)
HOOK_DIR = "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/"
HOOKS = tuple(HOOK_DIR + name + ".java" for name in (
    "EscrowCargoReconciliationProbe", "EscrowCargoProbeDevice", "EscrowRuntime", "EscrowTestEndpoints",
    "EscrowLedgerWriter", "EscrowResourceQueue", "EscrowReconciliationDriver", "EscrowReconciliationAction",
    "EscrowReconciliationPruning", "EscrowBarrier", "EscrowCargoStorageProbe", "EscrowStoreFault"))
HARNESS = ("scripts/run_v180_cargo_cuts.py", "scripts/run_v180_cargo_reconciliation.py",
           "scripts/run_v180_escrow_forced_stop.py", "scripts/run_v060_flight_server_smoke.py",
           "scripts/cargo_storage_oracle.py", "scripts/loader_cut_nbt.py")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise native.smoke.SmokeError(message)


def validate_markers(disk: dict, case: str, restarted: bool) -> None:
    """Every persisted applied endpoint retains its exact marker until L2, never just an equal total."""
    root, device, rocket = disk["ledger"], disk["device"], disk["rocket"]
    load = CASES[case]["direction"] == "LOAD"
    payload = next(block["block_entity"]["data"]["tank"]["fluid"]
                   for block in rocket["snapshot"]["relative_blocks"] if "block_entity" in block)
    for fluid in (payload, device["tank"]):
        require(not fluid or fluid.get("FluidName") == "minecraft:water" and not fluid.get("Tag"),
                "Native cargo changed fluid identity or metadata")
    require(device["role"] == ("LOAD" if load else "UNLOAD"), "Native device changed its role")
    require(not root["rocket_authorities"] and not root["armed_flights"] and root["issued_launch_serial"] == 0,
            "Ordinary cargo acquired flight authority")
    op = root["records"][0] if root["records"] else None
    for section, role in ((rocket["resource_escrow"], "DESTINATION" if load else "SOURCE"),
                          (device["resource_escrow"], "SOURCE" if load else "DESTINATION")):
        markers, epoch = section["markers"], section["resource_epoch"]
        if restarted or epoch == 0:
            require(not markers, "Neutral or retired endpoint retained a marker")
        elif op:
            require(root["issued_through"] == 1 and op["seq"] == 1 and len(markers) == 1,
                    "Applied endpoint lost its one issued marker")
            marker = markers[0]
            require(marker["op_id"] == op["op_id"] and marker["seq"] == 1
                    and marker["role"] == role and marker["epoch_after"] == 1,
                    "Applied endpoint marker differs from its durable operation")
        else:
            require(len(markers) <= 1 and all(m["seq"] == 1 and m["role"] == role and m["epoch_after"] == 1 for m in markers),
                    "Post-L2 endpoint retained an unrelated marker")
        if epoch:
            require(section.get("ledger_id") == root["ledger_id"], "Applied endpoint changed ledger binding")


def inspect(world: Path, case: str, restarted: bool) -> dict:
    result = storage.inspect(world, case, restarted) if CASES[case]["oracle"] == "storage" else native.inspect(world, CASES[case]["oracle"], restarted)
    validate_markers(result, case, restarted and case != "FC_LOAD_E2_ROCKET_EXTERNAL")
    return result


def observations(lines: list[str], case: str, restart: int) -> None:
    prefix = "ARCE_RELEASE_CARGO_RESULT" if restart else "ARCE_RELEASE_CARGO_PAUSED"
    matches = [line for line in lines if prefix + " cut=" + case + " " in line]
    require(len(matches) == 1, "Missing/repeated cargo boundary observation")
    if not restart:
        require("checkpoint=" + CASES[case]["checkpoint"] + " " in matches[0], "Wrong cargo stop boundary")
        if CASES[case]["cut"] in ("K4", "K5"):
            require("selected=" + ("SOURCE" if CASES[case]["cut"] == "K4" else "DESTINATION") in matches[0],
                    "Wrong selected native store")
    reason = CASES[case].get("reason")
    if reason and (not restart or case == "FC_LOAD_E2_ROCKET_EXTERNAL"):
        proof = matches if restart else [line for line in lines if "ARCE_CARGO_STORAGE_PROOF cut=" + case + " " in line]
        require(len(proof) == 1 and "reason=" + reason + " " in proof[0], "Storage proof reported a different refusal")
    require(not any("/ERROR]" in line or "/FATAL]" in line for line in lines), "Native run logged ERROR/FATAL")


def raw_snapshot(world: Path) -> dict:
    return storage.read(world)


def run(args) -> None:
    native.assert_pin(args.source_commit); native.require_lock(args.lock_owner); native.preflight()
    session, evidence = args.session_dir.resolve(), args.evidence_dir.resolve()
    require(not session.exists() and not evidence.exists() and session != evidence
            and session not in evidence.parents and evidence not in session.parents, "Existing or overlapping output")
    libraries = args.source_server.resolve() / "libraries"
    require(libraries.is_dir() and not any(p.is_symlink() for p in libraries.rglob("*")), "Unsafe/missing template libraries")
    session.mkdir(parents=True); evidence.mkdir(parents=True)
    token = uuid.uuid4().hex; sentinel = session / ".w138-owned"; sentinel.write_text(token, encoding="ascii")
    pins = dict(source_commit=args.source_commit, jar_sha256=native.digest(args.jar),
                hooks={path: native.digest(REPO / path) for path in HOOKS + HARNESS})
    report = dict(status="IN_PROGRESS", cases=[], pins=pins, cleanup=False)
    process = None
    try:
        shutil.copytree(libraries, session / "libraries")
        (session / "mods").mkdir(); shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = native.smoke.allocate_port(); native.smoke.write_server_configuration(session, port, True)
        for case in args.case:
            row = dict(case=case, **CASES[case], pins=pins, starts=[], findings=[])
            report["cases"].append(row)
            properties = native.smoke.expected_server_properties(port, True)
            properties.update({"level-name": "world-" + case, "level-type": "minecraft:flat", "max-tick-time": "60000",
                               "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}], "biome": "minecraft:plains"})})
            (session / "server.properties").write_text("".join(f"{key}={value}\n" for key, value in sorted(properties.items())), encoding="ascii")
            for restart in range(3):
                native.assert_pin(args.source_commit); native.require_lock(args.lock_owner)
                require(native.digest(args.jar) == pins["jar_sha256"], "JAR changed")
                command = _server_command(args.java); command[2] = "-Xmx4G"
                command[1:1] = ["-Dadvancedrocketrycommunity.escrowScenario=" + ("recover" if restart else "start"),
                                "-Dadvancedrocketrycommunity.escrowCase=" + case]
                start = dict(restart=restart, preflight=native.preflight(), command=command); row["starts"].append(start)
                process = native.smoke.CapturedProcess(command, session, evidence / f"{case}-{restart}.log")
                prefix = "ARCE_RELEASE_CARGO_RESULT" if restart else "ARCE_RELEASE_CARGO_PAUSED"
                process.wait_for(re.compile(prefix + " cut=" + case + " "), args.startup_timeout)
                if restart:
                    process.command("stop"); start["exit_code"] = process.finish()
                else:
                    process.process.kill(); process.abort(); start["forced_stop"] = True
                lines = process.lines; process = None
                world = session / ("world-" + case)
                # Retain independently decoded raw data even when the oracle rejects it.
                raw = evidence / f"{case}-{restart}-raw.json"
                raw.write_text(json.dumps(raw_snapshot(world), indent=2), encoding="utf-8")
                start["raw"] = dict(file=raw.name, sha256=native.digest(raw))
                try:
                    observations(lines, case, restart)
                    require(not restart or start["exit_code"] == 0, "Restart exited unsuccessfully")
                    start["disk"] = inspect(world, case, bool(restart))
                    if restart and "disk" in row["starts"][0]:
                        require(start["disk"]["identity"] == row["starts"][0]["disk"]["identity"], "Restart changed identities")
                    start["status"] = "PASS"
                except native.smoke.SmokeError as failure:
                    start.update(status="FAILED", error=str(failure)); row["findings"].append(str(failure))
                (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
            row["status"] = "FAILED" if row["findings"] else "PASS"
            require(not row["findings"], "First contradictory cut: " + case + ": " + "; ".join(row["findings"]))
            print(case + ": PASS (force-kill and two restarts)", flush=True)
        report["status"] = "PASS"
    except Exception as failure:
        report.update(status="FAILED", error=str(failure)); raise
    finally:
        if process is not None:
            process.abort()
        if session.resolve() == session and not session.is_symlink() and sentinel.is_file() and sentinel.read_text(encoding="ascii") == token:
            shutil.rmtree(session); report["cleanup"] = not session.exists()
        (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument("source_server", type=Path)
    for key in ("jar", "session-dir", "evidence-dir"):
        parser.add_argument("--" + key, type=Path, required=True)
    for key in ("source-commit", "java", "lock-owner"):
        parser.add_argument("--" + key, required=True)
    parser.add_argument("--case", action="append", choices=CASES, required=True)
    parser.add_argument("--startup-timeout", type=float, default=240)
    try:
        run(parser.parse_args()); return 0
    except (OSError, native.smoke.SmokeError) as failure:
        print("FAIL: " + str(failure)); return 1


if __name__ == "__main__":
    raise SystemExit(main())
