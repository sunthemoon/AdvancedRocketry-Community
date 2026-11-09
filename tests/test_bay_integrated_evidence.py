"""Evidence preservation and case-composition regressions for the C1 campaign."""
from pathlib import Path
import sys
import tempfile
import unittest
from copy import deepcopy

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_bay_forced_stop as bay
import bay_integrated_evidence as evidence


class EvidenceTests(unittest.TestCase):
    def test_snapshot_keeps_authority_and_entities_not_server_or_generated_terrain(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            world = root / "world"
            for name in ("data/receipts.dat", "data/registry.dat.arce-pending", "entities/r.0.0.mca",
                         "dimensions/example/moon/entities/r.0.0.mca", "playerdata/rider.dat",
                         "region/r.0.0.mca", "session.lock"):
                path = world / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(name.encode())
            result = evidence.snapshot_world(world, root / "evidence")
            self.assertEqual(5, len(result["files"]))
            self.assertFalse((root / "evidence/region").exists())
            self.assertFalse((root / "evidence/session.lock").exists())
            self.assertEqual(64, len(result["files"][0]["sha256"]))
            self.assertEqual(b"data/receipts.dat", (root / "evidence/data/receipts.dat").read_bytes())

    def test_snapshot_refuses_overwrite_and_oversize_without_partial_copy(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            path = root / "world/data/a.dat"
            path.parent.mkdir(parents=True)
            path.write_bytes(b"123")
            with self.assertRaises(ValueError):
                evidence.snapshot_world(root / "world", root / "evidence", max_bytes=2)
            self.assertFalse((root / "evidence").exists())
            evidence.snapshot_world(root / "world", root / "evidence")
            with self.assertRaises(FileExistsError):
                evidence.snapshot_world(root / "world", root / "evidence")

    def test_missing_ticks_are_not_estimated_from_wall_clock(self):
        rows = evidence.observed_ticks(["[12:00:00] ARCE_BAY_CLAIM state=D6 reason=PROOF",
                                        "[12:00:01] ARCE_BAY_CLAIM state=RETIRE reason=NONE"])
        self.assertEqual("NOT_OBSERVABLE", rows["d_step_ticks"])
        self.assertEqual([], rows["tick_markers"])

    def test_tick_markers_preserve_raw_lines_and_game_clock_domains(self):
        line = "[12:00:01] ARCE_RELEASE_BAY_EXIT_STATE tick=123 phase=OFFER"
        rows = evidence.observed_ticks([line])
        self.assertEqual([dict(ticks=[123], line=line)], rows["tick_markers"])
        self.assertEqual("NOT_OBSERVABLE", rows["d_step_ticks"])

    def test_new_cases_use_existing_cut_controls_and_two_restarts(self):
        expected = {"W325_B3_STAGED_PRESENT", "W325_B3_STAGED_ABSENT", "W325_B4_LIVE_X_PRESENT_S",
                    "W325_B4_LIVE_X_ABSENT_S", "W325_B8_STAGED_OFFLINE", "W325_B8_MOVED_OFFLINE"}
        self.assertEqual(expected, {key for key in bay.CASES if key.startswith("W325_")})
        for key in expected:
            self.assertEqual(2, len(bay.CASES[key]["restarts"]))
        for key in ("W325_B4_LIVE_X_PRESENT_S", "W325_B4_LIVE_X_ABSENT_S"):
            self.assertTrue(bay.CASES[key]["survey"])
            self.assertFalse(bay.CASES[key].get("decommission", False))
            self.assertEqual(2, bay.CASES[key]["owned"])
        for key in ("W325_B8_STAGED_OFFLINE", "W325_B8_MOVED_OFFLINE"):
            self.assertTrue(bay.CASES[key]["offline"])

    def test_live_survey_cases_require_two_registrations_and_both_consumed_packages(self):
        fixture = dict(logical="l", data="data", solar="solar", packages="a+b")
        status = dict(claim="NONE", transfer="COMMITTED", state="LANDED", y="70", packages="c+d",
                      consumed=["op-data", "op-survey"], receipts=[], owned=2)
        for case in ("W325_B4_LIVE_X_PRESENT_S", "W325_B4_LIVE_X_ABSENT_S"):
            bay.check_settled(case, 1, status, fixture)
            for changes in (dict(owned=1), dict(consumed=["op-data", None]), dict(packages="c+b")):
                bad = deepcopy(status)
                bad.update(changes)
                with self.assertRaises(bay.smoke.SmokeError):
                    bay.check_settled(case, 1, bad, fixture)


if __name__ == "__main__":
    unittest.main()
