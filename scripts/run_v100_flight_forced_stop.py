#!/usr/bin/env python3
"""Kill owned packaged-server processes at each durable flight phase and recover."""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

if __package__:
    from . import run_dedicated_server_smoke as server_smoke
    from . import run_v060_flight_server_smoke as flight
    from .run_v090_forced_stop_recovery import force_stop as _recorded_force_stop
    from .run_v090_migration_server_smoke import _copy_server
else:
    import run_dedicated_server_smoke as server_smoke
    import run_v060_flight_server_smoke as flight
    from run_v090_forced_stop_recovery import force_stop as _recorded_force_stop
    from run_v090_migration_server_smoke import _copy_server


SmokeError = server_smoke.SmokeError
CASES = (
    "ASSEMBLED", "FUELED", "COUNTDOWN", "ASCENT", "TRANSIT_PREPARED",
    "DESTINATION_SPAWNED", "PASSENGERS_TRANSFERRED", "SOURCE_REMOVED", "DESCENT", "LANDED",
)
# Journal phase, loaded source count, destination count, recovery action.
RECOVERY_CONTRACT = {
    "COUNTDOWN": ("PREPARED", 1, 0, "KEEP_SOURCE"),
    "ASCENT": ("PREPARED", 1, 0, "KEEP_SOURCE"),
    "TRANSIT_PREPARED": ("PREPARED", 1, 0, "KEEP_SOURCE"),
    "DESTINATION_SPAWNED": ("DESTINATION_SPAWNED", 1, 1, "REMOVE_SOURCE_KEEP_DESTINATION"),
    "PASSENGERS_TRANSFERRED": ("PASSENGERS_TRANSFERRED", 1, 1, "REMOVE_SOURCE_KEEP_DESTINATION"),
    "SOURCE_REMOVED": ("SOURCE_REMOVED", 0, 1, "KEEP_DESTINATION"),
    "DESCENT": ("COMMITTED", 0, 1, "KEEP_DESTINATION"),
    "LANDED": ("COMMITTED", 0, 1, "KEEP_DESTINATION"),
}
RUN_TOKEN = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
UUID_PATTERN = r"[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"
# Match whole INFO messages, not chat text or a successful-looking substring.
INFO_PREFIX = r"^(?:\[[0-9:]+\] \[Server thread/INFO\] \[[^\]\r\n]+\]: )?"
LEGACY_DISASSEMBLY = re.compile(INFO_PREFIX + flight.DISASSEMBLY_LOG.pattern + r"$")
SEALED_DISASSEMBLY = re.compile(
    INFO_PREFIX + rf"ARCE_ROCKET_SEALED_DISASSEMBLY entity=({UUID_PATTERN}) "
    rf"logical=({UUID_PATTERN}) result=([A-Z_]+)$"
)
DISASSEMBLY_RECEIPT = re.compile(
    rf"(?:{LEGACY_DISASSEMBLY.pattern})|(?:{SEALED_DISASSEMBLY.pattern})"
)
DISASSEMBLY_PENDING = re.compile(INFO_PREFIX + r"Release-test disassembly pending$")
DISASSEMBLY_REFUSAL = re.compile(INFO_PREFIX + r"Release-test disassembly failed: ([A-Z_]+)$")
DISASSEMBLY_DURABLE = re.compile(
    INFO_PREFIX + rf"ARCE_DISASSEMBLY_DURABILITY_COMPLETE transaction=({UUID_PATTERN}) "
    r"phase=DONE reason=SEALED additional=\[\] overflow=0$"
)
LAUNCH_RECEIPT = re.compile(INFO_PREFIX + flight.LAUNCH_LOG.pattern + r"$")
LAUNCH_PREPARED = re.compile(
    INFO_PREFIX + rf"ARCE_TRANSFER_PREPARE transfer=({UUID_PATTERN}) logical=({UUID_PATTERN}) "
    r"source=([^ ]+) destination=([^ ]+) code=([A-Z_]+) candidates=(\d+) chunks=(\d+) detail=([^\r\n]+)$"
)
LAUNCH_FAILED = re.compile(
    INFO_PREFIX.replace("/INFO", "/(?:INFO|ERROR)") + rf"ARCE_TRANSFER_PREPARE_FAILED transfer=({UUID_PATTERN}) "
    rf"logical=({UUID_PATTERN}) source=({UUID_PATTERN})$"
)
LAUNCH_OUTCOME = re.compile(
    rf"(?:{LAUNCH_RECEIPT.pattern})|(?:{LAUNCH_PREPARED.pattern})|(?:{LAUNCH_FAILED.pattern})"
)


def wait_settled_transfer(process, staged: dict, report: dict) -> dict:
    """Match C6's exact post-settlement event in this recovery process only.

    An already LANDED recovery logs INFO after adding T to settled; a resumed
    DESCENT logs recovery at WARN before landing and needs the later retained event.
    """
    transfer = staged["transfer_id"]
    if staged["state"] == "LANDED":
        message = (f"ARCE_TRANSFER_RECOVERY transfer={transfer} phase=COMMITTED "
                   "source_count=0 destination_count=1 action=KEEP_DESTINATION status=RECOVERED")
        form = "settled_recovery"
    else:
        message = (f"ARCE_TRANSFER_PHASE transfer={transfer} logical={report['logical']} "
                   f"phase=COMMITTED event=landed_reservation_retained entity={report['entity']} "
                   f"fuel_before={staged['fuel_before']} fuel_after={report['fuel']} "
                   f"required={staged['required_fuel']}")
        form = "retained"
    pattern = re.compile(INFO_PREFIX + re.escape(message) + r"$")
    index = process.wait_for(pattern, 60.0, start_at=0)
    line = process.lines[index].rstrip("\r\n")
    if pattern.fullmatch(line) is None:
        raise SmokeError("Disassembly lacks the exact same-transfer settlement event")
    return dict(form=form, transfer=transfer, line=line, line_index=index)


def submit_disassembly_protocol(process, command: str, entity: str, logical: str,
                                start_at: int, deadline: float) -> dict:
    """No readiness line exists on C6: each two-second retry is a new request.

    Only pending and REGION_BUSY are retryable. Neither is destructive admission.
    Stop submitting on the first receipt, then require the unchanged receipt checks.
    """
    checked, attempts, retry_at = start_at, 0, time.monotonic()
    answered = True
    while True:
        now = time.monotonic()
        if now >= deadline:
            raise SmokeError("Disassembly protocol exceeded the existing 45-second budget")
        for index in range(checked, len(process.lines)):
            line = process.lines[index].rstrip("\r\n")
            refusal = DISASSEMBLY_REFUSAL.fullmatch(line)
            if refusal is not None:
                if refusal[1] != "REGION_BUSY":
                    raise SmokeError(f"Disassembly protocol refused: {refusal[1]}")
                answered = True
            elif DISASSEMBLY_PENDING.fullmatch(line):
                answered = True
            elif DISASSEMBLY_RECEIPT.fullmatch(line):
                receipt = wait_disassembly_receipt(process, entity, logical, start_at, deadline=deadline)
                receipt["requests"] = attempts
                return receipt
        checked = len(process.lines)
        if process.process.poll() is not None:
            raise SmokeError("Server exited during disassembly protocol")
        if answered and now >= retry_at:
            process.command(command)
            attempts += 1
            answered, retry_at = False, now + 2.0
        else:
            time.sleep(min(0.05, deadline - now))


def wait_launch_receipt(process, entity: str, logical: str, dimension: str,
                        destination: str, checkpoint: str, fuel: int, start_at: int):
    """C6 admission is confirmed by the post-acknowledgement prepared callback.

    The admin command also prints 'launch failed: JOURNAL_PENDING'. That command
    answer is not the asynchronous outcome; only the identified callback proves
    countdown admission. Pending and confirmation share the old launch budget.
    """
    deadline = time.monotonic() + 30.0

    def wait(pattern, start):
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise SmokeError("Launch receipt exceeded the existing 30-second budget")
        return process.wait_for(pattern, remaining, start_at=start)

    index = wait(LAUNCH_RECEIPT, start_at)
    launch = LAUNCH_RECEIPT.fullmatch(process.lines[index].rstrip("\r\n"))
    if launch is None or (launch[2], launch[3], launch[4], launch[5], launch[6], int(launch[9])) != (
            entity, logical, dimension, destination, checkpoint, fuel):
        raise SmokeError("Launch receipt changed rocket identity, route, checkpoint or fuel")
    if launch[7] == "SUCCESS":
        return launch, index, dict(form="legacy", code="SUCCESS")
    if launch[7] != "JOURNAL_PENDING":
        raise SmokeError(f"Could not launch restart checkpoint {checkpoint}: {launch[7]}")
    confirmed_at = wait(LAUNCH_OUTCOME, index + 1)
    prepared = LAUNCH_PREPARED.fullmatch(process.lines[confirmed_at].rstrip("\r\n"))
    if prepared is None or prepared.groups()[:5] != (launch[1], logical, dimension, destination, "SUCCESS"):
        raise SmokeError("Pending launch lacks the matching successful journal acknowledgement callback")
    return launch, confirmed_at, dict(form="journalled", code="JOURNAL_PENDING",
                                     confirmation=prepared.group(0), transfer=launch[1], logical=logical)


def wait_disassembly_receipt(process, entity: str, logical: str, start_at: int,
                             *, deadline: float | None = None) -> dict:
    """Opt-in receipts for one isolated request, sharing the original 45-second budget.

    C6's diagnostic has a transaction ID, not the entity/logical IDs. These empty,
    single-rocket fixtures allow exactly one completion after the identified receipt;
    no block counts are inferred from it. World/content checks remain mandatory.
    """
    deadline = time.monotonic() + 45.0 if deadline is None else deadline

    def wait(pattern, start):
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise SmokeError("Disassembly receipt exceeded the existing 45-second budget")
        return process.wait_for(pattern, remaining, start_at=start)

    index = wait(DISASSEMBLY_RECEIPT, start_at)
    line = process.lines[index].rstrip("\r\n")
    legacy = LEGACY_DISASSEMBLY.fullmatch(line)
    sealed = SEALED_DISASSEMBLY.fullmatch(line)
    if legacy is not None:
        if legacy.groups() != (entity, logical, "SUCCESS", str(flight.EXPECTED_BLOCKS), "0"):
            raise SmokeError("Packaged disassembly did not restore the exact snapshot")
        return dict(form="legacy", entity=entity, logical=logical, code="SUCCESS",
                    blocks=flight.EXPECTED_BLOCKS, rolled_back=0)
    if sealed is None or sealed.groups() != (entity, logical, "SEALED"):
        raise SmokeError("Sealed disassembly receipt changed identity or result")
    if not any(DISASSEMBLY_PENDING.fullmatch(text.rstrip("\r\n"))
               for text in process.lines[start_at:index]):
        raise SmokeError("Sealed disassembly receipt lacks the preceding DISASSEMBLY_PENDING answer")
    durable_index = wait(DISASSEMBLY_DURABLE, index + 1)
    receipts = [text for text in process.lines[start_at:durable_index + 1]
                if DISASSEMBLY_RECEIPT.fullmatch(text.rstrip("\r\n"))]
    if len(receipts) != 1:
        raise SmokeError("Disassembly request emitted more than one receipt")
    durable = DISASSEMBLY_DURABLE.fullmatch(process.lines[durable_index].rstrip("\r\n"))
    if durable is None:
        raise SmokeError("Sealed disassembly lacks its DONE durability diagnostic")
    return dict(form="sealed", entity=entity, logical=logical, result="SEALED",
                durability_transaction=durable.group(1), durability_phase="DONE")


def verify_status(port: int, version: str, *, wait_ready_status: bool = False) -> None:
    if not wait_ready_status:
        server_smoke.validate_status_identity(server_smoke.wait_for_status(port), version)
        return
    # Forge may publish Done before its first server tick publishes status.
    # Retry only that exact transitional response, within the original budget.
    deadline = time.monotonic() + 30.0
    starting = {"text": "Server is still starting! Please wait before reconnecting."}
    while True:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise SmokeError("Server status did not become ready within 30 seconds")
        status = server_smoke.wait_for_status(port, timeout=remaining)
        if status != starting:
            server_smoke.validate_status_identity(status, version)
            return
        time.sleep(min(0.1, remaining))


def force_stop(process, harness):
    if process.process.poll() is not None:
        raise SmokeError("Server exited before the requested process kill")
    return _recorded_force_stop(process, harness)


def validate_receipt(case: str, match: re.Match[str], transfer: str) -> dict[str, object]:
    recovered, phase, source, destination, action, status = match.groups()
    observed = (phase, int(source), int(destination), action)
    if recovered != transfer or status != "RECOVERED" or observed != RECOVERY_CONTRACT[case]:
        raise SmokeError(f"{case} recovery receipt violates the phase/authority contract")
    return {
        "transfer_id": recovered, "phase": phase, "source_count_before": int(source),
        "destination_count_before": int(destination), "action": action, "status": status,
    }


def validate_report(case: str, staged: dict, report: dict) -> None:
    before = staged["report"]
    for key in ("logical", "snapshot", "capacity", "blocks", "passengers", "origin"):
        if report[key] != before[key]:
            raise SmokeError(f"{case} recovery changed {key}")
    if report["passengers"] != 0:
        raise SmokeError("This isolated flight harness does not exercise player passengers")
    if case in {"ASSEMBLED", "FUELED"}:
        state, dimension, fuel = case, flight.EARTH, before["fuel"]
    elif RECOVERY_CONTRACT[case][3] == "KEEP_SOURCE":
        state, dimension, fuel = "FUELED", flight.EARTH, staged["fuel_before"]
    else:
        state, dimension = "LANDED", flight.MOON
        fuel = staged["fuel_before"] - staged["required_fuel"]
    if (report["state"], report["dimension"], report["fuel"]) != (state, dimension, fuel):
        raise SmokeError(f"{case} recovery changed state, dimension or exact fuel debit")
    entity = staged["source_entity"] if dimension == flight.EARTH else staged["paused_entity"]
    if report["entity"] != entity:
        raise SmokeError(f"{case} recovery replaced a persisted authority unexpectedly")


class RecoveryHarness(flight.FlightHarness):
    def launch_to_checkpoint(self, process, *, dimension: str, entity: str,
                             destination_name: str, checkpoint: str):
        if not getattr(self, "journalled_launch_receipt", False):
            return super().launch_to_checkpoint(process, dimension=dimension, entity=entity,
                                                destination_name=destination_name, checkpoint=checkpoint)
        # This v1.0 forced-stop matrix covers the outward Earth/Moon leg only.
        if dimension != flight.EARTH or destination_name != "moon":
            raise SmokeError("Journalled checkpoint mode requires the isolated Earth/Moon leg")
        fueled = self.refuel(process, dimension, entity)
        start = len(process.lines)
        process.command(flight._in_dimension(
            dimension, f"arce rocket release-test launch {entity} {destination_name} {checkpoint}",
        ))
        launch, launch_index, receipt = wait_launch_receipt(
            process, entity, fueled["logical"], dimension, flight.MOON, checkpoint, fueled["amount"], start,
        )
        transfer = launch[1]
        pause_pattern = re.compile(
            rf"ARCE_RELEASE_TEST_FLIGHT_PAUSED checkpoint={re.escape(checkpoint)} "
            rf"transfer={re.escape(transfer)} "
        )
        pause_index = process.wait_for(pause_pattern, 30.0, start_at=launch_index)
        pause = flight.PAUSE_LOG.search(process.lines[pause_index])
        if pause is None:
            raise SmokeError(f"Could not parse paused checkpoint {checkpoint}")
        paused_checkpoint, paused_transfer, phase, state, paused_entity = pause.groups()
        if paused_checkpoint != checkpoint or paused_transfer != transfer:
            raise SmokeError("Release checkpoint froze the wrong transfer")
        dimension = flight.EARTH if phase == "PREPARED" else flight.MOON
        report = self.report(process, dimension, paused_entity)
        if report["state"] != state:
            raise SmokeError("Paused entity report changed the frozen flight state")
        return dict(transfer_id=transfer, logical_rocket_id=launch[3], source_entity=entity,
                    paused_entity=paused_entity, paused_dimension=dimension, phase=phase, state=state,
                    checkpoint=checkpoint, required_fuel=int(launch[8]), fuel_before=int(launch[9]),
                    report=report, launch_receipt=receipt)

    def disassemble_and_verify(self, process, report: dict, marker: str):
        protocol = getattr(self, "settled_disassembly_protocol", False)
        if protocol and not getattr(self, "sealed_disassembly_receipt", False):
            raise SmokeError("Settled disassembly protocol requires sealed-disassembly-receipt")
        if not getattr(self, "sealed_disassembly_receipt", False):
            return super().disassemble_and_verify(process, report, marker)
        dimension, entity, logical = (str(report[key]) for key in ("dimension", "entity", "logical"))
        start = len(process.lines)
        command = f"arce rocket release-test disassemble {entity}"
        # C6 requires deliberate disposal of the exact fuel read in the validated report.
        if report["fuel"] > 0:
            command += f" discard-fuel {report['fuel']}"
        submitted_at = time.monotonic()
        deadline = submitted_at + 45.0
        if protocol:
            receipt = submit_disassembly_protocol(
                process, flight._in_dimension(dimension, command), entity, logical, start, deadline,
            )
        else:
            process.command(flight._in_dimension(dimension, command))
            receipt = wait_disassembly_receipt(process, entity, logical, start)

        def remaining():
            budget = deadline - time.monotonic()
            if budget <= 0:
                raise SmokeError("Disassembly verification exceeded the existing 45-second budget")
            return budget
        # Kept identical to FlightHarness: a receipt alone never proves material restoration.
        origin_x, origin_y, origin_z = [int(value) for value in report["origin"]]
        conditions = (
            f"unless entity {entity} "
            f"if block {origin_x - 1} {origin_y} {origin_z} advancedrocketrycommunity:rocket_fuel_tank "
            f"if block {origin_x} {origin_y} {origin_z} advancedrocketrycommunity:rocket_motor "
            f"if block {origin_x + 1} {origin_y} {origin_z} minecraft:chest "
            f"if block {origin_x} {origin_y + 1} {origin_z} advancedrocketrycommunity:rocket_seat "
            f"if block {origin_x} {origin_y + 2} {origin_z} "
            "advancedrocketrycommunity:guidance_computer"
        )
        marker_options = {"timeout": remaining()} if protocol else {}
        self.command_marker(
            process,
            flight._in_dimension(dimension, f"execute {conditions} run say {marker}"),
            marker,
            **marker_options,
        )
        chest_position = f"{origin_x + 1} {origin_y} {origin_z}"
        data_start = len(process.lines)
        process.command(flight._in_dimension(dimension, f"data get block {chest_position} Items"))
        data_index = process.wait_for(flight.BLOCK_DATA_MARKER, remaining() if protocol else 30.0,
                                      start_at=data_start)
        data_match = flight.BLOCK_DATA_MARKER.search(process.lines[data_index])
        chest_data = data_match.group(1) if data_match else ""
        if (
            'id: "minecraft:diamond"' not in chest_data
            or "Count: 17b" not in chest_data
            or 'id: "minecraft:iron_ingot"' not in chest_data
            or "Count: 64b" not in chest_data
        ):
            raise SmokeError("Packaged disassembly changed chest inventory contents")
        if protocol:
            # The opt-in mode checks the complete two-slot inventory, not substrings.
            expected = ('[{Slot: 0b, id: "minecraft:diamond", Count: 17b}, '
                        '{Slot: 26b, id: "minecraft:iron_ingot", Count: 64b}]')
            if chest_data != expected:
                raise SmokeError("Packaged disassembly changed exact chest inventory")
            receipts = [line for line in process.lines[start:]
                        if DISASSEMBLY_RECEIPT.fullmatch(line.rstrip("\r\n"))]
            if len(receipts) != 1:
                raise SmokeError("Disassembly request emitted more than one receipt")
            for line in process.lines[start:]:
                refusal = DISASSEMBLY_REFUSAL.fullmatch(line.rstrip("\r\n"))
                if refusal is not None and refusal[1] != "REGION_BUSY":
                    raise SmokeError(f"Disassembly protocol refused: {refusal[1]}")
            remaining()
            receipt["elapsed_seconds"] = time.monotonic() - submitted_at
        process.command(flight._in_dimension(
            dimension,
            f"fill {origin_x - 1} {origin_y} {origin_z} "
            f"{origin_x + 1} {origin_y + 2} {origin_z} minecraft:air",
        ))
        return receipt

    def start(self, name: str) -> server_smoke.CapturedProcess:
        log = self.server / f"{RUN_TOKEN}-{name}-full.txt"
        process = server_smoke.CapturedProcess(flight._server_command(self.java), self.server, log)
        setattr(process, "_arce_name", name)
        setattr(process, "_arce_log_path", log)
        setattr(process, "_arce_started_at", datetime.now(timezone.utc).isoformat())
        try:
            process.wait_for(server_smoke.READY_MARKER, self.startup_timeout)
            verify_status(
                self.port, self.expected_version,
                wait_ready_status=getattr(self, "wait_ready_status", False),
            )
            return process
        except BaseException:
            process.abort()
            raise


def audit_recovery_log(lines: list[str], case: str, staged: dict,
                       expected_version: str | None = None) -> None:
    for finding in server_smoke.scan_log(lines, expected_mod_version=expected_version):
        match = flight.RECOVERY_LOG.search(finding)
        # Only the exact verified recovery WARN is expected. ERROR/FATAL still fail.
        if (case in RECOVERY_CONTRACT and match is not None
                and server_smoke.WARNING_LINE.search(finding)):
            validate_receipt(case, match, staged["transfer_id"])
            continue
        raise SmokeError(f"{case} recovery logged a blocking finding: {finding}")


def verify_single_authority(process, harness: RecoveryHarness, report: dict, case: str) -> None:
    # This is a disposable, initially empty world with only one test rocket.
    # Both source/destination regions are loaded by the normal recovery service.
    # An unbounded @e selector is already cross-dimension; enumerate it once.
    process.command("scoreboard objectives add arce_v100 dummy")
    process.command("scoreboard players set #rockets arce_v100 0")
    process.command("execute as @e[type=advancedrocketrycommunity:rocket] "
                    "run scoreboard players add #rockets arce_v100 1")
    marker = f"V100_{case}_ONE_LOADED_AUTHORITY"
    harness.command_marker(process, flight._in_dimension(
        report["dimension"], f"execute if score #rockets arce_v100 matches 1 "
        f"if entity {report['entity']} run say {marker}",
    ), marker)


def run_case(harness: RecoveryHarness, case: str) -> dict[str, object]:
    process = None
    try:
        process = harness.start(f"v100-{case.lower()}-stage")
        initial = harness.assemble(process)
        staged = {"source_entity": initial["entity"], "report": initial}
        if case == "FUELED":
            harness.refuel(process, flight.EARTH, initial["entity"])
            staged["report"] = harness.report(process, flight.EARTH, initial["entity"])
        elif case != "ASSEMBLED":
            staged.update(harness.launch_to_checkpoint(
                process, dimension=flight.EARTH, entity=initial["entity"],
                destination_name="moon", checkpoint=case,
            ))
            if staged["phase"] != RECOVERY_CONTRACT[case][0]:
                raise SmokeError(f"{case} did not stop at the requested journal phase")
        save_at = len(process.lines)
        process.command("save-all flush")
        process.wait_for(server_smoke.SAVE_MARKER, 60.0, start_at=save_at)
        abrupt = force_stop(process, harness)
        process = None

        process = harness.start(f"v100-{case.lower()}-recover")
        receipt = {}
        dimension, entity = flight.EARTH, staged["source_entity"]
        if case in RECOVERY_CONTRACT:
            transfer = staged["transfer_id"]
            index = process.wait_for(re.compile(
                rf"ARCE_TRANSFER_RECOVERY transfer={re.escape(transfer)} ",
            ), 60.0)
            match = flight.RECOVERY_LOG.search(process.lines[index])
            if match is None:
                raise SmokeError(f"{case} recovery receipt could not be parsed")
            receipt = validate_receipt(case, match, transfer)
            if receipt["action"] != "KEEP_SOURCE":
                dimension, entity = flight.MOON, staged["paused_entity"]
                if case != "LANDED":
                    process.wait_for(re.compile(
                        rf"ARCE_TRANSFER_PHASE transfer={re.escape(transfer)} .*"
                        rf"event=landing_complete entity={re.escape(entity)}",
                    ), 60.0)
        report = harness.report(process, dimension, entity)
        validate_report(case, staged, report)
        settlement = None
        if getattr(harness, "settled_disassembly_protocol", False) and report["state"] == "LANDED":
            settlement = wait_settled_transfer(process, staged, report)
        verify_single_authority(process, harness, report, case)
        disassembly = harness.disassemble_and_verify(process, report, f"V100_{case}_DISASSEMBLED")
        process.command(f"forceload remove {flight.X} {flight.Z}")
        harness.stop(process)
        audit_recovery_log(process.lines, case, staged, harness.expected_version)
        process = None
        return {
            "case": case, "staged": staged, "recovered": report, "recovery": receipt,
            "forced_stop": abrupt, "durable_save_before_kill": True,
            "same_world_restart": True, "one_loaded_authority": True,
            "snapshot_identity_and_fuel_preserved": True,
            "disassembly_blocks_and_expected_inventory_verified": True,
            "disassembly_receipt": disassembly,
            **({"disassembly_settlement": settlement} if settlement is not None else {}),
        }
    except BaseException:
        if process is not None:
            process.abort()
        raise


def verify_local_session(server: Path, port: int) -> dict[str, str]:
    properties = server_smoke.parse_java_properties((server / "server.properties").read_bytes())
    if properties.get("server-ip") != "127.0.0.1" or properties.get("server-port") != str(port):
        raise SmokeError("Recovery server must use the baseline port on loopback only")
    return properties


def write_evidence(directory: Path, session: Path, summary: dict) -> None:
    # The directory is reserved before any server is started, never overwritten.
    logs = []
    for source in sorted(session.glob(f"{RUN_TOKEN}-v100-*-full.txt")):
        if server_smoke.is_link_or_junction(source) or source.stat().st_size > 16 * 1024 * 1024:
            raise SmokeError("Recovery log is unsafe or exceeds 16 MiB")
        destination = directory / source.name
        shutil.copy2(source, destination)
        logs.append({"file": source.name, "sha256": server_smoke.digest_file(destination)})
    summary["logs"] = logs
    (directory / "summary.json").write_text(
        json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8", newline="\n",
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_server_dir", type=Path)
    parser.add_argument("--session-dir", type=Path, required=True)
    parser.add_argument("--baseline-summary", type=Path, required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--base-commit", required=True)
    parser.add_argument("--expected-version", default="1.20.1-1.0.0-dev")
    parser.add_argument("--wait-ready-status", action="store_true",
                        help="Poll Forge's starting response within the existing 30-second status budget")
    parser.add_argument("--sealed-disassembly-receipt", action="store_true",
                        help="Also accept an identified SEALED receipt after pending, with DONE durability")
    parser.add_argument("--journalled-launch-receipt", action="store_true",
                        help="Accept JOURNAL_PENDING only with its matching post-acknowledgement SUCCESS callback")
    parser.add_argument("--settled-disassembly-protocol", action="store_true",
                        help="Wait for settlement and submit fresh exact-fuel requests within 45 seconds")
    parser.add_argument("--case", action="append", choices=CASES, dest="cases")
    java_home = os.environ.get("JAVA_HOME")
    parser.add_argument("--java", default=str(Path(java_home) / "bin/java") if java_home else "java")
    parser.add_argument("--startup-timeout", type=float, default=240.0)
    args = parser.parse_args()
    if args.settled_disassembly_protocol and not args.sealed_disassembly_receipt:
        parser.error("--settled-disassembly-protocol requires --sealed-disassembly-receipt")
    return args


def main() -> int:
    args = parse_args()
    evidence = None
    harness = None
    summary = {
        "schema_version": 1, "version": "v1.0.0", "build": args.expected_version,
        "source_state": "development_worktree", "base_commit": args.base_commit,
        "status": "IN_PROGRESS", "cases": [],
        "requested_cases": list(dict.fromkeys(args.cases or CASES)),
        "scope": "unmanned flight checkpoints after explicit durable flush; not assembly, disassembly or player crash coverage",
    }
    try:
        if re.fullmatch(r"[0-9a-f]{40}", args.base_commit) is None:
            raise SmokeError("Base commit must be a full lowercase SHA-1")
        for path in (args.source_server_dir, args.session_dir, args.evidence_dir):
            if server_smoke.is_link_or_junction(path):
                raise SmokeError("Recovery inputs/outputs must not be linked directories")
        if args.evidence_dir.exists():
            raise SmokeError("Refusing to overwrite existing recovery evidence")
        source, session = args.source_server_dir.resolve(), args.session_dir.resolve()
        if source == session or source.is_relative_to(session) or session.is_relative_to(source):
            raise SmokeError("Source and disposable session directories must not overlap")
        output = args.evidence_dir.resolve()
        if any(output == root or output.is_relative_to(root) or root.is_relative_to(output)
               for root in (source, session)):
            raise SmokeError("Evidence must be separate from server directories")
        baseline = flight._load_summary(args.baseline_summary)
        _copy_server(source, session)
        port, artifact_hash = flight._verify_inputs(session, baseline, args.expected_version)
        properties = verify_local_session(session, port)
        java, java_version = server_smoke.resolve_java(args.java)
        output.mkdir(parents=True, exist_ok=False)
        evidence = output
        summary.update({
            "artifact_sha256": artifact_hash, "java": java_version,
            "baseline_summary_sha256": server_smoke.digest_file(args.baseline_summary),
            "harness_sha256": server_smoke.digest_file(Path(__file__)),
            "online_mode": properties.get("online-mode"),
        })
        harness = RecoveryHarness(java=java, server=session, port=port,
                                  expected_version=args.expected_version,
                                  startup_timeout=args.startup_timeout)
        harness.wait_ready_status = args.wait_ready_status
        summary["wait_ready_status"] = args.wait_ready_status
        harness.sealed_disassembly_receipt = args.sealed_disassembly_receipt
        summary["sealed_disassembly_receipt"] = args.sealed_disassembly_receipt
        harness.journalled_launch_receipt = args.journalled_launch_receipt
        summary["journalled_launch_receipt"] = args.journalled_launch_receipt
        harness.settled_disassembly_protocol = args.settled_disassembly_protocol
        summary["settled_disassembly_protocol"] = args.settled_disassembly_protocol
        for case in summary["requested_cases"]:
            result = run_case(harness, case)
            summary["cases"].append(result)
            print(f"[PASS] Forced-stop flight checkpoint {case}", flush=True)
        summary["status"] = "PASS"
        summary["complete_flight_matrix"] = len(summary["cases"]) == len(CASES)
        return 0
    except (OSError, ValueError, SmokeError) as error:
        summary["status"] = "FAIL"
        summary["error"] = str(error)
        print(f"[FAIL] {error}", file=sys.stderr)
        return 1
    finally:
        if evidence is not None:
            summary["completed_at"] = datetime.now(timezone.utc).isoformat()
            summary["processes"] = harness.process_documents if harness else []
            write_evidence(evidence, args.session_dir.resolve(), summary)


if __name__ == "__main__":
    sys.exit(main())
