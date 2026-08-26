#!/usr/bin/env python3
"""Reject Minecraft client references outside the dedicated client package."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


CLIENT_REFERENCE = re.compile(r"\bnet\.minecraft\.client(?:\.|\b)")


def find_violations(source_root: Path) -> list[str]:
    violations: list[str] = []
    if not source_root.is_dir():
        return [f"Source root does not exist: {source_root}"]

    for path in sorted(source_root.rglob("*.java")):
        relative = path.relative_to(source_root)
        if "client" in relative.parts:
            continue
        try:
            lines = path.read_text(encoding="utf-8").splitlines()
        except (OSError, UnicodeError) as exc:
            violations.append(f"{relative.as_posix()}: cannot read: {exc}")
            continue
        for line_number, line in enumerate(lines, start=1):
            if CLIENT_REFERENCE.search(line):
                violations.append(f"{relative.as_posix()}:{line_number}")
    return violations


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "source_root",
        nargs="?",
        type=Path,
        default=Path("src/main/java"),
        help="Java source root (default: src/main/java)",
    )
    return parser.parse_args()


def main() -> int:
    violations = find_violations(parse_args().source_root)
    if violations:
        for violation in violations:
            print(f"[FAIL] Client reference outside client package: {violation}")
        return 1
    print("[PASS] No net.minecraft.client references outside the client package")
    return 0


if __name__ == "__main__":
    sys.exit(main())
