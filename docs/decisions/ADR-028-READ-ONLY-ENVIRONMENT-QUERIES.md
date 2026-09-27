# ADR-028 — Server-scoped read-only environment queries

```yaml
status: ACCEPTED
date: 2026-09-27
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.3.0
api_version: "1.6"
acceptance_basis: maintainer authorization to follow recommended current-version implementation solutions
```

## Decision and scope

Expose configured celestial/station environment data without exporting internal
managers, mutating the world, or adding a second spatial cache. This is V130-ENV
under ADR-020/021, not new planet content, gravity simulation or release approval.
The existing ADR-014 instance-before-Level precedence remains authoritative.

API 1.6 retains the nineteen existing exports and adds five classes (four top-level
types and one nested enum) in `api.environment`:

- `EnvironmentQueries`: interface with
  `Optional<EnvironmentSnapshot> at(ResourceKey<Level> dimension, BlockPos position)`.
- `ServerEnvironmentReadyEvent`: final, non-cancelable FORGE-bus event, with a
  public non-null `EnvironmentQueries` constructor and `queries()` accessor.
- `EnvironmentSnapshot`: record with `ResourceLocation bodyId`, `Locus locus`,
  `Optional<UUID> instanceId`, `double gravityMultiplier`, `boolean vacuum`, and
  `Optional<AtmosphereProfile> atmosphere`. Nested `Locus` has `SURFACE` and
  `STATION_ORBIT`. Surface snapshots have no instance and require atmosphere;
  station snapshots require an instance and have no atmosphere profile.
- `AtmosphereProfile`: record with `double pressure`, `boolean breathable`,
  `double temperatureKelvin`, and `ResourceLocation profile`.

Both `bodyId` and `AtmosphereProfile.profile` are at most 128 characters. Gravity is finite in 0..10 (the existing
station persistence bound; celestial data remains limited to 4). Pressure is
finite in 0..10 relative-atmosphere units; temperature is finite in 0..2000 kelvin.
Breathable profiles require positive pressure. A surface snapshot's vacuum flag
must agree with pressure being zero. Null arguments throw `NullPointerException`;
invalid record values throw `IllegalArgumentException`.

## Lifecycle, lookup and freshness

The host emits the ready event during `ServerStartedEvent`, once for each logical
server, on its owning thread. Consumers subscribe during mod construction on
`MinecraftForge.EVENT_BUS`, retain the handle, and replace it for each new server.
The event is not a registration request or a modification hook. It is not emitted
by client-only resource loading; integrated servers use the same logical-server
contract as dedicated servers. Normal Forge event-listener exception semantics
apply; no same-JVM sandbox is promised.

The handle is bound to that server only. Queries must run on its owning server
thread. Off-thread calls and calls after invalidation throw `IllegalStateException`
before touching catalogs, saved data or Levels. Invalidation happens at server
stopping (highest event priority), with stopped cleanup as a fallback. Retaining
an expired handle does not retain the server/Level/SavedData objects. Old handles
never become usable again when a subsequent integrated server starts.

Each query reads one active immutable catalog snapshot and current committed
station state. In shared Space, an indexed station-region lookup wins; gaps,
reservations, blocked registries and deleted/unknown orbit bodies return empty,
never Earth or the generic Space definition. Other dimensions resolve only when
exactly one catalog body maps to an existing server Level. Unknown/unavailable or
ambiguous dimensions return empty. Vertical coordinates do not change a station's
horizontal ownership region. No terrain, block entity, chunk, light or biome is read.
Unloaded chunk coordinates are supported because all information is in memory.

Surface values come from the active body definition. Station identity/orbit body,
gravity and vacuum come from the persisted station record, not the orbited body's
surface environment. Station records do not define pressure, breathable air,
temperature or an atmosphere-profile ID: `atmosphere()` is empty, not fabricated.

Accepted celestial catalog candidates are visible on the next query; rejected
celestial candidates retain the previous catalog. This is not an atomic promise
over every datapack reload listener. Committed station creation/deletion is visible immediately.
Returned records are immutable detached values, not live views. No freshness or
generation token is persisted or sent over the network. Queries use existing
bounded indexed catalogs/registries; no per-position cache or world scan is added.

These are **configured base-environment values**, not effective entity gravity,
room oxygen, weather, suit protection or permission to travel/build. In particular,
the existing gravity controller remains Level-based; this API does not apply a
station-specific gravity modifier. Consumers needing live values must query again.
Metadata lookup confers no player authority; exposing it through a C2S handler
still requires that handler's own permission, distance and resource checks.

## Compatibility, validation and rollback

No save root/schema, packet, player physics or existing provider changes. Startup
acquires the existing station SavedData; the query itself performs no storage I/O
and never marks it dirty. An installed consumer may be removed without migration.

Verify record constraints; actual surface/station/unknown/ambiguous resolution;
accepted/rejected catalog reload; station create/delete and blocked state;
wrong-thread and expired handles; no chunk loads or data mutation; isolated API
classifier/consumer and dedicated startup/restart with persisted station identity.
Keep existing tests and run the short build/DataGen/GameTest checks. The full
original-feature campaign remains scheduled under ADR-018.

Rollback removes the additive service/exports and consumer use together; it does
not convert worlds. After public release, API-021 deprecation/major rules apply.

Platform event usage follows the
[Forge 1.20.1 event documentation](https://docs.minecraftforge.net/en/1.20.1/concepts/events/).
No upstream code or art is imported by this slice.
