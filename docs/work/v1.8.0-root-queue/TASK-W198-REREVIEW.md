# TASK W198-rereview: independent read-only re-review of the W198 oracle fixes

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w198` at `ca25c912e`; the fix is `1e86fa5a2..ca25c912e` (5 commits, test-side only). Review worktree `D:/GitHub/arce-v180-w198-rereview`, a detached checkout of `ca25c912e` |
| Task it answers | [TASK-W198-FIX](TASK-W198-FIX.md), Root decision R173 |
| First review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W198-review/REVIEW.md` (BLOCK, SHA-256 `6d813733…8fca6e`), with its `test_review_oracle.py` |
| Record | `docs/work/v1.8.0-root-queue/records/W198.md` on the branch, the fix section |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W198/`, especially `fix-01/` (the per-row replay results) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W198-rereview/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **W198-R1 to R3 are closed.** Each fix rejects its reproduction for the right reason, and generally: any
   unaccounted resource or sealed identity inside the census (including nested inventories); any pre-RESTORED U without
   the exact current durable D1; any missing member at a block-authoritative election. Try at least one variant of
   each reproduction that the new tests do not cover.
2. **No new false rejections or passes.** Legitimate partial pre-RESTORED cuts still pass; the shared election helper's
   other uses are unchanged; the census is not widened.
3. **The replay.** Reproduce the replay of all 357 archives with the fixed oracle, and check `fix-01/` against it. The
   original evidence is unchanged.
4. **Nothing weakened**, and still no production change.
5. **Merge conflicts** against the shared head (`origin/codex/v1.8.0-classic-content`).

## Rerun

In your review worktree only: the W198 Python tests, the first review's `test_review_oracle.py` (now expected to pass)
and the archive replay. No Gradle, GameTest, native case or server. Delete anything you generate in the worktree.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W198-R4, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
