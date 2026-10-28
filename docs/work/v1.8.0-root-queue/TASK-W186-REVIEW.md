# TASK W186-review: independent read-only review of the sealed discovery revisit

Root: Claude. Date: 2026-10-29. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w186` at `e002c4b23`, cut from `073a97f7f`; the slice is three commits: `7c64d1bea` (test), `4f4084cae` (fix) and `e002c4b23` (record). Review worktree `D:/GitHub/arce-v180-w186-review`, a detached checkout of `e002c4b23` |
| Task it answers | [TASK-W186](TASK-W186-SEALED-DISCOVERY-REVISIT.md) (Root decision R146) |
| Diagnosis | [records/W184.md](records/W184.md), "Failure 1" |
| Record | `docs/work/v1.8.0-root-queue/records/W186.md` on the branch, with its design note |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W186/` |
| Hosted run | the run on `e002c4b23` (Root pushed the branch at 05:01); read its result if it has finished |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W186-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

Codex implemented it, so a Claude reviewer gives the independent read.

## What to check

1. **The revisit.** After `ConcurrentModificationException`, the cursor restarts the same chunk's block-entity
   iterator, at most as many times as the chunk had block entities when the visit began. Each restart consumes the
   step that was already charged.
   - Is every step still charged to `SealedWorkBudget`, so no tick does more work than before?
   - Each restart scans the chunk from its first block entity again. What is the worst case for a chunk with many
     block entities whose map keeps changing, in steps and in ticks that the sweep stays on that chunk? Is it bounded,
     and does it starve other chunks or other sealed work?
   - Can the same carrier be found twice, or a carrier be skipped, because the iterator restarts?
   - Does a chunk that unloads, or a Level that closes, during a revisit end the visit cleanly?
2. **Publication.** A publication still replaces only the carrier the attempt names; nothing else about attempts,
   journal entries or saved formats changes.
3. **The tests.** The A0 test fails first on the unfixed code and covers a map that keeps changing. The GameTest
   fixture is unchanged, and the record's before and after tick counts come from complete runs.
4. **Merge conflicts** against the shared head (`origin/codex/v1.8.0-classic-content`).

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: `SealedLoadedDiscoveryTest` and the
`rocket.server` and `rocket.sealed` packages. Check 6 GiB free memory first. No GameTest, native case, runData, full
build or server. Delete the worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W186-R1, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
