#!/usr/bin/env python3
"""Validate the machine-readable v0.0.2 bootstrap provenance record."""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import os
import re
import subprocess
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
EXPECTED_NOTICE_PATH = "THIRD-PARTY-NOTICES.md"

SHA256 = re.compile(r"[0-9a-f]{64}")
COMMIT = re.compile(r"[0-9a-f]{40}")
GIT_OBJECT_ID = re.compile(r"[0-9a-f]{40}")

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
REVIEW_DIGEST_DOMAIN = b"arce-v0.0.2-bootstrap-provenance-review-v3\0"
REVIEW_METADATA_SENTINEL = "<REVIEW-METADATA>"
GIT_TIMEOUT_SECONDS = 15
GIT_REGULAR_FILE_MODES = frozenset(("100644", "100755"))
GIT_TARGET_SNAPSHOTS = ("import", "audited")
GitTreeEntry = tuple[str, str, str]

RECORD_IDENTITY_FIELDS = (
    "record_version",
    "scope_version",
    "import_commit",
    "audited_target_commit",
)
RECORD_ONLY_REVIEW_FIELDS = (
    "record_status",
    "final_status_after_review",
    "reviewed_audited_target_commit",
    "reviewed_content_sha256",
)
INITIAL_RECORD_FIELDS = (
    *RECORD_IDENTITY_FIELDS,
    *RECORD_ONLY_REVIEW_FIELDS,
    "reviewer",
    "reviewed_at",
)

APPROVED_DOCUMENT_CONTRADICTIONS = (
    (
        "pending provenance status",
        re.compile(
            r"\b(?:PENDING_HUMAN_REVIEW|EVIDENCE_COMPLETE_HUMAN_REVIEW_PENDING)\b"
        ),
    ),
    (
        "pending approval prose",
        re.compile(
            r"(?:record_status\s+is\s+intentionally\s+pending|"
            r"does\s+not\s+assign\s+`?THIRD_PARTY_APPROVED`?|"
            r"does\s+not\s+claim\s+human\s+license\s+approval|"
            r"pending\s+human\s+(?:license|decision|review)|"
            r"human\s+review\s+must\s+resolve|"
            r"approval\s+remains\s+pending|"
            r"pending\s+reviewer\s+confirmation|"
            r"unresolved\s+(?:record\s+)?fields)",
            re.IGNORECASE,
        ),
    ),
)
UNCHECKED_CHECKBOX = re.compile(r"^\s*-\s*\[\s\]\s+", re.MULTILINE)
YAML_FENCE = re.compile(
    r"```yaml[^\S\r\n]*\r?\n(?P<body>.*?)(?:\r?\n)```",
    re.DOTALL,
)


class DuplicateJsonKeyError(ValueError):
    """Raised when a provenance JSON object contains an ambiguous duplicate key."""


def _reject_duplicate_json_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise DuplicateJsonKeyError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


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


def _run_git(
    repository_root: Path,
    arguments: list[str],
) -> subprocess.CompletedProcess[bytes]:
    return subprocess.run(
        ["git", "-C", str(repository_root), *arguments],
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=GIT_TIMEOUT_SECONDS,
    )


def _git_error(result: subprocess.CompletedProcess[bytes]) -> str:
    return result.stderr.decode("utf-8", errors="replace").strip() or (
        f"git exited with status {result.returncode}"
    )


def _validate_git_repository(repository_root: Path, errors: list[str]) -> bool:
    try:
        result = _run_git(repository_root, ["rev-parse", "--show-toplevel"])
    except (OSError, subprocess.SubprocessError) as exc:
        errors.append(f"cannot inspect provenance Git repository: {exc}")
        return False
    if result.returncode != 0:
        errors.append(
            "provenance root must be a Git worktree: " + _git_error(result)
        )
        return False
    try:
        discovered = Path(
            result.stdout.decode("utf-8", errors="strict").strip()
        ).resolve()
    except (OSError, UnicodeError) as exc:
        errors.append(f"cannot resolve provenance Git worktree root: {exc}")
        return False
    if os.path.normcase(str(discovered)) != os.path.normcase(str(repository_root)):
        errors.append(
            "provenance repository root does not match the Git worktree root: "
            f"{discovered}"
        )
        return False

    try:
        shallow_result = _run_git(
            repository_root, ["rev-parse", "--is-shallow-repository"]
        )
    except (OSError, subprocess.SubprocessError) as exc:
        errors.append(f"cannot inspect provenance Git history depth: {exc}")
        return False
    if shallow_result.returncode != 0:
        errors.append(
            "cannot inspect provenance Git history depth: " + _git_error(shallow_result)
        )
        return False
    if shallow_result.stdout.strip() == b"true":
        errors.append(
            "provenance validation requires a complete, non-shallow Git history"
        )
        return False
    return True


def _git_commit_exists(
    repository_root: Path,
    commit: str,
    label: str,
    errors: list[str],
) -> bool:
    try:
        result = _run_git(
            repository_root,
            ["cat-file", "-e", f"{commit}^{{commit}}"],
        )
    except (OSError, subprocess.SubprocessError) as exc:
        errors.append(f"cannot verify {label} {commit}: {exc}")
        return False
    if result.returncode != 0:
        errors.append(f"{label} does not exist as a local Git commit: {commit}")
        return False
    return True


def _git_commit_parents(
    repository_root: Path,
    commit: str,
    label: str,
    errors: list[str],
) -> list[str] | None:
    try:
        result = _run_git(
            repository_root,
            ["rev-list", "--parents", "-n", "1", commit],
        )
    except (OSError, subprocess.SubprocessError) as exc:
        errors.append(f"cannot inspect parents of {label} {commit}: {exc}")
        return None
    if result.returncode != 0:
        errors.append(
            f"cannot inspect parents of {label} {commit}: {_git_error(result)}"
        )
        return None
    try:
        fields = result.stdout.decode("ascii", errors="strict").strip().split()
    except UnicodeError as exc:
        errors.append(f"cannot decode parents of {label} {commit}: {exc}")
        return None
    if not fields or fields[0] != commit or any(
        COMMIT.fullmatch(parent) is None for parent in fields[1:]
    ):
        errors.append(f"cannot parse parents of {label} {commit}")
        return None
    return fields[1:]


def _git_tree_entry(
    repository_root: Path,
    commit: str,
    path: str,
    label: str,
    errors: list[str],
) -> tuple[bool, GitTreeEntry | None]:
    try:
        result = _run_git(
            repository_root,
            ["ls-tree", "-z", commit, "--", path],
        )
    except (OSError, subprocess.SubprocessError) as exc:
        errors.append(f"cannot inspect {label} at Git commit {commit}: {exc}")
        return False, None
    if result.returncode != 0:
        errors.append(
            f"cannot inspect {label} at Git commit {commit}: {_git_error(result)}"
        )
        return False, None
    records = [record for record in result.stdout.split(b"\0") if record]
    if not records:
        return True, None
    if len(records) != 1:
        errors.append(
            f"cannot parse {label} at Git commit {commit}: expected one tree entry"
        )
        return False, None
    try:
        metadata, observed_path = records[0].split(b"\t", 1)
        mode_bytes, type_bytes, object_bytes = metadata.split(b" ", 2)
        mode = mode_bytes.decode("ascii", errors="strict")
        object_type = type_bytes.decode("ascii", errors="strict")
        object_id = object_bytes.decode("ascii", errors="strict")
    except (ValueError, UnicodeError) as exc:
        errors.append(f"cannot parse {label} at Git commit {commit}: {exc}")
        return False, None
    if observed_path != path.encode("utf-8") or COMMIT.fullmatch(object_id) is None:
        errors.append(f"cannot parse {label} at Git commit {commit}")
        return False, None
    return True, (mode, object_type, object_id)


def _validate_git_tree_snapshot(
    repository_root: Path,
    commit: str,
    path: str,
    declared_mode: object,
    declared_object_type: object,
    declared_blob_oid: object,
    label: str,
    errors: list[str],
) -> GitTreeEntry | None:
    entry_valid, entry = _git_tree_entry(
        repository_root, commit, path, label, errors
    )
    if not entry_valid:
        return None
    if entry is None:
        errors.append(f"{label} is missing from Git commit {commit}")
        return None

    mode, object_type, object_id = entry
    if mode not in GIT_REGULAR_FILE_MODES or object_type != "blob":
        errors.append(
            f"{label} must be a regular Git blob with mode 100644 or 100755; "
            f"observed mode={mode} type={object_type}"
        )
    if isinstance(declared_mode, str) and mode != declared_mode:
        errors.append(
            f"Git mode mismatch for {label} at commit {commit}: expected "
            f"{declared_mode}, observed {mode}"
        )
    if isinstance(declared_object_type, str) and object_type != declared_object_type:
        errors.append(
            f"Git object type mismatch for {label} at commit {commit}: expected "
            f"{declared_object_type}, observed {object_type}"
        )
    if isinstance(declared_blob_oid, str) and object_id != declared_blob_oid:
        errors.append(
            f"Git blob OID mismatch for {label} at commit {commit}: expected "
            f"{declared_blob_oid}, observed {object_id}"
        )
    return entry


def _commit_parents_cached(
    repository_root: Path,
    commit: str,
    label: str,
    parent_cache: dict[str, list[str] | None],
    errors: list[str],
) -> list[str] | None:
    if commit not in parent_cache:
        parent_cache[commit] = _git_commit_parents(
            repository_root, commit, label, errors
        )
    return parent_cache[commit]


def _validate_path_changed_from_first_parent(
    repository_root: Path,
    commit: str,
    path: str,
    current_entry: GitTreeEntry | None,
    label: str,
    parent_cache: dict[str, list[str] | None],
    errors: list[str],
) -> None:
    if current_entry is None:
        return
    parents = _commit_parents_cached(
        repository_root, commit, label, parent_cache, errors
    )
    if parents is None:
        return
    if not parents:
        # A root commit is compared to Git's conceptual empty parent tree.
        return

    first_parent = parents[0]
    parent_valid, parent_entry = _git_tree_entry(
        repository_root,
        first_parent,
        path,
        f"{label} first-parent snapshot",
        errors,
    )
    if not parent_valid:
        return
    if parent_entry == current_entry:
        errors.append(
            f"{label} {commit} does not add or change {path} relative to its "
            f"first parent {first_parent}"
        )


def _validate_path_changed_from_every_parent(
    repository_root: Path,
    commit: str,
    path: str,
    current_entry: GitTreeEntry | None,
    label: str,
    parent_cache: dict[str, list[str] | None],
    errors: list[str],
) -> None:
    if current_entry is None:
        return
    parents = _commit_parents_cached(
        repository_root, commit, label, parent_cache, errors
    )
    if parents is None or not parents:
        return

    for parent in parents:
        parent_valid, parent_entry = _git_tree_entry(
            repository_root,
            parent,
            path,
            f"{label} parent snapshot",
            errors,
        )
        if not parent_valid:
            return
        if parent_entry == current_entry:
            errors.append(
                f"{label} {commit} does not introduce or change {path}: the path "
                f"is unchanged in parent {parent}"
            )
            return


def _validate_git_ancestor(
    repository_root: Path,
    ancestor: str,
    descendant: str,
    label: str,
    errors: list[str],
) -> None:
    try:
        result = _run_git(
            repository_root,
            ["merge-base", "--is-ancestor", ancestor, descendant],
        )
    except (OSError, subprocess.SubprocessError) as exc:
        errors.append(f"cannot verify {label}: {exc}")
        return
    if result.returncode == 1:
        errors.append(
            f"{label} has an invalid ancestry: {ancestor} is not an ancestor of "
            f"{descendant}"
        )
    elif result.returncode != 0:
        errors.append(f"cannot verify {label}: {_git_error(result)}")


def _git_blob(
    repository_root: Path,
    commit: str,
    path: str,
    label: str,
    errors: list[str],
) -> bytes | None:
    try:
        result = _run_git(
            repository_root,
            ["cat-file", "blob", f"{commit}:{path}"],
        )
    except (OSError, subprocess.SubprocessError) as exc:
        errors.append(f"cannot read {label} from Git commit {commit}: {exc}")
        return None
    if result.returncode != 0:
        errors.append(
            f"{label} is missing from Git commit {commit}: {_git_error(result)}"
        )
        return None
    return result.stdout


def _git_text_attributes(
    repository_root: Path,
    commit: str,
    path: str,
    errors: list[str],
) -> tuple[str | None, str | None]:
    try:
        result = _run_git(
            repository_root,
            ["check-attr", f"--source={commit}", "text", "eol", "--", path],
        )
    except (OSError, subprocess.SubprocessError) as exc:
        errors.append(
            f"cannot inspect Git attributes for {path} at {commit}: {exc}"
        )
        return None, None
    if result.returncode != 0:
        errors.append(
            f"cannot inspect Git attributes for {path} at {commit}: "
            + _git_error(result)
        )
        return None, None

    attributes: dict[str, str] = {}
    try:
        output = result.stdout.decode("utf-8", errors="strict")
    except UnicodeError as exc:
        errors.append(
            f"cannot decode Git attributes for {path} at {commit}: {exc}"
        )
        return None, None
    for line in output.splitlines():
        parts = line.rsplit(": ", 2)
        if len(parts) == 3:
            attributes[parts[1]] = parts[2]
    return attributes.get("text"), attributes.get("eol")


def _git_blob_hash_candidates(
    repository_root: Path,
    commit: str,
    path: str,
    blob: bytes,
    errors: list[str],
) -> dict[str, str]:
    candidates = {"raw Git blob": hashlib.sha256(blob).hexdigest()}
    text_attribute, eol_attribute = _git_text_attributes(
        repository_root, commit, path, errors
    )
    if text_attribute in ("set", "auto") and eol_attribute in ("lf", "crlf"):
        canonical_lf = blob.replace(b"\r\n", b"\n")
        materialized = (
            canonical_lf
            if eol_attribute == "lf"
            else canonical_lf.replace(b"\n", b"\r\n")
        )
        candidates[f"declared eol={eol_attribute} materialization"] = hashlib.sha256(
            materialized
        ).hexdigest()
    return candidates


def _validate_git_raw_blob_hash(
    repository_root: Path,
    commit: str,
    path: str,
    declared_hash: object,
    label: str,
    errors: list[str],
) -> None:
    if not isinstance(declared_hash, str) or SHA256.fullmatch(declared_hash) is None:
        return
    blob = _git_blob(repository_root, commit, path, label, errors)
    if blob is None:
        return
    observed_hash = hashlib.sha256(blob).hexdigest()
    if declared_hash != observed_hash:
        errors.append(
            f"raw Git blob SHA-256 mismatch for {label} at Git commit {commit}: "
            f"expected {declared_hash}, observed {observed_hash}"
        )


def _validate_git_materialized_hash(
    repository_root: Path,
    commit: str,
    path: str,
    declared_hash: object,
    label: str,
    errors: list[str],
) -> None:
    if not isinstance(declared_hash, str) or SHA256.fullmatch(declared_hash) is None:
        return
    blob = _git_blob(repository_root, commit, path, label, errors)
    if blob is None:
        return
    candidates = _git_blob_hash_candidates(
        repository_root, commit, path, blob, errors
    )
    raw_hash = candidates["raw Git blob"]
    materialized_candidates = {
        description: digest
        for description, digest in candidates.items()
        if description != "raw Git blob"
    }
    if declared_hash == raw_hash or declared_hash not in materialized_candidates.values():
        observed = ", ".join(
            f"{description}={digest}"
            for description, digest in sorted(candidates.items())
        )
        errors.append(
            f"worktree materialized SHA-256 for {label} at Git commit {commit} "
            f"must match a non-raw declared-EOL materialization: expected "
            f"{declared_hash}; {observed}"
        )


def _load_json(path: Path, errors: list[str]) -> dict[str, Any] | None:
    try:
        document = json.loads(
            path.read_text(encoding="utf-8"),
            object_pairs_hook=_reject_duplicate_json_keys,
        )
    except (OSError, UnicodeError, json.JSONDecodeError, DuplicateJsonKeyError) as exc:
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
        for deprecated_field in ("import_target_sha256", "current_target_sha256"):
            if deprecated_field in target:
                errors.append(
                    f"imported target {target_path} {deprecated_field} is ambiguous "
                    "in schema 3; use raw Git blob metadata and the optional "
                    "worktree_materialized_sha256 field"
                )
        for snapshot in GIT_TARGET_SNAPSHOTS:
            mode_field = f"{snapshot}_target_git_mode"
            object_type_field = f"{snapshot}_target_git_object_type"
            oid_field = f"{snapshot}_target_git_blob_oid"
            raw_hash_field = f"{snapshot}_target_raw_blob_sha256"
            mode = target.get(mode_field)
            if mode not in GIT_REGULAR_FILE_MODES:
                errors.append(
                    f"imported target {target_path} {mode_field} must be 100644 "
                    "or 100755"
                )
            if target.get(object_type_field) != "blob":
                errors.append(
                    f"imported target {target_path} {object_type_field} must be blob"
                )
            _validate_lower_hex(
                target.get(oid_field),
                GIT_OBJECT_ID,
                f"imported target {target_path} {oid_field}",
                errors,
            )
            _validate_lower_hex(
                target.get(raw_hash_field),
                SHA256,
                f"imported target {target_path} {raw_hash_field}",
                errors,
            )

        audited_raw_hash = target.get("audited_target_raw_blob_sha256")
        materialized_hash = target.get("worktree_materialized_sha256")
        if materialized_hash is not None:
            materialized_valid = _validate_lower_hex(
                materialized_hash,
                SHA256,
                f"imported target {target_path} worktree_materialized_sha256",
                errors,
            )
            if materialized_valid and materialized_hash == audited_raw_hash:
                errors.append(
                    f"imported target {target_path} worktree_materialized_sha256 "
                    "is unnecessary because it equals the audited raw Git blob hash"
                )
        worktree_hash = (
            materialized_hash if materialized_hash is not None else audited_raw_hash
        )
        _validate_file_hash(
            target_file,
            worktree_hash,
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
        for snapshot in ("introduced", "audited"):
            mode_field = f"{snapshot}_git_mode"
            object_type_field = f"{snapshot}_git_object_type"
            oid_field = f"{snapshot}_git_blob_oid"
            raw_hash_field = f"{snapshot}_raw_blob_sha256"
            if asset.get(mode_field) not in GIT_REGULAR_FILE_MODES:
                errors.append(
                    f"local asset {asset_path} {mode_field} must be 100644 or 100755"
                )
            if asset.get(object_type_field) != "blob":
                errors.append(
                    f"local asset {asset_path} {object_type_field} must be blob"
                )
            _validate_lower_hex(
                asset.get(oid_field),
                GIT_OBJECT_ID,
                f"local asset {asset_path} {oid_field}",
                errors,
            )
            _validate_lower_hex(
                asset.get(raw_hash_field),
                SHA256,
                f"local asset {asset_path} {raw_hash_field}",
                errors,
            )
        if asset.get("target_sha256") != asset.get("audited_raw_blob_sha256"):
            errors.append(
                f"local asset {asset_path} target_sha256 must equal "
                "audited_raw_blob_sha256 for a binary resource"
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


def _validate_git_history(
    repository_root: Path,
    document: dict[str, Any],
    targets: dict[str, dict[str, Any]],
    assets: dict[str, dict[str, Any]],
    errors: list[str],
) -> None:
    if not _validate_git_repository(repository_root, errors):
        return

    import_commit = document.get("import_commit")
    audited_commit = document.get("audited_target_commit")
    import_valid = isinstance(import_commit, str) and COMMIT.fullmatch(import_commit)
    audited_valid = isinstance(audited_commit, str) and COMMIT.fullmatch(audited_commit)
    import_exists = bool(
        import_valid
        and _git_commit_exists(
            repository_root, import_commit, "import_commit", errors
        )
    )
    audited_exists = bool(
        audited_valid
        and _git_commit_exists(
            repository_root, audited_commit, "audited_target_commit", errors
        )
    )

    try:
        head_result = _run_git(repository_root, ["rev-parse", "--verify", "HEAD"])
    except (OSError, subprocess.SubprocessError) as exc:
        errors.append(f"cannot resolve provenance repository HEAD: {exc}")
        head_result = None
    head_commit: str | None = None
    if head_result is not None:
        if head_result.returncode != 0:
            errors.append(
                "provenance repository must have a valid HEAD commit: "
                + _git_error(head_result)
            )
        else:
            try:
                candidate_head = head_result.stdout.decode(
                    "ascii", errors="strict"
                ).strip()
            except UnicodeError as exc:
                errors.append(f"cannot decode provenance repository HEAD: {exc}")
            else:
                if COMMIT.fullmatch(candidate_head) is None:
                    errors.append(
                        "provenance repository HEAD is not a lowercase 40-character "
                        "Git commit ID"
                    )
                else:
                    head_commit = candidate_head

    if import_exists and audited_exists:
        assert isinstance(import_commit, str)
        assert isinstance(audited_commit, str)
        _validate_git_ancestor(
            repository_root,
            import_commit,
            audited_commit,
            "import_commit -> audited_target_commit",
            errors,
        )
    if audited_exists and head_commit is not None:
        assert isinstance(audited_commit, str)
        _validate_git_ancestor(
            repository_root,
            audited_commit,
            head_commit,
            "audited_target_commit -> HEAD",
            errors,
        )

    parent_cache: dict[str, list[str] | None] = {}
    for path, target in targets.items():
        if import_exists:
            assert isinstance(import_commit, str)
            import_entry = _validate_git_tree_snapshot(
                repository_root,
                import_commit,
                path,
                target.get("import_target_git_mode"),
                target.get("import_target_git_object_type"),
                target.get("import_target_git_blob_oid"),
                f"imported target {path} import snapshot",
                errors,
            )
            _validate_path_changed_from_first_parent(
                repository_root,
                import_commit,
                path,
                import_entry,
                f"import_commit for imported target {path}",
                parent_cache,
                errors,
            )
            _validate_git_raw_blob_hash(
                repository_root,
                import_commit,
                path,
                target.get("import_target_raw_blob_sha256"),
                f"imported target {path} import snapshot",
                errors,
            )
        if audited_exists:
            assert isinstance(audited_commit, str)
            _validate_git_tree_snapshot(
                repository_root,
                audited_commit,
                path,
                target.get("audited_target_git_mode"),
                target.get("audited_target_git_object_type"),
                target.get("audited_target_git_blob_oid"),
                f"imported target {path} audited snapshot",
                errors,
            )
            _validate_git_raw_blob_hash(
                repository_root,
                audited_commit,
                path,
                target.get("audited_target_raw_blob_sha256"),
                f"imported target {path} audited snapshot",
                errors,
            )
            materialized_hash = target.get("worktree_materialized_sha256")
            if materialized_hash is not None:
                _validate_git_materialized_hash(
                    repository_root,
                    audited_commit,
                    path,
                    materialized_hash,
                    f"imported target {path} audited snapshot",
                    errors,
                )
        if audited_exists and head_commit is not None:
            _validate_git_tree_snapshot(
                repository_root,
                head_commit,
                path,
                target.get("audited_target_git_mode"),
                target.get("audited_target_git_object_type"),
                target.get("audited_target_git_blob_oid"),
                f"imported target {path} HEAD snapshot",
                errors,
            )
            _validate_git_raw_blob_hash(
                repository_root,
                head_commit,
                path,
                target.get("audited_target_raw_blob_sha256"),
                f"imported target {path} HEAD snapshot",
                errors,
            )
            materialized_hash = target.get("worktree_materialized_sha256")
            if materialized_hash is not None:
                _validate_git_materialized_hash(
                    repository_root,
                    head_commit,
                    path,
                    materialized_hash,
                    f"imported target {path} HEAD snapshot",
                    errors,
                )

    introduced_commits: dict[str, bool] = {}
    for path, asset in assets.items():
        introduced_commit = asset.get("introduced_commit")
        if not isinstance(introduced_commit, str) or COMMIT.fullmatch(
            introduced_commit
        ) is None:
            continue
        if introduced_commit not in introduced_commits:
            introduced_commits[introduced_commit] = _git_commit_exists(
                repository_root,
                introduced_commit,
                f"local asset introduced_commit for {path}",
                errors,
            )
        introduced_exists = introduced_commits[introduced_commit]
        if introduced_exists and audited_exists:
            assert isinstance(audited_commit, str)
            _validate_git_ancestor(
                repository_root,
                introduced_commit,
                audited_commit,
                f"local asset {path} introduced_commit -> audited_target_commit",
                errors,
            )
        if introduced_exists:
            introduction_entry = _validate_git_tree_snapshot(
                repository_root,
                introduced_commit,
                path,
                asset.get("introduced_git_mode"),
                asset.get("introduced_git_object_type"),
                asset.get("introduced_git_blob_oid"),
                f"local asset {path} introduction snapshot",
                errors,
            )
            _validate_path_changed_from_every_parent(
                repository_root,
                introduced_commit,
                path,
                introduction_entry,
                f"introduced_commit for local asset {path}",
                parent_cache,
                errors,
            )
            _validate_git_raw_blob_hash(
                repository_root,
                introduced_commit,
                path,
                asset.get("introduced_raw_blob_sha256"),
                f"local asset {path} introduction snapshot",
                errors,
            )
        if audited_exists:
            assert isinstance(audited_commit, str)
            _validate_git_tree_snapshot(
                repository_root,
                audited_commit,
                path,
                asset.get("audited_git_mode"),
                asset.get("audited_git_object_type"),
                asset.get("audited_git_blob_oid"),
                f"local asset {path} audited snapshot",
                errors,
            )
            _validate_git_raw_blob_hash(
                repository_root,
                audited_commit,
                path,
                asset.get("audited_raw_blob_sha256"),
                f"local asset {path} audited snapshot",
                errors,
            )
        if audited_exists and head_commit is not None:
            _validate_git_tree_snapshot(
                repository_root,
                head_commit,
                path,
                asset.get("audited_git_mode"),
                asset.get("audited_git_object_type"),
                asset.get("audited_git_blob_oid"),
                f"local asset {path} HEAD snapshot",
                errors,
            )
            _validate_git_raw_blob_hash(
                repository_root,
                head_commit,
                path,
                asset.get("audited_raw_blob_sha256"),
                f"local asset {path} HEAD snapshot",
                errors,
            )

        generator_path = asset.get("generator_path")
        if audited_exists and isinstance(generator_path, str):
            assert isinstance(audited_commit, str)
            audited_generator_entry = _validate_git_tree_snapshot(
                repository_root,
                audited_commit,
                generator_path,
                None,
                None,
                None,
                f"local asset {path} generator audited snapshot",
                errors,
            )
            _validate_git_raw_blob_hash(
                repository_root,
                audited_commit,
                generator_path,
                asset.get("generator_sha256"),
                f"local asset {path} generator audited snapshot",
                errors,
            )
            if head_commit is not None:
                head_generator_entry = _validate_git_tree_snapshot(
                    repository_root,
                    head_commit,
                    generator_path,
                    None,
                    None,
                    None,
                    f"local asset {path} generator HEAD snapshot",
                    errors,
                )
                _validate_git_raw_blob_hash(
                    repository_root,
                    head_commit,
                    generator_path,
                    asset.get("generator_sha256"),
                    f"local asset {path} generator HEAD snapshot",
                    errors,
                )
                if (
                    audited_generator_entry is not None
                    and head_generator_entry is not None
                    and audited_generator_entry != head_generator_entry
                ):
                    errors.append(
                        f"local asset {path} generator HEAD snapshot must exactly "
                        "match its audited Git tree entry"
                    )


def _parse_markdown_scalar(text: str, field: str) -> object:
    fence = YAML_FENCE.search(text)
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


def _parse_markdown_scalar_occurrences(text: str, field: str) -> list[object]:
    values: list[object] = []
    for fence in YAML_FENCE.finditer(text):
        body = fence.group("body")
        for match in re.finditer(
            rf"^{re.escape(field)}:\s*(.*?)\s*$", body, re.MULTILINE
        ):
            value = match.group(1)
            if value in ("null", "~"):
                values.append(None)
            elif (
                len(value) >= 2
                and value[0] == value[-1]
                and value[0] in ("'", '"')
            ):
                values.append(value[1:-1])
            else:
                values.append(value)
    return values


def _parse_markdown_target_scalar_occurrences(text: str, field: str) -> list[object]:
    values: list[object] = []
    for fence in YAML_FENCE.finditer(text):
        body = fence.group("body")
        if (
            re.search(r"^status:\s*", body, re.MULTILINE) is None
            or re.search(r"^proposed_status_after_review:\s*", body, re.MULTILINE)
            is None
        ):
            continue
        for match in re.finditer(
            rf"^{re.escape(field)}:\s*(.*?)\s*$", body, re.MULTILINE
        ):
            value = match.group(1)
            if value in ("null", "~"):
                values.append(None)
            elif (
                len(value) >= 2
                and value[0] == value[-1]
                and value[0] in ("'", '"')
            ):
                values.append(value[1:-1])
            else:
                values.append(value)
    return values


def _field_count(yaml_body: str, field: str) -> int:
    return len(
        re.findall(rf"^{re.escape(field)}:\s*.*?$", yaml_body, re.MULTILINE)
    )


def _validate_record_yaml_structure(record_text: str, errors: list[str]) -> None:
    bodies = [match.group("body") for match in YAML_FENCE.finditer(record_text)]
    if not bodies:
        errors.append("provenance Markdown must contain an initial YAML metadata block")
        return

    initial_body = bodies[0]
    for field in INITIAL_RECORD_FIELDS:
        if _field_count(initial_body, field) != 1:
            errors.append(
                f"initial provenance YAML {field} must occur exactly once"
            )

    record_only_fields = (*RECORD_IDENTITY_FIELDS, *RECORD_ONLY_REVIEW_FIELDS)
    for body in bodies[1:]:
        for field in record_only_fields:
            if _field_count(body, field):
                errors.append(
                    f"reserved provenance YAML field {field} must occur only in "
                    "the initial metadata block"
                )

        is_target_review_block = bool(
            _field_count(body, "status")
            and _field_count(body, "proposed_status_after_review")
        )
        for field in ("reviewer", "reviewed_at"):
            count = _field_count(body, field)
            if count and not is_target_review_block:
                errors.append(
                    f"reserved provenance YAML field {field} outside the initial "
                    "metadata block must belong to a target review block"
                )
            elif is_target_review_block and count != 1:
                errors.append(
                    f"provenance target review YAML {field} must occur exactly once"
                )
        if is_target_review_block:
            for field in ("status", "proposed_status_after_review"):
                if _field_count(body, field) != 1:
                    errors.append(
                        f"provenance target review YAML {field} must occur exactly once"
                    )


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


def _canonical_review_record(record_content: bytes) -> bytes:
    record_text = record_content.decode("utf-8", errors="strict")
    fence = YAML_FENCE.search(record_text)
    if fence is None:
        return record_content

    body = fence.group("body")
    for field in REVIEW_METADATA_FIELDS:
        body = re.sub(
            rf"^{re.escape(field)}:[^\r\n]*(?P<eol>\r?\n|$)",
            lambda match, field=field: (
                f"{field}: {REVIEW_METADATA_SENTINEL}{match.group('eol')}"
            ),
            body,
            count=1,
            flags=re.MULTILINE,
        )
    canonical = record_text[: fence.start("body")] + body + record_text[fence.end("body") :]
    return canonical.encode("utf-8")


def compute_review_content_sha256(
    document: dict[str, Any], record_content: bytes, notice_content: bytes
) -> str:
    """Bind approval to every evidence field, record byte, and notice byte.

    The six mutable approval metadata values are replaced by fixed sentinels to
    avoid a circular digest. All other manifest values (including unknown future
    fields), all other provenance Markdown bytes, and the complete third-party
    notice bytes participate in the digest.
    """

    manifest_content = _canonical_review_document(document)
    canonical_record_content = _canonical_review_record(record_content)
    digest = hashlib.sha256()
    digest.update(REVIEW_DIGEST_DOMAIN)
    for content in (manifest_content, canonical_record_content, notice_content):
        digest.update(len(content).to_bytes(8, "big"))
        digest.update(content)
    return digest.hexdigest()


def _validate_approved_document_state(
    record_text: str,
    notice_text: str,
    reviewer: object,
    reviewed_at: object,
    errors: list[str],
) -> None:
    documents = (
        ("provenance Markdown", record_text),
        ("third-party notice", notice_text),
    )
    for label, text in documents:
        for description, pattern in APPROVED_DOCUMENT_CONTRADICTIONS:
            if pattern.search(text):
                errors.append(
                    f"approved review {label} still contains {description}"
                )
        if UNCHECKED_CHECKBOX.search(text):
            errors.append(
                f"approved review {label} still contains an unchecked checklist item"
            )

    expected_record_fields: dict[str, object] = {
        "status": APPROVED_RECORD_STATUS,
        "proposed_status_after_review": None,
        "reviewer": reviewer,
        "reviewed_at": reviewed_at,
    }
    for field, expected in expected_record_fields.items():
        occurrences = _parse_markdown_scalar_occurrences(record_text, field)
        if not occurrences:
            errors.append(
                f"approved provenance Markdown must retain reviewed {field} fields"
            )
        elif any(value != expected for value in occurrences):
            errors.append(
                f"approved provenance Markdown {field} fields contradict the "
                "manifest review state"
            )

    expected_notice_fields: dict[str, object] = {
        "status": APPROVED_RECORD_STATUS,
        "reviewer": reviewer,
        "reviewed_at": reviewed_at,
    }
    for field, expected in expected_notice_fields.items():
        occurrences = _parse_markdown_scalar_occurrences(notice_text, field)
        if occurrences != [expected]:
            errors.append(
                f"approved third-party notice {field} must occur exactly once and "
                "match the manifest review state"
            )


def _validate_pending_document_state(
    record_text: str,
    notice_text: str,
    errors: list[str],
) -> None:
    expected_notice_fields: dict[str, object] = {
        "status": PENDING_TARGET_STATUS,
        "reviewer": None,
        "reviewed_at": None,
    }
    for field, expected in expected_notice_fields.items():
        occurrences = _parse_markdown_scalar_occurrences(notice_text, field)
        if occurrences != [expected]:
            errors.append(
                f"pending third-party notice {field} must occur exactly once and "
                "match the manifest review state"
            )

    expected_record_target_fields: dict[str, object] = {
        "status": PENDING_TARGET_STATUS,
        "proposed_status_after_review": APPROVED_RECORD_STATUS,
        "reviewer": None,
        "reviewed_at": None,
    }
    for field, expected in expected_record_target_fields.items():
        occurrences = _parse_markdown_target_scalar_occurrences(record_text, field)
        if not occurrences:
            errors.append(
                f"pending provenance Markdown must retain target {field} fields"
            )
        elif any(value != expected for value in occurrences):
            errors.append(
                f"pending provenance Markdown target {field} fields contradict the "
                "manifest review state"
            )


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
    notice_text: str | None,
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
        if record_text is not None and notice_text is not None:
            _validate_pending_document_state(record_text, notice_text, errors)
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
                "approved review content digest does not match the current manifest, "
                "provenance record, and third-party notice"
            )
        expected_target_status = APPROVED_RECORD_STATUS
        expected_proposed_status = None
        if record_text is None or notice_text is None:
            errors.append(
                "approved review requires readable provenance Markdown and "
                "third-party notice documents"
            )
        else:
            _validate_approved_document_state(
                record_text,
                notice_text,
                reviewer,
                reviewed_at,
                errors,
            )
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
    ) != 3:
        errors.append("schema_version must be integer 3")
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
    _validate_git_history(repository_root, document, targets, assets, errors)
    details["components"] = len(components)
    details["targets"] = len(targets)
    details["local_assets"] = len(assets)

    record_path_value = document.get("record_path")
    if record_path_value != EXPECTED_RECORD_PATH:
        errors.append(f"record_path must be {EXPECTED_RECORD_PATH}")
    record_path = _required_local_file(
        repository_root, record_path_value, "provenance Markdown record", errors
    )
    record_content: bytes | None = None
    record_text: str | None = None
    if record_path is not None:
        try:
            record_content = record_path.read_bytes()
            record_text = record_content.decode("utf-8", errors="strict")
        except (OSError, UnicodeError) as exc:
            record_content = None
            record_text = None
            errors.append(f"Cannot read provenance Markdown record: {exc}")

    notice_path = _required_local_file(
        repository_root,
        EXPECTED_NOTICE_PATH,
        "third-party notice",
        errors,
    )
    notice_content: bytes | None = None
    notice_text: str | None = None
    if notice_path is not None:
        try:
            notice_content = notice_path.read_bytes()
            notice_text = notice_content.decode("utf-8", errors="strict")
        except (OSError, UnicodeError) as exc:
            notice_content = None
            notice_text = None
            errors.append(f"Cannot read third-party notice: {exc}")

    if record_text is not None:
        _validate_record_yaml_structure(record_text, errors)
    _validate_record_metadata(document, record_text, errors)
    calculated_review_digest = (
        compute_review_content_sha256(document, record_content, notice_content)
        if record_content is not None and notice_content is not None
        else None
    )
    if calculated_review_digest is not None:
        details["review_content_sha256"] = calculated_review_digest

    review_status = _validate_review(
        document.get("review"),
        targets,
        record_text,
        notice_text,
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
