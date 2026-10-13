#!/usr/bin/env python3
"""Run a disposable native schema-3 upgrade, repeat start, downgrade refusal and backup recovery."""

from __future__ import annotations

import argparse
import gzip
import json
import os
import platform
import re
import shutil
import socket
from pathlib import Path

from inspect_celestial_saved_data import NbtReader
from run_dedicated_server_smoke import CapturedProcess, READY_MARKER, SAVE_MARKER, SmokeError, digest_file


DATA_FILE = "advancedrocketrycommunity_satellite_missions.dat"
FORGE = "1.20.1-47.4.10"
MAX_EVIDENCE = 100 * 1024 * 1024


def payload(file: Path) -> dict:
    if not 0 < file.stat().st_size <= 17 * 1024 * 1024:
        raise SmokeError("Registry fixture exceeds its compressed bound")
    with gzip.open(file, "rb") as stream:
        raw = stream.read(17 * 1024 * 1024 + 1)
    if len(raw) > 17 * 1024 * 1024:
        raise SmokeError("Registry fixture exceeds its expanded bound")
    return NbtReader(raw).read_root()["data"]


def projection(root: dict, *, upgraded: bool) -> dict:
    result = {key: root[key] for key in ("satellites", "missions", "instances", "research_accounts")}
    if upgraded:
        result = json.loads(json.dumps(result))
        for instance in result["instances"]:
            if instance.pop("origin_kind") != "survey_mission" or instance["schema_version"] != 2:
                raise SmokeError("Instance did not retain its legacy survey origin")
            instance["source_mission"] = instance.pop("origin_id")
            instance["schema_version"] = 1
    return result


def prepare(template: Path, target: Path, jar: Path, port: int, world: Path | None = None) -> None:
    target.mkdir()
    shutil.copytree(template / "libraries", target / "libraries")
    (target / "mods").mkdir()
    shutil.copy2(jar, target / "mods" / jar.name)
    (target / "eula.txt").write_text("eula=true\n", encoding="ascii")
    (target / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nlevel-name=world\n"
        "level-type=minecraft:normal\ngenerate-structures=false\nview-distance=2\nsimulation-distance=2\n"
        "max-players=1\nspawn-protection=0\nsync-chunk-writes=true\nenable-rcon=false\n",
        encoding="ascii",
    )
    if world is not None:
        shutil.copytree(world, target / "world")


def launch(java: str, server: Path, evidence: Path, name: str) -> CapturedProcess:
    args_name = "win_args.txt" if platform.system() == "Windows" else "unix_args.txt"
    args_file = Path("libraries/net/minecraftforge/forge") / FORGE / args_name
    if not (server / args_file).is_file():
        raise SmokeError("Native template does not contain the pinned Forge host")
    return CapturedProcess([java, "-Xms512M", "-Xmx2G", f"@{args_file.as_posix()}", "nogui"],
                           server, evidence / f"{name}.log")


def stop(process: CapturedProcess) -> None:
    start = len(process.lines)
    process.command("save-all flush")
    process.wait_for(SAVE_MARKER, 60, start_at=start)
    process.command("stop")
    if process.finish() != 0:
        raise SmokeError("Native server did not stop normally")
    if any("/ERROR]" in line or "/FATAL]" in line for line in process.lines):
        raise SmokeError("Successful native run emitted ERROR/FATAL")


def run(args: argparse.Namespace, summary: dict) -> None:
    work, evidence = args.work_dir.resolve(), args.evidence_dir.resolve()
    if work.exists() or evidence.exists() or work == evidence or work in evidence.parents or evidence in work.parents:
        raise SmokeError("Disposable work and evidence directories must be new and disjoint")
    # Deletion is restricted to this newly created task directory; source templates/worlds are read-only.
    work.mkdir(parents=True)
    evidence.mkdir(parents=True)
    (work / ".w21b-owned").write_text("W21B\n")
    with socket.socket() as binding:
        binding.bind(("127.0.0.1", 0))
        port = binding.getsockname()[1]
    summary.update(tested_commit=args.tested_commit, old_jar_sha256=digest_file(args.old_jar),
                   candidate_jar_sha256=digest_file(args.candidate_jar), forge=FORGE, port=port,
                   fixture_sha256=digest_file(args.fixture))
    processes: list[CapturedProcess] = []
    try:
        old = work / "old"
        prepare(args.template, old, args.old_jar, port)
        process = launch(args.java, old, evidence, "01-old-create"); processes.append(process)
        process.wait_for(READY_MARKER, args.startup_timeout); stop(process)
        original = old / "world" / "data" / DATA_FILE
        original.parent.mkdir(exist_ok=True)
        shutil.copy2(args.fixture, original)
        process = launch(args.java, old, evidence, "02-old-fixture"); processes.append(process)
        process.wait_for(READY_MARKER, args.startup_timeout); stop(process)
        before = payload(original)
        if before["schema_version"] != 3 or len(before["instances"]) != 7:
            raise SmokeError("Pre-W21b host did not retain the seven-state root-3 fixture")
        if sorted(row["state"] for row in before["instances"]) != sorted(
                ["pending", "available", "available", "allocated", "depleted", "expired", "quarantined"]):
            raise SmokeError("Pre-W21b fixture states changed")
        shutil.copy2(original, evidence / "pre-upgrade.dat")
        (evidence / "before.json").write_text(json.dumps(before, indent=2) + "\n")

        candidate = work / "candidate"
        prepare(args.template, candidate, args.candidate_jar, port, old / "world")
        file = candidate / "world" / "data" / DATA_FILE
        process = launch(args.java, candidate, evidence, "03-upgrade"); processes.append(process)
        process.wait_for(READY_MARKER, args.startup_timeout)
        upgraded = payload(file)
        if upgraded["schema_version"] != 4 or upgraded.get("ground_surveys") != []:
            raise SmokeError("Candidate did not migrate to registry root 4")
        if upgraded["save_epoch"] != before["save_epoch"] or projection(upgraded, upgraded=True) != projection(before, upgraded=False):
            raise SmokeError("Migration changed identity, progress, resources or account balances")
        backup_root = candidate / "world" / "advancedrocketrycommunity-backups"
        backups = sorted(path for path in backup_root.iterdir() if path.is_dir())
        if len(backups) != 1 or (backups[0] / DATA_FILE).read_bytes() != original.read_bytes():
            raise SmokeError("Upgrade did not preserve exactly one original backup")
        shutil.copytree(backups[0], evidence / "backup")
        shutil.copy2(file, evidence / "upgraded-at-start.dat")
        (evidence / "after.json").write_text(json.dumps(upgraded, indent=2) + "\n")
        stop(process)

        saved_hash, saved_time = digest_file(file), file.stat().st_mtime_ns
        process = launch(args.java, candidate, evidence, "04-second-start"); processes.append(process)
        process.wait_for(READY_MARKER, args.startup_timeout)
        if digest_file(file) != saved_hash or file.stat().st_mtime_ns != saved_time or len(list(backup_root.iterdir())) != 1:
            raise SmokeError("Second pre-start migration wrote a current file or made another backup")
        if any("ARCE_SATELLITE_SCHEMA4_UPGRADE" in line for line in process.lines):
            raise SmokeError("Second start attempted another satellite upgrade")
        summary["second_start_no_backup_no_rewrite"] = True
        stop(process)

        downgrade = work / "downgrade"
        # Other authorities come from the old host, isolating the satellite downgrade boundary.
        prepare(args.template, downgrade, args.old_jar, port, old / "world")
        downgraded_file = downgrade / "world" / "data" / DATA_FILE
        shutil.copy2(evidence / "upgraded-at-start.dat", downgraded_file)
        unchanged = digest_file(downgraded_file)
        process = launch(args.java, downgrade, evidence, "05-refused-downgrade"); processes.append(process)
        process.wait_for(re.compile(r"FUTURE_SCHEMA.*satellite_missions|satellite_missions.*future schema 4"),
                         args.startup_timeout)
        code = process.finish(120)
        if any(READY_MARKER.search(line) for line in process.lines) or digest_file(downgraded_file) != unchanged:
            raise SmokeError("Old host started or rewrote the schema-4 registry")
        summary["downgrade_exit_code"] = code
        summary["downgrade_refused_file_unchanged"] = True
        shutil.copy2(evidence / "backup" / DATA_FILE, downgraded_file)
        process = launch(args.java, downgrade, evidence, "06-backup-recovery"); processes.append(process)
        process.wait_for(READY_MARKER, args.startup_timeout); stop(process)
        if projection(payload(downgraded_file), upgraded=False) != projection(before, upgraded=False):
            raise SmokeError("Backup recovery changed the legacy domain state")
        summary.update(status="PASS", upgraded_instances=7, backups=1, backup_recovery=True)
    finally:
        for process in processes:
            if process.process.poll() is None:
                process.abort()
        if work.is_dir() and (work / ".w21b-owned").read_text() == "W21B\n":
            shutil.rmtree(work)
        total = sum(path.stat().st_size for path in evidence.rglob("*") if path.is_file())
        summary["evidence_bytes"] = total
        summary["temporary_servers_removed"] = not work.exists()
        (evidence / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
        if total > MAX_EVIDENCE:
            raise SmokeError("Native evidence exceeds 100 MiB")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("template", "old-jar", "candidate-jar", "fixture", "work-dir", "evidence-dir"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--tested-commit", required=True)
    parser.add_argument("--java", default=str(Path(os.environ["JAVA_HOME"]) / "bin" / "java.exe"))
    parser.add_argument("--startup-timeout", type=float, default=240)
    args = parser.parse_args()
    summary = {"status": "FAIL"}
    try:
        run(args, summary)
        print(json.dumps(summary, indent=2))
        return 0
    except (SmokeError, OSError, ValueError) as exception:
        print(f"FAIL: {exception}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
