from scripts.collect_v002_manual_evidence import COMMITTED_BUNDLE
from scripts.collect_v002_manual_evidence import MAX_BUNDLE_ENTRIES
from scripts.collect_v002_manual_evidence import MAX_JSON_BYTES
from scripts.collect_v002_manual_evidence import RECORD_NAME
from scripts.collect_v002_manual_evidence import collect_evidence
from scripts.collect_v002_manual_evidence import extract_log_excerpt
from scripts.collect_v002_manual_evidence import inspect_png
from scripts.collect_v002_manual_evidence import validate_bundle
from tests.manual_evidence_cases.png import make_png
from unittest.mock import patch
import hashlib
import json
import struct


class PayloadBoundsCases:
    """PNG and archived payload bounds."""

    def test_png_crc_and_privacy_metadata_are_rejected(self) -> None:
        valid = self.build / "valid.png"
        valid.write_bytes(make_png())
        corrupted = self.build / "corrupted.png"
        damaged = bytearray(make_png())
        damaged[-1] ^= 1
        corrupted.write_bytes(damaged)
        metadata = self.build / "metadata.png"
        metadata.write_bytes(make_png(metadata=(b"tEXt", b"Author\0private")))

        self.assertEqual(640, inspect_png(valid)["width"])
        with self.assertRaisesRegex(ValueError, "CRC mismatch"):
            inspect_png(corrupted)
        with self.assertRaisesRegex(ValueError, "privacy-bearing PNG metadata"):
            inspect_png(metadata)

    def test_unknown_png_ancillary_chunk_and_chunk_count_are_rejected(self) -> None:
        hidden = self.build / "hidden.png"
        hidden.write_bytes(
            make_png(metadata=(b"raNd", b"ghp_abcdefghijklmnopqrstuvwxyz"))
        )
        valid = self.build / "valid-count.png"
        valid.write_bytes(make_png())

        with self.assertRaisesRegex(ValueError, "unknown or nonessential"):
            inspect_png(hidden)
        with patch("scripts.collect_v002_manual_evidence.MAX_PNG_CHUNKS", 2):
            with self.assertRaisesRegex(ValueError, "chunks"):
                inspect_png(valid)

    def test_png_accepts_safe_physical_pixel_dimensions(self) -> None:
        for axis, unit in ((3779, 0), (3779, 1), (0x7FFFFFFF, 1)):
            with self.subTest(axis=axis, unit=unit):
                path = self.build / f"physical-{axis}-unit-{unit}.png"
                path.write_bytes(
                    make_png(
                        metadata=(b"pHYs", struct.pack(">IIB", axis, axis, unit))
                    )
                )

                details = inspect_png(path)

                self.assertEqual(["pHYs"], details["metadata_chunks"])

    def test_png_rejects_invalid_physical_pixel_dimensions(self) -> None:
        cases = {
            "wrong-size": (b"\0" * 8, "invalid size"),
            "zero-x": (struct.pack(">IIB", 0, 3779, 1), "31-bit unsigned"),
            "zero-y": (struct.pack(">IIB", 3779, 0, 1), "31-bit unsigned"),
            "large-x": (
                struct.pack(">IIB", 0x80000000, 3779, 1),
                "31-bit unsigned",
            ),
            "large-y": (
                struct.pack(">IIB", 3779, 0xFFFFFFFF, 1),
                "31-bit unsigned",
            ),
            "unknown-unit": (
                struct.pack(">IIB", 3779, 3779, 2),
                "unit must be 0 or 1",
            ),
        }
        for name, (payload, message) in cases.items():
            with self.subTest(name=name):
                path = self.build / f"physical-{name}.png"
                path.write_bytes(make_png(metadata=(b"pHYs", payload)))
                with self.assertRaisesRegex(ValueError, message):
                    inspect_png(path)

    def test_png_physical_dimensions_must_be_unique_and_precede_idat(self) -> None:
        payload = struct.pack(">IIB", 3779, 3779, 1)
        duplicate = self.build / "physical-duplicate.png"
        duplicate.write_bytes(
            make_png(
                metadata=(b"pHYs", payload),
                before_idat=((b"pHYs", payload),),
            )
        )
        late = self.build / "physical-after-idat.png"
        late.write_bytes(make_png(after_idat=((b"pHYs", payload),)))

        with self.assertRaisesRegex(ValueError, "must be unique"):
            inspect_png(duplicate)
        with self.assertRaisesRegex(ValueError, "must precede IDAT"):
            inspect_png(late)

    def test_png_dimensions_are_bounded(self) -> None:
        tiny = self.build / "tiny.png"
        tiny.write_bytes(make_png(320, 200))
        huge = self.build / "huge.png"
        huge.write_bytes(make_png(4097, 4096))

        with self.assertRaisesRegex(ValueError, "at least"):
            inspect_png(tiny)
        with self.assertRaisesRegex(ValueError, "pixel count"):
            inspect_png(huge)

    def test_log_excerpt_line_count_is_bounded(self) -> None:
        log = self.build / "long.log"
        log.write_text("line\n" * 201, encoding="utf-8")

        with self.assertRaisesRegex(ValueError, "exceeds 200 lines"):
            extract_log_excerpt(log, 1, 201, [])

    def test_tampered_archived_log_is_rejected(self) -> None:
        errors, _, output = self.collect(self.ready_session())
        self.assertEqual([], errors)
        target = output / "logs" / "client_startup_world.txt"
        target.write_text("tampered 198.51.100.5\n", encoding="utf-8")

        validation_errors, _ = validate_bundle(output, self.root)

        self.assertTrue(
            any("log excerpt metadata mismatch" in error for error in validation_errors),
            validation_errors,
        )

    def test_bundle_filesystem_entry_scan_is_bounded(self) -> None:
        errors, _, output = self.collect(
            self.ready_session(), "surplus-entry-bundle"
        )
        self.assertEqual([], errors)
        surplus = output / "surplus"
        surplus.mkdir()
        for index in range(MAX_BUNDLE_ENTRIES):
            (surplus / f"entry-{index:03d}").mkdir()

        validation_errors, _ = validate_bundle(output, self.root)

        self.assertTrue(
            any(
                f"more than {MAX_BUNDLE_ENTRIES} filesystem entries" in error
                for error in validation_errors
            ),
            validation_errors,
        )

    def test_oversized_archived_profile_snapshot_is_rejected_before_parsing(
        self,
    ) -> None:
        errors, _, output = self.collect(
            self.ready_session(), "oversized-archived-profile"
        )
        self.assertEqual([], errors)
        target = output / "client-profiles" / "matching-before.json"
        with target.open("wb") as stream:
            stream.truncate(MAX_JSON_BYTES + 1)

        validation_errors, _ = validate_bundle(output, self.root)

        self.assertTrue(
            any(
                f"archived matching before profile snapshot exceeds "
                f"{MAX_JSON_BYTES} bytes" in error
                for error in validation_errors
            ),
            validation_errors,
        )

    def test_oversized_archived_server_summary_is_rejected_before_parsing(
        self,
    ) -> None:
        errors, _, output = self.collect(
            self.ready_session(), "oversized-archived-summary"
        )
        self.assertEqual([], errors)
        target = output / "server" / "server-summary.json"
        with target.open("wb") as stream:
            stream.truncate(MAX_JSON_BYTES + 1)

        validation_errors, _ = validate_bundle(output, self.root)

        self.assertTrue(
            any(
                f"server harness summary archive exceeds {MAX_JSON_BYTES} bytes"
                in error
                for error in validation_errors
            ),
            validation_errors,
        )

    def test_deeply_nested_archived_json_is_rejected_without_crashing(self) -> None:
        session = self.ready_session()
        output = self.root / COMMITTED_BUNDLE
        errors, _ = collect_evidence(
            self.write_session(session, "deep-archive-session.json"),
            output,
            self.root,
        )
        self.assertEqual([], errors)
        payload = b"[" * 2000 + b"0" + b"]" * 2000
        target = output / "client-profiles" / "matching-before.json"
        target.write_bytes(payload)
        record_path = output / RECORD_NAME
        record = json.loads(record_path.read_text(encoding="utf-8"))
        snapshot = record["client_profiles"]["matching"]["before_snapshot"]
        digest = hashlib.sha256(payload).hexdigest()
        snapshot.update(
            {"sha256": digest, "source_sha256": digest, "size": len(payload)}
        )
        record_path.write_text(
            json.dumps(record, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        validation_errors, _ = validate_bundle(output, self.root)

        self.assertTrue(
            any(
                "archived matching before profile snapshot exceeds the JSON "
                "nesting limit" in error
                for error in validation_errors
            ),
            validation_errors,
        )
