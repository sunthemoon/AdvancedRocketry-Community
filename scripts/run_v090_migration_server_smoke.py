#!/usr/bin/env python3
"""Exercise packaged schema-1 backup, migration, rollback boundary, and restart."""

from __future__ import annotations

import argparse
import gzip
import json
import os
import re
import shutil
import struct
import sys
from datetime import datetime, timezone
from pathlib import Path

if __package__:
    from .run_dedicated_server_smoke import (
        SmokeError,
        digest_file,
        is_link_or_junction,
        resolve_java,
    )
    from .run_v060_flight_server_smoke import FlightHarness, _load_summary, _verify_inputs
    from .validate_v090_migration_fixtures import (
        FIXTURE_ROOT,
        MANIFEST_NAME,
        verify as verify_fixtures,
    )
else:
    from run_dedicated_server_smoke import (
        SmokeError,
        digest_file,
        is_link_or_junction,
        resolve_java,
    )
    from run_v060_flight_server_smoke import FlightHarness, _load_summary, _verify_inputs
    from validate_v090_migration_fixtures import (
        FIXTURE_ROOT,
        MANIFEST_NAME,
        verify as verify_fixtures,
    )


ROOT = Path(__file__).resolve().parents[1]
EXPECTED_VERSION = "1.20.1-0.9.0-beta.1"
MAX_COPY_FILES = 20_000
MAX_COPY_BYTES = 2 * 1024 * 1024 * 1024
DATA_VERSION = 3465
MIGRATED_LOG = re.compile(
    r"\[ARCE-BETA-1002\] Beta world data check complete: "
    r"managed=5, migrated=5, backup=([^\s]+)"
)
CURRENT_LOG = re.compile(
    r"\[ARCE-BETA-1001\] Beta world data check complete: "
    r"managed=5, migrated=0, backup=none"
)
REPORT_LOG = re.compile(
    r"ARCE-BETA-1101 build=[^\s]+ forge=[^\s]+ jei=[^\s]+ "
    r"root_schema=2 operational=true roots=11111 bodies=0 transactions=0 "
    r"transfers=0 stations=0 missions=0 players=0/\d+ "
    r"atmosphere_volume=\d+ atmosphere_tick=\d+ "
    r"protocols=life:\d+,celestial:\d+,flight:\d+,visual:\d+ "
    r"flight_frame_max=\d+ ticket_policy=transient_transfer_only"
)
FIXTURES = {
    "advancedrocketrycommunity_celestial.dat": (
        "v030-celestial-v1.snbt",
        "{schema_version:1,bodies:[]}\n",
        (("list", "bodies", 10, ()),),
    ),
    "advancedrocketrycommunity_rocket_transactions.dat": (
        "v050-rocket-transactions-v1.snbt",
        "{schema_version:1,transactions:[]}\n",
        (("list", "transactions", 10, ()),),
    ),
    "advancedrocketrycommunity_rocket_transfers.dat": (
        "v060-rocket-transfers-v1.snbt",
        "{schema_version:1,transfers:[]}\n",
        (("list", "transfers", 10, ()),),
    ),
    "advancedrocketrycommunity_stations.dat": (
        "v070-stations-v1.snbt",
        "{schema_version:1,stations:[],reservations:[]}\n",
        (
            ("list", "stations", 10, ()),
            ("list", "reservations", 10, ()),
        ),
    ),
    "advancedrocketrycommunity_satellite_missions.dat": (
        "v080-satellite-missions-v1.snbt",
        "{schema_version:1,clock:{logical_game_time:1200L,last_observed_game_time:1200L},satellites:[],missions:[],research_accounts:[]}\n",
        (
            (
                "compound",
                "clock",
                (
                    ("long", "logical_game_time", 1200),
                    ("long", "last_observed_game_time", 1200),
                ),
            ),
            ("list", "satellites", 10, ()),
            ("list", "missions", 10, ()),
            ("list", "research_accounts", 10, ()),
        ),
    ),
}


def _utf(value: str) -> bytes:
    encoded = value.encode("utf-8")
    if len(encoded) > 65_535:
        raise SmokeError("NBT name exceeds the modified-UTF fixture bound")
    return struct.pack(">H", len(encoded)) + encoded


def _named_tag(tag_type: int, name: str, payload: bytes) -> bytes:
    return bytes((tag_type,)) + _utf(name) + payload


def _compound_payload(fields: tuple[tuple[object, ...], ...]) -> bytes:
    output = bytearray()
    for field in fields:
        kind = field[0]
        name = str(field[1])
        if kind == "int":
            output.extend(_named_tag(3, name, struct.pack(">i", int(field[2]))))
        elif kind == "long":
            output.extend(_named_tag(4, name, struct.pack(">q", int(field[2]))))
        elif kind == "list":
            element_type = int(field[2])
            values = tuple(field[3])
            if values:
                raise SmokeError("The archived Beta seed writer only permits empty root lists")
            output.extend(_named_tag(9, name, bytes((element_type,)) + struct.pack(">i", 0)))
        elif kind == "compound":
            nested = _compound_payload(tuple(field[2]))
            output.extend(_named_tag(10, name, nested))
        else:
            raise SmokeError(f"Unsupported fixture field kind: {kind}")
    output.append(0)
    return bytes(output)


def _saved_data_bytes(fields: tuple[tuple[object, ...], ...]) -> bytes:
    data_fields = (("int", "schema_version", 1),) + fields
    outer = (
        _named_tag(10, "data", _compound_payload(data_fields))
        + _named_tag(3, "DataVersion", struct.pack(">i", DATA_VERSION))
        + b"\x00"
    )
    root = bytes((10,)) + _utf("") + outer
    return gzip.compress(root, compresslevel=9, mtime=0)


def seed_alpha_files(
    world: Path,
    repository_root: Path = ROOT,
) -> dict[str, dict[str, object]]:
    fixture_errors = verify_fixtures(repository_root)
    if fixture_errors:
        raise SmokeError("Migration fixtures failed verification: " + "; ".join(fixture_errors))
    data = world / "data"
    data.mkdir(parents=True, exist_ok=True)
    if is_link_or_junction(data):
        raise SmokeError("World data directory is linked or reparse-backed")

    seeded: dict[str, dict[str, object]] = {}
    fixture_directory = repository_root / FIXTURE_ROOT
    for target_name, (fixture_name, expected_text, fields) in sorted(FIXTURES.items()):
        fixture_path = fixture_directory / fixture_name
        if fixture_path.read_text(encoding="utf-8", errors="strict") != expected_text:
            raise SmokeError(f"Binary seed contract differs from archived fixture {fixture_name}")
        target = data / target_name
        old = data / f"{target_name}_old"
        if is_link_or_junction(target) or is_link_or_junction(old):
            raise SmokeError("Managed SavedData target is linked or reparse-backed")
        target.unlink(missing_ok=True)
        old.unlink(missing_ok=True)
        target.write_bytes(_saved_data_bytes(tuple(fields)))
        seeded[target_name] = {
            "fixture": fixture_name,
            "fixture_sha256": digest_file(fixture_path),
            "source_sha256": digest_file(target),
            "source_bytes": target.stat().st_size,
        }
    return seeded


def _copy_server(source: Path, destination: Path) -> None:
    if is_link_or_junction(source) or not source.is_dir():
        raise SmokeError("Source packaged server directory is missing or unsafe")
    if destination.exists() or is_link_or_junction(destination):
        raise SmokeError(f"Refusing to overwrite migration smoke session: {destination}")
    files = 0
    total = 0
    for path in source.rglob("*"):
        if is_link_or_junction(path):
            raise SmokeError(f"Source packaged server contains a linked path: {path.name}")
        if path.is_file():
            files += 1
            total += path.stat().st_size
            if files > MAX_COPY_FILES or total > MAX_COPY_BYTES:
                raise SmokeError("Source packaged server exceeds the bounded copy inventory")
    shutil.copytree(source, destination)
    logs = destination / "logs"
    if logs.exists():
        shutil.rmtree(logs)
    for path in destination.glob("*-full.txt"):
        path.unlink()


def _find_log(
    process,
    pattern: re.Pattern[str],
) -> tuple[re.Match[str], str]:  # type: ignore[no-untyped-def]
    for line in process.lines:
        match = pattern.search(line)
        if match is not None:
            return match, line.rstrip()
    raise SmokeError(f"Missing packaged migration marker: {pattern.pattern}")


def _run_report(process) -> str:  # type: ignore[no-untyped-def]
    start = len(process.lines)
    process.command("arce beta report")
    index = process.wait_for(REPORT_LOG, 30.0, start_at=start)
    return process.lines[index].rstrip()


def validate_backup(
    world: Path,
    backup_name: str,
    seeded: dict[str, dict[str, object]],
) -> dict[str, object]:
    if re.fullmatch(r"[0-9]{8}T[0-9]{6}\.[0-9]{3}Z-schema1-to2", backup_name) is None:
        raise SmokeError("Migration backup name is not bounded and canonical")
    backup = world / "advancedrocketrycommunity-backups" / backup_name
    manifest_path = backup / "manifest.json"
    if is_link_or_junction(backup) or not backup.is_dir():
        raise SmokeError("Migration backup directory is missing or unsafe")
    if is_link_or_junction(manifest_path) or not manifest_path.is_file():
        raise SmokeError("Migration backup manifest is missing or unsafe")
    if manifest_path.stat().st_size > 65_536:
        raise SmokeError("Migration backup manifest exceeds the byte limit")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8", errors="strict"))
    if not isinstance(manifest, dict) or manifest.get("manifestSchema") != 1:
        raise SmokeError("Migration backup manifest schema is invalid")
    entries = manifest.get("files")
    if not isinstance(entries, list) or len(entries) != len(seeded):
        raise SmokeError("Migration backup manifest file inventory is invalid")
    indexed = {entry.get("file"): entry for entry in entries if isinstance(entry, dict)}
    for name, source in seeded.items():
        backup_file = backup / name
        if is_link_or_junction(backup_file) or not backup_file.is_file():
            raise SmokeError(f"Migration backup is missing {name}")
        expected = source["source_sha256"]
        if digest_file(backup_file) != expected or indexed.get(name, {}).get("sha256") != expected:
            raise SmokeError(f"Migration backup is not byte-exact for {name}")
    return {
        "directory": backup_name,
        "manifest_sha256": digest_file(manifest_path),
        "file_count": len(entries),
    }


def _write_evidence(
    directory: Path,
    summary: dict[str, object],
    filtered_lines: list[str],
) -> None:
    if directory.exists() or is_link_or_junction(directory):
        raise SmokeError(f"Refusing to overwrite migration smoke evidence: {directory}")
    directory.mkdir(parents=True)
    (directory / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=True, indent=2, sort_keys=True) + "\n",
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
        evidence = args.evidence_dir.resolve()
        baseline = _load_summary(args.baseline_summary.resolve())
        _copy_server(source, session)
        port, artifact_sha256 = _verify_inputs(session, baseline, args.expected_version)
        java, java_version = resolve_java(args.java)
        world = session / "world"
        if not (world / "level.dat").is_file():
            raise SmokeError("Copied baseline does not contain the accepted smoke world")
        seeded = seed_alpha_files(world)
        fixture_manifest = ROOT / FIXTURE_ROOT / MANIFEST_NAME
        harness = FlightHarness(
            java=java,
            server=session,
            port=port,
            expected_version=args.expected_version,
            startup_timeout=args.startup_timeout,
        )

        process = harness.start("v090-migration")
        migration_match, migration_line = _find_log(process, MIGRATED_LOG)
        first_report_line = _run_report(process)
        harness.stop(process)
        process = None
        backup = validate_backup(world, migration_match.group(1), seeded)
        migrated_hashes = {
            name: digest_file(world / "data" / name)
            for name in sorted(seeded)
        }
        if any(migrated_hashes[name] == seeded[name]["source_sha256"] for name in seeded):
            raise SmokeError("At least one schema-1 file was not replaced by schema-2 bytes")

        process = harness.start("v090-current-restart")
        _, current_line = _find_log(process, CURRENT_LOG)
        second_report_line = _run_report(process)
        harness.stop(process)
        process = None

        summary = {
            "schema_version": 1,
            "tested_commit": args.tested_commit,
            "mod_version": args.expected_version,
            "artifact_sha256": artifact_sha256,
            "fixture_manifest_sha256": digest_file(fixture_manifest),
            "java_version": java_version,
            "started_at": harness.process_documents[0]["started_at"],
            "completed_at": datetime.now(timezone.utc).isoformat(),
            "diagnostics": ["ARCE-BETA-1002", "ARCE-BETA-1001"],
            "seeded_files": seeded,
            "migrated_sha256": migrated_hashes,
            "backup": backup,
            "cycles": harness.process_documents,
            "restart_current": True,
            "operator_report_operational": True,
        }
        filtered = [
            migration_line,
            first_report_line,
            current_line,
            second_report_line,
        ]
        _write_evidence(evidence, summary, filtered)
        print(f"[PASS] Migrated {len(seeded)} schema-1 SavedData roots")
        print(f"[PASS] Byte-exact backup: {backup['directory']}")
        print("[PASS] Packaged restart reported schema 2 current and operational")
        return 0
    except (OSError, ValueError, json.JSONDecodeError, SmokeError) as error:
        if process is not None:
            process.abort()
        print(f"[FAIL] {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
