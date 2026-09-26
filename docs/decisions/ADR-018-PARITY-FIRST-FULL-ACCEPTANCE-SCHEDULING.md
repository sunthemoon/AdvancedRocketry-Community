# ADR-018 — Parity-first full acceptance scheduling

```yaml
status: ACCEPTED
date: 2026-09-06
deciders: [sunthemoon]
accepted_at: 2026-09-06
owner_reaffirmed_at: 2026-09-26
owner: sunthemoon
scope: v1.2.0 through classic machine and dimension completion
expires: before the first feature-parity release candidate
recovery_condition: run and disposition the deferred full matrix before assigning a feature-parity candidate
supersedes: ""
```

## Context

The owner prioritizes completing the original machine and dimension implementation over
repeated long-duration acceptance runs while the content matrix is still changing.
Running the entire remote Linux, real-GPU, two-client and long-load matrix after every
intermediate content slice would consume substantial time without proving the final
parity build.

This does not make untested implementation acceptable. Small regressions are cheaper to
locate at the slice that introduces them, and migration, conservation, security and
bounded-work invariants cannot be reconstructed reliably only at the end.

## Decision

Defer the complete integrated acceptance campaign until every original machine and
dimension in the porting matrix has a completed implementation. A `DEFERRED`,
`REJECTED`, `MISSING` or `UNKNOWN` row does not satisfy this scheduling trigger.
The deferred campaign includes:

- long-duration and high-population load/soak runs;
- the complete remote Debian dedicated-server matrix;
- the complete real-GPU visual matrix;
- the complete two-real-client multiplayer matrix;
- the all-machine, all-dimension end-to-end regression campaign.

Every implementation slice still runs its bounded targeted unit/GameTests as applicable,
compiles its affected runtime adapters, and records migration, resource-conservation,
security and crash-recovery evidence when it introduces those behaviors. Short checks
may not be deleted or weakened to defer a defect.

The decision changes test scheduling only. It does not:

- mark an intermediate or parity version `PASSED`, `RELEASED` or tagged;
- claim G0–G9 evidence that was not actually collected;
- permit a feature-parity candidate while the deferred matrix is incomplete;
- waive provenance, schema, packet/NBT bounds, no-forced-load or server-authority rules;
- turn an automated build into V1/V2, long-soak or human acceptance evidence.

## Completion trigger

The full campaign becomes mandatory when the porting matrix shows every original
machine and dimension implemented. Closing an inventory row by deferring or rejecting
it is not completion for this trigger. Finishing an individual machine, dimension,
slice or intermediate version is not a trigger for the campaign. The owner reaffirmed
this rule on 2026-09-10, 2026-09-11, 2026-09-25 and 2026-09-26: complete the original
machines and dimensions first, then run the complete acceptance campaign. Before
assigning the first feature-parity candidate, the owner must review the matrix,
run or precisely disposition every deferred environment and record the resulting
Gate status. A version number alone
does not satisfy this trigger.

## Consequences

- Development time is spent on completing content before expensive repeated campaigns.
- Intermediate versions remain honestly `IN_PROGRESS` where their full Gate evidence is
  deferred.
- Slice-local failures remain attributable because targeted tests continue to run.
- Final acceptance will be larger and may reveal integration defects later than an
  incremental full campaign would.
