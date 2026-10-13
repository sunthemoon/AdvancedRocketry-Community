#!/usr/bin/env python3
"""Exercise property-gated escrow fixtures in a disposable packaged Forge server."""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import os
import platform
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import uuid
import zlib

import run_dedicated_server_smoke as smoke
from inspect_celestial_saved_data import NbtReader
from run_v060_flight_server_smoke import _server_command


CASES = tuple(f"K{i}" for i in range(11)) + ("C1", "C2", "C3", "E2", "E2_CAPTURE", "E4L1_FAIL", "E4L1_UNKNOWN", "E4L2_FAIL", "E4L2_UNKNOWN", "E4L2_RESAVE", "E5", "G3", "G3L2")
DRAIN_STOPS = ("G3", "G3L2", "E4L2_RESAVE")
RESAVE_RESOLVED = re.compile(r"Escrow ledger generation (\d+) resolved as FAILED_BEFORE_REPLACE after an unknown outcome")
RESULT = re.compile(r"ARCE_RELEASE_ESCROW_RESULT source=(\d+) bank=(\d+) records=(\d+) source_epoch=(\d+) bank_epoch=(\d+) ledger=([0-9a-f-]{36})")
CHAIN_RESULT = re.compile(r"ARCE_RELEASE_ESCROW_CHAIN_RESULT source_a=(\d+) source_b=(\d+) bank=(\d+) records=(\d+) epochs=1,1,2")
E5_RESULT = re.compile(r"ARCE_RELEASE_ESCROW_E5_RESULT records=(\d+) manual=(\d+) stalled=(\d+) unloaded=(\d+) healthy=(\d+)")
LEDGER = "advancedrocketrycommunity_resource_escrow.dat"


def digest(path: Path) -> str:
    hasher = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            hasher.update(block)
    return hasher.hexdigest()


def ledger(path: Path) -> dict:
    if not path.is_file() or path.is_symlink() or path.stat().st_size > 1_114_112:
        raise smoke.SmokeError("Missing or oversized native ledger")
    with gzip.open(path, "rb") as stream:
        raw = stream.read(1_114_113)
    if len(raw) > 1_114_112:
        raise smoke.SmokeError("Oversized expanded native ledger")
    outer = NbtReader(raw).read_root()
    data = outer.get("data")
    if not isinstance(data, dict) or data.get("schema_version") != 1:
        raise smoke.SmokeError("Wrong native escrow schema")
    return data


def record(path: Path, slot: int = 132) -> dict:
    # Fixture chunks [4,4] and (chains only) [5,4]. Inline records only.
    with path.open("rb") as stream:
        stream.seek(slot * 4)
        location = struct.unpack(">I", stream.read(4))[0]
        if location >> 8 < 2 or location & 255 == 0:
            raise smoke.SmokeError("Missing native fixture record")
        stream.seek((location >> 8) * 4096)
        length, compression = struct.unpack(">IB", stream.read(5))
        if not 1 < length <= 1_048_577 or length + 4 > (location & 255) * 4096 or compression != 2:
            raise smoke.SmokeError("Unsupported native fixture record")
        compressed = stream.read(length - 1)
    decoder = zlib.decompressobj()
    raw = decoder.decompress(compressed, 4_194_305)
    if len(raw) > 4_194_304 or not decoder.eof or decoder.unused_data:
        raise smoke.SmokeError("Invalid native fixture record envelope")
    return NbtReader(raw).read_root()


def inspect(world: Path, chain: bool = False, shared: bool = False) -> dict:
    root = ledger(world / "data" / LEDGER)
    terrain = record(world / "region" / "r.0.0.mca")
    entities = record(world / "entities" / "r.0.0.mca")
    source = [entry for entry in terrain["block_entities"] if [entry.get(k) for k in ("x", "y", "z")] == [64, 100, 64]]
    banks = [entry for entry in entities["Entities"] if "arce_escrow_fixture" in entry.get("ForgeData", {})]
    if len(source) != 1 or len(banks) != 1:
        raise smoke.SmokeError("Native fixture identity is missing or duplicated")
    second = []
    if chain:
        second_root = terrain if shared else record(world / "region" / "r.0.0.mca", 133)
        second = [entry for entry in second_root["block_entities"] if [entry.get(k) for k in ("x", "y", "z")] == [68 if shared else 80, 100, 64]]
        if len(second) != 1:
            raise smoke.SmokeError("Native second source is missing or duplicated")
    return dict(ledger=root, source=source[0]["ForgeData"], bank=banks[0]["ForgeData"],
                second=second[0]["ForgeData"] if len(second) == 1 else None,
                files={name: digest(world / name) for name in (
                    "data/" + LEDGER, "region/r.0.0.mca", "entities/r.0.0.mca")})


def inspect_e5(world: Path) -> dict:
    result = dict(ledger=ledger(world / "data" / LEDGER), endpoints=[], files={})
    for x, y, z in ((64, 100, 64), (96, 100, 64), (2048, 100, 2048), (128, 100, 64)):
        cx, cz = x >> 4, z >> 4
        slot = (cx & 31) + ((cz & 31) << 5)
        region = f"r.{cx // 32}.{cz // 32}.mca"
        terrain = record(world / "region" / region, slot)
        entities = record(world / "entities" / region, slot)
        sources = [e for e in terrain["block_entities"] if [e.get(k) for k in ("x", "y", "z")] == [x, y, z]]
        banks = [e for e in entities["Entities"] if e.get("Pos") == [x + 2.5, float(y), z + .5]]
        if len(sources) != 1 or len(banks) != 1:
            raise smoke.SmokeError("Missing or duplicated E5 endpoint")
        result["endpoints"].append(dict(source=sources[0]["ForgeData"], bank=banks[0]["ForgeData"]))
        for directory in ("region", "entities"):
            result["files"][directory + "/" + region] = digest(world / directory / region)
    result["files"]["data/" + LEDGER] = digest(world / "data" / LEDGER)
    return result


def check_e5(disk: dict, recovery: bool) -> None:
    records = disk["ledger"]["records"]
    phases = sorted(r["phase"] for r in records)
    if phases != (["REPAIR"] if recovery else ["PREPARED", "REPAIR", "STALLED"]):
        raise smoke.SmokeError("E5 reason-specific phases did not survive persistence")
    expected = ((4000, 1000), (4999, 0), (4000 if recovery else 5000, 1000 if recovery else 0), (4000, 1000))
    for endpoint, quantities in zip(disk["endpoints"], expected):
        actual = tuple(endpoint[k]["arce_escrow_fixture"]["amount"] for k in ("source", "bank"))
        if actual != quantities:
            raise smoke.SmokeError(f"E5 raw resource states disagree: {actual} != {quantities}")
    if any(r["reason"] != ("OTHER_STATE" if r["phase"] == "REPAIR" else "BARRIER_VETO" if r["phase"] == "STALLED" else "NONE") for r in records):
        raise smoke.SmokeError("Unexpected E5 persisted reason")


def run(args) -> dict:
    session = args.session_dir.resolve()
    evidence = args.evidence_dir.resolve()
    if session.exists() or session.is_symlink():
        raise smoke.SmokeError("Refusing to overwrite a native session")
    if evidence == session or session in evidence.parents:
        raise smoke.SmokeError("Evidence must be outside the disposable session")
    if shutil.disk_usage(session.parent).free < 10 * 1024**3:
        raise smoke.SmokeError("Native run requires at least 10 GiB free")
    libraries = args.source_server.resolve() / "libraries"
    if not libraries.is_dir() or any(p.is_symlink() for p in libraries.rglob("*")):
        raise smoke.SmokeError("Unsafe or missing packaged Forge libraries")
    arguments = libraries / "net/minecraftforge/forge/1.20.1-47.4.10" / ("win_args.txt" if platform.system() == "Windows" else "unix_args.txt")
    if not arguments.is_file():
        raise smoke.SmokeError("Missing pinned Forge server arguments")
    required = set(re.findall(r"libraries/[^\s;:]+\.jar", arguments.read_text(encoding="utf-8")))
    missing = sorted(path for path in required if not (args.source_server.resolve() / path).is_file())
    if not required or missing:
        raise smoke.SmokeError("Incomplete packaged Forge libraries: " + ", ".join(missing[:3]))
    session.mkdir()
    owned_root = session.resolve()
    ownership = uuid.uuid4().hex
    (session / ".arce-w11b1-owned").write_text(ownership, encoding="ascii")
    evidence.mkdir(parents=True, exist_ok=True)
    report = dict(status="IN_PROGRESS", source_commit=args.source_commit, jar_sha256=digest(args.jar), cases=[],
                  limits="S2 stopped-process ordering, not OS crash/power loss or unobservable vanilla failures")
    process = None
    try:
        shutil.copytree(libraries, session / "libraries")
        (session / "mods").mkdir()
        shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = smoke.allocate_port()
        smoke.write_server_configuration(session, port, True)
        for cut in args.case or CASES:
            properties = smoke.expected_server_properties(port, True)
            properties.update({"level-name": "world-" + cut, "level-type": "minecraft:flat", "max-tick-time": "60000"})
            (session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(properties.items())), encoding="ascii")
            row = dict(cut=cut, restarts=[])
            report["in_progress"] = row
            for restart in range(3):
                command = _server_command(args.java)
                command[2] = "-Xmx4G"
                command.insert(1, "-Dadvancedrocketrycommunity.escrowScenario=" + ("recover" if restart else "start"))
                command.insert(1, "-Dadvancedrocketrycommunity.escrowCase=" + cut)
                if restart == 0:
                    command.insert(1, "-Dadvancedrocketrycommunity.escrowCheckpoint=" + cut)
                log = evidence / f"{cut}-restart-{restart}.log"
                process = smoke.CapturedProcess(command, session, log)
                if restart == 0:
                    if cut in DRAIN_STOPS:
                        process.wait_for(re.compile(r"ARCE_RELEASE_ESCROW_DRAIN_READY cut=" + cut + r"(?: |$)"), args.startup_timeout)
                        process.command("stop")
                        if process.finish() != 0:
                            raise smoke.SmokeError("Native queued-writer shutdown failed")
                        cut_lines = list(process.lines)
                    else:
                        process.wait_for(re.compile(r"ARCE_RELEASE_ESCROW_PAUSED cut=" + cut + r"(?: |$)"), args.startup_timeout)
                        process.process.kill()  # Only this harness-owned process; no JVM shutdown hooks.
                        process.abort()
                    process = None
                    row["cut_disk"] = inspect_e5(session / ("world-" + cut)) if cut == "E5" else inspect(session / ("world-" + cut), cut in ("C1", "C2", "C3"), cut == "C3")
                    if cut == "E5":
                        check_e5(row["cut_disk"], False)
                    if cut in ("C1", "C2", "C3"):
                        disk = row["cut_disk"]
                        amounts = (disk["source"]["arce_escrow_fixture"]["amount"],
                                   disk["second"]["arce_escrow_fixture"]["amount"],
                                   disk["bank"]["arce_escrow_fixture"]["amount"])
                        if amounts != (4000, 4000, 1000 if cut == "C1" else 2000) or len(disk["ledger"]["records"]) != (1 if cut == "C3" else 2):
                            raise smoke.SmokeError("Raw chain cut does not match its endpoint prefix boundary")
                        if cut == "C3":
                            marker_counts = tuple(len(disk[key]["resource_escrow"]["markers"]) for key in ("source", "second", "bank"))
                            if marker_counts != (0, 1, 1):
                                raise smoke.SmokeError("Shared-slot partial retirement pruned a later marker")
                    if cut == "G3" and len(row["cut_disk"]["ledger"]["records"]) != 1:
                        raise smoke.SmokeError("Shutdown did not retain its acknowledged, unapplied L1")
                    if cut == "G3L2" and row["cut_disk"]["ledger"]["records"]:
                        raise smoke.SmokeError("Shutdown did not drain the proven L2 generation")
                    if cut == "E4L2_RESAVE":
                        # The ambiguous L2 was not replaced; the newer save invalidated its proof before the retry, so
                        # the drain resolves it as FAILED_BEFORE_REPLACE instead of retrying forever or landing it.
                        disk = row["cut_disk"]
                        resolved = [match for match in map(RESAVE_RESOLVED.search, cut_lines) if match]
                        amounts = (disk["source"]["arce_escrow_fixture"]["amount"], disk["bank"]["arce_escrow_fixture"]["amount"])
                        if (len(disk["ledger"]["records"]) != 1 or amounts != (4000, 1000) or len(resolved) != 1
                                or int(resolved[0].group(1)) != disk["ledger"]["writer_generation"] + 1):
                            raise smoke.SmokeError("Invalidated ambiguous L2 was not resolved and retained by the drain")
                else:
                    pattern = E5_RESULT if cut == "E5" else CHAIN_RESULT if cut in ("C1", "C2", "C3") else RESULT
                    index = process.wait_for(pattern, args.startup_timeout)
                    match = pattern.search(process.lines[index])
                    values = tuple(map(int, match.groups()[:4 if pattern is CHAIN_RESULT else 5]))
                    expected = (1, 1, 0, 0, 3) if pattern is E5_RESULT else (4000, 4000, 2000, 0) if pattern is CHAIN_RESULT else (
                        (5000, 0, 0, 0, 0) if cut in ("K0", "K1", "E4L1_FAIL", "E4L1_UNKNOWN") else (4000, 1000, 0, 1, 1))
                    if values != expected:
                        raise smoke.SmokeError(f"{cut} restart {restart}: {values} != {expected}")
                    process.command("stop")
                    code = process.finish()
                    if code != 0 or any("/ERROR]" in line or "/FATAL]" in line for line in process.lines):
                        raise smoke.SmokeError(f"Native restart failed or logged ERROR: {cut}/{restart}")
                    process = None
                    disk = inspect_e5(session / ("world-" + cut)) if cut == "E5" else inspect(session / ("world-" + cut), cut in ("C1", "C2", "C3"), cut == "C3")
                    if cut == "E5":
                        check_e5(disk, True)
                        row["restarts"].append(dict(restart=restart, values=values, disk=disk))
                        (evidence / "results.json").write_text(json.dumps(report | {"in_progress": row}, indent=2), encoding="utf-8")
                        continue
                    amounts = (disk["source"]["arce_escrow_fixture"]["amount"], disk["bank"]["arce_escrow_fixture"]["amount"])
                    if pattern is CHAIN_RESULT:
                        amounts = (amounts[0], disk["second"]["arce_escrow_fixture"]["amount"], amounts[1])
                    if amounts != expected[:len(amounts)] or disk["ledger"]["records"]:
                        raise smoke.SmokeError("Raw native stores disagree with the reported conserved resources")
                    row["restarts"].append(dict(restart=restart, values=values, disk=disk))
                (evidence / "results.json").write_text(json.dumps(report | {"in_progress": row}, indent=2), encoding="utf-8")
            row["status"] = "PASS"
            report["cases"].append(row)
            stop_mode = "writer-drain stop" if cut in DRAIN_STOPS else "hard stop"
            print(f"{cut}: PASS ({stop_mode}, two restarts, raw stores)", flush=True)
        report["status"] = "PASS"
        report.pop("in_progress", None)
        return report
    except Exception as failure:
        report["status"] = "FAILED"
        report["error"] = str(failure)
        raise
    finally:
        if process is not None:
            process.abort()
        (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        # This fresh directory was created by this run. Never delete the supplied template/worlds.
        marker = session / ".arce-w11b1-owned"
        if (session.exists() and not session.is_symlink() and session.resolve() == owned_root
                and marker.is_file() and not marker.is_symlink() and marker.read_text(encoding="ascii") == ownership):
            shutil.rmtree(session)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_server", type=Path)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--session-dir", type=Path, required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--case", action="append", choices=CASES)
    parser.add_argument("--java", default=str(Path(os.environ.get("JAVA_HOME", "")) / "bin/java.exe"))
    parser.add_argument("--startup-timeout", type=float, default=240)
    args = parser.parse_args()
    try:
        run(args)
        return 0
    except (OSError, smoke.SmokeError) as failure:
        print(f"FAIL: {failure}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
