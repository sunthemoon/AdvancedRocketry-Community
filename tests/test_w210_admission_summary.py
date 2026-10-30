import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from w210_admission_summary import c1_intervals, profile_intervals


class SummaryTest(unittest.TestCase):
    def raw(self):
        return dict(ticks=[dict(tick=3, tick_ns=8_000_000)], events=[
            dict(label="C1_RETAIN_BEFORE", tick=3, thread="Server thread", ns=1_000_000, cpu_ns=100, allocated_bytes=200, epoch_ms=100),
            dict(label="C1_RETAIN_AFTER", tick=3, thread="Server thread", ns=5_000_000, cpu_ns=300, allocated_bytes=500, epoch_ms=104)])

    def test_counter_deltas_keep_whole_tick_and_all_pairs(self):
        raw = self.raw()
        expected = dict(tick=3, wall_ms=4, cpu_ms=.0002, allocated_bytes=300, epoch_begin_ms=100, epoch_end_ms=104, whole_tick_ms=8)
        self.assertEqual([expected], c1_intervals(raw))
        raw["events"] *= 2
        self.assertEqual([expected, expected], c1_intervals(raw))
        self.assertEqual([], c1_intervals(dict(ticks=[], events=[])))

    def test_unpaired_or_unavailable_counters_fail_instead_of_becoming_zero(self):
        for key, value in (("cpu_ns", -1), ("allocated_bytes", 199), ("tick", 4), ("thread", "IO")):
            raw = self.raw(); raw["events"][1][key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                c1_intervals(raw)
        raw = self.raw(); raw["events"].pop()
        with self.assertRaises(ValueError):
            c1_intervals(raw)

    def test_samples_distinguish_encoder_measurer_and_ram_write_and_clip_gc(self):
        interval = c1_intervals(self.raw())
        lines = [f"jdk.ExecutionSample\t102000000\t102000000\tServer thread\t{frame}\n" for frame in (
            "RocketTransactionSavedData.canAppend;RocketTransactionCodec.encodeEntry",
            "RocketTransactionSavedData.canAppend;BoundedNbt.measure",
            "RocketTransactionSavedData.write", "SealedJournalEntry.retain")]
        lines += ["jdk.GCPhasePause\t99000000\t101000000\t\t\n",
                  "jdk.ExecutionSample\t99000000\t99000000\tServer thread\toutside\n",
                  "jdk.ExecutionSample\t102000000\t102000000\tIO\twriter\n"]
        row = profile_intervals(interval, lines)[0]
        self.assertEqual({"admission-encoder": 1, "admission-bounded-measurer": 1, "ram-write": 1, "other-retain": 1}, row["samples"])
        self.assertEqual(1, row["gc_overlap_ms"])

    def test_top_frames_use_count_then_name_at_equal_count_cutoff(self):
        frames = [f"Frame{index:02d}" for index in reversed(range(30))]
        # A duplicated frame in one stack still counts only once per sample.
        stacks = frames + ["Z.hot;Z.hot", "Z.hot"]
        expected = [("Z.hot", 2)] + [(f"Frame{index:02d}", 1) for index in range(24)]
        for order in (stacks, list(reversed(stacks))):
            with self.subTest(first_stack=order[0]):
                lines = [f"jdk.ExecutionSample\t102000000\t102000000\tServer thread\t{stack}\n"
                         for stack in order]
                row = profile_intervals(c1_intervals(self.raw()), lines)[0]
                self.assertEqual(expected, row["frames"])


if __name__ == "__main__":
    unittest.main()
