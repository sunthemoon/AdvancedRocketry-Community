# TASK W91B: part-1 N2, then P1-F3, under Root decision R23

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W91-P1-N2-F3](TASK-W91-P1-N2-F3.md) and
[records/W91.md](records/W91.md) first. You are a new session; the previous W91 session stopped at its design recheck.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w91-p1-f3` (unchanged; at W91's record `f263d640` plus this task) |
| Branch | `codex/v1.8.0-w91-p1-f3` (local commits only; Root merges) |
| Root decision | `D:/GitHub/ARCE-Task-Evidence/root-queue/W91/ROOT-DECISION-R23.md` (SHA-256 `f6efbff7c7fac6efea3b3ed217f7d56d678c3e864baa3047d4b93964341af064`): it reconciles W86 revision 2 with R17 and answers W91's blockers B1, B2 and the lease boundary |
| Record | `records/W91.md` (extend it; keep the recheck section) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W91/` |

## What to do

Carry out TASK-W91-P1-N2-F3 items 2 to 5 with R23's amendments:

1. **P1-N2 first**, as a self-contained commit (unchanged by R23).
2. **P1-F3** as W86 revision 2 specifies, amended by R23 items 1 to 6: keep every R17 logical-rocket consumer as it
   is; define `actionable(L)` on top of `RocketJournalLeases` without replacing or weakening it; make the scheduling
   waits of R23 item 3 per-record; keep R23 item 4's consumers on `clean()`; handle F2, O3J and F8 as R23 item 5 says;
   include R23 item 6's added sites in the reservation inventory.
3. **In the record**, add a final site table with each site's class (logical, `actionable(L)` or `clean()`), the
   `clean()` consumers' starvation bound, and every existing test whose expectation changed, with the reason.
4. **Tests and runs** as TASK-W91-P1-N2-F3 item 4 and 5 say. The existing R17 tests must pass unchanged.

Stop and report if a site cannot keep R23 items 1 to 6, or if the change needs a saved-format, acknowledgement-order
or proof change. Root sends the finished diff to an independent read-only review before merging it (R23 item 7).

## Commands

As TASK-W91-P1-N2-F3 says, including the rule that **no Gradle or Java starts while the heavy-JVM lock's `owner.txt`
names `W11B2-C54`**.

## Not in this task

As TASK-W91-P1-N2-F3 says.
