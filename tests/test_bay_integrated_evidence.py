"""Evidence preservation and case-composition regressions for the C1 campaign."""
from pathlib import Path
import sys
import tempfile
import unittest
from copy import deepcopy
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_bay_forced_stop as bay
import bay_integrated_evidence as evidence
import loader_cut_nbt as nbt
import run_v180_bay_exit_cuts as exit_runner


class EvidenceTests(unittest.TestCase):
    def test_online_copy_disk_requires_the_online_rider_on_every_saved_image(self):
        census = dict(rows=["copy:minecraft:overworld:hash:0:0:NONE:0:false"])
        entity = dict(UUID="copy", RocketEntityData=dict(assembly_transaction_id="logical",
            snapshot=dict(content_hash="hash"), resource_escrow=dict(resource_epoch=0),
            flight_data=dict(passengers=dict(assignments=[dict(passenger_id=bay.ONLINE_RIDER)]))))
        root = dict(Position=[0, 0], Entities=[entity])
        with patch.object(bay, "chunk_record", return_value=root):
            self.assertEqual(1, len(bay.inspect_copy_disk(Path("fixture"), "logical", census,
                                                        passenger=bay.ONLINE_RIDER)))
        for seats in ([], [dict(passenger_id="00000000-0000-4000-8000-000000185001")]):
            entity["RocketEntityData"]["flight_data"]["passengers"]["assignments"] = seats
            with patch.object(bay, "chunk_record", return_value=root), self.assertRaises(bay.smoke.SmokeError):
                bay.inspect_copy_disk(Path("fixture"), "logical", census, passenger=bay.ONLINE_RIDER)

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
                    "W325_B3_STAGED_ONLINE", "W325_B3_MOVED_ONLINE", "W325_B8_STAGED_ONLINE", "W325_B8_MOVED_ONLINE",
                    "W325_OWNER_FULL", "W325_OTHER_OWNER_ROOM", "W325_B3_STAGED_ONLINE_ABSENT",
                    "W325_B3_MOVED_ONLINE_ABSENT", "W325_RETAINED_GLOBAL", "W325_RETAINED_OWNER", "W325_REPAIR_FULL",
                    "W325_RELEASED_FULL", "W325_REPAIR_FAIR"}
        expected.update(("W325_D4_FAILED", "W325_D7_FAILED"))
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

    def test_global_capacity_fixture_preserves_sixty_four_slots_and_all_lower_caps(self):
        with tempfile.TemporaryDirectory() as temp:
            world = Path(temp) / "world"
            summary = evidence.seed_capacity_world(world, 64)
            self.assertEqual(64, summary["count"])
            _, root = nbt.read_gzip(world / "data" / bay.RECEIPTS)
            payload = nbt.compound(root, "data")
            rows = nbt.plain(nbt.compound(payload, "receipts"))
            self.assertEqual(64, len(rows))
            self.assertEqual(64, nbt.compound(payload, "issued_through").value)
            from collections import Counter
            owners = Counter(tuple(row["owner"]) for row in rows)
            rockets = Counter(tuple(row["rocket"]) for row in rows)
            self.assertEqual([16] * 4, sorted(owners.values()))
            self.assertEqual([8] * 8, sorted(rockets.values()))
            self.assertEqual(64, len({tuple(row["op"]) for row in rows}))
            self.assertEqual(64, len({tuple(row["satellite"]) for row in rows}))
            with self.assertRaises(FileExistsError):
                evidence.seed_capacity_world(world, 64)

    def test_owner_fixture_is_explicit_and_bounds_do_not_weaken_global_case(self):
        with tempfile.TemporaryDirectory() as temp:
            import uuid
            owner = "00000000-0000-4000-8000-000000128d01"
            world = Path(temp) / "world"
            evidence.seed_capacity_world(world, 16, owner)
            _, root = nbt.read_gzip(world / "data" / bay.RECEIPTS)
            rows = nbt.compound(root, "data", "receipts").value[1]
            self.assertTrue(all(nbt.compound(row, "owner") == nbt.uuid_tag(uuid.UUID(owner)) for row in rows))
            for count in (0, 65):
                with self.assertRaises(ValueError):
                    evidence.seed_capacity_world(Path(temp) / f"bad{count}", count)
            self.assertEqual(64, bay.CASES["B11_FULL"]["fill"])
            self.assertEqual(56, bay.CASES["B17_FAIR"]["fill"])

    def test_exit_fixture_initial_login_is_in_the_same_chunk_as_the_vertical_descent(self):
        run = Mock()
        run.args.flight_timeout = 120
        fixture = "ARCE_RELEASE_BAY_FIXTURE logical=l transfer=t rocket=r data=x solar=y launch=SUCCESS packages=a+b"
        run.process.lines = [fixture]
        rider = f"ARCE_RELEASE_BAY_EXIT_FIXTURE rider={bay.ONLINE_RIDER} name=W254Rider mounted=true seat=0 mode=survival"
        run.send.side_effect = ["Marked chunk", "Set the world spawn point to 8, 2, 8", "spawnRadius is now set to 0",
                                "ARCE_RELEASE_SRW_ARMED", rider]
        exit_runner.launch(run)
        commands = [call.args[0] for call in run.send.call_args_list]
        self.assertEqual("setworldspawn 8 2 8", commands[1])
        self.assertEqual("gamerule spawnRadius 0", commands[2])
        self.assertIn("exit fixture", commands[-1])

    def test_retained_fixtures_keep_the_independent_global_and_owner_limits(self):
        with tempfile.TemporaryDirectory() as temp:
            for count, owner in ((4096, None), (128, "00000000-0000-4000-8000-000000128d01")):
                world = Path(temp) / str(count)
                result = evidence.seed_capacity_world(world, count, owner, phase="REJECTED")
                self.assertEqual(count, result["count"])
                self.assertEqual(0, result["pending"])
                _, root = nbt.read_gzip(world / "data" / bay.RECEIPTS)
                rows = nbt.compound(root, "data", "receipts").value[1]
                self.assertEqual(count, len(rows))
                self.assertTrue(all(nbt.plain(nbt.compound(row, "phase")) == "REJECTED" for row in rows))
                self.assertLess(len(nbt.write(b"", root)), 4 * 1024**2)
            with self.assertRaises(ValueError):
                evidence.seed_capacity_world(Path(temp) / "bad", 4097, phase="REJECTED")

    def test_repair_and_released_fixtures_preserve_pending_charges(self):
        with tempfile.TemporaryDirectory() as temp:
            for released in (False, True):
                world = Path(temp) / str(released)
                result = evidence.seed_capacity_world(world, 64, phase="REPAIR", released=released)
                self.assertEqual(64, result["pending"])
                _, root = nbt.read_gzip(world / "data" / bay.RECEIPTS)
                for row in nbt.compound(root, "data", "receipts").value[1]:
                    self.assertEqual("REPAIR", nbt.plain(nbt.compound(row, "phase")))
                    self.assertEqual(int(released), nbt.compound(row, "released").value)

    def test_online_copy_census_does_not_claim_the_offline_fixture_uuid_is_assigned(self):
        line = "ARCE_RELEASE_BAY_CENSUS logical=l count=1 transfer=COMMITTED assigned=false rows=[u:minecraft:overworld:hash:0:0:NONE:0:false]"
        result = bay.check_copy_census(line, "l", 1, assigned=False)
        self.assertFalse(result["assigned"])
        with self.assertRaises(bay.smoke.SmokeError):
            bay.check_copy_census(line, "l", 1)

    def test_definite_group_write_failures_keep_exact_authority_and_only_retry_later(self):
        fixture = dict(packages="a+b")
        status = dict(transfer="PREPARED", state="TRANSIT", locked="true", packages="a+b",
                      consumed=[None, None], owned=1)
        bay.check_failed_group_write(status, fixture, "D4", "same", "same")
        consumed = dict(status, packages="c+b", consumed=["op", None])
        bay.check_failed_group_write(consumed, fixture, "D7", "same", "same")
        for changes in (dict(transfer="COMMITTED"), dict(locked="false"), dict(owned=2), dict(packages="c+b")):
            with self.assertRaises(bay.smoke.SmokeError):
                bay.check_failed_group_write(dict(status, **changes), fixture, "D4", "same", "same")
        with self.assertRaises(bay.smoke.SmokeError):
            bay.check_failed_group_write(status, fixture, "D4", "same", "changed")


if __name__ == "__main__":
    unittest.main()
