# GATE_STATUS — v0.5.0 release candidate

```yaml
version: v0.5.0
status: READY_FOR_AUDIT
tested_implementation_commit: "5cbd912bb1ad30afd242e21ca8095e53f265dab9"
base_commit: "9c270e29673b97fc78dc73024c58d623b5869c48"
branch: "codex/v0.5.0-rocket-snapshot"
build: "1.20.1-0.5.0-dev"
upstream_commit: "c5cd5af62fc07cd4e0d24f06a16033f181c47c04"
provenance_review: APPROVED
pull_request: ""
forge_ci: ""
governance_ci: ""
pull_request_checks: PENDING
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
overall: READY_FOR_AUDIT
remaining_items:
  - pass pull-request checks and merge the accepted candidate
  - reproduce the accepted JAR after merge
human_approved_by: "sunthemoon"
human_approved_at: "2026-09-01"
```

Repository owner `sunthemoon` approved the zero-copy provenance record and the
G0, G8, and G9 decisions on 2026-09-01. The exact tested implementation is
`5cbd912bb1ad30afd242e21ca8095e53f265dab9`; subsequent candidate commits may
add tests, evidence, and documentation but must not change its distributable
JAR bytes.

The candidate remains `READY_FOR_AUDIT`, not `PASSED`, until its pull-request
checks complete and the accepted JAR is reproduced after merge. No tag or
public release is created by this milestone record.
