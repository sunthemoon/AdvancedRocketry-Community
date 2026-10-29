# TASK W196-review: independent read-only review of W27d4 (released flight)

Root: Claude. Date: 2026-10-29. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w196` at `c61480208`, cut from `c0b0daab8`; the slice is `c0b0daab8..c61480208` (12 commits). Review worktree `D:/GitHub/arce-v180-w196-review`, a detached checkout of `c61480208` |
| Task it answers | [TASK-W196](TASK-W196-W27D4-RELEASED-FLIGHT.md) |
| Design | `docs/work/v1.8.0-root-queue/records/W196.md` on the branch (the one-page design at its top); [records/W127-PLAN.md](records/W127-PLAN.md), the W27d4 sub-leaf; [records/W27A-DESIGN.md](records/W27A-DESIGN.md) sections 4.1, 5, 6, 8 and 8.1 and B10 |
| Risk | R-042 and R-043 in [the risk register](../../11-RISK-REGISTER.md) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W196/` |
| Hosted run | the run on `c61480208` (Root pushed the branch at 14:45); read its result if it has finished |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W196-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

This is a persistence slice: a coherent REPAIR or REGISTRY_BLOCKED bay group flies on with its packages under
`released = 1`. Admission stays off outside the GameTest server and the full release-test gate, but the restart
dispatch of released receipts runs on every server. Review the code, not the record.

## What to check

1. **Release eligibility.** Only a coherent REPAIR or REGISTRY_BLOCKED group is released; `released = 1` is
   acknowledged exactly before the first delegated step; a forced flag replacement cannot release a group that is not
   coherent.
2. **The permits.** Each delegated step (`tickLive`, destination publication, passenger moves, source removal,
   cleanup) checks the unresolved package signatures, consumed markers, ends, snapshot IDs, wrapper and journal
   digests against the complete pending group. Can a phase race, a changed endpoint or a stale permit let a step act on
   the wrong state?
3. **Consumption order.** REGISTERED consumption finishes before any package reaches the destination. Can a package be
   delivered twice, lost, or reach the destination while still registered?
4. **Restart.** Released receipts dispatch at the next start; a released receipt with T absent holds for offline repair;
   the destination identity kept across released phases (`368058cd2`) cannot be confused with another flight's.
5. **R-042's NOT_LOADED lock.** What a released receipt on a landed rocket does while the receipt index is not loaded.
6. **Nothing weakened:** formats (beyond the existing `released` field), bounds, budgets, quotas, timeouts and
   `RocketTransferService`. Check the new manifest entries.
7. **R-043.** List each sentence that W27d4 makes wrong or missing, for Root's update before merge.
8. **Merge conflicts** against the shared head (`origin/codex/v1.8.0-classic-content`) and W164 (`codex/v1.8.0-w164`
   at `73c1a703b`) if it has not merged yet.

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: the new and changed unit classes, and the
`rocket.bay`, `rocket.server` and `resourceescrow` packages; the native harness's Python tests. Targeted unit runs need
no heavy-JVM lock. Check 6 GiB free memory first. No GameTest, native case, runData, full build or server. Delete the
worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W196-R1, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
