#!/usr/bin/env python3
"""Four finite owned-process discovery cuts, followed by unedited same-world recovery.

Uses the unchanged packaged host and an external loopback JDI observer. Never
invokes target methods, repairs saves or treats process termination as power loss.
"""
from __future__ import annotations

import argparse
import contextlib
import json
import platform
import re
import shutil
import socket
import subprocess
import sys
import time
import uuid
from pathlib import Path

if __package__:
    from . import v140_discovery_cut_fixture as fixture
else:
    import v140_discovery_cut_fixture as fixture

migration, worlds = fixture.migration, fixture.worlds
support, server, schema = fixture.support, fixture.server, fixture.schema
require, write_json, HOST = fixture.require, fixture.write_json, fixture.HOST


def port_closed(port: int) -> None:
    with socket.socket() as connection:
        connection.settimeout(1)
        require(connection.connect_ex(("127.0.0.1", port)) != 0, "Owned listener remains open")


def installed(root: Path, artifacts: list[dict]) -> None:
    expected = {entry["name"]: entry["sha256"] for entry in artifacts}
    require({path.name for path in (root / "mods").iterdir()} == set(expected), "Installed mod set differs")
    for name, digest in expected.items():
        path = support.safe_path(root / "mods" / name)
        require(server.digest_file(path) == digest, "Installed artifact changed: " + name)


def terminate_at_cut(process, probe) -> dict:
    require(process.process.poll() is None and probe.process.poll() is None,
            "Owned server and suspended observer must remain alive before kill")
    pid = process.process.pid
    process.process.kill()
    code = process.finish(30)
    require(code != 0, "Forced stop was reported as a clean exit")
    probe_code = probe.finish(10)
    require(probe_code == 0 and any(line.strip() == "MIG03_DISCONNECTED_AFTER_CUT" for line in probe.lines),
            "Observer did not confirm held-cut disconnection")
    return {"pid": pid, "operation": "owned Popen.kill", "exit_code": code, "probe_exit_code": probe_code}


class CutRun(worlds.Harness):
    def __init__(self, root, output, java, port, artifacts, classes):
        super().__init__(root, output, java, port)
        self.artifacts, self.classes = artifacts, classes

    @contextlib.contextmanager
    def phase(self, name, debug_port=None):
        directory = self.output / name
        directory.mkdir(parents=True)
        command = support.rocket_smoke._server_command(self.java)
        if debug_port is not None:
            command.insert(1, f"-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:{debug_port}")
        receipt = {"phase": name, "result": "IN_PROGRESS", "command": command, "cwd": str(self.root), "port": self.port}
        process, commands = None, []
        write_json(directory / "launch.json", receipt)
        try:
            installed(self.root, self.artifacts)
            process = server.CapturedProcess(command, self.root, directory / "stdout.txt")
            receipt["pid"] = process.process.pid
            original = process.command
            def recorded(value):
                commands.append(value)
                original(value)
            process.command = recorded
            process.wait_for(server.READY_MARKER, 240)
            status = server.wait_for_status(self.port)
            support.validate_status(status, True, self.artifacts[0]["version"])
            write_json(directory / "status.json", status)
            self.query(process, "arce celestial validate", "Celestial catalog generation 1 is valid with 6 bodies")
            require(any("Accepted satellite catalog generation 1 with 2 definitions" in line for line in process.lines),
                    "Satellite catalog not accepted")
            yield process, directory, receipt
            require(process.process.poll() is not None, "Phase did not terminate its owned process")
            schema.validate_log(process.lines, [])
            installed(self.root, self.artifacts)
            port_closed(self.port)
            if debug_port is not None:
                port_closed(debug_port)
            receipt.update(result="PASS", log_counts=server.log_audit_counts(process.lines))
        except BaseException as exc:
            receipt.update(result="FAIL", error=f"{type(exc).__name__}: {exc}")
            raise
        finally:
            if process is not None:
                process.abort()
            receipt["exit_code"] = process.process.poll() if process else None
            write_json(directory / "observations.json", receipt)
            write_json(directory / "commands.json", commands)
            for relative in ("logs/latest.log", "logs/debug.log", "server.properties", "eula.txt"):
                source = self.root / relative
                if source.exists():
                    (directory / source.name).write_bytes(support.regular(source, 32 * 1024**2))

    def stop(self, process):
        self.query(process, "save-all flush", server.SAVE_MARKER, 60)
        process.command("stop")
        require(process.finish() == 0, "Clean process did not exit zero")

    def mission_status(self, process, mission, expected):
        deadline = time.monotonic() + 10
        while True:
            match = self.query(process, "arce satellite admin mission " + mission["mission"],
                               rf"mission={mission['mission']} .* status=(\S+) deadline=")
            if match.group(1) == expected:
                return
            require(expected == "CLAIMED" and match.group(1) == "CLAIM_PENDING_DISCOVERY"
                    and time.monotonic() < deadline, "Automatic mission state differs")
            time.sleep(0.05)

    def prepare(self, old):
        with self.phase("prepare") as (process, directory, receipt):
            start = len(process.lines)
            match = self.query(process, f"arce satellite release-test launch {worlds.native.OWNER} mars",
                    r"ARCE_RELEASE_TEST_SATELLITE_LAUNCH satellite=(\S+) mission=(\S+) owner=(\S+) target=(\S+) code=SUCCESS deadline=(\d+)")
            satellite, mission, owner, body, deadline = match.groups()
            require(owner == worlds.native.OWNER and body == HOST + ":mars", "Prepared mission identity differs")
            selected = {"mission": str(uuid.UUID(mission)), "satellite": str(uuid.UUID(satellite)),
                        "owner": owner, "body": body, "deadline": int(deadline)}
            process.wait_for(re.compile(r"ARCE_SATELLITE_SCHEDULER completed=\d+ inspected=\d+ remaining=0"), 30, start_at=start)
            self.mission_status(process, selected, "READY")
            self.stop(process)
            ready = fixture.capture(self.root / "world", directory / "state")
            require(not ready["pending"], "Clean preparation left scratch data")
            fixture.check_prepared(old, ready["authority"], selected)
            receipt["mission"] = selected
            return selected, ready["authority"]

    def cut(self, boundary, ready, mission):
        debug_port = server.allocate_port()
        with self.phase(f"cut-{boundary}/kill", debug_port) as (process, directory, receipt):
            self.mission_status(process, mission, "READY")
            self.query(process, "save-all flush", server.SAVE_MARKER, 60)
            before = fixture.capture(self.root / "world", directory / "before")
            require(not before["pending"], "Fresh cut world already has scratch data")
            fixture.same_authority(ready, before["authority"])
            command = [self.java, "--add-modules", "jdk.jdi", "-cp", str(self.classes),
                       "DiscoveryCutProbe", str(debug_port), str(boundary), mission["mission"]]
            write_json(directory / "probe-launch.json", {"command": command, "cwd": str(self.root)})
            probe = None
            try:
                probe = server.CapturedProcess(command, self.root, directory / "probe.txt")
                line, bci = (100, 394) if boundary == 4 else (90, 326)
                probe.wait_for(re.compile(rf"^MIG03_ARMED boundary={boundary} line={line} bci={bci} bytecode_sha256=[0-9a-f]{{64}}$"), 15)
                process.command("arce satellite release-test claim " + mission["mission"])
                probe.wait_for(re.compile(rf"^MIG03_CUT_REACHED boundary={boundary} store=\S+$"), 15)
                require(probe.process.poll() is None, "Observer exited before paused capture")
                paused = fixture.capture(self.root / "world", directory / "paused")
                fixture.check_cut(ready, paused, mission, boundary)
                receipt["termination"] = terminate_at_cut(process, probe)
                killed = fixture.capture(self.root / "world", directory / "killed")
                require(killed == paused, "Authority/scratch bytes changed between suspended capture and kill")
                receipt["world_after_kill"] = migration.inventory(self.root / "world")
                receipt["boundary"] = boundary
                return killed, receipt["world_after_kill"]
            finally:
                # Even observation failures stop the owned server before detaching the debugger.
                if process.process.poll() is None:
                    process.process.kill()
                    process.finish(30)
                if probe is not None:
                    probe.abort()

    def recover(self, boundary, ready, mission, killed, world_after_kill):
        directory = self.output / f"cut-{boundary}"
        before = fixture.capture(self.root / "world", directory / "before-restart")
        require(before == killed and migration.inventory(self.root / "world") == world_after_kill,
                "World changed after termination and before restart")
        times = fixture.check_cut(ready, killed, mission, boundary)
        with self.phase(f"cut-{boundary}/recovery") as (process, directory, receipt):
            self.mission_status(process, mission, "READY" if boundary == 1 else "CLAIMED")
            self.query(process, "save-all flush", server.SAVE_MARKER, 60)
            automatic = fixture.capture(self.root / "world", directory / "before-manual-claim")
            fixture.check_automatic(ready, automatic, mission, boundary, killed)
            self.claim_research(process, mission, "SUCCESS" if boundary == 1 else "ALREADY_CLAIMED")
            self.query(process, "save-all flush", server.SAVE_MARKER, 60)
            first = fixture.capture(self.root / "world", directory / "first-claim")
            fixture.check_phase(ready, first["authority"], mission, "claimed", True, **times)
            if boundary != 1:
                fixture.same_authority(automatic["authority"], first["authority"])
            self.claim_research(process, mission, "ALREADY_CLAIMED")
            self.stop(process)
            final = fixture.capture(self.root / "world", directory / "final")
            fixture.same_authority(first["authority"], final["authority"])
            require(not first["pending"] and not final["pending"], "Completed claim left scratch data")
            receipt.update(boundary=boundary, mission=mission, automatic_state="READY" if boundary == 1 else "CLAIMED")


def setup(root, libraries, source, artifacts):
    root.mkdir()
    shutil.copytree(libraries, root / "libraries")
    (root / "mods").mkdir()
    for item in artifacts:
        shutil.copyfile(item["path"], root / "mods" / item["name"])
    migration.copy_world(source, root / "world")
    port = server.allocate_port()
    server.write_server_configuration(root, port, True)
    return port


def compile_probe(java, output):
    source = Path(__file__).parent / "java/DiscoveryCutProbe.java"
    destination = output / "DiscoveryCutProbe.java"
    destination.write_bytes(support.regular(source, 32768))
    classes = output / "probe-classes"
    classes.mkdir()
    javac = Path(java).with_name("javac.exe" if platform.system() == "Windows" else "javac")
    command = [str(javac), "--add-modules", "jdk.jdi", "-d", str(classes), str(destination)]
    result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=30)
    (output / "probe-compile.txt").write_bytes(result.stdout)
    write_json(output / "probe-compile.json", {"command": command, "exit_code": result.returncode})
    require(result.returncode == 0, "JDI helper did not compile")
    return classes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("work_dir", type=Path)
    for name in ("libraries-dir", "migration-evidence", "host-jar", "fixture-jar", "evidence-dir"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--accept-eula", action="store_true", required=True)
    args, output, source_inventory = parser.parse_args(), None, None
    try:
        root, libraries, previous, host, consumer, evidence = map(support.safe_path,
            (args.work_dir, args.libraries_dir, args.migration_evidence, args.host_jar, args.fixture_jar, args.evidence_dir))
        require(not root.exists() and not evidence.exists(), "Work and evidence directories must be new")
        inputs = (libraries, previous, host, consumer)
        for destination in (root, evidence):
            for other in (*inputs, evidence if destination == root else root):
                require(destination != other and destination not in other.parents and other not in destination.parents,
                        "Input/output paths must be disjoint")
        members = list(libraries.rglob("*"))
        require(libraries.is_dir() and 0 < len(members) <= 1024, "Library tree exceeds bounds")
        for path in members:
            support.safe_path(path)
        artifacts = [support.artifact(host, HOST, "1.20.1-1.4.0-dev"), support.artifact(consumer, support.FIXTURE, "1.0.0")]
        provenance = fixture.verify_source(previous, artifacts)
        java, version = server.resolve_java(args.java)
        evidence.mkdir(); root.mkdir(); output = evidence
        write_json(output / "source-provenance.json", provenance)
        source = previous / "original-world"
        source_inventory = migration.inventory(source)
        old = fixture.capture(source, output / "original-state")["authority"]
        args_file = libraries / "net/minecraftforge/forge" / server.FORGE_COORDINATE / ("win_args.txt" if platform.system() == "Windows" else "unix_args.txt")
        summary = {"result": "IN_PROGRESS", "schema_version": 1, "artifacts": artifacts, "java": version,
                   "scope": "Four finite two-store process cuts; no target invocation, state repair, clients or power-loss claim",
                   "forge_args_sha256": server.digest_file(args_file), "work_dir": str(root)}
        write_json(output / "summary.json", summary)
        classes = compile_probe(java, output)
        prepared = root / "prepare"
        port = setup(prepared, libraries, source, artifacts)
        mission, ready = CutRun(prepared, output, java, port, artifacts, classes).prepare(old)
        backup_inventory = migration.copy_world(prepared / "world", output / "ready-world")
        write_json(output / "ready-world-files.json", backup_inventory)
        write_json(output / "mission.json", mission)
        for boundary in (1, 2, 3, 4):
            case = root / f"cut-{boundary}"
            port = setup(case, libraries, output / "ready-world", artifacts)
            run = CutRun(case, output, java, port, artifacts, classes)
            killed, files = run.cut(boundary, ready, mission)
            run.recover(boundary, ready, mission, killed, files)
            print(f"[PASS] Boundary {boundary}: owned cut and same-world recovery", flush=True)
        require(migration.inventory(source) == source_inventory, "Original source changed")
        require(migration.inventory(output / "ready-world") == backup_inventory, "Clean prepared backup changed")
        summary.update(result="PASS", mission=mission, boundaries=[1, 2, 3, 4], source_unchanged=True, backup_unchanged=True)
        write_json(output / "summary.json", summary)
        print(f"[PASS] Four finite native cuts; evidence: {output}")
        return 0
    except (OSError, ValueError, KeyError, TypeError, IndexError, subprocess.SubprocessError, support.SmokeError) as exc:
        if output is not None:
            write_json(output / "failure.json", {"error": str(exc), "type": type(exc).__name__})
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1
    finally:
        if output is not None:
            if source_inventory is not None:
                write_json(output / "original-unchanged.json", {"unchanged": migration.inventory(source) == source_inventory})
            files = sorted(path for path in output.rglob("*") if path.is_file() and path.name != "SHA256SUMS")
            (output / "SHA256SUMS").write_text("".join(f"{server.digest_file(path)}  {path.relative_to(output).as_posix()}\n"
                                              for path in files), encoding="ascii", newline="\n")


if __name__ == "__main__":
    raise SystemExit(main())
