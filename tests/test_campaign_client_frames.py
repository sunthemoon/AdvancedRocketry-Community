import csv
import unittest

from scripts.campaign_client.cli import main
from scripts.campaign_client.frametimes import analyze
from test_campaign_client_support import Fixture


class FrameTests(Fixture):
    def csv(self, columns, rows):
        path = self.root / "frames.csv"
        with path.open("w", newline="", encoding="utf-8-sig") as stream:
            writer = csv.writer(stream)
            writer.writerow(columns)
            writer.writerows(rows)
        return path

    def test_v1_hand_computed_pid_filter(self):
        path = self.csv(["Application", "ProcessID", "MsBetweenPresents"],
                        [["javaw.exe", 123, 10], ["other.exe", 4, 1000], ["javaw.exe", 123, 20],
                         ["javaw.exe", 123, 30], ["javaw.exe", 123, 40]])
        result = analyze(path, pid=123, threshold_ms=25)
        self.assertEqual(4, result["frame_count"])
        self.assertAlmostEqual(0.1, result["capture_length_seconds"])
        self.assertEqual(40, result["average_fps"])
        self.assertEqual(25, result["one_percent_low_fps"])
        self.assertEqual(40, result["percentile_99_frame_time_ms"])
        self.assertEqual(40, result["longest_frame_ms"])
        self.assertEqual({"frame_index": 2, "frame_time_ms": 30, "time_from_start_seconds": 0.03}, result["first_stall"])
        self.assertTrue(result["definitions"])

    def test_v2_name_filter_and_ceiling_percentile(self):
        path = self.csv(["ProcessName", "ProcessID", "FrameTime"],
                        [["JavaW.exe", 123, 10]] * 99 + [["JavaW.exe", 123, 20], ["JavaW.exe", 123, 40]])
        result = analyze(path, name="javaw.exe", threshold_ms=40)
        self.assertEqual(101, result["frame_count"])
        self.assertAlmostEqual(101 / 1.05, result["average_fps"])
        self.assertAlmostEqual(1000 / 30, result["one_percent_low_fps"])
        self.assertEqual(20, result["percentile_99_frame_time_ms"])
        self.assertIsNone(result["first_stall"])

    def test_v2_alternative_column_one_frame_stall_at_zero(self):
        path = self.csv(["Application", "MsBetweenPresents"], [["java.exe", 50]])
        result = analyze(path, name="java.exe", threshold_ms=49)
        self.assertEqual(20, result["average_fps"])
        self.assertEqual(0, result["first_stall"]["time_from_start_seconds"])

    def test_invalid_or_missing_frames(self):
        for value in (0, -1, "NaN", "inf", "invalid"):
            path = self.csv(["ProcessID", "FrameTime"], [[123, value]])
            with self.subTest(value=value), self.assertRaises(ValueError):
                analyze(path, pid=123, threshold_ms=20)
        path = self.csv(["ProcessID", "FrameTime"], [[456, 10]])
        with self.assertRaisesRegex(ValueError, "No matching"):
            analyze(path, pid=123, threshold_ms=20)

    def test_threshold_and_filter_required(self):
        path = self.csv(["ProcessID", "FrameTime"], [[123, 10]])
        with self.assertRaises(TypeError):
            analyze(path, pid=123)
        for threshold in (0, -1, float("nan")):
            with self.assertRaises(ValueError):
                analyze(path, pid=123, threshold_ms=threshold)
        for kwargs in ({}, {"pid": 123, "name": "java.exe"}):
            with self.assertRaises(ValueError):
                analyze(path, threshold_ms=20, **kwargs)

    def test_frames_cli_writes_definitions(self):
        path = self.csv(["ProcessID", "FrameTime"], [[123, 10]])
        output = self.root / "result.json"
        self.assertEqual(0, main(["frames", str(path), "--pid", "123", "--threshold-ms", "50", "--output", str(output)]))
        self.assertIn("definitions", output.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
