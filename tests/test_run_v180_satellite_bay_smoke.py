"""Pure helpers of the W27b native satellite bay harness: the typed NBT reader and the refusal log audit."""
from __future__ import annotations

import struct
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))

import run_dedicated_server_smoke as smoke  # noqa: E402
import run_v180_satellite_bay_smoke as bay  # noqa: E402


def named(kind: int, name: str, payload: bytes) -> bytes:
    encoded = name.encode("utf-8")
    return bytes([kind]) + struct.pack(">H", len(encoded)) + encoded + payload


def string(text: str) -> bytes:
    encoded = text.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


class TypedReaderTest(unittest.TestCase):
    def test_types_are_kept_so_a_widened_value_differs(self) -> None:
        root = (named(3, "schema_version", struct.pack(">i", 1))
                + named(11, "owner", struct.pack(">i", 4) + struct.pack(">4i", 5, 6, 7, 8))
                + named(7, "extra", struct.pack(">i", 3) + bytes([1, 2, 3]))
                + named(9, "list", bytes([10]) + struct.pack(">i", 1) + named(1, "Count", bytes([1])) + b"\x00")
                + named(8, "id", string("advancedrocketrycommunity:data_satellite_package")) + b"\x00")
        kind, value = bay.TypedReader(named(10, "", root)).read_root()
        self.assertEqual(10, kind)
        self.assertEqual((3, 1), value["schema_version"])
        self.assertEqual((11, [5, 6, 7, 8]), value["owner"])
        self.assertEqual((7, [1, 2, 3]), value["extra"])
        self.assertEqual((9, [(10, {"Count": (1, 1)})]), value["list"])
        self.assertNotEqual((4, 1), value["schema_version"])

    def test_truncated_and_trailing_payloads_refuse(self) -> None:
        complete = named(10, "", named(3, "a", struct.pack(">i", 1)) + b"\x00")
        with self.assertRaises(smoke.SmokeError):
            bay.TypedReader(complete[:-2]).read_root()
        with self.assertRaises(smoke.SmokeError):
            bay.TypedReader(complete + b"\x00").read_root()

    def test_the_expected_roots_have_the_harness_fields(self) -> None:
        self.assertEqual({"schema_version", "owner", "package"}, set(bay.typed_root(True)[1]))
        self.assertEqual({"schema_version", "owner"}, set(bay.typed_root(False)[1]))
        self.assertEqual((1, 1), bay.typed_package()[1]["Count"])


class AuditTest(unittest.TestCase):
    EVENT = ("[12:00:00] [Server thread/ERROR] [ne.mi.ev.EventBus/EVENTBUS]: Exception caught during firing event: "
             + bay.SAVE_REFUSAL)
    CHUNK = "[12:00:00] [Server thread/ERROR] [minecraft/ChunkMap]: Failed to save chunk [10, 10]"
    GUARD = ("[12:00:00] [Server thread/WARN] [io.gi.su.ad.ro.ba.SatelliteBayBlockEntity/]: "
             "ARCE_SATELLITE_BAY_GUARDED pos=162, 0, 162 guard=QUARANTINED keepable=false")

    def test_a_paired_refusal_of_an_allowed_chunk_passes(self) -> None:
        report = bay.audit([self.GUARD, self.EVENT, self.CHUNK], {(10, 10)}, {bay.SAVE_REFUSAL},
                           {"162, 0, 162|QUARANTINED|false": 1})
        self.assertEqual(1, report["refusal_pairs"])
        self.assertEqual([(10, 10)], report["refused_chunks"])

    def test_another_chunk_an_unpaired_header_or_a_missing_guard_line_fails(self) -> None:
        with self.assertRaises(smoke.SmokeError):
            bay.audit([self.EVENT, self.CHUNK], {(12, 10)}, {bay.SAVE_REFUSAL}, {})
        with self.assertRaises(smoke.SmokeError):
            bay.audit([self.EVENT], {(10, 10)}, {bay.SAVE_REFUSAL}, {})
        with self.assertRaises(smoke.SmokeError):
            bay.audit([self.EVENT, self.CHUNK], {(10, 10)}, {bay.SAVE_REFUSAL},
                      {"162, 0, 162|QUARANTINED|false": 1})
        with self.assertRaises(smoke.SmokeError):
            bay.audit([self.EVENT.replace(bay.SAVE_REFUSAL, "other"), self.CHUNK], {(10, 10)},
                      {bay.SAVE_REFUSAL}, {})

    def test_one_first_event_startup_hold_is_allowed_per_start(self) -> None:
        hold = ("[12:00:00] [Server thread/WARN] [io.gi.su.ad.AdvancedRocketryCommunity/]: "
                "ARCE_FIRST_EVENT_HELD kind=first_moon_landing reason=SOURCE_UNCONFIRMED")
        self.assertEqual(1, bay.audit([hold], set(), set(), {})["first_event_startup_holds"])
        with self.assertRaises(smoke.SmokeError):
            bay.audit([hold, hold], set(), set(), {})
        with self.assertRaises(smoke.SmokeError):
            bay.audit([hold.replace("SOURCE_UNCONFIRMED", "ROOT_UNREADABLE")], set(), set(), {})

    def test_other_bay_audit_lines_fail(self) -> None:
        removed = self.GUARD.replace("ARCE_SATELLITE_BAY_GUARDED", "ARCE_SATELLITE_BAY_GUARDED_ROOT_REMOVED")
        with self.assertRaises(smoke.SmokeError):
            bay.audit([removed], set(), set(), {})


if __name__ == "__main__":
    unittest.main()
