"""Boundary checks for the native escrow harness, without starting another server."""

import gzip
from pathlib import Path
import struct
import sys
import tempfile
import unittest
import zlib

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_escrow_forced_stop as escrow
import run_v180_escrow_benchmark as benchmark


class EscrowHarnessTest(unittest.TestCase):
    def test_checkpoint_matrix_is_fixed_and_complete_for_k_cuts(self):
        self.assertEqual(tuple(f"K{i}" for i in range(11)), escrow.CASES[:11])
        self.assertIn("G3", escrow.CASES)
        self.assertTrue({"C1", "C2", "E2", "G3L2"}.issubset(escrow.CASES))
        self.assertTrue({"C3", "E5"}.issubset(escrow.CASES))

    def test_benchmark_percentiles_keep_main_and_io_separate_and_budgets_unchanged(self):
        with tempfile.TemporaryDirectory() as temporary:
            file = Path(temporary) / "samples.csv"
            file.write_text("phase,value\ncapture_main,1000000\ncapture_main,2000000\nforce_io,8000000\nallocation_bytes,1024\n", encoding="ascii")
            result = benchmark.summarize(file)
            self.assertEqual(1, result["capture_main"]["p50"])
            self.assertEqual(2, result["capture_main"]["p95"])
            self.assertEqual(8, result["force_io"]["p99"])
            self.assertEqual("bytes", result["allocation_bytes"]["unit"])
            self.assertEqual({"mean_ms": 25, "p95_ms": 50, "p99_ms": 100, "nonsave_spike_ms": 500,
                              "rss_bytes": int(5.5 * 1024**3)}, benchmark.BUDGETS)

    def test_missing_and_oversized_ledger_refuse(self):
        with tempfile.TemporaryDirectory() as temporary:
            file = Path(temporary) / escrow.LEDGER
            with self.assertRaises(escrow.smoke.SmokeError):
                escrow.ledger(file)
            file.write_bytes(bytes(1_114_113))
            with self.assertRaises(escrow.smoke.SmokeError):
                escrow.ledger(file)

    def test_external_record_is_not_decoded(self):
        with tempfile.TemporaryDirectory() as temporary:
            file = Path(temporary) / "r.0.0.mca"
            raw = bytearray(12288)
            struct.pack_into(">I", raw, 132 * 4, (2 << 8) | 1)
            struct.pack_into(">IB", raw, 8192, 2, 130)
            file.write_bytes(raw)
            with self.assertRaises(escrow.smoke.SmokeError):
                escrow.record(file)


if __name__ == "__main__":
    unittest.main()
