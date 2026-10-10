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
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter
from dataclasses import dataclass, field
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
HEADER_LIKE = re.compile(r"^\[[^\]]+\]\s+\[[^\]]+/(?:TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\]")
PASSED = re.compile(r"^All (?P<count>\d+) required tests passed :\)$")
LAUNCH_LOGGER = "cpw.mods.modlauncher.Launcher/MODLAUNCHER"
BATCH_LOGGER = "net.minecraft.gametest.framework.GameTestBatchRunner/"
SERVER_LOGGER = "net.minecraft.gametest.framework.GameTestServer/"


class ManifestError(ValueError):
    """Raised when the expected-log manifest is malformed."""


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
            if HEADER_LIKE.match(line.lstrip("\ufeff \t")) or current is None and line.strip():
                parsed.problems.append(f"line {number}: malformed or unrecognized log header")
            continuation.append(line)
            continue
        close()
        continuation = []
        level, logger, message = header["level"], header["logger"], header["message"]
        if level == "INFO" and logger == LAUNCH_LOGGER and "--launchTarget, forgegametestserveruserdev" in message:
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
        elif level == "INFO" and logger == SERVER_LOGGER and message == "Game test server shutting down":
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


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("log", nargs="?", type=Path, default=DEFAULT_LOG)
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--inventory", action="store_true",
                        help="list every ERROR, FATAL and WARN group instead of checking")
    arguments = parser.parse_args(argv)

    try:
        text = arguments.log.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError) as exc:
        print(f"[FAIL] Cannot read GameTest log {arguments.log}: {exc}", file=sys.stderr)
        return 2
    parsed = parse_log(text)
    if arguments.inventory:
        print("\n".join(inventory(parsed)))
        return 0
    try:
        expectations = load_manifest(arguments.manifest)
    except ManifestError as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 2

    report = check(parsed, expectations)
    for note in report.notes:
        print(f"[INFO] {note}")
    if report.passed:
        print(f"[PASS] Every ERROR and WARN entry of {arguments.log} matches one of "
              f"{len(expectations)} expectations with its declared count; no FATAL entry")
        return 0
    for problem in report.problems:
        print(f"[FAIL] {problem}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
