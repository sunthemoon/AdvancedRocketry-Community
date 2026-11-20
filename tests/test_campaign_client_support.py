"""Synthetic files and lookups only; no Java, launcher or real window access."""
import json
import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

from scripts.campaign_client.attach import ProcessIdentity, WindowIdentity
from scripts.campaign_client.evidence import sha256


class Fixture(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="w399-test-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.server = self.root / "prepared-server"
        self.game = self.root / "prepared-game"
        for directory in (self.server, self.game):
            (directory / "mods").mkdir(parents=True)
        self.jar = self.make_jar(self.root / "candidate.jar", "advancedrocketrycommunity")
        for directory in (self.server, self.game):
            shutil.copyfile(self.jar, directory / "mods/candidate.jar")
        args = self.server / "libraries/net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt"
        args.parent.mkdir(parents=True)
        args.write_text("fixture", encoding="utf-8")
        (self.game / "logs").mkdir()
        (self.game / "logs/latest.log").write_text("[Client thread/INFO]: Setting user: TesterOne\n", encoding="utf-8")
        (self.game / "options.txt").write_text("lang:en_us\nguiScale:2\n", encoding="utf-8")
        self.cell_path = self.root / "cell.json"
        self.cell_spec = {"schema_version": 1, "forge": "47.4.10", "jei": False,
                          "runtime_root": str(self.root / "runtime"), "server_dir": str(self.server),
                          "client_game_dir": str(self.game), "version_id": "1.20.1-forge-47.4.10",
                          "candidate": {"path": str(self.jar), "sha256": sha256(self.jar)}, "jei_jar": None}
        self.dump_cell()
        self.attach_spec = {"pid": 123, "account_label": "owner-one", "player_name": "TesterOne",
                            "language": "en_us", "gui_scale": 2, "game_dir": self.game,
                            "window_size": [1280, 720]}
        self.settings = {"online-mode": True, "white-list": True, "enforce-whitelist": True,
                         "server-ip": "127.0.0.1", "server-port": 25565,
                         "whitelist": [{"name": "TesterOne", "uuid": "12345678-1234-4234-8234-123456789abc"}],
                         "ops": [], "jvm_properties": {"arce.test": "true"}}
        self.session_spec = {"schema_version": 1, "mode": "attach", "cell": str(self.cell_path),
                             "run_dir": str(self.root / "run"), "java": str(self.root / "java.exe"),
                             "singleplayer": False, "time_limit_seconds": 60, "input_interval_seconds": 0.05,
                             "clients": [dict(self.attach_spec, game_dir=str(self.game))], "server": self.settings}

    @staticmethod
    def make_jar(path, mod):
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("META-INF/mods.toml", f'[[mods]]\nmodId="{mod}"\n')
        return path

    def dump_cell(self):
        self.cell_path.write_text(json.dumps(self.cell_spec), encoding="utf-8")

    def dump_session(self):
        path = self.root / "session-input.json"
        path.write_text(json.dumps(self.session_spec), encoding="utf-8")
        return path


class FakeWindows:
    def __init__(self):
        self.identity = ProcessIdentity(123, "javaw.exe", 133000000000000000)
        self.found = [WindowIdentity(456, 123, True, "GLFW30", "Minecraft 1.20.1")]
        self.front = 456
        self.events = []

    def process(self, pid):
        if self.identity is None:
            raise ValueError("Missing process")
        return self.identity

    def windows(self, pid):
        return self.found

    def foreground(self):
        return self.front

    def size(self, handle):
        return (1280, 720)

    def focus(self, handle):
        self.events.append(("focus", handle))
        self.front = handle

    def key(self, *args):
        self.events.append(("key", *args))

    def post(self, *args):
        self.events.append(("post", *args))

    def point(self, handle, x, y, screen=False):
        if not 0 <= x < 1280 or not 0 <= y < 720:
            raise ValueError("Outside window")
        return y << 16 | x

    def resize(self, *args):
        self.events.append(("resize", *args))
