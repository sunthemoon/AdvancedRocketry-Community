# ADR-057 — Black-hole generator

```yaml
status: PROPOSED
revision: 1
date: 2026-10-01
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.7.0
slices: [V170-BH-01]
development_dependency: ADR-016, ADR-031, ADR-041, ADR-043, ADR-044, ADR-054
```

## Context

The legacy black-hole generator works only on a station whose parent body is a
star flagged as a black hole. It consumes one item of any kind at a time from its
input hatches (1 tick for stone, dirt, netherrack and cobblestone; 500 ticks for
anything else) and produces 500 RF per tick, times an integer multiplier, while
burning ([audit §3](../work/v1.7.0-legacy-audit.md)). This project's celestial
schema has no star or black-hole flag (ADR-043), and stations orbit only
orbitable bodies.

## Decision

### 1. Singularities without a celestial schema change

A body becomes a singularity through separate data,
`data/<namespace>/singularities/<path>.json`, schema 1, strict (≤ 4 KiB per file,
≤ 16 files):

| Field | Bound |
|---|---|
| `body` | a body ID; unique across files |
| `output_fe_per_tick` | 1..2,048 (legacy 500) |
| `fuel_table` | the ID of a fuel table (§2) |

The celestial schema (2), its codec, the client snapshot and every persisted ID
are unchanged. Singularity files reload with the catalog like ADR-052 §2 (an
invalid reload keeps the last complete set; an invalid initial load fails
loading). A profile applies only while its body exists in the live catalog, is
orbitable and is not landable; otherwise the generator reports
`NO_SINGULARITY`, and nothing is rewritten.

**Example content (C12).** A new example system, like Tau Ceti (ADR-043):
`advancedrocketrycommunity:cygnus_x1`, a root body with no Level, not landable,
**orbitable**, solar intensity 0, no discovery requirement (a public object, so it
can never be locked), with placeholder values inside the schema bounds (gravity
4.0, a vacuum atmosphere profile). Stations reach it only by interstellar warp
(ADR-044, 8,000,000 FE by default), which is the endgame gate. It has no rocket
routes. A `singularities/cygnus_x1.json` profile with 500 FE per tick uses the
`default` fuel table. The 16-root limit (ADR-043) has room. Packaging goes into a
new `src/generated/v1.7/resources` directory under the existing
one-authoritative-copy audit.

### 2. Fuel

`data/<namespace>/black_hole_fuels/<path>.json`, schema 1, strict (≤ 8 KiB,
≤ 16 tables): `default_burn_ticks` 1..72,000 and `entries`, 0..64 entries
{`item` existing item, `burn_ticks` 1..72,000}, unique items. The built-in
`default` table reproduces the legacy values: stone, cobblestone, dirt and
netherrack burn 1 tick each, and every other item burns 500 ticks.

Only **plain** items (no item tag) are accepted as fuel, so a container item,
a named tool or a written book is never destroyed with its contents. Insertion
of anything else into the fuel slots is refused.

### 3. Structure and state

- A structure-only multiblock (ADR-054 §2.1): controller
  `advancedrocketrycommunity:black_hole_generator`, pattern
  `machine_patterns/black_hole_generator.json`, at most 5 × 5 × 5, four
  rotations, no mirror; community-authored blocks.
- It operates only in the Space Level inside a committed station region whose
  station's **live** orbit body has a singularity profile, with the station
  registry operational. Otherwise it idles: `STATION_UNAVAILABLE` or
  `NO_SINGULARITY`. A warp away pauses it; a warp back resumes it.
- The controller root holds the fuel buffer (9 slots, insertable by automation,
  plain items only), the burn state `remaining_ticks` and `burn_rate`
  (FE per tick fixed when the current item started), and an energy buffer of
  2,000,000 FE. Fuel, burn state and energy are in **one** block entity, so one
  chunk save covers consumption and generation (ADR-054 §8).

### 4. Generation

Each tick, for an eligible, formed, enabled generator:

1. If `remaining_ticks = 0`, the energy buffer is not full and the fuel buffer is
   not empty: consume **one** item from the first non-empty slot,
   `remaining_ticks = burn_ticks(item)`,
   `burn_rate = output_fe_per_tick × energyPercent / 100`.
2. If `remaining_ticks > 0` and the buffer has room for `burn_rate`: add
   `burn_rate`, `remaining_ticks −= 1`. If the buffer has no room, the burn
   **pauses** (nothing is consumed and nothing is wasted; the legacy kept burning
   into a full buffer).
3. Push up to 20,000 FE per tick in total to adjacent energy receivers, in a
   fixed face order.

`endgame.blackHoleGenerator.energyPercent` is 10..400 (default 100), so output
never exceeds 8,192 FE per tick per generator. Energy is created only from
consumed fuel, never offline and never from an empty buffer. A disabled system
or an ineligible location pauses the burn with its state kept.

### 5. Limits

At most `endgame.blackHoleGenerator.activePerOwner` (≤ 4) and
`activeGlobal` (≤ 64) formed generators burn at once; others idle with
`ACTIVE_LIMIT`. The pass costs O(1) per generator and touches no other block
than the push targets.

### 6. Menus, visuals and audit

- Menu: fuel slots, energy, output rate, remaining burn, the orbit body and
  status (`GENERATING`, `PAUSED_FULL`, `NO_SINGULARITY`, ...). No buttons are
  needed beyond the ADR-054 status view; no intent changes generation.
- Visuals: the controller's update tag carries `generating`; the client draws one
  bounded accretion-disc effect (one quad mesh, ≤ 512 vertices, no particles
  beyond 8 per tick). The singularity in the per-station sky uses an existing
  visual profile; a dedicated black-hole sky is deferred. V1 is `[H]`.
- Audit: state changes (start, pause, resume, ineligible) and a summary every
  1,200 ticks (items consumed, energy produced).

### 7. Threat model

| Threat | Control |
|---|---|
| Infinite energy | Energy only from consumed fuel; bounded rate and buffer; pause when full |
| Duplication | Consumption and generation in one block entity |
| Destroying valuables | Plain items only; containers and tagged items refused |
| Grief | Touches nothing outside its own block entity and push targets |
| Server load | O(1) per generator; active limits |
| Data edits | Strict bounded data; missing body fails closed |

## Deferred and rejected

Recorded in `PORTING_MATRIX.md`: a black-hole sky renderer and an accretion
spectacle beyond the bounded effect are **deferred** to the v1.8 visual batch.
Star-type flags in the celestial schema are **not** introduced.

## Verification

- A0: singularity and fuel codecs and reload rules, eligibility, the burn state
  machine with pause-when-full (reference vectors), rate bound, plain-item rule,
  limits.
- A1: a station warped to the example system generates; a station elsewhere
  idles; warp away pauses and back resumes; full buffer pauses without
  consuming; push to an adjacent receiver; tagged item refused; disabled switch;
  zero tickets.
- S1 (C13): restart mid-burn keeps `remaining_ticks` and energy.

## Rollback

Disable by config; burn state stays. Removing the example data leaves stations
orbiting a missing body, which is the existing unknown-orbit-body case (ADR-044).
