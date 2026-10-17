# TASK W11b2, continuation 23: the governed source dispatcher and the rest of step 3, then steps 4 to 7

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md)
and the documents it names first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (at `503eb18f` plus this task) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local only; Root merges) |
| Input | `records/W11B2.md`, "Continuation 22" (live F1 preparation and the acknowledged F2 countdown action are connected; where step 3 resumes) |
| Root decisions | R1 to R9 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C09-review/ROOT-DECISIONS.md` (SHA-256 `05324af2e613228f4238268d6001cbec9dfb55ca04e0d4198b5e0ec809eafd7e`) |
| Record | Add a "Continuation 23" section to `records/W11B2.md`. Keep the existing text |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C23/` |

Root removed continuation 22's two refused directories. Root accepts continuation 22's six checker warnings as the
expected state of the unfinished governed routing; they must be gone, with N1's three, when the governed flight is
complete. A refused cleanup never stops your work (WORKER-RULES).

## What to do (commit each step with its tests)

1. **Finish step 3**, where continuation 22 says it resumes: the live governed source dispatcher (COUNTDOWN, ASCENT and
   TRANSIT, bounded motion, validated joins and restart clock rebasing); then destination publication, passengers,
   source removal, post-F4 return and failed landing, deferred callbacks and terminal proofs and cleanup.
2. **Steps 4 to 7 of TASK-W11B2-CONTINUE-10, in order:** F0 to F8 and C0 to C8; the required test that closes N1, so
   that the unchanged log checker passes; the A0 cases and the S2 cuts; F6 to F11 with the eight-flight benchmark as a
   measurement.

Keep the whole unit suite passing after every step. A change to an acknowledgement order, a proof, a hold or the
replay exclusion needs Root first: stop, commit what you have with your record, and report. If you run short of time,
stop at a committed step boundary with every test of that step passing, and say exactly where the next continuation
starts. Spend the session on new code, not on repeating the previous regression.

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C23/`.

## Not in this task

- Production admission, W11b3, the bay slices, W27f reconstruction, signed per-fluid accounting.
- Part-1 findings F3, F8 and N2 (after the W11b2 merge).
- ADRs, the risk register, status files and the ledger (Root).
