import copy
import unittest

from scripts import run_v130_atmosphere_boundary_smoke as runner


class AtmosphereBoundarySmokeTests(unittest.TestCase):
    def state(self, lit=False, energy=39000, oxygen=2999, phase=0):
        return {"boundary": {"Name": runner.BOUNDARY, "Properties": {"open": "true"}},
                "vent_block": {"Name": runner.VENT, "Properties": {"lit": str(lit).lower()}},
                "vent_data": {"schema_version": 1, "oxygen_canisters": 0, "empty_canisters": 0,
                              "oxygen_units": oxygen, "energy": energy, "oxygen_phase": phase}}

    def test_exactly_one_expected_startup_event_and_version(self):
        runner.validate_registration([runner.REGISTERED], False)
        runner.validate_registration([runner.SKIPPED], True)
        for lines, skipped in (([], False), ([runner.REGISTERED] * 2, False),
                               ([runner.REGISTERED, runner.SKIPPED], False),
                               ([runner.SKIPPED], False), ([runner.REGISTERED], True),
                               ([runner.REGISTERED.replace("1.5", "1.1")], False)):
            with self.subTest(lines=lines, skipped=skipped), self.assertRaises(runner.SmokeError):
                runner.validate_registration(lines, skipped)

    def test_clean_restarts_preserve_open_state_and_resources(self):
        state = self.state()
        runner.validate_disk(state, "setup-reload", None)
        runner.validate_disk(state, "open-restart", state)
        closed = self.state(True, 38580, 2998, 7)
        runner.validate_disk(closed, "provider-skipped", state)
        opened = self.state(False, 38580, 2998, 0)
        runner.validate_disk(opened, "provider-restored", closed)

    def test_wrong_block_state_or_resource_growth_is_not_a_restart_pass(self):
        before = self.state()
        mutations = [lambda state: state["boundary"]["Properties"].update(open="false"),
                     lambda state: state["vent_block"]["Properties"].update(lit="true"),
                     lambda state: state["vent_data"].update(energy=39001),
                     lambda state: state["vent_data"].update(oxygen_units=3000),
                     lambda state: state["vent_data"].update(oxygen_phase=1),
                     lambda state: state["vent_data"].update(schema_version=2),
                     lambda state: state["vent_data"].update(unexpected=1)]
        for mutation in mutations:
            state = copy.deepcopy(before)
            mutation(state)
            with self.assertRaises(runner.SmokeError):
                runner.validate_disk(state, "open-restart", before)

    def test_missing_provider_must_really_supply_not_only_show_lit(self):
        for state in (self.state(True), self.state(True, energy=38900, oxygen=2998),
                      self.state(True, energy=38000, oxygen=2999), self.state(False, energy=38000, oxygen=2998)):
            with self.assertRaises(runner.SmokeError):
                runner.validate_disk(state, "provider-skipped", self.state())

    def test_invalid_schema_bounds_and_phase_reject(self):
        for key, value in (("oxygen_units", 0), ("energy", 0), ("energy", 40001), ("oxygen_units", 3001),
                           ("oxygen_canisters", 1), ("empty_canisters", 1), ("oxygen_phase", 20)):
            state = self.state()
            state["vent_data"][key] = value
            with self.subTest(key=key), self.assertRaises(runner.SmokeError):
                runner.validate_disk(state, "setup-reload", None)
        with self.assertRaises(runner.SmokeError):
            runner.validate_disk(self.state(), "arbitrary", None)


if __name__ == "__main__":
    unittest.main()
