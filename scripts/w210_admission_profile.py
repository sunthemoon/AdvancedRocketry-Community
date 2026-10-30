"""Supplement W208's unchanged native operation with diagnostic-only C1 resource observations."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import shutil
import sys

import run_w208_availability as native

OBSERVE = '        event.put("thread", Thread.currentThread().getName());'
METRICS = '''
        if (label.equals("C1_RETAIN_BEFORE") || label.equals("C1_RETAIN_AFTER")) {
            var bean = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
            event.put("cpu_ns", bean.getCurrentThreadCpuTime());
            event.put("allocated_bytes", bean.getThreadAllocatedBytes(Thread.currentThread().getId()));
            event.put("epoch_ms", System.currentTimeMillis());
        }'''


def instrument(source: str) -> str:
    if source.count(OBSERVE) != 1 or 'event.put("allocated_bytes"' in source:
        raise ValueError("Expected exactly one uninstrumented W208 observer")
    return source.replace(OBSERVE, OBSERVE + METRICS)


def instrument_export(export: Path, evidence: Path):
    relative = Path("src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/W208NativeAvailability.java")
    file = export / relative
    before = file.read_text(encoding="utf-8")
    after = instrument(before)
    file.write_text(after, encoding="utf-8")
    import difflib
    (evidence / "diagnostic-observer.patch").write_text("".join(difflib.unified_diff(
        before.splitlines(keepends=True), after.splitlines(keepends=True), fromfile=str(relative), tofile=str(relative))), encoding="utf-8")


def profiled_stage(args, session, output, stage, active):
    command = native._server_command
    def profiled_command(java):
        result = command(java)
        # Leave the heap slots intact: W208 sets slot 2 to -Xmx4G.
        result.insert(3, f"-XX:StartFlightRecording=filename=profile-{stage}.jfr,settings=profile,dumponexit=true")
        return result
    native._server_command = profiled_command
    try:
        return ORIGINAL_STAGE(args, session, output, stage, active)
    finally:
        native._server_command = command
        profile = session / f"profile-{stage}.jfr"
        if profile.exists() and (active[0] is None or active[0].process.poll() is not None):
            shutil.copy2(profile, output / f"{stage}.jfr")


ORIGINAL_STAGE = native.run_stage


def main():
    if len(sys.argv) > 1 and sys.argv[1] == "instrument":
        parser = argparse.ArgumentParser(description=__doc__)
        parser.add_argument("command", choices=["instrument"])
        parser.add_argument("export", type=Path)
        parser.add_argument("evidence", type=Path)
        args = parser.parse_args()
        instrument_export(args.export, args.evidence)
        return 0
    native.run_stage = profiled_stage
    return native.main()


if __name__ == "__main__":
    raise SystemExit(main())
