# TASK W240: the adapter-fault mismatch test that stalls in DISASSEMBLY_PENDING

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w240` (new; branch `codex/v1.8.0-w240` from the shared head named in your prompt) |
| Record | `docs/work/v1.8.0-root-queue/records/W240.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W240/` |
| Failure | W237's sharded branch run 41261374700 on `10f50015c`, shard 0 (job 124717797365, log `D:/GitHub/ARCE-Task-Evidence/root-queue/tools/job-124717797365.log`, line 4874). Batch `w164_adapter_fault` started at 01:46:18; at 01:48:59, `AdapterFaultGameTests.mismatchedRestoreCleansPartialInventoryAndRetries` failed with "Unfinished adapter transition: DISASSEMBLY_PENDING". The other shards passed. W237 changes only the log checker and `build.gradle`'s post-run check, so it cannot cause this. Root re-ran all jobs |
| Sources | `src/adapterTest/.../AdapterFaultGameTests.java` (its `RestoreTrial` step machine and the RESTORE_MISMATCH mode) and `FixtureAdapterFaults`; W225-fix3's changes to the same batch (`records/W225.md`, R220) |

**Root decision R249.** This is the first hosted failure of this test, and it falls under the local-run watch item in
QUEUE: a recurrence in a hosted run becomes a task.

1. Find why the mismatch trial never leaves DISASSEMBLY_PENDING within its timeout. For example: the restore
   callback is never invoked, the mismatch path never schedules the retry, a dependency on another trial in the same
   batch, a tick-count or async wait, or a neighbour's state. Reproduce it if you can: run the batch alone and next to
   its shard-0 neighbours, in the hosted order, several times.
2. Fix the cause in the fixture if it is a fixture defect. If it is a product defect in the adapter restore path,
   stop at a failing test and a record entry, and say so.

Do not:

- remove or weaken an assertion;
- raise a timeout or budget;
- move a test to another batch.

## Verification

- The changed tests, failing first where reproduced.
- The batch ten times under the heavy-JVM lock, alone and in the hosted order.
- The Python log-check suites.

Root pushes; a sharded branch run qualifies the result.

## Not in this task

Other adapter tests, status files and the ledger.
