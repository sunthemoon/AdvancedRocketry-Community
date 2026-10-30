"""Diagnostic export changes only the existing observer, never production code or protocol."""
import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from w210_admission_profile import OBSERVE, METRICS, instrument


class ProfileTest(unittest.TestCase):
    def test_real_observer_has_exactly_one_additive_change(self):
        file = Path(__file__).resolve().parents[1] / "src/test/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/W208NativeAvailability.java"
        before = file.read_text()
        after = instrument(before)
        self.assertEqual(before, after.replace(METRICS, ""))
        self.assertEqual(1, after.count('event.put("cpu_ns"'))
        self.assertEqual(1, after.count('event.put("allocated_bytes"'))

    def test_changed_or_repeated_observer_is_not_silently_patched(self):
        for source in ("", OBSERVE * 2, instrument(OBSERVE)):
            with self.subTest(source=source), self.assertRaises(ValueError):
                instrument(source)


if __name__ == "__main__":
    unittest.main()
