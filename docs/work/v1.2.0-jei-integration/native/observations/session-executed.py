"""Bounded owned-window JEI inspection, using an existing isolated runtime.

Commands are JSON arrays in this session's private commands directory. No
production classes are instrumented. Native F2 captures are observations, not
an automatic visual verdict.
"""
from pathlib import Path
import importlib.util
import json
import os
import re
import shutil
import sys
import time

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "scripts"))
import run_dedicated_server_smoke as server
import run_v100_compatibility_matrix as matrix
import v100_compatibility_runtime as runtime

control_source = ROOT / "docs/work/v1.0.0-native-reconnect/native/arce-console-ui-control.py"
spec = importlib.util.spec_from_file_location("owned_control", control_source)
control = importlib.util.module_from_spec(spec)
spec.loader.exec_module(control)

JAVA = r"C:\Program Files\Java\jdk-17.0.7\bin\java.exe"
RUNTIME = Path(os.environ["TEMP"]) / "arce-v100-compatibility/runtime-1"
WORK = ROOT / ".gradle/v120-jei-session"
EVIDENCE = Path(__file__).parent / "observations"
ARTIFACT = ROOT / "build/libs/advancedrocketry-community-1.20.1-1.1.1-dev.jar"
EXPECTED_ARTIFACT = "92821ec3273a7b87c2892003ca5321f8cf65105a41572a3f730c3582d67c2000"
USER = "V120Jei"


def main():
    WORK.mkdir(exist_ok=False)
    EVIDENCE.mkdir(exist_ok=False)
    commands = WORK / "commands"
    commands.mkdir()
    summary = dict(started_at=matrix.now(), artifact_sha256=server.digest_file(ARTIFACT),
                   authenticated=False, source_state="development_worktree",
                   actions=[], screenshots=[], processes=[], errors=[])
    dedicated = client = None
    server_game = WORK / "server"
    game = WORK / "client"
    try:
        if summary["artifact_sha256"] != EXPECTED_ARTIFACT:
            raise RuntimeError("Artifact changed before the scoped client check")
        runtime.profile(RUNTIME, "47.4.10")
        runtime.verify_file(RUNTIME / "jei.jar", runtime.JEI_SHA1)
        template = runtime.safe_path(RUNTIME / "servers/47.4.10")
        entries = list(template.rglob("*"))
        if (len(entries) > 20000 or any(server.is_link_or_junction(p) for p in entries)
                or sum(p.stat().st_size for p in entries if p.is_file()) > 2 * 1024**3
                or any((template / name).exists() for name in ("world", "mods", "server.properties"))):
            raise RuntimeError("Installed server template is not a bounded pristine directory")
        shutil.copytree(template, server_game)
        summary["server_mods"] = matrix.install_mods(server_game, ARTIFACT, None)
        port = server.allocate_port()
        summary["port"] = port
        summary["properties_sha256"] = server.write_server_configuration(server_game, port, True)
        args_file = server_game / "libraries/net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt"
        dedicated = server.CapturedProcess([JAVA, "-Xms512M", "-Xmx2G",
            "-Dadvancedrocketrycommunity.releaseTestHooks=true", "@" + str(args_file), "nogui"],
            server_game, EVIDENCE / "server-full.txt")
        dedicated.wait_for(server.READY_MARKER, 240)
        game.mkdir()
        summary["client_mods"] = matrix.install_mods(game, ARTIFACT, RUNTIME / "jei.jar")
        (game / "options.txt").write_text(matrix.CLIENT_OPTIONS
                + "pauseOnLostFocus:false\ntutorialStep:none\nlang:en_us\n", encoding="utf-8")
        command = runtime.client_command(RUNTIME, game, "47.4.10", USER, port, JAVA)
        matrix.argument_file(game / "client.args", command[1:])
        client = server.CapturedProcess([JAVA, "@client.args"], game, EVIDENCE / "client-full.txt")
        client.wait_for(matrix.marker("Connected to a modded server."), 180)
        client.wait_for(re.compile("optional_compat=jei machine_recipes rolling="), 60)
        dedicated.wait_for(matrix.marker(USER + " joined the game"), 30)
        window = control.Window(client.process.pid)
        summary.update(client_pid=client.process.pid, server_pid=dedicated.process.pid,
                       window=window.handle, client_size=window.size())
        runtime.write_json(WORK / "ready.json", summary)
        print("READY " + json.dumps(summary), flush=True)
        deadline = time.monotonic() + 480
        seen = set()
        stopped = False
        while time.monotonic() < deadline and not stopped:
            if client.process.poll() is not None or dedicated.process.poll() is not None:
                raise RuntimeError("Owned client/server exited during inspection")
            for path in sorted(commands.glob("*.json")):
                if path.name in seen:
                    continue
                if not re.fullmatch(r"[0-9]{4}\.json", path.name) or path.stat().st_size > 16384:
                    raise RuntimeError("Invalid command file")
                actions = json.loads(path.read_text(encoding="utf-8-sig"))
                if not isinstance(actions, list) or not 1 <= len(actions) <= 32:
                    raise RuntimeError("Invalid command array")
                seen.add(path.name)
                for action in actions:
                    record = dict(at=matrix.now(), source=path.name, action=action)
                    try:
                        kind = action["action"]
                        if kind == "server":
                            value = action["command"]
                            if len(value) > 2048 or any(c in value for c in "\r\n\0"):
                                raise ValueError("Invalid server command")
                            dedicated.command(value)
                        elif kind == "key":
                            key = action["key"]
                            if type(key) is not int or not 1 <= key <= 255:
                                raise ValueError("Invalid key")
                            window.key(key)
                        elif kind == "click":
                            window.click(action["x"], action["y"], action.get("right", False))
                        elif kind == "focus":
                            record["focused"] = window.focus()
                        elif kind == "wait":
                            delay = action["seconds"]
                            if not 0 <= delay <= 5:
                                raise ValueError("Delay exceeds short action bound")
                            time.sleep(delay)
                        elif kind == "shot":
                            name = action["name"]
                            if not re.fullmatch(r"[a-z0-9_-]{1,64}\.png", name):
                                raise ValueError("Invalid capture name")
                            summary["screenshots"].append(window.screenshot(game, EVIDENCE / name))
                        elif kind == "stop":
                            stopped = True
                        else:
                            raise ValueError("Unknown action")
                    except Exception as exc:
                        record["error"] = repr(exc)
                        summary["errors"].append(record)
                    summary["actions"].append(record)
                    if stopped:
                        break
                runtime.write_json(EVIDENCE / ("actions-" + path.name), summary["actions"])
                print("ACTIONS " + path.name, flush=True)
            time.sleep(0.1)
        summary["deadline_reached"] = not stopped
    except Exception as exc:
        summary["errors"].append(dict(at=matrix.now(), error=repr(exc)))
        print("ERROR " + repr(exc), flush=True)
    finally:
        for process, label, directory in ((client, "client", game), (dedicated, "server", server_game)):
            if process is None:
                continue
            try:
                if process.process.poll() is None:
                    if label == "client":
                        matrix.close_owned_window(process)
                        code = process.finish(45)
                    else:
                        code = matrix.stop_server(process)
                else:
                    code = process.finish()
                summary["processes"].append(dict(role=label, pid=process.process.pid, exit_code=code))
            except Exception as exc:
                summary["errors"].append(dict(cleanup=label, error=repr(exc)))
                process.abort()
            matrix.archive_native_logs(directory, EVIDENCE, label)
        summary["completed_at"] = matrix.now()
        runtime.write_json(EVIDENCE / "summary.json", summary)
        print("FINISHED " + json.dumps(summary["processes"]), flush=True)
    return int(bool(summary["errors"]))


if __name__ == "__main__":
    raise SystemExit(main())
