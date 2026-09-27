"""Bounded, copy-only inputs and storage oracles for the v1.4 migration exercise."""
from __future__ import annotations

import copy
import hashlib
import json
import os
import re
import shutil
import zipfile
from pathlib import Path

if __package__:
    from . import run_v140_planetary_worlds_smoke as worlds
    from . import run_v130_satellite_payload_smoke as legacy
else:
    import run_v140_planetary_worlds_smoke as worlds
    import run_v130_satellite_payload_smoke as legacy

support, server, schema = worlds.support, worlds.server, worlds.schema
require, write_json, HOST = worlds.require, worlds.write_json, worlds.HOST
OLD_HOST_SHA = "d1f9f564ed1dcd1df7ac0648b4091a07dfa9a02f63b36b827f36aad591224475"
PACK = Path("world/datapacks/migration_probe")
REMOVED = {HOST + ":mars", HOST + ":venus"}


def inventory(root: Path) -> list[dict]:
    support.safe_path(root)
    require(root.is_dir(), "World directory is missing")
    rows, total, directories = [], 0, 0
    for directory, children, files in os.walk(root, followlinks=False):
        directories += 1
        require(directories <= 512, "World directory budget exceeded")
        for name in children + files:
            support.safe_path(Path(directory) / name)
        for name in sorted(files):
            path = Path(directory) / name
            raw = support.regular(path, 16 * 1024**2)
            total += len(raw)
            rows.append({"path": path.relative_to(root).as_posix(), "bytes": len(raw),
                         "sha256": hashlib.sha256(raw).hexdigest()})
            require(len(rows) <= 512 and total <= 256 * 1024**2, "World fixture exceeds its copy budget")
    require(rows, "World fixture is empty")
    return sorted(rows, key=lambda row: row["path"])


def verify_history(world: Path, evidence: Path, artifacts: list[dict]) -> dict:
    summary = json.loads(support.regular(evidence / "summary.json", 1024**2))
    require(summary["result"] == "PASS" and len(summary["cycles"]) == 3, "Historical run is incomplete")
    require(artifacts[0]["sha256"] == OLD_HOST_SHA, "Not the pinned v1.3 handoff host")
    require([entry["sha256"] for entry in summary["artifacts"]] == [entry["sha256"] for entry in artifacts],
            "Historical host/consumer identities differ")
    entries = support.regular(evidence / "SHA256SUMS", 65536).decode("ascii").splitlines()
    require(entries and len(entries) == len(set(entries)), "Empty/duplicate historical manifest")
    for line in entries:
        digest, relative = line.split("  ", 1)
        path = support.safe_path(evidence / relative)
        require(path.is_relative_to(evidence) and server.digest_file(path) == digest,
                "Historical evidence checksum differs: " + relative)
    final = summary["cycles"][-1]
    require(final["phase"] == "reinstalled" and all(c["result"] == "PASS" and c["exit_code"] == 0
            for c in summary["cycles"]), "Historical process receipts differ")
    captures = final["disk_evidence"]
    require({legacy.SAVED, "world/level.dat", "world/region/r.0.0.mca", legacy.DEFINITION_FILE,
             legacy.PACK + "/pack.mcmeta"} == set(captures), "Historical capture scope differs")
    for relative, record in captures.items():
        local = Path(relative).relative_to("world")
        raw = support.regular(world / local, 16 * 1024**2)
        require(len(raw) == record["bytes"] and hashlib.sha256(raw).hexdigest() == record["sha256"],
                "Retained original world differs from the historical capture: " + relative)
    require(not (world / Path(schema.BINDINGS).relative_to("world")).exists(), "Original already has v1.4 bindings")
    return {"manifest_entries": len(entries), "matched_captures": captures,
            "summary_sha256": server.digest_file(evidence / "summary.json"), "source": str(world)}


def copy_world(source: Path, destination: Path) -> list[dict]:
    source, destination = support.safe_path(source), support.safe_path(destination)
    require(not destination.exists() and source != destination and source not in destination.parents
            and destination not in source.parents, "World copy must be new and disjoint")
    before = inventory(source)
    shutil.copytree(source, destination)
    require(inventory(destination) == before == inventory(source), "Copy changed world bytes")
    return before


def install_legacy_pack(root: Path, artifact: Path) -> None:
    pack = root / PACK
    require(not pack.exists(), "Migration probe already exists")
    directory = pack / "data" / HOST / "celestial_bodies"
    directory.mkdir(parents=True)
    write_json(pack / "pack.mcmeta", {"pack": {"pack_format": 15, "description": "Migration fixture"}})
    with zipfile.ZipFile(artifact) as jar:
        for body in ("earth", "moon", "space"):
            raw = jar.read(f"data/{HOST}/celestial_bodies/{body}.json")
            require(json.loads(raw).get("schema_version", 1) == 1, "Baseline definition is not schema 1")
            (directory / (body + ".json")).write_bytes(raw)


def removal_resources(artifact: Path, removed: bool) -> tuple[dict, dict, int]:
    with zipfile.ZipFile(artifact) as jar:
        resources = {name: json.loads(jar.read(name)) for name in jar.namelist()
                     if name.startswith(f"data/{HOST}/") and name.endswith(".json")
                     and name.split("/")[2] in ("celestial_bodies", "travel_routes")}
        satellite = json.loads(jar.read(f"data/{HOST}/satellite_definitions/data_satellite.json"))
    hidden = []
    for name, value in resources.items():
        if "/celestial_bodies/" in name and value["id"] not in REMOVED:
            require(value.get("parent") not in REMOVED, "Removal leaves a dependent child")
        if ("/celestial_bodies/" in name and value["id"] in REMOVED
                or "/travel_routes/" in name and any(value[end]["body_id"] in REMOVED for end in ("from", "to"))):
            hidden.append(name.removeprefix(f"data/{HOST}/"))
    require(sum(name.startswith("celestial_bodies/") for name in hidden) == 2, "Removal body inputs differ")
    metadata = {"pack": {"pack_format": 15, "description": "Migration fixture"}}
    if removed:
        metadata["filter"] = {"block": [{"namespace": re.escape(HOST), "path": re.escape(name)} for name in sorted(hidden)]}
        satellite["allowed_targets"] = [target for target in satellite["allowed_targets"] if target not in REMOVED]
    routes = sum("/travel_routes/" in name for name in resources)
    return metadata, satellite, routes - (sum(name.startswith("travel_routes/") for name in hidden) if removed else 0)


def set_removal(root: Path, artifact: Path, removed: bool) -> int:
    metadata, satellite, count = removal_resources(artifact, removed)
    pack = root / PACK
    directory = pack / "data" / HOST / "satellite_definitions"
    directory.mkdir(parents=True, exist_ok=True)
    write_json(pack / "pack.mcmeta", metadata)
    write_json(directory / "data_satellite.json", satellite)
    return count


def capture_state(root: Path, output: Path, *, closed_world=True) -> dict:
    output.mkdir(parents=True, exist_ok=True)
    state = {}
    for name in ("satellite_missions", "celestial", "stations"):
        relative = Path("world/data") / (HOST + "_" + name + ".dat")
        if not (root / relative).exists():
            require(name == "stations", "Required historical authority missing")
            state[name] = None
            continue
        raw = support.regular(root / relative, 4 * 1024**2)
        (output / relative.name).write_bytes(raw)
        state[name] = schema.read_nbt(output / relative.name)["data"]
    if closed_world:
        # Region allocation padding is finalized at close, not save-all flush.
        relative = Path("world/region/r.0.0.mca")
        raw = support.regular(root / relative, worlds.region.MAX_REGION_BYTES)
        (output / "terminal-region.mca").write_bytes(raw)
        chunk = worlds.region._decode_chunk(worlds.region._region_chunk(raw, 16, 16), 16, 16)
        terminals = [entry for entry in chunk.get("block_entities", [])
                     if tuple(entry.get(axis) for axis in ("x", "y", "z")) == legacy.POSITION]
        require(len(terminals) == 1 and terminals[0]["id"] == HOST + ":satellite_terminal", "Historical terminal missing")
        state["terminal"] = terminals[0]["SatelliteTerminal"]
    state["bindings"] = None
    if (root / schema.BINDINGS).exists():
        state["bindings"] = json.loads(schema.capture_bindings(root, output / "bindings.json"))
    journal = root / "world/data" / worlds.native.TRANSFER_JOURNAL
    if journal.exists():
        (output / "transfers.dat").write_bytes(support.regular(journal, 4 * 1024**2))
        worlds.validate_transfer(worlds.native.read_transfers(output / "transfers.dat"), [], [], {})
    write_json(output / "decoded.json", state)
    return state


def by_id(values: list[dict], field: str) -> dict:
    result = {support.uuid_from_nbt(value[field]): value for value in values}
    require(len(result) == len(values), "Duplicate persisted identity")
    return result


def preserve_history(old: dict, current: dict, *, closed_world=True) -> None:
    if closed_world:
        require(old["terminal"] == current["terminal"], "Upgrade changed historical native terminal")
    for name in ("satellite_missions", "celestial"):
        require(old[name]["schema_version"] == current[name]["schema_version"] == 2, "Saved root schema changed")
    for field, identity in (("missions", "mission_id"), ("satellites", "satellite_id"), ("research_accounts", "owner_id")):
        before, after = (by_id(state["satellite_missions"][field], identity) for state in (old, current))
        require(all(after.get(key) == value for key, value in before.items()), "Historical " + field + " changed")
    require(all(value in current["celestial"]["bodies"] for value in old["celestial"]["bodies"]),
            "Historical discovery/visit changed")


def same_authority(before: dict, after: dict, *, closed_world=True) -> None:
    def without_clock(state):
        result = copy.deepcopy(state)
        if not closed_world:
            result.pop("terminal", None)
        clock = result["satellite_missions"].pop("clock")
        require(set(clock) == {"logical_game_time", "last_observed_game_time"}
                and all(type(value) is int and value >= 0 for value in clock.values()), "Invalid mission clock")
        return result
    require(without_clock(before) == without_clock(after), "Restart/replay changed authority beyond the mission clock")


def check_claims(old: dict, state: dict, missions: list[dict], owner: str, pending: bool, *, closed_world=True) -> None:
    preserve_history(old, state, closed_world=closed_world)
    registry = state["satellite_missions"]
    stored, satellites = by_id(registry["missions"], "mission_id"), by_id(registry["satellites"], "satellite_id")
    require(set(stored) == set(by_id(old["satellite_missions"]["missions"], "mission_id")) | {m["mission"] for m in missions}
            and set(satellites) == set(by_id(old["satellite_missions"]["satellites"], "satellite_id")) | {m["satellite"] for m in missions},
            "Upgrade mission/satellite set differs")
    accounts = by_id(registry["research_accounts"], "owner_id")
    require(set(accounts) == set(by_id(old["satellite_missions"]["research_accounts"], "owner_id")) | {owner}, "Account set differs")
    account = accounts[owner]
    require(account["schema_version"] == 1 and (account["balance"], account["lifetime_earned"], account["lifetime_spent"])
            == (40, 240, 200), "Pending/restored claim duplicated or lost research")
    bodies = state["celestial"]["bodies"]
    expected = {entry["id"] for entry in old["celestial"]["bodies"]} | {HOST + ":mars"}
    if not pending:
        expected.add(HOST + ":venus")
    require(len(bodies) == len(expected) and {entry["id"] for entry in bodies} == expected, "Discovery set differs")
    for mission in missions:
        record, satellite = stored[mission["mission"]], satellites[mission["satellite"]]
        waiting = pending and mission["body"] == HOST + ":venus"
        require(record["schema_version"] == satellite["schema_version"] == 1
                and support.uuid_from_nbt(record["owner_id"]) == support.uuid_from_nbt(satellite["owner_id"]) == owner
                and support.uuid_from_nbt(record["satellite_id"]) == mission["satellite"]
                and record["definition_id"] == satellite["definition_id"] == HOST + ":data_satellite"
                and record["target_body_id"] == mission["body"] and record["completes_at"] == mission["deadline"]
                and record["completes_at"] - record["started_at"] == 200
                and (record["research_yield"], record["discovery_cost"], record["discovery_required"]) == (120, 100, 1)
                and record["status"] == ("claim_pending_discovery" if waiting else "claimed"), "Captured mission changed")
        require(satellite["status"] == "operational" and (support.uuid_from_nbt(satellite["current_mission_id"])
                == mission["mission"] if waiting else "current_mission_id" not in satellite), "Current mission binding differs")


def check_completion(pending: dict, restored: dict, mission: dict, *, closed_world=True) -> None:
    expected = copy.deepcopy(pending)
    record = by_id(expected["satellite_missions"]["missions"], "mission_id")[mission["mission"]]
    record["status"] = "claimed"
    by_id(expected["satellite_missions"]["satellites"], "satellite_id")[mission["satellite"]].pop("current_mission_id")
    added = [body for body in restored["celestial"]["bodies"] if body["id"] == mission["body"]]
    require(len(added) == 1 and set(added[0]) == {"id", "discovered_at"} and added[0]["discovered_at"] >= 0,
            "Automatic discovery fabricated visit data")
    expected["celestial"]["bodies"].extend(added)
    expected["celestial"]["bodies"].sort(key=lambda body: body["id"])
    same_authority(expected, restored, closed_world=closed_world)
