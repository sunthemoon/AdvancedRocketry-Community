import tempfile
import unittest
from pathlib import Path

from scripts.run_v080_multiplayer_server_smoke import (
    CLIENT_NAMES,
    EXPECTED_VERSION,
    _filter_client_log,
)


class V080MultiplayerServerSmokeTests(unittest.TestCase):
    def test_client_names_are_bounded_and_distinct(self) -> None:
        self.assertEqual(("ClientA", "PilotB"), CLIENT_NAMES)
        self.assertEqual(2, len(set(CLIENT_NAMES)))
        self.assertTrue(all(3 <= len(name) <= 16 for name in CLIENT_NAMES))

    def test_client_filter_requires_bound_lifecycle(self) -> None:
        marker = (
            "ARCE_V080_G4_SHARED_STATE players=2 satellites=102 missions=102 "
            "artifact=0ce6c6bf9eb603f5"
        )
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "latest.log"
            path.write_text(
                "\n".join((
                    "Setting user: ClientA",
                    f"Advanced Rocketry: Community Edition {EXPECTED_VERSION} initialized",
                    "Connected to a modded server.",
                    marker,
                    "Stopping!",
                )) + "\n",
                encoding="utf-8",
            )

            filtered, document = _filter_client_log(path, "ClientA", marker)

        self.assertEqual("ClientA", document["username"])
        self.assertTrue(document["clean_shutdown"])
        self.assertIn(marker, filtered)


if __name__ == "__main__":
    unittest.main()
