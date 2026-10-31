# TASK W220-review: independent read-only review of the governed publication-failure evidence

Root: Claude. Date: 2026-10-31. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w220` at `abfbd9e8d`, cut from `eb6bd6b26`; the slice is `eb6bd6b26..abfbd9e8d` (4 commits). Review worktree `D:/GitHub/arce-v180-w220-review`, a detached checkout of `abfbd9e8d` |
| Task it answers | [TASK-W220](TASK-W220-GOVERNED-PUBLICATION-FAILURE.md), Root decision R207 (finding W211-R1) |
| Record | `docs/work/v1.8.0-root-queue/records/W220.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W220/` |
| Hosted run | the run on `abfbd9e8d` (Root pushed the branch at 09:05); read its result if it has finished |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W220-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

W220 reports two rows, a return-eligible and a held-phase publication veto on a real loader-funded governed flight,
each passing with two restarts (six starts, four restarts), and null entity creation conditionally excluded.

## What to check

1. **The veto mechanism** (`EscrowLoaderS6PublicationProbe`, the change to `EscrowLoaderS6FlightProbe`): it cancels
   Forge's entity join event only for the exact selected destination, only behind the hooks property, the exact case
   and the release gate, and only in the selected round; nothing else in the executor's decisions changes; no other
   entity or flight is affected.
2. **Each row against the product's code** (`EscrowGovernedDestination` lines 177 to 205,
   `EscrowGovernedReturnAction.phase`): the return-eligible row really reaches `SPAWN_FAILED` and the return, the
   held row really reaches HELD and its later retry; recompute both from the retained images: the fixed transfer and
   destination identity, the complete typed debit and authority, the source and its envelope, the outcome, and the
   recovery to a completed state after both restarts; UNKNOWN never read as zero.
3. **The null-creation exclusion:** is `EntityType.create` returning null truly unreachable in the supported product,
   as the record argues from the bytecode?
4. **Tests** failed first and would catch a wrong outcome; the oracle reuses W211's strict checks; the 30 mutations
   and six replays are what the record says.
5. **Nothing weakened**, test-side only apart from the release-gated probe; **merge conflicts** against the shared
   head and W221 (`codex/v1.8.0-w221`, local in `D:/GitHub/arce-v180-w221`; read it without changing it).

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: the new and changed test classes and the
`resourceescrow` package; the loader S6 and escrow Python tests; the oracle on the retained images. Targeted unit runs
need no heavy-JVM lock. Check 6 GiB free memory first. No GameTest, native case, runData, full build or server. Delete
the worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W220-R1, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
