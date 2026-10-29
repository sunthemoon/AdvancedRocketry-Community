# TASK W195: the empty-slot proof GameTest that failed on a hosted run

Root: Claude. Date: 2026-10-29. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w195` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w195` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W195.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W195/`, which holds the hosted job log `job-123217349252.log` |
| Background | [records/W187.md](records/W187.md) and [records/W184.md](records/W184.md), the autosave guards |

Hosted run 40798480031 at `05f07d7d` (W190 merged; W190 changed only Python harness scripts) failed one GameTest:
`twoTrulyEmptySlotsReceiveIndependentOwnStoreAbsenceProofs`, "Empty-slot proofs were not retained together". It
passed in the runs before and after.

## What to do

1. Find the cause from the job log and the code: whether the periodic autosave (or another save) superseded one of
   the two empty-slot proofs between their captures, as W184 and W187 found for other fixtures, or something else.
2. If it is the autosave, guard the fixture as W187 did, with the window derived and commented. If it is a production
   cause, do not fix it; describe it with a reproduction and stop.
3. Do not change a timeout, bound, budget or assertion, and add no manifest entry.

## Verification

The changed batch three times under the heavy-JVM lock; the whole unit suite if a shared fixture changes. Root's
hosted run stands in for a complete local GameTest.

## Not in this task

Other fixtures, production code, ADRs, status files and the ledger.
