# ADR-071 - Manual evidence fixture lifecycle and existing class size

```yaml
status: PROPOSED
date: 2026-10-10
owner: sunthemoon
deciders: []
target_version: v1.8.0
slice: C19-MANUAL-FIXTURE-86
```

## Context and bounded candidate

The existing ManualEvidenceTests scenario class at committed source `18bde6da`
spans 3,841 AST lines, already above the 800-line review threshold. The narrowly
assigned repository-fixture candidate reduces that class to 3,784 lines by
moving only initial synthetic repository construction into a small fixture
mixin/helper. It does not add methods to the scenario class or production
validation logic. All original 137 scenario methods, the CLI method and every
non-setUp helper body remain unchanged. Exact before/after source/body checks
and runtime results belong to the separate task checkpoint, not this proposal.

The class fixture contains only initial repository files/history. Each case
physically copies the complete ordinary file tree and Git object/config/index
database, then creates its own artifact files. No hardlinks, alternates,
checkout rematerialization, shared case mutations or approval/result cache.
Class cleanup is registered before construction can fail. Independent fixture
preservation, isolation and failure-cleanup tests are in a separate small class.

## Proposed disposition and remaining scale review

Keep this small development candidate separately reviewable from broad scenario
reorganization. This record explains the existing size; it is not an accepted
size waiver, permission for unrelated growth, or integration/release approval.
The prior independent organization finding remains. Before Main integration,
resolve the oversized scenario organization within v1.8 through a separately
reviewed decomposition preserving original named selections, coverage,
assertions and physical isolation, or an explicit owner-approved bounded
disposition. No next-version recovery condition or Gate exemption is proposed.

The bootstrap scenario class remains a separate issue under proposed ADR-070;
this proposal does not approve or supersede it. Fixture reuse does not qualify
the complete Python command, provenance approvals, native behavior or any Gate.
No persistent/public ID, save schema, API or player-visible policy changes.
This proposed disposition expires at the v1.8 Main integration decision.

## Task and evidence

[Task86](../work/v1.8.0-c19-strict-validator/MANUAL-FIXTURE-CANDIDATE-TASK-86.md)
freezes source scope, preservation and original command budgets. Scoped tests
and independent actual-diff review must be recorded separately before commit.
The candidate remains unintegrated until its prerequisites are resolved. No
ADR acceptance is inferred from tests or from this document's existence.

## Separately reviewed decomposition candidate

[Task92](../work/v1.8.0-c19-strict-validator/MANUAL-DECOMPOSITION-TASK-92.md)
and [independent93](../work/v1.8.0-c19-strict-validator/MANUAL-DECOMPOSITION-REVIEW-TASK-93.md)
produce normally pushed successor `17bb40bcb3b3490469a6433f2d7de2f1baa9b620`,
parent f2587b54, outside Main. All 160 original function nodes are source/AST-exact,
including every original137 scenario, CLI, setUp and fixture helper. The original
138 selections/order and local runnable classes remain. Fourteen non-TestCase
scenario mixins and two fixture-operation helpers replace the oversized class;
the facade is60 AST lines and maximum changed/new class439, all below500.
No production, repository-fixture or original fixture-test bytes change.

Root organization3/fixture3/manual138 and independent organization3/manual138
return original wait0; actual committed organization3 separately returns0.
The size prerequisite is addressed in this reviewed candidate, not by a waiver.
This supersedes the earlier candidate's unresolved manual organization statement;
it does not supersede the historical3,784-line observation or adjacent ADR070.
Method defining-module/traceback locations may change as explicitly frozen;
runnable identities, assertions and physical per-case isolation remain.

Independent review retains its preparation file-cap deviation and unrelated Main
status change, with tested source/tasks/helpers unchanged; no blanket custody
compliance is inferred. Complete qualification, Main integration and those review
limits are recorded in [checkpoint94](../work/v1.8.0-c19-strict-validator/MANUAL-DECOMPOSITION-CHECKPOINT-94.md).
ADR071 remains PROPOSED; this annotation is not owner acceptance or integration,
Gate, delivery or release approval.
