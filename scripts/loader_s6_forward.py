"""S6 F2 forward recovery: bind the full checked T, debit and native survivor."""
import copy
import re

import loader_cut_nbt as nbt
import loader_s6_flight as flight
from loader_s6_flight import integrity, legacy, s6, SmokeError

CASES = flight.F2[1:]


def native_tag(value, key=""):
    """Additional types from RocketTransferJournalCodec and RocketFlightNbtCodec."""
    if key == "armed": return flight.armed_tag(value)
    if isinstance(value, dict): return integrity.compound({k: native_tag(v, k) for k, v in value.items()})
    if key in ("transfer_id", "owner_id", "source_entity_id", "destination_entity_id", "request_id",
               "transaction_id", "active_transfer_id", "ledger_id", "owner", "device", "physical_rocket",
               "logical_rocket", "logical_rocket_id", "physical", "logical", "assembly_snapshot", "passenger_id"):
        return integrity.uid(s6.canonical_uuid(value))
    if key == "seat_index": return integrity.scalar(value, nbt.INT)
    if key in ("required_fuel", "resource_epoch"): return integrity.scalar(value, nbt.LONG)
    if isinstance(value, list) and key not in ("chunk", "source_origin", "current_origin", "bounding_box",
                                               "position", "passenger_anchors", "pos", "motion"):
        elements = [native_tag(v) for v in value]
        kind = elements[0].type if elements else 0
        legacy.require(all(v.type == kind for v in elements), "Mixed forward native list")
        return nbt.Tag(nbt.LIST, (kind, elements))
    return integrity.native_tag(value, key)


def transfer_digest(row):
    return integrity.digest(native_tag(row))


def survivor_digest(rocket):
    data = rocket["RocketEntityData"]; snapshot = data["snapshot"]; state = data["flight_data"]
    frame = dict(schema_version=1, physical=rocket["UUID"], type=rocket["id"], logical=data["assembly_transaction_id"],
        owner=data["owner_id"], level=state["current_dimension"], chunk=[int(rocket["Pos"][0]) // 16, int(rocket["Pos"][2]) // 16],
        pos=rocket["Pos"], motion=rocket["Motion"], no_gravity=rocket["NoGravity"], no_physics=1,
        on_ground=rocket["OnGround"], invulnerable=rocket["Invulnerable"], yaw=rocket["Rotation"][0], pitch=rocket["Rotation"][1],
        passengers=[], assembly_snapshot=snapshot["snapshot_id"], content_hash=snapshot["content_hash"],
        snapshot=snapshot, flight=state, wrapper=data["resource_escrow"])
    return integrity.digest(native_tag(frame))


def check(image, restart, identity, cut, witness):
    legacy.require(cut in CASES and restart in (0, 1, 2) and image.get("read_status") == "KNOWN",
                   "Unknown or unsupported forward observation")
    try:
        if not restart: return prepared(image, cut, witness)
        legacy.require(identity is not None and witness is not None, "Forward restart lost its initial/live witness")
        disk = s6.normalize_ids(image["native"]); initial = identity["initial"]; row = identity["transfer"]
        expected_root = copy.deepcopy(identity["root"]); expected_root.update(writer_generation=6, armed_flights=[])
        legacy.require(disk["ledger"] == expected_root and disk["journal"] == identity["empty_journal"]
                       and disk["source"] is None and disk["destination"] is not None, "Forward terminal authority changed")
        legacy.require(len(disk["loaders"]) == 1 and [disk["loaders"][0][k] for k in ("x", "y", "z")] == [66, 100, 64]
                       and disk["loaders"][0]["arce_fluid_fuel_loader"] == initial["loader"], "Forward recovery changed its original loader")
        survivor = disk["destination"]; actual = flight.projection(survivor)
        landed_time = witness["landed_time"]
        legacy.require(isinstance(landed_time, int) and landed_time >= row["destination_flight"]["state_started_game_time"] + 80,
                       "Forward survivor lacks its completed descent clock")
        expected = copy.deepcopy(initial["source"])
        expected.update(UUID=s6.canonical_uuid(row["native_transfer"]["armed"]["d"]), Pos=[80.5, 100., 64.5])
        landed = copy.deepcopy(row["destination_flight"])
        landed.update(state="LANDED", state_started_game_time=landed_time); landed.pop("plan"); landed.pop("active_transfer_id")
        expected["RocketEntityData"].update(snapshot=row["destination_snapshot"], flight_data=landed,
            resource_escrow=dict(schema_version=1, ledger_id=initial["ledger"], resource_epoch=0, markers=[]))
        legacy.require(actual == expected and not survivor.get("Passengers")
                       and survivor_digest(survivor) == witness["survivor_digest"], "Changed complete forward survivor or live witness")
        result = dict(identity, survivor=actual)
        if restart == 2: legacy.require(result == identity, "Second forward restart changed immutable survivor")
        return dict(identity=result, raw_total=4500, available_total=4500, authorized_debit=500, loss=0)
    except (KeyError, TypeError, IndexError, ValueError, OverflowError) as failure:
        raise SmokeError("Malformed forward observation: " + str(failure)) from failure


def prepared(image, cut, witness):
    disk = s6.normalize_ids(image["native"]); journal = disk["journal"]
    legacy.require(witness is not None and len(journal["transfers"]) == 1, "F2 lost its checked T witness")
    row = journal["transfers"][0]
    legacy.require(transfer_digest(row) == witness["transfer_digest"], "Changed complete checked PREPARED T")
    # Apply every existing source, wrapper, marker, loader, ARMED and generation assertion without relaxing it.
    stationary = copy.deepcopy(image); stationary["native"]["journal"]["transfers"] = []
    initial = flight.check(stationary, 0, cut=flight.F2[0], witness=witness)["identity"]
    arm = disk["ledger"]["armed_flights"][0]; source = initial["source"]["RocketEntityData"]
    legacy.require(set(row) == {"schema_version", "transfer_id", "phase", "logical_rocket_id", "owner_id", "source_entity_id",
        "source_snapshot", "destination_snapshot", "source_flight", "destination_flight", "required_fuel", "created_at_game_time",
        "checksum", "native_transfer"} and row["schema_version"] == 3 and row["phase"] == "PREPARED"
        and row["required_fuel"] == 500 and row["source_entity_id"] == initial["native"]["physical"]
        and row["owner_id"] == initial["native"]["owner"] and row["logical_rocket_id"] == initial["native"]["logical"]
        and row["transfer_id"] == s6.canonical_uuid(arm["t"]) and row["source_snapshot"] == source["snapshot"]
        and row["native_transfer"]["armed"] == arm and row["native_transfer"]["schema_version"] == 1
        and row["native_transfer"]["signature_kind"] == "FUEL_VECTOR_V1", "F2 changed its native transfer authority")
    start = row["created_at_game_time"]; transit = row["source_flight"]; descent = row["destination_flight"]
    expected_transit = copy.deepcopy(source["flight_data"])
    expected_transit.update(state="TRANSIT", state_started_game_time=start + 140,
                            plan=transit["plan"], active_transfer_id=row["transfer_id"])
    expected_descent = copy.deepcopy(expected_transit)
    expected_descent.update(state="DESCENT", state_started_game_time=start + 160, current_body="advancedrocketrycommunity:moon",
        current_origin=[80, 100, 64], current_target=transit["plan"]["destination_target"])
    expected_descent["fuel"].update(amount=500, committed_debits=[dict(transaction_id=row["transfer_id"])])
    expected_descent["fuel"]["propulsion"].update(primary=integrity.mono_bank(500), revision=2)
    legacy.require(start >= source["flight_data"]["state_started_game_time"] and transit == expected_transit
                   and descent == expected_descent, "F2 changed its complete typed debit, clocks or manifest")
    snapshot = copy.deepcopy(source["snapshot"])
    snapshot.update(snapshot_id=row["destination_snapshot"]["snapshot_id"], source_origin=[80, 100, 64],
                    created_at_game_time=start, content_hash=row["destination_snapshot"]["content_hash"])
    legacy.require(snapshot == row["destination_snapshot"] and snapshot["snapshot_id"] != source["snapshot"]["snapshot_id"],
                   "F2 changed its relocated structure")
    result = dict(initial=initial, transfer=row, root=disk["ledger"], empty_journal=stationary["native"]["journal"])
    return dict(identity=result, raw_total=5000, available_total=None, authorized_debit=500, loss=None)


def transfer_witness(lines, cut):
    prefix = "ARCE_LOADER_S6_FLIGHT_TRANSFER cut=" + cut + " "
    found = [(i, re.search(re.escape(prefix) + r"digest=([0-9a-f]{64})$", line.rstrip("\r\n")))
             for i, line in enumerate(lines) if prefix in line]
    arms = [i for i, line in enumerate(lines) if "ARCE_LOADER_S6_FLIGHT_ARMED cut=" + cut + " " in line]
    pauses = [i for i, line in enumerate(lines) if "ARCE_LOADER_S6_FLIGHT_PAUSED cut=" + cut + " " in line]
    legacy.require(len(found) == len(arms) == len(pauses) == 1 and found[0][1] and arms[0] < found[0][0] < pauses[0],
                   "Missing/misordered complete checked T witness")
    return found[0][1].group(1)


def recovery_witness(lines, cut):
    prefix = "ARCE_LOADER_S6_FLIGHT_SURVIVOR cut=" + cut + " "
    found = [re.search(re.escape(prefix) + r"landed_time=(\d+) digest=([0-9a-f]{64})$", line.rstrip("\r\n"))
             for line in lines if prefix in line]
    legacy.require(len(found) == 1 and found[0], "Missing/duplicate forward survivor witness")
    return dict(landed_time=int(found[0].group(1)), survivor_digest=found[0].group(2))
