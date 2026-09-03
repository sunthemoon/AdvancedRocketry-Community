import json
import tempfile
import unittest
from pathlib import Path

from scripts.validate_v090_resources import (
    ROOT,
    _placeholder_contract,
    audit_languages,
    audit_resources,
)


class V090ResourceAuditTests(unittest.TestCase):
    def test_repository_resource_contract_passes(self) -> None:
        summary, errors = audit_resources(ROOT)

        self.assertEqual([], errors)
        self.assertEqual("PASS", summary["result"])
        self.assertGreater(summary["en_us_keys"], 100)
        self.assertEqual(summary["en_us_keys"], summary["zh_cn_keys"])
        self.assertEqual(4, summary["textual_status_surfaces"])

    def test_placeholder_contract_ignores_literal_percent(self) -> None:
        self.assertEqual(("%s", "%2$d"), _placeholder_contract("Fuel %s / %2$d %%"))

    def test_language_audit_rejects_missing_key_and_placeholder_drift(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            en = root / "en_us.json"
            zh = root / "zh_cn.json"
            en.write_text(
                json.dumps({"screen.advancedrocketrycommunity.test": "Value %s", "only.en": "x"}),
                encoding="utf-8",
            )
            zh.write_text(
                json.dumps({"screen.advancedrocketrycommunity.test": "值 %s %s"}),
                encoding="utf-8",
            )

            _, errors = audit_languages((en, zh))

        self.assertTrue(any("missing translation key only.en" in error for error in errors))
        self.assertTrue(any("placeholder contract differs" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
