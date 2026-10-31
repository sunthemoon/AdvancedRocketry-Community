"""Synthetic sticky transitions over a retained native-shaped loader image."""
import copy
import unittest
import test_loader_s6_oracle as fixtures
import loader_s6_sticky as sticky
from run_dedicated_server_smoke import SmokeError


def seed():
    image = fixtures.LoaderS6OracleTest().image()
    root = image["native"]["ledger"]
    root["writer_generation"] = 3
    root["records"][0].update(phase="REPAIR", reason="JOURNAL_GUARD_MISMATCH")
    return image


class StickyOracleTests(unittest.TestCase):
    def test_all_three_images_are_identical_and_unknown_is_not_zero(self):
        image = seed(); original = copy.deepcopy(image)
        identity = sticky.check(image, 0)["identity"]
        for restart in (1, 2):
            result = sticky.check(image, restart, identity)
            self.assertEqual(identity, result["identity"])
            self.assertIsNone(result["available_total"]); self.assertIsNone(result["loss"])
        self.assertEqual(original, image)
        with self.assertRaises(SmokeError): sticky.check(dict(read_status="UNKNOWN", native=None), 0)

    def test_complete_operation_wrapper_resources_and_identity_remain_fixed(self):
        baseline = seed(); identity = sticky.check(baseline, 0)["identity"]
        for mutate in (
            lambda d: d["ledger"].update(writer_generation=4),
            lambda d: d["ledger"]["records"][0].update(reason="OTHER_STATE"),
            lambda d: d["ledger"]["records"][0].update(phase="PREPARED"),
            lambda d: d["ledger"]["records"][0].update(created_tick=99999),
            lambda d: d["ledger"]["records"][0].update(extra=1),
            lambda d: d["loaders"][0]["arce_fluid_fuel_loader"]["tank"].update(Amount=4001),
            lambda d: d["loaders"][0]["arce_fluid_fuel_loader"]["resource_escrow"].update(markers=[]),
            lambda d: d["rocket"]["RocketEntityData"]["resource_escrow"].update(resource_epoch=2),
            lambda d: d["rocket"]["RocketEntityData"]["snapshot"].update(created_at_game_time=99999)):
            changed = copy.deepcopy(baseline); mutate(changed["native"])
            with self.subTest(mutate=mutate), self.assertRaises(SmokeError): sticky.check(changed, 1, identity)

    def test_log_requires_exact_checked_hold_and_completion_order(self):
        state = "ARCE_LOADER_S6_STICKY_STATE cut=" + sticky.CASE + " generation=3 records=1 pending=1 reason=JOURNAL_GUARD_MISMATCH tank=4000 bank=1000 epochs=1,1 markers=1,1"
        result = "ARCE_LOADER_S6_STICKY_RESULT cut=" + sticky.CASE
        sticky.check_logs([state, result], 1)
        for lines in ([result], [state], [result, state], [state, state, result], [state.replace("pending=1", "pending=0"), result]):
            with self.assertRaises(SmokeError): sticky.check_logs(lines, 1)
