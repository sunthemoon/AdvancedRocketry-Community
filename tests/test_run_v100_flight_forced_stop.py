import copy
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

from scripts import run_v100_flight_forced_stop as recovery


TRANSFER = "11111111-1111-1111-1111-111111111111"
ENTITY = "22222222-2222-2222-2222-222222222222"
LOGICAL = "33333333-3333-3333-3333-333333333333"


class ReceiptProcess:
    """Deterministic line-backed process; no sockets, JVMs or Windows APIs."""
    def __init__(self, lines):
        self.lines = []
        self.output = lines
        self.commands = []
        self.waits = []

    def command(self, command):
        self.commands.append(command)
        if "release-test disassemble" in command or "release-test launch" in command:
            self.lines.extend(self.output)
        if "data get block" in command:
            self.lines.append('has the following block data: [{id: "minecraft:diamond", Count: 17b}, '
                              '{id: "minecraft:iron_ingot", Count: 64b}]')

    def wait_for(self, pattern, timeout, *, start_at=0):
        self.waits.append((pattern, timeout, start_at))
        for index in range(start_at, len(self.lines)):
            if pattern.search(self.lines[index]):
                return index
        raise recovery.SmokeError("Missing requested receipt/marker")


def disassembly_lines(form="sealed", **changes):
    values = dict(entity=ENTITY, logical=TRANSFER, result="SEALED", code="SUCCESS", blocks=5, rolled_back=0)
    values.update(changes)
    if form == "legacy":
        return [("ARCE_RELEASE_TEST_DISASSEMBLY entity={entity} logical={logical} "
                 "code={code} blocks={blocks} rolled_back={rolled_back}").format(**values)]
    return ["Release-test disassembly pending",
            ("ARCE_ROCKET_SEALED_DISASSEMBLY entity={entity} logical={logical} result={result}").format(**values),
            f"ARCE_DISASSEMBLY_DURABILITY_COMPLETE transaction={ENTITY} "
            "phase=DONE reason=SEALED additional=[] overflow=0"]


def launch_lines(code="JOURNAL_PENDING"):
    return [f"ARCE_RELEASE_TEST_LAUNCH request={TRANSFER} entity={ENTITY} logical={LOGICAL} "
            f"source={recovery.flight.EARTH} destination={recovery.flight.MOON} "
            f"checkpoint=COUNTDOWN code={code} required_fuel=372 fuel_before=1000",
            "Release-test launch failed: JOURNAL_PENDING",
            f"ARCE_TRANSFER_PREPARE transfer={TRANSFER} logical={LOGICAL} "
            f"source={recovery.flight.EARTH} destination={recovery.flight.MOON} "
            "code=SUCCESS candidates=1 chunks=1 detail=selected"]


class ProtocolProcess(ReceiptProcess):
    def __init__(self, replies, inventory=None):
        super().__init__([])
        self.replies = iter(replies)
        self.process = Mock()
        self.process.poll.return_value = None
        self.inventory = inventory or ('[{Slot: 0b, id: "minecraft:diamond", Count: 17b}, '
                                       '{Slot: 26b, id: "minecraft:iron_ingot", Count: 64b}]')

    def command(self, command):
        self.commands.append(command)
        if "release-test disassemble" in command:
            self.lines.extend(next(self.replies, ["Release-test disassembly pending"]))
        if "data get block" in command:
            self.lines.append("has the following block data: " + self.inventory)


class SettledDisassemblyProtocolTests(unittest.TestCase):
    def setUp(self):
        self.clock = 0.0
        self.harness = recovery.RecoveryHarness(java="java", server=Path("isolated"), port=25610,
                                              expected_version="test", startup_timeout=240)
        self.harness.sealed_disassembly_receipt = True
        self.harness.settled_disassembly_protocol = True
        self.report = dict(entity=ENTITY, logical=TRANSFER, dimension=recovery.flight.MOON,
                           origin=[8, 20, 8], fuel=628)

    def sleep(self, seconds):
        self.clock += seconds

    def run_protocol(self, process):
        with patch.object(recovery.time, "monotonic", side_effect=lambda: self.clock), \
                patch.object(recovery.time, "sleep", side_effect=self.sleep), \
                patch.object(self.harness, "command_marker") as marker:
            result = self.harness.disassemble_and_verify(process, self.report, "verified")
        self.assertIn("unless entity " + ENTITY, marker.call_args.args[1])
        self.assertEqual(5, marker.call_args.args[1].count("if block"))
        self.assertLessEqual(marker.call_args.kwargs["timeout"], 45)
        return result

    def test_fresh_exact_fuel_request_after_preparation(self):
        process = ProtocolProcess([["Release-test disassembly pending"], disassembly_lines()])
        result = self.run_protocol(process)
        requests = [c for c in process.commands if "release-test disassemble" in c]
        self.assertEqual(2, result["requests"])
        self.assertEqual([requests[0]] * 2, requests)
        self.assertIn("discard-fuel 628", requests[0])
        self.assertGreaterEqual(result["elapsed_seconds"], 2)
        self.assertLess(result["elapsed_seconds"], 45)

    def test_busy_can_clear_but_other_refusals_fail(self):
        process = ProtocolProcess([["Release-test disassembly failed: REGION_BUSY"], disassembly_lines()])
        self.assertEqual(2, self.run_protocol(process)["requests"])
        for code in ("WORLD_CHANGED", "FUEL_DISPOSAL_REQUIRED", "ENTITY_STATE_INVALID"):
            with self.subTest(code=code), self.assertRaisesRegex(recovery.SmokeError, code):
                self.run_protocol(ProtocolProcess([[f"Release-test disassembly failed: {code}"] + disassembly_lines()]))

    def test_busy_never_clears_and_preparation_never_ready_expire(self):
        for answer in ("Release-test disassembly failed: REGION_BUSY", "Release-test disassembly pending"):
            self.clock = 0
            process = ProtocolProcess([[answer]] * 100)
            with self.subTest(answer=answer), self.assertRaisesRegex(recovery.SmokeError, "45-second"):
                self.run_protocol(process)
            self.assertAlmostEqual(45, self.clock)
            self.assertLessEqual(len(process.commands), 23)

    def test_no_answer_is_not_permission_to_repeat_and_exit_fails(self):
        process = ProtocolProcess([[]])
        with self.assertRaisesRegex(recovery.SmokeError, "45-second"):
            self.run_protocol(process)
        self.assertEqual(1, len(process.commands))
        self.clock = 0
        process.process.poll.return_value = 1
        with self.assertRaisesRegex(recovery.SmokeError, "Server exited"):
            self.run_protocol(process)

    def test_second_sealed_receipt_before_or_after_done_fails(self):
        lines = disassembly_lines()
        for replies in (lines[:2] + [lines[1]] + lines[2:], lines + [lines[1]]):
            with self.subTest(replies=replies), self.assertRaisesRegex(recovery.SmokeError, "more than one"):
                self.run_protocol(ProtocolProcess([replies]))

    def test_material_failure_and_wrong_inventory_still_fail(self):
        for inventory in ("[]", '[{Slot: 0b, id: "minecraft:diamond", Count: 17b}, '
                          '{Slot: 26b, id: "minecraft:iron_ingot", Count: 64b}, {Count: 1b}]'):
            with self.subTest(inventory=inventory), self.assertRaisesRegex(recovery.SmokeError, "inventory"):
                self.run_protocol(ProtocolProcess([disassembly_lines()], inventory))
        with patch.object(recovery.time, "monotonic", return_value=0), \
                patch.object(self.harness, "command_marker", side_effect=recovery.SmokeError("blocks absent")), \
                self.assertRaisesRegex(recovery.SmokeError, "blocks absent"):
            self.harness.disassemble_and_verify(ProtocolProcess([disassembly_lines()]), self.report, "verified")

    def test_verification_cannot_extend_budget(self):
        process = ProtocolProcess([disassembly_lines()])
        def late_blocks(*args, **kwargs):
            self.clock = 45
        with patch.object(recovery.time, "monotonic", side_effect=lambda: self.clock), \
                patch.object(self.harness, "command_marker", side_effect=late_blocks), \
                self.assertRaisesRegex(recovery.SmokeError, "45-second"):
            self.harness.disassemble_and_verify(process, self.report, "verified")

    def test_settlement_requires_exact_current_transfer_event(self):
        staged = dict(transfer_id=LOGICAL, state="DESCENT", fuel_before=1000, required_fuel=372)
        retained = (f"ARCE_TRANSFER_PHASE transfer={LOGICAL} logical={TRANSFER} phase=COMMITTED "
                    f"event=landed_reservation_retained entity={ENTITY} fuel_before=1000 fuel_after=628 required=372")
        process = ReceiptProcess([])
        for line in (retained, "[12:34:56] [Server thread/INFO] [mod/]: " + retained):
            process.lines = [line]
            self.assertEqual(LOGICAL, recovery.wait_settled_transfer(process, staged, self.report)["transfer"])
        for line in (retained.replace(LOGICAL, ENTITY), retained.replace(ENTITY, LOGICAL),
                     retained.replace(TRANSFER, ENTITY), retained.replace("628", "629"),
                     retained.replace("retained", "released"), retained + " extra", "[Server] " + retained):
            process.lines = [line]
            with self.subTest(line=line), self.assertRaises(recovery.SmokeError):
                recovery.wait_settled_transfer(process, staged, self.report)
        staged["state"] = "LANDED"
        line = (f"ARCE_TRANSFER_RECOVERY transfer={LOGICAL} phase=COMMITTED "
                "source_count=0 destination_count=1 action=KEEP_DESTINATION status=RECOVERED")
        process.lines = ["[12:34:56] [Server thread/INFO] [mod/]: " + line]
        self.assertEqual("settled_recovery", recovery.wait_settled_transfer(process, staged, self.report)["form"])
        process.lines = [process.lines[0].replace("/INFO", "/WARN")]
        with self.assertRaises(recovery.SmokeError):
            recovery.wait_settled_transfer(process, staged, self.report)

    def test_option_defaults_off_and_requires_receipt_mode(self):
        argv = ["runner", "source", "--session-dir", "session", "--baseline-summary", "baseline",
                "--evidence-dir", "evidence", "--base-commit", "a" * 40]
        with patch.object(sys, "argv", argv):
            self.assertFalse(recovery.parse_args().settled_disassembly_protocol)
        with patch.object(sys, "argv", argv + ["--settled-disassembly-protocol"]), self.assertRaises(SystemExit):
            recovery.parse_args()
        with patch.object(sys, "argv", argv + ["--settled-disassembly-protocol", "--sealed-disassembly-receipt"]):
            self.assertTrue(recovery.parse_args().settled_disassembly_protocol)


class JournalledLaunchTests(unittest.TestCase):
    def read(self, lines, start=0):
        process = ReceiptProcess([])
        process.lines = lines
        result = recovery.wait_launch_receipt(process, ENTITY, LOGICAL, recovery.flight.EARTH,
                                              recovery.flight.MOON, "COUNTDOWN", 1000, start)
        return result, process

    def test_pending_requires_post_acknowledgement_callback(self):
        for prefix in ("", "[15:37:44] [Server thread/INFO] [io.gi.su.ad.AdvancedRocketryCommunity/]: "):
            (launch, index, receipt), _ = self.read([prefix + line + "\n" for line in launch_lines()])
            self.assertEqual("JOURNAL_PENDING", launch[7])
            self.assertEqual(2, index)
            self.assertEqual("journalled", receipt["form"])
            self.assertEqual((TRANSFER, LOGICAL), (receipt["transfer"], receipt["logical"]))

    def test_wrong_rocket_route_checkpoint_or_fuel_in_launch_fails(self):
        for old, new in ((f"entity={ENTITY}", f"entity={TRANSFER}"), (f"logical={LOGICAL}", f"logical={ENTITY}"),
                         (f"source={recovery.flight.EARTH}", "source=other:world"),
                         (f"destination={recovery.flight.MOON}", "destination=other:world"),
                         ("checkpoint=COUNTDOWN", "checkpoint=ASCENT"), ("fuel_before=1000", "fuel_before=999")):
            lines = launch_lines()
            lines[0] = lines[0].replace(old, new)
            with self.subTest(old=old), self.assertRaises(recovery.SmokeError):
                self.read(lines)

    def test_foreign_callback_cannot_confirm_pending_launch(self):
        for old, new in ((TRANSFER, ENTITY), (LOGICAL, ENTITY), (recovery.flight.EARTH, "other:world"),
                         (recovery.flight.MOON, "other:world")):
            lines = launch_lines()
            lines[2] = lines[2].replace(old, new)
            with self.subTest(old=old), self.assertRaises(recovery.SmokeError):
                self.read(lines)

    def test_missing_stale_or_early_confirmation_fails(self):
        lines = launch_lines()
        for value, start in (([], 0), (lines[:1], 0), (lines[:2], 0), ([lines[2]] + lines[:2], 0),
                             (lines, len(lines))):
            with self.subTest(value=value, start=start), self.assertRaises(recovery.SmokeError):
                self.read(value, start)

    def test_refusal_after_pending_wins_over_later_success(self):
        lines = launch_lines()
        for refusal in (lines[2].replace("code=SUCCESS", "code=TRANSFER_JOURNAL_BLOCKED"),
                        lines[0].replace("code=JOURNAL_PENDING", "code=TRANSFER_PREPARE_FAILED"),
                        f"[15:37:44] [Server thread/ERROR] [io.gi.su.ad.AdvancedRocketryCommunity/]: "
                        f"ARCE_TRANSFER_PREPARE_FAILED transfer={TRANSFER} logical={LOGICAL} source={ENTITY}"):
            with self.subTest(refusal=refusal), self.assertRaises(recovery.SmokeError):
                self.read(lines[:2] + [refusal] + lines[2:])

    def test_chat_substrings_and_malformed_confirmations_fail(self):
        for at in (0, 2):
            for prefix, suffix in (("prefix ", ""), ("", " trailing" if at == 0 else "\nother"),
                                   ("[15:37:44] [Server thread/INFO] [minecraft/MinecraftServer]: [Server] ", "")):
                lines = launch_lines()
                lines[at] = prefix + lines[at] + suffix
                with self.subTest(at=at, prefix=prefix, suffix=suffix), self.assertRaises(recovery.SmokeError):
                    self.read(lines)

    def test_one_decreasing_launch_budget_and_no_wait_on_legacy_success(self):
        process = ReceiptProcess([])
        process.lines = launch_lines()
        with patch.object(recovery.time, "monotonic", side_effect=[0, 1, 5]):
            recovery.wait_launch_receipt(process, ENTITY, LOGICAL, recovery.flight.EARTH,
                                         recovery.flight.MOON, "COUNTDOWN", 1000, 0)
        self.assertEqual([29, 25], [call[1] for call in process.waits])
        with patch.object(recovery.time, "monotonic", side_effect=[0, 1, 30]):
            with self.assertRaisesRegex(recovery.SmokeError, "30-second"):
                self.read(launch_lines())
        (_, _, receipt), process = self.read(launch_lines("SUCCESS")[:1])
        self.assertEqual("legacy", receipt["form"])
        self.assertEqual(1, len(process.waits))

    def test_non_pending_refusals_fail_without_confirmation(self):
        for code in ("TRANSFER_JOURNAL_BLOCKED", "INVALID_STATE", "TRANSFER_PREPARE_FAILED"):
            with self.subTest(code=code), self.assertRaises(recovery.SmokeError):
                self.read(launch_lines(code))

    def test_option_defaults_off_and_is_explicit(self):
        args = ["runner", "source", "--session-dir", "session", "--baseline-summary", "baseline",
                "--evidence-dir", "evidence", "--base-commit", "a" * 40]
        for enabled in (False, True):
            with patch.object(sys, "argv", args + (["--journalled-launch-receipt"] if enabled else [])):
                self.assertEqual(enabled, recovery.parse_args().journalled_launch_receipt)

    def test_default_delegates_to_unchanged_shared_harness(self):
        harness = recovery.RecoveryHarness(java="java", server=Path("isolated"), port=25610,
                                          expected_version="test", startup_timeout=240)
        kwargs = dict(dimension=recovery.flight.EARTH, entity=ENTITY, destination_name="moon", checkpoint="COUNTDOWN")
        with patch.object(recovery.flight.FlightHarness, "launch_to_checkpoint") as original:
            harness.launch_to_checkpoint("process", **kwargs)
            original.assert_called_once_with("process", **kwargs)
        process = ReceiptProcess(launch_lines())
        with patch.object(harness, "refuel"), self.assertRaisesRegex(recovery.SmokeError, "Could not launch"):
            # The unchanged default still rejects JOURNAL_PENDING even with a callback.
            harness.launch_to_checkpoint(process, **kwargs)

    def test_opt_in_still_requires_pause_and_unchanged_report_state(self):
        harness = recovery.RecoveryHarness(java="java", server=Path("isolated"), port=25610,
                                          expected_version="test", startup_timeout=240)
        harness.journalled_launch_receipt = True
        kwargs = dict(dimension=recovery.flight.EARTH, entity=ENTITY, destination_name="moon", checkpoint="COUNTDOWN")
        pause = f"ARCE_RELEASE_TEST_FLIGHT_PAUSED checkpoint=COUNTDOWN transfer={TRANSFER} "
        pause += f"phase=PREPARED state=COUNTDOWN entity={ENTITY}"
        for state in (None, "FUELED", "COUNTDOWN"):
            process = ReceiptProcess(launch_lines() + ([pause] if state else []))
            with patch.object(harness, "refuel", return_value=dict(logical=LOGICAL, amount=1000)), \
                    patch.object(harness, "report", return_value=dict(state=state)):
                if state != "COUNTDOWN":
                    with self.assertRaises(recovery.SmokeError):
                        harness.launch_to_checkpoint(process, **kwargs)
                else:
                    result = harness.launch_to_checkpoint(process, **kwargs)
                    self.assertEqual("journalled", result["launch_receipt"]["form"])
                    self.assertEqual(TRANSFER, result["transfer_id"])


class DisassemblyReceiptTests(unittest.TestCase):
    def read(self, lines):
        process = ReceiptProcess([])
        process.lines = lines
        return recovery.wait_disassembly_receipt(process, ENTITY, TRANSFER, 0)

    def test_both_complete_forms_are_accepted_as_whole_info_messages(self):
        for form in ("legacy", "sealed"):
            for prefix in ("", "[15:37:44] [Server thread/INFO] [io.gi.su.ad.AdvancedRocketryCommunity/]: "):
                with self.subTest(form=form, prefix=prefix):
                    result = self.read([prefix + line + "\n" for line in disassembly_lines(form)])
                    self.assertEqual(form, result["form"])
                    self.assertEqual((ENTITY, TRANSFER), (result["entity"], result["logical"]))
                    if form == "sealed":
                        self.assertNotIn("blocks", result)
                        self.assertEqual("DONE", result["durability_phase"])

    def test_wrong_entity_or_logical_id_is_rejected_for_both_forms(self):
        for form in ("legacy", "sealed"):
            for mutation in (dict(entity=TRANSFER), dict(logical=ENTITY)):
                with self.subTest(form=form, mutation=mutation), self.assertRaises(recovery.SmokeError):
                    self.read(disassembly_lines(form, **mutation))

    def test_non_sealed_results_and_bad_legacy_counts_fail(self):
        for result in ("REJECTED", "HELD", "SEALED_EXTRA"):
            with self.subTest(result=result), self.assertRaises(recovery.SmokeError):
                self.read(disassembly_lines(result=result))
        for mutation in (dict(code="FAILED"), dict(blocks=4), dict(rolled_back=1)):
            with self.subTest(mutation=mutation), self.assertRaises(recovery.SmokeError):
                self.read(disassembly_lines("legacy", **mutation))

    def test_missing_receipt_pending_or_durability_cannot_pass(self):
        lines = disassembly_lines()
        for value in ([], [lines[0]], lines[1:], lines[:-1], [lines[1], lines[0], lines[2]],
                      [lines[0], lines[2], lines[1]], lines[:2] + disassembly_lines("legacy") + lines[2:]):
            with self.subTest(lines=value), self.assertRaises(recovery.SmokeError):
                self.read(value)

    def test_substrings_chat_and_malformed_durability_do_not_pass(self):
        for form in ("legacy", "sealed"):
            lines = disassembly_lines(form)
            at = 0 if form == "legacy" else 1
            for changed in ("prefix " + lines[at], lines[at] + " trailing",
                            "[15:37:44] [Server thread/INFO] [minecraft/MinecraftServer]: "
                            "[Server] " + lines[at]):
                value = list(lines)
                value[at] = changed
                with self.subTest(form=form, changed=changed), self.assertRaises(recovery.SmokeError):
                    self.read(value)
        for change in ("phase=HELD", "reason=REJECTED", "overflow=1", "additional=[other]"):
            lines = disassembly_lines()
            key = change.split("=")[0]
            old = dict(phase="phase=DONE", reason="reason=SEALED", overflow="overflow=0",
                       additional="additional=[]")[key]
            lines[-1] = lines[-1].replace(old, change)
            with self.subTest(change=change), self.assertRaises(recovery.SmokeError):
                self.read(lines)

    def test_existing_lines_are_excluded_and_one_budget_is_shared(self):
        process = ReceiptProcess([])
        process.lines = disassembly_lines()
        with self.assertRaises(recovery.SmokeError):
            recovery.wait_disassembly_receipt(process, ENTITY, TRANSFER, len(process.lines))
        with patch.object(recovery.time, "monotonic", side_effect=[0, 1, 5]):
            recovery.wait_disassembly_receipt(process, ENTITY, TRANSFER, 0)
        self.assertEqual([44, 40], [call[1] for call in process.waits[-2:]])
        with patch.object(recovery.time, "monotonic", side_effect=[0, 45]):
            with self.assertRaisesRegex(recovery.SmokeError, "45-second"):
                recovery.wait_disassembly_receipt(process, ENTITY, TRANSFER, 0)

    def test_option_defaults_off_and_is_explicit(self):
        args = ["runner", "source", "--session-dir", "session", "--baseline-summary", "baseline",
                "--evidence-dir", "evidence", "--base-commit", "a" * 40]
        for enabled in (False, True):
            with patch.object(sys, "argv", args + (["--sealed-disassembly-receipt"] if enabled else [])):
                self.assertEqual(enabled, recovery.parse_args().sealed_disassembly_receipt)

    def test_default_delegates_unchanged_to_shared_harness(self):
        harness = recovery.RecoveryHarness(java="java", server=Path("isolated"), port=25610,
                                          expected_version="test", startup_timeout=240)
        with patch.object(recovery.flight.FlightHarness, "disassemble_and_verify") as original:
            harness.disassemble_and_verify("process", {"entity": ENTITY}, "marker")
            original.assert_called_once_with("process", {"entity": ENTITY}, "marker")

    def test_opt_in_preserves_block_and_inventory_assertions_for_both_forms(self):
        harness = recovery.RecoveryHarness(java="java", server=Path("isolated"), port=25610,
                                          expected_version="test", startup_timeout=240)
        harness.sealed_disassembly_receipt = True
        report = dict(entity=ENTITY, logical=TRANSFER, dimension=recovery.flight.EARTH, origin=[384, 101, 384], fuel=0)
        for form in ("legacy", "sealed"):
            process = ReceiptProcess(disassembly_lines(form))
            with patch.object(harness, "command_marker") as marker:
                result = harness.disassemble_and_verify(process, report, "verified")
                self.assertEqual(form, result["form"])
                command = marker.call_args.args[1]
                self.assertIn(f"unless entity {ENTITY}", command)
                self.assertEqual(5, command.count("if block"))
            process = ReceiptProcess(disassembly_lines(form))
            with patch.object(harness, "command_marker", side_effect=recovery.SmokeError("blocks absent")):
                with self.assertRaisesRegex(recovery.SmokeError, "blocks absent"):
                    harness.disassemble_and_verify(process, report, "verified")
            process = ReceiptProcess(disassembly_lines(form))
            original_command = process.command

            def missing_inventory(command):
                original_command(command)
                if "data get block" in command:
                    process.lines[-1] = "has the following block data: []"

            process.command = missing_inventory
            with patch.object(harness, "command_marker"), self.assertRaisesRegex(recovery.SmokeError, "inventory"):
                harness.disassemble_and_verify(process, report, "verified")

    def test_opt_in_explicitly_consents_to_only_the_reported_fuel(self):
        harness = recovery.RecoveryHarness(java="java", server=Path("isolated"), port=25610,
                                          expected_version="test", startup_timeout=240)
        harness.sealed_disassembly_receipt = True
        process = ReceiptProcess(disassembly_lines())
        report = dict(entity=ENTITY, logical=TRANSFER, dimension=recovery.flight.EARTH, origin=[384, 101, 384], fuel=628)
        with patch.object(harness, "command_marker"):
            harness.disassemble_and_verify(process, report, "verified")
        self.assertIn(f"disassemble {ENTITY} discard-fuel 628", process.commands[0])


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
    def test_status_default_keeps_single_strict_identity_check(self):
        status = {"text": "Server is still starting! Please wait before reconnecting."}
        with patch.object(recovery.server_smoke, "wait_for_status", return_value=status) as poll:
            with self.assertRaisesRegex(recovery.SmokeError, "no version"):
                recovery.verify_status(25610, "1.20.1-1.8.0-dev")
            poll.assert_called_once_with(25610)

    def test_ready_status_retries_only_starting_response_with_remaining_budget(self):
        starting = {"text": "Server is still starting! Please wait before reconnecting."}
        ready = {"version": {"name": "1.20.1"}}
        with patch.object(recovery.server_smoke, "wait_for_status", side_effect=[starting, ready]) as poll, \
                patch.object(recovery.server_smoke, "validate_status_identity") as identity, \
                patch.object(recovery.time, "monotonic", side_effect=[10, 10.5, 11]), \
                patch.object(recovery.time, "sleep") as sleep:
            recovery.verify_status(25610, "1.20.1-1.8.0-dev", wait_ready_status=True)
            self.assertEqual([29.5, 29], [call.kwargs["timeout"] for call in poll.call_args_list])
            identity.assert_called_once_with(ready, "1.20.1-1.8.0-dev")
            sleep.assert_called_once_with(0.1)

    def test_ready_status_does_not_accept_invalid_or_mismatched_identity(self):
        for status in ({"text": "different response"}, {"version": {"name": "other"}},
                       {"text": "Server is still starting! Please wait before reconnecting.", "extra": True}):
            with self.subTest(status=status), \
                    patch.object(recovery.server_smoke, "wait_for_status", return_value=status) as poll:
                with self.assertRaises(recovery.SmokeError):
                    recovery.verify_status(25610, "1.20.1-1.8.0-dev", wait_ready_status=True)
                self.assertEqual(1, poll.call_count)

    def test_ready_status_expires_without_extending_original_budget(self):
        starting = {"text": "Server is still starting! Please wait before reconnecting."}
        with patch.object(recovery.server_smoke, "wait_for_status", return_value=starting) as poll, \
                patch.object(recovery.time, "monotonic", side_effect=[0, 0, 30]), \
                patch.object(recovery.time, "sleep"):
            with self.assertRaisesRegex(recovery.SmokeError, "within 30 seconds"):
                recovery.verify_status(25610, "1.20.1-1.8.0-dev", wait_ready_status=True)
            poll.assert_called_once_with(25610, timeout=30)

    def test_recovery_start_passes_explicit_ready_status_mode(self):
        harness = recovery.RecoveryHarness(java="java", server=Path("isolated"), port=25610,
                                          expected_version="1.20.1-1.8.0-dev", startup_timeout=240)
        harness.wait_ready_status = True
        with patch.object(recovery.server_smoke, "CapturedProcess"), \
                patch.object(recovery, "verify_status") as verify:
            harness.start("test")
            verify.assert_called_once_with(25610, "1.20.1-1.8.0-dev", wait_ready_status=True)

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
