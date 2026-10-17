from pathlib import Path
import re
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from inspect_escrow_receipt import FIELDS, decode_receipt


class EscrowReceiptInspectionTest(unittest.TestCase):
    def test_wire_dictionary_matches_the_versioned_java_codec(self):
        source = (ROOT / "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowReceiptCodec.java").read_text(encoding="utf-8")
        declaration = re.search(r"FIELDS = List.of\((.*?)\);", source, re.S).group(1)
        self.assertEqual(FIELDS, tuple(re.findall(r'"([a-z_]+)"', declaration)))

    def test_unknown_accounting_preserves_exact_digest_bytes_and_missing_identity(self):
        identity = [1, 2, 3, 4]
        wire = {"f0": 3, "f7": "UNKNOWN", "f6": list(range(32)), "f4": [{"fl": 1, "fk": {"ft": identity}}]}
        receipt = decode_receipt(wire)
        self.assertEqual("UNKNOWN", receipt["accounting_status"])
        self.assertEqual(bytes(range(32)).hex(), receipt["payload_hash"])
        self.assertEqual([{"identity": identity}], receipt["missing"])
        self.assertEqual([], receipt["survivors"])
        self.assertNotIn("audited_loss", receipt)
        self.assertNotIn("loss_known", receipt)
        for key in ("f00", "schema_version", "fzz"):
            changed = dict(wire); changed[key] = 3
            with self.assertRaises(ValueError):
                decode_receipt(changed)
        for status in ("KNOWN", "", "ZERO"):
            changed = dict(wire); changed["f7"] = status
            with self.assertRaises(ValueError):
                decode_receipt(changed)
        changed = dict(wire); changed["f6"] = [0] * 31
        with self.assertRaises(ValueError):
            decode_receipt(changed)


if __name__ == "__main__":
    unittest.main()
