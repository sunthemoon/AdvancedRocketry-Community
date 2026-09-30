# ADR-048 — v1.6 development baseline exception

```yaml
status: PROPOSED
date: 2026-09-30
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.6.0
accepted_baseline: 940a5ed3b90a4da0ca4e43417b2bddf41ebb7307
accepted_by: ""
accepted_at: ""
acceptance_basis: maintainer standing authorization to proceed with recommended solutions
expires: before v1.6.0 release-candidate freeze
recovery_condition: complete or explicitly disposition every inherited and current Required Gate before freezing a v1.6.0 candidate
supersedes: ""
```

## Context

[v1.6](../versions/V1.6.0-SATELLITE-RESOURCE-MISSIONS.md) lists three
prerequisites: the v1.3 satellite extension contract PASSED, v1.5 PASSED with
the orbit context frozen, and accepted ADRs for satellite types, scheduling and
resource generation. Neither v1.3 nor v1.5 is PASSED. The
[v1.5 development handoff](../releases/v1.5.0/RELEASE-EVIDENCE.md) records
implemented station/warp features and artifact-bound short verification; it
keeps V0/V1/V2, S2, reference-hardware performance and the candidate matrix open.
[ADR-039](ADR-039-V150-DEVELOPMENT-BASELINE-EXCEPTION.md) permits v1.5 only.
ADR-018 defers the full acceptance campaign until original content is complete,
but does not waive version prerequisites by itself.

The maintainer directs continued implementation, recommended solutions when
decisions arise, and verified development commits/pushes without release tags
(2026-09-30). This decision applies that standing authorization to one new
version; it does not invent a new numbered approval message or extend any
historical Gate waiver.

## Decision

Use `940a5ed3b90a4da0ca4e43417b2bddf41ebb7307` (the v1.5 closure commit) as the
immutable v1.6 development baseline on
`codex/v1.6.0-satellite-resource-missions`, while inherited acceptance stays
IN_PROGRESS. Permit only the existing
[Satellites & Resource Missions scope](../versions/V1.6.0-SATELLITE-RESOURCE-MISSIONS.md).
Public and persistent contracts are accepted separately before each slice is
implemented: [ADR-049](ADR-049-SATELLITE-BLUEPRINTS-AND-ASSEMBLY.md) (blueprints,
components, kinds, assembly), [ADR-050](ADR-050-MISSION-SCHEDULER-AND-RECOVERY.md)
(scheduler, lifecycle, cancellation, timeout, restart recovery),
[ADR-051](ADR-051-RESOURCE-MISSION-INSTANCES-AND-DELIVERY.md) (asteroid instances,
gas harvesting, reward delivery) and
[ADR-052](ADR-052-RESOURCE-TABLES-AND-SEEDS.md) (resource tables, seeds and
versioned rewards). The third prerequisite is satisfied only when those four are
ACCEPTED; the first two remain unchecked release prerequisites.

All player-visible outcomes remain required: at least three satellite kinds and
two resource-mission kinds that are completely playable; recovery of in-flight
missions across restart, upgrade and definition change; no permanent forced
chunk loading; the 500-task reference load; conservation of rewards and
components; zero Critical/High findings. Metadata, a diagnostic command or
passing unit tests alone cannot substitute for these outcomes.

Retain short build/unit/DataGen/GameTest checks, independent actual-diff review
and finite save/restart/authority checks for each changed behaviour. Known loss,
duplication or authority failures must be repaired; they are not deferred as
long-load testing. ADR-018 continues to schedule the full campaign; this exception
starts no remote, long-load or real-client campaign by itself.

Not authorized: PASSED/RELEASED or Gate PASS, candidate selection, tags,
publication, silent schema/API changes, unrecorded asset imports, v1.7 production
(orbital lasers/drills, railguns, black-hole power, gravity control, elevator
logistics), terraforming, or claims that v1.5 evidence independently proves new
satellite/mission behaviour. The earliest incomplete acceptance cursor remains
v1.0.0.

## Risk, expiry and recovery

Integration defects may remain in uncompleted inherited checks, and v1.6 changes
the same satellite registry that v0.8–v1.4 missions use. Preserve scoped commits,
exact artifact/fixture identities, independent findings and remaining
obligations. Before a v1.6 candidate freeze, every inherited and current Required
Gate needs evidence or a separately approved precise disposition with owner,
expiry and recovery condition. This decision does not extend any older waiver.
Without this version-limited exception, v1.6 remains planning-only.

## Acceptance record

Pending independent contract review of ADR-048..052 in the
[preparation evidence](../work/v1.6.0-preparation/VERIFICATION.md). Root records
acceptance separately from the independent review; acceptance permits
development, not runtime-completion claims, inherited Gate PASS or publication.
