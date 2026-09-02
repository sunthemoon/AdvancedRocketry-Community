#!/usr/bin/env python3
"""Exercise packaged v0.8 satellite missions, restart, and exact-once claim."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

if __package__:
    from .run_dedicated_server_smoke import SmokeError, resolve_java
    from .run_v060_flight_server_smoke import FlightHarness, _load_summary, _verify_inputs
else:
    from run_dedicated_server_smoke import SmokeError, resolve_java
    from run_v060_flight_server_smoke import FlightHarness, _load_summary, _verify_inputs


EXPECTED_VERSION = "1.20.1-0.8.0-dev"
OWNERS = (
    "00000000-0000-0000-0000-000000000801",
    "00000000-0000-0000-0000-000000000802",
)
TARGETS = ("earth", "moon")
LAUNCH_LOG = re.compile(
    r"ARCE_RELEASE_TEST_SATELLITE_LAUNCH satellite=([0-9a-f-]{36}) "
    r"mission=([0-9a-f-]{36}) owner=([0-9a-f-]{36}) target=([^ ]+) "
    r"code=([A-Z_]+) deadline=(\d+)"
)
CLAIM_LOG = re.compile(
    r"ARCE_RELEASE_TEST_SATELLITE_CLAIM mission=([0-9a-f-]{36}) "
    r"owner=([0-9a-f-]{36}) target=([^ ]+) code=([A-Z_]+) "
    r"status=([A-Z_]+) research=(\d+) discovered=(true|false)"
)
EVIDENCE_LOG = re.compile(
    r"ARCE_SATELLITE_EVIDENCE satellites=(\d+) missions=(\d+) active=(\d+) "
    r"ready=(\d+) pending=(\d+) claimed=(\d+) cancelled=(\d+) "
    r"unfinished=(\d+) research=(\d+) chunk_tickets=(\d+) scheduler=([^\s]+)"
)
BATCH_LOG = re.compile(
    r"ARCE_RELEASE_TEST_SATELLITE_BATCH requested=(\d+) created=(\d+) rejected=(\d+) "
    r"code=([A-Z_]+) elapsed_nanos=(\d+) chunk_tickets=(\d+) scheduler=([^\s]+)"
)
SCHEDULER_LOG = re.compile(
    r"ARCE_SATELLITE_SCHEDULER completed=(\d+) inspected=(\d+) remaining=(\d+)"
)


def _report(process) -> dict[str, int | str]:  # type: ignore[no-untyped-def]
    start = len(process.lines)
    process.command("arce satellite admin evidence")
    index = process.wait_for(EVIDENCE_LOG, 30.0, start_at=start)
    match = EVIDENCE_LOG.search(process.lines[index])
    if match is None:
        raise SmokeError("Could not parse satellite evidence receipt")
    values = [int(value) for value in match.groups()[:10]]
    return {
        "satellites": values[0],
        "missions": values[1],
        "active": values[2],
        "ready": values[3],
        "pending": values[4],
        "claimed": values[5],
        "cancelled": values[6],
        "unfinished": values[7],
        "research": values[8],
        "chunk_tickets": values[9],
        "scheduler": match.group(11),
    }


def _launch(process, owner: str, target: str) -> dict[str, object]:  # type: ignore[no-untyped-def]
    start = len(process.lines)
    process.command(f"arce satellite release-test launch {owner} {target}")
    index = process.wait_for(LAUNCH_LOG, 30.0, start_at=start)
    match = LAUNCH_LOG.search(process.lines[index])
    if match is None:
        raise SmokeError("Could not parse satellite launch receipt")
    satellite, mission, reported_owner, reported_target, code, deadline = match.groups()
    expected_target = f"advancedrocketrycommunity:{target}"
    if reported_owner != owner or reported_target != expected_target or code != "SUCCESS":
        raise SmokeError("Satellite launch changed owner, target, or result")
    return {
        "satellite_id": satellite,
        "mission_id": mission,
        "owner_id": owner,
        "target": expected_target,
        "deadline": int(deadline),
    }


def _claim(process, mission: dict[str, object], expected_code: str) -> dict[str, object]:  # type: ignore[no-untyped-def]
    start = len(process.lines)
    process.command(f"arce satellite release-test claim {mission['mission_id']}")
    index = process.wait_for(CLAIM_LOG, 30.0, start_at=start)
    match = CLAIM_LOG.search(process.lines[index])
    if match is None:
        raise SmokeError("Could not parse satellite claim receipt")
    mission_id, owner, target, code, status, research, discovered = match.groups()
    if (
        mission_id != mission["mission_id"]
        or owner != mission["owner_id"]
        or target != mission["target"]
        or code != expected_code
        or status != "CLAIMED"
        or discovered != "true"
    ):
        raise SmokeError("Satellite claim receipt changed authority or terminal state")
    return {
        "code": code,
        "status": status,
        "research": int(research),
        "discovered": True,
    }


def _wait_until_ready(
    process,
    expected_ready: int,
    expected_claimed: int,
    timeout: float = 45.0,
) -> dict[str, int | str]:  # type: ignore[no-untyped-def]
    deadline = time.monotonic() + timeout
    last: dict[str, int | str] | None = None
    while time.monotonic() < deadline:
        last = _report(process)
        if (
            last["ready"] == expected_ready
            and last["claimed"] == expected_claimed
            and last["active"] == 0
        ):
            return last
        time.sleep(1.0)
    raise SmokeError(f"Restarted satellite missions did not become ready: {last}")


def _write_evidence(
    directory: Path,
    summary: dict[str, object],
    missions: list[dict[str, object]],
    filtered_lines: list[str],
) -> None:
    if directory.exists():
        raise SmokeError(f"Refusing to overwrite v0.8 satellite evidence: {directory}")
    directory.mkdir(parents=True)
    (directory / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=True, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    (directory / "mission-ledger.json").write_text(
        json.dumps(
            {"schema_version": 1, "missions": missions},
            ensure_ascii=True,
            indent=2,
            sort_keys=True,
        ) + "\n",
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
    parser.add_argument("server_dir", type=Path)
    parser.add_argument("--baseline-summary", type=Path, required=True)
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
        server = args.server_dir.resolve()
        baseline = _load_summary(args.baseline_summary.resolve())
        port, artifact_sha256 = _verify_inputs(server, baseline, args.expected_version)
        java, java_version = resolve_java(args.java)
        harness = FlightHarness(
            java=java,
            server=server,
            port=port,
            expected_version=args.expected_version,
            startup_timeout=args.startup_timeout,
        )

        process = harness.start("v080-satellite-start")
        missions = [
            _launch(process, owner, target)
            for owner, target in zip(OWNERS, TARGETS, strict=True)
        ]
        before_restart = _report(process)
        if (
            before_restart["satellites"] != len(OWNERS)
            or before_restart["missions"] != len(OWNERS)
            or before_restart["active"] != len(OWNERS)
            or before_restart["chunk_tickets"] != 0
            or before_restart["scheduler"] != "deadline_queue"
        ):
            raise SmokeError(f"Initial satellite evidence is inconsistent: {before_restart}")
        harness.stop(process)
        process = None

        process = harness.start("v080-satellite-restart")
        after_restart = _report(process)
        if (
            after_restart["satellites"] != len(OWNERS)
            or after_restart["missions"] != len(OWNERS)
            or after_restart["claimed"] != 0
            or after_restart["active"] + after_restart["ready"] != len(OWNERS)
        ):
            raise SmokeError(f"Restart did not preserve satellite authority: {after_restart}")
        ready = _wait_until_ready(process, len(OWNERS), 0)
        for mission in missions:
            mission["first_claim"] = _claim(process, mission, "SUCCESS")
            mission["replayed_claim"] = _claim(process, mission, "ALREADY_CLAIMED")
            if mission["first_claim"] != mission["replayed_claim"] | {"code": "SUCCESS"}:
                raise SmokeError("Repeated claim changed research or discovery state")
        completed = _report(process)
        if (
            completed["claimed"] != len(OWNERS)
            or completed["unfinished"] != 0
            or completed["research"] != 40
            or completed["chunk_tickets"] != 0
        ):
            raise SmokeError(f"Final satellite evidence is inconsistent: {completed}")
        batch_start = len(process.lines)
        process.command("arce satellite release-test batch 100")
        batch_index = process.wait_for(BATCH_LOG, 30.0, start_at=batch_start)
        batch_match = BATCH_LOG.search(process.lines[batch_index])
        if batch_match is None:
            raise SmokeError("Could not parse satellite stress batch receipt")
        requested, created, rejected, code, elapsed, tickets, scheduler = batch_match.groups()
        if (
            (int(requested), int(created), int(rejected)) != (100, 100, 0)
            or code != "SUCCESS"
            or int(tickets) != 0
            or scheduler != "deadline_queue"
        ):
            raise SmokeError("Satellite stress batch exceeded a fixed authority bound")
        stress_started = _report(process)
        if (
            stress_started["active"] != 100
            or stress_started["claimed"] != len(OWNERS)
            or stress_started["unfinished"] != 100
        ):
            raise SmokeError(f"Satellite stress batch did not remain bounded: {stress_started}")
        stress_ready = _wait_until_ready(process, 100, len(OWNERS))
        scheduler_passes = []
        for line in process.lines[batch_index:]:
            match = SCHEDULER_LOG.search(line)
            if match is not None:
                scheduler_passes.append({
                    "completed": int(match.group(1)),
                    "inspected": int(match.group(2)),
                    "remaining": int(match.group(3)),
                })
        if (
            sum(item["completed"] for item in scheduler_passes) != 100
            or any(item["completed"] > 32 or item["inspected"] > 64 for item in scheduler_passes)
            or scheduler_passes[-1]["remaining"] != 0
        ):
            raise SmokeError(f"Satellite scheduler passes exceeded budget: {scheduler_passes}")
        harness.stop(process)
        process = None

        summary = {
            "schema_version": 1,
            "version": "v0.8.0",
            "build": args.expected_version,
            "artifact_sha256": artifact_sha256,
            "tested_implementation_commit": args.tested_commit,
            "completed_at": datetime.now(timezone.utc).isoformat(),
            "java": java_version,
            "port": port,
            "same_world_verified": True,
            "restart_persistence_verified": True,
            "owner_count": len(OWNERS),
            "exact_once_claim_verified": True,
            "celestial_discoveries_verified": True,
            "chunk_tickets": 0,
            "scheduler": "deadline_queue",
            "before_restart": before_restart,
            "after_restart": after_restart,
            "ready_after_restart": ready,
            "completed": completed,
            "stress": {
                "requested": int(requested),
                "created": int(created),
                "rejected": int(rejected),
                "creation_elapsed_nanos": int(elapsed),
                "started": stress_started,
                "ready": stress_ready,
                "scheduler_passes": scheduler_passes,
            },
            "processes": harness.process_documents,
        }
        evidence = args.evidence_dir.resolve()
        _write_evidence(evidence, summary, missions, harness.filtered_lines)
        print("[PASS] Two packaged satellite owners retained independent authority")
        print("[PASS] Same-world restart preserved both active missions and deadlines")
        print("[PASS] Deadline queue completed missions with zero chunk tickets")
        print("[PASS] Discovery and repeated claims remained exact-once")
        print("[PASS] 100 packaged missions drained within 32/64 completion/inspection budgets")
        print(f"[PASS] Artifact SHA-256: {artifact_sha256}")
        print(f"[PASS] Evidence: {evidence}")
        return 0
    except (OSError, SmokeError, ValueError, json.JSONDecodeError) as exc:
        if process is not None:
            process.abort()
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
