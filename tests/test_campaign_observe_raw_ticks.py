import contextlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest


# W403 owns package initialization; this test also works before that branch merges.
SPEC = importlib.util.spec_from_file_location(
    "campaign_raw_ticks_under_test",
    Path(__file__).resolve().parents[1] / "scripts/campaign_observe/raw_ticks.py",
)
raw = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = raw
SPEC.loader.exec_module(raw)


def reply(tick, samples):
    return (f"{raw.PREFIX}tick={tick} entries={len(samples)} data="
            + ",".join(f"{number}:{nanos}" for number, nanos in samples))


class RawTicksTest(unittest.TestCase):
    def test_parse_literal_log_line_and_integer_nanoseconds(self):
        poll = raw.parse_raw_tick_reply(
            "[12:00:00] [Server thread/INFO]: ARCE_RELEASE_TEST ticks_raw "
            "tick=4 entries=3 data=1:1000001,2:0,3:9007199254740993\n"
        )
        self.assertEqual(4, poll.tick)
        self.assertEqual(((1, 1000001), (2, 0), (3, 9007199254740993)), poll.samples)
        self.assertEqual((), raw.parse_raw_tick_reply(reply(0, [])).samples)
        self.assertEqual(((4, 1),), raw.parse_raw_tick_reply(reply(4, [(4, 1)])).samples)

    def test_ring_wrapped_poll_is_accepted_in_tick_order(self):
        pairs = [(tick, tick * 1000) for tick in range(138, 238)]
        self.assertEqual(tuple(pairs), raw.parse_raw_tick_reply(reply(238, pairs)).samples)

    def test_malformed_oversized_negative_or_unordered_replies_are_refused(self):
        lines = [
            "ARCE_RELEASE_TEST ticks tick=4 window=100",
            raw.PREFIX + "tick=4 entries=2 data=3:1",
            raw.PREFIX + "tick=-1 entries=0 data=",
            raw.PREFIX + "tick=2147483648 entries=0 data=",
            raw.PREFIX + "tick=1 entries=1 data=1:-1",
            raw.PREFIX + "tick=1 entries=1 data=1:9223372036854775808",
            reply(4, [(0, 1)]), reply(4, [(5, 1)]),
            reply(4, [(3, 1), (3, 1)]), reply(4, [(4, 1), (3, 1)]),
            reply(4, [(1, 1), (3, 1)]), reply(4, [(2, 1)]),
            reply(102, [(tick, 1) for tick in range(1, 102)]),
            reply(4, [(3, 1)]) + " junk", reply(4, [(3, 1)]) + raw.PREFIX,
            raw.PREFIX + "tick=1 entries=1 data=" + "1" * 4096,
        ]
        for line in lines:
            with self.subTest(line=line[:100]), self.assertRaises(ValueError):
                raw.parse_raw_tick_reply(line)

    def test_overlap_and_repeat_are_dropped_and_gap_is_reported(self):
        polls = [raw.parse_raw_tick_reply(line) for line in [
            "ARCE_RELEASE_TEST ticks_raw tick=4 entries=3 data=1:10,2:20,3:30",
            "ARCE_RELEASE_TEST ticks_raw tick=5 entries=3 data=2:20,3:30,4:40",
            "ARCE_RELEASE_TEST ticks_raw tick=5 entries=3 data=2:20,3:30,4:40",
            "ARCE_RELEASE_TEST ticks_raw tick=9 entries=2 data=7:70,8:80",
        ]]
        merged = raw.merge_polls(polls)
        self.assertEqual([(1, 10), (2, 20), (3, 30), (4, 40), (7, 70), (8, 80)], merged["samples"])
        self.assertEqual(5, merged["duplicate_count"])
        self.assertEqual([{"start_tick": 5, "end_tick": 6, "count": 2}], merged["gaps"])

    def test_same_command_tick_can_gain_its_just_completed_tick(self):
        polls = [raw.parse_raw_tick_reply(reply(3, pairs)) for pairs in [
            [(1, 1), (2, 2)], [(1, 1), (2, 2), (3, 3)],
        ]]
        self.assertEqual([(1, 1), (2, 2), (3, 3)], raw.merge_polls(polls)["samples"])

    def test_conflicting_overlap_and_counter_reset_are_refused(self):
        first = raw.parse_raw_tick_reply(reply(3, [(2, 10)]))
        for second in [reply(4, [(2, 11), (3, 30)]), reply(2, [(1, 10)])]:
            with self.subTest(second=second), self.assertRaises(ValueError):
                raw.merge_polls([first, raw.parse_raw_tick_reply(second)])

    def test_whole_run_nearest_rank_is_computed_after_overlap_removal(self):
        polls = [raw.parse_raw_tick_reply(reply(end, [(t, t * 1_000_000) for t in range(start, end + 1)]))
                 for start, end in [(1, 100), (51, 150), (101, 200)]]
        result = raw.summarize_raw_ticks(polls, 195)
        self.assertEqual(200, result["tick_count"])
        self.assertEqual(100, result["duplicate_count"])
        self.assertEqual(100.5, result["mean_ms"])
        self.assertEqual(190, result["p95_ms"])
        self.assertEqual(198, result["p99_ms"])
        self.assertEqual(200, result["max_ms"])
        self.assertEqual(0, result["missing_tick_count"])
        self.assertEqual([{"start_tick": 196, "end_tick": 200, "count": 5, "max_ms": 200}],
                         result["threshold_runs"])
        self.assertEqual(25, result["reference_budgets"]["mean_ms"])
        self.assertEqual(50, result["reference_budgets"]["p95_ms"])
        self.assertEqual(100, result["reference_budgets"]["p99_ms"])
        self.assertEqual(500, result["reference_budgets"]["non_startup_save_spike_ms"])
        self.assertNotIn("verdict", result)

    def test_threshold_runs_break_on_gaps_and_at_or_below_threshold_ticks(self):
        polls = [raw.parse_raw_tick_reply(reply(end, [(t, ns) for t, ns in pairs])) for end, pairs in [
            (6, [(1, 600000000), (2, 700000000), (3, 500000000), (4, 500000001), (5, 0)]),
            (9, [(7, 800000000), (8, 900000000)]),
        ]]
        result = raw.summarize_raw_ticks(polls, 500)
        self.assertEqual([(1, 2, 2), (4, 4, 1), (7, 8, 2)],
                         [(r["start_tick"], r["end_tick"], r["count"]) for r in result["threshold_runs"]])
        self.assertEqual([{"start_tick": 6, "end_tick": 6, "count": 1}], result["gaps"])
        self.assertEqual(7, result["tick_count"])
        # A slow tick on either side of a missing tick must not become a two-tick run.
        sparse = [raw.parse_raw_tick_reply(reply(t, [(t, 600000000)])) for t in (10, 12)]
        self.assertEqual(2, len(raw.summarize_raw_ticks(sparse, 500)["threshold_runs"]))

    def test_empty_and_single_tick_summaries(self):
        empty = raw.summarize_raw_ticks([], 500)
        self.assertEqual(0, empty["tick_count"])
        for key in ("first_tick", "last_tick", "mean_ms", "p95_ms", "p99_ms", "max_ms"):
            self.assertIsNone(empty[key])
        self.assertEqual([], empty["threshold_runs"])
        one = raw.summarize_raw_ticks([raw.parse_raw_tick_reply(reply(1, [(1, 1000001)]))], 0)
        for key in ("mean_ms", "p95_ms", "p99_ms", "max_ms"):
            self.assertEqual(1.000001, one[key])

    def test_invalid_thresholds(self):
        for threshold in (-1, float("nan"), float("inf")):
            with self.subTest(threshold=threshold), self.assertRaises(ValueError):
                raw.summarize_raw_ticks([], threshold)

    def test_cli_prints_observations_and_references_and_refuses_empty_log(self):
        with tempfile.TemporaryDirectory() as temporary:
            log = Path(temporary) / "ticks.log"
            log.write_text("unrelated log line\n" + reply(2, [(1, 1000000), (2, 2000000)]) + "\n",
                           encoding="utf-8")
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                self.assertEqual(0, raw.main([str(log), "--threshold-ms", "500"]))
            result = json.loads(output.getvalue())
            self.assertEqual(1.5, result["mean_ms"])
            self.assertIn("reference_budgets", result)
            log.write_text("no raw reply\n", encoding="utf-8")
            with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as error:
                raw.main([str(log), "--threshold-ms", "500"])
            self.assertEqual(2, error.exception.code)


if __name__ == "__main__":
    unittest.main()
