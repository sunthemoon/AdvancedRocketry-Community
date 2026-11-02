# TASK W262-review: independent read-only review of the bay exit's S2 fixes and S3 evidence (W254, W257, W262)

Root: Claude. Date: 2026-11-03. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w257` at `238ede480`: W254 (`0d38529a0` to `3417b983a`, the native harness, gated fixtures, packaged smoke and V2 checklist), W257 (`05c6a1b0b` to `dd667fbc8`, the committed-sample baseline), W262 (`c7505c87c` to `e7115af6e`, real-client timing and the R288 rule), and Root's merges of the shared head. Review `git diff 5335972b2...238ede480`. Review worktree `D:/GitHub/arce-v180-w262-review`, a detached checkout of `238ede480` that Root creates |
| Earlier review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W257-review/REVIEW.md` (SHA-256 `8c0f201cd853021e0e61ec029abe7db75b2b6701e26a0515add39e4d3ee43083`), which blocked W257 on W257-R1 to R6 |
| Design and tasks | [W230-DESIGN](records/W230-DESIGN.md) Revision 1; [TASK-W254](TASK-W254-BAY-EXIT-S3.md) (R279), [TASK-W257](TASK-W257-S2-COMMITTED-SAMPLES.md) (R283), [TASK-W262](TASK-W262-S2-REAL-CLIENT-LANDING.md) (R288) |
| Records | `records/W254.md`, `records/W257.md`, `records/W262.md` and `records/W254-V2-CHECKLIST.md` on the subject branch; evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W254/`, `W257/` and `W262/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W262-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **W257-R1 and R2 against R288.** At PlayerTick END only vanilla's temporary ORDINARY floor contact is tolerated;
   fluid, UNSAFE and UNKNOWN still close; committed samples close on any contact; `fall()` reads `airborne` from the
   last committed START (or the committed state when no temporary step ran since), never from a server-END sample after
   that tick's step; a check-only sample is held to the per-tick limits on its own displacement and can neither spend
   nor renew committed usage. Look for any path where a tolerated PLAYER_END contact lets the offer survive a real
   landing it should not protect, or lets a second landing be protected. Check the Forge 47.4.10 call order yourself.
2. **The real-client-timed tests** (unit falls at 2 to 3.9 blocks per tick, the ticked 2.1 and 3.9 falls and the
   ten-block packet landing, native X4) failed on the old rules for the named causes and pass now; W257's same-tick
   pair survives only as the labelled artificial `PACKET_PAIR`; the one changed unit assertion encoded the superseded
   R283 projection and its hard limits are still asserted.
3. **W257-R3.** The flushed cuts restart from a save that contains the exit; the ticket comparison now ignores only
   vanilla's one-tick UNKNOWN tickets and still catches any other added ticket; X7's relaxed contact timing is justified.
4. **The rest of W254 and W257** that the first review approved, as far as W262 touched it; every release-test hook is
   still gated; the drive's listeners end with their batch (R6).
5. **The consolidated R-042 and R-043 draft** in W262's record and the V2 checklist match the code.
6. **The sharded branch run** on `238ede480` once it finishes, and **nothing weakened**.

## Rerun

In your review worktree only: the bay-exit unit classes, the harness's Python tests, the whole unit suite and the
repository validator. Do not run GameTests, servers or clients.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK). Findings with an ID (W262-R1, ...), severity, file and line,
problem and suggested change; say for each earlier W257-R finding whether it is closed. The commands you ran and their
results. The SHA-256 of REVIEW.md on the last line.
