# GATE_STATUS

```yaml
version: v0.9.0
status: IN_PROGRESS
tested_implementation_commit: "f6cd77cebdb0a851cab76accbf66de565473b545"
reviewed_head_commit: ""
build: "1.20.1-0.9.0-beta.1"
branch: "codex/v0.9.0-beta-hardening"
base_commit: "0d59c01da458e13ed0014e98f91379c6f783e19d"
merge_commit: ""
upstream_commit: "c5cd5af62fc07cd4e0d24f06a16033f181c47c04"
provenance_review: APPROVED
pull_request: "https://github.com/sunthemoon/AdvancedRocketry-Community/pull/13"
forge_ci: ""
governance_ci: ""
pull_request_checks: PENDING
accepted_exception: "docs/decisions/ADR-013-V090-VISUAL-EVIDENCE-INHERITANCE.md"
gates:
  G0: PASS
  G1: PASS
  G2: PASS
  G3: PASS
  G4: PASS
  G5: PASS
  G6: PASS
  G7: PASS
  G8: PASS
  G9: IN_PROGRESS
overall: IN_PROGRESS
remaining_items:
  - pass all required PR checks and merge PR 13
  - reproduce the exact candidate from the merge commit
  - publish and verify GitHub pre-release v0.9.0-beta.1
human_approved_by: "sunthemoon"
human_approved_at: "2026-09-03"
```

The immutable v0.8.0 Gate remains at
[`../releases/v0.8.0/GATE-STATUS.md`](../releases/v0.8.0/GATE-STATUS.md).
v0.9.0 has complete pre-merge candidate evidence and owner acceptance on its
isolated Beta-hardening branch. G9 and the milestone remain `IN_PROGRESS`
until the reviewed head merges, reproduces exactly on `main`, and is published
as a verified GitHub pre-release.
