"""Test-side, bounded raw authority capture for the pinned bay native runners."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re
import shutil


def snapshot_world(world: Path, destination: Path, max_bytes: int = 8 * 1024**2) -> dict:
    """Keep journal/registry/player/entity bytes after stop, not a disposable server copy."""
    if destination.exists():
        raise FileExistsError(destination)
    files = []
    for path in sorted(world.rglob("*")):
        if path.is_symlink() or getattr(path, "is_junction", lambda: False)():
            raise ValueError(f"Linked world entry: {path}")
        if not path.is_file():
            continue
        relative = path.relative_to(world)
        if (relative.parts[0] == "data" or relative.name in ("level.dat", "level.dat_old")
                or "entities" in relative.parts and path.suffix == ".mca"
                or relative.parts[0] == "playerdata" and path.suffix == ".dat"):
            files.append((path, relative, path.stat().st_size))
    size = sum(row[2] for row in files)
    if size > max_bytes:
        raise ValueError(f"Raw authority snapshot exceeds {max_bytes} bytes: {size}")
    destination.mkdir(parents=True)
    rows = []
    for source, relative, length in files:
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        rows.append(dict(path=relative.as_posix(), bytes=length,
                         sha256=hashlib.sha256(target.read_bytes()).hexdigest()))
    result = dict(bytes=size, files=rows)
    (destination / "MANIFEST.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    return result


def observed_ticks(lines: list[str]) -> dict:
    """Preserve explicit tick observations without converting wall time into server ticks."""
    markers = []
    for line in lines:
        if "ARCE_" not in line:
            continue
        ticks = re.findall(r"\b(?:tick|ticks|deadline|firstTick)=(-?\d+)\b", line)
        if ticks:
            markers.append(dict(ticks=[int(value) for value in ticks], line=line.rstrip()))
    return dict(d_step_ticks="NOT_OBSERVABLE", tick_markers=markers,
                reason="C1 claim/proof transition logs do not include step start/end ticks; wall time is not ticks.")


def case_observations(directory: Path, case: str) -> dict:
    return {path.name: observed_ticks(path.read_text(encoding="utf-8", errors="replace").splitlines())
            for path in sorted(directory.glob(f"{case}-*.log"))}
