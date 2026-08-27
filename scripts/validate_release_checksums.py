#!/usr/bin/env python3
"""Validate v0.0.2 release evidence checksums and distributable metadata."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path, PurePosixPath, PureWindowsPath
from typing import Collection


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CHECKSUMS = Path("docs/releases/v0.0.2/checksums.txt")
DEFAULT_EVIDENCE_DIR = Path("docs/releases/v0.0.2/evidence")
DEFAULT_CONTENT_MANIFEST = (
    DEFAULT_EVIDENCE_DIR / "artifact/jar-content-manifest.json"
)
SHA256 = re.compile(r"[0-9a-f]{64}")
CHECKSUM_LINE = re.compile(r"^([0-9a-f]{64})[ \t]+(.+)$")
HASH_CHUNK_SIZE = 1024 * 1024


@dataclass(frozen=True)
class ChecksumEntry:
    sha256: str
    path: str
    line_number: int


@dataclass(frozen=True)
class ArtifactMetadata:
    filename: str
    sha256: str


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while True:
            chunk = stream.read(HASH_CHUNK_SIZE)
            if not chunk:
                return digest.hexdigest()
            digest.update(chunk)


def relative_path_error(value: str) -> str | None:
    """Return why a checksum path is unsafe, or ``None`` when it is portable."""
    if not value:
        return "path is empty"
    if value != value.strip():
        return "leading or trailing whitespace is not allowed"
    if any(ord(character) < 32 or ord(character) == 127 for character in value):
        return "control characters are not allowed"
    if "\\" in value:
        return "backslashes are not allowed; use POSIX separators"

    posix_path = PurePosixPath(value)
    windows_path = PureWindowsPath(value)
    if posix_path.is_absolute() or windows_path.is_absolute() or windows_path.drive:
        return "absolute or drive-qualified paths are not allowed"

    raw_parts = value.split("/")
    if any(part in ("", ".", "..") for part in raw_parts):
        return "empty, current-directory, or traversal path segments are not allowed"
    if posix_path.as_posix() != value:
        return "path is not a normalized POSIX relative path"
    return None


def parse_checksum_text(text: str) -> tuple[list[ChecksumEntry], list[str]]:
    entries: list[ChecksumEntry] = []
    errors: list[str] = []
    seen: dict[str, int] = {}

    for line_number, line in enumerate(text.splitlines(), start=1):
        if not line.strip() or line.lstrip().startswith("#"):
            continue

        match = CHECKSUM_LINE.fullmatch(line)
        if not match:
            errors.append(
                f"line {line_number}: expected lowercase SHA-256 and relative path"
            )
            continue

        checksum, relative = match.groups()
        path_error = relative_path_error(relative)
        if path_error:
            errors.append(f"line {line_number}: unsafe path {relative!r}: {path_error}")
            continue

        previous_line = seen.get(relative)
        if previous_line is not None:
            errors.append(
                f"line {line_number}: duplicate path {relative!r} "
                f"(first listed on line {previous_line})"
            )
            continue

        seen[relative] = line_number
        entries.append(ChecksumEntry(checksum, relative, line_number))

    return entries, errors


def read_tracked_files(repository_root: Path) -> tuple[set[str], str | None]:
    command = (
        "git",
        "-c",
        f"safe.directory={repository_root.as_posix()}",
        "-C",
        str(repository_root),
        "ls-files",
        "-z",
        "--cached",
    )
    try:
        completed = subprocess.run(command, check=True, capture_output=True)
        paths = {
            value.decode("utf-8")
            for value in completed.stdout.split(b"\0")
            if value
        }
        return paths, None
    except (OSError, subprocess.CalledProcessError, UnicodeError) as exc:
        return set(), f"Cannot enumerate committed repository files: {exc}"


def repository_relative(path: Path, repository_root: Path) -> tuple[str | None, str | None]:
    try:
        relative = path.resolve().relative_to(repository_root.resolve()).as_posix()
    except (OSError, ValueError) as exc:
        return None, f"Path must remain under the repository root: {path} ({exc})"
    return relative, None


def load_artifact_metadata(path: Path) -> tuple[ArtifactMetadata | None, list[str]]:
    errors: list[str] = []
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        return None, [f"Cannot read committed JAR content manifest {path}: {exc}"]

    if not isinstance(document, dict):
        return None, ["JAR content manifest must contain a JSON object"]

    filename = document.get("artifact")
    checksum = document.get("artifact_sha256")
    if not isinstance(filename, str) or not filename:
        errors.append("JAR content manifest is missing string artifact metadata")
    elif (
        PurePosixPath(filename).name != filename
        or PureWindowsPath(filename).name != filename
        or not filename.endswith(".jar")
    ):
        errors.append("JAR content manifest artifact must be a plain .jar filename")

    if not isinstance(checksum, str) or SHA256.fullmatch(checksum) is None:
        errors.append(
            "JAR content manifest is missing lowercase artifact_sha256 metadata"
        )

    if errors:
        return None, errors
    return ArtifactMetadata(filename, checksum), []


def _absolute_from_root(path: Path, repository_root: Path) -> Path:
    return path if path.is_absolute() else repository_root / path


def validate_release_checksums(
    repository_root: Path = ROOT,
    checksums_path: Path = DEFAULT_CHECKSUMS,
    evidence_dir: Path = DEFAULT_EVIDENCE_DIR,
    content_manifest_path: Path = DEFAULT_CONTENT_MANIFEST,
    artifact_path: Path | None = None,
    tracked_files: Collection[str] | None = None,
) -> tuple[list[str], dict[str, int | str | bool]]:
    """Validate committed evidence and the one external distributable JAR entry."""
    repository_root = repository_root.resolve()
    checksums_path = _absolute_from_root(checksums_path, repository_root)
    evidence_dir = _absolute_from_root(evidence_dir, repository_root)
    content_manifest_path = _absolute_from_root(
        content_manifest_path, repository_root
    )

    errors: list[str] = []
    details: dict[str, int | str | bool] = {
        "entries": 0,
        "committed_files_checked": 0,
        "evidence_files": 0,
        "artifact_verified": False,
    }

    checksums_relative, path_error = repository_relative(
        checksums_path, repository_root
    )
    if path_error:
        errors.append(path_error)
    evidence_relative, evidence_path_error = repository_relative(
        evidence_dir, repository_root
    )
    if evidence_path_error:
        errors.append(evidence_path_error)
    manifest_relative, manifest_path_error = repository_relative(
        content_manifest_path, repository_root
    )
    if manifest_path_error:
        errors.append(manifest_path_error)
    if errors:
        return errors, details

    if tracked_files is None:
        tracked, tracked_error = read_tracked_files(repository_root)
        if tracked_error:
            errors.append(tracked_error)
            return errors, details
    else:
        tracked = {str(path).replace("\\", "/") for path in tracked_files}

    assert checksums_relative is not None
    assert evidence_relative is not None
    assert manifest_relative is not None

    if checksums_relative not in tracked:
        errors.append(f"Checksum list is not committed: {checksums_relative}")

    try:
        checksum_text = checksums_path.read_text(encoding="utf-8")
    except (OSError, UnicodeError) as exc:
        errors.append(f"Cannot read checksum list {checksums_relative}: {exc}")
        return errors, details

    entries, parse_errors = parse_checksum_text(checksum_text)
    errors.extend(parse_errors)
    details["entries"] = len(entries)
    entries_by_path = {entry.path: entry for entry in entries}

    if checksums_relative in entries_by_path:
        errors.append("Checksum list must not contain a self-referential entry")

    evidence_prefix = f"{evidence_relative.rstrip('/')}/"
    evidence_files = {
        path for path in tracked if path.startswith(evidence_prefix)
    }
    if evidence_dir.is_dir():
        for path in evidence_dir.rglob("*"):
            if not (path.is_file() or path.is_symlink()):
                continue
            relative, relative_error = repository_relative(path, repository_root)
            if relative_error:
                errors.append(relative_error)
            elif relative is not None:
                evidence_files.add(relative)
    else:
        errors.append(f"Evidence directory does not exist: {evidence_relative}")

    details["evidence_files"] = len(evidence_files)
    omitted_evidence = sorted(evidence_files - entries_by_path.keys())
    if omitted_evidence:
        errors.append(
            "Evidence files omitted from checksum list: "
            + ", ".join(omitted_evidence)
        )

    artifact_entries: list[ChecksumEntry] = []
    committed_checked = 0
    for entry in entries:
        if entry.path == checksums_relative:
            continue
        if entry.path not in tracked:
            if PurePosixPath(entry.path).suffix == ".jar":
                artifact_entries.append(entry)
            else:
                errors.append(
                    f"Checksum target is not committed: {entry.path}"
                )
            continue

        candidate = repository_root / entry.path
        if candidate.is_symlink():
            errors.append(f"Committed checksum target must not be a symlink: {entry.path}")
            continue
        if not candidate.is_file():
            errors.append(f"Committed checksum target is missing: {entry.path}")
            continue

        try:
            actual = file_sha256(candidate)
        except OSError as exc:
            errors.append(f"Cannot hash committed file {entry.path}: {exc}")
            continue
        committed_checked += 1
        if actual != entry.sha256:
            errors.append(
                f"SHA-256 mismatch for committed file {entry.path}: "
                f"expected {entry.sha256}, got {actual}"
            )
    details["committed_files_checked"] = committed_checked

    if len(artifact_entries) != 1:
        errors.append(
            "Checksum list must contain exactly one non-committed distributable "
            f"JAR entry; found {len(artifact_entries)}"
        )

    if manifest_relative not in tracked:
        errors.append(
            f"JAR content manifest is not committed: {manifest_relative}"
        )
    if manifest_relative not in entries_by_path:
        errors.append(
            f"JAR content manifest is omitted from checksum list: {manifest_relative}"
        )

    metadata, metadata_errors = load_artifact_metadata(content_manifest_path)
    errors.extend(metadata_errors)

    artifact_entry = artifact_entries[0] if len(artifact_entries) == 1 else None
    if metadata is not None and artifact_entry is not None:
        if PurePosixPath(artifact_entry.path).name != metadata.filename:
            errors.append(
                "Distributable JAR checksum filename does not match committed "
                f"content manifest: {artifact_entry.path} != {metadata.filename}"
            )
        if artifact_entry.sha256 != metadata.sha256:
            errors.append(
                "Distributable JAR checksum does not match committed content "
                f"manifest: {artifact_entry.sha256} != {metadata.sha256}"
            )

    if artifact_path is not None:
        artifact_path = artifact_path.resolve()
        if not artifact_path.is_file():
            errors.append(f"Artifact does not exist: {artifact_path}")
        else:
            try:
                artifact_sha256 = file_sha256(artifact_path)
            except OSError as exc:
                errors.append(f"Cannot hash artifact {artifact_path}: {exc}")
            else:
                details["artifact_sha256"] = artifact_sha256
                if artifact_entry is not None and artifact_sha256 != artifact_entry.sha256:
                    errors.append(
                        "Artifact SHA-256 does not match checksum list: "
                        f"expected {artifact_entry.sha256}, got {artifact_sha256}"
                    )
                if metadata is not None:
                    if artifact_path.name != metadata.filename:
                        errors.append(
                            "Artifact filename does not match committed content "
                            f"manifest: {artifact_path.name} != {metadata.filename}"
                        )
                    if artifact_sha256 != metadata.sha256:
                        errors.append(
                            "Artifact SHA-256 does not match committed content "
                            f"manifest: expected {metadata.sha256}, got {artifact_sha256}"
                        )
                if (
                    artifact_entry is not None
                    and metadata is not None
                    and artifact_sha256 == artifact_entry.sha256 == metadata.sha256
                    and artifact_path.name == metadata.filename
                ):
                    details["artifact_verified"] = True

    if artifact_entry is not None:
        details["artifact_entry"] = artifact_entry.path
    return errors, details


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--repository-root",
        type=Path,
        default=ROOT,
        help="repository root used to resolve committed checksum paths",
    )
    parser.add_argument(
        "--checksums",
        type=Path,
        default=DEFAULT_CHECKSUMS,
        help="checksum list path, relative to the repository root by default",
    )
    parser.add_argument(
        "--evidence-dir",
        type=Path,
        default=DEFAULT_EVIDENCE_DIR,
        help="evidence tree that must be completely covered",
    )
    parser.add_argument(
        "--content-manifest",
        type=Path,
        default=DEFAULT_CONTENT_MANIFEST,
        help="committed JAR content manifest carrying artifact metadata",
    )
    parser.add_argument(
        "--artifact",
        type=Path,
        help="also hash and verify the built distributable JAR",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    errors, details = validate_release_checksums(
        repository_root=args.repository_root,
        checksums_path=args.checksums,
        evidence_dir=args.evidence_dir,
        content_manifest_path=args.content_manifest,
        artifact_path=args.artifact,
    )
    if errors:
        for error in errors:
            print(f"[FAIL] {error}")
        return 1

    print(
        "[PASS] Release checksums: "
        f"{details['entries']} entries, "
        f"{details['committed_files_checked']} committed files checked"
    )
    print(
        f"[PASS] Evidence checksum coverage: {details['evidence_files']} files"
    )
    print(
        "[PASS] Distributable JAR checksum matches the committed content manifest: "
        f"{details['artifact_entry']}"
    )
    if args.artifact is not None:
        print(f"[PASS] Built artifact SHA-256: {details['artifact_sha256']}")
    else:
        print("[PASS] Built artifact not supplied; committed metadata verified")
    return 0


if __name__ == "__main__":
    sys.exit(main())
