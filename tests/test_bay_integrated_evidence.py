"""Evidence preservation and case-composition regressions for the C1 campaign."""
from pathlib import Path
import sys
import tempfile
import unittest
from copy import deepcopy
from unittest.mock import Mock

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
                    "W325_B4_LIVE_X_ABSENT_S", "W325_B8_STAGED_OFFLINE", "W325_B8_MOVED_OFFLINE",
                    "W325_B3_STAGED_ONLINE", "W325_B3_MOVED_ONLINE", "W325_B8_STAGED_ONLINE", "W325_B8_MOVED_ONLINE"}
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

    def test_online_fixture_uses_the_existing_survival_rider_command_and_checks_boarding(self):
        run = Mock()
        run.args.flight_timeout = 120
        fixture = "ARCE_RELEASE_BAY_FIXTURE logical=l transfer=t rocket=r data=x solar=y launch=SUCCESS packages=a+b"
        run.process.lines = [fixture]
        rider = f"ARCE_RELEASE_BAY_EXIT_FIXTURE rider={bay.ONLINE_RIDER} name=W254Rider mounted=true seat=0 mode=survival"
        run.send.side_effect = ["Marked chunk", rider]
        result = bay.launch_fixture(run, online=True)
        self.assertEqual(bay.ONLINE_RIDER, result["rider"])
        self.assertIn("exit fixture", run.send.call_args_list[-1].args[0])
        run.send.side_effect = ["Marked chunk", rider.replace("mounted=true", "mounted=false")]
        with self.assertRaises(bay.smoke.SmokeError):
            bay.launch_fixture(run, online=True)

    def test_online_state_requires_a_live_rider_and_retained_rocket_seat(self):
        good = "ARCE_RELEASE_BAY_EXIT_STATE rider=online mounted=true seat=0 rocketSeat=0 health=20.0"
        run = Mock()
        run.send.return_value = good
        self.assertEqual("online", bay.online_state(run, "l", at_cut=True)["rider"])
        for before, after in (("online", "offline"), ("rocketSeat=0", "rocketSeat=-1"), ("mounted=true", "mounted=false")):
            run.send.return_value = good.replace(before, after)
            with self.assertRaises(bay.smoke.SmokeError):
                bay.online_state(run, "l", at_cut=True)


if __name__ == "__main__":
    unittest.main()
