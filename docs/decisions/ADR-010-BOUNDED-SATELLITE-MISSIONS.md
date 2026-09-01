# ADR-010 — Bounded logical satellites and offline missions

```yaml
status: ACCEPTED
date: 2026-09-01
deciders:
  - sunthemoon
target_version: v0.8.0
supersedes: ""
```

## Context

v0.8.0 must restore a small research and data-satellite loop without reviving
the legacy static satellite registry, numeric dimension identity, LibVulpes
machine hierarchy, or per-tick satellite objects. Missions must continue while
their owner is offline, survive server restart, reject replayed claims, and
never hold a chunk ticket merely to measure time.

The repository owner authorized the v0.8.0 vertical slice on 2026-09-01. This
decision fixes its persistence and authority boundary before gameplay code is
added.

## Decision

### Definitions and runtime identity

- `SatelliteDefinition` is loaded from bounded data-pack JSON. v0.8.0 ships one
  `data_satellite` definition.
- A launched satellite has a random UUID. It is not an entity, dimension id,
  chunk position, or incrementing global number.
- A mission snapshots its definition id, owner, target, duration, and research
  yield at start. Reloading definitions cannot rewrite an active mission.
- The first slice permits one unfinished mission per satellite and rejects
  unknown or absent definition types.

### Persistence and scheduling

- One Overworld-owned `SatelliteMissionSavedData` stores bounded satellite,
  mission, and research-account maps. The root, satellite, mission, and account
  records each carry their own schema version.
- Future or malformed schemas fail closed and preserve their original payload.
- Player-offline progress uses a persisted monotonic game-time clock. Server
  shutdown does not use wall time; missions resume from persisted game time on
  restart.
- An in-memory deadline priority queue is rebuilt once on load. The server
  checks its head every 20 ticks and completes at most 32 due missions per
  pass. It never scans every mission each tick.
- Initial hard limits are 4,096 satellites, 8,192 retained missions, 4,096
  research accounts, 1,024 unfinished missions, and a 4 MiB uncompressed NBT
  payload.

### Exact-once results

- Mission completion only changes durable state from `ACTIVE` to `READY`.
- Claiming credits research and advances the mission exactly once inside the
  same SavedData mutation. Replayed claims return the existing result.
- Celestial discovery is a separate idempotent server mutation. A persisted
  pending-discovery phase is replayed after restart until `CelestialSavedData`
  acknowledges it, preventing either duplicated research or lost discovery.
- Research comes only from a completed mission in this slice. Discovering an
  eligible unknown body consumes the documented fixed research cost.

### Player and network boundary

- One community-authored Satellite Terminal performs fixed component assembly,
  logical launch, mission control, result receipt, and discovery analysis.
- Assembly creates one UUID-bound satellite package and one matching control
  chip. Launch and claim require the matching chip at a loaded nearby terminal.
- Menu buttons send only bounded intent identifiers. The server revalidates
  player, menu, distance, loaded chunk, ownership, definition, target, state,
  receiver chip, capacity, and energy before every mutation.
- Launch registration is idempotent by package UUID. A terminal-side pending
  intent reconciles partial save ordering without creating a second satellite.

## Consequences

- v0.8.0 has no orbiting satellite entity, renderer, permanent chunk ticket,
  rocket payload bay, complete research tree, asteroid mining, solar power
  transmission, or terraforming satellite.
- The terminal deliberately replaces several legacy machines for this bounded
  slice. Later versions may split it only after the state and security boundary
  remains compatible.
- No upstream source or asset is copied. The locked MIT upstream commit is used
  only to document the legacy assemble, control-chip, and data-collection
  behavior.

## Verification

- Codec/NBT corruption, future schema, bounds, and round-trip tests.
- State-machine, monotonic-clock, queue-work, restart, offline, replay, owner,
  receiver, and discovery-commit tests.
- GameTest and packaged dedicated-server manufacturing-to-discovery flows.
- A 100-mission stress record proving zero chunk tickets and bounded queue work.
