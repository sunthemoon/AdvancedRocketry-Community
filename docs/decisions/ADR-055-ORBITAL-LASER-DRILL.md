# ADR-055 — Orbital laser drill

```yaml
status: PROPOSED
revision: 1
date: 2026-10-01
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.7.0
slices: [V170-LAS-01, V170-LAS-02]
development_dependency: ADR-016, ADR-041, ADR-043, ADR-044, ADR-052, ADR-054
```

## Context

The legacy orbital laser drill is a station multiblock that either conjures ores
from an unseeded `Random` (the default) or, with `laserDrillPlanet`, digs a 3×3
shaft into the planet below. It trusts client packets for its target and running
state, force-loads the station chunk and the client-chosen target chunk, keeps
only the last block's drops per layer, discards overflow, and cleans up from a
background thread ([audit §1](../work/v1.7.0-legacy-audit.md)). v1.7 needs a
representative orbital mining loop with server-validated targets, protection,
budgets, conserved output and no chunk loading.

## Decision

### 1. Structure and placement

- A multiblock on the machine kernel (ADR-016): controller block
  `advancedrocketrycommunity:orbital_laser_drill`, pattern
  `machine_patterns/orbital_laser_drill.json`, at most 5 × 5 × 5, four rotations,
  no mirror. C11 fixes the cells with community-authored blocks; no upstream
  structure or asset is copied.
- The controller block entity holds everything the drill pays for or produces,
  so one chunk save covers payment and output (ADR-054 §8):
  - one lens slot (`advancedrocketrycommunity:laser_lens` only, not consumed);
  - an output buffer of 18 slots, extractable by automation from the
    controller only, never insertable;
  - an energy buffer of 200,000 FE, input ≤ 20,000 FE per tick;
  - settings: `running`, redstone mode (`IGNORED`, `ON`, `INVERTED`, default
    `IGNORED`), mode (`LOGICAL` or `PHYSICAL`) and the physical link (§3);
  - `seed` (64-bit, from the server's `SecureRandom` on the first load, never
    client-derived) and the operation index `n` (starts at 0).
- It operates only in the Space Level, inside a committed station region where
  ADR-054 §3 allows `OPERATE`, with the station registry operational; otherwise
  `STATION_UNAVAILABLE`. The **orbit body** is the station's live orbit body
  (ADR-041), re-read for every operation, so a warp (ADR-044) changes it at the
  next operation.

### 2. Logical mode (default)

The drill samples the orbit body without touching any world block.

**Tables.** `data/<namespace>/laser_drill_tables/<path>.json`, schema 1, strict
(unknown fields rejected, ≤ 16 KiB per file, ≤ 32 tables):

| Field | Bound |
|---|---|
| `bodies` | 0..64 unique body IDs |
| `default` | boolean; at most one table may set it |
| `entries` | 1..64 entries {`item` existing plain item, `count` 1..64, `weight` 1..10,000}; unique items |

A body may appear in at most one table. Tables reload with the celestial catalog
and item registry like ADR-052 §1–§2: an invalid reload keeps the last complete
set and logs one aggregated error (≤ 32 lines); an invalid initial load fails
loading. A table's **version** is the first 16 hex characters of the SHA-256 of
its raw bytes.

**Eligibility.** The orbit body's own table applies; otherwise the `default`
table applies when the body `supportsSurfaceArrival()`. Gas giants, stars and
unmapped bodies need an explicit table. No table: `NO_TABLE`. A missing body:
`ORBIT_BODY_UNAVAILABLE`.

**Draw** (`laser-v1`). With the SplitMix64 generator of ADR-052 §3 and the
ASCII domain constant `LASERDRL = 0x4C4153455244524C`:

```text
state_n = (seed ^ LASERDRL) + (n + 1) × 0x9E3779B97F4A7C15     (64-bit wrapping)
z       = mix64(state_n)            (the SplitMix64 output function)
r       = (z >>> 1) % Σ weight
entry   = first entry, in file order, whose cumulative weight exceeds r
output  = (entry.item, entry.count)
```

This is the n-th output of `SplitMix64(seed ^ LASERDRL)`, computable without
iterating. The same seed and index always give the same stack, which makes
operations auditable.

**Operation.** At most one per `operationIntervalTicks` per drill. It happens
only when: the system is enabled, the structure is formed, a lens is present,
`running` is set and the redstone mode is satisfied, a table applies, the energy
buffer holds `cost`, and the output buffer can take the **whole** stack. Then, in
one tick at the controller: energy −= `cost`, `n` += 1, buffer += stack. If the
buffer is full the drill pauses with `OUTPUT_FULL`; `n` does not advance, so the
same draw is retried and nothing is discarded.

`cost = 10,000 × energyPercent / 100` FE (legacy 10,000 per operation).

**Built-in data (C11).** Tables for Earth, Moon, Mars and Venus and one
`default` table. The default reproduces the legacy proportion: one hit in ten is
an ore entry of 5 items, nine in ten are 5 cobblestone. Ore items are vanilla raw
ores and minerals; no LibVulpes or upstream item is used.

### 3. Physical mode (opt-in, default off)

Allowed only while `endgame.laserDrill.physicalMining` is true; otherwise
`PHYSICAL_DISABLED` and existing links stay inert.

**Target endpoint.** A `laser_target` endpoint (ADR-054 §9), the block
`advancedrocketrycommunity:laser_target`, placed by the owner on the surface it
will dig. Its root holds a 27-slot drop buffer (extractable, never insertable),
the shaft cursor and the link counter (below). Its footprint is the 3 × 3 column
centred on it; the footprint must lie inside the marker's own chunk (local x and
z in 1..14), else the marker is `FOOTPRINT_CROSSES_CHUNK` and cannot be selected.
Placing the marker itself goes through vanilla placement events, so claim mods
and station protection already apply.

**Link.** The owner selects one of their `ACTIVE` `laser_target` endpoints. The
link is valid for an operation only when all hold, else the code in brackets:

1. the marker is the owner's, or the actor is an operator (`TARGET_FOREIGN`);
2. the marker's body is the station's live orbit body, the body
   `supportsSurfaceArrival()`, and the marker's Level is that body's unique Level
   (`TARGET_WRONG_BODY`);
3. the drill's and the marker's chunks are `FULL` (`TARGET_UNLOADED`);
4. the marker accepts this controller (`LINK_LOST`, below).

**Shaft.** Cells are `(mx − 1 + i mod 3, y, mz − 1 + i div 3)` for `i = 0..8`.
Layers run from `my − 1` down to
`floor = max(minBuildHeight + 1, my − maxDepth)`. The marker stores
`next_layer`. One **operation is one whole layer**, processed in one tick:

1. Run the ADR-054 §5 chain steps 1–6 for the layer box.
2. Classify every cell before changing any:
   - air: nothing to do;
   - a block entity, a destroy speed below 0, or the tag
     `advancedrocketrycommunity:laser_drill_immune` (default content
     `#minecraft:wither_immune`): stop with `BLOCKED_IMMUNE`;
   - a `LiquidBlock` (a pure fluid): stop with `BLOCKED_FLUID`. Fluids are never
     deleted;
   - otherwise breakable; post the §5 step 7 break event for it, and a
     cancellation stops with `TARGET_PROTECTED`.
3. Compute the drops of every breakable cell with
   `Block.getDrops(state, level, pos, null, ownerFakePlayer, ItemStack.EMPTY)`
   (no tool, no fortune). If the marker buffer cannot take **all** of them,
   stop with `TARGET_BUFFER_FULL`.
4. Remove the breakable cells (`removeBlock`, no drop entities, no experience),
   add every drop to the marker buffer, set `next_layer −= 1`. A waterlogged
   block leaves its fluid, which stops the next layer.

A stop leaves the layer untouched; nothing is half-broken. When
`next_layer < floor` the shaft is `COMPLETE`. A shaft never breaks anything
outside its footprint, never damages entities and never places blocks (no
light-source blocks). Falling blocks and fluids next to the shaft follow vanilla
physics.

**Payment counters.** Payment is in the controller's chunk (Space Level), the
effect in the marker's chunk; they are saved independently. Both sides keep a
monotone counter for their single link:

- controller: `link = {marker_id, ops_paid}`;
- marker: `link = {controller_id, ops_done}`; a marker serves one controller.

At every contact (both chunks `FULL`, before each operation):

- `ops_done > ops_paid` (the marker was saved after an operation, the controller
  was not): the controller pays the **debt** `(ops_done − ops_paid) × cost`
  first, or waits with `ENERGY_DEBT`, then sets `ops_paid = ops_done`
  (`DEBT_SETTLED`);
- `ops_paid > ops_done` (the reverse): the marker performs the paid layers
  without a new payment (`CREDIT_USED`).

Then a normal operation: energy −= `cost`, `ops_paid += 1`, the layer runs,
`ops_done += 1`, all in one tick. Each crash therefore leaves at most one
unpaid or prepaid layer, and the next contact settles it. Debt is never
forgiven by relinking: a controller may change or clear its link only after a
contact with no debt and no credit, or when the marker is `MISSING`
(audited `LINK_ABANDONED`; an unsettled layer is the documented residual). A
marker owner may reset a marker that is not active; it then accepts a new
controller, and the old controller sees `LINK_LOST`.

### 4. Budgets and limits

| Value | Default | Bound |
|---|---|---|
| `endgame.laserDrill.energyPercent` | 100 | 10..1,000 |
| `endgame.laserDrill.operationIntervalTicks` | 20 | 20..1,200 |
| `endgame.laserDrill.maxDepth` | 64 | 1..256 |
| `endgame.laserDrill.activeGlobal` | 32 | ≤ 32 |
| `endgame.laserDrill.activePerOwner` | 4 | ≤ 4 |
| `endgame.laserDrill.logicalOperationsPerTick` | 32 | ≤ 32 |
| `endgame.laserDrill.layersPerTick` | 7 | ≤ 7 (63 cells) |

A drill counts as active while `running` is set; starting beyond a limit is
refused with `ACTIVE_LIMIT`. Work over a per-tick cap waits (ADR-054 §7).

### 5. Menus and visuals

- Menu: lens slot, output slots, energy, mode, running, redstone mode, the
  selected target (owner and operators only: name, body, position, depth,
  buffer fullness) and the last stop code. Buttons: start, stop, mode, redstone
  mode, target previous/next, link, unlink.
- **Danger confirmation:** starting physical mode (or relinking it) is two-step:
  the first `start` shows the footprint, the current layer, the floor and "this
  removes blocks"; only a `confirm` intent from the same player within 200 ticks
  sets `running`. Logical mode starts directly.
- The marker's menu shows its buffer, cursor, linked state and last code to its
  owner and operators.
- Visuals: the marker's update tag carries `active` and the current depth; the
  client draws one 3-wide beam from at most 384 blocks above the marker down to
  the current layer. The controller draws a short emitter glow. At most 8
  particles per tick per beam, none when particles are set to minimal. No sound
  louder than a machine hum. V1 is `[H]`.

### 6. Audit

`ARCE_ENDGAME system=laser_drill`: start, stop, mode and link changes, every
stop code change, `DEBT_SETTLED`, `CREDIT_USED`, `LINK_ABANDONED`, a physical
layer summary (marker, layer y, cells broken, drop items), and a logical summary
every 1,200 ticks per drill (operations, items, table version).

### 7. Threat model

| Threat | Control |
|---|---|
| Digging others' land | Physical mode off by default; marker placed by the owner through vanilla placement; ADR-054 §5 chain per layer including break events and zones; 3 × 3 footprint inside the marker's own chunk; depth cap |
| Destroying containers or special blocks | Block entities, unbreakable and immune-tagged blocks stop the shaft |
| Draining oceans / lava | Fluids stop the shaft; nothing deletes fluid |
| Duplication | Logical: payment and output in one block entity; physical: drops in the same chunk as the broken blocks; counters for payment; whole-layer atomicity |
| Lost drops | Whole drop set must fit before breaking; full buffers pause |
| Chunk loading | None; both chunks must be `FULL` |
| Forged target or running state | Server-side endpoint selection; no client coordinate; intents only |
| Server load | Fixed cadence, global layer and operation caps, active-drill limits |
| Off-thread mutation | All work on the server thread |

## Deferred and rejected

Recorded in `PORTING_MATRIX.md`:

- LINE_X, LINE_Z and SPIRAL target stepping: **deferred** to the v1.8 UI batch;
  the owner re-targets by placing another marker.
- Mining at raw coordinates without a marker, and force-loading the target:
  **rejected** (ADR-054 §12).
- Per-dimension blacklists by integer ID: replaced by tables and protection.

## Consequences

- Logical mining is safe to enable everywhere; physical mining is an explicit
  server decision.
- Physical mining runs only while the target area is loaded, typically with the
  owner nearby.

## Verification

- A0: `laser-v1` vectors (reference vectors), table codec and reload rules,
  eligibility, cost and cadence, output-full retry, shaft geometry, footprint
  rule, layer classification order, whole-layer stop, counter reconciliation for
  every crash cut, link change rules, limits.
- A1: logical loop on a station; warp changes the table; physical shaft through
  stone with drops in the marker; immune block, fluid, block entity, zone,
  spawn protection, API cancellation and break-event cancellation each stop the
  layer untouched; buffer-full stop; unloaded marker waits; disabled switch;
  zero tickets.
- S2 (C13): forced stop after a layer in each save order, then debt or credit
  settles to exactly one payment per layer.

## Rollback

Disable by config; the blocks stay. Removing the code turns the blocks into air
and drops nothing.
