"""W360's added cut inventory, held mixed stores and R-031 seed/audit boundaries."""
import copy
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import item_cuts_oracle as item
import loader_cut_nbt as nbt
import run_v180_item_cuts as runner
import run_v180_r042_native as ledger
from test_cargo_s4_s5_cuts_runner import released_disk
from test_loader_cut_nbt import compound


SUFFIXES = ("O1_BEFORE", "BIND_BEFORE", "O3_BEFORE", "O3J_BEFORE", "O4_BEFORE", "PRUNE_BEFORE", "VETO_DESTINATION_SAVED")


class UncoveredRowsTest(unittest.TestCase):
    def test_held_rows_require_identical_ledger_instead_of_a_release(self):
        for case in ("IS_O1_BEFORE", "IS_LOAD_O1_BEFORE", "IS_VETO_DESTINATION_SAVED", "IS_LOAD_VETO_DESTINATION_SAVED"):
            before = dict(records=[dict(phase="REPAIR" if case.endswith("O1_BEFORE") else "SETTLING_ACCEPT")], tombstones=[])
            runner.check_frozen_progress(case, before, copy.deepcopy(before))
            for after in (dict(records=[], tombstones=[]), dict(records=[], tombstones=[dict(released=True)])):
                with self.assertRaises(item.native.smoke.SmokeError):
                    runner.check_frozen_progress(case, before, after)

    def test_each_new_item_cut_has_a_probe_boundary_and_direction_specific_oracle(self):
        for prefix in ("IS_", "IS_LOAD_"):
            for suffix in SUFFIXES:
                case = prefix + suffix
                self.assertIn(case, runner.CASES)
                self.assertIn(case, item.CASES)
                self.assertEqual(item.CASES[case].get("load", False), prefix == "IS_LOAD_")
                self.assertEqual(runner.CASES[case]["probe"], case)
        for load in (False, True):
            case = "IS_" + ("LOAD_" if load else "") + "VETO_DESTINATION_SAVED"
            for point in ("cut", "final"):
                state, ends = item.CASES[case][point]
                self.assertEqual(state, item.ACCEPT)
                self.assertEqual(74, ends["rocket"][0] + ends["device"][0])
                self.assertEqual(1, ends["rocket"][2] + ends["device"][2])

    def test_new_releasing_cuts_reject_quantity_role_receipt_and_authority_mutations(self):
        for prefix in ("IS_", "IS_LOAD_"):
            for suffix in ("BIND_BEFORE", "O3_BEFORE", "O3J_BEFORE", "O4_BEFORE", "PRUNE_BEFORE"):
                case = prefix + suffix
                disk = released_disk()
                ends = item.CASES[case]["final"][1]
                disk["rocket"].update(cargo=ends["rocket"][0], epoch=ends["rocket"][1])
                disk["device"].update(amount=ends["device"][0], epoch=ends["device"][1],
                                      role="LOAD" if prefix == "IS_LOAD_" else "UNLOAD")
                disk["ledger"]["rocket_authorities"][0]["e"] = ends["rocket"][1]
                disk["ledger"]["tombstones"][0]["survivors"][1].update(signature_kind="ITEM_BUFFER_V1", quantity=0)
                item.check_settlement(case, disk, True)
                for mutate in (lambda d: d["rocket"].update(cargo=d["rocket"]["cargo"] + 1),
                               lambda d: d["device"].update(amount=d["device"]["amount"] + 1),
                               lambda d: d["device"].update(role="INVALID"),
                               lambda d: d["ledger"]["tombstones"][0].update(released=False),
                               lambda d: d["ledger"]["rocket_authorities"][0].update(p="foreign")):
                    bad = copy.deepcopy(disk); mutate(bad)
                    with self.subTest(case=case), self.assertRaises(item.native.smoke.SmokeError):
                        item.check_settlement(case, bad, True)

    def test_future_seed_uses_native_supported_version_plus_one_and_invalid_seed_retains_version(self):
        for supported in (2, 3, 9):
            for future in (False, True):
                with tempfile.TemporaryDirectory() as tmp:
                    path = Path(tmp) / "ledger.dat"
                    original = compound(data=compound(schema_version=nbt.Tag(nbt.INT, supported),
                                                      issued_through=nbt.Tag(nbt.LONG, 1)))
                    nbt.write_gzip(path, b"", original)
                    result = ledger.seed_bad_ledger(path, future)
                    payload = nbt.compound(nbt.read_gzip(path)[1], "data")
                    self.assertEqual(result["supported_version"], supported)
                    self.assertEqual(nbt.compound(payload, "schema_version").value, supported + int(future))
                    self.assertEqual(nbt.compound(payload, "issued_through").value, 1 if future else -1)
                    self.assertEqual(result["diagnostic"], "ARCE-BETA-2001" if future else "ARCE-BETA-2000")

    def test_presence_unknown_requires_its_ordered_postcheck_rendezvous(self):
        base = ["ARCE_RELEASE_PRESENCE_WAIT type=BAY_RECEIPTS", "ARCE_RELEASE_PRESENCE_UNKNOWN type=BAY_RECEIPTS",
                "ARCE_RELEASE_LOAD_FAILURE type=BAY_RECEIPTS injected=true",
                "[Server thread/ERROR] Error loading saved data: advancedrocketrycommunity_bay_receipts"]
        ledger.check_injected("X_BAY", base, "BAY_RECEIPTS", "advancedrocketrycommunity_bay_receipts")
        for bad in (base[1:], list(reversed(base)), base + [base[0]],
                    base + ["[ARCE-BETA-2000] Beta world data check blocked startup;"]):
            with self.assertRaises(ledger.smoke.SmokeError):
                ledger.check_injected("X_BAY", bad, "BAY_RECEIPTS", "advancedrocketrycommunity_bay_receipts")


if __name__ == "__main__":
    unittest.main()
