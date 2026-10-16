# TASK W11b2, continuation 33: the live CANCELLED path through F7 and F8, then N1's complete flights

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md),
[TASK-W11B2-CONTINUE-31B3](TASK-W11B2-CONTINUE-31B3.md), [TASK-W11B2-CONTINUE-10](TASK-W11B2-CONTINUE-10.md) and the
documents they name first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-f7f8` (at Root's merge of the W11b2 branch plus this task) |
| Branch | `codex/v1.8.0-w11b2-f7f8` (local only; Root merges it into `codex/v1.8.0-w11b2-rocket-integration`) |
| Contract | `records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`), Root decisions R1 to R11 as listed in TASK-W11B2-CONTINUE-30, and R12 and R13 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C31B/` (`ROOT-DECISION-R12.md`, SHA-256 `9c30c3598ed0d0a60adbcd0d9ed1e4b173e773f38a958a14d2bfe9cd9f7051d1`; `ROOT-DECISION-R13.md`, SHA-256 `c2a39814fa5e2cb08dd0413fd5a36340d2667b2a011f37e02b66ab4bfedbc5e4`) |
| Input | `records/W11B2-F7F8.md`, "Part 3" (live F6/F7 for HANDED_OFF, the F8 executor, R11 item 3), and `records/W11B2.md`, "Continuation 31" (live cancellation and failed-landing returns reach the CANCELLED F6 checkpoint) |
| Record | Add a "Continuation 33" section to `records/W11B2-F7F8.md`. Keep the existing text. Do not edit `records/W11B2.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C33/` |

Root reviewed 31B part 3 and merged the W11b2 branch (continuation 31 and continuation 32's committed steps) into this
branch. Today a HANDED_OFF landing goes through F6, F7 and F8 live, but a cancellation or failed-landing return stops at
`EscrowGovernedReturn`'s CANCELLED F6 checkpoint: nothing hands it to F7, so the source rocket stays locked and the
terminal entry is never written. Continuation 32 (Codex, worktree `D:/GitHub/arce-v180-w11b2-rocket-integration`) is
still running in parallel on unloaded-end tickets, return and pruning cuts, and C0 to C8.

## What to do (commit each step with its tests)

1. **The live CANCELLED path.** When a return reaches its CANCELLED F6 checkpoint, hand it to the existing F7 writer
   with outcome CANCELLED and the source survivor, then let the existing F8 executor clean up. Rules:
   - Exactly one F6 proof set per transfer. Either the return's retained proofs feed `EscrowGovernedHandoff.Proof`
     directly, or the return releases them before `EscrowGovernedTerminal` captures its own. Two proof sets for the
     same slot must never coexist. Record which you chose and why.
   - The F7 acknowledgement order, the proofs, the R13 lease and the successor-lock release are the ones HANDED_OFF
     already uses; only the outcome and the survivor (source, at the arm's epoch) differ. CANCELLED keeps the
     unchanged-fuel and epoch rules that `EscrowTerminalSuccessor.matches` already encodes for the source.
   - GameTests: a live cancellation and a live failed landing (blocked pad) each complete through F7 CANCELLED and F8,
     with the passenger, one-time debit rules, the F7-only resource release, an idle checked journal and exact terminal
     pruning, as part 3's landed fixtures check. A dismount between the CANCELLED checkpoint and replacement refuses
     the write (R13).
2. **The required N1 test** (TASK-W11B2-CONTINUE-10, step 5). Strengthen `RocketSettlementIdleGameTests` exactly as
   design section 1's "Required test" says: two complete flights after settlement, and two after an ordinary L2
   release, with passengers, successor refuelling, handoff and cleanup. No new log expectation for
   FLOOR_PROOF_REQUIRED, FLOOR_CONFLICT or ENDPOINT_JOIN_PROOF_REQUIRED. This closes N1 and F3's complete-flight
   requirement.
3. **The flight order F0 to F8** (TASK-W11B2-CONTINUE-10, step 4, design section 5.4). Check each row against the
   code, with section 5.1's canonical signatures. Fill and test what is missing; list each row with the class and test
   that covers it. C0 to C8 (section 5.3) belong to continuation 32 and are not in this task.
4. If time remains, the native CANCELLED F7 and F8 cuts (before, replaced and acknowledged for the CANCELLED handoff),
   two restarts each, pinned to the committed source and its jar, reusing `run_v180_escrow_terminal.py`.

## Write scope and boundaries

- In scope: `EscrowGovernedReturn`, `EscrowGovernedTerminal`, `EscrowGovernedHandoff`, `EscrowGovernedCleanup`,
  `RocketGovernedCleanupDispatcher`, `EscrowTerminalSuccessor`, `EscrowTerminalProbe`, `EscrowTerminalLeaseCut`,
  `RocketSettlementIdleGameTests`, `EscrowGovernedLiveReturnGameTests`, `EscrowGovernedSourceFlightGameTests`, their
  unit tests, `run_v180_escrow_terminal.py` and its Python test, and this record. Under R13 item 3, a narrow additive
  edit to another file is allowed when the frozen design or R1 to R13 already require it; record each one with its
  reason.
- Shared with continuation 32: `RocketGovernedSourceDispatcher` (it calls the return executor) and
  `RocketTransferEntities`. Keep your edits there small and additive; Root resolves the merge.
- Out of scope, owned by continuation 32: `RocketGovernedLaunchBoundary`, the ticket and readiness helpers, the
  reconciliation classes, `EscrowGovernedReadinessGameTests`, `EscrowReturnProbe` and `run_v180_escrow_returns.py`.
- Use a new GameTest batch only when a fixture needs isolation.

This session has room for more than one step: do not stop after the first committed step if time remains. Keep the
whole unit suite passing after every step and the unchanged log checker passing. A warning that remains is a finding
to report with its line. Stop only for a change to an acknowledgement order, a proof, a hold's effect or the replay
exclusion: commit what you have with your record, and report. Questions sent through the task conversation do not
reach Root; the record and the final answer do. If you run short of time, stop at a committed step boundary with every
test of that step passing, and say exactly where the next continuation starts. One GameTest run and one set of native
cuts per committed step is enough.

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C33/`. Continuation 32
shares the heavy-JVM lock; wait for it as WORKER-RULES says, and never remove a lock you do not own.

## Not in this task

- C0 to C8, unloaded-end tickets, the return and pruning cuts (continuation 32).
- The A0 serial, replay and cancel cases beyond what step 1 needs, F6 to F11 (console `inspect` and `retry`, status,
  fairness and backoff) and the eight-flight benchmark (later continuations).
- Production admission, W11b3, the bay slices, W27f reconstruction, signed per-fluid accounting.
- Part-1 findings F3, F8 and N2 (after the W11b2 merge; their designs are frozen as W86 and W85).
- ADRs, the risk register, status files and the ledger (Root).
