"""New-format positive cases and one independent refusal per changed oracle boundary."""
import copy
import hashlib
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import patch
from uuid import UUID

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import escrow_accounting as a
import loader_cut_nbt as nbt
from run_dedicated_server_smoke import SmokeError
from test_escrow_accounting_readers import fixture
import test_loader_s6_chain as chain_test
import test_loader_s6_abandon as abandon_test
import test_loader_s6_normalize as normalize_test
import test_cargo_reconciliation_runner as cargo_test
import test_cargo_storage_runner as storage_test
import test_cargo_recovery_runner as recovery_test
from test_cargo_s4_s5_cuts_runner import released_disk
import run_v180_cargo_s4_s5_cuts as settlement
import cargo_storage_oracle as storage
import cargo_recovery_oracle as recovery
import cargo_contention_oracle as contention
import loader_s6_integrity as integrity
import loader_cut_oracle as loader
import loader_named_cut_oracle as named
import item_cuts_oracle as items
from inspect_escrow_receipt import decode_receipt


def words(uid):
    return list(nbt.uuid_tag(UUID(a.identity(uid))).value)


def account_root(root, frozen=False, missing=False, neutral=False):
    """Use the real codec's closed row layout; adapt its values to the existing native-form test fixture."""
    root.update(schema_version=3, format_epoch=a.EPOCHS[3], fluid_accounts=[])
    records = root["records"]
    ids = [a.identity(op["op_id"]) for op in records]
    for index, op in enumerate(records):
        fluid = op["payload"] if op["kind"] == "FLUID_PROPULSION_FILL" else op["payload"]["fluid"]
        key = dict(i=fluid["FluidName"], d=list(hashlib.sha256(
            ("arce-escrow-fluid-key-1:" + fluid["FluidName"] + "\nabsent").encode()).digest()))
        row = nbt.plain(fixture("fuel-planned"))["fluid_accounts"][0]
        row.update(op=words(op["op_id"]))
        row["b"] = dict(s=int(op["kind"] != "FLUID_PROPULSION_FILL"),
                        e=[dict(key, s=5000, t=index * 1000, x=-1000, y=1000)])
        op.update(schema_version=3, accounting_baseline=list(bytes.fromhex(a.digest(a.row_tag(row).value["b"]))))
        if frozen:
            entry = dict(key, m=2 if missing and index == 1 else 3, t=0 if neutral else 1000*len(records), o=words(ids[0]))
            if not (missing and index == 1): entry["s"] = 4000
            if not missing and index == 0: entry.update(m=7, a=1000*len(records) if neutral else 0)
            row["f"] = dict(k=3 if missing else 1, g=words(ids[0]), e=[entry])
            if index == 0: row["f"]["m"] = sum((words(uid) for uid in ids), [])
        root["fluid_accounts"].append(row)
    if frozen:
        pairs = [nbt.Tag(nbt.COMPOUND, {"op": nbt.uuid_tag(UUID(uid)), "digest": nbt.Tag(nbt.BYTES,
            bytes.fromhex(a.digest(a.row_tag(row))))}) for uid, row in zip(ids, root["fluid_accounts"])]
        commitment = a.digest(nbt.Tag(nbt.COMPOUND, {"rows": nbt.Tag(nbt.LIST, (nbt.COMPOUND, pairs))}))
        for op in records: op["settlement"].update(schema_version=3, account_commitment=list(bytes.fromhex(commitment)))
    a.check(root)
    return root


class AccountingOracleTest(unittest.TestCase):
    def test_loader_chain_normalize_and_abandon_preserve_account_and_receipt_on_two_restarts(self):
        cases = ((chain_test.decision, chain_test.completed, chain_test.chain, "LF_S6_CHAIN_ACK", {}),
                 (normalize_test.neutral, normalize_test.completed, normalize_test.normalize, "LF_S6_CHAIN_NORMALIZE_AFTER", dict(neutral=True)),
                 (abandon_test.abandoned, abandon_test.completed, abandon_test.abandon, "LF_S6_CHAIN_ABANDON_ACK", dict(missing=True)))
        for initial, complete, oracle, cut, options in cases:
            with self.subTest(cut=cut):
                image = initial(); account_root(image["native"]["ledger"], frozen=True, **options)
                first = oracle.check(image, 0, cut=cut)
                final = complete(image)
                second = oracle.check(final, 1, first["identity"], cut)
                oracle.check(final, 2, second["identity"], cut)
                self.assertEqual("UNKNOWN" if options.get("missing") else "KNOWN",
                                 final["native"]["ledger"]["tombstones"][0]["accounting_status"])
                self.assertEqual(0 if options.get("missing") else 1,
                                 sum("a" in e for r in final["native"]["ledger"]["fluid_accounts"] for e in r["f"]["e"]))
                bad = copy.deepcopy(final); bad["native"]["ledger"]["fluid_accounts"][0]["b"]["e"][0]["s"] += 1
                with self.assertRaises(SmokeError): oracle.check(bad, 1, first["identity"], cut)
                bad = copy.deepcopy(image); bad["native"]["ledger"]["records"][0]["settlement"]["account_commitment"] = [0]*32
                with self.assertRaises(SmokeError): oracle.check(bad, 0, cut=cut)
                if not options.get("missing"):
                    bad = copy.deepcopy(image); row = bad["native"]["ledger"]["fluid_accounts"][1]
                    row["f"]["e"][0].update(m=7, a=0, o=row["op"])
                    with self.assertRaisesRegex(SmokeError, "adjustment owner"): oracle.check(bad, 0, cut=cut)

    def test_loader_integrity_and_both_loader_cut_entry_points_refuse_a_missing_row(self):
        disk = copy.deepcopy(json.loads((Path(__file__).parent / "fixtures/loader_s6/integrity-native.json").read_text())["e3"]["images"][0]["native"])
        from loader_s6_oracle import normalize_ids
        disk = normalize_ids(disk); account_root(disk["ledger"])
        integrity.pair_identity(disk, disk["ledger"])
        loader.check(disk, "LF_M_K5", 0)
        for call in (lambda d: integrity.pair_identity(d, d["ledger"]), lambda d: loader.check(d, "LF_M_K5", 0),
                     lambda d: named.check(d, "LF_M_NAMED_CROSS_K2", 0)):
            bad = copy.deepcopy(disk); bad["ledger"]["fluid_accounts"] = []
            with self.assertRaisesRegex(SmokeError, "account"): call(bad)

    def test_cargo_s1_checks_account_before_partial_store_or_retirement(self):
        helper = cargo_test.CargoReconciliationRunnerTest()
        for load in (False, True):
            sample = helper.sample(load, [0, 0]); root = nbt.plain(fixture("cargo-planned"))
            root["records"][0].update(phase="PREPARED", reason="NONE")
            if not load: root["records"][0]["kind"] = "FLUID_CARGO_UNLOAD"
            sample = (root, sample[1], sample[2])
            case = "FC_" + ("LOAD" if load else "UNLOAD") + "_L1_REPLACED"
            helper.inspect(sample, case)
            bad = copy.deepcopy(sample); bad[0]["fluid_accounts"] = []
            with self.assertRaises(SmokeError): helper.inspect(bad, case)

    def test_storage_recovery_and_contention_validate_account_even_when_files_are_mocked(self):
        # A malformed account must fail before unrelated native structures are examined.
        for oracle, args in ((storage, ("FC_LOAD_E1_OWN_STORE", False)),
                             (recovery, ("FC_LOAD_R3_OTHER", False)), (contention, (False,))):
            root = nbt.plain(fixture("cargo-planned")); root["records"][0]["accounting_baseline"] = [0]*32
            with self.subTest(oracle=oracle.__name__), patch.object(storage, "read", return_value=dict(ledger=root)), \
                    patch.object(contention, "healthy", return_value={}):
                with self.assertRaisesRegex(SmokeError, "Baseline digest"): oracle.inspect(Path("unused"), *args)

    def test_storage_recovery_and_contention_accept_root3_with_unchanged_native_expectations(self):
        raw = storage_test.CargoStorageOracleTest().raw()
        raw["ledger"] = nbt.plain(fixture("cargo-planned")); raw["ledger"]["records"][0].update(phase="PREPARED", reason="NONE")
        self.assertEqual(5000, storage_test.CargoStorageOracleTest().inspect(raw, "FC_LOAD_E1_OWN_STORE")["raw_total"])
        raw, rocket, device = recovery_test.CargoRecoveryOracleTest().raw()
        raw["ledger"] = nbt.plain(fixture("cargo-planned")); raw["ledger"]["records"][0].update(phase="REPAIR", reason="OTHER_STATE")
        device["tank"]["FluidName"] = "minecraft:lava"
        self.assertEqual(5000, recovery_test.CargoRecoveryOracleTest().inspect(raw, "R3_OTHER", True)["raw_total"])
        raw = storage_test.CargoStorageOracleTest().raw()
        root = nbt.plain(fixture("cargo-planned")); root["issued_through"] = 2
        op = root["records"][0]; op.update(phase="STALLED", reason="BARRIER_IO")
        raw["ledger"] = root
        device = raw["terrain"]["block_entities"][0]["arce_rocket_fluid_cargo"]
        rocket = raw["entities"]["Entities"][0]["RocketEntityData"]
        for section, role in ((device["resource_escrow"], "SOURCE"), (rocket["resource_escrow"], "DESTINATION")):
            section.update(ledger_id=root["ledger_id"], markers=[dict(op_id=op["op_id"], seq=1, role=role, epoch_after=1)])
        control = copy.deepcopy(raw); control["terrain"]["block_entities"][0].update(x=88, z=88)
        healthy = control["entities"]["Entities"][0]; healthy.update(UUID="healthy", Pos=[92.5, 100.0, 88.5])
        control_device = control["terrain"]["block_entities"][0]["arce_rocket_fluid_cargo"]
        control_device["binding"]["physical_rocket"] = "healthy"
        control_device["resource_escrow"]["markers"] = []; healthy["RocketEntityData"]["resource_escrow"]["markers"] = []
        with patch.object(storage, "read", return_value=raw), patch.object(contention, "healthy", return_value=control):
            self.assertEqual(10000, contention.inspect(Path("unused"), False)["raw_total"])

    def test_settlement_known_zero_and_unknown_missing_do_not_change_payload_amount(self):
        disk = released_disk(); root = nbt.plain(fixture("cargo-known-o4"))
        decoded = decode_receipt(root["tombstones"][0], a.rows_by_id(root["fluid_accounts"]))
        # Retain the old oracle's independent endpoint state while replacing its accounting framing with real codec output.
        disk["ledger"].update(schema_version=3, format_epoch=a.EPOCHS[3], fluid_accounts=root["fluid_accounts"])
        disk["ledger"]["tombstones"][0].update({k: decoded[k] for k in ("op_id", "schema_version", "accounting_digest", "accounting_status", "decision_digest")})
        settlement.check("FS_PRUNE_AFTER", disk, False)
        for mutate in (lambda r: r["tombstones"][0].update(accounting_status="UNKNOWN"),
                       lambda r: r["tombstones"][0].update(schema_version=3),
                       lambda r: r["fluid_accounts"][0]["f"]["e"][0].update(a=1),
                       lambda r: r["tombstones"][0].update(payload_amount=1000)):
            bad = copy.deepcopy(disk); mutate(bad["ledger"])
            with self.assertRaises(SmokeError): settlement.check("FS_PRUNE_AFTER", bad, False)
        missing_disk = copy.deepcopy(disk); missing_disk["device"] = None
        missing_disk["rocket"].update(cargo=5000, epoch=0)
        missing_disk["ledger"]["rocket_authorities"][0]["e"] = 0
        row = nbt.plain(fixture("cargo-missing-o4"))["fluid_accounts"][0]; row["f"]["e"][0]["s"] = 5000
        root = missing_disk["ledger"]; root["fluid_accounts"] = [row]
        receipt = root["tombstones"][0]
        receipt.update(accounting_status="UNKNOWN", accounting_digest=a.digest(a.row_tag(row)), choice="ABANDON", missing=[{}])
        settlement.check("FS_ABANDON_DEVICE", missing_disk, True)
        receipt["accounting_status"] = "KNOWN"
        with self.assertRaises(SmokeError): settlement.check("FS_ABANDON_DEVICE", missing_disk, True)

    def test_item_oracles_keep_unknown_and_refuse_schema4_in_root3(self):
        disk = released_disk()
        disk["rocket"]["cargo"] = 6; disk["device"]["amount"] = 64
        root = disk["ledger"]; root.update(schema_version=3, format_epoch=a.EPOCHS[3], fluid_accounts=[])
        root["tombstones"][0].update(schema_version=3, op_id=[0, 0, 0, 1])
        root["tombstones"][0]["survivors"][1].update(signature_kind="ITEM_BUFFER_V1", quantity=0)
        items.check_settlement("IS_PRUNE_AFTER", disk, True)
        # The item oracle must not allow a fluid KNOWN receipt even if its root is new.
        root = nbt.plain(fixture("cargo-known-o4")); root["tombstones"] = [
            decode_receipt(root["tombstones"][0], a.rows_by_id(root["fluid_accounts"]))]
        for call in (lambda: items.check_settlement("IS_PRUNE_AFTER", dict(ledger=root), False),
                     lambda: items.check_recovery("IR_LOOT", dict(ledger=dict(root, fluid_accounts=[])), False)):
            with self.assertRaises(SmokeError): call()


if __name__ == "__main__": unittest.main()
