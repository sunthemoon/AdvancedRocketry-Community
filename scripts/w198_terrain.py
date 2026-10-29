"""W198's explicit mapping, launch budgets and complete integrated native oracle."""
from __future__ import annotations

from pathlib import Path

from run_w152_native import native_case_plan, block_state_at, expected_resources
from run_v180_landed_disassembly_cuts import chunk_record, nbt_file, uuid_of, block_at
from w152_native_inputs import digest
from sealed_cut_oracle import elect, require_inventory

TRANSFER = "data/advancedrocketrycommunity_rocket_transfers.dat"
TRANSACTION = "data/advancedrocketrycommunity_rocket_transactions.dat"
AIR = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air", "<chunk-not-stored>", "<section-not-stored>"}


def require(value, message):
    if not value:
        raise ValueError(message)


def plan():
    return [dict(id=f"{i + 1:03}", input=row, starts=4, tranche=i // 15 + 1,
                 oracle="P" if row["cut"] == "C0_BASELINE_SAVED" else "U")
            for i, row in enumerate(native_case_plan())]


def check_launch(ledger, row, attempt, stage):
    require(len(ledger) < 60, "Tranche exceeds 60 counted launches")
    require(not any(entry.get("row") == row and entry.get("attempt") == attempt
                    and entry.get("stage") == stage for entry in ledger), "Repeated launch identity")


def check_attempt(results, row):
    previous = [entry for entry in results if entry["row"] == row]
    require(not any(entry["status"] == "PASS" for entry in previous), "Row already passed")
    require(sum(entry["status"] == "FAIL" for entry in previous) < 2, "Row has two failures; stop")


def check_transfer(root, old, retained):
    require(root.get("schema_version") == 3 and root.get("format_epoch") == "v1.8.0-first-moon-landing",
            "Changed T envelope")
    require(root.get("transfers") == ([old] if retained else []), "Changed exact T authority")
    require(root.get("classic_first_moon_landing") == dict(schema_version=1, state="EMPTY"), "Changed landing outbox")


def phase_at_cut(cut):
    if cut in {"C0_BASELINE_SAVED", "PREPARE_PROOF_CURRENT", "C1_RETAIN_BEFORE", "C1_RETAIN_AFTER", "C1_MOVE_BEFORE"}:
        return None
    if cut.startswith(("C5_MOVE_AFTER", "C5_UNKNOWN", "C5_ACK", "UNSEAL_", "MIXED_")):
        return None
    if cut.startswith(("C3_MOVE_AFTER", "C3_UNKNOWN", "C3_ACK", "C4_", "COMPLETE", "C5_")):
        return "RESTORED"
    return "LOCKED"


def rocket_census(roots):
    found = []; pending = [(row, 0) for row in roots]; visited = 0
    while pending:
        row, depth = pending.pop(); visited += 1
        require(depth <= 64 and visited <= 262_144, "Native entity walk bound exceeded")
        require(row.get("id") != "minecraft:player", "Saved player in entity storage")
        if "RocketEntityData" in row:
            require(depth == 0, "Nested rocket authority")
            found.append(row)
        pending.extend((child, depth + 1) for child in row.get("Passengers", []))
    return found


def entity_projection(entity):
    return {key: entity.get(key) for key in ("id", "UUID", "Pos", "RocketEntityData", "Passengers")}


def check_entity(entity, expected):
    require(entity_projection(entity) == entity_projection(expected), "Changed full D1 identity/resources/flight")


def check_member(root, block, snapshot, entity, transaction):
    state = snapshot["block_palette"][block["palette"]]
    at = [snapshot["source_origin"][i] + block["position"][i] for i in range(3)]
    require(root.get("schema") == 1 and uuid_of(root["snapshot_id"]) == snapshot["snapshot_id"]
            and root["rocket_entity_id"] == entity["UUID"] and root["content_hash"] == snapshot["content_hash"]
            and root["dimension"] == snapshot["source_dimension"] and root["position"] == at
            and root["state"] == state and root.get("block_entity") == block.get("block_entity"),
            "Changed complete sealed member")
    require(root["member_index"] == snapshot["relative_blocks"].index(block), "Changed member index")
    if transaction:
        require(root["transaction_id"] == transaction["transaction_id"], "Foreign sealed transaction")


def native_payload(be, payload):
    adapter = payload["adapter"].split(":")[-1]
    if adapter == "vanilla_container_v1":
        keys = {"Items", "LootTable", "LootTableSeed", "CustomName", "Lock"}
        return {key: value for key, value in be.items() if key in keys}
    if adapter == "pressurized_tank_v1":
        return {"tank": be.get("arce_pressurized_tank")}
    if adapter == "satellite_bay_v1":
        return be.get("arce_satellite_bay", {})
    raise ValueError("Unmapped fixture payload: " + adapter)


def check_census_payloads(roots, accounted, members):
    """Reject resources/identities outside the exact payload subtrees already validated.

    Unlike the labelled conservation sum, this walks nested inventories and item tags,
    and also rejects unlabelled stacks. Empty unrelated hosts are not extra authorities.
    Exempt decoded objects by identity, never by equality or claimed coordinates.
    """
    pending = [(root, 0) for root in roots]; visited = 0
    while pending:
        value, depth = pending.pop(); visited += 1
        require(depth <= 64 and visited <= 262_144, "Native census payload walk bound exceeded")
        if id(value) in accounted:
            continue
        if isinstance(value, dict):
            require(not (isinstance(value.get("id"), str) and "Count" in value)
                    and not (isinstance(value.get("FluidName"), str) and "Amount" in value)
                    and value.get("id") not in ("minecraft:item", "minecraft:player")
                    and ("arce_sealed_rocket_member" not in value
                         or id(value["arce_sealed_rocket_member"]) in members),
                    "Unaccounted census resource or sealed-member identity")
            children = value.values()
        elif isinstance(value, list):
            children = value
        else:
            continue
        require(visited + len(pending) + len(children) <= 262_144, "Native census payload walk bound exceeded")
        pending.extend((child, depth + 1) for child in children)


def inspect(world: Path, fixture: dict, row: dict, stage: str):
    old = fixture["transfer_root"]["transfers"][0]; expected = fixture["destination"]
    snapshot = old["destination_snapshot"]
    retained_t = row["oracle"] == "P"
    transfer = nbt_file(world / TRANSFER, 67_108_864)["data"]
    check_transfer(transfer, old, retained_t)
    path = world / TRANSACTION
    root = nbt_file(path, 67_108_864)["data"] if path.exists() else {}
    records = root.get("transactions", [])
    require(len(records) <= 1, "Multiple U authorities")
    transaction = records[0] if records else None
    if transaction:
        require(not retained_t and transaction["type"] == "SEALED_DISASSEMBLY"
                and transaction["snapshot"] == snapshot and transaction["rocket_entity_id"] == expected["UUID"]
                and transaction["owner_id"] == expected["RocketEntityData"]["owner_id"]
                and uuid_of(transaction["snapshot_id"]) == snapshot["snapshot_id"]
                and transaction["content_hash"] == snapshot["content_hash"], "Changed full U snapshot or identity")
    files = {TRANSFER: digest(world / TRANSFER)}
    if path.exists(): files[TRANSACTION] = digest(path)
    entities = []; terrain = {}; recipients = []; census = []; accounted = set(); members = set()
    for x in range(-1, 3):
        for z in range(-1, 3):
            for folder in ("region", "entities", "poi"):
                path = world / folder / f"r.{x >> 5}.{z >> 5}.mca"
                if path.exists(): files[path.relative_to(world).as_posix()] = digest(path)
                if folder == "region":
                    terrain[x, z] = chunk_record(path, x, z) or {}
                    census.extend(terrain[x, z].get("block_entities", []))
                if folder == "entities":
                    rows = (chunk_record(path, x, z) or {}).get("Entities", [])
                    census.extend(rows)
                    entities.extend(rocket_census(rows))
                    recipients.extend(item for item in rows if item.get("id") == "minecraft:item")
    for path in (world / "playerdata").glob("*.dat"):
        files[path.relative_to(world).as_posix()] = digest(path); recipients.append(nbt_file(path, 4_194_304))
    require(not recipients, "Unexpected inventory recipient/drop/player")
    sources = [entity for entity in entities if entity["UUID"] == fixture["source"]["UUID"]]
    control_cut = retained_t and stage in ("start", "recovery-cut")
    require(len(sources) <= (1 if control_cut else 0), "Old S survived outside the baseline control")
    if retained_t and stage == "start": require(len(sources) == 1, "Initial control lost its saved S input")
    if sources: check_entity(sources[0], fixture["source"])
    survivors = [entity for entity in entities if entity["UUID"] != fixture["source"]["UUID"]]
    require(len(survivors) <= 1, "Duplicate destination authority")
    for entity in survivors: check_entity(entity, expected)
    accounted.update(id(entity["RocketEntityData"]) for entity in entities)
    sealed = []; native = []; blocks = []
    for endpoint in ("source_snapshot", "destination_snapshot"):
        image = old[endpoint]
        for block in image["relative_blocks"]:
            at = [image["source_origin"][i] + block["position"][i] for i in range(3)]
            x, y, z = at; chunk = terrain[x >> 4, z >> 4]
            name = block_at(chunk or None, x, y, z)
            bes = [be for be in chunk.get("block_entities", []) if [be.get(k) for k in ("x", "y", "z")] == at]
            require(len(bes) <= 1, "Duplicate native block entity")
            be = bes[0] if bes else None
            blocks.append(dict(endpoint=endpoint, position=at, state=name, block_entity=be))
            if name in AIR:
                require(be is None, "Hidden payload beside air"); continue
            require(endpoint == "destination_snapshot", "Restored source terrain")
            if name == "advancedrocketrycommunity:sealed_rocket_block":
                require(be is not None, "Carrier without payload")
                check_member(be["arce_sealed_rocket_member"], block, snapshot, expected, transaction); sealed.append(be)
                member = be["arce_sealed_rocket_member"]
                members.add(id(member))
                if "block_entity" in block:
                    accounted.add(id(member["block_entity"]))
            else:
                state = block_state_at(chunk, x, y, z); wanted = image["block_palette"][block["palette"]]
                require(state["Name"] == wanted["id"] and state.get("Properties", {}) == wanted["properties"],
                        "Changed native block state/properties")
                payload = block.get("block_entity")
                require(be is None if payload is None else be is not None and native_payload(be, payload) == payload["data"],
                        "Changed complete native payload")
                if payload:
                    accounted.update(id(value) for value in native_payload(be, payload).values())
                native.append(dict(state=state, block_entity=be))
    check_census_payloads(census, accounted, members)
    phase = transaction["phase"] if transaction else None
    if stage == "start": require(phase == phase_at_cut(row["input"]["cut"]), "Wrong exact cut U phase")
    # I6's earlier U is not the current fuel/debit image. This matrix requires durable D1.
    if phase is not None and phase not in ("RESTORED", "COMMITTED"):
        require(len(survivors) == 1, "Missing durable D1 before block election")
    state = elect(phase, survivors, sealed, native, recipients)
    require(state["authority"] in ("ENTITY", "BLOCKS"), "Unsupported integrated authority")
    require_inventory(state, expected_resources("ALL"))
    # Empty fuel cannot be inferred from absence of D: successful block election is the sole disposal authority.
    if state["authority"] == "ENTITY": require(len(survivors) == 1, "Missing durable D1")
    if phase is None and state["authority"] == "BLOCKS": require(len(sealed) + len(native) == 6, "Incomplete footprint")
    if stage in ("recover1", "recover2"):
        require(not transaction and not sealed, "Stable restart retains U/carrier")
    state.update(transfer=transfer, transaction=root, entities=[entity_projection(entity) for entity in survivors],
                 blocks=blocks, files=files,
                 fuel=expected["RocketEntityData"]["flight_data"]["fuel"] if state["authority"] == "ENTITY" else "CONSENTED_DISPOSAL")
    return state


def comparable(state):
    # Journals retain write-generation metadata; compare authorities and complete records, not timestamps/hashes.
    return {key: (value.get("transactions", []) if key == "transaction" else value)
            for key, value in state.items() if key != "files"}
