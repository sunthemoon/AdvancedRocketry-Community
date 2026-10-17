# TASK W11b2, continuation 32: unloaded-end tickets and readiness, then the return and pruning cuts

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md),
[TASK-W11B2-CONTINUE-31](TASK-W11B2-CONTINUE-31.md) and the documents they name first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (at `36322849` plus this task) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local only; Root merges) |
| Contract | `records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`), and Root decisions R1 to R11 as listed in TASK-W11B2-CONTINUE-30, plus R12 and R13 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C31B/` (`ROOT-DECISION-R12.md`, SHA-256 `9c30c3598ed0d0a60adbcd0d9ed1e4b173e773f38a958a14d2bfe9cd9f7051d1`; `ROOT-DECISION-R13.md`, SHA-256 `c2a39814fa5e2cb08dd0413fd5a36340d2667b2a011f37e02b66ab4bfedbc5e4`) |
| Input | `records/W11B2.md`, "Continuation 31" (live cancellation and failed-landing returns reach the CANCELLED F6 checkpoint) |
| Record | Add a "Continuation 32" section to `records/W11B2.md`. Keep the existing text |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C32/` |

Root reviewed continuation 31 and removed its GameTest world. Continuation 31B part 3 (Codex, worktree
`D:/GitHub/arce-v180-w11b2-f7f8`, branch `codex/v1.8.0-w11b2-f7f8`) is still wiring F7, the F8 cleanup executor and
R11 item 3; Root merges it into this branch when it finishes.

## What to do (commit each step with its tests)

1. **Unloaded-end tickets and readiness.** Earlier records say "recorded ends must already be loaded; unloaded-end
   readiness integration remains open". `RocketGovernedLaunchBoundary.allows` refuses until both recorded ends are
   loaded and entity-ticking, and the governed dispatcher adds flight tickets only after a non-HELD result. So a
   governed flight whose destination end is not already loaded never proceeds. Design 2 says (section 5.5, the line
   beginning "flight tickets after positive classification"): "Transfers load only server-recorded ends under
   existing bounded flight tickets after positive classification; ordinary reconciliation/pruning/held repair add
   none." Implement exactly that:
   - after an ACTIVE classification, place or refresh the existing bounded tickets for the server-recorded ends;
   - while an end is loading, wait as a hold that changes nothing;
   - proceed only once the existing loaded-ends boundary passes.

   Never load an end on the client's word. Never ticket from reconciliation, pruning or held repair. Add no new
   ticket type, and keep the existing ticket bounds. Cover it with GameTests in which the destination end (and,
   separately, the source end after a restart) starts unloaded and the flight continues once it loads, plus a case
   that never loads and stays held with no fence WARN.
2. **The return cuts.** Continuation 31's item 4: native forced-stop cuts on both sides of each return's store, force
   and readback, two restarts each, pinned to the committed source and its jar.
3. **The pruning cuts** that continuation 09 listed as missing: native held-B or unloaded-C pruning, and the O4
   replacement-before-callback cut, two restarts each.
4. If time remains, step 4's credited-world reconciliation order C0 to C8 (section 5.3). Check each row against the
   code, and fill and test what is missing. A missing credited source is REPAIR.

## Write scope and boundaries

- In scope: `RocketGovernedLaunchBoundary`, `RocketGovernedSourceDispatcher`, `RocketGovernedArrivalDispatcher`, the
  ticket and readiness helpers in `RocketTransferEntities`, the reconciliation classes, their tests and GameTests,
  native-cut fixtures, and this record. Under R13 item 3, a narrow additive edit to another file is allowed when the
  frozen design or R1 to R13 already require it; record each one with its reason.
- Out of scope, owned by 31B: `EscrowGovernedHandoff`, the F8 executor, the CLEANUP route in `RocketTransferFences`
  and `EscrowGovernedDispatch`, `EscrowGovernedTerminalDiagnosticsTest` and `EscrowGovernedSourceFlightGameTests`.
  31B part 3 may add a ticket release helper to `RocketTransferEntities`; keep your edits there additive. Root
  resolves the merge.
- Use a new GameTest batch only when a fixture needs isolation.

This session has room for more than one step: do not stop after the first committed step if time remains. Keep the
whole unit suite passing after every step and the unchanged log checker passing. A warning that remains is a finding
to report with its line. Stop only for a change to an acknowledgement order, a proof, a hold's effect or the replay
exclusion: commit what you have with your record, and report. Questions sent through the task conversation do not
reach Root; the record and the final answer do. If you run short of time, stop at a committed step boundary with every
test of that step passing, and say exactly where the next continuation starts. One GameTest run and one set of native
cuts per committed step is enough.

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C32/`. Continuation 31B
part 3 shares the heavy-JVM lock; wait for it as WORKER-RULES says, and never remove a lock you do not own.

## Not in this task

- F7, F8 and R11 item 3 (31B), N1's complete flights, the A0 cases, F6 to F11 and the benchmark (later
  continuations), production admission, W11b3, the bay slices, W27f reconstruction, signed per-fluid accounting.
- Part-1 findings F3, F8 and N2 (after the W11b2 merge; their designs are frozen as W86 and W85).
- ADRs, the risk register, status files and the ledger (Root).
