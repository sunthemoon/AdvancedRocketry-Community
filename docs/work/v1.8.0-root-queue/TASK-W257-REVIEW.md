# TASK W257-review: independent read-only review of W254 (W230-S3) and W257 (the S2 committed-sample fix)

Root: Claude. Date: 2026-11-02. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w257` at `8f02e257e`: W254's commits `0d38529a0` to `3417b983a` (the native exit harness, gated fixtures, packaged smoke and two-client checklist), Root's merge of the shared head `5ba122014`, W257's commits `05c6a1b0b` (design note) to `dd667fbc8` (record), and Root's merge of the shared head `e367462e7`. Review `git diff 5335972b2...8f02e257e` (everything since W254's base). Review worktree `D:/GitHub/arce-v180-w257-review`, a detached checkout of `8f02e257e` that Root creates |
| Design and tasks | [W230-DESIGN](records/W230-DESIGN.md) Revision 1; [TASK-W254](TASK-W254-BAY-EXIT-S3.md) (R279) and [TASK-W257](TASK-W257-S2-COMMITTED-SAMPLES.md) (R283); the merged S2 tasks [TASK-W242-FIX](TASK-W242-FIX.md) and [TASK-W242-FIX2](TASK-W242-FIX2.md) (R264, R274) |
| Records | `docs/work/v1.8.0-root-queue/records/W254.md` and `records/W257.md` on the subject branch; evidence, `FINDING-S2-PLAYER-TICK.md` and `RISK-DRAFTS-W254.md` in `D:/GitHub/ARCE-Task-Evidence/root-queue/W254/`; W257's in `W257/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W257-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **W257 against R283.** Continuity compares only against committed samples (`PlayerTickEvent` START and the
   server-tick END); a PLAYER_END sample is checked against that baseline but never becomes it; a real upward move,
   teleport or skipped tick inside the tick still revokes; the R264 single early counter step, no rebaselining, fluid
   and unknown contact, the deadline and the budgets are unchanged. Look for any path where a committed sample can be
   skipped so that two committed samples are more than one tick apart, and for any change of what a contact probe
   sees. Check the call order in Forge 47.4.10 (`ServerGamePacketListenerImpl.tick`, `ServerPlayer.doTick`,
   `Player.tick`) yourself.
2. **The ticked-connection tests** reproduce the defect on the old code (they failed first) and cover descent,
   landing, expiry and correction; no existing bay-exit case changed its assertions.
3. **W254's harness and fixtures.** Every release-test hook is gated like the existing ones and unreachable on an
   ordinary server; the cut matrix and packaged smoke prove what the records say (seat, receipts, journal, chunk and
   ticket counts, two restarts); the native reruns of X2, X4, X5 and X7 now pass.
4. **The drafts.** `RISK-DRAFTS-W254.md` and W257's R-042 wording match the code; the two-client checklist is usable
   and names its open path decision.
5. **The sharded branch run** on `8f02e257e` once it finishes, and **nothing weakened**.

## Rerun

In your review worktree only: the bay-exit unit classes, the harness's Python tests, the whole unit suite and the
repository validator. Do not run GameTests, servers or clients.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK). Findings with an ID (W257-R1, ...), severity, file and line,
problem and suggested change. The commands you ran and their results. The SHA-256 of REVIEW.md on the last line.
