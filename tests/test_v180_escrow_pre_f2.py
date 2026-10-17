import copy
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_escrow_pre_f2 as runner


class PreF2OracleTest(unittest.TestCase):
    crewed = True

    def fixture(self, outcome="CANCELLED"):
        arm = dict(outcome=outcome, serial=1, src="source", d="destination", l="logical", t="transfer", epoch=1)
        root = dict(armed_flights=[arm], issued_launch_serial=1, records=[], floors=[], tombstones=[],
                    rocket_authorities=[], ledger_id="ledger")
        flight = dict(state="FUELED", fuel=dict(amount=1000, committed_debits=[]),
                      passengers=dict(assignments=[dict(passenger_id="assigned", seat_index=0)]))
        data = dict(assembly_transaction_id="logical", owner_id="owner", flight_data=flight,
                    resource_escrow=dict(ledger_id="ledger", resource_epoch=1, markers=[]))
        source = dict(UUID="source", id="advancedrocketrycommunity:rocket", RocketEntityData=data,
                      Pos=[64.5, 100., 64.5], Motion=[0., 0., 0.], Rotation=[0., 0.],
                      NoGravity=1, OnGround=0, Invulnerable=0)
        return root, [], source

    def inspect_fixture(self, root, transfers, source, cut="PF_ARM_REPLACED", restarted=True):
        with patch.object(runner, "ledger", return_value=root), \
                patch.object(runner, "journal", return_value={"transfers": transfers}), \
                patch.object(runner, "record", return_value={"Entities": [source]}), \
                patch.object(runner, "digest", return_value="file-hash"):
            return runner.inspect(Path("world"), cut, restarted, self.crewed)

    def test_no_t_cancellation_refuses_retained_arm_after_restart(self):
        for cut in ("PF_ARM_REPLACED", "PF_ARM_ACK"):
            for outcome in ("ARMED", "CANCELLED"):
                root, transfers, source = self.fixture(outcome)
                with self.subTest(cut=cut, outcome=outcome), self.assertRaises(runner.smoke.SmokeError):
                    self.inspect_fixture(root, transfers, source, cut)

    def test_no_t_requires_arm_at_cut_and_cleanup_after_each_restart(self):
        for cut in ("PF_ARM_REPLACED", "PF_ARM_ACK"):
            root, transfers, source = self.fixture("ARMED")
            before = self.inspect_fixture(root, transfers, source, cut, restarted=False)
            for restart in (1, 2):
                cleaned = copy.deepcopy(root)
                cleaned["armed_flights"] = []
                disk = self.inspect_fixture(cleaned, transfers, source, cut)
                runner.require_unchanged_source(disk, before)
                self.assertEqual([], disk["ledger"]["armed_flights"])
                with self.subTest(cut=cut, restart=restart), self.assertRaises(runner.smoke.SmokeError):
                    self.inspect_fixture(root, transfers, source, cut)
            root["armed_flights"] = []
            with self.assertRaises(runner.smoke.SmokeError):
                self.inspect_fixture(root, transfers, source, cut, restarted=False)

    def test_cleaned_state_refuses_extra_serial_leftover_t_and_other_authority(self):
        for cut in ("PF_ARM_REPLACED", "PF_ARM_ACK"):
            for kind in ("serial", "transfer", "arm", "records", "floors", "tombstones", "rocket_authorities"):
                root, transfers, source = self.fixture()
                if kind != "arm": root["armed_flights"] = []
                if kind == "serial": root["issued_launch_serial"] = 2
                elif kind == "transfer": transfers.append({"transfer_id": "transfer"})
                elif kind not in ("arm", "serial"): root[kind].append({"retained": True})
                with self.subTest(cut=cut, kind=kind), self.assertRaises(runner.smoke.SmokeError):
                    self.inspect_fixture(root, transfers, source, cut)

    def test_cleaned_state_refuses_changed_source_frontier(self):
        for kind in ("fuel", "debit", "epoch", "marker", "ledger", "manifest", "position", "state", "plan", "active"):
            root, transfers, source = self.fixture()
            root["armed_flights"] = []
            data = source["RocketEntityData"]; flight = data["flight_data"]; wrapper = data["resource_escrow"]
            if kind == "fuel": flight["fuel"]["amount"] = 999
            elif kind == "debit": flight["fuel"]["committed_debits"].append("transfer")
            elif kind == "epoch": wrapper["resource_epoch"] = 2
            elif kind == "marker": wrapper["markers"].append("marker")
            elif kind == "ledger": wrapper["ledger_id"] = "other"
            elif kind == "manifest": flight["passengers"]["assignments"] = []
            elif kind == "position": source["Pos"][1] += 1
            elif kind == "state": flight["state"] = "COUNTDOWN"
            elif kind == "plan": flight["plan"] = {}
            elif kind == "active": flight["active_transfer_id"] = "transfer"
            with self.subTest(kind=kind), self.assertRaises(runner.smoke.SmokeError):
                self.inspect_fixture(root, transfers, source)

    def test_complete_checkpoint_projection_refuses_same_count_manifest_and_other_source_changes(self):
        root, transfers, source = self.fixture("ARMED")
        before = self.inspect_fixture(root, transfers, source, restarted=False)
        root["armed_flights"] = []
        for kind in ("passenger", "seat", "owner", "motion", "rotation", "gravity", "ground", "invulnerable", "id"):
            changed = copy.deepcopy(source); data = changed["RocketEntityData"]
            if kind == "passenger": data["flight_data"]["passengers"]["assignments"][0]["passenger_id"] = "other"
            elif kind == "seat": data["flight_data"]["passengers"]["assignments"][0]["seat_index"] = 1
            elif kind == "owner": data["owner_id"] = "other"
            elif kind == "motion": changed["Motion"][0] = 1.
            elif kind == "rotation": changed["Rotation"][0] = 1.
            elif kind == "gravity": changed["NoGravity"] = 0
            elif kind == "ground": changed["OnGround"] = 1
            elif kind == "invulnerable": changed["Invulnerable"] = 1
            elif kind == "id": changed["id"] = "other"
            disk = self.inspect_fixture(root, transfers, changed)
            with self.subTest(kind=kind), self.assertRaisesRegex(runner.smoke.SmokeError, "Restart changed checkpointed E"):
                runner.require_unchanged_source(disk, before)

    def test_governed_cuts_resume_and_every_no_t_cut_cleans(self):
        expected = ("UNARMED", "CANCELLED_CLEANED", "CANCELLED_CLEANED", "ARMED", "RESUMED", "RESUMED", "RESUMED")
        for cut, outcome in zip(runner.CASES, expected):
            self.assertEqual(outcome, runner.recovery_outcome(cut, False))
            cleaned = cut in ("PF_ARM_REPLACED", "PF_ARM_ACK")
            self.assertEqual(outcome, runner.recovery_outcome(cut, True))
            self.assertEqual(cleaned, runner.cancellation_cut(cut))

    def read_journal(self, data, payload=b"bounded NBT fixture"):
        with patch.object(runner.gzip, "open") as compressed, patch.object(runner, "NbtReader") as reader:
            compressed.return_value.__enter__.return_value.read.return_value = payload
            reader.return_value.read_root.return_value = {"data": data}
            result = runner.journal(Path("world"))
            compressed.return_value.__enter__.return_value.read.assert_called_once_with(16 * 1024 * 1024 + 1)
            return result

    def test_pre_f2_journal_requires_current_root_and_empty_first_landing_outbox(self):
        data = {"schema_version": 3, "transfers": [],
                "classic_first_moon_landing": {"schema_version": 1, "state": "EMPTY"}}
        self.assertEqual(data, self.read_journal(data))
        for key, value in [("schema_version", 2), ("schema_version", 4), ("transfers", {}),
                           ("classic_first_moon_landing", None),
                           ("classic_first_moon_landing", {"schema_version": 2, "state": "EMPTY"}),
                           ("classic_first_moon_landing", {"schema_version": 1, "state": "ELECTED"}),
                           ("classic_first_moon_landing", {"schema_version": 1, "state": "EMPTY", "event": {}})]:
            changed = copy.deepcopy(data)
            changed[key] = value
            with self.subTest(key=key, value=value), self.assertRaises(runner.smoke.SmokeError):
                self.read_journal(changed)
        with self.assertRaises(runner.smoke.SmokeError):
            self.read_journal(data, b"x" * (16 * 1024 * 1024 + 1))

    def test_only_written_record_cases_expect_armed_after_restart(self):
        self.assertEqual(len(runner.CASES), 7)
        self.assertEqual([cut for cut in runner.CASES if runner.has_transfer(cut)],
                         ["PF_T_ACK", "PF_GOVERNED_T_ACK", "PF_LIVE_F2_ACK", "PF_LIVE_COUNTDOWN"])
        self.assertEqual([cut for cut in runner.CASES if runner.governed_transfer(cut)],
                         ["PF_GOVERNED_T_ACK", "PF_LIVE_F2_ACK", "PF_LIVE_COUNTDOWN"])

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


class UncrewedPreF2OracleTest(PreF2OracleTest):
    """R19A applies every accepting/refusing no-T check to the uncrewed mode too."""
    crewed = False


if __name__ == "__main__":
    unittest.main()
