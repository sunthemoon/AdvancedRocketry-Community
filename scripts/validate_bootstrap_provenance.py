#!/usr/bin/env python3
"""Validate the machine-readable v0.0.2 bootstrap provenance record."""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import re
import sys
from datetime import date
from pathlib import Path, PurePosixPath
from typing import Any
from urllib.parse import urlparse

if __package__:
    from .validate_release_checksums import file_sha256, relative_path_error
else:
    from validate_release_checksums import file_sha256, relative_path_error


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MANIFEST = Path("docs/provenance/v0.0.2-bootstrap-inputs.json")
EXPECTED_RECORD_PATH = "docs/provenance/v0.0.2-forge-mdk-and-gradle-wrapper.md"

SHA256 = re.compile(r"[0-9a-f]{64}")
COMMIT = re.compile(r"[0-9a-f]{40}")

PENDING_RECORD_STATUS = "EVIDENCE_COMPLETE_HUMAN_REVIEW_PENDING"
APPROVED_RECORD_STATUS = "THIRD_PARTY_APPROVED"
PENDING_TARGET_STATUS = "PENDING_HUMAN_REVIEW"

EXPECTED_COMPONENTS = {
    "forge_mdk": {
        "source_repository": "https://github.com/MinecraftForge/MinecraftForge",
        "source_commit": "132704e5f23dbee28d776738eb1c0c42fefc0bf6",
        "source_artifact": "forge-1.20.1-47.4.10-mdk.zip",
        "source_sha256": "73e0122becd05e39b47eced54e030380d66411850ed86786a2d58ecd886b0451",
        "license": "LGPL-2.1-only",
        "license_source_path": "LICENSE.txt",
        "license_source_sha256": "481c96d94d182382c4225d5b210f8c658c85350cf548f25c9f56c058804f1e57",
        "license_copy_target": (
            "docs/licenses/MINECRAFT-FORGE-1.20.1-47.4.10-LICENSE.txt"
        ),
        "license_copy_target_sha256": "481c96d94d182382c4225d5b210f8c658c85350cf548f25c9f56c058804f1e57",
    },
    "gradle_wrapper": {
        "source_repository": "https://github.com/gradle/gradle",
        "source_commit": "1cf537a851c635c364a4214885f8b9798051175b",
        "source_artifact": "gradle/wrapper/gradle-wrapper.jar",
        "source_sha256": "ed2c26eba7cfb93cc2b7785d05e534f07b5b48b5e7fc941921cd098628abca58",
        "license": "Apache-2.0",
        "license_source_path": "LICENSE",
        "license_source_sha256": "e5bfcf1132c8e12c3fce87d4dfbcb543cfb7202d8fa28ba85c07132e30836437",
        "license_copy_target": "docs/licenses/GRADLE-8.1.1-LICENSE.txt",
        "license_copy_target_sha256": "e5bfcf1132c8e12c3fce87d4dfbcb543cfb7202d8fa28ba85c07132e30836437",
    },
}

EXPECTED_TARGET_COMPONENTS = {
    ".gitattributes": "forge_mdk",
    ".gitignore": "forge_mdk",
    "build.gradle": "forge_mdk",
    "gradle.properties": "forge_mdk",
    "settings.gradle": "forge_mdk",
    "gradle/wrapper/gradle-wrapper.properties": "forge_mdk",
    "src/main/resources/pack.mcmeta": "forge_mdk",
    "src/main/resources/META-INF/mods.toml": "forge_mdk",
    "gradlew": "gradle_wrapper",
    "gradlew.bat": "gradle_wrapper",
    "gradle/wrapper/gradle-wrapper.jar": "gradle_wrapper",
}

EXPECTED_LOCAL_ASSETS = {
    "src/main/resources/advancedrocketrycommunity.png": ("NEW", "MIT"),
    "src/generated/resources/data/advancedrocketrycommunity/structures/empty.nbt": (
        "GENERATED",
        "MIT",
    ),
}

RESOURCE_ROOTS = (
    "src/main/resources/",
    "src/generated/resources/",
)
# ForgeGradle DataGen writes implementation metadata here. It is ignored by Git,
# excluded from the JAR in build.gradle, and rejected by the artifact auditor, so
# it is not a distributable source resource that needs a provenance entry.
EXCLUDED_RESOURCE_PREFIXES = ("src/generated/resources/.cache/",)
EXPECTED_RESOURCE_PATHS = frozenset(
    path
    for path in (*EXPECTED_TARGET_COMPONENTS, *EXPECTED_LOCAL_ASSETS)
    if path.startswith(RESOURCE_ROOTS)
)

REVIEW_METADATA_FIELDS = (
    "record_status",
    "reviewer",
    "reviewed_at",
    "final_status_after_review",
    "reviewed_audited_target_commit",
    "reviewed_content_sha256",
)
REVIEW_DIGEST_DOMAIN = b"arce-v0.0.2-bootstrap-provenance-review-v1\0"
REVIEW_METADATA_SENTINEL = "<REVIEW-METADATA>"


def _nonempty_string(value: object) -> bool:
    return isinstance(value, str) and bool(value.strip())


def _validate_lower_hex(
    value: object,
    pattern: re.Pattern[str],
    label: str,
    errors: list[str],
) -> bool:
    if not isinstance(value, str) or pattern.fullmatch(value) is None:
        errors.append(f"{label} must be lowercase {pattern.pattern}")
        return False
    return True


def _validate_source_path(value: object, label: str, errors: list[str]) -> None:
    if not isinstance(value, str):
        errors.append(f"{label} must be a normalized POSIX relative path")
        return
    path_error = relative_path_error(value)
    if path_error:
        errors.append(f"{label} is an unsafe path {value!r}: {path_error}")


def _required_local_file(
    repository_root: Path,
    value: object,
    label: str,
    errors: list[str],
) -> Path | None:
    if not isinstance(value, str):
        errors.append(f"{label} must be a normalized POSIX relative path")
        return None

    path_error = relative_path_error(value)
    if path_error:
        errors.append(f"{label} is an unsafe path {value!r}: {path_error}")
        return None

    candidate = repository_root.joinpath(*PurePosixPath(value).parts)
    cursor = repository_root
    for part in PurePosixPath(value).parts:
        cursor /= part
        if cursor.is_symlink():
            errors.append(f"{label} must not use a symlink: {value}")
            return None

    try:
        candidate.resolve(strict=False).relative_to(repository_root)
    except (OSError, ValueError) as exc:
        errors.append(f"{label} must remain under the repository root: {value} ({exc})")
        return None

    if not candidate.is_file():
        errors.append(f"{label} does not exist as a regular file: {value}")
        return None
    return candidate


def _validate_file_hash(
    path: Path | None,
    declared_hash: object,
    label: str,
    errors: list[str],
) -> None:
    if not _validate_lower_hex(declared_hash, SHA256, f"{label} SHA-256", errors):
        return
    if path is None:
        return
    try:
        actual = file_sha256(path)
    except OSError as exc:
        errors.append(f"Cannot hash {label}: {exc}")
        return
    if actual != declared_hash:
        errors.append(
            f"SHA-256 mismatch for {label}: expected {declared_hash}, got {actual}"
        )


def _load_json(path: Path, errors: list[str]) -> dict[str, Any] | None:
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        errors.append(f"Cannot read bootstrap provenance manifest {path}: {exc}")
        return None
    if not isinstance(document, dict):
        errors.append("Bootstrap provenance manifest must contain a JSON object")
        return None
    return document


def _validate_components(
    repository_root: Path,
    value: object,
    errors: list[str],
) -> dict[str, dict[str, Any]]:
    if not isinstance(value, list):
        errors.append("components must be a JSON array")
        return {}

    components: dict[str, dict[str, Any]] = {}
    for index, component in enumerate(value):
        label = f"components[{index}]"
        if not isinstance(component, dict):
            errors.append(f"{label} must be a JSON object")
            continue

        component_id = component.get("id")
        if not _nonempty_string(component_id):
            errors.append(f"{label}.id must be a non-empty string")
            continue
        assert isinstance(component_id, str)
        if component_id in components:
            errors.append(f"duplicate component id: {component_id}")
            continue
        components[component_id] = component

        repository = component.get("source_repository")
        if not _nonempty_string(repository):
            errors.append(f"component {component_id} source_repository is required")
        else:
            assert isinstance(repository, str)
            parsed = urlparse(repository)
            if parsed.scheme != "https" or not parsed.netloc:
                errors.append(
                    f"component {component_id} source_repository must be an HTTPS URL"
                )

        _validate_lower_hex(
            component.get("source_commit"),
            COMMIT,
            f"component {component_id} source_commit",
            errors,
        )
        if not _nonempty_string(component.get("source_artifact")):
            errors.append(f"component {component_id} source_artifact is required")
        _validate_lower_hex(
            component.get("source_sha256"),
            SHA256,
            f"component {component_id} source_sha256",
            errors,
        )
        _validate_source_path(
            component.get("license_source_path"),
            f"component {component_id} license_source_path",
            errors,
        )
        source_license_hash_valid = _validate_lower_hex(
            component.get("license_source_sha256"),
            SHA256,
            f"component {component_id} license_source_sha256",
            errors,
        )
        copy_license_hash_valid = _validate_lower_hex(
            component.get("license_copy_target_sha256"),
            SHA256,
            f"component {component_id} license_copy_target_sha256",
            errors,
        )

        expected = EXPECTED_COMPONENTS.get(component_id)
        if expected is not None:
            for field, expected_value in expected.items():
                if component.get(field) != expected_value:
                    errors.append(
                        f"component {component_id} {field} must be {expected_value}"
                    )

        copy_path = _required_local_file(
            repository_root,
            component.get("license_copy_target"),
            f"component {component_id} license copy",
            errors,
        )
        _validate_file_hash(
            copy_path,
            component.get("license_copy_target_sha256"),
            f"component {component_id} license copy",
            errors,
        )
        if (
            source_license_hash_valid
            and copy_license_hash_valid
            and component.get("license_source_sha256")
            != component.get("license_copy_target_sha256")
        ):
            errors.append(
                f"component {component_id} exact license source/copy hashes differ"
            )

    actual_ids = set(components)
    expected_ids = set(EXPECTED_COMPONENTS)
    missing = sorted(expected_ids - actual_ids)
    extra = sorted(actual_ids - expected_ids)
    if missing:
        errors.append("missing required components: " + ", ".join(missing))
    if extra:
        errors.append("unexpected components: " + ", ".join(extra))
    return components


def _validate_targets(
    repository_root: Path,
    value: object,
    errors: list[str],
) -> dict[str, dict[str, Any]]:
    if not isinstance(value, list):
        errors.append("targets must be a JSON array")
        return {}

    targets: dict[str, dict[str, Any]] = {}
    for index, target in enumerate(value):
        label = f"targets[{index}]"
        if not isinstance(target, dict):
            errors.append(f"{label} must be a JSON object")
            continue

        target_path = target.get("path")
        if not _nonempty_string(target_path):
            errors.append(f"{label}.path must be a non-empty string")
            continue
        assert isinstance(target_path, str)
        if target_path in targets:
            errors.append(f"duplicate imported target path: {target_path}")
            continue
        targets[target_path] = target

        target_file = _required_local_file(
            repository_root, target_path, f"imported target {target_path}", errors
        )
        _validate_source_path(
            target.get("source_path"),
            f"imported target {target_path} source_path",
            errors,
        )
        _validate_lower_hex(
            target.get("source_sha256"),
            SHA256,
            f"imported target {target_path} source_sha256",
            errors,
        )
        _validate_lower_hex(
            target.get("import_target_sha256"),
            SHA256,
            f"imported target {target_path} import_target_sha256",
            errors,
        )
        _validate_file_hash(
            target_file,
            target.get("current_target_sha256"),
            f"imported target {target_path}",
            errors,
        )

        expected_component = EXPECTED_TARGET_COMPONENTS.get(target_path)
        if expected_component is not None:
            if target.get("component") != expected_component:
                errors.append(
                    f"imported target {target_path} component must be "
                    f"{expected_component}"
                )
            expected_license = EXPECTED_COMPONENTS[expected_component]["license"]
            if target.get("license") != expected_license:
                errors.append(
                    f"imported target {target_path} license must be {expected_license}"
                )

        transformations = target.get("transformations")
        if (
            not isinstance(transformations, list)
            or not transformations
            or any(not _nonempty_string(item) for item in transformations)
        ):
            errors.append(
                f"imported target {target_path} transformations must contain "
                "at least one non-empty string"
            )

    actual_paths = set(targets)
    expected_paths = set(EXPECTED_TARGET_COMPONENTS)
    missing = sorted(expected_paths - actual_paths)
    extra = sorted(actual_paths - expected_paths)
    if missing:
        errors.append("missing required imported targets: " + ", ".join(missing))
    if extra:
        errors.append("unexpected imported targets: " + ", ".join(extra))
    return targets


def _validate_local_assets(
    repository_root: Path,
    value: object,
    errors: list[str],
) -> dict[str, dict[str, Any]]:
    if not isinstance(value, list):
        errors.append("local_assets must be a JSON array")
        return {}

    assets: dict[str, dict[str, Any]] = {}
    for index, asset in enumerate(value):
        label = f"local_assets[{index}]"
        if not isinstance(asset, dict):
            errors.append(f"{label} must be a JSON object")
            continue

        asset_path = asset.get("path")
        if not _nonempty_string(asset_path):
            errors.append(f"{label}.path must be a non-empty string")
            continue
        assert isinstance(asset_path, str)
        if asset_path in assets:
            errors.append(f"duplicate local asset path: {asset_path}")
            continue
        assets[asset_path] = asset

        target_file = _required_local_file(
            repository_root, asset_path, f"local asset {asset_path}", errors
        )
        _validate_file_hash(
            target_file,
            asset.get("target_sha256"),
            f"local asset {asset_path}",
            errors,
        )
        _validate_lower_hex(
            asset.get("introduced_commit"),
            COMMIT,
            f"local asset {asset_path} introduced_commit",
            errors,
        )
        if not _nonempty_string(asset.get("description")):
            errors.append(f"local asset {asset_path} description is required")

        expected = EXPECTED_LOCAL_ASSETS.get(asset_path)
        if expected is not None:
            expected_status, expected_license = expected
            if asset.get("status") != expected_status:
                errors.append(
                    f"local asset {asset_path} status must be {expected_status}"
                )
            if asset.get("license") != expected_license:
                errors.append(
                    f"local asset {asset_path} license must be {expected_license}"
                )

        if asset.get("status") == "GENERATED":
            generator_path = _required_local_file(
                repository_root,
                asset.get("generator_path"),
                f"local asset {asset_path} generator",
                errors,
            )
            _validate_file_hash(
                generator_path,
                asset.get("generator_sha256"),
                f"local asset {asset_path} generator",
                errors,
            )
            if not _nonempty_string(asset.get("generation_command")):
                errors.append(
                    f"local asset {asset_path} generation_command is required"
                )

    actual_paths = set(assets)
    expected_paths = set(EXPECTED_LOCAL_ASSETS)
    missing = sorted(expected_paths - actual_paths)
    extra = sorted(actual_paths - expected_paths)
    if missing:
        errors.append("missing required local assets: " + ", ".join(missing))
    if extra:
        errors.append("unexpected local assets: " + ", ".join(extra))
    return assets


def _repository_resource_files(repository_root: Path) -> set[str]:
    resources: set[str] = set()
    for root_prefix in RESOURCE_ROOTS:
        resource_root = repository_root.joinpath(*PurePosixPath(root_prefix).parts)
        if not resource_root.is_dir():
            continue
        for path in resource_root.rglob("*"):
            if not path.is_file():
                continue
            relative = path.relative_to(repository_root).as_posix()
            if relative.startswith(EXCLUDED_RESOURCE_PREFIXES):
                continue
            resources.add(relative)
    return resources


def _validate_resource_inventory(
    repository_root: Path,
    targets: dict[str, dict[str, Any]],
    assets: dict[str, dict[str, Any]],
    errors: list[str],
) -> None:
    declared = {
        path
        for path in (*targets, *assets)
        if path.startswith(RESOURCE_ROOTS)
    }
    discovered = _repository_resource_files(repository_root)
    missing_records = sorted(discovered - declared)
    stale_records = sorted(declared - discovered)
    if missing_records:
        errors.append(
            "resource files missing provenance entries: " + ", ".join(missing_records)
        )
    if stale_records:
        errors.append(
            "provenance resource entries without files: " + ", ".join(stale_records)
        )


def _parse_markdown_scalar(text: str, field: str) -> object:
    fence = re.search(r"```yaml\s*\n(?P<body>.*?)\n```", text, re.DOTALL)
    scope = fence.group("body") if fence is not None else ""
    match = re.search(rf"^{re.escape(field)}:\s*(.*?)\s*$", scope, re.MULTILINE)
    if match is None:
        return object()
    value = match.group(1)
    if value in ("null", "~"):
        return None
    if len(value) >= 2 and value[0] == value[-1] and value[0] in ("'", '"'):
        return value[1:-1]
    return value


def _valid_iso_date(value: object) -> bool:
    if not isinstance(value, str):
        return False
    try:
        date.fromisoformat(value)
    except ValueError:
        return False
    return True


def _canonical_review_document(document: dict[str, Any]) -> bytes:
    canonical = copy.deepcopy(document)
    canonical["review"] = {
        field: REVIEW_METADATA_SENTINEL for field in REVIEW_METADATA_FIELDS
    }
    return json.dumps(
        canonical,
        ensure_ascii=False,
        separators=(",", ":"),
        sort_keys=True,
    ).encode("utf-8")


def _canonical_review_record(record_text: str) -> bytes:
    fence = re.search(r"```yaml\s*\n(?P<body>.*?)\n```", record_text, re.DOTALL)
    if fence is None:
        return record_text.encode("utf-8")

    body = fence.group("body")
    for field in REVIEW_METADATA_FIELDS:
        body = re.sub(
            rf"^{re.escape(field)}:\s*.*$",
            f"{field}: {REVIEW_METADATA_SENTINEL}",
            body,
            count=1,
            flags=re.MULTILINE,
        )
    canonical = record_text[: fence.start("body")] + body + record_text[fence.end("body") :]
    return canonical.encode("utf-8")


def compute_review_content_sha256(
    document: dict[str, Any], record_text: str
) -> str:
    """Bind approval to every evidence field and the full Markdown record body.

    The six mutable approval metadata values are replaced by fixed sentinels to
    avoid a circular digest. All other manifest values (including unknown future
    fields) and all other Markdown bytes participate in the digest.
    """

    manifest_content = _canonical_review_document(document)
    record_content = _canonical_review_record(record_text)
    digest = hashlib.sha256()
    digest.update(REVIEW_DIGEST_DOMAIN)
    for content in (manifest_content, record_content):
        digest.update(len(content).to_bytes(8, "big"))
        digest.update(content)
    return digest.hexdigest()


def _validate_record_metadata(
    document: dict[str, Any], record_text: str | None, errors: list[str]
) -> None:
    if record_text is None:
        return
    expected_fields = {
        "record_version": str(document.get("schema_version")),
        "scope_version": document.get("scope_version"),
        "import_commit": document.get("import_commit"),
        "audited_target_commit": document.get("audited_target_commit"),
    }
    for field, expected in expected_fields.items():
        observed = _parse_markdown_scalar(record_text, field)
        if observed != expected:
            errors.append(
                f"provenance Markdown {field} does not match the manifest"
            )


def _validate_review(
    review_value: object,
    targets: dict[str, dict[str, Any]],
    record_text: str | None,
    audited_target_commit: object,
    calculated_review_digest: str | None,
    errors: list[str],
) -> str:
    if not isinstance(review_value, dict):
        errors.append("review must be a JSON object")
        return "UNKNOWN"

    status = review_value.get("record_status")
    reviewer = review_value.get("reviewer")
    reviewed_at = review_value.get("reviewed_at")
    final_status = review_value.get("final_status_after_review")
    reviewed_commit = review_value.get("reviewed_audited_target_commit")
    reviewed_digest = review_value.get("reviewed_content_sha256")

    actual_fields = set(review_value)
    expected_fields = set(REVIEW_METADATA_FIELDS)
    if actual_fields != expected_fields:
        missing = sorted(expected_fields - actual_fields)
        extra = sorted(actual_fields - expected_fields)
        if missing:
            errors.append("review is missing required fields: " + ", ".join(missing))
        if extra:
            errors.append("review has unexpected fields: " + ", ".join(extra))

    if status == PENDING_RECORD_STATUS:
        if any(
            value is not None
            for value in (
                reviewer,
                reviewed_at,
                final_status,
                reviewed_commit,
                reviewed_digest,
            )
        ):
            errors.append(
                "pending review must have null approval metadata, including "
                "reviewer, reviewed_at, reviewed commit, and reviewed content digest"
            )
        expected_target_status = PENDING_TARGET_STATUS
        expected_proposed_status: str | None = APPROVED_RECORD_STATUS
    elif status == APPROVED_RECORD_STATUS:
        if not _nonempty_string(reviewer):
            errors.append("approved review requires a non-empty reviewer")
        if not _valid_iso_date(reviewed_at):
            errors.append("approved review requires a valid ISO reviewed_at date")
        if final_status != APPROVED_RECORD_STATUS:
            errors.append(
                "approved review final_status_after_review must be "
                f"{APPROVED_RECORD_STATUS}"
            )
        reviewed_commit_valid = _validate_lower_hex(
            reviewed_commit,
            COMMIT,
            "approved review reviewed_audited_target_commit",
            errors,
        )
        if reviewed_commit_valid and reviewed_commit != audited_target_commit:
            errors.append(
                "approved review is bound to a different audited_target_commit"
            )
        reviewed_digest_valid = _validate_lower_hex(
            reviewed_digest,
            SHA256,
            "approved review reviewed_content_sha256",
            errors,
        )
        if reviewed_digest_valid and calculated_review_digest is None:
            errors.append(
                "approved review content digest cannot be verified without the Markdown record"
            )
        elif reviewed_digest_valid and reviewed_digest != calculated_review_digest:
            errors.append(
                "approved review content digest does not match the current manifest and "
                "Markdown record"
            )
        expected_target_status = APPROVED_RECORD_STATUS
        expected_proposed_status = None
    else:
        errors.append(
            "review.record_status must be "
            f"{PENDING_RECORD_STATUS} or {APPROVED_RECORD_STATUS}"
        )
        return str(status) if status is not None else "UNKNOWN"

    for path, target in targets.items():
        if target.get("status") != expected_target_status:
            errors.append(
                f"imported target {path} status is inconsistent with review state; "
                f"expected {expected_target_status}"
            )
        if target.get("proposed_status_after_review") != expected_proposed_status:
            errors.append(
                f"imported target {path} proposed_status_after_review is "
                "inconsistent with review state"
            )

    if record_text is not None:
        expected_fields = {
            "record_status": status,
            "reviewer": reviewer,
            "reviewed_at": reviewed_at,
            "final_status_after_review": final_status,
            "reviewed_audited_target_commit": reviewed_commit,
            "reviewed_content_sha256": reviewed_digest,
        }
        for field, expected in expected_fields.items():
            observed = _parse_markdown_scalar(record_text, field)
            if observed != expected:
                errors.append(
                    f"provenance Markdown {field} does not match the manifest review state"
                )
    return status


def _manifest_path_under_root(
    repository_root: Path,
    manifest_path: Path,
    errors: list[str],
) -> Path | None:
    if manifest_path.is_absolute():
        try:
            relative = manifest_path.relative_to(repository_root).as_posix()
        except ValueError:
            errors.append("Bootstrap provenance manifest must remain under the repository root")
            return None
    else:
        relative = manifest_path.as_posix()
    return _required_local_file(
        repository_root, relative, "bootstrap provenance manifest", errors
    )


def validate_bootstrap_provenance(
    repository_root: Path = ROOT,
    manifest_path: Path = DEFAULT_MANIFEST,
) -> tuple[list[str], dict[str, int | str]]:
    """Validate imported bootstrap targets, local assets, and review state."""
    repository_root = repository_root.resolve()
    errors: list[str] = []
    details: dict[str, int | str] = {
        "components": 0,
        "targets": 0,
        "local_assets": 0,
        "review_status": "UNKNOWN",
        "review_content_sha256": "UNKNOWN",
    }

    resolved_manifest = _manifest_path_under_root(
        repository_root, manifest_path, errors
    )
    if resolved_manifest is None:
        return errors, details
    document = _load_json(resolved_manifest, errors)
    if document is None:
        return errors, details

    if type(document.get("schema_version")) is not int or document.get(
        "schema_version"
    ) != 2:
        errors.append("schema_version must be integer 2")
    if document.get("scope_version") != "v0.0.2":
        errors.append("scope_version must be v0.0.2")
    _validate_lower_hex(
        document.get("import_commit"), COMMIT, "import_commit", errors
    )
    _validate_lower_hex(
        document.get("audited_target_commit"),
        COMMIT,
        "audited_target_commit",
        errors,
    )

    components = _validate_components(
        repository_root, document.get("components"), errors
    )
    targets = _validate_targets(repository_root, document.get("targets"), errors)
    assets = _validate_local_assets(
        repository_root, document.get("local_assets"), errors
    )
    _validate_resource_inventory(repository_root, targets, assets, errors)
    details["components"] = len(components)
    details["targets"] = len(targets)
    details["local_assets"] = len(assets)

    record_path_value = document.get("record_path")
    if record_path_value != EXPECTED_RECORD_PATH:
        errors.append(f"record_path must be {EXPECTED_RECORD_PATH}")
    record_path = _required_local_file(
        repository_root, record_path_value, "provenance Markdown record", errors
    )
    record_text: str | None = None
    if record_path is not None:
        try:
            record_text = record_path.read_text(encoding="utf-8")
        except (OSError, UnicodeError) as exc:
            errors.append(f"Cannot read provenance Markdown record: {exc}")

    _validate_record_metadata(document, record_text, errors)
    calculated_review_digest = (
        compute_review_content_sha256(document, record_text)
        if record_text is not None
        else None
    )
    if calculated_review_digest is not None:
        details["review_content_sha256"] = calculated_review_digest

    review_status = _validate_review(
        document.get("review"),
        targets,
        record_text,
        document.get("audited_target_commit"),
        calculated_review_digest,
        errors,
    )
    details["review_status"] = review_status
    return errors, details


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--repository-root",
        type=Path,
        default=ROOT,
        help="repository root used to resolve provenance paths",
    )
    parser.add_argument(
        "--manifest",
        type=Path,
        default=DEFAULT_MANIFEST,
        help="machine-readable provenance manifest path",
    )
    parser.add_argument(
        "--print-review-digest",
        action="store_true",
        help="print the canonical digest a human approval must bind",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    errors, details = validate_bootstrap_provenance(
        repository_root=args.repository_root,
        manifest_path=args.manifest,
    )
    if args.print_review_digest and details["review_content_sha256"] != "UNKNOWN":
        print(f"reviewed_content_sha256: {details['review_content_sha256']}")
    if errors:
        for error in errors:
            print(f"[FAIL] {error}")
        return 1

    print(
        "[PASS] Bootstrap provenance: "
        f"{details['components']} components, {details['targets']} imported targets, "
        f"{details['local_assets']} local assets"
    )
    print(
        "[PASS] Provenance review metadata is internally consistent: "
        f"{details['review_status']}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
