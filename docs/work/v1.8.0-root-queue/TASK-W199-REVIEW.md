# TASK W199-review: independent read-only review of the I6 preparation-cost fixes

Root: Claude. Date: 2026-10-29. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w199` at `74aa6acbd`, cut from `406a4538c`; the slice is `406a4538c..74aa6acbd` (8 commits). Review worktree `D:/GitHub/arce-v180-w199-review`, a detached checkout of `74aa6acbd` |
| Task it answers | [TASK-W199](TASK-W199-I6-PREPARATION-COST.md) (Root decision R169) |
| Record | `docs/work/v1.8.0-root-queue/records/W199.md` on the branch (attribution and design at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W199/` and W197's in `.../W197/` |
| Hosted run | the run on `74aa6acbd` (Root pushed the branch at 18:09); read its result if it has finished |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W199-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

W199 reports preparation P99 down from 327 ms to 41 ms and the proof-window maximum from 4,343 ms to 341 ms, with two
production fixes: `7645639d2` bounds cold I6 ticket starts without synchronous loads, and `70b2baf57` bounds write-back
validation to the candidate record. This touches I6 preparation and journal write-back, both persistence paths.

## What to check

1. **The attribution.** Do the JFR profiles support the stated causes of the 5.8 s ticks and of `keepLoaded`'s cost?
2. **`7645639d2`.** Readiness still requires exactly what it did (every checked column entity-loaded and ticking),
   the per-tick bound on ticket starts is fixed and documented, the existing bound (at most 16 columns per end) holds,
   and no path now treats an unloaded column as ready or skips a proof. Can the bound starve a preparation forever?
3. **`70b2baf57`.** Validating only the candidate record during write-back: can a write-back now accept a journal
   state that the whole-root validation would have refused? Check against the journal's invariants and W148.
4. **The two undeclared `DEADLINE` WARNs** in sealed batches that the record leaves open: are they caused by these
   changes (for example a preparation that now takes more ticks), and is each a real regression or a legitimate new
   line? Say which.
5. **The first-round substantive-work P99 of 138.57 ms**, still over docs/17's 100 ms: where it goes, and whether a
   further bounded remedy exists.
6. **Nothing weakened:** formats, bounds, budgets, quotas, timeouts, assertions and `RocketTransferService`.
7. **Risk text:** which sentences of R-035 or R-040 these changes make wrong, for Root.
8. **Merge conflicts** against the shared head (`origin/codex/v1.8.0-classic-content`) and W196 (`codex/v1.8.0-w196`).

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: the new and changed unit classes and the
`rocket.server`, `rocket.flight.persistence` and `rocket.sealed` packages; the W197 and W199 Python tests. Targeted
unit runs need no heavy-JVM lock. Check 6 GiB free memory first. No GameTest, native case, runData, full build or
server. Delete the worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W199-R1, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
