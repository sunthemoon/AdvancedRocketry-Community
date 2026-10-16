# TASK W87F: terrain drops from the W68 rig's room carving fail a GameTest on W87's batch set

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. Do not start
subagents.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w87-client-findings` (branch `codex/v1.8.0-w87-client-findings`, at `4d6ae3c5` plus this task) |
| Failure | Hosted run 38848383766, both attempts, at `4d6ae3c5`: `pipeSealsForwardItemsWaterAndFeWithoutKeepingAny` (batch `w68_pipe_seal`) failed with "Seal automation spilled items into the world: 1 rail at 243, -59, 148". Every other test passed. The shared branch passes it; W87's two new batches changed the batch order and so the placement |
| Record | `docs/work/v1.8.0-root-queue/records/W87.md`: add a "W87F" section. Keep the existing text |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W87F/` |

## Root's analysis (check it; do not assume it)

No code, recipe, loot table or template in this mod produces a rail, so the rail is world terrain (for example a
mineshaft: these GameTests run underground in generated terrain, as `AutomationRig.room` says). `room` carves its air
room and stone shell with `Block.UPDATE_CLIENTS`, but `BaseRailBlock.onRemove` updates its neighbours regardless of
the flags. A rail whose support was carved first then fails `canSurvive` in `neighborChanged` and drops itself. A drop
in the shell cell lies outside the box that `room` clears after carving, is pushed out of the stone into the room, and
the spill check then counts it.

## What to do

1. Confirm or correct the analysis. Use the hosted log (download it with the token from `git credential fill`, used
   only for api.github.com and never printed), `javap` on the mapped Forge jar, and a local reproduction if one is
   practical (for example a unit-sized GameTest that carves a room through rails on a support column).
2. Fix the fixture so that carving terrain can never leave drops that a spill check counts. Keep the spill assertions
   exactly as strict for items the machines handle: do not filter by item type, and do not widen any timeout. For
   example, remove fragile terrain without neighbour drops before carving, or clear terrain drops from the whole
   carved volume including the shell after carving and after the first tick. Apply it to every rig that carves rooms.
3. Add a GameTest that carves a room through a rail on its support and proves that no terrain drop reaches the spill
   check, while a real spill inside the room is still reported.
4. Run the targeted GameTests, then once at the end, under the heavy-JVM lock, the whole unit suite and
   `runGameTestServer` with `python -B scripts/check_gametest_log.py`. Commit to the branch. Do not push.

## Not in this task

- Any change to W87's client fixes, to the spill assertions' meaning, or to other tests' expectations.
- ADRs, the risk register, status files and the ledger (Root).
