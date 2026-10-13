# TASK W25: release provably stale scrubber facade bindings (W14F review L1 and L2)

Root: Claude. Date: 2026-10-13. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w25-scrubber-bindings` |
| Branch | `codex/v1.8.0-w25-scrubber-bindings` (local only; Root merges) |
| Kind | Parallel bug fix |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W14F-review/REVIEW.md`, SHA-256 `d5f9bead354ef3b505cf41fbf0b9b7a2f2078872e7452894d97ecd2e80223fe3`, findings L1 and L2 |
| Record | `docs/work/v1.8.0-root-queue/records/W25.md` |

## Problem

Read L1 and L2. A scrubber facade can become permanently unbreakable online, although it holds
no cartridge:
- **L1.** The vent's bank holds an *empty* association for another identity. Recovery skips
  empty slots, and observation never admits the stale facade. The refusal message also wrongly
  says the scrubber is not loaded or readable.
- **L2.** The facade is bound to a vent generation that no longer exists. Neither recovery nor
  breaking the vent unbinds it.

## Root's decision

1. **L1.** Recovery also releases an *empty* orphaned association, under the same rules as the
   W14F recovery:
   - clear the slot;
   - unbind a loaded conflicting facade;
   - move no item.

   Give it its own message, and correct the misleading refusal message.
2. **L2.** A binding is provably stale when the owner position is **loaded** and holds:
   - no vent; or
   - a vent of a different generation; or
   - a vent whose bank does not reference this facade.

   Such a binding may be released by the same defined player action at the facade. No item
   moves, and the facade then becomes removable. An unloaded owner position proves nothing:
   keep protection, and never load a chunk.
3. **Tests.** GameTests for both scenarios, built from the review's mixed snapshots:
   - removal is refused before the release and allowed after it;
   - nothing is duplicated or lost;
   - nothing changes while the owner position is unloaded.

   Pace loading waits with `GameTestTickPacer`.
4. Keep every existing W14 and W14F assertion unchanged.

## Commands

- The whole unit suite once.
- `runGameTestServer` under the lock, then the log checker. Report the totals.

## Not in this task

- Other scrubber behaviour.
- The gas charger (W11b3 and later).
- Status files and the ledger.
