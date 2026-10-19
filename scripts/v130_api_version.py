#!/usr/bin/env python3
"""The host's adapter API version, read from its one definition in the Java source.

The adapter fixture logs ``ApiVersions.current()`` in each registration line as ``API <major>.<minor>``. The v1.3
native smokes expect ``CURRENT`` there instead of each pinning a literal, so that an API release changes only
``ApiVersions.CURRENT`` (W101, W92-R9). The smokes run from a checkout of the commit that built the tested JAR.
"""

from __future__ import annotations

import re
from pathlib import Path

SOURCE = (Path(__file__).resolve().parent.parent / "src/main/java/io/github/sunthemoon/advancedrocketrycommunity"
          / "api/version/ApiVersions.java")
DEFINITION = re.compile(r"private static final ApiVersion CURRENT = new ApiVersion\((0|[1-9]\d{0,3}), (0|[1-9]\d{0,3})\);")
LIMIT = 64 * 1024


def read(path: Path = SOURCE) -> str:
    """Return ``<major>.<minor>`` from the source's single CURRENT definition, or refuse."""
    with path.open("rb") as stream:
        data = stream.read(LIMIT + 1)
    if len(data) > LIMIT:
        raise ValueError(f"API version source exceeds {LIMIT} bytes: {path}")
    matches = DEFINITION.findall(data.decode("utf-8"))
    if len(matches) != 1:
        raise ValueError(f"Expected one ApiVersions.CURRENT definition in {path}, found {len(matches)}")
    return ".".join(matches[0])


CURRENT = read()
