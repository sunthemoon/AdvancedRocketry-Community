# ADR-008 — Shared Space Level station regions and ownership

```yaml
status: ACCEPTED
date: 2026-09-01
deciders:
  - sunthemoon
target_version: v0.7.0
supersedes: "ADR-001 Space Level allocation outline only"
```

## Context

v0.7.0 must place many independently owned stations in the single fixed
`advancedrocketrycommunity:space` Level. Allocation must survive restart,
concurrent requests, failed platform generation, deletion, and administrative
recovery without overlapping another station or introducing dynamic dimension
identities. Clients must not select coordinates or decide access.

## Decision

### Identity and regions

- A station has a random UUID identity. It is not a dimension id and is never
  allocated from an incrementing global number.
- Each station owns one 512 by 512 block logical region. Region centers occupy
  a deterministic square-spiral grid with 1,024-block spacing, leaving a
  512-block separation between neighboring logical regions.
- The allocator persists both committed and reserved grid cells in Overworld
  `SavedData`. It checks a fixed maximum station count and never scans world
  blocks to discover ownership.
- Allocation is serialized on the server thread. The pure registry model also
  rejects duplicate UUIDs, cells, and overlapping regions, so concurrent or
  malformed callers fail closed.

### Creation transaction

Creation uses these durable phases:

```text
reserve region -> persist -> generate bounded 17x17 platform
-> persist StationState -> commit reservation
```

The platform center and one landing pad are derived only from the allocated
cell. Generation inspects and writes a fixed number of blocks. Any failure
removes blocks written by that attempt, releases the reservation, persists the
rollback, and emits an audit event. Restart recovery releases a reservation
that has no committed station; it never guesses that arbitrary world blocks
belong to a station.

### State and authority

- `StationState` has its own schema version and records station UUID, owner
  UUID, a bounded member set, region, landing pad, orbit body, creation time,
  and environment profile.
- `StationAccessService` is the only normal-play authorization boundary.
  Owners may manage membership and build; members may visit and build; other
  players may neither select the station nor modify station-owned blocks.
  Permission-level-2 operators may inspect and repair but bypasses are audited.
- Member removal takes effect against the next server action. No client cache
  is authoritative.
- Block place/break protection applies only inside committed station regions
  in the fixed Space Level. It performs an indexed cell lookup and does not
  traverse other stations or chunks.

### Rocket destinations and chunk activation

- The server sends a bounded list of stations accessible to the player when it
  opens the nearby rocket console. A C2S launch intent may contain a station
  UUID, but never a coordinate, region, landing pad, membership claim, or
  environment value.
- Earth and Moon remain fixed destinations. `SPACE_STATION` uses the fixed
  Space Level and a selected, currently accessible committed station.
- The transfer service resolves the station again at launch time and relocates
  the rocket only to that station's approved landing pad. A rocket in Space
  must itself be within an accessible committed station before it may depart.
- Temporary chunk tickets cover only the bounded rocket destination snapshot.
  Existing transfer cleanup removes them after settlement; station ownership
  never creates a permanent chunk ticket.

### Administration and deletion

- Permission-level-2 commands inspect, list, recover reservations, add/remove
  members, transfer ownership, and delete stations.
- Normal deletion requires an explicit station UUID plus confirmation token.
  It removes only the bounded platform footprint recorded for that station,
  never clears the full logical region, and verifies the cell still belongs to
  the target before release. A pre-delete state backup is written to the
  release/operator log.
- Orphaned owners are not deleted automatically. An operator transfers
  ownership or deliberately deletes the station.

## Alternatives

### One dimension per station

Rejected because it expands registry/save lifecycle complexity and contradicts
the fixed shared-Space version scope.

### Free-form coordinate allocation

Rejected because overlap proofs, bounded lookup, recovery, and client input
validation become substantially weaker.

### Client-owned destination coordinates

Rejected because a hostile packet could probe or load arbitrary chunks and
land inside another station.

## Consequences

The design is intentionally regular rather than spatially optimal. It makes
allocation, non-overlap, lookup, persistence, rollback, deletion, permissions,
and travel independently testable with fixed budgets. Moving or resizing a
station region is deferred; doing so later requires a migration ADR.

## Validation

- [ ] Allocate at least ten unique non-overlapping regions
- [ ] Round-trip and restart the registry without cell reuse
- [ ] Reject duplicate/concurrent allocation and unauthorized access
- [ ] Roll back failed platform generation and release its reservation
- [ ] Land only at the selected station pad and release travel tickets
- [ ] Delete one station without changing its neighbor
- [ ] Exercise owner/member/removal/operator behavior with two players
- [ ] Record bounded lookup, block-write, and chunk-ticket counters

