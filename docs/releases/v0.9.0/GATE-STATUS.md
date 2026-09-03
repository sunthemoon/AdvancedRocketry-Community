# GATE_STATUS — v0.9.0 Beta 1 pre-release

```yaml
version: v0.9.0
status: PASSED
tested_implementation_commit: "f6cd77cebdb0a851cab76accbf66de565473b545"
reviewed_head_commit: "7841dcc0d30b26a207ee221b0efbd1e25d459ed3"
base_commit: "0d59c01da458e13ed0014e98f91379c6f783e19d"
merge_commit: "a7196ff9b22220c344071a1af69a663036f76aef"
branch: "codex/v0.9.0-beta-hardening"
build: "1.20.1-0.9.0-beta.1"
artifact_sha256: "fbddf66938000cba369a83d4a22ff36b5ff1c9c635a0abd14f672b454e3946ad"
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

The repository owner directly approved G0, G8, and the Beta publication
decision on 2026-09-03. ADR-013 records the bounded reuse of genuine v0.8.0
screenshots for unchanged core visuals and the requirement to capture fresh
multi-scale evidence for v1.0.0.

G0-G9 are complete. PR #13 passed all four required checks and merged as
`a7196ff9b22220c344071a1af69a663036f76aef`; its tree exactly matches reviewed
head `7841dcc0d30b26a207ee221b0efbd1e25d459ed3`. A cache-disabled clean build
reproduced the candidate JAR and 758-entry manifest byte-for-byte, and all four
merge-commit checks passed. GitHub pre-release `v0.9.0-beta.1` serves the
download-verified JAR. This is a Beta, not a stable-release claim.
