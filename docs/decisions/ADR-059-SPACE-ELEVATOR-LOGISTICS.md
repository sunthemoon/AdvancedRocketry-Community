# ADR-059 — Space-elevator logistics

```yaml
status: PROPOSED
revision: 1
date: 2026-10-01
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.7.0
slices: [V170-ELEV-01]
development_dependency: ADR-040, ADR-041, ADR-044, ADR-045, ADR-046, ADR-054
amends: ADR-044 (warp request, confirmation and commit refuse a bound station), ADR-045 (resolves its open decisions; supersedes its "named station transitions" and "journaled passenger transport" constraints as stated in §5 and §8)
```

## Context

[ADR-045](ADR-045-SPACE-ELEVATOR-ENDPOINT-CONTRACT.md) shipped a pure endpoint
validator (five ordered rules) and an operator diagnostic in v1.5, and handed v1.7
constraints: unbinding always possible, a warp interlock, a deletion guard,
journaled transport that never loads the far end from a client position, fail
closed and preserve on catalog changes, validity re-derived from live state, and
four open decisions (storage, observed Level key, pair UUID, cardinality).

The legacy elevator is a 9×9 multiblock at both ends; players stand on a capsule
for 200 ticks, pay 50,000 energy, ride a capsule entity to `orbitHeight`, and
change dimension, with dimensions initialised on demand and a server-side null
dereference reachable from a client button ([audit §5](../work/v1.7.0-legacy-audit.md)).

## Decision

### 1. ADR-045 open decisions

- **Storage:** the `elevator_pairs` section of the endgame root (ADR-054 §10).
  The station record and its schema are unchanged.
- **Observed Level key:** stored at bind. A later catalog mapping of the body to
  another Level makes the pair invalid (`PAIR_LEVEL_CHANGED`), preserved.
- **Pair UUID:** `pair_id`, random at bind, never reused.
- **Cardinality:** at most one pair per station, per anchor endpoint, per
  terminal endpoint, and per anchor column `(Level, x, z)`. At most 1,024 pairs.

Pair record (≤ 512 bytes): `pair_id`, `station_id`, `terminal_id`, `anchor_id`,
`body_id`, `level_key`, `x`, `z`, `anchor_y`, `bound_at`, `bound_by`.

### 2. Endpoints

- **Anchor** (`elevator_anchor`): a structure-only multiblock (ADR-054 §2.1),
  controller `advancedrocketrycommunity:elevator_anchor`, pattern
  `machine_patterns/elevator_anchor.json`, at most 7 × 3 × 7, built on a body's
  surface Level. Its column `(x, z)` is the controller's.
- **Terminal** (`elevator_terminal`): a single block
  `advancedrocketrycommunity:elevator_terminal` inside a committed station
  region, not within 2 blocks horizontally of the ADR-040 landing-pad column
  (`TERMINAL_ON_PAD`), so landing stays unobstructed (ADR-045).
- Both hold the ADR-054 §11 transit state, a cargo input buffer of 4 slots
  (insertable), a receive buffer of 9 slots (extractable), and an energy buffer
  of 500,000 FE (input ≤ 20,000 FE per tick). Their **platform** is the 3 × 3
  area directly on top of the endpoint block.

### 3. Bind

From the terminal's menu, by the station owner or an operator (ADR-045 rule 2),
selecting one of the actor's own `ACTIVE` anchors (operators: any anchor). The
server checks, in order, and reports the first failure:

1. ADR-045 rules 1–5 for `(station, anchor body, x, z)`, with their existing
   `ElevatorEndpointCode` results, where the anchor body is derived from the
   anchor's Level (ADR-054 §9);
2. the terminal is `ACTIVE` and inside this station's committed region
   (`TERMINAL_UNAVAILABLE`);
3. the anchor is `ACTIVE` (`ANCHOR_UNAVAILABLE`) and owned by the actor, unless
   an operator binds (`ANCHOR_FOREIGN`);
4. cardinality (`STATION_BOUND`, `ANCHOR_BOUND`, `TERMINAL_BOUND`,
   `COLUMN_BOUND`);
5. no warp confirmation or countdown is pending for the station
   (`WARP_PENDING`);
6. root admission (ADR-054 §10).

Then the pair is written with a **barrier flush** before success is reported,
and audited. Concurrent binds are serialized on the server thread; the second
sees the first's pair.

**Validity at use** is re-derived each time from live state, never cached: ADR-045
rules 1, 3, 4 and 5 (ADR-045's owner-only rule 2 is replaced, per action, by
ADR-054 §3's overrides, so members can ride and automation can ship), the stored
`level_key` equal to the body's current Level, and both endpoints `ACTIVE` in the
index. A failure reports the rule and changes nothing; a missing body, a
remapped Level or an absent Level leaves the pair preserved and invalid.

### 4. Unbind

Always possible, whatever the pair's validity, for the station owner, the
anchor's owner or an operator: from either endpoint's menu, or with
`/arce endgame elevator unbind <station_id>` (connected player's own source, as
ADR-046). It never loads the far end, cancels pending rides of the pair, and is a
barrier flush. Cargo already escrowed or in transit is endpoint-addressed and
still completes (ADR-054 §11).

### 5. Warp interlock and station deletion

- ADR-044's request, confirmation and commit each refuse with `ELEVATOR_BOUND`
  while a pair names the station; evacuation warps too. Because unbinding is
  always possible, a bound station is never stranded.
- The commit check reads the live pair set on the server thread immediately
  before the checked relocation, in the same tick; a bind cannot interleave, and
  binds are refused while a warp is pending (§3 step 5). Because the binding is
  outside the station record, this **supersedes** ADR-045's plan to add "bind
  pair" and "unbind pair" as station transitions: no station-record transition
  or schema change is needed.
- **Fail closed** (review R1-M6): the station module reaches the pair set only
  through an `ElevatorStationGuard` port installed at startup, so it gains no
  import of the endgame module (AGENTS §3.4). Until it is installed, and while
  the endgame root is not operational (ADR-054 §10), warp request, confirmation
  and commit, evacuation warps and station deletion are refused with
  `ENDGAME_UNAVAILABLE`, as `StationRocketAuthority` fails closed for rockets
  (ADR-044 §5). The guard does not depend on `endgame.spaceElevator.enabled`: a
  disabled elevator still has pairs that pin the station.
- Crash order: a bind is durable before it is reported; a warp commit is the
  station's own checked write. If a crash loses an unbind and the station was
  warped afterwards, the pair is invalid by rule 3 at the next use (fail closed),
  and unbind remains available.
- `/arce station admin delete` fails closed with `ELEVATOR_REFERENCES` while a
  pair names the station, or while a transit record's source or destination is
  an endpoint inside the station's region (the rocket guard pattern, ADR-044 §5).

### 6. Cargo

Cargo uses the ADR-054 §11 ledger between the two endpoints of one pair. The
destination is always the other end of the departing endpoint's pair; the pair
must be valid at escrow. Delivery is endpoint-addressed, so it completes even if
the pair is unbound or invalid afterwards.

- Payload: up to 4 stacks, the whole cargo input buffer, at most one launch per
  endpoint per 20 ticks and `endgame.spaceElevator.launchesPerTick` (≤ 4) on the
  server, by a `launch` intent or `auto` with a redstone mode as ADR-056 §4.
- Cost `20,000 × energyPercent / 100` FE, travel 200 ticks
  (`endgame.spaceElevator.energyPercent` 10..1,000, default 100).

### 7. Who may ride or ship

ADR-054 §3's per-action overrides apply (review R1-M3): riding and shipping
need the station's `VISIT` access (owner, members, operators), the rocket rule,
and the anchor owner must be the station's current owner or a member
(`ANCHOR_OWNER_NOT_MEMBER`, operators exempt); binding needs the station owner
or an operator; unbinding is open to the station owner, the anchor owner and
operators.

### 8. Passenger rides

- **Request:** a `ride` intent from a player whose feet are on the departure
  endpoint's platform, with §7 access, not riding anything and carrying no
  passengers (`DISMOUNT_FIRST`), and no other pending ride. At most 4 pending
  rides per endpoint and 64 on the server.
- **Arrival pre-load** (review R1-M4): the request adds one region ticket of
  the dedicated type `advancedrocketrycommunity:elevator_arrival` at the arrival
  chunk, at distance 0 (the chunk becomes `FULL`, nothing around it ticks), with
  a 300-tick lifespan after which vanilla's ticket timeout removes it even if
  nothing else does. The position is the **server-derived** arrival position
  (the centre of the other endpoint's platform, from the pair record), never a
  client value; the chunk already exists because the endpoint was built in it,
  so it is read asynchronously from disk and nothing is generated. The ticket is
  removed at commit, at cancellation and at server stop. At most one per
  pending ride, so at most 64.
- **Countdown:** 100 ticks, shown to the rider. It is cancelled if the rider
  leaves the platform, disconnects, dies or changes Level, if the pair becomes
  invalid or unbound, or if the system is disabled.
- **Commit** (at most one per server tick; others wait and are rechecked):
  1. re-derive pair validity (§3) and the rider's access;
  2. the departing endpoint holds `50,000 × energyPercent / 100` FE (legacy
     50,000);
  3. the arrival chunk is `FULL` (`getChunkNow`); there is **no synchronous
     load**. If the pre-load has not finished, the commit waits, rechecking each
     tick, for at most 100 more ticks, then cancels with `ARRIVAL_UNLOADED`;
  4. the arrival block entity carries the recorded endpoint ID, and the two
     blocks above the platform centre are free of collision
     (`ARRIVAL_OBSTRUCTED`);
  5. ADR-054 §5 steps 2, 3 and 6 for the arrival box (the platform and the two
     blocks above it): bounds, protected zones and the public
     `EndgameEffectEvent` with effect `TELEPORT` (review R1-M5), else
     `TARGET_PROTECTED`. Step 4 is replaced by the §7 access rule; spawn
     protection does not apply because no block changes;
  6. then, in the same tick: teleport the player to the arrival position (fall
     distance reset by the teleport), and debit the departing endpoint only
     once the player is in the arrival Level at the arrival position. A
     teleport that another mod cancels (for example through Forge's dimension
     travel event) costs nothing.

  Any failure before step 6 changes nothing but releases the ticket and reports
  the code. Vanilla's own short `POST_TELEPORT` ticket follows any teleport and
  expires by itself; afterwards the player's presence keeps the chunk loaded.
- **No passenger journal.** A ride is one server-thread teleport of one player;
  vanilla stores the player's Level, position and inventory together in the
  player file, so a crash restores the player wholly at the departure or wholly
  at the arrival, and nothing can be duplicated or left between Levels. This
  supersedes ADR-045's "journaled transaction" constraint for passengers; cargo
  is journaled (§6). The energy debit and the player file are saved separately:
  a crash can cost one ride's energy without the ride, or give one ride without
  the debit. This is the energy-only residual named in ADR-054 §8.
- Per-player cooldown 200 ticks after a commit; `endgame.spaceElevator.ridesPerTick`
  is fixed at 1.

### 9. Menus, visuals and audit

- Terminal and anchor menus: buffers, energy, pair state with the failing rule,
  the far end (owner, station owner and operators only), auto and redstone mode,
  in-transit counts, receipts. Buttons: anchor previous/next (terminal only),
  bind, unbind, launch, auto, redstone mode, ride.
- Visuals: a tether beam from the anchor up to at most 384 blocks and from the
  terminal down, one per endpoint, drawn only while the pair is valid
  (update-tag flag). No capsule entity. V1 is `[H]`.
- Audit: bind, unbind, every refusal code change, escrow and delivery (ADR-054
  §11), ride request, cancel and commit with the rider, both endpoints and
  energy.
- ADR-045's `/arce station admin elevator check` stays as its unstable operator
  diagnostic; `/arce endgame elevator inspect <station_id>` (operator) shows the
  stored pair and its live validity.

### 10. Threat model

| Threat | Control |
|---|---|
| Teleporting into protected or unsafe places | Arrival only at the other endpoint's own platform, built by its owner under vanilla placement rules; obstruction check; zones and the `TELEPORT` API event at commit |
| Chunk loading from a client position | Arrival position is server-derived from the pair; one expiring pre-load ticket per pending ride (≤ 64, 300 ticks), released at commit or cancel; no synchronous load |
| Stranded or duplicated players | One-tick teleport; vanilla player file; no capsule entity |
| Cargo duplication or loss | ADR-054 §11 |
| Stranded station (warp) | Unbind always possible; warp refuses only while bound |
| Deleting a station with live references | Deletion guard |
| Unauthorised use | §7 access, rider on the platform, rate and cooldown |
| Catalog or Level changes | Fail closed and preserve |
| Server load | One ride commit per tick, launch caps, bounded pairs |

## Deferred and rejected

Recorded in `PORTING_MATRIX.md`: the capsule entity and animated ride (**deferred**
to the v1.8 visual batch); elevator chips with position lists (**rejected**);
station rotation and tether breakage (no station rotation exists; not revived).

## Verification

- A0: bind order with every rule failing, cardinality, validity re-derivation
  including a remapped Level, unbind authority, ride request and commit order,
  cost arithmetic, warp interlock decisions, deletion guard.
- A1: bind, ship cargo both ways, ride up and down; unbind with cargo in transit;
  warp refused while bound and allowed after unbind; deletion refused while
  bound; a remapped or missing body invalidates without deleting; obstruction
  refusal; a member can ride, a stranger cannot; disabled switch keeps unbind
  and arrivals working; after rides, cancellations, `ARRIVAL_UNLOADED` and a stop
  no `elevator_arrival` ticket remains.
- S2 (C13): forced stop during a ride countdown and right after a commit; cargo
  crash cuts as ADR-054 §11.

## Rollback

Disable by config; unbind and arrivals still work. Removing the code leaves the
pairs unused in `advancedrocketrycommunity_endgame.dat`; warp and deletion
guards disappear with it.
