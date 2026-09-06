# ADR-015 — v1.1 development baseline exception

```yaml
status: ACCEPTED
date: 2026-09-06
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.1.0
expires: before v1.1.0 release-candidate freeze
recovery_condition: complete or explicitly disposition every v1.0.0 Required Gate before freezing a v1.1.0 candidate
supersedes: ""
```

## Context

The v1.0 Stable Core implementation is committed at `a4ae20190e9f9a36b2a3cbabf1ea44b6746bc7d6`.
Its development JAR and short functional regressions are available, but the
v1.0 status remains `IN_PROGRESS`: candidate-bound evidence, final migration
and recovery runs, the deferred reference workload, fresh real-client
acceptance, independent review and release approval are incomplete.

The normal v1.1 prerequisite permits production implementation only after
v1.0 is `PASSED`. The repository owner accepts the residual development risk
and prioritizes completing the expansion implementation before the deferred
long-running and final-candidate acceptance work.

## Decision

Use commit `a4ae20190e9f9a36b2a3cbabf1ea44b6746bc7d6` as the immutable v1.1
development baseline and permit scoped v1.1 production implementation before
v1.0 is formally `PASSED`.

This is only an implementation-order exception. It does not:

- mark v1.0 as `PASSED`, released or tagged;
- convert the v1.0 development JAR into a release candidate;
- waive any v1.1 G0–G9 requirement;
- authorize a v1.1 release candidate while inherited v1.0 risks are unknown;
- extend earlier visual-evidence exceptions.

Each v1.1 slice must remain independently reviewable and must preserve the
v1.0 fixture and protocol baseline. Long-duration performance work stays
deferred until the implementation is substantially complete.

## Risk and mitigation

- A defect not found by the incomplete v1.0 candidate matrix may be inherited
  by v1.1. The immutable baseline, retained fixtures and incremental regression
  tests keep that defect attributable and reversible.
- Save, passenger-recovery and multiplayer behavior may need correction while
  v1.1 migration work is underway. Migration work must retain the original
  v1.0 fields and fail closed rather than guess a destination.
- Performance regressions may remain undiscovered until the deferred reference
  workload runs. Every world traversal and variable-size input still obeys its
  hard budget; no budget is relaxed by this ADR.
- Release status may be confused with implementation status. Status and release
  evidence must continue to identify v1.0 as `IN_PROGRESS` until its actual
  approvals exist.

## Validation

- [x] v1.0 implementation baseline commit recorded
- [x] v1.0 development artifact checksum recorded in its evidence handoff
- [x] Remaining v1.0 acceptance work remains visible
- [x] Maintainer accepted the implementation-order risk
- [ ] All inherited risks dispositioned before v1.1 candidate freeze

## Revisit when

Revisit before assigning a v1.1 candidate commit. At that point every open
v1.0 Required Gate must either be completed or receive a separate, precise ADR
with an owner, rationale, expiry and recovery condition. This ADR cannot by
itself approve a release.
