import unittest

from scripts.run_v070_multiplayer_server_smoke import CLIENT_NAMES, offline_uuid


class V070MultiplayerServerSmokeTests(unittest.TestCase):
    def test_offline_player_identity_matches_minecraft_algorithm(self) -> None:
        self.assertEqual("380df991-f603-344c-a090-369bad2a924a", str(offline_uuid("Dev")))
        self.assertNotEqual(offline_uuid(CLIENT_NAMES[0]), offline_uuid(CLIENT_NAMES[1]))

    def test_client_names_are_bounded_and_distinct(self) -> None:
        self.assertEqual(2, len(set(CLIENT_NAMES)))
        self.assertTrue(all(3 <= len(name) <= 16 for name in CLIENT_NAMES))


if __name__ == "__main__":
    unittest.main()
