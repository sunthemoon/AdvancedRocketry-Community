# TASK W91C: finish P1-F3 under R23, with the two fixture adaptations authorized

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W91-P1-N2-F3](TASK-W91-P1-N2-F3.md),
[TASK-W91B-P1-N2-F3-R23](TASK-W91B-P1-N2-F3-R23.md) and [records/W91.md](records/W91.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w91-p1-f3` (unchanged; at W91B's record `0a150d66` plus this task) |
| Branch | `codex/v1.8.0-w91-p1-f3` (local commits only; Root merges) |
| Inputs | Root decision R23 (`D:/GitHub/ARCE-Task-Evidence/root-queue/W91/ROOT-DECISION-R23.md`, SHA-256 `f6efbff7c7fac6efea3b3ed217f7d56d678c3e864baa3047d4b93964341af064`); W91B's N2 commits `31f5c34a` and `14f5240c`; its incomplete F3 draft `D:/GitHub/ARCE-Task-Evidence/root-queue/W91/implementation-20261018/f3-incomplete-draft.patch` (SHA-256 `3981df0abbb8d6e38922ff5b2787962a41167017908f6db3d0976227f413a92c`; uncompiled, a starting point only) |
| Record | `records/W91.md` (extend it) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W91/` |

## Root decision R24 (recorded here)

The two fixture-only adaptations in `records/W91.md` ("F3 test-fixture scope decision still required") are
authorized, exactly as proposed: `EscrowGovernedPreparationTest.aFullCallbackQueueAfterPreparedIsWrittenDoesNotDiscardTheLaunch`
and `EscrowPendingLandingGameTests` use an explicit two-slot detached fixture with pending dependencies for the
occupied slots, migrate to the new reservation query, and keep every F2 pending, no-discard, fallback, overflow and
landing assertion; F3's own concurrency measurements use the production bound of 128. Reason: F3 replaces the global
two-slot queue, but F2's put-then-reserve with F3's countdown fallback stays in production (R23 item 5), so these tests
must keep forcing the fallback path deliberately. No other existing test outside W86 Appendix C may change; list each
changed test and the reason in the record.

## What to do

1. **The jackhammer failure first.** W91B's full GameTest on `31f5c34a` failed the existing
   `installedunavailablestationbuilddeniesactualtoolbreak` (batch `jackhammer_native`, "Owned fixture requires loaded
   BE-free cells"), after W91B added the batch `journal_fail_back_admission` (378 batch names). Find out whether the
   new N2 GameTests interfere (shared or nearby fixture cells, block entities left behind, chunk loading, batch order),
   and fix the new tests if they do. Hosted run 39049214708 at `d9a7c0e9` (377 batch names, no N2 tests) passed all
   GameTests. If the cause is elsewhere, report it with evidence and do not change the jackhammer test.
2. **P1-F3** as TASK-W91B items 2 to 4 say, with R24's two adaptations.
3. **N2's remaining tests** (G-N2's held writer and one release, its stop or abort variant, and the native cuts) as W86
   section 8 lists, together with F3's.
4. Run the whole unit suite, one full GameTest with the unchanged log checker, and the native timing and cut runs, on
   committed source, under the heavy-JVM lock.

Root sends the finished diff to an independent read-only review before merging it (R23 item 7).

## Commands

As TASK-W91-P1-N2-F3 says. Other workers (W92, W93, W94) also compile and run tests; take the heavy-JVM lock for every
GameTest and native run. When your heavy run ends, remove your own lock with a plain `rm -rf` of exactly
`D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm` after checking that its `owner.txt` names you; if the tool refuses,
write the path in your record and final answer at once, because other workers wait on it.

## Not in this task

As TASK-W91-P1-N2-F3 says.
