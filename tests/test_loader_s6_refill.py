"""Independent synthetic mono/BI successor fixtures; not native release evidence."""
import base64
import copy
import unittest
from test_loader_s6_forward import fixture as flight_fixture
import loader_s6_refill as refill
from loader_s6_refill import forward, flight, SmokeError


def native(frame):
    return dict(id=frame["type"], UUID=frame["physical"], Pos=frame["pos"], Motion=frame["motion"],
        Rotation=[frame["yaw"], frame["pitch"]], NoGravity=frame["no_gravity"], OnGround=frame["on_ground"], Invulnerable=frame["invulnerable"],
        RocketEntityData=dict(schema_version=3, assembly_transaction_id=frame["logical"], owner_id=frame["owner"],
            snapshot=frame["snapshot"], flight_data=frame["flight"], resource_escrow=frame["wrapper"]))


def sample(bi=False):
    original, _, _, _ = flight_fixture(); disk = original["native"]; banks = 2 if bi else 1
    source = refill.frame(disk["source"]); root = disk["ledger"]; row = disk["journal"]["transfers"][0]
    source["pos"][0] = 74.5; source["snapshot"]["source_origin"] = [74, 100, 64]; source["flight"]["current_origin"] = [74, 100, 64]
    descriptor = source["snapshot"]["propulsion"]
    if bi: descriptor.update(mode="BIPROPELLANT", oxidizer=2000)
    source["flight"]["fuel"] = refill.fuel(descriptor, 1000, banks, bi); source["wrapper"]["resource_epoch"] = banks
    root = dict(root, issued_through=banks, writer_generation=1 + 2 * banks, issued_launch_serial=0, armed_flights=[])
    devices = copy.deepcopy(disk["loaders"])
    devices[0].pop("keepPacked", None); devices[0]["x"] = 78
    device = devices[0]["arce_fluid_fuel_loader"]; device["binding"]["x"] = 74
    if bi:
        device["tank"]["FluidName"] = refill.FUEL
        other = copy.deepcopy(devices[0]); other["z"] = 66
        other["arce_fluid_fuel_loader"].update(device="00000000-0000-0000-0000-000000000055", tank=dict(FluidName=refill.OXYGEN, Amount=4000))
        devices.append(other)
    baseline = copy.deepcopy(dict(rocket=source, ledger=root, loaders=devices))
    arm = row["native_transfer"]["armed"]; arm.update(epoch=banks, g=2 + 2 * banks,
        source=list(bytes.fromhex(flight.integrity.digest(forward.native_tag(source)))))
    row["source_snapshot"] = source["snapshot"]; row["native_transfer"]["source"]["pos"] = [74.5, 100., 64.5]
    row["native_transfer"]["destination"]["pos"] = [82.5, 148., 64.5]
    plan = row["source_flight"]["plan"]; plan["propulsion_debit"].update(oxidizer=500 if bi else 0, revision=banks)
    row["source_flight"] = dict(copy.deepcopy(source["flight"]), state="TRANSIT", state_started_game_time=160, plan=plan, active_transfer_id=row["transfer_id"])
    row["destination_flight"] = dict(copy.deepcopy(row["source_flight"]), state="DESCENT", state_started_game_time=180,
        current_body="advancedrocketrycommunity:moon", current_origin=[82, 100, 64], current_target=plan["destination_target"],
        fuel=refill.fuel(descriptor, 500, banks + 1, bi, row["transfer_id"]))
    row["destination_snapshot"]["propulsion"] = descriptor; row["destination_snapshot"]["source_origin"] = [82, 100, 64]
    snapshot = row["destination_snapshot"]
    landed = copy.deepcopy(source); landed.update(physical=arm["d"], chunk=[5, 4], pos=[82.5, 100., 64.5],
        assembly_snapshot=snapshot["snapshot_id"], content_hash=snapshot["content_hash"], snapshot=snapshot,
        wrapper=refill.clean_wrapper(root["ledger_id"], 0), flight=copy.deepcopy(row["destination_flight"]))
    landed["flight"].update(state="LANDED", state_started_game_time=260); landed["flight"].pop("plan"); landed["flight"].pop("active_transfer_id")
    final = copy.deepcopy(landed); final["flight"]["fuel"] = refill.fuel(descriptor, 1500, 2 * banks + 1, bi, row["transfer_id"])
    final["wrapper"]["resource_epoch"] = banks
    for d in devices:
        value = d["arce_fluid_fuel_loader"]; value["binding"] = refill.binding(final, 82)
        value["tank"]["Amount"] = 3000; value["resource_escrow"]["resource_epoch"] = 2
    operations = [dict(sequence=i + 1, operation="00000000-0000-0000-0000-" + str(100 + i).zfill(12),
        device=devices[i % banks]["arce_fluid_fuel_loader"]["device"], destination=source["physical"] if i < banks else final["physical"],
        bank="OXIDIZER" if i % banks else "PRIMARY") for i in range(2 * banks)]
    witness = dict(baseline=baseline, journal=copy.deepcopy(disk["journal"]), transfer_digest=forward.transfer_digest(row), landed=landed,
        pruned=dict(root, writer_generation=4 + 2 * banks, issued_launch_serial=1), operations=operations,
        final_digest=flight.integrity.digest(forward.native_tag(final)))
    image = dict(read_status="KNOWN", native=dict(source=None, destination=native(final), loaders=devices,
        ledger=dict(root, issued_through=2 * banks, writer_generation=4 + 4 * banks, issued_launch_serial=1),
        journal=dict(disk["journal"], transfers=[])))
    return image, witness


class RefillOracleTests(unittest.TestCase):
    def test_bounded_native_uuid_captures_and_ordered_refusal_pruning_logs_are_required(self):
        image, witness = sample(True); cut = refill.CASES[1]; nbt = flight.nbt
        def tag(value, key=""):
            if key == "armed": return flight.armed_tag(value)
            if key in ("writer_generation", "issued_through", "issued_launch_serial"): return flight.integrity.scalar(value, nbt.LONG)
            if isinstance(value, dict): return flight.integrity.compound({k: tag(v, k) for k, v in value.items()})
            if isinstance(value, list) and key not in ("pos", "motion", "chunk", "source_origin", "current_origin", "bounding_box", "position", "passenger_anchors"):
                elements = [tag(v) for v in value]; return nbt.Tag(nbt.LIST, (elements[0].type if elements else 0, elements))
            return forward.native_tag(value, key)
        def encoded(value): return base64.b64encode(nbt.write(b"", tag(value))).decode()
        def operation(op): return "ARCE_LOADER_S6_REFILL_OPERATION cut=" + cut + " " + " ".join(k + "=" + str(v) for k, v in op.items())
        lines = [operation(op) for op in witness["operations"][:2]] + [
            "ARCE_LOADER_S6_REFILL_BASELINE cut=" + cut + " nbt=" + encoded(witness["baseline"]),
            "ARCE_LOADER_S6_REFILL_TRANSFER cut=" + cut + " digest=" + witness["transfer_digest"] + " nbt=" + encoded(witness["journal"]),
            "ARCE_LOADER_S6_REFILL_LANDED cut=" + cut + " nbt=" + encoded(witness["landed"]),
            "ARCE_LOADER_S6_REFILL_REFUSED cut=" + cut + " hook=F7_ACK digest=" + "a"*64,
            "ARCE_LOADER_S6_REFILL_REFUSED cut=" + cut + " hook=F8_JOURNAL_ACK digest=" + "b"*64,
            "ARCE_LOADER_S6_REFILL_PRUNED cut=" + cut + " nbt=" + encoded(witness["pruned"]),
            *[operation(op) for op in witness["operations"][2:]],
            "ARCE_LOADER_S6_REFILL_FINAL cut=" + cut + " digest=" + witness["final_digest"],
            "ARCE_LOADER_S6_FLIGHT_PAUSED cut=" + cut + " hook=SUCCESSOR_RETIRED"]
        refill.check_logs(lines, 0, cut)
        parsed = refill.witness([line + "\r\n" for line in lines], cut)
        refill.check(image, 0, None, cut, parsed)
        for index in range(len(lines)):
            with self.subTest(missing=index), self.assertRaises(SmokeError): refill.check_logs(lines[:index] + lines[index + 1:], 0, cut)
            with self.subTest(duplicate=index), self.assertRaises(SmokeError): refill.check_logs(lines[:index] + [lines[index]] + lines[index:], 0, cut)
        changed = list(lines); changed[7], changed[8] = changed[8], changed[7]
        with self.assertRaises(SmokeError): refill.check_logs(changed, 0, cut)

    def test_mono_and_bipropellant_preserve_each_fluid_and_full_state_across_two_restarts(self):
        for bi in (False, True):
            image, witness = sample(bi); before = copy.deepcopy(image); cut = refill.CASES[int(bi)]
            result = refill.check(image, 0, None, cut, witness)
            self.assertEqual(before, image); self.assertIsNone(result["available_total"])
            self.assertEqual(2 if bi else 1, len(result["per_fluid"]))
            identity = result["identity"]
            for restart in (1, 2):
                recovered = refill.check(image, restart, identity, cut, dict(final_digest=witness["final_digest"]))
                self.assertEqual(0, recovered["loss"]); self.assertEqual(identity, recovered["identity"])

    def test_unknown_quantity_typed_swap_generation_and_transfer_authority_are_rejected(self):
        for bi in (False, True):
            image, witness = sample(bi); cut = refill.CASES[int(bi)]
            for mutate in (lambda d: d.update(read_status="UNKNOWN"), lambda d: d["native"]["ledger"].update(writer_generation=99),
                           lambda d: d["native"]["ledger"].update(armed_flights=[{}]),
                           lambda d: d["native"]["journal"].update(transfers=[{}]),
                           lambda d: d["native"]["loaders"][0]["arce_fluid_fuel_loader"]["tank"].update(Amount=3001),
                           lambda d: d["native"]["loaders"][0]["arce_fluid_fuel_loader"]["tank"].update(FluidName=refill.OXYGEN),
                           lambda d: d["native"]["destination"]["RocketEntityData"]["flight_data"]["fuel"]["propulsion"].update(revision=99),
                           lambda d: d["native"]["destination"]["RocketEntityData"].update(extra="unexpected")):
                changed = copy.deepcopy(image); mutate(changed)
                with self.assertRaises(SmokeError): refill.check(changed, 0, None, cut, witness)

    def test_every_native_snapshot_binding_identity_and_clock_leaf_is_compared(self):
        image, witness = sample(True); cut = refill.CASES[1]
        identity = refill.check(image, 0, None, cut, witness)["identity"]
        def paths(value, prefix=()):
            if isinstance(value, dict):
                for k, v in value.items(): yield from paths(v, prefix + (k,))
            elif isinstance(value, list) and value:
                for k, v in enumerate(value): yield from paths(v, prefix + (k,))
            else: yield prefix
        for key in ("destination", "loaders", "ledger", "journal"):
            for path in paths(image["native"][key]):
                changed = copy.deepcopy(image); target = changed["native"][key]
                for k in path[:-1]: target = target[k]
                value = target[path[-1]]; target[path[-1]] = value + 1 if isinstance(value, (int, float)) else ["extra"] if isinstance(value, list) else value + "x"
                with self.subTest(key=key, path=path), self.assertRaises(SmokeError):
                    refill.check(changed, 1, identity, cut, dict(final_digest=witness["final_digest"]))

    def test_initial_retired_markers_must_name_the_exact_successor_and_are_pruned_on_restart(self):
        image, witness = sample(True); cut = refill.CASES[1]
        for i, device in enumerate(image["native"]["loaders"]):
            op = witness["operations"][2 + i]
            device["arce_fluid_fuel_loader"]["resource_escrow"]["markers"] = [dict(op_id=op["operation"], seq=op["sequence"], role="SOURCE", epoch_after=2)]
        op = witness["operations"][-1]
        image["native"]["destination"]["RocketEntityData"]["resource_escrow"]["markers"] = [dict(op_id=op["operation"], seq=4, role="DESTINATION", epoch_after=2)]
        before = copy.deepcopy(image); result = refill.check(image, 0, None, cut, witness); self.assertEqual(before, image)
        with self.assertRaises(SmokeError): refill.check(image, 1, result["identity"], cut, dict(final_digest=witness["final_digest"]))
        image["native"]["destination"]["RocketEntityData"]["resource_escrow"]["markers"][0]["seq"] = 3
        with self.assertRaises(SmokeError): refill.check(image, 0, None, cut, witness)

    def test_changed_checked_capture_and_nonpruned_authority_do_not_authorize_refill(self):
        image, witness = sample(); cut = refill.CASES[0]
        for mutate in (lambda w: w["journal"]["transfers"][0].update(checksum="0"*64),
                       lambda w: w["pruned"].update(armed_flights=[{}]),
                       lambda w: w["baseline"]["rocket"]["wrapper"].update(resource_epoch=0),
                       lambda w: w["operations"][-1].update(destination=w["operations"][0]["destination"]),
                       lambda w: w["landed"]["flight"]["fuel"].update(amount=501)):
            changed = copy.deepcopy(witness); mutate(changed)
            with self.assertRaises(SmokeError): refill.check(image, 0, None, cut, changed)


if __name__ == "__main__": unittest.main()
