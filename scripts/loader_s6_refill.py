"""Strict mono/BI successor refill; complete witnessed state, not scalar totals."""
import copy
import re
import loader_s6_terminal as terminal
from loader_s6_terminal import forward, flight, legacy, SmokeError

CASES = ("LF_S6_REFILL", "LF_S6_REFILL_BI")
FUEL = "advancedrocketrycommunity:hydrogen"
OXYGEN = "advancedrocketrycommunity:oxygen"


def frame(rocket):
    data = rocket["RocketEntityData"]; state = data["flight_data"]; snapshot = data["snapshot"]
    legacy.require(set(data) == {"schema_version", "assembly_transaction_id", "owner_id", "snapshot", "flight_data", "resource_escrow"},
                   "Refill changed entity data shape")
    return dict(schema_version=1, physical=rocket["UUID"], type=rocket["id"], logical=data["assembly_transaction_id"],
        owner=data["owner_id"], level=state["current_dimension"], chunk=[int(rocket["Pos"][0]) // 16, int(rocket["Pos"][2]) // 16],
        pos=rocket["Pos"], motion=rocket["Motion"], no_gravity=rocket["NoGravity"], no_physics=1,
        on_ground=rocket["OnGround"], invulnerable=rocket["Invulnerable"], yaw=rocket["Rotation"][0], pitch=rocket["Rotation"][1],
        passengers=[], assembly_snapshot=snapshot["snapshot_id"], content_hash=snapshot["content_hash"],
        snapshot=snapshot, flight=state, wrapper=data["resource_escrow"])


def generation(fills, flown=False): return 1 + 2 * fills + 3 * int(flown)


def bank(amount, fluid): return dict(amount=amount, branch="FLUID", fluid=fluid) if amount else dict(amount=0, branch="EMPTY")


def fuel(descriptor, amount, revision, bi, transfer=None):
    return dict(capacity=2000, amount=amount, committed_debits=[] if transfer is None else [dict(transaction_id=transfer)],
        propulsion=dict(schema=1, descriptor=descriptor, primary=bank(amount, FUEL if bi else legacy.FUEL),
            oxidizer=bank(amount if bi else 0, OXYGEN), revision=revision))


def binding(rocket, x):
    return dict(schema_version=1, logical_rocket=rocket["logical"], physical_rocket=rocket["physical"],
                level="minecraft:overworld", x=x, y=100, z=64)


def clean_wrapper(ledger, epoch): return dict(schema_version=1, ledger_id=ledger, resource_epoch=epoch, markers=[])


def baseline(witness, bi):
    capture = forward.normalize(witness["baseline"]); source = capture["rocket"]; root = capture["ledger"]
    banks = 2 if bi else 1; descriptor = copy.deepcopy(flight.integrity.MONO_DESCRIPTOR)
    if bi: descriptor.update(mode="BIPROPELLANT", oxidizer=2000)
    legacy.require(set(root) == {"schema_version", "format_epoch", "ledger_id", "issued_through", "writer_generation",
        "records", "floors", "tombstones", "rocket_authorities", "armed_flights", "issued_launch_serial"}
        and root["schema_version"] == 2 and root["format_epoch"] == "v1.8.0-resource-escrow"
        and root["issued_through"] == banks and root["writer_generation"] == generation(banks)
        and root["issued_launch_serial"] == 0
        and not any(root[k] for k in ("records", "floors", "tombstones", "rocket_authorities", "armed_flights")),
        "Refill baseline changed complete ledger authority")
    state = source["flight"]; snapshot = source["snapshot"]
    legacy.require(source["schema_version"] == 1 and source["type"] == legacy.ROCKET and source["level"] == "minecraft:overworld"
        and source["chunk"] == [4, 4] and source["pos"] == [74.5, 100., 64.5] and source["motion"] == [0., 0., 0.]
        and source["no_gravity"] == source["no_physics"] == 1 and source["on_ground"] == source["invulnerable"] == 0
        and source["yaw"] == source["pitch"] == 0 and source["passengers"] == []
        and source["assembly_snapshot"] == snapshot["snapshot_id"] and source["content_hash"] == snapshot["content_hash"]
        and snapshot["propulsion"] == descriptor and snapshot["source_origin"] == [74, 100, 64]
        and state["schema_version"] == 2 and state["logical_rocket_id"] == source["logical"]
        and state["state"] == "ASSEMBLED" and state["current_origin"] == [74, 100, 64]
        and state["current_body"] == "advancedrocketrycommunity:earth" and state["current_dimension"] == source["level"]
        and state["passengers"] == dict(assignments=[], seat_capacity=1) and not any(k in state for k in ("plan", "active_transfer_id"))
        and state["fuel"] == fuel(descriptor, 1000, banks, bi)
        and source["wrapper"] == clean_wrapper(root["ledger_id"], banks), "Refill baseline changed full typed source")
    devices = capture["loaders"]
    legacy.require(len(devices) == banks, "Refill baseline has wrong device count")
    for index, device in enumerate(devices):
        actual = device["arce_fluid_fuel_loader"]
        legacy.require([device[k] for k in ("x", "y", "z")] == [78, 100, 64 + 2 * index] and device["id"] == legacy.LOADER
            and actual["schema_version"] == 1 and actual["owner"] == source["owner"]
            and actual["binding"] == binding(source, 74)
            and actual["tank"] == dict(FluidName=OXYGEN if index else FUEL if bi else legacy.FUEL, Amount=4000)
            and actual["resource_escrow"] == clean_wrapper(root["ledger_id"], 1), "Refill baseline changed full loader")
    legacy.require(len({d["arce_fluid_fuel_loader"]["device"] for d in devices}) == banks, "Duplicated refill device identity")
    return capture, descriptor


def expected(witness, bi):
    captured, descriptor = baseline(witness, bi); source = captured["rocket"]; root = captured["ledger"]; banks = 2 if bi else 1
    journal = forward.normalize(witness["journal"])
    legacy.require(len(journal["transfers"]) == 1, "Refill lost checked PREPARED T")
    row = journal["transfers"][0]; arm = row["native_transfer"]["armed"]
    legacy.require(forward.transfer_digest(row) == witness["transfer_digest"] and row["phase"] == "PREPARED"
        and row["schema_version"] == 3 and row["required_fuel"] == 500 and row["source_entity_id"] == source["physical"]
        and row["logical_rocket_id"] == source["logical"] and row["owner_id"] == source["owner"]
        and row["source_snapshot"] == source["snapshot"] and arm["outcome"] == "ARMED" and arm["epoch"] == banks
        and arm["g"] == generation(banks) + 1 and arm["serial"] == 1 and arm["release"] == 0
        and forward.s6.canonical_uuid(arm["t"]) == row["transfer_id"] and forward.s6.canonical_uuid(arm["src"]) == source["physical"]
        and bytes(arm["source"]).hex() == flight.integrity.digest(forward.native_tag(source)), "Refill changed checked flight binding")
    start = row["created_at_game_time"]; transit = copy.deepcopy(source["flight"]); plan = row["source_flight"]["plan"]
    transit.update(state="TRANSIT", state_started_game_time=start + 140, plan=plan, active_transfer_id=row["transfer_id"])
    descent = copy.deepcopy(transit); descent.update(state="DESCENT", state_started_game_time=start + 160,
        current_body="advancedrocketrycommunity:moon", current_origin=[82, 100, 64], current_target=plan["destination_target"],
        fuel=fuel(descriptor, 500, banks + 1, bi, row["transfer_id"]))
    legacy.require(start >= source["flight"]["state_started_game_time"] and transit == row["source_flight"]
        and descent == row["destination_flight"] and plan["propulsion_debit"]["primary"] == 500
        and plan["propulsion_debit"]["oxidizer"] == (500 if bi else 0) and plan["propulsion_debit"]["revision"] == banks,
        "Refill changed typed debit, transit or descent")
    snapshot = copy.deepcopy(source["snapshot"])
    snapshot.update(snapshot_id=row["destination_snapshot"]["snapshot_id"], source_origin=[82, 100, 64],
        created_at_game_time=start, content_hash=row["destination_snapshot"]["content_hash"])
    legacy.require(snapshot == row["destination_snapshot"] and snapshot["snapshot_id"] != source["snapshot"]["snapshot_id"],
                   "Refill changed relocated structure")
    landed = forward.normalize(witness["landed"]); arrival = copy.deepcopy(source)
    arrival.update(physical=forward.s6.canonical_uuid(arm["d"]), chunk=[5, 4], pos=[82.5, 100., 64.5],
        assembly_snapshot=snapshot["snapshot_id"], content_hash=snapshot["content_hash"], snapshot=snapshot,
        wrapper=clean_wrapper(root["ledger_id"], 0))
    descent.update(state="LANDED", state_started_game_time=landed["flight"]["state_started_game_time"])
    descent.pop("plan"); descent.pop("active_transfer_id"); arrival["flight"] = descent
    legacy.require(arrival == landed and descent["state_started_game_time"] >= start + 240, "Refill changed complete landed survivor")
    arrival = copy.deepcopy(arrival); arrival["wrapper"] = clean_wrapper(root["ledger_id"], banks)
    arrival["flight"]["fuel"] = fuel(descriptor, 1500, 2 * banks + 1, bi, row["transfer_id"])
    devices = copy.deepcopy(captured["loaders"])
    for device in devices:
        value = device["arce_fluid_fuel_loader"]; value["binding"] = binding(arrival, 82)
        value["tank"]["Amount"] = 3000; value["resource_escrow"] = clean_wrapper(root["ledger_id"], 2)
    root = dict(root, issued_through=2 * banks, writer_generation=generation(2 * banks, True), issued_launch_serial=1)
    legacy.require(forward.normalize(witness["pruned"]) == dict(root, issued_through=banks, writer_generation=generation(banks, True)),
                   "Refill admitted before exact F8 terminal pruning")
    journal = dict(journal, transfers=[])
    legacy.require(journal == dict(schema_version=3, transfers=[], format_epoch="v1.8.0-first-moon-landing",
        classic_first_moon_landing=dict(schema_version=1, state="EMPTY")), "Refill changed empty journal")
    legacy.require(all(op["destination"] == source["physical"] for op in witness["operations"][:banks]),
                   "Refill initial operations named another physical source")
    return dict(survivor=arrival, loaders=devices, ledger=root, journal=journal, operations=witness["operations"])


def check(image, restart, identity, cut, witness):
    legacy.require(cut in CASES and restart in (0, 1, 2) and image.get("read_status") == "KNOWN", "UNKNOWN or unsupported refill image")
    try:
        banks = 2 if cut.endswith("_BI") else 1; target = identity if restart else expected(witness, banks == 2)
        legacy.require(target is not None, "Refill restart lost its complete initial identity")
        disk = forward.normalize(image["native"]); survivor = disk["destination"]
        legacy.require(disk["source"] is None and survivor is not None and disk["ledger"] == target["ledger"]
            and disk["journal"] == target["journal"] and len(disk["loaders"]) == banks
            and survivor["RocketEntityData"]["schema_version"] == 3 and not survivor.get("Passengers"), "Refill terminal authority changed")
        observed = copy.deepcopy(frame(survivor)); devices = sorted(disk["loaders"], key=lambda d: d["z"])
        operations = target["operations"]
        legacy.require(len(operations) == 2 * banks and len({op["operation"] for op in operations}) == 2 * banks, "Refill did not record each actual fill")
        for index, op in enumerate(operations):
            device = target["loaders"][index % banks]["arce_fluid_fuel_loader"]
            legacy.require(op["sequence"] == index + 1 and op["device"] == device["device"]
                and op["bank"] == ("OXIDIZER" if index % banks else "PRIMARY")
                and (index < banks or op["destination"] == observed["physical"]), "Refill operation identity or bank changed")
        def wrapper(actual, expected, op, role):
            marked = dict(expected, markers=[dict(op_id=op["operation"], seq=op["sequence"], role=role,
                                                  epoch_after=expected["resource_epoch"])])
            legacy.require(actual == expected or not restart and actual == marked, "Refill changed endpoint epoch or retired marker")
        for index, (device, expected_device) in enumerate(zip(devices, target["loaders"])):
            actual = copy.deepcopy(device); actual.pop("keepPacked", None)
            wrapper(actual["arce_fluid_fuel_loader"]["resource_escrow"], expected_device["arce_fluid_fuel_loader"]["resource_escrow"],
                    operations[banks + index], "SOURCE")
            actual["arce_fluid_fuel_loader"]["resource_escrow"]["markers"] = []
            legacy.require(actual == expected_device, "Refill changed full device, binding, tank or metadata")
        wrapper(observed["wrapper"], target["survivor"]["wrapper"], operations[-1], "DESTINATION")
        observed["wrapper"]["markers"] = []
        legacy.require(observed == target["survivor"] and flight.integrity.digest(forward.native_tag(observed)) == witness["final_digest"],
                       "Refill changed complete survivor or live witness")
        return dict(identity=target, raw_total=4500 * banks, available_total=4500 * banks if restart else None,
                    authorized_debit=500 * banks, loss=0 if restart else None,
                    per_fluid={device["arce_fluid_fuel_loader"]["tank"]["FluidName"]: dict(loader=3000, rocket=1500, debit=500)
                               for device in target["loaders"]})
    except (KeyError, TypeError, IndexError, ValueError, OverflowError) as failure:
        raise SmokeError("Malformed refill observation: " + str(failure)) from failure


def witness(lines, cut, restart=False):
    def exact(label, expression):
        prefix = "ARCE_LOADER_S6_REFILL_" + label + " cut=" + cut + " "
        matches = [(i, re.search(re.escape(prefix) + expression + "$", line.rstrip("\r\n")))
                   for i, line in enumerate(lines) if prefix in line]
        legacy.require(len(matches) == 1 and matches[0][1], "Missing/duplicate/malformed refill witness: " + label)
        return matches[0]
    end_at, end = exact("FINAL", r"digest=([0-9a-f]{64})")
    result = dict(final_digest=end.group(1))
    if restart: return result
    base_at, base = exact("BASELINE", r"nbt=([A-Za-z0-9+/=]+)")
    row_at, row = exact("TRANSFER", r"digest=([0-9a-f]{64}) nbt=([A-Za-z0-9+/=]+)")
    landed_at, landed = exact("LANDED", r"nbt=([A-Za-z0-9+/=]+)")
    pruned_at, pruned = exact("PRUNED", r"nbt=([A-Za-z0-9+/=]+)")
    refusals = []
    for boundary in ("F7_ACK", "F8_JOURNAL_ACK"):
        prefix = "ARCE_LOADER_S6_REFILL_REFUSED cut=" + cut + " hook=" + boundary + " "
        matches = [(i, re.search(re.escape(prefix) + r"digest=([0-9a-f]{64})$", line.rstrip("\r\n"))) for i, line in enumerate(lines) if prefix in line]
        legacy.require(len(matches) == 1 and matches[0][1], "Missing exact terminal refill refusal")
        refusals.append(matches[0][0])
    operations = []; places = []; prefix = "ARCE_LOADER_S6_REFILL_OPERATION cut=" + cut + " "
    for at, line in enumerate(lines):
        if prefix not in line: continue
        match = re.search(re.escape(prefix) + r"sequence=(\d+) operation=(\S+) device=(\S+) destination=(\S+) bank=(PRIMARY|OXIDIZER)$", line.rstrip("\r\n"))
        legacy.require(match is not None, "Malformed actual refill operation")
        sequence, operation, device, destination, bank_name = match.groups()
        operations.append(dict(sequence=int(sequence), operation=operation, device=device, destination=destination, bank=bank_name)); places.append(at)
    banks = 2 if cut.endswith("_BI") else 1
    legacy.require(len(operations) == 2 * banks and max(places[:banks]) < base_at < row_at < landed_at < refusals[0] < refusals[1]
        < pruned_at < min(places[banks:]) <= max(places[banks:]) < end_at, "Misordered refill, authority or refusal witnesses")
    result.update(baseline=terminal.decode_capture(base.group(1)), journal=terminal.decode_capture(row.group(2)),
        transfer_digest=row.group(1), landed=terminal.decode_capture(landed.group(1)), pruned=terminal.decode_capture(pruned.group(1)), operations=operations)
    return result


def check_logs(lines, restart, cut):
    legacy.require(not any("/ERROR]" in line or "/FATAL]" in line for line in lines), "Unexpected refill ERROR/FATAL")
    witness(lines, cut, bool(restart))
    prefix = "ARCE_LOADER_S6_FLIGHT_" + ("RESULT" if restart else "PAUSED") + " cut=" + cut + " "
    legacy.require(sum(prefix in line for line in lines) == 1, "Missing/duplicate refill completion")
    if not restart:
        legacy.require(sum(line.rstrip("\r\n").endswith(prefix + "hook=SUCCESSOR_RETIRED") for line in lines) == 1,
                       "Wrong refill checkpoint")
