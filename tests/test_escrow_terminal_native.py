"""Terminal native disk oracle: acknowledgement order and immutable survivor checks."""
import copy
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_escrow_terminal as terminal


def fixture(cut, restarted=False):
    cancel = cut.startswith("FT_CANCEL_"); stage_cut = cut.replace("FT_CANCEL_", "FT_")
    flight = dict(state="FUELED" if cancel else "LANDED", state_started_game_time=100,
                  fuel=dict(amount=1000 if cancel else 500, committed_debits=[dict(transaction_id="prior" if cancel else "T")]), passengers=dict(assignments=[]))
    destination = dict(UUID="S" if cancel else "D", id="advancedrocketrycommunity:rocket", Pos=[64.5 if cancel else 80.5, 100., 64.5], Motion=[0., 0., 0.],
                       NoGravity=True, Rotation=[0., 0.], OnGround=False, Invulnerable=False,
                       RocketEntityData=dict(schema_version=3, owner_id="O", assembly_transaction_id="L", snapshot={"id": "snapshot"},
                                             flight_data=flight, resource_escrow=dict(schema_version=1, ledger_id="R", resource_epoch=7 if cancel else 0, markers=[])))
    armed = dict(outcome="ARMED" if stage_cut == "FT_HANDOFF_BEFORE" else "CANCELLED" if cancel else "HANDED_OFF",
                 serial=1, epoch=7 if cancel else 1, ledger="R", d="D", src="S", l="L", o="O", t="T")
    pruned = restarted or stage_cut in ("FT_PRUNE_REPLACED", "FT_PRUNE_ACK")
    root = dict(ledger_id="R", issued_launch_serial=1, records=[], floors=[], tombstones=[], rocket_authorities=[],
                armed_flights=[] if pruned else [armed])
    native_arm = copy.deepcopy(armed); native_arm["outcome"] = "ARMED"
    frozen = copy.deepcopy(flight); frozen.update(state="TRANSIT", plan={}, active_transfer_id="T")
    transfer = dict(schema_version=3, phase="COMMITTED", native_transfer=dict(armed=native_arm), transfer_id="T",
                    destination_entity_id="D", destination_snapshot={"id": "snapshot"}, destination_flight=frozen)
    if cancel:
        transfer.update(phase="PREPARED", source_entity_id="S", source_snapshot={"id": "snapshot"}, source_flight=frozen,
                        created_at_game_time=10)
        transfer.pop("destination_entity_id")
        transfer["native_transfer"]["source"] = dict(flight_changes=dict(state="FUELED", state_started_game_time=1),
                                                     flight_absent=["plan", "active_transfer_id"], yaw=0., pitch=0., invulnerable=False)
    present = not restarted and (stage_cut.startswith("FT_HANDOFF") or stage_cut == "FT_JOURNAL_BEFORE")
    return root, [transfer] if present else [], destination if cancel else None, None if cancel else destination, cut, restarted


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

    def test_cancelled_source_cannot_gain_a_debit_refund_or_different_epoch(self):
        for field, value in (("amount", 1500), ("amount", 500), ("committed_debits", [dict(transaction_id="T")]),
                             ("committed_debits", []), ("committed_debits", [dict(transaction_id="changed")])):
            values = list(fixture("FT_CANCEL_HANDOFF_ACK"))
            values[2]["RocketEntityData"]["flight_data"]["fuel"][field] = value
            with self.subTest(field=field, value=value), self.assertRaises(terminal.phases.smoke.SmokeError):
                terminal.require_state(*values)
        values = list(fixture("FT_CANCEL_PRUNE_ACK")); values[2]["RocketEntityData"]["resource_escrow"]["resource_epoch"] = 0
        with self.assertRaises(terminal.phases.smoke.SmokeError): terminal.require_state(*values)

    def test_cancelled_cut_requires_source_absence_of_destination_and_exact_authority(self):
        for index, value in ((2, None), (3, {})):
            values = list(fixture("FT_CANCEL_HANDOFF_ACK")); values[index] = value
            with self.assertRaises(terminal.phases.smoke.SmokeError): terminal.require_state(*values)
        for field, value in (("outcome", "HANDED_OFF"), ("src", "D"), ("epoch", 0), ("t", "changed")):
            values = list(fixture("FT_CANCEL_HANDOFF_ACK")); values[0]["armed_flights"][0][field] = value
            with self.subTest(field=field), self.assertRaises(terminal.phases.smoke.SmokeError): terminal.require_state(*values)
        for field, value in (("phase", "COMMITTED"), ("source_snapshot", {}), ("destination_entity_id", "D")):
            values = list(fixture("FT_CANCEL_HANDOFF_ACK")); values[1][0][field] = value
            with self.subTest(field=field), self.assertRaises(terminal.phases.smoke.SmokeError): terminal.require_state(*values)

    def test_cancelled_source_requires_returned_clock_and_full_projection(self):
        for field, value in (("state_started_game_time", 1), ("state_started_game_time", 9), ("current_origin", {})):
            values = list(fixture("FT_CANCEL_HANDOFF_ACK")); values[2]["RocketEntityData"]["flight_data"][field] = value
            with self.subTest(field=field, value=value), self.assertRaises(terminal.phases.smoke.SmokeError):
                terminal.require_state(*values)


if __name__ == "__main__": unittest.main()
