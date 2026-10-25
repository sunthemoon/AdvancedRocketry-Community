import copy
import unittest
from unittest.mock import patch
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

    def test_pressure_retains_every_obligation_and_every_installed_player(self):
        for cut in ("C28", "C30"):
            disk = fixture(); disk["journal"]["transfers"] = []
            ids = [rejoin.FIRST, rejoin.PLAYER, rejoin.PLAYER] if cut == "C28" else [rejoin.FIRST, rejoin.PLAYER] + [str(__import__('uuid').UUID(int=i+1000)) for i in range(129)]
            disk["sidecar"].update(next_sequence=2 if cut == "C28" else 10,
                                   entries=[dict(player=words(i), seat="VERIFIED", placement="PENDING") for i in ids])
            for identity in set(ids) - {rejoin.FIRST}:
                disk["players"][identity] = copy.deepcopy(disk["players"][rejoin.PLAYER]); disk["players"][identity]["UUID"] = words(identity)
            baseline = rows.verify(disk, cut, "seed")
            rows.verify(disk, cut, "recover", baseline, disk)
            for mutate in (lambda d: d["sidecar"]["entries"].pop(), lambda d: d["sidecar"]["entries"][0].update(attempt={}),
                           lambda d: d["players"][ids[-1]].update(Health=0), lambda d: d["players"][ids[-1]].update(Pos=[99, 99, 99])):
                bad = copy.deepcopy(disk); mutate(bad)
                with self.assertRaises(rows.smoke.SmokeError): rows.verify(bad, cut, "recover", baseline, disk)

    def test_disk_ceiling_log_is_exact_scoped_and_requires_the_native_observations(self):
        prefix = "[12:00:00] [Server thread/{}] [io.gi.su.ad.AdvancedRocketryCommunity/]: "
        warning = prefix.format("WARN") + f"ARCE_REJOIN_PLAYER_LIMIT player={rejoin.PLAYER} kind=OVERSIZED size=68157440 ceiling=67108864"
        lines = [warning, prefix.format("INFO") + "ARCE_W160F_DISK_CEILING bytes=68157440 ceiling=67108864 pending=true",
                 prefix.format("INFO") + "ARCE_W160F_EXPANDED mode=seed payload_bytes=3145728 entries=0"]
        with patch.object(rows.release, "audit_log", return_value={}):
            rows.audit_log(lines, "C33", "seed")
            for bad in (lines[1:], lines + [warning], lines[:-1], [s.replace("68157440", "67108864") for s in lines]):
                with self.assertRaises(rows.smoke.SmokeError): rows.audit_log(bad, "C33", "seed")
            with self.assertRaises(rows.smoke.SmokeError): rows.audit_log(lines, "C33", "recover")


if __name__ == "__main__": unittest.main()
