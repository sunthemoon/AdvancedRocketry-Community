#!/usr/bin/env python3
"""Read-only check of the W47 C18d asset closure audit (AUDIT-01.csv and AUDIT-01.md).

It writes nothing. It checks that every input row of the revision-3 asset coverage appears exactly once, in order,
with its input columns unchanged; that every SHA-256 the audit cites matches the file on disk; that every cited
``path@commit`` names the commit that added that file; and that the cited plan, allowlist, derivation and provenance
facts match the committed files. Run from the repository root:

    python -B docs/work/v1.8.0-c18d-asset-closure/check_audit_01.py [--upstream <upstream/ar tree at c5cd5af>]
"""

from __future__ import annotations

import argparse
import csv
import fnmatch
import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
COVERAGE = "docs/work/v1.8.0-c18-contract/proposals/revision-03/asset-coverage.csv"
PLAN = "docs/work/v1.8.0-asset-plan.csv"
ALLOWLIST = "docs/work/v1.8.0-asset-import-allowlist.txt"
DERIVATION = "docs/work/v1.8.0-vanilla-derivation.json"
FINDINGS = "docs/provenance/v1.8.0-origin-findings.json"
RECORD = "docs/provenance/v1.8.0-c18d-presentation.json"
ADR061 = "docs/decisions/ADR-061-CLASSIC-CONTENT-IDENTITY-IMPORT-AND-VALIDATION.md"
ADR062 = "docs/decisions/ADR-062-CLASSIC-CONTENT-DISPOSITIONS-AND-BATCHES.md"
PREFIX = "src/main/resources/assets/advancedrocketry/"
NEW_COLUMNS = ["class", "runtime_target", "runtime_sha256", "evidence", "missing"]
CITATION = re.compile(r"([A-Za-z0-9_./-]+\.[A-Za-z0-9]+)@([0-9a-f]{8})\b")


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def rows(relative: str) -> list[dict[str, str]]:
    with open(ROOT / relative, encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


def git(*args: str) -> str:
    return subprocess.run(["git", "-C", str(ROOT), *args], capture_output=True, text=True, check=False).stdout


def pinned(relative: str, key: str) -> str:
    match = re.search(rf"^{key}:\s*([0-9a-f]{{64}})\s*$", (ROOT / relative).read_text(encoding="utf-8"), re.M)
    return match.group(1) if match else ""


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--upstream", type=Path, help="optional read-only upstream tree to re-hash the sources")
    arguments = parser.parse_args()
    errors: list[str] = []

    source = rows(COVERAGE)
    audit = rows(str((HERE / "AUDIT-01.csv").relative_to(ROOT)))
    input_columns = list(source[0].keys())
    with open(HERE / "AUDIT-01.csv", encoding="utf-8", newline="") as handle:
        header = next(csv.reader(handle))
    if header != input_columns + NEW_COLUMNS:
        errors.append(f"header differs: {header}")

    # 1. Every input row exactly once, in the same order, with unchanged input columns.
    paths = [row["source_path"] for row in audit]
    for path in {p for p in paths if paths.count(p) != 1}:
        errors.append(f"{path} appears {paths.count(path)} times")
    if len(audit) != len(source):
        errors.append(f"{len(audit)} audit rows for {len(source)} input rows")
    for index, (given, row) in enumerate(zip(source, audit)):
        changed = [column for column in input_columns if given[column] != row[column]]
        if changed:
            errors.append(f"row {index + 1} ({given['source_path']}): input columns changed or out of order: {changed}")

    plan = rows(PLAN)
    allowlist = dict(line.rstrip("\n").split("\t") for line in open(ROOT / ALLOWLIST, encoding="utf-8") if line.strip())
    derivation = {asset["asset"]: asset for asset in json.loads((ROOT / DERIVATION).read_text(encoding="utf-8"))["assets"]}
    excluded = {f["asset"] for f in json.loads((ROOT / FINDINGS).read_text(encoding="utf-8"))["findings"]
                if f.get("decision") == "EXCLUDED"}
    manifest = {row["source_path"]: row["sha256"] for row in rows("legacy-manifest/assets.csv")}
    record = {entry["target_path"]: entry for entry in json.loads((ROOT / RECORD).read_text(encoding="utf-8"))["entries"]}

    def first_match(asset: str) -> dict[str, str] | None:
        return next((rule for rule in plan if fnmatch.fnmatchcase(asset, rule["pattern"])), None)

    # 2. The pinned inputs the audit reads are the accepted ones.
    if sha256(ROOT / ALLOWLIST) != pinned(ADR062, "import_allowlist_sha256"):
        errors.append("allowlist SHA-256 differs from the ADR-062 pin")
    if sha256(ROOT / DERIVATION) != pinned(ADR061, "derivation_results_sha256"):
        errors.append("derivation results SHA-256 differs from the ADR-061 pin")

    # 3. No file under src/ carries a source hash (nothing is imported).
    source_hashes = {row["source_sha256"] for row in source}
    for path in (ROOT / "src").rglob("*"):
        if path.is_file() and sha256(path) in source_hashes:
            errors.append(f"{path.relative_to(ROOT).as_posix()} has the SHA-256 of an audited source")

    citations = 0
    added_cache: dict[str, str] = {}
    counts: dict[str, int] = {}
    for row in audit:
        asset = row["source_path"].removeprefix(PREFIX)
        name, cls, target, digest = row["source_path"], row["class"], row["runtime_target"], row["runtime_sha256"]
        counts[cls] = counts.get(cls, 0) + 1
        if cls not in "ABCDEF" or len(cls) != 1:
            errors.append(f"{name}: class {cls!r}")
        if not row["evidence"].strip() or not row["missing"].strip():
            errors.append(f"{name}: empty evidence or missing")
        if (cls == "A") != (row["missing"] == "none"):
            errors.append(f"{name}: only class A rows may have nothing missing")
        expected_handling = {"B": "IMPORT", "D": "REVIEW", "F": "REVIEW"}.get(cls)
        if expected_handling and row["handling"] != expected_handling:
            errors.append(f"{name}: class {cls} needs handling {expected_handling}")

        # 4. Every SHA-256 cited matches the file on disk.
        if manifest.get(row["source_path"]) != row["source_sha256"]:
            errors.append(f"{name}: source_sha256 differs from legacy-manifest/assets.csv")
        if arguments.upstream is not None:
            upstream = arguments.upstream / row["source_path"]
            if not upstream.is_file() or sha256(upstream) != row["source_sha256"]:
                errors.append(f"{name}: source_sha256 differs from the upstream tree")
        if (target == "-") != (digest == "-"):
            errors.append(f"{name}: runtime_target and runtime_sha256 must both be '-' or both set")
        elif target != "-":
            if not (ROOT / target).is_file():
                errors.append(f"{name}: runtime target {target} is missing")
            elif sha256(ROOT / target) != digest:
                errors.append(f"{name}: runtime_sha256 differs from {target}")
        if cls == "C":
            entry = record.get(target)
            if entry is None or entry["target_sha256"] != digest or entry["status"] != "NEW":
                errors.append(f"{name}: no NEW entry with this target hash in {RECORD}")

        # 5. Every path@commit names the commit that added the file, and the file still exists.
        for path, commit in CITATION.findall(row["evidence"] + " " + row["missing"]):
            citations += 1
            if path not in added_cache:
                log = git("log", "--diff-filter=A", "--format=%h", "--abbrev=8", "--", path).split()
                added_cache[path] = log[-1] if log else ""
            if added_cache[path] != commit:
                errors.append(f"{name}: {path}@{commit} but the adding commit is {added_cache[path] or 'none'}")
            if not (ROOT / path).is_file():
                errors.append(f"{name}: cited {path} is missing")

        # 6. Cited plan, allowlist and derivation facts match the committed files.
        rule = first_match(asset)
        derived = derivation.get(asset, {})
        stated = f"plan rule {rule['order']} {rule['pattern']} {rule['handling']} " if rule else "?"
        if not rule or rule["pattern"] != row["rule_pattern"] or rule["handling"] != row["handling"] or \
                not row["evidence"].startswith(stated):
            errors.append(f"{name}: evidence does not cite the current first-match plan rule")
        if f"allowlist {allowlist.get(asset, 'not listed')};" not in row["evidence"]:
            errors.append(f"{name}: evidence misstates the allowlist ceiling")
        if f"derivation {derived.get('verdict')}" not in row["evidence"] or derived.get("sha256") != row["source_sha256"]:
            errors.append(f"{name}: evidence misstates the derivation result")

        # 7. Class B is import-ready only under the allowlist, a CLEAR verdict and no inherited review.
        if cls == "B":
            if allowlist.get(asset) != "IMPORT" or derived.get("verdict") != "CLEAR" or asset in excluded:
                errors.append(f"{name}: class B without IMPORT allowlist, CLEAR verdict and no EXCLUDED finding")
            for related in derived.get("related") or []:
                other = related["asset"] if isinstance(related, dict) else related
                other_rule = first_match(other)
                if derivation.get(other, {}).get("verdict") != "CLEAR" or other in excluded or \
                        (other_rule and other_rule["handling"] == "REVIEW"):
                    errors.append(f"{name}: related {other} would make it REVIEW at most (ADR-061 section 4.8)")

    # 8. The class counts in AUDIT-01.md equal the CSV.
    stated_counts = {m.group(1): int(m.group(2)) for m in
                     re.finditer(r"^\| ([A-F]) \| [^|]+\| (\d+) \|", (HERE / "AUDIT-01.md").read_text(encoding="utf-8"), re.M)}
    for cls in "ABCDEF":
        if stated_counts.get(cls) != counts.get(cls, 0):
            errors.append(f"AUDIT-01.md states {stated_counts.get(cls)} class {cls} rows; the CSV has {counts.get(cls, 0)}")

    if errors:
        for error in errors:
            print(f"[FAIL] {error}")
        print(f"[FAIL] {len(errors)} problems")
        return 1
    summary = ", ".join(f"{cls} {counts.get(cls, 0)}" for cls in "ABCDEF")
    print(f"[PASS] {len(audit)} rows, each input row once and in order; classes: {summary}")
    print(f"[PASS] runtime and source SHA-256 values match the files on disk"
          + ("" if arguments.upstream is None else " and the upstream tree"))
    print(f"[PASS] {citations} path@commit citations name the adding commit; plan, allowlist, derivation and record facts match")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
