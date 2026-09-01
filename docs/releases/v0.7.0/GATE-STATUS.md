# GATE_STATUS — v0.7.0 release candidate

```yaml
version: v0.7.0
status: PASSED
tested_implementation_commit: "e1c2db8ca3e67ae7f92fbbbbd5b6c23a25f7412f"
reviewed_head_commit: "d4caac833ba20c1f017631fb18dafd43e50a6f7d"
base_commit: "83bb6748444845aaad9d16f66da8c70f86b737be"
merge_commit: "b75e301f6cd77cfc1c1ade0e9b16c485f736c93b"
branch: "codex/v0.7.0-space-station"
build: "1.20.1-0.7.0-dev"
upstream_commit: "c5cd5af62fc07cd4e0d24f06a16033f181c47c04"
provenance_review: APPROVED
pull_request: "https://github.com/sunthemoon/AdvancedRocketry-Community/pull/11"
forge_ci: "https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33506933608"
governance_ci: "https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33506933587"
pull_request_checks: 4/4_PASS
accepted_exception: "docs/decisions/ADR-009-V070-VISUAL-EVIDENCE-ATTESTATION.md"
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

Repository owner `sunthemoon` approved the zero-copy provenance inventory and
the G0, G8, and G9 decisions on 2026-09-01. G8 is bound to the real two-client
log bundle, packaged station run, authority matrix, and ADR-009; no screenshot
or video is claimed.

All technical and owner-review Gates are complete. PR #11 passed all four
checks and merged as
`b75e301f6cd77cfc1c1ade0e9b16c485f736c93b`. A cache-disabled forced clean
build from that merge reproduced the 1,009,631-byte JAR and 636-entry content
manifest byte-for-byte. The immutable record is
[`evidence/artifact/post-merge-reproduction.json`](evidence/artifact/post-merge-reproduction.json).
No tag or public release is created by this milestone.
