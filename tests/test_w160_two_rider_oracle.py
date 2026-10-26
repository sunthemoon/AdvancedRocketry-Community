"""Mutations of C22's complete released-seat set at every persisted boundary."""
import copy
from pathlib import Path
import sys
import unittest
import uuid

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_w159_rejoin_cuts as runner


def words(identity):
    return [(uuid.UUID(identity).int >> n) & 0xFFFFFFFF for n in (96, 64, 32, 0)]


def fixture():
    flight = {"fuel": {"amount": 1000, "debits": []}, "passengers": {"assignments": []}}
    players = {identity: dict(present=True, UUID=words(identity), Health=1, DeathTime=0,
                             Dimension="minecraft:overworld", Pos=[2.5, 70, 3.5], RootVehicle=None, marker=None)
               for identity in (runner.FIRST, runner.PLAYER)}
    player = {k: v for k, v in players[runner.PLAYER].items() if k not in ("RootVehicle", "marker", "present")}
    return dict(player=player, players=players, sidecar=dict(next_sequence=1, entries=[], batches=[]),
                rockets=[dict(entity=dict(UUID=[1, 1, 1, 1], RocketEntityData=dict(snapshot={}, flight_data=flight)))],
                journal=dict(transfers=[dict(source_flight=copy.deepcopy(flight), destination_flight=copy.deepcopy(flight))]))


class TwoRiderOracleTests(unittest.TestCase):
    def test_each_released_rider_in_each_reconstructible_manifest_is_rejected_at_each_stage(self):
        seed = fixture()
        for mode in ("seed", "recover", "check"):
            boundary = "C22" if mode == "seed" else ""
            runner.verify(seed, "H1", "C22", mode, boundary, None, None if mode == "seed" else seed)
            for identity in (runner.FIRST, runner.PLAYER):
                for store in ("entity", "source_flight", "destination_flight"):
                    with self.subTest(mode=mode, rider=identity, store=store):
                        disk = copy.deepcopy(seed)
                        flight = (disk["rockets"][0]["entity"]["RocketEntityData"]["flight_data"] if store == "entity"
                                  else disk["journal"]["transfers"][0][store])
                        flight["passengers"]["assignments"].append(dict(passenger_id=words(identity), seat_index=0))
                        with self.assertRaises(runner.smoke.SmokeError):
                            runner.verify(disk, "H1", "C22", mode, boundary, None, None if mode == "seed" else seed)

    def test_census_checks_every_captured_copy_and_every_journal_record(self):
        for identity in (runner.FIRST, runner.PLAYER):
            for store in ("entity", "source_flight", "destination_flight"):
                with self.subTest(rider=identity, store=store):
                    disk = fixture()
                    disk["rockets"].append(copy.deepcopy(disk["rockets"][0]))
                    disk["journal"]["transfers"].append(copy.deepcopy(disk["journal"]["transfers"][0]))
                    flight = (disk["rockets"][1]["entity"]["RocketEntityData"]["flight_data"] if store == "entity"
                              else disk["journal"]["transfers"][1][store])
                    flight["passengers"]["assignments"].append(dict(passenger_id=words(identity), seat_index=1))
                    with self.assertRaises(runner.smoke.SmokeError):
                        runner.verify_two_riders(disk, None)

    def test_both_installed_players_and_restart_positions_remain_required(self):
        seed = fixture()
        for identity in (runner.FIRST, runner.PLAYER):
            for key, value in (("present", False), ("UUID", [0]*4), ("Health", 0), ("DeathTime", 1),
                               ("RootVehicle", {}), ("marker", {}), ("Dimension", "minecraft:the_end"),
                               ("Pos", [2.5, 71, 3.5])):
                with self.subTest(rider=identity, key=key):
                    disk = copy.deepcopy(seed); disk["players"][identity][key] = value
                    with self.assertRaises(runner.smoke.SmokeError):
                        runner.verify_two_riders(disk, seed)


if __name__ == "__main__":
    unittest.main()
