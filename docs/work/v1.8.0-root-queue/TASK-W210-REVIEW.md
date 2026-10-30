# TASK W210-review: independent read-only review of the C1 admission sizing remedy

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w210` at `66386d5b8`, cut from `4e9a29487`; the slice is `4e9a29487..66386d5b8` (7 commits, 10 files). Production: `cfba7b695` (`RocketTransactionAdmission` new, `RocketTransactionCodec`, `RocketTransactionSavedData`). Review worktree `D:/GitHub/arce-v180-w210-review`, a detached checkout of `66386d5b8` |
| Task it answers | [TASK-W210](TASK-W210-C1-ADMISSION-SIZING.md), Root decisions R181 and R183 (finding W208-R2) |
| Record | `docs/work/v1.8.0-root-queue/records/W210.md` on the branch (the design note at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W210/` |
| Hosted run | the run on `66386d5b8` (Root pushed the branch at 11:27); read its result if it has finished |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W210-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

W210 reports the near-bound C1 retain down from 318.235 ms to 0.518 ms and its allocation from 390 MB to 27 KB, with
sizing moved to entry writes and loads (a new 2,048-block resident's write 0.052 to 0.792 ms; reload 1.818 to 2.320 s
in one diagnostic run).

## What to check

1. **Exact equivalence.** `canAppend` admits and refuses exactly what the full re-encode did, for every resident and
   candidate: the cache is used only for the identical entry object; the metadata-only update when the snapshot object
   is unchanged is exact (is `RocketSnapshot` truly immutable, and can an equal but different snapshot or a mutated
   one reach this path?); the header, the list-child adjustment, the byte and node bounds and the depth match the
   writer's own encoding; an entry whose sizing fails falls back to the original behaviour.
2. **Every mutation path.** Load (and a load that fails and clears), write, replace with a new snapshot, phase and
   progress updates, and remove keep the cache consistent; no path leaves a stale size that could admit an
   over-bound root; the cache stays bounded and is owner-thread only.
3. **The cost moved, not hidden.** Where the sizing now runs (writes, loads), on which thread, and whether any of it
   lands on a player-facing tick in a way docs/17 would count; the reload increase; the small-root retain.
4. **Tests** (`RocketTransactionAdmissionTest`): they failed first, cover the cases TASK-W210 lists (fresh and
   reloaded roots, each mutation, under, at and over the byte bound, the count limit, a rejected candidate and a
   retry, a maximum-size candidate), and would catch a stale cache.
5. **The measurement.** Recompute the before and after figures from the retained evidence; same harness, entry
   points and JAR provenance; nothing subtracted.
6. **Nothing weakened:** formats, bounds, budgets, timeouts, refusal codes, acknowledgement and assertions.
7. **Risk text:** which sentences of R-040, R-035 or ADR-074 these changes make wrong, for Root.
8. **Merge conflicts** against the shared head (`origin/codex/v1.8.0-classic-content`) and W211 (`codex/v1.8.0-w211`,
   local only; read it from `D:/GitHub/arce-v180-w211` without changing it).

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: `RocketTransactionAdmissionTest` and the
`rocket.persistence` and `rocket.server` packages; the W210 Python tests; the summary scripts on the retained
evidence. Targeted unit runs need no heavy-JVM lock. Check 6 GiB free memory first. No GameTest, native case, runData,
full build or server. Delete the worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W210-R1, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
