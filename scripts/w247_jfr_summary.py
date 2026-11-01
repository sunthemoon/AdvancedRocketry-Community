"""W247: attribute the long server ticks of a focused run (ticks.jsonl from the W247 probe) with its JFR recording.

For every tick whose period is at least --min-ms, counts the server thread's execution samples by inclusive frame,
sums its park time, and counts samples of the chunk worker threads in the same window. Samples are counts, not
exact durations. Writes JSON next to the inputs and prints a short table.
"""
import argparse
import bisect
import collections
import json
import pathlib
import subprocess

SAMPLES = {"jdk.ExecutionSample", "jdk.NativeMethodSample"}
MARKERS = (
    ("fixture_region", "K3ModelTestRegion.<init>"),
    ("fixture_set_block", "K3ModelTestRegion.set"),
    ("sync_get_chunk", "ServerChunkCache.getChunk"),
    ("managed_block", "BlockableEventLoop.managedBlock"),
    ("full_chunk_promotion", "ChunkMap.protoChunkToFullChunk"),
    ("chunk_load_event", "ChunkEvent$Load"),
    ("chunk_save", "ChunkMap.save"),
    ("chunk_serialize", "ChunkSerializer.write"),
    ("unload", "ChunkMap.processUnloads"),
    ("ticket_update", "DistanceManager.runAllUpdates"),
    ("light", "LightEngine"),
    ("test_tick", "GameTestTicker.tick"),
    ("level_tick", "ServerLevel.tick"),
)


def category(stack):
    names = [name for name, frame in MARKERS if frame in stack]
    return "+".join(names) if names else "other"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=pathlib.Path)
    parser.add_argument("--workspace", type=pathlib.Path, default=pathlib.Path.cwd())
    parser.add_argument("--java", default="C:/Program Files/Java/jdk-17.0.7/bin/java.exe")
    parser.add_argument("--min-ms", type=float, default=1000.0)
    args = parser.parse_args()
    ticks = [json.loads(line) for line in (args.directory / "ticks.jsonl").read_text(encoding="utf-8").splitlines()]
    ticks = [tick for tick in ticks if not tick.get("summary") and tick["period_ms"] >= args.min_ms]
    windows = [(tick["start_ms"] * 1_000_000, int((tick["start_ms"] + tick["period_ms"]) * 1_000_000), tick) for tick in ticks]
    starts = [window[0] for window in windows]
    rows = {tick["tick"]: dict(tick=tick["tick"], period_ms=tick["period_ms"], generated=tick["generated"],
                               server_samples=collections.Counter(), server_frames=collections.Counter(),
                               server_park_ms=0.0, worker_samples=0, worker_frames=collections.Counter(),
                               other_samples=collections.Counter())
            for tick in ticks}
    command = [args.java, str(args.workspace / "scripts/W199JfrExtract.java"), str(args.directory / "server.jfr")]
    with subprocess.Popen(command, stdout=subprocess.PIPE, text=True, encoding="utf-8") as process:
        for line in process.stdout:
            kind, start, end, thread, stack = line.rstrip("\r\n").split("\t", 4)
            start, end = int(start), int(end)
            index = bisect.bisect_right(starts, start) - 1
            if index < 0 or start > windows[index][1]:
                continue
            row = rows[windows[index][2]["tick"]]
            if kind in SAMPLES:
                if thread == "Server thread":
                    row["server_samples"][category(stack)] += 1
                    row["server_frames"].update(set(frame.rsplit(":", 1)[0] for frame in stack.split(";") if frame))
                elif thread.startswith("Worker-Main"):
                    row["worker_samples"] += 1
                    row["worker_frames"].update(set(frame.rsplit(":", 1)[0] for frame in stack.split(";") if frame))
                else:
                    row["other_samples"][thread.split("-")[0]] += 1
            elif kind == "jdk.ThreadPark" and thread == "Server thread":
                row["server_park_ms"] += (min(end, windows[index][1]) - start) / 1e6
        if process.wait() != 0:
            raise SystemExit("JFR extraction failed")
    result = []
    for row in rows.values():
        row["server_frames"] = row["server_frames"].most_common(25)
        row["worker_frames"] = row["worker_frames"].most_common(40)
        row["server_samples"] = dict(row["server_samples"].most_common())
        row["other_samples"] = dict(row["other_samples"].most_common(6))
        row["server_park_ms"] = round(row["server_park_ms"], 1)
        result.append(row)
    output = args.directory / "jfr-long-ticks.json"
    output.write_text(json.dumps(result, indent=1) + "\n", encoding="utf-8", newline="\n")
    for row in result:
        top = ", ".join(f"{name}={count}" for name, count in list(row["server_samples"].items())[:4])
        print(f"tick {row['tick']:>5} {row['period_ms']:>8.1f} ms gen={row['generated']:>4} park={row['server_park_ms']:>7.1f} ms "
              f"workers={row['worker_samples']:>5} server: {top}")


if __name__ == "__main__":
    main()
