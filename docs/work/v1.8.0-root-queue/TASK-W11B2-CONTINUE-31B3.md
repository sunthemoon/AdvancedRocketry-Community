# TASK W11b2, continuation 31B part 3: live F7, the F8 cleanup executor and R11 item 3

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-31B](TASK-W11B2-CONTINUE-31B.md),
[TASK-W11B2-CONTINUE-31B2](TASK-W11B2-CONTINUE-31B2.md) and the documents they name first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-f7f8` (at `8a665dad` plus this task) |
| Branch | `codex/v1.8.0-w11b2-f7f8` (local only; Root merges it into `codex/v1.8.0-w11b2-rocket-integration`) |
| Contract | As TASK-W11B2-CONTINUE-31B2, plus **R13** in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C31B/ROOT-DECISION-R13.md` (SHA-256 `c2a39814fa5e2cb08dd0413fd5a36340d2667b2a011f37e02b66ab4bfedbc5e4`) |
| Input | `records/W11B2-F7F8.md`, part 1 (the unwired F7 writer core) and "Part 2" (the native-observation audit) |
| Record | Add a "Part 3" section to `records/W11B2-F7F8.md`. Keep the existing text. Do not edit `records/W11B2.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C31B3/` |

Root read part 2's audit and answered it with R13. The F6 and F7 lease uses the standard already accepted for F1's
source checkpoint: synchronous invalidation through `EscrowRuntime.invalidateFlight` (removal, tracking loss,
dismount), owner-thread re-observation, and a full re-observation at capture. Position and motion setters are not
hooked, for the reason R13 gives. R13 item 3 also pre-authorizes narrow additive edits to any file that the frozen design
or R1 to R13 already require, including `RocketEntity` and `RocketEscrowInterlocks`. Record each one; do not stop for
file scope.

## What to do (commit each step with its tests)

The steps of TASK-W11B2-CONTINUE-31B2, unchanged:
1. The live F6 lease (R13 items 1 and 2), the wired F7 writer, and the exact successor-lock release after the F7
   acknowledgement (R12 items 2 and 3).
2. The checked F8 cleanup executor, with the explicit ticket and pad release (R12 item 4).
3. R11 item 3: the CLEANUP route, and the A0 diagnostic's changed expectations.
4. The two live landed-flight fixtures through F7 and F8, and unit tests for both outcomes.
5. If time remains, the native F7 and F8 cuts.

This session has room for all five steps: do not stop after the first committed step if time remains. Stop only for a
change to an acknowledgement order, a proof, a hold's effect or the replay exclusion; in that case commit what you have
with your record and report. The rules of TASK-W11B2-CONTINUE-31B on the log checker, warnings, commands and what is not
in the task apply unchanged. Continuation 31 runs in parallel and shares the heavy-JVM lock; wait for it as
WORKER-RULES says, and never remove a lock you do not own.
