#!/usr/bin/env python3
"""Copy-only packaged-server fuel accounting, repeat restart and oversized-chunk refusal probe."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import struct
import time
import zipfile
import zlib
from pathlib import Path

if __package__:
    from . import run_dedicated_server_smoke as server
    from . import run_v050_rocket_server_smoke as rocket
    from . import v120_precision_world_fixture as regions
    from . import v140_migration_fixture as world_copy
else:
    import run_dedicated_server_smoke as server
    import run_v050_rocket_server_smoke as rocket
    import v120_precision_world_fixture as regions
    import v140_migration_fixture as world_copy

ROOT_KEY = "arce_combustion_generator"
POSITIONS = {(132 + index * 2, 80, 132): name for index, name in enumerate(("coal", "lava", "future", "corrupt"))}
FORCELOAD_RESULT = re.compile(r"force loaded|No chunks were marked for force loading")
FORCELOAD_QUERY = re.compile(r"Chunk at \[8, ?8\] in minecraft:overworld is marked for force loading")
REPORT_COMPLETE = re.compile(r"\[minecraft/MinecraftServer\]: ARCE_COMBUSTION_REPORT cell=corrupt ")
EXPECTED_SAVE_ERROR = re.compile(r"Failed to save chunk (?:8,8|\[8, 8\])(?:$|\s)")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def write(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def artifact(path: Path, version: str | None = None) -> dict:
    require(path.is_file() and not server.is_link_or_junction(path), "Artifact must be a regular file")
    with zipfile.ZipFile(path) as archive:
        metadata = archive.read("META-INF/mods.toml").decode("utf-8")
        if version:
            require('modId="advancedrocketrycommunity"' in metadata and f'version="{version}"' in metadata,
                    "Artifact identity/version mismatch")
    return {"path": str(path), "name": path.name, "sha256": server.digest_file(path), "bytes": path.stat().st_size}


def capture(runtime: Path, evidence: Path) -> tuple[bytes, dict]:
    compressed = regions._region_chunk((runtime / "world/region/r.0.0.mca").read_bytes(), 8, 8)
    chunk = regions._decode_chunk(compressed, 8, 8)
    roots = {}
    for entity in chunk.get("block_entities", []):
        position = (entity.get("x"), entity.get("y"), entity.get("z"))
        if position in POSITIONS:
            require(entity.get("id") == "advancedrocketrycommunity:combustion_generator", "Fixture block entity disappeared")
            roots[POSITIONS[position]] = entity.get(ROOT_KEY)
    require(set(roots) == set(POSITIONS.values()), "Fixture block entities are incomplete")
    (evidence / "chunk-8-8.nbt.zlib").write_bytes(compressed)
    write(evidence / "generator-roots.json", roots)
    return compressed, roots


def check_current(roots: dict, remaining: int = 1_100) -> None:
    coal, lava = roots["coal"], roots["lava"]
    require(coal == {"schema": 1, "energy": 20_000, "duration": 1_600, "remaining": remaining, "fuel": {}},
            "Coal accounting differs from furnace burn time, full-buffer pause or export debit")
    require(lava["schema"] == 1 and lava["energy"] == 20_000 and lava["duration"] == 20_000
            and lava["remaining"] == 19_500 and lava["fuel"] == {"id": "minecraft:bucket", "Count": 1},
            "Lava fuel/container accounting differs")
    require(roots["future"]["schema"] == 2 and roots["future"]["fuel"]["Count"] == 3
            and roots["corrupt"]["energy"] == 20_001 and roots["corrupt"]["fuel"]["Count"] == 3,
            "Retained unsupported fuel was lost")


def oversize(runtime: Path, evidence: Path) -> bytes:
    """Replace the unique future-root string with 8,192 bytes, only in the disposable chunk."""
    path = runtime / "world/region/r.0.0.mca"
    before = path.read_bytes()
    compressed = regions._region_chunk(before, 8, 8)
    expanded = zlib.decompress(compressed)
    key, value = b"extension", b"retain-verbatim"
    needle = b"\x08" + struct.pack(">H", len(key)) + key + struct.pack(">H", len(value)) + value
    require(expanded.count(needle) == 1, "Oversized fixture marker is not unique")
    replacement = b"\x0a" + struct.pack(">H", len(key)) + key
    # Keep each array within the existing bounded forensic reader's 4096-entry limit; the aggregate root still
    # exceeds 8192 bytes. Neither production budgets nor the independent NBT reader are relaxed for the fixture.
    for part in (b"part0", b"part1"):
        replacement += b"\x07" + struct.pack(">H", len(part)) + part + struct.pack(">i", 4_096) + bytes(4_096)
    replacement += b"\x00"
    patched = zlib.compress(expanded.replace(needle, replacement))
    index = (8 + 8 * 32) * 4
    offset = int.from_bytes(before[index:index + 3], "big") * 4_096
    sectors = before[index + 3]
    record = struct.pack(">I", len(patched) + 1) + b"\x02" + patched
    require(len(record) <= sectors * 4_096, "Patch must fit the original chunk allocation")
    result = bytearray(before)
    result[offset:offset + sectors * 4_096] = record + bytes(sectors * 4_096 - len(record))
    (evidence / "region-before-patch.mca").write_bytes(before)
    path.write_bytes(result)
    require(regions._region_chunk(bytes(result), 8, 8) == patched, "Patch failed to round-trip")
    write(evidence / "patch.json", {"chunk": [8, 8], "root": ROOT_KEY, "array_bytes": 8_192, "array_parts": 2,
                                   "before_sha256": hashlib.sha256(compressed).hexdigest(),
                                   "after_sha256": hashlib.sha256(patched).hexdigest()})
    return patched


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    for flag in ("runtime-template", "source-world", "v17-jar", "host-jar", "work-root"):
        parser.add_argument("--" + flag, type=Path, required=True)
    parser.add_argument("--fixture-jar", type=Path)
    parser.add_argument("--java", default="C:/Program Files/Java/jdk-17.0.7/bin/java.exe")
    args = parser.parse_args()
    for key in ("runtime_template", "source_world", "v17_jar", "host_jar", "work_root"):
        setattr(args, key, getattr(args, key).resolve())
    work = args.work_root
    require(not work.exists(), "Refusing to reuse a server/evidence directory")
    require(args.runtime_template not in work.parents and args.source_world not in work.parents,
            "Disposable runtime must be disjoint from historical inputs")
    old = artifact(args.v17_jar, "1.20.1-1.7.0-dev")
    current = artifact(args.host_jar, "1.20.1-1.8.0-dev")
    consumer = artifact(args.fixture_jar.resolve()) if args.fixture_jar else None
    work.mkdir(parents=True)
    runtime = work / "server"
    runtime.mkdir()
    source_identity = world_copy.copy_world(args.source_world, runtime / "world")
    shutil.copytree(args.runtime_template / "libraries", runtime / "libraries")
    mods = runtime / "mods"
    mods.mkdir()
    if consumer:
        shutil.copyfile(consumer["path"], mods / consumer["name"])
    port = server.allocate_port()
    properties = server.write_server_configuration(runtime, port, True)
    write(work / "inputs.json", {"old": old, "current": current, "fixture": consumer,
                               "source_world": str(args.source_world), "server_properties_sha256": properties})
    write(work / "source-world-files.json", source_identity)
    command = rocket._server_command(args.java)
    command[2] = "-Xmx2G"
    receipts = []

    def phase(name: str, jar: dict, actions=None, expected_save_refusal=False):
        directory = work / name
        directory.mkdir()
        # Only remove the exact host files this harness installed in its newly-created mods directory.
        for host in (old, current):
            target = mods / host["name"]
            if target.exists():
                target.unlink()
        shutil.copyfile(jar["path"], mods / jar["name"])
        require(server.digest_file(mods / jar["name"]) == jar["sha256"], "Installed host hash differs")
        write(directory / "launch.json", {"command": command, "cwd": str(runtime), "host": jar})
        commands = []
        receipt = {"phase": name, "result": "IN_PROGRESS"}
        process = None

        def query(text: str, marker: str, timeout=60):
            start = len(process.lines)
            commands.append(text)
            process.command(text)
            return process.wait_for(re.compile(marker), timeout, start_at=start)

        try:
            process = server.CapturedProcess(command, runtime, directory / "stdout.txt")
            process.wait_for(server.READY_MARKER, 300)
            query("forceload add 128 128", FORCELOAD_RESULT.pattern)
            query("forceload query 128 128", FORCELOAD_QUERY.pattern)
            time.sleep(1)
            if actions:
                actions(process, query)
            if jar == current:
                start = len(process.lines)
                # The fixture logs each row and then emits console feedback. Wait for the last feedback,
                # not the earlier logger row: otherwise a previous command's duplicate can satisfy this query.
                query("arce combustion release-test report", REPORT_COMPLETE.pattern)
                reports = [line.strip() for line in process.lines[start:] if "ARCE_COMBUSTION_REPORT" in line]
                for cell in ("future", "corrupt"):
                    require(any(f"cell={cell}" in line and "repair=true energy_cap=false item_cap=false" in line
                                for line in reports), "Unsupported fixture exposes a capability")
                receipt["reports"] = reports
            query("save-all flush", server.SAVE_MARKER.pattern, 300)
            commands.append("stop")
            process.command("stop")
            require(process.finish(300) == 0, "Packaged server did not stop cleanly")
            findings = server.scan_log(process.lines)
            if expected_save_refusal:
                require(findings and "Refusing chunk save with oversized combustion input" in "".join(process.lines),
                        "Chunk save did not explicitly refuse oversized input")
                for finding in findings:
                    require(EXPECTED_SAVE_ERROR.search(finding) is not None
                            or "Exception caught during firing event: Refusing chunk save with oversized combustion input" in finding,
                            "Unexpected log finding: " + finding)
                receipt["expected_refusal_findings"] = findings
            else:
                require(not findings, "Unexpected packaged-server findings: " + json.dumps(findings))
            receipt.update(result="PASS", exit_code=0, log_counts=server.log_audit_counts(process.lines))
        except BaseException as error:
            receipt.update(result="FAIL", error=f"{type(error).__name__}: {error}")
            if process:
                process.abort()
            raise
        finally:
            write(directory / "receipt.json", receipt)
            write(directory / "commands.json", commands)
            for log in ("latest.log", "debug.log"):
                if (runtime / "logs" / log).exists():
                    shutil.copyfile(runtime / "logs" / log, directory / log)
        receipts.append(receipt)
        return directory

    phase("v17-upgrade-baseline", old)
    directory = phase("seed-and-save", current,
                      lambda process, query: query("arce combustion release-test prepare", REPORT_COMPLETE.pattern))
    _, original = capture(runtime, directory)
    check_current(original)
    for name in ("restart-1", "restart-2"):
        directory = phase(name, current)
        _, restored = capture(runtime, directory)
        require(restored == original, "Same-world restart rewrote fuel, credit or unsupported roots")
    def export(process, query):
        query("arce combustion release-test export", "ARCE_COMBUSTION_EXPORT amount=1000")
        time.sleep(2)
    directory = phase("resume-export-and-save", current, export)
    _, exported = capture(runtime, directory)
    check_current(exported, 1_075)
    require(exported["future"] == original["future"] and exported["corrupt"] == original["corrupt"],
            "Resource export rewrote unsupported neighbours")
    patch_dir = work / "oversized-patch"
    patch_dir.mkdir()
    oversized = oversize(runtime, patch_dir)
    directory = phase("oversized-refusal", current, export, expected_save_refusal=True)
    after, _ = capture(runtime, directory)
    require(after == oversized, "Failed save overwrote the on-disk oversized chunk")
    require(world_copy.inventory(args.source_world) == source_identity, "Historical source world was modified")
    require(server.digest_file(args.v17_jar) == old["sha256"] and server.digest_file(args.host_jar) == current["sha256"],
            "Input JAR changed during the probe")
    write(work / "summary.json", {"result": "PASS", "phases": receipts, "same_world_restarts": 2,
                                 "source_world_unchanged": True, "oversized_chunk_unchanged": True,
                                 "client_evidence": "NOT_PERFORMED", "required_gates_passed": False})
    print("PASS: packaged combustion generation, two restarts, resumed export and oversized chunk refusal")


if __name__ == "__main__":
    main()
