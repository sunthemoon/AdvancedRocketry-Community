#!/usr/bin/env python3
"""Validate the v1.8.0 legacy content inventory, content ledger and asset plan.

The checks need only this repository:

* the inventory (``docs/work/v1.8.0-legacy-inventory.json``) agrees with
  ``legacy-manifest`` (source hashes, registered blocks, items, block entities
  and advancements);
* the content ledger gives every inventory unit exactly one disposition, and
  every disposition carries the plan, target and decision its kind requires;
* the asset plan assigns every legacy asset exactly one handling (first
  matching rule), has no dead rule, and agrees with the provenance records of
  files already imported.

``--require-accepted`` additionally fails while any referenced ADR is not
ACCEPTED; release closure (C19) runs with it.
"""

from __future__ import annotations

import argparse
import csv
import fnmatch
import json
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INVENTORY = Path("docs/work/v1.8.0-legacy-inventory.json")
LEDGER = Path("docs/work/v1.8.0-content-ledger.csv")
ASSET_PLAN = Path("docs/work/v1.8.0-asset-plan.csv")
UPSTREAM_ASSET_ROOT = "src/main/resources/assets/advancedrocketry/"
MOD_ID = "advancedrocketrycommunity"

LEDGER_COLUMNS = ["unit_id", "disposition", "plan", "target", "decision", "notes", "evidence"]
ASSET_COLUMNS = ["order", "pattern", "handling", "plan", "reason"]
DISPOSITIONS = ("IMPLEMENTED", "REDESIGNED", "PLANNED", "MERGED", "DEFERRED", "REJECTED")
ASSET_HANDLINGS = ("IMPORTED", "IMPORT", "REVIEW", "REGENERATE", "EXCLUDE")
BATCHES = (
    "C15a", "C15b", "C15c",
    "C16a", "C16b", "C16c", "C16d",
    "C17a", "C17b", "C17c",
    "C18a", "C18b", "C18c", "C18d",
)
REGENERATE_PLANS = ("batch of the owning unit", "batch of the output")
VERSION = re.compile(r"^v(\d+)\.(\d+)\.(\d+)$")
ADR_TOKEN = re.compile(r"^ADR-(\d{3})$")
VERSION_TOKEN = re.compile(r"^V(\d+\.\d+\.\d+)$")
MODERN_ID = re.compile(MOD_ID + r":([a-z0-9_]+)")
CURRENT = (1, 8, 0)
MAX_FIELD = 512


def _version(text: str) -> tuple[int, int, int] | None:
    match = VERSION.match(text)
    return tuple(int(part) for part in match.groups()) if match else None


def read_csv(path: Path, columns: list[str], errors: list[str]) -> list[dict[str, str]]:
    with path.open(encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        if reader.fieldnames != columns:
            errors.append(f"{path.name}: header must be {','.join(columns)}")
            return []
        rows = list(reader)
    for number, row in enumerate(rows, start=2):
        for key, value in row.items():
            if key is None or value is None:
                errors.append(f"{path.name}:{number}: wrong field count")
            elif len(value) > MAX_FIELD or "\n" in value:
                errors.append(f"{path.name}:{number}: field {key} too long or multi-line")
    return rows


def manifest_hashes(root: Path) -> dict[str, str]:
    hashes: dict[str, str] = {}
    for name, column in (("java-files.csv", "path"), ("assets.csv", "source_path")):
        with (root / "legacy-manifest" / name).open(encoding="utf-8", newline="") as handle:
            for row in csv.DictReader(handle):
                hashes[row[column]] = row["sha256"]
    return hashes


def manifest_registrations(root: Path) -> dict[str, set[str]]:
    found: dict[str, set[str]] = {"block": set(), "item": set(), "block_entity": set()}
    with (root / "legacy-manifest/registries.csv").open(encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            declaration = row["declaration"].strip()
            if declaration.startswith("//"):
                continue
            match = re.search(r'registerBlock\(\w+\.\w+\s*\.setRegistryName\("([^"]+)"\)', declaration)
            if match:
                found["block"].add(match.group(1))
            match = re.search(r'registerItem\(\w+\.\w+\.setRegistryName\("([^"]+)"\)', declaration)
            if match:
                found["item"].add(match.group(1))
            match = re.search(r'registerTileEntity\(\w+\.class,\s*(?:new ResourceLocation\(Constants\.modId,\s*)?"([^"]+)"',
                              declaration)
            if match:
                found["block_entity"].add(match.group(1))
    return found


def modern_ids(root: Path) -> set[str]:
    """String literals of the registry classes: every registered modern ID appears there."""
    ids: set[str] = set()
    registry = root / "src/main/java/io/github/sunthemoon" / MOD_ID / "registry"
    for path in sorted(registry.glob("*.java")):
        ids.update(re.findall(r'"([a-z0-9_]+)"', path.read_text(encoding="utf-8")))
    return ids


def adr_statuses(root: Path) -> dict[str, str]:
    statuses: dict[str, str] = {}
    for path in sorted((root / "docs/decisions").glob("ADR-*.md")):
        match = re.match(r"(ADR-\d{3})-", path.name)
        if not match:
            continue
        status = re.search(r"^status:\s*(\w+)", path.read_text(encoding="utf-8"), re.M)
        statuses[match.group(1)] = status.group(1) if status else "UNKNOWN"
    return statuses


def version_documents(root: Path) -> set[str]:
    names = set()
    for path in (root / "docs/versions").glob("V*.md"):
        match = re.match(r"V(\d+\.\d+\.\d+)-", path.name)
        if match:
            names.add(match.group(1))
    return names


def validate_inventory(root: Path, inventory: dict, errors: list[str]) -> list[dict]:
    if inventory.get("schema") != 1:
        errors.append("inventory: schema must be 1")
    commit = (root / "legacy-manifest/UPSTREAM_COMMIT.txt").read_text(encoding="utf-8").strip()
    if inventory.get("upstream_commit") != commit:
        errors.append("inventory: upstream commit differs from legacy-manifest")
    hashes = manifest_hashes(root)
    sources = inventory.get("sources", {})
    for path, digest in sources.items():
        if hashes.get(path) != digest:
            errors.append(f"inventory: source {path} hash differs from legacy-manifest")
    units = inventory.get("units", [])
    identifiers = [unit.get("id") for unit in units]
    for identifier, count in Counter(identifiers).items():
        if count > 1:
            errors.append(f"inventory: duplicate unit {identifier}")
    counts = Counter(unit.get("kind") for unit in units)
    if dict(sorted(counts.items())) != inventory.get("counts"):
        errors.append("inventory: counts do not match the units")
    for unit in units:
        if unit.get("source") not in sources:
            errors.append(f"inventory: {unit.get('id')} source is not a recorded input")
        if unit.get("id") != f"{unit.get('kind')}:{unit.get('legacy_name')}":
            errors.append(f"inventory: {unit.get('id')} does not match its kind and name")
    registered = manifest_registrations(root)
    for kind, names in registered.items():
        listed = {unit["legacy_name"] for unit in units if unit.get("kind") == kind}
        if listed != names:
            errors.append(f"inventory: {kind} units differ from legacy-manifest registrations: "
                          f"missing {sorted(names - listed)[:5]}, extra {sorted(listed - names)[:5]}")
    advancements = {path.rsplit("/", 1)[-1][:-5] for path in hashes
                    if path.startswith(UPSTREAM_ASSET_ROOT + "advancements/") and path.endswith(".json")}
    listed = {unit["legacy_name"] for unit in units if unit.get("kind") == "advancement"}
    if advancements != listed:
        errors.append("inventory: advancement units differ from legacy-manifest assets")
    return units


def validate_ledger(root: Path, units: list[dict], rows: list[dict[str, str]], errors: list[str],
                    require_accepted: bool) -> Counter:
    unit_ids = [unit["id"] for unit in units]
    known = set(unit_ids)
    statuses = adr_statuses(root)
    versions = version_documents(root)
    registered = modern_ids(root)
    by_id: dict[str, dict[str, str]] = {}
    pending_adrs: Counter = Counter()
    for row in rows:
        unit_id = row["unit_id"]
        if unit_id in by_id:
            errors.append(f"ledger: duplicate row {unit_id}")
        by_id[unit_id] = row
        if unit_id not in known:
            errors.append(f"ledger: {unit_id} is not an inventory unit")
    for unit_id in unit_ids:
        if unit_id not in by_id:
            errors.append(f"ledger: {unit_id} has no disposition")
    for unit_id, row in by_id.items():
        disposition, plan, target, decision = row["disposition"], row["plan"], row["target"], row["decision"]
        where = f"ledger: {unit_id}"
        if disposition not in DISPOSITIONS:
            errors.append(f"{where}: disposition {disposition!r} is not one of {DISPOSITIONS}")
            continue
        tokens = decision.split()
        adrs = []
        for token in tokens:
            if ADR_TOKEN.match(token):
                status = statuses.get(token)
                if status is None:
                    errors.append(f"{where}: {token} does not exist")
                elif status not in ("ACCEPTED", "PROPOSED"):
                    errors.append(f"{where}: {token} is {status}")
                else:
                    adrs.append(token)
                    if status != "ACCEPTED":
                        pending_adrs[token] += 1
            elif VERSION_TOKEN.match(token):
                if VERSION_TOKEN.match(token).group(1) not in versions:
                    errors.append(f"{where}: no version document for {token}")
            else:
                errors.append(f"{where}: decision token {token!r} is neither an ADR nor a version document")
        if disposition in ("REDESIGNED", "DEFERRED", "REJECTED", "MERGED", "PLANNED") and not adrs:
            errors.append(f"{where}: {disposition} needs an ADR")
        if row["evidence"] and not (disposition in ("IMPLEMENTED", "REDESIGNED") and plan == "v1.8.0"):
            errors.append(f"{where}: evidence is recorded only for rows delivered in v1.8.0")
        if disposition == "PLANNED":
            if plan not in BATCHES:
                errors.append(f"{where}: PLANNED plan {plan!r} is not a v1.8 batch")
        elif disposition in ("IMPLEMENTED", "REDESIGNED"):
            version = _version(plan)
            if version is None or version > CURRENT:
                errors.append(f"{where}: {disposition} plan {plan!r} must be a version up to v1.8.0")
            elif version == CURRENT:
                evidence = row["evidence"]
                if not evidence.startswith("docs/work/v1.8.0-") or not (root / evidence).is_file():
                    errors.append(f"{where}: {disposition} in v1.8.0 needs the delivering batch's evidence file")
        elif disposition == "DEFERRED":
            if plan != "post-2.0":
                errors.append(f"{where}: DEFERRED plan must be post-2.0")
        elif plan != "-":
            errors.append(f"{where}: {disposition} plan must be '-'")
        if disposition == "MERGED":
            matches = fnmatch.filter(unit_ids, target) if "*" in target else ([target] if target in known else [])
            if not matches:
                errors.append(f"{where}: merge target {target!r} names no unit")
            for match in matches:
                if match == unit_id:
                    errors.append(f"{where}: merges into itself")
                elif by_id.get(match, {}).get("disposition") == "MERGED":
                    errors.append(f"{where}: merge target {match} is itself MERGED")
        elif disposition == "REJECTED" or disposition == "DEFERRED":
            if target != "-":
                errors.append(f"{where}: {disposition} target must be '-'")
        elif not target or target == "-":
            errors.append(f"{where}: {disposition} needs a target")
        for modern in MODERN_ID.findall(target):
            if modern not in registered:
                errors.append(f"{where}: target {MOD_ID}:{modern} is not registered")
        if disposition == "IMPLEMENTED" and unit_id.split(":", 1)[0] in ("block", "item", "item_variant", "entity") \
                and not MODERN_ID.search(target):
            errors.append(f"{where}: IMPLEMENTED content needs a registered modern ID")
    if require_accepted:
        for adr, count in sorted(pending_adrs.items()):
            errors.append(f"ledger: {count} rows depend on {adr}, which is not ACCEPTED")
    return Counter((row["disposition"], row["plan"] if row["disposition"] == "PLANNED" else "") for row in by_id.values())


def provenance_imports(root: Path) -> set[str]:
    imported: set[str] = set()
    for path in sorted((root / "docs/provenance").glob("*.json")):
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            continue
        entries = data.get("entries", []) if isinstance(data, dict) else []
        for entry in entries:
            if not isinstance(entry, dict):
                continue
            source = entry.get("source_path", "")
            repository = entry.get("source_repository", "")
            if isinstance(source, str) and source.startswith(UPSTREAM_ASSET_ROOT) \
                    and "Advanced-Rocketry/AdvancedRocketry" in str(repository):
                imported.add(source[len(UPSTREAM_ASSET_ROOT):])
    return imported


def validate_assets(root: Path, rows: list[dict[str, str]], errors: list[str]) -> Counter:
    hashes = manifest_hashes(root)
    assets = sorted(path[len(UPSTREAM_ASSET_ROOT):] for path in hashes if path.startswith(UPSTREAM_ASSET_ROOT))
    rules = []
    for expected, row in enumerate(rows, start=1):
        where = f"asset plan rule {row['order']}"
        if row["order"] != str(expected):
            errors.append(f"{where}: order must be {expected}")
        if row["handling"] not in ASSET_HANDLINGS:
            errors.append(f"{where}: handling {row['handling']!r} is not one of {ASSET_HANDLINGS}")
        elif row["handling"] in ("IMPORT", "REVIEW") and row["plan"] not in BATCHES:
            errors.append(f"{where}: {row['handling']} plan {row['plan']!r} is not a v1.8 batch")
        elif row["handling"] == "REGENERATE" and row["plan"] not in BATCHES + REGENERATE_PLANS:
            errors.append(f"{where}: REGENERATE plan {row['plan']!r} is not a batch")
        elif row["handling"] == "EXCLUDE" and row["plan"] != "-":
            errors.append(f"{where}: EXCLUDE plan must be '-'")
        elif row["handling"] == "IMPORTED" and _version(row["plan"]) is None:
            errors.append(f"{where}: IMPORTED plan must be the importing version")
        if not row["reason"]:
            errors.append(f"{where}: reason is required")
        rules.append(row)
    used: Counter = Counter()
    handling_of: dict[str, str] = {}
    for asset in assets:
        for index, rule in enumerate(rules):
            if fnmatch.fnmatchcase(asset, rule["pattern"]):
                used[index] += 1
                handling_of[asset] = rule["handling"]
                break
        else:
            errors.append(f"asset plan: {asset} matches no rule")
    for index, rule in enumerate(rules):
        if used[index] == 0:
            errors.append(f"asset plan rule {rule['order']}: pattern {rule['pattern']!r} matches no asset")
    imported = provenance_imports(root)
    for asset in sorted(imported):
        if handling_of.get(asset) != "IMPORTED":
            errors.append(f"asset plan: {asset} has a provenance record but is not handled as IMPORTED")
    for asset, handling in sorted(handling_of.items()):
        if handling == "IMPORTED" and asset not in imported:
            errors.append(f"asset plan: {asset} is handled as IMPORTED without a provenance record")
    return Counter(handling_of.values())


def validate(root: Path, require_accepted: bool = False) -> tuple[dict, list[str]]:
    errors: list[str] = []
    inventory = json.loads((root / INVENTORY).read_text(encoding="utf-8"))
    units = validate_inventory(root, inventory, errors)
    ledger_rows = read_csv(root / LEDGER, LEDGER_COLUMNS, errors)
    dispositions = validate_ledger(root, units, ledger_rows, errors, require_accepted)
    asset_rows = read_csv(root / ASSET_PLAN, ASSET_COLUMNS, errors)
    handlings = validate_assets(root, asset_rows, errors)
    summary = {
        "units": len(units),
        "dispositions": dict(sorted(Counter(key[0] for key in dispositions.elements()).items())),
        "planned_by_batch": dict(sorted((key[1], count) for key, count in dispositions.items() if key[0] == "PLANNED")),
        "assets": dict(sorted(handlings.items())),
        "result": "FAIL" if errors else "PASS",
    }
    return summary, errors


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--require-accepted", action="store_true")
    arguments = parser.parse_args(argv)
    summary, errors = validate(arguments.root, arguments.require_accepted)
    for error in errors[:200]:
        print(error, file=sys.stderr)
    print(json.dumps(summary, indent=2, sort_keys=True))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
