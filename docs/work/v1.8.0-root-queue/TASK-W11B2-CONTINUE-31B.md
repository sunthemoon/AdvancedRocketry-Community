# TASK W11b2, continuation 31B: checked F7 handoff and F8 cleanup, with R11 item 3

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md),
[TASK-W11B2-CONTINUE-30](TASK-W11B2-CONTINUE-30.md) and the documents they name first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-f7f8` (at `37c50d54` plus this task) |
| Branch | `codex/v1.8.0-w11b2-f7f8` (local only; Root merges it into `codex/v1.8.0-w11b2-rocket-integration`) |
| Contract | `records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`), section 1's lifetime rows and section 5.4's F6, F7 and F8 rows, and Root decisions R1 to R11 as listed in TASK-W11B2-CONTINUE-30 |
| Input | `records/W11B2.md`, "Continuation 29" and "Continuation 30" (the return permit, R11 items 1, 2 and 4, and the F6 checkpoint prerequisites are done) |
| Record | A new file `records/W11B2-F7F8.md`. Do not edit `records/W11B2.md`; continuation 31 appends to it in parallel |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C31B/` |

To go faster, the rest of step 3 now runs as two parallel tasks. **Continuation 31**, a separate Codex session in
`D:/GitHub/arce-v180-w11b2-rocket-integration`, wires the live cancellation and failed-landing returns up to the
CANCELLED F6 checkpoint. **This task** implements what comes after F6. Root merges both, then a later continuation wires
the live CANCELLED path through your executors and continues with steps 4 to 7.

## What to do (commit each step with its tests)

1. **F6 proof lifetime and checked F7.** Keep fresh F6 native slot proofs and `EscrowGovernedTerminalPassengers` checks
   jointly valid at F7 capture and immediately before replacement. The checked ledger update records the terminal
   outcome and completes the serial; it updates a READY head's successor identity, typed frontier and invariant digest
   only if a head exists (destination epoch 0 for handoff, preserved source epoch for cancellation). Receipts stay
   immutable. The exact callback releases the successor's resource lock. After replacement, terminal authority permits
   cleanup only, never resume, debit or removal again.
2. **The checked F8 cleanup executor.** After the F7 acknowledgement: checked journal removal (or the exact next-F2
   replacement) proves the completed transfer absent and releases its dependent pad and tickets; then a checked ledger
   generation prunes only that exact terminal entry, with the journal lease rechecked at capture and replacement. Restart
   repeats cleanup and pruning only. UNKNOWN keeps the exact entry and its capacity.
3. **R11 item 3.** A CLEANUP decision routes the record to this executor, as ACTIVE routes to the phase executors, and
   the executor repeats every final check. An actionable cleanup logs no fence WARN. A cleanup that cannot proceed for a
   reason that needs an operator reports that once, with its own specific reason. Remove the interim
   `GOVERNED_CLEANUP_REQUIRED` fence only once the route is wired, and change the A0 diagnostic's expectations as R11
   item 4 says: a successful F7 routes to cleanup with no WARN, and a blocked cleanup logs one WARN with its specific
   reason. The log checker and its expectation file stay unchanged.
4. **Live landed flights.** Extend the two terminal-checkpoint fixtures in `EscrowGovernedSourceFlightGameTests`
   (separate and shared slots) from F6 through F7 and F8 to an idle journal, a pruned ARMED entry and a released
   successor, with the connected passenger. Unit-test the executors for both HANDED_OFF and CANCELLED outcomes; the live
   CANCELLED GameTests come after Root's merge.
5. If time remains, section 5.4's native cuts for F7 and F8 (journal replacement and callback, terminal-prune ledger
   replacement and callback), two restarts each, pinned to the committed source and its jar.

## Write scope and boundaries

- In scope: new F7 and F8 classes, the CLEANUP route in `RocketTransferFences` and `EscrowGovernedDispatch`,
  `EscrowGovernedTerminalDisk`, `EscrowGovernedTerminalPassengers`, `EscrowGovernedTerminalDiagnosticsTest`,
  `EscrowGovernedSourceFlightGameTests`, new unit tests, their native-cut fixtures, and `records/W11B2-F7F8.md`.
- Out of scope, owned by continuation 31: the rocket's cancellation and failed-landing paths,
  `EscrowGovernedReturnAction` and `EscrowGovernedReturnGameTests`. If you need a helper in `EscrowCoordinator`, add a
  small new method; do not change existing ones. Root resolves the merge.
- Use a new GameTest batch only when a fixture needs isolation, as continuation 30 did.

This session has room for more than one step: do not stop after the first committed step if time remains. Keep the
whole unit suite passing after every step and the unchanged log checker passing. A warning that remains is a finding
to report with its line. A change to an acknowledgement order, a proof, a hold's effect or the replay exclusion needs
Root first: stop, commit what you have with your record, and report. If you run short of time, stop at a committed step
boundary with every test of that step passing, and say exactly where the next continuation starts. One GameTest run per
committed step is enough.

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C31B/`. This is a fresh
worktree, so the first Gradle run compiles from scratch. Continuation 31 shares the heavy-JVM lock; wait for it as
WORKER-RULES says, and never remove a lock you do not own.

## Not in this task

- The live cancellation and failed-landing returns (continuation 31), steps 4 to 7 (later continuations),
  production admission, W11b3, the bay slices, W27f reconstruction, signed per-fluid accounting.
- Part-1 findings F3, F8 and N2 (after the W11b2 merge; their designs are frozen as W86 and W85).
- ADRs, the risk register, status files and the ledger (Root).
