"""Create or inspect campaign directories and seal regular files with SHA-256."""

import hashlib
import os
import re
import shutil
from pathlib import Path

from .common import child, safe_path, write_json

MIN_FREE_BYTES = 10 * 1024 ** 3
FOLDERS = ("candidate", "checklists", "fixtures", "phase1-prep", "phase2-v1",
           "phase3-ds", "phase4-v2", "phase5-ref", "results")
MANIFEST = "SHA256SUMS.txt"


def check_free_space(paths, disk_usage=shutil.disk_usage):
    """Check every named filesystem, including nonexistent output descendants."""
    readings = {}
    for path in paths:
        ancestor = safe_path(path)
        while not ancestor.exists():
            if ancestor == ancestor.parent:
                raise ValueError(f"Filesystem does not exist: {ancestor}")
            ancestor = ancestor.parent
        free = disk_usage(ancestor).free
        readings[str(ancestor)] = free
        if free < MIN_FREE_BYTES:
            raise ValueError(f"Less than 10 GiB free at {ancestor}: {free} bytes")
    return readings


def identity_values(identity, short_sha):
    identity = dict(identity)
    if re.fullmatch(r"[0-9a-f]{7,40}", short_sha) is None:
        raise ValueError("Expected a lowercase short commit SHA (7 to 40 digits)")
    if re.fullmatch(r"[0-9a-f]{40}", identity.get("git_commit", "")) is None:
        raise ValueError("Expected a full git_commit SHA")
    if not identity["git_commit"].startswith(short_sha):
        raise ValueError("Short SHA does not identify git_commit")
    if re.fullmatch(r"[0-9a-f]{64}", identity.get("jar_sha256", "")) is None:
        raise ValueError("Expected jar_sha256")
    for key in ("forge", "java"):
        if not isinstance(identity.get(key), str) or not identity[key].strip():
            raise ValueError(f"Expected {key}")
    return {**identity, "schema_version": 1}


def evidence_root(root, short_sha, identity, *, check=False, drives=(), disk_usage=shutil.disk_usage):
    identity = identity_values(identity, short_sha)
    base = safe_path(root)
    target = child(base, short_sha)
    check_free_space((target, *drives), disk_usage)
    if check:
        for folder in FOLDERS:
            if not child(target, folder).is_dir():
                raise ValueError(f"Missing campaign folder: {folder}")
        import json
        actual = json.loads(child(target, "candidate/IDENTITY.json").read_text(encoding="utf-8"))
        if actual != identity:
            raise ValueError("Candidate identity differs")
        return target
    if target.exists():
        raise FileExistsError(f"Evidence root already exists: {target}")
    base.mkdir(parents=True, exist_ok=True)
    target.mkdir()  # Exclusive even if another creator wins the race.
    for folder in FOLDERS:
        child(target, folder).mkdir()
    write_json(child(target, "candidate/IDENTITY.json"), identity)
    return target


def regular_files(root):
    root = safe_path(root)
    if not root.is_dir():
        raise ValueError("Evidence folder does not exist")
    files = {}
    entries = 0
    for directory, dirs, names in os.walk(root, followlinks=False):
        for name in [*dirs, *names]:
            path = safe_path(Path(directory) / name)
            entries += 1
            if entries > 100000:
                raise ValueError("Evidence tree exceeds 100000 entries")
            if not path.is_dir() and not path.is_file():
                raise ValueError(f"Nonregular evidence entry: {path}")
        for name in names:
            path = Path(directory) / name
            relative = path.relative_to(root).as_posix()
            if relative != MANIFEST:
                if any(c in relative for c in "\n\r\\"):
                    raise ValueError("Unrepresentable checksum filename")
                files[relative] = path
    return files


def digest_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def write_checksums(root):
    root = safe_path(root)
    files = regular_files(root)
    # Existing seals are never silently overwritten.
    with child(root, MANIFEST).open("x", encoding="utf-8", newline="\n") as stream:
        for name in sorted(files):
            stream.write(f"{digest_file(files[name])}  {name}\n")
    return len(files)


def verify_checksums(root):
    root = safe_path(root)
    files = regular_files(root)
    expected = {}
    for line in child(root, MANIFEST).read_text(encoding="utf-8").splitlines():
        match = re.fullmatch(r"([0-9a-f]{64})  (.+)", line)
        if match is None:
            raise ValueError("Malformed checksum line")
        digest, name = match.groups()
        path = child(root, name)
        if path.relative_to(root).as_posix() != name or name == MANIFEST or name in expected:
            raise ValueError("Unsafe or duplicate checksum name")
        expected[name] = digest
    if files.keys() != expected.keys():
        raise ValueError("Checksum inventory differs (missing or extra file)")
    for name, path in files.items():
        if digest_file(path) != expected[name]:
            raise ValueError(f"Checksum mismatch: {name}")
    return len(files)
