"""Diagnostic transformation and non-overlapping attribution boundaries."""
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))


class InstrumentationTest(unittest.TestCase):
    def test_wrap_handles_comments_strings_and_early_returns(self):
        from w212_instrument import wrap
        source = 'class A { int f() { /* } */ String s = "}"; if (true) { return 1; } return 2; } }'
        result = wrap(source, "int f()", "f")
        self.assertIn('W212Timing.open("f")', result)
        self.assertIn('/* } */ String s = "}";', result)
        self.assertTrue(result.endswith("\n    } }"))

    def test_transform_is_explicit_and_rejects_missing_or_ambiguous_anchor(self):
        from w212_instrument import wrap, TARGETS
        for source in ('class A {}', 'void f() {} void f() {}'):
            with self.assertRaises(ValueError):
                wrap(source, "void f()", "f")
        self.assertGreaterEqual(len(TARGETS), 8)
        for relative, spans in TARGETS.items():
            source = (ROOT / "src/main/java/io/github/sunthemoon/advancedrocketrycommunity" / relative).read_text(encoding="utf-8")
            for anchor, label in spans:
                source = wrap(source, anchor, label)
            self.assertEqual(source.count("var w212Span ="), len(spans))

    def test_exclusive_attribution_preserves_unassigned_tick_time(self):
        from w212_summary import partition
        rows = [dict(label="outer", start=10, end=90, depth=0),
                dict(label="hash", start=20, end=40, depth=1),
                dict(label="decode", start=50, end=60, depth=1)]
        result = partition(rows, 100)
        self.assertEqual(result, {"outer": 50, "hash": 20, "decode": 10, "unassigned": 20})


if __name__ == "__main__":
    unittest.main()
