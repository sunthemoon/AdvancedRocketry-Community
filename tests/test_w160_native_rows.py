import copy
import unittest
from test_w160_two_rider_oracle import fixture, words
import run_w160_native_rows as rows
import run_w159_rejoin_cuts as rejoin


class NativeRowsTests(unittest.TestCase):
    def test_terminal_rows_require_both_files_resources_and_exact_seat_census(self):
        for cut in ("C12", "C25", "C26", "C27"):
            disk = fixture(); disk["journal"]["transfers"] = []
            if cut == "C25": disk.update(rockets=[], restored_blocks=[{"Name": "minecraft:iron_block"}]*2)
            if cut == "C27":
                disk["rockets"][0]["entity"]["RocketEntityData"]["flight_data"]["passengers"]["assignments"] = [
                    dict(passenger_id=words(identity), seat_index=index) for index, identity in enumerate((rejoin.FIRST, rejoin.PLAYER))]
            baseline = rows.verify(disk, cut, "recover")
            rows.verify(disk, cut, "check", baseline, disk)
            for identity in ((rejoin.PLAYER,) if cut == "C12" else (rejoin.FIRST, rejoin.PLAYER)):
                for key, value in (("Health", 0), ("marker", {}), ("RootVehicle", {}), ("Pos", [0, 0, 0])):
                    with self.subTest(cut=cut, player=identity, key=key):
                        bad = copy.deepcopy(disk); bad["players"][identity][key] = value
                        with self.assertRaises(rows.smoke.SmokeError): rows.verify(bad, cut, "check", baseline, disk)
            bad = copy.deepcopy(disk)
            if cut == "C25": bad["restored_blocks"][0] = {"Name": "minecraft:air"}
            else: bad["rockets"][0]["entity"]["RocketEntityData"]["flight_data"]["fuel"]["debits"].append("new")
            with self.assertRaises(rows.smoke.SmokeError): rows.verify(bad, cut, "check", baseline, disk)

    def test_external_rollback_is_only_the_older_roster_with_equal_resources(self):
        before = fixture(); after = copy.deepcopy(before)
        for disk, identities in ((before, [(rejoin.PLAYER, 1)]), (after, [(rejoin.FIRST, 0), (rejoin.PLAYER, 1)])):
            disk["rockets"][0]["entity"]["RocketEntityData"]["flight_data"]["passengers"]["assignments"] = [
                dict(passenger_id=words(identity), seat_index=index) for identity, index in identities]
        rows.verify_rollback(before, after)
        for key in ("sidecar", "journal", "players"):
            bad = copy.deepcopy(after); bad[key] = {}
            with self.assertRaises(rows.smoke.SmokeError): rows.verify_rollback(before, bad)
        bad = copy.deepcopy(after); bad["rockets"][0]["entity"]["RocketEntityData"]["flight_data"]["fuel"]["amount"] = 999
        with self.assertRaises(rows.smoke.SmokeError): rows.verify_rollback(before, bad)


if __name__ == "__main__": unittest.main()
