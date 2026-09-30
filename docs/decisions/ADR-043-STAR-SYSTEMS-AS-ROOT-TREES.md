# ADR-043 — Star systems as celestial root trees

```yaml
status: PROPOSED
date: 2026-09-30
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.5.0
development_dependency: ADR-031, ADR-037, ADR-041
implements: V150-STAR-01 contract for STAR-02 and STAR-03
```

## Context

The v1.5 plan asks for multiple star systems, interstellar routes that only warp
can use, and discovery requirements, with at least two systems for a warp test.
The celestial catalog (schema 2, at most 128 bodies) is a parent tree. Earth is
the required root, mapped to the Overworld; Moon, Space, Mars, Venus and the gas
giant are its descendants. `CelestialCatalog.create` already accepts several
roots, but nothing gives them meaning. Rocket routes (`travel_routes`) are
validated against the body IDs of the same reload candidate. Discovery (ADR-037)
is shared, persisted per body, and is earned by data-satellite missions whose
targets come from satellite definitions.

## Decision

### Identity without a new schema

- A **star system** is the set of bodies under one root body; its stable ID is
  the root body's ID. Earth's tree is the home system, whose ID stays
  `advancedrocketrycommunity:earth` for compatibility. It is displayed as "Sol"
  but no new ID is minted and nothing is renamed.
- A new system is added by data: a root body (typically the star) with
  `orbit.distance = 0`, no Level mapping, not landable, and its descendants.
  Celestial schema 2, the codec and every persisted ID are unchanged.
- Bounds: at most 16 root bodies per catalog (new validation, in addition to
  the 128-body cap); root lookup is a bounded parent walk (depth ≤ 128), and
  the catalog precomputes each body's system once per reload.
- The fixed baseline is unchanged: Earth must be a root mapped to the
  Overworld, and Moon and Space must be under Earth.

### Routes and travel

- Rocket routes must connect anchors in one system. A reload candidate with a
  route whose endpoints are in different systems is rejected as a whole, and the
  previous catalog stays active (existing atomic reload behavior). All packaged
  routes are within Earth's system.
- Interstellar movement exists only as station warp (WARP-01 contract). Rockets
  never plan or fly between systems.

### Knowledge and unlocks

- No new persistence. A body is **known** when it does not require discovery or
  has a recorded discovery (ADR-037). A system is known when any of its bodies is
  known. Warp may target only an orbitable, known body in another system.
- The existing data-satellite research is the unlock path: a system's bodies are
  discovered like Mars. Their satellite definition targets are data (STAR-03).

### Example content (STAR-03)

Add one original example system:

- `advancedrocketrycommunity:tau_ceti`: root star, no Level, not landable or
  orbitable, solar intensity 0, discovery required;
- `advancedrocketrycommunity:tau_ceti_e`: planet under it, orbitable, not
  landable, no Level, solar intensity 0.5, discovery required.

The system uses an existing visual profile; no art is imported, and the facts
are public astronomy. The data satellite's allowed targets include `tau_ceti_e`.
Generated data lands in a new `src/generated/v1.5/resources` directory. Earlier
generated directories are unchanged inputs.

### Client

`CelestialSnapshot` already carries parents. STAR-02 must make the rocket star map
show only the viewer's current system (or group roots) within existing bounds.
Any change to the snapshot wire format needs its own protocol version bump,
recorded in the implementing slice. Per-system sky rendering is not part of this
ADR.

## Non-goals

Galactic coordinates, distances between systems, per-system time or sky, landable
planets in other systems (they would need new fixed Levels, ADR-031), and rocket
interstellar routes.

## Verification

- Unit: system derivation, the 16-root bound, cross-system route rejection with
  the old catalog retained, known-system logic, and Earth-baseline invariance.
- GameTest: reload with the example system; rocket planner cannot reach it; a
  data-satellite discovery makes it known; station environment for a station
  orbiting `tau_ceti_e` resolves its solar intensity.
- Required short commands; generated output only in the new v1.5 directory.

## Rollback

Remove the example data and validation. No save or schema migration exists.
Stations moved to another system by warp would reference a body that is missing
after rollback. That is the existing unknown-orbit-body case: the station is
retained, not remapped.
