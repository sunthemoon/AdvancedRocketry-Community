#!/usr/bin/env python3
"""W346 (records/W346-DESIGN.md): W27A B6.5's native crash state at the source-save boundary, beside the forced-stop runner.

The production sequence after D7 discards the source S once SOURCE_REMOVED is acknowledged. An ordinary save of the
source Level can store that absence before the destination Level stores the new rocket. This case reaches that state
with the gated save-boundary control and the existing ``B16_GUARD`` cut:

* the first start launches the standard bay fixture (a data bay and a research-locked solar bay), pauses D7's guard
  after D6 has proved both ends, holds the Moon's saves, releases the guard, waits until the transfer journal has
  acknowledged COMMITTED, runs the autosave's own ``saveEverything(true, false, false)`` (which skips the held Moon) and
  hard-kills the JVM;
* the stopped world must hold no rocket of L at either end, the committed T' with its consumed snapshots, the registered
  data satellite and no receipt;
* restart 1 must rebuild the destination from T' (REBUILD_DESTINATION) with the same consumed operations, no package
  back and no receipt; restart 2 must keep that rebuilt destination (KEEP_DESTINATION) without forcing a source back.

The candidate pin (``--jar-sha256``, ``--candidate-commit`` and the installed-JAR checks), the session and the fixture
helpers are the forced-stop runner's. At the kill the oracle reads nothing that the Moon's held saves stopped except
its entity regions, whose absence of L is the state under test. After clean restart stops, terrain saves resume and
the inventory scan includes the Moon's terrain too.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import time

import bay_integrated_evidence as evidence_tools
import run_dedicated_server_smoke as smoke
import run_v180_bay_forced_stop as forced
from run_v180_landed_disassembly_cuts import chunk_record, nbt_file, uuid_of

MOON = "advancedrocketrycommunity:moon"
OVERWORLD = "minecraft:overworld"
CONTROL = "arce bay release-test save-boundary"
TRANSFERS_FILE = "advancedrocketrycommunity_rocket_transfers.dat"
TRANSFERS_LIMIT = 4 * 1024 * 1024
BAY_FIELDS = {"schema_version", "owner", "package", "consumed_op"}
BAY_ADAPTER = "advancedrocketrycommunity:satellite_bay_v1"
OVERWORLD_ENTITIES = Path("entities")
MAX_INVENTORY_FILES = 256
MAX_INVENTORY_REGION_BYTES = 128 * 1024 * 1024
# The overworld's queued region writes of the autosave pass finish on the IO worker; the kill waits this long for them,
# far inside the destination column's flight-ticket bound (400 ticks after the landing; W346-DESIGN section 3).
WRITE_SETTLE_SECONDS = 2.0
POLL_SECONDS = 0.25

HELD = re.compile(r"ARCE_RELEASE_SAVE_BOUNDARY_HELD level=(\S+) noSave=(true|false)")
RESTORED = re.compile(r"ARCE_RELEASE_SAVE_BOUNDARY_RESTORED level=(\S+) noSave=(true|false)")
REFUSED = re.compile(r"ARCE_RELEASE_SAVE_BOUNDARY_REFUSED action=(\w+) level=(\S+) reason=(\w+)")
CONTROL_REPLY = re.compile("|".join(pattern.pattern for pattern in (HELD, RESTORED, REFUSED)))
WAIT = re.compile(r"ARCE_RELEASE_SAVE_BOUNDARY_WAIT logical=(\S+) transfer=(\S+) phase=(\w+) acknowledged=(true|false) "
                  r"destination_held=(true|false)")
AUTOSAVE = re.compile(r"ARCE_RELEASE_SAVE_BOUNDARY_AUTOSAVE logical=(\S+) transfer=(\S+) phase=(\w+) acknowledged=(true|false) "
                      r"destination_level=(\S+) destination_entity=(\S+) destination_present=(true|false) "
                      r"destination_chunk_loaded=(true|false) source_count=(\d+) result=(true|false) skipped=\[([^\]]*)\] "
                      r"saved=\[([^\]]*)\]")
AUTOSAVE_REPLY = re.compile(AUTOSAVE.pattern + "|" + WAIT.pattern)
RECOVERY = re.compile(r"ARCE_TRANSFER_RECOVERY transfer=(\S+) phase=(\w+) source_count=(\d+) destination_count=(\d+) "
                      r"action=(\w+) status=(\w+)")

W346_CASES = {
    # B6.5: the destination absent after D7 because the source Level saved S's discard first (W338 finding 3, W342).
    "W346_B6_SAVE_BOUNDARY": dict(rows=("B6.5",), fixture="standard", cut="B16_GUARD", hold=MOON,
                                  recovery=(("COMMITTED", 0, 0, "REBUILD_DESTINATION", "RECOVERED"),
                                            ("COMMITTED", 0, 1, "KEEP_DESTINATION", "RECOVERED")),
                                  settled="LANDED", owned=1),
}
W346_TRANCHES = {"W346_R": ["W346_B6_SAVE_BOUNDARY"]}


def control_reply(line: str) -> tuple[str, str, str]:
    """One control line as (kind, level, detail): HELD/RESTORED with noSave, or REFUSED_<action> with its reason."""
    if match := HELD.search(line):
        return "HELD", match[1], match[2]
    if match := RESTORED.search(line):
        return "RESTORED", match[1], match[2]
    if match := REFUSED.search(line):
        return "REFUSED_" + match[1].upper(), match[2], match[3]
    raise smoke.SmokeError(f"Not a save-boundary control line: {line!r}")


def parse_autosave(line: str) -> dict:
    match = AUTOSAVE.search(line)
    if match is None:
        raise smoke.SmokeError(f"Not an autosave report: {line!r}")
    keys = ("logical", "transfer", "phase", "acknowledged", "destination_level", "destination_entity", "destination_present",
            "destination_chunk_loaded", "source_count", "result", "skipped", "saved")
    report = dict(zip(keys, match.groups()))
    for key in ("acknowledged", "destination_present", "destination_chunk_loaded", "result"):
        report[key] = report[key] == "true"
    report["source_count"] = int(report["source_count"])
    report["skipped"] = [value for value in report["skipped"].split(",") if value]
    report["saved"] = [value for value in report["saved"].split(",") if value]
    return report


def check_autosave(report: dict, fixture: dict, held: str = MOON) -> None:
    """The autosave ran once T' was COMMITTED and acknowledged, with the destination only in memory in the held Level."""
    problems = []
    if report["logical"] != fixture["logical"] or report["transfer"] != fixture["transfer"]:
        problems.append("names another rocket or transfer")
    if report["phase"] != "COMMITTED" or not report["acknowledged"]:
        problems.append("ran before the journal acknowledged COMMITTED")
    if report["destination_level"] != held or report["destination_entity"] == "NONE":
        problems.append("has no destination in the held Level")
    if not report["destination_present"] or not report["destination_chunk_loaded"]:
        problems.append("ran without the destination in memory in an entity-loaded chunk")
    if report["source_count"] != 0:
        problems.append("ran with a loaded source")
    if not report["result"] or report["skipped"] != [held] or OVERWORLD not in report["saved"] or held in report["saved"]:
        problems.append("did not skip exactly the held Level and save the overworld")
    if problems:
        raise smoke.SmokeError(f"The autosave pass {'; '.join(problems)}: {report}")


def recovery_rows(lines: list[str], transfer: str) -> list[tuple]:
    """Every recovery line of one transfer, as (phase, source count, destination count, action, status)."""
    return [(match[2], int(match[3]), int(match[4]), match[5], match[6]) for line in lines
            if (match := RECOVERY.search(line)) and match[1] == transfer]


def bay_roots(snapshot: dict) -> list[dict]:
    """The satellite-bay roots of one snapshot, in block order: each bay's consumed operation and whether it holds a package."""
    roots = []
    for block in snapshot.get("relative_blocks", []):
        payload = block.get("block_entity") if isinstance(block, dict) else None
        if not isinstance(payload, dict) or payload.get("adapter") != BAY_ADAPTER:
            continue
        data = payload.get("data", {})
        if not isinstance(data, dict) or not set(data) <= BAY_FIELDS:
            raise smoke.SmokeError(f"Unexpected bay root fields: {sorted(data)}")
        roots.append(dict(consumed=uuid_of(data.get("consumed_op")), package="package" in data))
    return roots


def journal_record(world: Path, logical: str) -> dict | None:
    """The transfer journal file's record for L in a stopped world, or None; refused when L has several records."""
    path = world / "data" / TRANSFERS_FILE
    if not path.is_file():
        return None
    root = nbt_file(path, TRANSFERS_LIMIT)
    data = root.get("data", root)
    records = [row for row in data.get("transfers", []) if uuid_of(row.get("logical_rocket_id")) == logical]
    if len(records) > 1:
        raise smoke.SmokeError(f"The journal file holds {len(records)} records for {logical}")
    if not records:
        return None
    row, destination = records[0], records[0]["destination_snapshot"]
    return dict(transfer=uuid_of(row["transfer_id"]), phase=row["phase"], checksum=row["checksum"],
                source_entity=uuid_of(row["source_entity_id"]), destination_entity=uuid_of(row.get("destination_entity_id")),
                destination_dimension=destination["source_dimension"], destination_origin=list(destination["source_origin"]),
                source_bays=bay_roots(row["source_snapshot"]), destination_bays=bay_roots(destination))


def regions(world: Path, directory: Path) -> list[Path]:
    folder = world / directory
    return sorted(folder.glob("r.*.*.mca")) if folder.is_dir() else []


def stored_entities(world: Path, directory: Path):
    """Each stored entity of one dimension's entity regions (bounded: every chunk slot of every region file)."""
    for region in regions(world, directory):
        parts = region.name.split(".")
        region_x, region_z = int(parts[1]), int(parts[2])
        for slot in range(1024):
            root = chunk_record(region, region_x * 32 + slot % 32, region_z * 32 + slot // 32)
            for entity in (root or {}).get("Entities", []):
                yield entity


def rockets_of(world: Path, logical: str, directory: Path) -> list[dict]:
    """Every saved rocket of L in one dimension, with its UUID and its bays."""
    found = []
    for entity in stored_entities(world, directory):
        data = entity.get("RocketEntityData", {})
        if uuid_of(data.get("assembly_transaction_id")) == logical:
            found.append(dict(uuid=uuid_of(entity.get("UUID")), bays=bay_roots(data.get("snapshot", {}))))
    return found


def item_entities(world: Path, directory: Path) -> int:
    return sum(1 for entity in stored_entities(world, directory) if entity.get("id") == "minecraft:item")


def registered_package_paths(value: object, satellite: str, path: str):
    """Find the registered identity anywhere in bounded decoded NBT, including nested/modded inventories.

    Do not select by item name, adapter or inventory key: packages keep their SatelliteIdentity even when nested in
    another item or a non-bay payload. A matching raw identity is refused even if its other fields are malformed.
    The deliberately unregistered solar identity is not the group's registered data identity.
    """
    if isinstance(value, dict):
        identity = value.get("SatelliteIdentity")
        if isinstance(identity, dict) and uuid_of(identity.get("satellite_id")) == satellite:
            yield path + ".SatelliteIdentity"
        for key, child in value.items():
            yield from registered_package_paths(child, satellite, f"{path}.{key}")
    elif isinstance(value, list):
        for index, child in enumerate(value):
            if isinstance(child, (dict, list)):
                yield from registered_package_paths(child, satellite, f"{path}[{index}]")


def inventory_evidence(world: Path, fixture: dict, *, held_moon: bool) -> dict:
    """Scan all saved players, journal snapshots, and entity/terrain records, not just fixture coordinates.

    At the kill only the held Moon's terrain is excluded: its unsaved contents cannot prove anything. The Moon's
    entity sections remain the intended absence evidence. After either clean restart stop all terrain is eligible.
    POI is never inventory evidence. Existing NBT readers bound bytes, tags, depth and each region's 1,024 slots;
    excess files, oversized/truncated regions and unreadable NBT fail instead of silently reducing coverage.
    """
    files = sorted((world / "playerdata").glob("*.dat")) + [world / "data" / TRANSFERS_FILE]
    region_files = sorted(path for path in world.rglob("r.*.*.mca") if path.parent.name in ("entities", "region"))
    if len(files) + len(region_files) > MAX_INVENTORY_FILES:
        raise smoke.SmokeError("Inventory scan exceeds its file budget")
    observed = dict(files=[], skipped_held_terrain=[], registered_packages=[])

    def check(root: object, location: str) -> None:
        found = next(registered_package_paths(root, fixture["data"], location), None)
        if found is not None:
            raise smoke.SmokeError(f"A registered package ({fixture['data']}) persists at {found}")

    for path in files + region_files:
        relative = path.relative_to(world)
        if any(parent.is_symlink() or getattr(parent, "is_junction", lambda: False)()
               for parent in (path, *path.parents) if parent.is_relative_to(world)):
            raise smoke.SmokeError(f"Linked inventory evidence: {relative}")
        if held_moon and relative.parent == forced.MOON_ENTITIES.parent / "region":
            observed["skipped_held_terrain"].append(relative.as_posix())
            continue
        row = dict(path=relative.as_posix())
        try:
            if path.suffix == ".dat":
                check(nbt_file(path, TRANSFERS_LIMIT), row["path"])
            else:
                if not 8192 <= path.stat().st_size <= MAX_INVENTORY_REGION_BYTES:
                    raise smoke.SmokeError(f"Truncated or oversized inventory region: {relative}")
                _, x, z, _ = path.name.split(".")
                row["stored_chunks"] = 0
                for slot in range(1024):
                    chunk_x, chunk_z = int(x) * 32 + slot % 32, int(z) * 32 + slot // 32
                    root = chunk_record(path, chunk_x, chunk_z)
                    if root is not None:
                        row["stored_chunks"] += 1
                        check(root, f"{row['path']}[{chunk_x},{chunk_z}]")
            row["sha256"] = evidence_tools.file_sha256(path)
        except (OSError, ValueError) as failure:
            raise smoke.SmokeError(f"Unreadable inventory evidence {relative}: {failure}") from failure
        observed["files"].append(row)
    return observed


def entity_files(world: Path) -> list[str]:
    return sorted(path.relative_to(world).as_posix() for path in world.rglob("*.mca") if "entities" in path.relative_to(world).parts)


def destination_chunk_stored(world: Path, origin: list[int]) -> bool:
    chunk_x, chunk_z = origin[0] >> 4, origin[2] >> 4
    return chunk_record(world / forced.MOON_ENTITIES / f"r.{chunk_x >> 5}.{chunk_z >> 5}.mca", chunk_x, chunk_z) is not None


def expected_bays(consumed: list) -> list[dict]:
    """The standard fixture's bays after D5: the data bay consumed with its op, the research-locked solar bay packaged."""
    return [dict(consumed=op, package=op is None) for op in consumed]


def cut_disk(world: Path, fixture: dict, first: dict) -> dict:
    """The stopped world after the kill: no rocket of L at either end, committed T' with consumed snapshots, the data
    satellite registered, no receipt (W346-DESIGN section 5)."""
    logical, data = fixture["logical"], world / "data"
    observed = dict(receipts_naming_l=[(row["satellite"], row["phase"]) for row in evidence_tools.bay_receipts(data / forced.RECEIPTS)
                                       if row["rocket"] == logical])
    if observed["receipts_naming_l"]:
        raise smoke.SmokeError(f"A receipt names L on disk at the kill: {observed['receipts_naming_l']}")
    observed["registry"] = evidence_tools.registry_satellites(data / forced.REGISTRY_FILE)
    if observed["registry"].count(fixture["data"]) != 1 or fixture["solar"] in observed["registry"]:
        raise smoke.SmokeError(f"The registry does not hold exactly the group's registered satellite: {observed['registry']}")
    record = journal_record(world, logical)
    if record is None:
        raise smoke.SmokeError("The journal file holds no record for L at the kill")
    observed["journal"] = record
    consumed = first["at_kill"]["consumed"]
    if (record["transfer"] != fixture["transfer"] or record["phase"] != "COMMITTED"
            or record["destination_entity"] != first["autosave"]["destination_entity"] or record["destination_dimension"] != MOON):
        raise smoke.SmokeError(f"The journal file does not hold committed T' with the reported destination: {record}")
    if record["source_bays"] != expected_bays(consumed) or record["destination_bays"] != expected_bays(consumed):
        raise smoke.SmokeError(f"T' on disk does not carry the consumed snapshots {consumed}: {record}")
    observed["source_rockets"] = rockets_of(world, logical, OVERWORLD_ENTITIES)
    observed["destination_rockets"] = rockets_of(world, logical, forced.MOON_ENTITIES)
    if observed["source_rockets"] or observed["destination_rockets"]:
        raise smoke.SmokeError(f"A rocket of L is on disk at the kill: source={observed['source_rockets']} "
                               f"destination={observed['destination_rockets']}")
    observed["destination_chunk_stored"] = destination_chunk_stored(world, record["destination_origin"])
    if not observed["destination_chunk_stored"]:
        raise smoke.SmokeError("The destination origin chunk has no stored entity record; the reader would prove nothing")
    observed["item_entities"] = item_entities(world, OVERWORLD_ENTITIES) + item_entities(world, forced.MOON_ENTITIES)
    if observed["item_entities"]:
        raise smoke.SmokeError(f"{observed['item_entities']} item entities are on disk at the kill")
    observed["inventories"] = inventory_evidence(world, fixture, held_moon=True)
    observed["entity_files"] = entity_files(world)
    return observed


def restart_disk(world: Path, fixture: dict, cut: dict, previous: list[str], restart: int) -> dict:
    """After a restart's stop: exactly the rebuilt destination of L with T''s UUID and consumed bays, T' unchanged, the
    same registry, no receipt, no item entity, no source, and no entity region file removed."""
    logical, data, journal = fixture["logical"], world / "data", cut["journal"]
    label = f"restart {restart}"
    observed = dict(receipts_naming_l=[(row["satellite"], row["phase"]) for row in evidence_tools.bay_receipts(data / forced.RECEIPTS)
                                       if row["rocket"] == logical])
    if observed["receipts_naming_l"]:
        raise smoke.SmokeError(f"{label}: a receipt names L on disk: {observed['receipts_naming_l']}")
    observed["registry"] = evidence_tools.registry_satellites(data / forced.REGISTRY_FILE)
    if sorted(observed["registry"]) != sorted(cut["registry"]):
        raise smoke.SmokeError(f"{label}: the registry changed: {cut['registry']} -> {observed['registry']}")
    record = journal_record(world, logical)
    if record != journal:
        raise smoke.SmokeError(f"{label}: T' changed in the journal file: {journal} -> {record}")
    observed["destination_rockets"] = rockets_of(world, logical, forced.MOON_ENTITIES)
    observed["source_rockets"] = rockets_of(world, logical, OVERWORLD_ENTITIES)
    if observed["destination_rockets"] != [dict(uuid=journal["destination_entity"], bays=journal["destination_bays"])]:
        raise smoke.SmokeError(f"{label}: expected exactly the rebuilt destination {journal['destination_entity']} with T''s "
                               f"bays: {observed['destination_rockets']}")
    if observed["source_rockets"]:
        raise smoke.SmokeError(f"{label}: a source of L was forced back: {observed['source_rockets']}")
    observed["item_entities"] = item_entities(world, OVERWORLD_ENTITIES) + item_entities(world, forced.MOON_ENTITIES)
    if observed["item_entities"]:
        raise smoke.SmokeError(f"{label}: {observed['item_entities']} item entities are on disk")
    observed["inventories"] = inventory_evidence(world, fixture, held_moon=False)
    observed["entity_files"] = entity_files(world)
    removed = sorted(set(previous) - set(observed["entity_files"]))
    if removed:
        raise smoke.SmokeError(f"{label}: entity region files were removed: {removed}")
    return observed


def control_check(run: forced.Session, level: str) -> list[str]:
    """Before the flight: hold, a refused second hold, restore, a refused second restore."""
    replies = []
    for action, expected in (("hold", ("HELD", "true")), ("hold", ("REFUSED_HOLD", "ALREADY_SAVE_OFF")),
                             ("restore", ("RESTORED", "false")), ("restore", ("REFUSED_RESTORE", "NOT_HELD"))):
        line = run.send(f"{CONTROL} {action} {level}", CONTROL_REPLY)
        kind, named, detail = control_reply(line)
        if (kind, detail) != expected or named != level:
            raise smoke.SmokeError(f"The control's {action} answered {kind} {named} {detail}, expected {expected}")
        replies.append(line.strip())
    return replies


def await_autosave(run: forced.Session, fixture: dict) -> tuple[dict, int]:
    """Poll the autosave until the journal has acknowledged COMMITTED; that one call then checks and saves in one tick."""
    deadline, waits = time.monotonic() + run.args.settle_timeout, 0
    while True:
        line = run.send(f"{CONTROL} autosave {fixture['logical']}", AUTOSAVE_REPLY)
        if AUTOSAVE.search(line):
            return parse_autosave(line), waits
        wait = WAIT.search(line)
        if wait[5] != "true":
            raise smoke.SmokeError(f"The destination Level is no longer held: {line.strip()}")
        waits += 1
        if time.monotonic() > deadline:
            raise smoke.SmokeError(f"The journal never acknowledged COMMITTED for L: {line.strip()}")
        time.sleep(POLL_SECONDS)


def check_at_kill(status: dict, fixture: dict, autosave: dict) -> None:
    """The destination in memory just before the kill: T' COMMITTED, no claim or receipt, the data bay consumed."""
    packages, expected = status["packages"].split("+"), fixture["packages"].split("+")
    if (status["claim"] != "NONE" or status["transfer"] != "COMMITTED" or status["rocket"] != autosave["destination_entity"]
            or status["receipts"] or status["owned"] != 1 or status["state"] not in ("DESCENT", "LANDED")
            or len(status["consumed"]) != 2 or status["consumed"][0] is None or status["consumed"][1] is not None
            or len(packages) != 2 or packages[0] == expected[0] or packages[1] != expected[1]):
        raise smoke.SmokeError(f"The destination is not the consumed committed rocket before the kill: {status}")


def first_start(run: forced.Session, case: str) -> tuple[dict, dict]:
    spec = W346_CASES[case]
    process = run.start(case, 0, {"bayCheckpoint": spec["cut"]})
    observed: dict = dict(control_check=control_check(run, spec["hold"]))
    fixture = forced.w331_launch(run, spec["fixture"])
    run.wait(re.compile(rf"ARCE_RELEASE_BAY_PAUSED cut={spec['cut']} op="), run.args.flight_timeout)
    observed["paused"] = run.status(fixture["logical"])
    if observed["paused"]["claim"] != "RETIRE" or observed["paused"]["state"] != "TRANSIT" \
            or observed["paused"]["transfer"] != "PREPARED":
        raise smoke.SmokeError(f"The guard pause is not D7 with T in TRANSIT: {observed['paused']}")
    held = run.send(f"{CONTROL} hold {spec['hold']}", CONTROL_REPLY)
    if control_reply(held) != ("HELD", spec["hold"], "true"):
        raise smoke.SmokeError(f"The destination Level was not held: {held.strip()}")
    observed["held"] = held.strip()
    released = len(process.lines)
    run.send("arce bay release-test release", re.compile(r"ARCE_RELEASE_BAY_RELEASE_REQUESTED"))
    ready = re.compile(rf"ARCE_BAY_CLAIM transfer={fixture['transfer']} logical={fixture['logical']} state=READY_SOURCE\b")
    run.wait_after(ready, released, run.args.flight_timeout)
    at = next(index for index in range(released, len(process.lines)) if ready.search(process.lines[index]))
    refused = [line.strip() for line in process.lines[released:at] if (match := forced.RETIRE_REFUSED.search(line))
               and match[2] == fixture["logical"]]
    if refused:
        raise smoke.SmokeError(f"D7's guard refused after the hold: {refused}")
    observed["autosave"], observed["autosave_waits"] = await_autosave(run, fixture)
    check_autosave(observed["autosave"], fixture, spec["hold"])
    saved_at = time.monotonic()
    time.sleep(WRITE_SETTLE_SECONDS)
    observed["at_kill"] = run.status(fixture["logical"])
    check_at_kill(observed["at_kill"], fixture, observed["autosave"])
    observed["seconds_autosave_to_kill"] = round(time.monotonic() - saved_at, 3)
    code = run.end("kill", run.args.exit_timeout)
    errors = evidence_tools.unexpected_errors(process.lines)
    if errors:
        raise smoke.SmokeError(f"{case}: first start logged unexpected ERROR/FATAL: {errors}")
    observed.update(exit_code=code, claims=[match.groups() for match in forced.CLAIM.finditer("".join(process.lines))])
    return fixture, observed


def await_settled(run: forced.Session, case: str, restart: int, fixture: dict) -> dict:
    spec = W346_CASES[case]
    deadline = time.monotonic() + run.args.settle_timeout
    while True:
        status = run.status(fixture["logical"])
        if forced.w331_settled(spec, spec["settled"], status, fixture):
            forced.w331_check(case, spec, spec["settled"], status, fixture)
            return status
        if time.monotonic() > deadline:
            raise smoke.SmokeError(f"{case}/{restart}: never settled as {spec['settled']}: {status}")
        time.sleep(1.0)


def restart_start(run: forced.Session, case: str, restart: int, fixture: dict, world: Path, cut: dict, previous: list[str]) -> dict:
    spec, journal = W346_CASES[case], cut["journal"]
    process = run.start(case, restart, {})
    entry: dict = dict(restart=restart)
    entry["settled"] = await_settled(run, case, restart, fixture)
    if (entry["settled"]["rocket"] != journal["destination_entity"] or entry["settled"]["transfer"] != "COMMITTED"
            or entry["settled"]["consumed"] != cut["consumed"]):
        raise smoke.SmokeError(f"{case}/{restart}: not T''s destination with its consumed operations: {entry['settled']}")
    marker = re.compile(rf"ARCE_TRANSFER_RECOVERY transfer={fixture['transfer']} ")
    run.wait(marker, 60)
    entry["recovery"] = recovery_rows(process.lines, fixture["transfer"])
    if entry["recovery"] != [spec["recovery"][restart - 1]]:
        raise smoke.SmokeError(f"{case}/{restart}: recovery chose {entry['recovery']}, expected {[spec['recovery'][restart - 1]]}")
    entry["pending"] = run.pending()
    forced.check_retired_pending(case, entry["pending"])
    code = run.end("stop", run.args.exit_timeout)
    if code != 0 or any("/ERROR]" in line or "/FATAL]" in line for line in process.lines):
        raise smoke.SmokeError(f"{case}/{restart}: the restart failed or logged ERROR/FATAL")
    entry.update(exit_code=code, oracles=restart_disk(world, fixture, cut, previous, restart))
    return entry


def run_w346_case(run: forced.Session, case: str) -> dict:
    spec = W346_CASES[case]
    forced.ORIGIN = (8, 2, 8)
    world = run.session / ("world-" + case)
    properties = smoke.expected_server_properties(run.port, True)
    properties.update({"level-name": "world-" + case, "level-type": "minecraft:flat",
                       "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}],
                                                         "biome": "minecraft:plains"})})
    (run.session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(properties.items())),
                                                   encoding="ascii")
    row = dict(case=case, rows=list(spec["rows"]), spec={key: value for key, value in spec.items()})
    fixture, row["first_start"] = first_start(run, case)
    cut = cut_disk(world, fixture, row["first_start"])
    cut["consumed"] = row["first_start"]["at_kill"]["consumed"]
    row["cut_disk"] = dict(oracles=cut, raw=evidence_tools.snapshot_world(world, run.evidence / f"{case}-cut-raw"))
    row["restarts"], previous = [], cut["entity_files"]
    for restart in (1, 2):
        entry = restart_start(run, case, restart, fixture, world, cut, previous)
        previous = entry["oracles"]["entity_files"]
        entry["raw"] = evidence_tools.snapshot_world(world, run.evidence / f"{case}-restart-{restart}-raw")
        row["restarts"].append(entry)
    row["fixture"] = fixture
    row["status"] = "PASS"
    return row


def run(args) -> dict:
    if not args.case and not args.tranche:
        args.tranche = "W346_R"
    return forced.run(args, extra_cases={case: run_w346_case for case in W346_CASES}, extra_tranches=W346_TRANCHES)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_server", type=Path)
    for key in ("jar", "session-dir", "evidence-dir"):
        parser.add_argument("--" + key, type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    forced.add_candidate_arguments(parser)
    parser.add_argument("--java", required=True)
    parser.add_argument("--lock-owner", required=True)
    parser.add_argument("--case", action="append", choices=list(W346_CASES))
    parser.add_argument("--tranche", choices=sorted(W346_TRANCHES))
    parser.add_argument("--stop-on-failure", action="store_true", help="Preserve the failed case and stop this family")
    parser.add_argument("--startup-timeout", type=float, default=240)
    parser.add_argument("--exit-timeout", type=float, default=180)
    parser.add_argument("--flight-timeout", type=float, default=120)
    parser.add_argument("--settle-timeout", type=float, default=180)
    args = parser.parse_args()
    try:
        report = run(args)
        return 0 if report["status"] == "PASS" else 1
    except (OSError, smoke.SmokeError, evidence_tools.CandidateError) as failure:
        print(f"FAIL: {failure}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
