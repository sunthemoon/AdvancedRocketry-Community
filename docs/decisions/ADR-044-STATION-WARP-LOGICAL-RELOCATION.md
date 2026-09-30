# ADR-044 — Station warp as logical orbit relocation (revision 2)

```yaml
status: PROPOSED
revision: 2
date: 2026-09-30
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.5.0
development_dependency: ADR-040, ADR-041, ADR-043
amends: ADR-040 (warp fields; "no speculative warp fields yet"), ADR-041 (adds the named transition "orbit relocation")
implements: V150-WARP-01 contract for WARP-02..05 and UI-01..03
station_registry_schema: 4 (proposed; record 2 and reservation 1 unchanged)
```

Revision 1 was rejected by independent contract review (three High findings:
energy paid after the commit, core identity, and the rocket rule). This revision
replaces the energy mechanism and the rocket rule; the relocation architecture
is kept.

## Context

The v1.5 plan asks for a costed, multi-stage, recoverable warp with a warp core,
energy or fuel, a target and a countdown. Passengers and docked rockets must be
kept, and assets must survive failure. Stations are fixed cells in one shared
Space Level; their context comes from `orbit_body` (ADR-040/041). A physical copy
would move up to 768×768 columns of blocks and entities across Levels, which is
unbounded, and ADR-040 forbids scans. The rocket transfer journal
(`RocketTransferSavedData`) records every flight in motion, from `PREPARED`
through `COMMITTED`. Committed records are kept as landed reservations until the
rocket launches again or is disassembled.

## Decision

### 1. Logical relocation

Warp changes only the station's `orbit_body`. Cell, region, pad, blocks,
entities and players stay where they are. Environment, gravity, routes, quotes
and discovery follow on the next query. No block, chunk or entity is moved,
loaded, scanned or queried. The plan's "target copy / source delete" wording is
resolved as relocation, which plan section 17 allows.

Disclosure: every system is a label over the same Space Level, and cells of
different systems are separated by at least 256 unclaimed columns. Players,
blocks and disassembled rockets can physically cross that gap. Warp buys an
orbit context (environment, routes, quotes), not physical isolation.

### 2. Energy lives in one store: the station registry

- New registry root field `warp_energy`: a list of `{station_id, energy}`, one
  entry per station with a non-zero balance, stored in the same file as the
  station records. Each balance is a long from 0 to 10,000,000 FE. The list has
  at most 4,096 entries and each entry at most 64 bytes.
- **The warp core is a stateless terminal.** Block `advancedrocketrycommunity:warp_core`
  has a block entity only to expose the Forge Energy capability. It stores no
  energy, sequence or identity. `receiveEnergy` credits the station whose
  committed region contains the core, looked up with one indexed `findAt`. In a
  gap, in a blocked registry or in a station at capacity it accepts 0. There is
  no extraction. Crediting is an ordinary in-memory registry mutation (dirty,
  saved by ordinary saves, like membership). It is not a checked write. At most
  200,000 FE per station per server tick are accepted, tracked in memory per
  tick.
- The balance is kept outside `StationState`, so charging never makes an
  expansion or gravity confirmation stale.
- **Warp commit is one checked write.** It debits the cost from the balance and
  sets the new `orbit_body` in the same candidate registry, through the ADR-041
  checked path (validate, stage, force, read back, atomic replace, publish). This
  adds exactly one named transition to ADR-041, **orbit relocation**: only
  `orbit_body` changes on the record, the station's balance falls by exactly the
  cost, and nothing else changes. The same guard rejects any other change before
  writing.
- Cores have no identity, so any number of cores in one region feed the same
  balance. A core in a gap or a foreign station credits nothing, or credits the
  station it is actually in. It drops as a plain item (loot: itself, with no
  block-entity data). Station deletion discards the balance (audited). A reused
  cell starts at zero, and a core carried over carries nothing.
- Cost (server config, bounds 0..10,000,000, defaults): 2,000,000 FE within a
  system, 8,000,000 FE between systems or away from an unavailable orbit body.
  The cost is computed from the catalog captured at commit, never from the client.

**Crash-cut matrix.** The only warp write is the single atomic replacement:

| Cut | Result |
|---|---|
| Before the checked replacement | Old orbit, old balance: no warp, no charge |
| During replacement (caught failure) | Old file kept or restored (ADR-041): no warp, no charge |
| Replacement outcome unreadable | Updates quarantined; the acknowledged (old) authority is re-saved; no warp, no charge |
| After replacement | New orbit, debited balance: warp charged exactly once |
| Countdown in progress (memory only) | No warp, no charge |

Charging has the usual cross-store rollback class of Forge Energy between
separately saved stores: a crash can re-supply or lose at most the energy
credited since the last save of the registry or of the energy source. This is
bounded by 200,000 FE per tick times the autosave interval. The warp itself
cannot be duplicated or made free.

### 3. Entry point, confirmation and countdown

- `/arce station warp <body_id>` must be run by the connected player's own
  non-silent command source (ADR-040/041 local-actor rule). The player must be
  the owner or an operator, stand in the committed region with their chunk
  loaded, and be **looking at a warp core** in that region (server-side ray pick
  within 5 blocks; no client coordinates). The reply names the station, the
  current and target body, whether the move is within a system or interstellar,
  the cost and the balance. It warns that docked rockets move with the station
  and that the warp cannot be undone for free. It then requires
  `/arce station warp confirm <station_id>` within 10 seconds (one-shot, bound to
  the actor, the observed station state and the target, like expansion).
- Confirmation starts a **countdown** of 200 ticks, reported to online members.
  Only one countdown per station is allowed, with at most 64 globally. Countdowns
  are memory-only. `/arce station warp cancel` (owner or operator, local) cancels
  one. A countdown is discarded at server stop; the owner logging out does not
  cancel it.
- **At commit**, on the server thread, the countdown aborts with a reason if any
  of these fails:
  - warp is enabled;
  - the registry is operational and not quarantined;
  - the station still equals the confirmed state (`STALE` on any change);
  - the target is still present, orbitable, known and different from the current
    orbit;
  - the balance covers the cost from the commit-time catalog;
  - the rocket rule (§4) passes.
  Otherwise it performs the checked write.
- Allowed targets: any orbitable, known body (ADR-043) other than the current
  orbit, in the same system (relocation) or another system (interstellar). A
  station whose orbit body is unavailable may warp to any valid target
  (evacuation, interstellar cost).
- **Kill switch:** server config `stations.warpEnabled` (default true). When it is
  false, requests, confirmations and pending commits are refused. Charging still
  works. Stations and their access are unaffected.

### 4. Rockets and passengers

- **Passengers** stay in place and see the new context. There is no teleport.
- **Docked rockets** move with the station. For a `TravelTarget.Station` source,
  launch admission takes the source body from the station record, and no longer
  requires the rocket's saved `current_body` to equal it. These existing checks
  are kept:
  - `currentTarget() == Station(id)`;
  - the station found at the rocket's position has that ID;
  - the live Level equals `currentDimension` and matches the snapshot's source
    dimension.
  New flight and transfer records written at launch store the station's current
  orbit body as `current_body`. A plan quoted before a warp is stale: admission
  requires the plan's source anchor to be the station's current orbit anchor,
  otherwise the player re-quotes.
- **Rule for rockets in motion** (in memory, no entity query, no chunk load),
  checked when the warp is requested and again in the same server operation as
  the commit. Warp is denied if:
  1. the transfer journal is not operational (fail closed); or
  2. a record whose source or destination snapshot overlaps the region is in
     `PREPARED`, `DESTINATION_SPAWNED`, `PASSENGERS_TRANSFERRED` or
     `SOURCE_REMOVED`; or
  3. a `COMMITTED` record overlapping the region is still descending: game time
     is below its destination state start plus `DESCENT_TICKS` plus 20.

  Landed reservations (other `COMMITTED` records) do not block. Invariant, to be
  tested including migrated legacy flights: every flight from countdown through
  descent has a journal record. Assembly transactions do not block; assembly
  derives its target from position when it completes.
- The existing fail-open in station deletion (journal not operational) is fixed
  in the same slice. Deletion also fails closed.
- **Module direction:** warp code reaches rocket state only through a port
  interface in the station module (`StationRocketAuthority`), implemented by the
  rocket module and wired at startup. This follows the ADR-041 pattern and adds
  no new cross-module imports. The existing station↔rocket import cycle
  (`StationManager` imports rocket classes, rocket classes import station
  classes) is recorded as pre-existing technical debt.

### 5. Persistence and migration

- Registry root schema 4, epoch `v1.5.0-station-warp`, requires `warp_energy`.
  Records stay schema 2 and reservations schema 1. Root 3 migrates to 4 by adding
  an empty `warp_energy` list, through the existing pre-start backup migration
  (ADR-040), recorded per file in the manifest. Legacy roots 1 and 2 chain to 4.
  Root 4 accepts only record 2 and reservation 1. A balance list with duplicates,
  unknown stations, a negative or over-bound balance, or too many entries blocks
  the whole registry, fail-closed as before. Older builds refuse root 4. Restore
  uses the complete pre-upgrade backup.
- The warp core block entity has schema version 1 with no other fields.
- No journal file: the countdown is memory-only and the commit is one atomic
  write. The plan's "journal" maps to this state machine: `REQUESTED` (memory),
  `COUNTDOWN` (memory), `COMMITTED` (one write), plus audit log lines
  `ARCE_STATION_WARP phase=... station=... target=... cost=... balance=...`.
  `/arce station admin inspect` shows the balance.

### 6. Sky, UI and elevator

- The sky after a warp is the generic Space sky until ORBIT-03, which has its own
  ADR.
- A warp screen (UI-01) needs its own bounded, versioned payload. It is optional
  on top of the commands.
- The elevator is out of scope here.

## Plan traceability (sections 9–15)

| Plan item | Where |
|---|---|
| WarpState legal/illegal transitions; each stage recoverable; resources charged once | WARP-02 unit tests; the crash-cut matrix above as a JUnit fault-injection matrix |
| Multi-star routes and discovery requirements | STAR-02/03 (ADR-043) |
| Two stations warping at once to different targets | WARP-05 GameTest and MIG-02 native |
| Owner disconnect/reconnect; passengers online/offline | WARP-04 GameTest (countdown survives logout; offline player data untouched) |
| Docked rocket saved and restored across a warp | WARP-04 GameTest and native |
| Catalog reload or target removal during countdown aborts safely | WARP-03 GameTest |
| Non-member, forged or stale requests; no client energy or cost | UI-03 and WARP-03 tests (local-actor rule, server-computed cost) |
| No arbitrary chunk loading; bounded work | Design (§1, §4); UI-03 checks loaded-chunk counts |
| 10/100-station performance; warp preparation bounded per tick | ORBIT-04 scale test (checked write), WARP-05 |
| Real-client cache and position before/after warp; sky; multiplayer; video | V1/V2 in ACC-02 (not available in this environment; stays open) |
| Warp can be disabled independently | `stations.warpEnabled` (§3) |

## Non-goals

Physical copies, other Levels, warp without a core or energy, remote initiation,
fuel items, per-system time, physical isolation of systems, space-elevator
logistics, and warping rockets or players separately from a station.

## Rollback

Warp commits are ordinary root-4 registry changes. Older builds refuse root 4.
Restore the complete pre-upgrade backup (ADR-040). No other migration exists.
