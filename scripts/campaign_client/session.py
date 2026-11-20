"""Session lifecycle and file-drop rounds; owner clients are never owned processes."""
import os
import shutil
import time
import uuid
from pathlib import Path

from .attach import AttachedClient, matching_windows
from .cells import copy_tree, load_cell
from .evidence import copy_log, dumps, now, write_json
from .input import Input
from .launch import prepare_client
from .processes import CapturedProcess, require_windows
from .protocol import INPUTS, load_session, read_request
from .server import server_command, write_settings


class MachineLock:
    def __init__(self, path):
        self.path, self.token = Path(path), uuid.uuid4().hex
        self.retain = False

    def __enter__(self):
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.path.mkdir()  # Atomic and fail-closed if the machine is busy; never break someone else's lock.
        try:
            (self.path / "owner.txt").write_text(f"W399 campaign pid={os.getpid()} {self.token} {now()}", encoding="utf-8")
        except BaseException:
            self.path.rmdir()
            raise
        return self

    def __exit__(self, *_):
        if self.retain:
            return
        owner = self.path / "owner.txt"
        if self.token not in owner.read_text(encoding="utf-8"):
            raise RuntimeError("Machine lock ownership changed; lock retained")
        owner.unlink()
        self.path.rmdir()


class Session:
    def __init__(self, spec, cell, backend):
        self.spec, self.cell, self.backend = spec, cell, backend
        self.run = spec["run_dir"]
        self.evidence = self.run / "evidence"
        self.clients, self.owned, self.temporary = {}, [], []
        self.server = None
        self.created = False
        self.pacer = [float("-inf")]
        self.deadline = time.monotonic() + spec["time_limit_seconds"]
        self.summary = {"schema_version": 1, "started_at": now(), "mode": spec["mode"], "status": "STARTING",
                        "forge": cell["forge"], "jei": cell["jei"], "version_id": cell["version_id"],
                        "candidate_sha256": cell["candidate"]["sha256"], "request_count": 0,
                        "failed_requests": 0, "screenshots": [], "cleanup_errors": []}

    def check_time(self):
        if any(getattr(process, "error", None) for process, _ in self.owned):
            raise RuntimeError("Owned process output capture failed")
        if time.monotonic() >= self.deadline:
            raise TimeoutError("Session time limit reached")

    def sleep(self, seconds):
        self.check_time()
        time.sleep(min(seconds, max(0, self.deadline - time.monotonic())))
        self.check_time()

    def save(self):
        write_json(self.run / "summary.json", self.summary)
        if self.spec["mode"] == "attach":
            write_json(self.run / "attach.json", {"schema_version": 1, "verified_at": now(),
                       "clients": [c["attached"].record for c in self.clients.values()]})

    def setup(self):
        sources = [self.cell[key] for key in ("server_dir", "client_game_dir", "runtime_root")]
        sources += [c["game_dir"] for c in self.spec["clients"] if "game_dir" in c]
        for source in sources:
            if self.run.is_relative_to(source) or source.is_relative_to(self.run):
                raise ValueError("Run directory must not overlap prepared inputs")
        self.run.mkdir(parents=True, exist_ok=False)
        self.created = True
        self.evidence.mkdir()
        (self.evidence / "screenshots").mkdir()
        (self.run / "requests").mkdir()
        write_json(self.run / "session.json", self.spec)
        if self.spec["mode"] == "attach":
            # All attach validation precedes any server start. No foreground stealing.
            for spec in self.spec["clients"]:
                attached = AttachedClient(spec, self.cell, self.backend)
                self.clients[spec["player_name"]] = {
                    "attached": attached, "game": spec["game_dir"], "process": None,
                    "input": Input(self.backend, attached.handle, attached.verify,
                                   self.spec["input_interval_seconds"], self.sleep, "attach", self.pacer)}
            self.save()
        if not self.spec["singleplayer"]:
            target = self.run / "server"
            self.temporary.append(target)
            copy_tree(self.cell["server_dir"], target)
            self.check_time()
            write_settings(target, self.spec["server"])
            command = server_command(self.spec["java"], target, self.cell["forge"], self.spec["server"])
            self.server = CapturedProcess(command, target, self.evidence / "server-console.log", console=True)
            self.owned.append((self.server, True))
            self.server.wait_for("Done (", 300, self.sleep)
        if self.spec["mode"] == "launch":
            for spec in self.spec["clients"]:
                self.check_time()
                game = self.run / ("client-" + spec["player_name"])
                self.temporary.append(game)
                command = prepare_client(self.cell, spec, game, self.spec["server"]["server-port"],
                                         self.spec["java"], self.spec["singleplayer"])
                self.check_time()
                proc = CapturedProcess(command, game, self.evidence / ("client-" + spec["player_name"] + "-console.log"))
                self.owned.append((proc, False))
                deadline = time.monotonic() + 300
                window = None
                while time.monotonic() < deadline:
                    windows = matching_windows(self.backend, proc.process.pid)
                    if len(windows) > 1:
                        raise ValueError("Several disposable Minecraft windows")
                    if windows:
                        window = windows[0]
                        break
                    if proc.process.poll() is not None:
                        raise ValueError("Disposable client exited during startup")
                    self.sleep(0.2)
                if window is None:
                    raise TimeoutError("Disposable client window not found")
                identity = self.backend.process(proc.process.pid)
                def verify(identity=identity, handle=window.handle):
                    if self.backend.process(identity.pid) != identity:
                        raise ValueError("Disposable process identity changed")
                    windows = matching_windows(self.backend, identity.pid)
                    if len(windows) != 1 or windows[0].handle != handle or self.backend.foreground() != handle:
                        raise ValueError("Disposable window identity or foreground changed")
                self.clients[spec["player_name"]] = {"game": game, "process": proc,
                    "input": Input(self.backend, window.handle, verify, self.spec["input_interval_seconds"], self.sleep, "launch", self.pacer)}
                self.summary.setdefault("clients", {})[spec["player_name"]] = {"pid": proc.process.pid,
                    "window": window.handle, "launched_at": now()}
        self.summary["status"] = "READY"
        self.save()

    def client_wait(self, client, substring, timeout, start=0):
        if client["process"]:
            return client["process"].wait_for(substring, timeout, self.sleep, start)
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            client["attached"].verify(foreground=False)
            with (client["game"] / "logs/latest.log").open(encoding="utf-8", errors="replace") as stream:
                index = 0
                while line := stream.readline(65536):
                    if len(line) == 65536 and not line.endswith("\n"):
                        raise ValueError("Client log line exceeds bound")
                    if index >= start and substring in line:
                        return line.rstrip("\n")
                    index += 1
                    if index % 1024 == 0:
                        self.check_time()
            self.sleep(0.1)
        raise TimeoutError("Attached log wait expired")

    def perform(self, op):
        self.check_time()
        kind = op["op"]
        if kind == "stop":
            return {"stop": True}
        if kind == "sleep":
            self.sleep(op["seconds"])
            return {"slept": op["seconds"]}
        client = self.clients[op.get("player", next(iter(self.clients)))]
        if kind == "close_client":
            if self.spec["mode"] == "attach":
                raise ValueError("Attach mode cannot close clients")
            client["process"].job.close()
            return {"closed": True}
        if kind == "server":
            if self.server is None:
                start = client["process"].count
                client["input"].key("T")
                client["input"].perform({"op": "text", "text": "/" + op["command"].lstrip("/")})
                client["input"].key("ENTER")
                if op.get("wait"):
                    return {"receipt": self.client_wait(client, op["wait"], op.get("timeout", 30), start)}
            else:
                start = self.server.count
                self.server.command(op["command"])
                if op.get("wait"):
                    return {"receipt": self.server.wait_for(op["wait"], op.get("timeout", 30), self.sleep, start)}
            self.sleep(op.get("settle", 0.3))
            return {"command_sent": True, "tail": self.server.tail(op.get("tail", 3)) if self.server else []}
        if kind == "client_wait":
            return {"line": self.client_wait(client, op["pattern"], op.get("timeout", 30), op.get("start_at", 0))}
        if kind == "status":
            if client.get("attached"):
                client["attached"].verify(foreground=False)
            return {"size": self.backend.size(client["input"].handle),
                    "server_alive": self.server.process.poll() is None if self.server else None,
                    "client_alive": client["process"].process.poll() is None if client["process"] else True,
                    "server_tail": self.server.tail(op.get("tail", 6)) if self.server else []}
        if kind == "screenshot":
            result = client["input"].screenshot(client["game"], self.evidence / "screenshots" / (op["name"] + ".png"))
            self.summary["screenshots"].append(result)
            return result
        if kind in INPUTS:
            return client["input"].perform(op)
        raise ValueError("Unknown operation")

    def loop(self):
        seen = set()
        with (self.run / "results.jsonl").open("x", encoding="utf-8") as results:
            while time.monotonic() < self.deadline:
                pending = sorted(p for p in (self.run / "requests").glob("*.json") if p.name not in seen)
                if len(seen) + len(pending) > 10000:
                    raise ValueError("Session exceeds 10000 request files")
                for path in pending:
                    seen.add(path.name)
                    record = {"id": None, "request_file": path.name, "at": now(), "outputs": [], "error": None}
                    stop = False
                    try:
                        request = read_request(path, self.spec["mode"], self.clients)
                        record["id"] = request["id"]
                        if self.spec["singleplayer"] and any(op["op"] == "server" and len(op["command"].lstrip("/")) > 255
                                                            for op in request["ops"]):
                            raise ValueError("Integrated-server chat command exceeds 256 characters")
                        # Reject the entire batch before any side effect if any target is stale or defocused.
                        targets = {op.get("player", next(iter(self.clients))) for op in request["ops"]
                                   if op["op"] in INPUTS}
                        for name in targets:
                            attached = self.clients[name].get("attached")
                            if attached:
                                attached.verify()
                        for op in request["ops"]:
                            record["outputs"].append(dict(self.perform(op), t=now()))
                            if op["op"] == "stop":
                                stop = True
                                break
                    except Exception as exc:
                        record["error"] = f"{type(exc).__name__}: {exc}"
                        self.summary["failed_requests"] += 1
                    self.summary["request_count"] += 1
                    results.write(dumps(record) + "\n")
                    results.flush()
                    self.save()
                    if stop:
                        self.summary["status"] = "COMPLETE"
                        return
                self.sleep(0.15)
        self.summary["status"] = "DEADLINE"

    def cleanup(self):
        for client in self.clients.values():
            try:
                client["input"].release()
            except Exception as exc:
                self.summary["cleanup_errors"].append(f"Input release: {type(exc).__name__}")
        for process, server in reversed(self.owned):
            try:
                process.close(server=server)
            except Exception as exc:
                self.summary["cleanup_errors"].append(f"Owned process: {type(exc).__name__}")
        games = [("client-" + name, c["game"]) for name, c in self.clients.items()]
        known_games = {g for _, g in games}
        games += [(p.name, p) for p in self.temporary if p not in known_games and p.name != "server"]
        if not self.spec["singleplayer"]:
            games.append(("server", self.run / "server"))
        for label, directory in games:
            for log in (directory / "logs").glob("*.log"):
                try:
                    copy_log(log, self.evidence / f"{label}-{log.name}")
                except Exception as exc:
                    self.summary["cleanup_errors"].append(f"Log copy: {type(exc).__name__}")
        if not any(e.startswith("Owned process:") for e in self.summary["cleanup_errors"]):
            for directory in self.temporary:
                try:
                    if directory.parent.resolve() != self.run or directory.resolve() != directory:
                        raise ValueError("Temporary directory escaped run root")
                    if directory.exists():
                        shutil.rmtree(directory)
                except Exception as exc:
                    self.summary["cleanup_errors"].append(f"Temporary cleanup: {type(exc).__name__}")
        self.summary["ended_at"] = now()
        if self.summary["cleanup_errors"]:
            self.summary["status"] = "FAIL"
        self.save()

    def execute(self):
        try:
            self.setup()
            self.loop()
        except TimeoutError:
            self.summary["status"] = "DEADLINE"
        except BaseException as exc:
            self.summary["status"] = "FAIL"
            self.summary["failure"] = f"{type(exc).__name__}: {exc}"
            raise
        finally:
            if self.created and self.evidence.is_dir():
                self.cleanup()
        return 0 if self.summary["status"] == "COMPLETE" and not self.summary["failed_requests"] else 1


def run_session(path, lock):
    spec = load_session(path)
    cell = load_cell(spec["cell"])
    # No platform probing, process access or JVM start until validation has succeeded.
    require_windows("Live sessions require Windows")
    for drive in {Path.home().anchor, spec["run_dir"].anchor, os.environ.get("SystemDrive", "C:") + "/"}:
        if shutil.disk_usage(drive).free < 10 * 1024**3:
            raise ValueError("At least 10 GiB free is required on system and run drives")
    from .windows import Windows
    with MachineLock(lock) as held:
        session = Session(spec, cell, Windows())
        try:
            return session.execute()
        finally:
            if any(e.startswith("Owned process:") for e in session.summary["cleanup_errors"]):
                held.retain = True
                session.summary["machine_lock_retained"] = True
                session.save()
