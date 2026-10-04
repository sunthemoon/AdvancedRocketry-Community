"""Pure bounded fixtures for the packaged combustion probe; no Java/server launch."""
import json
import struct
import tempfile
import unittest
import zlib
from pathlib import Path

from scripts import run_v180_combustion_smoke as probe


def string(name, value):
    key, text = name.encode(), value.encode()
    return b'\x08' + struct.pack('>H', len(key)) + key + struct.pack('>H', len(text)) + text


def region(payload):
    compressed = zlib.compress(payload)
    body = struct.pack('>I', len(compressed) + 1) + b'\x02' + compressed
    header = bytearray(8_192)
    index = (8 + 8 * 32) * 4
    header[index:index + 4] = b'\x00\x00\x02\x04'
    return bytes(header) + body + bytes(16_384 - len(body))


class CombustionSmokeTests(unittest.TestCase):
    def test_report_completion_waits_for_final_console_feedback(self):
        self.assertIsNone(probe.REPORT_COMPLETE.search(
            '[Server thread/INFO] [io.gi.su.ad.AdvancedRocketryCommunity/]: ARCE_COMBUSTION_REPORT cell=corrupt repair=true'))
        self.assertIsNotNone(probe.REPORT_COMPLETE.search(
            '[Server thread/INFO] [minecraft/MinecraftServer]: ARCE_COMBUSTION_REPORT cell=corrupt repair=true'))
        self.assertIsNone(probe.REPORT_COMPLETE.search(
            '[Server thread/INFO] [minecraft/MinecraftServer]: ARCE_COMBUSTION_REPORT cell=future repair=true'))

    def test_forceload_marker_handles_new_and_persisted_flags(self):
        self.assertIsNotNone(probe.FORCELOAD_RESULT.search('Marked chunk [8,8] to be force loaded'))
        self.assertIsNotNone(probe.FORCELOAD_RESULT.search('No chunks were marked for force loading'))
        self.assertIsNone(probe.FORCELOAD_RESULT.search('That position is not loaded'))
        self.assertIsNotNone(probe.FORCELOAD_QUERY.search('Chunk at [8, 8] in minecraft:overworld is marked for force loading'))
        self.assertIsNone(probe.FORCELOAD_QUERY.search('Chunk at [9, 8] in minecraft:overworld is marked for force loading'))

    def test_expected_save_error_is_coordinate_exact(self):
        for value in ('Failed to save chunk 8,8', 'Failed to save chunk [8, 8]'):
            self.assertIsNotNone(probe.EXPECTED_SAVE_ERROR.search(value))
        for value in ('Failed to save chunk 8,80', 'Failed to save chunk 9,8', 'Unrelated ERROR'):
            self.assertIsNone(probe.EXPECTED_SAVE_ERROR.search(value))

    def test_oversized_patch_keeps_header_allocation_and_creates_backup(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / 'server/world/region/r.0.0.mca'
            path.parent.mkdir(parents=True)
            evidence = root / 'evidence'
            evidence.mkdir()
            original = region(b'\x0a\x00\x00' + string('extension', 'retain-verbatim') + b'\x00')
            path.write_bytes(original)
            result = probe.oversize(root / 'server', evidence)
            self.assertEqual(original[:8_192], path.read_bytes()[:8_192])
            self.assertEqual(original, (evidence / 'region-before-patch.mca').read_bytes())
            decoded = probe.regions.NbtReader(zlib.decompress(result)).read_root()
            self.assertEqual({'part0': [0] * 4_096, 'part1': [0] * 4_096}, decoded['extension'])
            self.assertEqual(8_192, json.loads((evidence / 'patch.json').read_text())['array_bytes'])

    def test_nonunique_patch_marker_refuses_without_modifying_region(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / 'server/world/region/r.0.0.mca'
            path.parent.mkdir(parents=True)
            evidence = root / 'evidence'
            evidence.mkdir()
            original = region(b'\x0a\x00\x00' + string('extension', 'retain-verbatim') * 2 + b'\x00')
            path.write_bytes(original)
            with self.assertRaisesRegex(RuntimeError, 'not unique'):
                probe.oversize(root / 'server', evidence)
            self.assertEqual(original, path.read_bytes())


if __name__ == '__main__':
    unittest.main()
