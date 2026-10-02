#!/usr/bin/env python3
"""Validate the v1.8.0 legacy content inventory, content ledger and asset plan.

The checks need only this repository:

* the inventory (``docs/work/v1.8.0-legacy-inventory.json``) agrees with
  ``legacy-manifest`` (source hashes, registered blocks, items, block entities
  and advancements);
* the content ledger gives every inventory unit exactly one disposition, and
  every disposition carries the plan, target and decision its kind requires;
* the asset plan assigns every legacy asset exactly one handling (first
  matching rule), has no dead rule, names existing owning units, keeps every
  importable asset inside the import allowlist pinned by ADR-062, and agrees
  with the provenance records of files already imported and with the recorded
  origin findings;
* every asset the plan would import has the derivation verdict ``CLEAR`` in
  the committed vanilla-derivation results (``HIT`` assets are never imported
  or reviewed, ``SUSPECT`` and ``UNSUPPORTED`` assets import only after a
  ``CLEARED`` origin finding);
* the derivation results match the SHA-256 pinned in ADR-061, name the two
  vanilla client JARs by the SHA-256 values pinned there (and in
  ``tools/audit/fetch_vanilla_clients.py``), use the thresholds of
  ``tools/audit/vanilla_derivation.py``, and give every asset the verdict its
  recorded measures produce. CI regenerates the results with ``--check``.

The inventory's own content (lines, display names, non-registry units) is
checked by ``tools/audit/inventory_v180_content.py --check`` against the
upstream tree; CI runs both.

``--require-accepted`` additionally fails while any referenced ADR is not
ACCEPTED. ``--closure`` (C19) implies it and also fails on any PLANNED row or
any asset still handled as REVIEW.
"""

from __future__ import annotations

import argparse
import csv
import fnmatch
import hashlib
import importlib.util
import json
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INVENTORY = Path("docs/work/v1.8.0-legacy-inventory.json")
LEDGER = Path("docs/work/v1.8.0-content-ledger.csv")
ASSET_PLAN = Path("docs/work/v1.8.0-asset-plan.csv")
ALLOWLIST = Path("docs/work/v1.8.0-asset-import-allowlist.txt")
ORIGIN_FINDINGS = Path("docs/provenance/v1.8.0-origin-findings.json")
DERIVATION = Path("docs/work/v1.8.0-vanilla-derivation.json")
PLAYER_IMPACT_ADR = "ADR-062"
PLAYER_IMPACT_PATH = Path("docs/decisions/ADR-062-CLASSIC-CONTENT-DISPOSITIONS-AND-BATCHES.md")
DERIVATION_ADR_PATH = Path("docs/decisions/ADR-061-CLASSIC-CONTENT-IDENTITY-IMPORT-AND-VALIDATION.md")
DERIVATION_TOOL = Path("tools/audit/vanilla_derivation.py")
CLIENT_FETCHER = Path("tools/audit/fetch_vanilla_clients.py")
VANILLA_CLIENTS = ("1.12.2", "1.20.1")
VERDICTS = ("HIT", "SUSPECT", "CLEAR", "UNSUPPORTED")
UPSTREAM_ASSET_ROOT = "src/main/resources/assets/advancedrocketry/"
MOD_ID = "advancedrocketrycommunity"

LEDGER_COLUMNS = ["unit_id", "disposition", "plan", "target", "decision", "notes", "evidence"]
ASSET_COLUMNS = ["order", "pattern", "handling", "plan", "units", "reason"]
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
# A v1.8 delivery names its batch's evidence folder, for example docs/work/v1.8.0-c16c-machines/VERIFICATION.md.
EVIDENCE_PATH = re.compile(r"^docs/work/v1\.8\.0-(c1[5-8][a-d])-[a-z0-9-]+/[A-Za-z0-9_.-]+\.md$")
ADR_TOKEN = re.compile(r"^ADR-(\d{3})$")
VERSION_TOKEN = re.compile(r"^V(\d+\.\d+\.\d+)$")
MODERN_ID = re.compile(MOD_ID + r":([a-z0-9_]+)")
DATE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
# Kinds whose IMPLEMENTED rows must name a modern ID registered in the matching family.
ID_FAMILIES = {
    "block": ("block", "item"), "block_variant": ("block", "item"), "item": ("item",), "item_variant": ("item",),
    "entity": ("entity",), "fluid": ("fluid",), "enchantment": ("enchantment",), "sound_event": ("sound",),
    "biome": ("biome",),
}
CURRENT = (1, 8, 0)
MAX_FIELD = 512


def _token(identifier: str) -> re.Pattern:
    """An ID as a whole token: block:lathe does not match block:latheX or xblock:lathe."""
    return re.compile(r"(?<![A-Za-z0-9_:])" + re.escape(identifier) + r"(?![A-Za-z0-9_])")


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


def resource_roots(root: Path) -> list[Path]:
    roots = [root / "src/main/resources", root / "src/generated/resources"]
    roots.extend(sorted((root / "src/generated").glob("v*/resources")))
    return [path for path in roots if path.is_dir()]


def registered_ids(root: Path) -> dict[str, set[str]]:
    """Registered modern IDs by family, read from the generated resources.

    Every registered block, item, entity, enchantment and fluid type has an
    English name; sound events are keys of sounds.json; biomes are data files.
    """
    families: dict[str, set[str]] = {name: set() for name in ("block", "item", "entity", "enchantment", "fluid",
                                                               "sound", "biome")}
    prefixes = {"block": "block", "item": "item", "entity": "entity", "enchantment": "enchantment",
                "fluid_type": "fluid"}
    for base in resource_roots(root):
        # DataGen writes each version's language file into its own pseudo-namespace.
        for lang in sorted(base.glob(f"assets/{MOD_ID}*/lang/en_us.json")):
            for key in json.loads(lang.read_text(encoding="utf-8")):
                parts = key.split(".")
                if len(parts) == 3 and parts[1] == MOD_ID and parts[0] in prefixes:
                    families[prefixes[parts[0]]].add(parts[2])
        sounds = base / "assets" / MOD_ID / "sounds.json"
        if sounds.is_file():
            families["sound"].update(json.loads(sounds.read_text(encoding="utf-8")))
        biomes = base / "data" / MOD_ID / "worldgen" / "biome"
        if biomes.is_dir():
            families["biome"].update(path.stem for path in biomes.glob("*.json"))
    return families


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
        if unit.get("source") == "curated":
            if unit.get("kind") != "libvulpes" or not unit.get("reason"):
                errors.append(f"inventory: {unit.get('id')} is curated without a LibVulpes kind and a reason")
        elif unit.get("source") not in sources:
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
    families = registered_ids(root)
    registered = set().union(*families.values())
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
        if disposition == "REDESIGNED" and not [token for token in tokens if token != PLAYER_IMPACT_ADR]:
            errors.append(f"{where}: REDESIGNED needs the ADR or version document that delivered the mechanism")
        if disposition in ("DEFERRED", "REJECTED") and PLAYER_IMPACT_ADR not in tokens:
            errors.append(f"{where}: {disposition} needs {PLAYER_IMPACT_ADR}, which records the player impact")
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
                if not EVIDENCE_PATH.match(evidence) or not (root / evidence).is_file():
                    errors.append(f"{where}: {disposition} in v1.8.0 needs the delivering batch's evidence file")
                else:
                    text = (root / evidence).read_text(encoding="utf-8")
                    if not _token(unit_id).search(text):
                        errors.append(f"{where}: evidence file {evidence} does not list the unit")
                    for modern in MODERN_ID.findall(target):
                        if not _token(f"{MOD_ID}:{modern}").search(text):
                            errors.append(f"{where}: evidence file {evidence} does not list {MOD_ID}:{modern}")
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
        kind = unit_id.split(":", 1)[0]
        if disposition == "IMPLEMENTED" and kind in ID_FAMILIES:
            wanted = set().union(*(families[family] for family in ID_FAMILIES[kind]))
            if not any(modern in wanted for modern in MODERN_ID.findall(target)):
                errors.append(f"{where}: IMPLEMENTED content needs a registered modern "
                              f"{'/'.join(ID_FAMILIES[kind])} ID")
    if require_accepted:
        for adr, count in sorted(pending_adrs.items()):
            errors.append(f"ledger: {count} rows depend on {adr}, which is not ACCEPTED")
    counter = Counter((row["disposition"], row["plan"] if row["disposition"] == "PLANNED" else "")
                      for row in by_id.values())
    return counter, by_id


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


def pinned_allowlist_digest(root: Path) -> str | None:
    match = re.search(r"^import_allowlist_sha256:\s*([0-9a-f]{64})\s*$",
                      (root / PLAYER_IMPACT_PATH).read_text(encoding="utf-8"), re.M)
    return match.group(1) if match else None


def load_allowlist(root: Path, errors: list[str]) -> dict[str, str]:
    data = (root / ALLOWLIST).read_bytes()
    if pinned_allowlist_digest(root) != hashlib.sha256(data).hexdigest():
        errors.append(f"asset plan: {ALLOWLIST.name} does not match the digest pinned in {PLAYER_IMPACT_ADR}")
    allowed: dict[str, str] = {}
    for number, line in enumerate(data.decode("utf-8").splitlines(), start=1):
        asset, _, handling = line.partition("\t")
        if handling not in ("IMPORT", "REVIEW") or not asset or asset in allowed:
            errors.append(f"{ALLOWLIST.name}:{number}: expected a unique asset, a tab and IMPORT or REVIEW")
            continue
        allowed[asset] = handling
    return allowed


def adr_owner(root: Path) -> str | None:
    match = re.search(r"^owner:\s*(\S+)\s*$", (root / PLAYER_IMPACT_PATH).read_text(encoding="utf-8"), re.M)
    return match.group(1) if match else None


def load_origin_findings(root: Path, errors: list[str]) -> tuple[dict[str, str], set[str]]:
    """Decisions by asset, and the assets whose HIT verdict the owner overturned (ADR-061 section 4.8)."""
    data = json.loads((root / ORIGIN_FINDINGS).read_text(encoding="utf-8"))
    owner = adr_owner(root)
    findings: dict[str, str] = {}
    overturned: set[str] = set()
    if data.get("schema_version") != 1 or not isinstance(data.get("findings"), list):
        errors.append(f"{ORIGIN_FINDINGS.name}: schema_version 1 with a findings list required")
        return findings, overturned
    for finding in data["findings"]:
        asset = finding.get("asset")
        if (finding.get("decision") not in ("CLEARED", "EXCLUDED") or not finding.get("reviewer")
                or not DATE.match(str(finding.get("reviewed_at", ""))) or not finding.get("basis")
                or not asset or asset in findings):
            errors.append(f"{ORIGIN_FINDINGS.name}: invalid or duplicate finding {asset!r}")
            continue
        if finding["decision"] == "CLEARED" and finding["reviewer"] != owner:
            # Anyone else clears a file only as a named independent reviewer with a committed review record.
            record = finding.get("review_record")
            if (finding.get("role") != "independent reviewer" or not isinstance(record, str)
                    or not record.startswith("docs/") or not (root / record).is_file()
                    or asset not in (root / record).read_text(encoding="utf-8")):
                errors.append(f"{ORIGIN_FINDINGS.name}: {asset} is CLEARED by {finding['reviewer']!r}, who is neither "
                              f"the owner nor an independent reviewer with a review record naming the asset")
                continue
        if "overrides" in finding:
            confirmed, record = finding.get("confirmed_by"), finding.get("confirmation_record")
            if (finding["overrides"] != "HIT" or finding["decision"] != "CLEARED" or finding["reviewer"] != owner
                    or not isinstance(confirmed, str) or not confirmed or confirmed == finding["reviewer"]
                    or not isinstance(record, str) or not record.startswith("docs/") or not (root / record).is_file()
                    or asset not in (root / record).read_text(encoding="utf-8")
                    or confirmed not in (root / record).read_text(encoding="utf-8")):
                errors.append(f"{ORIGIN_FINDINGS.name}: {asset} overrides a verdict without the owner's CLEARED "
                              f"decision and a second person's confirmation")
                continue
            overturned.add(asset)
        findings[asset] = finding["decision"]
    return findings, overturned


def _repository_module(relative: Path):
    """The tool code under test always comes from this checkout, never from a fixture root."""
    spec = importlib.util.spec_from_file_location("arce_" + relative.stem, ROOT / relative)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def load_derivation(root: Path, errors: list[str]) -> tuple[dict[str, str], dict[str, list[tuple[str, str]]]]:
    """Verdicts by asset, and the legacy files each asset shares pixels with (ADR-061 section 4.8)."""
    raw = (root / DERIVATION).read_bytes()
    adr = (root / DERIVATION_ADR_PATH).read_text(encoding="utf-8")
    where = DERIVATION.name
    pinned = re.search(r"^derivation_results_sha256:\s*([0-9a-f]{64})\s*$", adr, re.M)
    if not pinned or pinned.group(1) != hashlib.sha256(raw).hexdigest():
        errors.append(f"{where}: does not match the digest pinned in ADR-061")
    data = json.loads(raw)
    tool = _repository_module(DERIVATION_TOOL)
    fetcher = _repository_module(CLIENT_FETCHER)
    if data.get("schema") != tool.SCHEMA or data.get("generator") != DERIVATION_TOOL.as_posix():
        errors.append(f"{where}: schema {tool.SCHEMA} from {DERIVATION_TOOL.as_posix()} required")
    if data.get("thresholds") != tool.thresholds():
        errors.append(f"{where}: thresholds differ from {DERIVATION_TOOL.as_posix()}")
    clients = data.get("vanilla") if isinstance(data.get("vanilla"), dict) else {}
    if sorted(clients) != sorted(VANILLA_CLIENTS):
        errors.append(f"{where}: must compare against exactly the clients {', '.join(VANILLA_CLIENTS)}")
    for label in VANILLA_CLIENTS:
        pin = re.search(r'^\s+"' + re.escape(label) + r'":\s*([0-9a-f]{64})\s*$', adr, re.M)
        recorded = (clients.get(label) or {}).get("sha256")
        if not pin or pin.group(1) != recorded or fetcher.CLIENTS.get(label, {}).get("sha256") != recorded:
            errors.append(f"{where}: the {label} client SHA-256 differs from the pins in ADR-061 and "
                          f"{CLIENT_FETCHER.as_posix()}")
    verdicts: dict[str, str] = {}
    related: dict[str, list[tuple[str, str]]] = {}
    digests = {entry.get("asset"): entry.get("sha256") for entry in data.get("assets", [])}
    for entry in data.get("assets", []):
        asset, level = entry.get("asset"), entry.get("verdict")
        if level not in VERDICTS or not asset or asset in verdicts:
            errors.append(f"{where}: invalid or duplicate entry {asset!r}")
            continue
        verdicts[asset] = level
        for match in entry.get("related", []):
            other, relation = match.get("asset"), match.get("level")
            if other not in digests or other == asset or relation not in ("HIT", "SUSPECT"):
                errors.append(f"{where}: {asset} has an invalid related entry {other!r}")
                continue
            if match.get("method") == "hash":
                agrees = relation == "HIT" and digests[other] == entry.get("sha256")
            else:
                try:
                    agrees = tool.verdict(match) == relation
                except (KeyError, TypeError):
                    agrees = False
            if not agrees:
                errors.append(f"{where}: {asset} lists {other} as {relation}, which its recorded measures do not give")
                continue
            related.setdefault(asset, []).append((other, relation))
        if "hash_match" in entry:
            expected = "HIT"
        elif isinstance(entry.get("best"), dict):
            try:
                expected = tool.verdict(entry["best"])
            except (KeyError, TypeError):
                expected = "invalid measures"
        else:
            expected = level if level in ("CLEAR", "UNSUPPORTED") else "CLEAR or UNSUPPORTED"
        if level != expected:
            errors.append(f"{where}: {asset} is {level} but its recorded measures give {expected}")
    if dict(sorted(Counter(verdicts.values()).items())) != data.get("counts"):
        errors.append(f"{where}: counts do not match the entries")
    return verdicts, related


def validate_assets(root: Path, rows: list[dict[str, str]], errors: list[str],
                    ledger: dict[str, dict[str, str]]) -> Counter:
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
        owners = [] if row["units"] == "-" else row["units"].split()
        for owner in owners:
            if owner not in ledger:
                errors.append(f"{where}: owning unit {owner} is not in the ledger")
            elif row["handling"] in ("IMPORT", "REVIEW") and ledger[owner]["disposition"] in ("DEFERRED", "REJECTED"):
                errors.append(f"{where}: {row['handling']} for {owner}, which is {ledger[owner]['disposition']}")
            elif (row["handling"] in ("IMPORT", "REVIEW") and row["plan"] in BATCHES
                  and ledger[owner]["disposition"] == "PLANNED" and ledger[owner]["plan"] in BATCHES
                  and BATCHES.index(ledger[owner]["plan"]) > BATCHES.index(row["plan"])):
                errors.append(f"{where}: owning unit {owner} is delivered in {ledger[owner]['plan']}, "
                              f"after the rule's batch {row['plan']}")
        rules.append(row)
    # A model's textures must not land later than the model: compare rules that share an owning unit.
    model_batches: dict[str, int] = {}
    for row in rules:
        if (row["handling"] in ("IMPORT", "REVIEW") and row["plan"] in BATCHES and row["pattern"].startswith("models/")
                and not row["pattern"].endswith(".json")):
            for owner in ([] if row["units"] == "-" else row["units"].split()):
                model_batches[owner] = min(model_batches.get(owner, len(BATCHES)), BATCHES.index(row["plan"]))
    for row in rules:
        if (row["handling"] in ("IMPORT", "REVIEW") and row["plan"] in BATCHES
                and row["pattern"].startswith("textures/models/")):
            for owner in ([] if row["units"] == "-" else row["units"].split()):
                if owner in model_batches and BATCHES.index(row["plan"]) > model_batches[owner]:
                    errors.append(f"asset plan rule {row['order']}: model texture for {owner} lands in {row['plan']}, "
                                  f"after its model ({BATCHES[model_batches[owner]]})")
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
    allowed = load_allowlist(root, errors)
    findings, overturned = load_origin_findings(root, errors)
    verdicts, related = load_derivation(root, errors)
    if set(verdicts) != set(assets):
        errors.append(f"{DERIVATION.name}: does not cover exactly the legacy assets")
    for asset, handling in sorted(handling_of.items()):
        verdict = verdicts.get(asset)
        if handling in ("IMPORT", "REVIEW") and verdict == "HIT" and asset not in overturned:
            errors.append(f"asset plan: {asset} is vanilla-derived (HIT) but handled as {handling}")
        elif handling == "IMPORT" and verdict != "CLEAR" and findings.get(asset) != "CLEARED":
            errors.append(f"asset plan: {asset} has derivation verdict {verdict} and no CLEARED origin finding")
        ceiling = allowed.get(asset)
        cleared = ceiling == "REVIEW" and findings.get(asset) == "CLEARED"
        if handling == "IMPORT" and not (ceiling == "IMPORT" or cleared):
            errors.append(f"asset plan: {asset} is IMPORT beyond its allowlist ceiling {ceiling or 'none'}")
        elif handling == "REVIEW" and ceiling is None:
            errors.append(f"asset plan: {asset} is REVIEW but not in the import allowlist")
        if findings.get(asset) == "EXCLUDED" and handling != "EXCLUDE":
            errors.append(f"asset plan: {asset} was excluded by an origin finding but is {handling}")
        if handling == "IMPORT" and findings.get(asset) != "CLEARED":
            # ADR-061 section 4.8: a file sharing pixels with a derived, suspect or quarantined file inherits review.
            for other, relation in related.get(asset, []):
                if findings.get(other) == "CLEARED":
                    continue  # a cleared source passes nothing on
                reason = (f"derivation verdict {verdicts[other]}" if verdicts.get(other) in ("HIT", "SUSPECT", "UNSUPPORTED")
                          else "REVIEW" if handling_of.get(other) == "REVIEW"
                          else "EXCLUDED origin finding" if findings.get(other) == "EXCLUDED" else None)
                if reason:
                    errors.append(f"asset plan: {asset} is IMPORT but shares pixels with {other} ({relation}), "
                                  f"whose {reason} it inherits (ADR-061 section 4.8)")
                    break
    return Counter(handling_of.values())


def validate(root: Path, require_accepted: bool = False, closure: bool = False) -> tuple[dict, list[str]]:
    errors: list[str] = []
    inventory = json.loads((root / INVENTORY).read_text(encoding="utf-8"))
    units = validate_inventory(root, inventory, errors)
    ledger_rows = read_csv(root / LEDGER, LEDGER_COLUMNS, errors)
    dispositions, ledger = validate_ledger(root, units, ledger_rows, errors, require_accepted or closure)
    asset_rows = read_csv(root / ASSET_PLAN, ASSET_COLUMNS, errors)
    handlings = validate_assets(root, asset_rows, errors, ledger)
    if closure:
        planned = sum(count for key, count in dispositions.items() if key[0] == "PLANNED")
        if planned:
            errors.append(f"closure: {planned} ledger rows are still PLANNED")
        if handlings.get("REVIEW"):
            errors.append(f"closure: {handlings['REVIEW']} assets are still handled as REVIEW")
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
    parser.add_argument("--closure", action="store_true", help="C19: accepted ADRs, no PLANNED row, no REVIEW asset")
    arguments = parser.parse_args(argv)
    summary, errors = validate(arguments.root, arguments.require_accepted, arguments.closure)
    for error in errors[:200]:
        print(error, file=sys.stderr)
    print(json.dumps(summary, indent=2, sort_keys=True))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
