#!/usr/bin/env python3
"""Resolve exact CI artifact names from the checked-out Gradle properties."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAX_PROPERTIES_BYTES = 65536
IDENTITY_PATTERNS = {
    "minecraft_version": r"[0-9]+\.[0-9]+\.[0-9]+",
    "mod_version": r"[0-9]+\.[0-9]+\.[0-9]+(?:[-+][0-9A-Za-z][0-9A-Za-z.+-]*)?",
    "mod_artifact_id": r"[a-z0-9]+(?:-[a-z0-9]+)*",
}


def read_identity(properties: Path) -> dict[str, object]:
    """Accept the repository's literal key=value subset, not escaped aliases.

    Gradle supports more Java-properties syntax than this CI input contract.
    Reject other forms instead of silently deriving a different artifact name.
    Unrelated values (including the description's escaped newlines) are not
    interpreted or exported to the shell.
    """
    with properties.open("rb") as handle:
        raw = handle.read(MAX_PROPERTIES_BYTES + 1)
    if len(raw) > MAX_PROPERTIES_BYTES:
        raise ValueError("gradle.properties exceeds the 64 KiB CI input limit")
    values: dict[str, str] = {}
    for number, raw_line in enumerate(raw.decode("utf-8").split("\n"), 1):
        line = raw_line.removesuffix("\r").lstrip(" \t\f")
        if not line or line.startswith(("#", "!")):
            continue
        key, separator, value = line.partition("=")
        key = key.rstrip(" \t\f")
        if (
            not separator
            or re.fullmatch(r"[A-Za-z0-9_.-]+", key) is None
            or line.endswith("\\")
            or key in values
        ):
            raise ValueError(f"noncanonical or duplicate property at line {number}")
        values[key] = value.lstrip(" \t\f")
    for key, pattern in IDENTITY_PATTERNS.items():
        value = values.get(key, "")
        if len(value) > 96 or re.fullmatch(pattern, value) is None:
            raise ValueError(f"missing or unsafe build identity property: {key}")
    version = f"{values['minecraft_version']}-{values['mod_version']}"
    artifact = f"build/libs/{values['mod_artifact_id']}-{version}"
    return {
        "schema_version": 1,
        "properties_sha256": hashlib.sha256(raw).hexdigest(),
        "minecraft_version": values["minecraft_version"],
        "mod_version": values["mod_version"],
        "mod_artifact_id": values["mod_artifact_id"],
        "build_version": version,
        "artifact": artifact + ".jar",
        "sources": artifact + "-sources.jar",
    }


def github_environment(identity: dict[str, object]) -> str:
    return "".join(
        f"{name}={identity[key]}\n"
        for name, key in (
            ("ARCE_BUILD_VERSION", "build_version"),
            ("ARCE_ARTIFACT", "artifact"),
            ("ARCE_SOURCES", "sources"),
        )
    )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--properties", type=Path, default=ROOT / "gradle.properties")
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--github-env", type=Path)
    args = parser.parse_args(argv)
    try:
        paths = [args.properties.resolve(), args.manifest.resolve()]
        if args.github_env is not None:
            paths.append(args.github_env.resolve())
            if not args.github_env.is_file():
                raise ValueError("GitHub environment file must already exist")
        if len(set(paths)) != len(paths):
            raise ValueError("properties, manifest and environment files must differ")
        identity = read_identity(args.properties)
        document = json.dumps(identity, indent=2, sort_keys=True) + "\n"
        args.manifest.parent.mkdir(parents=True, exist_ok=True)
        args.manifest.write_text(document, encoding="utf-8", newline="\n")
        if args.github_env is not None:
            with args.github_env.open("a", encoding="utf-8", newline="\n") as handle:
                handle.write(github_environment(identity))
        print(document, end="")
        return 0
    except (OSError, UnicodeError, ValueError) as error:
        print(f"[FAIL] Cannot resolve CI artifact identity: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
