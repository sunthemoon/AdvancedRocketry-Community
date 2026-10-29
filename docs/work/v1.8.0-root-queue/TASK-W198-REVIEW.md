# TASK W198-review: independent read-only review of the N3-B4 terrain matrix

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w198` at `1e86fa5a2`, cut from `124deb404`; the slice is `124deb404..1e86fa5a2` (4 commits, test-side only). Review worktree `D:/GitHub/arce-v180-w198-review`, a detached checkout of `1e86fa5a2` |
| Task it answers | [TASK-W198](TASK-W198-N3-B4-COMBINED-TERRAIN-MATRIX.md) |
| Record | `docs/work/v1.8.0-root-queue/records/W198.md` on the branch (the mapping plan at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W198/` (about 34.1 MB); the inventory `D:/GitHub/ARCE-Task-Evidence/root-queue/W164/r156-terrain-inventory.json` |
| Hosted run | the run on `1e86fa5a2` (Root pushed the branch at 03:16). The branch predates W201, so W196's two-claim case `outsideCopyDuringPartialCaptureHoldsOnlyItsClaim` may fail there; that is a known shared-head fault W201 fixed, not W198's |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W198-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

W198 reports all 89 rows passing, 357 counted starts in six tranches, all 357 native archives replayed and 178 clean
restart observations verified, with one harness failure fixed by `2422da0f7` (injected terrain journal UNKNOWN logs).

## What to check

1. **The mapping.** `f4ff2af47` was committed before any native run. Every one of the 89 inventory rows is mapped to
   a cut and an oracle on the integrated I6 path; each row called equivalent or impossible has a sound reason; none is
   dropped or folded silently; the start count matches.
2. **The oracle.** For each row: I6's durable D1, exact T, complete resources, no old S, U or terrain duplicate, and two
   unchanged restarts. Is any of these checked weakly, or only by absence of a log line?
3. **The harness.** `W152NativeI6`, the `W152NativeProbe` changes and the two scripts: the integrated path really
   goes through I6's installed request (not the direct B2 hook); the non-integrated paths are unchanged.
4. **`2422da0f7`.** Which UNKNOWN log lines it now accepts, that each is one the row injects, and that nothing else
   (an unexpected ERROR, a real failure) can pass because of it. Its Python test failed first.
5. **The evidence.** Reproduce the tranche ledgers, the start counts and a sample of replays from the evidence; check
   the hashes; the JAR was built from committed source.
6. **Nothing weakened:** no production change, and no change to formats, bounds, budgets, timeouts or assertions.
7. **Merge conflicts** against the shared head (`origin/codex/v1.8.0-classic-content`, which now includes W200, W201
   and W202).

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: the `rocket.server` unit package; the W198
Python tests and its audit or replay scripts on the retained evidence. Targeted unit runs need no heavy-JVM lock. Check
6 GiB free memory first. No GameTest, native case, runData, full build or server. Delete the worktree's `build`,
`.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W198-R1, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
