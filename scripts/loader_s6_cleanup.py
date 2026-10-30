"""Exact terminal authority, checked removal and pruning prefixes for the loader fixture."""
import copy
import re
import loader_s6_terminal as terminal
from loader_s6_terminal import forward, flight, progress, legacy, SmokeError

CASES = tuple("LF_S6_" + group + "_" + side for group in ("F7", "F8_JOURNAL", "F8_PRUNE") for side in ("BEFORE", "REPLACED", "ACK"))
FIELDS = {"schema_version", "format_epoch", "ledger_id", "issued_through", "writer_generation", "records", "floors",
          "tombstones", "rocket_authorities", "issued_launch_serial", "armed_flights"}


def check(image, restart, identity, cut, witness):
    legacy.require(cut in CASES and restart in (0, 1, 2) and image.get("read_status") == "KNOWN",
                   "UNKNOWN or unsupported cleanup prefix")
    if restart: return terminal.recover(image, restart, identity, witness)
    try:
        disk = forward.normalize(image["native"]); capture = forward.normalize(witness["capture"])
        legacy.require(set(disk["ledger"]) == FIELDS and len(capture["transfers"]) == 1, "Cleanup changed its complete root or captured T")
        row = capture["transfers"][0]; arm = row["native_transfer"]["armed"]
        original = progress.prelaunch(dict(id=legacy.ROCKET, RocketEntityData=dict(schema_version=3)), row, disk["ledger"]["ledger_id"])
        baseline = copy.deepcopy(image); baseline["native"].update(source=original, destination=None, journal=capture)
        baseline["native"]["ledger"].update(writer_generation=4, armed_flights=[arm])
        checked = forward.prepared(baseline, forward.CASES[0], witness)
        expected_root = copy.deepcopy(checked["identity"]["root"])
        terminal_root = copy.deepcopy(expected_root); terminal_root.update(writer_generation=5)
        terminal_root["armed_flights"][0]["outcome"] = "HANDED_OFF"
        pruned = copy.deepcopy(expected_root); pruned.update(writer_generation=6, armed_flights=[])
        if cut in ("LF_S6_F8_PRUNE_REPLACED", "LF_S6_F8_PRUNE_ACK"): expected_root = pruned
        elif cut != "LF_S6_F7_BEFORE": expected_root = terminal_root
        legacy.require(disk["ledger"] == expected_root, "Changed exact cleanup authority/generation")
        if "_JOURNAL_" not in cut:
            staged = forward.normalize(witness["staged_ledger"])
            legacy.require(staged == (terminal_root if cut.startswith("LF_S6_F7_") else pruned), "Changed complete staged/acknowledged cleanup ledger")
        expected_journal = copy.deepcopy(capture)
        if cut.startswith("LF_S6_F7_") or cut == "LF_S6_F8_JOURNAL_BEFORE":
            expected_journal["transfers"][0].update(phase="COMMITTED", destination_entity_id=forward.s6.canonical_uuid(arm["d"]))
        else: expected_journal["transfers"] = []
        legacy.require(disk["journal"] == expected_journal, "Changed cleanup T/no-T boundary")
        landed = copy.deepcopy(image); landed["native"]["ledger"] = pruned
        landed["native"]["journal"]["transfers"] = []
        checked["identity"] = forward.check(landed, 1, checked["identity"], forward.CASES[0], witness["landed"])["identity"]
        return dict(checked, raw_total=4500)
    except (KeyError, TypeError, IndexError, ValueError, OverflowError) as failure:
        raise SmokeError("Malformed cleanup observation: " + str(failure)) from failure


def witness(lines, cut):
    result = terminal.witness(lines, cut)
    if "_JOURNAL_" in cut: return result
    prefix = "ARCE_LOADER_S6_FLIGHT_LEDGER cut=" + cut + " "
    found = [(i, re.search(re.escape(prefix) + r"nbt=([A-Za-z0-9+/=]+)$", line.rstrip("\r\n")))
             for i, line in enumerate(lines) if prefix in line]
    landed = [i for i, line in enumerate(lines) if "ARCE_LOADER_S6_FLIGHT_LANDED cut=" + cut + " " in line]
    pauses = [i for i, line in enumerate(lines) if "ARCE_LOADER_S6_FLIGHT_PAUSED cut=" + cut + " " in line]
    legacy.require(len(found) == len(landed) == len(pauses) == 1 and found[0][1]
                   and landed[0] < found[0][0] < pauses[0], "Missing/misordered complete cleanup ledger witness")
    result["staged_ledger"] = terminal.decode_capture(found[0][1].group(1))
    return result


def hook(cut):
    if "_JOURNAL_" in cut and not cut.endswith("_ACK"): return "JOURNAL_COMMIT"
    if cut.endswith("_ACK"): return cut.removeprefix("LF_S6_")
    return "K8" if cut.endswith("_BEFORE") else "K9"
