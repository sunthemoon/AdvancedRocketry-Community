# ADR-049 — Satellite blueprints, components, kinds and assembly

```yaml
status: PROPOSED
revision: 2
date: 2026-09-30
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.6.0
slices: [V160-SAT-01, V160-SAT-02, V160-SAT-03]
related: [ADR-010, ADR-029, ADR-037, ADR-043, ADR-050, ADR-051, ADR-052]
```

Revision 2 answers the first independent review (H3, M4, M9, M10, M11, M14,
L4, L5, L13); see the [preparation evidence](../work/v1.6.0-preparation/VERIFICATION.md).

## Context

v0.8 (ADR-010) ships one fixed recipe at the Satellite Terminal: a chassis, a
solar module, a data-storage payload and a blank control chip give a bound
package and a chip. v1.3 (ADR-029) lets mods register other payload items for the
same research/discovery mission. A satellite record has no kind, stats or
location, and its definition (schema 1) describes only research missions.

The legacy game (see the [audit](../work/v1.6.0-legacy-audit.md)) assembles a
satellite from a chassis, one primary function and up to six modules, summing
their stats (power, battery, data), and deploys it on a rocket. Its
representative types scan, bank solar power or run resource missions, and every
satellite ticks every tick.

v1.6 needs several kinds that can be built and deployed, without per-tick
satellite objects, static registries or stats trusted from the client.

## Decision

### 1. Kinds

A closed host enum, persisted as a lowercase string:

| Kind | Player outcome in v1.6 |
|---|---|
| `data` | Existing research/discovery missions (ADR-010/029/037), unchanged |
| `survey` | Asteroid surveys (ADR-051) and a bounded mineral/biome area scan (§8) |
| `solar` | Power to one linked receiver while that receiver is loaded (§9) |
| `asteroid_miner` | Asteroid resource missions (ADR-051) |
| `gas_harvester` | Gas-giant harvesting missions (ADR-051) |

A record with an unknown kind blocks the whole registry and preserves its
payload, the same rule as a future schema (ADR-050 §9). API 1.7 payload
registration keeps producing `data` definitions only. A public API for the other
kinds needs its own ADR under ADR-021.

### 2. Component catalog

Data-driven files `data/<namespace>/satellite_components/<path>.json`, schema 1.
Decoding is strict: unknown fields are rejected, a file is at most 8 KiB, and
there are at most 64 files.

```json
{ "schema_version": 1, "item": "advancedrocketrycommunity:satellite_solar_module",
  "role": "power", "power_generation": 4 }
```

| Field | Bound | Rule |
|---|---|---|
| `item` | existing item, not air, ≤ 128 chars | unique across the catalog |
| `role` | `chassis`, `primary`, `power`, `battery`, `data_storage`, `cargo` | required |
| `kind` | a non-`data` kind from §1 | required iff `role = primary` |
| `power_generation` | 1..1,000 | only and required for `power` |
| `battery_capacity` | 1..1,000,000 | only and required for `battery` |
| `data_capacity` | 1..100,000 | only and required for `data_storage` |
| `cargo_stacks` | 1..27 (64-item units) | only and required for `cargo` |
| `primary_rating` | 1..100 | only and required for `primary` |

A component matches only a plain item stack (no `tag`, no `ForgeCaps`, exact
native round-trip), as in ADR-029. An invalid reload keeps the last complete
catalog; an invalid initial load fails loading. The catalog reloads together with
the satellite definitions.

Built-in values reuse the legacy numbers:

- the existing chassis;
- power: the existing solar module 4, a new advanced solar panel 40;
- battery modules 10,000 and 40,000;
- data storage: the existing data-storage unit 1,000;
- a cargo hold of 9 units;
- primary modules for survey, solar transmitter, asteroid drill and gas intake,
  each with rating 10.

New items use community-authored assets unless a provenance record says
otherwise.

### 3. Definitions

`data` definitions stay schema 1 and ADR-029 applies unchanged, including its
16-definition limit. The other kinds use schema 2:

- common fields: `schema_version: 2`, `id`, `kind`, `primary_component` (item),
  `required_lifetime_research` (0..1,000,000, default 0), and `launch_targets`
  (1..16 unique celestial IDs);
- `survey`: `mission_duration_ticks` (20..72,000), `instances_per_survey`
  (1..4), `scan_energy` (1..100,000), `scan_radius_blocks` (16..48), and
  `scan_cell_blocks` (4, 8 or 16). `scan_radius_blocks` must be a multiple of
  `scan_cell_blocks`;
- `solar`: `output_multiplier_percent` (1..400);
- `asteroid_miner`, `gas_harvester`: no extra fields (ADR-052 supplies durations).

There are at most 16 schema-2 definitions, and at most one definition per
`primary_component`.

### 4. Blueprint, stats and refusals

A **blueprint** is the builder's slot layout. Slot 0 holds the chassis, slot 1
one primary, and slots 2–7 zero to six modules, one item per slot; each slot
accepts only its role. Stats are 64-bit sums, and each is checked against a cap:

| Stat | Formula | Cap |
|---|---|---|
| `power` | Σ `power_generation` | 1,000 |
| `battery` | 720 + Σ `battery_capacity` | 1,000,000 |
| `data` | Σ `data_capacity` | 100,000 |
| `cargo` | Σ `cargo_stacks` | 27 |
| `rating` | primary `primary_rating` | 100 |

Host-fixed v1 requirements:

- every kind: `power ≥ 1`;
- `survey`: `data ≥ 1` and `battery ≥ scan_energy`;
- `asteroid_miner` and `gas_harvester`: `cargo ≥ 1`.

Refusal codes, checked in this order:

1. menu, proximity and rate checks (§10);
2. `OUTPUT_OCCUPIED`;
3. `INVALID_LAYOUT` (a slot is empty or holds the wrong role; the chassis or
   primary is missing);
4. `DEFINITION_UNAVAILABLE`;
5. `STAT_LIMIT`;
6. `REQUIREMENT_UNMET`;
7. `RESEARCH_LOCKED`;
8. `NO_ENERGY`.

Nothing is clamped silently.

The legacy `data` recipe {chassis, solar module, data-storage unit} has the stats
{power 4, battery 720, data 1,000, cargo 0, rating 0}.

### 5. Satellite Builder

A new host block assembles the four non-`data` kinds. `data` satellites are
assembled only at the terminal, by the ADR-010 recipe and ADR-029 payloads, which
are unchanged (ADR-010 anticipated this split).

- Slots 0–7 as in §4; slot 8 holds a blank control chip; slot 9 is the package
  output. Automation may insert only matching components and extract only from
  slot 9. A 10,000 FE buffer accepts energy from any side.
- The only C2S intent is `ASSEMBLE`, through vanilla `clickMenuButton`. It
  carries no item, stat or kind. The server re-checks: the player is alive, in the
  same dimension, within 8 blocks, in a loaded chunk, with the menu bound to this
  block; the catalog generation is unchanged; then §4 in order, lifetime research,
  and 1,000 FE. There is at most one assembly per builder per 20 ticks.
- Success, in the same tick and block entity: consume one item per filled slot
  and the energy, write a new UUID identity to the chip, and put a
  `satellite_package` (a new generic package item) in slot 9. The registry is not
  touched at assembly.
- Root schema 1: exactly 10 slots, energy and a format marker; the ADR-029
  preflight (64 KiB, depth 20, 2,048 nodes; items 4 KiB, depth 16, 256 nodes);
  unknown-item quarantine; exactly-once raw-root carry on removal.

### 6. Package and chip identity, and launch

Item identity schema 2 adds `kind` and `components` (1..8 item IDs in slot
order). Schema-1 identities decode as `kind: data` with the legacy blueprint.

Launch uses the terminal's logical launch (ADR-010): matching chip, owner,
a loaded nearby terminal, and `LAUNCH_POWER_THRESHOLD`. It is idempotent by
package UUID. For non-`data` kinds the server:

- re-derives the stats from the **current** catalog and re-checks §4 (caps and
  requirements), plus `required_lifetime_research`. On failure the package is
  kept and the result is `COMPONENT_UNAVAILABLE`, `STAT_LIMIT` or
  `REQUIREMENT_UNMET`;
- launches the satellite **idle** into an `orbit_body` chosen from
  `launch_targets`. The body must be orbitable and, if it requires discovery,
  discovered (ADR-037). It stays fixed for the satellite's life;
- snapshots the kind parameters (§3) into the satellite;
- on replay, returns `IDEMPOTENT` when a satellite with the same ID, owner,
  definition and kind exists without a mission. This fixes the current
  `IDENTITY_CONFLICT` answer to a missionless replay.

Item NBT is not trusted for numbers. A forged component list amounts to owning
those components, which already needs creative or operator rights, and an owner
mismatch is still refused. `data` launch still starts its first mission, as today.

### 7. Satellite record schema 2

Satellite records go from schema 1 to 2 (registry root 3, ADR-050 §10). They
gain:

- `kind`;
- `orbit_body` (absent for `data`);
- the blueprint snapshot: component IDs and the five stats;
- the kind state:
  - `survey`: `charge` and `charge_time`, plus the snapshotted scan parameters;
  - `solar`: `output_multiplier_percent` and an optional receiver link (receiver
    UUID and link epoch);
  - other kinds: empty.

A record is at most 2 KiB. With eight 128-character IDs the worst case is about
1.7 KiB.

Schema-1 records migrate to `kind: data`, with `components: []` and a
`legacy_blueprint` flag. They get the fixed legacy stats as a label; this does
not claim which items were consumed, since payload-based satellites never used a
data-storage unit. Identity, owner, launch time, status and current mission are
kept. Stats and parameters never change after launch.

Research unlocks use **lifetime** research, which is monotonic and never spent.
There is no purchase, second currency or new account field, and ADR-037 is
unchanged.

The per-owner satellite limit is 256; the global limit of 4,096 is unchanged
(ADR-050 §6). An owner may **decommission** an idle satellite: one with no
unfinished mission, and either no receiver link or a link to a missing receiver
(§9). This is done with its chip at a terminal. The record is removed, the chip
becomes blank, and nothing is refunded.

### 8. Survey area scan

The survey satellite's bound chip is used in hand. The server derives the centre
from the player's block position, and the player's Level must map to the
satellite's `orbit_body`. A scan does not depend on missions.

- Cost: `scan_energy`, paid from the lazy battery,
  `charge = min(battery, charge + power × Δlogical_ticks)` in saturating 64-bit
  arithmetic. The charge is persisted, and the registry marked dirty, only when a
  scan is paid.
- Area: columns from the Level's minimum to maximum build height, over
  `(2 × radius / cell)²` ≤ 576 cells.
- Limits: one job per player, four jobs on the server, a 100-tick cooldown per
  player, and ≤ 16,384 block-state reads per tick per job, on the main thread. A
  worst-case job takes about 216 ticks.
- Chunks: only chunks returned by `getChunkNow` are read; the other cells are
  `UNKNOWN`. The job is cancelled if the player logs out, changes Level or moves
  more than 64 blocks.
- Result per cell: the ratio of `#forge:ores` blocks to non-air blocks, scaled to
  0..65,535, and the dominant biome (palette ≤ 16 IDs). It is sent in one S2C
  packet of ≤ 8 KiB (§10). Results are transient and grant nothing.
- The job limit, cooldown and read budget are server config, capped at these values.

### 9. Solar receiver

A new **microwave receiver** block has four chip slots and a `receiver_id`.

- **Link.** Inserting a `solar` satellite's bound chip claims the link
  (satellite → receiver UUID) in the registry at the receiver's next 20-tick
  check, unless the satellite is already linked. The **existing holder keeps the
  link** until one of its own checks finds that it no longer holds the chip, or
  until it is removed. A duplicated chip in a second receiver therefore does not
  take the link and produces nothing.
- **Unlink.** Breaking the receiver clears the links it holds. At a terminal with
  the chip, the owner may unlink a satellite whose receiver is missing (its
  chunk is loaded and has no receiver with that ID). Operators may unlink by ID.
- **Output.** While loaded and linked, the receiver produces
  `Σ power × solar_intensity(orbit_body) × output_multiplier_percent / 100` FE/t.
  The value is floored and capped at 10,000 FE/t. It goes into a 100,000 FE
  buffer that pushes to neighbours. There is no offline banking, so downtime
  produces nothing and nothing is caught up. Only link changes write to the
  registry.
- **Root schema 1:** four slots, energy, `receiver_id` and a format marker. It
  has the ADR-029 preflight bounds, unknown-item quarantine and exactly-once
  raw-root carry.

### 10. Network and menus

| Surface | Change | Bound |
|---|---|---|
| Terminal menu extra data | `FORMAT_VERSION` 1 → 2 (marker −1 kept). Adds: up to 16 schema-2 definitions with kind and ≤ 16 launch targets; the player's ≤ 16 instances; ≤ 8 gas products for the selected body; ≤ 64 bound-mission summaries; ≤ 32 reward-buffer entries | ≤ 32,600 bytes, tested at maximum; version-1 readers reject it |
| Terminal buttons | New fixed IDs through `clickMenuButton`: select previous/next for kind, target, instance and product; start, claim, cancel, withdraw, decommission, unlink. Selection is server-side menu state; no packet carries an index | fixed-size |
| Builder menu | New menu type, extra data format 1: position and catalog generation | ≤ 64 bytes |
| Receiver menu | New menu type, extra data format 1: position, links and output | ≤ 1 KiB |
| Scan result | New channel `advancedrocketrycommunity:satellite`, protocol `1`, S2C only | ≤ 8 KiB |

Existing channel versions (life support 1, celestial 3, rocket flight 8, rocket
visual 1) are unchanged. As with the existing channels, a client without the new
channel cannot join. All intents are validated on the main thread: player,
distance, loaded chunk, menu binding, catalog generation, ownership and state,
with a 10-tick per-player rate limit.

Star map: the terminal names mission targets from the same celestial display
data (IDs, names, parents, discovery state) as the console star map (ADR-035).
v1.6 adds no star-map overlay; that is deferred to the v1.8 UI batch. A V1 check
compares the two views.

## Deferred and rejected

These are recorded in `PORTING_MATRIX.md`.

- Rocket satellite-bay deployment is **deferred** to v1.8. The terminal launch is
  the only deployment path, so satellites are not carried by rockets.
- The biome-changer (terraforming) satellite is **rejected for v1.6** and deferred
  with terraforming.
- The spy telescope, typed data units and the Observatory multiblock are not
  revived in v1.6; surveys replace the Observatory's asteroid analysis. The legacy
  chip-copy slot is not revived; each assembly binds a new chip.

## Migration and rollback

Registry, item and block-root schema changes are forward-only. A v1.5 host refuses
a v1.6 world in its pre-start validation, because of registry root 3. On v1.5,
builder and receiver blocks, and any chips inside them, become air. Downgrading
therefore requires the pre-upgrade backup. Data packs that use schema-1
definitions keep working.

## Verification

- A0: component and definition codec bounds; role and field consistency; slot
  layout; aggregation and caps in 64-bit; refusal order; the requirement matrix;
  launch re-validation; kind-parameter snapshots; schema-1 migration with the
  legacy label; item identity; worst-case record size.
- A1: builder happy path and every refusal; automation slot rules; launch of each
  kind and idempotent replay; lifetime-research and discovery gates; decommission;
  scan budgets, `UNKNOWN` cells and zero chunk tickets; receiver link holding
  against a duplicate chip, unlink paths and output.
- Maximum-size tests for the terminal menu and the scan packet; builder and
  receiver root quarantine and carry tests.
