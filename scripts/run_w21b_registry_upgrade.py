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
import subprocess
import time
from pathlib import Path

try:
    from .inspect_celestial_saved_data import NbtReader
    from .run_dedicated_server_smoke import CapturedProcess, READY_MARKER, SAVE_MARKER, SmokeError, digest_file
except ImportError:
    from inspect_celestial_saved_data import NbtReader
    from run_dedicated_server_smoke import CapturedProcess, READY_MARKER, SAVE_MARKER, SmokeError, digest_file


ROOT = Path(__file__).resolve().parents[1]
DATA_FILE = "advancedrocketrycommunity_satellite_missions.dat"
FORGE = "1.20.1-47.4.10"
MAX_EVIDENCE = 100 * 1024 * 1024
BUILD_TIMEOUT_SECONDS = 1_800


def git(repository: Path, *args: str) -> str:
    try:
        completed = subprocess.run(("git", *args), cwd=repository, capture_output=True, text=True,
                                   encoding="utf-8", errors="replace", check=False, timeout=120)
    except subprocess.TimeoutExpired as exception:
        raise SmokeError(f"git {' '.join(args)} timed out") from exception
    if completed.returncode != 0:
        raise SmokeError(f"git {' '.join(args)} failed: {completed.stderr.strip()}")
    return completed.stdout


def source_identity(repository: Path, tested_commit: str | None = None) -> dict:
    """The tested source is HEAD of a clean tree; a given --tested-commit must name that HEAD."""
    head = git(repository, "rev-parse", "HEAD").strip()
    if git(repository, "status", "--porcelain", "--untracked-files=all"):
        raise SmokeError("The tested worktree has uncommitted or untracked changes")
    if tested_commit is not None and (len(tested_commit) < 7 or not head.startswith(tested_commit.lower())):
        raise SmokeError(f"--tested-commit {tested_commit} is not HEAD {head}")
    return {"tested_commit": head, "worktree_clean": True}


def build_candidate(repository: Path, command: list[str], jar: Path, log: Path) -> dict:
    """Builds the candidate from the clean HEAD and records the jar's SHA-256 as soon as the build ends."""
    before = source_identity(repository)
    started = time.time()
    with log.open("w", encoding="utf-8") as output:
        try:
            completed = subprocess.run(command, cwd=repository, stdout=output, stderr=subprocess.STDOUT,
                                       check=False, timeout=BUILD_TIMEOUT_SECONDS)
        except subprocess.TimeoutExpired as exception:
            raise SmokeError("Candidate build timed out") from exception
    if completed.returncode != 0:
        raise SmokeError(f"Candidate build failed with exit code {completed.returncode}")
    # Two seconds cover the coarsest file timestamp; a jar from an earlier build is older than that.
    if not jar.is_file() or jar.stat().st_mtime < started - 2:
        raise SmokeError("Candidate build did not write the candidate jar")
    built = {"candidate_jar_sha256_at_build": digest_file(jar), "candidate_build_log": log.name,
             "candidate_built_by_harness": True}
    if source_identity(repository) != before:
        raise SmokeError("The tested source changed during the candidate build")
    return built


def default_build_command(repository: Path) -> list[str]:
    wrapper = repository / ("gradlew.bat" if platform.system() == "Windows" else "gradlew")
    return [str(wrapper), "--offline", "--console=plain", "jar"]


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
    repository = args.repository.resolve()
    identity = source_identity(repository, args.tested_commit)
    # Deletion is restricted to this newly created task directory; source templates/worlds are read-only.
    work.mkdir(parents=True)
    evidence.mkdir(parents=True)
    (work / ".w21b-owned").write_text("W21B\n")
    with socket.socket() as binding:
        binding.bind(("127.0.0.1", 0))
        port = binding.getsockname()[1]
    summary.update(identity, candidate_built_by_harness=False)
    processes: list[CapturedProcess] = []
    try:
        if args.build_candidate:
            summary.update(build_candidate(repository, default_build_command(repository), args.candidate_jar.resolve(),
                                           evidence / "00-build-candidate.log"))
        summary.update(old_jar_sha256=digest_file(args.old_jar), candidate_jar_sha256=digest_file(args.candidate_jar),
                       forge=FORGE, port=port, fixture_sha256=digest_file(args.fixture))
        if args.build_candidate and summary["candidate_jar_sha256"] != summary["candidate_jar_sha256_at_build"]:
            raise SmokeError("The candidate jar changed after the harness built it")
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
        if source_identity(repository) != identity or digest_file(args.candidate_jar) != summary["candidate_jar_sha256"]:
            raise SmokeError("The tested source or candidate jar changed during the native run")
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
    parser.add_argument("--repository", type=Path, default=ROOT,
                        help="Clean checkout under test; its HEAD is recorded as the tested commit")
    parser.add_argument("--tested-commit", help="Optional; refused unless it names the repository's HEAD")
    parser.add_argument("--build-candidate", action="store_true",
                        help="Build --candidate-jar from the clean HEAD with the Gradle jar task first")
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
