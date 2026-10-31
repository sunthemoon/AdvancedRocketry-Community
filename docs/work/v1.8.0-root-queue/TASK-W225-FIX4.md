# TASK W225-fix4: the beacon reload fixture's entity readiness in shard 1

Root: Claude. Date: 2026-10-31. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w225` (W225's; continue on its branch) |
| Branch | `codex/v1.8.0-w225`, now at `0e4df9b6a` (pushed). Local commits only; Root pushes and merges |
| Record | `docs/work/v1.8.0-root-queue/records/W225.md`: append "R230 beacon readiness" |
| Hosted runs | Fourth sharded run 41211528081 on `0e4df9b6a`: the build and shards 0, 2 and 3 passed (14.9 to 20.2 minutes); shard 1 failed one test. Its log is `D:/GitHub/ARCE-Task-Evidence/root-queue/tools/job-124570077053.log` (failure at line 5855). The third run's shard 1 failed the same test: `job-124511316018.log`. The second run's shard 1 passed it: `D:/GitHub/ARCE-Task-Evidence/root-queue/W225-hosted/job-124497458605.log` |

**Root decision R230.** W225-fix3 fixed three of R220's four failures; the fourth run confirms them. The fourth
failure came back:

`BeaconReloadGameTests.unloadAndPendingReloadKeepTheActiveRecordAndFinderMarker`: "Fixture chunk is not ticker-ready:
status=ENTITY_TICKING, entities=false, blockTick=true".

It happened in 2 of the last 3 shard-1 runs. In the fourth run, batch `beacon_native_reload` started in the same
second as `w01g_partial_resources_root`. Another test's player (`jackhammerTest`) had just logged in. The fixture's
existing `awaitTickReady` already pumps entity loading (`EntitySections.loadNow`) for 40 attempts, and still saw no
loaded entities.

Find out why the fixture chunk's entities do not load in time in this order. Candidates include another test
holding or unloading the same chunk, the entity section never being requested, the pump not reaching the task that
loads it, and the test positions overlapping. Compare the passing and failing logs, and reproduce the order locally
under the heavy-JVM lock.

Fix the cause:

- in the fixture, if the test waits for the wrong thing;
- in the neighbour, if it leaves state behind;
- in the product, if it never loads the entities. If it is a product defect, stop at a failing test and a record
  entry, and say so.

Do not:

- remove or weaken an assertion;
- raise a timeout or the 40-attempt budget;
- move the test to another batch.

Also state why W225-fix3's earlier change to this class did not cover it.

## Verification

- The changed tests alone, failing first where you can reproduce the failure.
- The `beacon_native_reload` batch together with its shard-1 neighbours, in the failing order, ten times under the
  heavy-JVM lock.
- The Python suites the workflow runs.

Root pushes; the hosted run qualifies the result.

## Not in this task

Workflow or partition changes, other tests, status files and the ledger.
