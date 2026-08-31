import hashlib
import tempfile
import unittest
from pathlib import Path

from scripts.run_dedicated_server_smoke import SmokeError
from scripts.run_v050_rocket_server_smoke import (
    EXPECTED_VERSION,
    _server_command,
    _verify_inputs,
)


class V050RocketServerSmokeTests(unittest.TestCase):
    def test_input_binding_accepts_only_the_installed_baseline_artifact(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            server = Path(temporary)
            artifact = (
                server
                / "mods"
                / f"advancedrocketry-community-{EXPECTED_VERSION}.jar"
            )
            artifact.parent.mkdir()
            artifact.write_bytes(b"bound rocket artifact")
            digest = hashlib.sha256(artifact.read_bytes()).hexdigest()
            summary = {
                "mod_version": EXPECTED_VERSION,
                "server_port": 25585,
                "artifact_sha256": digest,
            }

            self.assertEqual((25585, digest), _verify_inputs(server, summary, EXPECTED_VERSION))
            artifact.write_bytes(b"tampered")
            with self.assertRaisesRegex(SmokeError, "does not match"):
                _verify_inputs(server, summary, EXPECTED_VERSION)

    def test_invalid_port_or_version_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            server = Path(temporary)
            (server / "mods").mkdir()
            summary = {
                "mod_version": "wrong",
                "server_port": 0,
                "artifact_sha256": "0" * 64,
            }
            with self.assertRaisesRegex(SmokeError, "expected artifact version"):
                _verify_inputs(server, summary, EXPECTED_VERSION)

    def test_release_test_hook_is_explicitly_scoped_to_the_smoke_process(self) -> None:
        command = _server_command("java")

        self.assertIn("-Dadvancedrocketrycommunity.releaseTestHooks=true", command)
        self.assertEqual("nogui", command[-1])
        self.assertEqual(1, sum("releaseTestHooks" in item for item in command))


if __name__ == "__main__":
    unittest.main()
