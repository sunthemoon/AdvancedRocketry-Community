import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from w199_jfr_summary import summarize


class W199JfrSummaryTest(unittest.TestCase):
    def test_excludes_between_tick_sleep_and_clips_blocking_intervals(self):
        rows = [dict(tick="1", phase="PREPARATION", tick_ns="2000000", end_epoch_ms="10"),
                dict(tick="2", phase="PROOF", tick_ns="2000000", end_epoch_ms="20")]
        events = ["jdk.ExecutionSample\t9000000\t9000000\tServer thread\tA;B\n",
                  "jdk.ExecutionSample\t12000000\t12000000\tServer thread\tSleep\n",
                  "jdk.ThreadPark\t7000000\t19000000\tServer thread\tWait\n",
                  "jdk.GCPhasePause\t19000000\t21000000\tGC Thread\t\n"]
        result = summarize(rows, events)
        prep, proof = result["phases"]["PREPARATION"], result["phases"]["PROOF"]
        self.assertEqual({"Server thread": 1}, prep["samples_by_thread"])
        self.assertEqual({}, proof["samples_by_thread"])
        self.assertEqual({"jdk.ThreadPark": 2.0}, prep["blocking_ms"])
        self.assertEqual({"jdk.ThreadPark": 1.0, "jdk.GCPhasePause": 1.0}, proof["blocking_ms"])

    def test_keeps_other_threads_distinct_and_deduplicates_recursive_frames(self):
        rows = [dict(tick="1", phase="PROOF", tick_ns="600000000", end_epoch_ms="1000")]
        events = ["jdk.ExecutionSample\t500000000\t500000000\tServer thread\tA;A;B\n",
                  "jdk.ExecutionSample\t500000000\t500000000\tWorker\tC\n"]
        result = summarize(rows, events)
        profile = result["ticks"][0]["profile"]
        self.assertEqual({"Server thread": 1, "Worker": 1}, profile["samples_by_thread"])
        self.assertEqual({"A": 1, "B": 1}, dict(profile["server_frames"]))


if __name__ == "__main__":
    unittest.main()
