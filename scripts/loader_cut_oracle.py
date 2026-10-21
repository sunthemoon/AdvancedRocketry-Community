"""Independent raw-NBT oracle for W97; mixed stores are not spendable control totals."""
from __future__ import annotations

import copy
from pathlib import Path

import loader_cut_nbt as nbt
import run_dedicated_server_smoke as smoke
from run_v180_escrow_forced_stop import LEDGER, digest, ledger, record
from run_v180_escrow_pre_f2 import journal

PREFIX = "LF_M_"
CREATION = ("N0", "N1", "N2_DEFINITE", "N2_UNKNOWN", "N3_FULL", "N3_NOT_LOADED", "N3_PREFLIGHT")
CREATION_SUPPLEMENTAL = ("N3_ELIGIBILITY",)
NORMAL = tuple(f"K{i}" for i in range(11)) + ("K2_BI", "K6_BI", "K9_BI")
SUPPLEMENTAL = ("K2_ACK", "K3_DEBIT", "K6_FORCED", "K10_ACK")
D2 = {"D2_C0": "K0", "D2_C1_BEFORE": "K1", "D2_C1_REPLACED": "K2", "D2_C1_ACK": "K2_ACK",
      "D2_C2": "K3", "D2_C3": "K3", "D2_C4_BEFORE": "K5", "D2_C4_AFTER": "K6",
      "D2_C5_BEFORE": "K6", "D2_C5_AFTER": "K6_FORCED", "D2_C6_BEFORE": "K6",
      "D2_C6_AFTER": "K6", "D2_C6_JOINT": "K7", "D2_C7_BEFORE": "K8",
      "D2_C7_REPLACED": "K9", "D2_C7_ACK": "K10_ACK", "D2_C8": "K10", "D2_C7_REPLACED_BI": "K9_BI"}
CHAINS = ("C1", "C2", "C2_PARTIAL_REPLACED", "C2_PARTIAL_RELEASED", "C2_PARTIAL_REPLACED_BI")
S3 = tuple(PREFIX + name for name in tuple(D2) + CHAINS)
STORAGE = {"E4L1_FAIL": "K1", "E4L1_UNKNOWN": "K1", "E4L2_FAIL": "K8", "E4L2_UNKNOWN": "K8",
           "E4L2_RESAVE": "K8", "E4L2_RESAVE_RESOLVED": "K8", "E2_CAPTURE": "K4",
           "E4L1_BEFORE": "K1", "E4L1_REPLACED": "K2", "E4L1_ACK": "K2_ACK",
           "E4L2_BEFORE": "K8", "E4L2_REPLACED": "K9", "E4L2_ACK": "K10_ACK",
           # W107C (R43 items 2 and 3): stopped like E2_CAPTURE, at K4 or K6 by the driver's first store.
           "E1_OWN_STORE": "K4", "E2_EXTERNAL": "K4"}
S4 = tuple(PREFIX + name for name in STORAGE)
S5 = (PREFIX + "R1_UNKNOWN_UNLOAD",)
# W107C: E2's restarts keep the held K6 state, STALLED with the refusal; R1's floor and R3's edited-state families.
HELD = {"E2_EXTERNAL": "K6"}
FLOORS = ("R1_FLOORS_255_DISTINCT", "R1_FLOORS_255_COALESCED", "R1_FLOORS_256")
R3 = nbt.R3_EDITS
S5_FLOORS = tuple(PREFIX + name for name in FLOORS)
S5_R3 = tuple(PREFIX + name for name in R3)
MANUAL = ("MISSING_ENDPOINT", "OTHER_STATE", "FOREIGN_MARKER", "LEDGER_REPLACED", "LEDGER_BEHIND",
          "INVALID_ENDPOINT", "RELOCATED_ENDPOINT", "FLOOR_CONFLICT", "JOURNAL_GUARD_MISMATCH")
CASES = (tuple(PREFIX + name for name in CREATION + NORMAL + SUPPLEMENTAL + CREATION_SUPPLEMENTAL) + S3 + S4 + S5
         + S5_FLOORS + S5_R3)
NOISE_AT = (76, 100, 76)
NOISE_BYTES = 1_572_864
EXTRA_ROCKET_POS = [72.5, 100.0, 72.5]
EXTRA_LOADER_AT = (74, 100, 72)
NOT_RUN = {}
CAPACITY_POSITIONS = [(66, 100, 64), (66, 100, 65), (67, 100, 64), (67, 100, 65), (68, 100, 65), (69, 100, 65)]
LOADER = "advancedrocketrycommunity:fluid_fuel_loader"
ROCKET = "advancedrocketrycommunity:rocket"
FUEL = "advancedrocketrycommunity:rocket_fuel"
HYDROGEN = "advancedrocketrycommunity:hydrogen"
OXYGEN = "advancedrocketrycommunity:oxygen"


def require(ok: bool, message: str) -> None:
    if not ok:
        raise smoke.SmokeError(message)


def origin(cut: str) -> int:
    return 2048 if cut == PREFIX + "R1_UNKNOWN_UNLOAD" else 64


def inspect(world: Path, cut: str = "") -> dict:
    name = cut.removeprefix(PREFIX)
    if name in FLOORS:
        return inspect_floors(world, cut)
    path = world / "data" / LEDGER
    root = ledger(path) if path.is_file() else None
    chunk = origin(cut) // 16
    region = f"r.{chunk // 32}.{chunk // 32}.mca"
    slot = chunk % 32 + (chunk % 32) * 32
    if name == "E2_EXTERNAL":
        return inspect_external(world, root, region, slot)
    terrain = record(world / "region" / region, slot)
    entities = record(world / "entities" / region, slot)["Entities"]
    rockets = [entry for entry in entities if "RocketEntityData" in entry or entry.get("id") == ROCKET]
    require(len(rockets) == 1 and not journal(world)["transfers"], "Missing/duplicate rocket or unexpected transfer")
    devices = sorted((entry for entry in terrain["block_entities"] if entry.get("id") == LOADER), key=lambda e: (e["x"], e["y"], e["z"]))
    return dict(ledger=root, loaders=devices, rocket=rockets[0],
                files={name: digest(world / name) if (world / name).is_file() else None
                       for name in ("data/" + LEDGER, "region/" + region, "entities/" + region)})


def inspect_external(world: Path, root: dict | None, region: str, slot: int) -> dict:
    """E2: vanilla's external ``c.4.4.mcc`` terrain record, read with the bounded typed reader; only loaders are kept."""
    terrain, info = nbt.chunk(world / "region", 4, 4)
    entries = nbt.compound(terrain, "block_entities").value[1]
    noise = [entry for entry in entries if [nbt.compound(entry, key).value for key in ("x", "y", "z")] == list(NOISE_AT)]
    require(len(noise) == 1 and len(nbt.compound(noise[0], "ForgeData", "arce_w107c_noise").value) == NOISE_BYTES,
            "Missing or changed E2 noise fixture")
    devices = sorted((nbt.plain(entry) for entry in entries if nbt.plain(nbt.compound(entry, "id")) == LOADER),
                     key=lambda e: (e["x"], e["y"], e["z"]))
    entities = record(world / "entities" / region, slot)["Entities"]
    rockets = [entry for entry in entities if "RocketEntityData" in entry or entry.get("id") == ROCKET]
    require(len(rockets) == 1 and not journal(world)["transfers"], "Missing/duplicate rocket or unexpected transfer")
    files = {name: digest(world / name) if (world / name).is_file() else None
             for name in ("data/" + LEDGER, "region/" + region, "region/c.4.4.mcc", "entities/" + region)}
    return dict(ledger=root, loaders=devices, rocket=rockets[0], terrain_record=info, files=files)


def inspect_floors(world: Path, cut: str) -> dict:
    """R1: both rockets of chunk (4,4), their loaders and the checked journal's landed records."""
    root = ledger(world / "data" / LEDGER) if (world / "data" / LEDGER).is_file() else None
    terrain = record(world / "region" / "r.0.0.mca", 132)
    entities = record(world / "entities" / "r.0.0.mca", 132)["Entities"]
    rockets = [entry for entry in entities if "RocketEntityData" in entry or entry.get("id") == ROCKET]
    main = [entry for entry in rockets if entry.get("Pos") == [64.5, 100.0, 64.5]]
    extra = [entry for entry in rockets if entry.get("Pos") == EXTRA_ROCKET_POS]
    coalesced = cut.endswith("COALESCED")
    require(len(main) == 1 and len(extra) == int(not coalesced) and len(rockets) == len(main) + len(extra),
            "Missing, duplicate or moved floor-cut rocket")
    devices = sorted((entry for entry in terrain["block_entities"] if entry.get("id") == LOADER), key=lambda e: (e["x"], e["y"], e["z"]))
    other = [entry for entry in devices if tuple(entry.get(k) for k in ("x", "y", "z")) == EXTRA_LOADER_AT]
    return dict(ledger=root, loaders=[entry for entry in devices if entry not in other], rocket=main[0],
                extra_rocket=extra[0] if extra else None, extra_loader=other[0] if other else None,
                transfers=journal(world)["transfers"],
                files={name: digest(world / name) if (world / name).is_file() else None
                       for name in ("data/" + LEDGER, "region/r.0.0.mca", "entities/r.0.0.mca")})


def expected(cut: str, restart: int) -> dict:
    require(cut in CASES, "Unknown cut")
    name0 = cut.removeprefix(PREFIX)
    require(name0 not in FLOORS, "Floor cuts have their own oracle")
    if name0 in HELD and restart:
        return dict(expected(PREFIX + HELD[name0], 0), held=True)
    if name0 in R3:
        require(not restart, "R3 restarts compare with their edited baseline")
        cut = PREFIX + "K6"
    if cut in S5:
        cut = PREFIX + "K1"
    if cut.removeprefix(PREFIX) in D2:
        cut = PREFIX + D2[cut.removeprefix(PREFIX)]
    if cut.removeprefix(PREFIX) in STORAGE:
        cut = PREFIX + STORAGE[cut.removeprefix(PREFIX)]
    name = cut.removeprefix(PREFIX).removesuffix("_BI")
    bi = cut.endswith("_BI")
    creation = name in CREATION + CREATION_SUPPLEMENTAL
    if creation:
        complete = restart == 2  # Second restart first checks unchanged state, then exercises the next attempt.
        return dict(source=int(complete), destination=int(complete), markers=0, records=0,
                    issued=int(complete), bi=False, ledger=name == "N1" or complete, generation=1 if name == "N1" and not complete else None)
    admitted = name not in ("K0", "K1")
    if restart:
        return dict(source=int(admitted), destination=int(admitted), markers=0, records=0,
                    issued=(2 if bi else 1) if admitted else 0, bi=bi, ledger=True, generation=None)
    source = name in ("K4", "K6", "K6_FORCED", "K7", "K8", "K9", "K10_ACK", "K10")
    destination = name in ("K5", "K6", "K6_FORCED", "K7", "K8", "K9", "K10_ACK", "K10")
    retired = name in ("K9", "K10_ACK", "K10")
    return dict(source=int(source), destination=int(destination), markers=1, records=int(admitted and not retired),
                issued=(2 if bi else 1) if admitted else 0, bi=bi, ledger=True, generation=1 if not admitted else None)


def check_section(section: dict, epoch: int, markers: int, root: dict | None, role: str, sequence: int) -> None:
    require(section["schema_version"] == 1 and section["resource_epoch"] == epoch, "Wrong raw resource epoch/schema")
    require(len(section["markers"]) == markers, "Wrong raw marker count")
    require(section.get("ledger_id") == (root["ledger_id"] if epoch else None), "Wrong raw ledger binding")
    if markers:
        marker = section["markers"][0]
        require(set(marker) == {"op_id", "seq", "role", "epoch_after"} and marker["role"] == role
                and marker["seq"] == sequence and marker["epoch_after"] == epoch, "Wrong raw marker identity/frontier")
        if root["records"]:
            require(marker["op_id"] == root["records"][0]["op_id"], "Marker names another operation")


def check(disk: dict, cut: str, restart: int) -> dict:
    name = cut.removeprefix(PREFIX)
    if name in FLOORS:
        return check_floors(disk, cut, restart)
    if name in R3 and restart:
        return check_r3(disk, cut)
    if name in CHAINS:
        return check_chain(disk, cut, restart)
    exp = expected(cut, restart)
    if name == "E2_EXTERNAL":
        record_info = disk["terrain_record"]
        require(record_info["external"] and record_info["compressed"] > 1_048_576 and record_info["decoded"] <= 4_194_304,
                "E2's source chunk is not an external record inside the decoded bound")
    if exp.get("held"):
        op = disk["ledger"]["records"][0] if disk["ledger"] and disk["ledger"]["records"] else {}
        require(op.get("phase") == "STALLED" and op.get("reason") == "BARRIER_EXTERNAL",
                "E2's held record is not STALLED with BARRIER_EXTERNAL")
    root, devices, rocket = disk["ledger"], disk["loaders"], disk["rocket"]
    bi = exp["bi"]
    capacity = cut == PREFIX + "N3_PREFLIGHT"
    require(len(devices) == (6 if capacity else 2 if bi else 1), "Wrong native loader count")
    require(bool(root) == exp["ledger"], "Missing/unexpected ledger")
    if root:
        require(root["issued_through"] == exp["issued"] and len(root["records"]) == exp["records"], "Wrong raw sequence/records")
        require(not any(root[key] for key in ("floors", "tombstones", "armed_flights", "rocket_authorities", "issued_launch_serial")),
                "Fill created settlement/flight authority")
        require(root["writer_generation"] >= 1 and (exp["generation"] is None or root["writer_generation"] == exp["generation"]),
                "Wrong raw ledger generation")
    data = rocket["RocketEntityData"]
    flight, wrapper = data["flight_data"], data["resource_escrow"]
    at = origin(cut)
    require(rocket["id"] == ROCKET and rocket["Pos"] == [at + .5, 100.0, at + .5] and not rocket.get("Passengers")
            and not flight["passengers"]["assignments"] and "active_transfer_id" not in flight
            and data["assembly_transaction_id"] == flight["logical_rocket_id"], "Changed native flight identity/authority")
    propulsion = flight["fuel"]["propulsion"]
    require(not flight["fuel"].get("committed_debits", []) and "plan" not in flight,
            "Fill introduced a flight withdrawal or plan")
    amounts = [propulsion[key]["amount"] for key in ("primary", "oxidizer")]
    require(amounts == ([1000, 1000 * exp["destination"]] if bi else [1000 * exp["destination"], 0]), "Wrong raw fuel vector")
    sequence = 2 if bi else 1
    check_section(wrapper, int(bi) + exp["destination"], exp["destination"] * exp["markers"], root, "DESTINATION", sequence)
    identity = dict(physical=rocket["UUID"], logical=data["assembly_transaction_id"], owner=data["owner_id"], loaders=[])
    for index, device in enumerate(devices):
        position = CAPACITY_POSITIONS[index] if capacity else (at + 2 + index, 100, at)
        require(tuple(device.get(key) for key in ("x", "y", "z")) == position and device["id"] == LOADER,
                "Wrong native loader position/type")
        loader = device["arce_fluid_fuel_loader"]
        require(loader["owner"] == data["owner_id"] and loader["binding"]["physical_rocket"] == rocket["UUID"]
                and loader["binding"]["logical_rocket"] == data["assembly_transaction_id"], "Changed native owner/binding")
        prior = bi and index == 0
        moved = 0 if capacity and index else 1 if prior else exp["source"]
        fuel = HYDROGEN if prior else OXYGEN if bi else FUEL
        require(loader["tank"] == {"FluidName": fuel, "Amount": 5000 - 1000 * moved}, "Wrong raw tank payload/quantity")
        check_section(loader["resource_escrow"], moved, 0 if prior else moved * exp["markers"], root, "SOURCE", sequence)
        identity["loaders"].append({key: loader[key] for key in ("owner", "device", "binding")})
    if root and root["records"]:
        op = root["records"][0]
        loader = devices[-1]["arce_fluid_fuel_loader"]
        require(op["source"]["identity"] == loader["device"] and op["source"]["type"] == LOADER
                and op["destination"]["identity"] == rocket["UUID"] and op["payload"] == {"FluidName": OXYGEN if bi else FUEL, "Amount": 1000}
                and op["native_binding"]["complete"] == 1 and op["native_binding"]["kind"] == "FUEL_VECTOR_V1",
                "L1 is not the complete native loader fill")
    if exp["source"] and exp["destination"] and exp["markers"]:
        require(devices[-1]["arce_fluid_fuel_loader"]["resource_escrow"]["markers"][0]["op_id"] == wrapper["markers"][0]["op_id"],
                "Stored endpoints disagree on operation identity")
    return identity


def check_chain(disk: dict, cut: str, restart: int) -> dict:
    """C1's mixed prefix is locked, not a claim that physical quantities alone are available."""
    name = cut.removeprefix(PREFIX).removesuffix("_BI"); bi = cut.endswith("_BI")
    root, devices, rocket = disk["ledger"], disk["loaders"], disk["rocket"]
    prefix = 1 if name == "C1" and not restart else 2
    partial = name.startswith("C2_PARTIAL") and not restart
    require(root is not None and len(devices) == 2 and root["issued_through"] == 2, "Wrong chain authority/device count")
    require(len(root["records"]) == (0 if restart else 1 if partial else 2), "Wrong chain retirement prefix")
    require(root["writer_generation"] >= 2 and not any(root[key] for key in
            ("floors", "tombstones", "armed_flights", "rocket_authorities", "issued_launch_serial")), "Unexpected chain authority")
    data = rocket["RocketEntityData"]; flight = data["flight_data"]; wrapper = data["resource_escrow"]
    require(rocket["id"] == ROCKET and rocket["Pos"] == [64.5, 100.0, 64.5] and not rocket.get("Passengers")
            and not flight["passengers"]["assignments"] and "active_transfer_id" not in flight
            and data["assembly_transaction_id"] == flight["logical_rocket_id"], "Changed chain flight identity/authority")
    propulsion = flight["fuel"]["propulsion"]
    require(not flight["fuel"].get("committed_debits", []) and "plan" not in flight,
            "Chain introduced a flight withdrawal or plan")
    require([propulsion[k]["amount"] for k in ("primary", "oxidizer")] ==
            ([1000, 1000 * (prefix - 1)] if bi else [1000 * prefix, 0]), "Wrong chain raw fuel vector")
    identity = dict(physical=rocket["UUID"], logical=data["assembly_transaction_id"], owner=data["owner_id"], loaders=[])
    ids = {}

    def section(value, epoch, sequences, role):
        require(value["schema_version"] == 1 and value["resource_epoch"] == epoch
                and value.get("ledger_id") == root["ledger_id"], "Wrong chain epoch/binding")
        require([m["seq"] for m in value["markers"]] == sequences, "Wrong chain marker prefix")
        for marker in value["markers"]:
            seq = marker["seq"]
            require(set(marker) == {"op_id", "seq", "role", "epoch_after"} and marker["role"] == role
                    and marker["epoch_after"] == (seq if role == "DESTINATION" else 1), "Wrong chain marker frontier")
            require(seq not in ids or ids[seq] == marker["op_id"], "Chain endpoints name different operations")
            ids[seq] = marker["op_id"]

    section(wrapper, prefix, [] if restart else list(range(1, prefix + 1)), "DESTINATION")
    for index, device in enumerate(devices):
        require(device["id"] == LOADER and tuple(device[k] for k in ("x", "y", "z")) == (66 + index, 100, 64),
                "Wrong chain native loader position/type")
        loader = device["arce_fluid_fuel_loader"]
        require(loader["owner"] == data["owner_id"] and loader["binding"]["physical_rocket"] == rocket["UUID"]
                and loader["binding"]["logical_rocket"] == data["assembly_transaction_id"], "Changed chain owner/binding")
        fluid = (HYDROGEN if index == 0 else OXYGEN) if bi else FUEL
        require(loader["tank"] == {"FluidName": fluid, "Amount": 4000}, "Wrong chain raw tank payload/quantity")
        section(loader["resource_escrow"], 1, [] if restart else [index + 1], "SOURCE")
        identity["loaders"].append({key: loader[key] for key in ("owner", "device", "binding")})
    sequences = [] if restart else [2] if partial else [1, 2]
    require([op["seq"] for op in root["records"]] == sequences, "Chain retired a noncontiguous prefix")
    for op in root["records"]:
        seq = op["seq"]; loader = devices[seq - 1]["arce_fluid_fuel_loader"]
        require(op["op_id"] == ids[seq] and op["source"]["identity"] == loader["device"]
                and op["source"]["type"] == LOADER and op["destination"]["identity"] == rocket["UUID"]
                and (op["source"]["before_epoch"], op["source"]["after_epoch"]) == (0, 1)
                and (op["destination"]["before_epoch"], op["destination"]["after_epoch"]) == (seq - 1, seq)
                and op["payload"] == {"FluidName": loader["tank"]["FluidName"], "Amount": 1000}
                and op["native_binding"]["complete"] == 1 and op["native_binding"]["kind"] == "FUEL_VECTOR_V1",
                "Chain record is not its complete native fill")
    return identity


def check_floors(disk: dict, cut: str, restart: int) -> dict:
    """R1: the seeded floors survive unchanged, a named fill adds exactly its one key, and the fill is the matrix's."""
    name = cut.removeprefix(PREFIX); coalesced = name.endswith("COALESCED"); saturated = name.endswith("256")
    seeded = 256 if saturated else 255; fills = 2 if coalesced else 1
    root = disk["ledger"]; rocket = disk["rocket"]; extra = disk["extra_rocket"]
    require(root is not None, "Floor cut lost its ledger")
    data = rocket["RocketEntityData"]; logical = data["assembly_transaction_id"]
    neutral = [floor for floor in root["floors"] if "transfer_id" not in floor]
    named = [floor for floor in root["floors"] if "transfer_id" in floor]
    require(len(neutral) == seeded and sorted(floor["highest_seq"] for floor in neutral) == list(range(1, seeded + 1))
            and all(floor["schema_version"] == 2 and floor["resource_epoch"] == 1 and floor["signature_kind"] == "FUEL_VECTOR_V1"
                    for floor in neutral)
            and len({tuple(floor["rocket_id"]) for floor in neutral}) == seeded, "Seeded floors changed, were collected or duplicated")

    def landed(target: dict):
        records = [entry for entry in disk["transfers"]
                   if entry.get("logical_rocket_id") == target["RocketEntityData"]["assembly_transaction_id"]]
        require(len(records) == 1 and records[0].get("phase") == "COMMITTED"
                and records[0].get("destination_entity_id") == target["UUID"], "Missing or changed landed journal record")
        return records[0]["transfer_id"]

    require(len(disk["transfers"]) == int(not saturated) + int(extra is not None), "Unexpected journal transfers")
    transfer = None if saturated else landed(rocket)
    if extra is not None:
        landed(extra)
    if restart and not saturated:
        require(len(named) == 1 and named[0]["rocket_id"] == logical and named[0]["transfer_id"] == transfer
                and named[0]["entity_id"] == rocket["UUID"] and named[0]["resource_epoch"] == fills
                and named[0]["highest_seq"] == seeded + fills, "The named fill did not install exactly its one floor")
    else:
        require(not named, "A floor was installed before retirement or for a NONE fill")
    for op in root["records"]:
        require(op["transfer_guard"].get("transfer_id") == transfer and op["transfer_guard"]["rocket_id"] == logical,
                "The fill's journal guard names another transfer")
    # The fill itself follows the seeded watermark: rebase its sequences and apply the unchanged matrix oracle.
    rebased = copy.deepcopy(disk); base = rebased["ledger"]
    require(base["issued_through"] > seeded, "The fill did not follow the seeded watermark")
    base["issued_through"] -= seeded; base["floors"] = []
    for op in base["records"]:
        op["seq"] -= seeded
    for section in [rebased["rocket"]["RocketEntityData"]["resource_escrow"]] + [
            device["arce_fluid_fuel_loader"]["resource_escrow"] for device in rebased["loaders"]]:
        for marker in section["markers"]:
            marker["seq"] -= seeded
    # Named cuts stop at K2 (L1 replaced, no endpoint applied): the raw state of K2_ACK, or COALESCED's shared L1.
    identity = coalesced_stop(rebased) if coalesced and not restart else check(rebased, PREFIX + ("C2" if coalesced else "K2_ACK"), restart)
    if extra is not None:
        loader = disk["extra_loader"]
        require(loader is not None, "Missing distinct-key loader")
        other = loader["arce_fluid_fuel_loader"]; wrapper = extra["RocketEntityData"]["resource_escrow"]
        require(wrapper["resource_epoch"] == 0 and not wrapper["markers"] and "ledger_id" not in wrapper
                and extra["RocketEntityData"]["flight_data"]["fuel"]["propulsion"]["primary"]["amount"] == 0
                and other["tank"] == {"FluidName": FUEL, "Amount": 5000} and other["resource_escrow"]["resource_epoch"] == 0
                and not other["resource_escrow"]["markers"] and "ledger_id" not in other["resource_escrow"]
                and other["binding"]["physical_rocket"] == extra["UUID"] and other["owner"] == data["owner_id"],
                "The refused distinct key changed its rocket or loader")
        identity = dict(identity, extra=dict(physical=extra["UUID"], device=other["device"]))
    return identity


def coalesced_stop(disk: dict) -> dict:
    """R1 COALESCED at K2, rebased: one durable L1 holds both same-key fills, and no endpoint has applied either."""
    root, devices, rocket = disk["ledger"], disk["loaders"], disk["rocket"]
    require(root["issued_through"] == 2 and [op["seq"] for op in root["records"]] == [1, 2] and len(devices) == 2
            and root["writer_generation"] >= 2 and not any(root[key] for key in ("floors", "tombstones", "armed_flights", "rocket_authorities")),
            "The coalesced L1 does not hold exactly both fills")
    data = rocket["RocketEntityData"]; wrapper = data["resource_escrow"]; flight = data["flight_data"]
    require(rocket["id"] == ROCKET and rocket["Pos"] == [64.5, 100.0, 64.5] and not rocket.get("Passengers")
            and "active_transfer_id" not in flight and wrapper["resource_epoch"] == 0 and not wrapper["markers"] and "ledger_id" not in wrapper
            and [flight["fuel"]["propulsion"][key]["amount"] for key in ("primary", "oxidizer")] == [0, 0],
            "The coalesced stop applied or bound the rocket")
    identity = dict(physical=rocket["UUID"], logical=data["assembly_transaction_id"], owner=data["owner_id"], loaders=[])
    for index, device in enumerate(devices):
        loader = device["arce_fluid_fuel_loader"]; section = loader["resource_escrow"]; op = root["records"][index]
        require(device["id"] == LOADER and tuple(device[k] for k in ("x", "y", "z")) == (66 + index, 100, 64)
                and loader["tank"] == {"FluidName": FUEL, "Amount": 5000} and section["resource_epoch"] == 0 and not section["markers"]
                and "ledger_id" not in section and loader["owner"] == data["owner_id"] and loader["binding"]["physical_rocket"] == rocket["UUID"],
                "The coalesced stop applied or rebound a loader")
        require(op["source"]["identity"] == loader["device"] and op["source"]["type"] == LOADER and op["destination"]["identity"] == rocket["UUID"]
                and (op["source"]["before_epoch"], op["source"]["after_epoch"]) == (0, 1)
                and (op["destination"]["before_epoch"], op["destination"]["after_epoch"]) == (index, index + 1)
                and op["payload"] == {"FluidName": FUEL, "Amount": 1000}
                and op["native_binding"]["complete"] == 1 and op["native_binding"]["kind"] == "FUEL_VECTOR_V1",
                "A coalesced record is not its loader's complete native fill")
        identity["loaders"].append({key: loader[key] for key in ("owner", "device", "binding")})
    return identity


def check_r3(disk: dict, cut: str) -> dict:
    """R3 restarts against the edited stopped copy: nothing repaired, released, replayed or invented; at most a reason."""
    name = cut.removeprefix(PREFIX); edited = disk["edited"]
    root, base = disk["ledger"], edited["ledger"]

    def rocket_state(entity: dict):
        data = entity["RocketEntityData"]
        return (entity["UUID"], entity["Pos"], data["resource_escrow"], data["flight_data"]["fuel"]["propulsion"],
                data["owner_id"], data["assembly_transaction_id"], entity.get("Passengers"))

    def loaders(state: dict):
        return [(device["x"], device["y"], device["z"], device["arce_fluid_fuel_loader"]) for device in state["loaders"]]

    require(rocket_state(disk["rocket"]) == rocket_state(edited["rocket"]), "Restart changed the rocket endpoint")
    require(loaders(disk) == loaders(edited), "Restart repaired, released or moved a loader endpoint")
    if name.startswith("R3_LEDGER_"):
        keys = ("ledger_id", "issued_through", "records", "floors", "tombstones", "armed_flights", "rocket_authorities")
        require(root is None if name == "R3_LEDGER_MISSING" else root is not None and base is not None
                and {key: root[key] for key in keys} == {key: base[key] for key in keys}, "Restart invented or changed a ledger")
    else:
        require(root is not None and root["ledger_id"] == base["ledger_id"] and root["issued_through"] == base["issued_through"] == 1
                and len(root["records"]) == len(base["records"]) == 1 and root["writer_generation"] >= base["writer_generation"]
                and not any(root[key] for key in ("floors", "tombstones", "armed_flights", "rocket_authorities")),
                "Restart retired, replayed or replaced the held record")
        op, prior = root["records"][0], base["records"][0]
        keys = ("op_id", "seq", "source", "destination", "payload", "native_binding", "transfer_guard")
        require({key: op[key] for key in keys} == {key: prior[key] for key in keys} and op["phase"] == "REPAIR"
                and op["reason"] in MANUAL, "The record is not a persisted manual repair of the same fill")
    data = disk["rocket"]["RocketEntityData"]
    return dict(physical=disk["rocket"]["UUID"], logical=data["assembly_transaction_id"], owner=data["owner_id"],
                loaders=[{key: device["arce_fluid_fuel_loader"][key] for key in ("owner", "device", "binding")} for device in disk["loaders"]])
