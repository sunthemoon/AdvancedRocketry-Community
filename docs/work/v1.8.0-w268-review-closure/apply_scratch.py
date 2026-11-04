"""Create a NEW scratch Git export and apply W268 drafts there, never in a worktree."""
from __future__ import annotations

import argparse
from collections import Counter
import csv
import fnmatch
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import tarfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
PLAN = "docs/work/v1.8.0-asset-plan.csv"
ALLOW = "docs/work/v1.8.0-asset-import-allowlist.txt"
ORIGIN = "docs/provenance/v1.8.0-origin-findings.json"
ADR = "docs/decisions/ADR-062-CLASSIC-CONTENT-DISPOSITIONS-AND-BATCHES.md"
PREFIX = "src/main/resources/assets/advancedrocketry/"


def git(*args, cwd=ROOT) -> bytes:
    return subprocess.check_output(["git", "-c", "core.autocrlf=false", *map(str, args)], cwd=cwd)


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def csv_rows(path: Path) -> list[dict]:
    return list(csv.DictReader(io.StringIO(path.read_text(encoding="utf-8-sig"))))


def audit(scratch: Path, before: list[dict], expected: set[str]) -> dict:
    after = csv_rows(scratch / PLAN)
    assets = [r["source_path"].removeprefix(PREFIX) for r in csv_rows(scratch / "legacy-manifest/assets.csv")]
    changes = []
    counts_before, counts_after = Counter(), Counter()
    for asset in assets:
        old = next(r for r in before if fnmatch.fnmatchcase(asset, r["pattern"]))
        new = next(r for r in after if fnmatch.fnmatchcase(asset, r["pattern"]))
        counts_before[old["handling"]] += 1
        counts_after[new["handling"]] += 1
        if old != new:
            assert asset in expected, ("out-of-scope first-match change", asset)
            assert old["handling"] == "REVIEW" and new["handling"] == "EXCLUDE", asset
            assert new["plan"] == "-", asset
            assert all(old[k] == new[k] for k in old if k not in ("handling", "reason", "plan")), asset
            changes.append(dict(asset=asset, before=old, after=new))
    assert {c["asset"] for c in changes} == expected
    assert all(any(fnmatch.fnmatchcase(a, r["pattern"]) for a in assets) for r in after)
    before_allow = dict(line.split("\t") for line in git("show", "HEAD:" + ALLOW, cwd=scratch).decode().splitlines())
    after_allow = dict(line.split("\t") for line in (scratch / ALLOW).read_text().splitlines())
    assert set(before_allow) - set(after_allow) == expected
    assert all(before_allow[a] == after_allow[a] for a in after_allow)
    return dict(result="PASS", enumerated_files=len(assets), changed_files=len(changes), changes=changes,
                unchanged_files=len(assets) - len(changes), before_counts=counts_before, after_counts=counts_after,
                removed_allowlist_entries=len(expected), allowlist_sha256=sha((scratch / ALLOW).read_bytes()))


def apply(scratch: Path) -> dict:
    scratch = scratch.resolve()
    if scratch.exists():
        raise ValueError("Scratch destination must not exist.")
    if scratch.is_relative_to(ROOT.resolve()):
        raise ValueError("Scratch must be outside this worktree.")
    if not scratch.parent.is_dir():
        raise ValueError("Create the external evidence parent first.")
    existing = git("worktree", "list", "--porcelain").decode().splitlines()
    for line in existing:
        if line.startswith("worktree "):
            worktree = Path(line[9:]).resolve()
            if scratch.is_relative_to(worktree):
                raise ValueError("Scratch must not be inside ANY worktree.")
    changes = json.loads((HERE / "changes.json").read_text())
    # Keep the original draft simulation reproducible after the real adoption.
    # This immutable export is not an installer for a later working tree.
    base = changes["source_commit"]
    for path, expected in changes["source_sha256"].items():
        if sha(git("show", base + ":" + path)) != expected:
            raise ValueError("Central baseline changed: " + path)
    source_head = git("rev-parse", base).decode().strip()
    before = list(csv.DictReader(io.StringIO(git("show", base + ":" + PLAN).decode())))
    subprocess.run(["git", "clone", "--shared", "--no-checkout", "--quiet", str(ROOT), str(scratch)], check=True)
    git("update-ref", "HEAD", base, cwd=scratch)
    git("read-tree", base, cwd=scratch)
    data = git("archive", "--format=tar", base)
    with tarfile.open(fileobj=io.BytesIO(data)) as tar:
        tar.extractall(scratch, filter="data")
    assert not git("status", "--porcelain", cwd=scratch).strip(), "Raw LF export must be clean."
    # Include only scoped proposal files if invoked during development before commit.
    dest = scratch / HERE.relative_to(ROOT)
    dest.mkdir(parents=True, exist_ok=True)
    for path in HERE.iterdir():
        if path.is_file():
            (dest / path.name).write_bytes(path.read_bytes())
    for name, target in (("asset-plan.csv.draft", PLAN), ("allowlist.txt.draft", ALLOW), ("origin-findings.json.draft", ORIGIN)):
        (scratch / target).write_bytes((HERE / name).read_bytes())
    adr = (scratch / ADR).read_text(encoding="utf-8")
    adr = re.sub(r"^status: ACCEPTED$", "status: PROPOSED", adr, count=1, flags=re.M)
    adr = re.sub(r"^revision: 6$", "revision: 7\nrevision_7_status: DRAFT_PENDING_OWNER\nrevision_7_prepared_at: 2026-11-03", adr, count=1, flags=re.M)
    adr = re.sub(r"^import_allowlist_sha256: [0-9a-f]{64}$", "import_allowlist_sha256: " + changes["allowlist_after_sha256"], adr, count=1, flags=re.M)
    # Historical revision-6 acceptance remains explicitly historical; no new owner signature.
    adr += "\n## Revision 7 draft - W268 (NOT ACCEPTED)\n\nHistorical acceptance fields above describe revision 6 and earlier, not this draft.\n\n"
    proposal = (HERE / "PROPOSAL.md").read_text(encoding="utf-8")
    start = proposal.index("## Revision 7 (")
    end = proposal.index("## Reference audit method")
    adr += proposal[start:end]
    (scratch / ADR).write_text(adr, encoding="utf-8", newline="\n")
    expected = {a for c in changes["asset_plan"] for a in c["assets"]}
    report = audit(scratch, before, expected)
    report.update(source_commit=source_head, baseline_commit=changes["source_commit"], scratch=str(scratch),
                  acceptance="NONE: ADR-062 revision 7 is PROPOSED; reviewer placeholders are not human findings")
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scratch", type=Path, required=True)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    report = apply(args.scratch)
    encoded = json.dumps(report, indent=2) + "\n"
    if args.report:
        args.report.write_text(encoded, encoding="utf-8")
    print(json.dumps({k: v for k, v in report.items() if k != "changes"}, indent=2))
