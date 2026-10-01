# ADR-058 — Area gravity controller

```yaml
status: PROPOSED
revision: 2
date: 2026-10-01
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.7.0
slices: [V170-GRAV-01]
development_dependency: ADR-040, ADR-041, ADR-046, ADR-054
amends: ADR-041 (adds the area-field layer in front of the station position override)
```

## Context

The legacy game has two gravity machines ([audit §4](../work/v1.7.0-legacy-audit.md)):

- the **station gravity controller**, a block that ramps the station's gravity to
  a slider or redstone value and broadcasts a packet to every player on each
  change;
- the **area gravity controller**, a small multiblock that every tick fetches
  every entity in a cube of radius 10..32, adds motion per enabled face (push or
  pull in any direction), resets fall distance and spawns particles.

This project already applies gravity to players through Forge's synchronized
`ENTITY_GRAVITY` attribute: `CelestialGravityController` takes a position
override (the station region, ADR-041) and falls back to the Level profile.
Station gravity is already settable by owners and operators with
`/arce station gravity` through a checked commit (ADR-041).

## Decision

### 1. Station gravity stays a command

The station-wide value remains ADR-041's checked command. The legacy station
gravity controller block is **deferred** to the v1.8 UI batch together with
ADR-046's station screens; it would add a second write path to the same checked
station record.

### 2. Area gravity field device

A single block, `advancedrocketrycommunity:gravity_field_controller`, with a
block entity holding: `running`, redstone mode (`IGNORED`, `ON`, `INVERTED`,
default `IGNORED`), radius `r`, target multiplier `m` in hundredths, and an
energy buffer of 50,000 FE (input ≤ 1,000 FE per tick).

| Setting | Range | Step | Default |
|---|---|---|---|
| radius `r` | 2..16 | 1 | 8 |
| multiplier `m` | 10..200 (0.10 g .. 2.00 g) | 5 | 50 |

The **field box** is the cube `[x − r, x + r] × [y − r, y + r] × [z − r, z + r]`
around the controller. Inside a committed station region the box is clipped to
the region's horizontal bounds; an empty result is `FIELD_OUTSIDE_STATION`.

The multiplier applies immediately, with no legacy ramp; settings changes are
already spaced by the ADR-054 §4 intent rate.

### 3. Activation

A field is **active** while all of these hold, re-checked on every settings
change, every activation and every 200 ticks:

1. the system is enabled, the device is owned, `running` is set and the redstone
   mode is satisfied;
2. authority, checkable while everyone is offline: outside stations the device
   is owned; inside a station region the device owner must be the station's
   **current owner** (`STATION_OWNER_REQUIRED`), because the field overrides the
   station's own gravity. Intents inside a station need station
   `MANAGE_STATION` (the station owner or an operator); an operator who places a
   field in someone else's station assigns it to the station owner with
   `device owner` (ADR-054 §13) before it can run;
3. the ADR-054 §5 chain steps 2–6 pass for the box with effect
   `ENTITY_GRAVITY` (bounds, zones, stations, spawn protection, the API event).
   Step 1 does not apply because no chunk is modified; the field acts only on
   players present in loaded chunks;
4. the energy buffer pays the upkeep for this tick: `5 + 2 × r` FE per tick
   (at most 37);
5. the index has room (§4).

Otherwise the field is inactive with the code of the first failing condition
and is removed from the index in the same tick.

### 4. Field index and lookup

Active fields are kept in a runtime index, never persisted (the block entity
holds the settings, and the index is rebuilt as controllers load and tick):

- per Level, keyed by chunk: a field is listed in every chunk its box touches
  (at most 3 × 3 chunks, because a 33-block span covers at most 3 chunks);
- at most 16 fields per chunk and at most 4 of one owner per chunk
  (`FIELD_DENSITY`, so one player cannot fill a bucket next to someone else's
  base), 8 active per owner, 256 per Level and 1,024 per server
  (`ACTIVE_LIMIT`);
- removed on deactivation, block removal, chunk unload, Level unload, server
  stop and the system switch turning off (the whole index is cleared that tick).

`CelestialGravityController` keeps its single position-override function; the
integrator composes it as **field, then station region, then Level profile**:

```text
at(level, pos) = fieldIndex.at(level, pos)
                 .or(stationRegion.at(level, pos))      (ADR-041, unchanged)
```

`fieldIndex.at` reads only the player's chunk bucket (≤ 16 candidates), keeps the
fields whose box contains the player's block position **and that affect this
player** (§5), and returns `m / 100` of
the one with the smallest box volume, ties broken by the lower `device_id` in
ADR-054 §7 ID order. It runs in the existing living-tick hook for `ServerPlayer`s,
so its cost is one bounded lookup per player per tick. The hook already covers
every `ServerPlayer`; spectators receive the modifier but do not fall.

The modifier stays transient (`addTransientModifier`), so nothing about a field
is saved with a player; leaving the field, a relog or a disabled system returns
the station or Level value on the next tick.

### 5. Scope of effect

- **Players only**, through the attribute. Values stay inside the existing
  0..4.0 bound.
- **Consent** (review R1-M9). Vanilla jump physics make the range harmful to
  people who did not choose it: from 1.40 g a player can no longer jump onto a
  full block (2.00 g: 0.77 blocks), so a pit or a one-block step can trap them,
  and at 0.10 g a jump peaks near 6.9 blocks and costs about 4 HP of fall
  damage. Therefore:
  - inside a committed station region the field affects every player in its
    clipped box, as the station's own gravity does: it is the station owner's
    space and the field must belong to the station owner (§3). Its effective
    value there is capped at 1.00 g, the top of ADR-041's station command range,
    so a field can never impose more than the station owner could already set
    for the whole station (review R2-L6);
  - everywhere else a field affects only its owner and players who **trust**
    that owner (review R2-M1): consent is the affected player's own choice. A
    player manages it with `/arce endgame field trust <player>`, `field untrust
    <player>` and `field trusted`, from their own connected command source only;
    the list holds at most 32 owner UUIDs and is stored under
    `Player.PERSISTED_NBT_TAG` in the player's persistent data, which Forge
    keeps across death. Untrusting takes effect on the next tick. Everyone else
    keeps the station or Level gravity inside the box.
- Mobs, items and projectiles are **deferred** (v1.8 matrix): the attribute does
  not cover non-living entities, and per-entity motion scans are what made the
  legacy controller expensive.
- Directional push or pull per face is **rejected**: it lets one player move
  others against their will, can trap them, and desynchronises client motion.
- Fall distance is not modified; fall damage behaves as under any other
  gravity value in this project.

### 6. Menus, visuals and audit

- Menu: radius ±1, multiplier ±5, running, redstone mode, energy, status and the
  effective clipped box size. Coordinates are shown only to the owner, the
  station owner and operators.
- Visuals: the update tag carries `active`, `r` and `m`; a client inside a
  loaded active field sees at most 4 subtle particles per tick, none when
  particles are minimal. The box is not outlined for others. V1 is `[H]`.
- Audit: activation, deactivation with code, settings changes with old and new
  values.

### 7. Threat model

| Threat | Control |
|---|---|
| Trapping or hurting players (2.00 g blocks a one-block jump, 0.10 g jumps cost fall damage) | Outside stations only the owner and players who trust the owner, by their own command, are affected; inside stations only the station owner's own fields, capped at 1.00 g; no lateral force; box ≤ 33³ |
| Overriding a station's gravity without consent | `MANAGE_STATION` inside stations; box clipped to the region |
| Fields over spawn, zones or claims | Chain steps 2–6 including spawn protection, zones and the API event |
| Server load | Chunk-bucketed index, ≤ 16 candidates per lookup, active caps, no entity scans |
| Crowding someone else's fields out (`FIELD_DENSITY`) | At most 4 fields of one owner per chunk |
| Persistent side effects | Transient modifier only |
| Information leak | Box and coordinates only in the owner's and operators' menus |

Residual: a claim mod that listens to neither block events nor
`EndgameEffectEvent` cannot veto a field on its land; outside stations such a
field affects only players who consented, and zones and spawn protection
still apply.

## Deferred and rejected

Recorded in `PORTING_MATRIX.md`: the station gravity controller block
(**deferred**, v1.8 UI batch); fields on non-player entities (**deferred**);
directional thrust (**rejected**); the legacy `IGravityManager` API (**deferred**,
reconsidered with the v1.8 matrix).

## Verification

- A0: settings bounds and steps, box and clipping, activation order, upkeep,
  consent rule and jump-height thresholds (reference vectors), per-owner density,
  arithmetic, index caps and buckets, winner selection with nested, equal and
  overlapping boxes (reference vectors), composition with the station override.
- A1: on a planet, the owner and a player who trusts the owner get the field's
  value and another player in the same box keeps the Level gravity; `field
  trust|untrust` changes only the issuing player's own list and survives death;
  on a station, every player in the box gets the value capped at 1.00 g; a member
  cannot activate on a station, the station owner can; spawn-area and zone
  refusals; ownership transfer of the station deactivates a field owned by the
  previous owner; API cancellation; out of energy drops the field the same tick;
  chunk unload removes it; disabled switch restores gravity; relog keeps no
  modifier; zero tickets.
- C13: lookup cost with 256 fields in one Level and 20 players.

## Rollback

Disable by config; the attribute returns to station or Level gravity on the next
tick. Removing the code turns the blocks into air.

## Review history

- Revision 1 (`10e3d2d`): independent contract review round 1 asked for changes.
- Revision 2 answers it, together with root findings S1–S4 and finding F02 of the
  external v1.3–v1.6 deep-test report, one commit per finding; see
  [review-01-dispositions](../work/v1.7.0-preparation/review-01-dispositions.md).
