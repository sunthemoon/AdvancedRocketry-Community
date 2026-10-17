# TASK W11b2, continuation 16: the reservation decision, the rest of step 3, then steps 4 to 7

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md)
and the documents it names first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (at `d178e727` plus this task) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local only; Root merges) |
| Input | `records/W11B2.md`, "Continuation 15" (the governed dispatcher and the three source-action permits are done; it stopped at the reservation boundary) |
| Root decision | "Continuation 15: the reservation boundary" (R1 to R4) at the end of `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C09-review/ROOT-DECISIONS.md` (SHA-256 `1cff184db88c738e8f697ed793298f14bf50a9e444c52ef4d851b05e066ac64d`) |
| Record | Add a "Continuation 16" section to `records/W11B2.md`. Keep the existing text |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C16/` |

## What to do (commit each step with its tests)

1. **Apply Root's decisions R1 to R4, in their own commit.**
   - R1: admission charges every unsettled component, the candidate's included, at the fixed ceiling,
     after the candidate's preview has succeeded. Measure the effective root capacity and pin it (that
     number admitted, one more refused).
   - R2: leave the ordinary transition and autosave snapshot outside the reservation. Add no new save
     refusal.
   - R3: turn `EscrowReservationTransitionTest` into the requirement that R3 describes.
   - If a transition can raise the all-ceiling total after admission, stop and report it (R1).
2. **Finish step 3**, where continuation 15 says it resumes:
   - the live launch and F1 reservation connection;
   - the action-specific recovery fence use;
   - the destination, passenger, removal and return permits;
   - deferred callbacks, cancellation and failed landing.
3. **Steps 4 to 7 of TASK-W11B2-CONTINUE-10, in order:**
   - the flight order F0 to F8 and the reconciliation C0 to C8;
   - the required test that closes N1, so that the unchanged log checker passes;
   - the A0 cases and the S2 cuts;
   - F6 to F11, with the eight-flight benchmark recorded as a measurement.

Keep the whole unit suite passing after every step. A change to an acknowledgement order, a proof, a
hold or the replay exclusion needs Root first: stop, commit what you have with your record, and report.
If you run short of time, stop at a committed step boundary with every test of that step passing, and
say exactly where the next continuation starts.

**The second intermittent sleep test.**
`SleepRespawnGameTests.sealedBreathableRoomsAdmitSleepButAPendingRoomDoesNot` is not yours. If it fails
in your final run, record it with the log line and say that your change does not touch it. Do not retry
it until it passes, and do not edit it.

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C16/`.

## Not in this task

- Production admission, W11b3, the bay slices, W27f reconstruction, signed per-fluid accounting.
- ADRs, the risk register, status files and the ledger (Root).
