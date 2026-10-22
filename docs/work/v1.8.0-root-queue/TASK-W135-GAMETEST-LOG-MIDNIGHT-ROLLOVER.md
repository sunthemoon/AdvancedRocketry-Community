# TASK W135: the GameTest log checker loses a run that crosses midnight

Root: Claude. Date: 2026-10-22. Read [WORKER-RULES](WORKER-RULES.md) and `scripts/check_gametest_log.py` with its tests
(`tests/test_check_gametest_log.py`) first. You are a new session. This is a parallel bug: take it only in a slot that
does not delay progress tasks.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w135` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w135` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W135.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W135/` |

## The defect

Hosted regression run 39693516643 (commit `339dd2a1`) ran its GameTests from 23:37 to 00:13 UTC. All 1,298 required
tests passed, but the log checker failed: "the log has no GameTest userdev launch header", many "misplaced test batch"
lines and zero counts for most WARN and ERROR expectations. Forge's `log4j2.xml` (in `fmlloader`) writes
`logs/latest.log` through a `RollingRandomAccessFile` with `filePattern="logs/%d{yyyy-MM-dd}-%i.log.gz"`,
`TimeBasedTriggeringPolicy`, `OnStartupTriggeringPolicy` and `DefaultRolloverStrategy max="99" fileIndex="min"`. At
midnight in the JVM's time zone (UTC on the hosted runner, UTC+8 on this machine) the first part of the run moves into a
`.log.gz` file, and `latest.log` keeps only the rest. The startup trigger also rolls the previous run's log into a
`.log.gz` file in the same folder, so a folder can hold parts of several runs.

## What to do

In `scripts/check_gametest_log.py` only (plus its tests): when the checked log is a `latest.log` whose first lines are
not the start of a run, rebuild the current run from the rolled parts beside it, then check the rebuilt text exactly
as today. Identify the current run's parts from their content (the launch header and the line timestamps, which carry
the date), not from file names or modification times alone, because the startup roll writes the previous run's part at
the same time. Refuse, as today, when no complete run can be rebuilt (a missing part, a gap, two candidate parts, a part
from another run). Print which files were used. Do not read gzip input without a size bound.

No expectation, count, rule or manifest entry changes, and no check becomes weaker: a log without a midnight roll is
checked byte-for-byte as before. The Gradle `doLast` check in `build.gradle` and the hosted workflow are Root's; if they
need a change, describe it in the record and Root makes it.

## Tests

Unit tests with synthetic logs: a run split at midnight into a `.log.gz` part and `latest.log` passes exactly as the
joined text does; a folder that also holds the previous run's part picks the right one; a missing first part, a gap and
an ambiguous pair are refused; an unsplit log behaves as before; an oversized gzip part is refused. Then the whole
Python test file and the repository validator. No GameTest run is needed; if you want one real check, take a copy of a
real `latest.log`, split it yourself at a line boundary and gzip the first part.

## Not in this task

`build.gradle`, the workflows, Forge's logging configuration, status files and the ledger (Root).
