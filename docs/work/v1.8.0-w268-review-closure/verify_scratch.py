"""Run W268's checks in a disposable draft-applied copy; retain logs, not exports."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import uuid

from apply_scratch import apply, ROOT


def verify(upstream: Path, clients: Path, output: Path, quick: bool = False) -> None:
    output = output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    # A short sibling path keeps the repository's long historical JUnit filenames
    # below Windows' non-extended 260-character file API limit.
    scratch = output.parent / ("s" + uuid.uuid4().hex[:8])
    if scratch.exists():
        raise FileExistsError(scratch)
    report = dict(commands=[], mode="quick" if quick else "full", source_commit=subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT).decode().strip())
    prefix = [sys.executable, "-X", "utf8", "-B"]
    env = dict(os.environ, PYTHONDONTWRITEBYTECODE="1", PYTHONUTF8="1", GIT_OPTIONAL_LOCKS="0")

    def run(label: str, command: list[str], cwd: Path = scratch, timeout: int = 1800) -> None:
        start = time.monotonic()
        result = subprocess.run(command, cwd=cwd, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout)
        (output / (label + ".log")).write_bytes(result.stdout)
        entry = dict(label=label, command=command, cwd=str(cwd), exit_code=result.returncode,
                     seconds=round(time.monotonic() - start, 3), log=label + ".log",
                     log_sha256=hashlib.sha256(result.stdout).hexdigest())
        report["commands"].append(entry)
        (output / "verification.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print(label, "exit=" + str(result.returncode), "seconds=" + str(entry["seconds"]), flush=True)
        print(result.stdout.decode("utf-8", errors="replace")[-1600:], flush=True)

    try:
        audit = apply(scratch)
        (output / "first-match-audit.json").write_text(json.dumps(audit, indent=2) + "\n", encoding="utf-8")
        report["first_match_audit"] = {k: v for k, v in audit.items() if k != "changes"}
        for suffix, flags in (("normal", []), ("accepted", ["--require-accepted"]), ("closure", ["--closure"])):
            run("scratch-ledger-" + suffix, prefix + ["scripts/validate_v180_content_ledger.py"] + flags)
        if quick:
            # Supplement a full run after proposal-only/helper edits. Never report
            # this mode as execution of the importer or four repository suites.
            local_test = "docs/work/v1.8.0-w268-review-closure/test_proposal.py"
            run("proposal-tests", prefix + [local_test])
            run("proposal-tests-optimized", prefix + ["-O", local_test])
            run("scratch-repository", prefix + ["scripts/validate_repository.py", "--require-approved-identity"])
            run("branch-repository", prefix + ["scripts/validate_repository.py", "--require-approved-identity"], ROOT)
            report["completed"] = True
            return
        for plan in sorted((scratch / "tools/import").glob("v180-*-plan.json")):
            data = json.loads(plan.read_bytes())
            # The matching record is identified from the plan's targets, not a stale hardcoded batch list.
            records = []
            sources = {e["source_path"] for e in data["entries"]}
            targets = {e["target_path"] for e in data["entries"]}
            for record in sorted((scratch / "docs/provenance").glob("v1.8.0-*.json")):
                entries = json.loads(record.read_bytes()).get("entries", [])
                if sources == {e.get("source_path") for e in entries} and targets == {e.get("target_path") for e in entries}:
                    records.append(record)
            assert len(records) == 1, (plan, records)
            command = prefix + ["tools/import/import_v180_assets.py", "verify", "--upstream", str(upstream),
                                "--plan", plan.relative_to(scratch).as_posix(), "--record", records[0].relative_to(scratch).as_posix()]
            for version in ("1.12.2", "1.20.1"):
                command += ["--vanilla", version + "=" + str(clients / ("client-" + version + ".jar"))]
            run("import-verify-" + plan.stem.removeprefix("v180-").removesuffix("-plan"), command)
        run("import-verify-v010", prefix + ["tools/import/import_v010_assets.py", "verify", "--upstream", str(upstream)])
        # Individual modules give complete counts even when a baseline-coupled assertion fails.
        for module in ("test_import_v180_assets", "test_validate_v180_content_ledger", "test_validate_v180_provenance", "test_validate_v090_resources"):
            run("scratch-" + module, prefix + ["-m", "unittest", "tests." + module])
        run("scratch-repository", prefix + ["scripts/validate_repository.py", "--require-approved-identity"])
        run("branch-repository", prefix + ["scripts/validate_repository.py", "--require-approved-identity"], ROOT)
        report["completed"] = True
    except Exception as error:
        report["error"] = repr(error)
        raise
    finally:
        if scratch.exists():
            if scratch.parent != output.parent or not scratch.name.startswith("s") or scratch.resolve().is_relative_to(ROOT.resolve()):
                raise RuntimeError("Scratch cleanup boundary mismatch: " + str(scratch))
            shutil.rmtree(scratch)
        report["scratch_removed"] = not scratch.exists()
        (output / "verification.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--upstream", type=Path, required=True)
    parser.add_argument("--clients", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--quick", action="store_true", help="Supplemental draft/helper checks only; skips all imports and the four repository test modules")
    args = parser.parse_args()
    verify(args.upstream.resolve(), args.clients.resolve(), args.output, args.quick)
