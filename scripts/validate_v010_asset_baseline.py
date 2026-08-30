#!/usr/bin/env python3
"""Validate v0.1.0 audit, provenance, DataGen resources, and optional JAR."""

from __future__ import annotations

import argparse
import csv
import hashlib
import importlib.util
import json
import re
import sys
import zipfile
from pathlib import Path, PurePosixPath
from typing import Any, Iterable

if __package__:
    from .manage_v010_generated_manifest import verify as verify_generated_manifest
else:
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from manage_v010_generated_manifest import verify as verify_generated_manifest


ROOT = Path(__file__).resolve().parents[1]
IMPORTER_PATH = ROOT / "tools/import/import_v010_assets.py"
PROVENANCE_RECORD = Path("docs/provenance/v0.1.0-minimal-content.json")
GENERATED_MANIFEST = Path("docs/provenance/v0.1.0-generated-resources.json")
AUDIT_ASSETS = Path("legacy-manifest/assets.csv")
AUDIT_COMMIT = Path("legacy-manifest/UPSTREAM_COMMIT.txt")
SCHEMA = Path("docs/provenance/schema-v1.json")
MOD_ID = "advancedrocketrycommunity"
EXPECTED_UPSTREAM_COMMIT = "c5cd5af62fc07cd4e0d24f06a16033f181c47c04"
EXPECTED_IMPORT_COUNT = 10
MAX_JSON_BYTES = 2 * 1024 * 1024
MAX_ASSET_CSV_BYTES = 4 * 1024 * 1024
MAX_RESOURCE_FILES = 512
MAX_RESOURCE_BYTES = 64 * 1024 * 1024
MAX_JAR_BYTES = 32 * 1024 * 1024
EXPECTED_TRANSLATIONS = {
    f"block.{MOD_ID}.machine_casing",
    f"item.{MOD_ID}.advanced_circuit",
    f"item.{MOD_ID}.basic_circuit",
    f"item.{MOD_ID}.data_storage_unit",
    f"item.{MOD_ID}.silicon_wafer",
    f"itemGroup.{MOD_ID}.main",
    f"message.{MOD_ID}.machine_casing_inert",
    f"subtitle.{MOD_ID}.ui_select",
    f"tooltip.{MOD_ID}.development_component",
}


def _load_importer():
    spec = importlib.util.spec_from_file_location("arce_import_v010_assets", IMPORTER_PATH)
    if spec is None or spec.loader is None:
        raise ValueError("cannot load the bound v0.1.0 importer")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def _load_json(path: Path, limit: int = MAX_JSON_BYTES) -> Any:
    if path.is_symlink() or not path.is_file():
        raise ValueError(f"JSON file is missing or unsafe: {path}")
    data = path.read_bytes()
    if len(data) > limit:
        raise ValueError(f"JSON file exceeds {limit} bytes: {path}")
    return json.loads(data.decode("utf-8", errors="strict"))


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _safe_relative(raw: str) -> str:
    path = PurePosixPath(raw)
    if (
        not raw
        or len(raw.encode("utf-8", errors="strict")) > 4096
        or path.is_absolute()
        or path.as_posix() != raw
        or "\\" in raw
        or ":" in raw
        or any(part in {"", ".", ".."} for part in path.parts)
        or any(ord(character) < 32 or ord(character) == 127 for character in raw)
    ):
        raise ValueError(f"unsafe repository-relative path: {raw!r}")
    return raw


def _regular_bytes(repository_root: Path, relative: str, limit: int) -> bytes:
    safe = _safe_relative(relative)
    path = repository_root.joinpath(*PurePosixPath(safe).parts)
    if path.is_symlink() or not path.is_file():
        raise ValueError(f"target is missing or not a regular file: {relative}")
    data = path.read_bytes()
    if len(data) > limit:
        raise ValueError(f"target exceeds {limit} bytes: {relative}")
    return data


def _core_from_record(record: dict[str, Any]) -> dict[str, Any]:
    return {key: value for key, value in record.items() if key != "review"}


def validate_provenance_record(repository_root: Path) -> tuple[list[str], dict[str, Any]]:
    errors: list[str] = []
    details: dict[str, Any] = {}
    try:
        importer = _load_importer()
        schema = _load_json(repository_root / SCHEMA)
        record_path = repository_root / PROVENANCE_RECORD
        record = _load_json(record_path)
    except (OSError, UnicodeError, ValueError, json.JSONDecodeError) as exc:
        return [str(exc)], details
    if not isinstance(schema, dict) or schema.get("$schema") != "https://json-schema.org/draft/2020-12/schema":
        errors.append("provenance JSON schema is missing its draft-2020-12 identity")
    if not isinstance(record, dict):
        return errors + ["provenance record must be an object"], details
    expected_keys = {
        "schema_version",
        "batch_id",
        "target_version",
        "source_repository",
        "source_branch",
        "source_commit",
        "license",
        "copyright_notice",
        "entries",
        "review",
    }
    if set(record) != expected_keys:
        errors.append("provenance record keys do not match schema v1")
        return errors, details
    core = _core_from_record(record)
    digest = _sha256(importer.canonical_json(core))
    try:
        importer.validate_review(record["review"], digest)
    except ValueError as exc:
        errors.append(str(exc))
    if record_path.read_bytes() != importer.pretty_json(record):
        errors.append("provenance record is not canonical sorted JSON")
    if record.get("schema_version") != 1 or record.get("target_version") != "v0.1.0":
        errors.append("provenance schema or target version mismatch")
    if record.get("source_commit") != EXPECTED_UPSTREAM_COMMIT:
        errors.append("provenance record is not bound to the approved upstream commit")
    if record.get("source_repository") != "https://github.com/Advanced-Rocketry/AdvancedRocketry":
        errors.append("provenance record uses an unapproved repository")
    if record.get("source_branch") != "1.12" or record.get("license") != "MIT":
        errors.append("provenance branch/license mismatch")
    if record.get("copyright_notice") != "Copyright (c) 2017":
        errors.append("provenance record omits the original copyright notice")
    entries = record.get("entries")
    if not isinstance(entries, list) or len(entries) != EXPECTED_IMPORT_COUNT:
        errors.append(f"provenance record must contain exactly {EXPECTED_IMPORT_COUNT} minimal-batch entries")
        return errors, details

    audit_path = repository_root / AUDIT_ASSETS
    try:
        if audit_path.is_symlink() or not audit_path.is_file() or audit_path.stat().st_size > MAX_ASSET_CSV_BYTES:
            raise ValueError("legacy asset manifest is missing, unsafe, or too large")
        with audit_path.open(encoding="utf-8", newline="") as handle:
            audit_rows = list(csv.DictReader(handle))
    except (OSError, UnicodeError, csv.Error, ValueError) as exc:
        return errors + [str(exc)], details
    audit_by_path = {row.get("source_path", ""): row for row in audit_rows}
    targets: set[str] = set()
    sources: set[str] = set()
    casefolded: set[str] = set()
    target_hashes: dict[str, str] = {}
    for index, entry in enumerate(entries):
        if not isinstance(entry, dict):
            errors.append(f"provenance entry {index} is not an object")
            continue
        expected_entry_keys = {
            "target_path",
            "status",
            "source_repository",
            "source_branch",
            "source_commit",
            "source_path",
            "source_sha256",
            "target_sha256",
            "license",
            "copyright_notice",
            "transformation",
        }
        if set(entry) != expected_entry_keys:
            errors.append(f"provenance entry {index} keys do not match schema v1")
            continue
        source = entry["source_path"]
        target = entry["target_path"]
        try:
            _safe_relative(source)
            _safe_relative(target)
        except (UnicodeError, ValueError) as exc:
            errors.append(str(exc))
            continue
        if source in sources or target in targets or target.casefold() in casefolded:
            errors.append(f"duplicate/case-colliding provenance target: {target}")
        sources.add(source)
        targets.add(target)
        casefolded.add(target.casefold())
        if entry["status"] != "UPSTREAM_AR_MIT":
            errors.append(f"minimal imported target has invalid status: {target}")
        if (
            entry["source_repository"] != record["source_repository"]
            or entry["source_branch"] != record["source_branch"]
            or entry["source_commit"] != record["source_commit"]
            or entry["license"] != "MIT"
            or entry["copyright_notice"] != "Copyright (c) 2017"
        ):
            errors.append(f"entry source/license binding differs from its batch: {target}")
        row = audit_by_path.get(source)
        if row is None:
            errors.append(f"provenance source is absent from exact legacy manifest: {source}")
        elif (
            row.get("sha256") != entry["source_sha256"]
            or row.get("source_commit") != EXPECTED_UPSTREAM_COMMIT
            or row.get("license_status") != "UPSTREAM_AR_MIT"
        ):
            errors.append(f"provenance source hash/commit/license differs from legacy manifest: {source}")
        if not isinstance(entry["transformation"], list) or not entry["transformation"]:
            errors.append(f"provenance target has no recorded transformation: {target}")
        try:
            target_data = _regular_bytes(repository_root, target, 8 * 1024 * 1024)
        except ValueError as exc:
            errors.append(str(exc))
            continue
        actual_hash = _sha256(target_data)
        if actual_hash != entry["target_sha256"]:
            errors.append(f"provenance target hash mismatch: {target}")
        target_hashes[target] = actual_hash

    try:
        commit_text = (repository_root / AUDIT_COMMIT).read_text(encoding="ascii", errors="strict")
    except (OSError, UnicodeError) as exc:
        errors.append(f"cannot read legacy audit commit: {exc}")
    else:
        if commit_text != EXPECTED_UPSTREAM_COMMIT + "\n":
            errors.append("legacy audit commit does not match UPSTREAM.md/provenance")
    details.update(
        {
            "record": record,
            "targets": targets,
            "target_hashes": target_hashes,
            "review_status": record["review"].get("status") if isinstance(record.get("review"), dict) else "INVALID",
            "content_digest": digest,
        }
    )
    return errors, details


def _enumerate_resource_files(repository_root: Path) -> tuple[list[str], list[str]]:
    errors: list[str] = []
    roots = (
        Path(f"src/main/resources/assets/{MOD_ID}"),
        Path("src/generated/resources"),
    )
    paths: list[str] = []
    total = 0
    seen_casefolded: set[str] = set()
    for relative_root in roots:
        root = repository_root / relative_root
        if not root.exists():
            continue
        if root.is_symlink() or not root.is_dir():
            errors.append(f"resource root is unsafe: {relative_root.as_posix()}")
            continue
        for path in sorted(root.rglob("*"), key=lambda value: value.as_posix()):
            if path.is_dir():
                continue
            relative = path.relative_to(repository_root).as_posix()
            if relative.startswith("src/generated/resources/.cache/"):
                continue
            if path.is_symlink() or not path.is_file():
                errors.append(f"resource is not a regular file: {relative}")
                continue
            if relative.casefold() in seen_casefolded:
                errors.append(f"case-colliding resource path: {relative}")
                continue
            seen_casefolded.add(relative.casefold())
            size = path.stat().st_size
            total += size
            if size > 8 * 1024 * 1024 or total > MAX_RESOURCE_BYTES:
                errors.append(f"resource byte limit exceeded at: {relative}")
                continue
            paths.append(relative)
    if len(paths) > MAX_RESOURCE_FILES:
        errors.append(f"resource count exceeds {MAX_RESOURCE_FILES}")
    return paths, errors


def _resource_location_target(value: str, kind: str) -> str | None:
    if value.startswith("#"):
        return None
    if ":" in value:
        namespace, path = value.split(":", 1)
    else:
        namespace, path = "minecraft", value
    if namespace != MOD_ID:
        return None
    if kind == "model":
        return f"src/generated/resources/assets/{MOD_ID}/models/{path}.json"
    if kind == "texture":
        return f"src/main/resources/assets/{MOD_ID}/textures/{path}.png"
    if kind == "sound":
        return f"src/main/resources/assets/{MOD_ID}/sounds/{path}.ogg"
    return None


def _walk_json(value: object, prefix: str = "") -> Iterable[tuple[str, str]]:
    if isinstance(value, dict):
        for key in sorted(value):
            child = f"{prefix}.{key}" if prefix else key
            yield from _walk_json(value[key], child)
    elif isinstance(value, list):
        for index, item in enumerate(value):
            yield from _walk_json(item, f"{prefix}[{index}]")
    elif isinstance(value, str):
        yield prefix, value


def validate_resources(repository_root: Path, imported_targets: set[str]) -> tuple[list[str], dict[str, Any]]:
    errors: list[str] = []
    details: dict[str, Any] = {}
    generated_path = repository_root / GENERATED_MANIFEST
    try:
        errors.extend(verify_generated_manifest(repository_root, generated_path))
        generated = _load_json(generated_path)
    except (OSError, UnicodeError, ValueError, json.JSONDecodeError) as exc:
        return [str(exc)], details
    if not isinstance(generated, dict) or not isinstance(generated.get("targets"), list):
        return errors + ["generated-resource manifest is malformed"], details
    generated_targets = {
        entry.get("path") for entry in generated["targets"] if isinstance(entry, dict)
    }
    managed = imported_targets | generated_targets
    actual, enumeration_errors = _enumerate_resource_files(repository_root)
    errors.extend(enumeration_errors)
    actual_set = set(actual)
    if actual_set != managed:
        missing = sorted(managed - actual_set)
        unrecorded = sorted(actual_set - managed)
        if missing:
            errors.append("managed resources are missing: " + ", ".join(missing))
        if unrecorded:
            errors.append("unrecorded resources entered the distributable roots: " + ", ".join(unrecorded))

    parsed_json: dict[str, object] = {}
    for relative in actual:
        try:
            data = _regular_bytes(repository_root, relative, 8 * 1024 * 1024)
        except ValueError as exc:
            errors.append(str(exc))
            continue
        lower = relative.lower()
        if lower.endswith(".json"):
            try:
                parsed_json[relative] = json.loads(data.decode("utf-8", errors="strict"))
            except (UnicodeError, json.JSONDecodeError) as exc:
                errors.append(f"invalid resource JSON {relative}: {exc}")
        elif lower.endswith(".png"):
            if len(data) < 33 or not data.startswith(b"\x89PNG\r\n\x1a\n"):
                errors.append(f"invalid PNG resource: {relative}")
            else:
                width = int.from_bytes(data[16:20], "big")
                height = int.from_bytes(data[20:24], "big")
                if not 1 <= width <= 4096 or not 1 <= height <= 4096:
                    errors.append(f"PNG dimensions outside bounds: {relative}")
        elif lower.endswith(".ogg") and not data.startswith(b"OggS"):
            errors.append(f"invalid OGG resource: {relative}")

    references_checked = 0
    for relative, value in sorted(parsed_json.items()):
        if f"assets/{MOD_ID}/" not in relative:
            continue
        is_sound_definition = relative.endswith(f"assets/{MOD_ID}/sounds.json")
        for key_path, reference in _walk_json(value):
            kind = ""
            if key_path.endswith("parent") or key_path.endswith("model") or ".model" in key_path:
                kind = "model"
            elif ".textures." in f".{key_path}." or key_path.startswith("textures."):
                kind = "texture"
            elif is_sound_definition and (key_path.endswith(".name") or re.search(r"sounds\[\d+\]$", key_path)):
                kind = "sound"
            if not kind:
                continue
            target = _resource_location_target(reference, kind)
            if target is None:
                continue
            references_checked += 1
            if target not in actual_set:
                errors.append(f"missing {kind} reference from {relative}: {reference} -> {target}")

    for locale in ("en_us", "zh_cn"):
        relative = f"src/generated/resources/assets/{MOD_ID}/lang/{locale}.json"
        value = parsed_json.get(relative)
        if not isinstance(value, dict):
            errors.append(f"language file is absent or malformed: {relative}")
        else:
            missing_keys = sorted(EXPECTED_TRANSLATIONS - set(value))
            extra_keys = sorted(set(value) - EXPECTED_TRANSLATIONS)
            if missing_keys:
                errors.append(f"{locale} is missing translations: {', '.join(missing_keys)}")
            if extra_keys:
                errors.append(f"{locale} contains unexpected translations: {', '.join(extra_keys)}")

    expected_counts = {
        "blockstates": 1,
        "models": 6,
        "recipes": 5,
        "loot_tables": 1,
    }
    observed_counts = {
        "blockstates": sum("/blockstates/" in path for path in actual),
        "models": sum(f"assets/{MOD_ID}/models/" in path for path in actual),
        "recipes": sum(f"data/{MOD_ID}/recipes/" in path for path in actual),
        "loot_tables": sum(f"data/{MOD_ID}/loot_tables/" in path for path in actual),
    }
    for category, expected in expected_counts.items():
        if observed_counts[category] != expected:
            errors.append(f"unexpected generated {category} count: {observed_counts[category]} (expected {expected})")
    details.update(
        {
            "resource_count": len(actual),
            "generated_count": len(generated_targets),
            "references_checked": references_checked,
            "observed_counts": observed_counts,
            "managed_targets": managed,
        }
    )
    return errors, details


def _archive_path(relative: str) -> str:
    for prefix in ("src/main/resources/", "src/generated/resources/"):
        if relative.startswith(prefix):
            return relative.removeprefix(prefix)
    raise ValueError(f"resource is outside archive roots: {relative}")


def validate_jar(jar_path: Path, repository_root: Path, managed_targets: set[str], target_hashes: dict[str, str]) -> list[str]:
    errors: list[str] = []
    if jar_path.is_symlink() or not jar_path.is_file():
        return [f"JAR is missing or unsafe: {jar_path}"]
    if jar_path.stat().st_size > MAX_JAR_BYTES:
        return [f"JAR exceeds {MAX_JAR_BYTES} bytes"]
    expected_archive = {_archive_path(path) for path in managed_targets}
    imported_archive_hashes = {_archive_path(path): value for path, value in target_hashes.items()}
    try:
        with zipfile.ZipFile(jar_path) as archive:
            names = [info.filename for info in archive.infolist() if not info.is_dir()]
            if len(names) != len(set(names)) or len(names) != len({name.casefold() for name in names}):
                errors.append("JAR contains duplicate or case-colliding entries")
            actual_resources = {
                name
                for name in names
                if name.startswith((f"assets/{MOD_ID}/", f"data/{MOD_ID}/", "data/minecraft/tags/blocks/"))
            }
            if actual_resources != expected_archive:
                missing = sorted(expected_archive - actual_resources)
                extra = sorted(actual_resources - expected_archive)
                if missing:
                    errors.append("JAR omits managed resources: " + ", ".join(missing))
                if extra:
                    errors.append("JAR contains unrecorded resources: " + ", ".join(extra))
            if any(name.startswith("assets/advancedrocketry/") for name in names):
                errors.append("JAR contains the legacy namespace")
            if any(token in name.lower() for name in names for token in ("quarantine", "rejected")):
                errors.append("JAR contains a quarantined/rejected path")
            for name, expected_hash in imported_archive_hashes.items():
                try:
                    data = archive.read(name)
                except KeyError:
                    continue
                if _sha256(data) != expected_hash:
                    errors.append(f"JAR imported resource hash mismatch: {name}")
    except (OSError, zipfile.BadZipFile) as exc:
        errors.append(f"cannot inspect JAR: {exc}")
    return errors


def validate_v010_asset_baseline(
    repository_root: Path = ROOT,
    jar_path: Path | None = None,
) -> tuple[list[str], dict[str, Any]]:
    repository_root = repository_root.resolve()
    provenance_errors, provenance = validate_provenance_record(repository_root)
    if provenance_errors:
        return provenance_errors, provenance
    resource_errors, resources = validate_resources(repository_root, provenance["targets"])
    errors = list(resource_errors)
    details = {**provenance, **resources}
    if jar_path is not None and not errors:
        errors.extend(
            validate_jar(
                jar_path.resolve(),
                repository_root,
                resources["managed_targets"],
                provenance["target_hashes"],
            )
        )
        details["jar"] = str(jar_path)
    return errors, details


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    errors, details = validate_v010_asset_baseline(jar_path=args.jar)
    if errors:
        for error in errors:
            print(f"[FAIL] {error}", file=sys.stderr)
        return 1
    print(
        f"[PASS] v0.1.0 asset baseline: {len(details['targets'])} imported, "
        f"{details['generated_count']} generated, {details['resource_count']} total resources"
    )
    print(
        f"[PASS] JSON/reference validation: {details['references_checked']} local references checked; "
        "no missing or case-colliding project resources"
    )
    print(f"[INFO] Provenance human review: {details['review_status']}")
    if args.jar:
        print(f"[PASS] JAR contains exactly the managed v0.1.0 resource set: {args.jar}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
