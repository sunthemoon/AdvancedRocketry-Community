import copy
import io
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_rocket_settlement_completion as native
from inspect_escrow_receipt import FIELDS, decode_receipt


def wire(**fields):
    digits = "0123456789abcdefghijklmnopqrstuvwxyz"
    def key(name):
        index = FIELDS.index(name)
        return "f" + (digits[index] if index < 36 else digits[index // 36] + digits[index % 36])
    return {key(name): value for name, value in fields.items()}


class SettlementCompletionOracleTest(unittest.TestCase):
    def test_journal_pins_integrated_w44_root_and_empty_outbox(self):
        good = dict(schema_version=3, transfers=[], classic_first_moon_landing=dict(schema_version=1, state="EMPTY"))
        for field, value in ((None, None), ("schema_version", 2), ("transfers", {}),
                             ("classic_first_moon_landing", None),
                             ("classic_first_moon_landing", dict(schema_version=1, state="PENDING"))):
            data = copy.deepcopy(good)
            if field: data[field] = value
            with patch.object(native.gzip, "open", return_value=io.BytesIO(b"fixture")), \
                    patch.object(native, "NbtReader") as reader:
                reader.return_value.read_root.return_value = {"data": data}
                if field:
                    with self.assertRaises(native.smoke.SmokeError): native.journal(Path("world"))
                else: self.assertEqual(good, native.journal(Path("world")))

    def test_each_veto_start_requires_its_actual_published_hold_reason(self):
        for choice in native.CHOICES:
            for boundary in ("VETO", "VETO_CHECKPOINTED"):
                if choice == "FLOOR" and boundary == "VETO_CHECKPOINTED": continue
                cut = f"SR_{choice}__{boundary}"
                self.assertIn(cut, native.CASES)
                for restart in range(3):
                    expected = "Frozen settlement endpoint changed" if restart and choice != "FLOOR" and boundary == "VETO" else "BARRIER_VETO"
                    lines = [f"ARCE_ESCROW_SETTLEMENT_HELD digest=abc reason={expected} records=retained",
                             f"ARCE_RELEASE_SETTLEMENT_VETO cut={cut} reason={expected} records={2 if choice.startswith('CHAIN') else 1} "
                             f"checkpointed={str(boundary == 'VETO_CHECKPOINTED').lower()} recovery={str(bool(restart)).lower()}"]
                    self.assertEqual(expected, native.inspect_hold(lines, cut, restart))
                    for invalid in ([], lines[:1], lines[1:], lines + lines[1:],
                                    [line.replace(expected, "WRONG_REASON") for line in lines]):
                        with self.assertRaises(native.smoke.SmokeError): native.inspect_hold(invalid, cut, restart)

    def test_native_start_and_acceptance_require_the_same_machine_lock_owner(self):
        self.assertIsNone(native.require_lock(None))
        with patch.object(Path, "read_text", return_value="owner\n"):
            self.assertEqual("owner", native.require_lock("owner"))
            with self.assertRaises(native.smoke.SmokeError):
                native.require_lock("different owner")
        with patch.object(Path, "read_text", side_effect=FileNotFoundError):
            with self.assertRaises(FileNotFoundError):
                native.require_lock("owner")

    def setUp(self):
        endpoint = dict(kind="ENTITY", identity=[1, 2, 3, 4])
        receipt_wire = wire(schema_version=3, released=1, actor=native.ACTOR, actor_name="Server",
                            decision_digest=[0xaa] * 32, accounting_status="UNKNOWN", release_generation=1,
                            before_choice=[wire(endpoint=wire(**endpoint), missing=0, bound=1, quantity=1000,
                                                signature_kind="FUEL_VECTOR_V1", invariant_digest=[0xbb] * 32,
                                                state=wire(epoch=1, signature=[0xcc] * 32))])
        self.root = dict(ledger_id=[10, 11, 12, 13], records=[], floors=[], tombstones=[decode_receipt(receipt_wire)])
        self.wrapper = dict(schema_version=3, flight_data=dict(fuel=dict(amount=1000)),
                            resource_escrow=dict(resource_epoch=1, ledger_id=self.root["ledger_id"], markers=[]))
        self.rocket = dict(UUID=endpoint["identity"], Pos=[64.5, 100., 64.5], RocketEntityData=self.wrapper)
        self.sources = [dict(ForgeData=dict(arce_escrow_fixture=dict(amount=4000)))]
        self.transfers = []

    def inspect(self, cut="SR_ACCEPT__O4_AFTER", restarted=True):
        with patch.object(native, "ledger", return_value=self.root), patch.object(native, "digest", return_value="a" * 64), \
                patch.object(native, "journal", return_value=dict(transfers=self.transfers)), \
                patch.object(native, "slot_present", return_value=True), \
                patch.object(native, "record", side_effect=[dict(Entities=[self.rocket]), dict(block_entities=self.sources)]):
            return native.inspect(Path("world"), cut, restarted=restarted)

    def test_released_resources_and_epoch_are_intact(self):
        self.assertEqual(5000, self.inspect()["raw_total"])

    def test_checkpointed_veto_and_historical_mismatch_have_distinct_source_states(self):
        endpoint = dict(kind="ENTITY", identity=self.rocket["UUID"])
        self.root["records"] = [dict(settlement=dict(actor=native.ACTOR, actor_name="Server", digest="a" * 64,
                                                     observations=[dict(endpoint=endpoint)]))]
        self.root["tombstones"] = []
        self.wrapper["resource_escrow"]["markers"] = [dict(op_id=[1, 2, 3, 4])]
        for restarted in (False, True):
            self.sources[0]["ForgeData"]["arce_escrow_fixture"]["amount"] = 4000
            self.inspect("SR_ACCEPT__VETO_CHECKPOINTED", restarted)
            with self.assertRaises(native.smoke.SmokeError): self.inspect("SR_ACCEPT__VETO", restarted)
            self.sources[0]["ForgeData"]["arce_escrow_fixture"]["amount"] = 5000
            self.inspect("SR_ACCEPT__VETO", restarted)
            with self.assertRaises(native.smoke.SmokeError): self.inspect("SR_ACCEPT__VETO_CHECKPOINTED", restarted)
        self.sources = []
        self.inspect("SR_ABANDON__VETO_CHECKPOINTED")
        with self.assertRaises(native.smoke.SmokeError): self.inspect("SR_ABANDON__VETO")

    def test_held_normalization_does_not_claim_released_resource_mutations(self):
        self.root["records"] = [dict(settlement=dict(actor=native.ACTOR, actor_name="Server", digest="a" * 64,
                                                     observations=[dict(endpoint=dict(kind="ENTITY", identity=self.rocket["UUID"]))]))]
        self.root["tombstones"] = []
        self.wrapper["flight_data"]["fuel"]["amount"] = 0
        self.wrapper["resource_escrow"] = dict(resource_epoch=0, markers=[])
        self.inspect("SR_NORMALIZE__VETO_CHECKPOINTED")
        self.wrapper["flight_data"]["fuel"]["amount"] = 2000
        with self.assertRaises(native.smoke.SmokeError): self.inspect("SR_NORMALIZE__VETO_CHECKPOINTED")

    def test_unacknowledged_or_lost_receipt_is_rejected(self):
        self.root["tombstones"][0]["released"] = 0
        with self.assertRaises(native.smoke.SmokeError): self.inspect()
        self.root["tombstones"] = []
        with self.assertRaises(native.smoke.SmokeError): self.inspect()

    def test_actor_survivor_identity_and_epoch_are_checked(self):
        original = copy.deepcopy(self.root)
        for field, value in (("actor", [0, 0, 0, 0]), ("actor_name", "someone"), ("decision_digest", "a" * 65)):
            self.root = copy.deepcopy(original); self.root["tombstones"][0][field] = value
            with self.assertRaises(native.smoke.SmokeError): self.inspect()
        self.root = original; self.rocket["UUID"] = [0, 0, 0, 0]
        with self.assertRaises(native.smoke.SmokeError): self.inspect()

    def test_wrong_resource_amount_retained_markers_or_removed_epoch_are_rejected(self):
        original = copy.deepcopy(self.wrapper)
        for field, value in (("resource_epoch", 0), ("markers", [dict(op_id=[1, 1, 1, 1])])):
            self.wrapper = copy.deepcopy(original); self.rocket["RocketEntityData"] = self.wrapper
            self.wrapper["resource_escrow"][field] = value
            with self.assertRaises(native.smoke.SmokeError): self.inspect()
        self.wrapper = original; self.rocket["RocketEntityData"] = self.wrapper; self.wrapper["flight_data"]["fuel"]["amount"] = 2000
        with self.assertRaises(native.smoke.SmokeError): self.inspect()

    def test_matching_journal_cannot_remain_after_release(self):
        self.transfers = [dict(transfer_id=[9, 9, 9, 9])]
        with self.assertRaises(native.smoke.SmokeError): self.inspect()

    def test_pre_o1_neither_invents_intent_nor_releases_records(self):
        self.root["tombstones"] = []
        self.root["records"] = [dict(destination=dict(kind="ENTITY", identity=self.rocket["UUID"]),
                                     phase="REPAIR", reason="OTHER_STATE")]
        self.wrapper["resource_escrow"]["markers"] = [dict(op_id=[1, 2, 3, 4])]
        self.inspect("SR_ACCEPT__O1_BEFORE")
        self.root["records"][0]["settlement"] = dict(digest="a" * 64)
        with self.assertRaises(native.smoke.SmokeError): self.inspect("SR_ACCEPT__O1_BEFORE")

    def test_floor_requires_real_empty_payload_at_ack_but_allows_later_vanilla_empty_deletion(self):
        def check(restarted, present, entities):
            def slot(path, index=132):
                return dict(Position=[index & 31, index >> 5], Entities=entities, block_entities=[])
            with patch.object(native, "ledger", return_value=self.root), patch.object(native, "digest", return_value="a" * 64), \
                    patch.object(native, "journal", return_value=dict(transfers=[])), \
                    patch.object(native, "slot_present", return_value=present), patch.object(native, "record", side_effect=slot):
                return native.inspect(Path("world"), "SR_FLOOR__O4_AFTER", restarted=restarted)
        self.assertEqual(0, check(False, True, [])["raw_total"])
        self.assertEqual({132: False, 133: False}, check(True, False, [])["empty_slots"])
        with self.assertRaises(native.smoke.SmokeError): check(False, False, [])
        with self.assertRaises(native.smoke.SmokeError): check(False, True, [dict(id="minecraft:armor_stand")])

    def test_named_fill_must_retain_the_exact_guard_until_checked_removal(self):
        guard = dict(transfer_id=[9, 8, 7, 6])
        self.root["records"] = [dict(transfer_guard=guard, settlement=dict(actor=native.ACTOR, actor_name="Server", digest="a" * 64,
                                      observations=[dict(endpoint=dict(kind="ENTITY", identity=self.rocket["UUID"]))]))]
        self.root["floors"] = [dict(transfer_id=guard["transfer_id"])]
        self.root["tombstones"] = []
        self.transfers = [dict(transfer_id=guard["transfer_id"])]
        self.wrapper["resource_escrow"]["markers"] = [dict(op_id=[1, 2, 3, 4])]
        self.inspect("SR_NAMED_ACCEPT__O1_AFTER", restarted=False)
        self.transfers = [dict(transfer_id=[0, 0, 0, 0])]
        with self.assertRaises(native.smoke.SmokeError): self.inspect("SR_NAMED_ACCEPT__O1_AFTER", restarted=False)
        self.transfers = []
        with self.assertRaises(native.smoke.SmokeError): self.inspect("SR_NAMED_ACCEPT__O1_AFTER", restarted=False)

    def test_release_preserves_exact_frozen_decision_and_named_guard(self):
        endpoint = dict(kind="ENTITY", identity=[5, 6, 7, 8])
        observation = dict(endpoint=endpoint, missing=0, quantity=1000, normalized_wrapper={})
        intent = dict(actor=native.ACTOR, actor_name="Server", digest="a" * 64, choice="ACCEPT_CURRENT",
                      confirmed_tick=10, observations=[observation])
        op = dict(op_id=[1, 2, 3, 4], kind="FLUID_PROPULSION_FILL", source=dict(kind="BLOCK_ENTITY"),
                  destination=endpoint, transfer_guard=dict(transfer_id=[9, 9, 9, 9]), settlement=intent)
        receipt = dict(op_id=op["op_id"], actor=native.ACTOR, actor_name="Server", decision_digest="a" * 64,
                       choice="ACCEPT_CURRENT", confirmed_tick=10, guards=[op["transfer_guard"]],
                       before_choice=[{key: value for key, value in observation.items() if key != "normalized_wrapper"}])
        before = dict(records=[op], tombstones=[])
        after = dict(records=[], tombstones=[receipt])
        native.require_progress(before, after)
        for key, value in (("actor_name", "different"), ("choice", "ABANDON"), ("decision_digest", "b" * 64),
                           ("confirmed_tick", 11), ("guards", []), ("before_choice", []), ("op_id", [8, 8, 8, 8])):
            changed = copy.deepcopy(after); changed["tombstones"][0][key] = value
            with self.assertRaises(native.smoke.SmokeError): native.require_progress(before, changed)

    def test_matrix_covers_every_acknowledgement_and_every_choice_veto(self):
        for choice in native.CHOICES:
            for phase in ("O1", "O3", "O4"):
                for side in ("BEFORE", "AFTER"):
                    self.assertIn(f"SR_{choice}__{phase}_{side}", native.CASES)
            for index in range(3 if choice.startswith("CHAIN") else 2):
                for side in ("BEFORE", "AFTER"):
                    self.assertIn(f"SR_{choice}__O2_{side}_{index}", native.CASES)
            self.assertIn(f"SR_{choice}__VETO", native.CASES)
        self.assertIn("SR_FLOOR__O3J_BEFORE", native.CASES)
        self.assertIn("SR_FLOOR__O3J_AFTER", native.CASES)
        for choice in ("NAMED_ACCEPT", "NAMED_ABANDON"):
            for side in ("BEFORE", "AFTER"):
                self.assertIn(f"SR_{choice}__O3J_{side}", native.CASES)
        self.assertIn("SR_NORMALIZE__O_BIND_BEFORE", native.CASES)
        self.assertIn("SR_NORMALIZE__O_BIND_AFTER", native.CASES)
        self.assertEqual(len(native.CASES), len(set(native.CASES)))


if __name__ == "__main__":
    unittest.main()
