# TASK W226: the gas charge pad's O1 finite canister exchange (PAD-D4)

Root: Claude. Date: 2026-10-31. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w226` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w226` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W226.md` (new; the design note at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W226/` |
| Decision | PAD-D4 in [OWNER-DECISIONS-20261031](records/OWNER-DECISIONS-20261031.md): "O1：单人房主用有限罐交换（推荐）" |
| Design | `docs/work/v1.8.0-root-queue/records/W174-DESIGN.md` section 9 (O1, with W174-R5 to R8 and R13), section 4.8 (the pad's output slot and source rules), section 10 (risk text) and section 2.4 (merged equipment and canister code, `SuitEquipmentService.fillOneCanister`) |
| Background | ADR-066 (sections 4 and 9), ADR-054 (vanilla torn saves); the ledger row `block:oxygenCharger` (PLANNED) |

**Root decision R217.** The owner chose O1 for the single-player host. O1 is two pieces, both usable by any player: the
pad fills an empty canister with 1,000 mB from its bank into its output slot, committing bank and output together; and
a new player-local refill puts exactly 1,000 units from a filled canister into the matching installed tank, or into an
installed EMPTY tank, and returns one empty canister. The automatic charging protocol for dedicated players and LAN
guests (W174 sections 4 to 8) is a later task and is not in this one; the pad keeps refusing automatic charging for
the integrated owner.

## What to do

1. **Design note first, committed before code** (one page), from W174 section 9 and its R5 to R8 refinements: the pad's
   fill step (single-store, bank and output committed together), the player-local editor (select OXYGEN or HYDROGEN by
   the actual filled canister; one existing matching tank or one EMPTY tank; refuse duplicate-gas layouts, unsupported
   roots and less than 1,000 free units; enchanted chests accept only the oxygen active-buffer refill; revalidate the
   connected player, slot, count, tag identity and configuration on the server thread; prepare the detached chest and
   the inventory exchange with a guaranteed place for the empty canister, and refuse without debit when there is none,
   never dropping an item entity; publish without intervening callbacks), what stays non-atomic (vanilla container
   tearing between pad and player, the hosting-kind rollback of W174-R13), and the exact risk-register and ADR-066 note
   drafts for Root, outside the repository, with an audit script (format:
   `D:/GitHub/ARCE-Task-Evidence/root-queue/W218/W218-drafts.md`).
2. **Implement it** with tests that fail first: stacked canisters, exact capacity, partial room, a changed inventory
   between request and commit, a full return inventory, each gas, an EMPTY tank, a duplicate-gas layout, an enchanted
   chest, the pad's fill with an empty and a full bank, and that the integrated owner still gets no automatic charging.
   No new lifecycle hook, Access Transformer line, Mixin, receipt or readback.
3. **GameTests** for the pad fill and the player refill, and a short native check on a dedicated server and in an
   integrated world, under the heavy-JVM lock on a JAR built from committed source, including a restart that keeps the
   pad's bank and output and the player's tank as saved.

## Verification

The new tests alone, failing first; the life-support and equipment packages; the whole unit suite; the new and
related GameTest batches three times under the heavy-JVM lock; the native check. Root's hosted run on the pushed branch
stands in for a complete GameTest. An independent review follows before merge.

## Not in this task

The automatic charging protocol (W174 sections 4 to 8), O2, other equipment, budgets, timeouts, ADRs (beyond the
drafts), status files and the ledger.
