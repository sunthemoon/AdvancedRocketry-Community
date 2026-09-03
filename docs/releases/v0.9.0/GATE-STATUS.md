# GATE_STATUS — v0.9.0 Beta 1 candidate

```yaml
version: v0.9.0
status: IN_PROGRESS
tested_implementation_commit: "f6cd77cebdb0a851cab76accbf66de565473b545"
reviewed_head_commit: ""
base_commit: "0d59c01da458e13ed0014e98f91379c6f783e19d"
merge_commit: ""
branch: "codex/v0.9.0-beta-hardening"
build: "1.20.1-0.9.0-beta.1"
artifact_sha256: "fbddf66938000cba369a83d4a22ff36b5ff1c9c635a0abd14f672b454e3946ad"
upstream_commit: "c5cd5af62fc07cd4e0d24f06a16033f181c47c04"
provenance_review: APPROVED
pull_request: "https://github.com/sunthemoon/AdvancedRocketry-Community/pull/13"
forge_ci: ""
governance_ci: ""
pull_request_checks: PENDING
accepted_exception: "docs/decisions/ADR-013-V090-VISUAL-EVIDENCE-INHERITANCE.md"
release_url: ""
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

The repository owner directly approved G0, G8, and the Beta publication
decision on 2026-09-03. ADR-013 records the bounded reuse of genuine v0.8.0
screenshots for unchanged core visuals and the requirement to capture fresh
multi-scale evidence for v1.0.0.

G0-G8 are evidence-complete and the owner has approved Beta publication. G9
and the overall milestone remain `IN_PROGRESS` until PR checks, merge, exact
post-merge reproduction, and the GitHub pre-release asset are complete and
recorded.
