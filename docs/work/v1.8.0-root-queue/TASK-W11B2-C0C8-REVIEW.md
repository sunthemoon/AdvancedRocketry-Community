# TASK W11b2-C0C8-review: independent read-only review of the ordinary reconciliation driver C0 to C8

Root: Claude. Date: 2026-10-17. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: you
change nothing in any repository worktree.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w11b2-rocket-integration`. Continuation 35: `5510ca32`, `88058206`, `2b6e3c4c`, `ccf83d91` (code) and `6b38b615` (record), on top of `7750c7f0`. Continuation 37: `7e8a6a6b`, `2208ba1c`, `ac25c708` (code) and `a564e845` (record), on top of `0aa39187` |
| Your checkout | `D:/GitHub/arce-v180-w11b2-c0c8-review`, a detached worktree of `19b9cfa5` (it contains both continuations). Read it and run tests there; do not commit, switch or reset it |
| Contract | `docs/work/v1.8.0-root-queue/records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`), sections 5.1, 5.3 and 5.5; Root decision R13 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C31B/ROOT-DECISION-R13.md`; Root decision R15 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C35/ROOT-DECISION-R15.md` |
| Records under review | `records/W11B2.md`, sections "Continuation 35" and "Continuation 37" |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C35/` and `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C37/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C0C8-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

Codex implemented these commits; this review must come from a different session and model (AGENTS.md section 13,
persistence and save-refusal slices). A new Codex session (continuation 39) is working on F10 and the eight-flight
benchmark in `D:/GitHub/arce-v180-w11b2-rocket-integration` now, so findings can still reach it. Root already knows one
issue and has assigned it to continuation 39: `RocketEscrowAudit.observe` (rocket tick, load and save) calls
`EscrowReconciliationPruning.observe`, which runs a full settlement observation every tick for a rocket whose markers
are all live. Do not report it again unless you find a correctness consequence beyond its cost.

## What to check

1. **R15 conformance.**
   - Item 1: C0, C3, C6 and restart classification validate by the canonical prefix classification, the binding's
     identity and typed frontier fields and the ledger, epoch and exact marker chain, and never compare
     `before_wrapper`, `after_wrapper`, `source_before`, `source_after`, `destination_before` or `destination_after`.
   - Item 2: C2 alone compares them, as R15 lists.
   - Item 3: (a) a retirement starts only when every later L1-acknowledged member on each touched endpoint has applied;
     (b) C0 never binds over a stale marker; (c) a C2 mismatch is OTHER_STATE, sticky REPAIR, never rebound.
2. **Design section 5.3, rows C0 to C8.** For each row, the action, its acknowledgement and its forced-stop result.
   `RocketEscrowAudit.attempt` reports `proved=true` only from C7's exact successful acknowledgement for that attempt
   and its members, never after apply, one-sided proof, loaded absence or reconstruction. UNKNOWN keeps the exact
   candidate, L1 and the frozen endpoints. Tokens and the journal lease are rechecked at capture and immediately
   before replacement.
3. **Permits and hooks.** `EscrowReconciliationAction.Permit`, `RocketEntity.applyEscrowFill`,
   `EscrowReconciliationPruning.Permit` and `RocketEntity.pruneReconciledEscrow`: one use, owner thread, bound to the
   exact capture, and no general mutation or idle exemption. Check each narrow edit outside the driver files against
   R13 item 3 (the records list them: `RocketRuntime`, `RocketManager`, `RocketFlightService`,
   `RocketTransferService`, `EscrowSettlementDisk`, `EscrowRuntime`, `EscrowCoordinator`).
4. **C8 and restart pruning.** `EscrowReconciliationPruning.retired` and `apply`: a marker is pruned only when its
   operation is absent from the checked root, below its issued watermark, not unresolved and not a settlement receipt,
   and only as a leading prefix; no former identity is inferred, and resources and epochs never change. Pruning can
   run inside `addAdditionalSaveData`: check that it cannot change an endpoint between a C3 freeze and its C4 to C6
   capture, or during any other proof (settlement O2, flight F1 or F6).
5. **Budgets and loading.** Eight scheduler visits per tick, at most one component capture and one endpoint action per
   tick, no chunk loading and no tickets, retained captures under 16 MiB.
6. **AGENTS.md section 3.3.** Every new refusal or hold of saved data has a stated scope, duration, log volume and
   recovery. Name any that R-035 or the ADR-065 notes do not cover yet; Root writes them.
7. **Tests.** Do the unit tests, the GameTests and the native runner
   (`scripts/run_v180_escrow_reconciliation.py`, `tests/test_escrow_reconciliation_runner.py`) prove what the records
   claim? Look for weakened or removed assertions and for conservation checks that a cut could pass without.
8. **Rerun** in your checkout: `gradlew.bat --offline test --tests '*EscrowReconciliation*' --console=plain` and
   `python -B -m unittest discover -s tests -p test_escrow_reconciliation_runner.py -v`. Set `JAVA_HOME` to
   `C:/Program Files/Java/jdk-17.0.7`. Do not run GameTests, native runs or the whole suite, and take no heavy-JVM
   lock.

## Output format

- A verdict: MERGE-READY-AS-PART, FIX-FIRST, or BLOCK.
- Findings, each with an ID, a severity (Critical, High, Medium, Low, Info), the file and line at `19b9cfa5`, the
  failure scenario and a suggested fix.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.

## Not in this task

Any edit, commit, push or merge in a repository worktree; any other agent; GameTests, native runs and benchmarks;
continuation 36's commands and continuation 38's fixtures; F10.
