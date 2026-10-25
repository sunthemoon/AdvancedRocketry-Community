import copy
import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_w158_release_cuts as runner


class DivergedReleaseTests(unittest.TestCase):
    def test_h1_newcomer_survives_and_only_the_absent_old_riders_are_released(self):
        def player(n): return [22544384, 16384, -2147483648, n]
        def flight(ids): return {"fuel": {"amount": 1000, "debits": []}, "passengers": {"assignments": [
            {"passenger_id": player(n), "seat_index": i} for i, n in enumerate(ids)]}}
        seed = {"rockets": [{"entity": {"UUID": player(9), "RocketEntityData": {"snapshot": {"items": [42]}, "flight_data": flight([3, 2])}}}],
                "journal": {"transfers": [{"source_flight": flight([1, 2]), "destination_flight": flight([1, 2])}], "outbox": {"state": "EMPTY"}},
                "sidecar": {"next_sequence": 1, "batches": [{"released": [player(1), player(2)], "slots": [
                    {"level": "minecraft:overworld", "x": 4, "z": 4}, {"level": "advancedrocketrycommunity:moon", "x": 4, "z": 4}]}],
                    "entries": [{"player": player(n), "seat_index": n - 1, "seat": "PENDING", "placement": placement}
                        for n, placement in [(1, "NOT_REQUIRED"), (2, "PENDING")]]}}
        baseline = runner.verify_diverged(seed, "seed", None)
        recovered = copy.deepcopy(seed)
        recovered["rockets"][0]["entity"]["RocketEntityData"]["flight_data"] = flight([3])
        recovered["journal"]["transfers"] = []
        recovered["sidecar"]["entries"] = [dict(seed["sidecar"]["entries"][1], seat="VERIFIED")]
        runner.verify_diverged(recovered, "recover", baseline)
        for change in ("newcomer", "entry", "outbox", "fuel", "slots"):
            altered = copy.deepcopy(recovered)
            if change == "newcomer": altered["rockets"][0]["entity"]["RocketEntityData"]["flight_data"]["passengers"]["assignments"][0]["passenger_id"] = player(1)
            elif change == "entry": altered["sidecar"]["entries"][0]["player"] = player(3)
            elif change == "outbox": altered["journal"]["outbox"]["state"] = "DELIVERED"
            elif change == "fuel": altered["rockets"][0]["entity"]["RocketEntityData"]["flight_data"]["fuel"]["amount"] -= 1
            else: altered["sidecar"]["batches"][0]["slots"].pop()
            with self.assertRaises(runner.smoke.SmokeError, msg=change): runner.verify_diverged(altered, "check", baseline)
        self.assertIn(("H1", "C24"), runner.CASES)
        self.assertEqual("C3", runner.stop_boundary("C24", "seed", ""))
