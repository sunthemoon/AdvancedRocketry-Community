# TASK W11b2, continuation 41: governed retry continuity, successor waits and the eight-flight benchmark

Root: Claude. Date: 2026-10-17. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md),
[TASK-W11B2-CONTINUE-39](TASK-W11B2-CONTINUE-39.md) and the documents they name first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-c41` (new, created by Root at continuation 39's record `7b1139b8` plus this task) |
| Branch | `codex/v1.8.0-w11b2-c41` (local commits only; Root pushes it and merges it into `codex/v1.8.0-w11b2-rocket-integration`) |
| Contract | `records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`), section 5.4 (rows F1, F6, F7) and section 5.5's budget paragraph; Root decisions R1 to R15 as listed in TASK-W11B2-CONTINUE-37 |
| Input | `records/W11B2.md`, "Continuation 39", especially "Final committed-source check and remaining work", items 3 and 4 |
| Record | `records/W11B2-C41.md` (new file, so that it merges cleanly with continuation 40's edits to `records/W11B2.md`) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C41/` |

Continuation 39 delivered F10's observation caching (`0efca67a`) and F1's retry backoff with the
`ARCE_ESCROW_SUCCESSOR_HOLD` transition logging (`631e4430`). This task finishes the rest of its step 2 and its step 3.
Continuation 40 runs in parallel in `D:/GitHub/arce-v180-w11b2-rocket-integration` on the C0 to C8 review's fixes,
plus the GameTest and native C0 to C8 rerun that continuation 39 could not run; do not edit the reconciliation driver,
view, validation, pruning or change classes, `RocketEscrowAudit` or the reconciliation probe and runner.

## What to do (commit each step with its tests)

1. **F6 and F7 retry continuity.** Section 5.5: F1 and F6 proof failures, F1 and F7 write failures and
   successor-dispatch waits reuse the 100, 400 and 1,200-tick backoff; hard and manual conflicts stay sticky; F1 write
   outcomes keep the writer's UNKNOWN rules and exact candidate; the 200-tick ledger-unavailable retry gate stays.
   Continuation 39 found that a terminal `Work` replacement can discard failure history: fix that, and make the F6
   proof failures, the F7 write failures and the successor-dispatch waits follow the same progression without a new
   transfer or attempt resetting it. Log `ARCE_ESCROW_SUCCESSOR_HOLD` once per `(L, T, serial, phase, reason, backoff)`
   transition, never per position tick; a healthy flight logs no hold or fence warning. Add unit tests for each route
   and one GameTest or native cut that shows a failure history surviving a terminal `Work` replacement.
2. **The eight-flight benchmark**, as TASK-W11B2-CONTINUE-39 step 3 says: eight concurrent governed transfers under the
   docs/17 reference load, with periodic saves, a restart, and competing ordinary C0 to C8 reconciliation of fixture
   fills, including at least one ordinary retirement whose start-to-C8 latency you report (the C0 to C8 review's F6:
   any transfer journal write restarts a C3 to C7 in progress, so its latency may be unbounded under load). Record
   main-thread capture and encoding time, allocations, queue age, writer and hand-off latency, and MSPT mean, P95 and
   P99 against docs/17's unchanged budgets, including the 1 MiB root encode and every pruning generation. The existing
   `scripts/run_v180_escrow_benchmark.py` measures saturated original-worker costs and is not this measurement.
   Measure on a quiet machine: before each benchmark run, list the running `java.exe` processes and wait until no other
   worktree's Gradle test or server JVM is running (the heavy-JVM lock alone does not stop other workers' unit
   suites), and record the list with the results. If a budget fails, subdivide the work or lower its cadence without
   weakening a limit or omitting a proof, and say what you changed; if it still fails, stop and report the numbers.

## Write scope and boundaries

The governed dispatch, terminal, preparation and successor-retry classes and their writer callbacks, the hold logging,
the benchmark probe and runner, their tests and GameTests, and the new record file. Under R13 item 3, a narrow
additive edit to another file is allowed when the frozen design or R1 to R15 already require it; record each one with
its reason.

No change to an acknowledgement order, a proof, a hold's effect, the replay exclusion, the log checker or its
expectations, and no relaxed budget, limit, timeout or assertion. Stop only for a change to one of those: commit what
you have with your record, and report. Questions sent through the task conversation do not reach Root; the record and
the final answer do. If you run short of time, stop at a committed step boundary with every test of that step passing,
and say exactly where the next continuation starts. Keep the whole unit suite and the unchanged log checker passing;
one GameTest run per committed step is enough.

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C41/`. Other workers share
the heavy-JVM lock; wait for it as WORKER-RULES says, and never remove a lock you do not own. If the tool policy refuses
to let you remove your own lock or world, write the exact path in your record and final answer and stop waiting on it;
Root removes it. If a GameTest run crosses midnight, check every rolled log part.

## Not in this task

The C0 to C8 review's fixes and the C0 to C8 native rerun (continuation 40); F6 and F9 of design section 6
(continuations 38 and 38M); production admission; W27f reconstruction; other propulsion operations; W11b3; the final
independent review; ADRs, the risk register, the R-035 text, status files and the ledger (Root).
