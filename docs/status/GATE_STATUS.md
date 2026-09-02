# GATE_STATUS

```yaml
version: v0.8.0
status: PASSED
tested_implementation_commit: "a3b4192d37c524687a0a26bf12d075a8ec6c1e99"
reviewed_head_commit: "ca80bdb1d3df1adb7f108e8417664b323d8017ee"
build: "1.20.1-0.8.0-dev"
branch: "codex/v0.8.0-progression-satellites"
base_commit: "6b33eaf0be1a01b7feceefe785d8fba7e9717b02"
merge_commit: "8e39b1ef440306632cf101b5017e0bcb1f12eef5"
upstream_commit: "c5cd5af62fc07cd4e0d24f06a16033f181c47c04"
provenance_review: APPROVED
pull_request: "https://github.com/sunthemoon/AdvancedRocketry-Community/pull/12"
forge_ci: "https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33672396978"
governance_ci: "https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33672397107"
pull_request_checks: 4/4_PASS
accepted_exception: "docs/decisions/ADR-011-V080-VISUAL-EVIDENCE-SEQUENCE.md"
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

Repository owner `sunthemoon` approved G0, G8, and G9 on 2026-09-03. G8 is
bound to the ordered terminal screenshots, final-candidate two-client
screenshots, packaged logs, authority matrix, and ADR-011. PR #12 passed 4/4
checks and merged as `8e39b1ef440306632cf101b5017e0bcb1f12eef5`.
A forced cache-disabled clean build from the merge reproduced the accepted JAR
and its 723-entry content manifest byte-for-byte, so v0.8.0 is `PASSED`.
