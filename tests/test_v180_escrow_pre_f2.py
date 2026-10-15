import copy
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_escrow_pre_f2 as runner


class PreF2OracleTest(unittest.TestCase):
    def test_only_the_two_written_record_cases_expect_armed_after_restart(self):
        self.assertEqual(len(runner.CASES), 5)
        self.assertEqual([cut for cut in runner.CASES if runner.has_transfer(cut)],
                         ["PF_T_ACK", "PF_GOVERNED_T_ACK"])

    def test_governed_oracle_requires_independent_versions_exact_arm_and_no_publication(self):
        arm = {"serial": 1, "outcome": "ARMED", "d": "fixed-destination"}
        record = {"schema_version": 3, "phase": "PREPARED",
                  "source_flight": {"schema_version": 2}, "destination_flight": {"schema_version": 2},
                  "native_transfer": {"schema_version": 1, "armed": arm.copy(), "signature_kind": "FUEL_VECTOR_V1"}}
        runner.require_governed_record(record, arm)
        for key, value in [("schema_version", 2), ("phase", "COMMITTED"),
                           ("destination_entity_id", "fixed-destination"), ("native_transfer", {})]:
            changed = copy.deepcopy(record)
            changed[key] = value
            with self.assertRaises(runner.smoke.SmokeError):
                runner.require_governed_record(changed, arm)
        changed = copy.deepcopy(record)
        changed["native_transfer"]["armed"]["serial"] = 2
        with self.assertRaises(runner.smoke.SmokeError):
            runner.require_governed_record(changed, arm)
        changed = copy.deepcopy(record)
        changed["source_flight"]["schema_version"] = 3
        with self.assertRaises(runner.smoke.SmokeError):
            runner.require_governed_record(changed, arm)


if __name__ == "__main__":
    unittest.main()
