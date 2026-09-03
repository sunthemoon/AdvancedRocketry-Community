# GATE_STATUS

```yaml
version: v0.9.0
status: PASSED
tested_implementation_commit: "f6cd77cebdb0a851cab76accbf66de565473b545"
reviewed_head_commit: "7841dcc0d30b26a207ee221b0efbd1e25d459ed3"
build: "1.20.1-0.9.0-beta.1"
branch: "codex/v0.9.0-beta-hardening"
base_commit: "0d59c01da458e13ed0014e98f91379c6f783e19d"
merge_commit: "a7196ff9b22220c344071a1af69a663036f76aef"
upstream_commit: "c5cd5af62fc07cd4e0d24f06a16033f181c47c04"
provenance_review: APPROVED
pull_request: "https://github.com/sunthemoon/AdvancedRocketry-Community/pull/13"
forge_ci: "https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33738824242"
governance_ci: "https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33738824130"
pull_request_checks: 4/4_PASS
accepted_exception: "docs/decisions/ADR-013-V090-VISUAL-EVIDENCE-INHERITANCE.md"
release_url: "https://github.com/sunthemoon/AdvancedRocketry-Community/releases/tag/v0.9.0-beta.1"
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
  G9: PASS
overall: PASSED
remaining_items: []
human_approved_by: "sunthemoon"
human_approved_at: "2026-09-03"
```

The immutable v0.8.0 Gate remains at
[`../releases/v0.8.0/GATE-STATUS.md`](../releases/v0.8.0/GATE-STATUS.md).
v0.9.0 passed G0-G9. PR #13 passed all four required checks and merged as
`a7196ff9b22220c344071a1af69a663036f76aef`; the merge tree equals reviewed
head `7841dcc0d30b26a207ee221b0efbd1e25d459ed3`. A cache-disabled clean `main`
build reproduced the accepted JAR and content manifest byte-for-byte, all four
merge checks passed, and the same verified JAR is published as Beta pre-release
`v0.9.0-beta.1`.
