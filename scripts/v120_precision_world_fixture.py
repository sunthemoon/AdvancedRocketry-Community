#!/usr/bin/env python3
"""Extract and verify bounded Precision Assembler chunks from a saved server world."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import struct
import sys
import uuid
import zlib
from pathlib import Path

if __package__:
    from .inspect_celestial_saved_data import NbtError, NbtReader
else:
    from inspect_celestial_saved_data import NbtError, NbtReader


ROOT = Path(__file__).resolve().parents[1]
SUMMARY = ROOT / "docs/work/v1.2.0-precision-assembler/packaged-restart/precision/summary.json"
FIXTURE = ROOT / "docs/work/v1.2.0-migration/fixtures/precision-final"
CHUNKS = ((8, 8), (8, 9), (9, 8), (9, 9))
CONTROLLER = (143, 80, 143)
INPUTS = ((142, 80, 143), (144, 80, 143), (142, 80, 144),
          (144, 80, 144), (142, 80, 145))
OUTPUTS = ((144, 80, 145), (142, 80, 146))
ENERGY = (144, 80, 146)
PORTS = set(INPUTS + OUTPUTS + (ENERGY,))
MOD = "advancedrocketrycommunity:"
SHA256 = re.compile(r"[0-9a-f]{64}\Z")
MAX_REGION_BYTES = 16 * 1024 * 1024
MAX_COMPRESSED_CHUNK = 128 * 1024
MAX_EXPANDED_CHUNK = 1024 * 1024


class FixtureError(ValueError):
    """A saved-world fixture failed a bounded or semantic check."""


def _sha256(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def _read_regular(path: Path, limit: int) -> bytes:
    if path.is_symlink() or not path.is_file() or path.stat().st_size > limit:
        raise FixtureError(f"Missing, linked or oversized file: {path}")
    content = path.read_bytes()
    if len(content) > limit:
        raise FixtureError(f"File changed or exceeded its bound: {path}")
    return content


def _summary(path: Path) -> tuple[dict[str, object], str]:
    source = _read_regular(path, 64 * 1024)
    try:
        summary = json.loads(source)
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise FixtureError("Precision summary is not valid JSON") from exc
    if not isinstance(summary, dict) or summary.get("schema_version") != 1:
        raise FixtureError("Precision summary has an unsupported schema")
    final = summary.get("after_final_restart")
    if (summary.get("same_world_verified") is not True
            or summary.get("exact_once_output_verified") is not True
            or summary.get("fixture_crosses_chunk_boundary") is not True
            or summary.get("completed") != final
            or summary.get("stable_after_completion") != final):
        raise FixtureError("Precision summary lacks stable same-world completion evidence")
    region = summary.get("region_after_final_restart")
    if (not isinstance(region, dict) or not isinstance(region.get("sha256"), str)
            or SHA256.fullmatch(region["sha256"]) is None):
        raise FixtureError("Precision summary has no final region hash")
    return summary, _sha256(source)


def _region_chunk(region: bytes, x: int, z: int) -> bytes:
    if len(region) < 8192:
        raise FixtureError("Region header is truncated")
    index = 4 * ((x & 31) + 32 * (z & 31))
    sector = int.from_bytes(region[index:index + 3], "big")
    sector_count = region[index + 3]
    start = sector * 4096
    if sector < 2 or sector_count < 1 or start + sector_count * 4096 > len(region):
        raise FixtureError(f"Chunk {x},{z} has an invalid region allocation")
    length = struct.unpack_from(">I", region, start)[0]
    if (length < 2 or length > MAX_COMPRESSED_CHUNK + 1
            or length + 4 > sector_count * 4096 or region[start + 4] != 2):
        raise FixtureError(f"Chunk {x},{z} is missing or is not bounded zlib NBT")
    return region[start + 5:start + 4 + length]


def _decode_chunk(compressed: bytes, x: int, z: int) -> dict[str, object]:
    if len(compressed) > MAX_COMPRESSED_CHUNK:
        raise FixtureError(f"Chunk {x},{z} exceeds the compressed-size bound")
    try:
        stream = zlib.decompressobj()
        expanded = stream.decompress(compressed, MAX_EXPANDED_CHUNK + 1)
        if (len(expanded) > MAX_EXPANDED_CHUNK or not stream.eof
                or stream.unconsumed_tail or stream.unused_data):
            raise FixtureError(f"Chunk {x},{z} exceeds its NBT bound or has trailing bytes")
        root = NbtReader(expanded).read_root()
    except (NbtError, zlib.error, struct.error) as exc:
        raise FixtureError(f"Chunk {x},{z} contains invalid NBT") from exc
    if not isinstance(root, dict) or root.get("xPos") != x or root.get("zPos") != z:
        raise FixtureError(f"Chunk {x},{z} has a mismatched coordinate")
    return root


def _position(data: object) -> tuple[int, int, int]:
    if not isinstance(data, dict):
        raise FixtureError("Machine position is not a compound")
    position = tuple(data.get(axis) for axis in ("x", "y", "z"))
    if len(position) != 3 or any(type(value) is not int for value in position):
        raise FixtureError("Machine position has invalid coordinates")
    return position


def _stack(data: object) -> str:
    if data == {}:
        return "minecraft:air:0"
    if not isinstance(data, dict) or set(data) != {"id", "Count"}:
        raise FixtureError("Machine Item stack has unexpected fields")
    item_id, count = data["id"], data["Count"]
    if not isinstance(item_id, str) or type(count) is not int or not 1 <= count <= 64:
        raise FixtureError("Machine Item stack has invalid identity or count")
    return f"{item_id}:{count}"


def _canonical_uuid(value: object) -> str:
    if not isinstance(value, str):
        raise FixtureError("Machine UUID is not a string")
    try:
        parsed = uuid.UUID(value)
    except ValueError as exc:
        raise FixtureError("Machine UUID is invalid") from exc
    if str(parsed) != value:
        raise FixtureError("Machine UUID is not canonical")
    return value


def _validate_machine(chunks: dict[tuple[int, int], bytes], summary: dict[str, object]) -> str:
    entities: dict[tuple[int, int, int], dict[str, object]] = {}
    for (x, z), compressed in chunks.items():
        root = _decode_chunk(compressed, x, z)
        encoded = root.get("block_entities")
        if not isinstance(encoded, list):
            raise FixtureError(f"Chunk {x},{z} has no block-entity list")
        for block_entity in encoded:
            if not isinstance(block_entity, dict):
                raise FixtureError("Block entity is not a compound")
            if block_entity.get("id") not in (MOD + "precision_assembler",
                                               MOD + "precision_assembler_port"):
                continue
            position = _position(block_entity)
            if position[0] >> 4 != x or position[2] >> 4 != z or position in entities:
                raise FixtureError("Machine BlockEntity is duplicated or in the wrong chunk")
            entities[position] = block_entity
    if set(entities) != {CONTROLLER} | PORTS:
        raise FixtureError("Fixture does not contain exactly one controller and eight ports")

    final = summary["after_final_restart"]
    if not isinstance(final, dict):
        raise FixtureError("Precision summary has no final resource report")
    controller = entities[CONTROLLER]
    lifecycle = controller.get("arce_multiblock")
    process = controller.get("arce_process")
    if (controller.get("id") != MOD + "precision_assembler"
            or not isinstance(lifecycle, dict) or not isinstance(process, dict)):
        raise FixtureError("Precision controller roots are missing")
    machine_id = _canonical_uuid(lifecycle.get("machine_instance_id"))
    positions = lifecycle.get("parts")
    if (lifecycle.get("schema_version") != 1
            or lifecycle.get("formation_state") != final.get("formation")
            or lifecycle.get("generation") != final.get("generation")
            or not isinstance(positions, list)
            or {_position(part) for part in positions} != PORTS
            or len(positions) != len(PORTS)):
        raise FixtureError("Precision controller formation or part identity differs from the report")
    if (process.get("schema_version") != 1
            or process.get("state") != str(final.get("state", "")).lower()
            or process.get("resource_revision") != final.get("revision")
            or process.get("progress_ticks") != final.get("progress")
            or process.get("last_applied_transaction") != final.get("last_applied")
            or "arce_process_journal" in controller):
        raise FixtureError("Precision process NBT differs from the final restart report")
    _canonical_uuid(process["last_applied_transaction"])

    inputs = final.get("inputs")
    outputs = final.get("outputs")
    if (not isinstance(inputs, list) or len(inputs) != len(INPUTS)
            or not isinstance(outputs, list) or len(outputs) != len(OUTPUTS)
            or any(not isinstance(stack, str) for stack in inputs + outputs)):
        raise FixtureError("Precision summary has an incomplete Item report")
    expected_stacks = dict(zip(INPUTS, inputs))
    expected_stacks.update(zip(OUTPUTS, outputs))
    for position in PORTS:
        port = entities[position]
        resources = port.get("arce_precision_port")
        binding = port.get("arce_part_binding")
        if (port.get("id") != MOD + "precision_assembler_port"
                or not isinstance(resources, dict) or not isinstance(binding, dict)
                or resources.get("schema_version") != 1
                or binding.get("schema_version") != 1
                or binding.get("controller_level") != "minecraft:overworld"
                or _position(binding.get("controller")) != CONTROLLER
                or binding.get("machine_instance_id") != machine_id
                or binding.get("generation") != lifecycle["generation"]):
            raise FixtureError(f"Precision port {position} has invalid resources or binding")
        if position == ENERGY:
            if (resources.get("port_type") != "precision_assembler_energy_input_port"
                    or resources.get("energy") != final.get("energy")):
                raise FixtureError("Precision Energy port differs from the final report")
        else:
            port_type = "precision_assembler_item_input_port" if position in INPUTS \
                else "precision_assembler_item_output_port"
            if (resources.get("port_type") != port_type
                    or _stack(resources.get("item")) != expected_stacks[position]):
                raise FixtureError(f"Precision Item port {position} differs from the final report")
    return machine_id


def create(region_path: Path, output: Path, summary_path: Path = SUMMARY) -> dict[str, object]:
    if output.exists():
        raise FixtureError(f"Refusing to overwrite fixture directory: {output}")
    summary, summary_hash = _summary(summary_path)
    region = _read_regular(region_path, MAX_REGION_BYTES)
    region_hash = _sha256(region)
    if region_hash != summary["region_after_final_restart"]["sha256"]:
        raise FixtureError("Source region does not match the archived final-restart hash")
    chunks = {(x, z): _region_chunk(region, x, z) for x, z in CHUNKS}
    machine_id = _validate_machine(chunks, summary)
    manifest = {
        "schema_version": 1,
        "source_summary_sha256": summary_hash,
        "source_region_sha256": region_hash,
        "source_tested_commit": summary["tested_implementation_commit"],
        "artifact_sha256": summary["artifact_sha256"],
        "machine_instance_id": machine_id,
        "chunks": {f"c.{x}.{z}.nbt.zlib": _sha256(chunks[(x, z)]) for x, z in CHUNKS},
    }
    output.mkdir(parents=True)
    for (x, z), compressed in chunks.items():
        (output / f"c.{x}.{z}.nbt.zlib").write_bytes(compressed)
    (output / "manifest.json").write_text(
        json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8", newline="\n"
    )
    return manifest


def verify(fixture: Path = FIXTURE, summary_path: Path = SUMMARY) -> dict[str, object]:
    if fixture.is_symlink() or not fixture.is_dir():
        raise FixtureError("Precision fixture directory is missing or linked")
    manifest_source = _read_regular(fixture / "manifest.json", 16 * 1024)
    try:
        manifest = json.loads(manifest_source)
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise FixtureError("Precision fixture manifest is not valid JSON") from exc
    summary, summary_hash = _summary(summary_path)
    names = {f"c.{x}.{z}.nbt.zlib" for x, z in CHUNKS}
    if (not isinstance(manifest, dict) or manifest.get("schema_version") != 1
            or manifest.get("source_summary_sha256") != summary_hash
            or manifest.get("source_region_sha256") != summary["region_after_final_restart"]["sha256"]
            or manifest.get("source_tested_commit") != summary.get("tested_implementation_commit")
            or manifest.get("artifact_sha256") != summary.get("artifact_sha256")
            or not isinstance(manifest.get("chunks"), dict)
            or set(manifest["chunks"]) != names
            or {path.name for path in fixture.iterdir()} != names | {"manifest.json"}):
        raise FixtureError("Precision fixture manifest or file set disagrees with archived evidence")
    chunks: dict[tuple[int, int], bytes] = {}
    for x, z in CHUNKS:
        name = f"c.{x}.{z}.nbt.zlib"
        compressed = _read_regular(fixture / name, MAX_COMPRESSED_CHUNK)
        if _sha256(compressed) != manifest["chunks"][name]:
            raise FixtureError(f"Precision fixture chunk hash mismatch: {name}")
        chunks[(x, z)] = compressed
    machine_id = _validate_machine(chunks, summary)
    if machine_id != manifest.get("machine_instance_id"):
        raise FixtureError("Precision fixture machine identity changed")
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument("--create-from-region", type=Path)
    action.add_argument("--verify", action="store_true")
    parser.add_argument("--fixture", type=Path, default=FIXTURE)
    parser.add_argument("--summary", type=Path, default=SUMMARY)
    args = parser.parse_args()
    try:
        if args.verify:
            manifest = verify(args.fixture, args.summary)
        else:
            manifest = create(args.create_from_region, args.fixture, args.summary)
    except (FixtureError, OSError, KeyError, TypeError) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1
    print("[PASS] Precision saved-world fixture: four chunks, one controller, eight ports")
    print(f"[PASS] Source region SHA-256: {manifest['source_region_sha256']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
