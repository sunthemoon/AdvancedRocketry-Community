"""Exact native files used by W164's bounded census, without unrelated terrain.

All entity-region files, external chunks, journals, player data and metadata are
kept. Terrain/POI region files cover both recorded discovery rectangles. Each
retained file is byte-identical, including its full region header and padding;
no compacted/derived region substitutes for an original cut image.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import tarfile

from run_v180_predecessor_smoke import require


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def regions(old):
    result = set()
    for snapshot in (old["source_snapshot"], old["destination_snapshot"]):
        origin, bounds = snapshot["source_origin"], snapshot["bounding_box"]
        dimension = snapshot["source_dimension"]
        prefix = "" if dimension == "minecraft:overworld" else "dimensions/" + dimension.replace(":", "/") + "/"
        require(".." not in prefix and "\\" not in prefix, "Unsafe recorded dimension")
        min_x, max_x = (origin[0] + bounds[0] - 1) >> 4, (origin[0] + bounds[3] + 2) >> 4
        min_z, max_z = (origin[2] + bounds[2] - 1) >> 4, (origin[2] + bounds[5] + 2) >> 4
        require(1 <= (max_x - min_x + 1) * (max_z - min_z + 1) <= 16, "Invalid recorded discovery rectangle")
        for x in range(min_x, max_x + 1):
            for z in range(min_z, max_z + 1):
                for kind in ("region", "poi"):
                    result.add(f"{prefix}{kind}/r.{x >> 5}.{z >> 5}.mca")
    return result


def preserve_bounded(world: Path, evidence: Path, restart: int) -> dict:
    old = json.loads((evidence / "predecessor.json").read_text(encoding="utf-8"))
    required_regions = regions(old)
    archive = evidence / f"raw-{restart}.tar.xz"
    require(not archive.exists(), "Do not overwrite native evidence")
    hashes = {}; omitted = []
    with tarfile.open(archive, "x:xz", preset=6) as output:
        for path in sorted(world.rglob("*")):
            if not path.is_file(): continue
            name = path.relative_to(world).as_posix()
            if not (path.suffix in (".mca", ".mcc", ".dat", ".arce-pending") or path.name == "level.dat_old"): continue
            require(not path.is_symlink() and path.resolve().is_relative_to(world.resolve()), "Native input escaped its world")
            if path.suffix == ".mca" and path.parent.name != "entities" and name not in required_regions:
                omitted.append(name); continue
            hashes[name] = digest(path)
            # Stable archive metadata; the native file's own bytes are unmodified.
            member = tarfile.TarInfo(name); member.size = path.stat().st_size
            with path.open("rb") as stream: output.addfile(member, stream)
    require(archive.stat().st_size <= 50_000_000, "Native archive exceeds its per-file limit")
    with tarfile.open(archive, "r:xz") as stored:
        require(set(stored.getnames()) == set(hashes), "Native archive changed the input list")
        for name, expected in hashes.items():
            with stored.extractfile(name) as stream:
                require(hashlib.file_digest(stream, "sha256").hexdigest() == expected, "Native input bytes changed")
    manifest = dict(files=hashes, discovery_regions=sorted(required_regions), unobserved_terrain=omitted)
    manifest_path = evidence / f"raw-{restart}-hashes.json"
    with manifest_path.open("x", encoding="utf-8") as stream: json.dump(manifest, stream, indent=2)
    return dict(file=archive.name, sha256=digest(archive), files=len(hashes), bytes=archive.stat().st_size,
                manifest=manifest_path.name, manifest_sha256=digest(manifest_path), format="w164-exact-native-files-1")


def compact_case(row: dict, evidence: Path) -> None:
    """One solid archive, with separate namespaces and native hashes for every observed start.

    Called before the final case result is published. Only this case's newly
    created intermediate archives are removed, after all native bytes verify.
    """
    starts = [start for start in row["starts"] if "native_inputs" in start]
    if not starts: return
    target = evidence / "native-inputs.tar.xz"
    require(not target.exists(), "Do not overwrite a retained case archive")
    expected = {}; originals = []
    with tarfile.open(target, "x:xz", preset=6) as output:
        for start in starts:
            inputs = start["native_inputs"]; source = evidence / inputs["file"]
            require(source.resolve().parent == evidence.resolve() and digest(source) == inputs["sha256"],
                    "Intermediate native input changed")
            hashes = json.loads((evidence / inputs["manifest"]).read_text(encoding="utf-8"))["files"]
            prefix = f"restart-{start['restart']}/"
            with tarfile.open(source, "r:xz") as archive:
                require(set(archive.getnames()) == set(hashes), "Intermediate native input list changed")
                for member in archive.getmembers():
                    require(member.isfile(), "Unexpected non-file native input")
                    name = prefix + member.name
                    require(name not in expected, "Repeated native restart image")
                    expected[name] = hashes[member.name]
                    info = tarfile.TarInfo(name); info.size = member.size
                    with archive.extractfile(member) as stream: output.addfile(info, stream)
            originals.append((source, inputs["sha256"]))
    with tarfile.open(target, "r:xz") as archive:
        require(set(archive.getnames()) == set(expected), "Solid archive changed the native input list")
        for name, checksum in expected.items():
            with archive.extractfile(name) as stream:
                require(hashlib.file_digest(stream, "sha256").hexdigest() == checksum, "Solid archive changed native bytes")
    common = dict(file=target.name, sha256=digest(target), bytes=target.stat().st_size)
    row["native_archive"] = common
    for start in starts:
        inputs = start["native_inputs"]
        inputs.update(common, member_prefix=f"restart-{start['restart']}/", shared_archive=True)
    for source, checksum in originals:
        require(source.resolve().parent == evidence.resolve() and digest(source) == checksum,
                "Intermediate archive cleanup ownership changed")
        source.unlink()
