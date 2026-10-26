# TASK W145: governed launches discarded before ARMED capture (`NOT_ENQUEUED`), reproducible under the cargo reference load

Root: Claude. Date: 2026-10-23. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W139](TASK-W139-INTERMITTENT-DISCARDED-LAUNCHES.md)
with W139's record, and W144's record and its R85 analysis
(`D:/GitHub/ARCE-Task-Evidence/root-queue/W144/r85-analysis/analysis.json`) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w145` (new; from the shared branch at the commit named in your prompt, which has W139's discard lines and W144's harness) |
| Branch | `codex/v1.8.0-w145` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W145.md` (new; a one-page plan at its top before any code) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W145/` |

## The finding

W144's native reference load (`scripts/run_v180_cargo_native_benchmark.py`) on a fresh world with room in the ledger
sent eight governed launches; all eight logged `ARCE_LAUNCH_DISCARD site=F1_CALLBACK reason=NO_ACKNOWLEDGED_RECORD`, and
the analysis shows each was `NOT_ENQUEUED` before its ARMED capture, not a failed write. The same family appears
intermittently in complete GameTest runs (W139's table: `restoredAscentAheadOfLevelClockResumesWithoutResourceChanges`,
the two Moon-flight tests, the settlement-idle L2 launch). Production escrow admission is off, but governed launches
run through this path.

## What to do

1. Reproduce with W144's reference load and find exactly why `EscrowFlightCheckpoint` (or its queue) does not enqueue the
   ARMED capture: the condition that answers, the queue and writer state at that moment, and why the callback then runs
   with no record instead of waiting or retrying. Record what you observe, not inferences.
2. Write the one-page plan with the cause and the fix: a launch must either be enqueued and acknowledged, or be refused
   with a clear reason the caller reports; it must not be dropped silently. No saved format, schema, bound, lease,
   budget or timeout change; if the fix seems to need one, stop and report.
3. Implement it, each step in its own commit with a test that fails before the change: a unit test of the queue
   condition, and a GameTest or native case that reproduces the discard and passes after the fix.
4. Verify: W144's reference load accepts all eight flights (report the counts); the W139 tests above pass in repeated
   filtered runs under CPU load (report the counts); one complete GameTest with the unchanged log checker.

This is a persistence slice: an independent read-only review by a fresh Codex session follows before merge.

## Verification

Each changed class alone, the whole unit suite, the Python tests you touch; the heavy runs under the heavy-JVM lock with
the atomic pattern and the hand-off rule in WORKER-RULES. Check C: and D: above 10 GiB and 6 GiB free memory first. Keep
the evidence under 100,000,000 bytes.

## Not in this task

The cargo visual republication (W146), enabling admission, ADRs, the risk register, status files and the ledger (Root).
