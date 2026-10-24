#!/usr/bin/env python3
"""Native W157 startup checks on a disposable server, called by the heavy-JVM lock owner."""
from __future__ import annotations

import argparse
import ctypes
import gzip
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import uuid

import run_dedicated_server_smoke as smoke
from run_v060_flight_server_smoke import _server_command

NAME = "advancedrocketrycommunity_rocket_rejoins.dat"
REFUSAL = "Beta world data check blocked startup; type=ROCKET_REJOINS;"


def payload(schema: int, sequence: int) -> bytes:
    def text(value: str) -> bytes:
        raw = value.encode("ascii")
        return struct.pack(">H", len(raw)) + raw

    def tag(kind: int, name: str, value: bytes) -> bytes:
        return bytes([kind]) + text(name) + value

    data = (tag(3, "schema_version", struct.pack(">i", schema))
            + tag(8, "format_epoch", text("v1.8.0-rocket-rejoins"))
            + tag(4, "next_sequence", struct.pack(">q", sequence))
            + tag(9, "batches", b"\0\0\0\0\0")
            + tag(9, "entries", b"\0\0\0\0\0") + b"\0")
    return gzip.compress(tag(10, "", tag(10, "data", data) + b"\0"), mtime=0)


def run(args) -> dict:
    repo = Path(__file__).resolve().parents[1]
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip()
    if commit != args.source_commit or subprocess.check_output(["git", "status", "--porcelain"], cwd=repo, text=True):
        raise smoke.SmokeError("Native run requires the exact clean committed source")
    token = os.environ.get("W157_LOCK_TOKEN", "")
    lock = Path("D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm/owner.txt")
    if not token or not lock.read_text(encoding="utf-8-sig").startswith(token):
        raise smoke.SmokeError("Heavy-JVM lock is not owned by the caller")
    evidence = args.evidence_dir.resolve()
    evidence.mkdir(exist_ok=False)
    temp_root = Path("D:/GitHub/ARCE-Task-Evidence/Temp").resolve()
    session = temp_root / ("w157-native-" + uuid.uuid4().hex)
    result = dict(status="IN_PROGRESS", source_commit=commit, jar_sha256=smoke.digest_file(args.jar), starts=[])
    try:
        session.mkdir()
        shutil.copytree(args.source_server / "libraries", session / "libraries")
        (session / "mods").mkdir()
        shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = smoke.allocate_port()
        smoke.write_server_configuration(session, port, True)
        for case in ("unused", "malformed", "future"):
            world = session / ("world-" + case)
            sidecar = world / "data" / NAME
            original = None
            if case != "unused":
                sidecar.parent.mkdir(parents=True)
                original = payload(2 if case == "future" else 1, 0 if case == "future" else -1)
                sidecar.write_bytes(original)
                (evidence / (case + ".dat")).write_bytes(original)
            props = smoke.expected_server_properties(port, True)
            props.update({"level-name": world.name, "level-type": "minecraft:flat", "max-tick-time": "60000",
                          "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}],
                                                            "biome": "minecraft:plains"})})
            (session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(props.items())), encoding="ascii")
            for attempt in (1, 2):
                for drive in (Path("C:/"), Path("D:/")):
                    if shutil.disk_usage(drive).free < 10 * 1024**3:
                        raise smoke.SmokeError("Insufficient disk before native start")
                class MemoryStatus(ctypes.Structure):
                    _fields_ = [("length", ctypes.c_ulong), ("load", ctypes.c_ulong)] + [
                        (name, ctypes.c_ulonglong) for name in ("total", "available", "page_total", "page_available",
                                                               "virtual_total", "virtual_available", "extended")]
                memory = MemoryStatus()
                memory.length = ctypes.sizeof(memory)
                if not ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(memory)) or memory.available < 6 * 1024**3:
                    raise smoke.SmokeError("Insufficient or unknown free memory before native start")
                command = _server_command(args.java)
                command[2] = "-Xmx4G"
                process = smoke.CapturedProcess(command, session, evidence / f"{case}-{attempt}.log")
                try:
                    if case == "unused":
                        process.wait_for(re.compile(r'Done \(.*For help, type "help"'), 240)
                        process.command("save-all flush")
                        process.wait_for(re.compile(r"Saved the game"), 60)
                        process.command("stop")
                    else:
                        process.wait_for(re.compile(re.escape(REFUSAL)), 240)
                    code = process.finish()
                finally:
                    if process.process.poll() is None:
                        process.abort()
                lines = "".join(process.lines)
                refusals = lines.count(REFUSAL)
                if case == "unused":
                    if code != 0 or sidecar.exists() or "/ERROR]" in lines or "/FATAL]" in lines:
                        raise smoke.SmokeError("Unused world failed or gained a sidecar")
                elif refusals != 1 or sidecar.read_bytes() != original or 'For help, type "help"' in lines:
                    raise smoke.SmokeError("Startup refusal, byte preservation or no-login ordering failed")
                result["starts"].append(dict(case=case, attempt=attempt, command=command, exit=code, refusals=refusals,
                                             free_memory=memory.available,
                                             sidecar_present=sidecar.exists(),
                                             sidecar_sha256=hashlib.sha256(original).hexdigest() if original else None))
        result["status"] = "PASS"
    except Exception as failure:
        result.update(status="FAIL", error=str(failure))
    finally:
        (evidence / "results.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
        if session.is_dir() and not session.is_symlink() and session.resolve().parent == temp_root:
            shutil.rmtree(session)
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-server", type=Path, required=True)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--java", required=True)
    result = run(parser.parse_args())
    print(json.dumps(result, indent=2))
    return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
