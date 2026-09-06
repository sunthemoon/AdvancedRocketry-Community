#!/usr/bin/env python3
"""Kill owned servers at real, flushed rocket transaction stages, then restart twice."""

from __future__ import annotations

import argparse
import gzip
import json
import os
import re
import shutil
import sys
import uuid
from datetime import datetime, timezone
from pathlib import Path

if __package__:
    from . import run_dedicated_server_smoke as server_smoke
    from . import run_v060_flight_server_smoke as flight
    from . import run_v100_flight_forced_stop as recovery
    from .inspect_celestial_saved_data import NbtReader
    from .run_v090_migration_server_smoke import _copy_server
else:
    import run_dedicated_server_smoke as server_smoke
    import run_v060_flight_server_smoke as flight
    import run_v100_flight_forced_stop as recovery
    from inspect_celestial_saved_data import NbtReader
    from run_v090_migration_server_smoke import _copy_server


SmokeError = server_smoke.SmokeError
RUN_TOKEN = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
CASES = tuple(
    f"{operation}:{phase}:{progress}"
    for operation, mutation, finals in (
        ("ASSEMBLY", "EXTRACTING", ("EXTRACTED", "SPAWNED", "COMMITTED")),
        ("DISASSEMBLY", "RESTORING", ("RESTORED", "COMMITTED")),
    )
    for phase, progress in (
        ("SNAPSHOT_VALIDATED", 0), ("LOCKED", 0),
        *((mutation, progress) for progress in range(1, 6)),
        *((phase, 5) for phase in finals),
        ("ROLLING_BACK", 2), ("ROLLED_BACK", 2), ("FAILED", 1),
    )
)
UUID_PATTERN = r"[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"
PAUSED = re.compile(
    r"ARCE_RELEASE_TRANSACTION_PAUSED type=([A-Z_]+) phase=([A-Z_]+) progress=(\d+) "
    rf"transaction=({UUID_PATTERN}) entity=({UUID_PATTERN}|none) "
    r"snapshot=([0-9a-f]{64}) blocks=(\d+) dimension=([^ ]+) "
    r"origin=(-?\d+),(-?\d+),(-?\d+)"
)
VALIDATED = re.compile(
    r"ARCE_ROCKET_SCAN operation=validate code=SUCCESS dimension=minecraft:overworld "
    r"assembler=384, 100, 384 blocks=(\d+) snapshot=([0-9a-f]{64}) detail=validated"
)
RECOVERED = re.compile(r"ARCE_ROCKET_RECOVERY outcome=([A-Z_]+)")
OPERATOR = re.compile(
    r"ARCE-BETA-1101 build=([^ ]+) forge=[^ ]+ jei=[^ ]+ root_schema=2 "
    r"operational=true roots=11111 bodies=\d+ transactions=(\d+) "
    r"transfers=0 stations=0 missions=0 players=0/\d+ "
)
JOURNAL = "advancedrocketrycommunity_rocket_transactions.dat"
BLOCKS = (flight.FUEL_TANK, flight.MOTOR, flight.CHEST, flight.SEAT, flight.GUIDANCE)


def authority(case: str) -> str:
    operation, phase, _ = case.split(":")
    if operation == "ASSEMBLY":
        return "ENTITY" if phase in {"SPAWNED", "COMMITTED"} else "BLOCKS"
    return "BLOCKS" if phase in {"RESTORED", "COMMITTED"} else "ENTITY"


def validate_pause(case: str, match: re.Match[str], before: dict) -> dict:
    kind, phase, progress, transaction, entity, snapshot, blocks, dimension, x, y, z = match.groups()
    expected_entity = kind == "DISASSEMBLY" or phase in {"SPAWNED", "COMMITTED"}
    if (f"{kind}:{phase}:{progress}" != case or snapshot != before["snapshot"]
            or int(blocks) != 5 or dimension != flight.EARTH
            or [int(x), int(y), int(z)] != [flight.X, flight.Y + 1, flight.Z]
            or (entity != "none") != expected_entity):
        raise SmokeError("Observed checkpoint differs from the requested transaction and fixture")
    if "entity" in before and entity != before["entity"]:
        raise SmokeError("Checkpoint changed the assembled entity identity")
    return dict(type=kind, phase=phase, progress=int(progress), transaction=transaction,
                entity=entity, snapshot=snapshot, blocks=int(blocks), dimension=dimension,
                origin=[int(x), int(y), int(z)])


def uuid_from_nbt(value: object) -> str:
    if (not isinstance(value, list) or len(value) != 4
            or any(type(part) is not int or not -(2**31) <= part < 2**31 for part in value)):
        raise SmokeError("Journal UUID must be a four-integer array")
    return str(uuid.UUID(bytes=b"".join((part & 0xffffffff).to_bytes(4, "big") for part in value)))


def read_journal(path: Path) -> dict:
    if server_smoke.is_link_or_junction(path) or not path.is_file() or path.stat().st_size > 1024**2:
        raise SmokeError("Journal is missing, unsafe or exceeds 1 MiB")
    with gzip.open(path, "rb") as stream:
        payload = stream.read(4 * 1024**2 + 1)
    if len(payload) > 4 * 1024**2:
        raise SmokeError("Journal exceeds the 4 MiB decoded fixture bound")
    root = NbtReader(payload).read_root()
    data = root.get("data") if isinstance(root, dict) else None
    if not isinstance(data, dict) or data.get("schema_version") != 2:
        raise SmokeError("Journal must retain the existing schema 2")
    if not isinstance(data.get("transactions"), list):
        raise SmokeError("Journal transactions must be a list")
    return data


def validate_journal(data: dict, paused: dict) -> None:
    entries = data["transactions"]
    if len(entries) != 1 or not isinstance(entries[0], dict):
        raise SmokeError("Checkpoint must persist exactly one real transaction")
    record = entries[0]
    for key in ("type", "phase", "progress", "dimension"):
        if record.get(key) != paused[key]:
            raise SmokeError(f"Persisted journal changed {key}")
    if (record.get("content_hash") != paused["snapshot"]
            or uuid_from_nbt(record.get("transaction_id")) != paused["transaction"]):
        raise SmokeError("Persisted journal changed transaction or snapshot identity")
    entity = uuid_from_nbt(record["rocket_entity_id"]) if "rocket_entity_id" in record else "none"
    if entity != paused["entity"]:
        raise SmokeError("Persisted journal changed entity identity")


def validate_entity(report: dict, paused: dict, before: dict) -> None:
    expected = dict(entity=paused["entity"], snapshot=paused["snapshot"], dimension=flight.EARTH,
                    state="ASSEMBLED", fuel=0, capacity=1000, passengers=0, transfer="none",
                    origin=paused["origin"], blocks=5)
    if "logical" in before:
        expected["logical"] = before["logical"]
    if any(report.get(key) != value for key, value in expected.items()):
        raise SmokeError("Recovered entity violated identity, state or resource conservation")


class TransactionHarness(flight.FlightHarness):
    def start(self, name: str, checkpoint: str | None = None):
        command = flight._server_command(self.java)
        if checkpoint is not None:
            if checkpoint not in CASES:
                raise SmokeError("Checkpoint is outside the bounded transaction matrix")
            command.insert(1, f"-Dadvancedrocketrycommunity.transactionCheckpoint={checkpoint}")
        log = self.server / f"{RUN_TOKEN}-v100-txn-{name}-full.txt"
        process = server_smoke.CapturedProcess(command, self.server, log)
        process._arce_name = name
        process._arce_log_path = log
        process._arce_started_at = datetime.now(timezone.utc).isoformat()
        try:
            process.wait_for(server_smoke.READY_MARKER, self.startup_timeout)
            server_smoke.validate_status_identity(server_smoke.wait_for_status(self.port), self.expected_version)
            return process
        except BaseException:
            process.abort()
            raise

    def operator_report(self, process) -> str:
        start = len(process.lines)
        process.command("arce beta report")
        index = process.wait_for(OPERATOR, 30.0, start_at=start)
        match = OPERATOR.search(process.lines[index])
        if match.group(1) != self.expected_version or match.group(2) != "0":
            raise SmokeError("Recovery left a pending transaction or changed the build identity")
        return match.group(0)

    def contents(self, process) -> dict:
        start = len(process.lines)
        process.command(f"arce rocket validate {flight._position(flight.ASSEMBLER)}")
        index = process.wait_for(VALIDATED, 45.0, start_at=start)
        match = VALIDATED.search(process.lines[index])
        if match.group(1) != "5":
            raise SmokeError("Restored structure does not have exactly five blocks")
        start = len(process.lines)
        process.command(f"data get block {flight._position(flight.CHEST)} Items")
        index = process.wait_for(flight.BLOCK_DATA_MARKER, 30.0, start_at=start)
        items = flight.BLOCK_DATA_MARKER.search(process.lines[index]).group(1)
        return dict(snapshot=match.group(2), items_snbt=items)

    def material_authority(self, process, expected: str, entity: str, marker: str):
        process.command("scoreboard objectives add arce_v100_txn dummy")
        process.command("scoreboard players set #rockets arce_v100_txn 0")
        process.command("execute as @e[type=advancedrocketrycommunity:rocket] "
                        "run scoreboard players add #rockets arce_v100_txn 1")
        count = 1 if expected == "ENTITY" else 0
        conditions = f"if score #rockets arce_v100_txn matches {count} "
        if expected == "ENTITY":
            conditions += f"if entity {entity} "
            conditions += " ".join(f"if block {flight._position(pos)} minecraft:air" for pos in BLOCKS) + " "
        conditions += ("unless entity @e[type=minecraft:item,x=382,y=100,z=383,dx=4,dy=4,dz=2] ")
        try:
            self.command_marker(process, f"execute {conditions}run say {marker}", marker)
        except SmokeError:
            # Read-only diagnostics retain the failed assertion and its timeout.
            for index, pos in enumerate(BLOCKS):
                position = flight._position(pos)
                process.command(f"execute if block {position} minecraft:air run say TXN_BLOCK_{index}_AIR")
                process.command(f"execute unless block {position} minecraft:air run say TXN_BLOCK_{index}_OCCUPIED")
            process.command(f"data get block {flight._position(flight.CHEST)} Items")
            self.command_marker(process, "say TXN_DIAGNOSTICS_DONE", "TXN_DIAGNOSTICS_DONE")
            raise

    def disassemble(self, process, entity: str) -> None:
        start = len(process.lines)
        process.command(f"arce rocket release-test disassemble {entity}")
        index = process.wait_for(flight.DISASSEMBLY_LOG, 45.0, start_at=start)
        match = flight.DISASSEMBLY_LOG.search(process.lines[index])
        if match.group(1) != entity or match.groups()[2:] != ("SUCCESS", "5", "0"):
            raise SmokeError("Recovered entity could not be disassembled without material loss")

    def finish_clean(self, process) -> dict:
        self.stop(process)
        findings = server_smoke.scan_log(process.lines)
        if findings:
            raise SmokeError(f"Transaction server logged a blocking finding: {findings[0]}")
        journal = read_journal(self.server / "world/data" / JOURNAL)
        if journal["transactions"]:
            raise SmokeError("Clean shutdown persisted unresolved transaction records")
        return dict(journal_entries=0, journal_sha256=server_smoke.digest_file(self.server / "world/data" / JOURNAL))


def run_case(harness: TransactionHarness, case: str, evidence: Path) -> dict:
    name = case.lower().replace(":", "-")
    process = None
    try:
        process = harness.start(f"{name}-stage", case)
        harness.operator_report(process)
        harness.configure_rocket(process)
        before = harness.contents(process)
        harness.command_marker(process,
            f"execute if data block {flight._position(flight.CHEST)} "
            "Items[{Slot:0b,id:\"minecraft:diamond\",Count:17b}] "
            f"if data block {flight._position(flight.CHEST)} "
            "Items[{Slot:26b,id:\"minecraft:iron_ingot\",Count:64b}] "
            f"unless data block {flight._position(flight.CHEST)} Items[2] "
            f"run say TXN_{name}_CARGO_READY", f"TXN_{name}_CARGO_READY")
        if case.startswith("DISASSEMBLY:"):
            start = len(process.lines)
            process.command(f"arce rocket assemble {flight._position(flight.ASSEMBLER)}")
            index = process.wait_for(flight.ASSEMBLY_LOG, 45.0, start_at=start)
            match = flight.ASSEMBLY_LOG.search(process.lines[index])
            entity = match.group(3)
            report = harness.report(process, flight.EARTH, entity)
            if match.group(1) != "5" or report["snapshot"] != before["snapshot"]:
                raise SmokeError("Initial assembly changed the validated fixture")
            before.update(report)
            command = f"arce rocket release-test disassemble {entity}"
        else:
            command = f"arce rocket assemble {flight._position(flight.ASSEMBLER)}"
        start = len(process.lines)
        process.command(command)
        index = process.wait_for(PAUSED, 45.0, start_at=start)
        paused = validate_pause(case, PAUSED.search(process.lines[index]), before)
        # The marker follows saveEverything(flush=true). Kill immediately; do not
        # enqueue commands on the paused server thread or replace its world data.
        abrupt = recovery.force_stop(process, harness)
        process = None
        journal_path = harness.server / "world/data" / JOURNAL
        journal = read_journal(journal_path)
        validate_journal(journal, paused)
        saved_journal = evidence / f"{name}-staged.dat"
        shutil.copy2(journal_path, saved_journal)

        process = harness.start(f"{name}-recover")
        index = process.wait_for(RECOVERED, 60.0)
        outcome = RECOVERED.search(process.lines[index]).group(1)
        if outcome != "RECOVERED":
            raise SmokeError(f"Transaction recovery returned {outcome}")
        recovered = {}
        if authority(case) == "ENTITY":
            recovered = harness.report(process, flight.EARTH, paused["entity"])
            validate_entity(recovered, paused, before)
        harness.material_authority(process, authority(case), paused["entity"], f"TXN_{name}_AUTHORITY")
        if recovered:
            harness.disassemble(process, paused["entity"])
        restored = harness.contents(process)
        if restored != {key: before[key] for key in restored}:
            raise SmokeError("First restart changed the complete structure hash or chest Items")
        harness.material_authority(process, "BLOCKS", "none", f"TXN_{name}_RESTORED")
        first_operator = harness.operator_report(process)
        first_disk = harness.finish_clean(process)
        process = None

        process = harness.start(f"{name}-second-restart")
        repeated = harness.contents(process)
        if repeated != restored:
            raise SmokeError("Second restart changed the complete structure hash or chest Items")
        harness.material_authority(process, "BLOCKS", "none", f"TXN_{name}_SECOND_RESTART")
        second_operator = harness.operator_report(process)
        second_disk = harness.finish_clean(process)
        if any(RECOVERED.search(line) for line in process.lines):
            raise SmokeError("Second restart repeated transaction recovery after journal cleanup")
        process = None
        return dict(case=case, before=before, paused=paused, forced_stop=abrupt,
                    staged_journal_file=saved_journal.name,
                    staged_journal_sha256=server_smoke.digest_file(saved_journal),
                    recovery_outcome=outcome, authority=authority(case), recovered_entity=recovered,
                    restored=restored, second_restart=repeated,
                    first_operator_report=first_operator, second_operator_report=second_operator,
                    first_shutdown=first_disk, second_shutdown=second_disk)
    except BaseException:
        if process is not None:
            process.abort()
        raise


def prepare_session(args) -> tuple[Path, int, dict]:
    for path in (args.source_server_dir, args.session_dir, args.evidence_dir, args.artifact):
        if server_smoke.is_link_or_junction(path):
            raise SmokeError("Transaction test inputs and outputs must not be linked")
    source, session, output = (path.resolve() for path in (
        args.source_server_dir, args.session_dir, args.evidence_dir))
    roots = (source, session, output)
    if any(a == b or a.is_relative_to(b) or b.is_relative_to(a)
           for index, a in enumerate(roots) for b in roots[index + 1:]):
        raise SmokeError("Source, disposable session and evidence must not overlap")
    if session.exists() or output.exists():
        raise SmokeError("Refusing to overwrite a transaction session or evidence")
    baseline = flight._load_summary(args.baseline_summary)
    port, old_hash = flight._verify_inputs(source, baseline, args.expected_version)
    properties = recovery.verify_local_session(source, port)
    if properties.get("level-name", "world") != "world":
        raise SmokeError("Fixture server must use the default world directory")
    artifact = args.artifact.resolve()
    if (not artifact.is_file() or artifact.name != baseline.get("artifact")
            or not 0 < artifact.stat().st_size <= 32 * 1024**2):
        raise SmokeError("Tested artifact must match the versioned filename and bounded JAR size")
    _copy_server(source, session)
    installed = session / "mods" / artifact.name
    shutil.copy2(artifact, installed)
    artifact_hash = server_smoke.digest_file(artifact)
    if server_smoke.digest_file(installed) != artifact_hash:
        raise SmokeError("Disposable server artifact copy changed")
    return session, port, dict(artifact=artifact.name, artifact_sha256=artifact_hash,
                               template_artifact_sha256=old_hash,
                               online_mode=properties.get("online-mode"))


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_server_dir", type=Path)
    for option in ("session-dir", "baseline-summary", "evidence-dir", "artifact"):
        parser.add_argument(f"--{option}", type=Path, required=True)
    parser.add_argument("--base-commit", required=True)
    parser.add_argument("--expected-version", default="1.20.1-1.0.0-dev")
    parser.add_argument("--case", action="append", choices=CASES, dest="cases")
    home = os.environ.get("JAVA_HOME")
    parser.add_argument("--java", default=str(Path(home) / "bin/java") if home else "java")
    parser.add_argument("--startup-timeout", type=float, default=240.0)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    evidence = None
    harness = None
    summary = dict(schema_version=1, version="v1.0.0", build=args.expected_version,
                   base_commit=args.base_commit, source_state="development_worktree", status="IN_PROGRESS",
                   cases=[], requested_cases=list(dict.fromkeys(args.cases or CASES)),
                   scope="five-block unmanned real transaction stages after durable flush; not power loss during writes, passengers, Beta upgrade or performance")
    try:
        if re.fullmatch(r"[0-9a-f]{40}", args.base_commit) is None:
            raise SmokeError("Base commit must be a full lowercase SHA-1")
        session, port, inputs = prepare_session(args)
        java, java_version = server_smoke.resolve_java(args.java)
        args.evidence_dir.mkdir(parents=True, exist_ok=False)
        evidence = args.evidence_dir.resolve()
        summary.update(inputs, java=java_version, server_port=port,
                       baseline_summary_sha256=server_smoke.digest_file(args.baseline_summary),
                       harness_sha256=server_smoke.digest_file(Path(__file__)))
        harness = TransactionHarness(java=java, server=session, port=port,
                                     expected_version=args.expected_version, startup_timeout=args.startup_timeout)
        for case in summary["requested_cases"]:
            summary["cases"].append(run_case(harness, case, evidence))
            print(f"[PASS] Transaction checkpoint {case}: kill and two restarts", flush=True)
        summary.update(status="PASS", complete_transaction_matrix=len(summary["cases"]) == len(CASES))
        return 0
    except (OSError, ValueError, SmokeError) as error:
        summary.update(status="FAIL", error=str(error))
        print(f"[FAIL] {error}", file=sys.stderr)
        return 1
    finally:
        if evidence is not None:
            summary.update(completed_at=datetime.now(timezone.utc).isoformat(),
                           processes=harness.process_documents if harness else [])
            logs = []
            for source in sorted(args.session_dir.glob(f"{RUN_TOKEN}-v100-txn-*-full.txt")):
                if server_smoke.is_link_or_junction(source) or source.stat().st_size > 16 * 1024**2:
                    raise SmokeError("Transaction log is unsafe or exceeds 16 MiB")
                shutil.copy2(source, evidence / source.name)
                logs.append(dict(file=source.name, sha256=server_smoke.digest_file(evidence / source.name)))
            summary["logs"] = logs
            (evidence / "summary.json").write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n",
                                                  encoding="utf-8", newline="\n")


if __name__ == "__main__":
    sys.exit(main())
