#!/usr/bin/env python3
"""Validate the v0.0.2 mod JAR and optionally write a content manifest."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path, PurePosixPath
from typing import BinaryIO, TypedDict


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_VERSION = "1.20.1-0.0.2-dev"
EXPECTED_ARTIFACT_PREFIX = "advancedrocketry-community-"
EXPECTED_MOD_ID = "advancedrocketrycommunity"
EXPECTED_DISPLAY_NAME = "Advanced Rocketry: Community Edition"
CONTENT_MANIFEST_SCHEMA_VERSION = 1
HASH_CHUNK_SIZE = 1024 * 1024
REQUIRED_ENTRIES = (
    "META-INF/MANIFEST.MF",
    "META-INF/LICENSE",
    "META-INF/NOTICE.md",
    "META-INF/THIRD-PARTY-NOTICES.md",
    "META-INF/licenses/GRADLE-8.1.1-LICENSE.txt",
    "META-INF/licenses/MINECRAFT-FORGE-1.20.1-47.4.10-LICENSE.txt",
    "META-INF/mods.toml",
    "advancedrocketrycommunity.png",
    "pack.mcmeta",
    "io/github/sunthemoon/advancedrocketrycommunity/AdvancedRocketryCommunity.class",
)
PACKAGED_SOURCE_FILES = {
    "META-INF/LICENSE": ROOT / "LICENSE",
    "META-INF/NOTICE.md": ROOT / "NOTICE.md",
    "META-INF/THIRD-PARTY-NOTICES.md": ROOT / "THIRD-PARTY-NOTICES.md",
    "META-INF/licenses/GRADLE-8.1.1-LICENSE.txt": (
        ROOT / "docs" / "licenses" / "GRADLE-8.1.1-LICENSE.txt"
    ),
    "META-INF/licenses/MINECRAFT-FORGE-1.20.1-47.4.10-LICENSE.txt": (
        ROOT
        / "docs"
        / "licenses"
        / "MINECRAFT-FORGE-1.20.1-47.4.10-LICENSE.txt"
    ),
}
SENSITIVE_PARTS = {
    "credentials",
    "credentials.json",
    "id_ed25519",
    "id_rsa",
    "secrets",
}
SENSITIVE_SUFFIXES = (".jks", ".key", ".p12", ".pem", ".pfx")
SENSITIVE_CONTENT = (
    re.compile(rb"-----BEGIN [A-Z ]*PRIVATE KEY-----"),
    re.compile(rb"\bAKIA[0-9A-Z]{16}\b"),
    re.compile(rb"\bgh[pousr]_[A-Za-z0-9]{20,}\b"),
    re.compile(rb"\bgithub_pat_[A-Za-z0-9_]{20,}\b"),
    re.compile(rb"\bxox[baprs]-[A-Za-z0-9-]{10,}\b"),
)


class ContentManifestEntry(TypedDict):
    path: str
    size: int
    sha256: str


class ContentManifest(TypedDict):
    schema_version: int
    artifact: str
    artifact_sha256: str
    entry_count: int
    entries: list[ContentManifestEntry]


def stream_sha256(stream: BinaryIO) -> str:
    """Hash a binary stream without loading the complete payload into memory."""
    digest = hashlib.sha256()
    while True:
        chunk = stream.read(HASH_CHUNK_SIZE)
        if not chunk:
            return digest.hexdigest()
        digest.update(chunk)


def file_sha256(path: Path) -> str:
    with path.open("rb") as stream:
        return stream_sha256(stream)


def build_content_manifest(artifact: Path) -> ContentManifest:
    """Describe every archive entry in a stable, machine-independent order."""
    with zipfile.ZipFile(artifact) as archive:
        infos = archive.infolist()
        names = [info.filename for info in infos]
        if len(names) != len(set(names)):
            raise ValueError("cannot create a content manifest with duplicate entry names")

        entries: list[ContentManifestEntry] = []
        for info in infos:
            with archive.open(info) as stream:
                entry_sha256 = stream_sha256(stream)
            entries.append(
                {
                    "path": info.filename,
                    "size": info.file_size,
                    "sha256": entry_sha256,
                }
            )

    entries.sort(key=lambda entry: entry["path"])
    return {
        "schema_version": CONTENT_MANIFEST_SCHEMA_VERSION,
        "artifact": artifact.name,
        "artifact_sha256": file_sha256(artifact),
        "entry_count": len(entries),
        "entries": entries,
    }


def serialize_content_manifest(manifest: ContentManifest) -> str:
    """Serialize a content manifest with stable keys, whitespace, and newlines."""
    return json.dumps(
        manifest,
        ensure_ascii=True,
        indent=2,
        sort_keys=True,
    ) + "\n"


def write_content_manifest(output: Path, manifest: ContentManifest) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="\n") as stream:
        stream.write(serialize_content_manifest(manifest))


def parse_manifest(content: str) -> dict[str, str]:
    """Parse the small subset of the JAR manifest format used by this project."""
    logical_lines: list[str] = []
    for line in content.replace("\r\n", "\n").split("\n"):
        if line.startswith(" ") and logical_lines:
            logical_lines[-1] += line[1:]
        elif line:
            logical_lines.append(line)

    attributes: dict[str, str] = {}
    for line in logical_lines:
        if ": " in line:
            key, value = line.split(": ", 1)
            attributes[key] = value
    return attributes


def is_sensitive_entry(name: str) -> bool:
    lowered = PurePosixPath(name.replace("\\", "/")).as_posix().lower()
    parts = set(PurePosixPath(lowered).parts)
    return (
        bool(parts & SENSITIVE_PARTS)
        or any(part == ".env" or part.startswith(".env.") for part in parts)
        or lowered.endswith(SENSITIVE_SUFFIXES)
    )


def is_safe_entry(name: str) -> bool:
    path = PurePosixPath(name.replace("\\", "/"))
    return not (
        name.startswith(("/", "\\"))
        or ".." in path.parts
        or (path.parts and ":" in path.parts[0])
    )


def validate_artifact(
    artifact: Path,
    expected_version: str = DEFAULT_VERSION,
) -> tuple[list[str], dict[str, str | int]]:
    errors: list[str] = []
    details: dict[str, str | int] = {
        "artifact": str(artifact),
        "expected_version": expected_version,
    }

    if not artifact.is_file():
        return [f"Artifact does not exist: {artifact}"], details

    details["sha256"] = file_sha256(artifact)
    expected_name = f"{EXPECTED_ARTIFACT_PREFIX}{expected_version}.jar"
    if artifact.name != expected_name:
        errors.append(f"Artifact filename must be {expected_name}, got {artifact.name}")
    if any(marker in artifact.name.lower() for marker in ("none", "unspecified")):
        errors.append("Artifact filename contains an unresolved version marker")

    try:
        with zipfile.ZipFile(artifact) as archive:
            names = archive.namelist()
            details["entry_count"] = len(names)
            if len(names) != len(set(names)):
                errors.append("Archive contains duplicate entry names")

            corrupt_entry = archive.testzip()
            if corrupt_entry:
                errors.append(f"Archive integrity check failed at {corrupt_entry}")

            for required in REQUIRED_ENTRIES:
                if required not in names:
                    errors.append(f"Missing required JAR entry: {required}")

            unsafe = sorted(name for name in names if not is_safe_entry(name))
            if unsafe:
                errors.append("Unsafe archive paths: " + ", ".join(unsafe))

            sensitive = sorted(name for name in names if is_sensitive_entry(name))
            if sensitive:
                errors.append("Sensitive-looking files in artifact: " + ", ".join(sensitive))

            sensitive_content = sorted(
                name
                for name in names
                if not name.endswith("/")
                and archive.getinfo(name).file_size <= 1024 * 1024
                and any(pattern.search(archive.read(name)) for pattern in SENSITIVE_CONTENT)
            )
            if sensitive_content:
                errors.append(
                    "Credential-like content in artifact: "
                    + ", ".join(sensitive_content)
                )

            if errors and any(error.startswith("Missing required") for error in errors):
                return errors, details

            for packaged_path, source_path in PACKAGED_SOURCE_FILES.items():
                expected_content = source_path.read_bytes()
                if archive.read(packaged_path) != expected_content:
                    errors.append(
                        f"Packaged file does not match repository source: {packaged_path}"
                    )

            metadata = archive.read("META-INF/mods.toml").decode("utf-8")
            manifest = parse_manifest(
                archive.read("META-INF/MANIFEST.MF").decode("utf-8")
            )
            pack = json.loads(archive.read("pack.mcmeta").decode("utf-8"))
            license_text = archive.read("META-INF/LICENSE").decode("utf-8")
            notice_text = archive.read("META-INF/NOTICE.md").decode("utf-8")

            metadata_fragments = (
                'modLoader="javafml"',
                'license="MIT"',
                f'modId="{EXPECTED_MOD_ID}"',
                f'version="{expected_version}"',
                f'displayName="{EXPECTED_DISPLAY_NAME}"',
                'displayTest="MATCH_VERSION"',
                'features={java_version="[17,)"}',
                'versionRange="[47.4.10,48)"',
                'versionRange="[1.20.1,1.20.2)"',
            )
            missing_fragments = [
                fragment for fragment in metadata_fragments if fragment not in metadata
            ]
            if missing_fragments:
                errors.append(
                    "mods.toml is missing expected values: "
                    + ", ".join(missing_fragments)
                )
            if "${" in metadata:
                errors.append("mods.toml contains unresolved Gradle placeholders")

            expected_manifest = {
                "Implementation-Title": EXPECTED_DISPLAY_NAME,
                "Implementation-Version": expected_version,
                "Specification-Title": EXPECTED_MOD_ID,
            }
            for key, expected in expected_manifest.items():
                if manifest.get(key) != expected:
                    errors.append(
                        f"Manifest {key} must be {expected!r}, got {manifest.get(key)!r}"
                    )

            description = pack.get("pack", {}).get("description", "")
            if EXPECTED_DISPLAY_NAME not in description:
                errors.append("pack.mcmeta does not contain the approved display name")
            if pack.get("pack", {}).get("pack_format") != 15:
                errors.append("pack.mcmeta pack_format must be 15 for Minecraft 1.20.1")
            if "MIT License" not in license_text:
                errors.append("Packaged LICENSE does not contain the MIT license text")
            if "NOT AN OFFICIAL MINECRAFT PRODUCT" not in notice_text:
                errors.append("Packaged NOTICE is missing the Minecraft non-affiliation statement")

            text_entries = (
                "META-INF/MANIFEST.MF",
                "META-INF/mods.toml",
                "pack.mcmeta",
            )
            unresolved = [
                name
                for name in text_entries
                if "${" in archive.read(name).decode("utf-8")
            ]
            if unresolved:
                errors.append("Unresolved placeholders in: " + ", ".join(unresolved))
    except (
        AttributeError,
        KeyError,
        OSError,
        TypeError,
        UnicodeError,
        json.JSONDecodeError,
        zipfile.BadZipFile,
    ) as exc:
        errors.append(f"Cannot inspect artifact: {exc}")

    return errors, details


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("artifact", type=Path, help="path to the distributable mod JAR")
    parser.add_argument(
        "--expected-version",
        default=DEFAULT_VERSION,
        help=f"expanded mod version (default: {DEFAULT_VERSION})",
    )
    parser.add_argument(
        "--content-manifest",
        type=Path,
        metavar="PATH",
        help=(
            "write a deterministic JSON manifest containing every entry path, "
            "uncompressed size, and SHA-256"
        ),
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    errors, details = validate_artifact(args.artifact, args.expected_version)
    if errors:
        for error in errors:
            print(f"[FAIL] {error}")
        return 1

    content_manifest: ContentManifest | None = None
    if args.content_manifest is not None:
        try:
            if args.content_manifest.resolve() == args.artifact.resolve():
                raise ValueError("content manifest output must not overwrite the JAR")
            content_manifest = build_content_manifest(args.artifact)
            if content_manifest["artifact_sha256"] != details["sha256"]:
                raise ValueError("artifact changed while it was being inspected")
            write_content_manifest(args.content_manifest, content_manifest)
        except (OSError, RuntimeError, ValueError, zipfile.BadZipFile) as exc:
            print(f"[FAIL] Cannot write content manifest: {exc}")
            return 1

    print(f"[PASS] Artifact: {details['artifact']}")
    print(f"[PASS] SHA-256: {details['sha256']}")
    print(f"[PASS] Entries: {details['entry_count']}")
    print("[PASS] Metadata, notices, paths, and credential name/content scan")
    if content_manifest is not None:
        print(
            f"[PASS] Content manifest: {args.content_manifest} "
            f"({content_manifest['entry_count']} entries)"
        )
    return 0


if __name__ == "__main__":
    sys.exit(main())
