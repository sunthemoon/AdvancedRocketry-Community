"""Command-line entry points; imports of live Windows adapters are lazy."""
import argparse
import json
import sys
import time
import uuid
from pathlib import Path

from .cells import load_cell
from .baseline import load_baseline
from .evidence import dumps, redact, write_json
from .frametimes import analyze
from .protocol import load_session, number, validate_request


def submit(run, ops, mode, players, timeout):
    number(timeout, 0.01, 21600, "request timeout")
    text = Path(ops[1:]).read_text(encoding="utf-8-sig") if ops.startswith("@") else ops
    if len(text.encode("utf-8")) > 256 * 1024:
        raise ValueError("Request exceeds 256 KiB")
    rid = time.strftime("%H%M%S") + "-" + uuid.uuid4().hex[:8]
    request = validate_request({"id": rid, "ops": json.loads(text)}, mode, players)
    raw = json.dumps(request, ensure_ascii=False, allow_nan=False).encode("utf-8")
    if len(raw) > 256 * 1024:
        raise ValueError("Request exceeds 256 KiB")
    result_path = run / "results.jsonl"
    offset = result_path.stat().st_size if result_path.exists() else 0
    target = run / "requests" / (rid + ".json")
    temporary = target.with_suffix(".tmp")
    with temporary.open("xb") as stream:
        stream.write(raw)
    temporary.rename(target)
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if result_path.exists():
            with result_path.open("rb") as stream:
                stream.seek(offset)
                for line in stream:
                    if not line.endswith(b"\n"):
                        break
                    offset += len(line)
                    record = json.loads(line.decode("utf-8"))
                    if record.get("id") == rid:
                        print(dumps(record))
                        return 1 if record.get("error") else 0
        time.sleep(0.2)
    raise TimeoutError("No result for request " + rid)


def main(argv=None):
    parser = argparse.ArgumentParser(description="Credential-free Windows real-client campaign harness")
    commands = parser.add_subparsers(dest="command", required=True)
    check = commands.add_parser("check", help="Validate session and cell without starting a process")
    check.add_argument("session", type=Path)
    session = commands.add_parser("session", help="Run an owner-approved live round")
    session.add_argument("session", type=Path)
    session.add_argument("--jvm-lock", type=Path, required=True, help="Machine-wide heavy-JVM lock directory")
    session.add_argument("--lock-label", help="Name written before pid= in the lock's owner.txt "
                         "(default: campaign-<run directory name>)")
    frames = commands.add_parser("frames", help="Analyze a previously captured PresentMon CSV")
    frames.add_argument("csv", type=Path)
    group = frames.add_mutually_exclusive_group(required=True)
    group.add_argument("--pid", type=int)
    group.add_argument("--name")
    frames.add_argument("--threshold-ms", type=float, required=True)
    frames.add_argument("--output", type=Path, required=True)
    request = commands.add_parser("request", help="Submit a legacy JSON action list, or @file")
    request.add_argument("run", type=Path)
    request.add_argument("ops")
    request.add_argument("--mode", choices=("launch", "attach"), required=True)
    request.add_argument("--players", nargs="+", required=True)
    request.add_argument("--timeout", type=float, default=120)
    args = parser.parse_args(argv)
    try:
        if args.command == "frames":
            result = analyze(args.csv, threshold_ms=args.threshold_ms, pid=args.pid, name=args.name)
            write_json(args.output, result)
            print(dumps(result))
            return 0
        if args.command == "check":
            spec = load_session(args.session)
            (load_baseline if spec.get("launch_kind") == "baseline" else load_cell)(spec["cell"])
            label = "baseline profile" if spec.get("launch_kind") == "baseline" else "cell"
            print(f"PASS: session and prepared {label} validated; no process started")
            return 0
        if args.command == "request":
            return submit(args.run, args.ops, args.mode, args.players, args.timeout)
        from .session import run_session
        return run_session(args.session, args.jvm_lock, args.lock_label)
    except (OSError, ValueError, KeyError, TypeError, RuntimeError, TimeoutError) as exc:
        print(redact(f"FAIL: {type(exc).__name__}: {exc}"), file=sys.stderr)
        return 1
