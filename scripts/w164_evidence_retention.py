#!/usr/bin/env python3
"""R141: losslessly package only the two authorized superseded W164 trees.

Each manifest maps the original relative paths to exact bytes in a solid archive.
Restore into a NEW directory; never extract over live evidence or sealed packets.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil
import stat
import tarfile
import tempfile


AUTHORIZED = ("verification-9336b0fd4", "full-final-2")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def relative_path(name):
    path = PurePosixPath(name)
    require(name and not path.is_absolute() and ".." not in path.parts
            and "\\" not in name and ":" not in name, "Unsafe evidence path")
    return path


def inventory(directory):
    files, directories = {}, []
    for path in [directory, *sorted(directory.rglob("*"))]:
        info = path.lstat()
        require(not (getattr(info, "st_file_attributes", 0) & stat.FILE_ATTRIBUTE_REPARSE_POINT)
                and not path.is_symlink(), "Evidence links are not supported")
        require(path.resolve().is_relative_to(directory.resolve()), "Evidence escaped its tree")
        name = path.relative_to(directory).as_posix()
        if path.is_dir():
            if path != directory:
                directories.append(name)
        else:
            require(stat.S_ISREG(info.st_mode), "Evidence must contain regular files")
            files[name] = dict(bytes=info.st_size, sha256=digest(path))
    return dict(files=files, directories=directories)


def sealed_checksums(directory):
    count = 0
    for manifest in directory.rglob("*"):
        if manifest.name not in ("SHA256SUMS.txt", "checksums.txt"):
            continue
        for line in manifest.read_text(encoding="utf-8-sig").splitlines():
            if not line.strip():
                continue
            expected, name = line.split(maxsplit=1)
            name = name.lstrip("*")
            target = manifest.parent / relative_path(name)
            require(target.resolve().is_relative_to(directory.resolve()), "Sealed path escaped packet")
            require(target.is_file() and digest(target) == expected.lower(),
                    f"Sealed checksum mismatch: {name}")
            count += 1
    return count


def verify(root, manifest_path, destination):
    metadata = json.loads(manifest_path.read_text(encoding="utf-8"))
    name = metadata["original_directory"]
    require(name in AUTHORIZED, "Directory is not authorized by R141")
    archive = root / relative_path(metadata["archive"])
    require(digest(archive) == metadata["archive_sha256"], "Archive hash mismatch")
    destination.mkdir(parents=True, exist_ok=False)
    restored = destination / name
    restored.mkdir()
    expected = metadata["inventory"]
    for directory in expected["directories"]:
        (restored / relative_path(directory)).mkdir(parents=True, exist_ok=True)
    with tarfile.open(archive, "r:xz") as stored:
        members = stored.getmembers()
        require(len(members) == len(expected["files"])
                and {member.name for member in members} == set(expected["files"]),
                "Archive member list differs")
        for member in members:
            require(member.isfile(), "Unexpected non-file archive member")
            target = restored / relative_path(member.name)
            target.parent.mkdir(parents=True, exist_ok=True)
            with stored.extractfile(member) as source, target.open("xb") as output:
                shutil.copyfileobj(source, output)
    require(inventory(restored) == expected, "Reconstructed evidence differs")
    sealed = sealed_checksums(restored)
    require(sealed == metadata["sealed_checksums"], "Sealed checksum count differs")
    return dict(verified_files=len(expected["files"]), sealed_checksums=sealed,
                original_bytes=sum(row["bytes"] for row in expected["files"].values()))


def pack(root, name, scratch):
    require(name in AUTHORIZED, "Directory is not authorized by R141")
    root = root.resolve()
    source = root / name
    require(source.resolve() == source and source.parent == root, "Input path changed")
    archive_name = f"r141-{name}.tar.xz"
    manifest_name = f"r141-{name}-retention.json"
    for target in (root / archive_name, root / manifest_name):
        if target.exists():
            raise FileExistsError(target)
    before = inventory(source)
    checksums = sealed_checksums(source)
    scratch.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="r141-retention-", dir=scratch) as temporary:
        temporary = Path(temporary)
        archive = temporary / archive_name
        with tarfile.open(archive, "x:xz", preset=6) as output:
            for path in before["files"]:
                output.add(source / path, arcname=path, recursive=False)
        metadata = dict(schema=1, authorization="Root R141, TASK W164, 2026-10-28",
                        original_directory=name, archive=archive_name,
                        archive_sha256=digest(archive), archive_bytes=archive.stat().st_size,
                        inventory=before, sealed_checksums=checksums,
                        recovery="Run w164_evidence_retention.py verify with a new --destination; "
                                 "original paths are beneath that destination.")
        manifest = temporary / manifest_name
        manifest.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
        verify(temporary, manifest, temporary / "first-reconstruction")
        # Exclusive creation; an interrupted copy never licenses removal of inputs.
        for path in (archive, manifest):
            with path.open("rb") as stream, (root / path.name).open("xb") as output:
                shutil.copyfileobj(stream, output)
        verify(root, root / manifest_name, temporary / "published-reconstruction")
        require(inventory(source) == before, "Evidence changed during packaging")
        # R141 explicitly permits replacing these loose copies with verified archives.
        require(source.resolve() == root / name and source.parent == root, "Cleanup target changed")
        shutil.rmtree(source)
    return root / manifest_name


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("pack", "verify"))
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--name", choices=AUTHORIZED, required=True)
    parser.add_argument("--scratch", type=Path)
    parser.add_argument("--destination", type=Path)
    args = parser.parse_args()
    if args.action == "pack":
        require(args.scratch is not None, "pack requires --scratch outside evidence")
        require(not args.scratch.resolve().is_relative_to(args.root.resolve()),
                "Scratch must be outside evidence")
        print(pack(args.root, args.name, args.scratch))
    else:
        require(args.destination is not None, "verify requires a new --destination")
        manifest = args.root / f"r141-{args.name}-retention.json"
        print(json.dumps(verify(args.root, manifest, args.destination)))


if __name__ == "__main__":
    main()
