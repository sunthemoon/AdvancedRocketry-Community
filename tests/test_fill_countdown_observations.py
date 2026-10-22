"""The native countdown oracle must reject ambiguous launch traces and persisted authority drift."""
import copy
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import launch_trace_observations as trace
import run_v180_fill_countdown as cuts
from test_retire_before_fill_cuts import fixture


def countdown(cut="ASSEMBLED"):
    disk = fixture(True)
    flight = disk["rocket"]["RocketEntityData"]["flight_data"]
    flight["state"] = "COUNTDOWN"
    flight["plan"] = {"request_id": "request"}
    frozen = copy.deepcopy(flight); frozen["state"] = "TRANSIT"
    native = dict(armed={"transfer": "request"}, source=dict(flight_changes={"state": cut}, flight_absent=["plan"]))
    disk["transfers"] = [dict(phase="PREPARED", native_transfer=native, source_flight=frozen,
                             logical_rocket_id="logical", source_entity_id="entity")]
    disk["ledger"]["armed_flights"] = [native["armed"]]
    return disk


def lines(*events):
    return [f"ARCE_LAUNCH_TRACE cut=case request=request check={check} detail={detail}" for check, detail in events]


class FillCountdownObservationTest(unittest.TestCase):
    def test_countdown_requires_prepare_and_keeps_returned_code(self):
        found = trace.observations(lines(("REQUEST", "MOON"), ("PREPARE_ENTER", ""), ("PREPARE_OK", ""),
                                        ("CHECKPOINT_QUEUED", ""), ("RESULT", "JOURNAL_PENDING"), ("COUNTDOWN", "governed=true")))
        self.assertEqual("JOURNAL_PENDING", found[0]["code"])
        self.assertEqual("COUNTDOWN", found[0]["answer"]["check"])
        self.assertTrue(found[0]["prepare_reached"])

    def test_precondition_refusal_and_prepare_exception_are_distinct(self):
        for check, detail in (("BEGIN_PRECONDITION", "GUARD_HAS_TRANSFER"), ("TRANSFER_PRECONDITION", "RESOURCE_MUTATION_F1B")):
            found = trace.observations(lines(("REQUEST", "MOON"), (check, detail), ("RESULT", "TRANSFER_JOURNAL_BLOCKED")))[0]
            self.assertFalse(found["prepare_reached"])
            self.assertEqual(dict(check=check, detail=detail), found["answer"])
        message = "IllegalArgumentException: Launch changed the checkpointed source flight"
        found = trace.observations(lines(("REQUEST", "MOON"), ("PREPARE_ENTER", ""), ("PREPARE_EXCEPTION", message),
                                        ("RESULT", "TRANSFER_JOURNAL_BLOCKED")))[0]
        self.assertTrue(found["prepare_reached"])
        self.assertEqual(message, found["answer"]["detail"])

    def test_missing_ambiguous_and_unprepared_results_are_not_evidence(self):
        for events in (
            (("REQUEST", "MOON"), ("RESULT", "TRANSFER_JOURNAL_BLOCKED")),
            (("REQUEST", "MOON"), ("RESULT", "SUCCESS"), ("COUNTDOWN", "governed=true")),
            (("REQUEST", "MOON"), ("RESULT", "TRANSFER_JOURNAL_BLOCKED"), ("PREPARE_EXCEPTION", "message")),
            (("REQUEST", "MOON"), ("RESULT", "TRANSFER_JOURNAL_BLOCKED"), ("BEGIN_PRECONDITION", "GUARD_HAS_TRANSFER"),
             ("TRANSFER_PRECONDITION", "RESOURCE_MUTATION_F1B")),
        ):
            with self.subTest(events=events), self.assertRaises(cuts.smoke.SmokeError):
                trace.observations(lines(*events))

    def test_both_frozen_source_shapes_are_checked(self):
        for cut in cuts.CUTS:
            original = countdown(cut)
            self.assertEqual(cuts.check(original, cut), cuts.check(copy.deepcopy(original), cut))

    def test_rejects_fuel_source_and_authority_drift(self):
        mutations = (
            lambda d: d["transfers"][0]["native_transfer"]["source"]["flight_changes"].update(state="FUELED"),
            lambda d: d["rocket"]["RocketEntityData"]["flight_data"]["fuel"]["propulsion"]["primary"].update(amount=999),
            lambda d: d["ledger"]["floors"].append({}),
            lambda d: d["transfers"].clear(),
            lambda d: d["ledger"]["armed_flights"].clear(),
            lambda d: d["loader"]["arce_fluid_fuel_loader"]["binding"].update(physical_rocket="other"),
        )
        for mutate in mutations:
            disk = countdown(); mutate(disk)
            with self.assertRaises(cuts.smoke.SmokeError):
                cuts.check(disk, "ASSEMBLED")


if __name__ == "__main__":
    unittest.main()
