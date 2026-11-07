# TASK W322: the bay matrix pending-spawn GameTest races the transfer service

Root: Claude. Date: 2026-11-08. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w322`, branch `codex/v1.8.0-w322`, created by Root from the shared head |
| Decision | Root decision R381 (QUEUE row W322) |
| Background | `BayDeploymentMatrixGameTests.aRefusedCheckpointRequestPreservesPackagesWhileDestinationSpawnAwaitsAcknowledgement` (added by W298) failed with `The pending-spawn regression missed PREPARED source authority` once in W288's loaded local full run (2026-11-05, open in the status file) and again in shard 3 of the shared run 42403168945 at `b0b576620` (log: `D:/GitHub/ARCE-Task-Evidence/root-queue/shared-runs/run42403168945-attempt1-shard3.log`), while the same tree passed in W321's branch run 42400089907 |
| Record | `docs/work/v1.8.0-root-queue/records/W322.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W322/` |

## The failure

`BayDeploymentMatrixGameTests.queuePendingSpawn` (around lines 241 to 261) asserts that the transfer is still
`PREPARED`, then calls `spawnDestination` itself to observe the unacknowledged destination-spawn window with a live
source. The installed transfer service advances the same transfer on its own server tick once the destination chunks
are ready. When the service gets there first, the fixture finds the transfer past `PREPARED` and fails, although the
product behaved correctly.

## What to do

1. Confirm this race from the log, or reproduce it (for example by delaying the fixture by a tick or loading the
   destination chunks early).
2. Make the fixture own that step deterministically:
   - hold the service's own advancement of this one transfer while the fixture drives the destination spawn, through a
     narrow test-only gate if one exists or a minimal one added for tests;
   - or observe the service's own spawn window at the moment it opens.

   The assertions stay unchanged: PREPARED before the spawn, then `DESTINATION_SPAWNED` with an unpublished destination
   and a live source. The timeout stays unchanged.
3. Check the sibling tests in `BayDeploymentMatrixGameTests` and the other bay matrix and escrow fixtures that call
   `spawnDestination` themselves for the same race, and treat any you find the same way, or list them.
4. If the cause turns out to be in production code, stop and say so in the record first.

## Verification

- The reproduction before the change and the same run after it.
- The bay deployment matrix batches several times, and the bay and escrow batches once.
- `./gradlew build` with all unit tests.
- The full GameTest run with `scripts/check_gametest_log.py`, under the heavy-JVM lock (backslash lock path, as in
  WORKER-RULES).
- `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

Transfer, bay and escrow behaviour, other intermittent tests, the ledger, the status file and QUEUE.
