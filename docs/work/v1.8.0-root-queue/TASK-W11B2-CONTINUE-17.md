# TASK W11b2, continuation 17: floor-retirement headroom, the rest of step 3, then steps 4 to 7

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-16](TASK-W11B2-CONTINUE-16.md)
and the documents it names first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (at `a89b273c` plus this task) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local only; Root merges) |
| Input | `records/W11B2.md`, "Continuation 16" (R1 to R3 applied; stopped at the floor-retirement case) |
| Root decision | "Continuation 16: floor retirement and the R4 wording" (R5 to R7) at the end of `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C09-review/ROOT-DECISIONS.md` (SHA-256 `af71761575765fefb2144d31c3366b75653d08abf92416db2d708b59684a892e`) |
| Record | Add a "Continuation 17" section to `records/W11B2.md`. Keep the existing text |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C17/` |

## What to do (commit each step with its tests)

1. **Apply R5 to R7, in their own commit.** Fill admission keeps headroom for one floor retirement;
   pin the new capacities; turn the continuation 16 reproducer into the requirement R6 describes; add the
   bounded operator refusal line of R5. Keep continuation 16's R4 test (R7).
2. **Run the GameTests that continuation 16 could not run**, under the lock on the committed SHA of step
   1, then the unchanged log checker. If the lock is held, wait for it; do not take or change another
   worker's lock.
3. **Finish step 3**, where continuation 15 says it resumes: the live launch and F1 reservation
   connection; the action-specific recovery fence use; the destination, passenger, removal and return
   permits; deferred callbacks, cancellation and failed landing.
4. **Steps 4 to 7 of TASK-W11B2-CONTINUE-10, in order:** F0 to F8 and C0 to C8; the required test that
   closes N1; the A0 cases and the S2 cuts; F6 to F11 with the eight-flight benchmark as a measurement.

Keep the whole unit suite passing after every step. A change to an acknowledgement order, a proof, a
hold or the replay exclusion needs Root first: stop, commit what you have with your record, and report.
If you run short of time, stop at a committed step boundary with every test of that step passing, and
say exactly where the next continuation starts.

The two intermittent sleep tests (`NaturalBedGameTests.sixNativeHostsSleepKeepAbsenceAndTuplesAndRefuseLate`
and `SleepRespawnGameTests.sealedBreathableRoomsAdmitSleepButAPendingRoomDoesNot`) are W74's. If one fails
in your run, record it with the log line and say that your change does not touch it. Do not retry it
until it passes, and do not edit it.

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C17/`.

## Not in this task

- Production admission, W11b3, the bay slices, W27f reconstruction, signed per-fluid accounting.
- Part-1 findings F8 and F9 (W77 on its own branch), F3 and N2 (after the W11b2 merge).
- ADRs, the risk register, status files and the ledger (Root).
