# ADR-041 — Station orbit environment

```yaml
status: ACCEPTED
date: 2026-09-30
deciders: [sunthemoon]
owner: sunthemoon
accepted_by: sunthemoon
accepted_at: 2026-09-30
acceptance_basis: maintainer standing goal to complete the project with recommended solutions, after independent contract review ("accept with changes"); all required changes applied below
target_version: v1.5.0
development_dependency: ADR-039, ADR-040
amends: ADR-028 (gravity controller statement; see "Relation to ADR-028")
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
A checked management update is exactly one of two transitions: the single
512→768 growth with an unchanged environment, or a gravity-only change within the
unchanged region. Shrinking, vacuum or sun-angle changes, and any change to
identity, owner, name, cell, pad, orbit body or team are rejected. The model
rejects them and the persistence path refuses them before any write. Warp
(ADR-044) must add its own explicitly named transition.

A committed gravity write rewrites the whole station file with fsync on the server
thread. There is at most one such write per station per 100 server ticks (5 s,
`StationGravityService.WRITE_COOLDOWN_TICKS`). A repeated unchanged value writes
nothing and does not start the cooldown. ORBIT-04 measures the commit time with a
4,096-station registry against the tick budget. The cost is one indexed
lookup per player tick in Space, the same lookup build protection already uses.
Stored values above 4,000 stay valid storage and are clamped only for physics;
`EnvironmentQueries` keeps returning the configured value (0..10), preserving
ADR-028's "configured, not effective physics" contract and API 1.7 bytes.
Packaged and migrated stations store 0, equal to the packaged Space body, so they
see no change. Station regions do change in three cases, now intended:
a data pack that gives the Space body non-zero gravity (the station's stored value
wins inside its region); no loaded catalog (the old Level fallback was 1.0, a
station region now uses its stored value); and a legacy or hand-edited non-zero
record.

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
any station" or: the orbit body (and whether it is available), effective gravity
(with the configured value when clamped), vacuum, solar intensity and sun angle.
The station's name and UUID are shown only to its owner, members and operators,
matching `/arce station list`. It reads only the resolver and sends literal text,
like the other station commands. No new packet, translation key or client state.

### Solar scope in v1.5

The version plan's "lighting/solar" and "display solar conditions" outcomes are met
by the effective solar intensity: the orbited body's value, shown by the command
and available to server code. No solar-power machine exists in the rewrite yet.
Converting intensity to power belongs to the machine content that adds such a
machine (classic-content versions), not to v1.5. This is a scope statement, not
a waiver: no v1.5 plan item asks for power generation. The ORBIT-02 leaf is
titled accordingly.

### Relation to ADR-028

ADR-028 described the gravity controller as Level-based. This ADR amends that
statement: player gravity is position-aware inside station regions. ADR-028's API
semantics are unchanged: `EnvironmentQueries` still returns configured values,
and it still returns no station snapshot when the orbit body is missing from the
catalog, while physics keeps applying the station's stored gravity there.

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

## Acceptance record

The independent contract and implementation review of this draft and of
`f27b11a` found no Critical or High issues. Its verdict was "accept with changes".
Required changes, all applied before this acceptance:

1. Checked updates narrowed to the two named transitions.
2. The gravity write rate bound (cooldown) and an ORBIT-04 measurement.
3. The solar scope stated explicitly.
4. Qualified existing-world gravity effects.
5. An explicit amendment of ADR-028.

It also noted that ORBIT-02 was committed before this acceptance. The fix commit
that applies these changes is the first acceptance-bound implementation. The
review report is archived with the ORBIT evidence. Acceptance of this ADR is not
a Gate approval.
