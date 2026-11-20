"""Host measurements and caller-supplied run identity, without process discovery."""

import ctypes
import os
import platform
import re
import shutil
import time
from pathlib import Path

from .common import utc_now, write_json

RUN_FIELDS = ("git_commit", "jar_sha256", "forge", "java", "command", "start_utc", "end_utc", "exit_code")


def parse_meminfo(text):
    values = dict((key, int(value)) for key, value in re.findall(r"^(\w+):\s+(\d+) kB$", text, re.M))
    return {"memory_mib": values["MemTotal"] / 1024, "swap_mib": values["SwapTotal"] / 1024}


def parse_cpu_stat(text):
    line = next(line for line in text.splitlines() if line.startswith("cpu "))
    fields = [int(x) for x in line.split()[1:]]
    if len(fields) < 8 or any(x < 0 for x in fields):
        raise ValueError("Expected Linux aggregate CPU counters")
    # guest and guest_nice are already included in user/nice. Do not count twice.
    return sum(fields[:8]), fields[7]


def cpu_steal_percent(before, after):
    total_a, steal_a = parse_cpu_stat(before)
    total_b, steal_b = parse_cpu_stat(after)
    if total_b <= total_a or steal_b < steal_a:
        raise ValueError("CPU counters did not advance monotonically")
    return 100 * (steal_b - steal_a) / (total_b - total_a)


def windows_memory():
    from ctypes import wintypes as w

    class MemoryStatus(ctypes.Structure):
        _fields_ = [("length", w.DWORD), ("load", w.DWORD)] + [(key, ctypes.c_ulonglong) for key in
            ("total_phys", "avail_phys", "total_pagefile", "avail_pagefile", "total_virtual", "avail_virtual", "avail_extended")]

    status = MemoryStatus()
    status.length = ctypes.sizeof(status)
    api = ctypes.WinDLL("kernel32", use_last_error=True).GlobalMemoryStatusEx
    api.argtypes = [ctypes.POINTER(MemoryStatus)]
    api.restype = w.BOOL
    if not api(ctypes.byref(status)):
        raise ctypes.WinError(ctypes.get_last_error())
    return {"memory_mib": status.total_phys / 1024 ** 2,
            "swap_mib": max(0, status.total_pagefile - status.total_phys) / 1024 ** 2,
            "swap_basis": "GlobalMemoryStatusEx commit limit minus physical memory (not Linux SwapTotal)"}


def measure_host(provider, plan, disk, *, proc=Path("/proc"), sleep=time.sleep):
    host = {"provider": provider, "plan": plan, "os": platform.platform(), "vcpu": os.cpu_count(),
            "disk_free_gib": shutil.disk_usage(disk).free / 1024 ** 3, "measured_utc": utc_now()}
    if os.name == "nt":
        host.update(windows_memory())
        host["cpu_steal_average"] = "NOT_APPLICABLE (Windows)"
    else:
        host.update(parse_meminfo((proc / "meminfo").read_text()))
        before = (proc / "stat").read_text()
        sleep(1)
        after = (proc / "stat").read_text()
        host["cpu_steal_average"] = cpu_steal_percent(before, after)
        host["cpu_steal_unit"] = "percent over 1-second interval, not the whole campaign"
    return host


def write_host_record(output, *, provider, plan, disk, run, measure=measure_host):
    if set(run) != set(RUN_FIELDS):
        raise ValueError(f"Run fields must be exactly {RUN_FIELDS}")
    if re.fullmatch(r"[0-9a-f]{40}", run["git_commit"]) is None or re.fullmatch(r"[0-9a-f]{64}", run["jar_sha256"]) is None:
        raise ValueError("Invalid run commit or JAR SHA-256")
    from datetime import datetime
    times = []
    for key in ("start_utc", "end_utc"):
        if not run[key].endswith("Z"):
            raise ValueError("Run timestamps must be UTC ending in Z")
        times.append(datetime.fromisoformat(run[key].replace("Z", "+00:00")))
    if times[1] < times[0] or type(run["exit_code"]) is not int:
        raise ValueError("Invalid run interval or exit code")
    if any(not isinstance(run[key], str) or not run[key].strip() for key in ("forge", "java", "command")):
        raise ValueError("Run Forge, Java and command must be supplied")
    # Caller provides a SERVER command or a redacted client command, never discovered JVM arguments.
    if re.search(r"(?i)access.?token|password|client.?secret|bearer\s", run["command"]):
        raise ValueError("Do not record a credential-bearing command")
    value = {"schema_version": 1, "host": measure(provider, plan, disk), "run": dict(run)}
    write_json(output, value)
    return value
