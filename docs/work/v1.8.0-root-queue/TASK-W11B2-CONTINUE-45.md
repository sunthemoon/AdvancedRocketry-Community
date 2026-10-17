# TASK W11b2, continuation 45: the eight-flight benchmark (only)

Root: Claude. Date: 2026-10-17. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md),
[TASK-W11B2-CONTINUE-41](TASK-W11B2-CONTINUE-41.md) and [TASK-W11B2-CONTINUE-43](TASK-W11B2-CONTINUE-43.md) first.
You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-c41` (at continuation 43's record `a28004df` plus this task) |
| Branch | `codex/v1.8.0-w11b2-c41` (local commits only; Root pushes and merges) |
| Contract | `records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`), section 5.5's budget paragraph; `docs/17-V1PLUS-QUALITY-BUDGETS.md` sections 3 and 4; R16 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C43/ROOT-DECISION-R16.md` |
| Input | `records/W11B2-C41.md`, "Continuation 43" (R16's test-owned player gate, F6's observer and the hold-path bounds) |
| Record | Add a "Continuation 45" section to `records/W11B2-C41.md`. Keep the existing text |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C45/` |

This continuation does **only** the benchmark that continuations 39, 41, 42 and 43 each deferred. Root reviewed
continuation 43 and removed its two generated directories. The release-test player facility now lets the escrow
fixtures run with two test-owned connected players (R16 item 1). Continuation 44 runs in
`D:/GitHub/arce-v180-w11b2-rocket-integration` and is rerunning settlement cuts, so the machine is not quiet yet.

## What to do

1. **Build the benchmark probe and runner** (a new runner beside `scripts/run_v180_escrow_benchmark.py`, which measures
   saturated original-worker costs and is not this measurement). It drives, on a gated native dedicated server with
   the packaged jar from the committed source:
   - eight concurrent governed transfers (F1 to F8), with the docs/17 section 4 reference load: the two test-owned
     players from `ReleaseTestPlayers`, pumped every tick and shown to tick; 16 oxygen devices or sealed volumes; ten
     station records; 100 satellite tasks; the two active rockets; one 2048-block assembly or disassembly; a dimension
     transfer; periodic saves; and one restart;
   - competing ordinary C0 to C8 reconciliation of fixture fills, with at least one retirement whose start-to-C8
     latency you report, and one settlement O1 to O4;
   - the 1 MiB root encode and every pruning generation.
   Use existing fixture helpers where they exist; write the parts that do not. Commit the probe and runner with their
   unit tests before the measured runs.
2. **Measure on a quiet machine.** Before each run, list the running `java.exe` processes, wait until no other
   worktree's Gradle test or server JVM is running (the heavy-JVM lock alone does not stop other workers' unit suites),
   and record the list. Take the heavy-JVM lock for the whole run.
3. **Report** main-thread capture and encoding time, allocations, queue age, writer and hand-off latency, the
   ordinary retirement latency, and MSPT mean, P95 and P99 against docs/17's unchanged budgets (mean at most 25 ms, P95
   at most 50 ms, P99 at most 100 ms), with RSS, the environment and the exact commands. If a budget fails, subdivide
   the work or lower its cadence without weakening a limit or omitting a proof, say what you changed, and measure
   again; if it still fails, stop and report the numbers.

## Write scope and boundaries

The benchmark probe and runner, the release-test hooks they need, their tests, and the record. A production code
change is allowed only to meet a failed budget as step 3 says, under R13 item 3, and must be recorded with its reason.
No change to an acknowledgement order, a proof, a hold's effect, the replay exclusion, the log checker or its
expectations, and no relaxed budget, limit, timeout or assertion.

Questions sent through the task conversation do not reach Root; the record and the final answer do. If you run short
of time, stop at a committed boundary and say exactly where the next continuation starts.

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C45/`. Never remove a lock
you do not own. If the tool policy refuses to let you remove your own lock, world or server copy, write the exact path
in your record and final answer and continue; Root removes it.

## Not in this task

Everything except the benchmark: further wait-route changes, the settlement fixes (continuation 44), production
admission, W11b3, ADRs, the risk register, the R-035 text, status files and the ledger (Root).
