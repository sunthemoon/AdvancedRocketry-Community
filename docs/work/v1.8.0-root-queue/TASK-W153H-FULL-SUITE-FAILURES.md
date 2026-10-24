# TASK W153H: diagnose and fix the seven full-suite GameTest failures on the W153 branch

Root: Claude. Date: 2026-10-24. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W153G](TASK-W153G-R5-AND-R1-MATRIX.md) and the
W153G section of `records/W153.md` on the branch, with its evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W153G/`
(the complete GameTest log and checker output) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w153` |
| Branch | `codex/v1.8.0-w153` at `c25058c6` (local commits only; Root merges) |
| Record | append a "W153H" section to `docs/work/v1.8.0-root-queue/records/W153.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W153H/` |

## The failures

W153G's complete GameTest run at `c25058c6` had 1,333 of 1,340 tests passing. W153F's run at `669c9b57` passed all
1,338, and hosted CI on the shared branch is green. The seven failed tests are unchanged:

1. `concurrentPlayerLaunchesReserveDistinctPadsAndLandAfterCallbackOverflow`: timeout at 1,200 ticks.
2. `oldCopyLoadedAfterRetirementStartsIsDiscardedBeforeProof`: post-fill launch refused, `LANDING_PAD_UNAVAILABLE`.
3. `borderCrossingMarginsLandOnAnEmptyMoonAndARestartReticketsTheSameColumns`: timeout at 3,000 ticks.
4. `aDiscardAfterPublicationReleasesOnlyTheWarmupAndTheFlightLands`, 5. `theCapAndThePerPlayerLimitRefuseColdDestinationsAndEveryWarmupEnds`,
   6. `aStaleCopyThatTheWarmupLoadsIsRefusedByF1AndKept` and 7. `aWarmupKeepsItsPadThroughReservationChurnAndHandsOverAfterAPlayerRelaunch`:
   the Moon cannot become cold, because earlier player or start tickets remain (one on column 4,0).

Root's working hypothesis, to confirm or refute: state left over from the W153 branch's new monitor GameTests (W153G's
two membership cases, or W153F's offline warm-up, powered and cold-arrival fixtures). That could be a flight that
lands on the Moon, a retained ticket or warm-up, or a pad reservation that outlives its test. The other candidate:
the new tests changed the GameTest batch order, which follows HashMap order, so existing tests now meet that state.
Also check whether R2's production change (`62b24a06`, offline-owner warm-ups) can leave a ticket that a player flight
inherits.

## What to do

1. From the log, find which test left the Moon tickets and pad state, and when. Use the batch and test order, the
   ticket and warm-up lines, and an isolated rerun of the candidate test followed by one failing test.
2. Fix the cause where it is. For a test leak, clean up in the new test that left the state. For a production defect,
   fix it with a red-green unit test, and report a production behaviour change in the record. Do not change the
   seven failing tests' assertions, timeouts or preconditions.
3. Run the complete GameTest with the unchanged log checker until it passes, and the whole unit suite. If a failure
   still reproduces and you cannot tie it to this branch, stop and report it with evidence.

Heavy runs go under the heavy-JVM lock with the atomic pattern and the 90-second hand-off rule. Check C: and D: above
10 GiB and 6 GiB free memory first.

## Not in this task

New features, the review findings already fixed, ADRs, the risk register, status files and the ledger (Root).
