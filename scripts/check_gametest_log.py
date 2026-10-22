#!/usr/bin/env python3
"""Attribute every ERROR, FATAL and WARN entry of a GameTest log to a declared expectation.

Negative GameTests deliberately drive refusal and failure paths, and the code under
test logs those paths at ERROR or WARN exactly as production must. This checker reads a
complete Forge GameTest ``latest.log`` and the manifest
``scripts/gametest_expected_log.json``:

* a FATAL entry always fails;
* every ERROR or WARN entry must match exactly one expectation of its level;
* an expectation with ``counts`` must be observed exactly that many times in each named
  test batch and nowhere else;
* only a WARN expectation may use ``max`` instead, an upper bound for host- or
  environment-dependent warnings, optionally limited to ``batches``;
* one launch, completion, successful required-test summary and normal shutdown must
  appear in order; malformed headers and incomplete shutdown logs fail.

Every expectation names its logger and full message, and an expectation for an entry
that carries a stack trace can also name the GameTest whose deliberate failure the entry
records. Dedicated-server and client logs are out of scope: no ERROR is expected there.

Forge rolls ``latest.log`` into ``logs/<yyyy-MM-dd>-<n>.log.gz`` at midnight in the JVM's
time zone and when the next run starts. A ``latest.log`` that does not begin with the
GameTest launch is the rest of a run that crossed midnight: the checker rebuilds that run
from the rolled parts beside it, chosen by their content (launch header and event times),
and checks the rebuilt text exactly as an unsplit log. When no single complete run can be
rebuilt, it says why and checks ``latest.log`` alone, which fails as before.
"""

from __future__ import annotations

import argparse
import gzip
import io
import json
import re
import sys
import zlib
from collections import Counter
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_LOG = ROOT / "build" / "gametest" / "logs" / "latest.log"
DEFAULT_MANIFEST = ROOT / "scripts" / "gametest_expected_log.json"
MANIFEST_SCHEMA = 1
CHECKED_LEVELS = ("ERROR", "WARN")
STARTUP_BATCH = "<startup>"
SHUTDOWN_BATCH = "<shutdown>"
EXPECTATION_KEYS = frozenset(
    {"id", "level", "logger", "message", "stack_contains", "exception", "counts", "max", "batches", "test", "reason"}
)

HEADER = re.compile(
    r"^\[[^\]]+\] \[(?P<thread>.*)/(?P<level>TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\] "
    r"\[(?P<logger>[^\]]*)\]: (?P<message>.*)$"
)
BATCH_START = re.compile(r"^Running test batch '(?P<name>.+):\d+' \(\d+ tests\)\.\.\.$")
COMPLETE = re.compile(r"^=+ (?P<count>\d+) GAME TESTS COMPLETE =+$")
TEST_FRAME = re.compile(r"(?:GameTests?|Fixtures?)(?:\$[\w$]+)?\.[\w$]+$")
STACK_FRAME = re.compile(r"^\s+at\s+(?:[^\s/]*/)*(?P<frame>(?:[a-z_]\w*\.)+[A-Z][\w$]*\.[\w$]+)\(")
HEADER_LIKE = re.compile(r"^\[[^\r\n]*/(?:TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\b")
EVENT_TIMESTAMP = re.compile(r"\b\d{1,2}[A-Za-z]{3}\d{4}\s+\d{2}:\d{2}:\d{2}")
EVENT_FIELDS = re.compile(
    r"\[[^\]\r\n]*/(?:TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\b"
    r"[^\r\n]*\[[^\]\r\n]*\]\s*:"
)
PASSED = re.compile(r"^All (?P<count>\d+) required tests passed :\)$")
LAUNCH_LOGGER = "cpw.mods.modlauncher.Launcher/MODLAUNCHER"
BATCH_LOGGER = "net.minecraft.gametest.framework.GameTestBatchRunner/"
SERVER_LOGGER = "net.minecraft.gametest.framework.GameTestServer/"

# Forge's log4j2.xml (fmlloader) rolls the main log into these names; debug-<n>.log.gz is another log.
ROLLED_PART = re.compile(r"\d{4}-\d{2}-\d{2}-\d+\.log\.gz")
# The layout's %d{ddMMMyyyy HH:mm:ss.SSS}, in the JVM's time zone (the zone of its midnight roll).
EVENT_TIME = re.compile(
    r"^\[(?P<day>\d{2})(?P<month>[A-Z][a-z]{2})(?P<year>\d{4}) "
    r"(?P<hour>\d{2}):(?P<minute>\d{2}):(?P<second>\d{2})\.(?P<millis>\d{3})\] "
)
MONTHS = ("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
# A full v1.8 run logs about 6.3 MB; these bound what the rebuild decompresses.
MAX_PART_BYTES = 256 * 1024 * 1024
MAX_ROLLED_BYTES = 2 * 1024 * 1024 * 1024
MAX_ROLLED_PARTS = 1000
# The roll happens at the first event after midnight. Five full local runs on 2026-10-22 never
# went more than 23 seconds without an event, so a silence of more than ten minutes across a
# roll means a missing part or a part of another run.
MAX_ROLL_GAP = timedelta(minutes=10)


class ManifestError(ValueError):
    """Raised when the expected-log manifest is malformed."""


class RebuildError(ValueError):
    """Raised when a run that a midnight roll split cannot be rebuilt from its parts."""


@dataclass(frozen=True)
class Entry:
    """One log event: its header line and the continuation lines that follow it."""

    line: int
    level: str
    logger: str
    message: str
    batch: str
    continuation: tuple[str, ...]

    def test_frames(self) -> tuple[str, ...]:
        frames: list[str] = []
        for text in self.continuation:
            match = STACK_FRAME.match(text)
            if match and TEST_FRAME.search(match["frame"]) and match["frame"] not in frames:
                frames.append(match["frame"])
        return tuple(frames)


@dataclass(frozen=True)
class Expectation:
    id: str
    level: str
    logger: str
    message: re.Pattern[str]
    stack_contains: str | None
    exception: re.Pattern[str] | None
    counts: dict[str, int] | None
    maximum: int | None
    batches: frozenset[str] | None

    def describes(self, entry: Entry) -> bool:
        """Whether the entry has this expectation's level, logger, message and stack."""
        return (
            entry.level == self.level
            and entry.logger == self.logger
            and self.message.fullmatch(entry.message) is not None
            and (
                self.stack_contains is None
                or any(
                    (f".{self.stack_contains}" in f".{match['frame']}"
                     if self.stack_contains.endswith(".") else
                     match["frame"] == self.stack_contains or match["frame"].endswith("." + self.stack_contains))
                    for text in entry.continuation if (match := STACK_FRAME.match(text))
                )
            )
            and (self.exception is None or bool(entry.continuation)
                 and self.exception.fullmatch(entry.continuation[0]) is not None)
        )

    def allows_batch(self, batch: str) -> bool:
        if self.counts is not None:
            return batch in self.counts
        return self.batches is None or batch in self.batches


@dataclass
class ParsedLog:
    entries: list[Entry] = field(default_factory=list)
    completed_tests: int | None = None
    summary: list[str] = field(default_factory=list)
    problems: list[str] = field(default_factory=list)
    launched: bool = False
    passed_tests: int | None = None
    shutdown: bool = False


@dataclass
class Report:
    problems: list[str]
    notes: list[str]

    @property
    def passed(self) -> bool:
        return not self.problems


@dataclass(frozen=True)
class LogPart:
    """What the rebuild knows about one file of a run: its size, event times, launch and shutdown."""

    path: Path
    lines: int
    first: datetime
    last: datetime
    starts_run: bool
    launches: int
    shut_down: bool


@dataclass(frozen=True)
class RunText:
    """The text to check, the files (with their line ranges) it was rebuilt from, and why a rebuild was refused."""

    text: str
    sources: tuple[str, ...] = ()
    refusal: str | None = None


def _is_launch(level: str, logger: str, message: str) -> bool:
    return level == "INFO" and logger == LAUNCH_LOGGER and "--launchTarget, forgegametestserveruserdev" in message


def _is_shutdown(level: str, logger: str, message: str) -> bool:
    return level == "INFO" and logger == SERVER_LOGGER and message == "Game test server shutting down"


def parse_log(text: str) -> ParsedLog:
    """Split a log into entries and record the test batch that was running for each."""
    parsed = ParsedLog()
    batch = STARTUP_BATCH
    current: dict[str, object] | None = None
    continuation: list[str] = []

    def close() -> None:
        if current is not None and current["level"] in (*CHECKED_LEVELS, "FATAL"):
            parsed.entries.append(Entry(continuation=tuple(continuation), **current))

    for number, line in enumerate(text.splitlines(), start=1):
        if parsed.shutdown and line.strip():
            parsed.problems.append(f"line {number}: content after GameTest shutdown")
        header = HEADER.match(line)
        if header is None:
            if (HEADER_LIKE.match(line.lstrip("\ufeff \t")) or EVENT_TIMESTAMP.search(line)
                    or EVENT_FIELDS.search(line)
                    or current is None and line.strip()):
                parsed.problems.append(f"line {number}: malformed or unrecognized log header")
            continuation.append(line)
            continue
        close()
        continuation = []
        level, logger, message = header["level"], header["logger"], header["message"]
        if _is_launch(level, logger, message):
            if parsed.launched or current is not None:
                parsed.problems.append(f"line {number}: duplicate or misplaced GameTest launch")
            parsed.launched = True
        if level == "INFO" and logger == BATCH_LOGGER and (start := BATCH_START.match(message)):
            if not parsed.launched or parsed.completed_tests is not None:
                parsed.problems.append(f"line {number}: misplaced test batch")
            batch = start["name"]
        elif level == "INFO" and logger == SERVER_LOGGER and (complete := COMPLETE.match(message)):
            if parsed.completed_tests is not None or batch == STARTUP_BATCH:
                parsed.problems.append(f"line {number}: duplicate or misplaced completion banner")
            parsed.completed_tests = int(complete["count"])
            batch = SHUTDOWN_BATCH
        elif level == "INFO" and logger == SERVER_LOGGER and (passed := PASSED.match(message)):
            if parsed.completed_tests is None or parsed.passed_tests is not None:
                parsed.problems.append(f"line {number}: duplicate or misplaced required-test summary")
            parsed.passed_tests = int(passed["count"])
            parsed.summary.append(message)
        elif logger == SERVER_LOGGER and "required tests" in message:
            parsed.summary.append(message)
            parsed.problems.append(f"line {number}: unsuccessful or unrecognized required-test summary: {message}")
        elif _is_shutdown(level, logger, message):
            if parsed.passed_tests is None:
                parsed.problems.append(f"line {number}: shutdown before successful required-test summary")
            parsed.shutdown = True
        current = {"line": number, "level": level, "logger": logger, "message": message, "batch": batch}
    close()
    if not text.endswith("\n"):
        parsed.problems.append("the log ends with an incomplete line")
    return parsed


def _positive_int(value: object) -> bool:
    return isinstance(value, int) and not isinstance(value, bool) and value >= 1


def _expectation(raw: object, where: str) -> Expectation:
    if not isinstance(raw, dict):
        raise ManifestError(f"{where} must be an object")
    if unknown := sorted(set(raw) - EXPECTATION_KEYS):
        raise ManifestError(f"{where} has unknown keys {unknown}")
    for key in ("id", "level", "logger", "message", "test", "reason"):
        if not isinstance(raw.get(key), str) or not raw[key].strip():
            raise ManifestError(f"{where} needs a non-empty string {key!r}")
    level = raw["level"]
    if level not in CHECKED_LEVELS:
        raise ManifestError(f"{where} level must be one of {CHECKED_LEVELS}")
    stack_contains = raw.get("stack_contains")
    if stack_contains is not None and (not isinstance(stack_contains, str) or not stack_contains.strip()):
        raise ManifestError(f"{where} stack_contains must be a non-empty string when present")
    exception = raw.get("exception")
    if exception is not None and (not isinstance(exception, str) or not exception.strip()):
        raise ManifestError(f"{where} exception must be a non-empty string when present")

    counts, maximum, batches = raw.get("counts"), raw.get("max"), raw.get("batches")
    if (counts is None) == (maximum is None):
        raise ManifestError(f"{where} needs exactly one of 'counts' and 'max'")
    if counts is not None:
        if batches is not None:
            raise ManifestError(f"{where}: 'batches' only limits a 'max' expectation")
        if not isinstance(counts, dict) or not counts or not all(
            isinstance(batch, str) and batch.strip() and _positive_int(count) for batch, count in counts.items()
        ):
            raise ManifestError(f"{where} counts must map batch names to positive integers")
    else:
        if level != "WARN":
            raise ManifestError(f"{where}: only a WARN expectation may use 'max'; ERROR needs exact counts")
        if not _positive_int(maximum):
            raise ManifestError(f"{where} max must be a positive integer")
        if batches is not None and (not isinstance(batches, list) or not batches or not all(
            isinstance(batch, str) and batch.strip() for batch in batches
        )):
            raise ManifestError(f"{where} batches must be a non-empty list of batch names")
    try:
        message = re.compile(raw["message"])
        exception = re.compile(exception) if exception is not None else None
    except re.error as exc:
        raise ManifestError(f"{where} message is not a valid pattern: {exc}") from exc
    return Expectation(
        id=raw["id"],
        level=level,
        logger=raw["logger"],
        message=message,
        stack_contains=stack_contains,
        exception=exception,
        counts=dict(counts) if counts is not None else None,
        maximum=maximum,
        batches=frozenset(batches) if batches is not None else None,
    )


def load_manifest(path: Path) -> list[Expectation]:
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ManifestError(f"cannot read {path}: {exc}") from exc
    if (not isinstance(document, dict) or type(document.get("schema")) is not int
            or document.get("schema") != MANIFEST_SCHEMA):
        raise ManifestError(f"{path}: expected an object with schema {MANIFEST_SCHEMA}")
    raw_entries = document.get("expectations")
    if not isinstance(raw_entries, list) or not raw_entries:
        raise ManifestError(f"{path}: expectations must be a non-empty list")
    expectations: list[Expectation] = []
    for index, raw in enumerate(raw_entries):
        expectation = _expectation(raw, f"{path}: expectation {index}")
        if any(existing.id == expectation.id for existing in expectations):
            raise ManifestError(f"{path}: expectation {index} repeats id {expectation.id!r}")
        expectations.append(expectation)
    return expectations


def check(parsed: ParsedLog, expectations: list[Expectation]) -> Report:
    problems: list[str] = list(parsed.problems)
    notes: list[str] = []
    if parsed.completed_tests is None:
        problems.append("the log has no 'GAME TESTS COMPLETE' banner; it is truncated or not a GameTest log")
    else:
        notes.append("; ".join([f"{parsed.completed_tests} game tests complete", *parsed.summary]))
    if not parsed.launched:
        problems.append("the log has no GameTest userdev launch header")
    if parsed.passed_tests is None:
        problems.append("the log has no successful required-test summary")
    elif parsed.completed_tests is not None and not 1 <= parsed.passed_tests <= parsed.completed_tests:
        problems.append("required-test summary count is inconsistent with the completion banner")
    if not parsed.shutdown:
        problems.append("the log has no normal GameTest shutdown marker; shutdown may be truncated")

    observed: dict[str, Counter[str]] = {expectation.id: Counter() for expectation in expectations}
    levels: Counter[str] = Counter()
    for entry in parsed.entries:
        levels[entry.level] += 1
        if entry.level == "FATAL":
            problems.append(describe(entry, "FATAL entry"))
            continue
        described = [expectation for expectation in expectations if expectation.describes(entry)]
        matched = [expectation for expectation in described if expectation.allows_batch(entry.batch)]
        if len(matched) == 1:
            observed[matched[0].id][entry.batch] += 1
        elif matched:
            names = ", ".join(expectation.id for expectation in matched)
            problems.append(describe(entry, f"{entry.level} matches several expectations ({names})"))
        elif described:
            names = ", ".join(expectation.id for expectation in described)
            problems.append(describe(entry, f"{entry.level} of {names} in an undeclared batch"))
        else:
            problems.append(describe(entry, f"unexpected {entry.level}"))

    for expectation in expectations:
        seen = observed[expectation.id]
        if expectation.counts is not None:
            for batch, count in sorted(expectation.counts.items()):
                if seen[batch] != count:
                    problems.append(
                        f"expectation {expectation.id}: expected exactly {count} {expectation.level} "
                        f"entries in batch {batch!r}, observed {seen[batch]}"
                    )
        elif (total := sum(seen.values())) > expectation.maximum:
            problems.append(
                f"expectation {expectation.id}: expected at most {expectation.maximum} "
                f"{expectation.level} entries, observed {total}"
            )
    notes.append(", ".join(f"{level} {levels[level]}" for level in ("FATAL", *CHECKED_LEVELS)))
    return Report(problems=problems, notes=notes)


def describe(entry: Entry, what: str) -> str:
    frames = ", ".join(entry.test_frames()) or "no GameTest frame"
    return (
        f"line {entry.line}: {what}, batch {entry.batch!r}, logger {entry.logger!r}: "
        f"{entry.message[:240]} [{frames}]"
    )


def inventory(parsed: ParsedLog) -> list[str]:
    """Group checked entries so a maintainer can see what a new expectation must name."""
    groups: Counter[tuple[str, str, str, str, str]] = Counter()
    for entry in parsed.entries:
        message = re.sub(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "<uuid>", entry.message)
        message = re.sub(r"-?\d+", "<n>", message)
        groups[(entry.level, entry.batch, entry.logger, message, ", ".join(entry.test_frames()) or "-")] += 1
    return [
        f"{count:4d}  {level}  {batch}  {logger}  {message}  [{frames}]"
        for (level, batch, logger, message, frames), count in sorted(groups.items())
    ]


def starts_run(text: str) -> bool:
    """Whether the first non-blank line is the GameTest launch header, as every complete run begins."""
    for line in text.splitlines():
        if line.strip():
            header = HEADER.match(line)
            return header is not None and _is_launch(header["level"], header["logger"], header["message"])
    return False


def _event_time(line: str) -> datetime | None:
    match = EVENT_TIME.match(line)
    if match is None or match["month"] not in MONTHS:
        return None
    try:
        return datetime(int(match["year"]), MONTHS.index(match["month"]) + 1, int(match["day"]),
                        int(match["hour"]), int(match["minute"]), int(match["second"]), int(match["millis"]) * 1000)
    except ValueError:
        return None


def _when(time: datetime) -> str:
    return time.isoformat(sep=" ", timespec="milliseconds")


def _scan(path: Path, text: str) -> LogPart:
    """Read the event times, launches and shutdown of one part; a roll always starts a part with an event."""
    lines = text.splitlines()
    first = _event_time(lines[0]) if lines and HEADER.match(lines[0]) else None
    if first is None:
        raise RebuildError(f"{path.name} does not begin with a timestamped log event")
    last, launches, shut_down = first, 0, False
    for number, line in enumerate(lines, start=1):
        if (header := HEADER.match(line)) is None:
            continue
        if (time := _event_time(line)) is None:
            raise RebuildError(f"{path.name} line {number}: the event time cannot be read")
        fields = (header["level"], header["logger"], header["message"])
        last = max(last, time)
        launches += _is_launch(*fields)
        shut_down = shut_down or _is_shutdown(*fields)
    return LogPart(path, len(lines), first, last, starts_run(lines[0]), launches, shut_down)


def _read_rolled_part(path: Path) -> tuple[str, int]:
    """Decompress at most MAX_PART_BYTES of a rolled part; return it decoded as Path.read_text would, and its size."""
    try:
        with gzip.open(path, "rb") as stream:
            data = stream.read(MAX_PART_BYTES + 1)
    except (OSError, EOFError, zlib.error) as exc:
        raise RebuildError(f"cannot read {path.name}: {exc}") from exc
    if len(data) > MAX_PART_BYTES:
        raise RebuildError(f"{path.name} expands to more than {MAX_PART_BYTES:,} bytes")
    try:
        return io.TextIOWrapper(io.BytesIO(data), encoding="utf-8").read(), len(data)
    except UnicodeDecodeError as exc:
        raise RebuildError(f"cannot read {path.name}: {exc}") from exc


def rebuild_run(latest: Path, text: str) -> RunText:
    """Rebuild the run that ``latest`` (with this text) ends, from the rolled parts in its folder.

    Walking back from ``latest``, each earlier part is the one that ends last before its successor
    begins; a midnight must fall between them, with no longer silence than MAX_ROLL_GAP. The part
    that begins with the launch header starts the run. No other part may overlap the rebuilt run.
    """
    tail = _scan(latest, text)
    if tail.launches:
        raise RebuildError(f"{latest.name} holds a GameTest launch after its first line")
    try:
        paths = sorted(path for path in latest.parent.iterdir()
                       if ROLLED_PART.fullmatch(path.name) and path.is_file())
    except OSError as exc:
        raise RebuildError(f"cannot list {latest.parent}: {exc}") from exc
    if len(paths) > MAX_ROLLED_PARTS:
        raise RebuildError(f"the folder holds {len(paths)} rolled parts, more than {MAX_ROLLED_PARTS}")
    parts: list[LogPart] = []
    total = 0
    for path in paths:
        part_text, size = _read_rolled_part(path)
        total += size
        if total > MAX_ROLLED_BYTES:
            raise RebuildError(f"the rolled parts expand to more than {MAX_ROLLED_BYTES:,} bytes")
        parts.append(_scan(path, part_text))

    chain = [tail]
    while not chain[0].starts_run:
        successor = chain[0]
        earlier = [part for part in parts if part.last < successor.first]
        if not earlier:
            raise RebuildError(f"no rolled part ends before {successor.path.name} begins at "
                               f"{_when(successor.first)}; the first part of the run is missing")
        end = max(part.last for part in earlier)
        closest = [part for part in earlier if part.last == end]
        if len(closest) > 1:
            raise RebuildError(f"{' and '.join(part.path.name for part in closest)} both end at {_when(end)}; "
                               f"cannot tell which one {successor.path.name} continues")
        part = closest[0]
        if part.last.date() >= successor.first.date():
            raise RebuildError(f"{part.path.name} ends at {_when(part.last)} and {successor.path.name} begins at "
                               f"{_when(successor.first)} on the same day, so no midnight roll separates them")
        if successor.first - part.last > MAX_ROLL_GAP:
            raise RebuildError(f"{part.path.name} ends at {_when(part.last)}, {successor.first - part.last} before "
                               f"{successor.path.name} begins; a part is missing or {part.path.name} "
                               f"belongs to another run")
        if part.shut_down:
            raise RebuildError(f"{part.path.name} holds a GameTest shutdown, so it belongs to an earlier run")
        if part.launches != int(part.starts_run):
            raise RebuildError(f"{part.path.name} holds a GameTest launch after its first line")
        chain.insert(0, part)
    start, end = chain[0].first, max(part.last for part in chain)
    for part in parts:
        if part not in chain and part.first <= end and part.last >= start:
            raise RebuildError(f"{part.path.name} ({_when(part.first)} to {_when(part.last)}) overlaps the rebuilt "
                               f"run ({_when(start)} to {_when(end)}); cannot tell which parts belong to it")

    texts: list[str] = []
    ranges: list[str] = []
    number = 1
    for part in chain:
        if part is tail:
            part_text = text
        else:
            part_text = _read_rolled_part(part.path)[0]
            if _scan(part.path, part_text) != part:
                raise RebuildError(f"{part.path.name} changed while it was read")
            if not part_text.endswith("\n"):
                raise RebuildError(f"{part.path.name} ends with an incomplete line")
        texts.append(part_text)
        ranges.append(f"{part.path.name} (lines {number:,}-{number + part.lines - 1:,})")
        number += part.lines
    return RunText("".join(texts), tuple(ranges))


def load_run(path: Path) -> RunText:
    """Read the log to check, rebuilding the run when it is a ``latest.log`` split at midnight.

    Raises OSError or UnicodeDecodeError when the log itself cannot be read.
    """
    text = path.read_text(encoding="utf-8")
    if path.name != "latest.log" or starts_run(text):
        return RunText(text)
    try:
        return rebuild_run(path, text)
    except RebuildError as exc:
        return RunText(text, refusal=f"{path.name} does not begin with a GameTest launch, and its run cannot "
                                     f"be rebuilt from the rolled parts beside it: {exc}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("log", nargs="?", type=Path, default=DEFAULT_LOG)
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--inventory", action="store_true",
                        help="list every ERROR, FATAL and WARN group instead of checking")
    arguments = parser.parse_args(argv)

    try:
        run = load_run(arguments.log)
    except (OSError, UnicodeDecodeError) as exc:
        print(f"[FAIL] Cannot read GameTest log {arguments.log}: {exc}", file=sys.stderr)
        return 2
    if run.sources:
        print(f"[INFO] {arguments.log.name} continues a run that a midnight roll split; checking the run "
              f"rebuilt from {', '.join(run.sources)} in {arguments.log.parent}")
    parsed = parse_log(run.text)
    if arguments.inventory:
        if run.refusal:
            print(f"[WARN] {run.refusal}", file=sys.stderr)
        print("\n".join(inventory(parsed)))
        return 0
    try:
        expectations = load_manifest(arguments.manifest)
    except ManifestError as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 2

    report = check(parsed, expectations)
    if run.refusal:
        report.problems.insert(0, run.refusal)
    for note in report.notes:
        print(f"[INFO] {note}")
    if report.passed:
        checked = f"the run rebuilt from {len(run.sources)} files ending in " if run.sources else ""
        print(f"[PASS] Every ERROR and WARN entry of {checked}{arguments.log} matches one of "
              f"{len(expectations)} expectations with its declared count; no FATAL entry")
        return 0
    for problem in report.problems:
        print(f"[FAIL] {problem}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
