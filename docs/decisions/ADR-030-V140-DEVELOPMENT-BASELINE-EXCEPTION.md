# ADR-030 — v1.4 development baseline exception

```yaml
status: ACCEPTED
date: 2026-09-27
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.4.0
accepted_baseline: 1a192b4b9b9a90372c11643e086f7b0fbdcd1860
accepted_by: sunthemoon
accepted_at: 2026-09-27
acceptance_basis: maintainer standing authorization to proceed with recommended solutions
expires: before v1.4.0 release-candidate freeze
recovery_condition: complete or explicitly disposition every inherited and current Required Gate before freezing a v1.4.0 candidate
supersedes: ""
```

## Context

The reviewed v1.3 implementation now has a
[development handoff](../releases/v1.3.0/RELEASE-EVIDENCE.md). Its G0-G9 and
inherited acceptance are incomplete; that handoff is not a release candidate.
[ADR-020](ADR-020-V130-DEVELOPMENT-BASELINE-EXCEPTION.md) allowed only v1.3 work
and does not permit Planetary Expansion. [ADR-018](ADR-018-PARITY-FIRST-FULL-ACCEPTANCE-SCHEDULING.md)
postpones the full campaign but does not independently change version order.

The maintainer has directed continued implementation and authorized recommended
solutions. This decision is a separate, version-limited
development exception, not a broader acceptance waiver or an extension of an
expired historical approval.

## Decision

Use `1a192b4b9b9a90372c11643e086f7b0fbdcd1860`, containing the complete reviewed
v1.3 development implementation and handoff, as the immutable v1.4 development
baseline. Allow the existing
[v1.4 scope](../versions/V1.4.0-PLANETARY-EXPANSION.md) on its own branch even
while inherited versions remain IN_PROGRESS. Public/persistent contracts must
be accepted separately before their implementation; ADR-031 records the first.

Allowed scope: versioned multi-body data, fixed/startup-data Level mappings,
contrasting explorable worlds and a non-landable gas-giant example, environment,
star map and filtered navigation, discovery/research, sky/audio, audited assets,
migration and bounded verification. Do not silently replace any required
player-visible outcome with a metadata-only substitute.

Not authorized:

- v1.5+ production, arbitrary runtime dimension creation, full terraforming,
  multi-star warp or a wholesale legacy framework recreation;
- PASSED/RELEASED status, candidate assignment, tags, publication or Gate PASS;
- omission of schema/migration, resource conservation, authority or hard bounds;
- unrecorded art/code imports, unverified current-artifact claims, changing old
  evidence or automatically extending previous release waivers.

Every production slice keeps its short build/unit/GameTest checks and the
bounded save/restart/authority checks its changes need. Independent review must
inspect the actual diff and run applicable key checks. Full long-load, remote,
real-GPU/two-client and all-content testing remains scheduled by ADR-018, not
triggered by this decision or the completion of an intermediate version.

## Risks, expiry and recovery

Uncompleted inherited checks can reveal integration defects later. Retain
version-separated commits, exact artifacts, original fixtures and remaining
Gate obligations. Known duplication, corruption or authority failures require
repair rather than classification as deferred long-duration testing.

The exception expires before a v1.4 candidate is selected. Every inherited and
v1.4 Required Gate then needs actual evidence or a separately approved precise
disposition with owner, expiry and recovery condition. Prior exception expiry
conditions remain unchanged. Without this scoped exception, v1.4 would remain
planning-only until its original prerequisites pass.

## Acceptance record

Recorded on 2026-09-27 under the maintainer's standing direction to continue
implementation and use recommended solutions when a decision is needed.
This is a scoped application of that authorization, not a claim of a new
manual review or approval message for this numbered ADR.

The independent read-only review checked the actual baseline, proposed scope
and first contract; its unchanged report is archived with the
[preparation evidence](../work/v1.4.0-preparation/VERIFICATION.md). Review did
not confer maintainer authority or mark a Gate passed. Root recorded acceptance
after resolving the contract findings, separately accepting ADR-031. Candidate,
release and inherited acceptance remain unapproved.
