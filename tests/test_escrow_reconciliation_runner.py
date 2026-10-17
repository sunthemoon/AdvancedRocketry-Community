import copy
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_escrow_reconciliation as runner


class ReconciliationRunnerTest(unittest.TestCase):
    def sample(self, records=0, issued=1, amount=1000, source_amount=4000, retained=False):
        root = dict(ledger_id="ledger", issued_through=issued, records=[dict(phase="PREPARED", reason="NONE")] * records,
                    floors=[], tombstones=[], armed_flights=[], rocket_authorities=[], issued_launch_serial=0)
        source = dict(arce_escrow_fixture=dict(identity="source", amount=source_amount),
                      resource_escrow=dict(ledger_id="ledger", resource_epoch=(5000-source_amount)//1000,
                        markers=[dict(op_id="A", seq=1, role="SOURCE", epoch_after=1)] if retained else []))
        rocket = dict(UUID="physical", id="advancedrocketrycommunity:rocket", Pos=[64.5, 100., 64.5],
                      RocketEntityData=dict(assembly_transaction_id="logical", owner_id="owner", snapshot=dict(id="snapshot"),
                        resource_escrow=dict(ledger_id="ledger", resource_epoch=amount//1000, markers=[]),
                        flight_data=dict(logical_rocket_id="logical", fuel=dict(amount=amount, committed_debits=[]),
                                         passengers=dict(assignments=[]))))
        return root, [source], rocket

    def test_all_declared_cuts_are_unique_and_both_stores_have_every_boundary(self):
        self.assertEqual(33, len(runner.CASES))
        self.assertEqual(len(runner.CASES), len(set(runner.CASES)))
        for endpoint in ("SOURCE", "DESTINATION"):
            for stage in ("STORE", "FORCE", "READBACK"):
                for side in ("BEFORE", "AFTER"):
                    self.assertIn(f"CR_{endpoint}_{stage}_{side}", runner.CASES)

    def test_before_l1_cannot_debit_or_credit(self):
        sample = self.sample(issued=0, amount=0, source_amount=5000)
        runner.require_state(*sample, "CR_L1_BEFORE", True)
        sample[1][0]["arce_escrow_fixture"]["amount"] = 4000
        with self.assertRaises(runner.smoke.SmokeError):
            runner.require_state(*sample, "CR_L1_BEFORE", True)

    def test_mixed_durable_prefixes_need_l1_and_cannot_survive_retirement(self):
        sample = self.sample(records=1, amount=0)
        runner.require_state(*sample, "CR_SOURCE_STORE_AFTER", False)
        sample[0]["records"] = []
        with self.assertRaises(runner.smoke.SmokeError):
            runner.require_state(*sample, "CR_L2_REPLACED", False)

    def test_restart_requires_conservation_pruned_markers_and_same_identity(self):
        sample = self.sample(retained=True)
        identity = runner.require_state(*sample, "CR_C8_RELEASED", True)
        self.assertEqual("physical", identity["physical"])
        bad = copy.deepcopy(sample)
        bad[2]["RocketEntityData"]["resource_escrow"]["markers"] = [dict(op_id="retired")]
        with self.assertRaises(runner.smoke.SmokeError):
            runner.require_state(*bad, "CR_C8_RELEASED", True)
        bad = copy.deepcopy(sample)
        bad[2]["RocketEntityData"]["flight_data"]["logical_rocket_id"] = "foreign"
        with self.assertRaises(runner.smoke.SmokeError):
            runner.require_state(*bad, "CR_C8_RELEASED", True)

    def test_source_and_destination_mismatch_require_the_persisted_manual_reason(self):
        for cut in ("CR_C2_MISMATCH", "CR_C2_DESTINATION_MISMATCH"):
            sample = self.sample(records=1, amount=0, source_amount=5000)
            sample[0]["records"] = [dict(phase="REPAIR", reason="OTHER_STATE")]
            runner.require_state(*sample, cut, True)
            sample[0]["records"][0]["reason"] = "NONE"
            with self.assertRaises(runner.smoke.SmokeError):
                runner.require_state(*sample, cut, True)

    def test_later_member_and_stale_binding_restarts_finish_exactly_two_fills(self):
        for cut in ("CR_LATER_WAIT", "CR_STALE_BIND", "CR_LATER_L2_REPLACED"):
            sample = self.sample(issued=2, amount=2000)
            sample[1].append(copy.deepcopy(sample[1][0]))
            sample[1][1]["arce_escrow_fixture"]["identity"] = "second"
            if cut != "CR_LATER_WAIT":
                sample[1][0]["resource_escrow"]["markers"] = [dict(op_id="A", seq=1, role="SOURCE", epoch_after=1)]
            runner.require_state(*sample, cut, True)
            sample[1][1]["arce_escrow_fixture"]["amount"] = 5000
            with self.assertRaises(runner.smoke.SmokeError):
                runner.require_state(*sample, cut, True)

    def test_partial_l2_cut_requires_both_markers_and_only_the_later_record(self):
        sample = self.sample(records=1, issued=2, amount=2000)
        sample[1].append(copy.deepcopy(sample[1][0]))
        sample[0]["records"][0]["op_id"] = "B"
        sample[2]["RocketEntityData"]["resource_escrow"]["markers"] = [dict(op_id="A", seq=1), dict(op_id="B", seq=2)]
        runner.require_state(*sample, "CR_LATER_L2_REPLACED", False)
        empty = copy.deepcopy(sample)
        empty[0]["records"].clear()
        with self.assertRaises(runner.smoke.SmokeError):
            runner.require_state(*empty, "CR_LATER_L2_REPLACED", False)
        sample[2]["RocketEntityData"]["resource_escrow"]["markers"].pop(0)
        with self.assertRaises(runner.smoke.SmokeError):
            runner.require_state(*sample, "CR_LATER_L2_REPLACED", False)

    def test_l2_restart_requires_product_retained_source_marker_not_probe_cleanup(self):
        sample = self.sample(retained=True)
        runner.require_state(*sample, "CR_L2_REPLACED", True)
        sample[1][0]["resource_escrow"]["markers"].clear()
        with self.assertRaises(runner.smoke.SmokeError):
            runner.require_state(*sample, "CR_L2_REPLACED", True)
        runner.require_state(*sample, "CR_C6_JOINT_READBACK", True)


if __name__ == "__main__":
    unittest.main()
