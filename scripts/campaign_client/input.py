"""Guarded per-window input and native F2 capture (never desktop screenshots)."""
import shutil
import time

from .evidence import sha256
from .protocol import keycode, one_line


class Input:
    def __init__(self, backend, handle, verify, interval, sleep, mode, pacer=None):
        self.backend, self.handle, self.verify = backend, handle, verify
        self.interval, self.sleep, self.mode = interval, sleep, mode
        self.pacer = pacer if pacer is not None else [float("-inf")]
        self.held = set()

    def guard(self):
        remaining = self.interval - (time.monotonic() - self.pacer[0])
        if remaining > 0:
            self.sleep(remaining)
        if self.mode == "launch":
            self.backend.focus(self.handle)
        self.verify()
        self.pacer[0] = time.monotonic()

    def key_state(self, code, down):
        self.guard()
        self.backend.key(self.handle, code, down)
        if down:
            self.held.add(code)
        else:
            self.held.discard(code)

    def key(self, key, hold=0.08):
        code = keycode(key)
        self.key_state(code, True)
        try:
            self.sleep(hold)
        finally:
            self.key_state(code, False)

    def perform(self, op):
        kind = op["op"]
        hold = op.get("hold", 0.08)
        if kind == "key":
            self.key(op["key"], hold)
        elif kind in ("keydown", "keyup"):
            self.key_state(keycode(op["key"]), kind == "keydown")
        elif kind == "combo":
            codes = [keycode(key) for key in op["keys"]]
            try:
                for code in codes:
                    self.key_state(code, True)
                self.sleep(hold)
            finally:
                for code in reversed(codes):
                    if code in self.held:
                        self.key_state(code, False)
        elif kind == "text":
            one_line(op["text"], 256, "text")
            # WM_CHAR takes UTF-16 code units, not Unicode scalar values.
            data = op["text"].encode("utf-16-le")
            for index in range(0, len(data), 2):
                self.guard()
                self.backend.post(self.handle, 0x102, int.from_bytes(data[index:index + 2], "little"), 1)
        elif kind in ("click", "move", "scroll"):
            self.guard()
            point = self.backend.point(self.handle, op["x"], op["y"], screen=kind == "scroll")
            if kind == "scroll":
                self.backend.post(self.handle, 0x20A, ((120 * op["notches"]) & 0xFFFF) << 16, point)
            else:
                self.backend.post(self.handle, 0x200, 0, point)
                if kind == "click":
                    right = op.get("right", False)
                    self.guard()
                    self.backend.post(self.handle, 0x204 if right else 0x201, 2 if right else 1, point)
                    try:
                        self.sleep(hold)
                    finally:
                        self.guard()
                        self.backend.post(self.handle, 0x205 if right else 0x202, 0, point)
        elif kind == "resize":
            self.guard()
            self.backend.resize(self.handle, op["width"], op["height"])
        else:
            raise ValueError("Unknown window input")
        self.sleep(op.get("settle", 0.2))
        return {"op": kind, "client_size": self.backend.size(self.handle)}

    def screen_open(self):
        """Whether Minecraft shows the pointer (a screen, chat or menu); read only while the window is in front."""
        self.guard()
        return not self.backend.cursor_captured(self.handle)

    def wait_screen(self, open_, timeout):
        deadline = time.monotonic() + timeout
        while True:
            if self.screen_open() == open_:
                return True
            if time.monotonic() >= deadline:
                return False
            self.sleep(0.1)

    def screenshot(self, game, output):
        if output.exists():
            raise ValueError("Screenshot evidence already exists")
        directory = game / "screenshots"
        before = set(directory.glob("*.png"))
        self.key("F2")
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            new = set(directory.glob("*.png")) - before
            if len(new) > 1:
                raise ValueError("Ambiguous native screenshot")
            if len(new) == 1:
                source = next(iter(new))
                size = source.stat().st_size
                self.sleep(0.3)
                if 0 < size == source.stat().st_size:
                    shutil.copyfile(source, output)
                    return {"file": str(output.name), "bytes": size, "sha256": sha256(output),
                            "source": "native Minecraft F2 screenshot"}
            self.sleep(0.1)
        raise TimeoutError("No native F2 screenshot")

    def release(self):
        # Never send even a release to an unverified/defocused attach target.
        sleep = self.sleep
        try:
            # Releases are bounded cleanup, not new actions after the session deadline.
            self.sleep = time.sleep
            for code in list(self.held):
                self.key_state(code, False)
        finally:
            self.sleep = sleep
