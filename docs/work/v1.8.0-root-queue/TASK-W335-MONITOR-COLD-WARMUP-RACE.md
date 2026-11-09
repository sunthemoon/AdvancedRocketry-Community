# TASK W335: the monitor's cold warm-up check runs before the warm-up starts

Root: Claude. Date: 2026-11-09. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w335`, branch `codex/v1.8.0-w335`, created by Root from the shared head |
| Decision | Root decision R403 (QUEUE row W335) |
| Record | `docs/work/v1.8.0-root-queue/records/W335.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W335/` |
| Failing log | W330's full local GameTest run: `D:/GitHub/ARCE-Task-Evidence/root-queue/W330/full-gametest-latest.log`, around line 10202 |

## The failure

W330's full local GameTest run failed one test it did not touch:

```text
offlinemonitorwarmsacolddestinationandlaunchesonlyonafreshedge failed! Offline owner lost its attributed warm-up
```

The test is `EscrowMonitorGameTests.offlineMonitorWarmsAColdDestinationAndLaunchesOnlyOnAFreshEdge` (batch
`monitor_governed_cold`). After the cold launch reports `DESTINATION_LOADING`, step 10 asserts on the next tick that a
governed warm-up exists and is attributed to the offline owner. In the failing run the log shows:

- `ARCE_ROCKET_TERRAIN_PREPARATION ... event=STARTED age=0` at 20:31:47.038;
- the failure at 20:31:47.125;
- `ARCE_ROCKET_TERRAIN_PREPARATION ... event=SELECTED age=2` and `ARCE_GOVERNED_WARMUP event=STARTED` at 20:31:47.351.

So the warm-up began two ticks after preparation started, after the test had already looked for it. Root's reading:
the test assumes the warm-up exists on the tick after `DESTINATION_LOADING`, but production starts it only once
terrain preparation selects a pad, which can take more than one tick depending on what earlier tests left loaded. That
makes the test order-dependent. Confirm or correct this reading before changing anything.

## What to do

1. **Establish the contract.** From the production code and the design records, decide what `DESTINATION_LOADING`
   promises: that a warm-up already exists, or that terrain preparation has started and the warm-up follows. Cite the
   code. If `DESTINATION_LOADING` promises an existing warm-up and production breaks that promise, stop and report it
   as a production finding; do not change production code in this task.
2. **If the test's assumption is wrong**, fix the test so that it checks what the contract promises:
   - while this request's terrain preparation is pending and no warm-up exists, wait, within the test's existing
     timeout;
   - once a warm-up exists, it must be attributed to the offline owner, exactly as now;
   - fail if a warm-up for this rocket is ever attributed to anyone else, if preparation ends without a warm-up, or if
     the held edge launches or debits fuel, as now.
   Keep the test's meaning, its other assertions and its timeout unchanged. Say in the record why this is not a
   loosened assertion.
3. **A regression for the timing.** Show that the fixed test passes when the pad selection is delayed (for example by
   keeping the destination columns unloaded for a few more ticks in a scratch copy), and that it still fails when the
   warm-up is attributed to another player. Discard any scratch copy.
4. **Check the siblings.** Other monitor and governed warm-up tests may make the same next-tick assumption (search
   for `warmup(` and `DESTINATION_LOADING` in the GameTests). List them and fix only those with the same weakness.

## Verification

- The monitor batches alone, with `JAVA_TOOL_OPTIONS=-Dadvancedrocketrycommunity.gameTestBatches=...`, repeated a few
  times.
- `./gradlew build` with every unit test, then the full `runGameTestServer` with `scripts/check_gametest_log.py`, under
  the heavy-JVM lock (backslash lock path, as WORKER-RULES says). Other workers share the lock, so take it per step and
  release it between steps. Call Python by its full path in any detached script.
- `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews and merges.

## Not in this task

Production code; other failing tests unless they share this weakness; the risk register, the status file and QUEUE.
