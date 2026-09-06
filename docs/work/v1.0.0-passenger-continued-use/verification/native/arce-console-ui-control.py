"""Owned-window Minecraft input and native screenshots; no desktop-wide capture."""
import ctypes
from ctypes import wintypes as W
import hashlib
from pathlib import Path
import shutil
import time


class Window:
    def __init__(self, pid):
        self.pid = pid
        self.u = ctypes.WinDLL('user32', use_last_error=True)
        self.u.GetWindowThreadProcessId.argtypes = [W.HWND, ctypes.POINTER(W.DWORD)]
        self.u.GetClassNameW.argtypes = [W.HWND, W.LPWSTR, ctypes.c_int]
        self.u.IsWindowVisible.argtypes = [W.HWND]
        self.u.PostMessageW.argtypes = [W.HWND, W.UINT, W.WPARAM, W.LPARAM]
        self.u.GetClientRect.argtypes = [W.HWND, ctypes.POINTER(W.RECT)]
        self.u.GetWindowRect.argtypes = [W.HWND, ctypes.POINTER(W.RECT)]
        self.u.SetWindowPos.argtypes = [W.HWND, W.HWND, ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_int, W.UINT]
        self.u.SetForegroundWindow.argtypes = [W.HWND]
        self.u.MapVirtualKeyW.argtypes = [W.UINT, W.UINT]
        callback = ctypes.WINFUNCTYPE(W.BOOL, W.HWND, W.LPARAM)
        self.u.EnumWindows.argtypes = [callback, W.LPARAM]
        found = []

        @callback
        def visit(handle, _):
            owner = W.DWORD()
            self.u.GetWindowThreadProcessId(handle, ctypes.byref(owner))
            name = ctypes.create_unicode_buffer(256)
            self.u.GetClassNameW(handle, name, len(name))
            if owner.value == pid and self.u.IsWindowVisible(handle) and name.value.startswith('GLFW'):
                found.append(handle)
            return True

        self.u.EnumWindows(visit, 0)
        if len(found) != 1:
            raise RuntimeError(f'Expected one owned GLFW window for {pid}: {found}')
        self.handle = found[0]

    def validate(self):
        owner = W.DWORD()
        self.u.GetWindowThreadProcessId(self.handle, ctypes.byref(owner))
        if owner.value != self.pid:
            raise RuntimeError('Window ownership changed')

    def size(self):
        self.validate()
        rect = W.RECT()
        if not self.u.GetClientRect(self.handle, ctypes.byref(rect)):
            raise ctypes.WinError(ctypes.get_last_error())
        return rect.right, rect.bottom

    def post(self, message, word=0, value=0):
        self.validate()
        if not self.u.PostMessageW(self.handle, message, word, value):
            raise ctypes.WinError(ctypes.get_last_error())

    def focus(self):
        self.validate()
        return bool(self.u.SetForegroundWindow(self.handle))

    def key(self, key):
        scan = self.u.MapVirtualKeyW(key, 0)
        self.post(0x100, key, 1 | (scan << 16))
        time.sleep(0.08)
        self.post(0x101, key, 1 | (scan << 16) | (3 << 30))

    def click(self, x, y, right=False):
        width, height = self.size()
        if not (0 <= x < width and 0 <= y < height):
            raise ValueError('Click is outside the owned client area')
        point = (y << 16) | x
        self.post(0x200, 0, point)
        time.sleep(0.08)
        self.post(0x204 if right else 0x201, 2 if right else 1, point)
        time.sleep(0.08)
        self.post(0x205 if right else 0x202, 0, point)

    def resize(self, width, height):
        if not (640 <= width <= 1920 and 480 <= height <= 1080):
            raise ValueError('Window size is outside the test range')
        client = self.size()
        outer = W.RECT()
        self.u.GetWindowRect(self.handle, ctypes.byref(outer))
        if not self.u.SetWindowPos(self.handle, None, 0, 0,
                                  width + outer.right - outer.left - client[0],
                                  height + outer.bottom - outer.top - client[1], 0x0016):
            raise ctypes.WinError(ctypes.get_last_error())
        time.sleep(0.5)

    def screenshot(self, game, output):
        directory = Path(game) / 'screenshots'
        before = set(directory.glob('*.png')) if directory.exists() else set()
        self.key(0x71)
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            new = set(directory.glob('*.png')) - before
            if len(new) == 1:
                path = next(iter(new))
                size = path.stat().st_size
                time.sleep(0.2)
                if size > 0 and size == path.stat().st_size:
                    if Path(output).exists():
                        raise RuntimeError('Screenshot evidence already exists')
                    shutil.copy2(path, output)
                    return dict(file=Path(output).name, bytes=size,
                                sha256=hashlib.sha256(Path(output).read_bytes()).hexdigest(),
                                source='native Minecraft F2 screenshot', client_size=self.size())
            time.sleep(0.1)
        raise RuntimeError('No new native Minecraft screenshot observed')
