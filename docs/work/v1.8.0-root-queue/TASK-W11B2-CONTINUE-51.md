# TASK W11b2, continuation 51: the eight-flight benchmark's measured runs on the final integrated source

Root: Claude. Date: 2026-10-17. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md)
and [TASK-W11B2-CONTINUE-45](TASK-W11B2-CONTINUE-45.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-c51` (new, at the integrated W11b2 head `884eb889` plus this task) |
| Branch | `codex/v1.8.0-w11b2-c51` (local commits only; Root pushes and merges) |
| Contract | `records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`), section 5.5's budget paragraph; `docs/17-V1PLUS-QUALITY-BUDGETS.md` sections 3 and 4; TASK-W11B2-CONTINUE-45 (the workload and the report) |
| Input | Continuation 45's harness and its blocked first run: `records/W11B2-C41.md`, "Continuation 45" (runner `scripts/run_v180_governed_benchmark.py`, probe `EscrowFlightBenchmarkProbe`, case `GOVERNED_BENCH`) and its evidence `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C45/` |
| Record | `records/W11B2-C51.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C51/` |

Continuation 45 built the benchmark and stopped at `Benchmark lost an F1/F2 callback`: the journal-wide lease revoked
the other seven coalesced F1 callbacks after the first F2 publication. Root decision R17 item 3 (continuation 47) and
continuation 49 replaced it with the per-rocket lease, and `809e5fdb` changed continuation 45's diagnostic
`EscrowFlightBenchmarkAdmissionTest` to the decided rule. The head `884eb889` also has continuations 44, 46, 48 and
50 and W89; its unit suite (3,773 tests) passed, and its hosted regression runs. No other worker is running now.

## What to do

1. **Run continuation 45's unchanged concurrent workload** on a gated native dedicated server with the packaged jar
   from this committed source, exactly as TASK-W11B2-CONTINUE-45 items 1 to 3 describe: eight concurrent governed
   flights F1 to F8 with the docs/17 section 4 reference load and the two test-owned players, competing ordinary C0 to
   C8 reconciliation with at least one retirement, one settlement O1 to O4, the 1 MiB encode ceiling, every pruning
   generation, periodic saves and one restart. Do not serialize the flights or replace the workload.
2. **Quiet machine.** Before each run list the running `java.exe` processes, wait until none belongs to another
   worktree, record the list, and hold the heavy-JVM lock for the whole run, as continuation 45's runner does.
3. **Report** main-thread capture and encoding time, allocations, queue age, writer and hand-off latency, the ordinary
   retirement latency (start to C8), the flight latency (F1 request to the checked idle landed survivor), the
   restart's verification, and MSPT mean, P95 and P99 against docs/17's unchanged budgets (mean at most 25 ms, P95 at
   most 50 ms, P99 at most 100 ms), with RSS, the environment and the exact commands. Say that this Windows host is not
   the Debian reference node. Include the cost of the arrival executor running twice per COMMITTED visit (flight fix
   review N6) if the recorder can attribute it.
4. If the benchmark fails a functional check (for example a lost callback, a hold, a watchdog stop), keep the evidence,
   diagnose it with the retained files or a unit test, commit what you have and report; do not change production code
   for it. If only a budget fails, report the numbers and the dominant cost; a production change is allowed only as
   TASK-W11B2-CONTINUE-45 item 3 says (subdivide the work or lower its cadence without weakening a limit or omitting a
   proof), recorded with its reason, then measure again.
5. If the harness itself needs a fix (it never completed a run), keep it observation-only and record each change.

## Write scope and boundaries

The benchmark probe, runner, recorder and their release-test hooks and tests, and the record; production code only
under item 4. No change to an acknowledgement order, a proof, a lease, a hold's effect, the replay exclusion, the log
checker or its expectations, and no relaxed budget, limit, timeout or assertion.

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C51/` and disposable server
sessions under `D:/GitHub/ARCE-Task-Evidence/Temp/W11b2-C51-<n>`. C: has about 12.9 GB free; check both drives before
each start and stop below 10 GB. Never remove a lock you do not own. If the tool policy refuses to let you remove your
own lock, world or server copy, write the exact path in your record and final answer and continue; Root removes it.

## Not in this task

Everything except the benchmark: production admission, W11b3, ADRs, the risk register, the R-035 text, status files and
the ledger (Root).
