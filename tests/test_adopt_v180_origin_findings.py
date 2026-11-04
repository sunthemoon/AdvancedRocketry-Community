"""Structural adoption guards with synthetic findings, not review authorization."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest


SCRIPT = (Path(__file__).resolve().parents[1] /
          "docs/work/v1.8.0-w268-review-closure/adopt_findings.py")
SPEC = importlib.util.spec_from_file_location("adopt_v180_origin_findings", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class AdoptOriginFindingsTests(unittest.TestCase):
    def finding(self, reviewed_at):
        return dict(asset="textures/blocks/beacon.png", decision="EXCLUDED", reviewer="fixture-reviewer",
                    reviewed_at=reviewed_at, basis="Synthetic fixture only.",
                    review_record="docs/fixture-decision.md")

    def test_canonical_review_date_passes(self):
        MODULE.check_findings(dict(findings=[self.finding("2026-11-04")]))

    def test_preparation_date_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "Preparation date is not a review date"):
            MODULE.check_findings(dict(findings=[self.finding("2026-11-03")]))

    def test_invalid_date_refuses_before_writing(self):
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary) / "input.json"
            output = Path(temporary) / "existing.json"
            absent = Path(temporary) / "absent.json"
            output.write_bytes(b"unchanged output")
            for reviewed_at in ("2026-11-03\n", "2026-11-04\n", "2026-11-04\r\n", " 2026-11-04",
                                "2026-11-04 ", "2026-11-4", None, 20261104):
                with self.subTest(reviewed_at=reviewed_at):
                    source.write_text(json.dumps(dict(findings=[self.finding(reviewed_at)])), encoding="utf-8")
                    for target in (output, absent):
                        with self.assertRaisesRegex(ValueError, "Invalid review date"):
                            MODULE.adopt(source, target)
                    self.assertEqual(b"unchanged output", output.read_bytes())
                    self.assertFalse(absent.exists())


if __name__ == "__main__":
    unittest.main()
