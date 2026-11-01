# TASK W247: the K4 rotation GameTests' server-thread stalls

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w247` (new; branch `codex/v1.8.0-w247` from the shared head named in your prompt) |
| Record | `docs/work/v1.8.0-root-queue/records/W247.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W247/` |
| Evidence | Two hosted shard-2 logs that passed every test but failed the per-shard host-lag ceiling (R236): `D:/GitHub/ARCE-Task-Evidence/root-queue/W241/shared-run41292655524-shard2-attempt1.log` (10 `server-overloaded` entries) and `D:/GitHub/ARCE-Task-Evidence/root-queue/W244-fix/branch-run41307843635-shard2-attempt1.log` (14). Before K4's merge, shard 2 counted 6 or fewer |
| Suspects | W241-fix's rotation GameTests (`gametest/K4ModelGameTests.java`, the batches `w241_orbital_rotation`, `w241_precision_rotation` and `w241_black_hole_rotation`, and any other `w241_*` batch): stalls of 10.3 to 14.3 seconds follow them, and 11.5 to 24.6 seconds follow the batches next to them (`station_warp_deop`, `w133_escrow_fill_launch`). They form machines in four facings in distant regions (about x = -2,410,000) through `K3ModelTestRegion` |

**Root decision R266.** The K4 rotation tests appear to stall the server thread, probably by loading or generating
fresh chunks for their distant regions. Find out and fix it on the test side.

1. **Measure first.** Run the K4 model batches alone and next to `station_warp_deop` and `w133_escrow_fill_launch`
   in focused local runs under the heavy-JVM lock, and attribute the stall: chunk generation or loading, region
   ticket churn, saving, entity work or something else (JFR or tick timing, as W212 did). Compare with K3's model
   tests, which use the same helper.
2. **Fix the fixtures**, for example by reusing one region per test across its facings, building the facings one
   after another in the same loaded chunks, or placing them where chunks already exist. Keep every test out of other
   tests' forced template chunks (W233's lesson), keep every assertion, and keep the rotation coverage of all four
   facings. If the helper is shared with K3, keep K3's tests passing unchanged.
3. **Show it.** The before and after `server-overloaded` counts and stall sizes for the batches involved, from the same
   focused runs, and one full GameTest run with the log checker.

No change to the host-lag ceiling, ADR-072, any timeout, the batch order mechanism or product code.

## Verification

- The K-model unit tests and the whole unit suite.
- The focused before and after runs and one full GameTest run under the heavy-JVM lock.
- The repository validator.

Commit on the branch; do not push. Root pushes for a sharded run and reviews the diff.

## Not in this task

Other batches' lag, the bay exit, the import batches, status files and the ledger.
