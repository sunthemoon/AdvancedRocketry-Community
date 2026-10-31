# TASK W225-fix3: four cross-test interference failures the shard order exposed

Root: Claude. Date: 2026-10-31. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w225` (W225's; continue on its branch) |
| Branch | `codex/v1.8.0-w225`, now at `18d7ad291` (pushed). Local commits only; Root pushes and merges |
| Record | `docs/work/v1.8.0-root-queue/records/W225.md`: append "R220 shard-order interference" |
| Hosted run | 41191582633 on `18d7ad291`. Logs: `D:/GitHub/ARCE-Task-Evidence/root-queue/tools/job-124511315921.log` (shard 0), `job-124511316018.log` (shard 1), `job-124511315928.log` (shard 2). The second run's four passing shard logs are in `D:/GitHub/ARCE-Task-Evidence/root-queue/W225-hosted/` |

**Root decision R220.** The third sharded run built once (10.1 minutes) and ran the four shards (15.2 to 19.5
minutes). Shard 3 passed and every log check passed where reached, but three shards failed four tests. Shard 0 ran its
159 batches in exactly the same order as in the second run, which passed, so these are timing-dependent interferences
between tests that the per-shard order now places next to each other or runs together, not deterministic failures:

1. **Shard 0:** `AdapterFaultGameTests.throwingRestoreCleansPartialInventoryAndRetries` (batch `w164_adapter_fault`):
   "Duplicate inventory drops found: [16 cobbled_deepslate at (24.89, -59.71, 3.42) age=33]". The batch right before
   it is `endgame_destruction_blast`, whose test sets off power-6 TNT explosions twelve blocks above the same test
   position. Root's unverified hypothesis: the blast's block drops fall into the next batch's region after the
   fixture's `create` step has discarded the drops already present.
2. **Shard 1:** `unloadAndPendingReloadKeepTheActiveRecordAndFinderMarker`: "Fixture chunk is not ticker-ready:
   status=ENTITY_TICKING, entities=false, blockTick=true".
3. **Shard 2:** `borderCrossingMarginsLandOnAnEmptyMoonAndARestartReticketsTheSameColumns`: "Another ticket keeps the
   margin entity-ticking: Ticket[arce_rocket_flight 31 (d325b0aa-…)]".
4. **Shard 2:** `fiveLoadersFillOneRocketAndTheSixthIsRefusedWithNothingChanged`: "Fill 0 was not admitted:
   TRANSFER_JOURNAL_UNPROVED".

For each failure, use the logs and the code to find which other test, or which timing, produces the state. Then fix the
cause so that the test no longer depends on batch order or neighbours. Prefer, in this order:

- the test that leaves state behind cleans it up before it succeeds (drops, tickets, journal or transfer state);
- the fixture waits, within its existing timeout, for its own precondition before it acts. Entity loading runs as a
  server task, so a chunk can be visible before its entities are loaded (see `records/W122.md`).

If a product defect appears rather than a fixture defect, stop at a failing test and a record entry, and say so.

Do not:

- remove or weaken an assertion;
- raise a timeout or budget;
- skip a test, or move it into another batch, to hide the interaction.

Also search the three failing shard logs of this run and the four shard logs of the second run for other ERROR lines,
failures or leftovers of the same kinds, and list what you find.

## Verification

- The changed tests alone, failing first where you can reproduce the failure (for example, by running the two
  batches in the failing order).
- The Python suites the workflow runs.
- The relevant batches locally under the heavy-JVM lock, three times each, in the failing order.

Root pushes; the hosted run qualifies the result.

## Not in this task

Workflow or partition changes, status files and the ledger.
