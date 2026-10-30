"""S6 raw loader evidence. Unreadable stores never become zero-valued resources.

An E3 cut can expose either exact prefix at each independent native store. The
record still owns the entire component: a raw sum is not spendable accounting.
"""
from __future__ import annotations

from pathlib import Path
from uuid import UUID
import loader_cut_oracle as legacy
from run_dedicated_server_smoke import SmokeError

E3 = "LF_S6_E3_CACHE_CLOSE"
E5 = "LF_S6_E5_UNLOADED"

# UUID-valued native fields and the identity envelope returned by these oracles.
# Do not recognize UUIDs by array length: coordinates, quantities and hashes are
# not identities. `snapshot`, `ledger` and `identity` can also contain compounds.
UUID_FIELDS = frozenset((
    "UUID", "ledger_id", "op_id", "identity", "owner", "owner_id", "device",
    "physical_rocket", "logical_rocket", "logical_rocket_id", "assembly_transaction_id",
    "rocket_id", "physical", "logical", "snapshot", "snapshot_id", "ledger",
    "transfer_id", "entity_id", "destination_entity_id", "active_transfer_id",
))


def canonical_uuid(value):
    """Preserve the 128 bits of native signed int[4] and textual UUIDs exactly."""
    if isinstance(value, str):
        try:
            return str(UUID(value))
        except ValueError:
            # Legacy opaque string identities retain exact equality. They cannot
            # alias an int-array UUID; representation normalization adds no IDs.
            return value
    legacy.require(isinstance(value, (list, tuple)) and len(value) == 4
                   and all(type(word) is int and -(2**31) <= word < 2**31 for word in value),
                   "Malformed native UUID; expected four signed 32-bit integers")
    return str(UUID(bytes=b"".join(word.to_bytes(4, "big", signed=True) for word in value)))


def normalize_ids(value):
    """Copy an observation, canonicalizing only named identity fields."""
    if isinstance(value, dict):
        return {key: (canonical_uuid(item) if key in UUID_FIELDS and not isinstance(item, dict)
                      and not (key == "ledger" and item is None) else normalize_ids(item))
                for key, item in value.items()}
    if isinstance(value, list):
        return [normalize_ids(item) for item in value]
    return value


def inspect(world: Path, cut=E3) -> dict:
    try:
        return dict(read_status="KNOWN", native=inspect_e5(world) if cut == E5 else legacy.inspect(world))
    except (OSError, ValueError, KeyError, TypeError, SmokeError) as failure:
        return dict(read_status="UNKNOWN", native=None,
                    error=f"{type(failure).__name__}: {failure}")


def check_e3(image, restart, identity=None):
    legacy.require(restart in (0, 1, 2), "Invalid restart index")
    legacy.require(image.get("read_status") == "KNOWN" and isinstance(image.get("native"), dict),
                   "UNKNOWN native read cannot establish availability or zero loss")
    disk = normalize_ids(image["native"])
    identity = normalize_ids(identity)
    try:
        legacy.require(len(disk["loaders"]) == 1, "E3 requires exactly one real fuel loader")
        tank = disk["loaders"][0]["arce_fluid_fuel_loader"]["tank"]["Amount"]
        bank = disk["rocket"]["RocketEntityData"]["flight_data"]["fuel"]["propulsion"]["primary"]["amount"]
        prefixes = {(5000, 0): "K2", (4000, 0): "K4", (5000, 1000): "K5", (4000, 1000): "K6"}
        legacy.require((tank, bank) in prefixes, "Changed raw quantity; not an exact native BEFORE/AFTER image")
        prefix = prefixes[tank, bank]
        legacy.require(disk["ledger"] is not None, "Missing durable L1 ledger")
        observed = legacy.check(disk, "LF_M_" + ("K7" if restart else prefix), restart)
        # Legacy identity omits the ledger because its runner compares it separately.
        observed["ledger"] = disk["ledger"]["ledger_id"]
        legacy.require(identity is None or observed == identity, "Changed native identity across restarts")
        return dict(prefix=prefix, raw_total=tank + bank,
                    available_total=5000 if restart else None,
                    loss=0 if restart else None, identity=observed)
    except (KeyError, TypeError, IndexError) as failure:
        raise SmokeError(f"Malformed/missing raw loader state: {failure}") from failure


def check_observations(lines: list[str], restart: int, cut=E3) -> None:
    """Require the selected propagated failure, not merely a checkpoint label."""
    import re
    if cut == E5:
        if restart:
            wanted = "ARCE_LOADER_S6_FAIR_RESULT cut=" + E5 + " pairs=2 totals=5000,5000 records=0 markers=0 epochs=1,1,1,1"
            legacy.require(sum(wanted in line for line in lines) == 1, "Missing/repeated E5 completed pairs")
            legacy.require(not any("/ERROR]" in line or "/FATAL]" in line for line in lines), "E5 recovery ERROR/FATAL")
        else:
            wanted = r"ARCE_LOADER_S6_FAIR cut=" + E5 + r" unloaded_ticks=(\d+) records=1 issued=2 pending=1 healthy_tank=4000 healthy_bank=1000 healthy_markers=0"
            matches = [m for line in lines if (m := re.search(wanted, line))]
            legacy.require(len(matches) == 1 and int(matches[0][1]) >= 20, "Missing actual unloaded-work fairness")
            legacy.require(sum("ARCE_LOADER_S6_FAIR_CACHE regions=256" in line for line in lines) == 1,
                           "Missing first loader's propagated close fixture")
            errors = [line for line in lines if "/ERROR]" in line or "/FATAL]" in line]
            legacy.require(len(errors) == 1 and "Failed to store chunk [128, 128]" in errors[0], "Unexpected E5 native errors")
            legacy.require(any("java.nio.channels.ClosedChannelException" in line for line in lines), "Missing E5 own-close failure")
        return
    if restart:
        check_recovery_observations(lines)
        legacy.require(not any("/ERROR]" in line or "/FATAL]" in line for line in lines),
                       "Recovery logged ERROR/FATAL")
        return
    wanted = r"ARCE_LOADER_S6_E3 records=1 pending=1 issued=1 io_failures=1 reason=BARRIER_IO proof=false"
    legacy.require(sum(re.search(wanted + r"(?:\s|$)", line) is not None for line in lines) == 1,
                   "Missing exact propagated E3 failure/retained authority")
    legacy.require(sum("ARCE_LOADER_S6_CACHE regions=256 own_region_cached=false" in line for line in lines) == 1,
                   "Missing selected cache-close observation")
    errors = [line for line in lines if "/ERROR]" in line or "/FATAL]" in line]
    legacy.require(len(errors) == 1 and "Failed to store chunk [4, 4]" in errors[0],
                   "E3 native diagnostics differ from its one own-store failure: " + repr(errors))
    legacy.require(any("java.nio.channels.ClosedChannelException" in line for line in lines),
                   "E3 did not propagate the selected closed channel")


def check_recovery_observations(lines: list[str]) -> None:
    """Check every tick's partition independently of the JVM observer's verdict."""
    def fields(line, marker):
        return dict(token.split("=", 1) for token in line.split(marker, 1)[1].strip().split())
    rows = [fields(line, "ARCE_LOADER_S6_ACCOUNT ") for line in lines if "ARCE_LOADER_S6_ACCOUNT " in line]
    results = [fields(line, "ARCE_LOADER_S6_RESULT ") for line in lines if "ARCE_LOADER_S6_RESULT " in line]
    legacy.require(rows and len(results) == 1, "Missing/repeated S6 accounting or completion")
    ticks, known = [], []
    try:
        for row in rows:
            ticks.append(int(row["tick"]))
            if row["known"] == "false":
                legacy.require(all(row[k] == "UNKNOWN" for k in ("raw", "available", "held")),
                               "UNKNOWN observation converted to numeric resources")
                continue
            legacy.require(row["known"] == "true", "Invalid observation read status")
            known.append(row)
            numbers = {key: int(row[key]) for key in ("records", "source", "destination", "source_available",
                       "source_held", "destination_available", "destination_held", "in_transit")}
            s, d = numbers["source"], numbers["destination"]
            legacy.require(numbers["records"] in (0, 1), "Wrong recovery record count")
            if numbers["records"]:
                legacy.require(row["q"] == "1000" and row["spendable"] == "UNKNOWN",
                               "Changed record or tentative credit counted as spendable")
                legacy.require(s in (4000, 5000) and d in (0, 1000), "Resource image exceeds record authority")
                expected = (4000, 1000 if s == 5000 else 0, 0, 1000 if d == 1000 else 0,
                            1000 if (s, d) == (4000, 0) else 0)
            else:
                legacy.require(row["q"] == "null" and row["spendable"] == "5000" and (s, d) == (4000, 1000),
                               "Changed completed resource total or held amount without record")
                expected = (4000, 0, 1000, 0, 0)
            actual = tuple(numbers[k] for k in ("source_available", "source_held", "destination_available",
                                               "destination_held", "in_transit"))
            legacy.require(actual == expected and s == actual[0] + actual[1] and d == actual[2] + actual[3],
                           "Raw native does not equal available plus record-derived held")
        legacy.require(ticks == list(range(ticks[0], ticks[-1] + 1)), "Missing or repeated recovery tick")
        result = results[0]
        legacy.require(known and rows[-1] == known[-1] and known[-1]["records"] == "0"
                       and result == dict(cut=E3, observations=str(len(known)), total="5000", records="0",
                                          markers="0", source_epoch="1", destination_epoch="1"),
                       "Completion lacks exact final resources, epochs or observation count")
    except (KeyError, ValueError, TypeError) as failure:
        raise SmokeError(f"Malformed S6 accounting observation: {failure}") from failure


def inspect_e5(world):
    ledger = legacy.ledger(world / "data" / legacy.LEDGER)
    legacy.require(not legacy.journal(world)["transfers"], "E5 created a transfer")
    pairs = []
    for chunk in (128, 4):
        region = f"r.{chunk // 32}.{chunk // 32}.mca"
        slot = chunk % 32 + 32 * (chunk % 32)
        terrain = legacy.record(world / "region" / region, slot)
        entities = legacy.record(world / "entities" / region, slot)["Entities"]
        rockets = [e for e in entities if e.get("id") == legacy.ROCKET or "RocketEntityData" in e]
        loaders = [e for e in terrain["block_entities"] if e.get("id") == legacy.LOADER]
        legacy.require(len(rockets) == 1 and len(loaders) == 1, "E5 missing/duplicate native pair")
        pairs.append(dict(loaders=loaders, rocket=rockets[0]))
    return dict(ledger=ledger, pairs=pairs)


def check_e5(image, restart, identity=None):
    legacy.require(restart in (0, 1, 2) and image.get("read_status") == "KNOWN"
                   and isinstance(image.get("native"), dict), "E5 UNKNOWN is not zero loss")
    try:
        disk = normalize_ids(image["native"])
        identity = normalize_ids(identity)
        ledger, pairs = disk["ledger"], disk["pairs"]
        legacy.require(len(pairs) == 2 and ledger["issued_through"] == 2 and ledger["writer_generation"] >= 2
                       and len(ledger["records"]) == (0 if restart else 1), "E5 did not retain only the unloaded fill")
        legacy.require(not any(ledger[k] for k in ("floors", "tombstones", "armed_flights", "rocket_authorities", "issued_launch_serial")),
                       "E5 introduced unrelated flight/settlement authority")
        identities, totals = [], []
        for index, (pair, at) in enumerate(zip(pairs, (2048, 64))):
            legacy.require(len(pair["loaders"]) == 1, "E5 native loader count changed")
            device, rocket = pair["loaders"][0], pair["rocket"]
            source, destination = device["arce_fluid_fuel_loader"], rocket["RocketEntityData"]
            flight = destination["flight_data"]
            held = not restart and index == 0
            tank, bank = source["tank"]["Amount"], flight["fuel"]["propulsion"]["primary"]["amount"]
            legacy.require((tank in (4000, 5000) and bank in (0, 1000)) if held else (tank, bank) == (4000, 1000),
                           "E5 changed a pair's exact native quantities")
            legacy.require(source["tank"] == dict(FluidName=legacy.FUEL, Amount=tank)
                           and flight["fuel"]["propulsion"]["oxidizer"]["amount"] == 0, "E5 changed fluid vector")
            legacy.require(device["id"] == legacy.LOADER and tuple(device[k] for k in ("x", "y", "z")) == (at + 2, 100, at)
                           and rocket["id"] == legacy.ROCKET and rocket["Pos"] == [at + .5, 100., at + .5], "E5 moved/replaced a native endpoint")
            legacy.require(source["owner"] == destination["owner_id"] and source["binding"]["physical_rocket"] == rocket["UUID"]
                           and source["binding"]["logical_rocket"] == destination["assembly_transaction_id"] == flight["logical_rocket_id"],
                           "E5 changed owner or identity binding")
            legacy.require(not rocket.get("Passengers") and not flight["passengers"]["assignments"]
                           and "active_transfer_id" not in flight and "plan" not in flight
                           and not flight["fuel"].get("committed_debits"), "E5 created flight/passenger effects")
            source_after, destination_after = int(tank == 4000), int(bank == 1000)
            retired_ids = []
            for section, after, role in ((source["resource_escrow"], source_after, "SOURCE"),
                                         (destination["resource_escrow"], destination_after, "DESTINATION")):
                if not restart and index == 1:
                    # C8 prunes live markers, not an extra forced fixture save. Native stores may still carry
                    # precisely the retired sequence-2 marker; it is not held fuel or a surviving record.
                    count = len(section["markers"])
                    legacy.require(count in (0, 1), "Unexpected healthy retired marker count")
                    legacy.check_section(section, 1, count, dict(ledger, records=[]), role, 2)
                    retired_ids.extend(m["op_id"] for m in section["markers"])
                else:
                    legacy.check_section(section, after, int(held) * after, ledger, role, index + 1)
            legacy.require(len(set(retired_ids)) <= 1 and not any(m in [op["op_id"] for op in ledger["records"]] for m in retired_ids),
                           "Healthy native marker belongs to another operation")
            if held:
                op = ledger["records"][0]
                legacy.require(op["seq"] == 1 and op["source"]["identity"] == source["device"]
                               and op["source"]["type"] == legacy.LOADER and op["destination"]["identity"] == rocket["UUID"]
                               and op["payload"] == dict(FluidName=legacy.FUEL, Amount=1000)
                               and op["native_binding"]["complete"] == 1 and op["native_binding"]["kind"] == "FUEL_VECTOR_V1",
                               "E5 held image has no matching native record")
            identities.append(dict(device=source["device"], owner=source["owner"], binding=source["binding"],
                                   physical=rocket["UUID"], logical=flight["logical_rocket_id"]))
            totals.append(tank + bank)
        legacy.require(all(identities[0][k] != identities[1][k] for k in ("device", "physical", "logical")),
                       "E5's healthy work is not independent")
        observed = dict(ledger=ledger["ledger_id"], pairs=identities)
        legacy.require(identity is None or observed == identity, "E5 changed identity across restarts")
        return dict(identity=observed, pair_totals=totals, available_total=10000 if restart else None,
                    loss=0 if restart else None)
    except (KeyError, TypeError, IndexError) as failure:
        raise SmokeError(f"Malformed E5 native state: {failure}") from failure
