"""Opt-in passive observation of X01 late-loss states (W451, following W448 E7 and E8).

A copy of one planned state is started twice with no release probe selected: no
escrowScenario and no escrowCase, so nothing mutates or asserts on the fixture. Each
start waits, runs `save-all flush`, and stops in order. The raw ledger, endpoint
payloads, identities and markers are kept before the first start and after each save.

This adds evidence only. It never writes results.jsonl, never selects an oracle and
never reports PASS, so no existing verdict, marker, count or timeout is affected.
"""
from __future__ import annotations

import gzip
import hashlib
import json
from pathlib import Path
import re
import shutil
import time
import uuid
import zipfile
import zlib

import bay_integrated_evidence as evidence_tools
import run_dedicated_server_smoke as smoke
from run_v060_flight_server_smoke import _server_command
import v180_crash_replay as replay

# The five residual states of W448 E7 and E8, by their sealed S3 state ids.
OBSERVED = {"X01-05": "07-LATE-LOSS", "X01-06": "07-LATE-LOSS", "X01-07": "01-LATE-LOSS",
            "X01-09": "07-LATE-LOSS", "X01-11": "12-LATE-LOSS"}
PREFIX = "-Dadvancedrocketrycommunity."
HOOKS = PREFIX + "releaseTestHooks="
# "hooks" is S3's JVM command without its probe selection; "plain" also drops the release-test gate.
VARIANTS = (("hooks", True), ("plain", False))
DWELL_SECONDS = 20.0
SCALARS = ("ledger_id", "schema_version", "format_epoch", "writer_generation", "issued_through", "issued_launch_serial")
CONSERVED_LOST = "QUANTITIES_CONSERVED_AUTHORITY_OR_HISTORY_LOST"
CONSERVED = "QUANTITIES_CONSERVED_AUTHORITY_INTACT"
CHANGED = "QUANTITY_LOST_OR_CREATED"
UNDECIDABLE = "UNDECIDABLE"


def passive_command(base: list[str], hooks: bool) -> list[str]:
    """The server command with no probe selected; refuse any other mod property."""
    command = [part for part in base if not part.startswith(PREFIX) or part.startswith(HOOKS)]
    if len(command) != len(base):
        raise replay.ReplayError("Passive observation starts from the unselected server command")
    if not hooks:
        command = [part for part in command if not part.startswith(HOOKS)]
    return command


def same_state(sealed: dict, state: replay.State) -> dict:
    """The rebuilt state must be the sealed one: same id, derivations and present/absent files.

    Sealed S3 kept the trace and pre-images but not the kill image's region and entity
    files, and every recording draws new UUIDs, so byte hashes legitimately differ.
    """
    entry = next((s for s in sealed.get("states", []) if s.get("id") == state.id), None)
    if entry is None:
        raise replay.ReplayError(f"Sealed table has no state {state.id}")
    if entry["derivations"] != state.derivations:
        raise replay.ReplayError(f"Rebuilt {state.id} has different derivations from the sealed state")
    if {k: v is None for k, v in entry["files"].items()} != {k: v is None for k, v in state.files.items()}:
        raise replay.ReplayError(f"Rebuilt {state.id} has different present/absent seam files")
    return dict(state=state.id, derivations=state.derivations, sealed_files=entry["files"], rebuilt_files=state.files)


def _suffix(tag) -> str:
    if not tag:
        return ""
    return "#" + hashlib.sha256(json.dumps(tag, sort_keys=True).encode("utf-8")).hexdigest()[:12]


def contents(value, found: dict | None = None) -> dict:
    """Fluids and item stacks found by shape, keyed by type (and by a digest of any tag)."""
    found = {} if found is None else found
    if isinstance(value, dict):
        key = None
        if isinstance(value.get("FluidName"), str) and type(value.get("Amount")) is int:
            key, amount = "fluid:" + value["FluidName"] + _suffix(value.get("Tag")), value["Amount"]
        elif isinstance(value.get("id"), str) and type(value.get("Count")) is int:
            key, amount = "item:" + value["id"] + _suffix(value.get("tag")), value["Count"]
        if key is None:
            for item in value.values():
                contents(item, found)
        elif amount:
            found[key] = found.get(key, 0) + amount
    elif isinstance(value, list):
        for item in value:
            contents(item, found)
    return found


def authority(root: dict | None) -> dict | None:
    """Sequence, accounts, receipts and settlement tombstones of one decoded ledger."""
    if root is None:
        return None
    result = {key: root.get(key) for key in SCALARS}
    result["counts"] = {key: len(value) for key, value in sorted(root.items()) if isinstance(value, list) and key != "ledger_id"}
    result["records"] = [{k: op.get(k) for k in ("seq", "op_id", "kind", "phase", "reason")} | dict(settlement="settlement" in op)
                         for op in root.get("records", [])]
    result["tombstones"] = [{k: r.get(k) for k in ("op_id", "released", "choice", "accounting_status")}
                            for r in root.get("tombstones", [])]
    return result


def view(raw: dict) -> dict:
    """One comparable projection of the fluid, item and settlement runners' raw snapshots."""
    blocks = raw["block_entities"] if "block_entities" in raw else raw["terrain"]["block_entities"]
    entities = raw["entities"]["Entities"] if isinstance(raw["entities"], dict) else raw["entities"]
    transfers = raw["transfers"] if "transfers" in raw else raw["journal"]["transfers"]
    rockets, devices, others = [], [], []
    for entity in entities:
        data = entity.get("RocketEntityData")
        if data is None:
            continue
        flight = data.get("flight_data", {})
        rockets.append(dict(
            uuid=entity.get("UUID"), pos=entity.get("Pos"), logical=data.get("assembly_transaction_id"),
            owner=data.get("owner_id"), snapshot=data.get("snapshot", {}).get("snapshot_id"),
            flight_state=flight.get("state"), fuel=flight.get("fuel", {}).get("amount"),
            passengers=len(flight.get("passengers", {}).get("assignments", [])),
            escrow=data.get("resource_escrow"), cargo=contents(data.get("snapshot"))))
    for block in blocks:
        position = [block.get(k) for k in ("x", "y", "z")]
        roots = [k for k in block if k.startswith("arce_rocket_") and k.endswith("_cargo")]
        if roots:
            root = block[roots[0]]
            devices.append(dict(pos=position, root=roots[0], device=root.get("device"), owner=root.get("owner"),
                                role=root.get("role"), binding=root.get("binding"), escrow=root.get("resource_escrow"),
                                contents=contents(root)))
        elif contents(block):
            others.append(dict(pos=position, id=block.get("id"), contents=contents(block)))
    totals: dict = {}
    for holder in [r["cargo"] for r in rockets] + [d["contents"] for d in devices] + [o["contents"] for o in others]:
        for key, amount in holder.items():
            totals[key] = totals.get(key, 0) + amount
    fuel = sum(r["fuel"] for r in rockets if type(r["fuel"]) is int)
    if fuel:
        totals["rocket-fuel"] = fuel
    return dict(ledger=authority(raw.get("ledger")), journal=len(transfers), rockets=rockets, devices=devices,
                other_blocks=others, totals=dict(sorted(totals.items())))


def identities(value: dict) -> dict:
    return dict(rockets=sorted(json.dumps([r["uuid"], r["logical"], r["owner"], r["snapshot"]]) for r in value["rockets"]),
                devices=sorted(json.dumps([d["pos"], d["device"], d["owner"], d["role"], d["binding"]]) for d in value["devices"]))


def orphan_markers(value: dict) -> list[str]:
    """Endpoint markers and bindings that the ledger of the same snapshot no longer explains."""
    root = value["ledger"]
    found = []
    for name, endpoint in [("rocket", r) for r in value["rockets"]] + [("device", d) for d in value["devices"]]:
        section = endpoint.get("escrow") or {}
        if root is None:
            if section.get("resource_epoch") or section.get("markers") or "ledger_id" in section:
                found.append(f"{name}: escrow section without any ledger")
            continue
        if "ledger_id" in section and section["ledger_id"] != root["ledger_id"]:
            found.append(f"{name}: bound to another ledger")
        for marker in section.get("markers", []):
            live = any(op["op_id"] == marker.get("op_id") for op in root["records"])
            if not live and type(marker.get("seq")) is int and marker["seq"] > (root["issued_through"] or 0):
                found.append(f"{name}: marker seq {marker['seq']} is beyond issued_through {root['issued_through']}")
        if (section.get("resource_epoch") or 0) > (root["issued_through"] or 0):
            found.append(f"{name}: epoch {section['resource_epoch']} is beyond issued_through {root['issued_through']}")
    return found


def authority_lost(before: dict | None, after: dict | None, journal_before: int = 0, journal_after: int = 0) -> list[str]:
    """What the acknowledged ledger of the kill image had that a later ledger lacks."""
    if before is None:
        return []
    if after is None:
        return ["the whole ledger"]
    lost = []
    if after["ledger_id"] != before["ledger_id"]:
        lost.append("ledger identity changed")
    for key in ("issued_through", "issued_launch_serial", "writer_generation"):
        if (after[key] or 0) < (before[key] or 0):
            lost.append(f"{key} {before[key]} -> {after[key]}")
    for key, count in before["counts"].items():
        if after["counts"].get(key, 0) < count:
            lost.append(f"{key} {count} -> {after['counts'].get(key, 0)}")
    if journal_after < journal_before:
        lost.append(f"journal transfers {journal_before} -> {journal_after}")
    return lost


def classify(recorded: dict, snapshots: list[dict], starts: list[dict]) -> dict:
    """Typed totals against the kill image, and separately authority and history.

    `recorded` is the view of the kill image before the rewind. `snapshots` are the
    materialized state and the image after each start's save and stop.
    """
    missing = [f"snapshot {index} could not be decoded: {entry['error']}"
               for index, entry in enumerate(snapshots) if "view" not in entry]
    for start in starts:
        if not start.get("saved") or start.get("exit_code") != 0:
            missing.append(f"start {start.get('start')} did not complete save-all flush and an orderly stop: "
                           + str(start.get("error", "exit code " + str(start.get("exit_code")))))
    if len(snapshots) != 3 or len(starts) != 2:
        missing.append("two starts and three snapshots are required")
    if missing:
        return dict(verdict=UNDECIDABLE, missing=missing)
    views = [entry["view"] for entry in snapshots]
    base = recorded["totals"]
    if views[0]["totals"] != base or identities(views[0]) != identities(recorded):
        return dict(verdict=UNDECIDABLE, missing=["the rebuilt state's endpoints differ from the kill image"])
    deltas = [{key: after["totals"].get(key, 0) - base.get(key, 0) for key in sorted(set(base) | set(after["totals"]))
               if after["totals"].get(key, 0) != base.get(key, 0)} for after in views[1:]]
    result = dict(totals_before_kill=base, totals=[v["totals"] for v in views], deltas_after_each_start=deltas,
                  identities_unchanged=[identities(v) == identities(recorded) for v in views[1:]],
                  authority_before_kill=recorded["ledger"], authority=[v["ledger"] for v in views],
                  authority_lost=[authority_lost(recorded["ledger"], v["ledger"], recorded["journal"], v["journal"]) for v in views],
                  orphan_markers=[orphan_markers(v) for v in views])
    if any(deltas):
        result.update(verdict=CHANGED,
                      lost=[{k: -v for k, v in d.items() if v < 0} for d in deltas],
                      created=[{k: v for k, v in d.items() if v > 0} for d in deltas])
    elif result["authority_lost"][-1] or result["orphan_markers"][-1] or not all(result["identities_unchanged"]):
        result["verdict"] = CONSERVED_LOST
    else:
        result["verdict"] = CONSERVED
    return result


def archive(world: Path, session: Path, destination: Path) -> dict:
    """The native files of one snapshot, lossless, so a rejected decode can still be reviewed."""
    files = [world / "level.dat", *sorted((world / "data").glob("*")),
             *[p for folder in ("region", "entities") for p in sorted((world / folder).glob("*")) if p.suffix in (".mca", ".mcc")],
             *sorted((session / "crash-reports").glob("*.txt"))]
    manifest = {}
    with zipfile.ZipFile(destination, "x", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as output:
        for path in files:
            # Region files of untouched chunks are empty; an 8 MiB bound keeps a runaway file out of evidence.
            if not path.is_file() or path.is_symlink() or path.stat().st_size == 0 or path.stat().st_size > 8 * 1024**2:
                continue
            name = path.relative_to(session).as_posix()
            data = path.read_bytes()
            output.writestr(zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)), data, zipfile.ZIP_DEFLATED, 9)
            manifest[name] = dict(bytes=len(data), sha256=hashlib.sha256(data).hexdigest())
    return dict(file=destination.name, sha256=evidence_tools.file_sha256(destination), members=manifest)


def snapshot(world: Path, session: Path, output: Path, name: str, read) -> dict:
    entry = dict(native=archive(world, session, output / (name + "-native.zip")))
    try:
        text = json.dumps(read(world), indent=1)
        target = output / (name + "-raw.json")
        target.write_text(text, encoding="utf-8")
        entry["raw"] = dict(file=target.name, sha256=evidence_tools.file_sha256(target))
        entry["view"] = view(json.loads(text))
    except (OSError, EOFError, zlib.error, ValueError, KeyError, TypeError, smoke.SmokeError) as failure:
        entry["error"] = f"{type(failure).__name__}: {failure}"
    return entry


def observe_variant(args, state: replay.State, kill: Path, world_name: str, read, output: Path, session: Path,
                    label: str, hooks: bool, pin=lambda: None, dwell: float = DWELL_SECONDS) -> dict:
    """Two probe-free starts of one fresh copy of the state. The caller holds the heavy-JVM lock."""
    libraries = args.source_server.resolve() / "libraries"
    session.mkdir(parents=True)
    token = uuid.uuid4().hex
    sentinel = session / ".w451-owned"
    sentinel.write_text(token, encoding="ascii")
    result = dict(variant=label, release_test_hooks=hooks, dwell_seconds=dwell, starts=[], snapshots=[])
    process = None
    try:
        shutil.copytree(libraries, session / "libraries")
        (session / "mods").mkdir()
        installed = evidence_tools.install_candidate(args.jar, session / "mods" / args.jar.name, args.jar_sha256)
        port = smoke.allocate_port()
        smoke.write_server_configuration(session, port, True)
        properties = smoke.expected_server_properties(port, True)
        properties.update({"level-name": world_name, "level-type": "minecraft:flat", "max-tick-time": "60000",
                           "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}],
                                                             "biome": "minecraft:plains"})})
        (session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(properties.items())), encoding="ascii")
        world = session / world_name
        replay.materialize(kill, world, state)
        result["snapshots"].append(snapshot(world, session, output, f"{state.id}-{label}-0", read))
        for number in (1, 2):
            pin()
            evidence_tools.recheck_installed(installed)
            command = _server_command(args.java)
            command[2] = "-Xmx4G"
            command = passive_command(command, hooks)
            start = dict(start=number, command=command, saved=False, **installed)
            result["starts"].append(start)
            log = output / f"{state.id}-{label}-{number}.log"
            process = smoke.CapturedProcess(command, session, log)
            try:
                process.wait_for(re.compile(r"Done \("), args.startup_timeout)
                # Let the unprobed server tick, so its own recovery can act before the save.
                time.sleep(dwell)
                mark = len(process.lines)
                process.command("save-all flush")
                process.wait_for(re.compile("Saved the game"), args.startup_timeout, start_at=mark)
                start["saved"] = True
                process.command("stop")
                start["exit_code"] = process.finish()
            except (OSError, smoke.SmokeError) as failure:
                start["error"] = str(failure)
                process.abort()
                start["exit_code"] = process.process.poll()
            lines, process = process.lines, None
            start["markers"] = [line.rstrip("\r\n") for line in lines if "ARCE_" in line or "/ERROR]" in line or "/FATAL]" in line]
            start["errors"] = sum("/ERROR]" in line or "/FATAL]" in line for line in lines)
            start["log"] = log.name + ".gz"
            (output / start["log"]).write_bytes(gzip.compress(log.read_bytes(), mtime=0))
            log.unlink()
            result["snapshots"].append(snapshot(world, session, output, f"{state.id}-{label}-{number}", read))
    finally:
        if process is not None:
            process.abort()
        if not session.is_symlink() and sentinel.is_file() and sentinel.read_text(encoding="ascii") == token:
            shutil.rmtree(session)
        result["cleanup"] = not session.exists()
    return result


def observe_case(args, case: str, states: list[replay.State], kill: Path, context: dict, world_name: str, read,
                 evidence: Path, temp: Path, pin=lambda: None) -> dict:
    """Observe the one named state of a case; every other planned state is left unstarted."""
    sealed_path = Path(args.triage_of).parent / "cases" / case / "states.json"
    sealed = json.loads(sealed_path.read_text(encoding="utf-8"))
    state = next((s for s in states if s.id == OBSERVED[case]), None)
    if state is None:
        raise replay.ReplayError(f"{case}: the new recording did not plan {OBSERVED[case]}")
    rebuilt = same_state(sealed, state)
    rebuilt["sealed_states"] = dict(file=str(sealed_path), sha256=evidence_tools.file_sha256(sealed_path))
    recorded = view(json.loads(json.dumps(context["raw0"])))
    output = evidence / "observations"
    output.mkdir()
    report = dict(case=case, state=state.id, rebuilt=rebuilt, recorded_kill_image=recorded, variants=[],
                  not_started=[s.id for s in states if s.id != state.id],
                  note="Observation only: no oracle, no PASS, and no change to the S3 or triage verdicts.")
    for label, hooks in VARIANTS:
        run = observe_variant(args, state, kill, world_name, read, output, temp / ("w451-" + uuid.uuid4().hex), label, hooks,
                              pin, getattr(args, "observe_dwell", DWELL_SECONDS))
        run["classification"] = classify(recorded, run["snapshots"], run["starts"])
        report["variants"].append(run)
        (output / f"{state.id}.json").write_text(json.dumps(report, indent=1) + "\n", encoding="utf-8")
    return report
