#!/usr/bin/env python3
"""Native W157 startup checks on a disposable server, called by the heavy-JVM lock owner."""
from __future__ import annotations

import argparse
import ctypes
from dataclasses import dataclass
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
HEADER = re.compile(r"^\[[^\]\r\n]+\] \[(?P<thread>[^\]\r\n]+)/(?P<level>\w+)\] "
                    r"\[(?P<logger>[^\]\r\n]+)\]: (?P<message>[^\r\n]*)$")
EXCEPTION = re.compile(r"^(?:(?:Caused by|Suppressed): )?[\w.$]+(?:Exception|Error)(?::.*)?$")
PROJECT_LOGGERS = ("io.gi.su.ad.AdvancedRocketryCommunity/",
                   "io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity/")
VANILLA_NPE = ('java.lang.NullPointerException: Cannot invoke "net.minecraft.server.level.ServerLevel.m_6857_()" '
               'because "serverlevel2" is null')
VANILLA_FRAME = "at net.minecraft.server.MinecraftServer.m_129885_(MinecraftServer.java:513)"


@dataclass(frozen=True)
class RefusalExpectation:
    name: str
    loggers: tuple[str, ...]
    level: str
    message_pattern: str
    reason: str
    exceptions: tuple[str, ...] = ()
    count: int = 1


def refusal_expectations(case: str, session: Path) -> tuple[RefusalExpectation, ...]:
    """Exact messages except the generated UUID and in-session crash filename.

    This is a six-start fixture manifest, not a general native crash allowance.
    Exception identities/causes are checked too: vanilla's generic error headers
    must not hide a different failure. All counts are per complete process log.
    """
    diagnostic, reason = {
        "malformed": ("ARCE-BETA-2000", "advancedrocketrycommunity_rocket_rejoins failed semantic validation"),
        "future": ("ARCE-BETA-2001", "advancedrocketrycommunity_rocket_rejoins uses future schema 2"),
    }[case]
    exception = "io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataMigrationException: " + reason
    causes = (exception,) + (("Caused by: java.lang.IllegalArgumentException: Rejoin capacity or sequence",)
                             if case == "malformed" else ())
    message = (f"[{diagnostic}] {REFUSAL} reason={reason}. Keep the world stopped. Use compatible software or "
               "validated whole-world repair including all Levels, entities, POI, journals, rejoins, player data and "
               "metadata; never delete the sidecar or restore isolated files.")
    return (
        RefusalExpectation("sidecar-refusal", PROJECT_LOGGERS, "ERROR", re.escape(message),
                           "The case's invalid root must refuse startup before loading Levels.", causes),
        RefusalExpectation("forge-event-propagation", ("ne.mi.ev.EventBus/EVENTBUS", "net.minecraftforge.eventbus.EventBus/EVENTBUS"),
                           "ERROR", re.escape("Exception caught during firing event: " + reason),
                           "Forge propagates the same ServerAboutToStartEvent exception.", causes),
        RefusalExpectation("server-start-propagation", ("minecraft/MinecraftServer",), "ERROR",
                           re.escape("Encountered an unexpected exception"),
                           "MinecraftServer.runServer reports the same startup refusal.", causes),
        RefusalExpectation("forge-crash-report", ("ne.mi.co.ForgeMod/", "net.minecraftforge.common.ForgeMod/"), "FATAL",
                           r"Preparing crash report with UUID [0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}",
                           "Forge emits one generated crash-report UUID for the refused start."),
        RefusalExpectation("server-crash-report", ("minecraft/MinecraftServer",), "ERROR",
                           re.escape("This crash report has been saved to: " + session.as_posix())
                           + r"/(?:\./)?crash-reports/crash-\d{4}-\d{2}-\d{2}_\d{2}\.\d{2}\.\d{2}-server\.txt",
                           "The report must be in this disposable server, with only its generated timestamp variable."),
        RefusalExpectation("vanilla-unloaded-overworld-stop", ("minecraft/MinecraftServer",), "ERROR",
                           re.escape("Exception stopping the server"),
                           "Minecraft 1.20.1 saveAllChunks:513 dereferences overworld.getWorldBorder before a Level exists. "
                           "runServer unconditionally calls stopServer after pre-Level refusal; no Forge event skips this "
                           "vanilla save. Avoidance would require a Mixin or changing the pre-Level refusal contract.",
                           (VANILLA_NPE,)),
    )


def audit_log(lines: list[str], case: str, session: Path) -> dict:
    if case == "unused":
        findings = smoke.scan_log(lines, expected_mod_version=smoke.V180_STARTUP_VERSION)
        if findings:
            raise smoke.SmokeError("Unexpected native log entries: " + " | ".join(findings))
        return dict(counts=smoke.log_audit_counts(lines), expectations=[])

    expectations = refusal_expectations(case, session)
    counts = [0] * len(expectations)
    remainder = []
    headers = [(index, match) for index, value in enumerate(lines) if (match := HEADER.fullmatch(value.rstrip()))]
    for position, (start, header) in enumerate(headers):
        end = headers[position + 1][0] if position + 1 < len(headers) else len(lines)
        body = [value.strip() for value in lines[start + 1:end]]
        # Normalise path separators, not message contents, for the crash path only.
        for index, expected in enumerate(expectations):
            message = header["message"].replace("\\", "/") if expected.name == "server-crash-report" else header["message"]
            if (header["thread"] == "Server thread" and header["level"] == expected.level
                    and header["logger"] in expected.loggers and re.fullmatch(expected.message_pattern, message)):
                counts[index] += 1
                if tuple(value for value in body if EXCEPTION.fullmatch(value)) != expected.exceptions:
                    raise smoke.SmokeError(f"Unexpected exception under {expected.name}")
                if expected.name == "vanilla-unloaded-overworld-stop":
                    frames = [value for value in body if value.startswith("at ")]
                    if (not frames or not re.fullmatch(re.escape(VANILLA_FRAME) + r"(?: ~\[[^\r\n]+\] \{[^\r\n]*\})?", frames[0])
                            or any("advancedrocketrycommunity" in value.lower() for value in body)):
                        raise smoke.SmokeError("Unexpected vanilla shutdown origin")
                break
        else:
            remainder.append(lines[start])
    # Scan every non-exempt header AND every continuation for linkage failures or
    # malformed/unparsed ERROR/FATAL/project WARN entries. Never discard a stack.
    header_indices = {index for index, _ in headers}
    remainder.extend(value for index, value in enumerate(lines) if index not in header_indices)
    findings = smoke.scan_log(remainder)
    if findings:
        raise smoke.SmokeError("Unexpected native log entries: " + " | ".join(findings))
    if counts != [expected.count for expected in expectations]:
        raise smoke.SmokeError(f"Refusal log expectation counts differ: {dict(zip((e.name for e in expectations), counts))}")
    return dict(counts=smoke.log_audit_counts(lines), expectations=[
        dict(name=e.name, loggers=e.loggers, level=e.level, message_pattern=e.message_pattern,
             exceptions=e.exceptions, expected=e.count, observed=count, reason=e.reason)
        for e, count in zip(expectations, counts)])


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
                # This pinned vanilla/Forge refusal path reports and exits normally.
                # A separate process failure must not be hidden by its expected log.
                if code != 0:
                    raise smoke.SmokeError(f"Native {case} process exited abnormally: {code}")
                if case == "unused":
                    if sidecar.exists():
                        raise smoke.SmokeError("Unused world failed or gained a sidecar")
                elif refusals != 1 or sidecar.read_bytes() != original or 'For help, type "help"' in lines:
                    raise smoke.SmokeError("Startup refusal, byte preservation or no-login ordering failed")
                audit = audit_log(process.lines, case, session)
                result["starts"].append(dict(case=case, attempt=attempt, command=command, exit=code, refusals=refusals,
                                             log_audit=audit,
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
