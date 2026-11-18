# TASK W385: arrival readiness in GameTests that use a detached transfer service

Root: Claude. Date: 2026-11-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w385`, branch `codex/v1.8.0-w385`, at the shared head (W383 merged as `005acb5e5`) |
| Decision | Root decision R500 (QUEUE row W385) |
| Prior work | [W383's record](records/W383.md), sections "Which component started the recovery", "The fix" and "Not done" |
| Record | `docs/work/v1.8.0-root-queue/records/W385.md` |
| Write scope | The GameTest classes listed below and their fixture helpers, and the record. No production code, no log expectation, no bound, timeout or retry. |

## Why

W383 found that a GameTest which prepares a transfer with a detached `new RocketTransferService()` depends on the
arrival not waiting. If one column of the arrival's ticket set still loads entities, the arrival holds at its PREPARED
wait (`ARCE_ARRIVAL_ENTITIES_LOADING ... columns=n/m`). The runtime's own service does not treat the detached record
as live, so its journal recovery takes it over and logs an undeclared `ARCE_TRANSFER_RECOVERY` WARN. The test's own
assertions often still pass, and only the log check fails. W383 fixed `LegacyLandingPadOccupancyGameTests` by waiting
for every column of the arrival's ticket set (commit `94ca9c2f1`).

## What to do

1. **Audit each of these classes** for every transfer it drives through a detached service:
   - `LandingPadGameTests` (W383 names its waits at lines 155, 198 and 234);
   - `NamedLandingPadOccupancyGameTests`;
   - `RocketDestinationReadinessGameTests`, `RocketConcurrentFlightGameTests`, `RocketArrivalFootprintGameTests`;
   - `RocketFailBackAdmissionGameTests`, `RocketFailBackHeldWriterGameTests`;
   - `RocketLiveReconnectGameTests`, `RocketReconnectSchedulingGameTests`;
   - `RocketRetiredPadGameTestProbe`, `RocketSourceRetirementGameTests`, `RocketTransferBayGameTests`.

   For each transfer, decide whether the fixture's readiness wait covers the arrival's whole ticket set
   (`RocketTransferEntities.destinationTicketSet`, which follows `RocketDiscoveryColumns`). A test that expects the
   arrival to wait, or expects a recovery, is correct as it is; say so with the line.
2. **Fix each gap in the same way as W383.**
   - Wait for every column of the ticket set, with the existing bound unchanged.
   - Assert before the step that depends on it that the readiness covers the prepared record's own ticket set.
   - Where the test's assertions would also hold after a recovery, add an assertion that names the expected path.
3. **The open item "Concurrent-flight fixture wait"** in `docs/status/CURRENT_VERSION.md`: say whether
   `RocketConcurrentFlightGameTests.pendingSpawnDoesNotPauseAnotherCountdownAscentOrTransit` has this exposure, from
   the code and W378's diagnostics. Do not change the diagnostics.
4. **Report** a table: class, test, transfer, the columns waited for, the ticket set, gap yes or no, and the fix
   commit.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Use `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`. Use the heavy-JVM lock with the 90-second gap, keep at least
  10 GiB free on C: and D:, and delete GameTest worlds after each run.
- Keep evidence under `D:/GitHub/ARCE-Task-Evidence/root-queue/W385/`.
- Run the focused batches of the changed classes, and one full GameTest run with the log checker.
- Commit on the branch in reviewable commits, one per class or close group, with messages in the style of AGENTS.md
  section 6. Do not push.
- The last line of the record, and the last line of your answer, is the path of `records/W385.md`.
