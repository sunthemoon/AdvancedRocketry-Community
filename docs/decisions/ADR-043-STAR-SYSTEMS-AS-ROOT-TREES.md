# ADR-043 — Star systems as celestial root trees

```yaml
status: ACCEPTED
date: 2026-09-30
deciders: [sunthemoon]
owner: sunthemoon
accepted_by: sunthemoon
accepted_at: 2026-09-30
acceptance_basis: maintainer standing goal to complete the project with recommended solutions, after independent contract review ("accept with changes"); all six required changes applied
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
- **Compatibility impact.** The 16-root limit and the cross-system route rule
  (below) can reject a data pack that is valid today, and they reject it as a
  whole.
  - On a reload the previous catalog stays active.
  - On the initial load the server refuses to start, because
    `PlanetaryDefinitionReloadListener` has no valid pair to apply. The log names
    the rule (`too many root bodies: N > 16`, or
    `route <id> connects systems <a> and <b>`). Operators remove or merge roots,
    or split the route, and restart.
  - A test covers initial-load refusal. The packaged data has one root and no
    cross-system route.
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
  known. Warp targets follow ADR-044: any orbitable, known body other than the
  current orbit, in the same system (relocation) or another one (interstellar).
- System membership is **derived at each reload and never persisted**. A data pack
  that re-parents a body moves existing stations between systems without a warp.
  It can also change a pending warp's cost class, so ADR-044 computes the cost from
  the catalog captured at commit.
- The existing data-satellite research is the unlock path: a system's bodies are
  discovered like Mars. Their satellite definition targets are data (STAR-03).

### Example content (STAR-03)

Add one original example system:

- `advancedrocketrycommunity:tau_ceti`: root star, no Level, not landable or
  orbitable, solar intensity 0. **No discovery required**: the star is public and
  is not a satellite target, so requiring discovery would lock it forever.
- `advancedrocketrycommunity:tau_ceti_e`: planet under it, orbitable, not
  landable, no Level, solar intensity 0.5, discovery required.

The system uses an existing visual profile, and no art is imported. The star's
real properties cannot be represented: temperature is bounded at 2,000 K and
gravity at 4.0. The definitions therefore use **placeholder values** inside the
schema bounds (gravity 0, a vacuum atmosphere profile, and a temperature within
bounds). They are not literal astronomy. The system has no rocket routes, so a
rocket docked at a station there cannot fly anywhere (disclosed; nothing is
landable there).

Packaging: the data satellite's allowed targets gain `tau_ceti_e` through a v1.5
copy of `satellite_definitions/data_satellite.json` in the new
`src/generated/v1.5/resources`. The integrator excludes the v1.4 copy (as the v0.8
copy is already excluded) in `processResources` and `sourcesJar`, adds v1.4 to the
DataGen `--existing` list, and reruns the one-authoritative-copy-per-resource audit
(ADR-031/037). Earlier generated directories are otherwise unchanged inputs.

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
retained, not remapped, and ADR-044 allows it an evacuation warp to any valid
target.

## Acceptance record

An independent contract review found ADR-043 sound and feasible:

- several roots are already accepted;
- the star map lays out each root as its own row;
- the snapshot codec has no single-root assumption;
- the cross-system check can run inside the existing atomic reload pair.

Its verdict was "accept with changes". All six required changes are applied
above: target alignment with ADR-044, compatibility impact with a test,
reload-derived membership, satellite packaging, placeholder star values and no
discovery for the star, and evacuation for a missing orbit body. This is not a
Gate approval.
