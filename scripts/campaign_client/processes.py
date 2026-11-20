"""Owned process trees in Windows jobs, with bounded redacted output capture."""
import ctypes as C
from ctypes import wintypes as W
from collections import deque
import os
import subprocess
import threading
import time

from .evidence import redact

# Win32 process creation flags, spelled out so that the module also imports and tests on other systems.
CREATE_SUSPENDED = 0x00000004
CREATE_NEW_PROCESS_GROUP = 0x00000200


def require_windows(message="Live campaign sessions require Windows"):
    """The one platform guard for live sessions; tests replace it to exercise the Windows paths with fakes."""
    if os.name != "nt":
        raise ValueError(message)


class THREADENTRY32(C.Structure):
    _fields_ = [("size", W.DWORD), ("usage", W.DWORD), ("tid", W.DWORD), ("pid", W.DWORD),
                ("base_priority", W.LONG), ("delta_priority", W.LONG), ("flags", W.DWORD)]


class JOB_ACCOUNTING(C.Structure):
    _fields_ = [("user", C.c_int64), ("kernel", C.c_int64), ("period_user", C.c_int64),
                ("period_kernel", C.c_int64), ("faults", W.DWORD), ("total", W.DWORD),
                ("active", W.DWORD), ("terminated", W.DWORD)]


class ProcessJob:
    """Assign while suspended, before any descendant can escape the owned job."""
    def __init__(self):
        self.k = C.WinDLL("kernel32", use_last_error=True)
        for name, args, result in [
            ("CreateJobObjectW", [C.c_void_p, W.LPCWSTR], W.HANDLE),
            ("AssignProcessToJobObject", [W.HANDLE, W.HANDLE], W.BOOL),
            ("TerminateJobObject", [W.HANDLE, W.UINT], W.BOOL),
            ("QueryInformationJobObject", [W.HANDLE, C.c_int, C.c_void_p, W.DWORD, C.c_void_p], W.BOOL),
            ("CloseHandle", [W.HANDLE], W.BOOL),
            ("CreateToolhelp32Snapshot", [W.DWORD, W.DWORD], W.HANDLE),
            ("Thread32First", [W.HANDLE, C.POINTER(THREADENTRY32)], W.BOOL),
            ("Thread32Next", [W.HANDLE, C.POINTER(THREADENTRY32)], W.BOOL),
            ("OpenThread", [W.DWORD, W.BOOL, W.DWORD], W.HANDLE),
            ("ResumeThread", [W.HANDLE], W.DWORD),
        ]:
            fn = getattr(self.k, name)
            fn.argtypes, fn.restype = args, result
        self.handle = self.k.CreateJobObjectW(None, None)
        if not self.handle:
            raise C.WinError(C.get_last_error())

    def assign_resume(self, process):
        if not self.k.AssignProcessToJobObject(self.handle, W.HANDLE(int(process._handle))):
            raise C.WinError(C.get_last_error())
        snapshot = self.k.CreateToolhelp32Snapshot(4, 0)
        if snapshot == C.c_void_p(-1).value:
            raise C.WinError(C.get_last_error())
        try:
            entry = THREADENTRY32()
            entry.size = C.sizeof(entry)
            exists = self.k.Thread32First(snapshot, C.byref(entry))
            while exists:
                if entry.pid == process.pid:
                    thread = self.k.OpenThread(2, False, entry.tid)
                    if not thread:
                        raise C.WinError(C.get_last_error())
                    try:
                        if self.k.ResumeThread(thread) == 0xFFFFFFFF:
                            raise C.WinError(C.get_last_error())
                        return
                    finally:
                        self.k.CloseHandle(thread)
                exists = self.k.Thread32Next(snapshot, C.byref(entry))
            raise RuntimeError("Owned suspended process thread missing")
        finally:
            self.k.CloseHandle(snapshot)

    def close(self):
        if self.handle:
            if not self.k.TerminateJobObject(self.handle, 1):
                raise C.WinError(C.get_last_error())
            deadline = time.monotonic() + 10
            while True:
                accounting = JOB_ACCOUNTING()
                if not self.k.QueryInformationJobObject(self.handle, 1, C.byref(accounting), C.sizeof(accounting), None):
                    raise C.WinError(C.get_last_error())
                if not accounting.active:
                    break
                if time.monotonic() >= deadline:
                    raise TimeoutError("Owned process tree did not exit; job retained")
                time.sleep(0.05)
            self.k.CloseHandle(self.handle)
            self.handle = None


class CapturedProcess:
    def __init__(self, command, cwd, log, console=False):
        require_windows()
        self.lines, self.count, self.error = deque(maxlen=10000), 0, None
        self.lock = threading.Lock()
        self.job = ProcessJob()
        self.process = None
        self.stream = log.open("x", encoding="utf-8")
        env = dict(os.environ)
        for name in ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH"):
            env.pop(name, None)
        try:
            self.process = subprocess.Popen(command, cwd=cwd, env=env, stdin=subprocess.PIPE if console else
                                            subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                            text=True, encoding="utf-8", errors="replace",
                                            creationflags=CREATE_SUSPENDED | CREATE_NEW_PROCESS_GROUP)
            self.job.assign_resume(self.process)
        except BaseException:
            if self.process is not None:
                self.process.kill()  # Still suspended; no descendants exist if assignment failed.
                self.process.wait(timeout=10)
            self.job.close()
            self.stream.close()
            raise
        self.reader = threading.Thread(target=self._read, daemon=True)
        self.reader.start()

    def _read(self):
        try:
            while line := self.process.stdout.readline(65536):
                if len(line) == 65536 and not line.endswith("\n"):
                    raise ValueError("Process output line exceeds redaction bound")
                line = redact(line)
                self.stream.write(line)
                self.stream.flush()
                with self.lock:
                    self.lines.append((self.count, line.rstrip("\n")))
                    self.count += 1
        except Exception as exc:
            self.error = type(exc).__name__

    def command(self, text):
        if self.process.poll() is not None or self.process.stdin is None:
            raise ValueError("Owned server is not running")
        self.process.stdin.write(text + "\n")
        self.process.stdin.flush()

    def tail(self, count):
        with self.lock:
            return [line for _, line in list(self.lines)[-count:]] if count else []

    def wait_for(self, substring, timeout, sleep, start=0):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if self.error:
                raise ValueError("Process output capture failed")
            with self.lock:
                found = [line for index, line in self.lines if index >= start and substring in line]
            if found:
                return found[0]
            if self.process.poll() is not None:
                raise ValueError("Owned process exited while waiting")
            sleep(0.1)
        raise TimeoutError("Process log wait expired")

    def close(self, server=False):
        try:
            if server and self.process.poll() is None:
                try:
                    self.command("stop")
                    self.process.wait(timeout=30)
                except (OSError, ValueError, subprocess.TimeoutExpired):
                    pass
        finally:
            # Kill the job even if the original parent exited: descendants still belong to it.
            self.job.close()
            self.process.wait(timeout=10)
            self.reader.join(timeout=10)
            if self.reader.is_alive():
                raise RuntimeError("Owned output reader did not stop")
            self.process.stdout.close()
            if self.process.stdin:
                self.process.stdin.close()
            self.stream.close()
        if self.error:
            raise ValueError("Process output capture failed")
