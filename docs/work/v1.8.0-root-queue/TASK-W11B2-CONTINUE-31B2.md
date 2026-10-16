# TASK W11b2, continuation 31B part 2: live F7, the F8 cleanup executor and R11 item 3

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-31B](TASK-W11B2-CONTINUE-31B.md)
and the documents it names first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-f7f8` (at `a7dec6aa` plus this task) |
| Branch | `codex/v1.8.0-w11b2-f7f8` (local only; Root merges it into `codex/v1.8.0-w11b2-rocket-integration`) |
| Contract | As TASK-W11B2-CONTINUE-31B, plus **R12** in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C31B/ROOT-DECISION-R12.md` (SHA-256 `9c30c3598ed0d0a60adbcd0d9ed1e4b173e773f38a958a14d2bfe9cd9f7051d1`) |
| Input | `records/W11B2-F7F8.md` (the checked F7 writer core `EscrowGovernedHandoff` is done and unwired; read its integration gap and next steps) |
| Record | Add a "Part 2" section to `records/W11B2-F7F8.md`. Keep the existing text. Do not edit `records/W11B2.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C31B2/` |

Root reviewed part 1, removed its GameTest world, and answered its scope question with R12: you may now edit
`RocketEscrowGate`, `EscrowRuntime`, `RocketTransferService`, `RocketTransferEntities` and
`EscrowCoordinator.logicalLocked`, narrowly, as R12 says. Root also merged continuation 31's first commit (`cf45282d`,
the live countdown-return checkpoint) into this branch at `a7dec6aa`, because continuation 31 edits `EscrowRuntime` and
`RocketTransferService` in parallel. Keep your edits in those two files additive, and Root resolves the merge.

## What to do (commit each step with its tests)

Continue TASK-W11B2-CONTINUE-31B from its step 1, in its order:
1. Finish step 1: the live F6 observer with synchronous native and passenger invalidation (R12 item 5), the wired F7
   writer, and the exact successor-lock release after the F7 acknowledgement (R12 items 2 and 3).
2. Step 2: the checked F8 cleanup executor, with the explicit ticket and pad release (R12 item 4).
3. Step 3: R11 item 3, the CLEANUP route, and the A0 diagnostic's changed expectations.
4. Step 4: the two live landed-flight fixtures through F7 and F8, and unit tests for both outcomes.
5. Step 5, if time remains: the native F7 and F8 cuts.

This session has room for more than one step: do not stop after the first committed step if time remains. The rules of
TASK-W11B2-CONTINUE-31B on stopping, the log checker, warnings, commands and what is not in the task apply unchanged.
Continuation 31 shares the heavy-JVM lock; wait for it as WORKER-RULES says, and never remove a lock you do not own.
