import copy
import gzip
import sys
import tempfile
import unittest
import uuid
from pathlib import Path
from unittest.mock import Mock, patch

from scripts import run_v100_transaction_forced_stop as txn
from tests.test_run_v100_flight_forced_stop import ReceiptProcess, disassembly_lines, TRANSFER


TRANSACTION = "12345678-1234-5678-9012-123456789012"
ENTITY = "abcdef01-abcd-ef01-abcd-abcdef012345"
SNAPSHOT = "a" * 64


def pause(case, **changes):
    operation, phase, progress = case.split(":")
    values = dict(type=operation, phase=phase, progress=progress, transaction=TRANSACTION,
                  entity=ENTITY if operation == "DISASSEMBLY" or phase in {"SPAWNED", "COMMITTED"} else "none",
                  snapshot=SNAPSHOT, blocks=5, dimension=txn.flight.EARTH, origin="384,101,384")
    values.update(changes)
    return txn.PAUSED.fullmatch(
        ("ARCE_RELEASE_TRANSACTION_PAUSED type={type} phase={phase} progress={progress} "
         "transaction={transaction} entity={entity} snapshot={snapshot} blocks={blocks} "
         "dimension={dimension} origin={origin}").format(**values))


def nbt_uuid(value):
    data = uuid.UUID(value).bytes
    return [int.from_bytes(data[index:index + 4], "big", signed=True) for index in range(0, 16, 4)]


def journal(paused):
    entry = {key: paused[key] for key in ("type", "phase", "progress", "dimension")}
    entry.update(content_hash=paused["snapshot"], transaction_id=nbt_uuid(paused["transaction"]))
    if paused["entity"] != "none":
        entry["rocket_entity_id"] = nbt_uuid(paused["entity"])
    return dict(schema_version=2, transactions=[entry])


class TransactionForcedStopTests(unittest.TestCase):
    def test_matrix_covers_all_real_phases_and_every_fixture_mutation(self):
        self.assertEqual(25, len(txn.CASES))
        self.assertEqual(25, len(set(txn.CASES)))
        for operation, mutation, count in (("ASSEMBLY", "EXTRACTING", 13), ("DISASSEMBLY", "RESTORING", 12)):
            subset = [case for case in txn.CASES if case.startswith(operation + ":")]
            self.assertEqual(count, len(subset))
            self.assertTrue({f"{operation}:{mutation}:{step}" for step in range(1, 6)} <= set(subset))
            for phase, progress in (("SNAPSHOT_VALIDATED", 0), ("LOCKED", 0), ("COMMITTED", 5),
                                    ("ROLLING_BACK", 2), ("ROLLED_BACK", 2), ("FAILED", 1)):
                self.assertIn(f"{operation}:{phase}:{progress}", subset)

    def test_authority_contract_distinguishes_commit_and_rollback(self):
        self.assertEqual(12, sum(txn.authority(case) == "ENTITY" for case in txn.CASES))
        self.assertEqual("ENTITY", txn.authority("ASSEMBLY:SPAWNED:5"))
        self.assertEqual("BLOCKS", txn.authority("ASSEMBLY:FAILED:1"))
        self.assertEqual("ENTITY", txn.authority("DISASSEMBLY:FAILED:1"))
        self.assertEqual("BLOCKS", txn.authority("DISASSEMBLY:RESTORED:5"))

    def test_actual_pause_and_persisted_record_must_agree(self):
        for case in txn.CASES:
            with self.subTest(case=case):
                paused = txn.validate_pause(case, pause(case), dict(snapshot=SNAPSHOT))
                txn.validate_journal(journal(paused), paused)

    def test_changed_marker_cannot_claim_requested_checkpoint(self):
        for mutation in (dict(type="OTHER"), dict(phase="OTHER"), dict(progress="6"),
                         dict(snapshot="b" * 64), dict(blocks=6), dict(dimension="other:world"),
                         dict(origin="384,100,384"), dict(entity="none")):
            with self.subTest(mutation=mutation), self.assertRaises(txn.SmokeError):
                txn.validate_pause("ASSEMBLY:SPAWNED:5", pause("ASSEMBLY:SPAWNED:5", **mutation),
                                   dict(snapshot=SNAPSHOT))

    def test_disassembly_cannot_rebind_the_original_entity(self):
        with self.assertRaisesRegex(txn.SmokeError, "assembled entity identity"):
            txn.validate_pause("DISASSEMBLY:RESTORING:2", pause("DISASSEMBLY:RESTORING:2"),
                               dict(snapshot=SNAPSHOT, entity=TRANSACTION))

    def test_journal_mutations_are_rejected(self):
        paused = txn.validate_pause("ASSEMBLY:SPAWNED:5", pause("ASSEMBLY:SPAWNED:5"), dict(snapshot=SNAPSHOT))
        for key, value in dict(type="DISASSEMBLY", phase="EXTRACTED", progress=4,
                               dimension="other:world", content_hash="b" * 64,
                               transaction_id=nbt_uuid(ENTITY), rocket_entity_id=nbt_uuid(TRANSACTION)).items():
            data = journal(paused)
            data["transactions"][0][key] = value
            with self.subTest(key=key), self.assertRaises(txn.SmokeError):
                txn.validate_journal(data, paused)
        for entries in ([], [None], [journal(paused)["transactions"][0]] * 2):
            with self.subTest(entries=entries), self.assertRaises(txn.SmokeError):
                txn.validate_journal(dict(transactions=entries), paused)

    def test_uuid_parser_preserves_signed_nbt_and_rejects_invalid_arrays(self):
        self.assertEqual(ENTITY, txn.uuid_from_nbt(nbt_uuid(ENTITY)))
        for value in (None, [], [0] * 3, [0] * 5, [0, 0, 0, True], [0, 0, 0, 2**31]):
            with self.subTest(value=value), self.assertRaises(txn.SmokeError):
                txn.uuid_from_nbt(value)

    def test_entity_report_requires_exact_state_identity_and_resources(self):
        paused = txn.validate_pause("DISASSEMBLY:LOCKED:0", pause("DISASSEMBLY:LOCKED:0"), dict(snapshot=SNAPSHOT))
        before = dict(logical=TRANSACTION)
        report = dict(entity=ENTITY, logical=TRANSACTION, snapshot=SNAPSHOT, dimension=txn.flight.EARTH,
                      state="ASSEMBLED", fuel=0, capacity=1000, passengers=0, transfer="none",
                      origin=[384, 101, 384], blocks=5)
        txn.validate_entity(report, paused, before)
        for key in report:
            changed = copy.deepcopy(report)
            changed[key] = "changed"
            with self.subTest(key=key), self.assertRaises(txn.SmokeError):
                txn.validate_entity(changed, paused, before)

    def test_recovery_start_has_no_checkpoint_and_startup_failure_is_owned(self):
        harness = txn.TransactionHarness(java="java", server=Path("isolated"), port=25610,
                                         expected_version="1.20.1-1.0.0-dev", startup_timeout=1)
        for checkpoint in (None, "ASSEMBLY:SPAWNED:5"):
            process = Mock()
            process.wait_for.side_effect = txn.SmokeError("startup failed")
            with patch.object(txn.server_smoke, "CapturedProcess", return_value=process) as launch:
                with self.assertRaisesRegex(txn.SmokeError, "startup failed"):
                    harness.start("failed", checkpoint)
            command = launch.call_args.args[0]
            properties = [arg for arg in command if ".transactionCheckpoint=" in arg]
            self.assertEqual(0 if checkpoint is None else 1, len(properties))
            process.abort.assert_called_once_with()

    def test_unknown_checkpoint_is_rejected_before_process_launch(self):
        harness = txn.TransactionHarness(java="java", server=Path("isolated"), port=25610,
                                         expected_version="1.20.1-1.0.0-dev", startup_timeout=1)
        with patch.object(txn.server_smoke, "CapturedProcess") as launch:
            with self.assertRaises(txn.SmokeError):
                harness.start("failed", "ASSEMBLY:EXTRACTING:2049")
            launch.assert_not_called()

    def test_count_is_global_once_and_entity_authority_requires_empty_blocks(self):
        harness = txn.TransactionHarness(java="java", server=Path("isolated"), port=25610,
                                         expected_version="1.20.1-1.0.0-dev", startup_timeout=1)
        for authority, count in (("ENTITY", 1), ("BLOCKS", 0)):
            process = Mock()
            with patch.object(harness, "command_marker") as marker:
                harness.material_authority(process, authority, ENTITY, "verified")
            self.assertEqual(1, sum("as @e[" in call.args[0] for call in process.command.call_args_list))
            conditions = marker.call_args.args[1]
            self.assertIn(f"matches {count}", conditions)
            self.assertEqual(5 if authority == "ENTITY" else 0, conditions.count("minecraft:air"))
            self.assertIn("unless entity @e[type=minecraft:item", conditions)

    def test_failed_material_check_keeps_failure_and_collects_read_only_diagnostics(self):
        harness = txn.TransactionHarness(java="java", server=Path("isolated"), port=25610,
                                         expected_version="1.20.1-1.0.0-dev", startup_timeout=1)
        process = Mock()
        failure = txn.SmokeError("original assertion timed out")
        with patch.object(harness, "command_marker", side_effect=[failure, None]):
            with self.assertRaisesRegex(txn.SmokeError, "original assertion timed out"):
                harness.material_authority(process, "ENTITY", ENTITY, "verify")
        commands = [call.args[0] for call in process.command.call_args_list]
        self.assertEqual(10, sum("run say TXN_BLOCK_" in command for command in commands))
        for forbidden in ("setblock", "fill", "kill", "data merge", "data modify"):
            self.assertFalse(any(forbidden in command for command in commands))

    def test_overlapping_or_existing_outputs_fail_before_copy(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for session, output in ((root / "source/session", root / "evidence"),
                                    (root / "session", root / "session/evidence"),
                                    (root / "session", root / "source")):
                args = Mock(source_server_dir=root / "source", session_dir=session,
                            evidence_dir=output, artifact=root / "artifact.jar")
                with patch.object(txn, "_copy_server") as copy_server:
                    with self.assertRaisesRegex(txn.SmokeError, "overlap"):
                        txn.prepare_session(args)
                    copy_server.assert_not_called()
            (root / "existing").mkdir()
            args = Mock(source_server_dir=root / "source", session_dir=root / "existing",
                        evidence_dir=root / "evidence", artifact=root / "artifact.jar")
            with patch.object(txn, "_copy_server") as copy_server:
                with self.assertRaisesRegex(txn.SmokeError, "overwrite"):
                    txn.prepare_session(args)
                copy_server.assert_not_called()

    def test_unsafe_oversized_or_malformed_journal_is_not_accepted(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "journal.dat"
            with self.assertRaises(txn.SmokeError):
                txn.read_journal(path)
            path.write_bytes(b"x" * (1024**2 + 1))
            with self.assertRaisesRegex(txn.SmokeError, "1 MiB"):
                txn.read_journal(path)
            path.write_bytes(gzip.compress(b"x" * (4 * 1024**2 + 1)))
            with self.assertRaisesRegex(txn.SmokeError, "4 MiB"):
                txn.read_journal(path)
            path.write_bytes(gzip.compress(bytes((10, 0, 0, 0))))
            with self.assertRaisesRegex(txn.SmokeError, "schema 2"):
                txn.read_journal(path)


class ReadyStatusTests(unittest.TestCase):
    def test_transaction_start_passes_ready_status_mode_without_changing_checkpoint(self):
        harness = txn.TransactionHarness(java="java", server=Path("isolated"), port=25610,
                                         expected_version="1.20.1-1.8.0-dev", startup_timeout=240)
        harness.wait_ready_status = True
        with patch.object(txn.server_smoke, "CapturedProcess") as process, \
                patch.object(txn.recovery, "verify_status") as verify:
            harness.start("test", "ASSEMBLY:EXTRACTING:3")
            verify.assert_called_once_with(25610, "1.20.1-1.8.0-dev", wait_ready_status=True)
            self.assertIn("-Dadvancedrocketrycommunity.transactionCheckpoint=ASSEMBLY:EXTRACTING:3",
                          process.call_args.args[0])


class SealedDisassemblyTests(unittest.TestCase):
    def harness(self, enabled=False):
        harness = txn.TransactionHarness(java="java", server=Path("isolated"), port=25610,
                                         expected_version="test", startup_timeout=240)
        harness.sealed_disassembly_receipt = enabled
        return harness

    def test_option_is_explicit_and_defaults_off(self):
        args = ["runner", "source", "--session-dir", "session", "--baseline-summary", "baseline",
                "--evidence-dir", "evidence", "--artifact", "artifact", "--base-commit", "a" * 40]
        for enabled in (False, True):
            with patch.object(sys, "argv", args + (["--sealed-disassembly-receipt"] if enabled else [])):
                self.assertEqual(enabled, txn.parse_args().sealed_disassembly_receipt)

    def test_both_receipts_use_the_requested_entity_and_logical_id(self):
        for form in ("legacy", "sealed"):
            process = ReceiptProcess(disassembly_lines(form, entity=ENTITY))
            receipt = self.harness(True).disassemble(process, ENTITY, TRANSFER)
            self.assertEqual(form, receipt["form"])
            for mutation in (dict(entity=TRANSACTION), dict(logical=ENTITY)):
                values = dict(entity=ENTITY)
                values.update(mutation)
                with self.subTest(form=form, mutation=mutation), self.assertRaises(txn.SmokeError):
                    self.harness(True).disassemble(ReceiptProcess(disassembly_lines(form, **values)), ENTITY, TRANSFER)

    def test_missing_receipt_pending_done_or_wrong_result_fails(self):
        lines = disassembly_lines(entity=ENTITY)
        for value in ([], lines[1:], lines[:-1], disassembly_lines(entity=ENTITY, result="REJECTED")):
            with self.subTest(lines=value), self.assertRaises(txn.SmokeError):
                self.harness(True).disassemble(ReceiptProcess(value), ENTITY, TRANSFER)

    def test_default_still_requires_the_legacy_receipt_with_original_timeout(self):
        harness = self.harness()
        process = ReceiptProcess(disassembly_lines("legacy", entity=ENTITY))
        harness.disassemble(process, ENTITY, TRANSFER)
        self.assertEqual((txn.flight.DISASSEMBLY_LOG, 45.0, 0), process.waits[0])
        with self.assertRaises(txn.SmokeError):
            harness.disassemble(ReceiptProcess(disassembly_lines(entity=ENTITY)), ENTITY, TRANSFER)

    def test_observed_sealed_completion_is_not_a_passed_checkpoint(self):
        harness = self.harness(True)
        process = ReceiptProcess(disassembly_lines(entity=ENTITY))
        before = dict(snapshot=SNAPSHOT, items_snbt="complete chest")
        assembly = f"ARCE_ROCKET_TRANSACTION operation=assembly code=SUCCESS blocks=5 snapshot={SNAPSHOT} entity={ENTITY}"
        self.assertIsNotNone(txn.flight.ASSEMBLY_LOG.search(assembly))
        original_command = process.command

        def command(text):
            original_command(text)
            if "rocket assemble" in text:
                process.lines.append(assembly)

        process.command = command
        harness.start = Mock(return_value=process)
        harness.contents = Mock(side_effect=[dict(before), dict(before)])
        harness.report = Mock(return_value=dict(entity=ENTITY, logical=TRANSFER, snapshot=SNAPSHOT))
        harness.operator_report = Mock()
        harness.configure_rocket = Mock()
        harness.command_marker = Mock()
        harness.material_authority = Mock()
        harness.finish_clean = Mock(return_value={"journal_entries": 0})
        with patch.object(txn.recovery, "force_stop") as kill:
            row = txn.run_case(harness, "DISASSEMBLY:RESTORING:3", Path("unused"))
            self.assertEqual("NOT_REACHABLE_ON_C6", row["status"])
            self.assertIn("RocketTransactionReleaseProbe.bind(DISASSEMBLY)", row["reason"])
            kill.assert_not_called()
        harness.material_authority.assert_called_once()
        harness.finish_clean.assert_called_once_with(process)

    def test_unreachable_case_cannot_make_the_matrix_pass(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            args = Mock(base_commit="a" * 40, cases=list(txn.CASES), expected_version="test",
                        evidence_dir=root / "evidence", session_dir=root / "session", baseline_summary=root / "baseline",
                        java="java", startup_timeout=240, wait_ready_status=True, sealed_disassembly_receipt=True)
            args.session_dir.mkdir()
            rows = [dict(case=case, status="NOT_REACHABLE_ON_C6" if case.startswith("DISASSEMBLY:") else "PASS")
                    for case in txn.CASES]
            with patch.object(txn, "parse_args", return_value=args), \
                    patch.object(txn, "prepare_session", return_value=(args.session_dir, 25610, {})), \
                    patch.object(txn.server_smoke, "resolve_java", return_value=("java", "17")), \
                    patch.object(txn.server_smoke, "digest_file", return_value="hash"), \
                    patch.object(txn, "run_case", side_effect=[dict(row, reason="code path") for row in rows]):
                self.assertEqual(1, txn.main())
            import json
            summary = json.loads((args.evidence_dir / "summary.json").read_text())
            self.assertEqual("INCOMPLETE", summary["status"])
            self.assertFalse(summary["complete_transaction_matrix"])
            self.assertEqual((13, 12, 25), (summary["passed_cases"], summary["unreachable_cases"], len(summary["cases"])))


if __name__ == "__main__":
    unittest.main()
