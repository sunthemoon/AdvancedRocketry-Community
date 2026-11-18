# TASK W383: the legacy pad occupancy recovery path

Root: Claude. Date: 2026-11-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w383`, branch `codex/v1.8.0-w383`, at the shared head |
| Decision | Root decision R494 (QUEUE row W383) |
| Open item | `docs/status/CURRENT_VERSION.md`: "Legacy pad occupancy recovery path (2026-11-18)" |
| Record | `docs/work/v1.8.0-root-queue/records/W383.md` |
| Write scope | `LegacyLandingPadOccupancyGameTests` and its support, the transfer, arrival and recovery code on the failing path only if you prove a defect there, their unit tests, exact entries in `scripts/gametest_expected_log.json` if a diagnostic adds a log line, and the record. |

## The failure

Shared run 44254384368, attempt 1, shard 0, passed all of its 399 GameTests, but its log check failed. The relevant
test is `LegacyLandingPadOccupancyGameTests.wideParkedStructureAppearingAfterPreparationRefusesUnnamedPreSpawn`, in
batch `legacy_pad_occupancy`, with six tests.

- 07:14:06.258: `ARCE_TRANSFER_PREPARE transfer=95b617b0...` (overworld to space) and
  `ARCE_ARRIVAL_ENTITIES_LOADING ... columns=3/4`.
- At the same time, `ARCE_PAD_OCCUPANCY_REFUSED reason=occupied` names the parked structure.
- 07:14:06.358, two ticks later: `ARCE_TRANSFER_RETIREMENT_PROVED site=RECOVERED_SOURCE`, then
  `ARCE_TRANSFER_RECOVERY ... phase=PREPARED source_count=1 destination_count=0 action=KEEP_SOURCE status=RECOVERED`.
- The expected `ARCE_TRANSFER_RETURNED_TO_SOURCE ... reason=destination_pad_blocked` never came.
- The rocket ended at its source, so the test's own assertions passed. The batch ended one tick later.

The same log failure appeared once before, in W301F's local full run on 2026-11-05. W381's changed batch
`flight_recovery` ran later in that shard, so W381 is not the cause.

Evidence:

- the shard log: `D:/GitHub/ARCE-Task-Evidence/root-queue/shared-ci/run44254384368/shard0-attempt1.log`, around
  lines 6588 to 6760;
- W301F's checker output: `D:/GitHub/ARCE-Task-Evidence/root-queue/W301F/final/full-check.log`, and the log beside it
  if one is kept.

## What to do

1. **Establish which component started the recovery** two ticks after the preparation, and why.
   - Candidates include:
     - the journal recovery pass of the server's `RocketManager`;
     - a recovery triggered by an entity join or unload;
     - the batch cleanup of the GameTest framework;
     - another test of the same batch whose cleanup recovers every PREPARED transfer.
   - Read the test, the arrival's PREPARED wait and the recovery entry points.
2. **Decide whether two paths can race for one PREPARED transfer in production.** The two paths are the ordinary
   return to source with `destination_pad_blocked` and a journal recovery with `KEEP_SOURCE`.
   - Both leave the rocket at its source.
   - Check that neither can leave a duplicate or a lost rocket, or change fuel or cargo.
   - If production can race, write a unit test or GameTest that shows it.
3. **Try to reproduce.** Run the batch repeatedly under the heavy-JVM lock, with and without CPU load. Use the repeat
   harness W381 kept in `D:/GitHub/ARCE-Task-Evidence/root-queue/W381/`. Bound the repetitions, for example 30.
4. **Then one of three outcomes.**
   - **A test cause.** The test or a neighbour leaks state into the transfer. Fix it in the test, pinned.
   - **A production defect.** Fix it on the failing path, with a test that fails without the fix. No other save,
     network or admission behaviour changes.
   - **Neither is established.** Add failure-only diagnostics that name the component that started the recovery.
5. **Never loosen the log expectation, add a retry or raise a bound.**

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Use `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`. Use the heavy-JVM lock with the 90-second gap, keep at least
  10 GiB free on C: and D:, and delete GameTest worlds after each run.
- Keep evidence under `D:/GitHub/ARCE-Task-Evidence/root-queue/W383/`.
- Run the touched classes' unit tests, and one full GameTest run with the log checker.
- Commit on the branch in reviewable commits, with messages in the style of AGENTS.md section 6. Do not push.
- The last line of the record, and the last line of your answer, is the path of `records/W383.md`.
