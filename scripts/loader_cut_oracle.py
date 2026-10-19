"""Independent raw-NBT oracle for W97; mixed stores are not spendable control totals."""
from __future__ import annotations

from pathlib import Path

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
           "E4L2_BEFORE": "K8", "E4L2_REPLACED": "K9", "E4L2_ACK": "K10_ACK"}
S4 = tuple(PREFIX + name for name in STORAGE)
S5 = (PREFIX + "R1_UNKNOWN_UNLOAD",)
CASES = tuple(PREFIX + name for name in CREATION + NORMAL + SUPPLEMENTAL + CREATION_SUPPLEMENTAL) + S3 + S4 + S5
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
    path = world / "data" / LEDGER
    root = ledger(path) if path.is_file() else None
    chunk = origin(cut) // 16
    region = f"r.{chunk // 32}.{chunk // 32}.mca"
    slot = chunk % 32 + (chunk % 32) * 32
    terrain = record(world / "region" / region, slot)
    entities = record(world / "entities" / region, slot)["Entities"]
    rockets = [entry for entry in entities if "RocketEntityData" in entry or entry.get("id") == ROCKET]
    require(len(rockets) == 1 and not journal(world)["transfers"], "Missing/duplicate rocket or unexpected transfer")
    devices = sorted((entry for entry in terrain["block_entities"] if entry.get("id") == LOADER), key=lambda e: (e["x"], e["y"], e["z"]))
    return dict(ledger=root, loaders=devices, rocket=rockets[0],
                files={name: digest(world / name) if (world / name).is_file() else None
                       for name in ("data/" + LEDGER, "region/" + region, "entities/" + region)})


def expected(cut: str, restart: int) -> dict:
    require(cut in CASES, "Unknown cut")
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
    if cut.removeprefix(PREFIX) in CHAINS:
        return check_chain(disk, cut, restart)
    exp = expected(cut, restart)
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
