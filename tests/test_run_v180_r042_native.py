"""W332: the R-042 native runner's seeds, parsers and oracles, without a server."""
from pathlib import Path
import os
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
        self.assertIsNotNone(r042.INSERTED.search(PREFIX.replace("advancedrocketrycommunity/", "minecraft/MinecraftServer")
                                                  + "R-042 insertion reported"))
        self.assertIsNone(r042.INSERTED.search(PREFIX + "R-042 probe reported"), "the insertion waits for another probe's line")
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

    def test_the_status_of_a_rocket_without_bays_parses_with_empty_packages(self):
        line = PREFIX + ("ARCE_RELEASE_BAY_STATUS logical=l claim=NONE hold=NONE reason=NONE receipts=[] transfer=PREPARED rocket=r "
                         "state=TRANSIT y=50.0 packages= locked=false consumed= owned=0")
        match = r042.PLAIN_STATUS.search(line)
        self.assertEqual(("PREPARED", "r", "TRANSIT", "50.0", "", "false", ""), match.groups()[5:12])
        self.assertIsNone(r042.bay.STATUS.search(line), "W325's status regex now accepts a rocket without bays")
class OracleTests(unittest.TestCase):
    def test_the_pool_bay_requires_its_own_phase_and_reason(self):
        fixture = dict(logical="L", data="satellite", packages=["data", "solar"])
        class Fake:
            args = SimpleNamespace(flight_timeout=1, settle_timeout=1)
            def __init__(self, phase, reason, **changes):
                self.phase, self.reason, self.changes = phase, reason, changes
            def status(self, logical):
                return dict(dict(claim="NONE", state="LANDED", consumed=[None, None], receipts=[],
                                 packages=fixture["packages"]), **self.changes)
            def send(self, command, marker):
                return f"ARCE_RELEASE_BAY_DEPLOYMENT_STATUS satellite=satellite phase={self.phase} reason={self.reason}"
        with patch.object(r042.bay, "launch_fixture", return_value=fixture):
            for case, expected in (("P_SERVER", "DEPLOYMENT_CAPACITY"), ("P_OWNER", "OWNER_PENDING_FULL")):
                self.assertEqual(dict(phase="NONE", reason=expected), r042.bay_refused(Fake("NONE", expected), case)["menu"])
                for phase, reason in (("NONE", "DEPLOYMENT_UNAVAILABLE"), ("NONE", "NONE"),
                                      ("NONE", "OWNER_PENDING_FULL" if case == "P_SERVER" else "DEPLOYMENT_CAPACITY"),
                                      ("REPAIR", expected)):
                    with self.subTest(case=case, phase=phase, reason=reason), self.assertRaises(smoke.SmokeError):
                        r042.bay_refused(Fake(phase, reason), case)
                for changes in (dict(consumed=["s", None]), dict(receipts=["r"]), dict(packages=["different"])):
                    with self.subTest(changes=changes), self.assertRaises(smoke.SmokeError):
                        r042.bay_refused(Fake("NONE", expected, **changes), case)

    def test_run_l_requires_a_present_changed_final_ledger(self):
        class Fake:
            restart = -1
            def start(self, case, restart, properties): self.restart = restart
            def forceload(self): pass
            def cell(self, name, owner): return {}
            def attempt(self, name, kinds="ALL"):
                return {kind: attempt("ESCROW_PENDING" if self.restart == 1 else "READY",
                                      admitted=self.restart != 1) for kind in (r042.KINDS if kinds == "ALL" else kinds.split(","))}
            def await_completed(self, name, kinds=r042.KINDS):
                return {kind: report(kind, r042.AFTER[kind], epochs=(1, 1)) for kind in r042.KINDS}
            def plain(self): return dict(logical="L", rocket="R")
            def rider(self, action, logical): return dict(mounted=action == "board", seat=0)
            def save(self): pass
            def escrow_status(self): return "not-loaded" if self.restart == 1 else "present"
            def launch(self, rocket, checkpoint):
                return dict(code="TRANSFER_JOURNAL_BLOCKED" if self.restart == 1 else "SUCCESS")
            def report(self, name):
                return self.await_completed(name) if name == "S" else {
                    kind: report(kind, locked=True, protected=True, refusal="ESCROW_PENDING") for kind in r042.KINDS}
            def stop(self, case, restart, allowed=None):
                return dict(lines=[] if restart != 1 else [
                    "ARCE_RELEASE_LOAD_FAILURE type=RESOURCE_ESCROW injected=true",
                    "[x/ERROR] Error loading saved data: advancedrocketrycommunity_resource_escrow",
                    "ARCE_ESCROW_LEDGER_NOT_LOADED reason=LOAD_FAILED file=advancedrocketrycommunity_resource_escrow.dat; test"])
        for final in (None, "baseline", "changed"):
            with self.subTest(final=final), patch.object(r042, "new_world", return_value=Path("unused")), \
                    patch.object(r042, "snapshot", return_value={}), patch.object(r042, "disk", side_effect=[
                        {"ledger": "baseline"}, {"ledger": "baseline"}, {"ledger": final}]):
                if final == "changed":
                    self.assertEqual(final, r042.run_l(Fake(), "L_LEDGER")["starts"][-1]["disk"]["ledger"])
                else:
                    with self.assertRaisesRegex(smoke.SmokeError, "present, changed ledger"):
                        r042.run_l(Fake(), "L_LEDGER")

    def test_run_p_also_requires_the_case_specific_bay_reason(self):
        class Fake:
            def start(self, *args): pass
            def forceload(self): pass
            def cell(self, *args): return {}
            def pending(self): return dict(pending=64, retained=64)
            def attempt(self, name): return {kind: attempt() for kind in r042.KINDS}
            def report(self, name): return {kind: report(kind) for kind in r042.KINDS}
        with patch.object(r042, "new_world", return_value=Path("unused")), \
                patch.object(r042, "seed_pool", return_value=dict(rows=[], satellites=[])), \
                patch.object(r042, "bay_refused", return_value=dict(menu=dict(phase="NONE", reason="NONE"))):
            with self.assertRaisesRegex(smoke.SmokeError, "data bay answered"):
                r042.run_p(Fake(), "P_SERVER")

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
            def plain_status(self, logical):
                return self.statuses.pop(0)
            def gametime(self):
                return self.times.pop(0)
        with patch.object(r042.time, "sleep"):
            self.assertEqual(600, r042.flight_hold(Fake([held, held], [100, 400, 700, 700]), "N_BAY", "L")["ticks"])
            for second in (dict(held, y="51.0"), dict(held, transfer="COMMITTED"), dict(held, claim="RESUME"), dict(held, locked="false")):
                with self.assertRaises(smoke.SmokeError):
                    r042.flight_hold(Fake([held, second], [100, 700, 700]), "N_BAY", "L")

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
        self.assertEqual({"U", "N", "L", "P"}, set(r042.RUNNERS))
        self.assertTrue(all(case in r042.CASES and r042.CASES[case]["family"] == family for family, case in r042.SCRATCH.items()))

    def test_the_candidate_arguments_are_required(self):
        source = (Path(r042.__file__)).read_text(encoding="utf-8")
        self.assertIn("bay.add_candidate_arguments(parser)", source)
        self.assertIn("candidate = bay.check_candidate(args)", source)
        self.assertIn("bay.recheck_candidate(candidate)", source)
        self.assertTrue(re.search(r"--lock-owner\", required=True", source))


class UnknownPresenceTests(unittest.TestCase):
    def test_world_manifest_keeps_level_metadata_and_every_nested_file(self):
        with tempfile.TemporaryDirectory() as directory:
            world = Path(directory)
            (world / "data").mkdir()
            (world / "data/root.dat").write_bytes(b"valid")
            (world / "level.dat").write_bytes(b"metadata")
            before = r042.world_manifest(world)
            self.assertEqual({"data/root.dat", "level.dat"}, set(before))
            (world / "level.dat").write_bytes(b"changed metadata")
            self.assertNotEqual(before, r042.world_manifest(world))
            (world / "new-file").write_bytes(b"added")
            self.assertNotEqual(before, r042.world_manifest(world))

    def test_native_refusal_audits_every_propagation_and_its_exception(self):
        session = Path("D:/fixture")
        case = "U_BAY"
        reason = "advancedrocketrycommunity_bay_receipts presence is unknown: its file status check failed, so its absence is not proven"
        exception = "io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataMigrationException: " + reason
        def header(logger, level, message): return f"[12:00:00] [Server thread/{level}] [{logger}]: {message}"
        lines = [header("io.gi.su.ad.AdvancedRocketryCommunity/", "ERROR",
                        "[ARCE-BETA-2000] Beta world data check blocked startup; type=BAY_RECEIPTS; reason=" + reason
                        + ". " + r042.STORAGE_HINT + " Keep the world stopped. Escrow recovery requires a consistent "
                        "whole-world backup including all terrain, entities, POI, journals and player data; never restore or delete only the escrow file."),
                 header("ne.mi.ev.EventBus/EVENTBUS", "ERROR", "Exception caught during firing event: " + reason), exception,
                 header("minecraft/MinecraftServer", "ERROR", "Encountered an unexpected exception"), exception,
                 header("ne.mi.co.ForgeMod/", "FATAL", "Preparing crash report with UUID 00000000-0000-0000-0000-000000000000"),
                 header("minecraft/MinecraftServer", "ERROR", "This crash report has been saved to: D:/fixture/./crash-reports/crash-2026-11-10_08.01.53-server.txt"),
                 header("minecraft/MinecraftServer", "ERROR", "Exception stopping the server"), r042.refusal_log.VANILLA_NPE,
                 "    " + r042.refusal_log.VANILLA_FRAME]
        self.assertEqual([1] * 6, r042.check_unknown_refusal(lines, case, session)["audit"]["observed_counts"])
        for broken in (lines + [lines[5]], lines[:5] + lines[6:], lines[:2] + ["java.lang.IllegalStateException: other"] + lines[3:],
                       lines[:-1] + ["at some.other.frame(Source.java:1)"],
                       lines + [header("minecraft/MinecraftServer", "ERROR", "another error")]):
            with self.subTest(broken=broken), self.assertRaises(smoke.SmokeError):
                r042.check_unknown_refusal(broken, case, session)

    def test_handle_is_restored_and_closed_in_order_even_if_the_body_or_set_fails(self):
        for failure_at in (None, "body", "set"):
            events = []
            class Api:
                def open(self, file): events.append("open"); return 7
                def disposition(self, handle, pending):
                    self_handle.assertEqual(7, handle)
                    events.append(pending)
                    if pending and failure_at == "set": raise RuntimeError("set failed")
                def close(self, handle): events.append("close")
            self_handle = self
            with self.subTest(failure_at=failure_at), tempfile.TemporaryDirectory() as directory:
                file = Path(directory) / "valid.dat"
                file.write_bytes(b"valid")
                def exercise():
                    with r042.presence.delete_pending(file, Api()):
                        if failure_at == "body": raise RuntimeError("body failed")
                if failure_at:
                    with self.assertRaises(RuntimeError): exercise()
                else:
                    exercise()
                self.assertEqual(["open", True, False, "close"], events)
                self.assertEqual(b"valid", file.read_bytes())

    def test_a_handle_is_not_closed_if_clearing_the_disposition_fails(self):
        events = []
        class Api:
            def open(self, file): return 7
            def disposition(self, handle, pending):
                if not pending: raise OSError("clear failed")
            def close(self, handle): events.append("closed")
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / "valid.dat"
            file.write_bytes(b"valid")
            with self.assertRaisesRegex(OSError, "clear failed"):
                with r042.presence.delete_pending(file, Api()): pass
            self.assertEqual([], events)

    @unittest.skipUnless(os.name == "nt", "Windows delete-pending trigger")
    def test_real_delete_pending_handle_keeps_directory_entry_and_restores_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / "valid.dat"
            file.write_bytes(b"valid")
            with r042.presence.delete_pending(file):
                self.assertIn(file.name, os.listdir(directory))
                with self.assertRaises(PermissionError): file.read_bytes()
            self.assertEqual(b"valid", file.read_bytes())

    def test_probe_requires_vanilla_walk_unknown_marker_type_and_diagnostic(self):
        lines = ["VANILLA_VALIDATE=[]", "ACTUAL_ARCE_PRESENCE=UNKNOWN", "PRESENCE_UNKNOWN=true",
                 "DATA_TYPE=Optional[BAY_RECEIPTS]", "DIAGNOSTIC=ARCE-BETA-2000"]
        r042.check_unknown_probe("\n".join(lines), "BAY_RECEIPTS")
        for index in range(len(lines)):
            with self.subTest(index=index), self.assertRaises(smoke.SmokeError):
                r042.check_unknown_probe("\n".join(lines[:index] + lines[index + 1:]), "BAY_RECEIPTS")
        with self.assertRaises(smoke.SmokeError):
            r042.check_unknown_probe("\n".join(lines), "RESOURCE_ESCROW")

    def test_refusal_needs_correct_type_unknown_reason_storage_first_and_no_world_loading(self):
        for case in ("U_BAY", "U_LEDGER"):
            spec = r042.CASES[case]
            line = ERROR + (f"[ARCE-BETA-2000] Beta world data check blocked startup; type={spec['type']}; "
                            f"reason={r042.FILES[spec['file']][:-4]} presence is unknown: its file status check failed. "
                            + r042.STORAGE_HINT + " Keep the world stopped. Whole-world recovery.")
            self.assertEqual(spec["type"], r042.check_unknown_refusal([line], case)["type"])
            for broken in ([line, line], [line.replace("presence is unknown", "unreadable")],
                           [line.replace(spec["type"], "OTHER")], [line.replace(r042.STORAGE_HINT, "Fix it")],
                           [line, PREFIX.replace("advancedrocketrycommunity/", "minecraft/DedicatedServer")
                            + 'Done (2.3s)! For help, type "help"'],
                           [line, PREFIX + "ARCE_BAY_RECEIPTS_INDEX state=LOADED receipts=2"],
                           [line, ERROR + "unrelated failure"]):
                with self.subTest(case=case, broken=broken), self.assertRaises(smoke.SmokeError):
                    r042.check_unknown_refusal(broken, case)


if __name__ == "__main__":
    unittest.main()
