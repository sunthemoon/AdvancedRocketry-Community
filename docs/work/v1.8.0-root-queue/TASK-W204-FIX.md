# TASK W204-fix: a named pad is reused before its old claim's absence is acknowledged

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session; this file hands
W204's work over.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w204` (W204's; continue on its branch) |
| Branch | `codex/v1.8.0-w204`, now at `ce16502de` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W204.md`: append the design note, then the fix and the rest of the campaign |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W204/` (the finding is in `native-01/N2_PAD_MOVED/`); add `native-02/` |
| Background | [TASK-W204](TASK-W204-N3C-REFUSAL-AND-NAMED-PAD-CUTS.md) and the record's sections "Design and cut mapping" and "Native tranche and production finding" |

**Root decision R176.** W204's N2_PAD_MOVED cut found that `LandingPadService.claim`
(`station/pad/LandingPadService.java:86-98`) releases a named pad's old claim when the old transfer is absent from the
journal's memory, without requiring that the absence be acknowledged on disk. At the `after_atomic_move` boundary
(acknowledged 0, completions 0) a contender's claim succeeds. The move had succeeded there, so nothing was lost, but at
an UNKNOWN or failed removal the old transfer can still be on disk while its pad belongs to someone else. This is a
production fix in a persistence and authority path.

## What to do

1. **Design note first, appended to the record and committed before code** (one page): the rule for releasing an old
   claim; which journal state counts as acknowledged absence, including after a restart, when a loaded journal is the
   acknowledged state; what a contender sees while the old claim waits (a refusal it can retry, never a lost claim);
   the restart and lazy-reconciliation behaviour; and the `live=0` membership drop the record notes at the service's
   absent-record branch, which the fix must either leave correct or explain. No format change.
2. **Fix it** with a unit or GameTest regression that fails first, at the acknowledgement boundary: an absent but
   unacknowledged old transfer keeps its claim; an acknowledged absence releases it exactly once.
3. **Finish the campaign** on a JAR built from your committed source, under the heavy-JVM lock: rerun N2_PAD_MOVED and
   run N2_PAD_UNKNOWN, each with two restarts, plus exactly one acknowledged release; rerun N2_REFUSED and
   N2_STOPPED as controls. Stop and report if a row fails twice.

W206 runs native cases under the same lock; wait for it, never remove it.

## Verification

The new regression alone (failing first), the whole unit suite, the station, pad, scheduling and return GameTest
batches three times, and the native rows. Root's hosted run on the pushed branch stands in for a complete local
GameTest. An independent read-only review of the design and the fix follows before merge.

## Not in this task

Cancellation redesign, offline-crew rules, other pad policy, the production default limit, budgets, timeouts, ADRs,
status files and the ledger.
