"""Read-only bounded two-store observations for finite native discovery cuts."""
from __future__ import annotations

import copy
import hashlib
import json
import re
from pathlib import Path

if __package__:
    from . import v140_migration_fixture as migration
else:
    import v140_migration_fixture as migration

worlds, support, server, schema = migration.worlds, migration.support, migration.server, migration.schema
require, write_json, HOST = migration.require, migration.write_json, migration.HOST
STORES = ("satellite_missions", "celestial")
TABLES = {"missions": "mission_id", "satellites": "satellite_id", "research_accounts": "owner_id"}


def verify_source(evidence: Path, artifacts: list[dict]) -> dict:
    """Bind the copied old world to the completed MIG-02 evidence, without editing it."""
    summary = json.loads(support.regular(evidence / "summary.json", 1024**2))
    require(summary["result"] == "PASS" and summary["source_unchanged"] is True, "Source run is incomplete")
    require([summary["artifacts"][index]["sha256"] for index in (2, 1)]
            == [entry["sha256"] for entry in artifacts], "Source host/consumer identities differ")
    entries = support.regular(evidence / "SHA256SUMS", 256 * 1024).decode("ascii").splitlines()
    require(0 < len(entries) <= 1024, "Source manifest count exceeds bounds")
    paths = set()
    for line in entries:
        digest, relative = line.split("  ", 1)
        path = support.safe_path(evidence / relative)
        require(re.fullmatch(r"[0-9a-f]{64}", digest) and path.is_relative_to(evidence)
                and path not in paths, "Invalid or duplicate source manifest entry")
        paths.add(path)
        require(hashlib.sha256(support.regular(path, 32 * 1024**2)).hexdigest() == digest,
                "Source evidence checksum differs: " + relative)
    for relative in ("summary.json", "source-world-files.json"):
        require(evidence / relative in paths, "Source manifest omits provenance")
    original = migration.inventory(evidence / "original-world")
    require(original == json.loads(support.regular(evidence / "source-world-files.json", 1024**2)),
            "Original world differs from its recorded inventory")
    require(all(evidence / "original-world" / row["path"] in paths for row in original),
            "Source manifest omits original world bytes")
    return {"manifest_entries": len(entries), "source_files": original,
            "summary_sha256": server.digest_file(evidence / "summary.json"), "evidence": str(evidence)}


def capture(world: Path, output: Path) -> dict:
    """Copy only authorities and existing scratch; never read or modify live regions."""
    output.mkdir(parents=True)
    result = {"authority": {}, "pending": {}, "files": []}
    for store in STORES:
        for scratch in (False, True):
            name = HOST + "_" + store + ".dat" + (".arce-pending" if scratch else "")
            path = support.safe_path(world / "data" / name)
            if not path.exists():
                require(scratch, "Missing authority: " + store)
                continue
            raw = support.regular(path, 4 * 1024**2)
            target = output / name
            target.write_bytes(raw)
            result["pending" if scratch else "authority"][store] = schema.read_nbt(target)["data"]
            result["files"].append({"path": name, "bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()})
    write_json(output / "decoded.json", result)
    return result


def normalized(state: dict) -> dict:
    result = copy.deepcopy(state)
    require(set(result) == set(STORES), "Unexpected authority set")
    require(all(result[name]["schema_version"] == 2 for name in STORES), "Unexpected root schema")
    registry = result["satellite_missions"]
    clock = registry.pop("clock")
    require(set(clock) == {"logical_game_time", "last_observed_game_time"}
            and all(type(value) is int and value >= 0 for value in clock.values()), "Invalid mission clock")
    for name, field in TABLES.items():
        registry[name] = migration.by_id(registry[name], field)
    bodies = result["celestial"]["bodies"]
    result["celestial"]["bodies"] = {value["id"]: value for value in bodies}
    require(len(result["celestial"]["bodies"]) == len(bodies), "Duplicate celestial identity")
    return result


def same_authority(before: dict, after: dict) -> None:
    require(normalized(before) == normalized(after), "Authority changed beyond the two scheduler clock fields")


def check_prepared(old: dict, ready: dict, mission: dict) -> None:
    before, actual = normalized(old), normalized(ready)
    registry = actual["satellite_missions"]
    require(mission["owner"] not in before["satellite_missions"]["research_accounts"], "Owner is not fresh")
    require(mission["body"] not in before["celestial"]["bodies"], "Target is already discovered")
    stored = registry["missions"][mission["mission"]]
    start, ready_at = stored["started_at"], stored["ready_at"]
    require(type(start) is int and start >= 0 and type(ready_at) is int and ready_at >= start + 200,
            "Prepared mission time differs")
    require(stored == {"schema_version": 1, "mission_id": stored["mission_id"],
            "satellite_id": stored["satellite_id"], "owner_id": stored["owner_id"],
            "definition_id": HOST + ":data_satellite", "target_body_id": mission["body"],
            "started_at": start, "completes_at": start + 200, "ready_at": ready_at,
            "research_yield": 120, "discovery_cost": 100, "discovery_required": 1, "status": "ready"}
            and stored["completes_at"] == mission["deadline"]
            and support.uuid_from_nbt(stored["satellite_id"]) == mission["satellite"]
            and support.uuid_from_nbt(stored["owner_id"]) == mission["owner"], "Prepared mission snapshot differs")
    satellite = registry["satellites"][mission["satellite"]]
    require(satellite == {"schema_version": 1, "satellite_id": stored["satellite_id"],
            "owner_id": stored["owner_id"], "definition_id": stored["definition_id"],
            "launched_at": start, "status": "operational", "current_mission_id": stored["mission_id"]},
            "Prepared satellite binding differs")
    account = registry["research_accounts"][mission["owner"]]
    require(account == {"schema_version": 1, "owner_id": stored["owner_id"],
            "balance": 0, "lifetime_earned": 0, "lifetime_spent": 0}, "Prepared account is not empty")
    for table, identity, record in (("missions", mission["mission"], stored),
            ("satellites", mission["satellite"], satellite), ("research_accounts", mission["owner"], account)):
        require(identity not in before["satellite_missions"][table], "Prepared identity collides with history")
        before["satellite_missions"][table][identity] = record
    require(before == actual, "Preparation altered unrelated authority")


def check_phase(ready: dict, actual: dict, mission: dict, status: str, discovered: bool,
                *, resolved_at=None, discovered_at=None) -> dict:
    require(status in ("ready", "claim_pending_discovery", "claimed"), "Invalid observation phase")
    expected, observed = normalized(ready), normalized(actual)
    registry, current = expected["satellite_missions"], observed["satellite_missions"]
    stored, observed_mission = registry["missions"][mission["mission"]], current["missions"][mission["mission"]]
    receipt, discovery = None, None
    if status != "ready":
        receipt = observed_mission["resolved_at"]
        require(type(receipt) is int and receipt >= stored["ready_at"]
                and (resolved_at is None or resolved_at == receipt), "Claim receipt timestamp changed")
        stored.update(status=status, resolved_at=receipt)
        registry["research_accounts"][mission["owner"]].update(balance=20, lifetime_earned=120, lifetime_spent=100)
        if status == "claimed":
            registry["satellites"][mission["satellite"]].pop("current_mission_id")
    if discovered:
        discovery = observed["celestial"]["bodies"][mission["body"]]["discovered_at"]
        require(type(discovery) is int and discovery >= 0
                and (discovered_at is None or discovered_at == discovery), "Discovery timestamp changed")
        expected["celestial"]["bodies"][mission["body"]] = {"id": mission["body"], "discovered_at": discovery}
    require(expected == observed, "Claim changed authority outside its permitted transition")
    return {"resolved_at": receipt, "discovered_at": discovery}


def check_cut(ready: dict, capture: dict, mission: dict, boundary: int) -> dict:
    require(boundary in (1, 2, 3, 4), "Invalid cut boundary")
    phase = ("ready", "claim_pending_discovery", "claim_pending_discovery", "claimed")[boundary - 1]
    times = check_phase(ready, capture["authority"], mission, phase, boundary >= 3)
    pending = capture["pending"]
    store = "celestial" if boundary == 2 else "satellite_missions"
    require(set(pending) == ({store} if boundary < 4 else set()), "Unexpected scratch set at cut")
    if pending:
        candidate = dict(capture["authority"], **pending)
        check_phase(ready, candidate, mission, "claimed" if boundary == 3 else "claim_pending_discovery",
                    boundary >= 2, **times)
    return times


def check_automatic(ready: dict, capture: dict, mission: dict, boundary: int, killed: dict) -> None:
    times = check_cut(ready, killed, mission, boundary)
    check_phase(ready, capture["authority"], mission,
                "ready" if boundary == 1 else "claimed", boundary != 1, **times)
    if boundary == 1 and capture["pending"]:
        # An untouched scratch is not authority. A later store write may remove it;
        # startup need not rewrite a clean READY record merely to delete scratch.
        require(capture["pending"] == killed["pending"], "Unpaid restart changed its ignored scratch")
        pending_files = lambda value: [row for row in value["files"] if row["path"].endswith(".arce-pending")]
        require(pending_files(capture) == pending_files(killed), "Ignored scratch bytes changed")
    else:
        require(not capture["pending"], "Paid recovery left scratch data")
