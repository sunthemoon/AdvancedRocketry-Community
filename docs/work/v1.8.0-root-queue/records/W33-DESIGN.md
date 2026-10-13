# W33 combustion torch design

This implementer owns a new `atmosphere/torch` package, the unlit torch registry entries, one
COMMON key, a read-only supplied-volume accessor in `atmosphere/server`, its data generation
and output, its tests, the W33 records, and the save-protection documentation that AGENTS.md
section 3.3 requires. ADR-066 section 3.2 governs; section 2 supplies the shared root rules.

## Combustion air

A cell sustains combustion only when the authoritative instrument reading
(`AtmosphereAnalyzerRuntime.readAt`, the detector's source) is `BREATHABLE`. `NON_BREATHABLE`
is noncombustible and `PENDING` is unknown. `UNAVAILABLE` (an unloaded cell, or a Level with no
mapped body, such as the vanilla Nether) is unmanaged: nothing is converted or suppressed there.
A torch's cell is its own position. A fire starter's cell is the loaded cell adjacent to the
clicked face; for relighting it is the unlit torch's own cell. A block change dirties the
supplied room around it, and the cells read `VACUUM` until the rescan reaches them. So a torch is
extinguished only when its cell reads noncombustible and the Level's atmosphere is settled: no
dirty backlog, and no scan task other than those waiting on unloaded chunks.

## Tags and the `torch_pairs` catalog

- **Tags.** Block tag `advancedrocketrycommunity:combustion_torches` (`minecraft:torch`,
  `minecraft:wall_torch`) and item tag `advancedrocketrycommunity:combustion_fire_starters`
  (flint and steel, fire charge, blaze rod, blaze powder). The thermite torches are never
  combustion torches, even if a pack tags them.
- **Catalog.** `data/<ns>/torch_pairs/<path>.json`, schema 1, exactly
  `{schema_version, standing_block, wall_block, item}`. The pair ID is the file ID.
  - Bounds: at most 4 KiB per file, IDs of at most 128 UTF-8 bytes, and at most 64 pairs
    including the built-in `minecraft:torch` pair.
  - Registry checks: the standing block is a `TorchBlock` with no properties, the wall block is
    a `WallTorchBlock` with only `facing`, neither has a block entity, and the item is a
    `StandingAndWallBlockItem` that both blocks return from `asItem()`. Pairs naming a thermite
    or unlit torch, or mapping a block twice, are refused.
  - Any error rejects the whole set: the last complete set stays active, or only the built-in
    pair after a failed first load. One ERROR is logged and loading never fails.

## The unlit torches and the `arce_unlit_torch` root

`unlit_torch` (a `TorchBlock`) and `unlit_wall_torch` (a `WallTorchBlock`, `facing` only)
emit light 0, show no flame, have no item and no recipe, and share one block entity type. Their
pushing reaction is `BLOCK`. The root is `{schema_version: 1, pair, block, item}`: exact keys,
canonical resource locations of at most 128 UTF-8 bytes each, inside 2 KiB, depth 8 and 64 nodes.
It owns no inventory. The block entity overrides `onlyOpCanSetNbt()` to return `true`.

- **No root** (made by a command): the torch breaks with no drop and cannot be relit.
- **A decoded root** whose pair, blocks and item still resolve in the live catalog and
  registries, with the saved block matching the shape. The torch drops the saved item once
  through a dynamic loot entry, under the native `survives_explosion` rule, and can be relit.
- **Quarantine.** A root that does not decode (a future or corrupt schema) is kept as raw
  data and written back unchanged. A decoded root whose identity no longer resolves stays
  in place. Both refuse player breaking, explosions, relighting and support loss. An
  outgoing root that fails `BoundedNbt.fits` at 2 KiB, depth 8 and 64 nodes refuses the
  whole chunk save through `GuardedChunkSaves`. The ADR-066 W33 implementation note, the R-027
  scope and the new R-034 record this before any code.

## Conversion, the queue and volume events

- **Placement** (`EntityPlaceEvent`, lowest priority, not cancelled). A tagged torch in a cell
  reading `NON_BREATHABLE` or `PENDING` is refused when it has no catalog pair, or when the
  Level queue is full. Otherwise its position is queued. Other cells place normally.
- **Block events.** `NeighborNotifyEvent` queues the changed cell and its notified neighbours
  that hold supported tagged torches in noncombustible or pending cells.
- **Volume events.** After the atmosphere tick, the service compares the Level's supplied
  volumes with the previous tick. Each volume that lost supply becomes a sweep of its at most
  4,096 cells, which queues the tagged torches it finds. There are at most 128 sweeps per Level,
  and an extra one is counted as dropped. Nothing scans the Level.
- **The queue.** One FIFO per Level, at most 8,192 distinct positions, and 256 checks per
  tick, shared one-for-one between queued positions and sweep cells. A check of a combustible,
  unmanaged, changed or unloaded cell drops the entry. A pending or unsettled cell goes back
  to the tail and is not revisited in the same tick. Full queues log one WARN for each overload
  episode, and `metrics(level)` exposes counts. Queues clear on Level unload and server stop.
- **Extinguishing.** A standing torch becomes `unlit_torch`; a wall torch becomes
  `unlit_wall_torch` with the same `facing`. Both get the pair, block and item identity.
  With `OXYGEN.dropExtinguishedTorches` (COMMON, default false) the torch is removed instead,
  and one pair item drops.

## Fire starters and relighting

A `RightClickBlock` with a tagged item, on the server, is cancelled without item use when
the adjacent cell is noncombustible or pending. On an unlit torch, the server relights it only
when all of these hold:
- the root resolves and the torch is not quarantined;
- the torch cell is `BREATHABLE`;
- `mayUseItemAt` allows the player;
- the restored state survives at that position.

Relighting restores the pair's exact standing block, or its wall block with the saved
`facing`. Flint and steel loses one durability and a fire charge is consumed; other starters
and creative players spend nothing. A refused relight spends nothing.

## The environmental-change adapter (frozen here, ADR-054 style)

Each change checks the FULL chunk (`getChunkNow`), the world border and build height, and the
quarantine. Then the new state is set without neighbour updates, and a cancellable
`BlockEvent.EntityPlaceEvent` is posted. A relight names the player. An environmental extinguish
or removal names no entity, so there is no FakePlayer and no operator bypass. A cancellation
restores the snapshot, with the original block and data and no drops. Otherwise neighbours are
notified, as Forge does after item placement. No client value chooses a position or state.

## Tests

- **Unit:** catalog codec bounds, duplicates and whole-set rejection; root codec at its limits;
  queue cap, deduplication and fairness.
- **GameTests (Moon, pinned chunk):** standing and wall conversion with facing; relight and
  refusal; both drop modes; unsupported tagged refusal; thermite torch unchanged; queue cap;
  root preservation and the guarded save; operator-only NBT. Loading waits use
  `GameTestTickPacer`, and no callback schedules another.

## Not in this slice

Other C18a environment rules, fluids flowing into a quarantined torch (recorded as a risk),
open-air torches in a Level whose environment changes, status files and the ledger.
