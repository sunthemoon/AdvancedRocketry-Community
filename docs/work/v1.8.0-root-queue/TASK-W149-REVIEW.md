# TASK W149-review: independent read-only review of W149's W98-R1 design

Root: Claude. Date: 2026-10-23. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, and change nothing outside your own review worktree and output folder. Do not start other agents.

| Item | Value |
|---|---|
| Subject | `docs/work/v1.8.0-root-queue/records/W149-DESIGN.md` on branch `codex/v1.8.0-w149` at `4b328f85`. Review worktree `D:/GitHub/arce-v180-w149-review`, a detached checkout of `4b328f85` |
| Task it answers | [TASK-W149](TASK-W149-W98-R1-DESIGN.md) |
| Background | W98-R1 in the W98 review and [records/W98.md](records/W98.md), R-035's residual (b), W147's record (proofs superseded by repeated saves), and [records/W145.md](records/W145.md) (the governed launch queue) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W149-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **The behaviour on today's code**: is the same-slot invalidation described correctly against the shared head (how
   often, what the governed side does, whether a stream of ordinary captures can starve it)?
2. **The design**: does protecting the governed proof by capture generation and deferring same-slot ordinary captures
   keep both proofs sound (no proof accepted over a newer save it did not see; no ordinary census completed from a stale
   capture)? Is the stated one-attempt guarantee true under its stated conditions, and are the conditions honest about
   external saves?
3. **Liveness and fairness**: can a deferred ordinary capture wait forever, or starve W98's retirements and W130F's
   fill retirements? Is the wait bounded or at least reported, and is that acceptable as an R-035 entry?
4. **Constraints**: no saved format, schema, bound, lease, budget or timeout change; no server-thread block; per-tick
   work bounded; W98's census and backoff, W123's rules and W145's queue kept.
5. **Tests and slices**: does the plan cover the unit, GameTest, native load and restart cases needed to show the
   governed proof completes within the stated bound under continuous ordinary captures?

## Rerun

Read-only source inspection; probe tests in your output folder only if they settle a finding. No GameTest or server; no
heavy-JVM lock.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W149-R1, ...), severity, section, problem and
suggested change; the commands you ran; the SHA-256 of REVIEW.md on the last line.
