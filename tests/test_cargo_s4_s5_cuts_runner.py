import copy
import json
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_cargo_s4_s5_cuts as runner

SETTLEMENT_ORDER = [
    "FS_O1_BEFORE", "FS_O1_AFTER", "FS_BIND_BEFORE", "FS_BIND_AFTER", "FS_O2_ROCKET_BEFORE", "FS_O2_ROCKET_AFTER",
    "FS_O2_DEVICE_BEFORE", "FS_O2_DEVICE_AFTER", "FS_O3_BEFORE", "FS_O3_AFTER", "FS_O3J_BEFORE", "FS_O3J_AFTER",
    "FS_O4_BEFORE", "FS_O4_AFTER", "FS_PRUNE_BEFORE", "FS_PRUNE_AFTER", "FS_ABANDON_ROCKET", "FS_ABANDON_DEVICE",
    "FS_VETO_DESTINATION_SAVED", "FS_BIND_VETO"]


def rocket(x=68.5, cargo=4000, epoch=1, markers=0, bound=True, logical=(1, 2, 3, 4)):
    return dict(uuid=[9, 9, 9, 9], logical=list(logical), owner=[5, 5, 5, 5], x=x, pos=[x, 100.0, 64.5], cargo=cargo, epoch=epoch,
                fuel=1000, flight_state="FUELED",
                markers=[dict(op_id="op", seq=1, role="SOURCE", epoch_after=epoch)] * markers, bound=bound, passengers=[])


def device(amount=1000, epoch=1, markers=0, role="UNLOAD"):
    return dict(id=[7, 7, 7, 7], owner=[5, 5, 5, 5], role=role, logical=[1, 2, 3, 4], amount=amount, epoch=epoch,
                markers=[dict(op_id="op", seq=1, role="DESTINATION", epoch_after=epoch)] * markers)


def released_disk():
    receipt = dict(released=True, accounting_status="UNKNOWN", payload_amount=0, choice="ACCEPT_CURRENT", missing=[],
                   survivors=[dict(endpoint=dict(kind="ENTITY"), signature_kind="FUEL_VECTOR_V1"),
                              dict(endpoint=dict(kind="BLOCK_ENTITY"), signature_kind="FLUID_TANK_V1")])
    root = dict(ledger_id="ledger", records=[], tombstones=[receipt], floors=[], armed_flights=[], issued_launch_serial=0,
                rocket_authorities=[dict(k="FUEL_VECTOR_V1", outcome="READY", p=[9, 9, 9, 9], l=[1, 2, 3, 4], o=[5, 5, 5, 5], e=1)])
    value = rocket()
    return dict(ledger=root, journal=0, rockets=[value], rocket=value, device=device(), second=None, tank=0,
                blocks=["minecraft:air", "minecraft:air"], files={})


def stale_disk():
    value = rocket()
    root = dict(ledger_id="ledger", records=[], tombstones=[], floors=[], armed_flights=[], issued_launch_serial=0, rocket_authorities=[])
    return dict(ledger=root, journal=1, rockets=[value], rocket=value, device=device(), second=None, tank=0,
                blocks=["minecraft:air", "minecraft:air"], files={})


def retired_disk():
    disk = released_disk()
    disk["rocket"]["markers"] = rocket(markers=1)["markers"]
    disk["device"]["markers"] = device(markers=1)["markers"]
    disk["ledger"]["tombstones"][0]["before_choice"] = [
        dict(missing=False, endpoint=dict(kind=kind, identity=disk[key][identity]),
             state=dict(epoch=1, markers=copy.deepcopy(disk[key]["markers"])))
        for key, kind, identity in (("rocket", "ENTITY", "uuid"), ("device", "BLOCK_ENTITY", "id"))]
    return disk


class CargoSettlementFlightRunnerTest(unittest.TestCase):
    def test_f6_requires_the_joint_boundary_and_a_stored_landed_destination(self):
        case = "FF_F6_AFTER_CARGO"
        prefix = "ARCE_RELEASE_CARGO_CUT_PAUSED cut=" + case + " boundary="
        runner.observations([prefix + "F6_JOINT_PROVED"], case, 0)
        for boundary in ("F6_READBACK_AFTER", "BARRIER_READBACK_AFTER", "F6_LANDED"):
            with self.assertRaises(runner.smoke.SmokeError):
                runner.observations([prefix + boundary], case, 0)
        disk = stale_disk()
        value = rocket(x=80.5, cargo=1000, epoch=0) | dict(fuel=700, flight_state="LANDED")
        disk.update(rocket=value, rockets=[value], device=device(amount=4000, role="LOAD"))
        disk["ledger"].update(issued_launch_serial=1, armed_flights=[dict(outcome="ARMED")])
        runner.check(case, disk, False)
        for mutate in (lambda d: d.update(rockets=[]),
                       lambda d: d["rockets"].append(rocket(cargo=1000)),
                       lambda d: d["rocket"].update(x=68.5),
                       lambda d: d["rocket"].update(cargo=0),
                       lambda d: d["rocket"].update(epoch=1),
                       lambda d: d["rocket"].update(fuel=1000),
                       lambda d: d["rocket"].update(flight_state="DESCENT"),
                       lambda d: d.update(journal=0)):
            changed = copy.deepcopy(disk); mutate(changed)
            with self.assertRaises(runner.smoke.SmokeError):
                runner.check(case, changed, False)

    def test_retired_markers_match_the_frozen_audit_not_the_survivor_projection(self):
        disk = retired_disk()
        self.assertTrue(all("markers" not in survivor for survivor in disk["ledger"]["tombstones"][0]["survivors"]))
        for case in ("FS_O4_AFTER", "FS_PRUNE_BEFORE"):
            runner.check(case, disk, False)
        disk["rocket"]["markers"] = []; disk["device"]["markers"] = []
        runner.check("FS_O4_AFTER", disk, True)  # Pruning removes native markers, not the historical audit.

    def test_retired_markers_reject_a_missing_native_or_audit_marker(self):
        for key, index in (("rocket", 0), ("device", 1)):
            for location in ("native", "audit"):
                with self.subTest(endpoint=key, location=location):
                    disk = retired_disk()
                    markers = disk[key]["markers"] if location == "native" else disk["ledger"]["tombstones"][0]["before_choice"][index]["state"]["markers"]
                    markers.clear()
                    with self.assertRaises(runner.smoke.SmokeError):
                        runner.check("FS_O4_AFTER", disk, False)

    def test_retired_markers_reject_an_extra_or_duplicate_native_or_audit_marker(self):
        for key, index in (("rocket", 0), ("device", 1)):
            for location in ("native", "audit"):
                for operation in ("op", "extra"):
                    with self.subTest(endpoint=key, location=location, operation=operation):
                        disk = retired_disk()
                        markers = disk[key]["markers"] if location == "native" else disk["ledger"]["tombstones"][0]["before_choice"][index]["state"]["markers"]
                        markers.append(markers[0] | dict(op_id=operation))
                        with self.assertRaises(runner.smoke.SmokeError):
                            runner.check("FS_O4_AFTER", disk, False)

    def test_retired_markers_reject_each_wrong_native_or_audit_field(self):
        for key, index in (("rocket", 0), ("device", 1)):
            for location in ("native", "audit"):
                for field, value in (("op_id", "wrong"), ("seq", 2), ("role", "WRONG"), ("epoch_after", 2)):
                    with self.subTest(endpoint=key, location=location, field=field):
                        disk = retired_disk()
                        markers = disk[key]["markers"] if location == "native" else disk["ledger"]["tombstones"][0]["before_choice"][index]["state"]["markers"]
                        markers[0][field] = value
                        with self.assertRaises(runner.smoke.SmokeError):
                            runner.check("FS_O4_AFTER", disk, False)

    def test_retired_markers_require_the_same_physical_endpoint_and_epoch(self):
        for index in (0, 1):
            for field in ("identity", "kind", "epoch", "missing"):
                with self.subTest(endpoint=index, field=field):
                    disk = retired_disk(); observation = disk["ledger"]["tombstones"][0]["before_choice"][index]
                    if field == "epoch":
                        observation["state"][field] = 2
                    elif field == "missing":
                        observation[field] = True
                    else:
                        observation["endpoint"][field] = "wrong"
                    with self.assertRaises(runner.smoke.SmokeError):
                        runner.check("FS_O4_AFTER", disk, False)

    def test_post_launch_terminal_head_and_named_checks(self):
        disk = released_disk()
        disk["ledger"]["issued_launch_serial"] = 1
        disk["ledger"]["rocket_authorities"][0].update(outcome="HANDED_OFF", e=0, issued=1, completed=1, a="op", g=4)
        disk["ledger"]["tombstones"][0].update(anchor_op="op", release_generation=4)
        disk["rocket"].update(x=80.5, pos=[80.5, 100.0, 64.5], epoch=0, fuel=700, flight_state="LANDED")
        runner.check("FS_O3_AFTER", disk, True)
        mutations = (
            ("Head outcome", lambda d: d["ledger"]["rocket_authorities"][0].update(outcome="READY")),
            ("Head identity", lambda d: d["ledger"]["rocket_authorities"][0].update(l=[8, 8, 8, 8])),
            ("Head identity", lambda d: d["ledger"]["rocket_authorities"][0].update(o=[8, 8, 8, 8])),
            ("Head identity", lambda d: d["ledger"]["rocket_authorities"][0].update(completed=0)),
            ("Native cargo quantities", lambda d: d["rocket"].update(cargo=5000)),
            ("Native cargo quantities", lambda d: d["device"].update(amount=2000)),
            ("Native fuel quantity", lambda d: d["rocket"].update(fuel=1000)),
            ("Receipt", lambda d: d["ledger"]["tombstones"][0].update(released=False)),
            ("Receipt", lambda d: d["ledger"]["rocket_authorities"][0].update(a="other")),
            ("Markers", lambda d: d["rocket"].update(markers=[{}])),
            ("Markers", lambda d: d["device"].update(markers=[{}])))
        for name, mutate in mutations:
            with self.subTest(name=name):
                changed = copy.deepcopy(disk); mutate(changed)
                with self.assertRaisesRegex(runner.smoke.SmokeError, name):
                    runner.check("FS_O3_AFTER", changed, True)
        before_launch = released_disk()
        runner.check("FS_O1_AFTER", before_launch, True)
        before_launch["ledger"]["rocket_authorities"][0]["outcome"] = "HANDED_OFF"
        with self.assertRaisesRegex(runner.smoke.SmokeError, "expected READY"):
            runner.check("FS_O1_AFTER", before_launch, True)

    def test_failed_native_files_are_retained_byte_for_byte(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); session = root / "session"; world = session / "world-case"
            files = {"world-case/data/ledger.dat": b"not-decodable", "world-case/region/r.0.0.mca": b"terrain",
                     "world-case/entities/r.0.0.mca": b"entities", "crash-reports/crash.txt": b"failure"}
            for name, content in files.items():
                path = session / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(content)
            result = runner.retain_failed_files(session, world, root / "evidence")
            self.assertEqual(set(files), set(result["files"]))
            for name, content in files.items():
                self.assertEqual(content, (root / "evidence" / name).read_bytes())
                self.assertEqual(runner.digest(session / name), result["files"][name]["sha256"])

    def test_the_case_table_is_the_task_rows_in_order(self):
        settlement = [case for case, spec in runner.CASES.items() if spec["family"] == "C-S4"]
        flight = [case for case, spec in runner.CASES.items() if spec["family"] == "C-S5"]
        self.assertEqual(SETTLEMENT_ORDER, settlement)
        self.assertEqual(16, len(flight))
        for case in flight:
            self.assertTrue(case.startswith("FF_") and runner.CASES[case]["final"] is not None or case == "FF_GOVERNED_TERMINAL", case)
        # Only P1-F8's three windows set the existing landed-disassembly probe, and each waits for its own boundary.
        disassembly = {case: spec for case, spec in runner.CASES.items() if "properties" in spec}
        self.assertEqual({"FF_DISASSEMBLY_REMOVAL", "FF_DISASSEMBLY_RESTORATION", "FF_DISASSEMBLY_RELEASE"}, set(disassembly))
        for spec, boundary in zip(disassembly.values(), ("K1", "K2", "K5")):
            self.assertEqual({runner.LANDED: "LANDED_DISASSEMBLY:" + boundary}, spec["properties"])
            self.assertIn("boundary=" + boundary + " ", spec["marker"])

    def test_a_released_settlement_keeps_fuel_vector_authority_unknown_loss_and_conservation(self):
        runner.check("FS_O1_AFTER", released_disk(), True)
        mutations = (
            lambda d: d["ledger"]["tombstones"][0].update(accounting_status="KNOWN"),
            lambda d: d["ledger"]["tombstones"][0].update(payload_amount=1000),
            lambda d: d["ledger"]["tombstones"][0]["survivors"][0].update(signature_kind="COMPOSITE_V1"),
            lambda d: d["ledger"]["rocket_authorities"][0].update(k="COMPOSITE_V1"),
            lambda d: d["ledger"]["rocket_authorities"][0].update(e=0),
            lambda d: d["ledger"]["floors"].append({}),
            lambda d: d["rocket"].update(cargo=5000),
            lambda d: d["rocket"].update(markers=[dict(op_id="op")]),
            lambda d: d["device"].update(amount=2000),
            lambda d: d.update(journal=1),
            lambda d: d["rockets"].append(rocket(x=80.5)),
            lambda d: d["device"].update(role="LOAD"))
        for mutate in mutations:
            changed = copy.deepcopy(released_disk()); mutate(changed)
            with self.assertRaises(runner.smoke.SmokeError):
                runner.check("FS_O1_AFTER", changed, True)

    def test_six_thousand_millibuckets_are_raw_only_while_a_record_locks_both_ends(self):
        held = copy.deepcopy(released_disk())
        held["ledger"].update(tombstones=[], rocket_authorities=[], records=[dict(op_id="op", seq=1, phase="SETTLING_ACCEPT", reason="OTHER_STATE", settlement={})])
        held["rocket"].update(cargo=5000, epoch=0, markers=[], bound=False); held["device"] = device(markers=1)
        runner.check("FS_VETO_DESTINATION_SAVED", held, True)
        for key, value in (("op_id", "other"), ("seq", 2), ("role", "SOURCE"), ("epoch_after", 2)):
            altered = copy.deepcopy(held); altered["device"]["markers"][0][key] = value
            with self.assertRaises(runner.smoke.SmokeError):
                runner.check("FS_VETO_DESTINATION_SAVED", altered, True)
        unlocked = copy.deepcopy(held); unlocked["ledger"]["records"] = []
        with self.assertRaises(runner.smoke.SmokeError):
            runner.check("FS_VETO_DESTINATION_SAVED", unlocked, True)

    def test_a_stale_record_keeps_one_latest_rocket_and_never_a_rebuilt_copy(self):
        for case in ("FF_REBUILD_DESTINATION", "FF_KEEP_SOURCE", "FF_REBUILD_SOURCE", "FF_RECONNECT"):
            runner.check(case, stale_disk(), True)
            for mutate in (lambda d: d["rockets"].append(rocket(x=80.5, cargo=5000, epoch=0, bound=False)),
                           lambda d: d["rockets"][0].update(cargo=5000),
                           lambda d: d.update(journal=0),
                           lambda d: d["ledger"]["tombstones"].append({}),
                           lambda d: d.update(blocks=["advancedrocketrycommunity:pressurized_tank", "minecraft:iron_block"], tank=4000)):
                changed = copy.deepcopy(stale_disk()); mutate(changed)
                with self.assertRaises(runner.smoke.SmokeError):
                    runner.check(case, changed, True)

    def test_latest_cargo_is_restored_exactly_once(self):
        restored = stale_disk(); restored.update(rockets=[], rocket=None, journal=0, tank=4000,
                                                 blocks=["advancedrocketrycommunity:pressurized_tank", "minecraft:iron_block"])
        runner.check("FF_DISASSEMBLY_RELEASE", restored, True)
        for mutate in (lambda d: d.update(tank=5000), lambda d: d["rockets"].append(rocket()), lambda d: d.update(journal=1)):
            changed = copy.deepcopy(restored); mutate(changed)
            with self.assertRaises(runner.smoke.SmokeError):
                runner.check("FF_DISASSEMBLY_RELEASE", changed, True)

    def test_terminal_authority_never_makes_missing_cargo_a_pass(self):
        disk = stale_disk(); disk["rocket"].update(cargo=1000); disk["device"].update(amount=4000)
        disk["ledger"].update(issued_launch_serial=1, armed_flights=[dict(outcome="HANDED_OFF")])
        runner.check("FF_GOVERNED_TERMINAL", disk, True)
        for mutate in (lambda d: d.update(rockets=[]), lambda d: d["rocket"].update(cargo=0),
                       lambda d: d["ledger"]["armed_flights"][0].update(outcome="ARMED")):
            changed = copy.deepcopy(disk); mutate(changed)
            with self.assertRaises(runner.smoke.SmokeError):
                runner.check("FF_GOVERNED_TERMINAL", changed, True)

    def test_observations_need_the_exact_marker_no_errors_and_the_required_fence(self):
        runner.observations(["ARCE_RELEASE_CARGO_CUT_PAUSED cut=FS_O1_AFTER boundary=O1_AFTER"], "FS_O1_AFTER", 0)
        with self.assertRaises(runner.smoke.SmokeError):
            runner.observations(["ARCE_RELEASE_CARGO_CUT_PAUSED cut=FS_O1_AFTER boundary=O1_BEFORE"], "FS_O1_AFTER", 0)
        result = "ARCE_RELEASE_CARGO_CUT_RESULT cut=FF_KEEP_SOURCE state=REFUSED"
        fence = "[Server thread/WARN] ARCE_TRANSFER_FENCED transfer=t reason=FLOOR_CONFLICT repair=offline-whole-world"
        runner.observations([result, fence], "FF_KEEP_SOURCE", 1)
        for lines in ([result], [fence], [result, result, fence], [result, fence, "[worker/ERROR] failure"]):
            with self.assertRaises(runner.smoke.SmokeError):
                runner.observations(lines, "FF_KEEP_SOURCE", 1)
        runner.observations(["ARCE_LANDED_DISASSEMBLY_PAUSED boundary=K2 transfer=t logical=l"], "FF_DISASSEMBLY_RESTORATION", 0)
        with self.assertRaises(runner.smoke.SmokeError):
            runner.observations(["ARCE_RELEASE_CARGO_CUT_PAUSED cut=FF_DISASSEMBLY_RESTORATION boundary=K2"], "FF_DISASSEMBLY_RESTORATION", 0)

    def test_identity_allows_an_absent_end_but_not_another_rocket(self):
        cut = stale_disk()
        self.assertTrue(runner.same_identity(cut, stale_disk()))
        absent = stale_disk(); absent.update(rockets=[], device=None)
        self.assertTrue(runner.same_identity(cut, absent))
        other = stale_disk(); other["rockets"] = [rocket(logical=(8, 8, 8, 8))]
        self.assertFalse(runner.same_identity(cut, other))
        moved = stale_disk(); moved["device"] = device() | dict(id=[6, 6, 6, 6])
        self.assertFalse(runner.same_identity(cut, moved))

    def test_o3_restart_requires_the_frozen_proofs_and_journal_lease(self):
        prefix = "ARCE_RELEASE_CARGO_CUT_PROOF cut=FS_O3_AFTER checkpoint="
        lines = [prefix + name + " digest=frozen proved=2 waiting=0" for name in
                 ("O2_AFTER_0", "O2_AFTER_1", "O3J_AFTER", "O4_CAPTURE")]
        runner.recovered_proofs(lines, "frozen")
        for ending in ("\n", "\r\n"):
            runner.recovered_proofs([line + ending for line in lines], "frozen")
        for suffix in (" extra", " proved=1", " waiting=1", " "):
            with self.assertRaises(runner.smoke.SmokeError):
                runner.recovered_proofs([line + suffix + "\n" for line in lines], "frozen")
        for index in range(len(lines)):
            with self.assertRaises(runner.smoke.SmokeError):
                runner.recovered_proofs(lines[:index] + lines[index + 1:], "frozen")
        with self.assertRaises(runner.smoke.SmokeError):
            runner.recovered_proofs(lines, "changed")

    def test_keep_going_is_refused_outside_a_development_run(self):
        argv = ["runner", "template", "--jar", "j", "--session-dir", "s", "--evidence-dir", "e", "--source-commit", "c",
                "--java", "java", "--lock-owner", "o", "--case", "FS_O1_BEFORE", "--keep-going"]
        with patch.object(sys, "argv", argv), self.assertRaises(SystemExit):
            runner.main()

    def test_first_failed_cut_retains_three_starts_and_stops_before_the_next_cut(self):
        self.run_failed_cut(False)

    def test_failed_restart_without_result_marker_is_captured_before_cleanup(self):
        self.run_failed_cut(True)

    def run_failed_cut(self, wait_failure):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); (root / "template/libraries").mkdir(parents=True)
            jar = root / "test.jar"; jar.write_bytes(b"fixture")
            args = SimpleNamespace(source_commit="sha", lock_owner="owner", source_server=root / "template", jar=jar,
                                   session_dir=root / "session", evidence_dir=root / "evidence", java="java", startup_timeout=240,
                                   case=["FS_O1_BEFORE", "FS_O1_AFTER"], dev=False, keep_going=False)
            process = Mock(); process.finish.return_value = 0; process.lines = []
            process.process.poll.return_value = 0
            if wait_failure:
                process.wait_for.side_effect = [None, runner.smoke.SmokeError("exited without result marker"), None]
            disk = released_disk()
            with patch.object(runner, "assert_pin"), patch.object(runner, "require_lock"), \
                    patch.object(runner, "preflight", return_value={}), patch.object(runner, "digest", return_value="hash"), \
                    patch.object(runner.smoke, "CapturedProcess", return_value=process) as starts, \
                    patch.object(runner.smoke, "allocate_port", return_value=25565), \
                    patch.object(runner.smoke, "write_server_configuration"), \
                    patch.object(runner, "raw_snapshot", return_value={}), patch.object(runner, "observations"), \
                    patch.object(runner, "inspect", return_value=disk), \
                    patch.object(runner, "check", side_effect=[None, None] if wait_failure else [runner.smoke.SmokeError("held-state finding"), None, None]):
                with self.assertRaisesRegex(runner.smoke.SmokeError, "First contradictory cut"):
                    runner.run(args)
                self.assertEqual(3, starts.call_count)
            report = json.loads((args.evidence_dir / "results.json").read_text())
            self.assertEqual("FAILED", report["status"])
            self.assertEqual(1, len(report["cases"]))
            self.assertEqual(3, len(report["cases"][0]["starts"]))
            self.assertEqual(3, len(list(args.evidence_dir.glob("*-raw.json"))))
            failed = report["cases"][0]["starts"][1 if wait_failure else 0]
            self.assertEqual("FAILED", failed["status"])
            self.assertTrue((args.evidence_dir / failed["native_files"]["directory"] / "manifest.json").is_file())
            self.assertEqual("sha", report["pins"]["source_commit"])
            self.assertTrue(report["cleanup"])
            self.assertFalse(args.session_dir.exists())


if __name__ == "__main__":
    unittest.main()
