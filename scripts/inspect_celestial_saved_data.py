#!/usr/bin/env python3
"""Read and validate the bounded v0.3 celestial SavedData NBT file."""

from __future__ import annotations

import argparse
import gzip
import hashlib
import io
import json
import re
import struct
import sys
from pathlib import Path


MAX_COMPRESSED_BYTES = 1024 * 1024
MAX_DECOMPRESSED_BYTES = 4 * 1024 * 1024
MAX_DEPTH = 16
MAX_TAGS = 4096
MAX_COLLECTION_ENTRIES = 4096
MAX_STRING_BYTES = 4096
MAX_BODIES = 128
RESOURCE_LOCATION = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+$")


class NbtError(ValueError):
    """A deterministic bounded-NBT validation failure."""


class NbtReader:
    def __init__(self, payload: bytes):
        self.payload = payload
        self.offset = 0
        self.tags = 0

    def read_root(self) -> object:
        tag_type = self._u8()
        if tag_type != 10:
            raise NbtError("NBT root must be a compound")
        self._string()
        value = self._payload(tag_type, 0)
        if self.offset != len(self.payload):
            raise NbtError("NBT has trailing bytes")
        return value

    def _payload(self, tag_type: int, depth: int) -> object:
        if depth > MAX_DEPTH:
            raise NbtError(f"NBT exceeds depth {MAX_DEPTH}")
        self.tags += 1
        if self.tags > MAX_TAGS:
            raise NbtError(f"NBT exceeds {MAX_TAGS} tags")
        if tag_type == 1:
            return self._unpack(">b", 1)
        if tag_type == 2:
            return self._unpack(">h", 2)
        if tag_type == 3:
            return self._unpack(">i", 4)
        if tag_type == 4:
            return self._unpack(">q", 8)
        if tag_type == 5:
            return self._unpack(">f", 4)
        if tag_type == 6:
            return self._unpack(">d", 8)
        if tag_type == 7:
            length = self._length("byte array")
            return list(self._bytes(length))
        if tag_type == 8:
            return self._string()
        if tag_type == 9:
            child_type = self._u8()
            length = self._length("list")
            if child_type == 0 and length != 0:
                raise NbtError("non-empty NBT list cannot use TAG_End")
            return [self._payload(child_type, depth + 1) for _ in range(length)]
        if tag_type == 10:
            value: dict[str, object] = {}
            while True:
                child_type = self._u8()
                if child_type == 0:
                    return value
                name = self._string()
                if name in value:
                    raise NbtError(f"duplicate compound key: {name}")
                value[name] = self._payload(child_type, depth + 1)
        if tag_type == 11:
            length = self._length("int array")
            return [self._unpack(">i", 4) for _ in range(length)]
        if tag_type == 12:
            length = self._length("long array")
            return [self._unpack(">q", 8) for _ in range(length)]
        raise NbtError(f"unknown NBT tag type: {tag_type}")

    def _length(self, description: str) -> int:
        value = self._unpack(">i", 4)
        if value < 0 or value > MAX_COLLECTION_ENTRIES:
            raise NbtError(
                f"NBT {description} length outside 0..{MAX_COLLECTION_ENTRIES}"
            )
        return value

    def _string(self) -> str:
        length = self._unpack(">H", 2)
        if length > MAX_STRING_BYTES:
            raise NbtError(f"NBT string exceeds {MAX_STRING_BYTES} bytes")
        try:
            return self._bytes(length).decode("utf-8", errors="strict")
        except UnicodeError as exc:
            raise NbtError("NBT string is not valid UTF-8") from exc

    def _u8(self) -> int:
        return self._unpack(">B", 1)

    def _unpack(self, shape: str, size: int) -> int | float:
        return struct.unpack(shape, self._bytes(size))[0]

    def _bytes(self, size: int) -> bytes:
        end = self.offset + size
        if size < 0 or end > len(self.payload):
            raise NbtError("NBT ended unexpectedly")
        value = self.payload[self.offset:end]
        self.offset = end
        return value


def inspect_saved_data(path: Path) -> dict[str, object]:
    if path.is_symlink() or not path.is_file():
        raise NbtError(f"SavedData file is missing or unsafe: {path}")
    compressed_size = path.stat().st_size
    if compressed_size > MAX_COMPRESSED_BYTES:
        raise NbtError(f"SavedData exceeds {MAX_COMPRESSED_BYTES} compressed bytes")
    compressed = path.read_bytes()
    if len(compressed) != compressed_size:
        raise NbtError("SavedData changed while it was being read")
    try:
        with gzip.GzipFile(fileobj=io.BytesIO(compressed)) as stream:
            payload = stream.read(MAX_DECOMPRESSED_BYTES + 1)
    except (OSError, EOFError) as exc:
        raise NbtError("SavedData is not valid gzip-compressed NBT") from exc
    if len(payload) > MAX_DECOMPRESSED_BYTES:
        raise NbtError(f"SavedData exceeds {MAX_DECOMPRESSED_BYTES} decompressed bytes")
    root = NbtReader(payload).read_root()
    if not isinstance(root, dict) or not isinstance(root.get("data"), dict):
        raise NbtError("SavedData root must contain a data compound")
    data = root["data"]
    schema = data.get("schema_version")
    bodies = data.get("bodies")
    if type(schema) is not int or schema != 1:
        raise NbtError("SavedData schema_version must equal 1")
    if not isinstance(bodies, list) or len(bodies) > MAX_BODIES:
        raise NbtError(f"SavedData bodies must be a list of at most {MAX_BODIES}")

    entries: list[dict[str, object]] = []
    seen: set[str] = set()
    for index, body in enumerate(bodies):
        if not isinstance(body, dict):
            raise NbtError(f"SavedData body {index} must be a compound")
        body_id = body.get("id")
        discovered = body.get("discovered_at")
        visited = body.get("first_visit_at")
        if (
            not isinstance(body_id, str)
            or len(body_id) > 128
            or RESOURCE_LOCATION.fullmatch(body_id) is None
        ):
            raise NbtError(f"SavedData body {index} has an invalid id")
        if body_id in seen:
            raise NbtError(f"SavedData contains duplicate body id: {body_id}")
        seen.add(body_id)
        if type(discovered) is not int or discovered < 0:
            raise NbtError(f"SavedData body {body_id} has invalid discovered_at")
        if visited is not None and (type(visited) is not int or visited < discovered):
            raise NbtError(f"SavedData body {body_id} has invalid first_visit_at")
        entry: dict[str, object] = {
            "id": body_id,
            "discovered_at": discovered,
        }
        if visited is not None:
            entry["first_visit_at"] = visited
        entries.append(entry)
    entries.sort(key=lambda entry: str(entry["id"]))
    return {
        "schema_version": 1,
        "source_file": path.name,
        "source_sha256": hashlib.sha256(compressed).hexdigest(),
        "compressed_bytes": len(compressed),
        "body_count": len(entries),
        "bodies": entries,
    }


def write_report(path: Path, report: dict[str, object]) -> None:
    if path.exists():
        raise NbtError(f"Refusing to overwrite SavedData report: {path}")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(report, ensure_ascii=True, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
        newline="\n",
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("saved_data", type=Path)
    parser.add_argument("--output", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        report = inspect_saved_data(args.saved_data)
        if args.output:
            write_report(args.output.resolve(), report)
    except (OSError, UnicodeError, NbtError, struct.error) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1
    print(
        f"[PASS] Celestial SavedData schema 1 contains {report['body_count']} "
        f"bounded body entries"
    )
    print(f"[PASS] Source SHA-256: {report['source_sha256']}")
    if args.output:
        print(f"[PASS] Report: {args.output.resolve()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
