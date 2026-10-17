# TASK W11b2, continuation 31: live cancellation and failed-landing returns to the CANCELLED F6 checkpoint

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md),
[TASK-W11B2-CONTINUE-30](TASK-W11B2-CONTINUE-30.md) and the documents they name first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (at `37c50d54` plus this task) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local only; Root merges) |
| Contract | `records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`), sections 1 and 5.4, and Root decisions R1 to R11 as listed in TASK-W11B2-CONTINUE-30 |
| Input | `records/W11B2.md`, "Continuation 29" and "Continuation 30" (the return permit, R11 and the F6 checkpoint prerequisites are done) |
| Record | Add a "Continuation 31" section to `records/W11B2.md`. Keep the existing text |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C31/` |

Root reviewed continuation 30 and removed its GameTest world. To go faster, the rest of step 3 now runs as two
parallel tasks. **This task** wires the live returns. **Continuation 31B**, a separate Codex session in the worktree
`D:/GitHub/arce-v180-w11b2-f7f8` (branch `codex/v1.8.0-w11b2-f7f8`, also from `37c50d54`), implements the checked F7
handoff and F8 cleanup executor with R11 item 3. Root merges both and then wires the CANCELLED path through F7 and F8.

## What to do (commit each step with its tests)

1. **Live cancellation.** Route `cancelCountdown` for a governed record-3 flight through the existing
   `EscrowGovernedReturnAction` permit, with the boundary table of design section 1: no fabricated cancellation, no
   journal cleanup, no ordinary idle authority, fuel, debit history and passenger assignments unchanged.
2. **Failed landing.** Route a governed failed landing the same way, back to the exact unchanged source.
3. **CANCELLED F6 checkpoint.** Each live return ends at the CANCELLED F6 checkpoint: own-store, force and stable
   readback of the returned source, joint native slot proofs, and `EscrowGovernedTerminalPassengers` checks, with the
   journal and ARMED hold retained. GameTests drive real cancellations and failed landings, with a real connected
   passenger, for separate and shared slots.
4. If time remains, the native forced-stop cuts on both sides of each return's store, force and readback, two restarts
   each, pinned to the committed source and its jar.

## Write scope and boundaries

- In scope: the rocket's cancellation and failed-landing paths, `EscrowGovernedReturnAction`, a return controller if
  you need one, `EscrowGovernedReturnGameTests`, new return unit tests and GameTests, their native-cut fixtures, and
  this record.
- Out of scope, owned by 31B: F7 and F8 classes, the CLEANUP route in `RocketTransferFences` and
  `EscrowGovernedDispatch`, `EscrowGovernedTerminalDiagnosticsTest`, and `EscrowGovernedSourceFlightGameTests`.
  If you need a helper in `EscrowCoordinator` or the dispatcher, add a small new method; do not change existing ones.
  Root resolves the merge.
- Use a new GameTest batch only when a fixture needs isolation, as continuation 30 did.

This session has room for more than one step: do not stop after the first committed step if time remains. Keep the
whole unit suite passing after every step and the unchanged log checker passing. A warning that remains is a finding
to report with its line. A change to an acknowledgement order, a proof, a hold's effect or the replay exclusion needs
Root first: stop, commit what you have with your record, and report. If you run short of time, stop at a committed step
boundary with every test of that step passing, and say exactly where the next continuation starts. One GameTest run per
committed step is enough.

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C31/`. Continuation 31B
shares the heavy-JVM lock; wait for it as WORKER-RULES says, and never remove a lock you do not own.

## Not in this task

- F7, F8, steps 4 to 7 (later continuations), production admission, W11b3, the bay slices, W27f reconstruction,
  signed per-fluid accounting.
- Part-1 findings F3, F8 and N2 (after the W11b2 merge; their designs are frozen as W86 and W85).
- ADRs, the risk register, status files and the ledger (Root).
