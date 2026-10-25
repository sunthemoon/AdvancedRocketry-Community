import copy
import sys
from pathlib import Path
import unittest
from unittest.mock import patch
import uuid

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_w159_rejoin_cuts as runner


class RejoinCutRunnerTests(unittest.TestCase):
    def test_temporary_write_cut_requires_old_file_and_one_exact_warning(self):
        self.assertIn(("H1", "C21"), runner.CASES)
        disk = self.fixture(); disk["player"]["ForgeData"].clear(); disk["player"]["Pos"][1] = 60
        runner.verify(disk, "H1", "C21", "seed", "C21", None, None)
        with self.assertRaises(runner.smoke.SmokeError):
            runner.verify(self.fixture(), "H1", "C21", "seed", "C21", None, None)
        line = "[12:00:00] [Server thread/WARN] [minecraft/PlayerDataStorage]: Failed to save player data for W159Passenger"
        with patch.object(runner.release, "audit_log", return_value={}):
            self.assertEqual(1, runner.audit_log([line], "H1", "C21", "seed")["temporary_failure_observed"])
            for bad in ([], [line, line], [line + " unexpected"]):
                with self.assertRaises(runner.smoke.SmokeError): runner.audit_log(bad, "H1", "C21", "seed")
            with self.assertRaises(runner.smoke.SmokeError): runner.audit_log([line], "H1", "C21", "recover")

    def test_shutdown_clean_is_one_exact_parsed_message_only_in_c20_seed(self):
        line = "[12:00:00] [Server thread/INFO] [io.gi.su.ad.AdvancedRocketryCommunity/]: ARCE_W159_SHUTDOWN_CLEAN threads=0 tickets=0"
        with patch.object(runner.release, "audit_log", return_value={}):
            with self.assertRaises(runner.smoke.SmokeError):
                runner.audit_log([], "H1", "C20", "seed")
            for hold in ("H1", "H2"):
                report = runner.audit_log([line], hold, "C20", "seed")
                self.assertEqual(1, report["shutdown_clean_observed"])
                for bad in ([], [line, line], [line.replace("threads=0", "threads=1")],
                            [line.replace("tickets=0", "tickets=1")],
                            [line.replace("Server thread", "Other thread")],
                            [line.replace("INFO", "WARN")], [line + " trailing"],
                            [line.replace("io.gi.su.ad.AdvancedRocketryCommunity/", "minecraft/Util")],
                            [line, "unexpected continuation"]):
                    with self.assertRaises(runner.smoke.SmokeError):
                        runner.audit_log(bad, hold, "C20", "seed")
            for hold, cut, mode in (("H1", "C20", "recover"), ("H2", "C20", "check"),
                                    ("H1", "C10", "seed"), ("H3", "C20", "seed")):
                with self.assertRaises(runner.smoke.SmokeError):
                    runner.audit_log([line], hold, cut, mode)
                self.assertEqual(0, runner.audit_log([], hold, cut, mode)["shutdown_clean_observed"])

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

    def test_failed_death_lease_keeps_pending_dead_body_until_fresh_recovery(self):
        seed = self.fixture(); seed["player"]["Health"] = 0
        runner.verify(seed, "H1", "C18", "seed", "C18", None, None)
        bad = copy.deepcopy(seed); bad["sidecar"]["entries"].clear()
        with self.assertRaises(runner.smoke.SmokeError): runner.verify(bad, "H1", "C18", "seed", "C18", None, None)
        recovered = self.fixture()
        with self.assertRaises(runner.smoke.SmokeError): runner.verify(recovered, "H1", "C18", "recover", "C10", None, seed)
        recovered["sidecar"]["entries"][0]["attempt"]["generation"] = [9, 8, 7, 6]
        recovered["player"]["ForgeData"]["arce_passenger_rejoin"]["generation"] = [9, 8, 7, 6]
        runner.verify(recovered, "H1", "C18", "recover", "C10", None, seed)

    def test_failed_safety_lease_keeps_pending_until_fresh_safe_candidate(self):
        seed = self.fixture(); runner.verify(seed, "H1", "C19", "seed", "C19", None, None)
        bad = copy.deepcopy(seed); bad["sidecar"]["entries"].clear()
        with self.assertRaises(runner.smoke.SmokeError): runner.verify(bad, "H1", "C19", "seed", "C19", None, None)
        recovered = self.fixture()
        with self.assertRaises(runner.smoke.SmokeError): runner.verify(recovered, "H1", "C19", "recover", "C10", None, seed)
        recovered["sidecar"]["entries"][0]["attempt"].update(generation=[9, 8, 7, 6], x=4.5)
        recovered["player"]["ForgeData"]["arce_passenger_rejoin"]["generation"] = [9, 8, 7, 6]
        recovered["player"]["Pos"][0] = 4.5
        runner.verify(recovered, "H1", "C19", "recover", "C10", None, seed)

    def test_inflight_shutdown_retains_exact_pending_installed_player(self):
        disk = self.fixture(); runner.verify(disk, "H1", "C20", "seed", "C20", None, None)
        for mutate in (lambda d: d["sidecar"]["entries"].clear(), lambda d: d["player"]["ForgeData"].clear(),
                       lambda d: d["player"].update(RootVehicle={})):
            bad = copy.deepcopy(disk); mutate(bad)
            with self.assertRaises(runner.smoke.SmokeError): runner.verify(bad, "H1", "C20", "seed", "C20", None, None)


if __name__ == "__main__":
    unittest.main()
