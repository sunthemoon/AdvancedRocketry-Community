# ADR-017 — v1.2 development baseline exception

```yaml
status: PROPOSED
date: 2026-09-06
requested_decider: sunthemoon
owner: sunthemoon
target_version: v1.2.0
proposed_baseline: 112591404917df5e8c02e7ea59af5ce95c8fec05
expires: before v1.2.0 release-candidate freeze
recovery_condition: complete or explicitly disposition every inherited v1.0.0 and v1.1.0 Required Gate before freezing a v1.2.0 candidate
supersedes: ""
```

## Context

The v1.1.0 expansion implementation is present in commits
`da3e6efbc933034186d27b1809756d67cedc7b30` and
`caf251e3540b6f24a4939edece39fecfe8fb249e`. The behavior-preserving v1.1.1
maintenance split is present in `660cfc95c1dff7901a25ad937cc3c0e3242e51ef`.
The v1.2.0 audit and contract proposals are present through
`112591404917df5e8c02e7ea59af5ce95c8fec05`.

The implementation tasks in the v1.1.0 log are verified except final acceptance, but
the version remains `IN_PROGRESS`: no candidate commit, complete G0–G9 record,
independent review or human approval is assigned. The repository therefore does not
currently permit v1.2.0 production implementation.

The owner has previously prioritized completing implementation before deferred
long-duration load, remote Linux, real-GPU and two-client acceptance. ADR-015 applied
that priority only to v1.1.0 and explicitly expires before its candidate freeze. It
cannot be silently extended to v1.2.0. A new, version-bounded decision is required.

ADR-016 separately proposes the machine and multiblock contract. This ADR does not
accept ADR-016; both decisions remain independently reviewable.

## Proposed decision

If explicitly accepted by the repository maintainer, use commit
`112591404917df5e8c02e7ea59af5ce95c8fec05` as the immutable v1.2.0 development
baseline and permit scoped v1.2.0 production implementation before v1.1.0 is formally
`PASSED`, provided ADR-016 is also accepted before its contract is implemented.

The exception changes development order only. It does not:

- mark v1.0.0, v1.1.0, v1.1.1 or v1.2.0 `PASSED`, `RELEASED` or tagged;
- assign an integration/candidate commit or artifact checksum;
- waive, downgrade or reinterpret G0–G9 for any version;
- authorize v1.2.0 release-candidate freeze while inherited Gates remain open;
- extend ADR-013 or any earlier visual/multiplayer evidence exception;
- permit long-lived save IDs before ADR-016 acceptance;
- permit v1.3 public API, later machines or unrelated content in the v1.2 work;
- relax pattern, queue, packet, NBT, resource-conservation or no-forced-chunk limits.

Each production slice must start from or descend from the proposed baseline, use the
v1.2.0 implementation log, remain independently revertible, and run its targeted tests.
Long-duration acceptance can stay deferred while implementation is incomplete, but
bounded short regressions, migration fixtures and resource-conservation tests cannot be
deferred from the slice that introduces their behavior.

## Allowed work if accepted

Only the v1.2.0 scope in its version plan and accepted ADR-016:

1. pure Java process model and recoverable transaction journal;
2. Item/Fluid/Energy port policies and Forge adapters;
3. bounded pattern transform, diagnostics and controller/part lifecycle;
4. Rolling Machine, Electrolyzer migration and Precision Assembler;
5. common menu summary, optional JEI adapter and DataGen;
6. migration, GameTest, dedicated-server, security and performance evidence.

The first production slice is the pure Java process model plus transaction recovery.
It must not register blocks or persist new world data until the corresponding ADR-016
identity/schema section is accepted.

## Risk and mitigation

### Inherited release defects

An unobserved v1.0/v1.1 candidate, migration, multiplayer or visual defect may be
carried into v1.2. The fixed baseline and version-separated commits keep the source of
regressions attributable. Existing v1.0 and v1.1 fixtures remain mandatory and cannot
be rewritten to match v1.2 output.

### Save compatibility before final acceptance

Machine work introduces new persistent identities while previous versions are not
release-approved. ADR-016 must be accepted first; every object has its own schema,
future data is preserved-and-blocked, and Electrolyzer schema 1 stays readable.

### Resource duplication or loss

The process journal and resource plan are implemented before representative machines.
Every batch-changing slice includes insufficient-input, blocked-output, stale revision,
duplicate transaction and interruption recovery tests. A failure blocks that slice even
while long performance work is deferred.

### Performance evidence arrives late

Short deterministic budget checks accompany pattern/queue implementations. The owner-
deferred reference workload remains visible and must run before a candidate can freeze;
this proposal does not substitute empty-server or unit-test results for G7.

### Release-status confusion

Central status files continue to report their actual in-progress version. Development
branches and artifacts use `-dev`; no candidate identity, release manifest or tag is
created from this exception.

## Acceptance record

The proposal becomes effective only when this block is changed by the repository
maintainer in a dedicated commit:

```yaml
status: PROPOSED
accepted_by: ""
accepted_at: ""
accepted_baseline: ""
```

A continuation request, implementation commit or passing automated test is not a
substitute for this explicit record.

## Validation before acceptance

- [x] Full immutable development baseline recorded.
- [x] v1.1.0 implementation and acceptance statuses distinguished.
- [x] ADR-015 expiry and non-transferability recorded.
- [x] ADR-016 remains a separate proposed contract decision.
- [x] Inherited risks, per-slice checks and release-candidate prohibition documented.
- [ ] Maintainer explicitly records acceptance, identity, date and exact baseline.

## Expiry and recovery

This exception expires before any v1.2.0 release-candidate commit is assigned. At that
point every inherited v1.0.0 and v1.1.0 Required Gate must be completed or dispositioned
by a separate precise ADR with owner, affected scope, expiry and recovery condition.
ADR-017 cannot approve a candidate or release by itself.

If ADR-016 changes after implementation starts, affected slices return to contract
review and migration impact is recorded before integration. If the exception is
rejected, the audit/contract commits remain documentation-only and production work
waits for `v1.1.0 PASSED`.
