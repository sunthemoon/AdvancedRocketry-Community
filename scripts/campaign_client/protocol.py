"""Strict bounded session and legacy {id, ops} request contracts."""
import math
import re
from pathlib import Path
from uuid import UUID

from .cells import path_at, read_object
from .gates import MAX_GATE_SECONDS, gate_seconds

VK = {"ESC": 27, "ENTER": 13, "TAB": 9, "BACK": 8, "SPACE": 32, "SHIFT": 16,
      "CTRL": 17, "END": 35, "HOME": 36, "DELETE": 46, "UP": 38, "DOWN": 40,
      "LEFT": 37, "RIGHT": 39, "SLASH": 191,
      **{chr(n): n for n in range(65, 91)}, **{str(n): 48 + n for n in range(10)},
      **{f"F{n}": 111 + n for n in range(1, 13)}}
INPUTS = {"key", "keydown", "keyup", "combo", "text", "click", "move", "scroll", "resize", "screenshot",
          "require_screen", "close_screen"}
FIELDS = {
    "key": {"key", "hold"}, "keydown": {"key"}, "keyup": {"key"}, "combo": {"keys", "hold"},
    "text": {"text"}, "click": {"x", "y", "right", "hold"}, "move": {"x", "y"},
    "scroll": {"x", "y", "notches"}, "resize": {"width", "height"}, "screenshot": {"name"},
    "server": {"command", "wait", "timeout", "tail"}, "client_wait": {"pattern", "timeout", "start_at"},
    "sleep": {"seconds"}, "status": {"tail"}, "stop": set(), "close_client": set(),
    "require": {"command", "expect", "attempts", "interval", "timeout"},
    "expect_log": {"pattern", "timeout"}, "require_screen": {"open", "timeout"}, "close_screen": {"timeout"},
}


def number(value, low, high, label, integer=False):
    if type(value) not in ((int,) if integer else (int, float)) or not math.isfinite(value) or not low <= value <= high:
        raise ValueError(f"Invalid {label}")
    return value


def string(value, pattern, label):
    if not isinstance(value, str) or not re.fullmatch(pattern, value):
        raise ValueError(f"Invalid {label}")
    return value


def keycode(value):
    if isinstance(value, str):
        if value not in VK:
            raise ValueError("Unknown key")
        return VK[value]
    return number(value, 1, 255, "virtual key", True)


def one_line(value, limit, label):
    if not isinstance(value, str) or not 1 <= len(value) <= limit or any(c in value for c in "\r\n\0"):
        raise ValueError(f"Invalid {label}")
    return value


def validate_request(request, mode, players):
    if not isinstance(request, dict) or set(request) != {"id", "ops"}:
        raise ValueError("Request needs id and ops only")
    string(request["id"], r"[A-Za-z0-9_-]{1,80}", "request ID")
    if not isinstance(request["ops"], list) or not 1 <= len(request["ops"]) <= 200:
        raise ValueError("Request needs 1..200 actions")
    for op in request["ops"]:
        if not isinstance(op, dict) or op.get("op") not in FIELDS:
            raise ValueError("Unknown action")
        kind = op["op"]
        if set(op) - (FIELDS[kind] | {"op", "player", "settle"}):
            raise ValueError("Unknown action fields")
        if "player" in op and op["player"] not in players:
            raise ValueError("Unknown player")
        if kind == "close_client" and mode == "attach":
            raise ValueError("Attach mode cannot close clients")
        number(op.get("settle", 0), 0, 120, "settle")
        number(op.get("hold", 0.08), 0.01, 30, "hold")
        if kind in ("key", "keydown", "keyup"):
            keycode(op.get("key"))
        if kind == "combo":
            keys = op.get("keys")
            if not isinstance(keys, list) or not 1 <= len(keys) <= 8:
                raise ValueError("Combo needs 1..8 keys")
            for key in keys:
                keycode(key)
        if kind == "text":
            one_line(op.get("text"), 256, "text")
        if kind in ("click", "move", "scroll"):
            for axis in ("x", "y"):
                number(op.get(axis), 0, 8191, axis, True)
            if type(op.get("right", False)) is not bool:
                raise ValueError("Invalid mouse button")
        if kind == "scroll":
            number(op.get("notches"), -20, 20, "scroll notches", True)
        if kind == "resize":
            number(op.get("width"), 640, 3840, "width", True)
            number(op.get("height"), 480, 2160, "height", True)
        if kind == "screenshot":
            string(op.get("name"), r"[a-z0-9-]{1,80}", "screenshot name")
        if kind == "server":
            one_line(op.get("command"), 4096, "console command")
        if kind in ("client_wait", "server"):
            # Literal substrings, not owner-supplied regular expressions (bounded wait even on hostile input).
            if kind == "client_wait" or "wait" in op:
                one_line(op.get("pattern") if kind == "client_wait" else op["wait"], 512, "wait substring")
            number(op.get("timeout", 30), 0.01, 300, "wait timeout")
            number(op.get("start_at", 0), 0, 10000000, "log offset", True)
        if kind in ("server", "status"):
            number(op.get("tail", 6), 0, 100, "tail", True)
        if kind == "sleep":
            number(op.get("seconds"), 0, 120, "sleep")
        if kind == "require":
            one_line(op.get("command"), 4096, "console command")
            one_line(op.get("expect"), 512, "expected reply")
            number(op.get("attempts", 1), 1, 60, "attempts", True)
            number(op.get("interval", 1), 0.05, 30, "attempt interval")
            number(op.get("timeout", 5), 0.01, 30, "reply timeout")
            if gate_seconds(op) > MAX_GATE_SECONDS:
                raise ValueError("Gate may wait longer than 300 seconds")
        if kind == "expect_log":
            one_line(op.get("pattern"), 512, "wait substring")
            number(op.get("timeout", 30), 0.01, 300, "wait timeout")
        if kind in ("require_screen", "close_screen"):
            if kind == "require_screen" and type(op.get("open")) is not bool:
                raise ValueError("require_screen needs open: true or false")
            number(op.get("timeout", 5), 0.01, 30, "screen timeout")
    return request


def read_request(path, mode, players):
    return validate_request(read_object(path), mode, players)


def heap_mebibytes(value):
    string(value, r"[1-9][0-9]{0,3}[MG]", "server.max_heap (positive integer M or G)")
    size = int(value[:-1]) * (1024 if value[-1] == "G" else 1)
    return number(size, 1, 8192, "server.max_heap (at most 8G)", True)


def validate_server(spec):
    fields = {"online-mode", "white-list", "enforce-whitelist", "server-ip", "server-port",
              "whitelist", "ops", "jvm_properties"}
    if not isinstance(spec, dict) or set(spec) - {"max_heap"} != fields:
        raise ValueError("Server settings must explicitly declare all settings")
    heap_mebibytes(spec.get("max_heap", "3G"))
    for key in ("online-mode", "white-list", "enforce-whitelist"):
        if type(spec[key]) is not bool:
            raise ValueError("Invalid server boolean")
    if not isinstance(spec["server-ip"], str) or not re.fullmatch(r"[A-Za-z0-9.:-]{0,253}", spec["server-ip"]):
        raise ValueError("Invalid server bind address")
    number(spec["server-port"], 1, 65535, "server port", True)
    for kind in ("whitelist", "ops"):
        if not isinstance(spec[kind], list) or len(spec[kind]) > 2:
            raise ValueError("At most two declared players")
        names, ids = set(), set()
        for player in spec[kind]:
            if not isinstance(player, dict) or set(player) != {"name", "uuid"}:
                raise ValueError("Player needs name and UUID")
            string(player["name"], r"[A-Za-z0-9_]{3,16}", "player name")
            value = string(player["uuid"], r"[a-fA-F0-9]{8}(?:-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}", "UUID")
            if str(UUID(value)) in ids or player["name"].lower() in names:
                raise ValueError("Duplicate declared player")
            ids.add(str(UUID(value)))
            names.add(player["name"].lower())
    props = spec["jvm_properties"]
    if not isinstance(props, dict) or len(props) > 64:
        raise ValueError("Invalid JVM properties")
    for key, value in props.items():
        string(key, r"[A-Za-z0-9_.-]{1,128}", "JVM property name")
        one_line(value, 4096, "JVM property value")
    return dict(spec, max_heap=spec.get("max_heap", "3G"))


def load_session(path):
    path = Path(path).resolve()
    spec = read_object(path)
    required = {"schema_version", "mode", "cell", "run_dir", "time_limit_seconds", "input_interval_seconds",
                "clients", "server", "java", "singleplayer"}
    if set(spec) - {"launch_kind", "paired_world"} != required or type(spec["schema_version"]) is not int or spec["schema_version"] != 1:
        raise ValueError("Unsupported session schema")
    if spec.get("launch_kind", "candidate") not in ("candidate", "baseline"):
        raise ValueError("Unsupported launch kind")
    if spec.get("launch_kind") == "baseline" and (spec["mode"] != "launch" or spec["singleplayer"] is not True):
        raise ValueError("Baseline requires a disposable singleplayer launch")
    if "paired_world" in spec:
        if spec.get("launch_kind") != "baseline":
            raise ValueError("paired_world is only supported for baseline sessions")
        from .baseline_pairing import load_pairing
        paired = load_pairing(path.parent, spec["paired_world"])
        spec["paired_world"] = {"path": paired["path"], "sha256": paired["sha256"]}
    if spec["mode"] not in ("launch", "attach") or type(spec["singleplayer"]) is not bool:
        raise ValueError("Invalid mode")
    number(spec["time_limit_seconds"], 1, 21600, "session limit")
    number(spec["input_interval_seconds"], 0.05, 10, "input interval")
    if not isinstance(spec["clients"], list) or not 1 <= len(spec["clients"]) <= 2:
        raise ValueError("Session needs one or two clients")
    if spec["singleplayer"] and (spec["mode"] != "launch" or len(spec["clients"]) != 1):
        raise ValueError("Singleplayer needs one disposable client")
    names, pids, games = set(), set(), set()
    for client in spec["clients"]:
        fields = {"player_name", "language", "gui_scale", "window_size"}
        if spec["mode"] == "attach":
            fields |= {"pid", "account_label", "game_dir"}
        # Optional, launch only: the disposable client's pauseOnLostFocus option (default false).
        optional = {"pause_on_lost_focus"} if spec["mode"] == "launch" else set()
        if not isinstance(client, dict) or set(client) - optional != fields:
            raise ValueError("Invalid client fields")
        if type(client.get("pause_on_lost_focus", False)) is not bool:
            raise ValueError("Invalid pause_on_lost_focus")
        name = string(client["player_name"], r"[A-Za-z0-9_]{3,16}", "player name")
        if name.lower() in names:
            raise ValueError("Duplicate player")
        names.add(name.lower())
        string(client["language"], r"[a-z]{2}_[a-z]{2}", "language")
        number(client["gui_scale"], 1, 4, "GUI scale", True)
        size = client["window_size"]
        if not isinstance(size, list) or len(size) != 2:
            raise ValueError("Expected [width, height]")
        number(size[0], 640, 3840, "width", True)
        number(size[1], 480, 2160, "height", True)
        if spec["mode"] == "attach":
            number(client["pid"], 1, 2**32 - 1, "PID", True)
            one_line(client["account_label"], 80, "account label")
            client["game_dir"] = path_at(path.parent, client["game_dir"])
            if client["pid"] in pids or client["game_dir"] in games:
                raise ValueError("Attach clients must have separate PIDs and game directories")
            pids.add(client["pid"])
            games.add(client["game_dir"])
    spec["server"] = validate_server(spec["server"])
    if spec["mode"] == "launch" and (spec["server"]["online-mode"] or spec["server"]["server-ip"] != "127.0.0.1"):
        raise ValueError("Disposable clients require offline loopback")
    for key in ("cell", "run_dir", "java"):
        spec[key] = path_at(path.parent, spec[key])
    return spec
