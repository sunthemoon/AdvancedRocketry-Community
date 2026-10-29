"""Fail-closed tests for the W27d1 and W27d2 bay forced-stop native harness's pure checks."""
import sys
from pathlib import Path
import unittest
from unittest.mock import Mock, patch
from copy import deepcopy

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_bay_forced_stop as runner

L = "11111111-1111-4111-8111-111111111111"
X = "22222222-2222-4222-8222-222222222222"
Y = "33333333-3333-4333-8333-333333333333"
OP = "44444444-4444-4444-8444-444444444444"
FIXTURE = dict(logical=L, transfer="t", rocket="r", data=X, solar=Y, launch="SUCCESS", packages="a" * 64 + "+" + "b" * 64)
CONSUMED_PACKAGES = "c" * 64 + "+" + "b" * 64


def status_line(claim="NONE", receipts="", transfer="NONE", state="FUELED", y="2.0", packages=FIXTURE["packages"],
                hold="NONE", reason="NONE", locked="false", consumed="-+-", owned="0"):
    return (f"[12:00:00] [Server thread/INFO] [x/]: ARCE_RELEASE_BAY_STATUS logical={L} claim={claim} hold={hold} reason={reason} "
            f"receipts=[{receipts}] transfer={transfer} rocket=r2 state={state} y={y} packages={packages} locked={locked} "
            f"consumed={consumed} owned={owned}\n")


def landed(**changes):
    values = dict(transfer="COMMITTED", state="LANDED", y="70.0", packages=CONSUMED_PACKAGES, consumed=f"{OP}+-", owned="1")
    values.update(changes)
    return runner.parse_status(status_line(**values))


class ParseTests(unittest.TestCase):
    def test_save_completion_never_matches_save_start(self):
        self.assertIsNone(runner.SAVED.search("Saving the game (this may take a moment!)"))
        self.assertIsNotNone(runner.SAVED.search("Saved the game"))

    def test_fixture_waits_for_entity_ticking_before_launching(self):
        run = Mock()
        run.args.flight_timeout = 120
        run.send.side_effect = ["Marked chunk", "ARCE_RELEASE_BAY_FIXTURE_WAIT origin=8, 2, 8",
            f"ARCE_RELEASE_BAY_FIXTURE logical={L} transfer=t rocket=r data={X} solar={Y} launch=SUCCESS packages={FIXTURE['packages']}"]
        with patch.object(runner.time, "sleep"):
            self.assertEqual(FIXTURE, runner.launch_fixture(run))
        self.assertEqual(3, run.send.call_count)

    def test_fixture_line_parses_every_field(self):
        line = (f"ARCE_RELEASE_BAY_FIXTURE logical={L} transfer=t rocket=r data={X} solar={Y} launch=SUCCESS "
                f"packages={FIXTURE['packages']}\n")
        self.assertEqual(FIXTURE, runner.parse_fixture(line))
        with self.assertRaises(runner.smoke.SmokeError):
            runner.parse_fixture("ARCE_RELEASE_BAY_FIXTURE logical=x\n")

    def test_status_line_parses_receipts_consumption_and_ownership(self):
        status = runner.parse_status(status_line(receipts=f"{OP}:{X}:REJECTED:INTERRUPTED:true:false", consumed=f"{OP}+-", owned="1"))
        self.assertEqual("NONE", status["claim"])
        self.assertEqual("false", status["locked"])
        self.assertEqual([dict(op=OP, satellite=X, phase="REJECTED", reason="INTERRUPTED", acknowledged=True, registered=False)],
                         status["receipts"])
        self.assertEqual([OP, None], status["consumed"])
        self.assertEqual(1, status["owned"])
        with self.assertRaises(runner.smoke.SmokeError):
            runner.parse_status(status_line().replace(" locked=false", ""))
        with self.assertRaises(runner.smoke.SmokeError):
            runner.parse_status(status_line().replace(" owned=0", ""))
        with self.assertRaises(runner.smoke.SmokeError):
            runner.parse_receipts("a:b:c")


class ExpectationTests(unittest.TestCase):
    def test_every_case_names_a_known_cut_an_end_and_resolvable_outcomes(self):
        cuts = {"B1_STAGED", "B1_MOVED", "B9_STAGED", "B9_MOVED", "B12_UNKNOWN", "B18_INTENT", "B18_REJECTED", "B3_STAGED",
                "B3_MOVED", "B12_D4", "B15_D4", "B8_STAGED", "B8_MOVED", "B12_D7", "B15_D7", "B6_D5", "B16_GUARD"}
        for case, spec in runner.CASES.items():
            self.assertTrue(spec.get("cut") is None or spec["cut"] in cuts, case)
            self.assertFalse(spec.get("cut") and spec.get("registry"), case)
            self.assertIn(spec["end"], ("kill", "stop", "crash", "land"), case)
            self.assertIn(spec["durable"], ("NONE", "INTENT", "REJECTED", "REGISTERED", "RETIRED"), case)
            self.assertEqual(2, len(spec["restarts"]), case)
            for name in spec["restarts"] + ((spec["first"],) if "first" in spec else ()):
                self.assertTrue(name in ("HELD", "COPY_HELD") or name in runner.OUTCOMES, case)
                if name == "COPY_HELD":
                    self.assertIn(spec["copy_hold"], ("PROTECTED_DUPLICATE", "UNEXPECTED_COPY"), case)
                    self.assertEqual("REGISTERED", spec["durable"], case)
            self.assertEqual(spec["end"] == "land", "first" in spec, case)
            if spec.get("hold"):
                self.assertEqual("INTENT", spec["durable"], case)
            if spec.get("at_stop"):
                self.assertTrue(spec.get("journal") and spec["end"] == "stop", case)
            if spec.get("refuse"):
                self.assertTrue(spec.get("cut", "").startswith(("B9_", "B18_REJECTED")), case)
            if spec.get("b16"):
                self.assertTrue(spec["cut"] == "B16_GUARD" and spec["end"] == "land" and spec["b16"] in runner.B16_ACTIONS, case)
            if spec.get("journal_hold"):
                self.assertTrue(spec["durable"] == "REGISTERED" and spec["journal_hold"] in (1, 2), case)

    def test_the_tranches_split_every_case_once_under_sixty_starts(self):
        listed = [case for tranche in runner.TRANCHES.values() for case in tranche]
        self.assertEqual(sorted(runner.CASES), sorted(listed))
        self.assertEqual(len(listed), len(set(listed)))
        for cases in runner.TRANCHES.values():
            self.assertLessEqual(3 * len(cases), 60)

    def test_copy_census_requires_every_survivor_seat_the_journal_seat_and_unique_identities(self):
        row = f"{X}:minecraft:overworld:hash:0:0:NONE:0:true"
        line = f"ARCE_RELEASE_BAY_CENSUS logical={L} count=1 transfer=COMMITTED assigned=true rows=[{row}]"
        self.assertEqual(1, runner.check_copy_census(line, L, 1)["count"])
        for bad in (line.replace("count=1", "count=2"), line.replace("assigned=true", "assigned=false"),
                    line.replace(":true]", ":false]"), line.replace(f"logical={L}", f"logical={X}"),
                    line.replace("rows=[", f"rows=[{row},").replace("count=1", "count=2"),
                    line.replace(":hash:0:", ":hash:bad:")):
            with self.assertRaises(runner.smoke.SmokeError):
                runner.check_copy_census(bad, L, 1)

    def test_copy_disk_requires_actual_saved_identity_hash_wrapper_and_offline_seat(self):
        census = dict(rows=[f"{X}:minecraft:overworld:hash:0:0:{Y}:1:true"])
        entity = dict(UUID=X, RocketEntityData=dict(assembly_transaction_id=L, snapshot=dict(content_hash="hash"),
                      resource_escrow=dict(ledger_id=Y, resource_epoch=1), flight_data=dict(passengers=dict(assignments=[
                          dict(passenger_id="00000000-0000-4000-8000-000000185001")]))))
        root = dict(Position=[0, 0], Entities=[entity])
        with patch.object(runner, "chunk_record", return_value=root):
            self.assertEqual([runner.copy_row(census["rows"][0])], runner.inspect_copy_disk(Path("fixture"), L, census))
        changed_hash, changed_wrapper, no_seat = (deepcopy(root) for _ in range(3))
        changed_hash["Entities"][0]["RocketEntityData"]["snapshot"]["content_hash"] = "other"
        changed_wrapper["Entities"][0]["RocketEntityData"]["resource_escrow"] = dict(resource_epoch=0)
        no_seat["Entities"][0]["RocketEntityData"]["flight_data"]["passengers"]["assignments"] = []
        for bad in (None, dict(Position=[0, 0], Entities=[]), dict(Position=[1, 0], Entities=[entity]),
                    dict(Position=[0, 0], Entities=[entity, entity]), changed_hash, changed_wrapper, no_seat):
            with patch.object(runner, "chunk_record", return_value=bad), self.assertRaises(runner.smoke.SmokeError):
                runner.inspect_copy_disk(Path("fixture"), L, census)

    def test_protected_copy_hold_keeps_registered_authority_and_registry_count(self):
        values = dict(claim="RESUME", hold="REPAIR", reason="PROTECTED_DUPLICATE", locked="true", transfer="PREPARED",
                      receipts=f"{OP}:{X}:REGISTERED:NONE:true:true", owned="1")
        case = "W185_B6_PROTECTED"
        good = runner.parse_status(status_line(**values))
        self.assertTrue(runner.settled(case, 1, good, FIXTURE))
        runner.check_settled(case, 1, good, FIXTURE)
        for change in (dict(locked="false"), dict(transfer="COMMITTED"), dict(hold="OWNED_WAIT"),
                       dict(reason="UNEXPECTED_COPY"), dict(receipts=f"{OP}:{X}:REGISTERED:NONE:false:true")):
            self.assertFalse(runner.settled(case, 1, runner.parse_status(status_line(**{**values, **change})), FIXTURE))
        with self.assertRaises(runner.smoke.SmokeError):
            runner.check_settled(case, 1, runner.parse_status(status_line(**{**values, "owned": "2"})), FIXTURE)

    def test_a_durable_intent_becomes_interrupted_and_a_relaunch_rearms_and_consumes(self):
        self.assertEqual(runner.INTERRUPTED, runner.expected_receipt("B1_MOVED", 1))
        self.assertEqual("LANDED", runner.outcome("B1_MOVED", 2, relaunched=True))
        self.assertIsNone(runner.expected_receipt("B1_MOVED", 2, relaunched=True), "the retired group leaves no receipt")
        self.assertIsNone(runner.expected_receipt("B1_STAGED", 1))
        self.assertEqual(runner.REFUSED, runner.expected_receipt("B9_MOVED", 2))
        self.assertEqual(runner.INTERRUPTED, runner.expected_receipt("B2_STAGED", 1), "X absent before the checkpoint")
        self.assertEqual("LANDED", runner.outcome("B2_MOVED", 1), "X durable after it: promotion")
        self.assertEqual("LANDED_UNCONSUMED", runner.outcome("B11_FULL", 1), "a full store refuses before INTENT")
        self.assertEqual("LANDED", runner.outcome("B17_FAIR", 1))
        self.assertEqual("CONSUMED_REST", runner.outcome("B8_MOVED", 1), "a retired group's T' recovers as ordinary")

    def test_settled_needs_no_claim_the_expected_receipt_the_consumption_and_the_rest(self):
        interrupted = f"{OP}:{X}:REJECTED:INTERRUPTED:true:false"
        self.assertTrue(runner.settled("B1_MOVED", 1, runner.parse_status(status_line(receipts=interrupted)), FIXTURE))
        self.assertFalse(runner.settled("B1_MOVED", 1, runner.parse_status(status_line(claim="OUTCOMES", receipts=interrupted)),
                                        FIXTURE), "a held claim is not settled")
        self.assertFalse(runner.settled("B1_MOVED", 1, runner.parse_status(status_line(
            receipts=f"{OP}:{X}:INTENT:NONE:true:false")), FIXTURE), "INTENT is not resolved")
        self.assertFalse(runner.settled("B1_MOVED", 1, runner.parse_status(status_line(
            receipts=f"{OP}:{X}:REJECTED:INTERRUPTED:false:false")), FIXTURE), "an unacknowledged outcome is not settled")
        self.assertFalse(runner.settled("B1_MOVED", 1, runner.parse_status(status_line(receipts=interrupted, state="TRANSIT",
                                                                                         y="50.0")), FIXTURE))
        self.assertFalse(runner.settled("B1_STAGED", 1, runner.parse_status(status_line(receipts=interrupted)), FIXTURE),
                         "a staged-only INTENT must leave no receipt")
        self.assertTrue(runner.settled("B12_UNKNOWN", 1, landed(), FIXTURE))
        self.assertFalse(runner.settled("B12_UNKNOWN", 1, landed(consumed="-+-"), FIXTURE), "the data bay must be consumed")
        self.assertFalse(runner.settled("B12_UNKNOWN", 1, landed(receipts=f"{OP}:{X}:REGISTERED:NONE:true:true"), FIXTURE),
                         "a REGISTERED receipt is not retired")
        self.assertFalse(runner.settled("B12_UNKNOWN", 1, landed(claim="RETIRE"), FIXTURE))
        self.assertTrue(runner.settled("B11_FULL", 1, landed(consumed="-+-", packages=FIXTURE["packages"], owned="0"), FIXTURE))
        self.assertTrue(runner.settled("B8_MOVED", 1, runner.parse_status(status_line(consumed=f"{OP}+-", packages=CONSUMED_PACKAGES,
                                                                                        owned="1")), FIXTURE))

    def test_settled_invariants_refuse_wrong_packages_registrations_and_foreign_receipts(self):
        runner.check_settled("B1_STAGED", 1, runner.parse_status(status_line()), FIXTURE)
        runner.check_settled("B12_UNKNOWN", 1, landed(), FIXTURE)
        for case, status in (("B1_STAGED", runner.parse_status(status_line(packages=CONSUMED_PACKAGES))),
                             ("B1_STAGED", runner.parse_status(status_line(owned="1"))),
                             ("B1_STAGED", runner.parse_status(status_line(receipts=f"{OP}:{Y}:REJECTED:RESEARCH_LOCKED:true:false"))),
                             ("B1_STAGED", runner.parse_status(status_line(receipts=f"{OP}:{OP}:REJECTED:INTERRUPTED:true:false"))),
                             ("B12_UNKNOWN", landed(packages=FIXTURE["packages"])),
                             ("B12_UNKNOWN", landed(owned="2")),
                             ("B12_UNKNOWN", landed(packages="c" * 64 + "+" + "d" * 64)),
                             ("B12_UNKNOWN", landed(consumed=f"{OP}+{OP}"))):
            with self.assertRaises(runner.smoke.SmokeError, msg=case):
                runner.check_settled(case, 1, status, FIXTURE)

    def test_the_survey_variant_consumes_both_bays_after_its_decommission(self):
        both = landed(packages="c" * 64 + "+" + "d" * 64, consumed=f"{OP}+{OP}")
        runner.check_settled("B4_DECOMMISSION", 1, both, FIXTURE)
        with self.assertRaises(runner.smoke.SmokeError):
            runner.check_settled("B4_DECOMMISSION", 1, landed(), FIXTURE)
        good = [f"ARCE_RELEASE_BAY_FIRST_TICK satellite={Y} tick=1 decommission=SUCCESS launch=DEPLOYMENT_PENDING\n"]
        self.assertEqual("SUCCESS", runner.check_first_tick("B4_DECOMMISSION", good, FIXTURE)["decommission"])
        self.assertEqual(Y, runner.first_tick_satellite("B4_DECOMMISSION", FIXTURE))
        with self.assertRaises(runner.smoke.SmokeError):
            runner.check_first_tick("B4_DECOMMISSION", [good[0].replace("SUCCESS", "DEPLOYMENT_PENDING")], FIXTURE)

    def test_a_blocked_registry_hold_needs_the_held_claim_an_unchanged_intent_and_the_lock_in_transit(self):
        intent = f"{OP}:{X}:INTENT:NONE:true:false"
        held = dict(claim="OUTCOMES", hold="REPAIR", reason="REGISTRY_BLOCKED", receipts=intent, transfer="PREPARED",
                    state="TRANSIT", y="50.0", locked="true")
        self.assertEqual(runner.HELD_INTENT, runner.expected_receipt("B10_BLOCKED", 2))
        self.assertTrue(runner.settled("B10_BLOCKED", 1, runner.parse_status(status_line(**held)), FIXTURE))
        for change in (dict(claim="NONE"), dict(hold="OWNED_WAIT"), dict(reason="TRANSFER_AUTHORITY_CONFLICT"),
                       dict(locked="false"), dict(receipts=f"{OP}:{X}:REJECTED:INTERRUPTED:true:false"),
                       dict(receipts=f"{OP}:{X}:INTENT:NONE:false:false"), dict(transfer="NONE"), dict(state="FUELED"),
                       dict(y="2.0")):
            self.assertFalse(runner.settled("B10_BLOCKED", 1, runner.parse_status(status_line(**{**held, **change})), FIXTURE),
                             change)

    def test_a_held_restart_needs_the_seam_one_repair_transition_no_write_and_its_pending_charge(self):
        lines = [runner.REGISTRY_BLOCKED + "\n",
                 f"ARCE_BAY_CLAIM transfer=t logical={L} state=OWNED_WAIT reason=BAY_RESTART\n",
                 f"ARCE_BAY_CLAIM transfer=t logical={L} state=REPAIR reason=REGISTRY_BLOCKED\n"]
        self.assertEqual(1, runner.check_held_restart("B10_BLOCKED", 1, lines, FIXTURE, "d", "d", dict(pending=1))["transitions"])
        for found, before, after, pending in ((lines[1:], "d", "d", dict(pending=1)), (lines + lines[2:], "d", "d", dict(pending=1)),
                                              (lines, "d", "e", dict(pending=1)), (lines, None, None, dict(pending=1)),
                                              (lines, "d", "d", dict(pending=0))):
            with self.assertRaises(runner.smoke.SmokeError):
                runner.check_held_restart("B10_BLOCKED", 1, found, FIXTURE, before, after, pending)

    def test_a_stop_while_d0_waits_must_latch_with_the_claim_unresolved_and_act_no_more(self):
        waiting = f"ARCE_BAY_CLAIM transfer=t logical={L} state=OWNED_WAIT reason=ASSEMBLY_JOURNAL_CHECKPOINT\n"
        stop = "ARCE_BAY_STOPPED claims=1 unresolved=1\n"
        self.assertEqual(1, runner.check_stopped_while_waiting("B14_STOP", [waiting, stop], FIXTURE)["unresolved"])
        for lines in ([waiting], [waiting, stop, stop], [waiting, stop.replace("unresolved=1", "unresolved=0")],
                      [waiting, stop, waiting], [waiting.replace("ASSEMBLY_JOURNAL_CHECKPOINT", "BAY_VALIDATION"), stop],
                      [waiting, stop, f"ARCE_RELEASE_BAY_PAUSED cut=B1_MOVED op={OP}\n"]):
            with self.assertRaises(runner.smoke.SmokeError):
                runner.check_stopped_while_waiting("B14_STOP", lines, FIXTURE)

    def test_the_first_tick_must_refuse_decommission_and_launch_of_a_durable_intent(self):
        good = [f"ARCE_RELEASE_BAY_FIRST_TICK satellite={X} tick=1 decommission=DEPLOYMENT_PENDING launch=DEPLOYMENT_PENDING\n"]
        for case in ("B1_MOVED", "B2_MOVED", "B3_STAGED"):
            self.assertEqual(1, runner.check_first_tick(case, good, FIXTURE)["tick"], case)
        self.assertIsNone(runner.check_first_tick("B1_STAGED", [], FIXTURE))
        self.assertIsNone(runner.first_tick_satellite("B3_MOVED", FIXTURE), "a durable REGISTERED runs no first-tick hook")
        for lines in ([], [good[0].replace("decommission=DEPLOYMENT_PENDING", "decommission=SUCCESS")], good * 2):
            with self.assertRaises(runner.smoke.SmokeError):
                runner.check_first_tick("B1_MOVED", lines, FIXTURE)

    def test_a_checkpoint_failure_reports_unproved_and_never_rejects(self):
        injected = "[04:40:25] [ARCE-satellite-registry/INFO] [x/]: ARCE_RELEASE_SRW_INJECTED point=STAGED mode=FAIL\n"
        lines = [f"ARCE_BAY_CLAIM transfer=t logical={L} state=OWNED_WAIT reason=BAY_REGISTRY_PROOF\n", injected, injected, injected,
                 f"ARCE_BAY_CLAIM transfer=t logical={L} state=OWNED_WAIT reason=REGISTRY_UNPROVED\n",
                 f"ARCE_BAY_CLAIM transfer=t logical={L} state=READY_SOURCE reason=READY_SOURCE\n"]
        observed = runner.check_unproved("B13_FAILED", lines, FIXTURE)
        self.assertIn("REGISTRY_UNPROVED", observed["reasons"])
        self.assertEqual(3, observed["failures"])
        unknown = [line.replace("point=STAGED mode=FAIL", "point=MOVED mode=UNKNOWN") for line in lines]
        self.assertEqual(3, runner.check_unproved("B13_UNKNOWN", unknown, FIXTURE)["failures"])
        for found in (lines[:4] + lines[5:], lines[:5]):
            with self.assertRaises(runner.smoke.SmokeError):
                runner.check_unproved("B13_FAILED", found, FIXTURE)

    def test_unproved_pins_the_third_failure(self):
        """W170-R6: REGISTRY_UNPROVED after two or four injected failures fails the case."""
        injected = "ARCE_RELEASE_SRW_INJECTED point=STAGED mode=FAIL\n"
        unproved = f"ARCE_BAY_CLAIM transfer=t logical={L} state=OWNED_WAIT reason=REGISTRY_UNPROVED\n"
        ready = f"ARCE_BAY_CLAIM transfer=t logical={L} state=READY_SOURCE reason=READY_SOURCE\n"
        for count in (2, 4):
            with self.assertRaises(runner.smoke.SmokeError):
                runner.check_unproved("B13_FAILED", [injected] * count + [unproved, ready], FIXTURE)
        later = [injected] * 3 + [unproved, injected, ready]
        self.assertEqual(3, runner.check_unproved("B13_FAILED", later, FIXTURE)["failures"], "only failures before it count")

    def test_b16_needs_one_refused_guard_then_a_fresh_proof_before_ready_source(self):
        """W170-R22: a stale token must not retire; the refusal is followed by D6 again, then the one READY_SOURCE."""
        claim = lambda state, reason: f"ARCE_BAY_CLAIM transfer=t logical={L} state={state} reason={reason}\n"
        refused = f"ARCE_BAY_RETIRE_REFUSED transfer=t logical={L} outcome=GUARD_REFUSED\n"
        good = [claim("OWNED_WAIT", "BAY_RECEIPT_WRITE"), refused, claim("OWNED_WAIT", "BAY_SOURCE_PROOF"),
                claim("OWNED_WAIT", "BAY_RECEIPT_WRITE"), claim("READY_SOURCE", "READY_SOURCE")]
        self.assertEqual(3, len(runner.check_fresh_proof("B16_MOTION", good, FIXTURE)["after_refusal"]))
        for lines in (good[:1] + good[2:], good[:2] + good[3:], good + [refused], good[:2] + [good[4]]):
            with self.assertRaises(runner.smoke.SmokeError):
                runner.check_fresh_proof("B16_MOTION", lines, FIXTURE)

    def test_b16_allows_a_changed_receipt_file_only_after_the_fresh_proofs_own_d7(self):
        """W170-R45: a proof line alone, which follows every refusal, never excuses a changed file."""
        claim = lambda reason, logical=L: f"ARCE_BAY_CLAIM transfer=t logical={logical} state=OWNED_WAIT reason={reason}\n"
        refused = f"ARCE_BAY_RETIRE_REFUSED transfer=t logical={L} outcome=GUARD_REFUSED\n"
        paused = [claim("BAY_RECEIPT_WRITE"), "ARCE_RELEASE_BAY_RELEASE_REQUESTED\n"]
        proof, retire = claim("BAY_SOURCE_PROOF"), claim("BAY_RECEIPT_WRITE")
        self.assertTrue(runner.check_refused_file("B16_MOTION", paused + [refused, proof], 1, FIXTURE, "d", "d"),
                        "an unchanged file always passes")
        self.assertTrue(runner.check_refused_file("B16_MOTION", paused + [refused], 1, FIXTURE, "d", "d"))
        self.assertFalse(runner.check_refused_file("B16_MOTION", paused + [refused, proof, retire], 1, FIXTURE, "d", "e"),
                         "the fresh proof's own D7 may change the file")
        for lines in (paused + [refused], paused + [refused, proof], paused + [refused, retire, proof],
                      paused + [refused, proof, claim("BAY_RECEIPT_WRITE", X)], paused + [retire, refused, proof]):
            with self.assertRaises(runner.smoke.SmokeError, msg=lines):
                runner.check_refused_file("B16_MOTION", lines, 1, FIXTURE, "d", "e")
        with self.assertRaises(runner.smoke.SmokeError):
            runner.check_refused_file("B16_MOTION", paused, 1, FIXTURE, "d", "d")

    def test_a_held_startup_writer_needs_r1_to_renew_only_after_its_release(self):
        """W170-R23: the claim's first ticket renewal follows the released startup write."""
        released = "ARCE_RELEASE_JOURNAL_STARTUP_RELEASED\n"
        renewed = f"ARCE_BAY_RESUME_TICKETS transfer=t logical={L} stage=RESUME\n"
        other = renewed.replace(L, X)
        self.assertEqual(1, runner.check_tickets_after_release("B3_JOURNAL_HELD", [released, renewed], FIXTURE))
        self.assertEqual(2, runner.check_tickets_after_release("B3_JOURNAL_HELD", [other, released, other, renewed], FIXTURE))
        for lines in ([renewed, released], [released], [renewed], [released, released, renewed]):
            with self.assertRaises(runner.smoke.SmokeError):
                runner.check_tickets_after_release("B3_JOURNAL_HELD", lines, FIXTURE)
        self.assertEqual("LANDED", runner.outcome("B3_JOURNAL_HELD", 1))
        self.assertEqual("LANDED", runner.outcome("B16_MOTION", 0))


if __name__ == "__main__":
    unittest.main()
