"""Observe new Windows command jobs without acquiring existing process identities.

The Windows backend uses CPython's private _winapi creation handle API. Callers
must pin/test their Python build. Process creation and OS scheduling are not
preemptible; recorded elapsed limits are qualification checks, not a universal
wall-clock guarantee. Job ownership covers ordinary CreateProcess descendants,
not processes launched through an unrelated broker such as WMI.
"""
import ctypes
import math
import os
import subprocess
import threading
import time
from ctypes import wintypes
from pathlib import Path

if os.name == "nt":
    import _winapi
    import msvcrt


class _BasicLimits(ctypes.Structure):
    _fields_ = [("process_time", ctypes.c_longlong), ("job_time", ctypes.c_longlong),
                ("flags", wintypes.DWORD), ("minimum_working_set", ctypes.c_size_t),
                ("maximum_working_set", ctypes.c_size_t), ("active_processes", wintypes.DWORD),
                ("affinity", ctypes.c_size_t), ("priority", wintypes.DWORD),
                ("scheduling", wintypes.DWORD)]


class _IoCounters(ctypes.Structure):
    _fields_ = [(name, ctypes.c_ulonglong) for name in
                ("read_count", "write_count", "other_count", "read_bytes", "write_bytes", "other_bytes")]


class _ExtendedLimits(ctypes.Structure):
    _fields_ = [("basic", _BasicLimits), ("io", _IoCounters),
                ("process_memory", ctypes.c_size_t), ("job_memory", ctypes.c_size_t),
                ("peak_process_memory", ctypes.c_size_t), ("peak_job_memory", ctypes.c_size_t)]


def _kernel():
    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    signatures = {
        "CreateJobObjectW": ([ctypes.c_void_p, wintypes.LPCWSTR], wintypes.HANDLE),
        "SetInformationJobObject": ([wintypes.HANDLE, ctypes.c_int, ctypes.c_void_p, wintypes.DWORD], wintypes.BOOL),
        "AssignProcessToJobObject": ([wintypes.HANDLE, wintypes.HANDLE], wintypes.BOOL),
        "ResumeThread": ([wintypes.HANDLE], wintypes.DWORD),
    }
    for name, (arguments, result) in signatures.items():
        function = getattr(kernel, name)
        function.argtypes, function.restype = arguments, result
    return kernel


class _WindowsOwner:
    """Only handles returned for this new unnamed job and suspended process."""

    def __init__(self):
        self.kernel = None
        self.job = self.process = self.thread = self.pid = None
        self.assigned = self.resumed = False
        self.readers = {}
        self.parent_fds = []

    def launch(self, argv, cwd, env):
        self.kernel = _kernel()
        self.job = self.kernel.CreateJobObjectW(None, None)
        if not self.job:
            raise ctypes.WinError(ctypes.get_last_error())
        limits = _ExtendedLimits()
        limits.basic.flags = 0x2000  # JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE; no breakaway.
        if not self.kernel.SetInformationJobObject(self.job, 9, ctypes.byref(limits), ctypes.sizeof(limits)):
            raise ctypes.WinError(ctypes.get_last_error())
        stdin = os.open(os.devnull, os.O_RDONLY)
        self.parent_fds.append(stdin)
        writers = {}
        for name in ("stdout", "stderr"):
            read_fd, write_fd = os.pipe()
            self.readers[name] = read_fd
            self.parent_fds.append(write_fd)
            writers[name] = msvcrt.get_osfhandle(write_fd)
        standard = [msvcrt.get_osfhandle(stdin), writers["stdout"], writers["stderr"]]
        for handle in standard:
            os.set_handle_inheritable(handle, True)
        startup = subprocess.STARTUPINFO(dwFlags=subprocess.STARTF_USESTDHANDLES,
            hStdInput=standard[0], hStdOutput=standard[1], hStdError=standard[2],
            lpAttributeList={"handle_list": standard})
        flags = _winapi.CREATE_NO_WINDOW | 0x400 | 0x4  # UNICODE_ENVIRONMENT | SUSPENDED.
        self.process, self.thread, self.pid, _ = _winapi.CreateProcess(
            argv[0], subprocess.list2cmdline(argv), None, None, True, flags, env, str(cwd), startup)
        if not self.kernel.AssignProcessToJobObject(self.job, self.process):
            raise ctypes.WinError(ctypes.get_last_error())
        self.assigned = True
        if self.kernel.ResumeThread(self.thread) != 1:
            raise OSError("primary thread resume failed")
        self.resumed = True
        _winapi.CloseHandle(self.thread)
        self.thread = None
        for fd in self.parent_fds:
            os.close(fd)
        self.parent_fds.clear()

    def stop(self):
        if self.process and not self.assigned:
            # A failed assignment leaves our original creation thread suspended.
            _winapi.TerminateProcess(self.process, 1)
        if self.job:
            _winapi.CloseHandle(self.job)
            self.job = None

    def close(self):
        errors = []
        for name in ("job", "thread", "process"):
            handle = getattr(self, name)
            if handle:
                try:
                    _winapi.CloseHandle(handle)
                    setattr(self, name, None)
                except OSError as exc:
                    errors.append(type(exc).__name__ + ": " + str(exc)[:500])
        for fd in self.parent_fds:
            try:
                os.close(fd)
            except OSError as exc:
                errors.append(type(exc).__name__ + ": " + str(exc)[:500])
        self.parent_fds.clear()
        return errors


def _validate(argv, cwd, stdout, stderr, timeout, post_timeout, output_cap):
    if os.name != "nt":
        raise OSError("Windows command observation requires Windows")
    if not isinstance(argv, (list, tuple)) or not 1 <= len(argv) <= 128:
        raise ValueError("argv must have 1..128 entries")
    if any(not isinstance(value, str) or not value or "\0" in value for value in argv):
        raise ValueError("argv entries must be nonempty strings without NUL")
    if not Path(argv[0]).is_absolute() or len(subprocess.list2cmdline(argv).encode("utf-16-le")) > 65532:
        raise ValueError("executable must be absolute and command line bounded")
    for value, maximum in ((timeout, 3600), (post_timeout, 10)):
        if type(value) not in (int, float) or not math.isfinite(value) or not 0 < value <= maximum:
            raise ValueError("timing windows must be finite, positive and bounded")
    if type(output_cap) is not int or not 1 <= output_cap <= 4194304:
        raise ValueError("output cap must be 1..4194304 bytes")
    if not Path(cwd).is_absolute() or not Path(cwd).is_dir():
        raise ValueError("cwd must be an existing absolute directory")
    if any(not path.is_absolute() or not path.parent.is_dir() for path in (stdout, stderr)):
        raise ValueError("output parents must be existing absolute directories")
    if os.path.normcase(stdout) == os.path.normcase(stderr):
        raise ValueError("stdout and stderr must be distinct")


def observe(argv, *, cwd, env, stdout_path, stderr_path, timeout,
            post_timeout=10, output_cap=4194304):
    """Capture one new command; success requires exit, complete capture and limits.

    Output files are exclusively created. Environment is passed to the child but
    never included in the returned receipt. The caller owns file/directory scope.
    Closing the private job after the original process ends also retires its
    associated children; no existing process identity is ever opened.
    """
    stdout_path, stderr_path = Path(stdout_path), Path(stderr_path)
    _validate(argv, cwd, stdout_path, stderr_path, timeout, post_timeout, output_cap)
    owner, outputs, threads = _WindowsOwner(), {}, {}
    errors, failed = [], threading.Event()
    state = {name: {"bytes": 0, "observed_bytes": 0, "eof": False,
                   "overflow": False, "first_byte_elapsed_seconds": None} for name in ("stdout", "stderr")}
    started = time.monotonic()
    original_exit, outcome = None, "LAUNCH_ERROR"
    interrupted = None

    def capture(name, fd, output):
        try:
            with output:
                while chunk := os.read(fd, min(8192, output_cap + 1 - state[name]["observed_bytes"])):
                    if state[name]["first_byte_elapsed_seconds"] is None:
                        state[name]["first_byte_elapsed_seconds"] = time.monotonic() - started
                    state[name]["observed_bytes"] += len(chunk)
                    kept = chunk[:max(0, output_cap - state[name]["bytes"])]
                    output.write(kept)
                    output.flush()
                    state[name]["bytes"] += len(kept)
                    if state[name]["observed_bytes"] > output_cap:
                        state[name]["overflow"] = True
                        failed.set()
                        break
                else:
                    state[name]["eof"] = True
        except Exception as exc:
            errors.append(name + ": " + type(exc).__name__ + ": " + str(exc)[:500])
            failed.set()
        finally:
            try:
                os.close(fd)
            except OSError as exc:
                errors.append(name + " close: " + type(exc).__name__ + ": " + str(exc)[:500])
                failed.set()

    try:
        for name, path in (("stdout", stdout_path), ("stderr", stderr_path)):
            outputs[name] = path.open("xb")
        owner.launch(argv, cwd, env)
        for name, fd in list(owner.readers.items()):
            thread = threading.Thread(target=capture, args=(name, fd, outputs[name]), daemon=True)
            thread.start()
            threads[name] = thread
            del outputs[name]
            del owner.readers[name]
        while True:
            if failed.is_set():
                outcome = "CAPTURE_ERROR"
                break
            remaining = timeout - (time.monotonic() - started)
            if remaining <= 0:
                outcome = "TIMEOUT"
                break
            wait = _winapi.WaitForSingleObject(owner.process, max(1, min(20, math.ceil(remaining * 1000))))
            if wait == _winapi.WAIT_OBJECT_0:
                original_exit, outcome = _winapi.GetExitCodeProcess(owner.process), "EXITED"
                break
            if wait != _winapi.WAIT_TIMEOUT:
                raise OSError("unexpected original-process wait result")
    except Exception as exc:
        errors.append(type(exc).__name__ + ": " + str(exc)[:500])
    except BaseException as exc:
        interrupted = exc
    original_elapsed = time.monotonic() - started
    post_started = time.monotonic()
    post_exit = None
    try:
        owner.stop()
        reserve = min(0.25, post_timeout / 10)
        post_end = post_started + post_timeout - reserve
        if owner.process:
            remaining = max(0, post_end - time.monotonic())
            wait = _winapi.WaitForSingleObject(owner.process, math.floor(remaining * 1000))
            if wait == _winapi.WAIT_OBJECT_0:
                post_exit = _winapi.GetExitCodeProcess(owner.process)
            else:
                errors.append("original creation-handle wait incomplete")
        for thread in threads.values():
            thread.join(timeout=max(0, post_end - time.monotonic()))
    except Exception as exc:
        errors.append(type(exc).__name__ + ": " + str(exc)[:500])
    finally:
        for output in outputs.values():
            try:
                output.close()
            except OSError as exc:
                errors.append("output close: " + type(exc).__name__ + ": " + str(exc)[:500])
        for fd in owner.readers.values():
            try:
                os.close(fd)
            except OSError as exc:
                errors.append("pipe close: " + type(exc).__name__ + ": " + str(exc)[:500])
        owner.readers.clear()
        errors.extend(owner.close())
    post_elapsed = time.monotonic() - post_started
    if interrupted is not None:
        raise interrupted
    streams = {name: {**values, "reader_alive": name in threads and threads[name].is_alive()}
               for name, values in state.items()}
    captured = all(item["eof"] and not item["reader_alive"] and not item["overflow"] for item in streams.values())
    within_limits = original_elapsed <= timeout and post_elapsed <= post_timeout
    return {"schema_version": 1, "argv": list(argv), "cwd": str(cwd), "pid": owner.pid,
        "job_assigned_before_resume": owner.assigned, "primary_thread_resumed": owner.resumed,
        "outcome": outcome, "original_exit": original_exit, "original_elapsed_seconds": original_elapsed,
        "original_limit_seconds": timeout, "postdeadline_owned_exit": post_exit,
        "postdeadline_total_seconds": post_elapsed, "postdeadline_limit_seconds": post_timeout,
        "streams": streams, "errors": errors, "capture_complete": captured,
        "within_recorded_limits": within_limits,
        "ok": outcome == "EXITED" and original_exit == 0 and captured and within_limits and not errors}
