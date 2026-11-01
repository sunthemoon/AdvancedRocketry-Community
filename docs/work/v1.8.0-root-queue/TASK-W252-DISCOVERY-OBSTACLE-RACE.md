# TASK W252: the discovery recovery GameTest's pending-file obstacle

Root: Claude. Date: 2026-11-02. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w252` (new; branch `codex/v1.8.0-w252` from the shared head named in your prompt) |
| The failure | W246's branch run 41341199945, attempt 3, shard 0: `oldpaidreceiptandnewremovedtargetrecoverwithoutanotherclaim failed! ./world/./data/advancedrocketrycommunity_satellite_missions.dat.arce-pending`. The log is `D:/GitHub/ARCE-Task-Evidence/root-queue/W246-fix3/attempt3/run41341199945-shard0.log`; attempts 1 and 2 passed the same test |
| Background | [TASK-W194](TASK-W194-SATELLITE-PENDING-FILE-RACE.md) (a fixture competing with the runtime's writer for the same file), [ADR-074](../../decisions/ADR-074-OFF-THREAD-REGISTRY-AND-TRANSACTION-WRITERS.md) |
| Record | `docs/work/v1.8.0-root-queue/records/W252.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W252/` |

**Root decision R275.** `DiscoveryRecoveryGameTests` (`satellite/service`, around line 70) creates a directory at the
missions file's `.arce-pending` path to make the next write fail on purpose. The message is only that path, which
fits `Files.createDirectory` failing because the path already exists: the registry's off-thread writer was probably
still writing an earlier save.

1. **Confirm the cause** from the log and the code (which write was in flight, and from where).
2. **Fix it on the test side** if that is the cause: drain the writer (for example `missions.awaitForTests(server)`)
   before placing the obstacle, and fail clearly if the path is still occupied. Apply the same to any other test
   that places such an obstacle (for example `gametest/SatelliteReviewGameTests.java`, around line 88) if it has the
   same gap. If the cause is in production code, stop and report: that needs a design note first.
3. Keep every assertion, the expected save failure and every timeout.

## Verification

- The affected batches focused and repeated (at least five runs) under the heavy-JVM lock, before and after.
- The whole unit suite if any class changes outside GameTests, and the repository validator.

Commit on the branch; do not push. Root pushes for a sharded run and reviews the diff.

## Not in this task

The registry writer, ADR-074, other tests, status files and the ledger.
