"""Terminal native disk oracle: acknowledgement order and immutable survivor checks."""
import copy
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_escrow_terminal as terminal


def fixture(cut, restarted=False):
    flight = dict(state="LANDED", state_started_game_time=100,
                  fuel=dict(amount=500, committed_debits=[dict(transaction_id="T")]), passengers=dict(assignments=[]))
    destination = dict(UUID="D", id="advancedrocketrycommunity:rocket", Pos=[80.5, 100., 64.5], Motion=[0., 0., 0.],
                       NoGravity=True, Rotation=[0., 0.], OnGround=False, Invulnerable=False,
                       RocketEntityData=dict(schema_version=3, owner_id="O", assembly_transaction_id="L", snapshot={"id": "snapshot"},
                                             flight_data=flight, resource_escrow=dict(schema_version=1, ledger_id="R", resource_epoch=0, markers=[])))
    armed = dict(outcome="ARMED" if cut == "FT_HANDOFF_BEFORE" else "HANDED_OFF", serial=1, ledger="R", d="D", src="S", l="L", o="O", t="T")
    pruned = restarted or cut in ("FT_PRUNE_REPLACED", "FT_PRUNE_ACK")
    root = dict(ledger_id="R", issued_launch_serial=1, records=[], floors=[], tombstones=[], rocket_authorities=[],
                armed_flights=[] if pruned else [armed])
    native_arm = copy.deepcopy(armed); native_arm["outcome"] = "ARMED"
    frozen = copy.deepcopy(flight); frozen.update(state="TRANSIT", plan={}, active_transfer_id="T")
    transfer = dict(schema_version=3, phase="COMMITTED", native_transfer=dict(armed=native_arm), transfer_id="T",
                    destination_entity_id="D", destination_snapshot={"id": "snapshot"}, destination_flight=frozen)
    present = not restarted and (cut.startswith("FT_HANDOFF") or cut == "FT_JOURNAL_BEFORE")
    return root, [transfer] if present else [], None, destination, cut, restarted


class TerminalNativeOracleTest(unittest.TestCase):
    def test_every_cut_and_both_restarts_preserve_the_same_survivor(self):
        for cut in terminal.CASES:
            with self.subTest(cut=cut):
                before = terminal.require_state(*fixture(cut))
                for _ in range(2):
                    self.assertEqual(before, terminal.require_state(*fixture(cut, True)))

    def test_missing_or_duplicate_survivor_and_restored_source_are_rejected(self):
        for index, value in ((2, {}), (3, None)):
            values = list(fixture("FT_HANDOFF_ACK")); values[index] = value
            with self.assertRaises(terminal.phases.smoke.SmokeError): terminal.require_state(*values)

    def test_early_prune_and_unacknowledged_journal_survival_are_rejected(self):
        values = list(fixture("FT_JOURNAL_BEFORE")); values[0]["armed_flights"].clear()
        with self.assertRaises(terminal.phases.smoke.SmokeError): terminal.require_state(*values)
        values = list(fixture("FT_JOURNAL_ACK")); values[1].append({})
        with self.assertRaises(terminal.phases.smoke.SmokeError): terminal.require_state(*values)

    def test_replayed_debit_and_changed_fuel_or_epoch_are_rejected(self):
        for field, value in (("amount", 0), ("committed_debits", [dict(transaction_id="T")] * 2)):
            values = list(fixture("FT_HANDOFF_ACK")); values[3]["RocketEntityData"]["flight_data"]["fuel"][field] = value
            with self.assertRaises(terminal.phases.smoke.SmokeError): terminal.require_state(*values)
        values = list(fixture("FT_PRUNE_ACK")); values[3]["RocketEntityData"]["resource_escrow"]["resource_epoch"] = 1
        with self.assertRaises(terminal.phases.smoke.SmokeError): terminal.require_state(*values)

    def test_wrong_outcome_identity_or_frozen_snapshot_is_rejected(self):
        for field, value in (("outcome", "CANCELLED"), ("d", "S"), ("t", "other")):
            values = list(fixture("FT_HANDOFF_ACK")); values[0]["armed_flights"][0][field] = value
            with self.assertRaises(terminal.phases.smoke.SmokeError): terminal.require_state(*values)
        values = list(fixture("FT_HANDOFF_ACK")); values[1][0]["destination_snapshot"] = {}
        with self.assertRaises(terminal.phases.smoke.SmokeError): terminal.require_state(*values)


if __name__ == "__main__": unittest.main()
