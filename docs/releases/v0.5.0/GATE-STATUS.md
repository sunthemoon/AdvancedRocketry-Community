# GATE_STATUS — v0.5.0 release candidate

```yaml
version: v0.5.0
status: PASSED
tested_implementation_commit: "eae8d9224c708924930b781d7332eb69b6a4bf8d"
base_commit: "9c270e29673b97fc78dc73024c58d623b5869c48"
merge_commit: "90587983b78920ed1f62621c11825dfc11dd901b"
branch: "codex/v0.5.0-rocket-snapshot"
build: "1.20.1-0.5.0-dev"
upstream_commit: "c5cd5af62fc07cd4e0d24f06a16033f181c47c04"
provenance_review: APPROVED
pull_request: "https://github.com/sunthemoon/AdvancedRocketry-Community/pull/9"
forge_ci: "https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33451413796"
governance_ci: "https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33451413748"
pull_request_checks: 3/3_PASS
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
G0, G8, and G9 decisions on 2026-09-01. The exact tested implementation is
`eae8d9224c708924930b781d7332eb69b6a4bf8d`; subsequent candidate commits may
add tests, evidence, and documentation but must not change its distributable
JAR bytes.

PR #9 passed all three required checks and merged as
`90587983b78920ed1f62621c11825dfc11dd901b`. A cache-disabled clean build from
that exact merge reproduced the 703,307-byte main JAR, 357,173-byte sources
JAR, and 497-entry content manifest byte-for-byte. The reproduction record is
[`evidence/artifact/post-merge-reproduction.json`](evidence/artifact/post-merge-reproduction.json).
No tag or public release is created by this milestone record.
