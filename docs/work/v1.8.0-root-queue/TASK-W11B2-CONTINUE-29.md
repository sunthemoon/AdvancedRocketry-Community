# TASK W11b2, continuation 29: post-F4 return, failed landing and F6 to F8; then steps 4 to 7

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md)
and the documents it names first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (at `f5820b43` plus this task) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local only; Root merges) |
| Input | `records/W11B2.md`, "Continuation 28" (R10 is done and the unchanged checker passes; step 3 resumes at post-F4 return) |
| Root decisions | R1 to R9 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C09-review/ROOT-DECISIONS.md` (SHA-256 `05324af2e613228f4238268d6001cbec9dfb55ca04e0d4198b5e0ec809eafd7e`), and R10 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C27/ROOT-DECISION-R10.md` (SHA-256 `89e43983dba85032f0f8f0f7a63cc61e300f9ccc967adeaaefdae7e9201d3227`) |
| Record | Add a "Continuation 29" section to `records/W11B2.md`. Keep the existing text |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C29/` |

Root reviewed continuation 28 and removed its GameTest world. The unchanged log checker now passes: keep it passing.
A new warning is a finding to report with its line, never one to suppress. A refused cleanup never stops your work.

## What to do (commit each step with its tests)

1. **Finish step 3**, where continuation 28 says it resumes: post-F4 return, cancellation and failed landing; the F6 to
   F8 joint proofs, handoff and cleanup.
2. **Steps 4 to 7 of TASK-W11B2-CONTINUE-10, in order:** F0 to F8 and C0 to C8; the required test that closes N1; the
   A0 cases and the S2 cuts; F6 to F11 with the eight-flight benchmark as a measurement.

This session has room for more than one step: do not stop after the first committed step if time remains. Keep the
whole unit suite passing after every step. A change to an acknowledgement order, a proof, a hold's effect or the replay
exclusion needs Root first: stop, commit what you have with your record, and report. If you run short of time, stop at
a committed step boundary with every test of that step passing, and say exactly where the next continuation starts.
One GameTest run and one set of native cuts per committed step is enough.

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C29/`.

## Not in this task

- Production admission, W11b3, the bay slices, W27f reconstruction, signed per-fluid accounting.
- Part-1 findings F3, F8 and N2 (after the W11b2 merge; their designs are frozen as W86 and W85).
- ADRs, the risk register, status files and the ledger (Root).
