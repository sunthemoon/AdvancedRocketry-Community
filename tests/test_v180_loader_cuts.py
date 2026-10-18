"""W97 cut oracle and runner checks, without a JVM or native process."""
import copy
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import loader_cut_oracle as oracle
import run_v180_loader_cuts as runner


def fixture(cut, restart):
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
    for index in range(2 if bi else 1):
        prior = bi and index == 0; moved = 1 if prior else exp["source"]
        loaders.append(dict(id=oracle.LOADER, x=66+index, y=100, z=64, arce_fluid_fuel_loader=dict(
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


class LoaderCutsTest(unittest.TestCase):
    def test_matrix_counts_and_explicit_missing_baseline_failures(self):
        self.assertEqual(4, len(oracle.CREATION))
        self.assertEqual(14, len(oracle.NORMAL))
        self.assertEqual({"LF_M_N2_DEFINITE", "LF_M_N2_UNKNOWN", "LF_M_N3_PREFLIGHT"}, set(oracle.NOT_RUN))
        self.assertEqual(["K2_BI", "K6_BI", "K9_BI"], [c for c in oracle.NORMAL if c.endswith("BI")])

    def test_raw_cut_boundaries_are_not_status_aliases(self):
        # Source applied, destination applied, durable records, issued watermark.
        table = {"N0": (0, 0, 0, 0), "N1": (0, 0, 0, 0), "K0": (0, 0, 0, 0), "K1": (0, 0, 0, 0),
                 "K2": (0, 0, 1, 1), "K3": (0, 0, 1, 1), "K4": (1, 0, 1, 1), "K5": (0, 1, 1, 1),
                 "K6": (1, 1, 1, 1), "K7": (1, 1, 1, 1), "K8": (1, 1, 1, 1), "K9": (1, 1, 0, 1), "K10": (1, 1, 0, 1)}
        for cut, values in table.items():
            exp = oracle.expected("LF_M_"+cut, 0)
            self.assertEqual(values, tuple(exp[key] for key in ("source", "destination", "records", "issued")), cut)

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


if __name__ == "__main__": unittest.main()
