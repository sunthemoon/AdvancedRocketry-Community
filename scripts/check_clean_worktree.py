#!/usr/bin/env python3
"""Fail when Git reports tracked or untracked non-ignored worktree changes."""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
GIT_STATUS_COMMAND = (
    "git",
    "status",
    "--porcelain",
    "--untracked-files=all",
)


class GitStatusError(RuntimeError):
    """Raised when the repository status cannot be inspected."""


def get_worktree_status(repository: Path = ROOT) -> str:
    """Return porcelain status output, excluding files ignored by Git."""
    try:
        completed = subprocess.run(
            GIT_STATUS_COMMAND,
            cwd=repository,
            check=False,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
    except OSError as exc:
        raise GitStatusError(f"cannot execute git: {exc}") from exc

    if completed.returncode != 0:
        detail = completed.stderr.strip() or "git status returned no error output"
        raise GitStatusError(
            f"git status exited with code {completed.returncode}: {detail}"
        )
    return completed.stdout


def main() -> int:
    try:
        status = get_worktree_status()
    except GitStatusError as exc:
        print(f"[FAIL] Cannot inspect the DataGen worktree: {exc}", file=sys.stderr)
        return 1

    if status:
        print(
            "[FAIL] DataGen left tracked, staged, or untracked non-ignored "
            "worktree changes:",
            file=sys.stderr,
        )
        for entry in status.splitlines():
            print(f"  {entry}", file=sys.stderr)
        print(
            "Expected an empty `git status --porcelain --untracked-files=all` "
            "result.",
            file=sys.stderr,
        )
        return 1

    print("[PASS] DataGen worktree is clean, including untracked files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
