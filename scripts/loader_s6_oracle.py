"""S6 raw loader evidence. Unreadable stores never become zero-valued resources.

An E3 cut can expose either exact prefix at each independent native store. The
record still owns the entire component: a raw sum is not spendable accounting.
"""
from __future__ import annotations

from pathlib import Path
from uuid import UUID
import gzip
import io
import json
import loader_cut_oracle as legacy
import loader_s6_integrity as integrity
from run_dedicated_server_smoke import SmokeError

E3 = "LF_S6_E3_CACHE_CLOSE"
E5 = "LF_S6_E5_UNLOADED"
R2_KEEP = "LF_S6_R2_KEEP_SOURCE"
R2_DESTRUCTIVE = "LF_S6_R2_DESTRUCTIVE_SOURCE"
R2_RECONNECT = "LF_S6_R2_DUPLICATE_RECONNECT"
R2 = (R2_KEEP, R2_DESTRUCTIVE, R2_RECONNECT)
G3_L1 = "LF_S6_G3_L1_INFLIGHT"
G3_L2 = "LF_S6_G3_L2_INFLIGHT"
G3_QUEUED = "LF_S6_G3_L1_QUEUED"
G3_L2_QUEUED = "LF_S6_G3_L2_QUEUED"
G3_U1 = "LF_S6_G3_L1_UNKNOWN"
G3_U2 = "LF_S6_G3_L2_UNKNOWN"
G3_CLOSE = "LF_S6_G3_FAILED_CLOSE"
G3_UNKNOWN = (G3_U1, G3_U2)
G3_SELF_STOP = G3_UNKNOWN + (G3_CLOSE,)
G3 = (G3_L1, G3_L2, G3_QUEUED, G3_L2_QUEUED) + G3_SELF_STOP
G1_VETO, G1_SATURATION = "LF_S6_G1_VETO", "LF_S6_G1_SATURATION"
G1 = (G1_VETO, G1_SATURATION)

# Evidence-only level.dat envelope, independent of the celestial SavedData budget.
G1_LEVEL_COMPRESSED_BYTES = 1_048_576
G1_LEVEL_DECODED_BYTES = 4_194_304
G1_LEVEL_TAGS = 65_536

# UUID-valued native fields and the identity envelope returned by these oracles.
# Do not recognize UUIDs by array length: coordinates, quantities and hashes are
# not identities. `snapshot`, `ledger` and `identity` can also contain compounds.
UUID_FIELDS = frozenset((
    "UUID", "ledger_id", "op_id", "identity", "owner", "owner_id", "device",
    "physical_rocket", "logical_rocket", "logical_rocket_id", "assembly_transaction_id",
    "rocket_id", "physical", "logical", "snapshot", "snapshot_id", "ledger",
    "transfer_id", "entity_id", "source_entity_id", "destination_entity_id", "active_transfer_id",
    "request_id", "passenger_id", "destination_station_id", "station", "assembly_snapshot", "actor", "anchor_op",
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
        return dict(read_status="KNOWN", native=inspect_g1(world, cut) if cut in G1 else inspect_r2(world) if cut in R2 else inspect_e5(world) if cut == E5 else legacy.inspect(world))
    except (OSError, ValueError, KeyError, TypeError, SmokeError) as failure:
        return dict(read_status="UNKNOWN", native=None,
                    error=f"{type(failure).__name__}: {failure}")


def check_e3(image, restart, identity=None, generations=(2, 3, 3)):
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
        observed["native"] = integrity.pair_identity(disk, disk["ledger"])
        legacy.require(disk["ledger"]["writer_generation"] == generations[restart], "S6 changed durable generation")
        legacy.require(identity is None or observed == identity, "Changed native identity across restarts")
        return dict(prefix=prefix, raw_total=tank + bank,
                    available_total=5000 if restart else None,
                    loss=0 if restart else None, identity=observed)
    except (KeyError, TypeError, IndexError) as failure:
        raise SmokeError(f"Malformed/missing raw loader state: {failure}") from failure


def check_g3_l1(image, restart, identity=None, cut=G3_L1):
    legacy.require(cut in (G3_L1, G3_QUEUED), "Invalid L1 shutdown selection")
    result = check_e3(image, restart, identity, (3, 4, 4) if cut == G3_QUEUED else (2, 3, 3))
    legacy.require(restart != 0 or result["prefix"] == "K2", "Shutdown did not drain the held L1 without applying it")
    return result


def check_g3_l2_queued(image, restart, identity=None):
    result = check_e3(image, restart, identity, (3, 4, 4))
    legacy.require(result["prefix"] == "K6", "Pending native retirement changed its proved fill")
    legacy.require(image["native"]["ledger"]["writer_generation"] == (4 if restart else 3),
                   "Pending native retirement changed the stopped/recovered durable generation")
    return result


def check_g3_unknown(image, restart, identity=None, cut=G3_U1):
    legacy.require(cut in G3_UNKNOWN and restart in (0, 1, 2), "Invalid UNKNOWN shutdown selection")
    if cut == G3_U2:
        result = check_e3(image, restart, identity)
        legacy.require(restart != 0 or result["prefix"] == "K6", "UNKNOWN L2 changed its proved native prefix")
        return result
    legacy.require(image.get("read_status") == "KNOWN" and isinstance(image.get("native"), dict),
                   "UNKNOWN read cannot establish the L1 shutdown baseline")
    try:
        disk = normalize_ids(image["native"])
        observed = legacy.check(disk, "LF_M_E4L1_UNKNOWN", restart)
        observed["ledger"] = disk["ledger"]["ledger_id"]
        observed["native"] = integrity.pair_identity(disk, disk["ledger"])
        legacy.require(disk["ledger"]["writer_generation"] == 1, "UNKNOWN L1 changed durable generation")
        legacy.require(identity is None or observed == normalize_ids(identity), "UNKNOWN L1 changed native identity")
        return dict(identity=observed, raw_total=5000, available_total=5000 if restart else None, loss=0 if restart else None)
    except (KeyError, TypeError, IndexError) as failure:
        raise SmokeError(f"Malformed UNKNOWN L1 shutdown state: {failure}") from failure


def check_g3_l2(image, restart, identity=None, operation=None):
    legacy.require(image.get("read_status") == "KNOWN" and isinstance(image.get("native"), dict)
                   and image["native"].get("ledger", {}).get("writer_generation") == 3,
                   "L2 retirement changed its durable generation")
    return _retired_resources(image, restart, identity, operation)


def _retired_resources(image, restart, identity=None, operation=None):
    legacy.require(restart in (0, 1, 2) and image.get("read_status") == "KNOWN"
                   and isinstance(image.get("native"), dict), "G3 L2 UNKNOWN is not zero loss")
    try:
        disk = normalize_ids(image["native"])
        identity = normalize_ids(identity)
        observed_op = identity["op_id"] if restart and identity else operation
        legacy.require(observed_op is not None, "Missing observed L2 operation")
        observed_op = canonical_uuid(observed_op)
        ledger = disk["ledger"]
        legacy.require(ledger is not None and ledger["issued_through"] == 1 and not ledger["records"],
                       "L2 normal drain did not retire its one operation")
        legacy.require(len(disk["loaders"]) == 1, "L2 requires one real loader")
        source = disk["loaders"][0]["arce_fluid_fuel_loader"]["resource_escrow"]
        destination = disk["rocket"]["RocketEntityData"]["resource_escrow"]
        for section, role in ((source, "SOURCE"), (destination, "DESTINATION")):
            count = len(section["markers"])
            legacy.require(count == 0 if restart else count in (0, 1), "Unexpected retired L2 marker count")
            legacy.check_section(section, 1, count, ledger, role, 1)
            if count:
                legacy.require(section["markers"][0]["op_id"] == observed_op, "Retired marker names another L2 operation")
            # Only the already validated retired metadata is projected away on
            # this private copy, to reuse the exact completed quantity/binding checks.
            section["markers"] = []
        observed = legacy.check(disk, "LF_M_K7", 1)
        observed.update(ledger=ledger["ledger_id"], op_id=observed_op)
        observed["native"] = integrity.pair_identity(disk, ledger)
        legacy.require(identity is None or observed == identity, "L2 changed native identity across restarts")
        return dict(identity=observed, raw_total=5000, available_total=5000 if restart else None,
                    loss=0 if restart else None)
    except (KeyError, TypeError, IndexError) as failure:
        raise SmokeError(f"Malformed G3 L2 state: {failure}") from failure


def shutdown_operation(lines, cut):
    import re
    matches = [match for line in lines if (match := re.search(
        r"ARCE_LOADER_S6_DRAIN_READY cut=" + re.escape(cut)
        + r" boundary=K8 running=true operation=([0-9a-f-]{36})(?:\s|$)", line))]
    legacy.require(len(matches) == 1, "Missing/repeated L2 writer operation observation")
    operation = matches[0][1]
    try:
        legacy.require(str(UUID(operation)) == operation, "Noncanonical L2 operation observation")
    except ValueError as failure:
        raise SmokeError("Malformed L2 operation observation") from failure
    return operation


def check_observations(lines: list[str], restart: int, cut=E3, retired_operation=None) -> None:
    """Require the selected propagated failure, not merely a checkpoint label."""
    import re
    if cut in OPERATOR:
        check_o1_logs(lines, restart, cut)
        return
    if cut in G1:
        check_g1_logs(lines, restart, cut)
        return
    if cut in G3:
        if restart:
            if cut == G3_U1:
                expected = "ARCE_LOADER_MATRIX_STATE cut=LF_M_E4L1_UNKNOWN phase=recovered tanks=[5000] banks=0,0 epochs=[0] rocket_epoch=0 source_markers=[0] rocket_markers=0 issued=0 records=0 pending=0"
                legacy.require(sum(expected in line for line in lines) == 1 and
                               sum("ARCE_LOADER_MATRIX_RESULT cut=LF_M_E4L1_UNKNOWN" in line for line in lines) == 1,
                               "UNKNOWN L1 restart did not retain the exact unfilled baseline")
            else:
                check_recovery_observations(lines, retired_operation if cut == G3_L2 else None)
        else:
            boundary = "C6_PENDING" if cut == G3_L2_QUEUED else "K8" if cut == G3_L2 else "WRITE_UNKNOWN" if cut in G3_UNKNOWN else "BARRIER_IO" if cut == G3_CLOSE else "K1"
            suffix = " operation=" + shutdown_operation(lines, cut) if cut == G3_L2 else ""
            if cut in G3_UNKNOWN or cut in (G3_QUEUED, G3_L2_QUEUED):
                matches = [match for line in lines if (match := re.search(
                    r"ARCE_LOADER_S6_DRAIN_READY cut=" + re.escape(cut) + r" boundary=" + boundary
                    + r" running=true state=(\S+)", line))]
                legacy.require(len(matches) == 1, "Missing/repeated actual shutdown writer state")
                state = matches[0][1]
                suffix = " state=" + state
                if cut == G3_L2_QUEUED:
                    legacy.require(state == "active:true,successor:false,completions:0,running:true,unknown:false,generation:3,durable:2",
                                   "Pending native retirement lacks the busy non-retiring write")
                    expected = "ARCE_LOADER_S6_L2_PENDING records=1 pending=1 issued=1 proved=true locked=true tank=4000 bank=1000 epochs=1,1 markers=1,1"
                    legacy.require(sum(re.search(re.escape(expected) + r"(?:\s|$)", line) is not None for line in lines) == 1,
                                   "Missing/repeated proved pending retirement and exact resources")
                elif cut == G3_QUEUED:
                    legacy.require(state == "active:true,successor:true,completions:0,running:true,unknown:false,generation:2,durable:1",
                                   "Shutdown did not observe the uncaptured L1 successor")
                else:
                    match = re.fullmatch(r"active:true,successor:(true|false),completions:0,running:false,unknown:true,generation:(\d+),durable:(\d+)", state)
                    legacy.require(match is not None, "Shutdown was not in actual UNKNOWN state")
                    generation, durable = int(match[2]), int(match[3])
                    legacy.require(generation == durable + 1 and (durable == 1 if cut == G3_U1 else durable >= 2),
                                   "UNKNOWN shutdown changed the candidate/durable generation")
                    abandoned = f"Escrow ledger shutdown stopped retrying generation {generation} after 3 unknown outcomes; restart uses the durable ledger file"
                    resolved = f"Escrow ledger generation {generation} resolved as FAILED_BEFORE_REPLACE after an unknown outcome"
                    legacy.require(sum(abandoned in line or cut == G3_U2 and resolved in line for line in lines) == 1,
                                   "UNKNOWN shutdown lacks its bounded drain result")
            ready = "ARCE_LOADER_S6_DRAIN_READY cut=" + cut + " boundary=" + boundary + " running=true" + suffix
            released = "ARCE_LOADER_S6_DRAIN_RELEASE cut=" + cut + " boundary=" + boundary + " running=false" + suffix
            positions = []
            for marker in (ready, released):
                found = [i for i, line in enumerate(lines) if re.search(re.escape(marker) + r"(?:\s|$)", line)]
                legacy.require(len(found) == 1, "Missing/repeated exact shutdown writer observation")
                positions.append(found[0])
            legacy.require(positions[0] < positions[1] and any("Stopping server" in line for line in lines),
                           "Shutdown lacks ordered writer observations and a normal stop")
        errors = [line for line in lines if "/ERROR]" in line or "/FATAL]" in line]
        if cut == G3_CLOSE and not restart:
            legacy.require(1 <= len(errors) <= 2 and all("/ERROR]" in line and "Failed to store chunk [4, 4]" in line for line in errors),
                           "Failed-close shutdown has unrelated or repeated native errors")
            expected = "ARCE_LOADER_S6_CLOSE_FAILURE records=1 pending=1 issued=1 io_failures=1 reason=BARRIER_IO proof=false"
            legacy.require(sum(expected in line for line in lines) == 1
                           and sum("ARCE_LOADER_S6_CACHE regions=256 own_region_cached=false" in line for line in lines) == 1
                           and any("java.nio.channels.ClosedChannelException" in line for line in lines),
                           "Shutdown lacks the propagated own-close failure and retained authority")
        else:
            legacy.require(not errors, "G3 ERROR/FATAL")
        return
    if cut in R2:
        tag = "RESULT" if restart else "PAUSED"
        wanted = ("ARCE_LOADER_S6_R2_" + tag + " cut=" + cut
                  + " tank=4000 bank=1000 total=5000 epochs=1,1 records=0 markers=0 journal=1 copies="
                  + ("1" if cut == R2_KEEP else "2") + " reason=FLOOR_CONFLICT passenger="
                  + ("unmounted" if cut == R2_RECONNECT else "none"))
        legacy.require(sum(wanted in line for line in lines) == 1, "Missing/repeated R2 retained authority result")
        legacy.require(not any("/ERROR]" in line or "/FATAL]" in line for line in lines), "R2 ERROR/FATAL")
        return
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


def check_recovery_observations(lines: list[str], retired_operation=None) -> None:
    """Check every tick's partition independently of the JVM observer's verdict."""
    def fields(line, marker):
        return dict(token.split("=", 1) for token in line.split(marker, 1)[1].strip().split())
    rows = [fields(line, "ARCE_LOADER_S6_ACCOUNT ") for line in lines if "ARCE_LOADER_S6_ACCOUNT " in line]
    results = [fields(line, "ARCE_LOADER_S6_RESULT ") for line in lines if "ARCE_LOADER_S6_RESULT " in line]
    begins = [fields(line, "ARCE_LOADER_S6_BEGIN ") for line in lines if "ARCE_LOADER_S6_BEGIN " in line]
    legacy.require(rows and len(results) == 1 and len(begins) == 1, "Missing/repeated S6 accounting interval or completion")
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
            retired = int(row.get("retired", "0"))
            legacy.require(0 <= retired <= 2, "Wrong retired metadata count")
            if "retired" in row or "retired_operation" in row:
                legacy.require(retired_operation is not None and row.get("retired_operation") == canonical_uuid(retired_operation),
                               "Retired metadata lacks the observed operation identity")
            if numbers["records"]:
                legacy.require(retired == 0, "Active record markers misclassified as retired metadata")
                legacy.require(row["q"] == "1000" and row["spendable"] == "UNKNOWN",
                               "Changed record or tentative credit counted as spendable")
                legacy.require(s in (4000, 5000) and d in (0, 1000), "Resource image exceeds record authority")
                expected = (4000, 1000 if s == 5000 else 0, 0, 1000 if d == 1000 else 0,
                            1000 if (s, d) == (4000, 0) else 0)
            else:
                legacy.require(row["q"] == "null" and row["spendable"] == ("UNKNOWN" if retired else "5000")
                               and (s, d) == (4000, 1000),
                               "Changed completed resource total or held amount without record")
                expected = (4000, 0, 1000, 0, 0)
            actual = tuple(numbers[k] for k in ("source_available", "source_held", "destination_available",
                                               "destination_held", "in_transit"))
            legacy.require(actual == expected and s == actual[0] + actual[1] and d == actual[2] + actual[3],
                           "Raw native does not equal available plus record-derived held")
        legacy.require(ticks == list(range(ticks[0], ticks[-1] + 1)), "Missing or repeated recovery tick")
        result = results[0]
        start_tick, end_tick = int(begins[0]["tick"]), int(result["end_tick"])
        order = ["BEGIN" if "ARCE_LOADER_S6_BEGIN " in line else "ACCOUNT" if "ARCE_LOADER_S6_ACCOUNT " in line
                 else "RESULT" for line in lines if any("ARCE_LOADER_S6_" + key + " " in line for key in ("BEGIN", "ACCOUNT", "RESULT"))]
        legacy.require(begins[0] == dict(tick=str(start_tick)) and 100 <= end_tick - start_tick <= 2400
                       and ticks == list(range(start_tick, end_tick + 1))
                       and order == ["BEGIN"] + ["ACCOUNT"] * len(rows) + ["RESULT"],
                       "Missing prefix/suffix or misplaced S6 observation interval")
        legacy.require(known and rows[-1] == known[-1] and known[-1]["records"] == "0"
                       and int(known[-1].get("retired", "0")) == 0
                       and result == dict(cut=E3, observations=str(len(known)), total="5000", records="0",
                                          markers="0", source_epoch="1", destination_epoch="1", start_tick=str(start_tick),
                                          end_tick=str(end_tick), total_observations=str(len(rows))),
                       "Completion lacks exact final resources, epochs or observation count")
    except (KeyError, ValueError, TypeError) as failure:
        raise SmokeError(f"Malformed S6 accounting observation: {failure}") from failure


O1_BEFORE, O1_REPLACED, O1_ACK = "LF_S6_O1_BEFORE", "LF_S6_O1_REPLACED", "LF_S6_O1_ACK"
O1 = (O1_BEFORE, O1_REPLACED, O1_ACK)
O_STAGED = tuple("LF_S6_" + hook for hook in ("O2_BEFORE_0", "O2_AFTER_0", "O2_BEFORE_1", "O2_AFTER_1",
                                               "O3_BEFORE", "O3_REPLACED", "O3_ACK", "O4_BEFORE"))
O_RELEASED = ("LF_S6_O4_REPLACED", "LF_S6_O4_ACK")
OPERATOR = O1 + O_STAGED + O_RELEASED
O_INSTALLED = O_STAGED[-3:]
O_HOOKS = dict(zip(OPERATOR, ("K1", "K2", "O1_ACK", "O2_BEFORE_0", "O2_AFTER_0", "O2_BEFORE_1", "O2_AFTER_1",
                            "K1", "K2", "O3_AFTER", "K8", "K9", "O4_AFTER")))


def pending_receipt(operation):
    """Exact unreleased schema-3 receipt for this intact single-member decision."""
    intent = operation["settlement"]
    observations = [{key: value for key, value in item.items() if key != "normalized_wrapper"}
                    for item in intent["observations"]]
    return dict(schema_version=3, op_id=operation["op_id"], retired=[dict(op_id=operation["op_id"])],
                guards=[operation["transfer_guard"]], before_choice=observations, payload_amount=1000,
                payload_hash=operation["payload_hash"], accounting_status="UNKNOWN", decision_digest=intent["digest"],
                actor=intent["actor"], actor_name=intent["actor_name"], choice=intent["choice"],
                confirmed_tick=intent["confirmed_tick"], released=0, release_generation=0, anchor_op=operation["op_id"],
                missing=[], survivors=[dict(endpoint=o["endpoint"], epoch=o["state"]["epoch"], signature=o["state"]["signature"],
                    signature_kind=o["signature_kind"], invariant_digest=o["invariant_digest"], quantity=o["quantity"], bound=True)
                    for o in observations])


def check_o1(image, restart, identity=None, cut=O1_BEFORE):
    """Frozen operator intent/receipt is authority; its UNKNOWN loss is never zero."""
    if cut in O_RELEASED:
        return check_o4_released(image, restart, identity, cut)
    import copy
    import re
    from run_v180_rocket_settlement_completion import require_progress
    from run_v180_rocket_settlement_intent import ACTOR
    legacy.require(cut in OPERATOR and restart in (0, 1, 2) and image.get("read_status") == "KNOWN"
                   and isinstance(image.get("native"), dict), "Settlement UNKNOWN is not zero loss")
    try:
        disk = normalize_ids(image["native"]); identity = normalize_ids(identity)
        root = disk["ledger"]
        released = bool(restart and cut != O1_BEFORE)
        legacy.require(root is not None and root["issued_through"] == 1 and root["issued_launch_serial"] == 0
                       and not root["floors"] and not root["armed_flights"], "Settlement changed unrelated authority")
        installed = released or cut in O_INSTALLED
        legacy.require(len(root["records"]) == int(not released) and len(root["tombstones"]) == int(installed)
                       and len(root["rocket_authorities"]) == int(released), "Settlement lost or prematurely released authority")
        # Validate the exact ordinary resource envelopes separately from the operator
        # tables. This private projection is not a modified native image or a verdict.
        resources = copy.deepcopy(disk)
        for field in ("tombstones", "rocket_authorities"):
            resources["ledger"][field] = []
        resource_identity = legacy.check(resources, "LF_M_K6", int(released))
        resource_identity["ledger"] = root["ledger_id"]
        resource_identity["snapshot"] = disk["rocket"]["RocketEntityData"]["snapshot"]
        resource_identity["native"] = integrity.pair_identity(disk, root)
        legacy.require(identity is None or identity["resources"] == resource_identity,
                       "Settlement changed endpoint/ledger identity")
        actor = canonical_uuid(ACTOR)
        authority = root["tombstones"][0] if released else root["records"][0]
        operation = authority["op_id"]
        if not released:
            binding = authority["native_binding"]
            legacy.require(binding["physical"] == resource_identity["physical"] and binding["logical"] == resource_identity["logical"]
                           and binding["owner"] == resource_identity["owner"] == authority["owner"]
                           and binding["snapshot"] == resource_identity["snapshot"]["snapshot_id"], "Changed native settlement binding")
            legacy.require(authority["seq"] == 1 and authority["phase"] == ("REPAIR" if cut == O1_BEFORE else "SETTLING_ACCEPT")
                           and authority["reason"] == "OTHER_STATE", "Wrong operator boundary or manual cause")
            intent = authority.get("settlement", {})
            if cut == O1_BEFORE:
                legacy.require(not intent, "Pre-O1 invented an operator decision")
            else:
                legacy.require(intent["schema_version"] == 2 and intent["choice"] == "ACCEPT_CURRENT"
                               and intent["ledger_id"] == root["ledger_id"] and intent["actor"] == actor
                               and intent["actor_name"] == "Server" and re.fullmatch(r"[0-9a-f]{64}", intent["digest"])
                               and intent["confirmed_tick"] >= intent["preview_tick"] >= 0,
                               "Wrong frozen decision identity/choice/actor")
                legacy.require(len(intent["members"]) == 1 and intent["members"][0]["op_id"] == operation
                               and intent["members"][0]["phase"] == "REPAIR" and intent["members"][0]["reason"] == "OTHER_STATE"
                               and re.fullmatch(r"[0-9a-f]{64}", intent["members"][0]["digest"]), "Wrong frozen member")
                observations = intent["observations"]
                legacy.require(len(observations) == 2 and all(not o["missing"] and o["bound"] for o in observations),
                               "Missing/changed chosen survivor")
                for endpoint, quantity, role in ((authority["source"], 4000, "SOURCE"), (authority["destination"], 1000, "DESTINATION")):
                    chosen = [o for o in observations if o["endpoint"] == endpoint]
                    legacy.require(len(chosen) == 1 and chosen[0]["quantity"] == quantity, "Wrong frozen endpoint/quantity")
                    state = chosen[0]["state"]
                    legacy.require(state["ledger_id"] == root["ledger_id"] and state["epoch"] == 1 and state["signature"] == endpoint["after_signature"]
                                   and state["markers"] == [dict(op_id=operation, seq=1, role=role, epoch_after=1)],
                                   "Wrong frozen survivor marker/epoch")
            if installed:
                legacy.require(root["tombstones"] == [pending_receipt(authority)],
                               "Changed installed receipt or premature acknowledgement of release")
        else:
            legacy.require(authority["schema_version"] == 3 and authority["released"] == 1
                           and authority["accounting_status"] == "UNKNOWN" and "audited_loss" not in authority
                           and "loss_known" not in authority and authority["choice"] == "ACCEPT_CURRENT"
                           and authority["actor"] == actor and authority["actor_name"] == "Server"
                           and authority["payload_amount"] == 1000 and 0 < authority["release_generation"] <= root["writer_generation"],
                           "Wrong returning-identity receipt or invented accounting")
            legacy.require(identity is not None, "Released receipt needs its initial frozen decision")
            require_progress(identity["authority"], root)
            initial = identity["authority"]["records"][0]
            legacy.require(authority == dict(pending_receipt(initial), released=1, release_generation=authority["release_generation"]),
                           "Release changed the complete immutable receipt")
            legacy.require(authority["retired"] == [dict(op_id=initial["op_id"])] and authority["anchor_op"] == initial["op_id"]
                           and authority["payload_hash"] == initial["payload_hash"] and not authority["missing"],
                           "Receipt changed its retired identity or payload")
            chosen = next(o for o in initial["settlement"]["observations"] if o["endpoint"]["kind"] == "ENTITY")
            head = root["rocket_authorities"][0]
            expected = dict(v=1, l=resource_identity["logical"], o=resource_identity["owner"], a=initial["op_id"],
                            g=authority["release_generation"], p=resource_identity["physical"], e=1, k="FUEL_VECTOR_V1",
                            s=list(bytes.fromhex(chosen["state"]["signature"])), i=list(bytes.fromhex(chosen["invariant_digest"])),
                            issued=0, completed=0, outcome="READY")
            decoded_head = dict(head, **{key: canonical_uuid(head[key]) for key in ("l", "o", "a", "p")})
            legacy.require(decoded_head == expected, "Settlement changed the READY authority")
            if "completed" in identity:
                legacy.require(identity["completed"] == dict(receipts=root["tombstones"], heads=root["rocket_authorities"]),
                               "Second restart changed the complete released authority")
        if identity is not None and cut == O1_BEFORE:
            legacy.require(root["records"] == identity["authority"]["records"], "Pre-O1 changed the held record")
        observed = identity or dict(resources=resource_identity, authority=root)
        if released:
            observed = dict(observed, completed=dict(receipts=root["tombstones"], heads=root["rocket_authorities"]))
        return dict(identity=observed, raw_total=5000, available_total=None, loss=None,
                    state="RELEASED" if released else "PRE_O1" if cut == O1_BEFORE else "INSTALLED_UNRELEASED" if installed else "FROZEN_O1")
    except (KeyError, TypeError, IndexError, ValueError) as failure:
        raise SmokeError(f"Malformed operator settlement observation: {failure}") from failure


def check_o4_released(image, restart, identity, cut):
    """Late O4 has receipt authority, not a fabricated earlier native O1 image."""
    import copy
    import hashlib
    import re
    import struct
    from run_v180_rocket_settlement_intent import ACTOR
    legacy.require(cut in O_RELEASED and restart in (0, 1, 2) and image.get("read_status") == "KNOWN"
                   and isinstance(image.get("native"), dict), "O4 UNKNOWN is not zero loss")
    try:
        disk = normalize_ids(image["native"]); identity = normalize_ids(identity)
        root = disk["ledger"]
        legacy.require(root is not None and root["issued_through"] == 1 and root["issued_launch_serial"] == 0
                       and not root["records"] and not root["floors"] and not root["armed_flights"]
                       and len(root["tombstones"]) == 1 and len(root["rocket_authorities"]) == 1, "Wrong O4 authority tables")
        receipt = root["tombstones"][0]; operation = receipt["op_id"]
        legacy.require(str(UUID(operation)) == operation, "O4 operation identity is not a UUID")
        resources = copy.deepcopy(disk)
        resources["ledger"]["tombstones"] = []; resources["ledger"]["rocket_authorities"] = []
        # Reuse only its exact raw/retired-metadata checks, not its normal-retirement accounting claim.
        checked = _retired_resources(dict(read_status="KNOWN", native=resources), restart, operation=operation)
        observed = dict(checked["identity"], snapshot=disk["rocket"]["RocketEntityData"]["snapshot"])
        legacy.require(identity is None or identity["resources"] == observed, "O4 changed resource or snapshot identity")
        observations = receipt["before_choice"]
        legacy.require(len(observations) == 2 and {o["endpoint"]["kind"] for o in observations} == {"BLOCK_ENTITY", "ENTITY"},
                       "O4 lost an audited survivor")
        for o in observations:
            legacy.require(set(o) == {"endpoint", "missing", "bound", "quantity", "signature_kind", "invariant_digest", "state"},
                           "O4 changed the audit observation fields")
            endpoint = o["endpoint"]; entity = endpoint["kind"] == "ENTITY"
            expected = dict(kind=endpoint["kind"], level="minecraft:overworld", identity=observed["physical"] if entity else observed["loaders"][0]["device"],
                type=legacy.ROCKET if entity else legacy.LOADER, x=4 if entity else 66, y=0 if entity else 100,
                z=4 if entity else 64, unit="PRIMARY" if entity else "TANK", before_epoch=0, after_epoch=1,
                before_signature=endpoint["before_signature"], after_signature=endpoint["after_signature"])
            if entity: expected["logical_rocket"] = observed["logical"]
            legacy.require(endpoint == expected and all(re.fullmatch(r"[0-9a-f]{64}", endpoint[k]) for k in ("before_signature", "after_signature"))
                           and o["missing"] == 0 and o["bound"] == 1 and o["quantity"] == (1000 if entity else 4000)
                           and o["signature_kind"] == ("FUEL_VECTOR_V1" if entity else "FLUID_TANK_V1")
                           and re.fullmatch(r"[0-9a-f]{64}", o["invariant_digest"]), "O4 changed its chosen endpoint or quantity")
            legacy.require(o["state"] == dict(ledger_id=root["ledger_id"], epoch=1, signature=endpoint["after_signature"],
                markers=[dict(op_id=operation, seq=1, epoch_after=1, role="DESTINATION" if entity else "SOURCE")]), "O4 changed audited markers/epochs")
        # NC1 for this fixture's fixed standard compound: Amount:int and FluidName:string, ASCII sorted keys.
        def utf(value): return struct.pack(">H", len(value)) + value.encode("ascii")
        payload_hash = hashlib.sha256(b"\x0a\0\0\x03" + utf("Amount") + struct.pack(">i", 1000)
                                      + b"\x08" + utf("FluidName") + utf(legacy.FUEL) + b"\0").hexdigest()
        legacy.require(len(receipt["guards"]) == 1, "O4 changed the named guard count")
        guard = receipt["guards"][0]
        legacy.require(guard == dict(rocket_id=observed["logical"], journal_generation=1, journal_digest=guard["journal_digest"])
                       and re.fullmatch(r"[0-9a-f]{64}", guard["journal_digest"])
                       and re.fullmatch(r"[0-9a-f]{64}", receipt["decision_digest"])
                       and type(receipt["confirmed_tick"]) is int and receipt["confirmed_tick"] >= 0
                       and 0 < receipt["release_generation"] <= root["writer_generation"], "O4 changed its audit guard or generation")
        # This is an expected receipt projection only, never presented as an observed pre-O4 record.
        expected = pending_receipt(dict(op_id=operation, transfer_guard=guard, payload_hash=payload_hash,
            settlement=dict(actor=canonical_uuid(ACTOR), actor_name="Server", choice="ACCEPT_CURRENT",
                digest=receipt["decision_digest"], confirmed_tick=receipt["confirmed_tick"], observations=observations)))
        legacy.require(receipt == dict(expected, released=1, release_generation=receipt["release_generation"]), "O4 changed its complete receipt")
        chosen = next(o for o in observations if o["endpoint"]["kind"] == "ENTITY")
        head = root["rocket_authorities"][0]
        decoded_head = dict(head, **{key: canonical_uuid(head[key]) for key in ("l", "o", "a", "p")})
        legacy.require(decoded_head == dict(v=1, l=observed["logical"], o=observed["owner"], a=operation, g=receipt["release_generation"],
            p=observed["physical"], e=1, k="FUEL_VECTOR_V1", s=list(bytes.fromhex(chosen["state"]["signature"])),
            i=list(bytes.fromhex(chosen["invariant_digest"])), issued=0, completed=0, outcome="READY"), "O4 changed READY authority")
        authority = dict(receipts=root["tombstones"], heads=root["rocket_authorities"])
        legacy.require(identity is None or identity["authority"] == authority, "Restart changed the complete O4 authority")
        return dict(identity=dict(resources=observed, authority=authority), raw_total=5000, available_total=None, loss=None, state="RELEASED")
    except (KeyError, TypeError, IndexError, ValueError) as failure:
        raise SmokeError(f"Malformed released O4 observation: {failure}") from failure


def check_o1_logs(lines, restart, cut):
    lines = [line.rstrip("\r\n") for line in lines]
    marker = (f"ARCE_LOADER_S6_O_RESULT cut={cut} tank=4000 bank=1000 epochs=1,1 "
              f"records={int(cut == O1_BEFORE)} receipts={int(cut != O1_BEFORE)} state={'PRE_O1' if cut == O1_BEFORE else 'RELEASED'}"
              if restart else f"ARCE_LOADER_S6_O_PAUSED cut={cut} hook=" + O_HOOKS[cut])
    legacy.require(sum(line.endswith(marker) for line in lines) == 1, "Missing/repeated operator boundary")
    legacy.require(not any("/ERROR" in line or "/FATAL" in line for line in lines), "Unexpected operator settlement error")


def guard_position(index):
    legacy.require(0 <= index < 257, "Guard coordinate outside its fixed bound")
    return ((4 + index % 16) * 16 + 2, 100, (4 + index // 16) * 16)


def block_at(chunk, at):
    x, y, z = at
    sections = [section for section in chunk["sections"] if section["Y"] == y // 16]
    legacy.require(len(sections) == 1, "Missing/duplicate terrain section")
    states = sections[0]["block_states"]
    palette = states["palette"]
    legacy.require(0 < len(palette) <= 4096, "Invalid native block palette")
    if len(palette) == 1:
        return palette[0]["Name"]
    bits = max(4, (len(palette) - 1).bit_length()); per_word = 64 // bits
    index = (y & 15) * 256 + (z & 15) * 16 + (x & 15)
    value = (states["data"][index // per_word] >> (index % per_word * bits)) & ((1 << bits) - 1)
    legacy.require(value < len(palette), "Native block palette index out of bounds")
    return palette[value]["Name"]


def read_g1_day_time(path):
    from inspect_celestial_saved_data import NbtReader
    legacy.require(not path.is_symlink() and path.is_file(), "Missing/unsafe global control")
    with path.open("rb") as stream:
        compressed = stream.read(G1_LEVEL_COMPRESSED_BYTES + 1)
    legacy.require(len(compressed) <= G1_LEVEL_COMPRESSED_BYTES, "Global control exceeds compressed bound")
    try:
        with gzip.GzipFile(fileobj=io.BytesIO(compressed)) as stream:
            payload = stream.read(G1_LEVEL_DECODED_BYTES + 1)
    except (OSError, EOFError) as failure:
        raise SmokeError("Global control is not complete gzip NBT") from failure
    legacy.require(len(payload) <= G1_LEVEL_DECODED_BYTES, "Global control exceeds decoded bound")
    # A separate reader instance with a fixed, evidence-specific tag limit. The
    # celestial default and its depth/collection/string limits are not modified.
    value = NbtReader(payload, max_tags=G1_LEVEL_TAGS).read_root()["Data"]["DayTime"]
    legacy.require(type(value) is int, "Global DayTime is not an integer")
    return value


def inspect_g1(world, cut):
    disk = legacy.inspect(world)
    chunks = {}
    for index in range(257 if cut == G1_SATURATION else 1):
        x, _, z = guard_position(index); cx, cz = x // 16, z // 16
        chunks[cx, cz] = legacy.record(world / "region" / f"r.{cx // 32}.{cz // 32}.mca", cx % 32 + 32 * (cz % 32))
    disk["loaders"] = [entry for chunk in chunks.values() for entry in chunk["block_entities"] if entry.get("id") == legacy.LOADER]
    disk["protected_block"] = block_at(chunks[4, 4], (67, 100, 64))
    control = legacy.record(world / "region" / "r.0.0.mca", 21 + 32 * 4)
    disk["control_block"] = block_at(control, (338, 100, 64))
    disk["day_time"] = read_g1_day_time(world / "level.dat")
    disk["files"]["level.dat"] = legacy.digest(world / "level.dat")
    return disk


def check_g1(image, restart, identity=None, cut=G1_VETO):
    legacy.require(cut in G1 and restart in (0, 1, 2) and image.get("read_status") == "KNOWN"
                   and isinstance(image.get("native"), dict), "Guard UNKNOWN is not zero loss")
    try:
        disk = normalize_ids(image["native"])
        count = 257 if cut == G1_SATURATION else 1
        legacy.require(disk["ledger"] is None and len(disk["loaders"]) == count, "Guard admitted escrow or changed loader count")
        # The first device and the rocket must retain the exact never-admitted native baseline.
        legacy.check(dict(disk, loaders=[disk["loaders"][0]]), "LF_M_N0", 0)
        data = disk["rocket"]["RocketEntityData"]
        roots, devices = [], set()
        for index, loader in enumerate(disk["loaders"]):
            source = loader["arce_fluid_fuel_loader"]
            legacy.require(loader["id"] == legacy.LOADER and tuple(loader[k] for k in ("x", "y", "z")) == guard_position(index),
                           "Guard changed native loader type/position")
            legacy.require(source["schema_version"] == 1 and source["tank"] == dict(FluidName=legacy.FUEL, Amount=5000),
                           "Guard failed to retain the valid disk baseline")
            legacy.check_section(source["resource_escrow"], 0, 0, None, "SOURCE", 0)
            legacy.require(source["owner"] == data["owner_id"] and source["binding"] == dict(
                schema_version=1, level="minecraft:overworld", x=64, y=100, z=64,
                physical_rocket=disk["rocket"]["UUID"], logical_rocket=data["assembly_transaction_id"]), "Guard changed owner/binding")
            legacy.require(source["device"] not in devices, "Guard duplicated a native device identity")
            devices.add(source["device"]); roots.append(source)
        legacy.require(disk["protected_block"] == "minecraft:stone", "Protected terrain was saved despite veto")
        expected = "minecraft:stone" if cut == G1_SATURATION else "minecraft:diamond_block" if restart == 1 else "minecraft:gold_block"
        legacy.require(disk["control_block"] == expected and disk["day_time"] == (restart + 1) * 10000
                       and json.loads(disk["rocket"]["CustomName"]) == {"text": "W211_G1_" + str(restart + 1)},
                       "Terrain/global/entity save scopes differ from the selected guard")
        observed = dict(roots=roots, rocket_data=data, physical=disk["rocket"]["UUID"])
        legacy.require(identity is None or observed == normalize_ids(identity), "Guard changed retained root/rocket identity across restarts")
        return dict(identity=observed, raw_total=5000 * count, available_total=None, loss=None,
                    scope="LEVEL_TERRAIN" if cut == G1_SATURATION else "ONE_CHUNK_TERRAIN")
    except (KeyError, TypeError, IndexError, ValueError) as failure:
        raise SmokeError(f"Malformed native guard observation: {failure}") from failure


def check_g1_logs(lines, restart, cut):
    import re
    lines = [line.rstrip("\r\n") for line in lines]
    saturated = cut == G1_SATURATION
    result = (f"ARCE_LOADER_S6_G1_RESULT cut={cut} round={restart} loaders={257 if saturated else 1} "
              f"denials={256 if saturated else 1} scope={'LEVEL' if saturated else 'CHUNK'} retained_bits=7fc00001 save_attempted=true")
    capacity = "ARCE_LOADER_S6_G1_CAPACITY entries=256 duplicate_stable=true unrelated_allowed=true"
    legacy.require(sum(line.endswith(result) for line in lines) == 1
                   and sum(line.endswith(capacity) for line in lines) == int(saturated), "Missing/repeated native guard result or capacity proof")
    refusal = "Refusing chunk save with a fluid fuel loader root that native serialization cannot keep; back up and repair offline"
    overflow = "Refusing chunk save after denial capacity exhausted; back up and repair first"
    legacy.require(any(refusal in line for line in lines) and any(overflow in line for line in lines) == saturated,
                   "Missing selected native save refusal")
    errors = [index for index, line in enumerate(lines) if "/ERROR" in line or "/FATAL" in line]
    legacy.require(0 < len(errors) <= 4096, "Missing or excessive native guard error diagnostics")
    for index in errors:
        reasons = (refusal, overflow) if saturated else (refusal,)
        bus = any(lines[index].endswith("Exception caught during firing event: " + reason) for reason in reasons)
        chunk = (re.search(r"Failed to save chunk \d+,\d+\s*$", lines[index]) is not None and index + 1 < len(lines)
                 and any(lines[index + 1].endswith("java.lang.IllegalStateException: " + reason) for reason in reasons))
        legacy.require(bus or chunk,
                       "Unexpected error during native guard experiment")


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
        legacy.require(ledger["writer_generation"] == (5 if restart else 4), "E5 changed durable generation")
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
                                   physical=rocket["UUID"], logical=flight["logical_rocket_id"],
                                   native=integrity.pair_identity(pair, ledger)))
            totals.append(tank + bank)
        legacy.require(all(identities[0][k] != identities[1][k] for k in ("device", "physical", "logical")),
                       "E5's healthy work is not independent")
        observed = dict(ledger=ledger["ledger_id"], pairs=identities)
        legacy.require(identity is None or observed == identity, "E5 changed identity across restarts")
        return dict(identity=observed, pair_totals=totals, available_total=10000 if restart else None,
                    loss=0 if restart else None)
    except (KeyError, TypeError, IndexError) as failure:
        raise SmokeError(f"Malformed E5 native state: {failure}") from failure


def inspect_r2(world):
    root = legacy.ledger(world / "data" / legacy.LEDGER)
    terrain = legacy.record(world / "region" / "r.0.0.mca", 132)
    entities = legacy.record(world / "entities" / "r.0.0.mca", 132)["Entities"]
    loaders = [e for e in terrain["block_entities"] if e.get("id") == legacy.LOADER]
    rockets = [e for e in entities if e.get("id") == legacy.ROCKET or "RocketEntityData" in e]
    source = [e for e in rockets if e["Pos"] == [64.5, 100., 64.5]]
    legacy.require(len(source) == 1, "R2 missing/duplicate source")
    return dict(ledger=root, loaders=loaders, rocket=source[0], copies=[e for e in rockets if e is not source[0]],
                journal=legacy.journal(world))


def check_r2(image, restart, identity=None, cut=R2_KEEP):
    legacy.require(cut in R2 and restart in (0, 1, 2) and image.get("read_status") == "KNOWN"
                   and isinstance(image.get("native"), dict), "R2 UNKNOWN is not zero loss")
    try:
        disk = normalize_ids(image["native"])
        # Each R2 seed is after exactly one completed real fill. Stale history never authorizes a rewrite.
        observed = legacy.check(disk, "LF_M_K7", 1)
        data = disk["rocket"]["RocketEntityData"]
        copies, records = disk["copies"], disk["journal"]["transfers"]
        legacy.require(len(records) == 1 and len(copies) == (0 if cut == R2_KEEP else 1), "R2 changed journal or copy count")
        record = records[0]
        legacy.require(record["phase"] == ("COMMITTED" if cut == R2_DESTRUCTIVE else "PREPARED")
                       and record["source_entity_id"] == observed["physical"]
                       and record["logical_rocket_id"] == observed["logical"] and record["owner_id"] == observed["owner"]
                       and record["source_snapshot"] == data["snapshot"], "R2 changed stale record/source identity")
        legacy.require(record["source_flight"]["fuel"]["propulsion"]["primary"]["amount"] == 1000
                       and record["destination_flight"]["fuel"]["propulsion"]["primary"]["amount"] == 0,
                       "R2 changed historical fuel vector")
        for copy in copies:
            duplicate = copy["RocketEntityData"]
            legacy.require(copy["id"] == legacy.ROCKET and copy["UUID"] != observed["physical"]
                           and copy["Pos"] == [72.5, 100., 64.5] and not copy.get("Passengers")
                           and duplicate["owner_id"] == observed["owner"]
                           and duplicate["assembly_transaction_id"] == observed["logical"]
                           and duplicate["snapshot"] == record["destination_snapshot"], "R2 changed the historical copy")
            legacy.check_section(duplicate["resource_escrow"], 0, 0, disk["ledger"], "DESTINATION", 1)
            fuel = duplicate["flight_data"]["fuel"]["propulsion"]
            legacy.require([fuel[k]["amount"] for k in ("primary", "oxidizer")] == [0, 0], "R2 copy gained resources")
            if cut == R2_DESTRUCTIVE:
                legacy.require(record["destination_entity_id"] == copy["UUID"], "R2 changed acknowledged destination")
        observed.update(ledger=disk["ledger"]["ledger_id"], journal=records, source_data=data,
                        copies=[dict(physical=c["UUID"], data=c["RocketEntityData"]) for c in copies])
        legacy.require(identity is None or observed == normalize_ids(identity), "R2 changed retained state across restarts")
        return dict(identity=observed, raw_total=5000, available_total=None, loss=None)
    except (KeyError, TypeError, IndexError) as failure:
        raise SmokeError(f"Malformed R2 native state: {failure}") from failure
