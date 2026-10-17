# TASK W11b2, continuation 27: post-F4 return, failed landing, descent and landing, F6 to F8; then steps 4 to 7

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md)
and the documents it names first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (at `fe7c2fbc` plus this task) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local only; Root merges) |
| Input | `records/W11B2.md`, "Continuation 26" (F5 passengers and source removal are done; its last section says where step 3 resumes) |
| Root decisions | R1 to R9 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C09-review/ROOT-DECISIONS.md` (SHA-256 `05324af2e613228f4238268d6001cbec9dfb55ca04e0d4198b5e0ec809eafd7e`) |
| Record | Add a "Continuation 27" section to `records/W11B2.md`. Keep the existing text |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C27/` |

Root reviewed continuation 26 and removed its two refused native directories and the GameTest world. Root accepts the
ten checker warnings (continuation 25's eight plus two in the extended `escrow_governed_source_progress` fixture) as the
interim state of the unfinished governed routing. All of them must be gone when step 5 closes N1. A refused cleanup
never stops your work.

## What to do (commit each step with its tests)

1. **Finish step 3**, where continuation 26 says it resumes: post-F4 return, cancellation and failed landing; governed
   descent and landing; the remaining deferred terminal callbacks, the F6 to F8 joint proofs, handoff and cleanup.
2. **Steps 4 to 7 of TASK-W11B2-CONTINUE-10, in order:** F0 to F8 and C0 to C8; the required test that closes N1, so
   that the unchanged log checker passes; the A0 cases and the S2 cuts; F6 to F11 with the eight-flight benchmark as a
   measurement.

Keep the whole unit suite passing after every step. A change to an acknowledgement order, a proof, a hold or the
replay exclusion needs Root first: stop, commit what you have with your record, and report. If you run short of time,
stop at a committed step boundary with every test of that step passing, and say exactly where the next continuation
starts. Spend the session on new code: one GameTest run and one set of native cuts per committed step is enough.

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C27/`.

## Not in this task

- Production admission, W11b3, the bay slices, W27f reconstruction, signed per-fluid accounting.
- Part-1 findings F3, F8 and N2 (after the W11b2 merge; their designs are frozen as W86 and W85).
- ADRs, the risk register, status files and the ledger (Root).
