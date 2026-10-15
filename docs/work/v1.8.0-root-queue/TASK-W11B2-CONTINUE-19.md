# TASK W11b2, continuation 19: the rest of step 3, then steps 4 to 7

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md)
and the documents it names first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (at `b9b970f1` plus this task) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local only; Root merges) |
| Input | `records/W11B2.md`, "Continuation 18" (R8 and R9 applied; the shared branch merged at `646bf9ee`; 3,420 unit tests and 1,099 GameTests pass) and "Continuation 15" (where step 3 resumes) |
| Root decisions | R1 to R9 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C09-review/ROOT-DECISIONS.md` (SHA-256 `05324af2e613228f4238268d6001cbec9dfb55ca04e0d4198b5e0ec809eafd7e`) |
| Record | Add a "Continuation 19" section to `records/W11B2.md`. Keep the existing text |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C19/` |

Root accepts continuation 18: R8 applied to retained floors as well (one fewer fill per capacity row with 29
floors), the reservation walk, and the merge resolution, including the envelope test now asserting journal
root 3 from W44 with the root-2 migration check added.

## What to do (commit each step with its tests)

1. **Finish step 3**, where continuation 15 says it resumes: the live launch and F1 reservation connection;
   the action-specific recovery fence use; the destination, passenger, removal and return permits; deferred
   callbacks, cancellation and failed landing.
2. **Steps 4 to 7 of TASK-W11B2-CONTINUE-10, in order:** F0 to F8 and C0 to C8; the required test that closes
   N1, so that the unchanged log checker passes; the A0 cases and the S2 cuts; F6 to F11 with the eight-flight
   benchmark as a measurement.

Keep the whole unit suite passing after every step. A change to an acknowledgement order, a proof, a hold or
the replay exclusion needs Root first: stop, commit what you have with your record, and report. If you run
short of time, stop at a committed step boundary with every test of that step passing, and say exactly where
the next continuation starts.

The intermittent sleep-room GameTest belongs to W78. If it fails in your run, record it with the log line and
say that your change does not touch it. Do not retry it until it passes, and do not edit it.

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C19/`. If your
cleanup command is refused, record the exact paths; Root deletes them.

## Not in this task

- Production admission, W11b3, the bay slices, W27f reconstruction, signed per-fluid accounting.
- Part-1 findings F3, F8 and N2 (after the W11b2 merge).
- ADRs, the risk register, status files and the ledger (Root).
