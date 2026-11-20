import csv
import tempfile
import unittest
from pathlib import Path

from scripts.campaign_observe import ticks
from scripts.campaign_observe.common import schedule


def reply(tick=200, mean=12):
    return (f"[Server thread/INFO]: ARCE_RELEASE_TEST ticks window=100 tick={tick} mean_ms={mean}.000 "
            "p50_ms=10.000 p95_ms=20.000 p99_ms=30.000 max_ms=40.000 satellites=100")


def polls(count):
    return lambda duration, interval: range(count)


class TickTests(unittest.TestCase):
    def test_parse_actual_aggregate_and_duplicate_log_echo(self):
        result = ticks.parse_tick_reply(["unrelated", reply(), reply()])
        self.assertEqual({"tick": 200, "window": 100, "mean_ms": 12,
                          "p95_ms": 20, "p99_ms": 30, "max_ms": 40}, result)

    def test_missing_ambiguous_malformed_and_nonfinite(self):
        for lines in ([], [reply(), reply(201)], [reply().replace("window=100", "window=99")],
                      [reply().replace("12.000", "nan")], ["ARCE_RELEASE_TEST ticks tick=1"]):
            with self.subTest(lines=lines), self.assertRaises((ValueError, KeyError)):
                ticks.parse_tick_reply(lines)

    def test_reference_average(self):
        self.assertEqual({"mean_ms": 3.25}, ticks.parse_tick_reply([
            "ARCE_RELEASE_TEST refload status built=true driving=true players=2 mspt=3.25"], reference=True))

    def test_known_percentiles_and_threshold_runs(self):
        summary = ticks.summarize_ticks(list(enumerate(range(1, 101))), 97)
        self.assertEqual((100, 50.5, 95, 99, 100), tuple(summary[k] for k in
                         ("tick_count", "mean_ms", "p95_ms", "p99_ms", "max_ms")))
        self.assertEqual([{"start_tick": 97, "end_tick": 99, "count": 3, "max_ms": 100}], summary["threshold_runs"])
        split = ticks.summarize_ticks([(1, 10), (2, 10), (3, 5), (4, 11), (6, 12)], 5)
        self.assertEqual([2, 1, 1], [run["count"] for run in split["threshold_runs"]])
        self.assertIsNone(ticks.summarize_ticks([], 5)["p95_ms"])

    def test_bad_raw_ticks_are_not_summarized(self):
        for samples in ([(1, 1), (1, 2)], [(2, 1), (1, 2)], [(-1, 2)], [(1, float("inf"))]):
            with self.assertRaises(ValueError):
                ticks.summarize_ticks(samples, 5)
        with self.assertRaises(ValueError):
            ticks.percentile([1], 0)

    def test_sampler_preserves_windows_never_invents_raw_figures(self):
        responses = iter([[reply(200)], [reply(200)], [reply(220, 14)], [reply(350, 16)]])
        commands = []
        def channel(command):
            commands.append(command)
            return next(responses)
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp) / "ticks.csv"
            summary = ticks.sample_ticks(channel, output, duration=4, threshold_ms=500, scheduler=polls(4))
            with output.open(newline="") as stream:
                rows = list(csv.DictReader(stream))
            self.assertEqual(3, len(rows))
            self.assertEqual("80", rows[1]["overlap_ticks"])
            self.assertEqual("30", rows[2]["gap_ticks"])
            self.assertTrue(rows[0]["utc"].endswith("Z"))
            self.assertEqual(14, summary["mean_ms"])
            self.assertEqual(30, summary["detected_gap_ticks"])
            self.assertEqual(40, summary["max_reported_window_ms"])
            for key in ("tick_count", "p95_ms", "p99_ms", "max_ms", "threshold_runs"):
                self.assertIsNone(summary[key])
            self.assertEqual([ticks.TICK_COMMAND] * 4, commands)
            with self.assertRaises(FileExistsError):
                ticks.sample_ticks(channel, output, duration=1, threshold_ms=5, scheduler=polls(0))

    def test_reference_sampler_and_restart_refusal(self):
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp) / "ref.csv"
            summary = ticks.sample_ticks(lambda cmd: ["ARCE_RELEASE_TEST refload status mspt=2.00"],
                                         output, reference=True, duration=1, threshold_ms=500, scheduler=polls(1))
            self.assertEqual(2, summary["mean_ms"])
            self.assertIsNone(summary["detected_gap_ticks"])
            responses = iter([[reply(200)], [reply(100)]])
            with self.assertRaises(ValueError):
                ticks.sample_ticks(lambda cmd: next(responses), Path(temp) / "reset.csv", duration=2,
                                   threshold_ms=500, scheduler=polls(2))
            with self.assertRaises(ValueError):
                ticks.sample_ticks(None, Path(temp) / "bad.csv", duration=1, interval=2, threshold_ms=500)

    def test_forced_chunks_only_queries_validated_dimension(self):
        commands = []
        def channel(command):
            commands.append(command)
            return ["No force loaded chunks were found"]
        result = ticks.sample_forced_chunks(channel, ["minecraft:overworld"])
        self.assertEqual(["execute in minecraft:overworld run forceload query"], commands)
        self.assertIn("vanilla", result["scope"])
        with self.assertRaises(ValueError):
            ticks.sample_forced_chunks(channel, ["minecraft:overworld run stop"])

    def test_schedule_bounded_and_no_burst(self):
        now = [0.0]
        def sleep(delay):
            now[0] += delay
        observed = []
        for _ in schedule(3, 1, clock=lambda: now[0], sleep=sleep):
            observed.append(now[0])
            now[0] += 0.2
        self.assertEqual([0, 1, 2], observed)
        with self.assertRaises(ValueError):
            list(schedule(float("nan"), 1))
