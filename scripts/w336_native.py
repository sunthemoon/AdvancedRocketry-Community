"""W336 copy-only native harness; no candidate builds or production instrumentation."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import time
import uuid
import zipfile

import loader_cut_nbt as nbt
import run_dedicated_server_smoke as smoke
from run_v060_flight_server_smoke import _server_command
from run_v180_cargo_integrated import verify_candidate
from run_v180_rocket_settlement_completion import require_lock

NS = "advancedrocketrycommunity:"
TEMP = Path("D:/GitHub/ARCE-Task-Evidence/Temp")
GAMETIME = re.compile(r"\[.*(?:MinecraftServer|minecraft/MinecraftServer).*\]: The time is (\d+)\s*$")
SAVED = re.compile(r"Saved the game")


def require(ok, message):
    if not ok:
        raise ValueError(message)


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def parser(description):
    result = argparse.ArgumentParser(description=description)
    result.add_argument("source_server", type=Path)
    for key in ("jar", "session-dir", "evidence-dir"):
        result.add_argument("--" + key, required=True, type=Path)
    for key in ("source-commit", "candidate-commit", "jar-sha256", "java", "lock-owner"):
        result.add_argument("--" + key, required=True)
    result.add_argument("--startup-timeout", type=float, default=240)
    result.add_argument("--exit-timeout", type=float, default=180)
    result.add_argument("--smoke", action="store_true", help="Local development smoke, not candidate evidence")
    return result


class Native:
    def __init__(self, args):
        self.args = args
        self.session, self.evidence = args.session_dir.resolve(), args.evidence_dir.resolve()
        self.process = None
        self.token = uuid.uuid4().hex
        self.commands = []
        self.starts = []
        self.port = smoke.allocate_port()
        self.pin = verify_candidate(args.jar, args.source_commit, args.candidate_commit, args.jar_sha256)
        require_lock(args.lock_owner)
        require(self.session.parent == TEMP.resolve() and not self.session.exists()
                and not smoke.is_link_or_junction(self.session), "Unsafe or occupied W336 session")
        require(not self.evidence.exists() and self.session not in self.evidence.parents
                and self.evidence not in self.session.parents, "Occupied or overlapping evidence")
        libraries = args.source_server.resolve() / "libraries"
        require(libraries.is_dir() and not any(smoke.is_link_or_junction(p) for p in libraries.rglob("*")),
                "Missing or linked Forge libraries")
        self.session.mkdir()
        (self.session / ".w336-owned").write_text(self.token, encoding="ascii")
        self.evidence.mkdir(parents=True)
        shutil.copytree(libraries, self.session / "libraries")
        (self.session / "mods").mkdir()
        smoke.write_server_configuration(self.session, self.port, True)

    def start(self, label, world="world", properties=None, jar=None):
        require(self.process is None, "A W336 process is already running")
        require_lock(self.args.lock_owner)
        require(min(shutil.disk_usage(d).free for d in ("C:/", "D:/")) >= 10 * 1024**3,
                "Native start refused: less than 10 GiB free")
        require(smoke.digest_file(self.args.jar) == self.args.jar_sha256.lower(), "Candidate JAR changed")
        shutil.copy2(jar or self.args.jar, self.session / "mods/w336.jar")
        config = smoke.expected_server_properties(self.port, True)
        config.update({"level-name": world, "level-type": "minecraft:flat", "view-distance": "2",
                       "simulation-distance": "2", "spawn-protection": "0", "max-tick-time": "60000",
                       "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}],
                                                          "biome": "minecraft:plains"})})
        (self.session / "server.properties").write_text(
            "".join(f"{k}={v}\n" for k, v in sorted(config.items())), encoding="ascii")
        command = _server_command(self.args.java)
        command[2] = "-Xmx4G"
        command[4:4] = [f"-Dadvancedrocketrycommunity.{k}={v}" for k, v in sorted((properties or {}).items())]
        self.starts.append(dict(label=label, jar_sha256=smoke.digest_file(jar or self.args.jar), command=command))
        write_json(self.evidence / "starts.json", self.starts)
        self.process = smoke.CapturedProcess(command, self.session, self.evidence / (label + ".log"))
        return self.process

    def ready(self):
        self.process.wait_for(smoke.READY_MARKER, self.args.startup_timeout)

    def send(self, value, marker=None, timeout=60):
        start = len(self.process.lines)
        self.commands.append(value)
        self.process.command(value)
        if marker is not None:
            pattern = re.compile(marker) if isinstance(marker, str) else marker
            return self.process.lines[self.process.wait_for(pattern, timeout, start_at=start)]

    def barrier(self):
        token = "W336_" + uuid.uuid4().hex
        self.send("say " + token, re.escape(token))

    def tick(self):
        line = self.send("time query gametime", GAMETIME)
        return int(GAMETIME.search(line)[1])

    def loaded(self, at, dimension="minecraft:overworld"):
        tag = "W336_LOADED_" + uuid.uuid4().hex
        start = len(self.process.lines)
        self.send(f"execute in {dimension} if loaded {' '.join(map(str, at))} run say {tag}")
        self.barrier()
        return any(tag in line for line in self.process.lines[start:])

    def load(self, positions):
        for x, _, z in positions:
            self.send(f"forceload add {x} {z}")
        deadline = time.monotonic() + 120
        while True:
            if all(self.loaded(at) for at in positions):
                return
            require(time.monotonic() < deadline, "Fixture columns did not load")
            time.sleep(0.25)

    def stop(self, label, audit=None):
        self.send("stop")
        process, self.process = self.process, None
        code = process.finish(self.args.exit_timeout)
        require(code == 0, f"Native stop exited {code}")
        lines = (self.session / "logs/latest.log").read_text(encoding="utf-8", errors="replace").splitlines()
        shutil.copy2(self.session / "logs/latest.log", self.evidence / (label + "-latest.log"))
        if audit:
            audit(lines)
        else:
            require(not any("/ERROR]" in line or "/FATAL]" in line for line in lines), "Unexpected native ERROR/FATAL")
        return lines

    def capture(self, world, label):
        """Archive complete stopped world, including metadata and every dimension; verify bytes."""
        require(self.process is None, "Cannot archive a running world")
        path = self.evidence / (label + ".zip")
        rows = {}
        with zipfile.ZipFile(path, "x", zipfile.ZIP_DEFLATED) as output:
            for file in sorted(world.rglob("*")):
                require(not smoke.is_link_or_junction(file), "Linked world member")
                if file.is_file() and file.name != "session.lock":
                    key = file.relative_to(world).as_posix()
                    rows[key] = smoke.digest_file(file)
                    output.write(file, key)
            output.writestr("W336-MEMBERS.json", json.dumps(rows, indent=2))
        with zipfile.ZipFile(path) as archive:
            require(all(hashlib.sha256(archive.read(k)).hexdigest() == v for k, v in rows.items()),
                    "Native archive readback differs")
        return dict(file=path.name, sha256=smoke.digest_file(path), members=rows)

    def close(self):
        if self.process is not None:
            self.process.abort()
            self.process = None
        write_json(self.evidence / "commands.json", self.commands)
        require(self.session.parent == TEMP.resolve()
                and not smoke.is_link_or_junction(self.session)
                and (self.session / ".w336-owned").read_text(encoding="ascii") == self.token,
                "Refusing cleanup of a foreign session")
        shutil.rmtree(self.session)


def execute(args, family):
    run = Native(args)
    report = dict(status="IN_PROGRESS", pins=run.pin, local_smoke=args.smoke)
    try:
        report.update(family(run))
        report.setdefault("status", "PASS")
        if report["status"] == "IN_PROGRESS":
            report["status"] = "PASS"
        return 0 if report["status"] == "PASS" else 1
    except Exception as failure:
        report.update(status="FAILED", error=str(failure))
        if run.process is not None:
            run.process.abort()
            run.process = None
        report["failure_worlds"] = [run.capture(world, "failure-" + world.name)
                                    for world in sorted(run.session.glob("world*")) if world.is_dir()]
        raise
    finally:
        write_json(run.evidence / "results.json", report)
        run.close()
