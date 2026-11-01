"""W247: list each server-overloaded entry of a GameTest log with the batches running around it.

Reads a local latest.log or a hosted job log. Prints JSON. Diagnostic only; no limits are checked here.
"""
import argparse
import datetime as dt
import json
import pathlib
import re

LOCAL = re.compile(r"^\[(\d{2}[A-Za-z]{3}\d{4} \d{2}:\d{2}:\d{2}\.\d{3})\]")
HOSTED = re.compile(r"^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+)Z")
BATCH = re.compile(r"Running test batch '([^']+)' \((\d+) tests\)")
OVERLOAD = re.compile(r"Can't keep up! Is the server overloaded\? Running (\d+)ms or (\d+) ticks behind")


def stamp(line):
    match = LOCAL.match(line)
    if match:
        return dt.datetime.strptime(match[1], "%d%b%Y %H:%M:%S.%f").timestamp()
    match = HOSTED.match(line)
    if match:
        return dt.datetime.strptime(match[1][:26], "%Y-%m-%dT%H:%M:%S.%f").timestamp()
    return None


def summarize(path, focus):
    starts, overloads, last = [], [], None
    for line in path.read_text(encoding="utf-8-sig", errors="replace").splitlines():
        now = stamp(line)
        last = now if now is not None else last
        batch = BATCH.search(line)
        if batch:
            starts.append(dict(time=last, batch=batch[1], tests=int(batch[2])))
            continue
        overload = OVERLOAD.search(line)
        if overload:
            overloads.append(dict(time=last, behind_ms=int(overload[1]), behind_ticks=int(overload[2]),
                                  running=[s["batch"] for s in starts[-3:]]))
    for index, start in enumerate(starts):
        following = starts[index + 1]["time"] if index + 1 < len(starts) else last
        start["elapsed_s"] = round(following - start["time"], 3) if following and start["time"] else None
    origin = starts[0]["time"] if starts else None
    for row in overloads:
        row["t_s"] = round(row.pop("time") - origin, 3) if origin and row.get("time") else None
    for row in starts:
        row["t_s"] = round(row.pop("time") - origin, 3) if origin else None
    focused = [row for row in starts if not focus or any(row["batch"].startswith(prefix) for prefix in focus)]
    return dict(log=str(path), batches=len(starts), overload_count=len(overloads),
                overload_total_ms=sum(row["behind_ms"] for row in overloads),
                overload_max_ms=max((row["behind_ms"] for row in overloads), default=0),
                overloads=overloads, focus_batches=focused)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("logs", nargs="+", type=pathlib.Path)
    parser.add_argument("--focus", default="w241_,w236_,station_warp_deop,w133_escrow_fill_launch")
    parser.add_argument("--output", type=pathlib.Path)
    args = parser.parse_args()
    focus = [item for item in args.focus.split(",") if item]
    result = [summarize(path, focus) for path in args.logs]
    text = json.dumps(result, indent=1) + "\n"
    if args.output:
        args.output.write_text(text, encoding="utf-8", newline="\n")
    print(text)


if __name__ == "__main__":
    main()
