import unittest

from scripts.check_gametest_log import DEFAULT_MANIFEST, check, load_manifest, parse_log
from tests.test_check_gametest_log import CONFIG_LOGGER, COMPLETE, LAUNCH, SHUTDOWN, batch, line


class W05GameTestLogExpectations(unittest.TestCase):
    MESSAGE = "Incorrect key machines.sawmillVanillaWood was corrected from null to its default, true. "

    def setUp(self):
        self.expectations = [entry for entry in load_manifest(DEFAULT_MANIFEST) if entry.id in {
            "forge-config-key-defaulted", "w05-sawmill-config-key-defaulted"}]
        self.assertEqual(2, len(self.expectations))

    def report(self, messages, after_startup=False):
        warnings = [line("WARN", CONFIG_LOGGER, message) for message in messages]
        start = batch("w05_sawmill_switch")
        content = [LAUNCH, *([start, *warnings] if after_startup else [*warnings, start]), *COMPLETE, SHUTDOWN]
        return check(parse_log("\n".join(content) + "\n"), self.expectations)

    def test_owned_default_is_separate_and_generic_budget_is_unchanged(self):
        limits = {entry.id: entry.maximum for entry in self.expectations}
        self.assertEqual(99, limits["forge-config-key-defaulted"])
        self.assertEqual(1, limits["w05-sawmill-config-key-defaulted"])
        other = "Incorrect key machines.pumpEnabled was corrected from null to its default, true. "
        self.assertTrue(self.report([other] * 99 + [self.MESSAGE]).passed)
        self.assertFalse(self.report([other] * 100).passed)

    def test_only_one_owned_startup_warning_is_admitted_and_reused_config_needs_none(self):
        self.assertTrue(self.report([]).passed)
        self.assertTrue(self.report([self.MESSAGE]).passed)
        self.assertFalse(self.report([self.MESSAGE, self.MESSAGE]).passed)
        self.assertFalse(self.report([self.MESSAGE], after_startup=True).passed)

    def test_non_null_corrections_wrong_defaults_and_other_keys_do_not_use_owned_allowance(self):
        self.assertFalse(self.report([self.MESSAGE.replace("true", "false")]).passed)
        self.assertFalse(self.report([self.MESSAGE.replace("from null", "from false")]).passed)
        altered = self.MESSAGE.replace("sawmillVanillaWood", "sawmillVanillaWoodUnexpected")
        self.assertFalse(self.report([altered] * 100).passed)


if __name__ == "__main__":
    unittest.main()
