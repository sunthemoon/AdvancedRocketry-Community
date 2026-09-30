# ADR-049 — Satellite blueprints, components, kinds and assembly

```yaml
status: PROPOSED
date: 2026-09-30
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.6.0
slices: [V160-SAT-01, V160-SAT-02, V160-SAT-03]
related: [ADR-010, ADR-029, ADR-037, ADR-043, ADR-050, ADR-051, ADR-052]
```

## Context

v0.8 (ADR-010) ships one fixed recipe at the Satellite Terminal: chassis, solar
module, data-storage payload and a blank control chip give a bound package plus
chip. v1.3 (ADR-029) lets mods register other payload items for the same
research/discovery mission. A satellite record has no kind, stats or location;
its definition (schema 1) only describes research missions.

The legacy game (see the [audit](../work/v1.6.0-legacy-audit.md)) assembles a
satellite from a chassis, one primary function and up to six modules whose stats
(power generation, battery, data) are summed, and deploys it on a rocket. Its
representative types scan, bank solar power or run resource missions, all by
ticking every satellite every tick.

v1.6 needs several constructable, deployable kinds without per-tick satellite
objects, static registries or client-trusted stats.

## Decision

### 1. Kinds

A closed host enum persisted as a lowercase string:

| Kind | Player outcome in v1.6 |
|---|---|
| `data` | Existing research/discovery missions (ADR-010/029/037), unchanged |
| `survey` | Asteroid surveys (ADR-051) and a bounded mineral/biome area scan (§8) |
| `solar` | Rate-based power to one linked receiver (§9) |
| `asteroid_miner` | Asteroid resource missions (ADR-051) |
| `gas_harvester` | Gas-giant harvesting missions (ADR-051) |

Unknown kind strings fail closed (record quarantined, ADR-050). API 1.7 payload
registration keeps producing `data` definitions only; any public API for other
kinds needs a separate ADR under ADR-021.

### 2. Component catalog

Data-driven files `data/<namespace>/satellite_components/<path>.json`, schema 1,
decoded strictly (unknown fields rejected, ≤ 8 KiB per file, ≤ 64 files):

```json
{ "schema_version": 1, "item": "advancedrocketrycommunity:satellite_solar_module",
  "role": "power", "power_generation": 4 }
```

| Field | Bound | Rule |
|---|---|---|
| `item` | existing item, not air, ≤ 128 chars | unique across the catalog |
| `role` | `chassis`, `primary`, `power`, `battery`, `data_storage`, `cargo` | required |
| `kind` | a §1 kind | required iff `role = primary` |
| `power_generation` | 1..1,000 | only and required for `power` |
| `battery_capacity` | 1..1,000,000 | only and required for `battery` |
| `data_capacity` | 1..100,000 | only and required for `data_storage` |
| `cargo_stacks` | 1..27 (64-item units) | only and required for `cargo` |
| `primary_rating` | 1..100 | only and required for `primary` |

A component matches only a plain item stack (no `tag`, no `ForgeCaps`, exact
native round-trip), as in ADR-029. An invalid reload keeps the last complete
catalog; an invalid initial load fails loading. The catalog reloads together with
satellite definitions (ADR-029 merge) so that every `primary_component` resolves.

Built-in values reuse the legacy numbers: existing solar module power 4; a new
advanced solar panel power 40; battery modules 10,000 and 40,000; the existing
data-storage unit data 1,000; a cargo hold 9 units; primary modules (survey,
solar transmitter, asteroid drill, gas intake) rating 10; the existing chassis.
New items use community-authored assets unless a provenance record says otherwise.

### 3. Definitions (schema 2)

`satellite_definitions` gain schema 2. Schema-1 files keep loading unchanged as
`kind: data`. Schema 2 is a tagged union on `kind`:

- common: `schema_version: 2`, `id`, `kind`, `primary_component` (item; required
  unless `kind = data`), `required_lifetime_research` (0..1,000,000; default 0),
  `launch_targets` (1..16 unique celestial IDs; required unless `kind = data`);
- `data`: the schema-1 fields (duration, research yield, discovery cost, targets)
  with unchanged bounds;
- `survey`: `mission_duration_ticks` (20..72,000), `instances_per_survey` (1..4),
  `scan_energy` (1..100,000), `scan_radius_blocks` (16..48), `scan_cell_blocks`
  (4, 8 or 16);
- `solar`: `output_multiplier_percent` (1..400);
- `asteroid_miner` / `gas_harvester`: no extra fields (ADR-052 supplies durations).

Catalog limits: at most 16 `data` definitions (ADR-029's limit and menu format are
unchanged) plus at most 16 definitions of other kinds; at most one definition per
`primary_component`. A definition removal affects only future assembly, launch
and mission starts; existing satellites keep their snapshot (§6).

### 4. Blueprint and stats

A **blueprint** is the ordered component list of one assembly: one chassis, one
primary (absent only for the legacy `data` recipe), and 0..6 modules, one item
per slot. Stats are integer sums, each checked against a cap before use:

| Stat | Formula | Cap |
|---|---|---|
| `power` | Σ `power_generation` | 1,000 |
| `battery` | 720 + Σ `battery_capacity` | 1,000,000 |
| `data` | Σ `data_capacity` | 100,000 |
| `cargo` | Σ `cargo_stacks` | 27 |
| `rating` | primary `primary_rating` (0 if none) | 100 |

Exceeding a cap refuses assembly (`STAT_LIMIT`); no value is clamped silently.
Host-fixed v1 requirements: every kind `power ≥ 1`; `data` and `survey`
`data ≥ 1`; `survey` `battery ≥ scan_energy`; `asteroid_miner` and
`gas_harvester` `cargo ≥ 1`. The legacy `data` recipe is the blueprint
{chassis, solar module, data-storage unit} with stats {power 4, battery 720,
data 1,000, cargo 0, rating 0}.

### 5. Satellite Builder

A new host block performs blueprint assembly; the terminal keeps its fixed `data`
recipe and ADR-029 payload assembly unchanged (ADR-010 anticipated this split).

- Slots: 0 chassis, 1 primary, 2–7 modules, 8 blank control chip, 9 package output.
  Automation may insert only matching components and extract only slot 9.
- One C2S intent (`ASSEMBLE`) carries no item, stat or kind data. The server
  checks: player alive, same dimension, ≤ 8 blocks, loaded chunk, open menu bound
  to this block, catalog generation unchanged since the menu opened, output empty,
  every slot's component role, kind requirements, stat caps, definition present,
  `required_lifetime_research ≤` the player's lifetime research, and 1,000 FE.
  One assembly per builder per 20 ticks.
- Success atomically (same tick, same block entity) consumes one item per filled
  slot and the energy, writes a new UUID identity to the chip, and puts the package
  in slot 9. No SavedData mutation happens at assembly.
- Root schema 1 with the ADR-029 preflight (64 KiB, depth 20, 2,048 nodes; items
  4 KiB, depth 16, 256 nodes), exact slot count 10, unknown-item quarantine and
  exactly-once raw-root carry on removal.

### 6. Package/chip identity and launch

Item identity schema 2 adds `kind` and `components` (1..8 item IDs in blueprint
order). Schema-1 packages decode as `kind: data` with the legacy blueprint. At
launch the server re-derives stats from the **current** component catalog; an
unknown component returns `COMPONENT_UNAVAILABLE` and keeps the package. Item
NBT is not trusted for numbers: a forged component list is equivalent to owning
those components, which already requires creative/operator rights.

Launch stays the terminal's logical launch (ADR-010) with the matching chip,
owner, loaded nearby terminal and `LAUNCH_POWER_THRESHOLD` checks, idempotent by
package UUID. `data` launch still starts its first mission. Other kinds launch
**idle** into a chosen `orbit_body` from `launch_targets`; the body must be
discovered when it requires discovery (ADR-037) and must be orbitable. The orbit
body is fixed for the satellite's life. `required_lifetime_research` is checked
again at launch.

### 7. Satellite record schema 2

Satellite records move from schema 1 to 2 (registry root migration in ADR-050):
`kind`, `orbit_body` (absent for `data`), blueprint snapshot (component IDs and
the five stats), and a kind-state compound: `survey` {`charge`, `charge_time`};
`solar` {optional receiver link: receiver UUID}; others empty. Schema-1 records
migrate to `kind: data` with the legacy blueprint; identity, owner, launch time,
status and current mission are kept. A record stays ≤ 1 KiB. Stats never change
after launch; definition or component reloads cannot rewrite them.

Research unlocks use **lifetime** research (monotonic, never spent), so there is
no purchase, second currency or new account field (ADR-037 unchanged).

Per-owner limit: 256 satellites (global 4,096 unchanged). An owner may
**decommission** an idle satellite (no unfinished mission, no receiver link)
with its chip at a terminal: the record is removed, the chip becomes blank, and
nothing is refunded.

### 8. Survey area scan

The survey satellite's bound chip is used in hand. The server derives the centre
from the player's current block position; the player's Level must map to the
satellite's `orbit_body`. Cost: `scan_energy` from the lazy battery
`charge = min(battery, charge + power × Δlogical_ticks)`, persisted only when a
scan is paid. Limits: one job per player, four jobs server-wide, 100-tick
cooldown per player, `(2 × radius / cell)²` ≤ 576 cells, ≤ 16,384 block-state reads
per tick per job on the main thread. Only chunks returned by `getChunkNow` are
read; other cells are `UNKNOWN`. The job is cancelled if the player logs out,
changes Level or moves more than 64 blocks. Result per cell: ore count ratio
against `#forge:ores` (0..65,535) and the dominant biome (palette ≤ 16 IDs); the
S2C packet is ≤ 8 KiB. Results are transient and grant nothing.

### 9. Solar receiver

A new **microwave receiver** block has four chip slots. Inserting a `solar`
satellite's bound chip asserts a link (satellite → receiver UUID) in SavedData on
the next 20-tick check; the most recent assertion by the receiver actually holding
the chip wins. While loaded and linked, the receiver produces
`Σ power × solar_intensity(orbit_body) × output_multiplier_percent / 100` FE/t,
floor, capped at 10,000 FE/t, into a 100,000 FE buffer that pushes to neighbours.
There is no offline banking, so a server or chunk downtime produces nothing and
there is no catch-up. Link changes are the only SavedData writes. Energy is not a
reward record; a crash can at worst repeat or drop one receiver buffer's worth.

### 10. Network and menus

All new C2S packets are fixed-size intent IDs or indices into a server-built menu
snapshot, validated on the main thread (player, distance, loaded chunk, menu
binding, catalog generation, ownership, state) with 10-tick per-player rate
limits. Menu snapshots are bounded (≤ 32 definitions, ≤ 16 targets each) and fit
Forge's 32,600-byte menu extra-data limit, tested at maximum size.

## Deferred and rejected

- Rocket satellite bay deployment: **deferred** to the v1.8 porting matrix. The
  terminal launch remains the only deployment path. Player impact: satellites
  are not physically carried by rockets.
- Biome-changer (terraforming) satellite: **rejected for v1.6**.
- Spy telescope, typed data units, Observatory multiblock: not revived in v1.6;
  surveys replace the asteroid-analysis use.

## Migration and rollback

Registry and item schema changes are forward-only. Worlds opened by v1.6 cannot
be read by v1.5 hosts (future-schema fail-closed per ADR-010/050); keep backups
before downgrading. Data packs using schema-1 definitions keep working.

## Verification

- A0: component/definition codec bounds, role/field consistency, blueprint
  aggregation and caps, requirement matrix, schema-1 migration, item identity.
- A1: builder happy path and every refusal; automation slot rules; launch per
  kind; lifetime-research and discovery gates; decommission; scan budgets,
  unloaded-cell `UNKNOWN`, zero chunk tickets; receiver link uniqueness and output.
- Menu snapshot maximum-size test; builder root quarantine and carry tests.
