#!/usr/bin/env python3
"""Generate or verify a provenance-bound v1.8 asset batch (ADR-061 sections 4.3 to 4.9, ADR-066 section 7.3).

    python -B tools/import/import_v180_assets.py generate --upstream <Advanced Rocketry clone at the UPSTREAM.md
        commit> --plan tools/import/v180-<batch>-plan.json --record docs/provenance/v1.8.0-c18d-<batch>.json
        --vanilla 1.12.2=<client.jar> --vanilla 1.20.1=<client.jar>

``verify`` takes the same arguments, recomputes every target and the record and fails on any byte difference.
``generate`` runs every check first and writes nothing unless all of them pass.

Each plan entry names its source (the pinned upstream, or a repository-owned NEW file at its authoring commit),
the source path and SHA-256, the target path, one transformation, the owning ledger units and its row in the C18d
asset-coverage table. Transformations:

* ``copy``: the bytes unchanged, at the same asset path in the mod namespace;
* ``rename``: the bytes unchanged, at a new asset path;
* ``obj`` and ``mtl``: the bytes unchanged except the ``mtllib`` and ``usemtl`` (OBJ) or texture map (MTL)
  references the plan maps; every reference must be mapped and every mapping used;
* ``lang``: legacy ``.lang`` keys extracted to the modern keys the plan names; every legacy key is mapped or
  listed as discarded, and the output is sorted two-space JSON without a trailing newline (Forge's
  LanguageProvider format).

Checks for every entry: the source SHA-256 (plan, legacy manifest and coverage row), the owning units, the
derivation re-run with inheritance and the upstream history (``v180_import_checks``), and the OBJ/MTL format,
budget and reference check over the whole plan (``tools/audit/obj_mtl_check.py``).
"""

from __future__ import annotations

import argparse
import csv
import importlib
import json
import os
import re
import sys
from pathlib import Path
from typing import Any

REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from tools.audit import obj_mtl_check as models  # noqa: E402
from tools.audit.audit_upstream import load_tracked_files  # noqa: E402

checks = importlib.import_module("tools.import.v180_import_checks")
v010 = importlib.import_module("tools.import.import_v010_assets")

MOD_ID = models.MOD_ID
TARGET_VERSION = "v1.8.0"
GENERATOR = "tools/import/import_v180_assets.py"
RESOURCE_ROOT = "src/main/resources/"
TARGET_ROOT = RESOURCE_ROOT + "assets/" + MOD_ID + "/"
UPSTREAM = "UPSTREAM_AR_MIT"
NEW = "NEW"
UPSTREAM_NOTICE = "Copyright (c) 2017"
NEW_REPOSITORY = "https://github.com/sunthemoon/AdvancedRocketry-Community"
SOURCE_ROOTS = {UPSTREAM: checks.LEGACY_ASSET_ROOT, NEW: TARGET_ROOT}
SOURCE_KEYS = {"status", "source_repository", "source_branch", "source_commit", "license", "copyright_notice"}
ENTRY_KEYS = {"source", "source_path", "source_sha256", "target_path", "transformation", "units", "asset_coverage_row"}
# File types with a format check; OGG, JPEG and JSON have none here (ADR-066 section 7.3).
FILE_TYPES = {".png.mcmeta": ("copy", "rename"), ".png": ("copy", "rename"), ".obj": ("copy", "rename", "obj"),
              ".mtl": ("copy", "rename", "mtl"), ".lang": ("lang",)}
TRANSFORMATION_KEYS = {"copy": set(), "rename": set(), "obj": {"mtllib", "usemtl"}, "mtl": {"textures"},
                       "lang": {"keys", "discard"}}
IMPORTABLE_HANDLINGS = ("IMPORT", "IMPORTED")
UNUSABLE_DISPOSITIONS = ("DEFERRED", "REJECTED")
MAX_ENTRIES = 512
MAX_SOURCES = 8
MAX_UNITS = 16
MAX_PLAN_BYTES = 4 * 1024 * 1024
SHA256 = re.compile(r"[0-9a-f]{64}")
COMMIT = re.compile(r"[0-9a-f]{40}")
MODERN_KEY = re.compile(r"[A-Za-z0-9_.-]{1,256}")
LOCALE_TARGET = re.compile(r"lang/[a-z]{2,3}_[a-z]{2,4}\.json")


class ImportFailure(ValueError):
    """Every check that failed; the run writes nothing."""

    def __init__(self, errors: list[str]):
        super().__init__("; ".join(errors))
        self.errors = errors


def _file_type(path: str) -> str | None:
    return next((suffix for suffix in FILE_TYPES if path.endswith(suffix)), None)


def _string_map(value: object) -> bool:
    return isinstance(value, dict) and all(isinstance(key, str) and key and isinstance(item, str) and item
                                           for key, item in value.items())


def validate_plan(plan: object) -> dict[str, Any]:
    if not isinstance(plan, dict) or set(plan) != {"schema_version", "batch_id", "target_version", "asset_coverage",
                                                   "sources", "entries"}:
        raise ValueError("import plan keys do not match the v1.8 plan schema")
    if plan["schema_version"] != 1 or plan["target_version"] != TARGET_VERSION:
        raise ValueError("import plan schema/version mismatch")
    if not isinstance(plan["batch_id"], str) or not re.fullmatch(r"[a-z0-9][a-z0-9.-]{0,79}", plan["batch_id"]):
        raise ValueError("import plan batch_id is invalid")
    coverage = v010.safe_relative(plan["asset_coverage"]) if isinstance(plan["asset_coverage"], str) else ""
    if not coverage.startswith("docs/") or not coverage.endswith(".csv"):
        raise ValueError("import plan asset_coverage must be a CSV under docs/")
    sources = plan["sources"]
    if not isinstance(sources, dict) or not 1 <= len(sources) <= MAX_SOURCES:
        raise ValueError("import plan sources are outside bounds")
    for name, source in sources.items():
        status = source.get("status") if isinstance(source, dict) else None
        expected = SOURCE_KEYS | ({"authoring_commit"} if status == NEW else set())
        if not re.fullmatch(r"[a-z0-9_-]{1,32}", name) or status not in (UPSTREAM, NEW) or set(source) != expected:
            raise ValueError(f"source {name!r} does not match the schema of an {UPSTREAM} or {NEW} source")
        if not all(isinstance(value, str) and value for value in source.values()) or source["license"] != "MIT":
            raise ValueError(f"source {name!r} needs non-empty strings and the MIT license")
        if not all(COMMIT.fullmatch(source[key]) for key in ("source_commit", "authoring_commit") if key in source):
            raise ValueError(f"source {name!r} commits must be full lowercase SHA-1 values")
        if status == UPSTREAM and source["copyright_notice"] != UPSTREAM_NOTICE:
            raise ValueError(f"source {name!r} does not preserve the upstream notice {UPSTREAM_NOTICE!r}")
        if status == NEW and source["source_repository"] != NEW_REPOSITORY:
            raise ValueError(f"NEW source {name!r} must name {NEW_REPOSITORY}")
    if sum(source["status"] == UPSTREAM for source in sources.values()) > 1:
        raise ValueError("import plan names more than one upstream source")
    entries = plan["entries"]
    if not isinstance(entries, list) or not 1 <= len(entries) <= MAX_ENTRIES:
        raise ValueError(f"import plan needs 1 to {MAX_ENTRIES} entries")
    seen_sources: set[tuple[str, str]] = set()
    targets: set[str] = set()
    folded: set[str] = set()
    for index, entry in enumerate(entries):
        where = f"entry {index}"
        if (not isinstance(entry, dict) or set(entry) != ENTRY_KEYS or not isinstance(entry["source"], str)
                or entry["source"] not in sources
                or not all(isinstance(entry[key], str) for key in ("source_path", "target_path"))):
            raise ValueError(f"{where} keys or source do not match the plan schema")
        status = sources[entry["source"]]["status"]
        source_path = v010.safe_relative(entry["source_path"])
        target_path = v010.safe_relative(entry["target_path"])
        if not source_path.startswith(SOURCE_ROOTS[status]):
            raise ValueError(f"{where} source is outside {SOURCE_ROOTS[status]}")
        target_rest = target_path[len(TARGET_ROOT):]
        if not target_path.startswith(TARGET_ROOT) or not models.RESOURCE_PATH.fullmatch(target_rest):
            raise ValueError(f"{where} target is not a lower-case resource path under {TARGET_ROOT}")
        if not isinstance(entry["source_sha256"], str) or not SHA256.fullmatch(entry["source_sha256"]):
            raise ValueError(f"{where} source_sha256 must be a lowercase SHA-256")
        if (entry["source"], source_path) in seen_sources or target_path in targets or target_path.casefold() in folded:
            raise ValueError(f"{where} duplicates a source or target")
        seen_sources.add((entry["source"], source_path))
        targets.add(target_path)
        folded.add(target_path.casefold())
        file_type = _file_type(source_path)
        if file_type is None:
            raise ValueError(f"{where} source has no format check in this importer (PNG, MCMETA, OBJ, MTL, LANG)")
        transformation = entry["transformation"]
        kind = transformation.get("kind") if isinstance(transformation, dict) else None
        if kind not in FILE_TYPES[file_type] or set(transformation) != TRANSFORMATION_KEYS[kind] | {"kind"}:
            raise ValueError(f"{where} transformation is not one of {FILE_TYPES[file_type]} with its own keys")
        if kind in ("obj", "mtl") and not all(_string_map(transformation[key]) for key in TRANSFORMATION_KEYS[kind]):
            raise ValueError(f"{where} reference mappings must map names to names")
        if kind == "lang":
            discard = transformation["discard"]
            if not _string_map(transformation["keys"]) or not isinstance(discard, list) or not all(
                    isinstance(key, str) and key for key in discard) or len(set(discard)) != len(discard):
                raise ValueError(f"{where} lang keys must map names to names and discard unique names")
            if not LOCALE_TARGET.fullmatch(target_rest):
                raise ValueError(f"{where} lang target must be lang/<locale>.json")
        elif not target_path.endswith(file_type):
            raise ValueError(f"{where} target must keep the {file_type} file type")
        source_rest = source_path[len(SOURCE_ROOTS[status]):]
        if kind == "copy" and source_rest != target_rest:
            raise ValueError(f"{where} copy keeps the asset path; use rename to change it")
        if kind == "rename" and source_rest == target_rest:
            raise ValueError(f"{where} rename must change the asset path")
        units = entry["units"]
        if not isinstance(units, list) or len(units) > MAX_UNITS or len(set(units)) != len(units) or not all(
                isinstance(unit, str) and unit for unit in units):
            raise ValueError(f"{where} units must be a list of unique ledger unit IDs")
        row = entry["asset_coverage_row"]
        if status == UPSTREAM and not (isinstance(row, int) and not isinstance(row, bool) and row >= 1):
            raise ValueError(f"{where} needs its asset-coverage row number")
        if status == NEW and row is not None:
            raise ValueError(f"{where} is NEW and has no asset-coverage row")
    for target in targets:
        if target.endswith(".png.mcmeta") and target[:-len(".mcmeta")] not in targets:
            raise ValueError(f"{target} is an animation without its image in the plan")
    unused = sorted(set(sources) - {entry["source"] for entry in entries})
    if unused:
        raise ValueError(f"import plan sources {unused} are not used by any entry")
    return plan


def parse_lang(data: bytes, label: str) -> dict[str, str]:
    """A legacy 1.12 language file, strictly: UTF-8 without BOM, ``key=value`` lines, comments and blank lines."""
    if data.startswith(b"\xef\xbb\xbf"):
        raise ValueError(f"{label}: starts with a byte-order mark")
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError as error:
        raise ValueError(f"{label}: not UTF-8 ({error.reason} at byte {error.start})") from None
    values: dict[str, str] = {}
    for number, line in enumerate(text.split("\n"), start=1):
        line = line[:-1] if line.endswith("\r") else line
        if any(ord(character) < 32 and character != "\t" or ord(character) == 127 for character in line):
            raise ValueError(f"{label}:{number}: contains a control character")
        if line.strip() == "#PARSE_ESCAPES":
            raise ValueError(f"{label}:{number}: escape parsing is not supported")
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if not separator or not key or any(character.isspace() for character in key):
            raise ValueError(f"{label}:{number}: not a key=value line")
        if key in values:
            raise ValueError(f"{label}:{number}: duplicate key {key!r}")
        values[key] = value
    return values


def extract_lang(data: bytes, transformation: dict[str, Any], label: str) -> tuple[bytes, list[str]]:
    legacy = parse_lang(data, label)
    mapping, discard = transformation["keys"], set(transformation["discard"])
    unknown = sorted((set(mapping) | discard) - set(legacy))
    if unknown:
        raise ValueError(f"{label}: the plan names legacy keys the file does not have: {unknown[:5]}")
    if set(mapping) & discard:
        raise ValueError(f"{label}: the plan both maps and discards {sorted(set(mapping) & discard)[:5]}")
    unmapped = sorted(set(legacy) - set(mapping) - discard)
    if unmapped:
        raise ValueError(f"{label}: {len(unmapped)} legacy keys are neither mapped nor discarded by the plan, "
                         f"for example {unmapped[:5]}")
    modern = list(mapping.values())
    if len(set(modern)) != len(modern):
        raise ValueError(f"{label}: two legacy keys map to the same modern key")
    for key in modern:
        if not MODERN_KEY.fullmatch(key) or MOD_ID not in key.split("."):
            raise ValueError(f"{label}: {key!r} is not a modern {MOD_ID} language key")
    output = {new: legacy[old] for old, new in mapping.items()}
    description = [
        f"Extract {len(mapping)} legacy keys to the modern keys the plan names and discard {len(discard)} listed keys.",
        "Serialize UTF-8 JSON with sorted keys, a two-space indent and no trailing newline (Forge LanguageProvider).",
    ] + [f"Key {old} -> {new}" for old, new in sorted(mapping.items())]
    return json.dumps(output, ensure_ascii=False, sort_keys=True, indent=2).encode("utf-8"), description


def render(entry: dict[str, Any], status: str, data: bytes) -> tuple[bytes, list[str]]:
    """The target bytes and the record's transformation list."""
    transformation = entry["transformation"]
    kind = transformation["kind"]
    source_rest = entry["source_path"][len(SOURCE_ROOTS[status]):]
    target_rest = entry["target_path"][len(TARGET_ROOT):]
    label = entry["source_path"]
    if kind == "copy":
        if status == NEW:
            return data, ["Byte copy of the repository-owned file at its authoring commit."]
        return data, [f"Byte copy into the mod namespace: assets/advancedrocketry/{source_rest} to "
                      f"assets/{MOD_ID}/{target_rest}."]
    if kind == "rename":
        return data, [f"Byte copy renamed from {source_rest} to assets/{MOD_ID}/{target_rest}."]
    if kind == "lang":
        return extract_lang(data, transformation, label)
    parse = models.parse_obj if kind == "obj" else models.parse_mtl
    parse(data, label, references=False)
    mapping = {key: transformation[key] for key in sorted(TRANSFORMATION_KEYS[kind])}
    output = models.rewrite(data, kind, mapping, label)
    description = [f"Byte copy to assets/{MOD_ID}/{target_rest}, except the {kind.upper()} references the plan "
                   f"rewrites; every other byte is unchanged."]
    for group, values in mapping.items():
        description += [f"Rewrite {group} {old!r} -> {new!r}" for old, new in sorted(values.items())]
    return output, description


def _coverage(root: Path, relative: str) -> list[dict[str, str]]:
    path = root / relative
    if path.is_symlink() or not path.is_file():
        raise ValueError(f"asset coverage table is missing: {relative}")
    rows = list(csv.DictReader(path.read_text(encoding="utf-8").splitlines()))
    if rows and not {"source_path", "source_sha256", "owning_units"} <= set(rows[0]):
        raise ValueError(f"asset coverage table {relative} lacks source_path, source_sha256 or owning_units")
    return rows


def _check_new_source(root: Path, name: str, source: dict[str, str]) -> None:
    license_text = (root / "LICENSE").read_text(encoding="utf-8")
    if "MIT License" not in license_text or source["copyright_notice"] not in license_text:
        raise ValueError(f"NEW source {name!r} copyright notice is not in the repository LICENSE")
    if checks.first_parent(root, source["authoring_commit"]) != source["source_commit"]:
        raise ValueError(f"NEW source {name!r} source_commit is not the parent of its authoring commit")


def build(root: Path, upstream: Path, plan_path: Path, vanilla_specs: list[str]) -> tuple[dict, dict[str, bytes]]:
    """Run every check and return the record core and the target bytes; raise ImportFailure on any failure."""
    plan_bytes = checks.read_input(plan_path, MAX_PLAN_BYTES)
    plan = validate_plan(json.loads(plan_bytes.decode("utf-8")))
    inputs = checks.load_inputs(root)
    pin = inputs.upstream
    for name, source in plan["sources"].items():
        if source["status"] == UPSTREAM and any(source[key] != pin[key] for key in pin):
            raise ValueError(f"upstream source {name!r} differs from the pin in {checks.UPSTREAM_DOC}")
        if source["status"] == NEW:
            _check_new_source(root, name, source)
    files = {item.path: item.data for item in load_tracked_files(upstream, pin["source_commit"])}
    checks.require_full_history(upstream)
    legacy: dict[str, bytes] = {}
    for path, digest in inputs.manifest.items():
        if checks.sha256(files.get(path, b"")) != digest:
            raise ValueError(f"the legacy manifest differs from the upstream tree at {path}")
        legacy[path[len(checks.LEGACY_ASSET_ROOT):]] = files[path]
    coverage = _coverage(root, plan["asset_coverage"])
    errors: list[str] = []
    rendered: dict[str, bytes] = {}
    prepared = []
    for entry in plan["entries"]:
        source = plan["sources"][entry["source"]]
        status, path, where = source["status"], entry["source_path"], entry["target_path"]
        try:
            data = files[path] if status == UPSTREAM else checks.read_blob(root, source["authoring_commit"], path)
        except (KeyError, ValueError):
            errors.append(f"{where}: source {path} is not tracked at its pinned commit")
            continue
        digest = checks.sha256(data)
        if digest != entry["source_sha256"]:
            errors.append(f"{where}: source SHA-256 {digest} differs from the plan's {entry['source_sha256']}")
            continue
        asset = path[len(checks.LEGACY_ASSET_ROOT):] if status == UPSTREAM else None
        if status == UPSTREAM:
            if inputs.manifest.get(path) != digest:
                errors.append(f"{where}: source is not in the legacy manifest with this SHA-256")
            if inputs.handling(asset) not in IMPORTABLE_HANDLINGS:
                errors.append(f"{where}: {asset} is handled as {inputs.handling(asset)} in the asset plan, not IMPORT")
            row_number = entry["asset_coverage_row"]
            row = coverage[row_number - 1] if row_number <= len(coverage) else None
            if row is None or row["source_path"] != path or row["source_sha256"] != digest:
                errors.append(f"{where}: asset-coverage row {row_number} does not name this source and SHA-256")
            elif row["owning_units"] != "-" and set(row["owning_units"].split()) != set(entry["units"]):
                errors.append(f"{where}: units {entry['units']} differ from the coverage row's {row['owning_units']}")
        for unit in entry["units"]:
            if inputs.ledger.get(unit) is None or inputs.ledger[unit] in UNUSABLE_DISPOSITIONS:
                errors.append(f"{where}: unit {unit} is not an open ledger unit")
        try:
            target, description = render(entry, status, data)
        except ValueError as error:
            errors.append(str(error))
            continue
        rendered[entry["target_path"]] = target
        prepared.append((entry, source, asset, data, target, description))
    summaries, model_set, model_errors = models.check_model_set(rendered, TARGET_ROOT)
    errors += model_errors
    if errors:
        raise ImportFailure(errors)
    candidates: dict[str, bytes] = {}
    for entry, source, asset, data, target, _ in prepared:
        candidates[asset or "new:" + entry["source_path"]] = data
        if target != data:
            candidates["target:" + entry["target_path"]] = target
    derived = checks.derive(candidates, legacy, checks.load_vanilla(vanilla_specs, inputs.vanilla_pins))
    entries = []
    for entry, source, asset, data, target, description in prepared:
        where = entry["target_path"]
        result = derived[asset or "new:" + entry["source_path"]]
        verdicts = {"source": result["verdict"]}
        related = set(result["related"])
        if target != data:
            verdicts["target"] = derived["target:" + where]["verdict"]
        record = {
            "target_path": where, "status": source["status"], "source_repository": source["source_repository"],
            "source_branch": source["source_branch"], "source_commit": source["source_commit"],
            "source_path": entry["source_path"], "source_sha256": entry["source_sha256"],
            "target_sha256": checks.sha256(target), "license": source["license"],
            "copyright_notice": source["copyright_notice"], "transformation": description,
        }
        if source["status"] == NEW:
            record["authoring_commit"] = source["authoring_commit"]
        else:
            if inputs.digests.get(asset) != entry["source_sha256"]:
                errors.append(f"{where}: the pinned derivation results do not cover these source bytes")
            verdicts["recorded"] = inputs.verdicts.get(asset, "missing")
            related |= inputs.related.get(asset, set())
            record["upstream_history"], triggers = checks.upstream_history(upstream, pin["source_commit"],
                                                                           entry["source_path"])
        reasons = checks.review_reasons(inputs, verdicts, related)
        if source["status"] == UPSTREAM:
            reasons += triggers
        if "HIT" in verdicts.values():
            errors.append(f"{where}: derivation verdict HIT; a vanilla-derived file is never imported")
        elif reasons and (asset is None or inputs.findings.get(asset) != "CLEARED"):
            errors += [f"{where}: at most REVIEW without a CLEARED origin finding: {reason}" for reason in reasons]
        elif reasons:
            record["review_triggers"] = reasons
            record["origin_finding"] = "CLEARED"
        record.update(units=sorted(entry["units"]), asset_coverage_row=entry["asset_coverage_row"],
                      derivation={"verdicts": verdicts, "related": sorted(related)})
        if where in summaries:
            record["format_check"] = summaries[where]
        entries.append(record)
    if errors:
        raise ImportFailure(errors)
    sources = [{"name": name, **source} for name, source in sorted(plan["sources"].items())]
    core = {
        "schema_version": 2,
        "batch_id": plan["batch_id"],
        "target_version": TARGET_VERSION,
        "generator": GENERATOR,
        "plan_sha256": checks.sha256(plan_bytes),
        "sources": sources,
        "license": "MIT",
        "copyright_notice": "; ".join(sorted({source["copyright_notice"] for source in sources})),
        "derivation_results_sha256": inputs.results_sha256,
        "vanilla_client_sha256": dict(sorted(inputs.vanilla_pins.items())),
        "model_set": model_set,
        "entries": sorted(entries, key=lambda item: item["target_path"]),
    }
    return core, rendered


def _other_roots(root: Path) -> list[Path]:
    return sorted(path for path in (root / "src/generated").glob("*/resources") if path.is_dir()) + [
        root / "src/generated/resources"]


def check_targets(root: Path, rendered: dict[str, bytes]) -> list[str]:
    """A target must not also exist in a DataGen root, where it would be a second copy of the resource."""
    errors = []
    for target in sorted(rendered):
        for other in _other_roots(root):
            if (other / target[len(RESOURCE_ROOT):]).exists():
                errors.append(f"{target}: the same resource exists in {other.relative_to(root).as_posix()}")
    return errors


def _load_previous(record_path: Path) -> object | None:
    return json.loads(checks.read_input(record_path, MAX_PLAN_BYTES)) if record_path.exists() else None


def _write_all(files: dict[Path, bytes]) -> None:
    """Write every file or none: stage all, then replace, restoring earlier contents if a replace fails."""
    staged: list[tuple[Path, Path]] = []
    try:
        for path, data in files.items():
            path.parent.mkdir(parents=True, exist_ok=True)
            temporary = path.with_name(path.name + f".tmp-{os.getpid()}")
            staged.append((temporary, path))
            temporary.write_bytes(data)
        done: list[tuple[Path, bytes | None]] = []
        try:
            for temporary, path in staged:
                previous = path.read_bytes() if path.is_file() else None
                os.replace(temporary, path)
                done.append((path, previous))
        except OSError:
            for path, previous in reversed(done):
                if previous is None:
                    path.unlink(missing_ok=True)
                else:
                    path.write_bytes(previous)
            raise
    finally:
        for temporary, _ in staged:
            temporary.unlink(missing_ok=True)


def generate(root: Path, upstream: Path, plan_path: Path, record_path: Path, vanilla_specs: list[str]) -> dict:
    core, rendered = build(root, upstream, plan_path, vanilla_specs)
    previous = _load_previous(record_path)
    recorded = {}
    if isinstance(previous, dict) and isinstance(previous.get("entries"), list):
        recorded = {item.get("target_path"): item.get("target_sha256") for item in previous["entries"]
                    if isinstance(item, dict)}
    errors = check_targets(root, rendered)
    for target, data in sorted(rendered.items()):
        path = root / target
        if path.is_symlink() or (path.exists() and not path.is_file()):
            errors.append(f"{target}: exists and is not a regular file")
        elif path.is_file() and path.read_bytes() != data and recorded.get(target) != checks.sha256(path.read_bytes()):
            errors.append(f"{target}: exists with other content that this batch's record does not name")
    stale = sorted(set(recorded) - set(rendered))
    if stale:
        errors.append(f"the existing record names targets the plan no longer has; remove them deliberately: {stale}")
    if errors:
        raise ImportFailure(errors)
    record = v010.record_for(core, previous)
    _write_all({**{root / target: data for target, data in rendered.items()}, record_path: v010.pretty_json(record)})
    return record


def verify(root: Path, upstream: Path, plan_path: Path, record_path: Path, vanilla_specs: list[str]) -> list[str]:
    core, rendered = build(root, upstream, plan_path, vanilla_specs)
    errors = check_targets(root, rendered)
    recorded = checks.read_input(record_path, MAX_PLAN_BYTES)
    if recorded != v010.pretty_json(v010.record_for(core, json.loads(recorded))):
        errors.append("provenance record does not reproduce byte for byte from the plan, upstream and targets")
    for target, data in sorted(rendered.items()):
        path = root / target
        if path.is_symlink() or not path.is_file():
            errors.append(f"target is missing or not a regular file: {target}")
        elif path.read_bytes() != data:
            errors.append(f"target bytes differ from the deterministic transform: {target}")
    return errors


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("mode", choices=("generate", "verify"))
    parser.add_argument("--upstream", type=Path, required=True, help="Advanced Rocketry clone at the pinned commit")
    parser.add_argument("--plan", type=Path, required=True)
    parser.add_argument("--record", type=Path, required=True)
    parser.add_argument("--vanilla", action="append", required=True,
                        help="LABEL=PATH of a pinned vanilla client JAR; give 1.12.2 and 1.20.1")
    parser.add_argument("--repository-root", type=Path, default=REPOSITORY_ROOT)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    root = args.repository_root.resolve()
    plan = args.plan if args.plan.is_absolute() else root / args.plan
    record = args.record if args.record.is_absolute() else root / args.record
    try:
        if args.mode == "generate":
            generated = generate(root, args.upstream, plan, record, args.vanilla)
            print(f"[PASS] Generated {len(generated['entries'])} provenance-bound v1.8 targets; "
                  f"review={generated['review']['status']}")
            return 0
        errors = verify(root, args.upstream, plan, record, args.vanilla)
    except ImportFailure as failure:
        errors = failure.errors
    except (OSError, UnicodeError, ValueError, json.JSONDecodeError) as exc:
        errors = [str(exc)]
    if errors:
        for error in errors:
            print(f"[FAIL] {error}", file=sys.stderr)
        return 1
    loaded = json.loads(record.read_bytes())
    print(f"[PASS] {len(loaded['entries'])} v1.8 targets and their record reproduce byte for byte")
    print(f"[INFO] Provenance review status: {loaded['review']['status']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
