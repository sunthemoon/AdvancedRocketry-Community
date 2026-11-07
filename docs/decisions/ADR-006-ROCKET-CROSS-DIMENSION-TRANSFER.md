# ADR-006 — Server-authoritative Earth–Moon rocket transfer

```yaml
status: ACCEPTED
date: 2026-09-01
deciders:
  - sunthemoon
target_version: v0.6.0
supersedes: "ADR-002 transfer outline only"
```

## Context

A block-built rocket must move a bounded structure, fuel state, and up to its
declared seat count between the fixed Earth and Moon Levels. Minecraft does not
provide an atomic cross-Level entity transaction. A crash between destination
spawn and source removal can otherwise create two rockets, lose the only
rocket, consume fuel twice, or strand passengers.

The client must not choose coordinates, submit a structure snapshot, assert
fuel or mass, or cause arbitrary chunks to load. The transfer must also survive
server stop/restart at every externally visible flight state.

## Decision

### Authority and destinations

- Only `minecraft:overworld` and `advancedrocketrycommunity:moon` participate
  in v0.6 travel.
- A C2S intent contains a bounded action, rocket entity id, destination id,
  and request UUID. The server recomputes ownership, range, state, structure
  statistics, fuel, source body, destination Level, and reachability.
- Landing origins come from a small server-owned deterministic pad list. Client
  coordinates are never accepted. Pad probing and temporary flight chunk
  activation are bounded and may load only those predefined locations.
- Transit is a persisted server state. No continuous cross-Level interpolation
  is required; clients see countdown/ascent, a short transit transition, then
  descent at the destination.

### Durable transaction

Before mutation, the Overworld `SavedData` journal records a schema-versioned,
size-bounded transfer containing source and destination references, both
snapshots, owner, passenger UUIDs, required fuel, and a checksum. Phases are:

```text
PREPARED
→ DESTINATION_SPAWNED
→ PASSENGERS_TRANSFERRED
→ SOURCE_REMOVED
→ COMMITTED
```

The destination entity is created from a relocated snapshot with fuel already
reduced by the exact planned cost. The source retains its original fuel until
it is removed. Therefore the durable authority boundary is
`DESTINATION_SPAWNED`:

- before that phase, the source is authoritative;
- at or after that phase, the destination is authoritative.

Each phase update is idempotent. A repeated request UUID cannot create another
transaction or consume fuel again.

### Recovery matrix

- source only: keep and safely return the source to a non-flight state;
- destination only: keep and finish the destination;
- both: keep the journal-authoritative side and remove the other;
- neither: reconstruct the journal-authoritative side at its recorded safe pad
  and emit a high-severity audit event.

Passenger UUID/seat bindings remain in both entity flight data and the global
active-flight record. Online passengers move only after destination spawn.
Offline passengers rejoin at the authoritative rocket or its safe pad; the
system never edits or deletes playerdata.

#### Implementation note: a stationary rider's logout save (Root, 2026-11-07, R364, R365, R375, R378)

W278's round 7 finding 7-1: a rider whose high seat was released while it was offline logged in at the seat's height
and fell. Since W312, W312F and W312F2 (merged at `dcc0baa82`), a rider of a loaded, stationary rocket (ASSEMBLED,
FUELED or LANDED, with no active transfer, rejoin guard, release fence, held bay receipt or internal move) is
detached, through vanilla's own dismount, just before vanilla saves it: at logout, and in `ServerStoppingEvent`
before a server stop saves online players. Vanilla performs that dismount right after the save anyway.

- The player is saved where LEAVE SEAT puts it, only when a logout-only check proves that place harmless after
  login. The check accepts this mod's full-cube structural blocks as ordinary blocks, ignores the rider's own rocket
  and other players, and allows a fall of at most three blocks whose gravity-scaled landing deals no damage.
  Otherwise the save is as before.
- The seat assignment is unchanged, so the reconnect still reseats a kept seat. Playerdata is still written only by
  vanilla; no file is edited.
- Limits: saves made before this change, autosaves and crashes keep the seat's height once; a refused check keeps
  it; a stop proves at most about 42 riders within one tick's shared budget.

Evidence: [W312](../work/v1.8.0-root-queue/records/W312.md), [W312F](../work/v1.8.0-root-queue/records/W312F.md)
and [W312F2](../work/v1.8.0-root-queue/records/W312F2.md), with reviews W312R and W312FR.

### Landing and disassembly

Destination placement uses the same bounded structure snapshot with a new
dimension/origin identity. A pad must be loaded, within build height, free of
blocks and conflicting rockets, and large enough for the full bounds. Failure
keeps the rocket recoverable rather than overwriting the world. Normal v0.5
transactional disassembly then restores the relocated snapshot exactly.

## Alternatives

### A. `Entity#changeDimension` without a journal

- Less code.
- Cannot prove exactly-once fuel, unique authority, or crash recovery.
- Rejected.

### B. Remove the source before destination spawn

- Avoids a short dual-entity window.
- A failed spawn loses the only rocket and passengers.
- Rejected.

### C. Client-selected landing coordinates

- More flexible gameplay.
- Enables arbitrary chunk loading and unsafe world overwrite attempts.
- Rejected for v0.6.

## Consequences

### Positive

- The duplication/loss invariant has an explicit durable authority boundary.
- Fuel is represented exactly once on the authoritative entity.
- Recovery decisions are pure-testable and independent of client state.
- Fixed pads keep chunk access and landing validation bounded.

### Negative

- Transfer has a visible discontinuity instead of seamless inter-dimensional
  motion.
- Active flight and journal codecs add implementation and migration work.
- Fixed pads may reject a launch until an occupied pad is cleared.

## Validation

- [ ] Pure Java transition, reachability, fuel, replay, and recovery tests
- [ ] NBT round trips, malformed/future schema rejection, and size limits
- [ ] Forge GameTests for Earth→Moon→Earth and blocked landing
- [ ] Source-only, destination-only, both, and neither restart recovery
- [ ] Two-player disconnect/reconnect and two-rocket concurrency
- [ ] Twenty packaged-server round trips with conservation accounting
- [ ] Malicious C2S and bounded chunk-access report

## Revisit when

Revisit only when adding non-fixed destinations, shared orbital regions, or a
post-v1.0 dynamic-dimension design. Those changes require a new ADR and may not
weaken the v0.6 authority or recovery invariants.
