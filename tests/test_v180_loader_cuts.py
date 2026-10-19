"""W97 cut oracle and runner checks, without a JVM or native process."""
import copy
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import loader_cut_oracle as oracle
import run_v180_loader_cuts as runner


def fixture(cut, restart):
    if cut.removeprefix(oracle.PREFIX) in oracle.CHAINS:
        return chain_fixture(cut, restart)
    exp = oracle.expected(cut, restart); bi = exp["bi"]
    seq = 2 if bi else 1
    root = dict(ledger_id="ledger", writer_generation=exp["generation"] or 4, issued_through=exp["issued"],
                records=[], floors=[], tombstones=[], armed_flights=[], rocket_authorities=[], issued_launch_serial=0) if exp["ledger"] else None

    def section(epoch, markers, role):
        result = dict(schema_version=1, resource_epoch=epoch, markers=[])
        if epoch: result["ledger_id"] = "ledger"
        if markers: result["markers"].append(dict(op_id="op", seq=seq, role=role, epoch_after=epoch))
        return result

    loaders = []
    capacity = cut == "LF_M_N3_PREFLIGHT"
    for index in range(6 if capacity else 2 if bi else 1):
        prior = bi and index == 0; moved = 0 if capacity and index else 1 if prior else exp["source"]
        x, y, z = oracle.CAPACITY_POSITIONS[index] if capacity else (66+index, 100, 64)
        loaders.append(dict(id=oracle.LOADER, x=x, y=y, z=z, arce_fluid_fuel_loader=dict(
            device="device"+str(index), owner="owner", binding=dict(physical_rocket="physical", logical_rocket="logical"),
            tank=dict(FluidName=oracle.HYDROGEN if prior else oracle.OXYGEN if bi else oracle.FUEL, Amount=5000-1000*moved),
            resource_escrow=section(moved, 0 if prior else moved*exp["markers"], "SOURCE"))))
    rocket = dict(id=oracle.ROCKET, UUID="physical", Pos=[64.5, 100.0, 64.5], RocketEntityData=dict(
        owner_id="owner", assembly_transaction_id="logical", resource_escrow=section(int(bi)+exp["destination"], exp["destination"]*exp["markers"], "DESTINATION"),
        flight_data=dict(logical_rocket_id="logical", passengers=dict(assignments=[]), fuel=dict(propulsion=dict(
            primary=dict(amount=1000 if bi else exp["destination"]*1000), oxidizer=dict(amount=exp["destination"]*1000 if bi else 0))))))
    if root and exp["records"]:
        root["records"].append(dict(op_id="op", source=dict(identity=loaders[-1]["arce_fluid_fuel_loader"]["device"], type=oracle.LOADER),
                                    destination=dict(identity="physical"), payload=dict(FluidName=oracle.OXYGEN if bi else oracle.FUEL, Amount=1000),
                                    native_binding=dict(complete=1, kind="FUEL_VECTOR_V1")))
    return dict(ledger=root, loaders=loaders, rocket=rocket)


def chain_fixture(cut, restart):
    disk = fixture("LF_M_K6", 0)
    root, rocket = disk["ledger"], disk["rocket"]["RocketEntityData"]
    prefix = 1 if cut == "LF_M_C1" and not restart else 2
    bi = cut.endswith("_BI")
    root["issued_through"] = 2
    first = disk["loaders"][0]
    second = copy.deepcopy(first); second["x"] = 67
    second["arce_fluid_fuel_loader"]["device"] = "device1"
    disk["loaders"].append(second)
    rocket["resource_escrow"]["resource_epoch"] = prefix
    rocket["resource_escrow"]["markers"] = []
    root["records"] = []
    for index, device in enumerate(disk["loaders"]):
        seq = index + 1; loader = device["arce_fluid_fuel_loader"]
        fluid = (oracle.HYDROGEN if index == 0 else oracle.OXYGEN) if bi else oracle.FUEL
        loader["tank"]["FluidName"] = fluid
        marker = dict(op_id="op"+str(seq), seq=seq, role="SOURCE", epoch_after=1)
        loader["resource_escrow"]["markers"] = [] if restart else [marker]
        if seq <= prefix and not restart:
            rocket["resource_escrow"]["markers"].append(dict(marker, role="DESTINATION", epoch_after=seq))
        if not restart and ("PARTIAL" not in cut or seq == 2):
            root["records"].append(dict(op_id=marker["op_id"], seq=seq,
                source=dict(identity=loader["device"], type=oracle.LOADER, before_epoch=0, after_epoch=1),
                destination=dict(identity="physical", before_epoch=seq-1, after_epoch=seq),
                payload=dict(FluidName=fluid, Amount=1000), native_binding=dict(complete=1, kind="FUEL_VECTOR_V1")))
    propulsion = rocket["flight_data"]["fuel"]["propulsion"]
    propulsion["primary"]["amount"] = 1000 if bi else 1000 * prefix
    propulsion["oxidizer"]["amount"] = 1000 * (prefix - 1) if bi else 0
    return disk


class LoaderCutsTest(unittest.TestCase):
    def test_storage_failure_requires_exact_pending_reservation_and_outcome(self):
        for name in ("E4L1_FAIL", "E4L1_UNKNOWN", "E4L2_FAIL", "E4L2_UNKNOWN"):
            cut = oracle.PREFIX + name
            unknown = name.endswith("UNKNOWN"); l1 = name.startswith("E4L1")
            line = (f"ARCE_LOADER_MATRIX_FAILURE cut={cut} unknown={str(unknown).lower()} "
                    f"records={int(not l1)} pending={int(unknown or not l1)} issued={int(not l1)}")
            runner.storage_observations(cut, [line])
            for bad in ([], [line, line], [line.replace("issued=", "issued=9")], [line.replace("pending=", "pending=9")]):
                with self.assertRaises(oracle.smoke.SmokeError): runner.storage_observations(cut, bad)

    def test_storage_l1_failure_does_not_apply_and_l2_failure_never_retires(self):
        for cut in ("LF_M_E4L1_FAIL", "LF_M_E4L1_UNKNOWN"):
            for restart in range(3):
                self.assertEqual(oracle.expected("LF_M_K1", restart), oracle.expected(cut, restart))
        for cut in ("LF_M_E4L2_FAIL", "LF_M_E4L2_UNKNOWN", "LF_M_E4L2_RESAVE", "LF_M_E4L2_RESAVE_RESOLVED"):
            with self.assertRaises(oracle.smoke.SmokeError): oracle.check(fixture("LF_M_K9", 0), cut, 0)

    def test_c4_before_requires_destination_only_durable_state(self):
        self.assertEqual(oracle.expected("LF_M_K5", 0), oracle.expected("LF_M_D2_C4_BEFORE", 0))
        for wrong in ("LF_M_K4", "LF_M_K6"):
            with self.assertRaises(oracle.smoke.SmokeError):
                oracle.check(fixture(wrong, 0), "LF_M_D2_C4_BEFORE", 0)

    def test_s3_covers_every_reconciliation_stage_and_both_chain_modes(self):
        self.assertEqual(18, len(oracle.D2))
        self.assertEqual(5, len(oracle.CHAINS))
        self.assertEqual(23, len(oracle.S3))
        for stage in range(9):
            self.assertTrue(any(name.startswith(f"D2_C{stage}") for name in oracle.D2))

    def test_chain_partial_retirement_rejects_holes_old_frontiers_and_wrong_totals(self):
        for cut in ("LF_M_C1", "LF_M_C2", "LF_M_C2_PARTIAL_REPLACED", "LF_M_C2_PARTIAL_REPLACED_BI"):
            good = fixture(cut, 0)
            mutations = [lambda d: d["ledger"]["records"][0].update(seq=9),
                         lambda d: d["ledger"]["records"][0]["destination"].update(before_epoch=7),
                         lambda d: d["rocket"]["RocketEntityData"]["flight_data"]["fuel"].update(committed_debits=[{}]),
                         lambda d: d["rocket"]["RocketEntityData"]["flight_data"].update(plan={}),
                         lambda d: d["loaders"][1]["arce_fluid_fuel_loader"]["tank"].update(Amount=5000),
                         lambda d: d["rocket"]["RocketEntityData"]["resource_escrow"].update(resource_epoch=0),
                         lambda d: d["rocket"]["RocketEntityData"]["resource_escrow"]["markers"][0].update(op_id="other"),
                         lambda d: d["loaders"][1]["arce_fluid_fuel_loader"]["resource_escrow"].update(markers=[]),
                         lambda d: d["rocket"]["RocketEntityData"]["flight_data"]["fuel"]["propulsion"]["primary"].update(amount=3000)]
            for mutate in mutations:
                bad = copy.deepcopy(good); mutate(bad)
                with self.subTest(cut=cut), self.assertRaises(oracle.smoke.SmokeError): oracle.check(bad, cut, 0)

    def test_chain_second_restart_cannot_replay_either_device(self):
        for cut in oracle.CHAINS:
            name = oracle.PREFIX + cut
            good = fixture(name, 2)
            for index in range(2):
                bad = copy.deepcopy(good)
                bad["loaders"][index]["arce_fluid_fuel_loader"]["tank"]["Amount"] = 3000
                with self.assertRaises(oracle.smoke.SmokeError): oracle.check(bad, name, 2)

    def test_matrix_counts_include_r35_creation_cuts(self):
        self.assertEqual(7, len(oracle.CREATION))
        self.assertEqual(14, len(oracle.NORMAL))
        self.assertEqual({}, oracle.NOT_RUN)
        self.assertEqual(["K2_BI", "K6_BI", "K9_BI"], [c for c in oracle.NORMAL if c.endswith("BI")])

    def test_raw_cut_boundaries_are_not_status_aliases(self):
        # Source applied, destination applied, durable records, issued watermark.
        table = {"N0": (0, 0, 0, 0), "N1": (0, 0, 0, 0), "K0": (0, 0, 0, 0), "K1": (0, 0, 0, 0),
                 "K2": (0, 0, 1, 1), "K3": (0, 0, 1, 1), "K4": (1, 0, 1, 1), "K5": (0, 1, 1, 1),
                 "K6": (1, 1, 1, 1), "K7": (1, 1, 1, 1), "K8": (1, 1, 1, 1), "K9": (1, 1, 0, 1), "K10": (1, 1, 0, 1)}
        for cut, values in table.items():
            exp = oracle.expected("LF_M_"+cut, 0)
            self.assertEqual(values, tuple(exp[key] for key in ("source", "destination", "records", "issued")), cut)

    def test_failed_baseline_requires_three_outcomes_and_final_unavailable(self):
        for mode in ("DEFINITE", "UNKNOWN"):
            cut = "LF_M_N2_" + mode
            outcome = "WRITE_UNKNOWN" if mode == "UNKNOWN" else "WRITE_FAILED_BEFORE_REPLACE"
            lines = ["ARCE_LOADER_MATRIX_STATE phase=pending status=PREPARING"] + [
                f"ARCE_LOADER_MATRIX_BASELINE_FAILURE cut={cut} failure={i} outcome={outcome} pending={int(mode == 'UNKNOWN')} "
                f"unavailable={str(i == 3).lower()} status={'LEDGER_UNAVAILABLE' if i == 3 else 'PREPARING'}" for i in range(1, 4)]
            runner.creation_observations(cut, lines)
            for changed in (lines[:-1], lines + [lines[-1]], [line.replace("unavailable=true", "unavailable=false") for line in lines],
                            [line.replace("status=LEDGER_UNAVAILABLE", "status=PREPARING") for line in lines], lines[1:]):
                with self.assertRaises(oracle.smoke.SmokeError): runner.creation_observations(cut, changed)

    def test_capacity_refusal_checks_all_six_unchanged_native_sources(self):
        for restart in range(3):
            good = fixture("LF_M_N3_PREFLIGHT", restart)
            for index in range(6):
                changed = copy.deepcopy(good)
                changed["loaders"][index]["arce_fluid_fuel_loader"]["tank"]["Amount"] -= 1
                with self.assertRaises(oracle.smoke.SmokeError): oracle.check(changed, "LF_M_N3_PREFLIGHT", restart)
        with self.assertRaises(oracle.smoke.SmokeError): runner.creation_observations("LF_M_N3_PREFLIGHT", [])

    def test_all_supported_cuts_and_two_restarts(self):
        for cut in oracle.CASES:
            identity = None
            for restart in range(3):
                with self.subTest(cut=cut, restart=restart):
                    actual = oracle.check(fixture(cut, restart), cut, restart)
                    if identity is not None: self.assertEqual(identity, actual)
                    identity = actual

    def test_creation_second_restart_requires_successful_retry(self):
        for cut in oracle.CREATION:
            name = "LF_M_" + cut
            with self.assertRaises(oracle.smoke.SmokeError): oracle.check(fixture(name, 1), name, 2)

    def test_raw_quantity_epoch_marker_and_binding_mutations_are_rejected(self):
        good = fixture("LF_M_K6", 0)
        mutations = [lambda d: d["loaders"][0]["arce_fluid_fuel_loader"]["tank"].update(Amount=5000),
                     lambda d: d["rocket"]["RocketEntityData"]["flight_data"]["fuel"]["propulsion"]["primary"].update(amount=2000),
                     lambda d: d["rocket"]["RocketEntityData"]["resource_escrow"].update(resource_epoch=0),
                     lambda d: d["loaders"][0]["arce_fluid_fuel_loader"]["resource_escrow"].update(markers=[]),
                     lambda d: d["rocket"]["RocketEntityData"]["resource_escrow"]["markers"][0].update(op_id="foreign"),
                     lambda d: d["loaders"][0]["arce_fluid_fuel_loader"]["binding"].update(physical_rocket="foreign"),
                     lambda d: d["ledger"].update(issued_through=2),
                     lambda d: d["ledger"]["records"][0]["native_binding"].update(complete=0),
                     lambda d: d["ledger"].update(floors=[{}]),
                     lambda d: d["rocket"].update(Passengers=[{}])]
        for index, mutate in enumerate(mutations):
            changed = copy.deepcopy(good); mutate(changed)
            with self.subTest(mutation=index), self.assertRaises(oracle.smoke.SmokeError): oracle.check(changed, "LF_M_K6", 0)

    def test_mixed_store_is_not_mistaken_for_spendable_conservation(self):
        disk = fixture("LF_M_K5", 0)
        self.assertEqual(5000, disk["loaders"][0]["arce_fluid_fuel_loader"]["tank"]["Amount"])
        oracle.check(disk, "LF_M_K5", 0)
        disk["ledger"]["records"] = []
        with self.assertRaises(oracle.smoke.SmokeError): oracle.check(disk, "LF_M_K5", 0)

    def test_stale_markers_are_required_after_l2_replacement(self):
        disk = fixture("LF_M_K9", 0)
        disk["rocket"]["RocketEntityData"]["resource_escrow"]["markers"] = []
        with self.assertRaises(oracle.smoke.SmokeError): oracle.check(disk, "LF_M_K9", 0)

    def test_bipropellant_preserves_both_banks_and_tanks(self):
        disk = fixture("LF_M_K9_BI", 2)
        disk["loaders"][0]["arce_fluid_fuel_loader"]["tank"]["Amount"] = 3000
        with self.assertRaises(oracle.smoke.SmokeError): oracle.check(disk, "LF_M_K9_BI", 2)

    def test_first_tick_requires_one_marker_and_exact_reservations(self):
        line = "ARCE_LOADER_MATRIX_FIRST cut=LF_M_K2 recovery=true delegate=LOADED issued=1 records=1 pending=1"
        disk = fixture("LF_M_K2", 0)
        self.assertEqual(1, runner.first_tick([line], "LF_M_K2", 1, disk)["pending"])
        for lines in ([], [line, line], [line.replace("pending=1", "pending=0")], [line.replace("issued=1", "issued=2")]):
            with self.assertRaises(oracle.smoke.SmokeError): runner.first_tick(lines, "LF_M_K2", 1, disk)

    def test_cleanup_requires_exact_owned_child(self):
        with tempfile.TemporaryDirectory() as directory:
            parent = Path(directory); session = parent / "session"; session.mkdir()
            (session / ".w97-owned").write_text("ours")
            with self.assertRaises(oracle.smoke.SmokeError): runner.cleanup(session, "other", parent)
            with self.assertRaises(oracle.smoke.SmokeError): runner.cleanup(session, "ours", parent / "wrong")
            self.assertTrue(session.exists())
            runner.cleanup(session, "ours", parent); self.assertFalse(session.exists())

    def test_disk_guard_checks_both_drives(self):
        for free in (10*1024**3, 9*1024**3):
            with mock.patch.object(runner.shutil, "disk_usage", return_value=mock.Mock(free=free)):
                with self.assertRaises(oracle.smoke.SmokeError): runner.disk_space()

    def test_raw_cut_failure_stays_failed_and_collects_both_restarts(self):
        self.failed_cut(False)

    def test_native_log_error_stays_failed_but_does_not_skip_raw_stores_or_restarts(self):
        self.failed_cut(True)

    def failed_cut(self, log_error):
        cut = "LF_M_K5"
        snapshots = [fixture(cut, index) for index in range(3)]
        if not log_error:
            snapshots[0]["loaders"][0]["arce_fluid_fuel_loader"]["tank"]["Amount"] = 4000
        processes = []

        class Process:
            def __init__(self, command, session, log):
                index = len(processes); processes.append(self)
                self.process = mock.Mock()
                prior = snapshots[index-1]["ledger"] if index else None
                records = len(prior["records"]) if prior else 0
                self.lines = [f"ARCE_LOADER_MATRIX_FIRST cut={cut} recovery={str(bool(index)).lower()} "
                              f"delegate={'LOADED' if prior else 'NO_FILE'} issued={prior['issued_through'] if prior else 0} "
                              f"records={records} pending={records}",
                              "ARCE_LOADER_MATRIX_PRIMED cut=LF_M_K5 tank=5000 epoch=0 stores=0",
                              f"ARCE_LOADER_MATRIX_PAUSED cut={cut} hook=BARRIER_FORCE_AFTER first_store=DESTINATION thread=fixture"]
                if log_error and index == 0: self.lines.append("[fixture/ERROR] failed")
            def wait_for(self, pattern, timeout): pass
            def abort(self): pass
            def command(self, command): pass
            def finish(self): return 0

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); sessions = root / "sessions"; sessions.mkdir()
            template = root / "template"; (template / "libraries").mkdir(parents=True)
            jar = root / "fixture.jar"; jar.write_bytes(b"fixture")
            args = SimpleNamespace(session_parent=sessions, source_server=template, jar=jar,
                                   java="fixture-java", source_commit="fixture-commit", startup_timeout=240)
            with mock.patch.object(runner, "assert_pin"), mock.patch.object(runner, "require_lock"), \
                    mock.patch.object(runner, "disk_space", return_value={"C:/": 11*1024**3, "D:/": 11*1024**3}), \
                    mock.patch.object(runner.smoke, "CapturedProcess", Process), \
                    mock.patch.object(runner.oracle, "inspect", side_effect=snapshots):
                result = runner.run_cut(args, cut, root / "evidence", "fixture-owner", {"jar_sha256": runner.digest(jar)})
            self.assertEqual("FAILED", result["status"])
            self.assertEqual([{"restart": 0, "error": "Native start logged ERROR/FATAL" if log_error
                               else "Wrong raw tank payload/quantity"}], result["findings"])
            self.assertTrue(all("disk" in start for start in result["starts"]))
            self.assertEqual([0, 1, 2], [start["restart"] for start in result["starts"]])
            self.assertEqual(3, len(processes))
            self.assertTrue(result["cleanup"])
            self.assertFalse(Path(result["session"]).exists())


if __name__ == "__main__": unittest.main()
