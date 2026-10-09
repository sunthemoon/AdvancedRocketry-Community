# ADR-070 - Bootstrap test fixture lifecycle and existing class size

```yaml
status: PROPOSED
date: 2026-10-10
owner: sunthemoon
deciders: []
target_version: v1.8.0
slice: C19-BOOTSTRAP-FIXTURE-49
```

## Context

The existing Python unittest fixture class exceeds the repository's 800-line
review threshold before this slice. At base e28fd8a6 its AST spans lines 30-2141
(2,112 lines); candidate8062781c spans 30-2211 (2,182 lines). The separate real
historical-repository class remains 11 lines. This is a test scenario collection,
not an Entity, BlockEntity, persistent world service or production dependency.
The size still creates review and maintenance cost and needs an explicit record.

The candidate changes only fixture preparation and adds two direct preservation/
isolation scenarios. Its original 93 tests and complete 37-statement construction
tail are unchanged. Production validation is not moved into the test class.
The fixture builds a private minimal synthetic Git history once per class and
copies the entire raw tree/object database into each case; manifests are deep
copies. It never caches validation or approval results. Cleanup is registered
before construction or copying can fail. Tests mutate only their own copies.

## Proposed disposition and boundary

Keep this qualification candidate separately reviewable instead of combining it
with a broad scenario-class reorganization. Its current public test selection
names and helper semantics stay stable. Do not add further unrelated scenarios,
production logic or a general fixture framework to this class. This explanation
is not an accepted size waiver or permission for continued growth.

Before Main integration, the owner must resolve the size review within v1.8:
either approve this exact bounded test-only disposition or assign a separate
decomposition of scenario groups/shared fixture construction, with original
coverage, named selections and isolation verified. No later-version feature may
serve as the recovery condition. This proposed record expires at the v1.8 Main
integration decision and cannot authorize release or any Required Gate.

## Evidence and remaining work

[Checkpoint55](../work/v1.8.0-c19-strict-validator/BOOTSTRAP-FIXTURE-CHECKPOINT-55.md)
records exact source bindings, independent actual-diff/isolation review and full
95-test command at clean committed806. The broad Python command still times out
under its unchanged limit; old and new failed evidence stays unchanged. Ordinary
failure-path cleanup is assessed from installed unittest source, not injected
runtime failures. Cross-platform copying/ACL/abnormal-termination and whole
qualification are open. No accepted ADR, budget or Gate is modified here.
