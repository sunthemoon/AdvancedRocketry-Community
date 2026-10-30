"""S6 neutral-wrapper publication; a manual choice is not zero-loss accounting."""
import copy
import re

import loader_s6_chain as chain
from loader_s6_oracle import canonical_uuid, normalize_ids
from run_dedicated_server_smoke import SmokeError
from run_v180_rocket_settlement_intent import ACTOR

CASES = tuple("LF_S6_CHAIN_NORMALIZE_" + side + suffix for suffix in ("", "_BI") for side in ("BEFORE", "AFTER"))
require = chain.legacy.require


def reconstruct(disk, restart, authority, bi):
    """Check the actual neutral resources before reconstructing historical fills."""
    data = disk["rocket"]["RocketEntityData"]
    require(data["flight_data"]["fuel"] == dict(amount=0, capacity=2000, committed_debits=[], propulsion=chain.vector(bi, 0)),
            "Normalization changed the complete chosen empty fuel vector")
    require(data["resource_escrow"] == chain.wrapper(disk["ledger"]["ledger_id"] if restart else None, [], 0),
            "Normalization changed the exact neutral/checkpointed wrapper")
    require(len(authority["records"]) == 2, "Normalization lost the original component")
    projected = copy.deepcopy(disk)
    target = projected["rocket"]["RocketEntityData"]
    target["flight_data"]["fuel"].update(amount=1000 if bi else 2000, propulsion=chain.vector(bi, 2))
    target["resource_escrow"] = chain.wrapper(disk["ledger"]["ledger_id"], [] if restart else authority["records"], 2)
    return projected


def observations(projected, bi):
    result = chain.expected_observations(projected); chosen = result[2]
    chosen.update(bound=0, quantity=0, normalized_wrapper=chain.wrapper(projected["ledger"]["ledger_id"], [], 0))
    chosen["state"].update(epoch=0, signature=chain.fuel_signature(bi, 0), markers=[])
    return result


def check(image, restart, identity=None, cut=CASES[0]):
    require(cut in CASES and restart in (0, 1, 2) and image.get("read_status") == "KNOWN"
            and isinstance(image.get("native"), dict), "Normalization UNKNOWN is not zero loss")
    try:
        disk = normalize_ids(image["native"]); identity = normalize_ids(identity); root = disk["ledger"]
        bi = cut.endswith("_BI")
        require(not restart or identity is not None, "Normalization restart has no frozen initial authority")
        authority = identity["authority"] if restart else root
        projected = reconstruct(disk, restart, authority, bi)
        resources = chain.check_resources(projected, bi, bool(restart))
        resources["fuel"] = disk["rocket"]["RocketEntityData"]["flight_data"]["fuel"]
        require(identity is None or resources == identity["resources"], "Normalization changed complete native identity/snapshot")
        if not restart:
            require(not root["tombstones"] and not root["rocket_authorities"], "Normalization released before O2")
            records = root["records"]
            require(all(op["phase"] == "SETTLING_ACCEPT" and op["reason"] == "OTHER_STATE" for op in records),
                    "Normalization lost its acknowledged O1")
            intent = records[0]["settlement"]
            require(records[1]["settlement"] == intent and intent["schema_version"] == 2 and intent["choice"] == "ACCEPT_CURRENT"
                and intent["ledger_id"] == root["ledger_id"] and intent["actor"] == canonical_uuid(ACTOR) and intent["actor_name"] == "Server"
                and re.fullmatch(r"[0-9a-f]{64}", intent["digest"]) and intent["confirmed_tick"] >= intent["preview_tick"] >= 0
                and set(intent) == {"schema_version", "choice", "ledger_id", "actor", "actor_name", "digest",
                                    "confirmed_tick", "preview_tick", "members", "observations"}, "Normalization changed its frozen decision")
            require(len(intent["members"]) == 2, "Normalization omitted a member")
            for member, op in zip(intent["members"], records):
                require(member == dict(op_id=op["op_id"], phase="REPAIR", reason="OTHER_STATE", digest=member["digest"])
                        and re.fullmatch(r"[0-9a-f]{64}", member["digest"]), "Normalization changed a frozen member")
            require(sorted(intent["observations"], key=lambda o: o["endpoint"]["identity"]) ==
                    sorted(observations(projected, bi), key=lambda o: o["endpoint"]["identity"]),
                    "Normalization changed its complete prescribed neutral choice")
        else:
            generation = root["writer_generation"]
            require(generation > 0 and root["tombstones"] == [
                dict(t, released=1, release_generation=generation) for t in chain.receipts(authority)]
                and len(root["rocket_authorities"]) == 1, "Normalization changed complete immutable receipts")
            h = root["rocket_authorities"][0]
            decoded = dict(h, **{k: canonical_uuid(h[k]) for k in ("l", "o", "a", "p")})
            require(decoded == dict(chain.head(authority, generation), e=0), "Normalization changed epoch-zero READY head")
            if "completed" in identity:
                require(root == identity["completed"], "Second restart repeated normalization or changed authority")
        observed = identity or dict(resources=resources, authority=root)
        if restart: observed = dict(observed, completed=root)
        return dict(identity=observed, raw_total=8000,
                    per_fluid={chain.legacy.HYDROGEN: 4000, chain.legacy.OXYGEN: 4000} if bi else {chain.legacy.FUEL: 8000},
                    available_total=None, loss=None, state="RELEASED" if restart else "NEUTRAL_CHOSEN")
    except (KeyError, TypeError, IndexError, ValueError) as failure:
        raise SmokeError(f"Malformed S6 normalization observation: {failure}") from failure
