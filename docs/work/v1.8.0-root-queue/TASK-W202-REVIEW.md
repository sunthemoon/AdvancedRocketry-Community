# TASK W202-review: independent read-only review of the I6 write-back sizing

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w202` at `69a6abdc0`, cut from `9820b18c9`; the slice is `9820b18c9..69a6abdc0` (5 commits). Review worktree `D:/GitHub/arce-v180-w202-review`, a detached checkout of `69a6abdc0` |
| Task it answers | [TASK-W202](TASK-W202-I6-WRITE-BACK-SIZING.md) |
| Record | `docs/work/v1.8.0-root-queue/records/W202.md` on the branch (the design note at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W202/` |
| Hosted run | the run on `69a6abdc0` (Root pushed the branch at 01:42); read its result if it has finished |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W202-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

W202 reports the callback-inclusive P99 down from 168.25 ms to 63.83 ms and the worst proof tick from 242.56 ms to
63.99 ms. `f232b0909` counts NBT bytes through a null output stream instead of a byte buffer; `7c331f41a` returns the
journal root's byte count from the codec's own limit check instead of sizing the root again. Both are on the save and
insertion limit paths.

## What to check

1. **Exact sizing.** `RocketNbtSize.uncompressedBytes` returns the same count as before for every tag, including
   modified-UTF strings, empty and nested tags and sizes near each limit; failures (such as an over-long string) still
   fail the same way; nothing can wrap or saturate below a bound that matters.
2. **The reused count.** `encodeRootBytes` returns the size of exactly the root it built and checked, and no caller can
   use it for a different or later-mutated tag. Every codec, record and root check still runs, and the limit check and
   its exception are unchanged.
3. **The measurement.** `e4d1c8d58` and `scripts/w202_callback_summary.py`: the old summaries are kept unchanged, the
   new callback-inclusive cohort is correct, and the before and after runs are comparable (same harness, entry point
   and load). Reproduce the reported figures from the evidence.
4. **Nothing weakened:** formats, bounds, budgets, quotas, the callback maximum, timeouts, assertions and
   `RocketTransferService`.
5. **Risk text:** which sentences of R-035 these changes make wrong, for Root.
6. **Merge conflicts** against the shared head (`origin/codex/v1.8.0-classic-content`) and W198 (`codex/v1.8.0-w198`).

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: the new and changed unit classes and the
`rocket.model`, `rocket.flight.persistence` and `rocket.sealed` packages; the W197, W200 and W202 Python tests.
Targeted unit runs need no heavy-JVM lock. Check 6 GiB free memory first. No GameTest, native case, runData, full build
or server. Delete the worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W202-R1, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
