# TASK W11b2, continuation 22: the rest of step 3, then steps 4 to 7

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md)
and the documents it names first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (at `7a162ef4` plus this task) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local only; Root merges) |
| Input | `records/W11B2.md`, "Continuation 21" (the merged source is verified: 3,498 unit tests, 1,100 GameTests; where step 3 resumes) |
| Root decisions | R1 to R9 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C09-review/ROOT-DECISIONS.md` (SHA-256 `05324af2e613228f4238268d6001cbec9dfb55ca04e0d4198b5e0ec809eafd7e`) |
| Record | Add a "Continuation 22" section to `records/W11B2.md`. Keep the existing text |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C22/` |

Root removed continuation 21's world.

**A refused cleanup never stops your work.** Continuation 21 stopped because its world deletion was refused and it
would not run `runGameTestServer` again. Record any refused path for Root and keep going. `runGameTestServer` deletes
its own `build/gametest/world` before it starts; running it again is normal use, not a way around the refusal.
(WORKER-RULES now says the same.)

This continuation should produce code. Do not spend it re-verifying what continuation 21 already verified.

## What to do (commit each step with its tests)

1. **Finish step 3**, where continuation 21 says it resumes: connect live F1 preparation and F2 acknowledgement to the
   governed dispatcher and its action-specific boundaries; then destination publication, passengers, source removal,
   post-F4 return and failed landing, deferred callbacks and restart clock rebasing.
2. **Steps 4 to 7 of TASK-W11B2-CONTINUE-10, in order:** F0 to F8 and C0 to C8; the required test that closes N1, so
   that the unchanged log checker passes; the A0 cases and the S2 cuts; F6 to F11 with the eight-flight benchmark as a
   measurement.

Keep the whole unit suite passing after every step. A change to an acknowledgement order, a proof, a hold or the
replay exclusion needs Root first: stop, commit what you have with your record, and report. If you run short of time,
stop at a committed step boundary with every test of that step passing, and say exactly where the next continuation
starts.

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C22/`. Run the GameTests and
native cuts for the code you add, not a repeat of continuation 21's regression.

## Not in this task

- Production admission, W11b3, the bay slices, W27f reconstruction, signed per-fluid accounting.
- Part-1 findings F3, F8 and N2 (after the W11b2 merge).
- ADRs, the risk register, status files and the ledger (Root).
