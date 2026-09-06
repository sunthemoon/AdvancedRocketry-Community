import copy
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

from scripts import run_v100_flight_forced_stop as recovery


TRANSFER = "11111111-1111-1111-1111-111111111111"


def receipt(case, **changes):
    phase, source, destination, action = recovery.RECOVERY_CONTRACT[case]
    values = dict(transfer=TRANSFER, phase=phase, source=source, destination=destination,
                  action=action, status="RECOVERED")
    values.update(changes)
    line = ("ARCE_TRANSFER_RECOVERY transfer={transfer} phase={phase} "
            "source_count={source} destination_count={destination} "
            "action={action} status={status}").format(**values)
    return recovery.flight.RECOVERY_LOG.fullmatch(line)


def reports(case):
    after_destination = case in recovery.RECOVERY_CONTRACT and case not in {
        "COUNTDOWN", "ASCENT", "TRANSIT_PREPARED",
    }
    entity = "destination" if after_destination else "source"
    before = dict(entity=entity, logical="logical", snapshot="a" * 64, capacity=1000,
                  blocks=5, passengers=0, origin=[384, 100, 384], fuel=800)
    report = dict(before)
    report.update(
        dimension=recovery.flight.MOON if after_destination else recovery.flight.EARTH,
        state=case if case in {"ASSEMBLED", "FUELED"} else ("LANDED" if after_destination else "FUELED"),
        fuel=800 if case in {"ASSEMBLED", "FUELED"} or after_destination else 1000,
    )
    return dict(report=before, source_entity="source", paused_entity="destination",
                fuel_before=1000, required_fuel=200, transfer_id=TRANSFER), report


class FlightForcedStopTests(unittest.TestCase):
    def test_matrix_includes_every_durable_transfer_phase(self):
        self.assertEqual(10, len(recovery.CASES))
        self.assertEqual(set(recovery.CASES) - {"ASSEMBLED", "FUELED"},
                         set(recovery.RECOVERY_CONTRACT))
        self.assertEqual({"PREPARED", "DESTINATION_SPAWNED", "PASSENGERS_TRANSFERRED",
                          "SOURCE_REMOVED", "COMMITTED"},
                         {value[0] for value in recovery.RECOVERY_CONTRACT.values()})

    def test_exact_receipts_are_accepted(self):
        for case in recovery.RECOVERY_CONTRACT:
            with self.subTest(case=case):
                result = recovery.validate_receipt(case, receipt(case), TRANSFER)
                self.assertEqual("RECOVERED", result["status"])

    def test_changed_receipt_cannot_claim_recovery(self):
        for case in recovery.RECOVERY_CONTRACT:
            for mutation in ({"transfer": "2" * 36}, {"phase": "UNKNOWN"}, {"source": 2},
                             {"destination": 2}, {"action": "OTHER"}, {"status": "WAITING"}):
                with self.subTest(case=case, mutation=mutation):
                    with self.assertRaisesRegex(recovery.SmokeError, "authority contract"):
                        recovery.validate_receipt(case, receipt(case, **mutation), TRANSFER)

    def test_reports_preserve_identity_and_exact_fuel(self):
        for case in recovery.CASES:
            with self.subTest(case=case):
                recovery.validate_report(case, *reports(case))

    def test_report_mutations_cannot_claim_preservation(self):
        mutations = dict(logical="other", snapshot="b" * 64, capacity=1001, blocks=6,
                         passengers=1, origin=[0, 0, 0], state="FAILED", dimension="other",
                         entity="other", fuel=799)
        for case in recovery.CASES:
            staged, report = reports(case)
            for key, value in mutations.items():
                with self.subTest(case=case, key=key):
                    changed = copy.deepcopy(report)
                    changed[key] = value
                    with self.assertRaises(recovery.SmokeError):
                        recovery.validate_report(case, staged, changed)

    def test_player_manifest_cannot_be_mislabeled_as_tested(self):
        staged, report = reports("LANDED")
        staged["report"]["passengers"] = report["passengers"] = 1
        with self.assertRaisesRegex(recovery.SmokeError, "does not exercise player"):
            recovery.validate_report("LANDED", staged, report)

    def test_only_exact_recovery_warning_is_allowed(self):
        case = "SOURCE_REMOVED"
        staged, _ = reports(case)
        text = receipt(case).group(0)
        warning = "[Server thread/WARN] [advancedrocketrycommunity/]: " + text
        recovery.audit_recovery_log([warning], case, staged)
        for line in (warning.replace("/WARN", "/ERROR"),
                     warning.replace("RECOVERED", "WAITING"),
                     warning.replace(TRANSFER, "2" * 36),
                     "[Server thread/WARN] [advancedrocketrycommunity/]: other failure"):
            with self.subTest(line=line):
                with self.assertRaises(recovery.SmokeError):
                    recovery.audit_recovery_log([line], case, staged)

    def test_startup_failure_aborts_the_owned_process(self):
        process = Mock()
        process.wait_for.side_effect = recovery.SmokeError("startup failed")
        harness = recovery.RecoveryHarness(java="java", server=Path("isolated"), port=25610,
                                          expected_version="1.20.1-1.0.0-dev", startup_timeout=1)
        with patch.object(recovery.server_smoke, "CapturedProcess", return_value=process):
            with self.assertRaisesRegex(recovery.SmokeError, "startup failed"):
                harness.start("failed")
        process.abort.assert_called_once_with()

    def test_loaded_entities_are_not_counted_once_per_dimension(self):
        process, harness = Mock(), Mock()
        _, report = reports("ASSEMBLED")
        recovery.verify_single_authority(process, harness, report, "ASSEMBLED")
        enumerations = [call.args[0] for call in process.command.call_args_list
                        if "as @e[" in call.args[0]]
        self.assertEqual(1, len(enumerations))
        self.assertIn("matches 1", harness.command_marker.call_args.args[1])

    def test_forced_stop_kills_owned_process_without_graceful_command(self):
        with tempfile.TemporaryDirectory() as temporary:
            log = Path(temporary) / "process.txt"
            log.write_text("test\n", encoding="utf-8")
            process = Mock()
            process.lines = []
            process.process.poll.return_value = None
            process.finish.return_value = 1
            process._arce_log_path = log
            process._arce_name = "owned"
            process._arce_started_at = "2026-09-05T00:00:00+00:00"
            harness = Mock(process_documents=[], filtered_lines=[])
            result = recovery.force_stop(process, harness)
            process.process.kill.assert_called_once_with()
            process.command.assert_not_called()
            self.assertFalse(result["graceful_stop_command_sent"])

    def test_already_exited_server_is_not_mislabeled_as_forcibly_killed(self):
        for exit_code in (0, 1, -9):
            with self.subTest(exit_code=exit_code):
                process = Mock()
                process.process.poll.return_value = exit_code
                with self.assertRaisesRegex(recovery.SmokeError, "exited before"):
                    recovery.force_stop(process, Mock())
                process.process.kill.assert_not_called()

    def test_session_must_stay_on_loopback_at_the_verified_port(self):
        with tempfile.TemporaryDirectory() as temporary:
            server = Path(temporary)
            properties = server / "server.properties"
            properties.write_text("server-ip=127.0.0.1\nserver-port=25610\nonline-mode=true\n")
            self.assertEqual("true", recovery.verify_local_session(server, 25610)["online-mode"])
            for host, port in (("0.0.0.0", 25610), ("127.0.0.1", 25611)):
                properties.write_text(f"server-ip={host}\nserver-port={port}\n")
                with self.assertRaisesRegex(recovery.SmokeError, "loopback"):
                    recovery.verify_local_session(server, 25610)

    def test_evidence_copies_full_logs_with_matching_hashes(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            session, evidence = root / "session", root / "evidence"
            session.mkdir()
            evidence.mkdir()
            log = session / f"{recovery.RUN_TOKEN}-v100-case-full.txt"
            log.write_text("observed output\n", encoding="utf-8")
            summary = {"status": "FAIL", "cases": []}
            recovery.write_evidence(evidence, session, summary)
            self.assertEqual(log.read_bytes(), (evidence / log.name).read_bytes())
            self.assertEqual(recovery.server_smoke.digest_file(log), summary["logs"][0]["sha256"])
            self.assertIn('"status": "FAIL"', (evidence / "summary.json").read_text())

    def test_nested_session_is_rejected_before_any_copy(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            args = Mock(source_server_dir=root / "source", session_dir=root / "source/session",
                        evidence_dir=root / "evidence", base_commit="a" * 40,
                        expected_version="1.20.1-1.0.0-dev", cases=None)
            args.source_server_dir.mkdir()
            with patch.object(recovery, "parse_args", return_value=args), \
                    patch.object(recovery, "_copy_server") as copy_server:
                self.assertEqual(1, recovery.main())
                copy_server.assert_not_called()
            self.assertFalse(args.evidence_dir.exists())


if __name__ == "__main__":
    unittest.main()
