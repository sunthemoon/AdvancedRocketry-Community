#!/usr/bin/env python3
"""Exercise property-gated escrow fixtures in a disposable packaged Forge server."""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import os
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


CASES = tuple(f"K{i}" for i in range(11)) + ("G3",)
RESULT = re.compile(r"ARCE_RELEASE_ESCROW_RESULT source=(\d+) bank=(\d+) records=(\d+) source_epoch=(\d+) bank_epoch=(\d+) ledger=([0-9a-f-]{36})")
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


def record(path: Path) -> dict:
    # Fixture chunk [4,4], slot 132. Inline records only; no unbounded decompression.
    with path.open("rb") as stream:
        stream.seek(132 * 4)
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


def inspect(world: Path) -> dict:
    root = ledger(world / "data" / LEDGER)
    terrain = record(world / "region" / "r.0.0.mca")
    entities = record(world / "entities" / "r.0.0.mca")
    source = [entry for entry in terrain["block_entities"] if [entry.get(k) for k in ("x", "y", "z")] == [64, 100, 64]]
    banks = [entry for entry in entities["Entities"] if "arce_escrow_fixture" in entry.get("ForgeData", {})]
    if len(source) != 1 or len(banks) != 1:
        raise smoke.SmokeError("Native fixture identity is missing or duplicated")
    return dict(ledger=root, source=source[0]["ForgeData"], bank=banks[0]["ForgeData"],
                files={name: digest(world / name) for name in (
                    "data/" + LEDGER, "region/r.0.0.mca", "entities/r.0.0.mca")})


def run(args) -> dict:
    session = args.session_dir.resolve()
    evidence = args.evidence_dir.resolve()
    if session.exists() or session.is_symlink():
        raise smoke.SmokeError("Refusing to overwrite a native session")
    if shutil.disk_usage(session.parent).free < 10 * 1024**3:
        raise smoke.SmokeError("Native run requires at least 10 GiB free")
    libraries = args.source_server.resolve() / "libraries"
    if not libraries.is_dir() or any(p.is_symlink() for p in libraries.rglob("*")):
        raise smoke.SmokeError("Unsafe or missing packaged Forge libraries")
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
                if restart == 0:
                    command.insert(1, "-Dadvancedrocketrycommunity.escrowCheckpoint=" + cut)
                log = evidence / f"{cut}-restart-{restart}.log"
                process = smoke.CapturedProcess(command, session, log)
                if restart == 0:
                    if cut == "G3":
                        process.wait_for(re.compile(r"ARCE_RELEASE_ESCROW_DRAIN_READY cut=G3"), args.startup_timeout)
                        process.command("stop")
                        if process.finish() != 0:
                            raise smoke.SmokeError("Native queued-writer shutdown failed")
                    else:
                        process.wait_for(re.compile(r"ARCE_RELEASE_ESCROW_PAUSED cut=" + cut + r"(?: |$)"), args.startup_timeout)
                        process.process.kill()  # Only this harness-owned process; no JVM shutdown hooks.
                        process.abort()
                    process = None
                    row["cut_disk"] = inspect(session / ("world-" + cut))
                    if cut == "G3" and len(row["cut_disk"]["ledger"]["records"]) != 1:
                        raise smoke.SmokeError("Shutdown did not retain its acknowledged, unapplied L1")
                else:
                    index = process.wait_for(RESULT, args.startup_timeout)
                    match = RESULT.search(process.lines[index])
                    values = tuple(map(int, match.groups()[:5]))
                    expected = (5000, 0, 0, 0, 0) if cut in ("K0", "K1") else (4000, 1000, 0, 1, 1)
                    if values != expected:
                        raise smoke.SmokeError(f"{cut} restart {restart}: {values} != {expected}")
                    process.command("stop")
                    code = process.finish()
                    if code != 0 or any("/ERROR]" in line or "/FATAL]" in line for line in process.lines):
                        raise smoke.SmokeError(f"Native restart failed or logged ERROR: {cut}/{restart}")
                    process = None
                    disk = inspect(session / ("world-" + cut))
                    amounts = (disk["source"]["arce_escrow_fixture"]["amount"], disk["bank"]["arce_escrow_fixture"]["amount"])
                    if amounts != expected[:2] or disk["ledger"]["records"]:
                        raise smoke.SmokeError("Raw native stores disagree with the reported conserved resources")
                    row["restarts"].append(dict(restart=restart, values=values, disk=disk))
                (evidence / "results.json").write_text(json.dumps(report | {"in_progress": row}, indent=2), encoding="utf-8")
            row["status"] = "PASS"
            report["cases"].append(row)
            print(f"{cut}: PASS (hard stop, two restarts, raw stores)", flush=True)
        report["status"] = "PASS"
        report.pop("in_progress", None)
        return report
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
