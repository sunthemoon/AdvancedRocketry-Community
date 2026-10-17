# TASK W11b2, continuation 44: final review part A's findings F2 to F5 and I1, I2, I4

Root: Claude. Date: 2026-10-17. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md)
and [TASK-W11B2-CONTINUE-36](TASK-W11B2-CONTINUE-36.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (at Root's merge `3b35a8af` plus this task) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local commits only; Root pushes and merges) |
| Input | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-final-review-A/REVIEW.md` (SHA-256 `4d9d827380980d7ef69f6e33a0302d039404c356043bbde4b5c50654157d72a2`), read in full |
| Contract | `records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`), sections 1 to 4 and 6; Root decisions R1 to R9 |
| Record | Add a "Continuation 44" section to `records/W11B2-F7F8.md`. Keep the existing text |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C44/` |

The review's verdict is MERGE-WITH-FOLLOWUP. Root takes F1 (the R-035 capacity row) and F6 (recorded as an accepted
deviation: the receipt does not name its predecessor head, which has no safety effect because L stays locked from O1
to O4) and I3 and I5 (no change). Fix the rest, one commit per item, each with its tests:

1. **F2.** For a settled operation, key `retry`'s 200-tick cooldown by the settlement intent (its digest), not by the
   member operation, so one intent is accelerated at most once per 200 ticks however many members it has. Add the
   review's test: a two-member chain intent where the second member's request reports COOLDOWN.
2. **F3.** Make `EscrowOperatorGameTests` prove the provenance gate on the dispatch route: use a gated ledger with a
   real REPAIR operation, so an accepted actor would return 1 and cache a preview; for each rejected source and both
   function forms, assert that the preview cache and the ledger stay unchanged; for unsuppressed rejected sources,
   assert the refusal text. Keep every existing assertion.
3. **F4.** Make `EscrowSettlementCompletionProbe`'s released branch wait for the product pruning cursor, as the held-B
   branch already does, instead of calling `pruneSettledEscrow` itself. Then rerun, with
   `scripts/run_v180_rocket_settlement_completion.py`, every cut whose restart reaches the released state, two restarts
   each, on the committed source and its jar. If a cut then fails because the cursor never prunes, that is a product
   finding: record it with its evidence and do not change the oracle.
4. **F5.** A settlement survivor whose exact-group pruning keeps being refused must be visible: log one bounded WARN
   per (group, endpoint, reason) transition, never per visit, and show the pruning state (waiting for an earlier group,
   refused with a reason, or done) for released members in `/arce escrow inspect`. The hold's effect does not change.
   If any existing GameTest then emits the new WARN, report the test and the line; do not edit the log checker or its
   expectations (Root decides).
5. **I1.** Make the R9 walk's "retired marker pruning" step in `EscrowReservationWalkTest` compare the root before and
   after a real pruning generation, instead of a root with itself. Do not remove the step.
6. **I2.** In `scripts/run_v180_escrow_reconciliation.py`, check markers against amounts for unretired cuts as well as
   epochs, with a Python test. Rerun the C0 to C8 cuts once only if this check changes a result.
7. **I4.** In `EscrowSettlementRecovery` (O2) and `EscrowReconciliationDriver` (C4), evaluate `runtime.store(...)`
   before setting the busy flag, so a store refusal cannot leave the flag set. Add a unit test for each.

## Write scope and boundaries

The settlement, operator, pruning and recovery classes these items name, `EscrowReconciliationDriver` for I4 only,
the settlement and reconciliation probes and runners, their tests and GameTests, and the record. Continuation 43 runs
in `D:/GitHub/arce-v180-w11b2-c41` on the governed flight path and `EscrowTestEndpoints`; do not edit the governed
dispatch, terminal, preparation, destination, return or phases classes, or `EscrowTestEndpoints`.

No change to an acknowledgement order, a proof, a hold's effect, the replay exclusion, a schema, the log checker or its
expectations, and no relaxed budget, limit, timeout or assertion. Stop only for such a change: commit what you have
with your record, and report. Keep the whole unit suite passing after every item and run one full GameTest with the
unchanged log checker at the end.

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C44/`. Continuation 43
shares the heavy-JVM lock and needs a quiet machine for its benchmark: when you have finished your heavy runs, say so
in your record. Never remove a lock you do not own. If the tool policy refuses to let you remove your own lock or
world, write the exact path in your record and final answer and continue; Root removes it. If a GameTest run crosses
midnight, check every rolled log part.

## Not in this task

Review F1 and F6 and I3 and I5 (Root); the governed flight path (final review part B and continuation 43); production
admission; ADRs, the risk register, the R-035 text, status files and the ledger (Root).
