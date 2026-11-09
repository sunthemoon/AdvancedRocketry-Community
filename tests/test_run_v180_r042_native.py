"""W332: the R-042 native runner's seeds, parsers and oracles, without a server."""
from pathlib import Path
import re
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import bay_integrated_evidence as evidence  # noqa: E402
import run_dedicated_server_smoke as smoke  # noqa: E402
import run_v180_r042_native as r042  # noqa: E402

PREFIX = "[12:00:00] [Server thread/INFO] [advancedrocketrycommunity/]: "
ERROR = "[12:00:00] [Server thread/ERROR] [advancedrocketrycommunity/]: "
REFUSAL = (ERROR + "[ARCE-BETA-2000] Beta world data check blocked startup; type=BAY_RECEIPTS; "
           "reason=advancedrocketrycommunity_bay_receipts presence is unknown: its file status check failed, so its absence is "
           "not proven. " + r042.STORAGE_HINT + " If repair is still needed after that: Keep the world stopped.\n")


def attempt(status="ESCROW_FULL", admitted=False, unchanged=True, pending=(64, 64), retained=(64, 64), ledger=("absent", "absent")):
    return dict(status=status, admitted=admitted, unchanged=unchanged, pending=pending, retained=retained, ledger=ledger, logical="L")


def report(kind, amounts=None, epochs=(0, 0), markers=(0, 0), watching=False, locked=False, protected=False, refusal="NONE"):
    rocket, device = amounts or r042.BEFORE[kind]
    return dict(rocket=rocket, device=device, rocket_epoch=epochs[0], device_epoch=epochs[1], rocket_markers=markers[0],
                device_markers=markers[1], locked=locked, protected=protected, refusal=refusal, watching=watching, logical="L")


class SeedTests(unittest.TestCase):
    def test_the_server_pool_fills_64_slots_within_every_cap_and_every_row_is_pending(self):
        rows = r042.pool_rows("P_SERVER")
        self.assertEqual(64, len(rows))
        self.assertTrue(all(row["phase"] != "REJECTED" or row["released"] for row in rows))
        self.assertEqual({8}, {sum(1 for row in rows if row["rocket"] == rocket) for rocket in range(8)})
        self.assertEqual({16}, {sum(1 for row in rows if row["owner"] == owner) for owner in r042.SEED_OWNERS})
        self.assertNotIn(r042.OWNER, {row["owner"] for row in rows})
        self.assertNotIn(r042.P1, {row["owner"] for row in rows})
        kinds = {(row["phase"], row["reason"], row["released"]) for row in rows}
        self.assertEqual({("REPAIR", "REGISTRY_SIGNATURE_CONFLICT", True), ("REJECTED", "INTERRUPTED", True),
                          ("REPAIR", "TRANSFER_AUTHORITY_CONFLICT", False)}, kinds)

    def test_the_owner_pool_is_two_full_rockets_of_the_fixture_owner(self):
        rows = r042.pool_rows("P_OWNER")
        self.assertEqual(16, len(rows))
        self.assertEqual({r042.OWNER}, {row["owner"] for row in rows})
        self.assertEqual({0, 1}, {row["rocket"] for row in rows})
        self.assertEqual({True, False}, {row["released"] for row in rows})

    def test_the_cap_check_refuses_rows_outside_the_real_caps(self):
        base = dict(phase="REPAIR", reason="X", released=False)
        with self.assertRaises(smoke.SmokeError):
            r042.check_pool([dict(base, rocket=0, owner="o", phase="REJECTED")])
        with self.assertRaises(smoke.SmokeError):
            r042.check_pool([dict(base, rocket=n // 8, owner=f"o{n // 16}") for n in range(65)])
        with self.assertRaises(smoke.SmokeError):
            r042.check_pool([dict(base, rocket=n // 8, owner="o") for n in range(17)])
        with self.assertRaises(smoke.SmokeError):
            r042.check_pool([dict(base, rocket=0, owner=f"o{n}") for n in range(9)])

    def test_the_seed_writes_a_typed_receipt_file_that_reads_back_with_its_phases_and_never_overwrites(self):
        with tempfile.TemporaryDirectory() as directory:
            world = Path(directory)
            seed = r042.seed_pool(world, r042.pool_rows("P_SERVER"))
            receipts = evidence.bay_receipts(world / "data" / r042.FILES["receipts"])
            self.assertEqual(64, len(receipts))
            self.assertEqual([row["phase"] for row in seed["rows"]], [row["phase"] for row in receipts])
            self.assertEqual([row["released"] for row in seed["rows"]], [row["released"] for row in receipts])
            self.assertEqual(seed["satellites"], [row["satellite"] for row in receipts])
            with self.assertRaises(FileExistsError):
                r042.seed_pool(world, r042.pool_rows("P_OWNER"))


class ParserTests(unittest.TestCase):
    def test_the_probe_lines_parse(self):
        line = PREFIX + ("ARCE_RELEASE_R042_ATTEMPT cell=A kind=ITEM_LOAD found=true status=ESCROW_FULL admitted=false "
                         "unchanged=true pending=64>64 retained=64>64 ledger=absent>absent logical=abc")
        self.assertEqual(("A", "ITEM_LOAD", "true", "ESCROW_FULL", "false", "true", "64", "64", "64", "64", "absent", "absent", "abc"),
                         r042.ATTEMPT.search(line).groups())
        missing = r042.ATTEMPT.search(PREFIX + "ARCE_RELEASE_R042_ATTEMPT cell=A kind=FUEL found=false")
        self.assertEqual("false", missing[3])
        line = PREFIX + ("ARCE_RELEASE_R042_REPORT cell=B kind=FUEL found=true rocket=1000 device=0 rocketEpoch=1 deviceEpoch=1 "
                         "rocketMarkers=0 deviceMarkers=0 locked=true protected=true refusal=ESCROW_PENDING watching=false logical=x")
        self.assertEqual("ESCROW_PENDING", r042.REPORT.search(line)[12])
        self.assertEqual(("FLUID_LOAD", "o", "l", "r", "d"), r042.CELL.search(
            PREFIX + "ARCE_RELEASE_R042_CELL cell=A kind=FLUID_LOAD owner=o logical=l rocket=r device=d").groups()[1:])
        self.assertEqual("1", r042.INSERT.search(
            PREFIX + "ARCE_RELEASE_R042_INSERT satellite=s blocked=true valid=false remainder=1 held=false")[4])
        self.assertEqual("true", r042.RIDER.search(
            PREFIX + "ARCE_RELEASE_R042_RIDER action=board logical=l rider=online mounted=true vehicle=v seat=0 rocket=r")[4])

    def test_the_console_answers_parse(self):
        launch = r042.LAUNCH.search(PREFIX + "ARCE_RELEASE_TEST_LAUNCH request=q entity=e logical=l source=minecraft:overworld "
                                    "destination=advancedrocketrycommunity:moon checkpoint=COUNTDOWN code=ROCKET_BUSY required_fuel=1")
        self.assertEqual("ROCKET_BUSY", launch[5])
        good = r042.SATELLITE.search(PREFIX + "ARCE_RELEASE_TEST_SATELLITE_LAUNCH satellite=s mission=m owner=o "
                                     "target=advancedrocketrycommunity:earth code=SUCCESS deadline=5")
        self.assertEqual(("s", "SUCCESS", None), good.groups())
        refused = r042.SATELLITE.search(PREFIX + "Release-test satellite launch rejected: DEPLOYMENT_PENDING")
        self.assertEqual("DEPLOYMENT_PENDING", refused[3])
        self.assertEqual("refused.released", r042.RETIRE.search(
            PREFIX + "message.advancedrocketrycommunity.bay_receipt.refused.released")[1])
        self.assertEqual("not-loaded", r042.ESCROW_STATUS.search(PREFIX + "escrow admission=disabled ledger=not-loaded held=unknown")[1])
        self.assertIsNone(r042.ESCROW_STATUS.search(PREFIX + "escrow admission=disabled operations=0/64 held=0")[1])
        self.assertEqual(("LOAD_FAILED", "advancedrocketrycommunity_bay_receipts.dat"), r042.BAY_NOT_LOADED.search(
            "WARN]: ARCE_BAY_RECEIPTS_NOT_LOADED reason=LOAD_FAILED file=advancedrocketrycommunity_bay_receipts.dat; the file").groups())
        self.assertEqual(("advancedrocketrycommunity_bay_receipts",), r042.VANILLA_LOAD_ERROR.search(
            "[12:00:00] [Server thread/ERROR] [minecraft/DimensionDataStorage]: Error loading saved data: "
            "advancedrocketrycommunity_bay_receipts").groups())

    def test_the_refusal_line_splits_reason_and_hint(self):
        match = r042.REFUSAL.search(REFUSAL)
        self.assertEqual("BAY_RECEIPTS", match[1])
        self.assertTrue(match[2].endswith("so its absence is not proven"))
        self.assertTrue(match[3].startswith(r042.STORAGE_HINT))


class OracleTests(unittest.TestCase):
    def test_refusals_must_change_nothing_and_answer_the_expected_status(self):
        rows = {kind: attempt() for kind in r042.KINDS}
        self.assertEqual({kind: "ESCROW_FULL" for kind in r042.KINDS}, r042.check_refused(rows, "ESCROW_FULL", "absent"))
        for bad in (dict(admitted=True), dict(unchanged=False), dict(status="READY"), dict(pending=(64, 65)),
                    dict(retained=(1, 2)), dict(ledger=("absent", "present"))):
            with self.subTest(bad=bad), self.assertRaises(smoke.SmokeError):
                r042.check_refused(dict(rows, FUEL=attempt(**bad)), None, "absent")
        with self.assertRaises(smoke.SmokeError):
            r042.check_refused(dict(rows, ITEM_UNLOAD=attempt(status="OWNER_PENDING_FULL")), "ESCROW_FULL")

    def test_admissions_must_be_admitted(self):
        self.assertEqual({"FUEL": "PREPARING"}, r042.check_admitted({"FUEL": attempt("PREPARING", admitted=True)}))
        with self.assertRaises(smoke.SmokeError):
            r042.check_admitted({"FUEL": attempt("ESCROW_FULL")})

    def test_completion_is_exactly_one_transfer_with_no_marker_and_a_closed_switch(self):
        for kind in r042.KINDS:
            self.assertTrue(r042.untouched(report(kind), kind))
            done = report(kind, r042.AFTER[kind], epochs=(1, 1))
            self.assertTrue(r042.completed(done, kind))
            self.assertFalse(r042.completed(dict(done, watching=True), kind))
            self.assertFalse(r042.completed(dict(done, device_markers=1), kind))
            self.assertFalse(r042.completed(dict(done, rocket_epoch=2, device_epoch=2), kind), "a second transfer passed")
            self.assertFalse(r042.untouched(done, kind))

    def test_the_error_audit_allows_each_injected_line_exactly_as_often_as_given(self):
        load = "[1] [Server thread/ERROR] [minecraft/DimensionDataStorage]: Error loading saved data: advancedrocketrycommunity_bay_receipts\n"
        allowed = {r042.VANILLA_LOAD_ERROR: 1}
        self.assertEqual([], r042.audit_errors([PREFIX + "fine\n", load], allowed))
        self.assertTrue(r042.audit_errors([load, load], allowed))
        self.assertTrue(r042.audit_errors([PREFIX + "fine\n"], allowed))
        self.assertTrue(r042.audit_errors([load, ERROR + "something else\n"], allowed))
        self.assertTrue(r042.audit_errors(["[1] [Server thread/FATAL] [x/]: crash\n"], {}))

    def test_the_refused_start_needs_one_refusal_with_the_storage_hint_no_world_and_a_failing_exit(self):
        start = dict(exit_code=1, lines=[PREFIX + "Loading\n", REFUSAL,
                                         "[1] [Server thread/ERROR] [minecraft/MinecraftServer]: Encountered an unexpected exception\n"])
        self.assertEqual("BAY_RECEIPTS", r042.check_refused_start("U_BAY", start)["type"])
        for broken in (dict(start, exit_code=0),
                       dict(start, lines=start["lines"] + [PREFIX + 'Done (3.2s)! For help, type "help"\n']),
                       dict(start, lines=[REFUSAL.replace(r042.STORAGE_HINT, "Keep the world stopped.")]),
                       dict(start, lines=[REFUSAL, REFUSAL]),
                       dict(start, lines=[REFUSAL, ERROR + "ARCE_SOMETHING_ELSE failed\n"]),
                       dict(start, lines=[REFUSAL.replace("type=BAY_RECEIPTS", "type=RESOURCE_ESCROW")])):
            with self.subTest(broken=broken["lines"][-1][:60]), self.assertRaises(smoke.SmokeError):
                r042.check_refused_start("U_BAY", broken)

    def test_the_injection_must_happen_once_with_vanillas_one_load_error(self):
        lines = [PREFIX + "ARCE_RELEASE_LOAD_FAILURE type=BAY_RECEIPTS injected=true\n",
                 "[1] [Server thread/ERROR] [minecraft/DimensionDataStorage]: Error loading saved data: advancedrocketrycommunity_bay_receipts\n"]
        self.assertEqual("BAY_RECEIPTS", r042.check_injected("N_BAY", lines, "BAY_RECEIPTS", "advancedrocketrycommunity_bay_receipts")["injected"])
        for broken in (lines[:1], lines[1:], lines + [PREFIX + "ARCE_RELEASE_LOAD_FAILURE refused type=BAY_RECEIPTS: closed\n"]):
            with self.assertRaises(smoke.SmokeError):
                r042.check_injected("N_BAY", broken, "BAY_RECEIPTS", "advancedrocketrycommunity_bay_receipts")

    def test_the_flight_hold_needs_600_ticks_with_the_rocket_held_in_transit(self):
        held = dict(claim="NONE", transfer="PREPARED", state="TRANSIT", locked="true", y="50.0", rocket="r")
        class Fake:
            def __init__(self, statuses, times):
                self.statuses, self.times = list(statuses), list(times)
            def status(self, logical):
                return self.statuses.pop(0)
            def gametime(self):
                return self.times.pop(0)
        with patch.object(r042.time, "sleep"):
            self.assertEqual(600, r042.flight_hold(Fake([held, held], [100, 400, 700, 700]), "N_BAY", "L")["ticks"])
            for second in (dict(held, y="51.0"), dict(held, transfer="COMMITTED"), dict(held, claim="RESUME"), dict(held, locked="false")):
                with self.assertRaises(smoke.SmokeError):
                    r042.flight_hold(Fake([held, second], [100, 700, 700]), "N_BAY", "L")


class AccessListTests(unittest.TestCase):
    def test_deny_adds_the_file_and_folder_entries_and_restore_refuses_a_left_entry(self):
        calls = []
        def fake(*arguments):
            calls.append(arguments)
            return "D:/w/data BUILTIN\\Administrators:(I)(F)\n"
        with patch.object(r042, "icacls", side_effect=fake):
            r042.deny(Path("D:/w/data/file.dat"), "S-1-5-21-1")
            self.assertIn((str(Path("D:/w/data/file.dat")), "/deny", "*S-1-5-21-1:(F)"), calls)
            self.assertIn((str(Path("D:/w/data")), "/deny", "*S-1-5-21-1:(RD)"), calls)
            r042.restore(Path("D:/w/data/file.dat"), "S-1-5-21-1")
        with patch.object(r042, "icacls", return_value="D:/w/data DESKTOP\\Administrator:(DENY)(RD)\n"):
            with self.assertRaises(smoke.SmokeError):
                r042.restore(Path("D:/w/data/file.dat"), "S-1-5-21-1")

    def test_the_manifest_sees_every_changed_or_new_file(self):
        with tempfile.TemporaryDirectory() as directory:
            world = Path(directory)
            (world / "data").mkdir()
            (world / "data" / "a.dat").write_bytes(b"1")
            before = r042.manifest(world)
            (world / "data" / "a.dat").write_bytes(b"2")
            (world / "b").write_bytes(b"")
            after = r042.manifest(world)
            self.assertEqual({"data/a.dat", "b"}, {path for path in set(before) | set(after) if before.get(path) != after.get(path)})


class InvocationTests(unittest.TestCase):
    def test_a_scratch_run_runs_only_its_familys_case(self):
        args = SimpleNamespace(source_commit="x", scratch="N", case=["P_SERVER"])
        with patch.object(r042, "assert_pin"), patch.object(r042.bay, "check_candidate", return_value={}), \
                tempfile.TemporaryDirectory() as directory:
            args.session_dir = Path("D:/GitHub/ARCE-Task-Evidence/Temp") / ("W332-test-" + Path(directory).name)
            args.evidence_dir = Path(directory) / "evidence"
            with self.assertRaises(smoke.SmokeError):
                r042.run(args)
            self.assertFalse(args.session_dir.exists())

    def test_every_family_has_a_runner_and_every_scratch_family_a_case(self):
        self.assertEqual(set(r042.RUNNERS), {spec["family"] for spec in r042.CASES.values()})
        self.assertTrue(all(case in r042.CASES and r042.CASES[case]["family"] == family for family, case in r042.SCRATCH.items()))

    def test_the_candidate_arguments_are_required(self):
        source = (Path(r042.__file__)).read_text(encoding="utf-8")
        self.assertIn("bay.add_candidate_arguments(parser)", source)
        self.assertIn("candidate = bay.check_candidate(args)", source)
        self.assertIn("bay.recheck_candidate(candidate)", source)
        self.assertTrue(re.search(r"--lock-owner\", required=True", source))


if __name__ == "__main__":
    unittest.main()
