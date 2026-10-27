# TASK W168-review: independent read-only review of the ground preflight fast path

Root: Claude. Date: 2026-10-28. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w168` at `00409f25c`, on `67cc6e01e`. Read `git diff 67cc6e01e..00409f25c`. Review worktree `D:/GitHub/arce-v180-w168-review`, a detached checkout of `00409f25c` |
| Task it answers | [TASK-W168](TASK-W168-GROUND-PREFLIGHT.md) |
| Record | `docs/work/v1.8.0-root-queue/records/W168.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W168/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W168-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **No answer changes.** `GroundSurveys` now skips the whole-root encode when the existing reservations, plus the
   candidate's job, instance and new-account bytes, bound the root. Check:
   - that the added bytes are the exact encodings that the candidate adds;
   - that every record already in the root is covered by a reservation at least as large as its encoding;
   - that no START, account creation or other change that the whole-root preflight would refuse can now succeed.

   Name any record kind or path the bound misses, such as an instance replaced or reused by a START, or an account
   created by a credit.
2. **Existing tests.** Two earlier test classes changed:
   - `GroundSurveyScaleTest` now expects zero whole-root checks;
   - `GroundSurveyBoundsTest`'s refusal fixture now overflows the reservation bound, so it still reaches the refusal.

   Check that neither change weakens what the test protects, and that the refusal still leaves nothing changed.
3. **The new tests** fail on `2a7069390`, as the record says, and pin the boundary: exactly at the bound, one byte
   over, and a restored over-budget root, with the real codec.
4. **The measurements.** Check the benchmark's fixtures and sampling, and the record's before and after table.
   Root's reading, under R128: the slow path is a single spike of about 220 ms within docs/17 section 4's 500 ms cap.
   It runs only for a START or account creation within about 8 KiB of the 16 MiB bound. Ordinary changes on a root
   restored above its section budgets keep a slow path that is older than this slice. Say whether the evidence
   supports that reading.
5. **Nothing else changed:** formats, schema, bounds, section budgets, quotas, timeouts and the protocol.

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`:
- `GroundPreflightTest`, `GroundSurveyScaleTest` and `GroundSurveyBoundsTest`;
- the `satellite` packages.

Targeted unit runs need no heavy-JVM lock. Check 6 GiB free memory first. No GameTest, benchmark run, runData, full
build or server. Delete the worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W168-R1, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
