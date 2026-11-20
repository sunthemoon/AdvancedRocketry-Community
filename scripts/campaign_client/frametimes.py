"""Offline PresentMon v1/v2 CSV analysis; does not install or run a capture tool."""
import csv
import math
from pathlib import Path


def analyze(path, *, threshold_ms, pid=None, name=None):
    if type(threshold_ms) not in (int, float) or not math.isfinite(threshold_ms) or threshold_ms <= 0:
        raise ValueError("A positive finite stall threshold is required")
    if (pid is None) == (name is None):
        raise ValueError("Choose exactly one process ID or process name")
    samples = []
    with Path(path).open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        columns = reader.fieldnames or []
        timing = next((c for c in ("FrameTime", "MsBetweenPresents") if c in columns), None)
        identity = "ProcessID" if pid is not None else "Application"
        # PresentMon 2 distributions can use ProcessName rather than Application.
        if identity == "Application" and identity not in columns:
            identity = "ProcessName"
        if timing is None or identity not in columns:
            raise ValueError("Missing PresentMon frame time or process column")
        for index, row in enumerate(reader):
            if index >= 5000000:
                raise ValueError("CSV exceeds five million rows")
            match = row[identity] == str(pid) if pid is not None else row[identity].casefold() == name.casefold()
            if not match:
                continue
            value = float(row[timing])
            if not math.isfinite(value) or value <= 0:
                raise ValueError("Matched frame time must be positive and finite")
            samples.append(value)
    if not samples:
        raise ValueError("No matching frames")
    ordered = sorted(samples)
    slow_count = math.ceil(len(samples) * 0.01)
    elapsed, first = 0.0, None
    for index, value in enumerate(samples):
        if first is None and value > threshold_ms:
            first = {"frame_index": index, "frame_time_ms": value, "time_from_start_seconds": elapsed / 1000}
        elapsed += value
    return {"schema_version": 1, "filter": {"pid": pid, "name": name}, "frame_time_column": timing,
            "frame_count": len(samples), "capture_length_seconds": elapsed / 1000,
            "average_fps": len(samples) * 1000 / elapsed,
            "one_percent_low_fps": 1000 / (sum(ordered[-slow_count:]) / slow_count),
            "percentile_99_frame_time_ms": ordered[math.ceil(0.99 * len(samples)) - 1],
            "longest_frame_ms": ordered[-1], "threshold_ms": threshold_ms, "first_stall": first,
            "definitions": {
                "sample": "Every matched CSV row; values in milliseconds; multiple swap chains are not merged.",
                "capture_length": "Sum of matched frame times, not wall-clock span of the unfiltered CSV.",
                "average_fps": "Frame count divided by capture length in seconds.",
                "one_percent_low_fps": "1000 / mean of slowest ceil(frame_count * 0.01) frame times.",
                "percentile_99": "Nearest-rank: sorted frame time at ceil(0.99 * frame_count), one-based.",
                "first_stall": "First frame strictly longer than threshold; time is sum of preceding matched frames; index zero-based."
            }}
