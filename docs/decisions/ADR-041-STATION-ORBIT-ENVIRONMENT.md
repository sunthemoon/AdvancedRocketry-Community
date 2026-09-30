# ADR-041 — Station orbit environment

```yaml
status: PROPOSED
date: 2026-09-30
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.5.0
development_dependency: ADR-039, ADR-040
implements: V150-ORBIT-01 contract for ORBIT-02 and ORBIT-04
```

## Context

Station records (ADR-040 record schema 2) store `orbit_body`, `gravity_milli`
(0..10,000), `vacuum` and `solar_angle_milli_degrees` (0..359,999). New and
migrated stations use `BASIC_SPACE`: gravity 0, vacuum, angle 270,000.
Today only the public API reads them: `EnvironmentQueries` (ADR-028, API 1.7)
reports the configured station gravity and vacuum. Player gravity is applied
per Level by `CelestialGravityController`; all of shared Space uses the Space
body (gravity 0), and the controller rejects multipliers above the celestial
bound of 4.0. Exposure is disabled in Space (ADR-034). The sky in Space is the
generic space profile (ADR-036) with no per-station context. No solar power
consumer exists.

The version plan asks for orbit context around any data body, displayed orbital
environment, solar and gravity conditions, and optional gravity. This ADR fixes
the server semantics only. It does not change sky rendering, networking,
exposure, oxygen, warp or public API signatures.

## Decision

### Effective station environment

Add one server-side resolver for a position in the fixed Space Level. It uses
the existing indexed `findAt` (one cell lookup and one containment test) and
the active immutable celestial catalog. It never scans, loads chunks or reads
blocks.

- **Inside a committed station region** the effective environment is:
  - body: the station's `orbit_body`;
  - gravity: `min(gravity_milli / 1000, 4.0)` (the celestial physics bound);
  - vacuum: the stored `vacuum` flag (unchanged meaning);
  - solar intensity: the orbit body's `solar_intensity` when that body is in the
    active catalog, otherwise the shared Space body's value; orbit is above any
    atmosphere, so pressure does not attenuate it;
  - sun angle: `solar_angle_milli_degrees / 1000` degrees.
  If the orbit body is missing from the catalog (for example a removed data
  pack), the station keeps its stored gravity and vacuum, uses the Space body's
  solar intensity, and reports the body as unavailable. With no active catalog
  at all the solar intensity is 0. Nothing is rewritten.
- **Elsewhere in Space** (gaps, blocked registry) the shared Space body profile
  applies exactly as today. A blocked registry resolves no station.

### Gravity

`CelestialGravityController` uses the effective station gravity for players in
a station region and the Level profile everywhere else. The controller stays in
the celestial module and receives a position-gravity function from the station
module at wiring time, so no celestial→station dependency is added.
Checked management updates may change only the region or environment of a
station; identity, owner, name, cell, pad, orbit body and team are rejected by
the registry model. The cost is one indexed
lookup per player tick in Space, the same lookup build protection already uses.
Stored values above 4,000 stay valid storage and are clamped only for physics;
`EnvironmentQueries` keeps returning the configured value (0..10), preserving
ADR-028's "configured, not effective physics" contract and API 1.7 bytes.
Existing stations store 0, so existing worlds see no gravity change.

A station owner, or an operator, may set gravity with
`/arce station gravity <percent>`, 0..100 in steps of 1 (0..1,000 milli). The
same rules as expansion apply: the command must come from the connected
player's own command source while they stand in the station's committed region
with their chunk loaded; members, invitees, console, `/execute`, command blocks,
functions, signs and FakePlayers cannot. It is a direct, reversible setting
(no confirmation) written through the same checked candidate commit as
expansion: validate, stage, force, read back, atomically replace, then publish.
A failed write keeps the old gravity. Values above 1,000 are not settable by
command; existing larger values are preserved until changed.

### Display

`/arce station environment`, for any player in Space, reports either "outside
any station" or: station name and UUID, orbit body (and whether it is available),
effective gravity (with the configured value when clamped), vacuum, solar
intensity and sun angle. It reads only the resolver and sends literal text, like
the other station commands. No new packet, translation key or client state.

### Explicit non-goals in this ADR

- No per-station sky, orbited-body rendering or sun position on the client; the
  generic Space sky stays. That needs a synchronized context and a bounded
  protocol decision (ORBIT-03, separate ADR) plus real-GPU `V1` evidence.
- No change to exposure in Space, oxygen, pressure or sealed rooms.
- No solar power generation or machine; solar intensity is exposed for display
  and future consumers only.
- No orbit changes, orbit mutation command, warp, star systems or cost.
- No change to rockets, passengers, gravity for non-player entities, or any
  save schema. Gravity and angle already exist in record schema 2.

## Verification (ORBIT-02 / ORBIT-04)

- Unit: resolver inside/outside/boundary/negative coordinates, 512 and 768
  regions, missing orbit body, blocked registry, clamp at 4.0 and API value
  unchanged; gravity command policy matrix; checked write success/failure.
- GameTest: a connected player in a station with stored gravity gets the
  expected gravity attribute and returns to Space gravity in a gap; command
  display text; a catalog reload that changes the orbit body's solar intensity
  is reflected on the next query; unchanged loaded-chunk count.
- Required short commands; API class bytes unchanged; generated files unchanged.
- Native (with STATION-04 harness): set gravity, restart, value retained.

## Rollback

Revert the implementation. Stored gravity/angle values remain valid schema-2
data and are ignored again by older code; no downgrade migration is needed.
