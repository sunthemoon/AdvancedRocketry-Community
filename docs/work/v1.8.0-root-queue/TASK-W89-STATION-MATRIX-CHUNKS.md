# TASK W89: the station authority matrix fails after the W11b2 batch order changed

Root: Claude. Date: 2026-10-17. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w89` (created by Root at the integrated W11b2 head `2ed7f18e`) |
| Branch | `codex/v1.8.0-w89` (local commits only; Root pushes and merges) |
| Subject | `StationPermissionMatrixGameTests.everyCellOfTheStationAuthorityMatrixMatchesTheTable` (batch `station_permission_matrix`) |
| Record | `docs/work/v1.8.0-root-queue/records/W89.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W89/` |

This blocks the W11b2 merge: the hosted regression must pass on the integrated head. It has priority over other
parallel bugs; W11b2's own workers (continuations 48 and 49) also use the heavy-JVM lock.

## What is known

The test passed in every earlier run. It now fails deterministically on the integrated W11b2 head, in two separate
runs with the same numbers:
- hosted run 39017567495 on `2ed7f18e`, job 117352716777 (log line: `everycellofthestationauthoritymatrixmatchesthetable
  failed! 2 cells differ: [ADMIN_CREATE_DELETE/OPERATOR: chunks changed {... space=26}, actual REACHED,
  ADMIN_CREATE_DELETE/CONSOLE: chunks changed {}, actual REACHED]`);
- continuation 46's full GameTest, `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C46/final-gametest-latest.log`,
  lines 1365 to 1425: both failing cells log `cell_full_chunks=27`.

The matrix allows an operator create/delete to load at most 2 x 2 full chunks in the new station's region
(`chunksAllowed`, `fullChunks`), and counts the full chunks in that region **in absolute terms**. In both failing runs
the batches just before it were `station_warp_logout`, `escrow_governed_rest_hold`, `landing_pads` and
`precision_migration_port_failure`. W11b2 continuation 46 added new batch names, which reorders the run (batch order is
HashMap order; see the 2026-10-16 note on the order threshold in the QUEUE's machine operations, and W68).

## What to do

1. Find the cause: which earlier test leaves 27 full Space chunks in the region the matrix's new station receives, and
   why that region (station region allocation and reuse after delete, the earlier test's tickets, players or forced
   chunks, and when they are released). Confirm with the batch filter `advancedrocketrycommunity.gameTestBatches`
   (development-only) or a unit test, and compare with a passing log from before the reorder (for example the hosted
   run 39015130819 on `809e5fdb`).
2. Fix the real cause. If an earlier test leaks chunk tickets or loaded state, make it release them. If the matrix's
   measurement is wrong because it counts chunks another test loaded, measure what the cell itself loads (for example
   the full chunks in the region that were not loaded before the cell), keeping the same limit of 1 to 4 full chunks
   for this cell and the same no-change rule for every other cell. Under AGENTS.md section 5 you must not delete or
   weaken an assertion, extend a timeout, skip the test or move it to another batch to dodge the order.
3. Verify: your new or changed unit tests, the whole unit suite once, and one full `runGameTestServer` with the
   unchanged log checker, under the heavy-JVM lock. If you cannot reproduce or explain it, stop after step 1 and report
   what you found, with evidence.

Write a short record (cause, evidence, fix, commands and results) and commit the fix and the record in separate
commits.

## Write scope and boundaries

`StationPermissionMatrixGameTests` and the station or landing-pad GameTests and fixtures involved, the station region
allocation only if the cause is there (record why), their unit tests, and the record. No change to escrow or rocket
code, the log checker or its expectations, or central files (registries, build scripts, workflows, status files, the
ledger).

Never remove a lock you do not own. Check that C: and D: have more than 10 GiB free before a GameTest run. Remove your
own GameTest world afterwards; if the tool policy refuses it, list the exact path in the record for Root and continue.

## Not in this task

Any W11b2 or escrow work; other intermittent tests; performance budgets.
