"""Injectable, read-only attach identity checks; never inspect process arguments."""
import re
from dataclasses import asdict, dataclass

from .cells import check_game
from .evidence import now


@dataclass(frozen=True)
class ProcessIdentity:
    pid: int
    name: str
    created: int  # Windows FILETIME, UTC 100 ns units since 1601.


@dataclass(frozen=True)
class WindowIdentity:
    handle: int
    pid: int
    visible: bool
    class_name: str
    title: str


def matching_windows(backend, pid):
    return [w for w in backend.windows(pid) if w.pid == pid and w.visible
            and w.class_name == "GLFW30" and w.title.startswith("Minecraft")]


class AttachedClient:
    def __init__(self, spec, cell, backend):
        self.spec, self.backend = spec, backend
        self.identity = backend.process(spec["pid"])
        if self.identity.pid != spec["pid"] or self.identity.name.lower() not in ("java.exe", "javaw.exe"):
            raise ValueError("Attach target is not a live Java process")
        windows = matching_windows(backend, spec["pid"])
        if len(windows) != 1:
            raise ValueError("Expected exactly one visible Minecraft GLFW window")
        self.handle = windows[0].handle
        if list(backend.size(self.handle)) != spec["window_size"]:
            raise ValueError("Attached window size differs from declaration")
        game = spec["game_dir"]
        latest = game / "logs/latest.log"
        created_unix = self.identity.created / 10000000 - 11644473600
        if latest.stat().st_mtime < created_unix:
            raise ValueError("Client log predates target process")
        if latest.stat().st_size > 128 * 1024 * 1024:
            raise ValueError("Client identity log exceeds 128 MiB")
        found = None
        with latest.open(encoding="utf-8", errors="replace") as stream:
            while line := stream.readline(65536):
                if len(line) == 65536 and not line.endswith("\n"):
                    raise ValueError("Client identity log line exceeds bound")
                match = re.search(r"Setting user:\s*([A-Za-z0-9_]{3,16})(?:\s|$)", line)
                if match:
                    found = match[1]
        if found != spec["player_name"]:
            raise ValueError("Client log player name differs from declaration")
        if (game / "options.txt").stat().st_size > 65536:
            raise ValueError("Client options exceeds 64 KiB")
        options = dict(line.rstrip("\n").split(":", 1) for line in (game / "options.txt").read_text(
            encoding="utf-8").splitlines() if ":" in line)
        if options.get("lang") != spec["language"] or options.get("guiScale") != str(spec["gui_scale"]):
            raise ValueError("Client options differ from language/GUI scale declaration")
        self.record = {"verified_at": now(), "process": asdict(self.identity), "window": self.handle,
                       "player_name": found, "account_label": spec["account_label"], "game_dir": str(game),
                       "language": spec["language"], "gui_scale": spec["gui_scale"],
                       "window_size": spec["window_size"], "mods": check_game(cell, game),
                       "directory_binding": "Owner declaration; no process command line inspected"}

    def verify(self, foreground=True):
        if self.backend.process(self.identity.pid) != self.identity:
            raise ValueError("Process identity changed")
        windows = matching_windows(self.backend, self.identity.pid)
        if len(windows) != 1 or windows[0].handle != self.handle:
            raise ValueError("Window identity changed")
        if foreground and self.backend.foreground() != self.handle:
            raise ValueError("Attached window is not in the foreground")
        self.record["last_verified_at"] = now()

    def close(self):
        raise ValueError("Attach mode cannot close clients")
