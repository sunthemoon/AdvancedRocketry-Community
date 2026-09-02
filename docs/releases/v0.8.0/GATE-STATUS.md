# GATE_STATUS — v0.8.0 release candidate

```yaml
version: v0.8.0
status: PASSED
tested_implementation_commit: "a3b4192d37c524687a0a26bf12d075a8ec6c1e99"
reviewed_head_commit: "ca80bdb1d3df1adb7f108e8417664b323d8017ee"
base_commit: "6b33eaf0be1a01b7feceefe785d8fba7e9717b02"
merge_commit: "8e39b1ef440306632cf101b5017e0bcb1f12eef5"
branch: "codex/v0.8.0-progression-satellites"
build: "1.20.1-0.8.0-dev"
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

Repository owner `sunthemoon` approved the zero-copy provenance inventory and
the G0, G8, and G9 decisions on 2026-09-03. G8 is bound to the ordered terminal
screenshots, final-candidate two-client screenshots, packaged lifecycle logs,
authority matrix, and ADR-011. The terminal sequence is explicitly labeled as
pre-candidate and is not represented as continuous video.

All technical and owner-review Gates are complete. PR #12 passed all four
checks and merged as
`8e39b1ef440306632cf101b5017e0bcb1f12eef5`. A cache-disabled forced clean
build from that merge reproduced the 1,166,061-byte JAR and 723-entry content
manifest byte-for-byte. The immutable record is
[`evidence/artifact/post-merge-reproduction.json`](evidence/artifact/post-merge-reproduction.json).
No tag or public release is created by this milestone.
