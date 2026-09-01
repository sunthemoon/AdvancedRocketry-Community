#!/usr/bin/env python3
"""Generate or verify the exact v0.7.0 DataGen resource inventory."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
from pathlib import Path, PurePosixPath


ROOT = Path(__file__).resolve().parents[1]
GENERATED_ROOT = Path("src/generated/v0.7/resources")
DEFAULT_OUTPUT = Path("docs/provenance/v0.7.0-generated-resources.json")
MAX_FILE_BYTES = 2 * 1024 * 1024
MAX_TOTAL_BYTES = 8 * 1024 * 1024
EXPECTED_PATHS = frozenset({
    "assets/advancedrocketrycommunity/models/item/station_deployment_kit.json",
    "assets/advancedrocketrycommunity_v070/lang/en_us.json",
    "assets/advancedrocketrycommunity_v070/lang/zh_cn.json",
    "data/advancedrocketrycommunity/advancements/recipes/misc/station_deployment_kit.json",
    "data/advancedrocketrycommunity/recipes/station_deployment_kit.json",
})


def _load_json(path: Path) -> object:
    if path.is_symlink() or not path.is_file():
        raise ValueError(f"required JSON file is missing or unsafe: {path}")
    data = path.read_bytes()
    if len(data) > MAX_FILE_BYTES:
        raise ValueError(f"JSON file is too large: {path}")
    return json.loads(data.decode("utf-8", errors="strict"))


def _pretty(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode("utf-8")


def _relative(repository_root: Path, path: Path) -> str:
    relative = path.relative_to(repository_root).as_posix()
    parsed = PurePosixPath(relative)
    if parsed.as_posix() != relative or any(part in {"", ".", ".."} for part in parsed.parts):
        raise ValueError(f"unsafe generated path: {relative}")
    return relative


def render_manifest(repository_root: Path = ROOT) -> dict[str, object]:
    repository_root = repository_root.resolve()
    root = repository_root / GENERATED_ROOT
    if root.is_symlink() or not root.is_dir():
        raise ValueError("v0.7.0 generated resource root is missing or unsafe")

    targets: list[dict[str, object]] = []
    observed: set[str] = set()
    casefolded: set[str] = set()
    total = 0
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
        resource_path = path.relative_to(root).as_posix()
        observed.add(resource_path)
        if path.suffix.lower() != ".json":
            raise ValueError(f"v0.7.0 generated target has an unapproved type: {relative}")
        size = path.stat().st_size
        if size > MAX_FILE_BYTES:
            raise ValueError(f"generated target exceeds {MAX_FILE_BYTES} bytes: {relative}")
        total += size
        if total > MAX_TOTAL_BYTES:
            raise ValueError("generated targets exceed aggregate byte limit")
        data = path.read_bytes()
        json.loads(data.decode("utf-8", errors="strict"))
        targets.append({
            "path": relative,
            "bytes": size,
            "sha256": hashlib.sha256(data).hexdigest(),
        })

    if observed != EXPECTED_PATHS:
        missing = sorted(EXPECTED_PATHS - observed)
        unexpected = sorted(observed - EXPECTED_PATHS)
        raise ValueError(
            f"v0.7.0 generated inventory mismatch; missing={missing}, unexpected={unexpected}"
        )
    return {
        "schema_version": 1,
        "target_version": "v0.7.0",
        "status": "COMMUNITY_AUTHORED_DATAGEN",
        "generator": (
            "io.github.sunthemoon.advancedrocketrycommunity.datagen.BootstrapDataGenerators"
        ),
        "generated_root": GENERATED_ROOT.as_posix(),
        "allowed_file_types": [".json"],
        "expected_resource_paths": sorted(EXPECTED_PATHS),
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
        errors.append("v0.7.0 generated-resource manifest does not match DataGen target bytes")
    if output.read_bytes() != _pretty(actual):
        errors.append("v0.7.0 generated-resource manifest is not canonical sorted JSON")
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
            print(f"[PASS] Recorded {len(manifest['targets'])} v0.7.0 generated resources")
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
    print(f"[PASS] v0.7.0 manifest matches {len(manifest['targets'])} DataGen files")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
