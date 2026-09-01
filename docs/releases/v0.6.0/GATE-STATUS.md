# GATE_STATUS — v0.6.0 release candidate

```yaml
version: v0.6.0
status: PASSED
tested_implementation_commit: "6a293f705e939a67b5b617b1dfaa7deef4d6d7b6"
base_commit: "b349789c9d330da782c8e719f57ff09d8a262e7f"
merge_commit: "4c43ff6297324049eed758d210b9a5f99ed70876"
branch: "codex/v0.6.0-earth-moon-roundtrip"
build: "1.20.1-0.6.0-dev"
upstream_commit: "c5cd5af62fc07cd4e0d24f06a16033f181c47c04"
provenance_review: APPROVED
pull_request: "https://github.com/sunthemoon/AdvancedRocketry-Community/pull/10"
forge_ci: "https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33476308389"
governance_ci: "https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33476308388"
pull_request_checks: 3/3_PASS
accepted_exception: "docs/decisions/ADR-007-V060-VISUAL-EVIDENCE-ATTESTATION.md"
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
human_approved_at: "2026-09-01"
```

Repository owner `sunthemoon` approved the zero-copy provenance record and the
G0, G8, and G9 decisions on 2026-09-01. G8 uses the actual two-client log
bundle and the explicitly scoped ADR-007 attestation; no screenshot or video
is claimed.

The exact tested implementation is
`6a293f705e939a67b5b617b1dfaa7deef4d6d7b6`. PR #10 passed all three required
checks and merged as `4c43ff6297324049eed758d210b9a5f99ed70876`. A
cache-disabled, forced clean build from that merge reproduced the accepted
917,911-byte JAR, sources JAR, and 591-entry manifest byte-for-byte. The record
is [`evidence/artifact/post-merge-reproduction.json`](evidence/artifact/post-merge-reproduction.json).
No tag or public release is created by this milestone.
