import gzip
import hashlib
import json
import struct
import tempfile
import unittest
from pathlib import Path

from scripts.inspect_celestial_saved_data import (
    MAX_COLLECTION_ENTRIES,
    MAX_COMPRESSED_BYTES,
    NbtError,
    inspect_saved_data,
    write_report,
)


def _name(value: str) -> bytes:
    encoded = value.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def _named(tag_type: int, name: str, payload: bytes) -> bytes:
    return bytes((tag_type,)) + _name(name) + payload


def _string(value: str) -> bytes:
    return _name(value)


def _body(body_id: str, discovered_at: int, first_visit_at: int | None) -> bytes:
    payload = b"".join(
        (
            _named(8, "id", _string(body_id)),
            _named(4, "discovered_at", struct.pack(">q", discovered_at)),
            b""
            if first_visit_at is None
            else _named(4, "first_visit_at", struct.pack(">q", first_visit_at)),
            b"\x00",
        )
    )
    return payload


def _saved_data(
    bodies: list[tuple[str, int, int | None]], schema: int = 1
) -> bytes:
    body_payloads = [_body(*body) for body in bodies]
    data = b"".join(
        (
            _named(3, "schema_version", struct.pack(">i", schema)),
            _named(
                9,
                "bodies",
                b"\x0a" + struct.pack(">i", len(body_payloads)) + b"".join(body_payloads),
            ),
            b"\x00",
        )
    )
    root = b"\x0a" + _name("") + _named(10, "data", data) + b"\x00"
    return gzip.compress(root, mtime=0)


class CelestialSavedDataInspectorTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.root = Path(self.temporary_directory.name)

    def write_fixture(self, payload: bytes, name: str = "celestial.dat") -> Path:
        path = self.root / name
        path.write_bytes(payload)
        return path

    def test_valid_report_is_sorted_and_bound_to_source_hash(self) -> None:
        payload = _saved_data(
            [
                ("advancedrocketrycommunity:space", 30, None),
                ("advancedrocketrycommunity:earth", 10, 12),
            ]
        )
        path = self.write_fixture(payload)

        report = inspect_saved_data(path)

        self.assertEqual(1, report["schema_version"])
        self.assertEqual(2, report["body_count"])
        self.assertEqual(hashlib.sha256(payload).hexdigest(), report["source_sha256"])
        self.assertEqual(
            [
                {
                    "id": "advancedrocketrycommunity:earth",
                    "discovered_at": 10,
                    "first_visit_at": 12,
                },
                {
                    "id": "advancedrocketrycommunity:space",
                    "discovered_at": 30,
                },
            ],
            report["bodies"],
        )

    def test_future_schema_and_duplicate_body_are_rejected(self) -> None:
        future = self.write_fixture(_saved_data([], schema=2), "future.dat")
        duplicate = self.write_fixture(
            _saved_data(
                [
                    ("advancedrocketrycommunity:moon", 1, 1),
                    ("advancedrocketrycommunity:moon", 2, 2),
                ]
            ),
            "duplicate.dat",
        )

        with self.assertRaisesRegex(NbtError, "schema_version must equal 1"):
            inspect_saved_data(future)
        with self.assertRaisesRegex(NbtError, "duplicate body id"):
            inspect_saved_data(duplicate)

    def test_invalid_visit_order_and_resource_location_are_rejected(self) -> None:
        visit = self.write_fixture(
            _saved_data([("advancedrocketrycommunity:earth", 20, 19)]),
            "visit.dat",
        )
        identity = self.write_fixture(
            _saved_data([("AdvancedRocketryCommunity:earth", 1, None)]),
            "identity.dat",
        )

        with self.assertRaisesRegex(NbtError, "invalid first_visit_at"):
            inspect_saved_data(visit)
        with self.assertRaisesRegex(NbtError, "invalid id"):
            inspect_saved_data(identity)

    def test_malformed_gzip_and_oversized_file_are_rejected(self) -> None:
        malformed = self.write_fixture(b"not gzip", "malformed.dat")
        oversized = self.write_fixture(
            b"x" * (MAX_COMPRESSED_BYTES + 1), "oversized.dat"
        )

        with self.assertRaisesRegex(NbtError, "valid gzip-compressed NBT"):
            inspect_saved_data(malformed)
        with self.assertRaisesRegex(NbtError, "compressed bytes"):
            inspect_saved_data(oversized)

    def test_declared_collection_above_bound_is_rejected_before_payload_read(self) -> None:
        data = b"".join(
            (
                _named(3, "schema_version", struct.pack(">i", 1)),
                _named(
                    9,
                    "bodies",
                    b"\x0a" + struct.pack(">i", MAX_COLLECTION_ENTRIES + 1),
                ),
                b"\x00",
            )
        )
        root = b"\x0a" + _name("") + _named(10, "data", data) + b"\x00"
        path = self.write_fixture(gzip.compress(root, mtime=0), "collection.dat")

        with self.assertRaisesRegex(NbtError, "list length outside"):
            inspect_saved_data(path)

    def test_report_writer_is_deterministic_and_refuses_overwrite(self) -> None:
        report = {
            "source_sha256": "abc",
            "schema_version": 1,
            "bodies": [],
        }
        output = self.root / "nested" / "report.json"

        write_report(output, report)

        self.assertEqual(
            json.dumps(report, ensure_ascii=True, indent=2, sort_keys=True) + "\n",
            output.read_text(encoding="utf-8"),
        )
        with self.assertRaisesRegex(NbtError, "Refusing to overwrite"):
            write_report(output, report)


if __name__ == "__main__":
    unittest.main()
