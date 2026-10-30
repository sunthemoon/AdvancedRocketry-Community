"""S6 absent-loader decisions; reconstructed captures never count as native fuel."""
import copy
import re

import loader_s6_chain as chain
from loader_s6_oracle import canonical_uuid, normalize_ids, pending_receipt
from run_dedicated_server_smoke import SmokeError
from run_v180_rocket_settlement_intent import ACTOR

CASES = tuple("LF_S6_CHAIN_ABANDON_" + side + suffix for suffix in ("", "_BI")
              for side in ("BEFORE", "REPLACED", "ACK"))
require = chain.legacy.require


def inspect(world):
    try:
        disk = chain.legacy.inspect(world)
        terrain = chain.legacy.record(world / "region/r.0.0.mca", 132)
        disk["absent_slot"] = [entry for entry in terrain["block_entities"]
                               if [entry.get(k) for k in ("x", "y", "z")] == [67, 100, 64]]
        return dict(read_status="KNOWN", native=disk)
    except (OSError, ValueError, KeyError, TypeError, SmokeError) as failure:
        return dict(read_status="UNKNOWN", native=None, error=f"{type(failure).__name__}: {failure}")


def reconstruct(disk, released, authority):
    """Rebuild only the fixed second-loader capture from its record and codec shape.

    The actual second native slot must be empty. This private projection is
    used for signature verification, never for observed quantities or availability.
    """
    require(disk["absent_slot"] == [] and len(disk["loaders"]) == 1, "Abandon has a returning or replaced source")
    records = authority["records"]
    require(len(records) == 2 and records[1]["seq"] == 2, "Abandon lost its original connected member")
    result = copy.deepcopy(disk); op = records[1]; ghost = copy.deepcopy(disk["loaders"][0])
    ghost["x"] = 67
    root = ghost["arce_fluid_fuel_loader"]
    root["device"] = op["source"]["identity"]
    root["tank"] = dict(FluidName=op["payload"]["FluidName"], Amount=4000)
    root["resource_escrow"] = chain.wrapper(disk["ledger"]["ledger_id"], [] if released else [op], 1, "SOURCE")
    result["loaders"].append(ghost)
    return result


def observations(projected):
    result = chain.expected_observations(projected)
    missing = result[1]
    missing.update(missing=1, bound=0, quantity=0)
    del missing["state"]
    return result


def receipts(root):
    result = []
    for op in root["records"]:
        chosen = [o for o in op["settlement"]["observations"]
                  if o["endpoint"]["identity"] in (op["source"]["identity"], op["destination"]["identity"])]
        present = [o for o in chosen if not o["missing"]]
        member = dict(op, settlement=dict(op["settlement"], observations=present))
        receipt = pending_receipt(member)
        receipt.update(anchor_op=min(m["op_id"] for m in root["records"]),
                       before_choice=[{k: v for k, v in o.items() if k != "normalized_wrapper"} for o in chosen],
                       missing=[o["endpoint"] for o in chosen if o["missing"]])
        result.append(receipt)
    return result


def check(image, restart, identity=None, cut=CASES[0]):
    require(cut in CASES and restart in (0, 1, 2) and image.get("read_status") == "KNOWN"
            and isinstance(image.get("native"), dict), "Abandon UNKNOWN is not zero loss")
    try:
        disk = normalize_ids(image["native"]); identity = normalize_ids(identity); root = disk["ledger"]
        before = "_BEFORE" in cut; released = bool(restart and not before); bi = cut.endswith("_BI")
        require(not released or identity is not None, "Abandon release has no initial frozen authority")
        authority = identity["authority"] if released else root
        projected = reconstruct(disk, released, authority)
        resources = chain.check_resources(projected, bi, released)
        # Do not expose the reconstructed absent loader as an observed native identity.
        resources["loaders"] = resources["loaders"][:1]
        resources["missing_endpoint"] = authority["records"][1]["source"]
        require(identity is None or resources == identity["resources"], "Abandon changed native identity/snapshot/vector")
        if not released:
            require(not root["tombstones"] and not root["rocket_authorities"], "Abandon released before its selected boundary")
            records = root["records"]
            require(all(op["phase"] == ("REPAIR" if before else "SETTLING_ABANDON") and op["reason"] == "OTHER_STATE"
                        for op in records), "Abandon changed its held/frozen phase")
            if before:
                require(all(not op.get("settlement") for op in records), "Pre-O1 abandon invented a decision")
            else:
                intent = records[0]["settlement"]
                require(records[1]["settlement"] == intent and intent["schema_version"] == 2 and intent["choice"] == "ABANDON"
                    and intent["ledger_id"] == root["ledger_id"] and intent["actor"] == canonical_uuid(ACTOR) and intent["actor_name"] == "Server"
                    and re.fullmatch(r"[0-9a-f]{64}", intent["digest"]) and intent["preview_tick"] >= 0
                    and intent["confirmed_tick"] - intent["preview_tick"] >= 200
                    and set(intent) == {"schema_version", "choice", "ledger_id", "actor", "actor_name", "digest",
                                        "confirmed_tick", "preview_tick", "members", "observations"}, "Abandon changed its frozen decision or absence interval")
                require(len(intent["members"]) == 2, "Abandon omitted a member")
                for member, op in zip(intent["members"], records):
                    require(member == dict(op_id=op["op_id"], phase="REPAIR", reason="OTHER_STATE", digest=member["digest"])
                            and re.fullmatch(r"[0-9a-f]{64}", member["digest"]), "Abandon changed a frozen member")
                require(sorted(intent["observations"], key=lambda o: o["endpoint"]["identity"]) ==
                        sorted(observations(projected), key=lambda o: o["endpoint"]["identity"]), "Abandon changed a frozen survivor/absence")
            if identity:
                require(root == identity["authority"], "Pre-O1 abandon changed held authority")
        else:
            require(len(root["tombstones"]) == 2 and len(root["rocket_authorities"]) == 1, "Abandon lost receipts or READY head")
            generation = root["writer_generation"]
            require(generation > 0 and root["tombstones"] == [
                dict(t, released=1, release_generation=generation) for t in receipts(authority)], "Abandon changed complete immutable receipts")
            h = root["rocket_authorities"][0]
            decoded = dict(h, **{k: canonical_uuid(h[k]) for k in ("l", "o", "a", "p")})
            require(decoded == chain.head(authority, generation), "Abandon changed survivor READY authority")
            if "completed" in identity:
                require(root == identity["completed"], "Second restart changed abandon authority")
        observed = identity or dict(resources=resources, authority=root)
        if released: observed = dict(observed, completed=root)
        return dict(identity=observed, raw_total=6000,
                    per_fluid={chain.legacy.HYDROGEN: 5000, chain.legacy.OXYGEN: 1000} if bi else {chain.legacy.FUEL: 6000},
                    missing_amount=None, available_total=None, loss=None,
                    state="RELEASED" if released else "PRE_O1" if before else "FROZEN_O1")
    except (KeyError, TypeError, IndexError, ValueError) as failure:
        raise SmokeError(f"Malformed S6 abandon observation: {failure}") from failure
