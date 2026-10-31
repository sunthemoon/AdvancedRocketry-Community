"""Full checked F4/F5 prefixes; fixture-owned destination storage is not product timing."""
import copy
import re
import loader_s6_progress as progress
from loader_s6_progress import forward, flight, legacy, SmokeError

HOOKS = {"LF_S6_F4_ACK": "F4_DESTINATION_ACK",
    "LF_S6_F4_PUBLICATION_BEFORE": "F4_PUBLICATION_BEFORE",
    "LF_S6_F4_PUBLICATION_AFTER": "F4_PUBLICATION_AFTER",
    "LF_S6_F4_STORED": "FIXTURE_PHASE_READBACK",
    "LF_S6_F5_PASSENGERS_ACK": "F5_PASSENGERS_TRANSFERRED_ACK",
    "LF_S6_F5_SOURCE_ACK": "F5_SOURCE_REMOVED_ACK",
    "LF_S6_F5_SOURCE_REMOVAL_BEFORE": "F5_SOURCE_REMOVAL_BEFORE",
    "LF_S6_F5_SOURCE_REMOVAL_AFTER": "F5_SOURCE_REMOVAL_AFTER",
    "LF_S6_F5_COMMITTED_ACK": "F5_COMMITTED_ACK"}
CASES = tuple(HOOKS)
PHASES = {cut: "DESTINATION_SPAWNED" if cut.startswith("LF_S6_F4_") else
    "PASSENGERS_TRANSFERRED" if cut.endswith("PASSENGERS_ACK") else
    "COMMITTED" if cut.endswith("COMMITTED_ACK") else "SOURCE_REMOVED" for cut in CASES}


def check(image, restart, identity, cut, witness):
    legacy.require(cut in CASES and restart in (0, 1, 2) and image.get("read_status") == "KNOWN",
                   "UNKNOWN or unsupported checked phase prefix")
    if restart: return forward.check(image, restart, identity, forward.CASES[0], witness)
    try:
        disk = forward.normalize(image["native"])
        legacy.require(witness is not None and len(disk["journal"]["transfers"]) == 1, "Phase prefix lost its T witness")
        row = disk["journal"]["transfers"][0]
        allowed = ("DESTINATION_SPAWNED", "PASSENGERS_TRANSFERRED", "SOURCE_REMOVED", "COMMITTED") if cut.endswith("STORED") else (PHASES[cut],)
        legacy.require(row["phase"] in allowed and witness["phase"] == dict(phase=row["phase"], digest=forward.transfer_digest(row))
                       and row["destination_entity_id"] == forward.s6.canonical_uuid(row["native_transfer"]["armed"]["d"]),
                       "Changed phase or fixed destination identity")
        original = progress.prelaunch(flight.projection(disk["source"]), row, disk["ledger"]["ledger_id"])
        prepared = copy.deepcopy(row); prepared.update(phase="PREPARED"); prepared.pop("destination_entity_id")
        baseline = copy.deepcopy(image)
        baseline["native"].update(source=original, destination=None)
        baseline["native"]["journal"]["transfers"] = [prepared]
        checked = forward.prepared(baseline, forward.CASES[0], witness)
        expected = copy.deepcopy(original)
        if cut != "LF_S6_F4_ACK":
            observed = witness["publication_source"]
            legacy.require(set(observed) == {"phase_time", "digest"} and isinstance(observed["phase_time"], int)
                           and observed["phase_time"] >= row["source_flight"]["state_started_game_time"], "Invalid publication source clock")
            state = copy.deepcopy(row["source_flight"]); state["state_started_game_time"] = observed["phase_time"]
            expected["RocketEntityData"]["flight_data"] = state; expected["Pos"][1] = 148.
            legacy.require(forward.survivor_digest(disk["source"]) == observed["digest"], "Changed complete publication source witness")
        legacy.require(flight.projection(disk["source"]) == expected and not disk["source"].get("Passengers"), "Changed phase source envelope")
        if not cut.endswith("STORED"):
            legacy.require(disk["destination"] is None, "Unstored phase acquired a native destination")
            return checked
        destination = copy.deepcopy(original)
        destination.update(UUID=row["destination_entity_id"], Pos=[80.5, 148., 64.5])
        destination["RocketEntityData"].update(snapshot=row["destination_snapshot"], flight_data=row["destination_flight"],
            resource_escrow=dict(schema_version=1, ledger_id=disk["ledger"]["ledger_id"], resource_epoch=0, markers=[]))
        legacy.require(flight.projection(disk["destination"]) == destination and not disk["destination"].get("Passengers")
                       and witness["stored"] == dict(owner="FIXTURE", phase="DESCENT",
                           phase_time=row["destination_flight"]["state_started_game_time"], digest=forward.survivor_digest(disk["destination"])),
                       "Changed complete fixture-owned destination read-back")
        return dict(checked, raw_total=5500, store_owner="FIXTURE", stored_phase="DESCENT")
    except (KeyError, TypeError, IndexError, ValueError, OverflowError) as failure:
        raise SmokeError("Malformed checked phase observation: " + str(failure)) from failure


def witness(lines, cut):
    def exact(label, expression):
        prefix = "ARCE_LOADER_S6_FLIGHT_" + label + " cut=" + cut + " "
        found = [(i, re.search(re.escape(prefix) + expression + "$", line.rstrip("\r\n")))
                 for i, line in enumerate(lines) if prefix in line]
        transfers = [i for i, line in enumerate(lines) if "ARCE_LOADER_S6_FLIGHT_TRANSFER cut=" + cut + " " in line]
        pauses = [i for i, line in enumerate(lines) if "ARCE_LOADER_S6_FLIGHT_PAUSED cut=" + cut + " " in line]
        legacy.require(len(found) == len(transfers) == len(pauses) == 1 and found[0][1]
                       and transfers[0] < found[0][0] < pauses[0], "Missing/misordered phase witness: " + label)
        return found[0]
    phase_index, phase = exact("PHASE", r"phase=(DESTINATION_SPAWNED|PASSENGERS_TRANSFERRED|SOURCE_REMOVED|COMMITTED) digest=([0-9a-f]{64})")
    result = dict(phase=dict(phase=phase.group(1), digest=phase.group(2)))
    if cut != "LF_S6_F4_ACK":
        source_index, source = exact("PUBLICATION_SOURCE", r"phase_time=(\d+) digest=([0-9a-f]{64})")
        legacy.require(source_index < phase_index, "Publication source witness arrived after its selected phase")
        result["publication_source"] = dict(phase_time=int(source.group(1)), digest=source.group(2))
    if cut.endswith("STORED"): result["stored"] = progress.stored_witness(lines, cut)
    return result
