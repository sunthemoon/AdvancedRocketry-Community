# TASK W242-recheck: independent read-only recheck of W242-fix2

Root: Claude. Date: 2026-11-02. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w242` at `ffedf0dbc`: W242-fix2's commits `3b4257faa` to `7ecb70e27` on top of the reviewed `f0c622a30`, and Root's merge of the shared head `08e0b8a81`. Review `git diff f0c622a30 7ecb70e27`. Review worktree `D:/GitHub/arce-v180-w242-review`, which Root moves to a detached checkout of `ffedf0dbc` |
| Findings | W242-R1 and R3 to R10 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W242-review/REVIEW.md` (SHA-256 `bfcc2e8e10c1c46cc2e07462a63572f34facff8f0b1b89b52f06f1bd75bf603c`) |
| Task it answers | [TASK-W242-FIX2](TASK-W242-FIX2.md), Root decision R274 |
| Record | The "W242-fix2" section of `docs/work/v1.8.0-root-queue/records/W242.md` on the subject branch; logs and the new risk draft `RISK-DRAFTS-R274-20261102.md` in `D:/GitHub/ARCE-Task-Evidence/root-queue/W242-fix2/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W242-recheck/RECHECK.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **W242-R1.** `RocketEntity.removePassenger` calls `super` and then `RocketRuntime.passengerRemoved`, whose
   `getChunkNow` can neither load, wait nor add a ticket, and runs before vanilla's dismount read (check the 1.20.1
   call order in `Entity.removeVehicle`, `LivingEntity.stopRiding` and `ServerPlayer.stopRiding`); the exact ticket
   assertion is unchanged; the probe evidence (five cohorts before, ten after) supports the record's claims, including
   what it says it could only infer for masked exits.
2. **W242-R5.** Every exit-path column check and block read uses a never-waiting lookup and fails closed.
3. **W242-R3.** The two trusted moves run inside `internalPassengerMove`, the scope closes on every path, and the new
   GameTest failed before the fix.
4. **W242-R6 and R10.** Every non-SAFE, non-NO_SAFE_EXIT status grants nothing and has its own English and Chinese
   message; the pending-refusal map is bounded and cleared each tick and on revoke; the held LEAVE result no longer
   adds the flight line.
5. **W242-R8 and R9.** The fallback offset; the per-batch cleanup and the gated test hook touch only test state.
6. **The risk draft** adds the review's five residuals accurately.
7. **The sharded branch run** on `ffedf0dbc` once it finishes, and **nothing weakened**.

## Rerun

In your review worktree only: the bay-exit unit classes, the whole unit suite and the repository validator. Do not run
GameTests, servers or clients.

## Output format

A verdict for each finding (CLOSED or NOT CLOSED) and for the fix (APPROVE or BLOCK), new findings with an ID
(W242-R11, ...), the commands you ran and their results, and the SHA-256 of RECHECK.md on the last line.
