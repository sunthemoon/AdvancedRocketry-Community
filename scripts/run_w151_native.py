#!/usr/bin/env python3
"""W151 same-column native demand and two-restart cuts; called by the atomic-lock owner."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import statistics
import subprocess
import threading
import uuid

import run_v180_escrow_forced_stop as escrow
import run_v180_governed_benchmark as performance

smoke = escrow.smoke
MARKER = re.compile(r"W151_NATIVE_(DONE status=(\S+)|CUT boundary=(\S+))")
# Prespecified service assumptions, not values fitted to the observed outcome or production timeouts.
SERVICE = dict(S=200, F=200, R=200, C=40, Q=1000, W=200)


def cycle_bounds(raw: dict) -> list[dict]:
    rows = []
    for cycle in raw.get("cycles", []):
        if cycle.get("expected_refusal") is True:
            continue
        labels = {}
        for event in raw.get("events", []):
            if event.get("event") == "checkpoint" and event.get("cycle") == cycle["request"]:
                labels.setdefault(event["label"], event["tick"])
        capture, callback, writer, ack = (cycle[k] for k in ("capture", "callback", "writer_capture", "ack"))
        stored, forced, read = (labels[k] for k in ("BARRIER_STORE_AFTER", "BARRIER_FORCE_AFTER", "BARRIER_READBACK_AFTER"))
        # S conservatively includes the cadence wait AND the complete retained-store join.
        # F is the complete selected-worker force join; R includes reader queuing.
        measured = dict(S=stored-capture, F=forced-stored, R=read-forced, C=callback-read, Q=writer-callback, W=ack-writer)
        writer_events = [event for event in raw.get("events", []) if event.get("event") == "checkpoint"
                         and event.get("cycle") == cycle["request"] and writer <= event["tick"] <= ack]
        guard_bracket = any(event["label"] in ("K1", "K8") for event in writer_events)
        replacement = any(event["label"] in ("K2", "K9") for event in writer_events)
        rows.append(dict(request=cycle["request"], measured=measured, assumptions=SERVICE,
                         elapsed=ack-capture, bound=20+sum(SERVICE.values()),
                         passed=all(0 <= value <= SERVICE[key] for key, value in measured.items())
                         and ack-capture <= 20+sum(SERVICE.values()),
                         guard_and_replacement_observed=guard_bracket and replacement))
    if not rows:
        raise ValueError("No eligible native cycles")
    return rows


def costs(raw: dict, rss: list[int]) -> dict:
    values = sorted(value / 1_000_000 for value in raw.get("tick_ns", []))
    nonsave = [value / 1_000_000 for value in raw.get("tick_ns_nonsave", [])]
    if not values or not rss or not nonsave:
        raise ValueError("Missing tick/RSS observations")
    observed = dict(mean_ms=statistics.mean(values), p95_ms=performance.percentile(values, .95),
                    p99_ms=performance.percentile(values, .99), nonsave_spike_ms=max(nonsave), rss_bytes=max(rss))
    return dict(observed=observed, limits=performance.BUDGETS,
                passed=all(value <= performance.BUDGETS[key] for key, value in observed.items()))


def reference_requests(raw: dict) -> list[dict]:
    rows = [event for event in raw.get("events", []) if event.get("event") == "reference_request"]
    if not rows or raw.get("reference_requests") != len(rows):
        raise ValueError("Missing reference player request observations")
    seen = set()
    for index, row in enumerate(rows):
        if row["request"] in seen or row["attempt"] != index + 1:
            raise ValueError("Reference request replay or missing attempt")
        if index and (row["previous_outcome"] != "FAILED" or row["previous_request"] != rows[index-1]["request"]):
            raise ValueError("Reference player request replaced a non-failed request")
        seen.add(row["request"])
    return rows


def run(args) -> dict:
    repo = Path(__file__).resolve().parents[1]
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip()
    if commit != args.source_commit or subprocess.check_output(["git", "status", "--porcelain"], cwd=repo, text=True):
        raise smoke.SmokeError("Requires exact clean committed source")
    lock = Path("D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm/owner.txt")
    token = os.environ.get("W151_LOCK_TOKEN", "")
    if not token or not lock.read_text(encoding="utf-8-sig").startswith(token):
        raise smoke.SmokeError("Outer atomic-lock ownership missing")
    evidence = args.evidence_dir.resolve(); evidence.mkdir(exist_ok=False)
    session = Path("D:/GitHub/ARCE-Task-Evidence/Temp") / ("w151-native-" + uuid.uuid4().hex)
    owned = uuid.uuid4().hex; marker = session / ".w151-owned"
    result = dict(status="IN_PROGRESS", source_commit=commit, jar_sha256=escrow.digest(args.jar), case=args.case,
                  service_ceilings_ticks=SERVICE, overall_deadline_ticks=4000, commands=[], stages=[])
    try:
        session.mkdir(); marker.write_text(owned, encoding="ascii")
        shutil.copytree(args.source_server / "libraries", session / "libraries")
        (session / "mods").mkdir(); shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = smoke.allocate_port(); smoke.write_server_configuration(session, port, True)
        props = smoke.expected_server_properties(port, True)
        props.update({"sync-chunk-writes": "true", "level-name": "world", "level-type": "minecraft:flat", "max-tick-time": "60000",
                      "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1},
                          {"block": "minecraft:dirt", "height": 2}, {"block": "minecraft:grass_block", "height": 1}], "biome": "minecraft:plains"})})
        (session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(props.items())), encoding="ascii")
        for stage in ("start", "recover1", "recover2"):
            for drive in (Path("C:/"), Path("D:/")):
                if shutil.disk_usage(drive).free < 10 * 1024**3:
                    raise smoke.SmokeError("Insufficient disk before native start")
            command = escrow._server_command(args.java); command[2] = "-Xmx4G"
            command[1:1] = ["-XX:ActiveProcessorCount=4", "-Dadvancedrocketrycommunity.w151Native=true",
                            "-Dadvancedrocketrycommunity.escrowLaunchTrace=true",
                            "-Dadvancedrocketrycommunity.w151Stage="+stage, "-Dadvancedrocketrycommunity.w151Case="+args.case,
                            "-Dadvancedrocketrycommunity.w151Commit="+commit]
            result["commands"].append(command)
            process = smoke.CapturedProcess(command, session, evidence / (stage + ".log"))
            stop = threading.Event(); rss = []; monitor_errors = []
            def sample():
                try:
                    while not stop.is_set():
                        value = performance._rss_bytes(process.process.pid)
                        if value is not None: rss.append(value)
                        stop.wait(5)
                except Exception as failure: monitor_errors.append(str(failure))
            sampler = threading.Thread(target=sample, daemon=True); sampler.start()
            try:
                index = process.wait_for(MARKER, 650)
                match = MARKER.search(process.lines[index])
                if match[3]:
                    if stage != "start" or match[3] != args.case:
                        raise smoke.SmokeError("Unexpected cut boundary")
                    process.abort()
                else:
                    process.command("stop"); code = process.finish()
                    if code != 0: raise smoke.SmokeError(f"Native exit {code}")
            finally:
                stop.set(); sampler.join(20)
                if process.process.poll() is None: process.abort()
                report = session / "world/w151-native.json"
                if report.is_file(): shutil.copy2(report, evidence / (stage + ".json"))
                result["stages"].append(dict(stage=stage, rss=rss, monitor_errors=monitor_errors))
            raw = json.loads((evidence / (stage + ".json")).read_text(encoding="utf-8"))
            expected = "RECOVERED" if stage != "start" else "CUT" if args.case in ("deferred", "queued", "ack") else "COMPLETE"
            if raw["status"] != expected:
                result["status"] = raw["status"]
                raise smoke.SmokeError(f"{stage}: {raw['status']}: {raw.get('error')}")
            if monitor_errors or any("/ERROR]" in line or "/FATAL]" in line for line in process.lines):
                raise smoke.SmokeError("Monitor failure or native ERROR/FATAL")
            if stage == "start" and expected == "COMPLETE":
                result["reference_requests"] = reference_requests(raw)
                result["cycles"] = cycle_bounds(raw); result["performance"] = costs(raw, rss)
                if not all(row["passed"] for row in result["cycles"]):
                    result["status"] = "BOUND_MISSED"; raise smoke.SmokeError("A prespecified service bound was missed; stopping")
                if not all(row["guard_and_replacement_observed"] for row in result["cycles"]):
                    raise smoke.SmokeError("Missing writer guard/replacement bracket")
                if not result["performance"]["passed"]:
                    raise smoke.SmokeError("Existing performance budget failed")
            if stage != "start" and raw.get("restart_initial_slot_unpinned") is not True:
                raise smoke.SmokeError("Restart pin lifetime not observed")
        result["status"] = "PASS"
    except Exception as failure:
        if result["status"] == "IN_PROGRESS": result["status"] = "FAIL"
        result["error"] = str(failure)
    finally:
        (evidence / "results.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
        if session.is_dir() and not session.is_symlink() and marker.read_text(encoding="ascii") == owned:
            shutil.rmtree(session)
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-server", type=Path, required=True)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--case", choices=("load", "save", "deferred", "queued", "ack"), required=True)
    result = run(parser.parse_args()); print(json.dumps(result, indent=2))
    return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
