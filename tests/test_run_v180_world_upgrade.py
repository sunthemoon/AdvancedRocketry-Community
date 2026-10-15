"""Small independent inventories exercise upgrade preservation and fail-closed checks."""
import copy
import unittest

from scripts import run_v180_world_upgrade as upgrade
from scripts import v170_world_fixture as fixture


def inventory():
    return {
        "blocks": [{"pos": [128, 100, 128], "id": "advancedrocketrycommunity:machine",
                    "block_entity": "advancedrocketrycommunity:machine"}],
        "items": [{"pos": [128, 100, 240], "slot": 0, "id": "advancedrocketrycommunity:chip",
                   "count": 1, "tag": {"custom": "preserve"}}],
        "roots": {"celestial": {"status": "loaded", "data": {"schema_version": 2,
                  "format_epoch": "v0.9.0-beta", "bodies": [{"id": "advancedrocketrycommunity:moon"}]}}},
    }


class InventoryTests(unittest.TestCase):
    def setUp(self):
        self.old = inventory()
        self.new = copy.deepcopy(self.old)

    def test_identical_inventory(self):
        self.assertEqual([], upgrade.compare_inventories(self.old, self.new))

    def test_missing_block(self):
        self.new["blocks"].clear()
        self.assertIn("blocks", upgrade.compare_inventories(self.old, self.new)[0])

    def test_renamed_id(self):
        self.new["blocks"][0]["id"] = "advancedrocketrycommunity:replacement"
        self.assertIn("replacement", upgrade.compare_inventories(self.old, self.new)[0])

    def test_missing_block_entity(self):
        self.new["blocks"][0]["block_entity"] = None
        self.assertTrue(upgrade.compare_inventories(self.old, self.new))

    def test_block_entity_id_changed(self):
        self.new["blocks"][0]["block_entity"] = "minecraft:chest"
        self.assertTrue(upgrade.compare_inventories(self.old, self.new))

    def test_count_changed(self):
        self.new["items"][0]["count"] = 2
        self.assertIn("items", upgrade.compare_inventories(self.old, self.new)[0])

    def test_item_id_changed(self):
        self.new["items"][0]["id"] = "minecraft:air"
        self.assertTrue(upgrade.compare_inventories(self.old, self.new))

    def test_item_metadata_changed(self):
        self.new["items"][0]["tag"] = {}
        self.assertTrue(upgrade.compare_inventories(self.old, self.new))

    def test_same_totals_different_slot(self):
        self.new["items"][0]["slot"] = 1
        self.assertEqual(2, len(upgrade.compare_inventories(self.old, self.new)))

    def test_duplicate_slot_rejected(self):
        self.new["items"] *= 2
        with self.assertRaisesRegex(fixture.server.SmokeError, "Duplicate"):
            upgrade.compare_inventories(self.old, self.new)

    def test_refused_root(self):
        self.new["roots"]["celestial"]["status"] = "refused"
        self.assertIn("missing or refused", upgrade.compare_inventories(self.old, self.new)[0])

    def test_missing_root(self):
        self.new["roots"].clear()
        self.assertTrue(upgrade.compare_inventories(self.old, self.new))

    def test_unexpected_schema(self):
        self.new["roots"]["celestial"]["data"]["schema_version"] = 3
        self.assertTrue(upgrade.compare_inventories(self.old, self.new))

    def test_unknown_root_appears(self):
        self.new["roots"]["resource_escrow"] = {"status": "loaded", "data": {}}
        self.assertIn("unexpected new root", upgrade.compare_inventories(self.old, self.new)[0])

    def test_empty_progression_is_not_a_fabricated_event(self):
        self.new["roots"]["classic_progression"] = {"status": "loaded", "data": {
            "schema_version": 1, "format_epoch": "v1.8.0-classic-progression",
            "first_moon_landing": {"occurred": 0}, "first_station_warp": {"occurred": 0}}}
        self.assertEqual([], upgrade.compare_inventories(self.old, self.new))
        self.new["roots"]["classic_progression"]["data"]["first_moon_landing"]["occurred"] = 1
        self.assertTrue(upgrade.compare_inventories(self.old, self.new))


class MigrationTests(unittest.TestCase):
    def test_exact_station_migration_preserves_old_fields(self):
        old = {"schema_version": 4, "format_epoch": "v1.5.0-station-warp", "reservations": [],
               "warp_energy": [], "stations": [{"schema_version": 2, "name": "home",
               "environment": {"solar_angle_milli_degrees": 90000}}]}
        expected = {"schema_version": 5, "format_epoch": "v1.8.0-station-first-warp", "reservations": [],
                    "warp_energy": [], "classic_first_warp": {"schema_version": 1, "state": "EMPTY"},
                    "orbital_clock": {"schema_version": 1, "logical_tick": 0},
                    "stations": [{"schema_version": 3, "name": "home",
                    "environment": {"solar_angle_milli_degrees": 90000}, "orbital_controls": {
                    "format_version": 1, "distance": 4, "phases": [90000, 0, 0], "rates": [0, 0, 0], "epoch": 0}}]}
        self.assertEqual(expected, upgrade.migrate_expected("stations", old))
        actual = copy.deepcopy(expected)
        actual["orbital_clock"]["logical_tick"] = 20
        self.assertEqual([], upgrade.compare_root("stations", expected, actual))
        actual["stations"][0]["name"] = "lost"
        self.assertTrue(upgrade.compare_root("stations", expected, actual))

    def test_exact_satellite_migration(self):
        old = {"schema_version": 3, "format_epoch": "v1.6.0-satellite-missions",
               "instances": [{"schema_version": 1, "source_mission": [1, 2, 3, 4], "remaining": 19}]}
        self.assertEqual({"schema_version": 4, "format_epoch": "v1.8.0-ground-surveys", "ground_surveys": [],
                          "instances": [{"schema_version": 2, "origin_kind": "survey_mission",
                          "origin_id": [1, 2, 3, 4], "remaining": 19}]},
                         upgrade.migrate_expected("satellite_missions", old))
        self.assertIn("source_mission", old["instances"][0])

    def test_journal_migration_does_not_fabricate_historical_landing(self):
        old = {"schema_version": 2, "format_epoch": "v0.9.0-beta", "transfers": [{"opaque": "retained"}]}
        actual = upgrade.migrate_expected("rocket_transfers", old)
        self.assertEqual(old["transfers"], actual["transfers"])
        self.assertEqual({"schema_version": 1, "state": "EMPTY"}, actual["classic_first_moon_landing"])

    def test_unknown_epoch_refused_not_silently_accepted(self):
        with self.assertRaisesRegex(fixture.server.SmokeError, "schema/epoch"):
            upgrade.migrate_expected("stations", {"schema_version": 4, "format_epoch": "unknown"})

    def test_monotonic_counter_cannot_regress_or_replace_schema(self):
        old = {"save_epoch": 10, "schema_version": 1}
        self.assertEqual([], upgrade.compare_root("endgame", old, {"save_epoch": 12, "schema_version": 1}))
        self.assertTrue(upgrade.compare_root("endgame", old, {"save_epoch": 9, "schema_version": 1}))
        self.assertTrue(upgrade.compare_root("endgame", old, {"save_epoch": 12, "schema_version": 2}))


class EvidenceTests(unittest.TestCase):
    def test_registry_dump_all_types(self):
        text = "\n".join(f"Registry Name: {kind}\n| 1 | advancedrocketrycommunity:test | object |\n"
                         for kind in fixture.REGISTRIES)
        result = fixture.registry_dump(text)
        self.assertEqual(set(fixture.REGISTRIES), set(result))
        self.assertEqual("advancedrocketrycommunity:test", fixture.layout(result)["items"][0]["id"])
        with self.assertRaisesRegex(fixture.server.SmokeError, "Incomplete"):
            fixture.registry_dump(text.split("Registry Name: minecraft:block_entity_type")[0])

    def test_migration_log_and_restarts(self):
        first = "Beta world data check complete: managed=6, migrated=3, backup=test\nARCE_SATELLITE_SCHEMA4_UPGRADE source_schema=3"
        self.assertEqual(3, upgrade.check_log(first, first=True)["migrated"])
        again = "Beta world data check complete: managed=6, migrated=0, backup=none"
        self.assertEqual(0, upgrade.check_log(again, first=False)["migrated"])
        for invalid in (first, again + "\n[Server thread/ERROR] failure",
                        again + "\nARCE_MANAGED_ROOT_UNREADABLE", again + "\n" + again):
            with self.assertRaises(fixture.server.SmokeError):
                upgrade.check_log(invalid, first=False)


if __name__ == "__main__":
    unittest.main()
