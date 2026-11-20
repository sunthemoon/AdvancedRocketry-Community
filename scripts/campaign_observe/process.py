"""Resident memory and accumulated CPU time, without reading process arguments."""

import csv
import ctypes
import os
import re
from pathlib import Path

from .common import safe_path, schedule, utc_now


def valid_pid(pid):
    if type(pid) is not int or pid <= 0:
        raise ValueError("Expected one positive numeric PID, never PID 0 or a process name")
    return pid


def parse_proc_status(text):
    fields = {}
    for line in text.splitlines():
        match = re.fullmatch(r"(\w+):\s+(\d+) kB\s*", line)
        if match:
            fields[match[1]] = int(match[2]) * 1024
    if "VmRSS" not in fields:
        raise ValueError("VmRSS missing (process exited or is not a userspace process)")
    return fields["VmRSS"]


def parse_proc_stat(text, ticks_per_second):
    # comm can contain spaces and parentheses. Fields after its final ')' start at 3.
    fields = text[text.rindex(")") + 2:].split()
    return {"cpu_seconds": (int(fields[11]) + int(fields[12])) / ticks_per_second,
            "birth_id": fields[19]}


def windows_process(pid):
    from ctypes import wintypes as w

    class Counters(ctypes.Structure):
        _fields_ = [("cb", w.DWORD), ("PageFaultCount", w.DWORD)] + [
            (key, ctypes.c_size_t) for key in ("PeakWorkingSetSize", "WorkingSetSize",
            "QuotaPeakPagedPoolUsage", "QuotaPagedPoolUsage", "QuotaPeakNonPagedPoolUsage",
            "QuotaNonPagedPoolUsage", "PagefileUsage", "PeakPagefileUsage")]

    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    psapi = ctypes.WinDLL("psapi", use_last_error=True)
    kernel.OpenProcess.argtypes = [w.DWORD, w.BOOL, w.DWORD]
    kernel.OpenProcess.restype = w.HANDLE
    kernel.CloseHandle.argtypes = [w.HANDLE]
    kernel.CloseHandle.restype = w.BOOL
    kernel.GetProcessTimes.argtypes = [w.HANDLE] + [ctypes.POINTER(w.FILETIME)] * 4
    kernel.GetProcessTimes.restype = w.BOOL
    psapi.GetProcessMemoryInfo.argtypes = [w.HANDLE, ctypes.POINTER(Counters), w.DWORD]
    psapi.GetProcessMemoryInfo.restype = w.BOOL
    handle = kernel.OpenProcess(0x0400 | 0x0010, False, valid_pid(pid))
    if not handle:
        raise ctypes.WinError(ctypes.get_last_error())
    try:
        counters = Counters()
        counters.cb = ctypes.sizeof(counters)
        times = [w.FILETIME() for _ in range(4)]
        if not psapi.GetProcessMemoryInfo(handle, ctypes.byref(counters), counters.cb):
            raise ctypes.WinError(ctypes.get_last_error())
        if not kernel.GetProcessTimes(handle, *(ctypes.byref(t) for t in times)):
            raise ctypes.WinError(ctypes.get_last_error())
        values = [(t.dwHighDateTime << 32) | t.dwLowDateTime for t in times]
        return {"rss_bytes": counters.WorkingSetSize, "cpu_seconds": (values[2] + values[3]) / 1e7,
                "birth_id": str(values[0])}
    finally:
        kernel.CloseHandle(handle)


def read_process(pid):
    valid_pid(pid)
    if os.name == "nt":
        return windows_process(pid)
    root = Path("/proc") / str(pid)
    before = parse_proc_stat((root / "stat").read_text(), os.sysconf("SC_CLK_TCK"))
    rss = parse_proc_status((root / "status").read_text())
    after = parse_proc_stat((root / "stat").read_text(), os.sysconf("SC_CLK_TCK"))
    if before["birth_id"] != after["birth_id"]:
        raise ValueError("PID reused during sample")
    return {**after, "rss_bytes": rss}


def parse_heap_info(text):
    """JDK 17 G1/Parallel/Serial total/used heap lines; never metaspace.

    Other collector formats are refused, not guessed.
    """
    sizes = []
    for line in text.splitlines():
        if re.match(r"\s*(?:Metaspace|class space)", line, re.I):
            continue
        match = re.search(r"\btotal\s+(\d+(?:\.\d+)?)([KMGT]?),\s*used\s+(\d+(?:\.\d+)?)([KMGT]?)\b", line, re.I)
        if not match:
            continue
        total, tu, used, uu = match.groups()
        def size(number, unit):
            return int(float(number) * 1024 ** ("KMGT".index(unit.upper()) + 1 if unit else 0))
        sizes.append((size(total, tu), size(used, uu)))
    if not sizes:
        raise ValueError("Unsupported GC.heap_info heap format")
    return {"heap_committed_bytes": sum(x[0] for x in sizes), "heap_used_bytes": sum(x[1] for x in sizes)}


def sample_process(pid, output, *, duration, interval=1.0, heap_target=None, heap_interval=30,
                   reader=read_process, scheduler=schedule):
    """heap_target must hold the harness's still-running server Popen handle."""
    from .jdk import heap_info
    valid_pid(pid)
    if heap_target is not None and (heap_target.pid != pid or heap_target.role != "server"):
        raise ValueError("Automatic heap sampling requires this campaign server")
    if not interval <= heap_interval <= 3600:
        raise ValueError("Heap interval must be between the sample interval and 3600 s")
    import time
    birth = None
    next_heap = 0
    count = 0
    with safe_path(output).open("x", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=("utc", "pid", "birth_id", "rss_bytes", "cpu_seconds",
                                                    "heap_committed_bytes", "heap_used_bytes"))
        writer.writeheader()
        for _ in scheduler(duration, interval):
            sample = reader(pid)
            if birth is not None and birth != sample["birth_id"]:
                raise ValueError("PID reused; start a new sampler")
            birth = sample["birth_id"]
            if heap_target is not None and time.monotonic() >= next_heap:
                sample.update(parse_heap_info(heap_info(heap_target)))
                next_heap = time.monotonic() + heap_interval
            writer.writerow({"utc": utc_now(), "pid": pid, **sample})
            stream.flush()
            count += 1
    return count
