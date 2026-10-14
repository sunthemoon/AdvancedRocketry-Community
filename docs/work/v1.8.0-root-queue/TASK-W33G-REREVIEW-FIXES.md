# TASK W33G: fix the W33F re-review findings F1-R and F2-R, and pin N1

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w33-torches`, at `4e62a3dd` plus this task |
| Branch | `codex/v1.8.0-w33-torches` (local only; Root merges) |
| Re-review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W33F-review/REVIEW.md`, SHA-256 `09c8d97b03e242442c324f0099ea3eb1f91caec7fe5c923b888acfa96ff7665f`. Verdict BLOCK: F1-R High, F2-R Medium, N1 Low. F3 and the constructor cleanup are closed |
| Branch CI on `4e62a3dd` | Hosted run 38434663181 passed |
| Record | `docs/work/v1.8.0-root-queue/records/W33G.md` |

Read the whole re-review, the first review (`W33-review/REVIEW.md`) and `records/W33F.md`.
Fix each finding in its own commit, with its tests.

## Root's decisions

1. **F1-R (High): Forge `FluidUtil.tryPlaceFluid` deletes a quarantined torch.**
   - **The problem.** Forge admits any non-solid block, then `BlockWrapper.fill` calls
     `destroyBlockOnFluidPlacement`. `isSolid()` comes from the block state cache, not from
     the block entity.
   - **Recommended mechanism.** Add `forceSolidOn()` to both unlit torch registrations.
     - Forge's gate then refuses a quarantined torch: not empty, not non-solid, not
       replaceable, and the container gate rejects it.
     - A resolvable torch still takes `LiquidContainerBlockWrapper` and the adapter's existing
       outcome.
   - **Before you choose it, list the side effects.**
     - Find every vanilla and Forge use of `BlockStateBase.isSolid()` and `blocksMotion()`.
       Use the mapped jar, or the matching sources jar in the Gradle cache.
     - For each use, say whether a solid unlit torch changes behaviour that matters. Examples:
       entity movement, pathfinding, lava fire spread, falling blocks, fluid routing, mob
       spawning and placement.
     - If a side effect matters, choose a narrower mechanism and explain why.
     - A Mixin is not allowed (ADR-073).
   - **Tests.** Add GameTests that call `FluidUtil.tryPlaceFluid` with 1,000 mB of water and of
     lava. Cover standing and wall torches, each with a retained root and with a removed pair.
     Check that:
     - the block, the block entity, the exact identity and the root survive;
     - no item drops;
     - restoring the pair still recovers the torch.

     Add one resolvable-torch case through the same route.
   - **The R-034 text in your record.** It must state exactly what is protected. Native flow,
     buckets, dispensers and Forge's `FluidUtil` are protected. Mods that call `setBlock` or
     `destroyBlock` directly are not.
2. **F2-R (Medium): a neighbour's diagonal read can load a chunk.**
   - **Root's decision.** Admit extinguishing, the drop mode and relighting only when these are
     all FULL, checked with `hasChunk` and no acquisition:
     - the torch's own chunk;
     - all eight chunks around it.

     This is the same neighbourhood guarantee vanilla gives its own block ticks: a ticking
     chunk's eight neighbours are FULL. It replaces the axial and second-ring cell check, or
     contains it. Keep the recheck after the cancellable preflight.
   - **What it does not cover.** A callback chain that reaches beyond those nine chunks, such
     as long redstone or observer propagation, is vanilla's own behaviour for any block change.
     Say so in the record and in the R-034 text. It is not a claim that no callback ever reads
     further.
   - **Tests.** Add a corner fixture:
     - three chunks FULL, and the diagonal one absent;
     - every previously checked cell available;
     - a vanilla stair beside the torch, facing into the absent chunk, as in the re-review's
       scenario.

     Conversion, the drop mode and relighting must each defer. The loaded-chunk count and the
     ticket count stay unchanged. Extend `OwnedLoadedFrontier`, or add a sibling fixture, so
     the test owns and releases every ticket it adds. Keep the existing boundary tests.
3. **N1 (Low): a lava source formed by `lavaSourceConversion` takes the source branch.**
   - Root keeps the current outcome: a source state drops the torch item once.
   - Do not try to tell a bucket from flow inside `placeLiquid`.
   - Add a GameTest that enables the gamerule (and restores it), forms the source horizontally,
     and checks that exactly one item drops and no duplicate appears.
   - Record it as a known difference from vanilla's fizz.

## Commands

- The whole unit suite once.
- `runGameTestServer` under the lock, then the log checker. All W33 and W25 tests must pass. The
  escrow `SUPERSEDING_SAVE` failure, if it appears, belongs to W36. Report it, and do not change it.

## Not in this task

- Other torch behaviour.
- The escrow tests.
- ADR and risk-register edits, status files and the ledger (Root).
