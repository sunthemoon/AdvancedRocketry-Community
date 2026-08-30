#!/usr/bin/env python3
"""Generate or verify the hash inventory for v0.1.0 DataGen resources."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
from pathlib import Path, PurePosixPath


ROOT = Path(__file__).resolve().parents[1]
GENERATED_ROOT = Path("src/generated/resources")
PROVENANCE_RECORD = Path("docs/provenance/v0.1.0-minimal-content.json")
DEFAULT_OUTPUT = Path("docs/provenance/v0.1.0-generated-resources.json")
MAX_FILES = 512
MAX_FILE_BYTES = 8 * 1024 * 1024
MAX_TOTAL_BYTES = 64 * 1024 * 1024


def _load_json(path: Path) -> object:
    if path.is_symlink() or not path.is_file():
        raise ValueError(f"required JSON file is missing or unsafe: {path}")
    data = path.read_bytes()
    if len(data) > 2 * 1024 * 1024:
        raise ValueError(f"JSON file is too large: {path}")
    return json.loads(data.decode("utf-8", errors="strict"))


def _pretty(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode("utf-8")


def _relative(root: Path, path: Path) -> str:
    relative = path.relative_to(root).as_posix()
    parsed = PurePosixPath(relative)
    if parsed.as_posix() != relative or any(part in {"", ".", ".."} for part in parsed.parts):
        raise ValueError(f"unsafe generated path: {relative}")
    return relative


def render_manifest(repository_root: Path = ROOT) -> dict[str, object]:
    repository_root = repository_root.resolve()
    provenance = _load_json(repository_root / PROVENANCE_RECORD)
    if not isinstance(provenance, dict) or not isinstance(provenance.get("entries"), list):
        raise ValueError("v0.1.0 provenance record is malformed")
    imported = sorted(
        entry["target_path"]
        for entry in provenance["entries"]
        if isinstance(entry, dict)
        and isinstance(entry.get("target_path"), str)
        and entry["target_path"].startswith(GENERATED_ROOT.as_posix() + "/")
    )
    imported_set = set(imported)
    root = repository_root / GENERATED_ROOT
    if root.is_symlink() or not root.is_dir():
        raise ValueError("generated resource root is missing or unsafe")
    targets: list[dict[str, object]] = []
    total = 0
    casefolded: set[str] = set()
    for path in sorted(root.rglob("*"), key=lambda value: value.as_posix()):
        if path.is_dir():
            continue
        relative = _relative(repository_root, path)
        if relative.startswith(GENERATED_ROOT.as_posix() + "/.cache/"):
            continue
        if path.is_symlink() or not path.is_file():
            raise ValueError(f"generated target is not a regular file: {relative}")
        if relative.casefold() in casefolded:
            raise ValueError(f"case-colliding generated target: {relative}")
        casefolded.add(relative.casefold())
        if relative in imported_set:
            continue
        size = path.stat().st_size
        if size > MAX_FILE_BYTES:
            raise ValueError(f"generated target exceeds {MAX_FILE_BYTES} bytes: {relative}")
        total += size
        if total > MAX_TOTAL_BYTES:
            raise ValueError("generated targets exceed aggregate byte limit")
        data = path.read_bytes()
        targets.append(
            {
                "path": relative,
                "bytes": size,
                "sha256": hashlib.sha256(data).hexdigest(),
            }
        )
    if not targets or len(targets) > MAX_FILES:
        raise ValueError(f"generated target count outside bounds: {len(targets)}")
    return {
        "schema_version": 1,
        "target_version": "v0.1.0",
        "status": "GENERATED",
        "generator": "io.github.sunthemoon.advancedrocketrycommunity.datagen.BootstrapDataGenerators",
        "generated_root": GENERATED_ROOT.as_posix(),
        "provenance_managed_targets": imported,
        "targets": targets,
    }


def generate(repository_root: Path, output: Path) -> dict[str, object]:
    manifest = render_manifest(repository_root)
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_name(output.name + f".tmp-{os.getpid()}")
    temporary.write_bytes(_pretty(manifest))
    os.replace(temporary, output)
    return manifest


def verify(repository_root: Path, output: Path) -> list[str]:
    expected = render_manifest(repository_root)
    actual = _load_json(output)
    errors: list[str] = []
    if actual != expected:
        errors.append("generated-resource manifest does not match DataGen target bytes")
    if output.read_bytes() != _pretty(actual):
        errors.append("generated-resource manifest is not canonical sorted JSON")
    return errors


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("generate", "verify"))
    parser.add_argument("--repository-root", type=Path, default=ROOT)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    root = args.repository_root.resolve()
    output = args.output if args.output.is_absolute() else root / args.output
    try:
        if args.mode == "generate":
            manifest = generate(root, output)
            print(
                f"[PASS] Recorded {len(manifest['targets'])} generated resources; "
                f"{len(manifest['provenance_managed_targets'])} provenance-managed targets excluded"
            )
            return 0
        errors = verify(root, output)
    except (OSError, UnicodeError, ValueError, json.JSONDecodeError) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1
    if errors:
        for error in errors:
            print(f"[FAIL] {error}", file=sys.stderr)
        return 1
    manifest = _load_json(output)
    print(f"[PASS] Generated-resource manifest matches {len(manifest['targets'])} DataGen files")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
