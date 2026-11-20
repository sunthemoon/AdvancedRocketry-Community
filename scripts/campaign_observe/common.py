"""Small safety and output helpers shared by the observers."""

import json
import math
import stat
import time
from datetime import datetime, timezone
from pathlib import Path


def utc_now():
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def safe_path(value):
    path = Path(value).absolute()
    for part in (path, *path.parents):
        if part.is_symlink():
            raise ValueError(f"Linked path refused: {part}")
        try:
            attributes = getattr(part.lstat(), "st_file_attributes", 0)
        except FileNotFoundError:
            continue
        if attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT:
            raise ValueError(f"Reparse path refused: {part}")
    return path.resolve()


def child(root, relative):
    root = safe_path(root)
    path = safe_path(root / relative)
    if path == root or not path.is_relative_to(root):
        raise ValueError("Output must be below the evidence root")
    return path


def write_json(path, value):
    with safe_path(path).open("x", encoding="utf-8") as stream:
        json.dump(value, stream, indent=2, allow_nan=False)
        stream.write("\n")


def finite_nonnegative(value):
    number = float(value)
    if not math.isfinite(number) or number < 0:
        raise ValueError("Expected a finite nonnegative number")
    return number


def schedule(duration, interval, *, clock=time.monotonic, sleep=time.sleep):
    """Bounded polling; command time is included, with no catch-up burst."""
    if not 0 < duration <= 86400 or not 0.1 <= interval <= 3600:
        raise ValueError("Duration must be (0, 86400] s and interval [0.1, 3600] s")
    deadline = clock() + duration
    while clock() < deadline:
        started = clock()
        yield
        delay = min(deadline - clock(), interval - (clock() - started))
        if delay > 0:
            sleep(delay)
