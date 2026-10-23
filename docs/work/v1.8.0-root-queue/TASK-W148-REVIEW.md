# TASK W148-review: independent read-only review of W148's N3-B design amendment

Root: Claude. Date: 2026-10-23. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, and change nothing outside your own review worktree and output folder. Do not start other agents.

| Item | Value |
|---|---|
| Subject | `docs/work/v1.8.0-root-queue/records/W148-DESIGN.md` on branch `codex/v1.8.0-w148` at `1bf9a259`. Review worktree `D:/GitHub/arce-v180-w148-review`, a detached checkout of `1bf9a259` |
| Task it answers | [TASK-W148](TASK-W148-N3-B-DESIGN-AMENDMENT.md) |
| Background | [records/W96-DESIGN.md](records/W96-DESIGN.md), [records/W98.md](records/W98.md), [records/W116.md](records/W116.md), R-035 in the risk register |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W148-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **I6 and I7 on today's code**: are the windows stated correctly and completely against the shared head's source (write,
   crash point, restart, what is lost)? Did any later slice already close part of them, and does the note say so?
2. **The amendment**: does the extended pre-retirement proof close each window without opening another (a rocket with
   neither its record nor a proven single copy; a stale copy that can win; a terrain transaction left half-applied)?
   Is the order of writes and proofs sound, and does I7 keep the old end's exclusion authority until it is proven gone?
3. **Constraints**: no saved format, schema, bound, lease, budget or timeout change, or a stated necessity; bounded
   per-tick work; W123's acknowledgement rules and W98's census and backoff kept.
4. **Holds and refusals**: is each one described with its scope, duration, logging and recovery well enough for Root's
   risk-register entry (AGENTS section 3.3)?
5. **Tests and slices**: do the proposed unit, GameTest and native cuts cover every window with two restarts each, and
   are the slices independently mergeable and safe in between (the note warns against pre-M1 saving alone)?

## Rerun

Read-only source inspection; probe tests in your output folder only if they settle a finding. No GameTest or server; no
heavy-JVM lock.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W148-R1, ...), severity, section, problem and
suggested change; the commands you ran; the SHA-256 of REVIEW.md on the last line.
