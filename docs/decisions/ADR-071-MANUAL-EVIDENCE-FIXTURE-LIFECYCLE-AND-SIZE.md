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
