# TASK W11b2, continuation 34: the shutdown diagnostic (R14), then the ordinary reconciliation driver C0 to C8

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md),
[TASK-W11B2-CONTINUE-32](TASK-W11B2-CONTINUE-32.md), [TASK-W11B2-CONTINUE-10](TASK-W11B2-CONTINUE-10.md) and the documents
they name first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (at Root's merge of the F7/F8 branch plus this task) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local only; Root merges) |
| Contract | `records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`), Root decisions R1 to R11 as listed in TASK-W11B2-CONTINUE-30, R12 and R13 as listed in TASK-W11B2-CONTINUE-32, and **R14** in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C34/ROOT-DECISION-R14.md` (SHA-256 `bd9882404307b92365541a2dffaf92999b6020d2091eea39f4bfede6325d9db9`) |
| Input | `records/W11B2.md`, "Continuation 32" (its open shutdown diagnostic and its C0 to C8 table), and `records/W11B2-F7F8.md`, "Part 3" (F7, F8 and R11 item 3, now merged into this branch) |
| Record | Add a "Continuation 34" section to `records/W11B2.md`. Keep the existing text |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C34/` |

Root reviewed continuation 32, removed its Temp copies and GameTest world, and merged the F7/F8 branch (31B part 3: live
F6 and F7 for HANDED_OFF, the F8 executor, R11 item 3) into this branch. Continuation 33 (Codex, worktree
`D:/GitHub/arce-v180-w11b2-f7f8`) runs in parallel: it hands the CANCELLED return checkpoint to F7 and F8, adds N1's
complete flights in `RocketSettlementIdleGameTests`, and checks F0 to F8.

## What to do (commit each step with its tests)

1. **The shutdown diagnostic, exactly as R14 says.** A lifecycle-closed governed dispatch returns
   `GOVERNED_RUNTIME_STOPPED`, which `classifyGoverned` holds silently. The hold's effect is unchanged. Check the F7, F8
   and return paths for the same false WARN. Add R14's unit test, then rerun one return cut and one F7 or F8 terminal
   cut natively, with two restarts each, and show no `ARCE_TRANSFER_FENCED` after "Stopping server".
2. **The ordinary reconciliation driver, design section 5.3, rows C0 to C8.** Continuation 32's table shows that the
   primitives exist but `RocketEscrowAudit.attempt` still ends in `RECONCILIATION_UNIMPLEMENTED`. Connect them in the
   frozen order, with section 5.1's canonical signatures and native binding. As section 5.1 says, the first driver
   supports only FLUID_PROPULSION_FILL, with real native rockets and the explicitly gated registered fixture source.
   - `attempt` reports `proved=true` only from C7's exact successful retirement acknowledgement for that attempt and its
     requested members, never after apply, one-sided proof, loaded absence or reconstruction (the paragraph under the
     section 5.3 table).
   - A missing credited source is REPAIR (MISSING_ENDPOINT, component locked), never reconstruction (section 5.2).
   - Unloaded ends wait without tickets. Ordinary reconciliation adds no ticket (section 5.5).
   - Commit in row groups, each with its unit tests and GameTests: C0 to C2 (validate and bind, L1, apply), then C3 to
     C6 (freeze, own store, force, readback), then C7 and C8 (L2, release and prune).
3. **The C0 to C8 forced-stop cuts.** Section 5.3: each acknowledgement is cut before and after replacement, after
   replacement before its callback, and after the callback where it releases a lock, with two restarts each, pinned to
   the committed source and its jar. Reuse the existing native runner and its marker form. List the cuts that are left.

## Write scope and boundaries

- In scope: `EscrowGovernedDispatch` and `RocketTransferFences` for R14 only; `RocketEscrowAudit`,
  `EscrowReconciliationScheduler`, the reconciliation and settlement-pruning classes that the driver needs, a narrow
  driver entry point in `EscrowCoordinator`, their tests and GameTests, native-cut fixtures and runners, and this record.
  Under R13 item 3, a narrow additive edit to another file is allowed when the frozen design or R1 to R14 already
  require it; record each one with its reason.
- Out of scope, owned by continuation 33: `EscrowGovernedReturn`, `EscrowGovernedTerminal`, `EscrowGovernedHandoff`,
  `EscrowGovernedCleanup`, `RocketGovernedCleanupDispatcher`, `EscrowTerminalSuccessor`, `EscrowTerminalProbe`,
  `EscrowTerminalLeaseCut`, `RocketSettlementIdleGameTests`, `EscrowGovernedLiveReturnGameTests`,
  `EscrowGovernedSourceFlightGameTests` and `run_v180_escrow_terminal.py`. If R14's item 4 needs a change in one of
  them, make it the smallest possible additive edit and record it; Root resolves the merge.
- Use a new GameTest batch only when a fixture needs isolation.

This session has room for more than one step: do not stop after the first committed step if time remains. Keep the
whole unit suite passing after every step and the unchanged log checker passing. A warning that remains is a finding
to report with its line. Stop only for a change to an acknowledgement order, a proof, a hold's effect or the replay
exclusion: commit what you have with your record, and report. Questions sent through the task conversation do not
reach Root; the record and the final answer do. If you run short of time, stop at a committed step boundary with every
test of that step passing, and say exactly where the next continuation starts. One GameTest run and one set of native
cuts per committed step is enough.

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C34/`. Continuation 33
shares the heavy-JVM lock; wait for it as WORKER-RULES says, and never remove a lock you do not own.

## Not in this task

- The CANCELLED path through F7 and F8, N1's complete flights and the F0 to F8 check (continuation 33).
- Credited-source reconstruction (W27f), propulsion operations other than FLUID_PROPULSION_FILL, production admission.
- F6 to F11 (console `inspect` and `retry`, status, fairness and backoff) and the eight-flight benchmark (later
  continuations).
- W11b3, the bay slices, signed per-fluid accounting.
- Part-1 findings F3, F8 and N2 (after the W11b2 merge; their designs are frozen as W86 and W85).
- ADRs, the risk register, status files and the ledger (Root).
