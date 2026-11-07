# TASK W318: GameTests that leave stations behind

Root: Claude. Date: 2026-11-07. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w318`, branch `codex/v1.8.0-w318`, created by Root from the shared head |
| Decision | Root decision R369 (QUEUE row W318) |
| Background | `records/W315.md` (on W315's branch; merged soon): a station that `EscrowMonitorGameTests.stationMembershipRevokedAfterPreparedLaunchesAndInvalidatesArming` left in grid cell (0, 1) broke the sleep-in-vacuum test about 7,300 ticks later. W315 named five tests that create a station and never delete it |
| Record | `docs/work/v1.8.0-root-queue/records/W318.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W318/` |

## The tests

- `EscrowGovernedWarmupRefusalGameTests.aNamedPadClaimIsReleasedWhileWarmingReservedForOthersAndClaimedByTheRelaunch`
- `EscrowMonitorGameTests.stationMembershipRevokedBeforePreparedRefusesWithoutLaunchOrDebit`
- `EscrowMonitorGameTests.offlineMonitorWarmsAColdDestinationAndLaunchesOnlyOnAFreshEdge`
- `EscrowMonitorGameTests.stationMembershipRevokedAfterPreparedLaunchesAndInvalidatesArming`
- `EscrowGovernedWarmupGameTests.theCapAndThePerPlayerLimitRefuseColdDestinationsAndEveryWarmupEnds`

## What to do

1. Confirm that each test leaves its station in the registry after it ends (pass or fail). Search the other GameTests
   for the same pattern (a station created without a matching removal) and list any you find.
2. Make each test remove what it created when it ends, on success, failure or timeout. Remove the station, its notices
   and its platform template the way existing fixtures do (for example `StationWarpGameTests.Fixture.close`), only
   after the test's own transfer records, pad claims and warmups are settled, so that later batches see neither the
   station nor a record that names it.
3. The assertions, budgets and timeouts stay unchanged. If a station cannot be removed cleanly because production keeps
   a reference that a test cannot settle, stop for that test and say what holds it.
4. Add an end-of-run check if a cheap one fits: after the batches above, no station created by them remains.

## Verification

- The five tests and their batches, before and after, showing the station is gone afterwards.
- The full GameTest run with `scripts/check_gametest_log.py`, under the heavy-JVM lock. Keep the backslash lock path
  from WORKER-RULES.
- `./gradlew build` with all unit tests.
- `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

Station, escrow and warmup production code, the sleep tests (W315), the ledger, the status file and QUEUE.
