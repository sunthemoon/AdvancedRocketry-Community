import copy
import sys
from pathlib import Path
import unittest
import uuid

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_w159_rejoin_cuts as runner


class RejoinCutRunnerTests(unittest.TestCase):
    @staticmethod
    def fixture():
        player = [(uuid.UUID(runner.PLAYER).int >> n) & 0xFFFFFFFF for n in (96, 64, 32, 0)]
        release, generation = [1, 2, 3, 4], [5, 6, 7, 8]
        return {"player": {"UUID": player, "Health": 1.0, "DeathTime": 0, "Dimension": "minecraft:overworld",
                           "Pos": [2.5, 70.0, 3.5], "Motion": [0.0, 0.0, 0.0], "FallDistance": 0.0,
                           "ForgeData": {"arce_passenger_rejoin": {"schema": 1, "release": release, "player": player, "generation": generation}}},
                "sidecar": {"next_sequence": 1, "batches": [{"release": release, "dimension": "minecraft:overworld"}],
                            "entries": [{"player": player, "release": release, "placement": "PENDING", "seat": "VERIFIED",
                                         "attempt": {"generation": generation, "target": "PAD", "x": 2.5, "y": 70.0, "z": 3.5}}]},
                "rockets": [{"entity": {"UUID": [1, 1, 1, 1], "RocketEntityData": {"snapshot": {"fixed": 1},
                             "flight_data": {"fuel": {"amount": 1000, "debits": []}, "passengers": {"assignments": []}}}}}]}

    def test_matching_read_requires_exact_marker_position_liveness_and_mount_absence(self):
        disk = self.fixture(); baseline = runner.verify(disk, "H1", "C10", "seed", "C10", None, None)
        for key, value in (("Pos", [2.50001, 70, 3.5]), ("Health", 0), ("RootVehicle", {}),
                           ("Dimension", "minecraft:the_nether"), ("Motion", [0, 1, 0]), ("FallDistance", 1)):
            bad = copy.deepcopy(disk); bad["player"][key] = value
            with self.assertRaises(runner.smoke.SmokeError): runner.verify(bad, "H1", "C10", "recover", "C10", baseline, disk)
        bad = copy.deepcopy(disk); bad["player"]["ForgeData"]["arce_passenger_rejoin"]["generation"] = [0] * 4
        with self.assertRaises(runner.smoke.SmokeError): runner.verify(bad, "H1", "C10", "seed", "C10", baseline, None)

    def test_pre_save_and_post_p2_are_different_oracles(self):
        disk = self.fixture(); disk["player"]["ForgeData"].clear(); disk["player"]["Pos"][1] = 60
        baseline = runner.verify(disk, "H1", "C8", "seed", "C8", None, None)
        with self.assertRaises(runner.smoke.SmokeError): runner.verify(disk, "H1", "C11", "seed", "C11", baseline, None)
        disk = self.fixture(); disk["sidecar"]["entries"].clear()
        runner.verify(disk, "H1", "C11", "seed", "C11", baseline, None)
        with self.assertRaises(runner.smoke.SmokeError): runner.verify(disk, "H1", "C11", "check", "", baseline, None)
        disk["player"]["ForgeData"].clear(); runner.verify(disk, "H1", "C11", "check", "", baseline, None)

    def test_resource_identity_is_not_reduced_to_fuel_amount(self):
        disk = self.fixture(); baseline = runner.verify(disk, "H1", "C10", "seed", "C10", None, None)
        disk["rockets"][0]["entity"]["RocketEntityData"]["flight_data"]["fuel"]["debits"].append("new")
        # The oracle keeps its baseline detached from the later inspection.
        baseline["fuel"] = {"amount": 1000, "debits": []}
        with self.assertRaises(runner.smoke.SmokeError): runner.verify(disk, "H1", "C10", "recover", "C10", baseline, None)


if __name__ == "__main__":
    unittest.main()
