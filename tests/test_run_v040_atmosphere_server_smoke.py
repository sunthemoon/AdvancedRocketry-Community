import tempfile
import unittest
from pathlib import Path

from scripts.run_dedicated_server_smoke import SmokeError
from scripts.run_v040_atmosphere_server_smoke import (
    EXPECTED_VERSION,
    VENT_POSITIONS,
    _wait_for_marker,
    _verify_inputs,
    parse_tps_sample,
    percentile,
)


class V040AtmosphereServerSmokeTests(unittest.TestCase):
    def test_scenario_is_exactly_sixteen_bounded_rooms(self) -> None:
        self.assertEqual(16, len(VENT_POSITIONS))
        self.assertEqual(16, len(set(VENT_POSITIONS)))
        self.assertTrue(all(position[1] == 100 for position in VENT_POSITIONS))

    def test_five_minute_floor_cannot_be_relaxed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            summary = {
                "mod_version": EXPECTED_VERSION,
                "server_port": 25585,
                "artifact_sha256": "0" * 64,
            }
            with self.assertRaisesRegex(SmokeError, "between 300 and 1800"):
                _verify_inputs(root, summary, EXPECTED_VERSION, 299.999)

    def test_tps_parser_prefers_overall_and_percentile_is_nearest_rank(self) -> None:
        sample = parse_tps_sample([
            "Dim moon: Mean tick time: 1.250 ms. Mean TPS: 20.000",
            "Overall: Mean tick time: 2.500 ms. Mean TPS: 19.990",
        ])
        self.assertEqual((2.5, 19.99), sample)
        self.assertEqual(9.0, percentile([1.0, 9.0, 2.0, 8.0], 0.95))

    def test_unparseable_tps_output_fails_closed(self) -> None:
        with self.assertRaisesRegex(SmokeError, "no parseable tick sample"):
            parse_tps_sample(["TPS output unavailable"])

    def test_state_marker_command_is_retried_until_the_server_matches(self) -> None:
        class RunningProcess:
            def poll(self) -> None:
                return None

        class DelayedMarkerProcess:
            def __init__(self) -> None:
                self.lines: list[str] = []
                self.process = RunningProcess()
                self.commands: list[str] = []

            def command(self, value: str) -> None:
                self.commands.append(value)

            def wait_for(self, marker, timeout, *, start_at=0) -> int:
                if len(self.commands) < 3:
                    raise SmokeError("Timed out waiting for server marker")
                self.lines.append("[Server] V040_READY\n")
                return 0

        process = DelayedMarkerProcess()
        _wait_for_marker(process, "execute if block ...", "V040_READY", timeout=5.0)
        self.assertEqual(3, len(process.commands))


if __name__ == "__main__":
    unittest.main()
