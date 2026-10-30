"""The inventory excludes process-local generated names; startup observations stay separate."""
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from w215_first_use_summary import PACKAGE, named_mod_classes, startup_rows


class FirstUseSummaryTest(unittest.TestCase):
    def test_only_stable_mod_names_enter_the_inventory(self):
        a, b = PACKAGE + "resourceescrow.A", PACKAGE + "resourceescrow.A$Nested"
        self.assertEqual([a, b], named_mod_classes([b, a, a, a + "$$Lambda$42+0x123.55",
                                                   "java.lang.invoke.LambdaForm$MH+0x456.8", "net.minecraft.A"]))

    def test_startup_metrics_require_the_diagnostic_marker_and_parse_the_entire_row(self):
        self.assertEqual([], startup_rows("ARCE_RELAUNCH_CLASSES initialized in 42 ns"))
        self.assertEqual([{"elapsed_ns": 12, "heap_used_delta_bytes": -5}], startup_rows(
            '[Server thread/INFO] W215_STARTUP {"elapsed_ns":12,"heap_used_delta_bytes":-5}\n'))
        with self.assertRaises(ValueError):
            startup_rows("W215_STARTUP {broken}")


if __name__ == "__main__":
    unittest.main()
