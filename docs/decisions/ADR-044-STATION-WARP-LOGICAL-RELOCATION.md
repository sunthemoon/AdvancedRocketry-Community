# ADR-044 — Station warp as logical orbit relocation

```yaml
status: PROPOSED
date: 2026-09-30
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.5.0
development_dependency: ADR-040, ADR-041, ADR-043
implements: V150-WARP-01 contract for WARP-02..05 and UI-01..03
station_record_schema: 3 (proposed)
```

## Context

The version plan describes warp as a multi-stage, recoverable transaction with a
warp core, energy or fuel, targets and a countdown. Passengers and docked rockets
must be kept, and there is a "target copy / source delete" physical-move
assumption that ADR-040 left open. Stations are regions of a shared Space Level
identified by a fixed cell; their context comes from `orbit_body` (ADR-040/041).
A physical copy would have to move up to 768×768 columns of blocks, entities and
block entities across Levels, which is unbounded work, and ADR-040 forbids scans.

Launch admission from a station accepts a rocket physically inside the committed
region whose saved `current_body` equals the station's orbit body or Space
(`PlanetaryFlightAdmission`). In-flight or pending rockets are recorded in the
transfer and transaction authorities.

## Decision

### Logical relocation, not a physical copy

Warp changes only the station's `orbit_body` to a target body. Cell, region, pad,
blocks, entities and players stay exactly where they are. Everything that derives
context from the station (environment, gravity, routes, quotes, discovery of the
orbit) follows on the next query. The plan's dual-copy wording is resolved in
favor of relocation. No block, chunk or entity is moved, loaded or scanned.

### Targets

The target is any orbitable, known body (ADR-043) other than the current orbit:
another body in the same system (relocation) or in another system (interstellar).
A missing, unknown or non-orbitable target is rejected. A target removed by a
reload before commit aborts the warp; the station stays where it was.

### Warp core and cost

- New block `advancedrocketrycommunity:warp_core` with a block entity that stores
  Forge Energy (capacity 10,000,000 FE; input only, no output). It is original
  content using existing machine-casing textures; no art is imported.
- Cost is deterministic server data: 2,000,000 FE within a system,
  8,000,000 FE between systems. It is never supplied by the client.
- The player starts a warp by using the core (server-validated interaction: the
  block is in a loaded chunk, inside the station's committed region, and the actor
  is the owner or an operator under the ADR-040/041 local-actor rule). The core's
  position comes from the interaction, not from a scan or client coordinates.

### Exactly-once cost across two stores

The orbit change lives in the station file; the energy lives in the core's chunk.
They cannot be written atomically together, so:

- Station record schema 3 adds `warp_sequence` (long, starts at 0) and
  `warp_debt` (`{sequence, energy, core_pos}` or absent). Records 2 migrate to 3
  with sequence 0 and no debt, through the existing pre-start backup migration.
- Commit is a single checked station update (ADR-041 path). It sets the new
  `orbit_body`, increments `warp_sequence` to k and records the debt for k. This
  adds a third named checked transition, **orbit relocation**. Only `orbit_body`,
  `warp_sequence` (+1) and `warp_debt` may change; region, environment,
  identity, owner and team stay equal. The operator settlement is a fourth: only
  the debt's settled mark changes. ADR-041's "only two transitions" rule is
  extended by exactly these two. This also supersedes ADR-040's "no speculative
  warp fields yet": the fields are now specified.
- The core stores `paid_sequence`. When loaded and ticking, if the station's debt
  sequence k is above `paid_sequence`, it deducts the debt energy and sets
  `paid_sequence = k` in the same block-entity save. The deduction is keyed by k,
  so it cannot repeat.
- The station never clears the debt; it keeps only the latest one. A new warp
  must start from the core at the debt's `core_pos`, and that loaded block
  entity must already have `paid_sequence == k`. The check is read directly at
  request and commit.
- If the core's save is lost in a crash, the deduction is lost with it. On reload
  it pays sequence k again, which still charges exactly once in persisted terms.
  No write to the station depends on the core's save order.
- If a core is destroyed with `paid_sequence < k`, the station cannot warp again
  until an operator settles the debt with
  `/arce station admin warp-debt settle <id>`. That checked update marks sequence
  k settled, is audited, and costs nothing further.

### Countdown and state machine

`REQUESTED` → `COUNTDOWN` (200 ticks, cancellable by the owner or operator, or
aborted by any failed recheck) → `COMMITTED` → `PAID` (the core's own state) or
`SETTLED` (operator, core lost). Before commit the countdown
is in memory only: a restart during countdown aborts with no cost and no change,
which is recoverable by definition. `COMMITTED` and `PAID` are the persisted
fields above. Just before commit, the actor rule, target, core energy (at least
the cost), the absence of debt and the rocket rule below are all rechecked.

### Rockets and passengers

- **Passengers**, online or offline, stay in the station region and simply see
  the new orbit context. There is no teleport and no inventory movement.
- **Docked rockets** (rocket entities physically in the region) move with the
  station. Launch admission for a station source is changed so that position
  inside the committed region is the authority: the source body becomes the
  station's current orbit. A `current_body` saved before the warp no longer
  strands the rocket.
- **Pending or in-flight rockets** block warp. This covers any transfer or
  transaction whose source or destination snapshot overlaps the region (the
  existing deletion guard). It prevents a free interstellar trip by warping while
  a rocket is inbound.

### Protocol and UI

Starting, countdown feedback and cancellation go through the core interaction and
server messages first. A client screen (UI-01) needs a separate bounded, versioned
payload. The countdown is reported with chat or action-bar messages; no new packet
is required for this ADR.

## Non-goals

Physical copies, moving other Levels, warping without a core or energy, remote
initiation, fuel items, per-system time, space-elevator logistics, and warp of
rockets or players separately from a station.

## Verification

- Unit: target rules, cost table, state transitions, debt and sequence logic
  (charged once under every crash cut), record 2→3 migration, admission change.
- GameTest: a connected owner uses a charged core; countdown then commit; orbit,
  gravity and environment follow; energy is deducted once, including after a
  simulated reload of the core; an in-flight transfer blocks warp; a docked rocket
  launches afterwards; a non-owner, `/execute` or a FakePlayer is rejected.
- Native: warp across two systems, restart during countdown (no change), restart
  after commit before payment (paid once after reload), second restart
  (idempotent). The source world is untouched.
- Required short commands; API 1.7 unchanged unless a separate API decision
  exists.

## Rollback

Warp commits are ordinary schema-3 record changes. Returning to an older build
requires restoring the pre-upgrade backup (ADR-040); record 3 is refused by older
readers.
