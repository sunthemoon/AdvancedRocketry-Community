"""Run the W197 diagnostic JAR in an owned native-server copy under the caller's heavy-JVM lock."""
from __future__ import annotations

import argparse
import ctypes
import hashlib
import json
from pathlib import Path
import shutil
import time
import uuid
import zipfile

import run_dedicated_server_smoke as smoke
from run_v060_flight_server_smoke import _server_command
from run_v180_rocket_settlement_completion import require_lock
from w197_cost_summary import summarize

TEMP = Path("D:/GitHub/ARCE-Task-Evidence/Temp")


class Memory(ctypes.Structure):
    _fields_ = [("cb", ctypes.c_ulong), ("faults", ctypes.c_ulong)] + [
        (name, ctypes.c_size_t) for name in ("peak", "rss", "pool_peak", "pool", "nonpool_peak", "nonpool", "pagefile", "pagefile_peak")]


class FileTime(ctypes.Structure):
    _fields_ = [("low", ctypes.c_ulong), ("high", ctypes.c_ulong)]

    def value(self):
        return self.low + (self.high << 32)


def sha256(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def jar_inventory(production, diagnostic):
    with zipfile.ZipFile(production) as normal, zipfile.ZipFile(diagnostic) as bench:
        a = {name: hashlib.sha256(normal.read(name)).hexdigest() for name in normal.namelist()}
        b = {name: hashlib.sha256(bench.read(name)).hexdigest() for name in bench.namelist()}
    require(not any("W197" in name for name in a), "Normal production JAR ships benchmark code")
    added = sorted(b.keys() - a.keys())
    require(added and all("/W197" in name for name in added), "Unexpected diagnostic JAR addition")
    require(not a.keys() - b.keys(), "Diagnostic JAR lost production files")
    changed = sorted(name for name in a.keys() & b.keys() if a[name] != b[name])
    require(not changed, "Diagnostic changed production entries: " + str(changed))
    return dict(production_sha256=sha256(production), diagnostic_sha256=sha256(diagnostic), added=added, changed=changed)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--production-jar", required=True, type=Path)
    parser.add_argument("--libraries", required=True, type=Path)
    parser.add_argument("--evidence", required=True, type=Path)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--lock-owner", required=True)
    parser.add_argument("--java", default="C:/Program Files/Java/jdk-17.0.7/bin/java.exe")
    args = parser.parse_args()
    require_lock(args.lock_owner)
    output = args.evidence.resolve()
    output.mkdir(parents=True, exist_ok=True)
    require(not (output / "native-result.json").exists(), "Refusing to overwrite a native result")
    inventory = jar_inventory(args.production_jar, args.jar)
    (output / "jar-inventory.json").write_text(json.dumps(inventory, indent=2), encoding="utf-8")
    session = TEMP / ("W197-native-" + uuid.uuid4().hex)
    session.mkdir()
    token = uuid.uuid4().hex
    (session / ".w197-owned").write_text(token)
    row = dict(source_commit=args.source_commit, jars=inventory, status="RUNNING", starts=0)
    process = None
    samples = []
    memory_api = ctypes.WinDLL("psapi").GetProcessMemoryInfo
    memory_api.argtypes = (ctypes.c_void_p, ctypes.POINTER(Memory), ctypes.c_ulong)
    memory_api.restype = ctypes.c_int
    system_times = ctypes.windll.kernel32.GetSystemTimes
    system_times.argtypes = (ctypes.POINTER(FileTime),) * 3
    process_times = ctypes.windll.kernel32.GetProcessTimes
    process_times.argtypes = (ctypes.c_void_p,) + (ctypes.POINTER(FileTime),) * 4
    try:
        require(not smoke.is_link_or_junction(args.libraries)
                and not any(smoke.is_link_or_junction(p) for p in args.libraries.rglob("*")), "Unsafe library template")
        shutil.copytree(args.libraries, session / "libraries")
        (session / "mods").mkdir()
        shutil.copy2(args.jar, session / "mods" / args.jar.name)
        properties = smoke.expected_server_properties(smoke.allocate_port(), True)
        properties.update({"level-type": "minecraft:flat", "max-tick-time": "60000", "generator-settings": json.dumps({
            "layers": [{"block": "minecraft:bedrock", "height": 1}], "biome": "minecraft:plains"})})
        (session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(properties.items())), encoding="ascii")
        shutil.copy2(session / "server.properties", output / "server.properties")
        (session / "eula.txt").write_text("eula=true\n", encoding="ascii")
        command = _server_command(args.java)
        command[2] = "-Xmx4G"
        command[1:1] = ["-Dadvancedrocketrycommunity.w197I6Cost=true", "-Xlog:gc*:file=gc.log:time,uptime,level,tags"]
        require_lock(args.lock_owner)
        for drive in ("C:/", "D:/"):
            require(shutil.disk_usage(drive).free >= 10 * 1024**3, "Disk below 10 GiB: " + drive)
        row.update(command=command, starts=1, started=time.time())
        (output / "native-start.json").write_text(json.dumps(row, indent=2), encoding="utf-8")
        process = smoke.CapturedProcess(command, session, output / "native.log")
        row["pid"] = process.process.pid
        deadline = time.monotonic() + 2400
        while process.process.poll() is None and time.monotonic() < deadline:
            memory = Memory(); memory.cb = ctypes.sizeof(memory)
            idle, kernel, user = FileTime(), FileTime(), FileTime()
            created, exited, pkernel, puser = FileTime(), FileTime(), FileTime(), FileTime()
            sample = dict(time=time.time())
            if memory_api(int(process.process._handle), ctypes.byref(memory), memory.cb):
                sample.update(rss=memory.rss, peak_rss=memory.peak)
            if system_times(ctypes.byref(idle), ctypes.byref(kernel), ctypes.byref(user)):
                sample.update(host_idle_100ns=idle.value(), host_total_100ns=kernel.value() + user.value())
            if process_times(int(process.process._handle), ctypes.byref(created), ctypes.byref(exited), ctypes.byref(pkernel), ctypes.byref(puser)):
                sample["server_cpu_100ns"] = pkernel.value() + puser.value()
            samples.append(sample)
            time.sleep(2)
        require(process.process.poll() is not None, "40-minute harness deadline reached")
        row["exit_code"] = process.finish(30)
        require(row["exit_code"] == 0, "Native process exited nonzero")
        row["status"] = "COLLECTED"
    except Exception as error:
        row.update(status="FAIL", error=str(error))
    finally:
        if process is not None:
            process.abort()
        (output / "host-samples.json").write_text(json.dumps(samples, indent=2), encoding="utf-8")
        for path in (session / "world").glob("w197-*"):
            shutil.copy2(path, output / path.name)
        for path in (session / "gc.log", session / "logs/latest.log", session / "logs/debug.log"):
            if path.is_file():
                shutil.copy2(path, output / ("server-" + path.name))
        require(session.resolve().parent == TEMP.resolve() and not smoke.is_link_or_junction(session)
                and (session / ".w197-owned").read_text() == token, "Cleanup ownership changed")
        shutil.rmtree(session)
        row["finished"] = time.time()
        if (output / "w197-result.json").is_file():
            summary = summarize(output)
            (output / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
            row["fixture_qualified"] = summary["fixture_qualified"]
            row["timing_boundary_qualified"] = summary["timing_boundary_qualified"]
            if row["status"] == "COLLECTED":
                row["status"] = "PASS" if summary["fixture_qualified"] and summary["timing_boundary_qualified"] else "FAIL"
        log = (output / "native.log").read_text(encoding="utf-8", errors="replace") if (output / "native.log").exists() else ""
        row["errors"] = [line for line in log.splitlines() if "/ERROR]" in line or "/FATAL]" in line]
        row["overload_warnings"] = [line for line in log.splitlines() if "Can't keep up" in line]
        if row["errors"]:
            row["status"] = "FAIL"
        (output / "native-result.json").write_text(json.dumps(row, indent=2), encoding="utf-8")
    print(json.dumps(row, indent=2))
    return 0 if row["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
