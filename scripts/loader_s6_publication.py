"""W220: exact failed publication, returned source, and record-backed held retry."""
import copy
import re
import uuid
import loader_s6_phases as phases
from loader_s6_phases import forward, flight, legacy, SmokeError
from loader_s6_terminal import decode_capture

RETURN = "LF_S6_PUBLICATION_RETURN"
HELD = "LF_S6_PUBLICATION_HELD"
CASES = (RETURN, HELD)


def properties(cut, restart, identity=None):
    """Carry only the independently checked F2 tuple; never seed native authority."""
    legacy.require(cut in CASES and type(restart) is int and restart in (0, 1, 2),
                   "Unsupported publication process round")
    result = ["-Dadvancedrocketrycommunity.escrowS6PublicationRound=" + str(restart)]
    if not restart:
        return result
    try:
        row = identity["transfer"]
        arm = row["native_transfer"]["armed"]
        values = [row["transfer_id"], row["source_entity_id"], arm["d"]]
        legacy.require(row["phase"] == "PREPARED" and arm["t"] == values[0] and arm["src"] == values[1]
                       and identity["initial"]["native"]["physical"] == values[1]
                       and values[1] != values[2]
                       and all(isinstance(value, str) and str(uuid.UUID(value)) == value for value in values),
                       "Publication recovery lost its checked F2 identity")
        return result + ["-Dadvancedrocketrycommunity.escrowS6PublicationIdentity=" + ",".join(values)]
    except (KeyError, TypeError, ValueError, AttributeError) as failure:
        raise SmokeError("Missing or malformed publication F2 identity: " + str(failure)) from failure


def normalize(value):
    result = forward.normalize(value)
    def visit(node):
        if isinstance(node, dict):
            if {"v", "ledger", "src", "serial", "outcome"} <= set(node):
                for key in ("l", "o", "src", "t", "d"):
                    node[key] = forward.s6.canonical_uuid(node[key])
            for child in node.values(): visit(child)
        elif isinstance(node, list):
            for child in node: visit(child)
    visit(result)
    return result


def check(image, restart, identity=None, cut=RETURN, witness=None):
    legacy.require(cut in CASES and restart in (0, 1, 2) and image.get("read_status") == "KNOWN",
                   "UNKNOWN or unsupported publication observation")
    try:
        legacy.require(witness is not None, "Missing publication witness")
        disk = normalize(image["native"])
        if restart == 0:
            capture = normalize(witness["capture"])
            legacy.require(len(capture["transfers"]) == 1, "Missing checked PREPARED capture")
            row = capture["transfers"][0]
            original = phases.progress.prelaunch(flight.projection(disk["source"]), row, disk["ledger"]["ledger_id"])
            baseline = copy.deepcopy(image)
            baseline["native"] = copy.deepcopy(disk)
            baseline["native"].update(source=original, destination=None, journal=capture)
            identity = forward.prepared(baseline, forward.CASES[0], witness)["identity"]
            phase = "DESTINATION_SPAWNED" if cut == RETURN else "PASSENGERS_TRANSFERRED"
            expected_journal = copy.deepcopy(capture)
            expected_journal["transfers"][0].update(phase=phase, destination_entity_id=forward.s6.canonical_uuid(row["native_transfer"]["armed"]["d"]))
            current = expected_journal["transfers"][0]
            legacy.require(disk["journal"] == expected_journal and disk["destination"] is None
                           and witness["phase"] == dict(phase=phase, digest=forward.transfer_digest(current)),
                           "Publication lost its exact phase, fixed identity or destination absence")
            identity.update(publication_journal=expected_journal)
            expected = copy.deepcopy(original)
            if cut == RETURN:
                veto(witness["veto"], current, original)
                returned(expected, witness["returned"], row)
                identity["returned"] = witness["returned"]
            else:
                source_progress(expected, row, witness["publication_source"]["phase_time"])
                legacy.require(forward.survivor_digest(expected) == witness["publication_source"]["digest"],
                               "Changed complete publication source witness")
            legacy.require(flight.projection(disk["source"]) == expected and not disk["source"].get("Passengers"),
                           "Changed complete publication source envelope")
            identity.update(publication_source=expected, publication_native_source=copy.deepcopy(disk["source"]),
                            publication_loaders=copy.deepcopy(disk["loaders"]))
            return held(identity, disk)
        legacy.require(identity is not None, "Publication restart lost its initial identity")
        if cut == HELD and restart == 1:
            legacy.require(disk["ledger"] == identity["root"] and disk["journal"] == identity["publication_journal"]
                           and disk["destination"] is None, "Held retry changed durable authority or published a destination")
            row = disk["journal"]["transfers"][0]
            veto(witness["veto"], row, identity["initial"]["source"])
            legacy.require(flight.projection(disk["source"]) == identity["publication_source"]
                           and disk["source"] == identity["publication_native_source"]
                           and not disk["source"].get("Passengers")
                           and forward.survivor_digest(disk["source"]) == witness["veto"]["source_digest"],
                           "Held retry changed its complete native source")
            return held(identity, disk)
        if cut == HELD:
            # This row's first restart was held, not landed. The existing forward oracle
            # still checks its entire terminal image; no immutable landed image existed yet.
            result = forward.check(dict(image, native=disk), 1, identity, forward.CASES[0], witness)
            return dict(result, held_total=0)
        expected_root = copy.deepcopy(identity["root"])
        expected_root.update(writer_generation=6, armed_flights=[])
        legacy.require(disk["ledger"] == expected_root and disk["journal"] == identity["empty_journal"]
                       and disk["destination"] is None and disk["source"] is not None,
                       "Returned completion changed terminal authority or destination absence")
        loader(disk, identity)
        expected = identity["publication_source"]
        legacy.require(flight.projection(disk["source"]) == expected and not disk["source"].get("Passengers")
                       and witness == identity["returned"] and forward.survivor_digest(disk["source"]) == witness["digest"],
                       "Returned completion changed the full survivor or repeated a debit")
        return dict(identity=identity, raw_total=5000, available_total=5000, held_total=0, authorized_debit=0, loss=0)
    except (KeyError, TypeError, IndexError, ValueError, OverflowError) as failure:
        raise SmokeError("Malformed publication observation: " + str(failure)) from failure


def loader(disk, identity):
    legacy.require(len(disk["loaders"]) == 1 and [disk["loaders"][0][k] for k in ("x", "y", "z")] == [66, 100, 64]
                   and disk["loaders"][0]["arce_fluid_fuel_loader"] == identity["initial"]["loader"],
                   "Publication changed its complete loader binding, resources or epoch")


def held(identity, disk):
    loader(disk, identity)
    legacy.require(disk["loaders"] == identity["publication_loaders"], "Held retry changed its native loader")
    row = disk["journal"]["transfers"][0]
    legacy.require(disk["ledger"] == identity["root"] and disk["ledger"]["armed_flights"] == [row["native_transfer"]["armed"]],
                   "Held source has no exact ARMED record")
    amount = row["source_flight"]["fuel"]["amount"]
    legacy.require(amount == 1000 and disk["source"]["RocketEntityData"]["flight_data"]["fuel"] == row["source_flight"]["fuel"],
                   "Held source resources differ from the complete typed record")
    return dict(identity=identity, raw_total=4000 + amount, available_total=4000, held_total=amount,
                authorized_debit=500, loss=None)


def source_progress(source, row, time):
    legacy.require(type(time) is int and time >= row["source_flight"]["state_started_game_time"], "Invalid source clock")
    state = copy.deepcopy(row["source_flight"]); state["state_started_game_time"] = time
    source["RocketEntityData"]["flight_data"] = state; source["Pos"][1] = 148.


def veto(observed, row, original):
    legacy.require(set(observed) == {"phase", "transfer", "destination", "source_time", "source_digest", "transfer_digest", "count"}
                   and observed["phase"] == row["phase"] and observed["transfer"] == row["transfer_id"]
                   and observed["destination"] == row["destination_entity_id"] and observed["count"] == 1
                   and observed["transfer_digest"] == forward.transfer_digest(row), "Wrong or missing failed publication witness")
    source = copy.deepcopy(original); source_progress(source, row, observed["source_time"])
    legacy.require(forward.survivor_digest(source) == observed["source_digest"], "Veto changed the complete source envelope")


def returned(source, observed, row):
    legacy.require(set(observed) == {"time", "digest"} and type(observed["time"]) is int
                   and observed["time"] >= row["source_flight"]["state_started_game_time"], "Invalid return clock")
    source["RocketEntityData"]["flight_data"].update(state="FUELED", state_started_game_time=observed["time"])
    legacy.require(forward.survivor_digest(source) == observed["digest"], "Changed complete returned envelope")


def witness(lines, restart, cut):
    legacy.require(cut in CASES and restart in (0, 1, 2), "Unsupported publication witness round")
    expects_veto = cut == RETURN and restart == 0 or cut == HELD and restart == 1
    legacy.require(sum("ARCE_LOADER_S6_PUBLICATION_VETO cut=" + cut + " " in line for line in lines) == int(expects_veto),
                   "Unexpected or missing publication veto count")
    def exact(label, expression):
        prefix = "ARCE_LOADER_S6_" + label + " cut=" + cut + " "
        found = [(i, re.search(re.escape(prefix) + expression + "$", line.rstrip("\r\n")))
                 for i, line in enumerate(lines) if prefix in line]
        legacy.require(len(found) == 1 and found[0][1], "Missing/duplicate/malformed publication witness: " + label)
        return found[0]
    paused = restart == 0 or (cut == HELD and restart == 1)
    stop_at, stop = exact("FLIGHT_PAUSED" if paused else "PUBLICATION_RESULT",
                          "hook=" + hook(cut, restart) if paused else "outcome=" + ("CANCELLED" if cut == RETURN else "LANDED"))
    result = {}
    if restart == 0:
        # Reuse all prelaunch/ARMED/PREPARED framing checks with the existing exact selector.
        mapped = [line.replace(cut, flight.F2[1]) for line in lines]
        result.update(flight.witness(mapped, flight.F2[1]))
        capture_at, capture = exact("FLIGHT_CAPTURE", r"nbt=([A-Za-z0-9+/=]+)")
        phase_at, phase = exact("FLIGHT_PHASE", r"phase=(DESTINATION_SPAWNED|PASSENGERS_TRANSFERRED) digest=([0-9a-f]{64})")
        legacy.require(capture_at < phase_at < stop_at, "Misordered capture/phase/stop")
        result.update(capture=decode_capture(capture.group(1)), phase=dict(phase=phase.group(1), digest=phase.group(2)))
        if cut == HELD:
            source_at, source = exact("FLIGHT_PUBLICATION_SOURCE", r"phase_time=(\d+) digest=([0-9a-f]{64})")
            legacy.require(capture_at < source_at < phase_at, "Misordered publication source")
            result["publication_source"] = dict(phase_time=int(source.group(1)), digest=source.group(2))
    if (cut == RETURN and restart == 0) or (cut == HELD and restart == 1):
        veto_at, match = exact("PUBLICATION_VETO", r"phase=(DESTINATION_SPAWNED|PASSENGERS_TRANSFERRED) transfer=(\S+) destination=(\S+) source_time=(\d+) source_digest=([0-9a-f]{64}) transfer_digest=([0-9a-f]{64}) count=1")
        legacy.require(veto_at < stop_at and (restart != 0 or capture_at < veto_at < phase_at), "Misordered spawn veto")
        result["veto"] = dict(zip(("phase", "transfer", "destination", "source_time", "source_digest", "transfer_digest"), match.groups()))
        result["veto"].update(source_time=int(match.group(4)), count=1)
        if cut == HELD:
            held_at, _ = exact("PUBLICATION_HELD", r"source_locked=true destination_absent=true return=false")
            legacy.require(veto_at < held_at < stop_at, "Missing post-veto held retry observation")
    if cut == RETURN:
        returned_at, value = exact("PUBLICATION_RETURNED", r"time=(\d+) digest=([0-9a-f]{64})")
        legacy.require(returned_at < stop_at and (restart != 0 or veto_at < returned_at < phase_at), "Misordered returned survivor")
        observed = dict(time=int(value.group(1)), digest=value.group(2))
        if restart: return observed
        result["returned"] = observed
    elif restart == 2:
        result = forward.recovery_witness(lines, cut)
        survivor_at, _ = exact("FLIGHT_SURVIVOR", r"landed_time=(\d+) digest=([0-9a-f]{64})")
        legacy.require(survivor_at < stop_at, "Completed result preceded its survivor")
    return result


def hook(cut, restart):
    return "RETURN_F6_READBACK_1" if cut == RETURN else "F5_PASSENGERS_TRANSFERRED_ACK" if restart == 0 else "PUBLICATION_HELD"


def check_logs(lines, restart, cut):
    legacy.require(not any("/ERROR]" in line or "/FATAL]" in line for line in lines), "Unexpected publication ERROR/FATAL")
    return witness(lines, restart, cut)
