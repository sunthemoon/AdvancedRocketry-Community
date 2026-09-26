# ADR-020 — v1.3 development baseline exception

```yaml
status: ACCEPTED
date: 2026-09-26
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.3.0
accepted_by: sunthemoon
accepted_at: 2026-09-26
accepted_baseline: a66f7187b6483dad746010c8648814e0f4548f91
acceptance_ready: true
expires: before v1.3.0 release-candidate freeze
recovery_condition: complete or explicitly disposition every inherited Required Gate before freezing a v1.3.0 candidate
supersedes: ""
```

## Context

[ADR-017](ADR-017-V120-DEVELOPMENT-BASELINE-EXCEPTION.md) permits only v1.2
development before prior-version acceptance. It explicitly excludes v1.3 API
work. [ADR-018](ADR-018-PARITY-FIRST-FULL-ACCEPTANCE-SCHEDULING.md) postpones the
complete integrated test campaign, but does not itself change development order.

The [v1.3 plan](../versions/V1.3.0-PUBLIC-API-COMPATIBILITY.md) still requires
v1.1/v1.2 acceptance and a public API policy. This proposal makes the next
development decision explicit without requiring early execution of the deferred
full campaign. On 2026-09-26 the owner authorized following the recommended
solutions. This accepts the recommended development exception and, separately,
the first-slice policy in ADR-021. It is not release approval.

The proposal branch starts at `3dbe6884e6d8bff3c676c4f7cebee0824b507602`.
That commit does not contain all of the later uncommitted v1.2 implementation.
It is a documentation checkout identity only, not the proposed development baseline.

## Decision

Use `a66f7187b6483dad746010c8648814e0f4548f91`, containing the reviewed v1.2
implementation and evidence, and allow scoped v1.3 production work from it or its descendants
before inherited versions are formally `PASSED`. The public contracts used by
each slice must separately be accepted before implementation.

Allowed scope is the existing v1.3 version plan: public version policy,
rocket/container adapters, movable classification, atmosphere/equipment,
fuel/components, environment/body-context queries, minimal satellite extension
points, an independent compatibility mod and their documentation/short checks.
This does not authorize v1.4+ content or add it to the API kernel.

Every production slice remains separately reviewable and runs bounded relevant
tests and builds, plus migration, conservation and recovery checks when the new
behavior requires them. Existing source/asset provenance, server authority,
loaded-only world access and resource/NBT/network limits remain binding.

The exception does not:

- mark any version `PASSED`/`RELEASED`, assign a candidate, publish or create a tag;
- waive G0–G9, reinterpret historical failures or approve an untested artifact;
- change ADR-018's full-test trigger or extend an older evidence waiver;
- approve arbitrary third-party side effects or unsafe schema/protocol changes;
- freeze the API policy merely because the development-order proposal is accepted.

## Risks, expiry and recovery

Inherited defects may be found later. Keep version-separated implementation
commits, original migration fixtures and explicit per-artifact evidence. A
known blocking resource duplication, corruption or authority defect cannot be
reclassified as deferred long-duration testing.

The exception expires before assigning a v1.3 candidate commit. At that point,
every inherited and v1.3 Required Gate must have actual evidence or a separately
approved precise disposition with owner, expiry and recovery condition. Development
progress alone is not candidate approval. If rejected, v1.3 remains planning-only.

## Acceptance record

Acceptance prerequisites:

1. A real full 40-character commit containing the reviewed v1.2 implementation,
   including the approved persistence changes and their evidence.
2. Maintainer acceptance identity/date and the exact selected baseline.
3. Confirmation that the baseline is not merely this older proposal checkout.
4. Separate acceptance of the contract used by the first production slice.

The selected baseline contains the Precision implementation, representative
migration fixtures, Rolling test extraction, native JEI evidence, consolidated
documentation and byte-preserving archive attributes. It is not the older
proposal checkout. Prior artifact evidence keeps its original identity;
committing does not imply a candidate rebuild. This baseline was merged into
the isolated v1.3 worktree. Preparation and approval are tracked in the
[v1.3 implementation log](../work/v1.3.0-implementation-log.md).
