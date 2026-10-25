import copy
import sys
from pathlib import Path
import unittest
from unittest.mock import patch
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

    def test_other_absent_players_placement_obligations_are_conserved(self):
        disk = self.fixture(); other = copy.deepcopy(disk["sidecar"]["entries"][0]); other["player"] = [0, 0, 0, 1]
        other.pop("attempt"); disk["sidecar"]["entries"].append(other)
        baseline = runner.verify(disk, "H1", "C10", "seed", "C10", None, None)
        disk["sidecar"]["entries"].pop()
        with self.assertRaises(runner.smoke.SmokeError): runner.verify(disk, "H1", "C10", "recover", "C10", baseline, None)

    def test_c9_log_allowance_is_exact_and_preserves_unrelated_entries(self):
        path = "./world-W158-H1-C9/playerdata/" + runner.PLAYER + ".dat_old"
        def header(level, text): return "[12:00:00] [Server thread/" + level + "] [minecraft/Util]: " + text
        lines = []
        for retry in range(10):
            lines += [header("WARN", "Failed to delete"), "java.nio.file.DirectoryNotEmptyException: " + path,
                      "\tat net.minecraft.world.level.storage.PlayerDataStorage.save(PlayerDataStorage.java:33)",
                      "\tat io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinPlacement.tick(RocketRejoinPlacement.java:62)",
                      header("WARN", "Failed to execute delete old " + path),
                      header("ERROR", "Failed to create backup " + path + ", retrying " + str(retry) + "/10")]
        lines += [header("ERROR", "Failed to create backup " + path + ", aborting, progress might be lost")]
        unrelated = header("INFO", "Unrelated retained entry")
        with patch.object(runner.release, "audit_log", return_value={}) as base:
            report = runner.audit_log(lines + [unrelated], "H1", "C9", "seed")
            self.assertEqual(report["c9_expected"], report["c9_observed"])
            self.assertEqual(base.call_args.args[0], [unrelated])
            for bad in (lines[:-1], lines + [lines[-1]], [x.replace("retrying 1/10", "retrying 0/10") for x in lines],
                        [x.replace("DirectoryNotEmptyException", "IOException") for x in lines],
                        lines[:3] + ["Caused by: java.io.IOException: unrelated"] + lines[3:]):
                with self.assertRaises(runner.smoke.SmokeError): runner.audit_log(bad, "H1", "C9", "seed")
            with self.assertRaises(runner.smoke.SmokeError): runner.audit_log(lines, "H1", "C9", "recover")


if __name__ == "__main__":
    unittest.main()
