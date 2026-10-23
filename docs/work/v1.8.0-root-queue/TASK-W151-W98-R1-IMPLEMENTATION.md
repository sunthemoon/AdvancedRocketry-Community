# TASK W151: W98-R1, the shared-slot ordinary capture exclusion (implementation, Root decision R88)

Root: Claude. Date: 2026-10-23. Read [WORKER-RULES](WORKER-RULES.md), [records/W149-DESIGN.md](records/W149-DESIGN.md) (the
whole note, its slices in section 7), the W149 review (`D:/GitHub/ARCE-Task-Evidence/root-queue/W149-review/REVIEW.md`,
W149-R1 and R2), Root decision R88 (QUEUE) and the R-035 entry first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w151` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w151` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W151.md` (new; a one-page plan at its top before any code) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W151/` |

## What to do

Implement W149's design as its section 7 slices it: the governed proof protected by capture generation, a same-slot
ordinary capture deferred until the governed proof completes, and the one-attempt guarantee under its stated conditions.
Fold the review's two Low findings into the plan at the top of your record: the time bound includes the batch-wide
dependencies (W149-R1), and what happens when a fill demand expires before the pin releases (W149-R2). Keep W98's census
and backoff, W123's rules, W130F's caps and W145's queue; no server-thread block; bounded per-tick work. Each step in its
own commit with a test that fails before the change. A new log line gets an exact manifest entry when a GameTest drives
it.

No saved format, schema, bound, lease, budget, timeout or protocol change; stop and report if one seems needed. W150
(N3-B1) runs in parallel on the retirement coordinator; keep to your write scope and name any shared file in the record.

## Verification

The unit, GameTest and native load cases of W149's test plan, including a native case where the governed proof completes
within the stated bound under continuous ordinary captures, two restarts where the plan asks; each changed class alone,
the whole unit suite, one complete GameTest with the unchanged log checker, under the heavy-JVM lock with the atomic
pattern and the hand-off rule. Check C: and D: above 10 GiB and 6 GiB free memory first. An independent read-only review
by a fresh Codex session follows before merge.

## Not in this task

N3-B (W150 and its successors), enabling governed or cargo admission, ADRs, the risk register, status files and the
ledger (Root).
