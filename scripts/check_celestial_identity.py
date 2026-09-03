#!/usr/bin/env python3
"""Reject numeric dimension identity and DOM leakage in the v0.3 celestial slice."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CELESTIAL_ROOT = Path(
    "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/celestial"
)
MAX_FILES = 128
MAX_FILE_BYTES = 256 * 1024
NUMERIC_DIMENSION_ID = re.compile(
    r"\b(?:numeric[\s_]*dimension[\s_]*id|dimension[\s_]*id|dimid)\b",
    re.IGNORECASE,
)
NUMERIC_ID_COLLECTION = re.compile(r"\b(?:Map|Set)<\s*Integer\s*[,>]")
DOM_REFERENCE = re.compile(
    r"\borg\.w3c\.dom\b|\bDocumentBuilder(?:Factory)?\b|\bDOMSource\b"
)


def _read_java(root: Path, path: Path) -> str:
    if path.is_symlink() or not path.is_file():
        raise ValueError(f"celestial source is missing or unsafe: {path}")
    data = path.read_bytes()
    if len(data) > MAX_FILE_BYTES:
        raise ValueError(f"celestial source exceeds {MAX_FILE_BYTES} bytes: {path}")
    return data.decode("utf-8", errors="strict")


def check_celestial_identity(repository_root: Path = ROOT) -> list[str]:
    root = repository_root.resolve()
    source_root = root / CELESTIAL_ROOT
    if source_root.is_symlink() or not source_root.is_dir():
        return ["celestial source root is missing or unsafe"]
    files = sorted(source_root.rglob("*.java"), key=lambda path: path.as_posix())
    if not files or len(files) > MAX_FILES:
        return [f"celestial source count outside bounds: {len(files)}"]

    errors: list[str] = []
    texts: dict[str, str] = {}
    for path in files:
        try:
            text = _read_java(root, path)
        except (OSError, UnicodeError, ValueError) as exc:
            errors.append(str(exc))
            continue
        relative = path.relative_to(source_root).as_posix()
        texts[relative] = text
        if DOM_REFERENCE.search(text):
            errors.append(f"DOM reference crosses the celestial adapter: {relative}")
        if not relative.startswith("legacy/"):
            if NUMERIC_DIMENSION_ID.search(text):
                errors.append(f"numeric dimension identity appears at runtime: {relative}")
            if NUMERIC_ID_COLLECTION.search(text):
                errors.append(f"integer identity collection appears at runtime: {relative}")

    required = {
        "CelestialIds.java": (
            "ResourceKey<Level>",
            "MOON_LEVEL",
            "SPACE_LEVEL",
        ),
        "persistence/CelestialSavedData.java": (
            "ResourceLocation",
            "CURRENT_SCHEMA_VERSION",
        ),
        "network/CelestialSnapshot.java": (
            "ResourceLocation",
        ),
        "network/CelestialSnapshotCodec.java": (
            "CelestialCatalog.MAX_BODIES",
            "MAX_PACKET_BYTES",
        ),
    }
    for relative, markers in required.items():
        text = texts.get(relative)
        if text is None:
            errors.append(f"required identity source is missing: {relative}")
            continue
        for marker in markers:
            if marker not in text:
                errors.append(f"required identity marker {marker!r} missing: {relative}")

    saved_data = texts.get("persistence/CelestialSavedData.java", "")
    local_schema_marker = '"schema_version"' in saved_data
    centralized_schema_marker = all(
        marker in saved_data
        for marker in (
            "SavedDataSchemaMigrator",
            "ManagedSavedDataType.CELESTIAL",
        )
    )
    if saved_data and not (local_schema_marker or centralized_schema_marker):
        errors.append(
            "required schema identity is neither local nor delegated to the "
            "managed celestial migrator: persistence/CelestialSavedData.java"
        )

    legacy_numeric_files = {
        relative
        for relative, text in texts.items()
        if relative.startswith("legacy/") and NUMERIC_DIMENSION_ID.search(text)
    }
    allowed_legacy_numeric_files = {
        "legacy/LegacyCelestialImporter.java",
        "legacy/LegacyImportExporter.java",
        "legacy/LegacyXmlParser.java",
    }
    unexpected_legacy = legacy_numeric_files - allowed_legacy_numeric_files
    if unexpected_legacy:
        errors.append(
            "legacy numeric metadata escaped its report-only files: "
            + ", ".join(sorted(unexpected_legacy))
        )
    return errors


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository-root", type=Path, default=ROOT)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        errors = check_celestial_identity(args.repository_root)
    except (OSError, UnicodeError, ValueError) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1
    if errors:
        for error in errors:
            print(f"[FAIL] {error}", file=sys.stderr)
        return 1
    print(
        "[PASS] Celestial runtime uses namespaced ResourceLocation/ResourceKey "
        "identity; legacy numeric IDs remain report-only"
    )
    print("[PASS] No DOM API reference appears in the celestial implementation")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
