"""S6-only native identity and exact mono-prefix checks; no legacy changes.

The retained observations are decoded JSON, so numeric NBT types below come
from the named production codecs, never from guessing a Python integer's size.
"""
import copy
import hashlib
import re
from uuid import UUID

import loader_cut_nbt as nbt
from loader_cut_oracle import FUEL, LOADER, ROCKET, require

# FluidFuelLoaderEscrowFixtures.mono(2000), including Settings.DEFAULTS.
MONO_DESCRIPTOR = dict(schema=1, mode="MONO", primary=2000, oxidizer=0, numerator=1, denominator=1,
    thrust_units=2**56, capacity_units=2**56, core_units=2**56, gravity=1, required=1, hand=1,
    fuels=dict(MONO=[FUEL], BIPROPELLANT_FUEL=["advancedrocketrycommunity:hydrogen"],
               OXIDIZER=["advancedrocketrycommunity:oxygen"], WORKING_FLUID=["advancedrocketrycommunity:hydrogen"]))


def sha(value):
    return hashlib.sha256(value.encode("utf-8") if isinstance(value, str) else value).hexdigest()


def compound(fields):
    return nbt.Tag(nbt.COMPOUND, dict(sorted(fields.items())))


def scalar(value, kind=nbt.STRING):
    return nbt.Tag(kind, value.encode("utf-8") if kind == nbt.STRING else value)


def uid(value):
    raw = UUID(value).bytes
    return nbt.Tag(nbt.INTS, [int.from_bytes(raw[i:i+4], "big", signed=True) for i in range(0, 16, 4)])


def digest(tag):
    return sha(nbt.write(b"", tag))


def native_tag(value, key=""):
    """Codec-defined types for S6's fixed, standard no-payload mono fixture."""
    if isinstance(value, dict):
        return compound({k: native_tag(v, k) for k, v in value.items()})
    if key in ("owner", "device", "physical_rocket", "logical_rocket", "logical_rocket_id", "physical", "logical", "assembly_snapshot"):
        return uid(value)
    if key in ("source_origin", "current_origin", "bounding_box", "position", "chunk"):
        return nbt.Tag(nbt.INTS, value)
    if key == "passenger_anchors":
        return nbt.Tag(nbt.LIST, (nbt.INTS if value else 0, [nbt.Tag(nbt.INTS, v) for v in value]))
    if key in ("pos", "motion"):
        return nbt.Tag(nbt.LIST, (nbt.DOUBLE, [scalar(v, nbt.DOUBLE) for v in value]))
    if isinstance(value, list):
        elements = [native_tag(v) for v in value]
        kind = elements[0].type if elements else 0
        require(all(v.type == kind for v in elements), "S6 mixed native list")
        return nbt.Tag(nbt.LIST, (kind, elements))
    if isinstance(value, str):
        return scalar(value)
    if key in ("yaw", "pitch"):
        return scalar(value, nbt.FLOAT)
    if key in ("gravity", "required", "hand", "no_gravity", "no_physics", "on_ground", "invulnerable"):
        return scalar(value, nbt.BYTE)
    if key in ("amount", "capacity", "revision", "primary", "oxidizer", "numerator", "denominator",
               "thrust_units", "capacity_units", "core_units", "state_started_game_time", "created_at_game_time",
               "mass", "thrust", "fuel_capacity"):
        return scalar(value, nbt.LONG)
    require(key in ("Amount", "schema", "schema_version", "x", "y", "z", "palette", "seat_capacity",
                    "block_count", "engine_count", "seat_count", "guidance_count", "block_entity_count"),
            "S6 unknown native numeric type: " + key)
    return scalar(value, nbt.INT)


def capture_digests(pair, ledger, op):
    """Rebuild both declared captures of this stationary one-fill fixture.

    The fixture never moves/boards between admission and these cuts; no_physics
    is RocketEntity's fixed constructor value, not a value guessed from a hash.
    """
    device = pair["loaders"][0]; rocket = pair["rocket"]; data = rocket["RocketEntityData"]
    hashes = {}
    for after in (False, True):
        side = "after" if after else "before"
        source = copy.deepcopy({k: v for k, v in device.items() if k != "keepPacked"})
        source["arce_fluid_fuel_loader"].pop("resource_escrow")
        source["arce_fluid_fuel_loader"]["tank"]["Amount"] = 4000 if after else 5000
        tag = native_tag(source)
        tag.value["arce_fluid_fuel_loader"].value["resource_escrow"] = wrapper_tag(
            ledger["ledger_id"] if after else None, op if after else None, "SOURCE")
        # Restore canonical ordering after inserting the independently typed wrapper.
        tag.value["arce_fluid_fuel_loader"] = compound(tag.value["arce_fluid_fuel_loader"].value)
        hashes["source_" + side] = digest(tag)
        flight = copy.deepcopy(data["flight_data"])
        flight["fuel"]["amount"] = 1000 if after else 0
        flight["fuel"]["propulsion"].update(primary=mono_bank(1000 if after else 0), revision=int(after))
        frame = dict(schema_version=1, physical=rocket["UUID"], type=ROCKET,
            logical=data["assembly_transaction_id"], owner=data["owner_id"], level=flight["current_dimension"],
            chunk=[int(rocket["Pos"][0]) // 16, int(rocket["Pos"][2]) // 16], pos=rocket["Pos"], motion=rocket["Motion"],
            no_gravity=rocket["NoGravity"], no_physics=1, on_ground=rocket["OnGround"], invulnerable=rocket["Invulnerable"],
            yaw=rocket["Rotation"][0], pitch=rocket["Rotation"][1], passengers=[],
            assembly_snapshot=data["snapshot"]["snapshot_id"], content_hash=data["snapshot"]["content_hash"],
            snapshot=data["snapshot"], flight=flight)
        tag = native_tag(frame)
        tag.value["wrapper"] = wrapper_tag(ledger["ledger_id"] if after else None, op if after else None)
        hashes["destination_" + side] = digest(compound(tag.value))
    return hashes


def endpoint_digest(endpoint):
    return digest(compound({k: uid(v) if k in ("identity", "logical_rocket") else
        scalar(v, nbt.LONG if k.endswith("_epoch") else nbt.INT if k in ("x", "y", "z") else nbt.STRING)
        for k, v in endpoint.items()}))


def wrapper_tag(ledger, op=None, role="DESTINATION"):
    markers = [] if op is None else [compound(dict(op_id=uid(op["op_id"]), seq=scalar(op["seq"], nbt.LONG),
        role=scalar(role), epoch_after=scalar(1, nbt.LONG)))]
    fields = dict(schema_version=scalar(1, nbt.INT), resource_epoch=scalar(int(op is not None), nbt.LONG),
                  markers=nbt.Tag(nbt.LIST, (nbt.COMPOUND if markers else 0, markers)))
    if ledger is not None:
        fields["ledger_id"] = uid(ledger)
    return compound(fields)


def tank_signature(device, amount):
    payload = digest(compound(dict(FluidName=scalar(FUEL), Amount=scalar(amount, nbt.INT))))
    return sha("arce-escrow-fluid-tank-1:" + device + ":" + payload)


def mono_bank(amount):
    return dict(amount=amount, branch="FLUID", fluid=FUEL) if amount else dict(amount=0, branch="EMPTY")


def fuel_signature(descriptor, amount, revision):
    roles = ("MONO", "BIPROPELLANT_FUEL", "OXIDIZER", "WORKING_FLUID")
    numbers = ("mode", "primary", "oxidizer", "numerator", "denominator", "thrust_units", "capacity_units", "core_units")
    descriptor_hash = sha(":".join([str(descriptor[k]) for k in numbers]
        + [str(bool(descriptor[k])).lower() for k in ("gravity", "required", "hand")]
        + [";".join(role + "=[" + ", ".join(descriptor["fuels"][role]) + "]" for role in roles)]))
    def bank_hash(bank):
        return digest(compound({k: scalar(v, nbt.LONG if k == "amount" else nbt.STRING) for k, v in bank.items()}))
    return sha("propulsion-resources-1:" + descriptor_hash + ":" + bank_hash(mono_bank(amount))
               + ":" + bank_hash(mono_bank(0)) + ":" + str(revision))


def pair_identity(pair, ledger, records=True):
    """Validate the whole typed prefix and retain all immutable native fields."""
    require(len(pair["loaders"]) == 1, "S6 integrity requires one loader per pair")
    device = pair["loaders"][0]; loader = device["arce_fluid_fuel_loader"]
    rocket = pair["rocket"]; data = rocket["RocketEntityData"]; flight = data["flight_data"]
    snapshot = data["snapshot"]; fuel = flight["fuel"]; vector = fuel["propulsion"]
    descriptor = snapshot["propulsion"]; amount = vector["primary"]["amount"]
    require(data["schema_version"] == 3 and snapshot["schema_version"] == 2 and flight["schema_version"] == 2
            and loader["schema_version"] == 1, "S6 changed native schema")
    require(set(vector) == {"schema", "descriptor", "primary", "oxidizer", "revision"}
            and vector["schema"] == 1 and vector["descriptor"] == descriptor
            and descriptor == MONO_DESCRIPTOR,
            "S6 changed typed propulsion descriptor")
    require(amount in (0, 1000) and vector["primary"] == mono_bank(amount) and vector["oxidizer"] == mono_bank(0)
            and vector["revision"] == data["resource_escrow"]["resource_epoch"] == int(amount == 1000)
            and fuel["amount"] == amount and fuel["capacity"] == descriptor["primary"]
            and set(fuel) == {"amount", "capacity", "committed_debits", "propulsion"} and not fuel["committed_debits"],
            "S6 changed typed fuel, revision, capacity or scalar mirror")
    require(loader["binding"] == dict(schema_version=1, level=flight["current_dimension"],
            physical_rocket=rocket["UUID"], logical_rocket=data["assembly_transaction_id"],
            **dict(zip(("x", "y", "z"), flight["current_origin"])))
            and loader["owner"] == data["owner_id"] and flight["logical_rocket_id"] == data["assembly_transaction_id"],
            "S6 changed complete native loader binding")
    observed = dict(snapshot=snapshot, rocket_schema=data["schema_version"], flight_schema=flight["schema_version"],
                    physical=rocket["UUID"], logical=data["assembly_transaction_id"], owner=data["owner_id"],
                    binding=loader["binding"], device=loader["device"], loader_schema=loader["schema_version"],
                    descriptor=descriptor, capacity=fuel["capacity"])
    if records:
        for op in ledger["records"]:
            if op["source"]["identity"] == loader["device"]:
                check_record(op, pair, ledger)
    return observed


def check_record(op, pair, ledger):
    device = pair["loaders"][0]; loader = device["arce_fluid_fuel_loader"]
    rocket = pair["rocket"]; data = rocket["RocketEntityData"]; snapshot = data["snapshot"]
    flight = data["flight_data"]; descriptor = snapshot["propulsion"]
    require(op["schema_version"] == 2 and op["seq"] == 1 and op["kind"] == "FLUID_PROPULSION_FILL"
            and op["owner"] == loader["owner"] == data["owner_id"]
            and op["transfer_guard"]["rocket_id"] == data["assembly_transaction_id"]
            and op["payload"] == dict(FluidName=FUEL, Amount=1000), "S6 changed record owner, kind or payload")
    source, destination = op["source"], op["destination"]
    for endpoint, entity in ((source, False), (destination, True)):
        expected = dict(kind="ENTITY" if entity else "BLOCK_ENTITY", level=flight["current_dimension"],
            type=ROCKET if entity else LOADER, identity=rocket["UUID"] if entity else loader["device"],
            x=int(rocket["Pos"][0]) // 16 if entity else device["x"], y=0 if entity else device["y"],
            z=int(rocket["Pos"][2]) // 16 if entity else device["z"], unit="PRIMARY" if entity else "TANK",
            before_epoch=0, after_epoch=1,
            before_signature=fuel_signature(descriptor, 0, 0) if entity else tank_signature(loader["device"], 5000),
            after_signature=fuel_signature(descriptor, 1000, 1) if entity else tank_signature(loader["device"], 4000))
        if entity:
            expected["logical_rocket"] = data["assembly_transaction_id"]
        require(endpoint == expected, "S6 record endpoint/frontier disagrees with native prefix")
    binding = op["native_binding"]
    invariant = digest(compound(dict(schema_version=scalar(1, nbt.INT), type=scalar(ROCKET),
        physical=uid(rocket["UUID"]), logical=uid(data["assembly_transaction_id"]), owner=uid(data["owner_id"]),
        snapshot=uid(snapshot["snapshot_id"]), snapshot_hash=scalar(snapshot["content_hash"]))))
    expected = dict(v=1, complete=1, kind="FUEL_VECTOR_V1", physical=rocket["UUID"],
        logical=data["assembly_transaction_id"], owner=data["owner_id"], snapshot=snapshot["snapshot_id"])
    hashes = dict(snapshot_hash=snapshot["content_hash"], invariant=invariant,
        src_ref=endpoint_digest(source), dst_ref=endpoint_digest(destination),
        before_frontier=destination["before_signature"], after_frontier=destination["after_signature"],
        before_wrapper=digest(wrapper_tag(None)), after_wrapper=digest(wrapper_tag(ledger["ledger_id"], op)))
    hashes.update(capture_digests(pair, ledger, op))
    for key, value in hashes.items():
        require(re.fullmatch(r"[0-9a-f]{64}", value) is not None, "S6 malformed expected digest")
        expected[key] = list(bytes.fromhex(value))
    require(binding == expected, "S6 complete record binding disagrees with native identities/frontiers")
