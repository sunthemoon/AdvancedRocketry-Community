"""Synthetic real-codec fixtures exercise diagnostics, not activation or native durability."""
import copy
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import escrow_accounting as a
import loader_cut_nbt as nbt
import run_v180_escrow_forced_stop as native
from inspect_escrow_receipt import decode_receipt

FIXTURES = Path(__file__).parent / "fixtures/escrow_accounting"


def fixture(name):
    return nbt.compound(nbt.read_gzip(FIXTURES / (name + ".dat"))[1], "data")


def inspect_tag(tag):
    with tempfile.TemporaryDirectory() as directory:
        path = Path(directory) / "ledger.dat"
        nbt.write_gzip(path, b"", nbt.Tag(nbt.COMPOUND, {"data": tag}))
        return native.ledger(path)


class AccountingReadersTest(unittest.TestCase):
    def test_explicit_readers_decode_both_real_codec_roots_and_all_saved_stages(self):
        paths = list(FIXTURES.glob("*.dat")); self.assertEqual(18, len(paths))
        for path in paths:
            with self.subTest(path=path.name):
                root = native.ledger(path)
                self.assertEqual(2 if "root2" in path.stem else 3, root["schema_version"])
                a.validate_root(root)
                if root["tombstones"]:
                    status = "UNKNOWN" if "missing" in path.stem or "root2" in path.stem else "KNOWN"
                    self.assertEqual(status, root["tombstones"][0]["accounting_status"])
                    self.assertEqual(0 if path.stem.startswith("cargo") else 1000, root["tombstones"][0]["payload_amount"])

    def test_receipt4_requires_its_exact_bounded_frozen_row_not_just_a_status_allowance(self):
        for state in ("known", "missing"):
            root = fixture(f"fuel-{state}-o4"); raw = nbt.plain(root)
            rows = a.rows_by_id(raw["fluid_accounts"]); wire = raw["tombstones"][0]
            receipt = decode_receipt(wire, rows)
            self.assertEqual("KNOWN" if state == "known" else "UNKNOWN", receipt["accounting_status"])
            for mutate in (lambda w: w.update(f0=5), lambda w: w.update(f1=[0, 0, 0, 0]),
                           lambda w: w.update(f1b=[0] * 32), lambda w: w.update(f7="UNKNOWN" if state == "known" else "KNOWN")):
                bad = copy.deepcopy(wire); mutate(bad)
                with self.assertRaises(ValueError): decode_receipt(bad, rows)
            with self.assertRaises(ValueError): decode_receipt(wire)

    def test_root_version_epoch_row_and_operation_references_are_not_fallbacks(self):
        for version in (1, 2, 3, 4):
            tag = fixture("fuel-planned")
            if version == 3:
                tag.value["format_epoch"] = nbt.Tag(nbt.STRING, a.EPOCHS[2].encode())
            else: tag.value["schema_version"].value = version
            with self.assertRaises(native.smoke.SmokeError): inspect_tag(tag)
        for mutation in (lambda t: t.value.pop("fluid_accounts"),
                         lambda t: t.value["fluid_accounts"].value[1].clear()):
            tag = fixture("fuel-planned")
            mutation(tag)
            with self.assertRaises(native.smoke.SmokeError): inspect_tag(tag)
        tag = fixture("fuel-planned"); tag.value["records"].value[1][0].value["accounting_baseline"] = nbt.Tag(nbt.BYTES, bytes(32))
        with self.assertRaisesRegex(native.smoke.SmokeError, "Baseline digest"): inspect_tag(tag)
        tag = fixture("fuel-known-o4"); tag.value["tombstones"].value[1][0].value["f1b"] = nbt.Tag(nbt.BYTES, bytes(32))
        with self.assertRaisesRegex(native.smoke.SmokeError, "Receipt account digest"): inspect_tag(tag)
        tag = fixture("fuel-planned"); row = tag.value["fluid_accounts"].value[1][0]
        row.value["b"].value["e"].value[1][0].value["d"] = nbt.Tag(nbt.BYTES, bytes(32))
        tag.value["records"].value[1][0].value["accounting_baseline"] = nbt.Tag(nbt.BYTES, bytes.fromhex(a.digest(row.value["b"])))
        with self.assertRaisesRegex(native.smoke.SmokeError, "native key"): inspect_tag(tag)

    def test_all_r443_bounds_and_wrong_native_types_are_refused(self):
        worst = nbt.read_gzip(FIXTURES / "worst-row.nbt")[1]
        a.read_row(nbt.plain(worst), worst)
        row = fixture("fuel-known-o4").value["fluid_accounts"].value[1][0]
        bad = copy.deepcopy(row); bad.value["padding"] = nbt.Tag(nbt.BYTES, bytes(2048))
        with self.assertRaisesRegex(ValueError, "byte bound"): a.bounds(bad)
        bad = copy.deepcopy(row); bad.value["f"].value["m"] = nbt.Tag(nbt.INTS, [0, 0, 0, 1] * 15)
        with self.assertRaisesRegex(ValueError, "member bound"): a.read_row(nbt.plain(bad), bad)
        bad = nbt.Tag(nbt.COMPOUND, {"c": nbt.Tag(nbt.COMPOUND, {"c": nbt.Tag(nbt.COMPOUND,
            {"c": nbt.Tag(nbt.COMPOUND, {"c": nbt.Tag(nbt.COMPOUND, {"x": nbt.Tag(nbt.BYTE, 0)})})})})})
        with self.assertRaisesRegex(ValueError, "depth/node"): a.bounds(bad)
        bad = nbt.Tag(nbt.COMPOUND, {str(i): nbt.Tag(nbt.BYTE, 0) for i in range(65)})
        with self.assertRaisesRegex(ValueError, "depth/node"): a.bounds(bad)
        bad = copy.deepcopy(row); bad.value["b"].value["e"].value[1][0].value["s"].type = nbt.INT
        with self.assertRaisesRegex(ValueError, "NBT type"): a.read_row(nbt.plain(bad), bad)
        for target in ("v", "op"):
            bad = nbt.plain(row); bad[target] = 2 if target == "v" else [1]
            with self.assertRaises(ValueError): a.read_row(bad)

    def test_signed_balance_is_per_key_and_overflow_is_not_python_bigint_success(self):
        raw = nbt.plain(fixture("fuel-known-o4"))["fluid_accounts"][0]
        for gain in (-1000, -100, 0, 100):
            row = copy.deepcopy(raw); row["f"]["e"][0].update(t=1000-gain, a=gain)
            a.read_row(row)
            row["f"]["e"][0]["a"] += 1
            with self.assertRaisesRegex(ValueError, "balance"): a.read_row(row)
        row = copy.deepcopy(raw); row["f"]["e"][0].update(s=2**63-1, t=2**63-1, a=0)
        with self.assertRaisesRegex(ValueError, "overflow"): a.read_row(row)
        row = copy.deepcopy(raw); row["f"]["e"][0]["d"] = [1]*32
        with self.assertRaisesRegex(ValueError, "union"): a.read_row(row)

    def test_fresh_fluid_in_root3_cannot_silently_select_migrated_legacy_semantics(self):
        root = nbt.plain(fixture("fuel-root2")); root.update(schema_version=3, format_epoch=a.EPOCHS[3], fluid_accounts=[])
        a.validate_root(root)  # a legitimate migrated legacy record
        with self.assertRaisesRegex(native.smoke.SmokeError, "legacy branch"): a.check(root)

    def test_root_reader_rejects_oversize_and_wrong_balance_even_with_recomputed_receipt_digest(self):
        for change in (lambda r: r.value["f"].value["e"].value[1][0].value["a"].__setattr__("value", 1),
                       lambda r: r.value["f"].value.__setitem__("m", nbt.Tag(nbt.INTS, [0, 0, 0, 1]*15)),
                       lambda r: r.value["b"].value["e"].value[1][0].value.__setitem__("i", nbt.Tag(nbt.STRING, b"x"*2048))):
            root = fixture("fuel-known-o4"); row = root.value["fluid_accounts"].value[1][0]; change(row)
            root.value["tombstones"].value[1][0].value["f1b"] = nbt.Tag(nbt.BYTES, bytes.fromhex(a.digest(row)))
            with self.assertRaises(native.smoke.SmokeError): inspect_tag(root)
        root = fixture("fuel-known-o4")
        root.value["tombstones"].value[1][0].value["f1b"] = nbt.Tag(nbt.INTS, [0]*32)
        with self.assertRaisesRegex(native.smoke.SmokeError, "native digest type"): inspect_tag(root)

    def test_restart_keeps_baseline_and_frozen_row_not_only_status_or_quantity(self):
        before = native.ledger(FIXTURES / "fuel-known-o1.dat")
        after = native.ledger(FIXTURES / "fuel-known-o4.dat")
        a.check_progress(before, after)
        changed = copy.deepcopy(after); changed["fluid_accounts"] = []
        with self.assertRaises(native.smoke.SmokeError): a.check_progress(before, changed)


if __name__ == "__main__": unittest.main()
