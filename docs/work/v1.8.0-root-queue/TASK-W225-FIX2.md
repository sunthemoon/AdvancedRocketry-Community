# TASK W225-fix2: the observer's pack metadata and one cross-batch WARN in the sharded run

Root: Claude. Date: 2026-10-31. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w225` (W225's; continue on its branch) |
| Branch | `codex/v1.8.0-w225`, now at `d0692060a` (pushed). Local commits only; Root pushes and merges |
| Record | `docs/work/v1.8.0-root-queue/records/W225.md`: append "R218 hosted-run corrections" |
| Hosted run | 41186698031 on `d0692060a`; the four shard logs are in `D:/GitHub/ARCE-Task-Evidence/root-queue/W225-hosted/` (`job-124497458642.log` shard 0, `job-124497458605.log` shard 1, `job-124497458616.log` shard 2, `job-124497458595.log` shard 3) |

**Root decision R218.** The second sharded run built once (12.2 minutes) and ran all 1,557 GameTests in four shards
(330, 570, 317 and 340 tests, 15.2 to 19.9 minutes each), every test passing, but every shard's log check failed:

1. **All shards:** two unexpected startup WARNs, "Missing metadata in pack mod:arce_ci_gametest" and "Missing data pack
   mod:arce_ci_gametest" (and the first again at each data-pack reload in shard 3's `datapack_overlimit` batch),
   because the CI-only observer mod ships no `pack.mcmeta`. Give the observer mod correct pack metadata for Minecraft
   1.20.1 so that vanilla logs nothing about it; do not add these lines to the manifest.
2. **Shard 1:** "WARN of w132-item-cargo-named-hold in an undeclared batch, batch 'bay_registered_unavailable'". The
   new batch order put `bay_registered_unavailable` right after the batch that owns this expectation, and the WARN was
   logged after its own batch had ended. Find which test emits it and why it lands after its batch (asynchronous work,
   a scheduled callback, a save), then fix it so that the WARN is attributed correctly in any batch order: preferably
   the fixture waits for its own work to finish before its batch ends; or, if the WARN is legitimately emitted later,
   a precise attribution rule in the checker for that one expectation, with a test that fails first and a statement
   in the record of why it is not a loosening. Search the four logs for any other expectation that could spill over in
   a different order, and say what you find.

## Verification

The changed tests alone, failing first where meaningful; all Python suites the workflow runs; the relevant GameTest
batch locally under the heavy-JVM lock if the fixture changes (three times, in an order that puts
`bay_registered_unavailable` right after it). Root pushes and the hosted run qualifies the result.

## Not in this task

Other workflow changes, test assertion, timeout or budget changes beyond the one fixture, status files and the ledger.
