# TASK W118: cargo leaf D-P, the container mutation, slot selection and partial moves

Root: Claude. Date: 2026-10-20. Read [WORKER-RULES](WORKER-RULES.md), then the frozen cargo design
[records/W11CD-DESIGN.md](records/W11CD-DESIGN.md) sections 2.2 to 2.4, 3.2, 3.3 (with the R40 and R41 amendment),
5.1, 5.3 and section 6's D-P row, ADR-065's "W11c and W11d implementation note" and R-035 in
[docs/11-RISK-REGISTER.md](../../11-RISK-REGISTER.md). Also read the records of the leaves this mirrors and builds on:
[W106](records/W106.md) (C-P part 1: the tank host, the mutation, selection and eligibility),
[W112](records/W112.md) (C-P part 2: the resumable preparation and its tickets), [W110](records/W110.md) (D-K: the item
kinds, the stack codec and the buffer signature) and [W114](records/W114.md) (R40 and R41). You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w118` (new; from the shared branch at the commit Root names in the launch prompt) |
| Branch | `codex/v1.8.0-w118` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W118.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W118/` |

This leaf mutates a rocket snapshot's container payloads, so AGENTS section 13's rule for persistence slices applies:
write a one-page plan at the top of the record before the code (which classes, which bounds, which refusals and which
tests); Root has a read-only review done before the merge.

## What to do (section 6's D-P row; the design is frozen, so a disagreement is a finding to report, not to change)

1. **The container mutation** (sections 2.4 and 5.3). Extend the pure cargo mutation to item cargo on
   `vanilla_container_v1` payloads whose block state is `minecraft:chest`, `minecraft:trapped_chest` or
   `minecraft:barrel`, 27 slots: it applies the frozen item delta (item, `n`, tag, container slot and buffer slot) to
   the before snapshot, keeps every other block, the snapshot ID, propulsion and every aggregate bound, re-sorts the
   after `Items` list by slot as the adapter's whitelist does, preserves `CustomName` and `Lock`, and round-trips the
   codec. Other `ChestBlockEntity` subclasses refuse with `NO_CARGO_STORAGE`, a payload with a `LootTable` with
   `UNRESOLVED_LOOT`. Trapped chests follow W106's decision on the production tag; say what applies.
2. **Slot selection and partial moves** (section 5.3's "Partial transfer"), deterministic and server-side: a load takes
   the first nonempty buffer slot and fills the first container slot with the same item and tag and room, otherwise the
   first empty slot, moving `min(buffer count, max stack - target count)`; an unload is the mirror. Merging uses
   `isSameItemSameTags` plus the canonical digests (D-K's merge identity); display names never decide. The remainder
   stays in its slot.
3. **Eligibility for item cargo**, the item counterpart of the fluid predicate: side-effect free, before any sequence,
   queue, reservation or mutation, with D-K's structural and native stack checks (R39: the native checks run here,
   at admission) and the same enclosing operation, device and snapshot bounds. Put the item paths in item-specific
   classes beside the fluid ones; touch the shared `EscrowCargoEligibility`, `EscrowCargoPreparedCapture` and
   `EscrowCoordinator` only for the minimal dispatch, and do not change their refusal mapping: W117 (C-E1, active)
   rewrites that mapping, and Root resolves the merge.
4. **The resumable preparation for items.** Item kinds use W112's session-owned tickets, budget, transient lock and
   permit-gated publication unchanged; add only what an item delta needs.

## Tests (the D-P row)

A0: 27 slots; `LootTable` refusal; `NO_CARGO_STORAGE` for a large chest or another subclass; preserved names and locks;
partial counts at both ends (a full target, the max stack of a 16-stack item, the last item of a buffer slot); canonical
slot order; every nested bound, including inner-passes-outer-fails for bytes, depth and nodes with the full device and
snapshot overhead; deterministic recomputation with byte-identical hashes; unchanged propulsion, geometry and snapshot
ID with a new content hash. A1: explicit load and unload after-snapshot fixtures restored through the unchanged
`VanillaContainerRocketAdapter`, for chest and barrel. These are not real-transaction or crash-safe disassembly evidence
(that is D-E and D-S).

No saved format, schema, hash, bound, dictionary, proof, lease, budget or timeout changes; no existing assertion is
relaxed; new classes stay under 500 lines; `RocketTransferService` (796 lines) and `EscrowCoordinator` must not grow.
A new WARN or ERROR that a GameTest drives needs its own exact manifest entry. Stop and report if any of these seems
needed.

## Verification

Each new or changed test class alone, then the whole unit suite. Then one full GameTest with the unchanged log checker
on committed source, under the heavy-JVM lock (other workers hold it in turn; wait), releasing the lock and deleting
`build/gametest/world` in the `finally` of the same detached script that took it (WORKER-RULES). Check C: and D: above
10 GiB first. Memory is tight: one Gradle process at a time, and check that 6 GiB is free before each.

## Not in this task

The item devices, menus and spill drops (D-A), real item transactions and reconciliation (D-E), art and data (D-D),
C-E, enabling admission, ADRs, the risk register, status files and the ledger (Root).
