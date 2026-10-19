# TASK W110: cargo leaf D-K, the item cargo kinds, `CONTAINER`, the stack codec and the buffer signature (W11d)

Root: Claude. Date: 2026-10-20. Read [WORKER-RULES](WORKER-RULES.md), [records/W11CD-DESIGN.md](records/W11CD-DESIGN.md)
(frozen at `49f7e08b`; sections 2.2, 3.2, 3.3, 5.3 and section 6's D-K row) and [records/W100.md](records/W100.md)
(leaf C-K, which D-K mirrors for items) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w110` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w110` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W110.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W110/` |

This leaf extends the escrow ledger's saved format, so it is a persistence slice (AGENTS.md section 13): commit
a one-page plan first, and Root arranges an independent read-only review before merge.

## What to do

1. **Recheck sections 2.2, 3.2 and 3.3 against your base.** C-K (W100, merged `c3f80822`), C-I (W99) and C-P (W106,
   if merged at your base) have changed the escrow core since the design's `646bf9ee`. Note moved sites; stop and
   report if a difference changes the design.
2. **The plan.** Commit `docs/work/v1.8.0-root-queue/records/W110-PLAN.md` (one page) before any Java change: the
   classes you add or change, where the item kinds join C-K's direction-independent paths, the stack codec's bounds
   and refusals, the buffer signature's canonical form, and how the size report will be measured. Then continue.
3. **D-K's scope as the row gives it:** the kinds `ITEM_CARGO_LOAD` and `ITEM_CARGO_UNLOAD` (the names C-K reserved)
   on C-K's direction-independent rocket-side accessor, bindings, floors, guard checks and capacity preview; the
   endpoint unit `CONTAINER`; the item stack codec with section 5.3's local bounds (a registered item ID of at most
   128 ASCII characters, not air; a count from 1 to `min(64, getMaxStackSize())`; `ItemStack.save` output of at most
   4,096 bytes, depth 16 and 512 nodes, without a `ForgeCaps` key; a lossless round trip), refusing with
   `CARGO_TOO_LARGE` or `INVALID_DATA` and never normalizing; comparison by `ItemStack.isSameItemSameTags` plus
   equal canonical NBT digests (display names never decide); the payload hash through
   `RocketNativeResources.signature`; and the whole 4-slot buffer signature of section 2.2. Settlement keeps section
   3.3's representation: an item-device observation signs the whole typed buffer and uses quantity 0 only as an
   unused diagnostic.
4. **The size report** of section 3.3 ("Size measurement prerequisite") with the real encoders, both directions,
   for item payloads at their maxima, including the five-marker verdict; a candidate that does not fit is refused
   before any side effect, without raising a bound. Report it in the record, as W100 did for fluids.
5. **Tests (A0)** as the D-K row lists them: local and nested limits, just inside and just outside each; the
   `ForgeCaps` refusal; the round trip; equality and signature pins; item settlement and UNKNOWN semantics,
   including that a nonempty item buffer with diagnostic quantity 0 cannot be mistaken for a missing or empty
   survivor; unchanged fuel, fluid-cargo and floor-retire receipt bytes and inspector output. Run each new class
   alone and then the whole unit suite. This leaf needs no native start; run one full GameTest regression under
   the heavy-JVM lock on committed source with the unchanged log checker only if you change code that a GameTest
   reaches, and say which in the record.

Not in this leaf: container mutation and slot selection (D-P), the devices (D-A), real item transactions and the
fair queue (D-E), data and art (D-D), and every S2, V and B leaf. Item cargo admission stays unreachable in
production.

## Write scope

The escrow core classes that section 3.2 and 3.3 name for items, a new stack codec and signature class, new tests,
the plan and the record. The escrow root is unpublished (R-035: production admission is disabled), so adding
operation kinds needs no migration; say so in the record. No change to the fuel or fluid cargo paths' behaviour,
the receipt dictionary, a proof's meaning, a lease or the replay exclusion, and no relaxed assertion, limit or
timeout. Stop and report if one seems needed.

## Commands and not in this task

As WORKER-RULES says. Other workers also run tests; take the heavy-JVM lock for any GameTest and release it at
once afterwards. Check that C: and D: have more than 10 GiB free before a heavy start. If you ran a GameTest,
remove your own lock and `build/gametest/world` with a plain `rm -rf` after checking that the lock's `owner.txt`
names you; if the tool refuses, write the paths in the record and final answer at once. Run
`python -B scripts/validate_repository.py --require-approved-identity` before your last commit.

Not in this task: ADRs, the risk register, status files and the ledger (Root).
