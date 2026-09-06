# ADR-014 — Typed travel targets, route data and position-aware body context

```yaml
status: ACCEPTED
date: 2026-09-06
deciders:
  - sunthemoon
owner: sunthemoon
target_version: v1.1.0
supersedes:
  - ADR-001 Level-to-single-body lookup assumption
  - ADR-006 fixed destination list
  - ADR-008 SPACE_STATION destination representation
```

## Context

The v1.0 flight journal already persists bodies and dimensions as bounded
`ResourceLocation` strings, and station records already persist an arbitrary
`orbit_body`. The remaining fixed boundary is `RocketDestination`: 23 Java
files use its three values, C2S/S2C flight frames encode one-byte enum IDs,
and `CelestialCatalog` keeps only the first body mapped to a Level.

That representation cannot distinguish two station regions orbiting different
bodies inside the shared Space Level. Replacing the enum without a target
shape, route boundary and contextual migration would risk silently routing
unknown values to Earth, trusting client-supplied context, or changing the
verified v1.0 fuel balance.

The v1.0 version status is not yet `PASSED`. ADR-015 separately records the
owner's bounded development exception; it does not change the release status
of either version.

## Decision

### Concrete travel target

`TravelTarget` is a closed, schema-versioned sum type in v1.1:

| Type ID | Required identity | Meaning |
|---|---|---|
| `advancedrocketrycommunity:body_surface` | `body_id: ResourceLocation` | Surface context of a logical body |
| `advancedrocketrycommunity:orbit` | `body_id: ResourceLocation` | Generic orbit context around a body |
| `advancedrocketrycommunity:station` | `instance_id: UUID` | One committed station; body is resolved by the server |
| `advancedrocketrycommunity:mission` | `instance_id: UUID` | One registered mission destination; body is resolved by the server |

Body targets never carry an instance UUID. Instance targets never carry a
client-selected body, Level, region or coordinate. The server resolves the
instance and derives those values from authoritative registries.

JSON, NBT and network forms use `schema_version = 1`, the stable type ID and
exactly one identity field. Resource locations remain at most 128 characters;
a complete encoded target is at most 256 bytes. Unknown schemas, types,
additional fields and non-canonical UUIDs fail closed. Unknown targets are
preserved in migration diagnostics but are not made launchable.

### Route definition

Data routes connect `RouteAnchor` values rather than concrete runtime
instances. v1.1 anchors are only `body_surface(body_id)` and `orbit(body_id)`.
A station resolves to `orbit(station.orbitBody())`; a mission resolves through
its server registry. This prevents route data from depending on random UUIDs.

Each route has a stable `ResourceLocation` ID, schema version, source anchor,
destination anchor, `distance_units` and an explicit `bidirectional` flag.
Fuel remains server-computed from rocket statistics, authoritative source and
destination environment gravity, the v1.0 base cost, and route distance. A C2S
packet never supplies distance, fuel, gravity or a route choice.

Reload is all-or-nothing. The candidate registry rejects duplicate IDs,
missing bodies, equal endpoints, negative/out-of-range distances and duplicate
directed edges before replacing the active immutable graph. Directed cycles
are legal, but search uses a visited/best-cost map and hard budgets:

```text
routes <= 512
anchors <= 256
outgoing edges per anchor <= 64
expanded nodes per plan <= 1024
cached plans <= 256
```

Equal-cost paths are resolved by route ID order so results are deterministic.
An invalid reload retains the previous valid graph and reports bounded errors.

The baseline data preserves the effective v1.0 distances: Earth surface to
Moon surface `50`, Earth surface to Earth orbit `25`, and Moon surface to Earth
orbit `25`. Environment gravity, rather than the orbited body's surface
gravity, preserves the existing zero-gravity station arrival cost.

### World location and body context

`WorldLocation` contains only `ResourceKey<Level>` and immutable block
coordinates. It is a query value, not client authority and not a persisted
destination.

`BodyContext` contains the resolved body ID, locus (`SURFACE`, `ORBIT` or
`MISSION`) and an optional authoritative instance UUID. Environment lookup is
separate: station contexts use the persisted station environment; ordinary
surface/orbit contexts use validated celestial data.

Resolver precedence is:

1. an instance/region resolver for a committed server registry;
2. an unambiguous Level mapping in the active celestial catalog;
3. unresolved.

In the Space Level, `StationRegistryModel.findAt(x,z)` supplies constant-time,
in-memory region lookup. It yields the station's `orbit_body` and station UUID.
Coordinates outside a committed region are unresolved. Resolution never reads
or loads a chunk and never defaults an ambiguous or unknown location to Earth.
`CelestialCatalog` therefore changes from a single `putIfAbsent` Level map to a
bounded collection of candidates.

### Migration and compatibility

Migration is two-phase because v1.0 flight NBT can describe `space` plus a
position without storing the current station UUID:

1. A pure bounded decoder reads v1.0 fields into a legacy shadow value without
   changing their meaning or discarding the original fields.
2. After the station registry and Level services are available, a server-side
   migrator resolves the position to a committed station and writes the v1.1
   target. Body/dimension pairs map directly to body-surface targets; station
   destinations use their existing UUID.

An unresolved Space position, missing station, deleted body or unknown legacy
value leaves the rocket recoverable but blocks launch and emits a bounded
diagnostic. It never guesses Earth. Active v1.0 transfers finish under the
recorded v1.0 authority boundary before being resaved in the new schema.

Planned version changes are flight data schema `1 -> 2`, transfer record schema
`1 -> 2`, route data schema `1`, target schema `1`, and rocket network protocol
`5 -> 6`. The station state may remain schema 1 because `orbit_body` is already
stable and arbitrary; any change to its serialized shape requires a separate
schema bump. Old and new rocket network protocols are explicitly incompatible.

## Alternatives

### Keep expanding `RocketDestination`

- Small local changes.
- Requires Java changes for every body, cannot represent runtime station or
  mission identity, and preserves the Level/body conflation.
- Rejected.

### Put body, Level and coordinates in every client target

- Makes each packet self-contained.
- Trusts redundant client claims and enables unsafe remote-coordinate probes.
- Rejected.

### Use a `ResourceLocation` for station UUIDs

- Gives every target one apparent identifier type.
- Invents a lossy namespace convention for runtime objects and obscures UUID
  validation.
- Rejected.

### Treat all of Space as Earth orbit

- Preserves the current common case.
- Makes Moon-orbit and later bodies impossible and silently misidentifies gaps
  between station regions.
- Rejected.

## Consequences

### Positive

- New logical bodies and routes do not require an enum branch.
- Runtime instance authority remains server-owned.
- Shared Space can report different orbit bodies by position.
- Existing station persistence and cell lookup can be reused.
- Migration failures are recoverable and explicit rather than guessed.

### Negative

- Contextual migration cannot be completed by an NBT codec alone.
- The fixed-size three-quote S2C packet must become a bounded list.
- Catalog reload and route reload need a coordinated immutable snapshot.
- Callers must be migrated incrementally while the legacy decoder remains.

### Implementation size review

The v1.1 integration leaves `RocketManager` at approximately 900 source lines,
above the repository's 800-line review threshold. It remains the pre-existing
Forge lifecycle/assembly facade; target calculation, contextual migration,
route search and transfer execution are delegated to `RocketTargetFlightPlanner`,
`LegacyFlightTargetMigrator`, `BodyContextResolver`, `RocketFlightService` and
`RocketTransferService`. Keeping the public runtime facade stable avoids a
second unrelated lifecycle refactor inside the migration release, but no new
domain logic may be added to this class. Its decomposition is assigned to the
next maintenance version (`v1.1.1`) before further feature work.

`RocketFlightGameTests` also remains above 800 lines. It was already above the
threshold at the v1.0 baseline and is a scenario collection rather than a
production dependency boundary. The v1.1 additions stay in existing named
GameTest batches; splitting the fixture helpers and batches is likewise a
`v1.1.1` maintenance item. This note is not a waiver for additional growth.

## Validation

- [x] Read-only v1.0 identity/schema/network inventory
- [x] Machine-validated JSON contract samples
- [x] Maintainer accepts target shapes and limits
- [x] Codec round-trip and malformed/oversized input tests
- [x] Atomic route reload and bounded graph tests
- [x] Same-Level, two-station body-context GameTest
- [ ] v1.0 fixture migration and second-save identity
- [ ] C2S authority and no-forced-chunk audit
- [ ] Dedicated two-client regression
- [ ] Rollback from pre-integration commit

## Revisit when

Revisit before adding new target kinds, dynamic Levels, moving/resizing station
regions, cross-system warp, client-extensible route types or a public extension
API. Those belong to later versions and must not weaken v1.1 bounds.
