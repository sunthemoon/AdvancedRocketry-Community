# TASK W04F: fix the W04 independent review findings

Root: Claude. Date: 2026-10-12. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w04-force-field` (at the W04 candidate `cbdd24e3`: W04 merged with the shared branch; hosted run 38199213045 passed) |
| Branch | `codex/v1.8.0-w04-force-field` (local only; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W04-review/REVIEW.md`, SHA-256 `45695dd5b04eb9761f75e5b900515b2935fbb56375948e004b777043a958e6b4`. Verdict BLOCK: F1 Critical, F2 to F4 High. |
| Record | `docs/work/v1.8.0-root-queue/records/W04F.md` |

Read the whole review first. It traces each problem through the exact Forge 47.4.10 and
vanilla call order: placement snapshot capture and rollback, `Level.destroyBlock`, and
the redstone neighbour-signal reads.

## Root's decisions

Fix each finding in its own commit, together with its tests. Each test must drive the
real native path: `ItemStack.useOn` with a fake player, or `Level.destroyBlock`. A direct
`setBlock` or a preflight call is not enough.

1. **F1 (a cancelled placement leaves a refunded projector).**
   - Tell the rollback of an **uncommitted** projector placement apart from the destruction
     of committed data. While Forge restores a block snapshot
     (`level.restoringBlockSnapshots`) and the projector was placed in this transaction and
     not yet committed, allow the removal and release its reservation.
   - Grant no bypass for removing existing or quarantined roots.
   - Test: an item placement whose `EntityPlaceEvent` is cancelled, together with a listener
     that refuses FakePlayer breaks. Assert AIR, an unchanged item count and unchanged
     cleanup data.
2. **F2 (the last cleanup slot destroys the placed projector).**
   - Admission happens exactly once per projector. The delayed `onPlace` recognizes the
     reservation that this block entity already holds and never asks for a second
     identity.
   - If admission is impossible, refuse before the item is consumed.
   - Test: a survival item placement at 4,095 entries. Check inventory and world
     conservation and the reservation count, and also the refusal at 4,096.
3. **F3 (a successful world destruction loses the item).**
   - Keep the fail-closed refusal.
   - When `destroyBlock(pos, true)` succeeds with drops enabled, exactly one projector item
     drops; when it is refused, none drops and the block stays.
   - Test: allowed and refused `destroyBlock(..., true)` in pairs, counting the dropped stacks
     and the remaining source blocks.
4. **F4 (redstone reads load chunks and overrun the budget).**
   - Compute the projector's power through a bounded, non-loading view. Read the 25
     positions the stock query can touch only through loaded-chunk access (`getChunkNow`),
     and treat any unloaded position as "no decision this visit": no effect and no load.
   - Charge the scheduler the actual number of positions read.
   - Tests:
     - a conductor on a loaded chunk edge next to an absent chunk: the chunk stays
       unloaded, and nothing changes;
     - seven projectors whose actual inspected-cell total stays within the shared cap
       of 64 per tick.

## Tests and commands

- The tests above.
- The whole unit suite once. Each new test class must also pass when run alone.
- At the end: `runData` and `runGameTestServer` (under the lock), then the log checker.
  Report the GameTest totals.
- If the known vanilla IOWorker CME or the atmosphere boundary startup-budget failure
  appears, retry once and record it.

## Not in this task

- Landing pads.
- Station controls.
- Other API additions.
- Status files and the ledger.
