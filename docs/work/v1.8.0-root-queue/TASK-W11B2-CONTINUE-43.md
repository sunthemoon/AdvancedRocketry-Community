# TASK W11b2, continuation 43: Root decision R16, then the eight-flight benchmark

Root: Claude. Date: 2026-10-17. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md),
[TASK-W11B2-CONTINUE-41](TASK-W11B2-CONTINUE-41.md), [TASK-W11B2-CONTINUE-42](TASK-W11B2-CONTINUE-42.md) and the
documents they name first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-c41` (at continuation 42's record `87f20d43` plus this task) |
| Branch | `codex/v1.8.0-w11b2-c41` (local commits only; Root pushes and merges) |
| Contract | As TASK-W11B2-CONTINUE-41, plus **R16** in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C43/ROOT-DECISION-R16.md` (SHA-256 `f7014c92b651b56e81707152057b8ab6c72b1d8b8b55e9989eab428917a2dc26`) |
| Input | `records/W11B2-C41.md`, "Continuation 42" (the wait audit, the benchmark boundary and the remaining cost items) |
| Record | Add a "Continuation 43" section to `records/W11B2-C41.md`. Keep the existing text |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C43/` |

Root reviewed continuation 42 (`f8095b08`), removed its two generated directories and answered its two questions with
R16: reuse the existing release-test player facility (`ReleaseTestPlayers`, which v1.7.0's native reference load used)
for the benchmark's two players, with the fixture gate accepting only players that facility registered; F6's live
observer follows R13's standard during waits; and three hold-path cost rules.

Root has integrated the main W11b2 branch at `d497efe7` (continuations 36, 38, 38M and 40, and the shared branch with
W88's fix for the fairness test). This branch does not have W88's fix yet, so the fairness test may still fail here; if
it does, report its line and do not change it. Root merges this branch into the W11b2 branch after this continuation.

## What to do (commit each step with its tests)

1. R16 item 1: the test-owned player gate and its tests.
2. R16 item 2: F6's live observer under R13's standard, with its tests.
3. R16 item 3 (a) and (b), with their tests. Leave (c) unchanged unless the benchmark fails a budget.
4. The eight-flight benchmark exactly as TASK-W11B2-CONTINUE-41 step 2 says, with the two test players from step 1,
   on a quiet machine (list the running `java.exe` processes before each run, wait until no other worktree's Gradle
   test or server JVM is running, and record the list), including at least one ordinary C0 to C8 retirement with its
   start-to-C8 latency, periodic saves and a restart, and docs/17's unchanged budgets. If a budget fails, subdivide the
   work or lower its cadence without weakening a limit or omitting a proof, and say what you changed; if it still
   fails, stop and report the numbers.

## Write scope, boundaries and commands

As TASK-W11B2-CONTINUE-41, plus `EscrowTestEndpoints.requireEnabled`, `ReleaseTestPlayers` (moved or exposed only as
R16 item 1 allows) and the reconciliation files only where they call that gate. Evidence goes in
`D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C43/`. If the tool policy refuses to let you remove your own lock or
world, write the exact path in your record and final answer and stop waiting on it; Root removes it.

## Not in this task

As TASK-W11B2-CONTINUE-41.
