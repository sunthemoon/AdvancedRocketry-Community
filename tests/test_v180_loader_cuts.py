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
    name = cut.removeprefix(oracle.PREFIX)
    if name in oracle.CHAINS:
        return chain_fixture(cut, restart)
    if name in oracle.FLOORS:
        return floors_fixture(cut, restart)
    if name in oracle.R3:
        return r3_fixture(cut, restart)
    disk = matrix_fixture(cut, restart)
    if name == "E2_EXTERNAL":
        disk["terrain_record"] = dict(external=True, compressed=1_572_900, decoded=1_650_000, sectors=1, compression=2)
        if restart:
            disk["ledger"]["records"][0].update(phase="STALLED", reason="BARRIER_EXTERNAL")
    return disk


def matrix_fixture(cut, restart):
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
        at = oracle.origin(cut)
        x, y, z = oracle.CAPACITY_POSITIONS[index] if capacity else (at+2+index, 100, at)
        loaders.append(dict(id=oracle.LOADER, x=x, y=y, z=z, arce_fluid_fuel_loader=dict(
            device="device"+str(index), owner="owner", binding=dict(physical_rocket="physical", logical_rocket="logical"),
            tank=dict(FluidName=oracle.HYDROGEN if prior else oracle.OXYGEN if bi else oracle.FUEL, Amount=5000-1000*moved),
            resource_escrow=section(moved, 0 if prior else moved*exp["markers"], "SOURCE"))))
    rocket = dict(id=oracle.ROCKET, UUID="physical", Pos=[oracle.origin(cut)+.5, 100.0, oracle.origin(cut)+.5], RocketEntityData=dict(
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


def floors_fixture(cut, restart):
    """R1: the matrix fill after the seeded watermark, the seeded floors, the landed records and the refused key."""
    name = cut.removeprefix(oracle.PREFIX); coalesced = name.endswith("COALESCED"); saturated = name.endswith("256")
    seeded = 256 if saturated else 255; fills = 2 if coalesced else 1
    disk = chain_fixture("LF_M_C2", restart) if coalesced else matrix_fixture("LF_M_K2_ACK", restart)
    root = disk["ledger"]; rocket = disk["rocket"]
    root["issued_through"] += seeded
    guard = dict(rocket_id="logical") if saturated else dict(rocket_id="logical", transfer_id="t1")
    for index, op in enumerate(root["records"]):
        op["seq"] = op.get("seq", index + 1) + seeded; op["transfer_guard"] = dict(guard)
    for section in [rocket["RocketEntityData"]["resource_escrow"]] + [d["arce_fluid_fuel_loader"]["resource_escrow"] for d in disk["loaders"]]:
        for marker in section["markers"]:
            marker["seq"] += seeded
    root["floors"] = [dict(schema_version=2, rocket_id=[0, 0, 1, index], highest_seq=index + 1, resource_epoch=1,
                           signature_kind="FUEL_VECTOR_V1") for index in range(seeded)]
    if restart and not saturated:
        root["floors"].append(dict(schema_version=2, rocket_id="logical", transfer_id="t1", entity_id="physical",
                                   resource_epoch=fills, highest_seq=seeded + fills, signature_kind="FUEL_VECTOR_V1"))
    transfers = [] if saturated else [dict(transfer_id="t1", phase="COMMITTED", logical_rocket_id="logical", destination_entity_id="physical")]
    extra = extra_loader = None
    if not coalesced:
        transfers.append(dict(transfer_id="t2", phase="COMMITTED", logical_rocket_id="logical2", destination_entity_id="physical2"))
        extra = dict(id=oracle.ROCKET, UUID="physical2", Pos=list(oracle.EXTRA_ROCKET_POS), RocketEntityData=dict(
            owner_id="owner", assembly_transaction_id="logical2", resource_escrow=dict(schema_version=1, resource_epoch=0, markers=[]),
            flight_data=dict(fuel=dict(propulsion=dict(primary=dict(amount=0), oxidizer=dict(amount=0))))))
        extra_loader = dict(id=oracle.LOADER, x=74, y=100, z=72, arce_fluid_fuel_loader=dict(
            owner="owner", device="device-extra", binding=dict(physical_rocket="physical2", logical_rocket="logical2"),
            tank=dict(FluidName=oracle.FUEL, Amount=5000), resource_escrow=dict(schema_version=1, resource_epoch=0, markers=[])))
    return dict(disk, transfers=transfers, extra_rocket=extra, extra_loader=extra_loader)


def r3_fixture(cut, restart):
    """R3: the K6 stop; for restarts, the offline-edited copy as ``edited`` and the restarted copy around it."""
    name = cut.removeprefix(oracle.PREFIX)
    stopped = matrix_fixture("LF_M_K6", 0)
    stopped["ledger"]["records"][0].update(seq=1, phase="PREPARED", reason="NONE", transfer_guard=dict(rocket_id="logical"))
    if not restart:
        return stopped
    edited = copy.deepcopy(stopped); loader = edited["loaders"][0]["arce_fluid_fuel_loader"]
    if name == "R3_LEDGER_MISSING":
        edited["ledger"] = None
    elif name in ("R3_LEDGER_REPLACED", "R3_LEDGER_BEHIND"):
        edited["ledger"].update(issued_through=0, records=[], writer_generation=1)
        if name == "R3_LEDGER_REPLACED":
            edited["ledger"]["ledger_id"] = "other"
    elif name == "R3_ENDPOINT_FOREIGN":
        loader["resource_escrow"]["markers"][0]["op_id"] = "foreign"
    elif name == "R3_ENDPOINT_OTHER":
        loader["tank"]["Amount"] = 3000
    elif name == "R3_ENDPOINT_MISSING":
        edited["loaders"] = []
    else:
        edited["loaders"][0]["x"] = 68
    restarted = copy.deepcopy(edited)
    if name.startswith("R3_ENDPOINT_"):
        restarted["ledger"]["records"][0].update(phase="REPAIR", reason="MISSING_ENDPOINT" if name.endswith(("MISSING", "RELOCATED"))
                                                 else "FOREIGN_MARKER" if name.endswith("FOREIGN") else "OTHER_STATE")
        restarted["ledger"]["writer_generation"] += 1
    restarted["edited"] = edited
    return restarted


class LoaderCutsTest(unittest.TestCase):
    def test_unknown_unload_requires_retained_candidate_and_native_unload(self):
        cut = "LF_M_R1_UNKNOWN_UNLOAD"
        lines = [f"ARCE_LOADER_MATRIX_FAILURE cut={cut} unknown=true records=0 pending=1 issued=0",
                 f"ARCE_LOADER_MATRIX_UNLOADED cut={cut} ticks=20 records=0 pending=1 issued=0 same_candidate=true"]
        runner.unload_observations(cut, lines)
        for bad in (lines[:1], lines[1:], [line.replace("pending=1", "pending=0") for line in lines],
                    [line.replace("same_candidate=true", "same_candidate=false") for line in lines]):
            with self.assertRaises(oracle.smoke.SmokeError): runner.unload_observations(cut, bad)
        for restart in range(3):
            disk = fixture(cut, restart)
            self.assertEqual(5000, disk["loaders"][0]["arce_fluid_fuel_loader"]["tank"]["Amount"])
            oracle.check(disk, cut, restart)
            disk["rocket"]["Pos"] = [64.5, 100., 64.5]
            with self.assertRaises(oracle.smoke.SmokeError): oracle.check(disk, cut, restart)

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

    def test_c1_fixture_save_must_write_exactly_the_required_live_prefix(self):
        line = ("[Server thread/INFO] [advancedrocketrycommunity/]: ARCE_LOADER_MATRIX_FIXTURE_SAVE cut=LF_M_C1 terrain=4,4 "
                "entities=4,4 tanks=[4000, 4000] epochs=[1, 1] source_markers=[1, 1] bank=1000 rocket_epoch=1 rocket_markers=1")
        runner.one_match(runner.FIXTURE_SAVE, [line])
        for old, new in (("tanks=[4000, 4000]", "tanks=[4000, 5000]"), ("bank=1000", "bank=0"),
                         ("rocket_epoch=1", "rocket_epoch=2"), ("source_markers=[1, 1]", "source_markers=[1, 0]")):
            with self.assertRaises(oracle.smoke.SmokeError): runner.one_match(runner.FIXTURE_SAVE, [line.replace(old, new)])
        with self.assertRaises(oracle.smoke.SmokeError): runner.one_match(runner.FIXTURE_SAVE, [line, line])
        # The unchanged oracle still demands the durable unequal prefix, not the stores W107 observed.
        good = fixture("LF_M_C1", 0); oracle.check(good, "LF_M_C1", 0)
        observed = copy.deepcopy(good); observed["loaders"][1]["arce_fluid_fuel_loader"]["tank"]["Amount"] = 5000
        with self.assertRaises(oracle.smoke.SmokeError): oracle.check(observed, "LF_M_C1", 0)

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
                    # As run_cut does, an R3 cut compares identities only between the restarts of its edited copy.
                    identity = None if cut in oracle.S5_R3 and restart == 0 else actual

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

    def test_w107c_storage_cuts_require_their_observed_failure_and_e2s_external_hold(self):
        error = oracle.smoke.SmokeError
        e1 = ["ARCE_LOADER_MATRIX_STORE_FAULT cut=LF_M_E1_OWN_STORE chunk=4,4",
              "ARCE_LOADER_MATRIX_PROOF_FAILED cut=LF_M_E1_OWN_STORE reason=BARRIER_IO io_failures=1 refusals=0 superseding=0 records=1 pending=1"]
        runner.storage_observations("LF_M_E1_OWN_STORE", e1); runner.w107c_observations("LF_M_E1_OWN_STORE", 0, e1)
        runner.w107c_observations("LF_M_E1_OWN_STORE", 1, [])
        for bad in ([e1[1].replace("BARRIER_IO", "SUPERSEDING_SAVE")], [e1[1].replace("refusals=0", "refusals=1")],
                    [e1[1].replace("superseding=0", "superseding=1")], [e1[1].replace("io_failures=1", "io_failures=0")], [], e1 + e1[1:]):
            with self.assertRaises(error): runner.storage_observations("LF_M_E1_OWN_STORE", bad)
        with self.assertRaises(error): runner.w107c_observations("LF_M_E1_OWN_STORE", 1, e1)
        with self.assertRaises(error): runner.w107c_observations("LF_M_E1_OWN_STORE", 0, e1[1:])
        e2 = ["ARCE_LOADER_MATRIX_NOISE cut=LF_M_E2_EXTERNAL pos=76,100,76 bytes=1572864",
              "ARCE_LOADER_MATRIX_PROOF_FAILED cut=LF_M_E2_EXTERNAL reason=BARRIER_EXTERNAL io_failures=0 refusals=1 superseding=0 records=1 pending=1"]
        runner.storage_observations("LF_M_E2_EXTERNAL", e2)
        for bad in ([e2[0], e2[1].replace("BARRIER_EXTERNAL", "BARRIER_BOUNDS")], e2[1:], [e2[0], e2[1].replace("io_failures=0", "io_failures=1")]):
            with self.assertRaises(error): runner.storage_observations("LF_M_E2_EXTERNAL", bad)
        held = ["ARCE_LOADER_MATRIX_HELD cut=LF_M_E2_EXTERNAL reason=BARRIER_EXTERNAL phase=STALLED records=1 issued=1 pending=1 "
                "refusals=3 io_failures=0 tank=4000 bank=1000"]
        runner.w107c_observations("LF_M_E2_EXTERNAL", 1, held)
        for bad in ([], [held[0].replace("STALLED", "PREPARED")], [held[0].replace("refusals=3", "refusals=0")],
                    held + ["ARCE_LOADER_MATRIX_HOLD_TIMEOUT cut=LF_M_E2_EXTERNAL ticks=2300 state=PREPARED/NONE"]):
            with self.assertRaises(error): runner.w107c_observations("LF_M_E2_EXTERNAL", 2, bad)
        self.assertEqual(oracle.expected("LF_M_K6", 0), {k: v for k, v in oracle.expected("LF_M_E2_EXTERNAL", 1).items() if k != "held"})
        for restart in (1, 2):
            good = fixture("LF_M_E2_EXTERNAL", restart); oracle.check(good, "LF_M_E2_EXTERNAL", restart)
            for mutate in (lambda d: d["ledger"]["records"][0].update(phase="PREPARED"),
                           lambda d: d["ledger"]["records"][0].update(reason="BARRIER_BOUNDS"),
                           lambda d: d["ledger"].update(records=[]), lambda d: d["terrain_record"].update(external=False),
                           lambda d: d["terrain_record"].update(decoded=4_194_305),
                           lambda d: d["loaders"][0]["arce_fluid_fuel_loader"]["resource_escrow"].update(markers=[])):
                bad = copy.deepcopy(good); mutate(bad)
                with self.assertRaises(error): oracle.check(bad, "LF_M_E2_EXTERNAL", restart)

    def test_floor_cuts_keep_seeded_floors_add_one_named_key_and_never_change_the_refused_key(self):
        error = oracle.smoke.SmokeError
        for cut in oracle.S5_FLOORS:
            named = not cut.endswith("256")
            for restart in range(3):
                good = fixture(cut, restart); oracle.check(good, cut, restart)
                mutations = [lambda d: d["ledger"]["floors"].pop(0), lambda d: d["ledger"]["floors"][3].update(resource_epoch=2),
                             lambda d: d["ledger"]["floors"][3].update(highest_seq=999), lambda d: d["ledger"].update(issued_through=1),
                             lambda d: d["transfers"][0].update(phase="PREPARED"), lambda d: d["transfers"].pop(),
                             lambda d: d["ledger"]["floors"].append(dict(d["ledger"]["floors"][0], transfer_id="t9", rocket_id="other"))]
                if good["ledger"]["records"]:
                    mutations.append(lambda d: d["ledger"]["records"][0]["transfer_guard"].update(transfer_id="t9"))
                if good["extra_rocket"] is not None:
                    mutations += [lambda d: d["extra_loader"]["arce_fluid_fuel_loader"]["tank"].update(Amount=4000),
                                  lambda d: d["extra_rocket"]["RocketEntityData"]["resource_escrow"].update(resource_epoch=1)]
                if restart and named:
                    mutations += [lambda d: d["ledger"]["floors"].pop(), lambda d: d["ledger"]["floors"][-1].update(highest_seq=1),
                                  lambda d: d["ledger"]["floors"][-1].update(entity_id="other")]
                for index, mutate in enumerate(mutations):
                    bad = copy.deepcopy(good); mutate(bad)
                    with self.subTest(cut=cut, restart=restart, mutation=index), self.assertRaises(error): oracle.check(bad, cut, restart)
        # A held named fill after a restart is a raw finding, never a pass.
        held = fixture("LF_M_R1_FLOORS_255_DISTINCT", 0)
        with self.assertRaises(error): oracle.check(held, "LF_M_R1_FLOORS_255_DISTINCT", 1)

    def test_floor_observations_pin_reservations_refusals_and_the_one_new_floor(self):
        error = oracle.smoke.SmokeError
        cut = "LF_M_R1_FLOORS_255_DISTINCT"

        def refused(phase, used, reserved, next_):
            return f"ARCE_LOADER_MATRIX_FLOOR_REFUSED cut={cut} phase={phase} status=ESCROW_FULL used={used} reserved={reserved} total=256 next={next_}"
        start = [f"ARCE_LOADER_MATRIX_FLOORS cut={cut} seeded=255 named=true extra_named=true ledger=abc",
                 f"ARCE_LOADER_MATRIX_FLOOR_ADMIT cut={cut} phase=first named=true used=255 reserved=1 total=256", refused("start", 255, 1, 257)]
        settled = f"ARCE_LOADER_MATRIX_FLOOR_SETTLED cut={cut} fills=1 floors=256 named_floor=epoch1_seq256 used=256 reserved=0"
        first = [refused("restart_pending", 255, 1, 257), settled, refused("restart_settled", 256, 0, 257)]
        runner.w107c_observations(cut, 0, start); runner.w107c_observations(cut, 1, first); runner.w107c_observations(cut, 2, first[1:])
        for restart, bad in ((0, start[:2]), (0, [start[0], start[1].replace("reserved=1", "reserved=2"), start[2]]), (0, start[1:]),
                             (1, first[1:]), (1, [first[0], first[1].replace("epoch1", "epoch2"), first[2]]), (2, first),
                             (1, first + [f"ARCE_LOADER_MATRIX_FLOOR_UNSETTLED cut={cut} ticks=2300 records=1"])):
            with self.subTest(restart=restart, bad=bad), self.assertRaises(error): runner.w107c_observations(cut, restart, bad)
        cut = "LF_M_R1_FLOORS_255_COALESCED"
        runner.w107c_observations(cut, 0, [f"ARCE_LOADER_MATRIX_FLOORS cut={cut} seeded=255 named=true extra_named=false ledger=abc",
                                           f"ARCE_LOADER_MATRIX_FLOOR_ADMIT cut={cut} phase=first named=true used=255 reserved=1 total=256",
                                           f"ARCE_LOADER_MATRIX_FLOOR_ADMIT cut={cut} phase=same_key named=true used=255 reserved=1 total=256"])
        with self.assertRaises(error):
            runner.w107c_observations(cut, 0, [f"ARCE_LOADER_MATRIX_FLOORS cut={cut} seeded=255 named=true extra_named=false ledger=abc",
                                               f"ARCE_LOADER_MATRIX_FLOOR_ADMIT cut={cut} phase=first named=true used=255 reserved=1 total=256",
                                               f"ARCE_LOADER_MATRIX_FLOOR_ADMIT cut={cut} phase=same_key named=true used=255 reserved=2 total=257"])
        runner.w107c_observations(cut, 2, [f"ARCE_LOADER_MATRIX_FLOOR_SETTLED cut={cut} fills=2 floors=256 named_floor=epoch2_seq257 used=256 reserved=0"])
        cut = "LF_M_R1_FLOORS_256"
        runner.w107c_observations(cut, 0, [f"ARCE_LOADER_MATRIX_FLOORS cut={cut} seeded=256 named=false extra_named=true ledger=abc",
                                           f"ARCE_LOADER_MATRIX_FLOOR_ADMIT cut={cut} phase=first named=false used=256 reserved=0 total=256",
                                           f"ARCE_LOADER_MATRIX_FLOOR_REFUSED cut={cut} phase=start status=ESCROW_FULL used=256 reserved=0 total=256 next=258"])
        runner.w107c_observations(cut, 1, [f"ARCE_LOADER_MATRIX_FLOOR_REFUSED cut={cut} phase=restart_pending status=ESCROW_FULL used=256 reserved=0 total=256 next=258",
                                           f"ARCE_LOADER_MATRIX_FLOOR_SETTLED cut={cut} fills=1 floors=256 named_floor=NONE used=256 reserved=0",
                                           f"ARCE_LOADER_MATRIX_FLOOR_REFUSED cut={cut} phase=restart_settled status=ESCROW_FULL used=256 reserved=0 total=256 next=258"])

    def test_r3_restarts_compare_with_the_edited_copy_and_require_a_lock_or_a_manual_repair(self):
        error = oracle.smoke.SmokeError
        for cut in oracle.S5_R3:
            name = cut.removeprefix(oracle.PREFIX)
            self.assertEqual(oracle.expected("LF_M_K6", 0), oracle.expected(cut, 0))
            with self.assertRaises(error): oracle.expected(cut, 1)
            for restart in (1, 2):
                good = fixture(cut, restart); oracle.check(good, cut, restart)
                mutations = [lambda d: d["rocket"]["RocketEntityData"]["resource_escrow"].update(markers=[]),
                             lambda d: d["rocket"]["RocketEntityData"]["flight_data"]["fuel"]["propulsion"]["primary"].update(amount=2000)]
                if good["loaders"]:
                    mutations.append(lambda d: d["loaders"][0]["arce_fluid_fuel_loader"]["resource_escrow"].update(markers=[]))
                if name.startswith("R3_ENDPOINT_"):
                    mutations += [lambda d: d["ledger"]["records"][0].update(phase="PREPARED", reason="NONE"),
                                  lambda d: d["ledger"]["records"][0].update(phase="STALLED", reason="BARRIER_IO"),
                                  lambda d: d["ledger"].update(records=[]), lambda d: d["ledger"].update(issued_through=2),
                                  lambda d: d["ledger"]["records"][0].update(op_id="other")]
                elif name == "R3_LEDGER_MISSING":
                    mutations.append(lambda d: d.update(ledger=copy.deepcopy(fixture("LF_M_K6", 0)["ledger"])))
                else:
                    mutations += [lambda d: d["ledger"].update(records=[{}]), lambda d: d["ledger"].update(ledger_id="third")]
                for index, mutate in enumerate(mutations):
                    bad = copy.deepcopy(good); mutate(bad)
                    with self.subTest(cut=cut, restart=restart, mutation=index), self.assertRaises(error): oracle.check(bad, cut, restart)

    def test_r3_observations_pin_the_repair_or_the_orphan_lock_and_the_captured_generation(self):
        error = oracle.smoke.SmokeError
        repair = ("ARCE_LOADER_MATRIX_REPAIR cut=LF_M_R3_ENDPOINT_OTHER reason=OTHER_STATE phase=REPAIR records=1 issued=1 pending=1 "
                  "loader=66 tank=3000 loader_markers=1 rocket_markers=1 bank=1000 attempt=ESCROW_PENDING")
        runner.w107c_observations("LF_M_R3_ENDPOINT_OTHER", 1, [repair])
        for bad in ([], [repair.replace("OTHER_STATE", "BARRIER_IO")], [repair.replace("tank=3000", "tank=4000")],
                    [repair.replace("attempt=ESCROW_PENDING", "attempt=READY")], [repair, repair]):
            with self.assertRaises(error): runner.w107c_observations("LF_M_R3_ENDPOINT_OTHER", 1, bad)
        missing = ("ARCE_LOADER_MATRIX_REPAIR cut=LF_M_R3_ENDPOINT_MISSING reason=MISSING_ENDPOINT phase=REPAIR records=1 issued=1 pending=1 "
                   "loader=ABSENT tank=-1 loader_markers=-1 rocket_markers=1 bank=1000 attempt=NONE")
        runner.w107c_observations("LF_M_R3_ENDPOINT_MISSING", 2, [missing])
        orphan = ("ARCE_LOADER_MATRIX_ORPHAN cut=LF_M_R3_LEDGER_BEHIND ledger=BEHIND binding=SAME issued=0 marker_seq=1 records=0 "
                  "loader_markers=1 rocket_markers=1 tank=4000 bank=1000 attempt=ESCROW_PENDING")
        runner.w107c_observations("LF_M_R3_LEDGER_BEHIND", 1, [orphan])
        for bad in ([orphan.replace("binding=SAME", "binding=OTHER")], [orphan.replace("issued=0", "issued=1")], []):
            with self.assertRaises(error): runner.w107c_observations("LF_M_R3_LEDGER_BEHIND", 1, bad)
        captured = "ARCE_LOADER_MATRIX_LEDGER_CAPTURED cut=LF_M_R3_LEDGER_BEHIND generation=1 bytes=321 sha256=" + "a" * 64
        runner.w107c_observations("LF_M_R3_LEDGER_BEHIND", 0, [captured])
        runner.w107c_observations("LF_M_R3_LEDGER_MISSING", 0, [])
        for bad in ([], [captured, captured], [captured, "ARCE_LOADER_MATRIX_LEDGER_CAPTURE_FAILED cut=LF_M_R3_LEDGER_BEHIND failure=x"]):
            with self.assertRaises(error): runner.w107c_observations("LF_M_R3_LEDGER_BEHIND", 0, bad)

    def test_r3_cut_edits_the_stopped_copy_once_and_restarts_compare_with_it(self):
        cut = "LF_M_R3_ENDPOINT_FOREIGN"
        stopped, restarted = fixture(cut, 0), fixture(cut, 1)
        edited = restarted.pop("edited"); second = copy.deepcopy(restarted)
        snapshots = [stopped, edited, restarted, second]; processes = []
        repair = ("ARCE_LOADER_MATRIX_REPAIR cut=LF_M_R3_ENDPOINT_FOREIGN reason=FOREIGN_MARKER phase=REPAIR records=1 issued=1 pending=1 "
                  "loader=66 tank=4000 loader_markers=1 rocket_markers=1 bank=1000 attempt=ESCROW_PENDING")

        class Process:
            def __init__(self, command, session, log):
                index = len(processes); processes.append(self); self.process = mock.Mock()
                prior = (None, edited["ledger"], restarted["ledger"])[index]
                self.lines = [f"ARCE_LOADER_MATRIX_FIRST cut={cut} recovery={str(bool(index)).lower()} "
                              f"delegate={'LOADED' if prior else 'NO_FILE'} issued={prior['issued_through'] if prior else 0} "
                              f"records={len(prior['records']) if prior else 0} pending={len(prior['records']) if prior else 0}"]
                self.lines.append(f"ARCE_LOADER_MATRIX_PAUSED cut={cut} hook=BARRIER_FORCE_BEFORE first_store=SOURCE thread=fixture"
                                  if index == 0 else repair)
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
            manifest = dict(cut="R3_ENDPOINT_FOREIGN", edits=[dict(file="region/r.0.0.mca")])
            with mock.patch.object(runner, "assert_pin"), mock.patch.object(runner, "require_lock"), \
                    mock.patch.object(runner, "disk_space", return_value={"C:/": 11*1024**3, "D:/": 11*1024**3}), \
                    mock.patch.object(runner.smoke, "CapturedProcess", Process), \
                    mock.patch.object(runner.oracle, "inspect", side_effect=snapshots), \
                    mock.patch.object(runner.nbt, "r3_edit", return_value=manifest) as edit:
                result = runner.run_cut(args, cut, root / "evidence", "fixture-owner", {"jar_sha256": runner.digest(jar)})
            self.assertEqual("PASS", result["status"], result.get("findings"))
            edit.assert_called_once()
            self.assertEqual(("R3_ENDPOINT_FOREIGN", root / "evidence" / "edit-original"), edit.call_args.args[1:])
            self.assertEqual(manifest, result["edit"]); self.assertEqual(edited, result["edited"])
            self.assertEqual(3, len(processes)); self.assertTrue(result["cleanup"])

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
