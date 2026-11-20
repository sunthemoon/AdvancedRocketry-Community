#!/usr/bin/env python3
"""Campaign evidence utilities. JVM attachment is only exposed to the owning harness API.

Tick integration: campaign_observe.ticks.sample_ticks(channel, csv, duration=600,
threshold_ms=500). The callable must return reply lines with a bounded timeout.
Process heap/JFR integration: pass campaign_observe.jdk.CampaignJvm(popen, "server")
using the handle the harness started. This CLI never discovers or attaches JVMs.
"""

import argparse
import json
from pathlib import Path
import subprocess
import sys

if __package__:
    from .campaign_observe import evidence, host, jdk, process
else:
    from campaign_observe import evidence, host, jdk, process


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="operation", required=True)
    root = commands.add_parser("root", help="create or read-only check a candidate layout")
    root.add_argument("root", type=Path, help="e.g. D:/GitHub/ARCE-Task-Evidence/final-campaign")
    root.add_argument("short_sha")
    root.add_argument("--identity", required=True, type=Path, help="JSON: git_commit, jar_sha256, forge, java")
    root.add_argument("--check", action="store_true")
    root.add_argument("--drive", action="append", default=[], help="additional filesystem to check (e.g. C:/)")
    for operation in ("seal", "verify"):
        sub = commands.add_parser(operation)
        sub.add_argument("folder", type=Path)
    sub = commands.add_parser("host", help="write a new host/run JSON record")
    sub.add_argument("output", type=Path)
    sub.add_argument("--provider", required=True)
    sub.add_argument("--plan", required=True)
    sub.add_argument("--disk", required=True, type=Path)
    sub.add_argument("--run-json", required=True, type=Path, help="caller-supplied fields from docs/15 section 15")
    sub = commands.add_parser("process", help="RSS/CPU only; does not attach to a JVM")
    sub.add_argument("pid", type=int)
    sub.add_argument("output", type=Path)
    sub.add_argument("--duration", required=True, type=float)
    sub.add_argument("--interval", default=1.0, type=float)
    sub = commands.add_parser("jfr-json", help="summarize an existing jfr print JSON file")
    sub.add_argument("input", type=Path)
    sub = commands.add_parser("jfr-file", help="offline jfr print --json and summary")
    sub.add_argument("input", type=Path)
    sub.add_argument("json_output", type=Path)
    sub.add_argument("--jfr", default="jfr")
    args = parser.parse_args(argv)
    try:
        if args.operation == "root":
            identity = json.loads(args.identity.read_text(encoding="utf-8"))
            result = str(evidence.evidence_root(args.root, args.short_sha, identity, check=args.check, drives=args.drive))
        elif args.operation == "seal":
            result = {"files_sealed": evidence.write_checksums(args.folder)}
        elif args.operation == "verify":
            result = {"files_verified": evidence.verify_checksums(args.folder)}
        elif args.operation == "host":
            result = host.write_host_record(args.output, provider=args.provider, plan=args.plan, disk=args.disk,
                                           run=json.loads(args.run_json.read_text(encoding="utf-8")))
        elif args.operation == "process":
            result = {"samples": process.sample_process(args.pid, args.output, duration=args.duration, interval=args.interval)}
        elif args.operation == "jfr-json":
            result = jdk.summarize_jfr_json(json.loads(args.input.read_text(encoding="utf-8")))
        else:
            result = jdk.summarize_recording(args.input, args.json_output, jfr=args.jfr)
        print(json.dumps(result, indent=2, allow_nan=False))
        return 0
    except (OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError) as error:
        print(f"Observer refused: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
