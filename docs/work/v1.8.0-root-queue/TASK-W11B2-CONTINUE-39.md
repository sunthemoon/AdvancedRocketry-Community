# TASK W11b2, continuation 39: F10 (bounded escrow observation work) and the eight-flight benchmark

Root: Claude. Date: 2026-10-17. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md),
[TASK-W11B2-CONTINUE-37](TASK-W11B2-CONTINUE-37.md) and the documents they name first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (at Root's merge `19b9cfa5` of continuation 36, plus this task) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local commits only; Root pushes and merges) |
| Contract | `records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`): section 5.5's budget paragraph and section 6, row F10; Root decisions R1 to R15 as listed in TASK-W11B2-CONTINUE-37 |
| Input | `records/W11B2.md`, "Continuation 37" (C0 to C8 and 31 native cuts delivered); `records/W11B2-F7F8.md`, "Continuation 36" (status, inspect, retry) |
| Record | Add a "Continuation 39" section to `records/W11B2.md`. Keep the existing text |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C39/` |

Root reviewed continuation 37 and merged continuation 36 (`5874fa47`) into this branch. The merged tree passes 3,646 unit
tests. Continuation 38 runs in `D:/GitHub/arce-v180-w11b2-f7f8` on design section 6's F6 and F9 (settlement fixtures).
In this task, F10 is design section 6's finding, numbered after the settlement review; it is not a flight-order step.

## What to do (commit each step with its tests)

1. **F10.** Do what section 6, row F10, and section 5.5's budget paragraph say: cache generation-bound immutable decoded
   intents and bindings, invalidated on relevant state or store changes; use independent bounded cursors; skip writer,
   barrier and journal waits before expensive observations; revalidate immediately at capture and replacement. No
   per-tick full decode or rehash of 64 intents, and no same-tick whole-component rehash loop. Cached bindings never
   bypass the final checks. Root's review of continuation 37 found one case that belongs here: `RocketEscrowAudit.observe`
   (called from `RocketEntity.tick`, entity load and `addAdditionalSaveData`) now calls
   `EscrowReconciliationPruning.observe`, which runs a full `EscrowSettlementObserver.observe` every tick for each
   rocket whose wrapper has markers while its operations are still live, only to find nothing to prune. Gate it so the
   observation runs only when something it depends on changed (for example the acknowledged root generation, the
   wrapper's marker list or the rocket's load state), and keep the restart cleanup it performs. Add A0 tests that count
   the observations and show the cache invalidates on each relevant change.
2. **Coalescing and backoff checks.** Confirm, with tests, the section 5.5 rules that apply to the current code: one
   immutable generation per writer start with exact per-request acknowledgements, one in-flight snapshot and one
   successor, starts at most once per 10 ticks; 100, 400 and 1,200-tick backoff for F1 and F6 proof failures, F1 and F7
   write failures and successor-dispatch waits, with hard and manual conflicts staying sticky; the 200-tick
   ledger-unavailable retry gate; `ARCE_ESCROW_SUCCESSOR_HOLD` logged once per `(L, T, serial, phase, reason, backoff)`
   transition. Fix only what does not follow them, one commit per fix.
3. **The eight-flight benchmark.** Before admission, measure eight concurrent governed transfers under the docs/17
   reference load, with periodic saves and other competing escrow work (ordinary C0 to C8 reconciliation of fixture
   fills). Record main-thread capture and encoding time, allocations, queue age, writer and hand-off latency, and MSPT
   (mean, P95, P99) against docs/17's unchanged budgets, including the 1 MiB root encode and all pruning generations.
   The existing `scripts/run_v180_escrow_benchmark.py` measures saturated original-worker costs and is explicitly not
   this measurement; extend it or add a runner beside it. If a budget fails, subdivide work or lower cadence without
   weakening limits or omitting proofs, and say what you changed; if it still fails, stop and report the numbers.

## Write scope and boundaries

The escrow scheduler, cursors and caches (`EscrowReconciliationScheduler`, `EscrowReconciliationDriver`,
`EscrowReconciliationPruning`, `RocketEscrowAudit`, `EscrowSettlementRecovery`'s cursor and the coordinator's writer
coalescing), the governed dispatcher's backoff and hold logging, the benchmark probe and runner, their tests and
GameTests, and this record. Under R13 item 3, a narrow additive edit to another file is allowed when the frozen design
or R1 to R15 already require it; record each one with its reason. Continuation 38's settlement fixtures and runners
stay out of scope.

No change to an acknowledgement order, a proof, a hold's effect, the replay exclusion, the log checker or its
expectations, and no relaxed budget, limit, timeout or assertion.

This session has room for more than one step: do not stop after the first committed step if time remains. Keep the
whole unit suite passing after every step and the unchanged log checker passing. A warning that remains is a finding to
report with its line. Stop only for a change to an acknowledgement order, a proof, a hold's effect or the replay
exclusion: commit what you have with your record, and report. Questions sent through the task conversation do not reach
Root; the record and the final answer do. If you run short of time, stop at a committed step boundary with every test
of that step passing, and say exactly where the next continuation starts. One GameTest run per committed step is
enough; rerun the C0 to C8 native cuts only if step 1 or 2 changes their code path.

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C39/`. Continuation 38
shares the heavy-JVM lock; wait for it as WORKER-RULES says, and never remove a lock you do not own. The benchmark is a
heavy JVM and needs the lock for its whole run. If a GameTest run crosses midnight, check every rolled log part.

## Not in this task

F6 and F9 (continuation 38); production admission; credited-source reconstruction (W27f); other propulsion operations;
W11b3; the bay slices; signed per-fluid accounting; the part-1 review's findings F3 (W86), F8 (W85) and N2; the final
independent review; ADRs, the risk register, the R-035 text, status files and the ledger (Root).
