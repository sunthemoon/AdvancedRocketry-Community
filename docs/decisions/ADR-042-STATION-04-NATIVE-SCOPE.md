# ADR-042 — STATION-04 native coverage scope

```yaml
status: ACCEPTED
date: 2026-09-30
deciders: [sunthemoon]
owner: sunthemoon
accepted_by: sunthemoon
acceptance_basis: maintainer standing goal to complete the project with recommended solutions; scope record, not a Gate waiver
target_version: v1.5.0
affects: V150-STATION-04, V150-MIG-01
expires: before V150-ACC-02 (candidate-bound v1.5 acceptance)
```

## Context

V150-STATION-04 asks that an authentic v1.4 copy upgrade and restart with its
original station, team, orbit, pad and rocket identities, unknown-body handling
and neighboring blocks intact. The independent re-review found that the native
evidence covered team membership, expansion and restart, but not invitations,
blocks, rocket identities or unknown-body handling.

The retained v1.4 world (`arce-v140-mig-worlds…/server-final/world`) has no
rocket transactions or transfers, and both of its stations orbit bodies that are
present in the catalog. A check against it cannot show rocket or unknown-body
preservation; such a check would pass vacuously.

## Decision

1. STATION-04 native evidence now includes a v1.4-written invitation, created by
   the v1.4 host's own `invite` command from a connected owner, and four block
   markers. The markers are placed by the v1.4 host: three in the ring the
   expansion adopts, one in the gap beyond it. All must survive migration,
   expansion and restart.
2. Native preservation of **rocket identities** (docked or in-flight rocket
   transactions/transfers that touch a station) and of a station whose **orbit
   body is missing** from the catalog moves to **V150-MIG-01**, the station/core/
   passenger/rocket recovery matrix. That leaf builds fixtures that actually
   contain those states.
3. Until then, the existing unit evidence stands and nothing more is claimed:
   - legacy/current codec preservation of unknown orbit IDs (`StationSchemaMigrationTest`);
   - resolver fallback for a missing body (`StationOrbitEnvironmentResolverTest`);
   - rocket transaction and transfer authorities left byte-equal apart from the
     mission clock during migration.

This moves coverage between current-version leaves. It does not waive a Required
Gate or lower an acceptance bar. STATION-04 may be marked `verified` for its
remaining scope once reviewed. MIG-01 must name these two cases explicitly.

## Expiry and recovery

This record expires before V150-ACC-02. If MIG-01 has not produced native rocket
and missing-orbit-body evidence by then, STATION-04's original acceptance is open
again and v1.5 cannot pass G0-G9.
