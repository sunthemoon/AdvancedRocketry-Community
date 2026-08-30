#!/usr/bin/env python3
"""Generate or verify deterministic v0.0.2 G0 packaging evidence.

This command records mechanical facts about the binary and sources JARs.  It
does not provide legal approval and it does not replace a human review of the
rendered project README or other visual release material.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import stat
import sys
import tempfile
import zipfile
from pathlib import Path, PurePosixPath, PureWindowsPath
from typing import BinaryIO, TypedDict

if __package__:
    from .validate_build_artifact import (
        DEFAULT_VERSION,
        PACKAGED_SOURCE_FILES,
        build_content_manifest,
        file_sha256,
        is_sensitive_entry,
        validate_artifact,
    )
else:
    from validate_build_artifact import (
        DEFAULT_VERSION,
        PACKAGED_SOURCE_FILES,
        build_content_manifest,
        file_sha256,
        is_sensitive_entry,
        validate_artifact,
    )


ROOT = Path(__file__).resolve().parents[1]
SCHEMA_VERSION = 1
HASH_CHUNK_SIZE = 1024 * 1024
MAX_TEXT_EVIDENCE_SIZE = 4 * 1024 * 1024
MAX_ARCHIVE_ENTRIES = 4096
MAX_ARCHIVE_ENTRY_SIZE = 32 * 1024 * 1024
MAX_ARCHIVE_TOTAL_SIZE = 128 * 1024 * 1024
MAX_ARCHIVE_COMPRESSION_RATIO = 100
ALLOWED_COMPRESSION_METHODS = frozenset((zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED))
MODS_TOML_PATH = "META-INF/mods.toml"
MANIFEST_PATH = "META-INF/MANIFEST.MF"
SOURCES_MANIFEST_CONTENT = b"Manifest-Version: 1.0\r\n\r\n"
EVIDENCE_FILENAMES = (
    "README.md",
    "license-notice-scan.json",
    "mods.toml",
    "sources-jar-manifest.json",
)
MECHANICAL_SCOPE = (
    "Mechanical packaging evidence only. It does not constitute legal approval "
    "and does not replace human review of the rendered project README or other "
    "visual release material."
)


class EvidenceError(ValueError):
    """Raised when artifacts cannot produce valid G0 evidence."""


class ArchiveEntry(TypedDict):
    path: str
    size: int
    sha256: str


class ArtifactEvidence(TypedDict):
    role: str
    artifact: str
    artifact_sha256: str
    entry_count: int
    license_notice_entries: list[dict[str, object]]


def _stream_sha256(stream: BinaryIO) -> str:
    digest = hashlib.sha256()
    while True:
        chunk = stream.read(HASH_CHUNK_SIZE)
        if not chunk:
            return digest.hexdigest()
        digest.update(chunk)


def _serialize_json(value: object) -> bytes:
    return (
        json.dumps(value, ensure_ascii=True, indent=2, sort_keys=True) + "\n"
    ).encode("utf-8")


def _archive_path_error(value: str) -> str | None:
    if not value:
        return "entry name is empty"
    if "\\" in value:
        return "backslashes are not allowed"
    if any(ord(character) < 32 or ord(character) == 127 for character in value):
        return "control characters are not allowed"

    posix_path = PurePosixPath(value)
    windows_path = PureWindowsPath(value)
    if posix_path.is_absolute() or windows_path.is_absolute() or windows_path.drive:
        return "absolute or drive-qualified paths are not allowed"

    without_directory_marker = value[:-1] if value.endswith("/") else value
    parts = without_directory_marker.split("/")
    if not without_directory_marker or any(part in ("", ".", "..") for part in parts):
        return "empty, current-directory, or traversal segments are not allowed"
    if any(":" in part for part in parts):
        return "colons are not allowed in portable archive paths"
    normalized = posix_path.as_posix() + ("/" if value.endswith("/") else "")
    if normalized != value:
        return "entry name is not a normalized POSIX path"
    return None


def _is_license_or_notice_path(value: str) -> bool:
    filename = PurePosixPath(value).name.lower()
    return "license" in filename or "notice" in filename


def _read_small_entry(archive: zipfile.ZipFile, path: str) -> bytes:
    info = archive.getinfo(path)
    if info.file_size > MAX_TEXT_EVIDENCE_SIZE:
        raise EvidenceError(
            f"archive entry is too large for text evidence ({info.file_size} bytes): {path}"
        )
    return archive.read(info)


def _zip_entry_type_error(info: zipfile.ZipInfo) -> str | None:
    unix_mode = (info.external_attr >> 16) & 0xFFFF
    file_type = stat.S_IFMT(unix_mode)
    if info.is_dir():
        if info.file_size != 0:
            return "directory entries must have zero uncompressed size"
        if file_type not in (0, stat.S_IFDIR):
            return "directory marker has a non-directory file type"
        return None
    if file_type == stat.S_IFLNK:
        return "symbolic-link entries are not allowed"
    if file_type not in (0, stat.S_IFREG):
        return "non-regular archive entries are not allowed"
    return None


def _validate_archive_structure(
    archive: zipfile.ZipFile,
    artifact_role: str,
) -> list[zipfile.ZipInfo]:
    infos = archive.infolist()
    if len(infos) > MAX_ARCHIVE_ENTRIES:
        raise EvidenceError(
            f"{artifact_role} JAR has too many entries: "
            f"{len(infos)} > {MAX_ARCHIVE_ENTRIES}"
        )
    names = [info.filename for info in infos]
    if len(names) != len(set(names)):
        raise EvidenceError(f"{artifact_role} JAR contains duplicate entry names")

    path_errors = [
        f"{info.filename!r}: {error}"
        for info in infos
        if (error := _archive_path_error(info.filename)) is not None
    ]
    if path_errors:
        raise EvidenceError(
            f"{artifact_role} JAR contains unsafe archive paths: "
            + "; ".join(path_errors)
        )

    type_errors = [
        f"{info.filename!r}: {error}"
        for info in infos
        if (error := _zip_entry_type_error(info)) is not None
    ]
    if type_errors:
        raise EvidenceError(
            f"{artifact_role} JAR contains symbolic-link or non-regular entries: "
            + "; ".join(type_errors)
        )

    encrypted = sorted(info.filename for info in infos if info.flag_bits & 0x1)
    if encrypted:
        raise EvidenceError(
            f"{artifact_role} JAR contains encrypted entries: "
            + ", ".join(encrypted)
        )

    unsupported_compression = sorted(
        info.filename
        for info in infos
        if info.compress_type not in ALLOWED_COMPRESSION_METHODS
    )
    if unsupported_compression:
        raise EvidenceError(
            f"{artifact_role} JAR uses unsupported compression methods: "
            + ", ".join(unsupported_compression)
        )

    oversized = sorted(
        f"{info.filename} ({info.file_size} bytes)"
        for info in infos
        if info.file_size > MAX_ARCHIVE_ENTRY_SIZE
    )
    if oversized:
        raise EvidenceError(
            f"{artifact_role} JAR entries exceed the per-entry uncompressed-size "
            f"limit ({MAX_ARCHIVE_ENTRY_SIZE} bytes): " + ", ".join(oversized)
        )

    total_size = sum(info.file_size for info in infos)
    if total_size > MAX_ARCHIVE_TOTAL_SIZE:
        raise EvidenceError(
            f"{artifact_role} JAR total uncompressed size exceeds the limit: "
            f"{total_size} > {MAX_ARCHIVE_TOTAL_SIZE} bytes"
        )

    excessive_ratio = sorted(
        (
            f"{info.filename} "
            f"({info.file_size}:{info.compress_size} uncompressed:compressed)"
        )
        for info in infos
        if info.file_size > 0
        and (
            info.compress_size <= 0
            or info.file_size > info.compress_size * MAX_ARCHIVE_COMPRESSION_RATIO
        )
    )
    if excessive_ratio:
        raise EvidenceError(
            f"{artifact_role} JAR entries exceed the compression-ratio limit "
            f"({MAX_ARCHIVE_COMPRESSION_RATIO}:1): " + ", ".join(excessive_ratio)
        )

    sensitive = sorted(info.filename for info in infos if is_sensitive_entry(info.filename))
    if sensitive:
        raise EvidenceError(
            f"{artifact_role} JAR contains sensitive-looking paths: "
            + ", ".join(sensitive)
        )

    corrupt_entry = archive.testzip()
    if corrupt_entry is not None:
        raise EvidenceError(
            f"{artifact_role} JAR integrity check failed at {corrupt_entry}"
        )
    return infos


def _add_expected_source_file(
    files: dict[str, bytes],
    repository_sources: dict[str, str],
    archive_path: str,
    source_path: Path,
) -> None:
    if source_path.is_symlink() or not source_path.is_file():
        raise EvidenceError(
            f"sources-JAR repository input must be a regular file: {source_path}"
        )
    try:
        repository_path = source_path.resolve().relative_to(ROOT.resolve()).as_posix()
    except (OSError, ValueError) as exc:
        raise EvidenceError(
            f"sources-JAR repository input is outside the repository: {source_path}"
        ) from exc
    if archive_path in files:
        raise EvidenceError(f"sources-JAR input collision at {archive_path}")
    files[archive_path] = source_path.read_bytes()
    repository_sources[archive_path] = repository_path


def _build_expected_sources_layout() -> tuple[dict[str, bytes], dict[str, str]]:
    """Return the exact file payloads produced by this project's sourcesJar task."""
    files: dict[str, bytes] = {MANIFEST_PATH: SOURCES_MANIFEST_CONTENT}
    repository_sources: dict[str, str] = {}
    source_roots = (
        (ROOT / "src/main/java", False),
        (ROOT / "src/main/resources", False),
        (ROOT / "src/generated/resources", True),
    )
    for source_root, exclude_generator_cache in source_roots:
        if source_root.is_symlink() or not source_root.is_dir():
            raise EvidenceError(
                f"sources-JAR source root must be a regular directory: {source_root}"
            )
        for source_path in sorted(source_root.rglob("*")):
            relative = source_path.relative_to(source_root)
            if exclude_generator_cache and relative.parts[:1] == (".cache",):
                continue
            if source_path.is_symlink():
                raise EvidenceError(
                    f"sources-JAR source tree must not contain symlinks: {source_path}"
                )
            if source_path.is_dir():
                continue
            if not source_path.is_file():
                raise EvidenceError(
                    f"sources-JAR source tree contains a non-regular file: {source_path}"
                )
            _add_expected_source_file(
                files,
                repository_sources,
                relative.as_posix(),
                source_path,
            )

    for archive_path, source_path in PACKAGED_SOURCE_FILES.items():
        _add_expected_source_file(
            files,
            repository_sources,
            archive_path,
            source_path,
        )
    return files, repository_sources


def build_expected_sources_files() -> dict[str, bytes]:
    """Expose a copy of the exact repository-to-sources-JAR file mapping."""
    files, _ = _build_expected_sources_layout()
    return dict(files)


def _expected_directory_entries(file_paths: set[str]) -> set[str]:
    directories: set[str] = set()
    for file_path in file_paths:
        parent = PurePosixPath(file_path).parent
        while parent != PurePosixPath("."):
            directories.add(parent.as_posix() + "/")
            parent = parent.parent
    return directories


def _validate_sources_layout(
    archive: zipfile.ZipFile,
    infos: list[zipfile.ZipInfo],
) -> tuple[dict[str, bytes], dict[str, str]]:
    expected_files, repository_sources = _build_expected_sources_layout()
    expected_file_paths = set(expected_files)
    expected_directories = _expected_directory_entries(expected_file_paths)
    actual_files = {info.filename for info in infos if not info.is_dir()}
    actual_directories = {info.filename for info in infos if info.is_dir()}

    missing = sorted(expected_file_paths - actual_files)
    unexpected = sorted(actual_files - expected_file_paths)
    missing_directories = sorted(expected_directories - actual_directories)
    unexpected_directories = sorted(actual_directories - expected_directories)
    if missing or unexpected or missing_directories or unexpected_directories:
        parts: list[str] = []
        if missing:
            parts.append("missing files: " + ", ".join(missing))
        if unexpected:
            parts.append("unexpected files: " + ", ".join(unexpected))
        if missing_directories:
            parts.append("missing directories: " + ", ".join(missing_directories))
        if unexpected_directories:
            parts.append(
                "unexpected directories: " + ", ".join(unexpected_directories)
            )
        raise EvidenceError(
            "sources JAR does not match the exact Gradle sourcesJar layout: "
            + "; ".join(parts)
        )

    mismatched = sorted(
        archive_path
        for archive_path, expected_content in expected_files.items()
        if archive.read(archive_path) != expected_content
    )
    if mismatched:
        raise EvidenceError(
            "sources JAR entries do not match repository/generated inputs: "
            + ", ".join(mismatched)
        )
    return expected_files, repository_sources


def _repository_source_metadata(
    packaged_path: str,
    packaged_sha256: str,
) -> dict[str, object]:
    source_path = PACKAGED_SOURCE_FILES.get(packaged_path)
    if source_path is None:
        return {}
    try:
        relative_source = source_path.resolve().relative_to(ROOT.resolve()).as_posix()
    except (OSError, ValueError) as exc:
        raise EvidenceError(
            f"repository source for {packaged_path} is outside the repository: {exc}"
        ) from exc
    source_sha256 = file_sha256(source_path)
    if source_sha256 != packaged_sha256:
        raise EvidenceError(
            f"archive entry changed relative to its repository source: {packaged_path}"
        )
    return {
        "repository_source": relative_source,
        "repository_source_sha256": source_sha256,
        "matches_repository_source": True,
    }


def _scan_license_notice_entries(
    archive: zipfile.ZipFile,
    infos: list[zipfile.ZipInfo],
) -> list[dict[str, object]]:
    entries: list[dict[str, object]] = []
    for info in infos:
        if info.is_dir() or not _is_license_or_notice_path(info.filename):
            continue
        with archive.open(info) as stream:
            entry: dict[str, object] = {
                "path": info.filename,
                "sha256": _stream_sha256(stream),
                "size": info.file_size,
            }
        entry.update(
            _repository_source_metadata(info.filename, str(entry["sha256"]))
        )
        entries.append(entry)
    entries.sort(key=lambda entry: str(entry["path"]))
    return entries


def _check_packaged_source_files(
    archive: zipfile.ZipFile,
    names: set[str],
    artifact_role: str,
) -> None:
    missing = sorted(set(PACKAGED_SOURCE_FILES) - names)
    if missing:
        raise EvidenceError(
            f"{artifact_role} JAR is missing required license/notice entries: "
            + ", ".join(missing)
        )
    mismatched = [
        packaged_path
        for packaged_path, source_path in PACKAGED_SOURCE_FILES.items()
        if archive.read(packaged_path) != source_path.read_bytes()
    ]
    if mismatched:
        raise EvidenceError(
            f"{artifact_role} JAR license/notice entries do not match repository sources: "
            + ", ".join(sorted(mismatched))
        )


def _artifact_evidence(
    artifact: Path,
    role: str,
) -> tuple[
    ArtifactEvidence,
    bytes,
    list[ArchiveEntry],
    dict[str, bytes],
    dict[str, str],
]:
    expected_sources: dict[str, bytes] = {}
    repository_sources: dict[str, str] = {}
    try:
        before_hash = file_sha256(artifact)
        with zipfile.ZipFile(artifact) as archive:
            infos = _validate_archive_structure(archive, role)
            names = {info.filename for info in infos}
            if role == "sources":
                expected_sources, repository_sources = _validate_sources_layout(
                    archive, infos
                )
            _check_packaged_source_files(archive, names, role)
            if MODS_TOML_PATH not in names:
                raise EvidenceError(f"{role} JAR is missing {MODS_TOML_PATH}")
            mods_toml = _read_small_entry(archive, MODS_TOML_PATH)
            try:
                mods_toml.decode("utf-8")
            except UnicodeDecodeError as exc:
                raise EvidenceError(
                    f"{role} JAR {MODS_TOML_PATH} is not valid UTF-8: {exc}"
                ) from exc
            license_notice_entries = _scan_license_notice_entries(archive, infos)

        content_manifest = build_content_manifest(artifact)
        after_hash = file_sha256(artifact)
    except (OSError, KeyError, RuntimeError, zipfile.BadZipFile) as exc:
        raise EvidenceError(f"cannot inspect {role} JAR {artifact.name}: {exc}") from exc

    if before_hash != after_hash or content_manifest["artifact_sha256"] != before_hash:
        raise EvidenceError(f"{role} JAR changed while it was being inspected")

    return (
        {
            "role": role,
            "artifact": artifact.name,
            "artifact_sha256": before_hash,
            "entry_count": len(content_manifest["entries"]),
            "license_notice_entries": license_notice_entries,
        },
        mods_toml,
        content_manifest["entries"],
        expected_sources,
        repository_sources,
    )


def build_evidence(
    artifact: Path,
    sources_artifact: Path,
    expected_version: str = DEFAULT_VERSION,
) -> dict[str, bytes]:
    """Build the complete deterministic evidence file set in memory."""
    if artifact.is_symlink() or sources_artifact.is_symlink():
        raise EvidenceError("artifact inputs must not be symbolic links")
    artifact = artifact.resolve()
    sources_artifact = sources_artifact.resolve()
    if artifact == sources_artifact:
        raise EvidenceError("binary and sources artifacts must be different files")

    expected_sources_name = artifact.name.removesuffix(".jar") + "-sources.jar"
    if sources_artifact.name != expected_sources_name:
        raise EvidenceError(
            "sources artifact filename must pair with the binary artifact: "
            f"expected {expected_sources_name}, got {sources_artifact.name}"
        )

    (
        binary_evidence,
        binary_mods_toml,
        _,
        _,
        _,
    ) = _artifact_evidence(artifact, "binary")
    artifact_errors, artifact_details = validate_artifact(artifact, expected_version)
    if artifact_errors:
        raise EvidenceError(
            "binary artifact validation failed: " + "; ".join(artifact_errors)
        )
    if binary_evidence["artifact_sha256"] != artifact_details.get("sha256"):
        raise EvidenceError("binary artifact changed after validation")
    (
        sources_evidence,
        _,
        sources_entries,
        expected_source_files,
        repository_sources,
    ) = _artifact_evidence(sources_artifact, "sources")

    sources_paths = {entry["path"] for entry in sources_entries}
    if MANIFEST_PATH not in sources_paths:
        raise EvidenceError(f"sources JAR is missing {MANIFEST_PATH}")
    if not any(path.endswith(".java") for path in sources_paths):
        raise EvidenceError("sources JAR does not contain Java source files")
    class_entries = sorted(path for path in sources_paths if path.endswith(".class"))
    if class_entries:
        raise EvidenceError(
            "sources JAR unexpectedly contains compiled classes: "
            + ", ".join(class_entries)
        )

    required_license_notice_paths = sorted(PACKAGED_SOURCE_FILES)
    license_scan = {
        "artifacts": [binary_evidence, sources_evidence],
        "limitations": [
            "This scan records archive paths, sizes, hashes, and exact repository-source matches.",
            "A human reviewer must decide legal sufficiency and approve licensing conclusions.",
            "A human reviewer must visually inspect the rendered project README and release presentation.",
        ],
        "required_license_notice_paths": required_license_notice_paths,
        "schema_version": SCHEMA_VERSION,
        "scope": MECHANICAL_SCOPE,
    }

    sources_manifest = {
        "artifact": sources_evidence["artifact"],
        "artifact_sha256": sources_evidence["artifact_sha256"],
        "entries": sources_entries,
        "entry_count": sources_evidence["entry_count"],
        "license_notice_paths": [
            entry["path"] for entry in sources_evidence["license_notice_entries"]
        ],
        "paired_binary_artifact": binary_evidence["artifact"],
        "paired_binary_sha256": binary_evidence["artifact_sha256"],
        "repository_input_count": len(repository_sources),
        "repository_inputs": [
            {
                "archive_path": archive_path,
                "repository_path": repository_sources[archive_path],
                "sha256": hashlib.sha256(
                    expected_source_files[archive_path]
                ).hexdigest(),
                "size": len(expected_source_files[archive_path]),
            }
            for archive_path in sorted(repository_sources)
        ],
        "generated_inputs": [
            {
                "archive_path": MANIFEST_PATH,
                "generator": "Gradle Jar default manifest",
                "sha256": hashlib.sha256(expected_source_files[MANIFEST_PATH]).hexdigest(),
                "size": len(expected_source_files[MANIFEST_PATH]),
            }
        ],
        "schema_version": SCHEMA_VERSION,
        "scope": MECHANICAL_SCOPE,
    }

    readme = (
        "# v0.0.2 G0 mechanical packaging evidence\n\n"
        f"- Binary JAR: `{binary_evidence['artifact']}` "
        f"(`sha256:{binary_evidence['artifact_sha256']}`)\n"
        f"- Sources JAR: `{sources_evidence['artifact']}` "
        f"(`sha256:{sources_evidence['artifact_sha256']}`)\n\n"
        "This directory contains deterministic, machine-generated packaging evidence. "
        "It records license and notice placement, the exact processed `META-INF/mods.toml`, "
        "the complete sources-JAR content manifest, and each sources-JAR file's exact "
        "repository or generated input binding.\n\n"
        "This is mechanical evidence only. It does not constitute legal approval and does "
        "not replace human review of the rendered project README or other visual release "
        "material.\n"
    ).encode("utf-8")

    return {
        "README.md": readme,
        "license-notice-scan.json": _serialize_json(license_scan),
        "mods.toml": binary_mods_toml,
        "sources-jar-manifest.json": _serialize_json(sources_manifest),
    }


def _ensure_evidence_directory_safe(
    evidence_dir: Path,
    reject_unexpected: bool = True,
) -> None:
    if evidence_dir.is_symlink():
        raise EvidenceError("evidence directory must not be a symbolic link")
    if evidence_dir.exists() and not evidence_dir.is_dir():
        raise EvidenceError("evidence output path exists and is not a directory")
    if not evidence_dir.exists():
        return

    for child in evidence_dir.iterdir():
        if child.is_symlink():
            raise EvidenceError(f"evidence entry must not be a symbolic link: {child.name}")
        if not child.is_file():
            raise EvidenceError(f"unexpected non-file evidence entry: {child.name}")
        if reject_unexpected and child.name not in EVIDENCE_FILENAMES:
            raise EvidenceError(f"unexpected evidence file: {child.name}")


def _check_outputs_do_not_overwrite_artifacts(
    evidence_dir: Path,
    artifact: Path,
    sources_artifact: Path,
) -> None:
    artifact_paths = {artifact.resolve(), sources_artifact.resolve()}
    for filename in EVIDENCE_FILENAMES:
        if (evidence_dir / filename).resolve() in artifact_paths:
            raise EvidenceError(f"evidence output must not overwrite an artifact: {filename}")


def _write_atomic(path: Path, content: bytes) -> None:
    temporary_path: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="wb",
            prefix=f".{path.name}.",
            suffix=".tmp",
            dir=path.parent,
            delete=False,
        ) as stream:
            temporary_path = Path(stream.name)
            stream.write(content)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary_path, path)
    finally:
        if temporary_path is not None and temporary_path.exists():
            temporary_path.unlink()


def write_evidence(
    evidence_dir: Path,
    evidence: dict[str, bytes],
    artifact: Path,
    sources_artifact: Path,
) -> None:
    if set(evidence) != set(EVIDENCE_FILENAMES):
        raise EvidenceError("internal evidence file set does not match the schema")
    _ensure_evidence_directory_safe(evidence_dir)
    _check_outputs_do_not_overwrite_artifacts(
        evidence_dir, artifact, sources_artifact
    )
    evidence_dir.mkdir(parents=True, exist_ok=True)
    for filename in EVIDENCE_FILENAMES:
        _write_atomic(evidence_dir / filename, evidence[filename])


def verify_evidence(
    evidence_dir: Path,
    expected: dict[str, bytes],
) -> list[str]:
    errors: list[str] = []
    try:
        _ensure_evidence_directory_safe(evidence_dir, reject_unexpected=False)
    except EvidenceError as exc:
        return [str(exc)]
    if not evidence_dir.is_dir():
        return [f"evidence directory does not exist: {evidence_dir}"]

    actual_names = {child.name for child in evidence_dir.iterdir()}
    missing = sorted(set(EVIDENCE_FILENAMES) - actual_names)
    extra = sorted(actual_names - set(EVIDENCE_FILENAMES))
    if missing:
        errors.append("missing evidence files: " + ", ".join(missing))
    if extra:
        errors.append("unexpected evidence files: " + ", ".join(extra))

    for filename in EVIDENCE_FILENAMES:
        path = evidence_dir / filename
        if not path.is_file() or path.is_symlink():
            continue
        try:
            actual = path.read_bytes()
        except OSError as exc:
            errors.append(f"cannot read evidence file {filename}: {exc}")
            continue
        if actual != expected[filename]:
            errors.append(
                f"evidence file does not match the supplied artifacts: {filename}"
            )
    return errors


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("generate", "verify"))
    parser.add_argument("artifact", type=Path, help="built distributable binary JAR")
    parser.add_argument("sources_artifact", type=Path, help="matching built sources JAR")
    parser.add_argument(
        "--evidence-dir",
        type=Path,
        required=True,
        help="dedicated directory containing only generated G0 mechanical evidence",
    )
    parser.add_argument(
        "--expected-version",
        default=DEFAULT_VERSION,
        help=f"expanded mod version (default: {DEFAULT_VERSION})",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        evidence = build_evidence(
            args.artifact,
            args.sources_artifact,
            args.expected_version,
        )
        if args.mode == "generate":
            write_evidence(
                args.evidence_dir,
                evidence,
                args.artifact,
                args.sources_artifact,
            )
            print(
                f"[PASS] Generated {len(evidence)} deterministic G0 evidence files: "
                f"{args.evidence_dir}"
            )
        else:
            errors = verify_evidence(args.evidence_dir, evidence)
            if errors:
                for error in errors:
                    print(f"[FAIL] {error}")
                return 1
            print(
                f"[PASS] Verified {len(evidence)} deterministic G0 evidence files: "
                f"{args.evidence_dir}"
            )
    except (EvidenceError, OSError) as exc:
        print(f"[FAIL] {exc}")
        return 1

    print(f"[PASS] {MECHANICAL_SCOPE}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
