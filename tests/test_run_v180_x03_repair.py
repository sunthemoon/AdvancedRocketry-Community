"""X03-V skeleton: the verification start command, without starting a server."""
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_x03_repair as runner

HOOKS = "-Dadvancedrocketrycommunity.releaseTestHooks=true"
SCENARIO = "-Dadvancedrocketrycommunity.escrowScenario="
CASE = "-Dadvancedrocketrycommunity.escrowCase="
ROUTE = "-Dadvancedrocketrycommunity.escrowVerifyRoute="


class VerificationCommandTest(unittest.TestCase):
    def test_each_case_sets_the_hooks_the_verify_scenario_and_exactly_that_case(self):
        self.assertEqual(["FF_X03_LAUNCH", "FF_X03_OBSERVE", "FF_X03_UNLOAD"], sorted(runner.CASES))
        for case in runner.CASES:
            command = runner.verification_command("java", case)
            self.assertEqual("java", command[0])
            self.assertEqual(1, command.count(HOOKS))
            self.assertEqual([SCENARIO + "verify"], [value for value in command if value.startswith(SCENARIO)])
            self.assertEqual([CASE + case], [value for value in command if value.startswith(CASE)])
            self.assertEqual([], [value for value in command if value.startswith(ROUTE)])
            self.assertEqual("nogui", command[-1])

    def test_no_case_is_started_under_the_start_or_recover_scenario(self):
        for case in runner.CASES:
            command = runner.verification_command("java", case)
            self.assertNotIn(SCENARIO + "start", command)
            self.assertNotIn(SCENARIO + "recover", command)

    def test_only_the_launch_takes_a_route_and_only_a_known_one(self):
        command = runner.verification_command("java", "FF_X03_LAUNCH", "MOON")
        self.assertEqual([ROUTE + "MOON"], [value for value in command if value.startswith(ROUTE)])
        for case, route in (("FF_X03_OBSERVE", "MOON"), ("FF_X03_UNLOAD", "MOON"), ("FF_X03_LAUNCH", "MARS"),
                            ("FF_X03_LAUNCH", "")):
            with self.assertRaises(ValueError):
                runner.verification_command("java", case, route)

    def test_unknown_names_are_refused(self):
        for case in ("", "X03_OBSERVE", "ff_x03_observe", "OBSERVE", "FF-X03-OBSERVE"):
            with self.assertRaises(ValueError):
                runner.verification_command("java", case)

    def test_the_descendant_sequence_and_the_result_markers(self):
        self.assertEqual(("FF_X03_UNLOAD", "FF_X03_OBSERVE", "FF_X03_LAUNCH", "FF_X03_OBSERVE"), runner.DESCENDANT_SEQUENCE)
        self.assertEqual("ARCE_RELEASE_CARGO_CUT_RESULT cut=FF_X03_UNLOAD state=UNLOADED ", runner.marker("FF_X03_UNLOAD"))
        self.assertEqual({"OBSERVED", "UNLOADED", "LANDED"}, {spec["state"] for spec in runner.CASES.values()})


if __name__ == "__main__":
    unittest.main()
