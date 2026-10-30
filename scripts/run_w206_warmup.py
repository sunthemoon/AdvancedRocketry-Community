"""Native W206 campaign; caller owns heavy-jvm, source-pinned diagnostic JAR, and evidence directory."""
from __future__ import annotations
import argparse
import ctypes
import json
from pathlib import Path
import shutil
import time
import uuid
import zipfile
import hashlib
import subprocess

import run_dedicated_server_smoke as smoke
from run_v060_flight_server_smoke import _server_command
from run_v180_rocket_settlement_completion import require_lock
from run_w197_i6_cost import Memory, FileTime, sha256, require
from w206_warmup_summary import summarize

TEMP = Path("D:/GitHub/ARCE-Task-Evidence/Temp")


class IO(ctypes.Structure):
    _fields_ = [(name, ctypes.c_ulonglong) for name in
                ("reads", "writes", "others", "read_bytes", "write_bytes", "other_bytes")]


def archive_timing(session, output):
    """The optional probe uses CREATE_NEW per process. Retire only its copied diagnostic file, never world state."""
    source = session / "escrow-consumer-timing.json"
    if source.is_file():
        target = output / "server-escrow-consumer-timing.json"
        require(not target.exists(), "Timing archive already exists")
        shutil.copy2(source, target)
        require(sha256(source) == sha256(target), "Timing archive did not verify")
        source.unlink()


def inventory(production, diagnostic, attribution=False):
    with zipfile.ZipFile(production) as a, zipfile.ZipFile(diagnostic) as b:
        aa = {n: hashlib.sha256(a.read(n)).hexdigest() for n in a.namelist()}
        bb = {n: hashlib.sha256(b.read(n)).hexdigest() for n in b.namelist()}
    additions = sorted(bb.keys() - aa.keys())
    changed = sorted(n for n in aa.keys() & bb.keys() if aa[n] != bb[n])
    require(not any("/W206" in n for n in aa), "Normal JAR contains diagnostic fixture")
    require(additions and all("/W206" in n or attribution and "/W212Timing" in n for n in additions), "Unexpected diagnostic additions")
    allowed = set()
    if attribution:
        from w212_instrument import PACKAGE, TARGETS
        stems = {PACKAGE + "/" + p.removesuffix(".java") for p in TARGETS}
        # Nested classes inherit changed source line tables even when their own bodies are untouched.
        allowed = {n for n in changed if n.endswith(".class") and
                   any(n == stem + ".class" or n.startswith(stem + "$") for stem in stems)}
    require(set(changed) <= allowed and not aa.keys() - bb.keys(), "Unlisted production entries differ: " + repr(changed))
    return dict(production_sha256=sha256(production), diagnostic_sha256=sha256(diagnostic), added=additions, changed=changed)


def samples_for(process):
    memory = Memory(); memory.cb = ctypes.sizeof(memory)
    memory_api = ctypes.WinDLL("psapi").GetProcessMemoryInfo
    memory_api.argtypes = (ctypes.c_void_p, ctypes.POINTER(Memory), ctypes.c_ulong)
    io_api = ctypes.windll.kernel32.GetProcessIoCounters
    io_api.argtypes = (ctypes.c_void_p, ctypes.POINTER(IO))
    times = ctypes.windll.kernel32.GetProcessTimes
    times.argtypes = (ctypes.c_void_p,) + (ctypes.POINTER(FileTime),) * 4
    system = ctypes.windll.kernel32.GetSystemTimes
    system.argtypes = (ctypes.POINTER(FileTime),) * 3
    handle = int(process.process._handle)
    row = dict(time=time.time())
    if memory_api(handle, ctypes.byref(memory), memory.cb):
        row.update(rss=memory.rss, peak_rss=memory.peak)
    io = IO()
    if io_api(handle, ctypes.byref(io)):
        row.update({name: getattr(io, name) for name, _ in IO._fields_})
    idle, kernel, user = FileTime(), FileTime(), FileTime()
    if system(ctypes.byref(idle), ctypes.byref(kernel), ctypes.byref(user)):
        row.update(host_idle_100ns=idle.value(), host_total_100ns=kernel.value() + user.value())
    created, exited, kernel, user = FileTime(), FileTime(), FileTime(), FileTime()
    if times(handle, ctypes.byref(created), ctypes.byref(exited), ctypes.byref(kernel), ctypes.byref(user)):
        row["server_cpu_100ns"] = kernel.value() + user.value()
    return row


def run_stage(args, session, output, mode, shape):
    require_lock(args.lock_owner)
    for drive in ("C:/", "D:/"):
        require(shutil.disk_usage(drive).free >= 10 * 1024**3, "Disk below 10 GiB: " + drive)
    output.mkdir()
    command = _server_command(args.java); command[2] = "-Xmx4G"
    command[1:1] = ["-Dadvancedrocketrycommunity.w206=true",
                    "-Dadvancedrocketrycommunity.w206Mode=" + mode,
                    "-Dadvancedrocketrycommunity.w206Shape=" + shape,
                    "-Dadvancedrocketrycommunity.escrowConsumerTiming=true",
                    "-Xlog:gc*:file=gc.log:time,uptime,level,tags"]
    command[1:1] = ["-Dadvancedrocketrycommunity.w212TimingOnly=" + str(args.transition_mode == "timing-only").lower(),
                    "-Dadvancedrocketrycommunity.w212Repetitions=" + str(args.repetitions if mode == "flight" else 1)]
    if args.jfr:
        settings = "profile"
        if args.attribution:
            settings = str(output.resolve() / "attribution.jfc")
            jdk = Path(args.java).parent.parent
            subprocess.run([str(jdk / "bin/jfr.exe"), "configure", "--input", str(jdk / "lib/jfr/profile.jfc"),
                            "--output", settings, "jdk.ClassLoad#enabled=true", "jdk.ClassLoad#threshold=0ms"], check=True)
        command.insert(1, f"-XX:StartFlightRecording=filename=profile.jfr,settings={settings},dumponexit=true")
    row = dict(source_commit=args.source_commit, mode=mode, shape=shape, command=command, status="RUNNING",
               started=time.time(), destination_region_files_before=sorted(str(p.relative_to(session)) for p in
                   (session / "world/dimensions/advancedrocketrycommunity/moon/region").glob("*.mca")))
    (output / "start.json").write_text(json.dumps(row, indent=2))
    process = None; samples = []
    try:
        process = smoke.CapturedProcess(command, session, output / "native.log")
        row["pid"] = process.process.pid
        deadline = time.monotonic() + 1200
        while process.process.poll() is None and time.monotonic() < deadline:
            samples.append(samples_for(process)); time.sleep(1)
        require(process.process.poll() is not None, "20-minute external observation ceiling reached")
        row["exit_code"] = process.finish(30)
        require(row["exit_code"] == 0, "Native JVM exited nonzero")
        row["status"] = "COLLECTED"
    except Exception as error:
        row.update(status="FAIL", error=str(error))
    finally:
        if process is not None: process.abort()
        (output / "host-samples.json").write_text(json.dumps(samples, indent=2))
        for path in (session / "world").glob("w206-*"):
            shutil.copy2(path, output / path.name)
        for path in (session / "world").glob("w212-*"):
            shutil.copy2(path, output / path.name)
        for path in (session / "gc.log", session / "logs/latest.log", session / "logs/debug.log", session / "profile.jfr"):
            if path.is_file(): shutil.copy2(path, output / ("server-" + path.name))
        archive_timing(session, output)
        for path in (session / "crash-reports").glob("*.txt"):
            shutil.copy2(path, output / path.name)
        log = (output / "native.log").read_text(encoding="utf-8", errors="replace") if (output / "native.log").exists() else ""
        row["errors"] = [line for line in log.splitlines() if "/ERROR]" in line or "/FATAL]" in line]
        row["overload_warnings"] = [line for line in log.splitlines() if "Can't keep up" in line]
        if (output / "w206-result.json").exists():
            result = json.loads((output / "w206-result.json").read_text())
            row["outcome"] = result.get("outcome")
            summary = summarize(output)
            (output / "summary.json").write_text(json.dumps(summary, indent=2))
            expected = {"seed": "GENERATED_SEED", "flight": "MEASURED", "capacity": "LIFECYCLE_MEASURED",
                        "replace": "REPLACEMENT_MEASURED", "restart": "RESTART_CUT", "recover": "MEASURED"}[mode]
            if row["status"] == "COLLECTED":
                row["status"] = "PASS" if result.get("outcome") == expected else "FAIL"
            if mode in ("flight", "recover") and not summary["flight_qualified"]: row["status"] = "FAIL"
            if mode == "recover" and not all(result.get(k) for k in ("restart_absent", "restart_source_ready")): row["status"] = "FAIL"
            if mode not in ("seed", "recover") and not summary["timing"]["timing_qualified"]: row["status"] = "FAIL"
        else:
            row["status"] = "FAIL"; row["missing_result"] = True
        if row["errors"]: row["status"] = "FAIL"
        row["finished"] = time.time()
        (output / "native-result.json").write_text(json.dumps(row, indent=2))
    print(json.dumps(row), flush=True)
    return row


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for flag in ("jar", "production-jar", "libraries", "evidence"):
        parser.add_argument("--" + flag, required=True, type=Path)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--lock-owner", required=True)
    parser.add_argument("--jfr", action="store_true")
    parser.add_argument("--attribution", action="store_true")
    parser.add_argument("--transition-mode", choices=("full", "timing-only"), default="full")
    parser.add_argument("--repetitions", type=int, choices=range(1, 5), default=1)
    parser.add_argument("--cases", default="new-contained,new-border,new-square,new-strip,generated-contained,generated-border,generated-square,generated-strip,capacity,replace,restart")
    parser.add_argument("--java", default="C:/Program Files/Java/jdk-17.0.7/bin/java.exe")
    args = parser.parse_args(); require_lock(args.lock_owner)
    args.evidence.mkdir(parents=True, exist_ok=True)
    jars = inventory(args.production_jar, args.jar, args.attribution)
    (args.evidence / "jar-inventory.json").write_text(json.dumps(jars, indent=2))
    require(not smoke.is_link_or_junction(args.libraries)
            and not any(smoke.is_link_or_junction(p) for p in args.libraries.rglob("*")), "Unsafe library template")
    runs = []
    for case in args.cases.split(","):
        require(case in {"capacity", "replace", "restart"} or case in
                {t + "-" + s for t in ("new", "generated") for s in ("contained", "border", "square", "strip")}, "Unknown case")
        session = TEMP / ("W206-native-" + uuid.uuid4().hex); session.mkdir()
        token = uuid.uuid4().hex; (session / ".w206-owned").write_text(token)
        try:
            shutil.copytree(args.libraries, session / "libraries")
            (session / "mods").mkdir(); shutil.copy2(args.jar, session / "mods" / args.jar.name)
            properties = smoke.expected_server_properties(smoke.allocate_port(), True)
            properties.update({"level-seed": "206", "level-type": "minecraft:flat", "max-tick-time": "60000",
                               "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}], "biome": "minecraft:plains"})})
            (session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(properties.items())), encoding="ascii")
            (session / "eula.txt").write_text("eula=true\n")
            if case.startswith(("new-", "generated-")):
                shape = case.split("-", 1)[1]
                stages = [("seed", shape), ("flight", shape)] if case.startswith("generated-") else [("flight", shape)]
            else:
                stages = [(case, "contained")] + ([("recover", "contained")] if case == "restart" else [])
            for index, (mode, shape) in enumerate(stages):
                row = run_stage(args, session, args.evidence / f"{case}-{index}-{mode}", mode, shape)
                row["case"] = case; runs.append(row)
                if row["status"] != "PASS": break
        finally:
            require(session.resolve().parent == TEMP.resolve() and not smoke.is_link_or_junction(session)
                    and (session / ".w206-owned").read_text() == token, "Cleanup ownership changed")
            shutil.rmtree(session)
            (args.evidence / "campaign.json").write_text(json.dumps(dict(jars=jars, runs=runs), indent=2))
        # A production failure is a reported stop, not permission to patch it or extend lifetimes.
        if runs[-1]["status"] != "PASS": return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
