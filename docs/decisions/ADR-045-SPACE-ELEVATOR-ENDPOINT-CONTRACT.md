# ADR-045 — Space elevator endpoint contract (v1.5 minimum)

```yaml
status: PROPOSED
date: 2026-09-30
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.5.0
development_dependency: ADR-040, ADR-041, ADR-043, ADR-044
implements: V150-ELEVATOR contract (minimal endpoint contract and validation)
```

## Context

The v1.5 plan (§3, §6.10, §12) asks only for a "basic space elevator connection
contract, possibly incomplete", with placeholder validation, and says no elevator
interface may expose dangerous write operations yet (§12.5). Full elevator
logistics are out of scope (§4). Nothing in the code models an elevator today.

Stations (ADR-040) are cells in the shared Space Level with a fixed landing pad,
and they orbit one body (ADR-041). Warp (ADR-044) can change that body at any
time. A surface anchor lives in a body's own Level, which may be unloaded,
unmapped, or missing after a data-pack change.

## Decision

### What an endpoint pair is

An **elevator endpoint pair** connects:
- a committed station (by `station_id`); its end is the station's landing-pad
  column (derived from the record, never supplied by a client);
- a **surface anchor**: a body ID and a block column `(x, z)` in that body's
  mapped Level.

A pair is **valid** when all of these hold, checked on the server in this order
with the first failure reported:
1. the station registry is operational and the station exists;
2. the body is the station's current orbit body;
3. the body is in the current catalog, landable, and mapped to a loaded server
   Level (`CelestialCatalog` / `PlanetarySurfaceResolver` rules);
4. the column is inside that Level's world border and within
   ±30,000,000 blocks; the border is read without loading any chunk;
5. the requester is the station's owner or an operator (permission level 2).

A check reads data only. It does not load or generate chunks, place blocks,
reserve anything, move players or items, or persist anything.

### What v1.5 ships

- A pure model (`ElevatorEndpoint` value, `ElevatorEndpointValidator` with
  result codes), plus an operator-only, read-only command:
  `/arce station admin elevator check <station_id> <body_id> <x> <z>`. It
  prints the first failing rule or `valid`. There is no player command, no
  network packet, no block, no item, no API method and no SavedData.
- Tests: every rule in order, bounds, no chunk loading (loaded-chunk count
  unchanged), and that a warp to another body makes a previously valid pair
  invalid (rule 2).

### Rules fixed now for a later implementation

These are part of the contract, not implemented in v1.5:
- A persisted pair will be owned by the station record (a later station schema)
  and will be removed with the station.
- A station with a bound pair may not warp. The warp commit gains one check
  (fail closed), because a tether cannot follow a relocation.
- Transport through a pair will be a separate, journaled transaction, like
  rocket transfers. It never teleports on a client request, and it never loads
  the far end's chunks from a client-supplied position.
- Missing body, unmapped Level or unloaded Level: the pair is reported invalid
  and nothing is deleted (fail closed and preserve).

## Consequences

- v1.5 can show that endpoint validation composes with orbit context,
  star-system and warp rules, without committing to storage, logistics or UI.
- No save-schema, network-protocol or API change; nothing to migrate or roll
  back.

## Non-goals

Elevator blocks or structures, climbers, transport of players, items, fluids or
energy, persistence, public API, player-facing commands, GUI, and warp
integration beyond the fixed rule above.

## Rollback

Remove the command and the model; no data exists.
