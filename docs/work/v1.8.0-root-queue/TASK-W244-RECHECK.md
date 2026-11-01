# TASK W244-recheck: independent read-only recheck of W244-fix

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w244` at `606430600`: W244-fix's commits `9531c6b96` (design note) to `a18ef8621` (record) on top of the reviewed `96e4925c9` and Root's `5d0799199`, Root's `cc72ee294` applying the VERIFICATION capsule paragraph, and Root's merge of the shared head `81c199874`. Review worktree `D:/GitHub/arce-v180-w244-review`, which Root moves to a detached checkout of `606430600` |
| Findings | W244-R1, W244-R2 and W244-R4 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W244-review/REVIEW.md` (SHA-256 `1756fd9853507e0d1953729ab7d4b76077e5ce066f6535ca5a6e1215471f5edd`) |
| Task it answers | [TASK-W244-FIX](TASK-W244-FIX.md), Root decisions R263 and R265 (keep the countdown tint, the doorway facing north, a corner rider partly outside the capsule, all for V1 to judge) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W244-recheck/RECHECK.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **W244-R1.** The capsule is the legacy `Capsule` group with its own UVs, one per endpoint with a pending ride, at
   most 64 per frame, placed by the transcribed legacy transform (check the transcription against upstream
   `c5cd5af6` yourself), and every UV-mapped face samples opaque texels (rerun the review's check).
2. **The new OBJ reader** (`CapsuleMesh`). A resource pack can replace `spaceelevator.obj`. Confirm that any malformed
   file (an out-of-range index, a face with more than four corners, a missing texture coordinate or normal, a huge
   group) ends in the line-cage fallback with a bounded log, never an exception in the render path or unbounded work,
   and that it adds no second copy of logic the main loader should own without a reason.
3. **W244-R4.** The model and texture are checked once per resource reload; an unreadable texture falls back like a
   missing one.
4. **W244-R2.** A second-parent-only identical rename now lists its candidates; the earlier records are unchanged
   (import verify for K1 to K4, K6 and W244).
5. **The applied VERIFICATION paragraph** is true to the evidence.
6. **The sharded branch run** on `606430600` once it finishes, and **nothing weakened**.

## Rerun

In your review worktree only: the client model and elevator unit tests, the importer Python tests, import verify, and
the repository validator. Do not run GameTests, servers or clients.

## Output format

A verdict for each finding (CLOSED or NOT CLOSED) and for the fix (APPROVE or BLOCK), new findings with an ID
(W244-R5, ...), the commands you ran and their results, and the SHA-256 of RECHECK.md on the last line.
