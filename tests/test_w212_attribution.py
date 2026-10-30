"""Diagnostic transformation and non-overlapping attribution boundaries."""
from pathlib import Path
import sys
import unittest
import tempfile
import zipfile

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

    def test_diagnostic_inventory_includes_only_named_source_and_its_nested_classes(self):
        from run_w206_warmup import inventory
        from w212_instrument import PACKAGE
        nested = PACKAGE + "/resourceescrow/EscrowTransferEnvelope$Expanded.class"
        with tempfile.TemporaryDirectory() as directory:
            normal, diagnostic = (Path(directory) / n for n in ("normal.jar", "diagnostic.jar"))
            with zipfile.ZipFile(normal, "w") as jar: jar.writestr(nested, b"before")
            with zipfile.ZipFile(diagnostic, "w") as jar:
                jar.writestr(nested, b"after")
                jar.writestr(PACKAGE + "/resourceescrow/W212Timing.class", b"probe")
            self.assertEqual(inventory(normal, diagnostic, True)["changed"], [nested])
            with self.assertRaises(RuntimeError): inventory(normal, diagnostic, False)


if __name__ == "__main__":
    unittest.main()
