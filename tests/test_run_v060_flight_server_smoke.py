import hashlib
import tempfile
import unittest
from pathlib import Path

from scripts.run_dedicated_server_smoke import SmokeError
from scripts.run_v060_flight_server_smoke import (
    EXPECTED_FLIGHT_EVENTS,
    EXPECTED_VERSION,
    RESTART_CASES,
    ROUND_TRIPS,
    FlightHarness,
    _server_command,
    _verify_inputs,
)


class V060FlightServerSmokeTests(unittest.TestCase):
    def test_release_matrix_and_round_trip_budget_are_fixed(self) -> None:
        self.assertEqual(20, ROUND_TRIPS)
        self.assertEqual(
            [
                "ASSEMBLED",
                "FUELED",
                "COUNTDOWN",
                "ASCENT",
                "TRANSIT_PREPARED",
                "DESTINATION_SPAWNED",
                "DESCENT",
                "LANDED",
            ],
            RESTART_CASES,
        )
        self.assertEqual(8, len(EXPECTED_FLIGHT_EVENTS))
        self.assertEqual("countdown_complete", EXPECTED_FLIGHT_EVENTS[0])
        self.assertEqual("landed_reservation_retained", EXPECTED_FLIGHT_EVENTS[-1])

    def test_input_binding_accepts_only_the_installed_baseline_artifact(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            server = Path(temporary)
            artifact = (
                server
                / "mods"
                / f"advancedrocketry-community-{EXPECTED_VERSION}.jar"
            )
            artifact.parent.mkdir()
            artifact.write_bytes(b"bound v0.6 artifact")
            digest = hashlib.sha256(artifact.read_bytes()).hexdigest()
            summary = {
                "mod_version": EXPECTED_VERSION,
                "server_port": 25586,
                "artifact_sha256": digest,
            }

            self.assertEqual((25586, digest), _verify_inputs(
                server,
                summary,
                EXPECTED_VERSION,
            ))
            artifact.write_bytes(b"tampered")
            with self.assertRaisesRegex(SmokeError, "does not match"):
                _verify_inputs(server, summary, EXPECTED_VERSION)

    def test_release_test_hook_is_scoped_to_the_server_process(self) -> None:
        command = _server_command("java")

        self.assertIn("-Dadvancedrocketrycommunity.releaseTestHooks=true", command)
        self.assertEqual("nogui", command[-1])
        self.assertEqual(1, sum("releaseTestHooks" in item for item in command))

    def test_report_retries_once_after_entity_load_finishes(self) -> None:
        entity = "6de9fc3d-f3d9-4b5b-bad5-17f24e88444e"

        class DelayedEntityProcess:
            def __init__(self) -> None:
                self.lines: list[str] = []
                self.commands: list[str] = []
                self.waits = 0

            def command(self, value: str) -> None:
                self.commands.append(value)

            def wait_for(self, marker, timeout, *, start_at=0) -> int:
                self.waits += 1
                if self.waits == 1:
                    self.lines.append("[Server] No entity was found")
                elif self.waits == 2:
                    self.lines.append(
                        "ARCE_ROCKET_ENTITY_ACTIVE "
                        f"entity={entity} operational=true snapshot={'a' * 64}"
                    )
                else:
                    self.lines.append(
                        "ARCE_RELEASE_TEST_FLIGHT_REPORT "
                        f"entity={entity} "
                        "logical=2cb12ee0-7823-4be9-8394-8a144fdf2916 "
                        f"snapshot={'b' * 64} dimension=minecraft:overworld "
                        "state=ASSEMBLED fuel=0 capacity=1000 passengers=0 "
                        "transfer=none origin=384,101,384 blocks=5"
                    )
                return len(self.lines) - 1

        process = DelayedEntityProcess()
        report = FlightHarness.report(process, "minecraft:overworld", entity)

        self.assertEqual(2, len(process.commands))
        self.assertEqual(3, process.waits)
        self.assertEqual(entity, report["entity"])
        self.assertEqual("ASSEMBLED", report["state"])
        self.assertEqual([384, 101, 384], report["origin"])
        self.assertEqual(5, report["blocks"])


if __name__ == "__main__":
    unittest.main()
