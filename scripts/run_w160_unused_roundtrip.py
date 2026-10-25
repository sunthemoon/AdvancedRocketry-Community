#!/usr/bin/env python3
"""Unused-world old/current/old roundtrip with the R113 baseline-only log declaration."""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import uuid
import zipfile

import run_dedicated_server_smoke as smoke
import run_w158_release_cuts as release
from run_v060_flight_server_smoke import _server_command

OLD_SOURCE = "59f08e179c47be626f52ae278dad2d703fc8bfe6"
OLD_SHA256 = "a3341874427023e922acccb725a7c44987092f2a81a0ea33f47b49c75cd91a21"
MISSING_ID = "advancedrocketrycommunity:rocket_monitoring_station"
LOGGER = "ne.mi.re.GameData/REGISTRIES"
SUMMARY = "Forge Mod Loader detected missing registry entries."
SUMMARY_BODY = [
    "There are 2 missing entries in this save.",
    "If you continue the missing entries will get removed.",
    "A world backup will be automatically created in your saves directory.",
]
ATTEMPT = "There are unidentified mappings in this world - we are going to attempt to process anyway"
MAPPING = re.compile(r"Unidentified mapping from registry minecraft:(block|item)")
MAPPING_ID = re.compile(r"\s+([a-z0-9_.-]+:[a-z0-9_./-]+): ([0-9]+)")
MISSING = re.compile(r"Missing minecraft:(block|item):")
WARNING_ID = re.compile(r"\s+([a-z0-9_.-]+:[a-z0-9_./-]+)")


def audit_log(lines, start_index):
    """Consume only complete, exact R113 events; never hide their raw counts."""
    if start_index not in (1, 2, 3):
        raise smoke.SmokeError("Unused-world start must be 1, 2 or 3")
    lines = list(lines)
    observed = dict.fromkeys(("block_mapping", "item_mapping", "summary", "item_warning", "block_warning", "attempt"), 0)
    events = []
    for line in lines:
        header = release.HEADER.fullmatch(line.rstrip())
        if header or not events:
            events.append((header, [line.rstrip()]))
        else:
            events[-1][1].append(line.rstrip())
    remainder = []
    for header, event in events:
        message = header["message"] if header else ""
        mapping = MAPPING.fullmatch(message)
        missing = MISSING.fullmatch(message)
        # Recognize malformed variants too, including WARNs ignored by the base scanner.
        related = message.startswith(("Unidentified mapping from registry ", "Missing minecraft:",
                                      "Forge Mod Loader detected missing registry entries",
                                      "There are unidentified mappings in this world"))
        if not related:
            remainder.extend(event)
            continue
        if start_index != 3:
            raise smoke.SmokeError("Baseline registry declaration appeared before downgrade")
        expected_level = "ERROR" if mapping or message == ATTEMPT else "WARN"
        if header["logger"] != LOGGER or header["thread"] != "main" or header["level"] != expected_level:
            raise smoke.SmokeError("Unexpected baseline registry logger, thread or level")
        body = [line for line in event[1:] if line.strip()]
        if mapping:
            ids = [MAPPING_ID.fullmatch(line) for line in body]
            if len(ids) != 1 or ids[0] is None or ids[0][1] != MISSING_ID:
                raise smoke.SmokeError("Baseline mapping must list exactly the monitoring-station ID once")
            observed[mapping[1] + "_mapping"] += 1
        elif message == SUMMARY:
            if body != SUMMARY_BODY:
                raise smoke.SmokeError("Baseline summary must report exactly two missing entries")
            observed["summary"] += 1
        elif missing:
            # Forge may combine item and block into one event or log separate headers.
            sections = [message, *body]
            while sections:
                section = MISSING.fullmatch(sections.pop(0))
                identity = WARNING_ID.fullmatch(sections.pop(0)) if sections else None
                if section is None or identity is None or identity[1] != MISSING_ID:
                    raise smoke.SmokeError("Baseline warning must list exactly the declared ID per registry")
                observed[section[1] + "_warning"] += 1
        elif message == ATTEMPT:
            if body:
                raise smoke.SmokeError("Unexpected continuation on baseline attempt message")
            observed["attempt"] += 1
        else:
            raise smoke.SmokeError("Undeclared baseline registry diagnostic")
    expected = int(start_index == 3)
    for key, count in observed.items():
        if key in ("block_warning", "attempt"):
            valid = 0 <= count <= expected
        else:
            valid = count == expected
        if not valid:
            raise smoke.SmokeError(f"Baseline declaration count mismatch: {key}={count}, start={start_index}")
    findings = smoke.scan_log(remainder, expected_mod_version=smoke.V180_STARTUP_VERSION)
    if findings:
        raise smoke.SmokeError("Unused log audit: " + " | ".join(findings))
    return dict(observed=observed, raw=smoke.log_audit_counts(lines))


def run(args):
    repo = Path(__file__).resolve().parents[1]
    source = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip()
    if source != args.source_commit or subprocess.check_output(["git", "status", "--porcelain"], cwd=repo, text=True):
        raise smoke.SmokeError("Exact clean source required")
    token = os.environ.get("W160_LOCK_TOKEN", "")
    lock = Path("D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm/owner.txt")
    if not token or not lock.read_text(encoding="utf-8-sig").startswith(token):
        raise smoke.SmokeError("Owned heavy lock required")
    old = args.old_jar.resolve()
    if smoke.digest_file(old) != OLD_SHA256:
        raise smoke.SmokeError("Old jar differs from the pinned R113 baseline")
    with zipfile.ZipFile(old) as archive:
        if any("/rocket/rejoin/" in name for name in archive.namelist()):
            raise smoke.SmokeError("Archived jar already contains W80 storage")
    evidence = args.evidence_dir.resolve()
    evidence.mkdir(exist_ok=False)
    parent = Path("D:/GitHub/ARCE-Task-Evidence/Temp").resolve()
    session = parent / ("w160g-unused-" + uuid.uuid4().hex)
    result = dict(status="IN_PROGRESS", source_commit=source, starts=[], old_jar_sha256=OLD_SHA256,
                  new_jar_sha256=smoke.digest_file(args.jar), old_source_commit=OLD_SOURCE)

    def save():
        (evidence / "results.json").write_text(json.dumps(result, indent=2), encoding="utf-8")

    try:
        session.mkdir()
        shutil.copytree(args.source_server / "libraries", session / "libraries")
        (session / "mods").mkdir()
        port = smoke.allocate_port()
        smoke.write_server_configuration(session, port, True)
        props = smoke.expected_server_properties(port, True)
        props.update({"level-name": "world-W160-unused", "level-type": "minecraft:flat", "max-tick-time": "60000",
                      "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}], "biome": "minecraft:plains"})})
        (session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(props.items())), encoding="ascii")
        world = session / props["level-name"]
        for index, jar in enumerate((old, args.jar, old), 1):
            resources = release.resources()
            if min(resources["free_disk"].values()) <= 10 * 1024**3:
                raise smoke.SmokeError("Native run requires more than 10 GiB on C/D")
            expected = result["new_jar_sha256"] if index == 2 else OLD_SHA256
            if smoke.digest_file(jar) != expected:
                raise smoke.SmokeError("Pinned input artifact changed")
            installed = session / "mods" / "arce-unused.jar"
            shutil.copy2(jar, installed)
            if smoke.digest_file(installed) != expected:
                raise smoke.SmokeError("Copied artifact differs")
            command = _server_command(args.java)
            command[2] = "-Xmx4G"
            row = dict(index=index, jar_sha256=expected, command=command, **resources)
            result["starts"].append(row)
            save()
            process = smoke.CapturedProcess(command, session, evidence / f"unused-{index}.log")
            try:
                process.wait_for(re.compile(r'Done \(.*For help, type "help"'), 240)
                process.command("save-all flush")
                process.wait_for(re.compile("Saved the game"), 60)
                process.command("stop")
                row["exit"] = process.finish()
                if row["exit"] != 0:
                    raise smoke.SmokeError("Unused server exited abnormally")
                if (world / "data" / release.SIDECAR).exists():
                    raise smoke.SmokeError("Unused start created sidecar")
                row["sidecar_absent"] = True
                row["log_audit"] = audit_log(process.lines, index)
                row["saved_files"] = {p.relative_to(world).as_posix(): smoke.digest_file(p) for p in sorted(world.rglob("*.dat"))}
                save()
            finally:
                if process.process.poll() is None:
                    process.abort()
        result["status"] = "PASS"
    except Exception as failure:
        result.update(status="FAIL", error=str(failure))
    finally:
        if session.is_dir() and not smoke.is_link_or_junction(session) and session.resolve().parent == parent:
            shutil.rmtree(session)
        result["temporary_removed"] = not session.exists()
        save()
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("source-server", "jar", "old-jar", "evidence-dir"):
        parser.add_argument("--" + name, type=Path, required=True)
    for name in ("java", "source-commit"):
        parser.add_argument("--" + name, required=True)
    result = run(parser.parse_args())
    print(json.dumps(result, indent=2))
    raise SystemExit(result["status"] != "PASS")
