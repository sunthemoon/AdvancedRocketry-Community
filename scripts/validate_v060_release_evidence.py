#!/usr/bin/env python3
"""Validate the bounded v0.6.0 Earth-Moon release-evidence bundle."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import re
import sys
from pathlib import Path
from typing import Any

if __package__:
    from .manage_v060_generated_manifest import verify as verify_generated_manifest
    from .validate_release_checksums import load_artifact_metadata
    from .validate_v020_release_evidence import (
        EvidenceError,
        _load_json,
        _regular_file,
        _safe_relative,
        _sha256,
    )
else:
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from manage_v060_generated_manifest import verify as verify_generated_manifest
    from validate_release_checksums import load_artifact_metadata
    from validate_v020_release_evidence import (
        EvidenceError,
        _load_json,
        _regular_file,
        _safe_relative,
        _sha256,
    )


ROOT = Path(__file__).resolve().parents[1]
RELEASE_ROOT = Path("docs/releases/v0.6.0")
EVIDENCE_ROOT = RELEASE_ROOT / "evidence"
PROVENANCE_RECORD = Path("docs/provenance/v0.6.0-earth-moon-roundtrip.md")
GENERATED_RECORD = Path("docs/provenance/v0.6.0-generated-resources.json")
WAIVER_RECORD = Path("docs/decisions/ADR-007-V060-VISUAL-EVIDENCE-ATTESTATION.md")
EXPECTED_VERSION = "v0.6.0"
EXPECTED_BUILD = "1.20.1-0.6.0-dev"
EXPECTED_ARTIFACT = "advancedrocketry-community-1.20.1-0.6.0-dev.jar"
EXPECTED_ARTIFACT_SHA256 = (
    "cb8d34e797a57e94a1efb595af8dace6f40072cf0d96715a3d8db73a3518668d"
)
EXPECTED_ARTIFACT_BYTES = 917_911
EXPECTED_COMMIT = "6a293f705e939a67b5b617b1dfaa7deef4d6d7b6"
EXPECTED_EVIDENCE_COMMIT = "bbf424b43836d865a3f66cca3a580c32701fb46f"
EXPECTED_MERGE_COMMIT = "4c43ff6297324049eed758d210b9a5f99ed70876"
EXPECTED_MANIFEST_SHA256 = (
    "8e72c8946246b76aeb1beb453a9a14409806d7c9c47745e81e3185466c1bb529"
)
EXPECTED_GENERATED_SHA256 = (
    "7a43b35b0a914179d19a14d0bbb5a6b2a2fb4a59b869d9df4525d6005a24a5e0"
)
RESTART_CASES = [
    "ASSEMBLED",
    "FUELED",
    "COUNTDOWN",
    "ASCENT",
    "TRANSIT_PREPARED",
    "DESTINATION_SPAWNED",
    "DESCENT",
    "LANDED",
]
MULTIPLAYER_LOGS = {"server.txt", "client-a.txt", "client-b.txt"}
REQUIRED_RELEASE_DOCS = {
    "GATE-STATUS.md",
    "INSTALLATION.md",
    "KNOWN-ISSUES.md",
    "MANUAL-TEST.md",
    "PERFORMANCE.md",
    "RELEASE-EVIDENCE.md",
    "TEST-REPORT.md",
    "checksums.txt",
}
AUTHORIZED_REVIEWERS = {"sunthemoon"}
SHA256 = re.compile(r"[0-9a-f]{64}")
MACHINE_PATH = re.compile(r"[A-Za-z]:[\\/]|server-work[/\\]run-")
MAX_JSON_BYTES = 2 * 1024 * 1024
MAX_TEXT_BYTES = 512 * 1024
MAX_EVIDENCE_BYTES = 8 * 1024 * 1024


def _valid_date(value: object) -> bool:
    if not isinstance(value, str) or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", value):
        return False
    try:
        dt.date.fromisoformat(value)
    except ValueError:
        return False
    return True


def _validate_provenance(repository_root: Path, errors: list[str]) -> bool:
    try:
        provenance_path = _regular_file(
            repository_root, PROVENANCE_RECORD.as_posix(), MAX_TEXT_BYTES
        )
        provenance = provenance_path.read_text(encoding="utf-8", errors="strict")
        generated_path = _regular_file(
            repository_root, GENERATED_RECORD.as_posix(), MAX_JSON_BYTES
        )
        generated = _load_json(repository_root, GENERATED_RECORD)
        manifest_errors = verify_generated_manifest(repository_root, generated_path)
    except (EvidenceError, OSError, UnicodeError, ValueError, json.JSONDecodeError) as exc:
        errors.append(str(exc))
        return False
    errors.extend(manifest_errors)
    required = {
        "target_version: v0.6.0",
        "status: APPROVED",
        'reviewer: "sunthemoon"',
        'reviewed_at: "2026-09-01"',
        "copied_source_files: 0",
        "copied_binary_assets: 0",
        "generated_resources: 11",
        f"generated_manifest_sha256: {EXPECTED_GENERATED_SHA256}",
    }
    if any(item not in provenance for item in required):
        errors.append("v0.6.0 provenance is not explicitly owner-approved")
    targets = generated.get("targets")
    if (
        generated.get("schema_version") != 1
        or generated.get("target_version") != EXPECTED_VERSION
        or generated.get("status") != "COMMUNITY_AUTHORED_DATAGEN"
        or not isinstance(targets, list)
        or len(targets) != 11
        or _sha256(generated_path) != EXPECTED_GENERATED_SHA256
    ):
        errors.append("v0.6.0 generated-resource provenance is incomplete")
    return not errors


def _validate_artifact(
    repository_root: Path, artifact: Path | None, errors: list[str]
) -> tuple[dict[str, Any], str, str]:
    try:
        summary = _load_json(
            repository_root, EVIDENCE_ROOT / "artifact/artifact-summary.json"
        )
        manifest = _regular_file(
            repository_root,
            (EVIDENCE_ROOT / "artifact/jar-content-manifest.json").as_posix(),
            MAX_JSON_BYTES,
        )
    except EvidenceError as exc:
        errors.append(str(exc))
        return {}, "", ""
    metadata, metadata_errors = load_artifact_metadata(manifest)
    errors.extend(f"v0.6.0 {error}" for error in metadata_errors)
    main = summary.get("main_jar")
    repeated = summary.get("repeated_clean_builds")
    server_copy = summary.get("packaged_server_copy")
    content = summary.get("content_manifest")
    if not all(isinstance(item, dict) for item in (main, repeated, server_copy, content)):
        errors.append("v0.6.0 artifact summary is missing required records")
        return summary, "", ""
    if (
        summary.get("schema_version") != 1
        or summary.get("version") != EXPECTED_VERSION
        or summary.get("build") != EXPECTED_BUILD
        or summary.get("tested_implementation_commit") != EXPECTED_COMMIT
        or main
        != {
            "path": f"build/libs/{EXPECTED_ARTIFACT}",
            "filename": EXPECTED_ARTIFACT,
            "bytes": EXPECTED_ARTIFACT_BYTES,
            "sha256": EXPECTED_ARTIFACT_SHA256,
        }
        or repeated.get("count") != 2
        or repeated.get("byte_identical") is not True
        or repeated.get("main_sha256_values")
        != [EXPECTED_ARTIFACT_SHA256, EXPECTED_ARTIFACT_SHA256]
        or server_copy != {"byte_equal": True, "sha256": EXPECTED_ARTIFACT_SHA256}
        or content
        != {
            "path": (EVIDENCE_ROOT / "artifact/jar-content-manifest.json").as_posix(),
            "sha256": EXPECTED_MANIFEST_SHA256,
            "entry_count": 591,
        }
        or _sha256(manifest) != EXPECTED_MANIFEST_SHA256
        or metadata is None
        or metadata.filename != EXPECTED_ARTIFACT
        or metadata.sha256 != EXPECTED_ARTIFACT_SHA256
        or metadata.manifest.get("entry_count") != 591
    ):
        errors.append("v0.6.0 artifact summary does not bind the repeated packaged JAR")
    if artifact is not None:
        try:
            actual = artifact.resolve(strict=True)
        except OSError as exc:
            errors.append(f"artifact cannot be read: {exc}")
        else:
            if (
                not actual.is_file()
                or actual.is_symlink()
                or actual.stat().st_size != EXPECTED_ARTIFACT_BYTES
            ):
                errors.append("artifact is missing, unsafe, or has the wrong size")
            elif _sha256(actual) != EXPECTED_ARTIFACT_SHA256:
                errors.append("artifact SHA-256 differs from the v0.6.0 evidence")
    return summary, EXPECTED_ARTIFACT_SHA256, EXPECTED_COMMIT


def _validate_automated(
    repository_root: Path, artifact_hash: str, tested_commit: str, errors: list[str]
) -> dict[str, Any]:
    try:
        summary = _load_json(repository_root, EVIDENCE_ROOT / "automated/summary.json")
        gametest_path = _regular_file(
            repository_root,
            (EVIDENCE_ROOT / "automated/gametest.txt").as_posix(),
            MAX_TEXT_BYTES,
        )
        gametest_text = gametest_path.read_text(encoding="utf-8", errors="strict")
    except (EvidenceError, UnicodeError) as exc:
        errors.append(str(exc))
        return {}
    results = summary.get("results")
    security = summary.get("security")
    expected_security = {
        "bounded_flight_nbt": True,
        "bounded_landing_search": True,
        "bounded_network_intents": True,
        "client_final_decisions": 0,
        "exactly_once_fuel_debit": True,
        "forced_chunk_loads": 0,
        "loaded_destination_required": True,
        "permission_and_distance_checked": True,
        "replay_intents_rejected": True,
        "server_recomputed_flight_plan": True,
        "single_authority_recovery": True,
    }
    expected_results = {
        "celestial_identity": {"findings": 0, "result": "PASS"},
        "clean_builds": {"byte_identical": True, "result": "PASS", "runs": 2},
        "client_boundary": {"findings": 0, "result": "PASS"},
        "datagen": {"files": 11, "git_diff_clean": True, "result": "PASS"},
        "jar_audit": {"entries": 591, "findings": 0, "result": "PASS"},
        "junit": {"failed": 0, "passed": 204, "result": "PASS", "skipped": 0},
        "packaged_flight": {
            "flight_legs": 40,
            "restart_cases": 8,
            "result": "PASS",
            "round_trips": 20,
        },
        "python": {
            "discovered": 613,
            "failed": 0,
            "passed": 609,
            "result": "PASS",
            "skipped": 4,
        },
        "repository": {"failed": 0, "result": "PASS", "warnings": 0},
    }
    gametest = results.get("gametest") if isinstance(results, dict) else None
    if (
        summary.get("schema_version") != 1
        or summary.get("version") != EXPECTED_VERSION
        or summary.get("build") != EXPECTED_BUILD
        or summary.get("artifact_sha256") != artifact_hash
        or summary.get("tested_implementation_commit") != tested_commit
        or not isinstance(results, dict)
        or any(results.get(key) != value for key, value in expected_results.items())
        or not isinstance(gametest, dict)
        or gametest.get("result") != "PASS"
        or gametest.get("passed") != 39
        or gametest.get("failed") != 0
        or gametest.get("evidence")
        != (EVIDENCE_ROOT / "automated/gametest.txt").as_posix()
        or "All 39 required tests passed" not in gametest_text
        or "simulated_ticks=6000" not in gametest_text
        or "peak_inspections=112" not in gametest_text
        or MACHINE_PATH.search(gametest_text)
        or security != expected_security
    ):
        errors.append("v0.6.0 automated or authority summary is incomplete")
    return summary


def _validate_post_merge(
    repository_root: Path, artifact_hash: str, errors: list[str]
) -> bool:
    try:
        record = _load_json(
            repository_root,
            EVIDENCE_ROOT / "artifact/post-merge-reproduction.json",
        )
    except EvidenceError as exc:
        errors.append(str(exc))
        return False
    main = record.get("main_jar")
    sources = record.get("sources_jar")
    manifest = record.get("content_manifest")
    checks = record.get("pull_request_checks")
    tests = record.get("unit_tests")
    if (
        record.get("schema_version") != 1
        or record.get("version") != EXPECTED_VERSION
        or record.get("tested_implementation_commit") != EXPECTED_COMMIT
        or record.get("evidence_commit") != EXPECTED_EVIDENCE_COMMIT
        or record.get("merge_commit") != EXPECTED_MERGE_COMMIT
        or record.get("reproduced_at") != "2026-09-01"
        or record.get("build_result") != "PASS"
        or "--no-build-cache" not in str(record.get("build_command"))
        or "--rerun-tasks" not in str(record.get("build_command"))
        or main
        != {
            "byte_equal_to_candidate": True,
            "bytes": EXPECTED_ARTIFACT_BYTES,
            "sha256": artifact_hash,
        }
        or sources
        != {
            "byte_equal_to_candidate": True,
            "bytes": 451741,
            "sha256": "090ac53911686dec5f56ede8c0d9d9076e33840bcffedd56ec8619440bde3adc",
        }
        or manifest
        != {
            "byte_equal_to_candidate": True,
            "entry_count": 591,
            "sha256": EXPECTED_MANIFEST_SHA256,
        }
        or checks
        != {
            "forge_ci": (
                "https://github.com/sunthemoon/AdvancedRocketry-Community/"
                "actions/runs/33476308389"
            ),
            "governance_ci": (
                "https://github.com/sunthemoon/AdvancedRocketry-Community/"
                "actions/runs/33476308388"
            ),
            "result": "3/3_PASS",
        }
        or tests
        != {
            "errors": 0,
            "failures": 0,
            "passed": 204,
            "skipped": 0,
            "total": 204,
        }
    ):
        errors.append("v0.6.0 post-merge reproduction is incomplete or inconsistent")
    return not errors


def _validate_dedicated_server(
    repository_root: Path, artifact_hash: str, errors: list[str]
) -> bool:
    try:
        summary = _load_json(
            repository_root, EVIDENCE_ROOT / "dedicated-server/summary.json"
        )
        logs = [
            _regular_file(
                repository_root,
                (EVIDENCE_ROOT / f"dedicated-server/{name}.txt").as_posix(),
                MAX_TEXT_BYTES,
            )
            for name in ("first-start", "restart")
        ]
    except EvidenceError as exc:
        errors.append(str(exc))
        return False
    cycles = summary.get("cycles")
    world = summary.get("world")
    if (
        summary.get("schema_version") != 2
        or summary.get("artifact_sha256") != artifact_hash
        or summary.get("server_artifact_sha256") != artifact_hash
        or summary.get("mod_version") != EXPECTED_BUILD
        or summary.get("minecraft") != "1.20.1"
        or summary.get("forge") != "47.4.10"
        or summary.get("offline_mode") is not False
        or summary.get("server_bind") != "127.0.0.1"
        or not isinstance(world, dict)
        or world.get("same_world_verified") is not True
        or summary.get("world_level_dat") is not True
        or not isinstance(cycles, list)
        or not all(isinstance(cycle, dict) for cycle in cycles)
        or [cycle.get("name") for cycle in cycles] != ["first-start", "restart"]
        or any(cycle.get("exit_code") != 0 for cycle in cycles)
        or any(cycle.get("client_linkage_failure_count") != 0 for cycle in cycles)
        or any(cycle.get("project_error_count") != 0 for cycle in cycles)
    ):
        errors.append("v0.6.0 packaged dedicated-server lifecycle is incomplete")
    for path in logs:
        try:
            text = path.read_text(encoding="utf-8", errors="strict")
        except UnicodeError as exc:
            errors.append(str(exc))
            continue
        if MACHINE_PATH.search(text):
            errors.append(f"machine-local path leaked into {path.name}")
    return not errors


def _validate_flight_server(
    repository_root: Path, artifact_hash: str, errors: list[str]
) -> bool:
    try:
        summary = _load_json(repository_root, EVIDENCE_ROOT / "flight-server/summary.json")
        ledger = _load_json(
            repository_root, EVIDENCE_ROOT / "flight-server/round-trip-ledger.json"
        )
        matrix = _load_json(
            repository_root, EVIDENCE_ROOT / "flight-server/restart-matrix.json"
        )
        filtered = _regular_file(
            repository_root,
            (EVIDENCE_ROOT / "flight-server/filtered-lifecycle.log").as_posix(),
            MAX_TEXT_BYTES,
        )
        lifecycle = _regular_file(
            repository_root,
            (EVIDENCE_ROOT / "flight-server/lifecycle.txt").as_posix(),
            MAX_TEXT_BYTES,
        )
    except EvidenceError as exc:
        errors.append(str(exc))
        return False
    trips = summary.get("round_trips")
    processes = summary.get("processes")
    legs = ledger.get("legs")
    cases = matrix.get("cases")
    if (
        summary.get("schema_version") != 1
        or summary.get("artifact_sha256") != artifact_hash
        or summary.get("artifact_version") != EXPECTED_BUILD
        or summary.get("critical_or_high_findings") != 0
        or summary.get("same_world_verified") is not True
        or summary.get("single_authority_after_each_leg") is not True
        or summary.get("restart_matrix_cases") != 8
        or summary.get("restart_matrix_passed") != 8
        or not isinstance(processes, list)
        or len(processes) != 17
        or not all(isinstance(process, dict) for process in processes)
        or any(process.get("exit_code") != 0 for process in processes)
        or not isinstance(trips, dict)
        or trips.get("round_trips") != 20
        or trips.get("legs") != 40
        or trips.get("exact_fuel_debits") is not True
        or trips.get("container_inventory_conserved") is not True
        or trips.get("material_conserved_after_disassembly") is not True
        or ledger.get("schema_version") != 1
        or ledger.get("round_trips") != 20
        or not isinstance(legs, list)
        or len(legs) != 40
        or not all(isinstance(leg, dict) for leg in legs)
        or matrix.get("schema_version") != 1
        or not isinstance(cases, list)
        or not all(isinstance(case, dict) for case in cases)
        or [case.get("case") for case in cases] != RESTART_CASES
    ):
        errors.append("v0.6.0 packaged flight summary or restart inventory is incomplete")
        return False
    logical_ids = {leg.get("logical_rocket_id") for leg in legs}
    numeric_ledger = all(
        type(leg.get(key)) is int
        for leg in legs
        for key in ("sequence", "trip", "fuel_before", "fuel_after", "required_fuel")
    )
    if (
        len(logical_ids) != 1
        or None in logical_ids
        or not numeric_ledger
        or any(leg.get("sequence") != index for index, leg in enumerate(legs, 1))
        or any(leg.get("trip") != (index + 1) // 2 for index, leg in enumerate(legs, 1))
        or any(leg.get("exact_debit") is not True for leg in legs)
        or (
            numeric_ledger
            and any(
                leg["fuel_before"] - leg["fuel_after"] != leg["required_fuel"]
                for leg in legs
            )
        )
        or any(leg.get("block_count") != 5 for leg in legs)
        or any(case.get("container_inventory_conserved") is not True for case in cases)
        or any(case.get("exact_disassembly") is not True for case in cases)
    ):
        errors.append("v0.6.0 flight ledger violates identity, fuel, or conservation invariants")
    for path in (filtered, lifecycle):
        try:
            text = path.read_text(encoding="utf-8", errors="strict")
        except UnicodeError as exc:
            errors.append(str(exc))
            continue
        if MACHINE_PATH.search(text):
            errors.append(f"machine-local path leaked into {path.name}")
    return not errors


def _validate_multiplayer(
    repository_root: Path, artifact_hash: str, tested_commit: str, errors: list[str]
) -> bool:
    try:
        summary = _load_json(repository_root, EVIDENCE_ROOT / "multiplayer/summary.json")
    except EvidenceError as exc:
        errors.append(str(exc))
        return False
    server = summary.get("server")
    clients = summary.get("clients")
    observations = summary.get("observations")
    limitations = summary.get("limitations")
    if (
        summary.get("schema_version") != 1
        or summary.get("version") != EXPECTED_VERSION
        or summary.get("build") != EXPECTED_BUILD
        or summary.get("artifact_sha256") != artifact_hash
        or summary.get("tested_implementation_commit") != tested_commit
        or not isinstance(server, dict)
        or server.get("kind") != "packaged_forge_server"
        or server.get("loopback_only") is not True
        or server.get("clean_save_and_stop") is not True
        or not isinstance(clients, list)
        or not all(isinstance(client, dict) for client in clients)
        or [client.get("username") for client in clients] != ["Dev", "PilotB"]
        or any(client.get("kind") != "forge_userdev" for client in clients)
        or any(client.get("source_commit") != tested_commit for client in clients)
        or any(client.get("connected_to_modded_server") is not True for client in clients)
        or any(client.get("received_shared_marker") is not True for client in clients)
        or any(client.get("clean_shutdown") is not True for client in clients)
        or not isinstance(observations, dict)
        or observations.get("simultaneous_players") != 2
        or observations.get("catalog_generation") != 1
        or observations.get("catalog_bytes") != 469
        or observations.get("same_server_marker_received_by_both") is not True
        or observations.get("client_linkage_failures") != 0
        or not isinstance(limitations, list)
        or not any("No screenshot or video" in item for item in limitations)
    ):
        errors.append("v0.6.0 two-client packaged-server evidence is incomplete")

    logs = summary.get("logs")
    recorded: set[str] = set()
    if not isinstance(logs, list):
        errors.append("v0.6.0 multiplayer evidence has no log inventory")
    else:
        for record in logs:
            raw = record.get("file") if isinstance(record, dict) else None
            if not isinstance(raw, str) or Path(raw).name != raw:
                errors.append("v0.6.0 multiplayer log path is unsafe")
                continue
            try:
                path = _regular_file(
                    repository_root,
                    (EVIDENCE_ROOT / "multiplayer" / raw).as_posix(),
                    MAX_TEXT_BYTES,
                )
                text = path.read_text(encoding="utf-8", errors="strict")
            except (EvidenceError, UnicodeError) as exc:
                errors.append(str(exc))
                continue
            recorded.add(raw)
            if record.get("sha256") != _sha256(path) or MACHINE_PATH.search(text):
                errors.append(f"v0.6.0 multiplayer log binding differs: {raw}")
    actual = {
        path.name
        for path in (repository_root / EVIDENCE_ROOT / "multiplayer").glob("*.txt")
    }
    if recorded != MULTIPLAYER_LOGS or actual != MULTIPLAYER_LOGS:
        errors.append("v0.6.0 multiplayer log inventory is incomplete or contains extras")
    return not errors


def _validate_performance(repository_root: Path, errors: list[str]) -> bool:
    try:
        summary = _load_json(repository_root, EVIDENCE_ROOT / "performance/summary.json")
    except EvidenceError as exc:
        errors.append(str(exc))
        return False
    atmosphere = summary.get("atmosphere_soak")
    limits = summary.get("flight_limits")
    flight = summary.get("packaged_flight")
    if (
        summary.get("schema_version") != 1
        or summary.get("version") != EXPECTED_VERSION
        or not all(isinstance(item, dict) for item in (atmosphere, limits, flight))
        or atmosphere.get("result") != "PASS"
        or atmosphere.get("vents") != 16
        or atmosphere.get("active_vents") != 16
        or atmosphere.get("simulated_ticks") != 6000
        or atmosphere.get("peak_inspections", 1025)
        > atmosphere.get("maximum_level_inspections_per_tick", 1024)
        or limits
        != {
            "active_transfers": 64,
            "intent_window_ticks": 20,
            "landing_block_inspections": 2048,
            "landing_chunks": 16,
            "landing_pad_candidates": 8,
            "passengers": 16,
            "player_intents_per_window": 8,
            "transfer_entity_matches": 64,
        }
        or flight.get("result") != "PASS"
        or flight.get("round_trips") != 20
        or flight.get("flight_legs") != 40
        or flight.get("restart_cases") != 8
        or flight.get("restart_failures") != 0
    ):
        errors.append("v0.6.0 performance evidence is incomplete")
    return not errors


def _validate_owner(
    repository_root: Path, artifact_hash: str, tested_commit: str, errors: list[str]
) -> tuple[dict[str, Any], bool]:
    try:
        attestation = _load_json(
            repository_root, EVIDENCE_ROOT / "manual/owner-attestation.json"
        )
        waiver_path = _regular_file(
            repository_root, WAIVER_RECORD.as_posix(), MAX_TEXT_BYTES
        )
        waiver = waiver_path.read_text(encoding="utf-8", errors="strict")
    except (EvidenceError, UnicodeError) as exc:
        errors.append(str(exc))
        return {}, False
    approvals = attestation.get("approvals")
    basis = attestation.get("acceptance_basis")
    limitations = attestation.get("limitations_accepted")
    human_approved = bool(
        attestation.get("schema_version") == 1
        and attestation.get("version") == EXPECTED_VERSION
        and attestation.get("approved_by") in AUTHORIZED_REVIEWERS
        and _valid_date(attestation.get("approved_at"))
        and isinstance(approvals, dict)
        and approvals
        == {
            "G0_provenance": "PASS",
            "G8_visible_client_acceptance": "PASS",
            "G9_release_acceptance": "PASS",
        }
        and isinstance(basis, dict)
        and basis.get("artifact_sha256") == artifact_hash
        and basis.get("tested_implementation_commit") == tested_commit
        and basis.get("waiver") == WAIVER_RECORD.as_posix()
        and isinstance(limitations, list)
        and any("No screenshot or video" in item for item in limitations)
        and all(
            marker in waiver
            for marker in (
                "**Status:** Accepted",
                "**Owner:** `sunthemoon`",
                "**Expires:** when v0.7.0 validation begins",
                "## Risk and user impact",
                "## Mitigation",
                "## Recollection condition",
                "## Automatic failure reminder",
            )
        )
    )
    if not human_approved:
        errors.append("v0.6.0 G0/G8/G9 owner attestation or ADR is incomplete")
    return attestation, human_approved


def _validate_docs(repository_root: Path, errors: list[str]) -> bool:
    for name in sorted(REQUIRED_RELEASE_DOCS):
        try:
            _regular_file(repository_root, (RELEASE_ROOT / name).as_posix(), MAX_TEXT_BYTES)
        except EvidenceError as exc:
            errors.append(str(exc))
    return not errors


def _validate_checksums(
    repository_root: Path, artifact_summary: dict[str, Any], errors: list[str]
) -> bool:
    try:
        path = _regular_file(
            repository_root, (RELEASE_ROOT / "checksums.txt").as_posix(), MAX_TEXT_BYTES
        )
        lines = path.read_text(encoding="utf-8", errors="strict").splitlines()
    except (EvidenceError, UnicodeError) as exc:
        errors.append(str(exc))
        return False
    recorded: dict[str, str] = {}
    for line_number, line in enumerate(lines, start=1):
        if not line or line.startswith("#"):
            continue
        parts = line.split(None, 1)
        if len(parts) != 2 or SHA256.fullmatch(parts[0]) is None:
            errors.append(f"v0.6.0 checksums line {line_number} is invalid")
            continue
        try:
            relative = _safe_relative(parts[1].strip()).as_posix()
        except ValueError as exc:
            errors.append(str(exc))
            continue
        if relative in recorded:
            errors.append(f"v0.6.0 checksums repeats {relative}")
        recorded[relative] = parts[0]
    evidence_files = {
        item.relative_to(repository_root).as_posix()
        for item in (repository_root / EVIDENCE_ROOT).rglob("*")
        if item.is_file()
    }
    main = artifact_summary.get("main_jar", {})
    artifact_path = main.get("path")
    expected = evidence_files | {artifact_path}
    if artifact_path is None:
        errors.append("v0.6.0 artifact checksum path is missing")
        expected.discard(None)
    if set(recorded) != expected:
        errors.append("v0.6.0 checksums inventory is incomplete or contains extras")
    for relative in sorted(evidence_files):
        try:
            actual = _sha256(_regular_file(repository_root, relative, MAX_EVIDENCE_BYTES))
        except EvidenceError as exc:
            errors.append(str(exc))
            continue
        if recorded.get(relative) != actual:
            errors.append(f"v0.6.0 checksum mismatch: {relative}")
    if recorded.get(artifact_path) != main.get("sha256"):
        errors.append("v0.6.0 checksums omit or change the artifact binding")
    return not errors


def validate_v060_release_evidence(
    repository_root: Path = ROOT,
    artifact: Path | None = None,
    *,
    require_approved: bool = False,
) -> tuple[list[str], dict[str, Any]]:
    """Return validation errors and v0.6.0 Gate readiness details."""
    repository_root = repository_root.resolve()
    errors: list[str] = []

    before = len(errors)
    provenance_ready = _validate_provenance(repository_root, errors) and len(errors) == before
    before = len(errors)
    artifact_summary, artifact_hash, tested_commit = _validate_artifact(
        repository_root, artifact, errors
    )
    artifact_ready = len(errors) == before and bool(artifact_hash)
    before = len(errors)
    post_merge_ready = _validate_post_merge(repository_root, artifact_hash, errors)
    post_merge_ready = post_merge_ready and len(errors) == before
    before = len(errors)
    automated = _validate_automated(repository_root, artifact_hash, tested_commit, errors)
    automated_ready = len(errors) == before and bool(automated)
    before = len(errors)
    dedicated_ready = _validate_dedicated_server(repository_root, artifact_hash, errors)
    dedicated_ready = dedicated_ready and len(errors) == before
    before = len(errors)
    flight_ready = _validate_flight_server(repository_root, artifact_hash, errors)
    flight_ready = flight_ready and len(errors) == before
    before = len(errors)
    multiplayer_ready = _validate_multiplayer(
        repository_root, artifact_hash, tested_commit, errors
    )
    multiplayer_ready = multiplayer_ready and len(errors) == before
    before = len(errors)
    performance_ready = _validate_performance(repository_root, errors)
    performance_ready = performance_ready and len(errors) == before
    before = len(errors)
    attestation, human_approved = _validate_owner(
        repository_root, artifact_hash, tested_commit, errors
    )
    client_ready = multiplayer_ready and human_approved and len(errors) == before
    before = len(errors)
    docs_ready = _validate_docs(repository_root, errors) and len(errors) == before
    before = len(errors)
    checksums_ready = _validate_checksums(repository_root, artifact_summary, errors)
    checksums_ready = checksums_ready and len(errors) == before
    if require_approved and not human_approved:
        errors.append("v0.6.0 evidence has not received explicit G0/G8/G9 owner approval")

    security = automated.get("security", {}) if isinstance(automated, dict) else {}
    details = {
        "artifact_sha256": artifact_hash,
        "tested_implementation_commit": tested_commit,
        "provenance_ready": provenance_ready,
        "artifact_ready": artifact_ready,
        "post_merge_ready": post_merge_ready,
        "data_ready": provenance_ready and automated_ready,
        "automated_ready": automated_ready,
        "server_ready": dedicated_ready and flight_ready and multiplayer_ready,
        "persistence_ready": flight_ready,
        "authority_ready": automated_ready
        and flight_ready
        and security.get("server_recomputed_flight_plan") is True
        and security.get("client_final_decisions") == 0,
        "performance_ready": performance_ready,
        "client_ready": client_ready,
        "docs_ready": docs_ready and checksums_ready and post_merge_ready,
        "checksums_ready": checksums_ready,
        "human_approved": human_approved,
        "human_approved_at": attestation.get("approved_at")
        if isinstance(attestation, dict)
        else None,
    }
    return errors, details


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifact", type=Path)
    parser.add_argument("--require-approved", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    errors, details = validate_v060_release_evidence(
        ROOT, artifact=args.artifact, require_approved=args.require_approved
    )
    if errors:
        for error in errors:
            print(f"[FAIL] {error}")
        return 1
    print(
        "[PASS] v0.6.0 release evidence is complete: "
        + ", ".join(key for key, value in details.items() if value is True)
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
