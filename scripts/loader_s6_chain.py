"""S6 two-loader operator oracle; no production or historical oracle changes."""
import copy
import re
from uuid import UUID

import loader_cut_oracle as legacy
import loader_s6_integrity as i
from run_dedicated_server_smoke import SmokeError

CASES = tuple("LF_S6_CHAIN_" + side + suffix for suffix in ("", "_BI")
              for side in ("BEFORE", "REPLACED", "ACK"))


def descriptor(bi):
    return dict(i.MONO_DESCRIPTOR, mode="BIPROPELLANT", oxidizer=2000) if bi else copy.deepcopy(i.MONO_DESCRIPTOR)


def bank(amount, fluid):
    return dict(amount=amount, branch="FLUID", fluid=fluid) if amount else dict(amount=0, branch="EMPTY")


def vector(bi, epoch):
    return dict(schema=1, descriptor=descriptor(bi), revision=epoch,
        primary=bank((1000 if epoch else 0) if bi else epoch * 1000, legacy.HYDROGEN if bi else legacy.FUEL),
        oxidizer=bank(1000 if bi and epoch == 2 else 0, legacy.OXYGEN))


def fuel_signature(bi, epoch):
    v = vector(bi, epoch); d = v["descriptor"]
    roles = ("MONO", "BIPROPELLANT_FUEL", "OXIDIZER", "WORKING_FLUID")
    fields = ("mode", "primary", "oxidizer", "numerator", "denominator", "thrust_units", "capacity_units", "core_units")
    dh = i.sha(":".join([str(d[k]) for k in fields] + [str(bool(d[k])).lower() for k in ("gravity", "required", "hand")]
        + [";".join(role + "=[" + ", ".join(d["fuels"][role]) + "]" for role in roles)]))
    return i.sha("propulsion-resources-1:" + dh + ":" + i.digest(i.native_tag(v["primary"]))
                  + ":" + i.digest(i.native_tag(v["oxidizer"])) + ":" + str(epoch))


def tank_signature(device, fluid, amount):
    return i.sha("arce-escrow-fluid-tank-1:" + device + ":" + i.digest(i.native_tag(dict(FluidName=fluid, Amount=amount))))


def wrapper(ledger, records, epoch, role="DESTINATION"):
    markers = [dict(op_id=op["op_id"], seq=op["seq"], role=role,
                    epoch_after=op["seq"] if role == "DESTINATION" else 1) for op in records]
    value = dict(schema_version=1, resource_epoch=epoch, markers=markers)
    if ledger is not None: value["ledger_id"] = ledger
    return value


def wrapper_tag(value):
    nbt = i.nbt
    markers = [i.compound(dict(op_id=i.uid(m["op_id"]), seq=i.scalar(m["seq"], nbt.LONG),
        role=i.scalar(m["role"]), epoch_after=i.scalar(m["epoch_after"], nbt.LONG))) for m in value["markers"]]
    fields = dict(schema_version=i.scalar(1, nbt.INT), resource_epoch=i.scalar(value["resource_epoch"], nbt.LONG),
                  markers=nbt.Tag(nbt.LIST, (nbt.COMPOUND if markers else 0, markers)))
    if "ledger_id" in value: fields["ledger_id"] = i.uid(value["ledger_id"])
    return i.compound(fields)


def invariant(disk):
    r = disk["rocket"]; d = r["RocketEntityData"]
    return i.digest(i.compound(dict(schema_version=i.scalar(1, i.nbt.INT), type=i.scalar(legacy.ROCKET),
        physical=i.uid(r["UUID"]), logical=i.uid(d["assembly_transaction_id"]), owner=i.uid(d["owner_id"]),
        snapshot=i.uid(d["snapshot"]["snapshot_id"]), snapshot_hash=i.scalar(d["snapshot"]["content_hash"]))))


def captures(disk, op, records, bi):
    r = disk["rocket"]; d = r["RocketEntityData"]; root = disk["ledger"]; seq = op["seq"]
    result = {}
    for after in (False, True):
        epoch = seq - int(not after); selected = records[:epoch]
        w = wrapper(root["ledger_id"] if epoch else None, selected, epoch)
        result[("after" if after else "before") + "_wrapper"] = i.digest(wrapper_tag(w))
        flight = copy.deepcopy(d["flight_data"]); v = vector(bi, epoch)
        flight["fuel"].update(amount=v["primary"]["amount"], propulsion=v)
        frame = dict(schema_version=1, physical=r["UUID"], type=legacy.ROCKET, logical=d["assembly_transaction_id"],
            owner=d["owner_id"], level=flight["current_dimension"], chunk=[4, 4], pos=r["Pos"], motion=r["Motion"],
            no_gravity=r["NoGravity"], no_physics=1, on_ground=r["OnGround"], invulnerable=r["Invulnerable"],
            yaw=r["Rotation"][0], pitch=r["Rotation"][1], passengers=[], assembly_snapshot=d["snapshot"]["snapshot_id"],
            content_hash=d["snapshot"]["content_hash"], snapshot=d["snapshot"], flight=flight)
        tag = i.native_tag(frame); tag.value["wrapper"] = wrapper_tag(w)
        side = "after" if after else "before"
        result["destination_" + side] = i.digest(i.compound(tag.value))
        source = copy.deepcopy({k: v for k, v in disk["loaders"][seq - 1].items() if k != "keepPacked"})
        loader = source["arce_fluid_fuel_loader"]; loader.pop("resource_escrow")
        loader["tank"]["Amount"] = 4000 if after else 5000
        tag = i.native_tag(source)
        t = tag.value["arce_fluid_fuel_loader"]
        t.value["resource_escrow"] = wrapper_tag(wrapper(root["ledger_id"] if after else None,
                                                        [op] if after else [], int(after), "SOURCE"))
        tag.value["arce_fluid_fuel_loader"] = i.compound(t.value)
        result["source_" + side] = i.digest(tag)
    return result


def expected_binding(disk, op, records, bi):
    r = disk["rocket"]; d = r["RocketEntityData"]
    fields = dict(v=1, complete=1, kind="FUEL_VECTOR_V1", physical=r["UUID"], logical=d["assembly_transaction_id"],
                  owner=d["owner_id"], snapshot=d["snapshot"]["snapshot_id"])
    hashes = dict(snapshot_hash=d["snapshot"]["content_hash"], invariant=invariant(disk),
        src_ref=i.endpoint_digest(op["source"]), dst_ref=i.endpoint_digest(op["destination"]),
        before_frontier=fuel_signature(bi, op["seq"] - 1), after_frontier=fuel_signature(bi, op["seq"]))
    hashes.update(captures(disk, op, records, bi))
    fields.update({key: list(bytes.fromhex(value)) for key, value in hashes.items()})
    return fields


def check_resources(disk, bi, released):
    projected = copy.deepcopy(disk)
    projected["ledger"]["tombstones"] = []; projected["ledger"]["rocket_authorities"] = []
    identity = legacy.check_chain(projected, "LF_M_C2" + ("_BI" if bi else ""), int(released))
    root = disk["ledger"]; r = disk["rocket"]; d = r["RocketEntityData"]; flight = d["flight_data"]
    legacy.require(d["schema_version"] == 3 and d["snapshot"]["schema_version"] == 2 and flight["schema_version"] == 2
        and d["snapshot"]["propulsion"] == descriptor(bi)
        and flight["fuel"] == dict(amount=1000 if bi else 2000, capacity=2000, committed_debits=[], propulsion=vector(bi, 2)),
        "S6 chain changed complete typed fuel/schema")
    for device in disk["loaders"]:
        loader = device["arce_fluid_fuel_loader"]
        legacy.require(loader["schema_version"] == 1 and loader["binding"] == dict(schema_version=1, level="minecraft:overworld",
            physical_rocket=r["UUID"], logical_rocket=d["assembly_transaction_id"], x=64, y=100, z=64), "S6 chain changed complete binding")
    records = root["records"]
    for op in records:
        seq = op["seq"]; device = disk["loaders"][seq - 1]["arce_fluid_fuel_loader"]; fluid = device["tank"]["FluidName"]
        legacy.require(op["schema_version"] == 2 and op["kind"] == "FLUID_PROPULSION_FILL" and op["owner"] == d["owner_id"]
                       and op["transfer_guard"]["rocket_id"] == d["assembly_transaction_id"]
                       and "transfer_id" not in op["transfer_guard"] and op["transfer_guard"]["journal_generation"] == 1,
                       "S6 chain changed operation identity or guard")
        src = dict(kind="BLOCK_ENTITY", level="minecraft:overworld", type=legacy.LOADER, identity=device["device"],
            x=65 + seq, y=100, z=64, unit="TANK", before_epoch=0, after_epoch=1,
            before_signature=tank_signature(device["device"], fluid, 5000), after_signature=tank_signature(device["device"], fluid, 4000))
        dst = dict(kind="ENTITY", level="minecraft:overworld", type=legacy.ROCKET, identity=r["UUID"],
            logical_rocket=d["assembly_transaction_id"], x=4, y=0, z=4, unit="OXIDIZER" if bi and seq == 2 else "PRIMARY",
            before_epoch=seq - 1, after_epoch=seq, before_signature=fuel_signature(bi, seq - 1), after_signature=fuel_signature(bi, seq))
        legacy.require(op["source"] == src and op["destination"] == dst, "S6 chain changed complete endpoint/frontier")
        legacy.require(op["payload_hash"] == i.digest(i.native_tag(op["payload"]))
                       and op["native_binding"] == expected_binding(disk, op, records, bi), "S6 chain changed complete native capture binding")
    return dict(identity, ledger=root["ledger_id"], snapshot=d["snapshot"], fuel=flight["fuel"],
                loader_schemas=[l["arce_fluid_fuel_loader"]["schema_version"] for l in disk["loaders"]])


def expected_observations(disk):
    root = disk["ledger"]; records = root["records"]; bi = disk["rocket"]["RocketEntityData"]["snapshot"]["propulsion"]["mode"] == "BIPROPELLANT"
    result = []
    for op in records:
        e = op["source"]; w = wrapper(root["ledger_id"], [op], 1, "SOURCE")
        reference = dict(schema_version=1, kind=e["kind"], level=e["level"], type=e["type"], physical=e["identity"],
                         x=e["x"], y=e["y"], z=e["z"])
        result.append(dict(endpoint=e, missing=0, bound=1, quantity=4000, signature_kind="FLUID_TANK_V1",
            invariant_digest=i.digest(i.native_tag(reference)),
            state=dict(ledger_id=root["ledger_id"], epoch=1, signature=e["after_signature"], markers=w["markers"])))
    w = wrapper(root["ledger_id"], records, 2)
    result.append(dict(endpoint=records[0]["destination"], missing=0, bound=1, quantity=2000,
        signature_kind="FUEL_VECTOR_V1", invariant_digest=invariant(disk), normalized_wrapper=w,
        state=dict(ledger_id=root["ledger_id"], epoch=2, signature=fuel_signature(bi, 2), markers=w["markers"])))
    return result


def receipts(root):
    from loader_s6_oracle import pending_receipt
    records = root["records"]; anchor = min(op["op_id"] for op in records); result = []
    for op in records:
        observations = [o for o in op["settlement"]["observations"] if o["endpoint"]["identity"] in (op["source"]["identity"], op["destination"]["identity"])]
        member = dict(op, settlement=dict(op["settlement"], observations=observations))
        result.append(dict(pending_receipt(member), anchor_op=anchor))
    return result


def head(root, generation):
    op = root["records"][0]; chosen = next(o for o in op["settlement"]["observations"] if o["signature_kind"] == "FUEL_VECTOR_V1")
    return dict(v=1, l=op["transfer_guard"]["rocket_id"], o=op["owner"], a=min(m["op_id"] for m in root["records"]),
        g=generation, p=op["destination"]["identity"], e=2, k="FUEL_VECTOR_V1", s=list(bytes.fromhex(chosen["state"]["signature"])),
        i=list(bytes.fromhex(chosen["invariant_digest"])), issued=0, completed=0, outcome="READY")


def check(image, restart, identity=None, cut=CASES[0]):
    from loader_s6_oracle import normalize_ids, canonical_uuid
    from run_v180_rocket_settlement_intent import ACTOR
    legacy.require(cut in CASES and restart in (0, 1, 2) and image.get("read_status") == "KNOWN"
                   and isinstance(image.get("native"), dict), "Chain UNKNOWN is not zero loss")
    try:
        disk = normalize_ids(image["native"]); identity = normalize_ids(identity); root = disk["ledger"]
        before = "_BEFORE" in cut; released = bool(restart and not before); bi = cut.endswith("_BI")
        resources = check_resources(disk, bi, released)
        legacy.require(identity is None or resources == identity["resources"], "Chain changed a complete native identity/snapshot")
        if not released:
            legacy.require(not root["tombstones"] and not root["rocket_authorities"], "Chain prematurely installed or released its decision")
            records = root["records"]
            legacy.require(all(op["phase"] == ("REPAIR" if before else "SETTLING_ACCEPT") and op["reason"] == "OTHER_STATE" for op in records),
                           "Chain is not the selected manual or frozen component")
            if before:
                legacy.require(all(not op.get("settlement") for op in records), "Pre-O1 chain invented a decision")
            else:
                intent = records[0]["settlement"]
                legacy.require(records[1]["settlement"] == intent and intent["schema_version"] == 2 and intent["choice"] == "ACCEPT_CURRENT"
                    and intent["ledger_id"] == root["ledger_id"] and intent["actor"] == canonical_uuid(ACTOR) and intent["actor_name"] == "Server"
                    and re.fullmatch(r"[0-9a-f]{64}", intent["digest"]) and intent["confirmed_tick"] >= intent["preview_tick"] >= 0
                    and set(intent) == {"schema_version", "choice", "ledger_id", "actor", "actor_name", "digest", "confirmed_tick", "preview_tick", "members", "observations"},
                    "Chain changed its complete frozen decision")
                legacy.require(len(intent["members"]) == 2, "Chain omitted a frozen member")
                for member, op in zip(intent["members"], records):
                    legacy.require(member == dict(op_id=op["op_id"], phase="REPAIR", reason="OTHER_STATE", digest=member["digest"])
                                   and re.fullmatch(r"[0-9a-f]{64}", member["digest"]), "Chain changed a frozen member")
                legacy.require(sorted(intent["observations"], key=lambda o: o["endpoint"]["identity"])
                    == sorted(expected_observations(disk), key=lambda o: o["endpoint"]["identity"]), "Chain changed a complete frozen observation")
            if identity:
                legacy.require(root == identity["authority"], "Pre-O1 restart changed held authority")
        else:
            legacy.require(identity is not None and len(root["tombstones"]) == 2 and len(root["rocket_authorities"]) == 1,
                           "Chain release lost its initial decision, receipts or READY head")
            generations = {t["release_generation"] for t in root["tombstones"]}
            legacy.require(len(generations) == 1, "Split chain release generation")
            generation = next(iter(generations))
            legacy.require(0 < generation == root["writer_generation"], "Wrong chain release generation")
            expected = [dict(t, released=1, release_generation=generation) for t in receipts(identity["authority"])]
            legacy.require(root["tombstones"] == expected, "Chain changed the complete immutable receipts")
            h = root["rocket_authorities"][0]
            decoded = dict(h, **{k: canonical_uuid(h[k]) for k in ("l", "o", "a", "p")})
            legacy.require(decoded == head(identity["authority"], generation), "Chain changed its READY head")
            if "completed" in identity:
                legacy.require(root == identity["completed"], "Second restart changed complete chain authority")
        observed = identity or dict(resources=resources, authority=root)
        if released: observed = dict(observed, completed=root)
        return dict(identity=observed, raw_total=10000, per_fluid={legacy.HYDROGEN: 5000, legacy.OXYGEN: 5000} if bi else {legacy.FUEL: 10000},
                    available_total=None, loss=None, state="RELEASED" if released else "PRE_O1" if before else "FROZEN_O1")
    except (KeyError, TypeError, IndexError, ValueError) as failure:
        raise SmokeError(f"Malformed S6 chain observation: {failure}") from failure


def check_logs(lines, restart, cut):
    lines = [line.rstrip("\r\n") for line in lines]; before = "_BEFORE" in cut
    if restart:
        marker = (f"ARCE_LOADER_S6_CHAIN_RESULT cut={cut} tanks=4000,4000 banks={'1000,1000' if cut.endswith('_BI') else '2000,0'} "
                  f"epochs=1,1,2 records={2 if before else 0} receipts={0 if before else 2} state={'PRE_O1' if before else 'RELEASED'}")
    else:
        hook = "K1" if before else "K2" if "_REPLACED" in cut else "O1_ACK"
        marker = f"ARCE_LOADER_S6_CHAIN_PAUSED cut={cut} hook={hook}"
    legacy.require(sum(line.endswith(marker) for line in lines) == 1, "Missing/repeated chain operator boundary")
    legacy.require(not any("/ERROR" in line or "/FATAL" in line for line in lines), "Unexpected chain operator error")
