"""R54 distinguishes raw conservation, queued second-fill admission, and a launch request's result."""
import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import loader_named_cut_oracle as named
import run_v180_loader_cuts as runner


def lines(cut, admitted, allowed):
    attempt, result = ("READY", "PREPARING") if admitted else ("TRANSFER_JOURNAL_UNPROVED", "TRANSFER_JOURNAL_UNPROVED")
    return [f"ARCE_LOADER_MATRIX_LAUNCH cut={cut} code=TRANSFER_JOURNAL_BLOCKED required_fuel=500 retained_transfer=true mutation_allowed={str(allowed).lower()}",
            f"ARCE_LOADER_MATRIX_SECOND_FILL cut={cut} attempt={attempt} result={result} admitted={str(admitted).lower()} next_before=3 next_after={3 + admitted} pending_before=0 pending_after={int(admitted)} durable_records=0 resources_unchanged=true",
            f"ARCE_LOADER_MATRIX_FOLLOWUP_PAUSED cut={cut} hook=AFTER_REQUESTS_BEFORE_L1"]


class FollowUpObservationsTest(unittest.TestCase):
    def test_selected_cases_include_both_k2_and_ack_variants(self):
        self.assertEqual(7, len(runner.FOLLOW_UPS))
        for variant in ("DISTINCT", "COALESCED"):
            for suffix in ("", "_ACK"):
                self.assertIn("LF_M_R1_FLOORS_255_" + variant + suffix, runner.FOLLOW_UPS)
        self.assertIn("LF_M_NAMED_E5", runner.FOLLOW_UPS)
        for stop in ("K2", "K9"):
            self.assertIn("LF_M_NAMED_CROSS_" + stop, runner.FOLLOW_UPS)

    def test_real_admission_and_launch_are_not_inferred_from_mutation_permission(self):
        for cut in runner.FOLLOW_UPS:
            for admitted, allowed in ((True, True), (False, False), (False, True)):
                result = named.follow_up_observations(cut, lines(cut, admitted, allowed))
                self.assertEqual(admitted, result["second_fill"]["admitted"])
                self.assertEqual(allowed, result["mutation_allowed"])
                self.assertFalse(result["second_fill"]["durable"])
                self.assertFalse(result["second_fill"]["completed"])
                self.assertEqual("KNOWN_LIMITATION", result["launch"]["outcome"])
                self.assertEqual("TRANSFER_JOURNAL_BLOCKED", result["launch"]["code"])
                self.assertNotIn("usability", result)

    def test_missing_repeated_or_inconsistent_observations_are_findings(self):
        cut = "LF_M_NAMED_E5"; good = lines(cut, True, True)
        bad_sets = [good[:2], good[1:], good + [good[0]]]
        for old, new in (("admitted=true", "admitted=false"), ("next_after=4", "next_after=3"),
                         ("pending_after=1", "pending_after=0"), ("durable_records=0", "durable_records=1"),
                         ("resources_unchanged=true", "resources_unchanged=false"), ("attempt=READY", "attempt=ROCKET_BUSY"),
                         ("result=PREPARING", "result=READY"), ("retained_transfer=true", "retained_transfer=false"),
                         ("code=TRANSFER_JOURNAL_BLOCKED", "code=SUCCESS")):
            bad_sets.append([line.replace(old, new) for line in good])
        for bad in bad_sets:
            with self.subTest(lines=bad), self.assertRaises(named.base.smoke.SmokeError):
                named.follow_up_observations(cut, bad)


if __name__ == "__main__":
    unittest.main()
