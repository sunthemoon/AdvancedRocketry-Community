"""Retain verbatim native records for the W152 census, excluding unrelated spawn chunks.

The archive keeps each original region header, selected length-prefixed compressed
record and complete journal/player file. Replay builds a *derived* compact region;
its records, not the new sector offsets or region-file hash, equal the source.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path, PurePosixPath
import struct
import zipfile


def digest(file: Path) -> str:
    with file.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def preserve_native(world: Path, evidence: Path, stage: str, state: dict) -> dict:
    census = [(x, z) for x in range(-1, 3) for z in range(-1, 3)]
    if "recipient_data" in state: census.append((5, 0))
    names = {name.replace("\\", "/") for name in state["files"]}
    for x, z in census:
        path = world / "poi" / f"r.{x >> 5}.{z >> 5}.mca"
        if path.is_file(): names.add(str(path.relative_to(world)).replace("\\", "/"))
    if (world / "level.dat").is_file(): names.add("level.dat")
    if len(names) > 128:
        raise ValueError("Native input count exceeds its evidence bound")
    archive = evidence / (stage + "-native-inputs.zip")
    manifest = dict(format="w152-native-records-1", census=census, inputs={})
    total = 0
    with zipfile.ZipFile(archive, "x", compression=zipfile.ZIP_DEFLATED) as output:
        for name in sorted(names):
            file = world / name; row = dict(sha256=digest(file), size=file.stat().st_size)
            manifest["inputs"][name] = row
            # IOWorker may create a zero-length region before its first stored chunk.
            if file.suffix != ".mca" or row["size"] == 0:
                total += file.stat().st_size
                if total > 100_000_000: raise ValueError("Native input bytes exceed their evidence bound")
                output.write(file, name); row["entry"] = name; continue
            with file.open("rb") as stream:
                header = stream.read(8192)
                if len(header) != 8192: raise ValueError("Truncated native region header")
                row["header"] = "raw/" + name + ".header"; output.writestr(row["header"], header)
                row["records"] = []
                for x, z in census:
                    if file.name != f"r.{x >> 5}.{z >> 5}.mca": continue
                    slot = (x & 31) + (z & 31) * 32
                    location = struct.unpack_from(">I", header, slot * 4)[0]
                    record = dict(chunk=[x, z], slot=slot, location=location); row["records"].append(record)
                    if location == 0: continue
                    offset, sectors = location >> 8, location & 255
                    if offset < 2 or not sectors or (offset + sectors) * 4096 > row["size"]:
                        raise ValueError("Invalid native record allocation")
                    stream.seek(offset * 4096); prefix = stream.read(5)
                    length, compression = struct.unpack(">IB", prefix)
                    if not 1 < length <= 1_048_577 or length + 4 > sectors * 4096 or compression != 2:
                        raise ValueError("Unsupported native record envelope")
                    raw = prefix + stream.read(length - 1)
                    if len(raw) != length + 4: raise ValueError("Truncated native record")
                    total += len(raw)
                    if total > 100_000_000: raise ValueError("Native input bytes exceed their evidence bound")
                    record.update(entry=f"raw/{name}.slot-{slot}.bin", sha256=hashlib.sha256(raw).hexdigest())
                    output.writestr(record["entry"], raw)
        output.writestr("NATIVE-CENSUS.json", json.dumps(manifest, indent=2))
    if archive.stat().st_size > 50_000_000: raise ValueError("Native archive exceeds the per-file evidence limit")
    return dict(file=archive.name, sha256=digest(archive), format=manifest["format"],
                inputs={name: row["sha256"] for name, row in manifest["inputs"].items()})


def replay_native(archive: Path, world: Path) -> None:
    """Reconstruct only the observed slots in a new directory for the unchanged native reader."""
    if world.exists(): raise ValueError("Native replay destination already exists")
    with zipfile.ZipFile(archive) as source:
        if sum(entry.file_size for entry in source.infolist()) > 101_000_000:
            raise ValueError("Native replay exceeds its decoded evidence bound")
        manifest = json.loads(source.read("NATIVE-CENSUS.json"))
        if manifest["format"] != "w152-native-records-1": raise ValueError("Unknown native evidence format")
        if len(manifest["inputs"]) > 128: raise ValueError("Native replay input count exceeds its bound")
        world.mkdir()
        for name, row in manifest["inputs"].items():
            relative = PurePosixPath(name)
            if relative.is_absolute() or ".." in relative.parts or "\\" in name or ":" in name:
                raise ValueError("Unsafe native evidence path")
            file = world.joinpath(*relative.parts); file.parent.mkdir(parents=True, exist_ok=True)
            if "entry" in row:
                raw = source.read(row["entry"])
                if hashlib.sha256(raw).hexdigest() != row["sha256"]: raise ValueError("Native input hash differs")
                file.write_bytes(raw); continue
            original = source.read(row["header"])
            if len(original) != 8192: raise ValueError("Native header size differs")
            header = bytearray(8192); records = bytearray(); used = set()
            for record in row["records"]:
                slot = record["slot"]
                if not 0 <= slot < 1024 or slot in used: raise ValueError("Invalid/repeated native census slot")
                used.add(slot)
                if struct.unpack_from(">I", original, slot * 4)[0] != record["location"]:
                    raise ValueError("Census allocation differs from the original native header")
                if not record["location"]: continue
                raw = source.read(record["entry"])
                if hashlib.sha256(raw).hexdigest() != record["sha256"]: raise ValueError("Native record hash differs")
                length, compression = struct.unpack(">IB", raw[:5])
                if compression != 2 or len(raw) != length + 4: raise ValueError("Native record envelope differs")
                sectors = (len(raw) + 4095) // 4096
                if not 1 <= sectors <= 255: raise ValueError("Native replay record exceeds its sector bound")
                struct.pack_into(">I", header, slot * 4, ((2 + len(records) // 4096) << 8) | sectors)
                header[4096 + slot * 4:4100 + slot * 4] = original[4096 + slot * 4:4100 + slot * 4]
                records.extend(raw); records.extend(bytes(sectors * 4096 - len(raw)))
            file.write_bytes(header + records)
