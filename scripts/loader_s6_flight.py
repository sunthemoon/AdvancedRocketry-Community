"""Strict S6 loader-funded flight observations; no legacy oracle edits."""
import copy
import re
import struct

import loader_cut_nbt as nbt
import loader_cut_oracle as legacy
import loader_s6_integrity as integrity
import loader_s6_oracle as s6
from run_dedicated_server_smoke import SmokeError
from run_v180_escrow_forced_stop import LEDGER, digest, ledger, record

F1 = tuple("LF_S6_F1_" + side for side in (
    "STORE_BEFORE", "STORE_AFTER", "FORCE_BEFORE", "FORCE_AFTER", "READBACK_BEFORE", "READBACK_AFTER",
    "ARM_BEFORE", "ARM_REPLACED", "ARM_ACK"))
F2 = ("LF_S6_F2_BEFORE", "LF_S6_F2_REPLACED", "LF_S6_F2_ACK")
F3 = ("LF_S6_F3_ASCENT", "LF_S6_F3_TRANSIT")
F45 = tuple("LF_S6_" + side for side in ("F4_ACK", "F4_PUBLICATION_BEFORE", "F4_PUBLICATION_AFTER", "F4_STORED",
    "F5_PASSENGERS_ACK", "F5_SOURCE_ACK", "F5_SOURCE_REMOVAL_BEFORE", "F5_SOURCE_REMOVAL_AFTER", "F5_COMMITTED_ACK"))
F6 = tuple("LF_S6_F6_PROOF_" + side + "_" + str(index) for index in (0, 1) for side in ("BEFORE", "AFTER"))
F78 = tuple("LF_S6_" + group + "_" + side for group in ("F7", "F8_JOURNAL", "F8_PRUNE") for side in ("BEFORE", "REPLACED", "ACK"))
CASES = F1 + F2 + F3 + F45 + F6 + F78


def armed_digest(arm):
    """Codec-defined full Armed NBT; the witness is captured from the checked staged file."""
    return integrity.digest(armed_tag(arm))


def armed_tag(arm):
    expected = {"v", "ledger", "l", "o", "src", "epoch", "t", "serial", "g", "binding", "launch", "source",
                "dest", "d", "j", "release", "outcome"}
    legacy.require(set(arm) == expected and set(arm["j"]) == {"rocket_id", "journal_generation", "journal_digest"},
                   "Changed complete ordinary ARMED shape")
    fields = {}
    for key, value in arm.items():
        if key in ("ledger", "l", "o", "src", "t", "d"):
            fields[key] = integrity.uid(s6.canonical_uuid(value))
        elif key in ("binding", "launch", "source", "dest"):
            legacy.require(len(value) == 32, "Invalid ARMED digest")
            fields[key] = nbt.Tag(nbt.BYTES, bytes(value))
        elif key == "j":
            legacy.require(len(value["journal_digest"]) == 32, "Invalid ARMED journal digest")
            fields[key] = integrity.compound(dict(rocket_id=integrity.uid(s6.canonical_uuid(value["rocket_id"])),
                journal_generation=integrity.scalar(value["journal_generation"], nbt.LONG),
                journal_digest=nbt.Tag(nbt.BYTES, bytes(value["journal_digest"]))))
        else:
            fields[key] = integrity.scalar(value, nbt.STRING if key == "outcome" else nbt.INT if key == "v" else nbt.LONG)
    return integrity.compound(fields)


def check(image, restart, identity=None, cut=F1[0], witness=None):
    if cut in F78:
        import loader_s6_cleanup
        return loader_s6_cleanup.check(image, restart, identity, cut, witness)
    if cut in F6:
        import loader_s6_terminal
        return loader_s6_terminal.check(image, restart, identity, cut, witness)
    if cut in F45:
        import loader_s6_phases
        return loader_s6_phases.check(image, restart, identity, cut, witness)
    if cut in F3:
        import loader_s6_progress
        return loader_s6_progress.check(image, restart, identity, cut, witness)
    if cut in F2[1:]:
        import loader_s6_forward
        return loader_s6_forward.check(image, restart, identity, cut, witness)
    legacy.require(cut in F1 + F2[:1] and restart in (0, 1, 2), "Unimplemented flight selection")
    legacy.require(image.get("read_status") == "KNOWN" and isinstance(image.get("native"), dict),
                   "UNKNOWN flight read is not zero loss")
    try:
        disk = s6.normalize_ids(image["native"]); root = disk["ledger"]
        source = disk["source"]; data = source["RocketEntityData"]
        pair = dict(loaders=disk["loaders"], rocket=source)
        native = integrity.pair_identity(pair, root)
        device = disk["loaders"][0]; loader = device["arce_fluid_fuel_loader"]
        legacy.require(disk["destination"] is None and disk["journal"] == dict(schema_version=3, transfers=[],
                       classic_first_moon_landing=dict(schema_version=1, state="EMPTY"),
                       format_epoch="v1.8.0-first-moon-landing"), "Pre-F2 acquired a destination or T")
        armed_cut = cut in F1[-2:] + F2[:1]
        legacy.require(root["schema_version"] == 2 and root["format_epoch"] == "v1.8.0-resource-escrow" and root["issued_through"] == 1
                       and not any(root[key] for key in ("records", "floors", "tombstones", "rocket_authorities"))
                       and root["issued_launch_serial"] == int(armed_cut)
                       and root["writer_generation"] == (6 if restart and armed_cut else 4 if armed_cut else 3)
                       and len(root["armed_flights"]) == int(armed_cut and not restart), "Wrong F1 durable authority/generation")
        legacy.require(loader["tank"] == dict(FluidName=legacy.FUEL, Amount=4000)
                       and [device[k] for k in ("x", "y", "z")] == [66, 100, 64]
                       and data["flight_data"]["fuel"]["amount"] == 1000
                       and data["flight_data"]["state"] == "ASSEMBLED"
                       and not any(k in data["flight_data"] for k in ("plan", "active_transfer_id"))
                       and data["flight_data"]["passengers"] == dict(assignments=[], seat_capacity=1)
                       and source["Pos"] == [64.5, 100., 64.5] and source["Motion"] == [0., 0., 0.]
                       and not source.get("Passengers"), "Changed F1 resource or source state")
        operation = witness["operation"] if witness else identity.get("operation") if identity else None
        for wrapper, role in ((loader["resource_escrow"], "SOURCE"), (data["resource_escrow"], "DESTINATION")):
            clean = dict(schema_version=1, ledger_id=root["ledger_id"], resource_epoch=1, markers=[])
            marked = dict(clean, markers=[dict(op_id=operation, seq=1, role=role, epoch_after=1)])
            legacy.require(wrapper == clean or not restart and operation is not None and wrapper == marked,
                           "Changed F1 wrapper/retired marker or unpruned recovery")
        clean_source = projection(source)
        clean_source["RocketEntityData"]["resource_escrow"]["markers"] = []
        clean_loader = copy.deepcopy(loader); clean_loader["resource_escrow"]["markers"] = []
        armed_witness = witness.get("armed_digest") if witness else identity.get("armed_digest") if identity else None
        if not restart and cut in F1[-3:] + F2[:1]:
            legacy.require(isinstance(armed_witness, str) and re.fullmatch("[0-9a-f]{64}", armed_witness),
                           "Missing checked staged ARMED witness")
        observed = dict(native=native, ledger=root["ledger_id"], source=clean_source, loader=clean_loader,
                        operation=operation, armed_digest=armed_witness)
        if witness:
            legacy.require(witness["source"] == source["UUID"] and witness["logical"] == native["logical"]
                           and witness["owner"] == native["owner"] and witness["ledger"] == root["ledger_id"]
                           and witness["source_digest"] == source_digest(clean_source), "F1 raw image differs from its prelaunch witness")
        if root["armed_flights"]:
            arm = root["armed_flights"][0]
            legacy.require(arm["v"] == 1 and arm["ledger"] == root["ledger_id"] and s6.canonical_uuid(arm["l"]) == native["logical"]
                           and s6.canonical_uuid(arm["o"]) == native["owner"] and s6.canonical_uuid(arm["src"]) == native["physical"]
                           and arm["epoch"] == 1 and arm["serial"] == 1 and arm["g"] == 4 and arm["outcome"] == "ARMED"
                           and arm["release"] == 0 and "a" not in arm and bytes(arm["source"]).hex() == source_digest(clean_source),
                           "F1 ARMED disagrees with the complete native source")
            legacy.require(s6.canonical_uuid(arm["d"]) != native["physical"]
                           and s6.canonical_uuid(arm["j"]["rocket_id"]) == native["logical"]
                           and arm["j"]["journal_generation"] > 0 and armed_digest(arm) == armed_witness,
                           "F1 changed the complete checked staged ARMED tuple")
        if identity is not None:
            legacy.require(observed == identity, "Changed complete F1 restart identity")
        return dict(identity=observed, raw_total=5000, available_total=5000 if restart else None, loss=0 if restart else None)
    except (KeyError, TypeError, IndexError, ValueError) as failure:
        raise SmokeError("Malformed flight observation: " + str(failure)) from failure


def projection(rocket):
    return copy.deepcopy({key: rocket.get(key) for key in ("UUID", "id", "RocketEntityData", "Pos", "Motion",
                                                         "Rotation", "NoGravity", "OnGround", "Invulnerable")})


def source_digest(rocket):
    data = rocket["RocketEntityData"]; flight = data["flight_data"]; snapshot = data["snapshot"]
    frame = dict(schema_version=1, physical=rocket["UUID"], type=legacy.ROCKET, logical=data["assembly_transaction_id"],
        owner=data["owner_id"], level=flight["current_dimension"], chunk=[4, 4], pos=rocket["Pos"], motion=rocket["Motion"],
        no_gravity=rocket["NoGravity"], no_physics=1, on_ground=rocket["OnGround"], invulnerable=rocket["Invulnerable"],
        yaw=rocket["Rotation"][0], pitch=rocket["Rotation"][1], passengers=[], assembly_snapshot=snapshot["snapshot_id"],
        content_hash=snapshot["content_hash"], snapshot=snapshot, flight=flight)
    tag = integrity.native_tag(frame)
    wrapper = data["resource_escrow"]
    tag.value["wrapper"] = integrity.compound(dict(schema_version=integrity.scalar(1, nbt.INT),
        ledger_id=integrity.uid(wrapper["ledger_id"]), resource_epoch=integrity.scalar(1, nbt.LONG),
        markers=nbt.Tag(nbt.LIST, (0, []))))
    return integrity.digest(integrity.compound(tag.value))


def inspect(world):
    try:
        path = world / "entities/r.0.0.mca"
        endpoints = []
        for slot in (132, 133):
            with path.open("rb") as stream:
                stream.seek(slot * 4); header = stream.read(4)
            legacy.require(len(header) == 4, "Truncated native entity header")
            entities = [] if struct.unpack(">I", header)[0] == 0 else record(path, slot)["Entities"]
            rockets = [e for e in entities if "RocketEntityData" in e or e.get("id") == legacy.ROCKET]
            legacy.require(len(rockets) <= 1, "S6 flight duplicate native endpoint")
            endpoints.append(rockets[0] if rockets else None)
        terrain = record(world / "region/r.0.0.mca", 132)
        devices = [e for e in terrain["block_entities"] if e.get("id") == legacy.LOADER]
        return dict(read_status="KNOWN", native=dict(ledger=ledger(world / "data" / LEDGER),
            journal=legacy.journal(world), loaders=devices, source=endpoints[0], destination=endpoints[1],
            files={str(p.relative_to(world)): digest(p) for p in (path, world / "region/r.0.0.mca", world / "data" / LEDGER)}))
    except (OSError, ValueError, KeyError, TypeError, SmokeError) as failure:
        return dict(read_status="UNKNOWN", native=None, error=str(failure))


def witness(lines, cut):
    matches = [re.search(r"ARCE_LOADER_S6_FLIGHT_FILLED cut=" + re.escape(cut)
        + r" operation=(\S+) source=(\S+) logical=(\S+) owner=(\S+) ledger=(\S+) source_digest=([0-9a-f]{64}) tank=4000 bank=1000 epoch=1$",
        line.rstrip("\r\n")) for line in lines]
    matches = [m for m in matches if m]
    legacy.require(len(matches) == 1, "Missing/duplicate prelaunch flight witness")
    result = dict(zip(("operation", "source", "logical", "owner", "ledger", "source_digest"), matches[0].groups()))
    if cut in F1[-3:] + F2 + F3 + F45 + F6 + F78:
        prefix = "ARCE_LOADER_S6_FLIGHT_ARMED cut=" + cut + " "
        staged = [(i, re.search(re.escape(prefix) + r"digest=([0-9a-f]{64})$", line.rstrip("\r\n")))
                  for i, line in enumerate(lines) if prefix in line]
        baselines = [i for i, line in enumerate(lines) if "ARCE_LOADER_S6_FLIGHT_FILLED cut=" + cut + " " in line]
        pauses = [i for i, line in enumerate(lines) if "ARCE_LOADER_S6_FLIGHT_PAUSED cut=" + cut + " " in line]
        legacy.require(len(staged) == len(baselines) == len(pauses) == 1 and staged[0][1]
                       and baselines[0] < staged[0][0] < pauses[0], "Missing/misordered checked staged ARMED witness")
        result["armed_digest"] = staged[0][1].group(1)
    if cut in F2[1:] + F3 + F45 + F6 + F78:
        import loader_s6_forward
        result["transfer_digest"] = loader_s6_forward.transfer_witness(lines, cut)
    if cut in F3:
        import loader_s6_progress
        result["stored"] = loader_s6_progress.stored_witness(lines, cut)
    if cut in F45:
        import loader_s6_phases
        result.update(loader_s6_phases.witness(lines, cut))
    if cut in F6:
        import loader_s6_terminal
        result.update(loader_s6_terminal.witness(lines, cut))
    if cut in F78:
        import loader_s6_cleanup
        result.update(loader_s6_cleanup.witness(lines, cut))
    return result


def check_logs(lines, restart, cut):
    legacy.require(not any("/ERROR]" in line or "/FATAL]" in line for line in lines), "Unexpected flight ERROR/FATAL")
    prefix = "ARCE_LOADER_S6_FLIGHT_" + ("RESULT" if restart else "PAUSED") + " cut=" + cut + " "
    legacy.require(sum(prefix in line for line in lines) == 1, "Missing/duplicate selected flight observation")
    if restart and cut in F2[1:] + F3 + F45 + F6 + F78:
        recovery_witness(lines, cut)
    if not restart:
        witness(lines, cut)
        hook = {"LF_S6_F1_ARM_BEFORE": "K8", "LF_S6_F1_ARM_REPLACED": "K9", "LF_S6_F1_ARM_ACK": "F1_ARM_ACK",
                "LF_S6_F2_BEFORE": "JOURNAL_COMMIT", "LF_S6_F2_REPLACED": "JOURNAL_COMMIT",
                "LF_S6_F2_ACK": "F2_PREPARED_ACK"}.get(cut, "BARRIER_" + cut.removeprefix("LF_S6_F1_"))
        if cut in F3: hook = "FIXTURE_PHASE_READBACK"
        if cut in F45:
            import loader_s6_phases
            hook = loader_s6_phases.HOOKS[cut]
        if cut in F6:
            import loader_s6_terminal
            hook = loader_s6_terminal.hook(cut)
        if cut in F78:
            import loader_s6_cleanup
            hook = loader_s6_cleanup.hook(cut)
        legacy.require(sum(line.rstrip("\r\n").endswith(prefix + "hook=" + hook) for line in lines) == 1,
                       "Wrong flight checkpoint hook")


def recovery_witness(lines, cut):
    if cut not in F2[1:] + F3 + F45 + F6 + F78: return None
    import loader_s6_forward
    return loader_s6_forward.recovery_witness(lines, cut)
