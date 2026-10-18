# TASK W91E: finish P1-F3 (Root decision R26), then its native evidence

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W91D-P1-F3](TASK-W91D-P1-F3.md) and
[records/W91.md](records/W91.md) (including its W91D section) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w91-p1-f3` (unchanged; at W91D's record `d69ef452` plus this task) |
| Branch | `codex/v1.8.0-w91-p1-f3` (local commits only; Root merges) |
| Inputs | R23, R24 and R25 as the earlier W91 tasks name them; W91D's commits `4e18a647` (jackhammer fixture), `efcbcfcd` (staged per-record scheduling) and `de65a516` |
| Record | `records/W91.md` (extend it) |

## Root decision R26 (recorded here)

The four existing tests that W91D lists ("Four unchanged tests fail because their supposed journal mutation is only
`setDirty()`") may replace only that fault injection with a real change of the rocket's own record (for example
`put(theSameRecord)`), keeping every assertion:
`EscrowGovernedLandingActionTest.deferredLandingRefusesChangedJournalLedgerBoundaryRidersAndNativeFields`,
`EscrowGovernedPreparationTest.unacknowledgedOrStoppedJournalDoesNotIssueACountdownPermit`,
`EscrowGovernedReturnActionTest.supersededJournalAndLedgerInvalidateDeferredReturns` and
`EscrowGovernedSourceRecoveryTest.supersededJournalLedgerAndTerminalOutcomeInvalidateDeferredActions`. Reason: under
R23 a whole-journal dirty bit without a change to L's record must not revoke L's authority, which is the purpose of
P1-F3; these tests meant "L's journal changed", and `setDirty()` no longer expresses that. Do not add a global dirty-bit
condition to per-record authority. List the four changes in the record.

## What to do

1. Apply R26; the whole unit suite must pass with no failures.
2. Finish what W91D lists as remaining: the A1 cases, N2's held writer and one release, its stop or abort variant, the
   eight-transfer native timing with the escrow-consumer measurements and the hard criterion "an actionable flight that
   did not step: 0", and the native restart cuts, as W86 section 8 lists.
3. Run the whole unit suite, one full GameTest with the unchanged log checker, and the native runs, all on committed
   source, under the heavy-JVM lock. Then state in the record that the diff is ready for R23 item 7's independent
   review.

## Commands and not in this task

As TASK-W91C says. Other workers (W94B, W95F, W92F) also run tests; take the heavy-JVM lock for every GameTest and
native run and release it between runs when another worker waits. When your heavy runs end, remove your own lock and
your `build/gametest/world` with a plain `rm -rf` after checking that the lock's `owner.txt` names you; if the tool
refuses, write the paths in your record and final answer at once.
