# ADR-039 — v1.5 development baseline exception

```yaml
status: ACCEPTED
date: 2026-09-28
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.5.0
accepted_baseline: 6f530ac7db4bf0e06be6d6aaef35e6ca31a5651b
accepted_by: sunthemoon
accepted_at: 2026-09-28
acceptance_basis: maintainer standing authorization to proceed with recommended solutions
expires: before v1.5.0 release-candidate freeze
recovery_condition: complete or explicitly disposition every inherited and current Required Gate before freezing a v1.5.0 candidate
supersedes: ""
```

## Context

The [v1.4 development handoff](../releases/v1.4.0/RELEASE-EVIDENCE.md)
records implemented planetary features and artifact-bound short verification;
it is not a passed release. ADR-030 permits v1.4 only. ADR-018 defers the full
acceptance campaign until original-content implementation is complete but does
not itself waive version prerequisites.

The maintainer directs continued implementation, recommended solutions when
decisions arise, and verified development commits/pushes without release tags.
This decision applies that standing authorization to one new version; it does
not invent a new numbered approval message or extend historical Gate waivers.

## Decision

Use `6f530ac7db4bf0e06be6d6aaef35e6ca31a5651b` as the immutable v1.5 development
baseline on `codex/v1.5.0-orbital-station-warp`, while inherited acceptance stays
IN_PROGRESS. Permit only the existing
[Orbital/Station/Warp scope](../versions/V1.5.0-ORBITAL-STATION-WARP.md).
Accept public/persistent contracts separately before implementing each slice;
ADR-040 is the station foundation contract, not the complete warp protocol.

All player-visible outcomes remain required: arbitrary-body stations and bounded
expansion/access; actual orbit environment, solar/gravity and presentation;
costed, staged and recoverable warp with passenger/docked-rocket authority;
multi-star routes/unlocks; UI/security and a bounded elevator endpoint contract.
Metadata, a diagnostic command or passing unit tests alone cannot substitute
for these outcomes. Full elevator logistics and v1.6+ content remain excluded.

Retain short build/unit/DataGen/GameTest checks, independent actual-diff review
and finite save/restart/authority checks needed by each changed behavior.
Known loss, duplication or authority failures must be repaired; they are not
deferred as long-load testing. ADR-018 continues to schedule the full campaign;
this exception starts no remote, long-load or real-client campaign by itself.

Not authorized: PASSED/RELEASED or Gate PASS, candidate selection, tags,
publication, silent schema/API changes, unrecorded asset imports, v1.6 production
or claims that v1.4 evidence independently proves new station/warp behavior.
The earliest incomplete acceptance cursor remains v1.0.0.

## Risk, expiry and recovery

Integration defects may remain in uncompleted inherited checks. Preserve scoped
commits, exact artifact/fixture identities, independent findings and remaining
obligations. Before a v1.5 candidate freeze, every inherited and current Required
Gate needs evidence or a separately approved precise disposition with owner,
expiry and recovery condition. This decision does not extend any older waiver.
Without this version-limited exception, v1.5 remains planning-only.

## Acceptance record

Recorded on 2026-09-28 under the maintainer's standing direction to continue
implementation and use recommended solutions. This is a scoped application of
that authorization, not a new manual approval message for this numbered ADR.
Independent source/draft review and resolved station-contract findings are
retained in the [preparation evidence](../work/v1.5.0-preparation/VERIFICATION.md).
Root records this acceptance separately from the independent review. It permits
development, not runtime-completion claims, inherited Gate PASS or publication.
