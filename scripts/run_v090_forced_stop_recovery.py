#!/usr/bin/env python3
"""Kill one staged Beta flight process and verify exact same-world recovery."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

if __package__:
    from .run_dedicated_server_smoke import (
        SAVE_MARKER,
        SmokeError,
        digest_file,
        is_link_or_junction,
        resolve_java,
        scan_log,
    )
    from .run_v060_flight_server_smoke import (
        EARTH,
        MOON,
        RECOVERY_LOG,
        FlightHarness,
        _in_dimension,
        _load_summary,
        _verify_inputs,
    )
    from .run_v090_migration_server_smoke import _copy_server
    from .run_v090_soak_server import _load_migration_summary
else:
    from run_dedicated_server_smoke import (
        SAVE_MARKER,
        SmokeError,
        digest_file,
        is_link_or_junction,
        resolve_java,
        scan_log,
    )
    from run_v060_flight_server_smoke import (
        EARTH,
        MOON,
        RECOVERY_LOG,
        FlightHarness,
        _in_dimension,
        _load_summary,
        _verify_inputs,
    )
    from run_v090_migration_server_smoke import _copy_server
    from run_v090_soak_server import _load_migration_summary


EXPECTED_VERSION = "1.20.1-0.9.0-beta.1"
CHECKPOINT = "DESTINATION_SPAWNED"
EXPECTED_PHASE = "DESTINATION_SPAWNED"
EXPECTED_ACTION = "REMOVE_SOURCE_KEEP_DESTINATION"
RUN_TOKEN = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
LANDING_LOG = re.compile(
    r"ARCE_TRANSFER_PHASE transfer=([0-9a-f-]{36}) .*"
    r"event=landing_complete entity=([0-9a-f-]{36})"
)


def validate_forced_exit_code(exit_code: int) -> None:
    if exit_code == 0:
        raise SmokeError("Forced-stop process exited cleanly instead of being killed")


def validate_recovery_receipt(match: re.Match[str], transfer: str) -> dict[str, object]:
    (
        recovered_transfer,
        phase,
        source_count,
        destination_count,
        action,
        status,
    ) = match.groups()
    if (
        recovered_transfer != transfer
        or phase != EXPECTED_PHASE
        or int(source_count) != 1
        or int(destination_count) != 1
        or action != EXPECTED_ACTION
        or status != "RECOVERED"
    ):
        raise SmokeError("Forced-stop recovery receipt violated the exact authority contract")
    return {
        "transfer_id": recovered_transfer,
        "phase": phase,
        "source_count_before": int(source_count),
        "destination_count_before": int(destination_count),
        "action": action,
        "status": status,
    }


def _record_forced_process(process, harness: FlightHarness, exit_code: int) -> dict[str, object]:  # type: ignore[no-untyped-def]
    log_path = getattr(process, "_arce_log_path")
    findings = scan_log(process.lines)
    if findings:
        raise SmokeError(f"Forced-stop staging logged a blocking finding: {findings[0]}")
    document = {
        "name": getattr(process, "_arce_name"),
        "started_at": getattr(process, "_arce_started_at"),
        "completed_at": datetime.now(timezone.utc).isoformat(),
        "exit_code": exit_code,
        "termination": "process_kill",
        "graceful_stop_command_sent": False,
        "full_log_file": log_path.name,
        "full_log_sha256": digest_file(log_path),
    }
    harness.process_documents.append(document)
    harness.filtered_lines.extend(
        line.rstrip()
        for line in process.lines
        if "ARCE_" in line or SAVE_MARKER.search(line)
    )
    return document


def force_stop(process, harness: FlightHarness) -> dict[str, object]:  # type: ignore[no-untyped-def]
    pid = process.process.pid
    process.process.kill()
    exit_code = process.finish(timeout=30.0)
    validate_forced_exit_code(exit_code)
    document = _record_forced_process(process, harness, exit_code)
    return {"pid": pid, **document}


def _write_evidence(
    directory: Path,
    summary: dict[str, object],
    ledger: dict[str, object],
    filtered_lines: list[str],
) -> None:
    if directory.exists() or is_link_or_junction(directory):
        raise SmokeError(f"Refusing to overwrite forced-stop evidence: {directory}")
    directory.mkdir(parents=True)
    for name, value in (("summary.json", summary), ("recovery-ledger.json", ledger)):
        (directory / name).write_text(
            json.dumps(value, ensure_ascii=True, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
            newline="\n",
        )
    (directory / "filtered-lifecycle.log").write_text(
        "\n".join(filtered_lines) + "\n",
        encoding="utf-8",
        newline="\n",
    )


def parse_args() -> argparse.Namespace:
    java_home = os.environ.get("JAVA_HOME")
    default_java = str(Path(java_home) / "bin" / "java") if java_home else "java"
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_server_dir", type=Path)
    parser.add_argument("--session-dir", type=Path, required=True)
    parser.add_argument("--baseline-summary", type=Path, required=True)
    parser.add_argument("--migration-summary", type=Path, required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--tested-commit", required=True)
    parser.add_argument("--java", default=default_java)
    parser.add_argument("--expected-version", default=EXPECTED_VERSION)
    parser.add_argument("--startup-timeout", type=float, default=240.0)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    process = None
    try:
        if re.fullmatch(r"[0-9a-f]{40}", args.tested_commit) is None:
            raise SmokeError("Tested commit must be a full lowercase Git SHA-1")
        source = args.source_server_dir.resolve()
        session = args.session_dir.resolve()
        baseline_path = args.baseline_summary.resolve()
        migration_path = args.migration_summary.resolve()
        baseline = _load_summary(baseline_path)
        _copy_server(source, session)
        port, artifact_sha256 = _verify_inputs(session, baseline, args.expected_version)
        migration = _load_migration_summary(
            migration_path,
            artifact_sha256=artifact_sha256,
            expected_version=args.expected_version,
            tested_commit=args.tested_commit,
        )
        java, java_version = resolve_java(args.java)
        harness = FlightHarness(
            java=java,
            server=session,
            port=port,
            expected_version=args.expected_version,
            startup_timeout=args.startup_timeout,
        )

        process = harness.start("v090-forced-stop-stage")
        initial = harness.assemble(process)
        staged = harness.launch_to_checkpoint(
            process,
            dimension=EARTH,
            entity=str(initial["entity"]),
            destination_name="moon",
            checkpoint=CHECKPOINT,
        )
        if staged["phase"] != EXPECTED_PHASE or staged["checkpoint"] != CHECKPOINT:
            raise SmokeError("Flight did not reach the fixed durable forced-stop checkpoint")
        save_at = len(process.lines)
        process.command("save-all flush")
        process.wait_for(SAVE_MARKER, 60.0, start_at=save_at)
        abrupt = force_stop(process, harness)
        process = None

        process = harness.start("v090-forced-stop-recover")
        transfer = str(staged["transfer_id"])
        recovery_index = process.wait_for(
            re.compile(rf"ARCE_TRANSFER_RECOVERY transfer={re.escape(transfer)} "),
            60.0,
        )
        recovery_match = RECOVERY_LOG.search(process.lines[recovery_index])
        if recovery_match is None:
            raise SmokeError("Could not parse forced-stop recovery receipt")
        recovery = validate_recovery_receipt(recovery_match, transfer)
        landing_index = process.wait_for(
            re.compile(rf"ARCE_TRANSFER_PHASE transfer={re.escape(transfer)} .*event=landing_complete"),
            60.0,
            start_at=recovery_index,
        )
        landing_match = LANDING_LOG.search(process.lines[landing_index])
        if landing_match is None or landing_match.group(1) != transfer:
            raise SmokeError("Recovered transfer did not emit its landing receipt")
        destination_entity = landing_match.group(2)
        report = harness.report(process, MOON, destination_entity)
        staged_report = staged["report"]
        if not isinstance(staged_report, dict) or any(
            report[key] != staged_report[key]
            for key in ("logical", "snapshot", "fuel", "capacity", "blocks")
        ) or report["state"] != "LANDED":
            raise SmokeError("Forced-stop recovery changed rocket identity, material, fuel, or state")

        authority_marker = "V090_FORCED_STOP_SINGLE_AUTHORITY"
        harness.command_marker(
            process,
            _in_dimension(
                EARTH,
                f"execute unless entity {staged['source_entity']} in {MOON} "
                f"if entity {destination_entity} run say {authority_marker}",
            ),
            authority_marker,
        )
        harness.disassemble_and_verify(process, report, "V090_FORCED_STOP_DISASSEMBLED")
        process.command("forceload remove 384 384")
        harness.stop(process)
        process = None

        ledger = {
            "schema_version": 1,
            "checkpoint": CHECKPOINT,
            "transfer_id": transfer,
            "logical_rocket_id": report["logical"],
            "snapshot_sha256": report["snapshot"],
            "block_count": report["blocks"],
            "fuel_after_exact_debit": report["fuel"],
            "recovery": recovery,
            "single_authority_after_restart": True,
            "exact_disassembly": True,
            "container_inventory_conserved": True,
        }
        summary = {
            "schema_version": 1,
            "version": "v0.9.0",
            "build": args.expected_version,
            "tested_commit": args.tested_commit,
            "artifact_sha256": artifact_sha256,
            "baseline_summary_sha256": digest_file(baseline_path),
            "migration_summary_sha256": digest_file(migration_path),
            "migration_backup_file_count": migration["backup"]["file_count"],
            "completed_at": datetime.now(timezone.utc).isoformat(),
            "java": java_version,
            "checkpoint": CHECKPOINT,
            "durable_save_before_kill": True,
            "forced_exit_code": abrupt["exit_code"],
            "graceful_stop_command_sent_before_kill": False,
            "same_world_restart": True,
            "recovery_status": recovery["status"],
            "single_authority": True,
            "material_and_inventory_conserved": True,
            "schema_after_restart": 2,
            "critical_or_high_findings": 0,
            "processes": harness.process_documents,
        }
        _write_evidence(
            args.evidence_dir.resolve(),
            summary,
            ledger,
            harness.filtered_lines,
        )
        print(f"[PASS] Forced process exit code: {abrupt['exit_code']}")
        print(f"[PASS] Recovery action: {recovery['action']}")
        print("[PASS] One authority and exact material/inventory conservation after restart")
        print(f"[PASS] Artifact SHA-256: {artifact_sha256}")
        return 0
    except (OSError, ValueError, json.JSONDecodeError, SmokeError) as error:
        if process is not None:
            process.abort()
        print(f"[FAIL] {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
