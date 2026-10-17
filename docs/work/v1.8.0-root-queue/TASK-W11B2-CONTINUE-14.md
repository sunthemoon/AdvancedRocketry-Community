# TASK W11b2, continuation 14: step 3's dispatcher and permits, then steps 4 to 7

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2](TASK-W11B2-ROCKET-INTEGRATION.md),
[TASK-W11B2-CONTINUE-10](TASK-W11B2-CONTINUE-10.md) (steps 3 to 7) and [TASK-W11B2-CONTINUE-12](TASK-W11B2-CONTINUE-12.md)
(its Root decision) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (at `895e4274` plus this task) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local only; Root merges) |
| Contract | `records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`) and Root's decisions in TASK-W11B2-CONTINUE-09, TASK-W11B2-CONTINUE-12 and `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C09-review/ROOT-DECISIONS.md` |
| Input | `records/W11B2.md`, "Continuation 12" and "Continuation 13" (done, evidence, next) |
| Record | Add a "Continuation 14" section to `records/W11B2.md`. Keep the existing text |

Continuation 13 left the whole unit suite passing (3,235), all 1,059 GameTests passing, and the
review follow-ups applied. Root accepted its cause for the four failures: the tests advanced the
owner clock by wall-clock sleeps, and now they await real IO completion.

## What to do (commit each step with its tests)

1. **Finish step 3.**
   - Wire the governed record-3 dispatcher and the action-specific permits into section 1's
     boundary table: `RocketTransferFences.classify`, `RocketEscrowGate.idle`,
     `EscrowRecoveryFence.reason`, joins and callbacks, cancellation and failed landing.
   - Route every root-growing write through the combined reservation.
2. **Steps 4 to 7 of TASK-W11B2-CONTINUE-10, in order:**
   - the flight order F0 to F8 and the reconciliation C0 to C8;
   - the required test that closes N1, so that the unchanged log checker passes;
   - the A0 cases and the S2 cuts;
   - F6 to F11, with the eight-flight benchmark recorded as a measurement.

Keep the whole unit suite green after every step. A change to an acknowledgement order, a proof, a
hold or the replay exclusion needs Root first. In that case, stop, commit what you have with your
record, and report. If you run short of time, stop at a committed step boundary with every test of
that step passing, and say exactly where the next continuation starts.

## Commands

- The whole unit suite, passing, on the final committed SHA.
- `runGameTestServer` under the lock on a **committed** SHA, then the unchanged log checker.
- Native cuts under the lock for each step that adds them. Keep the evidence under 100 MB in
  `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C14/`.
- At the end, delete your generated `build/gametest/world` and Temp copies with a plain `rm -rf`, and
  make sure the heavy-JVM lock is released. If the tool refuses, record the exact paths; Root deletes
  them.

## Not in this task

- Production admission, W11b3, the bay slices, W27f reconstruction, and signed per-fluid accounting.
- ADRs, the risk register, status files and the ledger (Root).
