# TASK W11b2, continuation 42: the remaining successor wait routes and the eight-flight benchmark

Root: Claude. Date: 2026-10-17. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md),
[TASK-W11B2-CONTINUE-39](TASK-W11B2-CONTINUE-39.md), [TASK-W11B2-CONTINUE-41](TASK-W11B2-CONTINUE-41.md) and the
documents they name first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-c41` (at continuation 41's record `68cb8883` plus this task) |
| Branch | `codex/v1.8.0-w11b2-c41` (local commits only; Root pushes and merges) |
| Contract | As TASK-W11B2-CONTINUE-41 |
| Input | `records/W11B2-C41.md` (continuation 41: F6 and F7 retry continuity and failed-classification scheduling delivered; "Remaining work, risks and continuation") |
| Record | Add a "Continuation 42" section to `records/W11B2-C41.md`. Keep the existing text |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C42/` |

Root reviewed continuation 41 (`0173dd0b`) and removed its two generated GameTest directories. Its only GameTest
failure, `moreThan32ProcessorsShareOneFairBudgetAcrossTwoLevels`, belongs to W88, which is fixing it on another branch;
if it fails again here, report its line and do not change it. Continuation 40 runs in parallel in
`D:/GitHub/arce-v180-w11b2-rocket-integration` on the C0 to C8 review's fixes; do not edit the reconciliation driver,
view, validation, pruning or change classes, `RocketEscrowAudit` or the reconciliation probe and runner.

## What to do (commit each step with its tests)

1. **The remaining successor wait routes.** Continuation 41 covered failed public classification. Audit every other
   wait on the governed flight path against design section 5.5 (successor-dispatch waits reuse the 100, 400 and
   1,200-tick backoff; hard and manual conflicts stay sticky; `ARCE_ESCROW_SUCCESSOR_HOLD` once per transition; a
   healthy flight logs no hold or fence warning; ordinary checked-writer waits stay silent under R10, R11 and R14): at
   least loaded-end readiness and unresolved-passenger polling. List each route with its file and line and say whether
   it already follows the rule, and fix those that do not, with unit tests. A wait that is only polling a cheap
   in-memory condition need not back off, but it must not log per tick or rebuild expensive state per tick.
2. **The eight-flight benchmark**, exactly as TASK-W11B2-CONTINUE-41 step 2 says, including the quiet-machine rule (list
   the running `java.exe` processes before each run, wait until no other worktree's Gradle test or server JVM is
   running, and record the list), at least one ordinary C0 to C8 retirement with its start-to-C8 latency, and docs/17's
   unchanged budgets. If a budget fails, subdivide the work or lower its cadence without weakening a limit or omitting
   a proof, and say what you changed; if it still fails, stop and report the numbers.

## Write scope, boundaries and commands

As TASK-W11B2-CONTINUE-41, with the record in `records/W11B2-C41.md` and evidence in
`D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C42/`. If the tool policy refuses to let you remove your own lock or
world, write the exact path in your record and final answer and stop waiting on it; Root removes it.

## Not in this task

As TASK-W11B2-CONTINUE-41.
