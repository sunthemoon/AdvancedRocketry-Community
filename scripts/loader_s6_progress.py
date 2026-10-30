"""R202 persisted-phase evidence; fixture saves are not production timing claims."""
import copy
import re
import loader_s6_forward as forward
from loader_s6_forward import flight, legacy, SmokeError

CASES = ("LF_S6_F3_ASCENT", "LF_S6_F3_TRANSIT")

def check(image, restart, identity, cut, witness):
    legacy.require(cut in CASES and restart in (0, 1, 2) and image.get("read_status") == "KNOWN",
                   "UNKNOWN or unsupported persisted-phase observation")
    if restart:
        return forward.check(image, restart, identity, forward.CASES[0], witness)
    try:
        disk = forward.normalize(image["native"])
        legacy.require(witness is not None and len(disk["journal"]["transfers"]) == 1
                       and disk["destination"] is None, "Stored source lost its exact authority")
        row = disk["journal"]["transfers"][0]
        actual = flight.projection(disk["source"])
        original = prelaunch(actual, row, disk["ledger"]["ledger_id"])
        baseline = copy.deepcopy(image); baseline["native"]["source"] = original
        checked = forward.prepared(baseline, forward.CASES[0], witness)
        expected = copy.deepcopy(original)
        phase = cut.removeprefix("LF_S6_F3_")
        observed = witness["stored"]
        legacy.require(set(observed) == {"owner", "phase", "phase_time", "digest"}
                       and observed["owner"] == "FIXTURE" and observed["phase"] == phase
                       and isinstance(observed["phase_time"], int)
                       and observed["phase_time"] >= row["created_at_game_time"] + (60 if phase == "ASCENT" else 140),
                       "Stored phase witness changed its ownership, phase or clock")
        state = copy.deepcopy(row["source_flight"])
        state.update(state=phase, state_started_game_time=observed["phase_time"])
        expected["RocketEntityData"]["flight_data"] = state
        height = actual["Pos"][1]
        legacy.require(112 <= height <= 148 if phase == "ASCENT" else height == 148,
                       "Stored source left its selected phase column")
        expected["Pos"][1] = height
        legacy.require(actual == expected and not disk["source"].get("Passengers")
                       and forward.survivor_digest(disk["source"]) == observed["digest"],
                       "Changed complete stored source or read-back witness")
        return dict(checked, store_owner="FIXTURE", stored_phase=phase)
    except (KeyError, TypeError, IndexError, ValueError, OverflowError) as failure:
        raise SmokeError("Malformed persisted-phase observation: " + str(failure)) from failure


def prelaunch(actual, row, ledger):
    """Reconstruct T's full captured E for validation, never as observed inventory."""
    result = copy.deepcopy(actual); pose = row["native_transfer"]["source"]
    state = copy.deepcopy(row["source_flight"])
    state.update(pose["flight_changes"])
    for key in pose["flight_absent"]: state.pop(key)
    result.update(UUID=row["source_entity_id"], Pos=pose["pos"], Motion=pose["motion"],
        Rotation=[pose["yaw"], pose["pitch"]], NoGravity=pose["no_gravity"],
        OnGround=pose["on_ground"], Invulnerable=pose["invulnerable"])
    result["RocketEntityData"].update(assembly_transaction_id=row["logical_rocket_id"], owner_id=row["owner_id"],
        snapshot=row["source_snapshot"], flight_data=state,
        resource_escrow=dict(schema_version=1, ledger_id=ledger, resource_epoch=1, markers=[]))
    return result


def stored_witness(lines, cut):
    prefix = "ARCE_LOADER_S6_FLIGHT_STORED cut=" + cut + " "
    found = [(i, re.search(re.escape(prefix) + r"owner=FIXTURE phase=(ASCENT|TRANSIT) phase_time=(\d+) digest=([0-9a-f]{64})$",
                           line.rstrip("\r\n"))) for i, line in enumerate(lines) if prefix in line]
    transfers = [i for i, line in enumerate(lines) if "ARCE_LOADER_S6_FLIGHT_TRANSFER cut=" + cut + " " in line]
    pauses = [i for i, line in enumerate(lines) if "ARCE_LOADER_S6_FLIGHT_PAUSED cut=" + cut + " " in line]
    legacy.require(len(found) == len(transfers) == len(pauses) == 1 and found[0][1]
                   and transfers[0] < found[0][0] < pauses[0], "Missing/misordered fixture-owned read-back witness")
    match = found[0][1]
    return dict(owner="FIXTURE", phase=match.group(1), phase_time=int(match.group(2)), digest=match.group(3))
