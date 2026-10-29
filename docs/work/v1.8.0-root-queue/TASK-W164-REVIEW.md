# TASK W164-review: independent read-only persistence review of N3-B3 (I6 integration)

Root: Claude. Date: 2026-10-29. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w164` at `a96d31d38`. Its last shared-head merge is `c917c0ae3` (shared `1a4b48a29`), so `git diff 1a4b48a29 a96d31d38` is the whole slice (73 files). Review worktree `D:/GitHub/arce-v180-w164-review`, a detached checkout of `a96d31d38` |
| Task it answers | [TASK-W164](TASK-W164-N3-B3-I6-INTEGRATION.md), all of its continuation sections (R118 to R164) |
| Design | [records/W148-DESIGN.md](records/W148-DESIGN.md) sections 2, 2.1, 3.1, 3.2, 5, 6 and the N3-B3 row of section 7; [records/W150.md](records/W150.md) and [records/W152.md](records/W152.md) for N3-B1 and N3-B2 |
| Record | `docs/work/v1.8.0-root-queue/records/W164.md` on the branch (newest sections first) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W164/` and the archive `D:/GitHub/ARCE-Task-Evidence/archive/W164/` |
| Hosted run | the run on `a96d31d38` (Root pushed the branch at 12:22); read its result if it has finished |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W164-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

This is a persistence slice: it changes how landed rockets retire, how I6 preparation proves the durable D1, how the
journal's write-back and removal work, and which chunks preparation tickets. Production admission stays as the design
states. Review the code, not the record's account of it.

## What to check

1. **The I6 path against W148.** Each step of N3-B3: the plain hook's D1 and exclusion proof, Design M proving before
   M1, the cold-origin preparation and its tickets (at most 16 columns per end, W90's set), the no-T disassembly route
   to B2, FM12a's pending-before-absence and outbox semantics. Name every place the code departs from W148.
2. **Durability and conservation.** Can any stop, crash or refused write lose a rocket, duplicate one, or lose or
   duplicate its contents or terrain? Check the journal write-back and removal cuts the natives exercise, and the cases
   they do not.
3. **The gated seams.** The release-test cut policies added under R156 are unreachable in production and have the
   journal's lifetime, not a global mutable collection.
4. **The fixture changes over the slice's life** (R131, R135, R141 and the shared-head merges): no assertion, timeout
   or bound was loosened; autosave guards are derived and commented.
5. **The native evidence.** B-PRE (20 cases) and tranche 2 (14 rows, 47 of 66 starts) ran on committed source, with
   oracles that require I6's durable D1. Spot-check the oracle against the record of one failed and one passed row.
6. **Class sizes and `RocketTransferService`** against AGENTS section 3.4.
7. **Risk text.** Say which risk entries (R-035, R-039, R-040, R-042, R-043 or a new one) these changes make wrong or
   incomplete, for Root's update before merge.
8. **Merge conflicts** against the shared head (`origin/codex/v1.8.0-classic-content`), including W185 (`codex/v1.8.0-w185`
   at `609eda0ce`) if it has not merged yet.

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: the new and changed unit classes, and the
`rocket.flight.persistence`, `rocket.server`, `rocket.sealed` and `resourceescrow` packages; the W164 Python tests.
Targeted unit runs need no heavy-JVM lock. Check 6 GiB free memory first. No GameTest, native case, runData, full build
or server. Delete the worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W164-R1, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
