"""Opt-in capture of a store's candidates in the faulted start of an X02 live case (W450).

The injector reports the size and SHA-256 of each write (attempt) of the faulted store, and no bytes. The recorder of
X01 cannot run beside it: the injector refuses to install when the file operations are already replaced. A candidate
that was moved is the store file itself until the next move of that store, so this keeper reads the store file when
the injector's `attempt ... moved=true` line arrives and keeps the bytes only when their size and SHA-256 are the ones
of that line. Nothing is kept for an attempt that was not moved, and a read that does not match is listed and never
kept. The keeper changes no command, log, observation or class of a case.

On Windows a move onto a file that another process holds open fails. The read here is one small read just after a
move, and the report lists every attempt that failed without an injected fault (`unfaulted_failures`), so a run that
the read disturbed shows.
"""
from __future__ import annotations

import hashlib
from pathlib import Path
import re
from typing import Callable

ATTEMPT = re.compile(r"ARCE_RELEASE_STORAGE_FAULT attempt store=(\w+) attempt=(\d+) result=(\S+) moved=(true|false) "
                     r"faulted=(true|false) bytes=(\d+) sha256=([0-9a-f]{64})")
# The registry's own bound on one root; a larger store file is listed and not read.
MAX_BYTES = 16 * 1024 * 1024
# ReleaseTestStorageFault.MAX_REPORTED_ATTEMPTS: the injector reports no attempt past it.
MAX_ATTEMPTS = 64


class Keeper:
    """Keeps, under `directory`, the store file after each moved attempt of `store` whose line it is given."""

    def __init__(self, store: str, target: Callable[[Path], Path], directory: Path):
        self.store, self.target, self.directory = store, target, directory
        self.rows: list[dict] = []

    def line(self, cwd: Path, line: str) -> None:
        """One line of the faulted start's log, as it arrives. Never raises: a failed read is a listed row."""
        match = ATTEMPT.search(line)
        if match is None or match.group(1) != self.store or len(self.rows) >= MAX_ATTEMPTS:
            return
        row = dict(attempt=int(match.group(2)), result=match.group(3), moved=match.group(4) == "true",
                   faulted=match.group(5) == "true", bytes=int(match.group(6)), sha256=match.group(7), kept=None)
        if row["moved"]:
            try:
                path = self.target(Path(cwd))
                if not path.is_file():
                    row["read_error"] = "The store file is absent"
                elif path.stat().st_size > MAX_BYTES:
                    row["read_error"] = f"The store file exceeds {MAX_BYTES} bytes"
                else:
                    content = path.read_bytes()
                    row.update(read_bytes=len(content), read_sha256=hashlib.sha256(content).hexdigest())
                    if (row["read_bytes"], row["read_sha256"]) == (row["bytes"], row["sha256"]):
                        self.directory.mkdir(parents=True, exist_ok=True)
                        name = f"attempt-{row['attempt']}.dat"
                        (self.directory / name).write_bytes(content)
                        row["kept"] = name
            except OSError as failure:
                row["read_error"] = f"{type(failure).__name__}: {failure}"[:200]
        self.rows.append(row)

    def report(self) -> dict:
        return dict(store=self.store, directory=self.directory.name,
                    rule="A candidate is kept only when the store file, read when the injector's line of a moved attempt "
                         "arrived, has the size and the SHA-256 of that line.",
                    attempts=list(self.rows),
                    unfaulted_failures=[row["attempt"] for row in self.rows
                                        if row["result"] != "moved" and not row["faulted"]])
