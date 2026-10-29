"""I6 native cut admission and conservation, without launching a server."""
import copy
import gzip
import json
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))


def fixture():
    snapshot = dict(source_origin=[8, 100, 8], source_dimension="advancedrocketrycommunity:moon",
                    bounding_box=[-1, 0, 0, 1, 2, 0], relative_blocks=[dict(position=[0, 0, 0], items=[7])])
    fuel = dict(amount=1000, debit_history=[dict(amount=500)], propulsion=dict(revision=3))
    old = dict(transfer_id=[0, 0, 0, 1], logical_rocket_id=[0, 0, 0, 2], owner_id=[0, 0, 0, 3],
               source_entity_id=[0, 0, 0, 4], destination_entity_id=[0, 0, 0, 5], phase="COMMITTED",
               destination_snapshot=snapshot, destination_flight=dict(passengers=dict(seat_capacity=1)))
    entity = dict(UUID=old["destination_entity_id"], Pos=[8.5, 100, 8.5], RocketEntityData=dict(
        assembly_transaction_id=old["logical_rocket_id"], owner_id=old["owner_id"], snapshot=snapshot,
        flight_data=dict(logical_rocket_id=old["logical_rocket_id"], current_origin=[8, 100, 8],
                         current_dimension=snapshot["source_dimension"], fuel=fuel, state="FUELED",
                         passengers=dict(seat_capacity=1, assignments=[]))))
    disk = dict(entities=[dict(entity=entity, depth=0)], journal=dict(schema_version=3,
        format_epoch="v1.8.0-first-moon-landing", transfers=[],
        classic_first_moon_landing=dict(schema_version=1, state="EMPTY")),
        world_authority=dict(transactions=dict(transactions=[]), payload_blocks=[dict(
            end="destination_snapshot", position=[8, 100, 8], state="minecraft:air")]))
    return copy.deepcopy(disk), copy.deepcopy(old), copy.deepcopy(fuel)


class W164I6OracleTests(unittest.TestCase):
    def test_four_plain_and_ten_m_rows_are_separate(self):
        import w164_i6_oracle as oracle
        self.assertEqual(14, len(oracle.CASES))
        self.assertEqual(4, sum(name.startswith("PLAIN_") for name in oracle.CASES))
        self.assertEqual(42, len(oracle.CASES) * 3)

    def test_missing_d_is_rejected_even_at_k2(self):
        from w164_i6_oracle import check_disk
        disk, old, fuel = fixture()
        check_disk("K2", disk, old, fuel, 0)
        disk["entities"] = []
        with self.assertRaisesRegex(Exception, "D1"): check_disk("K2", disk, old, fuel, 0)

    def test_current_complete_snapshot_fuel_debits_and_identity_are_required(self):
        from w164_i6_oracle import check_disk
        for field in ("snapshot", "fuel", "debit", "owner", "logical", "physical", "location", "crew", "active"):
            with self.subTest(field=field):
                disk, old, fuel = fixture()
                entity = disk["entities"][0]["entity"]; data = entity["RocketEntityData"]; flight = data["flight_data"]
                if field == "snapshot": data["snapshot"]["relative_blocks"][0]["items"] = [8]
                elif field == "fuel": flight["fuel"]["amount"] -= 1
                elif field == "debit": flight["fuel"]["debit_history"] = []
                elif field == "owner": data["owner_id"] = [1, 2, 3, 4]
                elif field == "logical": data["assembly_transaction_id"] = [1, 2, 3, 4]
                elif field == "physical": entity["UUID"] = old["source_entity_id"]
                elif field == "location": entity["Pos"][0] += 16
                elif field == "crew": flight["passengers"]["assignments"] = [1]
                else: flight["active_transfer_id"] = old["transfer_id"]
                with self.assertRaises(Exception): check_disk("K2", disk, old, fuel, 0)

    def test_old_source_duplicate_and_nested_authorities_are_rejected(self):
        from w164_i6_oracle import check_disk
        for nested in (False, True):
            disk, old, fuel = fixture()
            if nested: disk["entities"][0]["depth"] = 1
            else: disk["entities"].append(copy.deepcopy(disk["entities"][0]))
            with self.assertRaises(Exception): check_disk("K2", disk, old, fuel, 0)

    def test_t_matches_the_actual_cut_not_the_callback(self):
        from w164_i6_oracle import check_disk
        for case, present in (("K1", True), ("K2", False), ("K3_MOVE", False), ("K3_UNKNOWN", True),
                              ("K4", True), ("K9_REFUSED", True), ("K9_LATE", True),
                              ("K10_ON", True), ("K10_OFF", True), ("K10_FAIL", False)):
            disk, old, fuel = fixture()
            disk["journal"]["transfers"] = [copy.deepcopy(old)] if present else []
            if case == "K1": disk["pending"] = dict(transfers=[])
            if case == "K3_MOVE": disk["pending"] = dict(transfers=[copy.deepcopy(old)])
            for restart in range(3): check_disk(case, disk, old, fuel, restart)
            disk["journal"]["transfers"] = [] if present else [copy.deepcopy(old)]
            with self.assertRaises(Exception): check_disk(case, disk, old, fuel, 0)

    def test_staged_writeback_must_be_identical(self):
        from w164_i6_oracle import check_disk
        disk, old, fuel = fixture()
        disk["pending"] = dict(transfers=[dict(old, phase="PREPARED")])
        with self.assertRaises(Exception): check_disk("K3_MOVE", disk, old, fuel, 0)

    def test_u_and_payload_blocks_cannot_coexist_with_d(self):
        from w164_i6_oracle import check_disk
        for kind in ("U", "block"):
            disk, old, fuel = fixture()
            if kind == "U": disk["world_authority"]["transactions"]["transactions"] = [dict(type="DISASSEMBLY")]
            else: disk["world_authority"]["payload_blocks"][0]["state"] = "minecraft:chest"
            with self.assertRaises(Exception): check_disk("K2", disk, old, fuel, 0)

    def test_plain_outbox_and_receipt_keep_the_exact_event(self):
        from w164_i6_oracle import check_disk
        disk, old, fuel = fixture()
        event = dict(event_id=[0, 0, 0, 9], kind="first_moon_landing", participants=[], fingerprint="a" * 64)
        disk["journal"]["classic_first_moon_landing"] = dict(schema_version=1, state="ELECTED", event=event)
        disk["receipt"] = dict(occurred=0)
        check_disk("PLAIN_ACK", disk, old, fuel, 0, event)
        disk["journal"]["classic_first_moon_landing"]["state"] = "DELIVERED"
        disk["receipt"] = dict(occurred=1, source="rocket_transfers", event=copy.deepcopy(event))
        check_disk("PLAIN_ACK", disk, old, fuel, 1, event)
        disk["receipt"]["event"]["fingerprint"] = "b" * 64
        with self.assertRaises(Exception): check_disk("PLAIN_ACK", disk, old, fuel, 2, event)

    def test_delivered_receipt_requires_exact_source_event_and_fields(self):
        from w164_i6_oracle import check_disk
        disk, old, fuel = fixture()
        event = dict(event_id=[0, 0, 0, 9], fingerprint="a" * 64)
        disk["journal"]["classic_first_moon_landing"] = dict(schema_version=1, state="DELIVERED", event=event)
        receipt = dict(occurred=1, source="rocket_transfers", event=event)
        for changed in (dict(occurred=1, event=event), dict(receipt, source="station_warp"),
                        dict(receipt, extra=1), dict(receipt, occurred=0),
                        dict(receipt, event=dict(event, fingerprint="b" * 64))):
            with self.subTest(receipt=changed):
                disk["receipt"] = changed
                with self.assertRaisesRegex(Exception, "exact durable R"):
                    check_disk("PLAIN_ACK", disk, old, fuel, 1, event)


class W164I6HarnessTests(unittest.TestCase):
    def test_elected_native_root_is_not_a_pre_f2_root(self):
        import loader_cut_nbt as nbt
        import run_w164_i6_native as native
        def compound(**fields): return nbt.Tag(nbt.COMPOUND, fields)
        def string(value): return nbt.Tag(nbt.STRING, value.encode("utf-8"))
        outbox = compound(schema_version=nbt.Tag(nbt.INT, 1), state=string("ELECTED"),
                          event=compound(event_id=nbt.Tag(nbt.INTS, [0, 0, 0, 9])))
        root = compound(data=compound(schema_version=nbt.Tag(nbt.INT, 3),
            format_epoch=string("v1.8.0-first-moon-landing"), transfers=nbt.Tag(nbt.LIST, (nbt.COMPOUND, [])),
            classic_first_moon_landing=outbox))
        with tempfile.TemporaryDirectory() as directory:
            world = Path(directory); (world / "data").mkdir()
            path = world / "data/advancedrocketrycommunity_rocket_transfers.dat"
            path.write_bytes(gzip.compress(nbt.write(b"", root)))
            with self.assertRaisesRegex(Exception, "nonempty landing outbox"):
                native.predecessor.journal(world)
            self.assertEqual(dict(schema_version=1, state="ELECTED", event=dict(event_id=[0, 0, 0, 9])),
                             native.read_elected_outbox(world))

    def test_landing_outbox_reader_accepts_elected_not_prelanding_empty(self):
        import run_w164_i6_native as native
        outbox = dict(schema_version=1, state="ELECTED", event=dict(event_id=[0, 0, 0, 9]))
        root = dict(schema_version=3, format_epoch="v1.8.0-first-moon-landing",
                    classic_first_moon_landing=outbox)
        with patch.object(native.predecessor, "read_journal", return_value=root) as reader:
            self.assertEqual(outbox, native.read_elected_outbox(Path("world")))
            reader.assert_called_once_with(Path("world/data/advancedrocketrycommunity_rocket_transfers.dat"))
        for changed in (dict(root, schema_version=2), dict(root, format_epoch="other"),
                        dict(root, classic_first_moon_landing=dict(schema_version=1, state="EMPTY")),
                        dict(root, classic_first_moon_landing=dict(outbox, schema_version=2))):
            with patch.object(native.predecessor, "read_journal", return_value=changed):
                with self.assertRaises(Exception): native.read_elected_outbox(Path("world"))

    def test_cut_and_fm12_properties_are_initial_start_only(self):
        import run_w164_i6_native as native
        for case in native.CASES:
            first = native.command_for(SimpleNamespace(java="java"), case, 0)
            self.assertIn("-Dadvancedrocketrycommunity.i6NativeCut=" + case, first)
            self.assertEqual(case.startswith("PLAIN_"), "-Darce.releaseTest.firstEventCut=FM12a" in first)
            for restart in (1, 2):
                command = native.command_for(SimpleNamespace(java="java"), case, restart)
                self.assertFalse(any("i6NativeCut=" in arg or "firstEventCut=" in arg for arg in command))

    def test_attempts_count_failed_launches_and_never_reset_the_66_limit(self):
        import run_w164_i6_native as native
        native.check_attempt([], "K2", 1)
        rows = [dict(case="K2", attempt=1, restart=0)]
        native.check_attempt(rows, "K2", 2)
        for history, case, attempt in ((rows, "K2", 1), ([], "K2", 2), (rows, "K2", 3), (rows * 64, "K1", 1)):
            with self.assertRaises(Exception): native.check_attempt(history, case, attempt)

    def test_r164_third_attempt_preserves_three_counted_starts(self):
        import run_w164_i6_native as native
        rows = [dict(case="PLAIN_MOVE", attempt=attempt, restart=restart)
                for attempt, restart in ((1, 0), (2, 0), (2, 1))]
        before = copy.deepcopy(rows)
        native.check_attempt(rows, "PLAIN_MOVE", 3)
        self.assertEqual(before, rows)
        other = [dict(case="K1", attempt=1, restart=0)]
        native.check_attempt(rows + other * 60, "PLAIN_MOVE", 3)
        with self.assertRaises(Exception): native.check_attempt(rows + other * 61, "PLAIN_MOVE", 3)
        for attempt in (0, 2, 4, 67):
            with self.assertRaises(Exception): native.check_attempt(rows, "PLAIN_MOVE", attempt)

    def test_retry_checks_latest_completed_failure_and_distinct_directory(self):
        import run_w164_i6_native as native
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            first = native.case_evidence(root, "PLAIN_MOVE", 1)
            second = native.case_evidence(root, "PLAIN_MOVE", 2)
            third = native.case_evidence(root, "PLAIN_MOVE", 3)
            self.assertEqual(root / "PLAIN_MOVE", first)
            self.assertEqual(root / "PLAIN_MOVE-attempt-2", second)
            self.assertEqual(root / "PLAIN_MOVE-attempt-3", third)
            first.mkdir(); second.mkdir()
            (first / "result.json").write_text(json.dumps(dict(status="FAIL")))
            for status in ("PASS", "RUNNING"):
                (second / "result.json").write_text(json.dumps(dict(status=status)))
                with self.assertRaisesRegex(Exception, "completed failed case"):
                    native.check_retry(root, "PLAIN_MOVE", 3)
            (second / "result.json").write_text(json.dumps(dict(status="FAIL")))
            native.check_retry(root, "PLAIN_MOVE", 3)
            self.assertFalse(third.exists())

    def test_actual_cut_log_is_required_without_expiry_or_success(self):
        import run_w164_i6_native as native
        native.check_cut_lines("PLAIN_ACK", ["ARCE_LANDED_DISASSEMBLY_PAUSED boundary=PLAIN_ACK"], "id")
        for lines in ([], ["ARCE_LANDED_DISASSEMBLY_PAUSED boundary=PLAIN_ACK",
                           "event=landed_reservation_released entity=id"],
                      ["ARCE_LANDED_DISASSEMBLY_PAUSED boundary=PLAIN_ACK", "ARCE_W164_CUT_EXPIRED"]):
            with self.assertRaises(Exception): native.check_cut_lines("PLAIN_ACK", lines, "id")


if __name__ == "__main__": unittest.main()
