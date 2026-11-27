"""Minimal Win32 metadata and window input API. No WMI or command-line access."""
import ctypes as C
from ctypes import wintypes as W
from pathlib import PureWindowsPath

from .attach import ProcessIdentity, WindowIdentity
from .gates import CursorVisibilityRequired, cursor_captured


class CURSORINFO(C.Structure):
    _fields_ = [("cbSize", W.DWORD), ("flags", W.DWORD), ("hCursor", W.HANDLE), ("ptScreenPos", W.POINT)]


class Windows:
    def __init__(self):
        self.k = C.WinDLL("kernel32", use_last_error=True)
        self.u = C.WinDLL("user32", use_last_error=True)
        self.callback_type = C.WINFUNCTYPE(W.BOOL, W.HWND, W.LPARAM)
        definitions = [
            (self.k, "OpenProcess", [W.DWORD, W.BOOL, W.DWORD], W.HANDLE),
            (self.k, "CloseHandle", [W.HANDLE], W.BOOL),
            (self.k, "QueryFullProcessImageNameW", [W.HANDLE, W.DWORD, W.LPWSTR, C.POINTER(W.DWORD)], W.BOOL),
            (self.k, "GetProcessTimes", [W.HANDLE] + [C.POINTER(W.FILETIME)] * 4, W.BOOL),
            (self.k, "GetExitCodeProcess", [W.HANDLE, C.POINTER(W.DWORD)], W.BOOL),
            (self.u, "EnumWindows", [self.callback_type, W.LPARAM], W.BOOL),
            (self.u, "GetWindowThreadProcessId", [W.HWND, C.POINTER(W.DWORD)], W.DWORD),
            (self.u, "GetClassNameW", [W.HWND, W.LPWSTR, C.c_int], C.c_int),
            (self.u, "GetWindowTextW", [W.HWND, W.LPWSTR, C.c_int], C.c_int),
            (self.u, "IsWindowVisible", [W.HWND], W.BOOL),
            (self.u, "GetForegroundWindow", [], W.HWND),
            (self.u, "SetForegroundWindow", [W.HWND], W.BOOL),
            (self.u, "GetClientRect", [W.HWND, C.POINTER(W.RECT)], W.BOOL),
            (self.u, "GetWindowRect", [W.HWND, C.POINTER(W.RECT)], W.BOOL),
            (self.u, "PostMessageW", [W.HWND, W.UINT, W.WPARAM, W.LPARAM], W.BOOL),
            (self.u, "MapVirtualKeyW", [W.UINT, W.UINT], W.UINT),
            (self.u, "ClientToScreen", [W.HWND, C.POINTER(W.POINT)], W.BOOL),
            (self.u, "SetWindowPos", [W.HWND, W.HWND] + [C.c_int] * 4 + [W.UINT], W.BOOL),
            (self.k, "GetCurrentThreadId", [], W.DWORD),
            (self.u, "AttachThreadInput", [W.DWORD, W.DWORD, W.BOOL], W.BOOL),
            (self.u, "keybd_event", [W.BYTE, W.BYTE, W.DWORD, C.c_size_t], None),
            (self.u, "GetClipCursor", [C.POINTER(W.RECT)], W.BOOL),
            (self.u, "GetCursorInfo", [C.POINTER(CURSORINFO)], W.BOOL),
            (self.u, "WindowFromPoint", [W.POINT], W.HWND),
            (self.u, "GetSystemMetrics", [C.c_int], C.c_int),
        ]
        for dll, name, args, result in definitions:
            fn = getattr(dll, name)
            fn.argtypes, fn.restype = args, result
        # Make pixel coordinates independent of desktop scaling.
        self.u.SetProcessDPIAware()

    @staticmethod
    def require(result):
        if not result:
            raise C.WinError(C.get_last_error())
        return result

    def process(self, pid):
        handle = self.require(self.k.OpenProcess(0x1000, False, pid))
        try:
            code = W.DWORD()
            self.require(self.k.GetExitCodeProcess(handle, C.byref(code)))
            if code.value != 259:
                raise ValueError("Process has exited")
            size, name = W.DWORD(32768), C.create_unicode_buffer(32768)
            self.require(self.k.QueryFullProcessImageNameW(handle, 0, name, C.byref(size)))
            times = [W.FILETIME() for _ in range(4)]
            self.require(self.k.GetProcessTimes(handle, *(C.byref(t) for t in times)))
            created = (times[0].dwHighDateTime << 32) | times[0].dwLowDateTime
            return ProcessIdentity(pid, PureWindowsPath(name.value).name, created)
        finally:
            self.k.CloseHandle(handle)

    def windows(self, pid):
        found = []
        @self.callback_type
        def visit(handle, _):
            owner = W.DWORD()
            self.u.GetWindowThreadProcessId(handle, C.byref(owner))
            if owner.value == pid:
                cls, title = C.create_unicode_buffer(256), C.create_unicode_buffer(1024)
                self.u.GetClassNameW(handle, cls, len(cls))
                self.u.GetWindowTextW(handle, title, len(title))
                found.append(WindowIdentity(handle, pid, bool(self.u.IsWindowVisible(handle)), cls.value, title.value))
            return True
        self.require(self.u.EnumWindows(visit, 0))
        return found

    def foreground(self):
        return self.u.GetForegroundWindow()

    def focus(self, handle):
        """Bring the disposable client's window to the front.

        Windows refuses SetForegroundWindow to a process that neither owns the foreground window nor
        was started by it, which is the case for a session launched without a console window (W443's
        first v2 round failed every request here). Sharing the foreground thread's input queue for the
        one call lifts that refusal; no key or pointer event is injected.
        """
        if self.u.GetForegroundWindow() == handle or self.u.SetForegroundWindow(handle):
            return
        front = self.u.GetForegroundWindow()
        current = self.k.GetCurrentThreadId()
        other = self.u.GetWindowThreadProcessId(front, None) if front else 0
        attached = bool(other and other != current and self.u.AttachThreadInput(current, other, True))
        try:
            granted = self.u.SetForegroundWindow(handle)
        finally:
            if attached:
                self.u.AttachThreadInput(current, other, False)
        if granted and self.u.GetForegroundWindow() == handle:
            return
        # Last resort, launch mode only (attach mode never calls focus): one synthetic Alt tap makes this
        # process the receiver of the last input event, which Windows accepts as the right to choose the
        # foreground window. It is the only desktop-wide input the driver ever sends (W443: the shared
        # queue alone was still refused while a browser held the foreground).
        self.u.keybd_event(0x12, 0, 0, 0)
        try:
            granted = self.u.SetForegroundWindow(handle)
        finally:
            self.u.keybd_event(0x12, 0, 2, 0)
        if not granted or self.u.GetForegroundWindow() != handle:
            raise ValueError("Windows refused to bring the client window to the front")

    def size(self, handle):
        rect = W.RECT()
        self.require(self.u.GetClientRect(handle, C.byref(rect)))
        return rect.right, rect.bottom

    def cursor_captured(self, handle):
        clip, origin = W.RECT(), W.POINT(0, 0)
        self.require(self.u.GetClipCursor(C.byref(clip)))
        self.require(self.u.ClientToScreen(handle, C.byref(origin)))
        width, height = self.size(handle)
        left, top = self.u.GetSystemMetrics(76), self.u.GetSystemMetrics(77)
        desktop = (left, top, left + self.u.GetSystemMetrics(78), top + self.u.GetSystemMetrics(79))
        clip_rect = (clip.left, clip.top, clip.right, clip.bottom)
        client = (origin.x, origin.y, origin.x + width, origin.y + height)
        try:
            return cursor_captured(clip_rect, client, desktop)
        except CursorVisibilityRequired:
            # GetCursorInfo is global. Require the game both in front and under the
            # sampled pointer, with a stable clip. No focus change or input is sent.
            if self.foreground() != handle:
                raise ValueError("Cursor visibility needs the client window in front")
            info = CURSORINFO()
            info.cbSize = C.sizeof(info)
            self.require(self.u.GetCursorInfo(C.byref(info)))
            self.require(self.u.GetClipCursor(C.byref(clip)))
            if self.foreground() != handle:
                raise ValueError("Client window lost the front during cursor visibility read")
            if clip_rect != (clip.left, clip.top, clip.right, clip.bottom):
                raise ValueError("Pointer clip changed during cursor visibility read")
            if self.u.WindowFromPoint(info.ptScreenPos) != handle:
                raise ValueError("Cursor visibility needs the game window under the pointer")
            return cursor_captured(clip_rect, client, desktop, info.flags)

    def post(self, handle, message, word=0, value=0):
        self.require(self.u.PostMessageW(handle, message, word, value))

    # Shift, as the virtual key the system reports for it. GLFW polls the real state of both Shift keys
    # and releases one it sees pressed only in a window message, so a posted Shift never holds
    # (W443: the sneak-and-use on the rocket assembler arrived as a plain use).
    SYSTEM_KEYS = {0x10: 0xA0, 0xA0: 0xA0, 0xA1: 0xA1}

    def key(self, handle, code, down):
        if code in self.SYSTEM_KEYS:
            if down and self.u.GetForegroundWindow() != handle:
                raise ValueError("Shift is a desktop-wide key and needs the client window in front")
            self.system_key(code, down)
            return
        scan = self.u.MapVirtualKeyW(code, 0)
        flags = 1 | scan << 16 | (0 if down else 3 << 30)
        self.post(handle, 0x100 if down else 0x101, code, flags)

    def system_key(self, code, down):
        """Press or release Shift in the system input queue. A release is always allowed."""
        key = self.SYSTEM_KEYS[code]
        self.u.keybd_event(key, self.u.MapVirtualKeyW(key, 0) & 0xFF, 0 if down else 2, 0)

    def point(self, handle, x, y, screen=False):
        width, height = self.size(handle)
        if not (0 <= x < width and 0 <= y < height):
            raise ValueError("Pointer outside client area")
        point = W.POINT(x, y)
        if screen:
            self.require(self.u.ClientToScreen(handle, C.byref(point)))
        return (point.y & 0xFFFF) << 16 | (point.x & 0xFFFF)

    def resize(self, handle, width, height):
        client = self.size(handle)
        outer = W.RECT()
        self.require(self.u.GetWindowRect(handle, C.byref(outer)))
        self.require(self.u.SetWindowPos(handle, None, 0, 0, width + outer.right - outer.left - client[0],
                                         height + outer.bottom - outer.top - client[1], 0x16))
