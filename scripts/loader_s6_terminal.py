"""Ordered F6 raw prefixes; absent stores are never inferred as zero loss."""
import base64
import copy
import re
import loader_s6_phases as phases
from loader_s6_phases import forward, flight, progress, legacy, SmokeError

CASES = tuple("LF_S6_F6_PROOF_" + side + "_" + str(index) for index in (0, 1) for side in ("BEFORE", "AFTER"))


def check(image, restart, identity, cut, witness):
    legacy.require(cut in CASES and restart in (0, 1, 2) and image.get("read_status") == "KNOWN",
                   "UNKNOWN or unsupported F6 prefix")
    if restart: return recover(image, restart, identity, witness)
    try:
        disk = forward.normalize(image["native"]); capture = forward.normalize(witness["capture"])
        legacy.require(len(capture["transfers"]) == 1, "Missing complete PREPARED capture")
        row = capture["transfers"][0]
        # This private reconstruction validates the already-hashed F1 capture; it is not observed inventory.
        original = progress.prelaunch(dict(id=legacy.ROCKET, RocketEntityData=dict(schema_version=3)), row, disk["ledger"]["ledger_id"])
        baseline = copy.deepcopy(image); baseline["native"].update(source=original, destination=None, journal=capture)
        checked = forward.prepared(baseline, forward.CASES[0], witness)
        committed = copy.deepcopy(capture)
        committed["transfers"][0].update(phase="COMMITTED", destination_entity_id=forward.s6.canonical_uuid(row["native_transfer"]["armed"]["d"]))
        legacy.require(disk["journal"] == committed, "F6 lost its exact COMMITTED authority")
        if cut.endswith("BEFORE_0"):
            legacy.require(isinstance(disk["source"], dict), "F6 source disappeared before its first proof")
            phase_witness = dict(witness, phase=dict(phase="COMMITTED", digest=forward.transfer_digest(committed["transfers"][0])))
            phases.check(image, 0, None, "LF_S6_F5_COMMITTED_ACK", phase_witness)
        else:
            legacy.require(disk["source"] is None, "F6 source absence proof is not reflected on disk")
        if cut.endswith("AFTER_1"):
            # Reuse every complete landed-state assertion; only the private ledger/journal are normalized to F8.
            landed = copy.deepcopy(image)
            landed["native"]["ledger"].update(writer_generation=6, armed_flights=[])
            landed["native"]["journal"]["transfers"] = []
            checked["identity"] = forward.check(landed, 1, checked["identity"], forward.CASES[0], witness["landed"])["identity"]
        else: legacy.require(disk["destination"] is None, "F6 acquired a premature native survivor")
        return dict(checked, raw_total=5000 if cut.endswith("BEFORE_0") else 4500 if cut.endswith("AFTER_1") else 4000)
    except (KeyError, TypeError, IndexError, ValueError, OverflowError) as failure:
        raise SmokeError("Malformed F6 observation: " + str(failure)) from failure


def recover(image, restart, identity, witness):
    result = forward.check(image, restart, identity, forward.CASES[0], witness)
    if identity is not None and "survivor" in identity:
        legacy.require(result["identity"] == identity, "Already-landed prefix changed its complete survivor on restart")
    return result


def decode_capture(encoded):
    legacy.require(isinstance(encoded, str) and len(encoded) <= 87_384, "Oversized or absent checked capture")
    try:
        raw = base64.b64decode(encoded, validate=True)
        legacy.require(len(raw) <= 65_536, "Oversized checked capture")
        return forward.normalize(flight.nbt.plain(flight.nbt.read(raw)[1]))
    except (ValueError, TypeError) as failure: raise SmokeError("Invalid checked capture: " + str(failure)) from failure


def witness(lines, cut):
    def exact(label, expression):
        prefix = "ARCE_LOADER_S6_FLIGHT_" + label + " cut=" + cut + " "
        found = [(i, re.search(re.escape(prefix) + expression + "$", line.rstrip("\r\n")))
                 for i, line in enumerate(lines) if prefix in line]
        legacy.require(len(found) == 1 and found[0][1], "Missing/duplicate/malformed F6 witness: " + label)
        return found[0]
    capture_at, capture = exact("CAPTURE", r"nbt=([A-Za-z0-9+/=]+)")
    source_at, source = exact("PUBLICATION_SOURCE", r"phase_time=(\d+) digest=([0-9a-f]{64})")
    landed_at, landed = exact("LANDED", r"landed_time=(\d+) digest=([0-9a-f]{64})")
    transfers = [i for i, line in enumerate(lines) if "ARCE_LOADER_S6_FLIGHT_TRANSFER cut=" + cut + " " in line]
    pauses = [i for i, line in enumerate(lines) if "ARCE_LOADER_S6_FLIGHT_PAUSED cut=" + cut + " " in line]
    legacy.require(len(transfers) == len(pauses) == 1 and transfers[0] < capture_at < source_at < landed_at < pauses[0],
                   "Misordered F6 checked capture, publication, landing or proof")
    return dict(capture=decode_capture(capture.group(1)), publication_source=dict(phase_time=int(source.group(1)), digest=source.group(2)),
                landed=dict(landed_time=int(landed.group(1)), survivor_digest=landed.group(2)))


def hook(cut):
    return ("BARRIER_STORE_BEFORE_" if "_BEFORE_" in cut else "BARRIER_READBACK_AFTER_") + cut[-1]
